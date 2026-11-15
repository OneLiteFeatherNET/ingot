# Design

Paths are relative to `reposilite-backend/src/main/kotlin/` on the current main branch unless they start with `openspec` or `docs`.

## Context

- The storage SPI is `com.reposilite.storage.StorageProvider`: `putFile`, `getFile` with an optional ranged form, `getFileDetails`,
  `removeFile`, `getFiles`, `getLastModifiedTime`, `getFileSize`, `exists`, `usage`, `canHold` and `shutdown`.
- A factory implements `StorageProviderFactory<PROVIDER, SETTINGS>` with `type`, `settingsType` and
  `create(journalist, failureFacade, workingDirectory, repositoryName, settings)` (`com/reposilite/storage/StorageProviderFactory.kt`).
  Its settings extend `StorageProviderSettings`, which adds `type`. The fixed value is declared with `@get:Custom(name = "const")`
  and secrets with `@get:Sensitive`.
- Factories are found in two places. `StorageFacade` runs `ServiceLoader.load(StorageProviderFactory::class.java)` in its
  property initialiser (`com/reposilite/storage/StorageFacade.kt`). `StorageProviderSerdes` runs the same call in a companion
  object and keeps the result in a static map used by the Jackson problem handler, the enum resolver and the subtype resolver
  (`com/reposilite/storage/StorageProviderSerdes.kt`). The application class loader sees only `fs` and `s3`, listed in
  `META-INF/services`.
- Plugin jars are loaded by `PluginLoader`, which creates one `URLClassLoader` per jar and looks up only `ReposilitePlugin` through
  it (`com/reposilite/plugin/PluginLoader.kt:86-87`, `:120`). `initialize()` calls `load()` on every plugin, then `initialize()` in
  dependency order (`:44-60`).
- Repositories are built during `MavenPlugin` initialisation through `RepositoryProvider` and `RepositoryFactory`.
  `RepositoryFactory` resolves the storage factory by `type` and throws `Unknown storage provider` when none matches
  (`com/reposilite/maven/RepositoryFactory.kt:64-72`), and the repository is then dropped (`RepositoryProvider.kt:173-174`).
- The form schema of the repository settings hard-codes the two providers with `@get:OneOf(FileSystemStorageProviderSettings::class,
  S3StorageProviderSettings::class)` (`com/reposilite/maven/application/MavenSettings.kt:173`). The dashboard renders it with the
  JsonForms `OneOf` renderer.
- The type guess for a document without `type` (`bucketName` present means `s3`, otherwise `fs`) is in
  `StorageProviderSerdes.kt:53-57`. Its comment explains why: the form does not send `type` when the `oneOf` changes. It stays.
- The search backend registration (`add-search-backend-registration`) uses an event after plugin initialisation. That is too late
  for storage, because repositories are built during initialisation. The search change keeps its event, and this change does not
  reuse it.
- Unverified before the change, to be checked by the first failing test (task 4.1): the parse of a `storageProvider` block with an
  unknown `type` calls `treeToValue(json, null)`, which is expected to fail the parse of the whole shared configuration.

## Decisions

### D1: discovery is pulled, after load and before initialise

`PluginLoader` keeps the class loader of each plugin jar. In `initialize()`, after the loop of `load()` calls and before the first
`initialize()`, it hands the application class loader and the plugin class loaders to a new internal `StorageProviderRegistry`:

```kotlin
internal class StorageProviderRegistry(
    private val journalist: Journalist,
    ...
) {
    fun discover(loaders: List<Pair<String, ClassLoader>>)   // name of the source, class loader
    fun settingsType(type: String): Class<out StorageProviderSettings>?
    fun resolve(type: String): StorageProviderFactory<*, *>?
    fun types(): Collection<String>
    fun registered(): List<RegisteredStorageProvider>        // for logs and the status
}
```

The registry runs `ServiceLoader.load(StorageProviderFactory::class.java, loader)` for each loader. The generic part of this, a helper
that returns the services of one type from a list of class loaders and reports each source, is written in a separate file so that
`RepositoryFormat` discovery (`net/onelitefeather/ingot/format/RepositoryFormat.kt`, which has the same gap) can reuse it. The migration of
`RepositoryFormat` is out of scope.

The alternative was an event emitted before `MavenPlugin` initialises. It is rejected as the default because the order of
`initialize()` follows plugin dependencies. A storage plugin that depends on nothing could run after `MavenPlugin`, and its
repositories would be built without it. Pulling after `load()` and before `initialize()` needs no ordering between plugins, because
`load()` is the step that every plugin must complete first.

`StorageFacade` keeps its no-argument constructor and its `open fun createStorageProvider(...)` signature. Its factory lookup asks the
registry, and a facade built with no registry falls back to the application class loader as today, so existing tests and subclasses are
unaffected.

### D2: the new factory interface

```kotlin
package net.onelitefeather.ingot.storage

const val STORAGE_PROVIDER_API_VERSION = 1

@RequiresOptIn(message = "Ingot storage provider API is experimental", level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalIngotStorageApi

@ExperimentalIngotStorageApi
interface IngotStorageProviderFactory<PROVIDER : StorageProvider, SETTINGS : StorageProviderSettings> :
    StorageProviderFactory<PROVIDER, SETTINGS> {

    val apiVersion: Int   // must equal STORAGE_PROVIDER_API_VERSION

    fun create(context: StorageProviderContext, settings: SETTINGS): PROVIDER

    // the inherited method delegates to the new one, so the registry always calls the new one
    override fun create(journalist: Journalist, failureFacade: FailureFacade, workingDirectory: Path, repositoryName: String, settings: SETTINGS): PROVIDER =
        create(StorageProviderContext.of(journalist, failureFacade, workingDirectory, repositoryName, ...), settings)
}
```

`StorageProviderFactory` is not changed, for the binary gate. Adding a member to it would break every implementor. A new interface that extends it adds members only to new implementors.

The registry accepts four kinds of factory:

- a built-in factory from the application class loader (`fs`, `s3`), which is a plain `StorageProviderFactory` and is accepted as it is today;
- an `IngotStorageProviderFactory` of the current API version;
- an `IngotStorageProviderFactory` of another API version, which is refused with a warning that names the class and both numbers;
- a plain `StorageProviderFactory` found in a plugin jar, which is refused with a warning that names the jar, because it has no `apiVersion`
  and no context (open question 1 in `proposal.md`).

The warning names the source as the jar file name, or the application class path for built-ins. It never prints a setting value.

### D3: opt-in

`@ExperimentalIngotStorageApi` is a `RequiresOptIn` at `WARNING` level, applied to `IngotStorageProviderFactory` and `StorageProviderContext`.
The constant `STORAGE_PROVIDER_API_VERSION` is documented as part of the same experimental package. A plugin that uses them without `@OptIn` gets a compiler warning, not an error, the same convention as
`add-repository-format-spi` and `add-search-backend-registration`. The first release that contains the package names it in the upgrade
notes.

### D4: built-in precedence and duplicate refusal

Registration order is fixed: the built-in factories from the application class loader first, then each plugin loader in the order of the
plugin list. The registry refuses:

- an id that does not match `^[a-z][a-z0-9-]*$`, with a warning that names the class (built-in ids `fs` and `s3` match, so they are not
  affected);
- an id already registered, with a warning that names both sources (`fs` and `s3` are the first entries, so a plugin can never replace them);
- a factory whose `type` is blank.

A refused factory is not used, and the others keep working. The registry logs the registered ids once at start, and it logs each refusal
once. Duplicate ids were silently overwritten before (`associateBy`), so this is a change of behaviour for a case that did not exist for
operators, since only two built-in ids are registered today.

### D5: the context

```kotlin
@ExperimentalIngotStorageApi
interface StorageProviderContext {
    val journalist: Journalist
    val failureFacade: FailureFacade
    val workingDirectory: Path
    val repositoryName: String

    fun environment(name: String): String?              // INGOT_<name>, then REPOSILITE_<name>
    fun secret(name: String): String?                   // reads <name>, or the file named by <name>_FILE
    fun createProvider(settings: StorageProviderSettings): StorageProvider   // a delegate of another registered type
}
```

- `environment` reads the `INGOT_` name first and the `REPOSILITE_` name second, as the search context does. It never reads a shared
  setting, so a plugin's variables are local to the replica, as for the search backends.
- `secret` reads the value of the variable. If the `_FILE` variant is set, it reads that file once, strips trailing line breaks, and
  prefers the plain variable when both are set. Every value returned is added to a redaction set of the context, and the context's
  logger replaces each member of that set with `***` before a message leaves the process. The rules are those of `add-file-secrets`; that
  change may land first, and this change then calls its reader.
- `createProvider(settings)` looks up the factory of `settings.type` in the registry and calls its `create` with a child context whose
  chain of types contains the outer one. A type that is already in the chain is refused with an error that lists the chain, so that a
  decorator that wraps itself cannot start (open question 5). The delegate is shut down by the owner: a provider that is created through the
  context is returned to the caller's `shutdown()` chain.
- `createProvider(settings)` with a type that is not registered (an `UnresolvedStorageProviderSettings` block) throws
  `StorageProviderNotInstalledException(type)`. `RepositoryFactory` treats that exception, from the outer factory or from any nested
  `createProvider`, exactly like a missing outer type (D7), so a decorator over a missing type leaves the repository `UNAVAILABLE` with the
  reason of the missing inner type.

`StorageProviderContext.of(...)` is internal. Plugins receive a context, they never build one.

### D6: shared configuration and schema read the registry

The companion object of `StorageProviderSerdes` no longer holds a static map. The three classes that read it take the registry in the
constructor, and the places that build them pass the instance that `PluginLoader` produced. The two resolvers are currently found by
`ServiceLoader` through `META-INF/services/com.reposilite.configuration.shared.EnumResolver` and `SubtypeResolver`, which only allows no-argument
constructors. The default is to remove those two service entries and construct the resolvers explicitly in the schema builder (task 6.1). If the
schema builder cannot take them in this change, the fallback is a registry slot that is set once at start and read at call time, and tests
bypass it by passing their own registry to the same constructors. Either way, no test reads a process-wide registry.

Parsing (task 4.x):

- `type` present and registered: the block is bound to the settings class of the registered factory.
- `type` present and not registered: the block becomes `UnresolvedStorageProviderSettings(type, raw: ObjectNode)`. It implements
  `StorageProviderSettings`, serialises as `raw` through `@JsonValue`, and is never converted to a value of another class. Numbers keep their
  source text through `BigDecimal` parsing, so the written document equals the read document.
- no `type`: the guess of `StorageProviderSerdes.kt:53-57` is unchanged, `s3` when `bucketName` is present, `fs` otherwise. The guessed
  type then goes through the same two rules. A guess to an unknown type cannot happen, because `s3` and `fs` are always registered.

The parse of the shared configuration does not need the registry to be complete, because unknown types are kept raw (open question 3). The
registry is complete before `MavenPlugin` builds repositories, and `RepositoryFactory` resolves the raw block then (D7).

Schema (task 6.x):

- `StorageProviderEnumResolver` lists the registered types whose settings class matches, read from the registry.
- `StorageProviderSubtypeResolver` lists the settings classes of all registered factories.
- The `@OneOf` annotation on `MavenSettings.kt:173` keeps its two built-in classes. The OpenAPI document is generated from annotations at
  build time, so a plugin type cannot appear in it. The OpenAPI diff gate of `add-openapi-diff-gate` therefore sees no change. The dashboard
  schema is produced at request time from the registry and is the form that lists plugin types. This answers the "check the schema
  generator" point of the brief without making the OpenAPI document depend on installed plugins.
- The form of a plugin type uses the settings class's own fields, `@Sensitive` included, so that the existing masking of
  `hideSettingsSecrets` applies unchanged.

### D7: an unavailable repository

When `RepositoryFactory` finds no factory for a raw block, it does not throw. It creates the repository in state `UNAVAILABLE` with the
reason `storage provider '<type>' is not installed` (open question 6 covers the representation). The repository:

- stays in the repository registry, so `GET` of its settings and the dashboard list show it with the reason;
- answers every file request with `503` and an RFC 9457 problem body (`add-problem-details`) whose detail is the reason, and the header
  `Retry-After: 30`; the answer does not touch the storage location;
- makes no call to any storage operation, including `exists`, `usage` and `canHold`;
- does not block the start of the server, and the other repositories keep serving.

Saving repository settings from the dashboard writes the raw block back unchanged, and the form shows it read-only, with the reason. The
block is not converted to the built-in classes, so no field is lost. No data is touched: nothing is removed, moved or re-created. When the
plugin is installed, a restart or a shared configuration reload (`add-shared-configuration-reload`) builds the repository again from the same
raw block, and it becomes available.

Any other exception from a factory's `create` is handled as today: it is reported through the failure facade and the repository is not
loaded. This rule is the same for built-in and plugin factories, so an invalid `fs` or `s3` block behaves exactly as before this change, and
a plugin reports its own setting errors this way.

Readiness (open question 2): `/ready` ignores `UNAVAILABLE` repositories by default, so one replica without the plugin does not block a rolling
update. The readiness reason lists the count of unavailable repositories for the operator.

### D8: the type guess stays

The guess for a document without `type` (`s3` when `bucketName` is present, `fs` otherwise) is kept exactly. The dashboard form still does not send
`type` when the `oneOf` changes, and the guess keeps that working. The guess is tested with the existing serdes tests, which pass unedited.

### D9: contract test kit

`StorageProviderContract` is an abstract JUnit 5 class in the test fixtures of `reposilite-backend` (`src/testFixtures/kotlin`). It has an abstract
`provider(temporaryDirectory: Path): StorageProvider` factory method and one `@Test` method per case. It starts from the nine cases of
`StorageProviderIntegrationTest` and adds:

- an out-of-range ranged read and a zero-length ranged read;
- `getFileSize` and `getLastModifiedTime` for a stored file and for a missing one;
- no partial object is visible during `putFile`: the test passes a blocking input stream and reads the target from another thread until the stream
  is released, with latches, and no sleep;
- a root delete is refused;
- a prefix is reported as `DirectoryInfo` and an empty prefix as `NOT_FOUND`;
- `NOT_FOUND` only for a missing key, never for an I/O failure;
- `StorageEntrySource` cursor paging when the provider implements it;
- last-writer-wins on concurrent puts to the same key, with a fixed number of threads and a latch.

`fs` runs the kit as a unit test with `@TempDir`. `s3` runs it in the existing integration source set with its container. A plugin module depends on
it with `testImplementation(testFixtures(project(":reposilite-backend")))`, as `opensearch-plugin` does.

The kit pins behaviour, it does not change it in this change. A case that `fs` or `s3` fails today is switched off for that provider with a
JUnit assumption whose message names the gap and the follow-up `fix(storage)` change that closes it (for example `fix-atomic-filesystem-upload`).
No setting, key, JSON shape, on-disk layout, object key or response of `fs` or `s3` changes here. Documented in the how-to for plugin
authors. Open question 4 covers the Maven artifact.

### D10: the fixture plugin

The registry test loads a real plugin jar, not a mock. A second source set `pluginFixture` in `reposilite-backend` holds one factory
(`type = "fixture"`, `apiVersion = 1`) and its settings class, plus a `META-INF/services/com.reposilite.storage.StorageProviderFactory` entry
that names the factory. Its `jar` task writes `pluginFixture.jar` into the build directory. The test task of `reposilite-backend` depends on
that task and receives the jar path as a system property. The test is not built from a mock: it copies the jar into a `@TempDir` and loads it
through `URLClassLoader(arrayOf(jar), StorageProviderFactory::class.java.classLoader)`, so the SPI interfaces resolve from the parent while the
fixture classes exist only in the jar. The test asserts first that `ServiceLoader` on the application loader finds no `fixture` factory, then
that the registry finds exactly one through the plugin loader, which makes the isolation explicit.

## Risks

- **Class loader leaks.** A plugin loader lives as long as the server. The registry holds the loaders it discovered and the server does not
  reload plugins, so nothing new is retained. Documented in the how-to.
- **A plugin that blocks in `create`.** `create` runs during repository build on the initialisation thread. A plugin that never returns stops
  the start. The core cannot interrupt a plugin. Documented; the how-to tells plugin authors to bound their connection timeouts.
- **Secrets in the raw block.** An unresolved block keeps its raw values, including secrets, because it is written back unchanged. The
  dashboard shows it through the same masking as any other settings block, and `hideSettingsSecrets` keeps the masking. The raw node is never
  written to a log.
- **The schema of an unavailable type.** The dashboard shows the reason, not a form, because the field schema is unknown without the factory.
- **The SPI is new and experimental.** It is marked, and the first release that includes it says so in the upgrade notes.

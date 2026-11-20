# Design

This change is the server part of decision D13 of `extract-cleanup-plugin` and `extract-javadoc-plugin`. Both name the file
`META-INF/ingot/openapi/<id>.json`, the lookup of `json-schemes/<type name>` through the plugin's own class loader and the rule that the
committed snapshot stays the full merged document, so those names are fixed here.

## Context

The code on `origin/main`, with the facts the design relies on:

- `JavalinConfiguration.configureOpenApi` (`reposilite-backend/src/main/kotlin/com/reposilite/web/application/JavalinConfiguration.kt:235`)
  registers `OpenApiPlugin` with a definition configuration (info, three security schemes) and one definition processor, which applies
  `ExtensibleEnumSchemas`, `SecondFactorAlternative`, `LoginChallengeSchemas` and `describeSecuritySchemes` to the document and returns
  it as a string. The document is built from the index `openapi-plugin/.index` on the first request to `/openapi`.
- `kapt("io.javalin.community.openapi:openapi-annotation-processor")` runs on the backend's main source set only. The test source sets
  exclude it (`reposilite-backend/build.gradle.kts`, the block before the end of the file), and the empty `json-schemes/index` of the
  test resources keeps the tests on the runtime schema generator.
- `SharedConfigurationFacade` (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SharedConfigurationFacade.kt`,
  the `init` block near line 139) builds `knownSchemes` from `JsonSchemaLoader().loadGeneratedSchemes()` and, for each domain type not
  in it, logs the warning and generates the schema at runtime.
- `Extensions.getPlugins()` returns every registered `PluginEntry`, whose `plugin` is the plugin instance. The class loader of that
  instance's class is the plugin's own loader: the shared loader of the plugins directory, a store loader, a bundled loader, the loader
  of an embedded jar, or the server's loader for a built-in plugin and for a plugin on the flat integration classpath.
- `OpenApiSnapshotIntegrationTest` canonicalises the served document (keys sorted, `info.version` replaced) before it compares it with
  `docs/openapi/ingot-openapi.json`.
- `buildSrc/src/main/kotlin/ingot/plugin/` holds the build helpers of the plugin system (`EmbeddedPluginJars`, `PluginJarManifest`) with
  tests in `buildSrc/src/test/kotlin/`.

## Decisions

### D1. One fragment file per plugin id, read through the plugin's class loader

The fragment is the document the processor generates for the plugin module, renamed to `META-INF/ingot/openapi/<plugin id>.json`. The
server reads it with `plugin.javaClass.classLoader.getResource(...)` for each registered id.

- Keyed by id, so the shared loader of the plugins directory, which holds every jar of that directory, still returns the one file of
  the plugin asked for. A file for an id that is not registered is never read.
- The class loader of the plugin's class covers every origin with one rule. On the flat integration classpath of an extraction, that
  loader is the server's, which is the "and, for the flat test classpath, through the server's" of D13.
- A built-in plugin of today ships no fragment, because the server's processor writes no file under `META-INF/ingot/`. Nothing is read
  twice.

Rejected: scanning `getResources("META-INF/ingot/openapi/")` on every loader. It would pick up fragments of plugins that failed to
register, and the order would depend on the classpath.

### D2. The merge adds `paths` and `components.schemas`, the server first, then plugins by id

A new internal object `PluginOpenApiFragments` in `net.onelitefeather.ingot.plugin.openapi` takes the server document as a Jackson
`ObjectNode`, a list of `(pluginId, bytes)` and a warning sink, and returns the document.

- Plugins are taken in ascending order of their id, so the result does not depend on load order.
- For each fragment, each entry of `paths` and of `components.schemas` is copied in the fragment's own order when its key is absent
  from the document. A present key is kept. The server's entries are present first, so the server always wins. Between two plugins
  the lower id wins.
- Every kept key logs one WARN line: `[OpenApi] Plugin '<id>' documents <kind> '<key>', which is already documented; keeping the
  existing entry`.
- A fragment that does not parse as a JSON object, or whose `paths` or `components.schemas` is present and not an object, is skipped
  whole, with one WARN line naming the plugin and the parse message. A fragment with neither key adds nothing and logs nothing.
- A missing `components` or `components.schemas` in the server document is created only when a fragment adds a schema.
- With an empty list the object returns the node it was given, unchanged. No copy, no re-serialisation.

The step runs first in the definition processor of `configureOpenApi`, before `ExtensibleEnumSchemas` and the others, so plugin
entries get the same treatment as server entries. Reading the fragments happens inside the processor, which runs when the document is
built on the first request, after every plugin has loaded. D13 says "the start logs one WARN line"; the line is written when the
document is first built, which is the first request to `/openapi` or the Swagger UI, not the start itself. Javalin builds the document
once per process, so the line is still written once (confirmed by task 0.3, see "Preconditions").

Rejected: merging into the snapshot at build time. A third-party plugin is not known at build time.

### D3. The schema lookup tries the plugin's loader before the runtime generator

A new internal function `PluginSettingsSchemas.find(type)` in `net.onelitefeather.ingot.plugin.schema` returns the bytes of
`json-schemes/<type.name>` through `type.classLoader`, or null. `SharedConfigurationFacade` calls it only when the type is not in
`knownSchemes` and `type.classLoader` is not the class loader of `SharedConfigurationFacade`. When it returns null, the facade logs the
warning and generates the schema at runtime, as today.

- Types on the server's loader keep today's path exactly, so the unit and integration tests, which rely on the empty
  `json-schemes/index`, do not change behaviour.
- The file name is the class name, unique per class, so a plugin's file cannot shadow a server file, and a server file of the same name
  does not exist because the type is not in the server's index.
- The bytes are read once at start and served from memory, as the compile-time schemas of the server are.

### D4. A build step in `buildSrc`, not a Gradle plugin

`ingot.plugin.PluginApiFragments.apply(project, pluginId)` is called from a plugin module's `build.gradle.kts`.

- It adds `kapt("io.javalin.community.openapi:openapi-annotation-processor:<version of the backend>")`, so the module uses the same
  processor version as the server.
- It renames `openapi-plugin/openapi-default.json` to `META-INF/ingot/openapi/<pluginId>.json` and excludes `openapi-plugin/.index` and
  `json-schemes/index` from the module's processed output, so the files are absent from the classes directory the integration classpath
  uses, not only from the jar. Task 0.2 confirmed where the processor writes: into the kapt class output
  `build/tmp/kapt3/classes/<source set>/`, not into `build/resources/<source set>/` and not into `build/generated/`. The Kotlin kapt
  plugin adds that directory to the source set output, so the files reach both the jar and the classes directories of the integration
  classpath from there. The step therefore rewrites that directory after the kapt task, not the output of `processResources`. See
  "Preconditions" below for the exact files.
- A task `verifyPluginApiFragments` reads the module's jar and fails when it holds `openapi-plugin/.index`, `json-schemes/index`, or any
  `META-INF/ingot/openapi/*.json` other than `<pluginId>.json`. It runs as part of `check`. Its rules are a pure function over a jar
  file, tested in `buildSrc/src/test/kotlin/` with temporary jars, as `EmbeddedPluginJars` is.

Rejected: a published Gradle plugin for third-party authors. It would be a second artifact to release and version. The author page
gives the plain Gradle lines instead, which are a handful.

### D5. Plugin API stays unchanged

No runtime type is public (proposal, "Plugin API"). The file layout is the contract a plugin author meets, and it is documented as
experimental. The plugin ABI gate and `reposilite-backend/plugin-api-classes.txt` show no change.

## Preconditions

Recorded on `origin/main` at `5dd921e11`, before the first code pull request.

### 0.1 Baseline

- `heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:classes :reposilite-backend:test --tests
  'com.reposilite.configuration.shared.*'`: green, 27 test classes, 166 tests, 0 failures, 0 errors, 0 skipped.
- `heavy ./gradlew --no-daemon --max-workers=2 :reposilite-backend:integration --tests
  'com.reposilite.web.OpenApiSnapshotIntegrationTest'`: green, 3 tests, 0 failures, 0 errors, 0 skipped. The test passing means the
  canonical form of the document served at `/openapi` equals the committed snapshot byte for byte.
- Reference of task 1.4: `docs/openapi/ingot-openapi.json`, 224064 bytes, sha256
  `df6073ee650ffc139b59a982e6b69e39f9bc64c5bbf3ab2808af20083ff41412`. A copy is kept outside the repository. Task 1.4 compares the
  canonical served document with no plugin fragment against this hash.

### 0.2 Output of the annotation processor

Read from the backend's own build, which applies `kotlin("kapt")` and
`kapt("io.javalin.community.openapi:openapi-annotation-processor:7.2.2")` exactly as a plugin module will through D4. kapt writes
generated resources to `build/tmp/kapt3/classes/<source set>/`, which for a plugin module's main source set is
`build/tmp/kapt3/classes/main/`:

| Path below `build/tmp/kapt3/classes/main/` | Content |
|---|---|
| `openapi-plugin/.index` | The file names of the generated documents, one per line. For one default document it is the single line `openapi-default.json` (20 bytes, no trailing newline). |
| `openapi-plugin/openapi-default.json` | The generated document of every `@OpenApi` route of the module (220470 bytes for the backend). |
| `json-schemes/index` | The fully qualified class names of every `@JsonSchema` class, one per line, no trailing newline (45 names for the backend). |
| `json-schemes/<fully qualified class name>` | One schema per class, the file name is `Class.name`, for example `json-schemes/com.reposilite.cleanup.application.CleanupSettings`. |

- None of these files is in `build/resources/main/` or `build/classes/kotlin/main/`, and nothing of them is under `build/generated/`
  (that holds only the kapt stubs and sources).
- The jar `reposilite-backend-<version>.jar` holds them at the same relative paths: `openapi-plugin/.index`,
  `openapi-plugin/openapi-default.json`, `json-schemes/index` and the `json-schemes/<class name>` files.
- The test resources keep an empty `reposilite-backend/src/test/resources/json-schemes/index`, and the `kaptTest`, `kaptIntegration`
  and `kaptTestFixtures` configurations exclude the processor, so no test source set writes its own copy.
- The same directory also holds unrelated kapt output (`META-INF/native-image/` of `picocli-codegen` in the backend), which the step
  of D4 leaves alone.

### 0.3 When the document is built

`OpenApiPlugin` of `javalin-openapi-plugin` 7.2.2, the version the backend declares as `javalinOpenApi`, registers the `/openapi`
route in `onStart` with a handler over `lazy { ... }`. The lazy block loads `openapi-plugin/.index`, applies the definition
configuration and then the definition processor. Kotlin's `lazy` is synchronised by default, so the document and every line the
processor logs are produced once per process, on the first request to `/openapi`. `ReposiliteFactory.createReposilite` loads and
initialises every plugin (`loadPluginsByServiceFiles`, `initialize`) before `Reposilite.launch` starts the web server, so the processor
always runs after every plugin has loaded.

## Risks

- **A plugin documents a route it does not serve.** The document then lists a route that answers 404. The same is true of a wrong
  annotation in the server today. It only affects the document, never routing or authorisation.
- **A large or hostile fragment.** It is read once, from a jar the operator installed, and only `paths` and `components.schemas` are
  copied. It cannot remove or replace a server entry.
- **The processor's output layout changes with a Javalin OpenAPI upgrade.** `verifyPluginApiFragments` then fails the module's build
  rather than shipping a jar with an index that shadows the server's.

## Migration plan

Each step is one pull request, buildable and passing alone:

0. The prerequisite `add-embedded-plugin-jars` is merged (outside this change).
1. The fragment merge and its tests. With no fragment, nothing changes.
2. The schema lookup and its tests. With no plugin schema, nothing changes.
3. The build step, its check and the plugin author page.

After pull requests 1 and 2 the gates of `extract-cleanup-plugin` (task 1a.1) and `extract-javadoc-plugin` (task 2a.1) are met. The
extractions apply the step of pull request 3 in their own modules.

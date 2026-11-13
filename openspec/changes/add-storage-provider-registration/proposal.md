# Proposal

## Open maintainer questions

Each question has a default. The design and tasks follow the default; a different answer changes only the named decision.

1. **Plain factories in plugin jars (design D2).** Default: a `StorageProviderFactory` that does not implement
   `IngotStorageProviderFactory` and sits in a plugin jar is refused with a warning that names the jar, because it has no
   `apiVersion` and no context. Alternative: accept it with a warning and pass it the journalist, failure facade, working
   directory and name only.
2. **Readiness with an unavailable repository (design D7).** Default: `/ready` does not fail when a repository is
   `UNAVAILABLE`, so a rolling update of an HA cluster is not blocked by a missing plugin on one replica. Alternative: `/ready`
   fails, which makes the missing plugin visible to the orchestrator but can stop a rollout.
3. **Parsing the shared configuration before plugins load (design D6).** Default: the shared configuration is parsed without the
   registry. An unknown `type` is kept as its raw block, and the registry resolves it when repositories are built, which happens
   after every plugin has loaded. Alternative: parse only after the registry is complete, which needs a second pass over the
   shared configuration on startup.
4. **Contract kit as a Maven artifact (design D9).** Default: `testFixtures` only, consumed by plugin modules inside this
   repository. Alternative: publish `net.onelitefeather.ingot:reposilite-storage-testkit`, which makes plugin authors outside the
   repository depend on it and puts its API under the gate.
5. **Nesting providers (design D5).** Default: `createProvider` may build any registered type, and a cycle (a type that is
   already being built further up the chain) is refused with an error that lists the chain. Alternative: allow only one level of
   nesting, which is simpler to document and to test.
6. **Shape of the unavailable state (design D7).** Default: the repository keeps a state `UNAVAILABLE` with a reason string, not
   an exception. Alternative: keep the repository out of the registry and report the reason only in the status and dashboard
   lists, which hides the repository from the Maven routes but needs a separate status record.

Research coverage for these five changes was partial: 10 of 20 research topics returned, and Google Cloud Storage, regional S3 clouds, Swift, WebDAV, SMB/NFS, content addressing, competitor SPIs and online migration were not researched. Every third-party claim in the five changes is marked as a verify task, and none is treated as verified yet.

## Why

Ingot offers two storage providers, `fs` and `s3`, and a plugin has no way to add a third. The one extension point that exists,
`StorageFacade`, is an open class that a plugin can wrap, but a wrapper cannot register a new `type`. The factories are found by
`ServiceLoader.load` on the application class loader only, and the `type` map that Jackson and the dashboard schema read is built
once in a companion object. A factory placed in a plugin jar is therefore never seen. A repository whose `storageProvider.type`
names a plugin type cannot be parsed, or, when the parse passes, it is dropped at startup with "Unknown storage provider".

Storage is the first backend family that operators will want to extend with plugins (Azure Blob, Google Cloud Storage, a local
read-through cache). Each of those needs the same seam. Writing it three times would produce three different answers to
questions that are shared: how a plugin is found, what happens when it is missing, what a plugin may read from the environment,
and how its secrets stay out of logs. This change decides those questions once, for every storage provider, before any new
provider is written.

The rules matter most for high availability. Several replicas share one database, and a repository's storage settings are shared
settings. If one replica has a plugin installed and another does not, the second must not stop, and it must not touch the data
of the first. A missing plugin therefore leaves the repository in a visible unavailable state, and no file is read or written
until a replica that has the plugin serves it.

## What Changes

- **Public experimental interfaces** in `net.onelitefeather.ingot.storage`: `IngotStorageProviderFactory`, which extends
  `StorageProviderFactory` and adds an `apiVersion` and a `create` that receives a `StorageProviderContext`; the context itself;
  the opt-in marker `@ExperimentalIngotStorageApi`; and the constant `STORAGE_PROVIDER_API_VERSION = 1`. The existing
  `StorageProviderFactory`, `StorageProvider` and `StorageFacade` are not changed.
- **Discovery after plugin load, before initialisation.** A new internal `StorageProviderRegistry` runs `ServiceLoader` over the
  application class loader and over the class loader of every plugin. It runs after every plugin's `load()` and before the first
  `initialize()`, so that repositories built during `MavenPlugin` initialisation see every provider.
- **Built-in precedence.** `fs` and `s3` are registered first, cannot be replaced, and keep their type ids, settings classes, JSON
  shape and behaviour. A plugin that claims one of these ids, or an id that is already registered, is refused with a warning that
  names both sources. A factory with an unsupported API version is refused with a warning that names the class and both numbers.
  Type ids are lower-case kebab-case.
- **Plugin context.** `StorageProviderContext` offers the journalist, the failure facade, the working directory and the repository
  name; `environment(name)`, which reads `INGOT_` and falls back to `REPOSILITE_`; `secret(name)`, which reads the `_FILE` form and
  redacts the value in logs; and `createProvider(settings)`, which builds a delegate of another registered type so that a provider
  can decorate another one.
- **Shared configuration and schema read the registry.** The companion map in `StorageProviderSerdes` is replaced by the
  registry. A storage block whose `type` is registered deserialises to its settings class. A block whose `type` is unknown
  deserialises to `UnresolvedStorageProviderSettings`, which keeps the raw JSON and writes back the identical document. The dashboard
  form lists the registered types with their own field schema, including the `@Sensitive` masking of the settings class.
- **Missing plugin.** The server starts. The repository is created in state `UNAVAILABLE` with the reason
  `storage provider '<type>' is not installed`. Requests to it answer `503` with problem details. The dashboard lists the reason,
  and saving settings keeps the block unchanged. No file is read, written or removed. After the plugin is installed, a restart or a
  configuration reload restores the repository.
- **Distribution rule for storage plugins.** A storage plugin is not bundled in the image. It is published as the Maven
  artifact `net.onelitefeather.ingot:<module>` and installed by hand into `plugins/`, which keeps working unchanged. The
  plugin store (`add-plugin-store`) can list the same artifact as a signed catalogue entry, and `publish-plugin-catalogue`
  publishes it there. Each backend change takes that store path in its own distribution tasks. This change neither depends
  on those two changes nor changes them.
- **Factory failures.** Besides a missing type, a factory's `create` that throws is handled as today: the failure is reported and the
  repository is not loaded. A nested `createProvider` of a missing type is treated as a missing type, so the repository is `UNAVAILABLE`.
- **Contract test kit.** An abstract JUnit 5 class `StorageProviderContract` in the test fixtures of `reposilite-backend`. It holds
  the nine cases of the existing integration test and the additional cases of design D9. `fs` runs it as a unit test with a
  temporary directory, and `s3` runs it in the integration source set with its container. A case that `fs` or `s3` fails today is
  skipped for that provider with its reason; this change does not alter either provider.
- **Documentation.** A reference page for the registry and the missing-plugin behaviour, a how-to for writing a storage plugin, and
  updates to the plugin API reference (the registration replaces wrapping `StorageFacade` as the extension point), to the
  environment variable page for the plugin-facing rules, and to the upgrade notes.

### Out of scope

- Capability hooks for the cluster cache, shared namespaces and usage reporting. These are `add-storage-capability-hooks`.
- Any storage backend. Azure Blob, Google Cloud Storage and the read-through cache are sibling changes that depend on this one.
- Migrating the discovery of `RepositoryFormat` to the same helper. The helper is written generically so that the format SPI can
  reuse it, but the migration is a later change.
- The plugin store (`add-plugin-store`) and its release path (`publish-plugin-catalogue`), changing the plugin loader's bundled switches, or how plugin jars are signed.
- Changing the existing `StorageProviderFactory` or `StorageProvider` interfaces. A change to them breaks the binary gate.
- Any new configuration key in the shared settings. Plugins read their own variables through the context.

## Capabilities

### New Capabilities

- `storage-provider-registration`: the public experimental storage interfaces, discovery from plugin class loaders, built-in
  precedence and refusal rules, the context and its environment and secret rules, the unresolved settings block, the unavailable
  repository, the dashboard schema, and the contract test kit.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `feat(storage)`, `build(storage)`
  and `docs(storage)`. The pull request title is:

  `feat(storage): let plugins register storage providers`

- **Breaking marker.** No breaking change. `fs` and `s3` keep their ids, settings, JSON shape and behaviour, and a configuration
  that works today works unchanged. No configuration key, environment variable, volume, manifest, image or plugin has to change.
  One behaviour changes on purpose: a storage block whose `type` is not installed used to fail the parse of the shared configuration
  or drop the repository at startup. It now starts the server, keeps the repository visible as `UNAVAILABLE`, and answers `503`.
  The upgrade notes say so. Nobody runs such a block on purpose, because only `fs` and `s3` exist.
- **Binary surface.** New public types under `net.onelitefeather.ingot.storage`, marked experimental. The packages `com.reposilite.*`
  gain no member, and `StorageFacade`, `StorageProviderFactory` and `StorageProvider` keep their signatures, so the `gate-plugin-abi`
  report of `com.reposilite.storage` stays unchanged. The new package enters the gate when its API dump is added (the follow-up named
  in `gate-plugin-abi`).
- **Build.** `java-test-fixtures` is already applied to `reposilite-backend` on main and is reused, and
  a second source set `pluginFixture` builds one small fixture plugin jar. Nothing else in the build changes.
- **Order.** Depends on nothing. The sibling changes `add-storage-capability-hooks`, `add-azure-blob-storage-plugin`,
  `add-gcs-storage-plugin` and `add-storage-cache-plugin` depend on this one.

## Verification

- Unit tests of the registry with a fixed set of factories: registration, version refusal, duplicate refusal, built-in precedence,
  plain factory refusal, invalid id refusal, and the cycle refusal of `createProvider`.
- A fixture plugin jar, built by the `pluginFixture` source set, loaded through a real `URLClassLoader` in a temporary directory,
  with the jar absent from the test class path so that only the plugin loader can see it.
- Serdes round trip of an unknown `type` that writes back the same JSON.
- Integration tests: a startup with a missing type reports `UNAVAILABLE`, answers `503` with problem details on the repository routes,
  keeps the download of other repositories working, and restores the repository after a restart with the plugin present.
- Schema test: the schema contains the plugin type with its own fields and masks its sensitive fields.
- The existing `fs` and `s3` tests stay unchanged and green.
- `openspec validate add-storage-provider-registration --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-storage-provider-registration` prints nothing.

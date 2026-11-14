# Proposal

## Open maintainer questions

Each question has the default this proposal already builds in. A maintainer who answers differently changes the
matching requirement and task before the code pull request is opened.

1. **Ranged miss.** A range request that misses the cache is passed to the delegate's ranged read and does not fill the
   cache (default). The alternative is to fill the whole file in the background. That costs a full transfer for one
   range, and it needs a bounded background pool per replica.
2. **HEAD revalidation on hit.** Off (default). A hit is served from the local file without asking the delegate. Freshness
   of a replaced release comes from the cluster invalidation and from the operator not allowing redeployment. The
   alternative is a delegate HEAD on every hit, which keeps the latency gain small.
3. **cacheOnUpload.** Off (default). Uploads go to the delegate and are read back on the first download. The alternative
   copies every accepted upload into the cache, which speeds up the first download of a fresh release at the cost of disk.
4. **maxSize default and units.** `5GB` (default), read as binary units (1 GB = 1024 x 1024 x 1024 bytes). The
   alternative is decimal units. The default is meant to follow the Artifactory `cache-fs` default; whether that default is
   5 GB, and whether Artifactory counts it in decimal or binary units, is verified by task 0.1.
5. **cacheMutable.** The key is part of the settings with default `false`. This proposal refuses `true` at start with a
   configuration error that names the key, because caching mutable keys needs a freshness rule that nobody has decided
   yet. The alternative is to remove the key until that decision exists. A time-to-live for mutable keys is not part of
   this proposal: mutable keys are never cached.
6. **Shared mutable key rule.** The rule `isMutableS3Key` moves to core as `isMutableStorageKey` without a change of
   behaviour, so that the cache uses the same rule as the S3 client. The cache adds the index files of the format
   (`archetype-catalog.xml` with its checksums and the `.index` directory) for itself only. Confirm the index file names.
7. **Metrics.** The cache reports its hit rate, size and evictions in the support details of the repository. Metrics are
   published only when a metrics facade of `add-cluster-metrics` is loaded, with no hard dependency (default). Confirm
   that no dependency on the prometheus plugin is wanted.
8. **Pinned entries.** When every cached file is in use by a reader, a new fill is skipped and the request is served from
   the delegate. It is not delayed (default). Confirm that a skipped fill is acceptable and that it is only counted, not
   logged on each request.

## Why

Every Maven resolve against an object store is a remote GET today. Each download costs latency, and every byte leaves the
bucket as egress. Release artifacts are immutable once published, so a copy kept on the node that serves them is safe, as
long as mutable metadata never goes through the copy. Operators who run Ingot in front of S3, Azure Blob or Google Cloud
Storage ask for a local read cache in front of the remote store. JFrog Artifactory is understood to ship the same pattern as
`cache-fs`: least recently used eviction, a default size of 5 GB, a maximum file size, an option to skip the cache during upload,
and checksum validation, with documentation that asks for one cache per node in a high availability setup and asks operators
not to use a cache when the store is already local. None of these points is cited yet; task 0.1 verifies each against the
current JFrog documentation before the proposal pull request.

This change ships that pattern as a plugin, not in the core. The cache wraps any other registered storage provider, so
it works with the S3 provider of today and with the cloud providers that the sibling changes add. The cache is an opt-in
storage type. A repository that does not select `cache` sees no difference.

## What Changes

- **A plugin module `reposilite-plugins/storage-cache-plugin`**, package `net.onelitefeather.ingot.storage.cache`, plugin
  id `storage-cache`. It registers the storage type `cache` through `IngotStorageProviderFactory` of
  `add-storage-provider-registration`, with `apiVersion = STORAGE_PROVIDER_API_VERSION` (1), opted in with
  `@OptIn(ExperimentalIngotStorageApi::class)`, and listed in the jar's `META-INF/services/com.reposilite.storage.StorageProviderFactory`.
  Without the jar, a repository of type `cache` is `UNAVAILABLE` with the reason `storage provider 'cache' is not installed`.
- **A decorator.** The `cache` provider wraps a delegate provider built from a nested settings block of any registered type
  except `cache`. Reads are served from a per-replica local directory when the file is cached. Mutable keys, the metadata
  and everything that describes a file come from the delegate.
- **Settings.** `type` (always `cache`), `delegate` (a nested storage settings block), `directory`, `maxSize`, `maxFileSize`,
  `cacheOnUpload` and `cacheMutable`. Every key has a default, so `{"type":"cache","delegate":{"type":"s3","bucketName":"..."}}`
  is a complete configuration. The `@Sensitive` fields of the delegate stay masked.
- **Read-through with single flight.** A full-file miss fetches the file once for all concurrent requests of the same key,
  writes it to a temporary file in the cache directory and renames it into place. A ranged miss is passed through.
- **Write-through, never write-back.** A write goes to the delegate first. Only a successful write is copied into the cache,
  and only when `cacheOnUpload` is `true`.
- **Invalidation across replicas.** A removal or an overwrite drops the local entry and publishes an invalidation. Every other
  replica drops its copy through `ClusterCacheAware.invalidate` of `add-storage-capability-hooks`. That change deliberately adds no
  publishing member to `ClusterBus` (its open questions 1 and 3). This change is the later change those questions name: it adds one
  experimental member to `ClusterBus` to publish an invalidation for a repository, and the cluster plugin delivers it to the same
  repository's provider on the other replicas. Only the cache publishes, so a plain `s3` repository receives no message and keeps its
  300-second bound unchanged. Without a cluster bus (a single replica) nothing is published, and nothing needs to be.
- **Eviction and limits.** Least recently used eviction by last access, a total size limit, and a maximum file size. The
  limit never blocks a write, and a cache failure never fails a read.
- **Start-up.** The in-memory index is rebuilt from the cache directory without database rows. Temporary files from an
  earlier run are removed. A lock file keeps a second process from using the same directory.
- **Capabilities.** The cache settings forward `SharedNamespaceAware` and `SupportDetails` to the delegate settings (capabilities on
  settings, as `add-storage-capability-hooks` decides). The provider forwards `ReadinessProbe` and `RelatedKeyAware` to the delegate,
  so that the delegate keeps its own invalidation rule, and implements `ClusterCacheAware` for its own invalidations.
- **Distribution.** The Maven artifact `net.onelitefeather.ingot:storage-cache-plugin` is the release unit, and the jar is
  not bundled in the image. The plugin store of `add-plugin-store` lists it as a signed catalogue entry, selectable with
  `INGOT_PLUGINS` or in the dashboard and published by the release pipeline of `publish-plugin-catalogue`. The jar also
  stays installable by hand into `plugins/`, and a jar there wins over a store entry with the same id.
- **Setting errors.** A delegate of type `cache`, `cacheMutable: true`, an unparsable size or a taken lock make the factory throw,
  so the repository is not loaded and the failure is reported, the registration rule for a failing factory. Only a delegate whose type
  is not installed leaves the repository `UNAVAILABLE`, because `createProvider` reports it as a missing type.
- **Tests.** Unit tests with an in-memory fake delegate, an injected `Clock`, a failure-injecting delegate and latches for the
  single flight. The storage contract suite runs against the cache over the file system delegate. An integration test runs
  the cache over the S3 test container.
- **Documentation.** A how-to page `docs/gitbook/how-to/use-a-local-read-cache.md`, a section of the storage providers
  reference with every setting, and a line in the upgrade notes that the type is new and optional.

### Out of scope

- Archive tier restore orchestration (the archive tier is not a storage type in this change).
- Sharding providers and replicating providers that spread files over several delegates.
- Write-back caching. A replica never holds a file the remote store lacks.
- Sharing one cache across replicas. Each replica has its own directory, and the cluster bus carries invalidations only.
- A time-to-live for mutable keys. Mutable keys are never cached (open question 5).
- A cache for the database, for the search index or for the repository metadata of the database.
- Warming the cache from a list of artifacts, and a management page for the cache in the dashboard.
- Background fills of ranged misses (open question 1).
- Revalidation of hits against the delegate (open question 2).

## Capabilities

### New Capabilities

- `storage-read-cache`: the `cache` storage type, its settings and delegate rules, the read and write paths, the invalidation
  across replicas, eviction and limits, start-up, the capabilities it forwards or implements, and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `refactor(storage)`,
  `feat(storage)`, `build(storage)`, `docs(storage)` and `ci(storage)`. The pull request title is:

  `feat(storage): add a read-through local cache storage plugin`

- **Breaking marker.** No breaking change. The `cache` type is new and optional, and a repository selects it only with
  `"type": "cache"` in its storage block. Existing `fs` and `s3` settings are read unchanged, and their defaults do not
  change. The shared mutable key rule moves to core (`refactor(storage)`), and the S3 client keeps its behaviour, which its
  existing tests pin. No environment variable, volume, manifest, image or plugin that an existing installation uses has to
  change. The plugin is not bundled, so the image is unchanged.
- **Binary surface.** The plugin depends on the public storage types of `com.reposilite.storage` and the experimental packages of
  the sibling changes. The moved rule `isMutableStorageKey` becomes public in `com.reposilite.storage` so that the plugin can call
  it, and it is added to the plugin API dump checked by `gate-plugin-abi`. The only other new member is the experimental publishing
  member of `ClusterBus` in `net.onelitefeather.ingot.storage.capability`.
- **Build.** A new Gradle subproject `:reposilite-plugins:storage-cache-plugin`, with the plugin id `storage-cache`, a
  shadow jar and the Maven publication `net.onelitefeather.ingot:storage-cache-plugin`. No line of `Dockerfile` changes and `PluginLoader.BUNDLED_SWITCHES` is not touched.
- **Order.** Depends on `add-storage-provider-registration` (the registration of storage factories, the `StorageProviderContext`
  that builds the delegate, and the nested settings schema) and on `add-storage-capability-hooks` (`ClusterCacheAware`,
  `SharedNamespaceAware`, `RelatedKeyAware` as capability interfaces). The distribution tasks depend on `add-plugin-store` and `publish-plugin-catalogue`; no other task does.

## Verification

- The unit suite of the plugin runs without network, database or system clock, and passes with the fixed clock.
- The storage contract suite passes on the file system delegate, and on the same cases as the plain provider.
- The integration test passes on the S3 test container, including a removal from a second replica and the invalidation it
  causes.
- The existing S3 tests pass unedited after the move of the mutable key rule.
- `openspec validate add-storage-cache-plugin --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-storage-cache-plugin` prints nothing.

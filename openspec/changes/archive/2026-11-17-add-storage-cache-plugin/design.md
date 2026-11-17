# Design

This design depends on `add-storage-provider-registration` (factories from plugin jars, the `StorageProviderContext` that builds
a provider from settings, and the nested settings schema) and on `add-storage-capability-hooks` (the capability interfaces
`ClusterCacheAware`, `SharedNamespaceAware` and `ReadinessProbe` forwarding, and the replacement of type checks). Names below
are those of the storage code of this repository and of those two changes.

## Context

- The SPI is `com.reposilite.storage.StorageProvider`: `putFile`, `getFile` with a ranged overload whose default skips bytes,
  `getFileDetails`, `removeFile`, `getFiles`, `getLastModifiedTime`, `getFileSize`, `exists`, `usage`, `canHold` and `shutdown`.
  A `StorageEntrySource` is an optional capability used for listings.
- `StorageProviderFactory<PROVIDER, SETTINGS>` names a `type`, a `settingsType` and a `create(journalist, failureFacade,
  workingDirectory, repositoryName, settings)`. Today the S3 provider wraps its client in `CachableS3Client`, a head cache that
  drops mutable keys (`isMutableS3Key`: a `maven-metadata` file or any file inside a `-SNAPSHOT` directory) and that takes the
  related keys of the format through `RelatedKeyAware`. The cache of this change follows the same rule for mutable keys.
- Repository formats hand their related keys to the storage through `format.storageHints.relatedKeys`
  (`RepositoryFactory.kt`), which is a prefix rule of the form `RelatedKeyRule`.
- Plugin jars get their own `URLClassLoader` (`PluginLoader.kt`). Their factories are only visible once
  `add-storage-provider-registration` discovers them, so the cache can build any other provider, including a provider of
  another plugin jar, through the context and not through a direct class reference.
- `StorageProviderSerdes` builds the JSON polymorphism and the schema from the same registry. A nested `delegate` of type
  `StorageProviderSettings` is read by the existing missing-instantiator handler, and the schema of its `oneOf` comes from the
  registry. This design relies on that handler for the nested block.
- The `failureFacade` reports problems (`throwException`, `logger`). A cache problem is reported there and never thrown to the
  request.
- Operators run Ingot in high availability with several replicas on one database. The database holds the locks and counters
  (`2026-08-08-kubernetes-ha-design.md`), and NATS is only an optional fan-out. The cache keeps no rows and uses only the
  invalidation capability on the cluster bus.

## Decisions

### D1: a decorator registered as the type `cache`

`CacheStorageProviderFactory` implements `IngotStorageProviderFactory` with `type = "cache"`, `settingsType =
CacheStorageProviderSettings` and `apiVersion = STORAGE_PROVIDER_API_VERSION` (1), opts in with
`@OptIn(ExperimentalIngotStorageApi::class)`, and is listed in the jar's `META-INF/services/com.reposilite.storage.StorageProviderFactory`.
Its `create` builds the delegate through the `StorageProviderContext` of `add-storage-provider-registration` from the nested `delegate` settings, and
returns a `CacheStorageProvider` that implements `StorageProvider` and the capability interfaces of D9.

- A delegate of type `cache` is refused at creation: the factory throws, so the repository is not loaded and the failure is reported,
  the registration rule for a failing factory. A cache over a cache adds a second disk layer with its own eviction and no
  gain, and the refusal keeps the nesting depth at one.
- A delegate whose type is not installed makes the repository UNAVAILABLE, with the same reason and the same state that core
  uses for a missing storage plugin: `createProvider` throws `StorageProviderNotInstalledException` for the inner type, and core
  handles it as a missing type (`add-storage-provider-registration`, D5 and D7). The cache does not fall back to a provider of its own.
- The wrapped provider is created once per repository. `shutdown()` shuts down the delegate and does nothing else, because the
  cache has nothing to flush.

### D2: settings

`CacheStorageProviderSettings` is a data class with defaults. `type` is always written, as for the other storage types.

| Key | Default | Meaning |
|---|---|---|
| `type` | `cache` | Storage type. Constant. |
| `delegate` | none, required | A nested `StorageProviderSettings` of any registered type except `cache`. Its `@Sensitive` fields stay masked. |
| `directory` | `""` | Cache directory. `""` means `<workingDirectory>/cache/<repositoryName>`. Must be local to the replica. |
| `maxSize` | `5GB` | Total size of cached files. Binary units `B`, `KB`, `MB`, `GB`, `TB`, case insensitive. A value that does not parse fails the start of the repository. |
| `maxFileSize` | `""` | Largest file that is cached. `""` means no limit. Same units as `maxSize`. Larger files are always served from the delegate. |
| `cacheOnUpload` | `false` | Copy an accepted upload into the cache. |
| `cacheMutable` | `false` | Reserved. `true` is refused at start (open question 5). |

Example:

```json
{"type":"cache","delegate":{"type":"s3","bucketName":"maven-releases"},"maxSize":"20GB"}
```

Settings are per repository, in the storage block of `configuration.shared.json`, like every other storage type. The cache adds
one key, `type: cache`, and no environment variable. An existing configuration without the type is read as before.

The dashboard form renders `delegate` as a nested `oneOf` generated from the registry. The schema excludes `cache` from its
choices. The form is checked for rendering in D10.

### D3: on-disk layout, index and lock

- Files live at `<directory>/data/<location>`, with the repository-relative path of the location. `Location` already rejects
  `..` and illegal characters, so a location cannot leave the directory.
- Temporary fills live in `<directory>/fill/`. Start-up removes everything in that folder.
- The in-memory index maps a location to its size, its last access (taken from the injected `Clock`) and its pin count. It is
  rebuilt at start by walking `data/`. The last access of a rebuilt entry is the file's modification time, which a hit also
  updates. No database row is written.
- `<directory>/.lock` is held with an exclusive `FileChannel` lock for the life of the provider. A second process that finds
  the lock taken makes the factory throw with a message that names the directory, so its repository is not loaded. On network file systems that do not honour
  locks, this check is weak, and the docs say so (D12).

### D4: reads

- **Hit.** A cached location is served from `data/` with a `FileChannel`. The index entry is pinned while the stream is open and
  unpinned when it closes. The access time of the entry is updated.
- **Full-file miss.** The provider takes the single-flight entry for the location (a map of in-flight `CompletableFuture`s).
  The first request asks the delegate for the size, opens the delegate stream and copies it into `fill/<random>`. When the copy
  is complete and its byte count equals the delegate's size, the file is renamed atomically into `data/`, the index entry is
  added (evicting first, D7) and the file is served. Concurrent requests for the same location wait on the same future and
  then read the file.
- **Size mismatch.** A copied byte count that differs from the delegate's size deletes the temporary file, removes any entry for
  the location and serves the request from a new delegate stream. The mismatch is reported through `failureFacade`.
- **Generation check.** Each location has a generation counter. An invalidation (D6) increments it. A fill commits only when the
  generation it started with is still current, so a fill that was overtaken by an overwrite does not store old bytes.
- **Ranged hit.** The byte range is applied with the file channel position, so no byte before the offset is read.
- **Ranged miss.** The request is passed to the delegate's ranged `getFile(location, offset, length)` and nothing is cached
  (open question 1).
- **Fill failure.** Any `IOException` during a fill (disk full, permission, short read) deletes the temporary file and serves the
  request from the delegate. The failure goes to `failureFacade`. A read never fails because of the cache.
- **Missing file on hit.** A cached file that cannot be opened (deleted by an operator or by a rebuild) drops its entry and serves
  the request from the delegate.
- Requests for a location whose size is larger than `maxFileSize` skip the cache entirely and go to the delegate.
- A location that is mutable (D9) skips the cache entirely.

### D5: writes are write-through

- `putFile` writes to the delegate first. When `cacheOnUpload` is `false`, the stream is passed through untouched.
- When `cacheOnUpload` is `true`, the stream is wrapped in a tee that copies every byte the delegate reads into a temporary file in
  `fill/`. The copy is committed to `data/` only when the delegate returned success, the tee reached end of stream and the file
  is within `maxFileSize`. A delegate failure deletes the temporary file, and the write fails as it would without the cache.
- A tee failure (disk full) stops the copy, deletes the temporary file, keeps the delegate write running and reports the failure.
  The cache never fails a write, and the write never waits for the cache.
- The local entry for the location is dropped after a successful delegate write, whether or not `cacheOnUpload` is on, because the
  overwrite may replace a release (D6). A new copy, when committed, is committed after that drop.

### D6: invalidation

- `removeFile` removes the file at the delegate first. On success it drops the local entry and the entries of the related keys
  (the prefixes of the format's `RelatedKeyRule`), increments the generation counters, and publishes the invalidation.
  A failed removal publishes nothing, because the delegate still has the file.
- An overwrite is handled the same way as a removal of the old version.
- `CacheStorageProvider` implements `ClusterCacheAware`. `add-storage-capability-hooks` gives `ClusterBus` no member and publishes
  nothing (its open questions 1 and 3), so this change adds one experimental member to `ClusterBus`,
  `publishInvalidation(repository: String, keys: Collection<String>)`, and the cluster plugin's implementation that delivers it to
  `invalidate(keys)` of the same repository's provider on every other replica. Only the cache calls it, so a plain `s3` repository
  receives no message and its 300-second bound stays as it is. Without a cluster bus the cache is never bound and publishes nothing.
  Its invalidation message carries the written location and its related prefixes. A replica that receives a message drops the matching local entries and increments their generation counters, the
  same way as the replica that wrote.
- An invalidation message may be lost, and a replica may be down when it is sent. The cache then serves the old copy until eviction
  or a restart. This is why D10 warns about redeployment, and why the docs say that a cache does not suit a repository that
  replaces releases.
- Invalidation is idempotent. A message that arrives twice, or for an entry that is not cached, changes nothing.

### D7: eviction and limits

- The cache evicts the least recently used entry (by the injected `Clock` at the last access) when a new fill would exceed
  `maxSize`. Eviction runs before the new file is renamed into `data/`.
- An entry with a pin count above zero (an open stream) is never evicted. When no entry can be evicted, the fill is skipped and
  the request is served from the delegate. The skip is counted, not logged for each request (open question 8).
- A file larger than `maxSize` is never cached.
- The limit never blocks a write, a read or a removal. The cache is at most over its limit for the duration of a single fill.

### D8: failure isolation

Every filesystem operation of the cache runs behind one catch-all in the read and write paths, reports through `failureFacade`
and falls back to the delegate. The file operations are behind a small interface (`CacheFileStore`) so that a unit test can
inject a disk-full or permission failure without a real disk. A cache failure never reaches the caller as an error response.

### D9: mutable keys and capabilities

- The mutable rule moves from the S3 package into `com.reposilite.storage` as the public function `isMutableStorageKey`
  (`refactor(storage)`). Its body is unchanged, so the S3 client keeps its behaviour, and its existing tests pin that.
- The cache applies `isMutableStorageKey` and, on top of it, the index files of the format (`archetype-catalog.xml` with its
  checksums, and anything under `.index/`). The extra names belong to the cache only (open question 6).
- Mutable keys go to the delegate for every operation. They are never stored in `data/`.
- Capabilities. `CacheStorageProviderSettings` implements `SharedNamespaceAware` and `SupportDetails`, because
  `add-storage-capability-hooks` puts both on the settings type: `namespaceKey()` and `overlaps` return the delegate settings' answer,
  so a cache over `s3` and a plain `s3` repository on the same bucket and prefix still collide. `describe()` returns the delegate
  settings' details. `CacheStorageProvider` implements `SupportDetails` for the counters of D11, which the support bundle adds after
  the settings' entries (`add-storage-capability-hooks`, D8), and forwards `ReadinessProbe` to the delegate. It forwards
  `RelatedKeyAware.useRelatedKeys` to the delegate too, so that a delegate with its own head cache keeps its invalidation rule,
  and it keeps a copy of the rule for its own invalidations (D6). It implements `ClusterCacheAware` for its own invalidations.
- `StorageEntrySource` is not implemented by the cache. The entry source is the delegate's, as `entrySource(failureFacade)`
  resolves it for the delegate, so that the listing order and the cursors are the delegate's.

### D10: start-up and dashboard checks

- Start-up deletes everything in `fill/`, rebuilds the index from `data/`, evicts until the total is within `maxSize`, and takes
  the lock (D3). Start-up reads no database row and makes no delegate request.
- A `cacheMutable: true` value, an unparsable size or a delegate of type `cache` makes the factory throw with a message that names
  the key, so the repository is not loaded. A delegate type that is not installed leaves the repository UNAVAILABLE with the core
  reason for the inner type (D1).
- When the repository allows redeployment, start logs one warning that a replaced release can be served from a cache until its
  entry is invalidated or evicted. The warning names the repository and does not repeat per request.
- The dashboard schema test asserts that the delegate choices are the registered types except `cache`, and that the nested
  `oneOf` renders in the form. If JsonForms does not render the nested `oneOf` for the delegate, the form falls back to the raw
  JSON editor for that field, and the gap is reported in the pull request, not hidden.

### D11: observability

`CacheStorageProvider` counts hits, misses, fills, skipped fills, fill failures, evictions and bytes. The counters and the current
size are part of the support details of the repository, through `SupportDetails` on `CacheStorageProvider`. When a metrics facade of `add-cluster-metrics` is
loaded, the same counters are published through it. Without it, no metrics code runs. The counters are per replica.

### D12: high availability and operator guidance

- One cache per replica. The cluster bus carries invalidations only, never file bytes. A shared volume for `directory` is refused by
  the documentation: the lock is the only guard, and it is weak on network file systems.
- A cache does not help when the store is already local. The docs say so, as Artifactory does.
- A repository that replaces releases needs redeployment disabled, or it needs the operator to accept a stale copy for as long as an
  invalidation is lost.

## Risks

- A lost invalidation serves an old release until eviction. This is the main risk and it is accepted, because release artifacts
  are immutable and redeployment is a deliberate operator choice. The warning and the docs make it visible.
- The tee of an upload adds a copy of the write path when `cacheOnUpload` is on. It is off by default for that reason.
- A large `maxFileSize` with a small `maxSize` skips most fills silently. The support details show the skipped count so that the
  operator can see it.
- The dashboard nested `oneOf` depends on `add-storage-provider-registration`. If the form cannot render it, the operator writes
  the JSON directly.

## Test strategy

Every test follows F.I.R.S.T. The clock is a fixed or stepped `Clock` passed to the provider. Latches replace sleeps in the
single-flight tests. The file store interface and the in-memory fake delegate make the disk-full and failure cases deterministic.
Each test has its own `@TempDir`. The storage contract suite runs the cache over a file system delegate in unit tests. The S3
integration test runs through `heavy` with `--max-workers=2`.

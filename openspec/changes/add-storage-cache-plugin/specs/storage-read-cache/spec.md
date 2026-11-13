# Spec Delta

## Purpose

An operator can put a per-replica local disk cache in front of any remote storage provider. The cache is a decorator selected
per repository with the type `cache`. It serves full-file reads of immutable release artifacts from local disk, and it sends
every write, every removal, all metadata and all mutable keys to the delegate, which stays authoritative. A cache failure never
fails a request.

## ADDED Requirements

### Requirement: Registration and distribution

The plugin SHALL register the storage type `cache` through `IngotStorageProviderFactory` of `add-storage-provider-registration`, with the
API version `STORAGE_PROVIDER_API_VERSION` (1). It SHALL NOT be part of the image and SHALL be published as
`net.onelitefeather.ingot:storage-cache-plugin`, installed by hand into `plugins/`. A repository SHALL use the cache only
when its storage block has `"type": "cache"`.

#### Scenario: Default installation

- **WHEN** no repository has the type `cache`
- **THEN** the plugin registers its type and no repository uses a cache, and every existing storage configuration reads as before

### Requirement: Settings and setting errors

The settings SHALL carry the keys `delegate`, `directory`, `maxSize`, `maxFileSize`, `cacheOnUpload` and `cacheMutable`, each with the
default of the design, and `type` SHALL always be written. A setting error SHALL make the factory throw, so that the repository is not
loaded and the failure is reported; only a delegate type that is not installed SHALL leave the repository `UNAVAILABLE`.

#### Scenario: Minimal settings

- **WHEN** the storage block is `{"type":"cache","delegate":{"type":"s3","bucketName":"maven"}}`
- **THEN** the cache uses the default directory `<workingDirectory>/cache/<repositoryName>`, the size limit `5GB`, no file size limit,
  `cacheOnUpload` `false` and `cacheMutable` `false`

#### Scenario: Delegate of the cache type

- **WHEN** the delegate has the type `cache`
- **THEN** the repository is not loaded and the reported failure names the nested type

#### Scenario: Delegate not installed

- **WHEN** the delegate has a type that no installed plugin registers
- **THEN** the repository is UNAVAILABLE with the same reason and state that core uses for a missing storage plugin

#### Scenario: Mutable caching requested

- **WHEN** `cacheMutable` is `true`
- **THEN** the repository is not loaded and the reported failure names the key `cacheMutable`

#### Scenario: Unparsable size

- **WHEN** `maxSize` or `maxFileSize` is not a number followed by one of `B`, `KB`, `MB`, `GB` or `TB`
- **THEN** the repository is not loaded and the reported failure names the key and the value's form, not a secret

#### Scenario: Dashboard form

- **WHEN** the dashboard renders the storage block of a repository with type `cache`
- **THEN** the `delegate` field offers the registered storage types except `cache`, and the choice of a type shows that type's fields

### Requirement: Mutable keys bypass the cache

The cache SHALL send every operation on a mutable key to the delegate and SHALL never store a mutable key in its directory. A key is
mutable when `isMutableStorageKey` returns true, or when it is an index file of the format (`archetype-catalog.xml` with its
checksums, or anything under `.index/`).

#### Scenario: Metadata read

- **WHEN** a client reads `org/example/app/maven-metadata.xml`
- **THEN** the delegate serves the read and the cache directory gains no file

#### Scenario: Snapshot file read

- **WHEN** a client reads a file inside a `-SNAPSHOT` directory
- **THEN** the delegate serves the read and the file is not stored in the cache

#### Scenario: Checksum of metadata

- **WHEN** a client reads `maven-metadata.xml.sha1` of an artifact
- **THEN** the delegate serves the read and the file is not stored in the cache

### Requirement: Full-file reads

The cache SHALL serve a full-file read of an immutable location from its local copy when one exists. On a miss it SHALL fetch the file
from the delegate once, store it atomically and serve it from the stored copy.

#### Scenario: Hit

- **WHEN** an immutable location is cached and a client reads it
- **THEN** the bytes come from the local file and the delegate receives no read request for them

#### Scenario: Miss fills the cache

- **WHEN** an immutable location is not cached and a client reads it
- **THEN** the cache copies the delegate's bytes into a temporary file in the cache directory, renames the file into the data directory
  after the copy is complete, and serves the request

#### Scenario: Concurrent misses share one fetch

- **WHEN** several clients read the same uncached immutable location at the same time
- **THEN** the delegate receives one read request for that location, and every client receives the same bytes

#### Scenario: Size mismatch

- **WHEN** the copied byte count of a fill differs from the size the delegate reports
- **THEN** the temporary file is deleted, the entry is removed, the request is served from a new delegate stream, and the failure is
  reported through the failure facade

#### Scenario: Overtaken fill

- **WHEN** an overwrite or a removal of the location happens while a fill of its previous version is in progress
- **THEN** the fill does not commit its bytes to the cache and the request is served from the delegate

#### Scenario: Cached file vanished

- **WHEN** a cached file cannot be opened on a hit
- **THEN** its entry is removed and the request is served from the delegate

### Requirement: Ranged reads

A ranged read of a cached location SHALL be served from the local file at the requested offset without reading the bytes before it. A
ranged read of an uncached location SHALL be passed to the delegate's ranged read, and it SHALL NOT start a fill.

#### Scenario: Range on hit

- **WHEN** a client reads bytes 100 to 199 of a cached location
- **THEN** the cache returns those 100 bytes from the local file

#### Scenario: Range miss passes through

- **WHEN** a client reads bytes 100 to 199 of an uncached location
- **THEN** the delegate's ranged read serves the request, and the cache directory gains no file for that location

### Requirement: Size and file limits

A location larger than `maxFileSize`, when that limit is set, SHALL NOT be cached and SHALL be served by the delegate. A file larger than
`maxSize` SHALL NOT be cached.

#### Scenario: File above the file size limit

- **WHEN** a location is larger than `maxFileSize`
- **THEN** every read of it goes to the delegate and the cache directory gains no file for it

### Requirement: Writes are write-through

A write SHALL go to the delegate first. The cache SHALL copy the written file only after the delegate reports success, and only when
`cacheOnUpload` is `true`. The cache SHALL never hold a file that the delegate does not hold, and it SHALL NOT write back.

#### Scenario: Write without upload caching

- **WHEN** `cacheOnUpload` is `false` and a client uploads a file
- **THEN** the delegate stores the file, and the cache holds no copy of it

#### Scenario: Write with upload caching

- **WHEN** `cacheOnUpload` is `true` and a client uploads a file
- **THEN** the delegate stores the file first, and after its success the cache commits the copy made during the upload

#### Scenario: Failed delegate write

- **WHEN** the delegate fails a write
- **THEN** the temporary copy is deleted, the cache holds no entry for the location, and the client receives the delegate's error

#### Scenario: Cache failure during an upload

- **WHEN** the disk of the cache is full during an upload with `cacheOnUpload` set
- **THEN** the upload completes at the delegate, the copy is discarded, and the failure is reported through the failure facade

### Requirement: Removal and overwrite invalidate

A removal or an overwrite of a location SHALL remove the file at the delegate first. On success it SHALL drop the local entry of the
location and of its related keys, and SHALL publish an invalidation through the `ClusterBus` publishing member that this change adds, so
that every other replica drops its copy through `ClusterCacheAware.invalidate`. Only the cache SHALL publish, so a repository of another
type SHALL receive no invalidation message. A failed removal SHALL publish nothing.

#### Scenario: Removal

- **WHEN** a client removes a cached release file
- **THEN** the delegate removes the file, the local entry is dropped, and the other replicas receive an invalidation for the location

#### Scenario: Overwrite

- **WHEN** a repository with redeployment enabled replaces a cached release file
- **THEN** the local entry is dropped even when `cacheOnUpload` is `false`, and the other replicas receive an invalidation

#### Scenario: Invalidation from another replica

- **WHEN** a replica receives an invalidation for a location that it caches
- **THEN** it drops the entry and the next read is served from the delegate

#### Scenario: Repeated invalidation

- **WHEN** the same invalidation is received twice, or for a location that is not cached
- **THEN** nothing changes and no error is reported

#### Scenario: Lost invalidation

- **WHEN** a replica is down when an invalidation is sent
- **THEN** that replica serves its old copy until the entry is evicted or the cache directory is cleared, and the repository's start log
  has warned about that when redeployment is allowed

### Requirement: Eviction and the size limit

The cache SHALL evict the least recently used entry, by its last access time from the injected clock, before a new file is committed when
the total would exceed `maxSize`. An entry in use by an open stream SHALL NOT be evicted. The size limit SHALL NOT block a write, a read or
a removal.

#### Scenario: Least recently used entry is evicted

- **WHEN** the cache is full and an entry is read after two others, and a new file is committed
- **THEN** the entry that was read least recently is evicted and the others remain

#### Scenario: All entries in use

- **WHEN** every cached entry has an open stream and a new fill would exceed `maxSize`
- **THEN** the fill is skipped, the request is served from the delegate, and the skipped fill is counted

#### Scenario: Limit does not block writes

- **WHEN** the cache is at its limit and a client uploads a file
- **THEN** the upload succeeds at the delegate and the cache does not refuse it

### Requirement: Failures never fail a read

A failure of the cache's directory, of its disk or of its temporary files SHALL be reported through the failure facade, and the request
SHALL be served from the delegate. The cache SHALL NOT return an error response that the delegate would not have returned.

#### Scenario: Disk full during a fill

- **WHEN** the disk is full while a fill copies bytes
- **THEN** the temporary file is deleted, the request is served from the delegate, and the failure is reported

#### Scenario: Permission failure on a hit

- **WHEN** the cache directory cannot be read for a cached file
- **THEN** the request is served from the delegate and the entry is dropped

### Requirement: Metadata and listings come from the delegate

`getFileDetails`, `exists`, `getFileSize`, `getLastModifiedTime`, `getFiles`, `usage` and `canHold` SHALL be answered by the delegate.
The cache SHALL NOT implement `StorageEntrySource`, so that listings use the delegate's entry source.

#### Scenario: File size of a cached file

- **WHEN** a client asks for the size of a cached file
- **THEN** the delegate answers, so that the value is the delegate's

#### Scenario: Usage

- **WHEN** the usage of the repository is requested
- **THEN** the delegate's usage is returned, and the size of the cache is reported only in the support details

### Requirement: Start-up

At start the cache SHALL delete everything in its temporary folder, SHALL rebuild its index from the data directory without database
rows and without a delegate request, SHALL evict until its total is within `maxSize`, and SHALL hold an exclusive lock on its directory.
A second process that cannot take the lock SHALL NOT load its repository, and the reported failure SHALL name the directory.

#### Scenario: Index rebuild

- **WHEN** the process starts with a data directory that holds cached files
- **THEN** every file is indexed with its size and its modification time as last access, and the first read of a file is a hit

#### Scenario: Leftover temporary files

- **WHEN** the process starts and the temporary folder holds files from an earlier run
- **THEN** those files are removed before the first request

#### Scenario: Lock taken

- **WHEN** another process holds the lock of the cache directory
- **THEN** the repository is not loaded and the reported failure names the directory

### Requirement: Capabilities

The cache settings SHALL forward `SharedNamespaceAware` and `SupportDetails` to the delegate settings. The cache provider SHALL forward
`ReadinessProbe` and `RelatedKeyAware` to its delegate, SHALL implement `SupportDetails` for its counters, and SHALL implement
`ClusterCacheAware` for its own invalidations.

#### Scenario: Shared namespace of a cached delegate

- **WHEN** a cache repository over `s3` and a plain `s3` repository use the same bucket and prefix
- **THEN** core refuses both, as it would for two plain `s3` repositories

#### Scenario: Plain S3 repository receives no invalidation

- **WHEN** a cache repository publishes an invalidation and a plain `s3` repository exists on the same replicas
- **THEN** the `s3` repository's head cache is not invalidated and keeps its 300-second bound

#### Scenario: Readiness of the delegate

- **WHEN** the delegate reports that it is not ready
- **THEN** the cache reports the same state

#### Scenario: Related keys

- **WHEN** the format of the repository provides a related key rule
- **THEN** the delegate receives the rule and the cache uses the same rule for its own invalidations

### Requirement: Observability

The cache SHALL count its hits, misses, fills, skipped fills, fill failures, evictions and the bytes it holds. These values SHALL appear in
the support details of the repository. When a metrics facade of `add-cluster-metrics` is loaded, the same values SHALL be published through
it, and when none is loaded, no metrics code SHALL run.

#### Scenario: Support details

- **WHEN** the support details of a repository with a cache are requested
- **THEN** they contain the hit count, the miss count, the size of the cache and its limit, and no path outside the cache directory

#### Scenario: No metrics facade

- **WHEN** no metrics facade is loaded
- **THEN** the cache counts its values in memory and publishes nothing

### Requirement: Testing

The unit tests SHALL run without a network, a database, a system clock or a shared disk. They SHALL use an in-memory fake delegate, an
injected clock, a failure-injecting delegate, a file store interface for disk failures, and latches for concurrency. The storage contract
suite SHALL run against the cache over a file system delegate. The integration test SHALL run the cache over the S3 test container, and
it SHALL run through the slot runner.

#### Scenario: Unit suite without sleeps

- **WHEN** the unit suite of the plugin runs
- **THEN** no test waits on time, reads the system clock, or shares state with another test

#### Scenario: Contract suite over the file system

- **WHEN** the storage contract suite runs against the cache with a file system delegate
- **THEN** every case that the plain provider passes, the cache passes as well

#### Scenario: Integration over S3

- **WHEN** the integration test runs with the S3 test container
- **THEN** a removal through one replica's cache makes the other replica's next read go to the delegate

### Requirement: Plugin store distribution

The plugin SHALL be listed in the plugin store of `add-plugin-store` as a signed catalogue entry, selectable with `INGOT_PLUGINS` or in the dashboard. A jar installed by hand in `plugins/` SHALL win over a store entry with the same id.

#### Scenario: Plugin selected from the store

- **WHEN** the store entry is selected with `INGOT_PLUGINS` or in the dashboard and a repository sets `type` to `cache`
- **THEN** the repository uses the provider of this plugin, as when the jar is installed by hand

#### Scenario: Manual jar and store entry share an id

- **WHEN** the jar is installed by hand in `plugins/` and a store entry with the same id is selected
- **THEN** the manual jar is loaded and the store entry is listed as shadowed

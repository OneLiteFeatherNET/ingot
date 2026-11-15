# Design

## Context

`CachableS3Client` wraps the S3 client and caches `headObject` answers in a Caffeine cache built by
`S3StorageProviderFactory`. The cache exists only when `metadataCacheSettings.enabled` is true. Each repository builds its own
cache, and the cluster bus opens after the server has started (`ClusterCacheHooks.clusterBusOpened`). The Maven resolution
cache already gets the same treatment: it has a maximum age, set to 300 seconds once a bus is bound, through
`RepositoryProvider.bindToClusterBus`. This change follows that pattern.

## Decisions

### Mutable keys are recognised by name

A key is mutable when its file name starts with `maven-metadata`, or when a directory segment ends with `-SNAPSHOT`. The
prefix covers the XML, its checksums and `maven-metadata-local.xml`. An allow-list of suffixes would miss the next sidecar
the repository manager writes. The check is a pure function of the key, so it is tested without a cache.

### The bus bound is a variable expiry, not a rebuilt cache

The cache keeps its configured creation and access expiry, and adds a volatile maximum age that the expiry reads on every
write and read: `min(configured, cap)` when a bus is bound, the configured value otherwise. Rebuilding the cache when the bus
opens would drop every entry and would need a path back to the repository. The read does not renew the bound: the cap is
measured from the store time, so a steady stream of reads cannot keep an answer past 300 seconds.

The binding reaches the live caches the way the resolution cache does, from the cluster hook. Where the cap lives between the
factory and that hook is open for the apply step (a holder handed to the factory, or the list of built providers that
`RepositoryProvider` already keeps). It must not be a static field, so that tests stay independent.

### The cap is fixed, not a new setting

The bound is 300 seconds, the same value as `CLUSTER_RESOLUTION_MAX_AGE_SECONDS`, and it is a constant in the storage package.
A setting would be a new key for a value that operators have no reason to tune, and the drop-in rule prefers no new key.

### SDK full jitter instead of an injectable jitter

`ReconnectBackoff` takes its jitter as a function so that tests can replace it. The SDK's `FullJitterBackoffStrategy` draws
from its own random source, which cannot be replaced. The proposal therefore asserts bounds over many draws instead of exact
delays. A bound holds on every draw, so the test is repeatable, and the team keeps one maintained schedule instead of a second
copy. The alternative, a copy of the SDK class with an injected random, was rejected because it recreates the code this
change removes.

## Not decided here

- Whether `STORAGE_CHANGED` should drop entries on other instances. The five-minute bound makes it unnecessary for correctness.
  It can be added later without changing the rules above.

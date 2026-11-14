# Design

Paths are relative to `reposilite-backend/src/main/kotlin/` on the current main branch, with `add-storage-provider-registration`
applied. That change lets a plugin factory reach `StorageFacade` and `StorageProviderSerdes`.

## Context

The concrete checks that core makes today, and what each protects:

| Where | Check | What it protects |
| --- | --- | --- |
| `com/reposilite/maven/RepositoryFactory.kt:74` | `storageProvider as? RelatedKeyAware` | The repository format hands its related keys (for Maven, `maven-metadata.xml` and its checksums) to the head cache. Without it a plugin keeps the Maven default. |
| `com/reposilite/maven/RepositoryProvider.kt` `bindToClusterBus` | `as? S3StorageProvider` | Binds the head cache to the cluster bus, so an answer lives at most 300 seconds. Mutable keys are never cached. |
| `com/reposilite/maven/RepositoryProvider.kt` `apply` | `findS3SharedBucketConflicts` on S3 settings | Two repositories must not share one bucket and key namespace. |
| `com/reposilite/maven/StorageUsageService.kt` `storageTargets` and `quotaBytes` | `as? FileSystemStorageProviderSettings`, `as? FileSystemStorageProvider` | Quota is read from fs settings, and a percentage quota needs the fs root. An unknown type falls back to `"fs"`. |
| `com/reposilite/maven/StorageUsageService.kt` `applyReceived` | `?: "fs"` | A received figure for an unconfigured repository is reported as fs. This is the bug the brief names. |
| `net/onelitefeather/ingot/support/SupportPlugin.kt` `repositoryStorage` | `as? S3StorageProviderSettings` for the region | The support bundle shows the S3 region. |
| `com/reposilite/status/StorageReadiness.kt` and `StatusPlugin` | `answersListing` on `getFiles(root)` | Readiness counts any NOT_FOUND as "the backend answered". |

Two facts shape the design.

- The S3 403-to-404 mapping (`S3StorageProvider.kt` `s3Error`) turns an auth failure into not found. The readiness probe cannot distinguish the two until that mapping changes, which is out of scope here.
- The HEAD cache of S3 is bound to the cluster by a maximum age only. There is no invalidation message between replicas today (`CachableS3Client.kt`, `bindToClusterBus`). The `invalidate` callback is added, but the cluster does not yet publish to it (open question 1 in `proposal.md`).

## Decisions

### D1: the package, the marker and the interfaces

All new types live in `net.onelitefeather.ingot.storage.capability` and carry `@ExperimentalIngotStorageApi`. The marker is the opt-in annotation of the storage SPI from `add-storage-provider-registration`, declared there as `net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi`. This change imports it and does not declare a second marker. Core opts in where it calls the interfaces.

```kotlin
package net.onelitefeather.ingot.storage.capability

import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi

@ExperimentalIngotStorageApi
interface ClusterCacheAware {
    fun bind(clusterBus: ClusterBus, maxAge: Duration)   // called once the cluster bus is open
    fun invalidate(keys: Collection<String>)              // called for writes made by another replica
}

@ExperimentalIngotStorageApi
interface ClusterBus                                     // a narrow port, implemented by the cluster plugin

@ExperimentalIngotStorageApi
interface RelatedKeyAware {
    fun useRelatedKeys(rule: RelatedKeyRule)
}

@ExperimentalIngotStorageApi
interface UsageCapability {
    val reportsUsage: Boolean        // true: usage() and canHold() answer real figures
    val supportsQuota: Boolean       // true: a configured quota is reported; enforcement stays fs-only in this change
    fun totalCapacityBytes(): Long? = null   // base for a percentage quota; null when unknown
}

@ExperimentalIngotStorageApi
interface ReadinessProbe {
    fun probe(): ReadinessOutcome
}

@ExperimentalIngotStorageApi
sealed interface ReadinessOutcome {
    data object Ok : ReadinessOutcome
    data object NotFoundOk : ReadinessOutcome   // the backend answered, the root is empty or missing
    data class Failed(val reason: String) : ReadinessOutcome
}

@ExperimentalIngotStorageApi
interface SupportDetails {
    fun describe(): Map<String, String>        // non-sensitive; the support redaction still runs over the result
}

@ExperimentalIngotStorageApi
interface SharedNamespaceAware {
    fun namespaceKey(): String?                 // null: this configuration has no shared namespace to check
    fun overlaps(other: SharedNamespaceAware): Boolean = namespaceKey() == other.namespaceKey()
    fun overlapMessage(): String =
        "Its storage namespace overlaps another repository sharing the same storage location. Give each repository a distinct namespace."
}

@ExperimentalIngotStorageApi
interface QuotaSettings {
    val quota: String?                          // as configured, for example "100%" or "10GB"
}
```

`RelatedKeyRule` keeps its current type. `Duration` is `java.time.Duration`.

### D2: which object implements which interface

The rule is that a capability sits on the object that holds the information.

| Interface | Implemented by | Why |
| --- | --- | --- |
| `SharedNamespaceAware` | settings type | The namespace is a function of configuration (`resolveKeyPrefix`). It is known before a client exists, and `RepositoryProvider` checks it before it builds any provider. |
| `SupportDetails` | settings type | The support bundle reads settings and never builds a provider. |
| `QuotaSettings` | settings type | Quota is a configured value. |
| `ClusterCacheAware`, `RelatedKeyAware`, `UsageCapability`, `ReadinessProbe` | provider | Each needs live state: a cache, a client, a root directory or a listing. |

A plugin's settings type therefore implements the namespace and support interfaces, and its provider implements the rest. The brief said "providers opt in". This is the one place where the decision is made on a different object, and open question 2 records it.

### D3: cluster-bound head cache

`RepositoryProvider.bindToClusterBus` no longer tests `is S3StorageProvider`. It calls `(provider as? ClusterCacheAware)?.bind(clusterBus, maxAge)` for every repository. The `as?` is on the capability, not on the class.

- S3 implements `ClusterCacheAware` by delegating `bind` to `bindToClusterBus()` on `CachableS3Client`. The 300-second cap, the reset of answers stored before the bind, and the behaviour of reads stay as they are.
- `invalidate(keys)` calls the same `invalidate(key)` logic that `putObject`, `deleteObject` and `completeMultipartUpload` already use, including related keys. Nothing calls it from the cluster in this change.
- `maxAge` defaults to `CLUSTER_HEAD_CACHE_MAX_AGE_SECONDS` (300 seconds), as today.
- A fs provider does not implement `ClusterCacheAware`, which matches today (only S3 is bound).

Tests inject a fake `Ticker` (the existing constructor parameter) to prove the 300-second expiry. No test sleeps and no test reads the system time.

### D4: the shared-namespace check

`RepositoryProvider.apply` replaces the call to `findS3SharedBucketConflicts` with a generic pass:

1. Collect `(id, settings)` for every configured repository whose settings implement `SharedNamespaceAware` and whose `namespaceKey()` is not null.
2. For each pair of different ids, if `a.overlaps(b)` is true, both ids are conflicts.
3. A conflicting id is reported with `failureFacade.throwException`, with the identifier `Cannot load repository '<id>'` and an `IllegalStateException` whose message is `settings.overlapMessage()`.

The S3 settings implement the check as before.

- `namespaceKey()` returns `s3:<trimmed endpoint without trailing slash>/<trimmed bucket>/<resolveKeyPrefix(id)>`, or null when the bucket is blank. The bucket is blank, so the repository never shares a namespace, which is the current `filterNot { bucketName.isBlank() }`.
- `overlaps` is true when both keys have the same endpoint and bucket, and one resolved prefix equals or starts with the other. This is the same rule as today's `collides` expression, moved onto the settings.
- `overlapMessage()` returns the current S3 text exactly: "Its S3 key namespace overlaps another repository sharing the same bucket. Give each repository a distinct 'prefix', or enable 'sharedBucket' on every repository sharing the bucket."
- `findS3SharedBucketConflicts` stays in place as a function that delegates to the generic pass. `S3SharedBucketConflictsTest` runs unchanged.

The namespace key uses the same string shape as the brief example. The key is opaque to core, and the overlap rule belongs to the provider.

### D5: usage and quota

`StorageUsageService` stops using class checks.

- `storageTargets` reads the quota through `QuotaSettings`, and only when the provider's `UsageCapability.supportsQuota` is true. Groups still have no quota.
- The provider's `UsageCapability` gives `reportsUsage` and `supportsQuota`. A provider that does not implement `UsageCapability` is treated as `reportsUsage = true`, `supportsQuota = false`. This matches the old behaviour for a type core does not know about, because no quota was ever read for it.
- `quotaBytes` for a percentage quota calls `totalCapacityBytes()` of the provider. `FileSystemStorageProvider` implements it as `rootDirectory.fileStore().totalSpace`, which is the same figure as today. A fixed size is still read by `FileSystemStorageProviderFactory.displaySizeToBytesCount`.
- `reportsUsage` decides whether `Repository.usage()` and `Repository.canHold()` answers are shown as figures. fs keeps its figures, and s3 keeps `usage` at -1 and `canHold` at `Long.MAX_VALUE`. The walk that measures used bytes runs for every provider, as today.
- Enforcement is unchanged: `canHold` and the upload path check quota for fs only, as they do today. Nothing new enforces a quota.

The snapshot's `storageType` is the configured type of the repository's settings. When no settings exist, it is `unknown`, not `fs` (open question 8).

### D6: the real type in the snapshot and the cluster event

- `storageTargets` keeps `settings?.storageProvider?.type`, and the default becomes `"unknown"`.
- `applyReceived` (a figure from another replica) uses `event.repositories[i].storageType`, and falls back to `"unknown"` when the repository is not configured here. The fallback to `"fs"` is removed.
- `StorageUsageFigure` gains `storageType: String = "unknown"`. The default lets a payload from an older replica decode. `compute` sets the field from the target. The event class lives in `com/reposilite/plugin/api/ClusterEvents.kt`, which is part of the plugin API, so the field is additive and the gate of `gate-plugin-abi` records it.

### D7: readiness

`StorageReadiness.kt` keeps `answersListing` for the existing tests and gains the default of the probe:

```kotlin
// default body of ReadinessProbe, applied through StorageProvider
override fun probe(): ReadinessOutcome = getFiles(Location.empty()).fold(
    { ReadinessOutcome.Ok },
    { error -> if (error.status == NOT_FOUND.code) ReadinessOutcome.NotFoundOk else ReadinessOutcome.Failed(error.message) }
)
```

- The default cannot be a member of `StorageProvider` because the capability is opt-in. The default is a helper `probeListing(provider)` that a provider calls to implement `ReadinessProbe`, and the status plugin calls `probe()` when the provider implements `ReadinessProbe`, and `probeListing` otherwise.
- Plugin providers map their own failures onto `Failed(reason)`, for example an authorization failure or a missing bucket or container. `NotFoundOk` is only for a backend that answered that the root has no entries.
- `StatusPlugin` treats `Ok` and `NotFoundOk` as answered, and `Failed` as not answered. Its output does not change for fs or s3.
- The reason in `Failed` is the error message, passed through the existing redaction of the status output.

### D8: the support bundle

`RepositoryStorage` gains `details: Map<String, String>`. `SupportPlugin.repositoryStorage` fills it from `(settings as? SupportDetails)?.describe().orEmpty()`, and then adds the entries of `(provider as? SupportDetails)?.describe()` when the repository's provider is built and implements the interface (open question 2: a provider may implement a settings capability too). A key from the settings wins over the same key from the provider. fs and s3 providers do not implement it, so their output is unchanged.

- `region` is kept with its meaning: for S3 it is `describe()["region"]`, and for everything else it is null. The field stays in the bundle so that a parser of today's bundle still reads it.
- S3 `describe()` returns `region` only when it is not blank. fs returns an empty map. Both match today's output.
- The support redaction still runs over the bundle, so a plugin cannot leak a secret through `describe()`.

### D9: the old name of `RelatedKeyAware`

The JVM name `com.reposilite.storage.s3.RelatedKeyAware` is referenced by compiled plugins and by the existing code. The declaration moves to the capability package, and the old name stays:

```kotlin
package com.reposilite.storage.s3

@Deprecated("Use net.onelitefeather.ingot.storage.capability.RelatedKeyAware", ReplaceWith("RelatedKeyAware", "net.onelitefeather.ingot.storage.capability.RelatedKeyAware"))
interface RelatedKeyAware : net.onelitefeather.ingot.storage.capability.RelatedKeyAware
```

A provider that implements the old name also implements the new one, so core's check on the new name finds it. The old name declares no member, so no compiled plugin loses a method. The deprecation warning is the only change for source code.

### D10: tests and fixtures

The tests are written first (red), then the production code (green). Every test follows F.I.R.S.T.: no sleeps, no system clock (a fixed `Clock` and a fake `Ticker`), no shared static state, a `@TempDir` per test, and a message on each assertion.

- **Parity tests** (fs and s3, against the behaviour before this change):
  - shared-bucket conflicts: the exact S3 message for each of the four conflict shapes (equal, nested, sibling, blank bucket); `S3SharedBucketConflictsTest` passes unchanged;
  - usage snapshots: fs with and without a quota, percentage and fixed; s3 with `usage` -1 and no quota;
  - support bundle: the `region` and `details` of an S3 entry and an fs entry;
  - readiness: an empty root, a missing root (NOT_FOUND), and a failing listing for both providers.
- **Fake provider tests**: a fake provider implementing each capability in turn. Each test asserts one behaviour: the namespace check refuses an overlap with the message of the fake; a plugin type appears in the snapshot with its type; an unknown type is `unknown`; a received figure of an unconfigured repository is `unknown`; a provider without `UsageCapability` reports no quota; the cache binds with `maxAge` and invalidates keys; `probe()` returns `Failed` with the reason.
- **Mixed version**: a `StorageUsageFigure` JSON without `storageType` decodes to `unknown`, and a JSON with `storageType` decodes on the decoder of the version before this change. The second half is not assumed: whether the cluster event decoder ignores unknown properties is verified first (task 4.1), and if it does not, the field is not added and the open question 7 default is revisited.
- Integration: `StatusPlugin` and the usage API answer the same as before for the default fs and s3 configuration. The integration suite runs with `heavy ./gradlew :reposilite-backend:integration --tests '*StorageCapab*' --max-workers=2`, targeted, never the full suite.

## Risks

- **Experimental API.** The interfaces are marked and listed as experimental in the upgrade notes. A plugin that implements them before a stable release accepts that they may change.
- **Opt-in mistakes.** A plugin that implements `ClusterCacheAware` is bound only when a cluster bus is open. On a single replica it is never bound and keeps whatever local bound it sets itself, which is no worse than a plugin without the interface. The docs say that a bound cache needs the cluster bus.
- **Silent fallback to the default.** A plugin without `UsageCapability` reports its figures without quota. The snapshot shows `supportsQuota: false` implicitly, and the plugin how-to says to implement the interface.
- **Support detail leakage.** The support redaction runs after `describe()`, but a plugin can still return a secret under an innocent key. The how-to says that `describe()` returns only values that are safe to publish.

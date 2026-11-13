# Proposal

## Open maintainer questions

Each question has the default this change assumes. A different answer changes the design sections named in brackets and nothing else.

1. **Publish cross-replica cache invalidations now?** Default: no. The `invalidate` callback exists and S3 implements it with today's invalidation logic, but the cluster plugin publishes no invalidation message in this change. S3 keeps today's behaviour: a HEAD answer lives at most 300 seconds on a cluster bus. Publishing would make a write on one replica drop the cached HEAD answer on the others at once, which is a visible behaviour change for S3 and belongs in its own change. [D3]
2. **Where do namespace and support hooks sit: settings or providers?** Default: on the settings type. `SharedNamespaceAware` and `SupportDetails` describe configuration that exists before any client is built, and `RepositoryProvider` and the support bundle read settings, not providers. Live-state hooks (`UsageCapability`, `ReadinessProbe`, `ClusterCacheAware`) sit on providers. A provider that needs the namespace or support details can implement them too. [D2]
3. **Shape of the cluster bus handle.** Default: `ClusterBus` is a narrow port in the capability package that the cluster plugin implements. This change adds no member to it. Only the binding is passed, so a later change can add publishing without touching providers. [D3]
4. **Quota enforcement for non-fs providers.** Default: no. Only the reporting is generalised, as the brief asks. Enforcement stays fs-only until a shared counter in the database exists for HA. [D5]
5. **Presigned redirects and WORM retention as capabilities.** Default: not in this change. They are "could" items for a later change. [Out of scope]
6. **Old name of `RelatedKeyAware`.** Default: keep `com.reposilite.storage.s3.RelatedKeyAware` on the JVM as a member-less sub-interface of the new capability interface, deprecated. Removing it would break compiled plugins at link time, so it waits for a major release. [D9]
7. **Additive fields in the support bundle and the cluster usage event.** Default: yes. The support bundle entry gains a `details` map and keeps `region` with its current meaning. The usage event gains `storageType` per figure and reads a missing value as `unknown`, so a replica of an older version still decodes it. [D6, D8]
8. **Type reported when a repository has no settings.** Default: `unknown`, not `fs`. Today a missing entry is reported as `fs`, which hides the real type. For configured fs and s3 repositories the value does not change. [D6]

## Why

Ingot keeps storage behind one interface, `StorageProvider`, and a plugin can already reach it once `add-storage-provider-registration` lands. Behaviour, however, is still chosen by type checks on concrete classes. Core asks `is S3StorageProvider`, `as? FileSystemStorageProviderSettings` and `as? RelatedKeyAware`. A provider from a plugin therefore silently gets none of the behaviour that fs and s3 get: no bound on its cache, no shared-namespace check, no quota or capacity report, no readiness nuance and no support entry. Nothing fails. The plugin simply runs with the Maven defaults and a wrong usage report.

The plugins planned next need exactly this behaviour. An Azure Blob provider has the same namespace problem as S3 (two repositories must not write into one container and prefix). A cache plugin needs its cache bound to the cluster. A Google Cloud Storage provider needs its readiness and support entry to be honest. Each of them would otherwise have to patch core, which defeats the point of a plugin.

The change replaces the type checks with capability interfaces. A provider states what it can do by implementing an interface, and core asks the interface. The behaviour of fs and s3 stays identical, which the parity tests prove.

## What Changes

- **Capability interfaces** in `net.onelitefeather.ingot.storage.capability`, all marked `@ExperimentalIngotStorageApi`, the opt-in marker that `add-storage-provider-registration` declares in `net.onelitefeather.ingot.storage` (no second marker is declared):
  - `ClusterCacheAware`: `bind(clusterBus, maxAge)` and `invalidate(keys)`. S3 implements it with the existing `CachableS3Client` logic.
  - `SharedNamespaceAware`: `namespaceKey()` returns an opaque key, and `overlaps(other)` decides whether two keys collide. The default is key equality. S3 keeps its prefix-nesting rule.
  - `RelatedKeyAware`: the existing interface, moved into the capability package and kept under its old JVM name as a deprecated member-less sub-interface.
  - `UsageCapability`: `reportsUsage`, `supportsQuota` and `totalCapacityBytes()`.
  - `QuotaSettings`: `quota`, implemented by the fs settings. Quota is a setting, so it is read from settings.
  - `ReadinessProbe`: `probe()` returns `Ok`, `NotFoundOk` or `Failed(reason)`. The default is today's listing logic.
  - `SupportDetails`: `describe()` returns non-sensitive key and value pairs for the support bundle.
- **Core uses the interfaces instead of class checks** in `RepositoryFactory`, `RepositoryProvider`, `StorageUsageService`, `StatusPlugin` and `SupportPlugin`. The shared-bucket check becomes the generic namespace check. The S3 conflict messages stay byte-identical.
- **Usage snapshot reports the real type.** A repository whose settings are missing or unknown reports `unknown`, not `fs`. A repository of a plugin type reports its configured type. The cluster usage event gains `storageType` per figure.
- **Quota reporting is generalised, enforcement is not.** The snapshot shows a quota only when the provider supports it and the settings carry one. Percentage quotas are measured against the provider's own `totalCapacityBytes()`.
- **Support bundle.** A repository storage entry gains a `details` map. `region` keeps its meaning for fs and s3.

### Out of scope

- Any new backend. Azure Blob, Google Cloud Storage and the cache are the sibling changes.
- Quota enforcement for non-fs providers. The shared counter for HA is a later change.
- Presigned redirects and WORM retention as capabilities.
- Publishing cross-replica cache invalidations (open question 1).
- Changing the S3 403-to-404 mapping. Until it changes, an S3 readiness probe cannot tell an auth failure from an empty bucket, and this change does not claim it can.
- Any configuration key, environment variable, manifest or volume change.

## Capabilities

### New Capabilities

- `storage-provider-capabilities`: the capability interfaces, the rule that core asks an interface and never a concrete class, the shared-namespace check, the usage report with the real type, the readiness outcomes and the support details.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `feat(storage)` and `docs(storage)`. The pull request title is:

  `feat(storage): replace provider type checks with capability interfaces`

- **Breaking marker.** No breaking change for existing installations. Nothing an operator must change: no configuration key, environment variable, deployment manifest, volume or stored data. The fs and s3 behaviour is identical, and the parity tests assert it. Two outputs change, and neither for a configured `fs` or `s3` repository: the support bundle entry gains `details`, and a repository whose settings are missing on this replica (a received figure of an unconfigured repository) or whose type is a plugin type reports `unknown` or its real type instead of `fs`. Configured `fs` and `s3` repositories report `fs` and `s3` as before. The upgrade notes say so.
- **Binary surface.** The new interfaces are experimental and sit in a new package. `com.reposilite.storage.s3.RelatedKeyAware` stays on the JVM, deprecated. The ABI gate of `gate-plugin-abi` must still pass for `com.reposilite.storage` and its subpackages.
- **Order.** Depends on `add-storage-provider-registration`, so that a plugin factory is found before the capabilities are asked of its provider.

## Verification

- Parity tests: fs and s3 produce the same shared-bucket conflict messages, the same usage snapshot, the same support bundle entries and the same readiness outcome as before this change.
- Unit tests with fake providers that implement each capability, and with a fake `Ticker` and a fixed `Clock`.
- `openspec validate add-storage-capability-hooks --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-storage-capability-hooks` prints nothing.

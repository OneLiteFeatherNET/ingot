# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (a fixed
`Clock` and a fake `Ticker` are injected), no fixed ports, no shared mutable state, a `@TempDir` per test, and assertions with
messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-storage-capability-hooks`: this change and nothing else. Verify:
  `openspec validate add-storage-capability-hooks --strict` passes and the dash grep from the proposal prints nothing.

## 2. Parity baseline (red first, then green)

- [ ] 2.1 `test(storage)`: add parity tests that pin today's output of fs and s3 before any production change: the four S3
  conflict shapes with their exact messages, the usage snapshot of an fs repository with a quota (percentage and fixed) and of an S3
  repository, the support bundle entries of both, and the readiness of an empty root, a missing root and a failing listing. Verify:
  they pass on the unchanged code, which is the baseline.

## 3. Capability interfaces and the registry of behaviour (red, then green)

- [ ] 3.1 `test(storage)`: failing tests with fake providers, one behaviour each: a provider implementing `ClusterCacheAware` is
  bound with its maximum age; a provider without it is not bound; `invalidate` drops a key and its related keys; a percentage quota
  is measured against `totalCapacityBytes()`, and is unknown when that is null; a provider without `supportsQuota` reports no quota;
  `probe()` answers `Failed` with the reason. Verify: they fail on the missing types.
- [ ] 3.2 `feat(storage)`: add the package `net.onelitefeather.ingot.storage.capability`, marked with the existing
  `net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi` of `add-storage-provider-registration`, with `ClusterCacheAware`, `ClusterBus`, `UsageCapability`, `ReadinessProbe` with `ReadinessOutcome`, `SupportDetails`,
  `SharedNamespaceAware`, `QuotaSettings`, and the moved `RelatedKeyAware` (D1, D9). Verify: the tests of 3.1 pass.
- [ ] 3.3 `feat(storage)`: implement the capabilities on the providers and settings: `CachableS3Client` and `S3StorageProvider` for
  `ClusterCacheAware`, `RelatedKeyAware` and `ReadinessProbe`; `S3StorageProvider` for `UsageCapability` with `reportsUsage` false
  and `supportsQuota` false; `S3StorageProviderSettings` for `SharedNamespaceAware` and `SupportDetails`; the filesystem provider for
  `UsageCapability` (`totalCapacityBytes` from the root) and `FileSystemStorageProviderSettings` for `QuotaSettings`. Keep the
  deprecated `com.reposilite.storage.s3.RelatedKeyAware` as a member-less sub-interface (D9). Verify: the parity tests of 2.1 still
  pass.

## 4. Core uses the capabilities (red, then green)

- [ ] 4.1 `test(storage)`: failing tests of the generic namespace check: fake settings with overlapping keys are refused with the
  fake's message; a null key takes no part; `findS3SharedBucketConflicts` gives the same set as the generic check for every S3 case
  of 2.1. Then failing tests of the usage snapshot: a plugin type is reported with its type; an unconfigured repository is `unknown`;
  a received figure without `storageType` decodes to `unknown`; a provider without `UsageCapability` reports no quota. Verify first, as
  its own test: a cluster usage event that carries an extra field decodes on the code before this change (unknown properties ignored).
  If it does not, stop and revisit open question 7 before adding `storageType`. Verify: they fail.
- [ ] 4.2 `feat(storage)`: replace the class checks in `RepositoryFactory` (`RelatedKeyAware`), `RepositoryProvider` (the cluster
  binding and the namespace check), `StorageUsageService` (quota, percentage base, storage type and the `unknown` fallback),
  `StatusPlugin` (`ReadinessProbe` with the listing as default) and `SupportPlugin` (`details` and `region`). Add `storageType` to
  `StorageUsageFigure` with the default `unknown` (D6). Verify: the tests of 4.1 pass and the parity tests of 2.1 pass unedited.

## 5. Build and API surface

- [ ] 5.1 `build(storage)`: add the capability package to the ABI dump of `gate-plugin-abi` where the gate covers it, and record the
  additive `storageType` field of `StorageUsageFigure` in the dump. Verify: `heavy ./gradlew :reposilite-backend:compileKotlin
  --max-workers=2` passes and the API check of the gate passes without other diffs.

## 6. Documentation

- [ ] 6.1 `docs(storage)`: update `docs/gitbook/reference/storage-providers.md` with the capability interfaces, the rule that core asks
  an interface, the namespace check, the quota rule (enforcement fs-only), the `unknown` storage type and the readiness outcomes.
- [ ] 6.2 `docs(storage)`: update `docs/gitbook/reference/plugin-api.md` to list the package `net.onelitefeather.ingot.storage.capability`
  as experimental, with each interface and the deprecated old name of `RelatedKeyAware`.
- [ ] 6.3 `docs(storage)`: update `docs/gitbook/explanation/storage-and-mirror-caching.md` for the cluster-bound head cache and the
  300-second maximum age for any provider that implements `ClusterCacheAware`, and state that no invalidation is published between
  replicas yet.
- [ ] 6.4 `docs(storage)`: add the upgrade note to `docs/gitbook/reference/upgrade-changes.md`: no operator action, the support bundle
  gains `details`, the usage snapshot reports `unknown` instead of `fs` for an unconfigured repository, and the capability package is
  experimental.

## 7. Code pull request

- [ ] 7.1 Open the code pull request under the title `feat(storage): replace provider type checks with capability interfaces`, from a
  branch of the remote main branch that already contains the merged proposal and the merged `add-storage-provider-registration`, with
  no file under `openspec/`.

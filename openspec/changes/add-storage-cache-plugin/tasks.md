# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected), no shared mutable state, a temporary directory per test (`@TempDir`), latches instead of timeouts for the
single flight, and assertions with messages. Unit tests run as `:reposilite-plugins:storage-cache-plugin:test --tests '<pattern>'`,
the integration test as `heavy ./gradlew :reposilite-plugins:storage-cache-plugin:integration --tests '<pattern>' --max-workers=2`,
never the full suite of `reposilite-backend`.

## 0. Verify third-party facts (before the proposal pull request, results recorded in `proposal.md`)

- [ ] 0.1 Verify each claim about JFrog Artifactory `cache-fs` against the current JFrog documentation and record the link and the date
  in `proposal.md`: least recently used eviction; the default size (assumed 5 GB) and whether it is counted in decimal or binary units;
  the maximum file size option; the option to skip the cache during upload; checksum validation; the advice of one cache per node in a
  high availability setup; and the advice not to use a cache in front of a local store. Correct the proposal and open question 4 where a
  claim is wrong.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-storage-cache-plugin`: this change and nothing else. Verify:
  `openspec validate add-storage-cache-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Shared mutable key rule (red, then green)

- [ ] 2.1 `test(storage)`: write a failing test that `isMutableStorageKey` is visible from the package `com.reposilite.storage` and
  returns the same answers as the S3 rule for `maven-metadata.xml`, its checksums, files inside `-SNAPSHOT` directories and plain
  release files. Verify: it fails on the missing function.
- [ ] 2.2 `refactor(storage)`: move `isMutableS3Key` to `com.reposilite.storage` as `isMutableStorageKey` with an unchanged body, and
  let `CachableS3Client` call it. Verify: the test of 2.1 passes, and the existing S3 tests (`CachableS3Client` tests) pass unedited.
  The plugin API dump of `gate-plugin-abi` gains the one new member.

## 3. Settings, registration and dashboard form (red, then green)

- [ ] 3.1 `test(storage)`: write failing unit tests of the settings: defaults of every key; `type` is always written; `maxSize` and
  `maxFileSize` parse `5GB`, `20GB`, `512MB` and the empty `maxFileSize`, and reject `5 gigabytes` and `-1GB`; a delegate of type
  `cache` makes the factory throw naming the nested type; `cacheMutable: true` makes the factory throw naming the key; a missing
  delegate type gives UNAVAILABLE with the core reason for the inner type; the default directory resolves to `<workingDirectory>/cache/<repositoryName>`. Add a schema test that the delegate
  choices are the registered types except `cache`. Verify: they fail.
- [ ] 3.2 `feat(storage)`: add `CacheStorageProviderSettings`, `CacheStorageProviderFactory` with type `cache` and
  `apiVersion = STORAGE_PROVIDER_API_VERSION`, opted in with `@OptIn(ExperimentalIngotStorageApi::class)`, the size parser, and the
  entry in `META-INF/services/com.reposilite.storage.StorageProviderFactory` so that the registry of `add-storage-provider-registration`
  finds it. The factory builds the delegate through `StorageProviderContext`.
  Verify: the tests of 3.1 pass.

## 4. Read path (red, then green)

- [ ] 4.1 `test(storage)`: write failing unit tests with an in-memory fake delegate and `@TempDir`: a hit serves the bytes without a
  delegate read; a miss fills and then serves; two concurrent misses on one key cause one delegate read (latches, both waiters released
  by the fill); a size mismatch deletes the temporary file and serves from the delegate; a range on hit returns the requested bytes
  and reads no earlier byte; a range miss passes through and creates no file; a file above `maxFileSize` is never cached; a mutable key
  bypasses the cache; a cached file that vanished drops its entry; the metadata calls go to the delegate. Verify: they fail.
- [ ] 4.2 `feat(storage)`: add `CacheStorageProvider` with the read path: hit with a `FileChannel`, full-file miss with the single
  flight map, the fill into `fill/`, the atomic rename, the size check, the ranged hit and the ranged passthrough, and the delegation
  of the metadata methods. Verify: the tests of 4.1 pass.

## 5. Write path, invalidation and cluster (red, then green)

- [ ] 5.1 `test(storage)`: write failing unit tests with an ordered fake delegate: the delegate write happens before the cache commit
  (the fake records the order); `cacheOnUpload` `false` creates no file; `cacheOnUpload` `true` commits only after success; a failed
  delegate write leaves no file and no entry; a disk-full failure in the tee keeps the delegate write running and reports the failure;
  a removal drops the entry and publishes one invalidation; a failed removal publishes nothing; an overwrite drops the entry even with
  `cacheOnUpload` `false`; a fill that started before an invalidation does not commit (generation check, driven by a latch); a received
  invalidation for an uncached key changes nothing; the related key prefixes are applied to the local index; a published invalidation
  reaches only the provider of the same repository on the other replica of a fake `ClusterBus`, and a plain `s3` provider there is not
  invalidated. Verify: they fail.
- [ ] 5.2 `feat(storage)`: add the write path with the tee into `fill/`, the invalidation after a successful delegate write or removal,
  the generation counters, the experimental `publishInvalidation` member of `ClusterBus` with its delivery in the cluster plugin, and
  the implementation of `ClusterCacheAware` for the invalidation message. Forward `SharedNamespaceAware` and `SupportDetails` of the
  settings to the delegate settings, forward `ReadinessProbe` and `RelatedKeyAware` to the delegate, and implement `SupportDetails` on
  the provider for the counters. Verify: the tests of 5.1 pass.

## 6. Eviction, start-up and failure isolation (red, then green)

- [ ] 6.1 `test(storage)`: write failing unit tests with an injected stepped `Clock` and the file store interface: the least recently
  used entry is evicted first; an entry with an open stream is not evicted; when every entry is pinned, the fill is skipped and counted;
  a file above `maxSize` is not cached; a write is not refused at the limit; start deletes the temporary folder; start rebuilds the index
  from `data/` with the file modification time and serves the first read as a hit without a delegate call; start evicts down to
  `maxSize`; a second lock on the same directory makes the factory throw naming the directory; a disk failure on hit serves
  from the delegate. Verify: they fail.
- [ ] 6.2 `feat(storage)`: add the index with its pin counts, the eviction before commit, the start-up rebuild, the lock file, the
  `CacheFileStore` interface with its file system implementation, and the catch-all of D8 that reports through `failureFacade`. Verify:
  the tests of 6.1 pass.

## 7. Contract and integration tests (red, then green)

- [ ] 7.1 `test(storage)`: add the storage contract suite run for the cache over a file system delegate, as a unit test in the plugin.
  Write it first, so that it fails on the missing provider, then make it pass with the implementation of groups 4 to 6. Verify:
  `:reposilite-plugins:storage-cache-plugin:test --tests '*CacheStorageContractTest*'` passes.
- [ ] 7.2 `test(storage)`: add `CacheOverS3IntegrationTest` in the integration source set. It starts the existing S3 test container,
  creates two cache providers with two directories (two replicas), and checks: a read fills the first cache, a removal through the
  first replica makes the second replica's next read go to the delegate, and a metadata file is never stored. It uses latches and
  the invalidation bus fake, not a time wait. Verify:
  `heavy ./gradlew :reposilite-plugins:storage-cache-plugin:integration --tests '*CacheOverS3IntegrationTest*' --max-workers=2` fails
  before the implementation and passes after it.

## 8. Build and distribution

- [ ] 8.1 `build(storage)`: add `:reposilite-plugins:storage-cache-plugin` to `settings.gradle.kts` with the plugin id `storage-cache`,
  the test fixtures of `reposilite-backend`, the test container dependency and a shadow jar. Do not add it to the `Dockerfile` and do not
  add it to `PluginLoader.BUNDLED_SWITCHES`. Verify:
  `heavy ./gradlew :reposilite-plugins:storage-cache-plugin:shadowJar --max-workers=2` passes and the image build job is unchanged. Apply
  `com.coditory.integration-test` as in `reposilite-backend` for the integration source set of 7.2.
- [ ] 8.2 `build(storage)`: add the Maven publication `net.onelitefeather.ingot:storage-cache-plugin` with the shadow jar and the POM, as
  the other storage plugins do. The store entry is a catalogue item of `add-plugin-store`, published by `publish-plugin-catalogue`; this task depends on both for that entry, and the manual jar path needs neither. Verify: a local publish to a temporary directory
  produces the jar and the POM.
- [ ] 8.3 `ci(storage)`: add the job "Storage cache" that runs the integration test on pull requests that touch the plugin or the storage
  SPI, and nightly otherwise, with `--max-workers=2`; `heavy` is a local slot runner and is not used on CI runners. Verify: the workflow
  passes its own lint.

## 9. Documentation

- [ ] 9.1 `docs(storage)`: add `docs/gitbook/how-to/use-a-local-read-cache.md` with when to use it (remote stores only, not with a local
  store), the sizing rule for `maxSize` and `maxFileSize`, the redeployment caveat and the lost invalidation, the single cache per replica
  and the shared volume warning, and the example configuration. Link it from `docs/gitbook/SUMMARY.md`.
- [ ] 9.2 `docs(storage)`: add the `cache` type with every setting of D2 to `docs/gitbook/reference/storage-providers.md`, and the
  support detail fields to `docs/gitbook/reference/repository-settings.md`. Each key of the table is documented with its default.
- [ ] 9.3 `docs(storage)`: add to `docs/gitbook/reference/upgrade-changes.md` that the storage type `cache` is new and optional, that no
  existing configuration changes, and that the plugin is installed from the plugin store or by hand into `plugins/` from the Maven artifact.

## 10. Code pull request

- [ ] 10.1 Open the code pull request under the title `feat(storage): add a read-through local cache storage plugin`, from a branch of
  the remote main branch that already contains the merged proposal and the merged predecessor changes
  (`add-storage-provider-registration`, `add-storage-capability-hooks`), with no file under `openspec/`.

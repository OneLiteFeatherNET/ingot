# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(the `Clock` is injected, and the readiness probe takes its clock as a parameter), no shared mutable state, a temporary
directory per test, a fixed project id, a random container port, and assertions with messages. Unit tests run as
`:reposilite-plugins:gcs-storage-plugin:test --tests '<pattern>'`, container tests through `heavy` with `--max-workers=2`.

## 0. Step 0 (before the proposal pull request, result recorded in proposal.md)

- [ ] 0.1 Run the existing `s3` integration tests against a real Google Cloud Storage bucket through the interoperability API
  with HMAC keys. Record per test in `proposal.md` under "Step 0 result": passed, failed with the error, or not applicable. Include
  an upload above 8 MiB. Do not commit the keys; use a throwaway bucket that is deleted afterwards.
- [ ] 0.2 Confirm `roles/storage.objectUser` on a bucket (bucket scope, not managed folder): list, create, get and delete a test
  object with a service account that holds only that role. Record the result in `proposal.md`. If it fails, record that the
  documentation names `roles/storage.objectAdmin` instead, and update design D8 and open question 3 in the same proposal edit.
- [ ] 0.3 Spike against `fsouza/fake-gcs-server` (BSD-2-Clause): check resumable uploads, `startOffset` and `pageToken` in listing,
  and the 404 and 403 bodies the SDK sees. Record each feature as supported or not in `proposal.md`. Unsupported features become
  skipped cases with a reason in the integration tests.
- [ ] 0.4 Confirm the current coordinates of `google-cloud-storage` and `libraries-bom` in the `googleapis/google-cloud-java`
  repository, and their Apache 2.0 licence, since `googleapis/java-storage` is said to be deprecated (verify that statement too).
  Record the version in `proposal.md`.
- [ ] 0.5 Verify the remaining third-party claims against current vendor documentation and record the link and date in `proposal.md`:
  the predefined role table for `roles/storage.objectUser` and `roles/storage.objectAdmin`; that Nexus Repository and Artifactory offer
  native GCS blob stores, and in which editions; that `startOffset` of `objects.list` is inclusive; the 256 KiB multiple for resumable
  chunks; whether the client or the JSON API can cancel a resumable session; that `ReadChannel.limit` exists in the pinned client; the
  expiry of an abandoned resumable session; and the strong consistency statements that design D10 relies on. Correct the design where a
  claim is wrong.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-gcs-storage-plugin`: this change and nothing else, with the step 0 result recorded and the
  tasks unchecked. Verify: `openspec validate add-gcs-storage-plugin --strict` passes and the dash grep from the proposal prints
  nothing.

## 2. Settings and factory (red, then green)

- [ ] 2.1 `test(storage)`: write failing unit tests of the settings rules of design D2: the defaults; `type` is always written;
  `bucket` is required; `chunkSize` must be a multiple of 256 KiB between 256 KiB and 64 MiB; `authentication` accepts `adc` and
  `service-account-key` only; a non-empty `credentialsJson` with `adc` is refused, naming both keys; `endpoint` without the
  process-wide switch is refused, naming the variable; the switch accepts `true` and `false` only, defaults to `false`, and its
  `INGOT_` name wins over `REPOSILITE_`. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(storage)`: add `GcsStorageProviderSettings`, the validation, the process-wide switch reader and the factory
  `GcsStorageProviderFactory` with `type` `gcs` and `apiVersion = STORAGE_PROVIDER_API_VERSION`, opted in with
  `@OptIn(ExperimentalIngotStorageApi::class)` and listed in `META-INF/services/com.reposilite.storage.StorageProviderFactory`, so that
  the registry of `add-storage-provider-registration` finds it.
  Verify: the tests of 2.1 pass and the plugin's factory is listed by `StorageProviderSerdes` in a test that loads it from a
  plugin-style class loader.
- [ ] 2.3 `test(storage)` then `feat(storage)`: failing test that the secret fields are annotated sensitive and that the settings
  schema for the dashboard form lists exactly the keys of design D2. Then the annotations. Verify: the schema test passes.

## 3. Adapter and provider (red, then green)

- [ ] 3.1 `test(storage)`: write failing unit tests against the in-memory fake of `GcsObjectClient`: put of a small file in one
  request; put of a large file in chunks of `chunkSize`; a failed upload creates no object; ranged read with seek and limit; zero
  length without a request; offset at the end gives the empty answer and a service 416 is not an error; a range past the end is truncated; object details, directory from prefix,
  empty directory `NOT_FOUND`, repository root; removal refuses the root before any call; the cursor is exclusive in native paging
  with a page boundary test; 404, 403 and redaction of error texts. Verify: they fail.
- [ ] 3.2 `feat(storage)`: add the `GcsObjectClient` adapter, the production adapter over the SDK with the HTTP transport, and
  `GcsStorageProvider` implementing the SPI and `StorageEntrySource`, with the error mapping of design D8. Verify: the tests of 3.1
  pass.
- [ ] 3.3 `test(storage)` then `feat(storage)`: failing unit tests of the capabilities: `usage` returns `-1`, `canHold` returns
  `Long.MAX_VALUE`, the shared namespace key `gcs:<bucket>/<prefix>`, the support details contain no credential, the readiness probe
  maps 403, 404, empty bucket and timeout with an injected clock. Then the capability implementations. Verify: the tests pass.
- [ ] 3.4 `test(storage)` then `feat(storage)`: failing unit test that the storage options use the HTTP transport and that `adc`
  creates the client without reading `credentialsJson`, with `service-account-key` using the key. Then the authentication wiring.
  Verify: the tests pass.

## 4. Build and shading (build)

- [ ] 4.1 `build(storage)`: add `:reposilite-plugins:gcs-storage-plugin` to `settings.gradle.kts`, with `compileOnly` on
  `:reposilite-backend`, the SDK version from the BOM, `com.gradleup.shadow` as in the OpenSearch plugin, the test fixtures of
  `reposilite-backend`, and `com.coditory.integration-test` as in `reposilite-backend` for the container tests. Verify:
  `heavy ./gradlew :reposilite-plugins:gcs-storage-plugin:compileKotlin --max-workers=2` passes.
- [ ] 4.2 `build(storage)`: relocate Guava, protobuf and gax under `net.onelitefeather.ingot.storage.gcs.shaded` in the shadow jar,
  and add a test that loads the shaded jar in a child class loader and resolves the provider class. Measure the jar size with and
  without relocation and record both numbers for the how-to page. Verify: the test passes and the sizes are recorded.
- [ ] 4.3 `build(storage)`: add the Maven publication `net.onelitefeather.ingot:gcs-storage-plugin` with the shaded jar and the POM. The store entry depends on `add-plugin-store` and `publish-plugin-catalogue`; the manual jar path needs neither.
  The image is not changed: no line in `Dockerfile`, no entry in `PluginLoader.BUNDLED_SWITCHES`. Verify: a local publish to a temporary
  directory produces the jar and the POM.

## 5. Container contract (red, then green)

- [ ] 5.1 `test(storage)`: add `GcsStorageContractTest` in the integration source set. It runs the storage contract kit against
  `fsouza/fake-gcs-server` pinned by tag and digest as a `GenericContainer`, with the client on the emulator endpoint and
  `NoCredentials`. Each case the emulator does not support from task 0.3 is skipped with its reason through an assumption.
  Verify: `heavy ./gradlew :reposilite-plugins:gcs-storage-plugin:integration --tests '*GcsStorageContractTest*' --max-workers=2`
  fails on the missing provider or passes only the cases that are implemented.
- [ ] 5.2 `test(storage)`: add the resumable-upload case with a 20 MiB object and the reader check that no partial object is visible
  during the upload. Verify: the case fails before the upload path exists and passes after 5.3.
- [ ] 5.3 `feat(storage)`: run the contract tests green with the provider of task 3. No change to the production code is expected; a
  change needed here goes back to task 3 as its own commit. Verify: the container tests pass, with the skipped cases listed.

## 6. CI (ci)

- [ ] 6.1 `ci(storage)`: add the job "Storage backends (gcs)" to the workflow, running the emulator contract tests on pull requests
  that touch the plugin and nightly otherwise, with `--max-workers=2` as the other container jobs use. `heavy` is a local slot runner
  and is not used on CI runners. Verify: the workflow passes its own lint.

## 7. Documentation (docs)

- [ ] 7.1 `docs(storage)`: add `docs/gitbook/how-to/use-google-cloud-storage.md` with the install steps (the plugin store of `add-plugin-store` and the Maven artifact by hand into `plugins/`), the GKE workload identity example, a settings example for `adc` and for `service-account-key`, the step 0
  result, the shaded jar sizes, the role of design D8 and open question 3, the non-atomic removal, the resumable session expiry, and
  the limits of the emulator. Verify: the page renders and every link resolves.
- [ ] 7.2 `docs(storage)`: add every key of the settings table of design D2 and the process-wide variable with its default to the
  configuration reference and to `docs/gitbook/reference/environment-variables.md`. Add the release notes to
  `docs/gitbook/reference/upgrade-changes.md`, stating that nothing changes for existing installations. Verify: each key appears in the
  reference pages with its default.
- [ ] 7.3 `docs(storage)`: link the new how-to from `docs/gitbook/SUMMARY.md`, and change the interoperability note of
  `docs/gitbook/how-to/use-s3-storage.md` to point to it, leaving its behaviour text unchanged. Verify: the summary links resolve.

## 8. Code pull request

- [ ] 8.1 Open the code pull request under the title `feat(storage): add a google cloud storage provider plugin`, from a branch of the
  remote main branch that already contains the merged proposal and the merged predecessor changes (`add-storage-provider-registration`,
  `add-storage-capability-hooks`), with no file under `openspec/`. The pull request description records the step 0 result, the
  manual run against a real bucket, and the shaded jar sizes.

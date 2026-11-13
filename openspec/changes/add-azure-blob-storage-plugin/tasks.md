# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected), no fixed ports (the Azurite container maps a random port), no shared mutable state, a temporary directory per
test, and assertions with messages. Unit tests run as `:reposilite-plugins:azure-storage-plugin:test --tests '<pattern>'`, container
tests as `:reposilite-plugins:azure-storage-plugin:integration --tests '<pattern>'` through `heavy`, always with `--max-workers=2`.

The SDK adapter is always behind the internal interface `AzureBlobClient`, so the unit tests use a fake and need no network.

## 0. Verify third-party facts (before the proposal pull request, results recorded in `proposal.md`)

- [ ] 0.1 Verify each claim about Azure and other products against the vendor's current documentation, record the source link and the
  date in `proposal.md`, and correct the proposal and design where a claim is wrong: Microsoft's stance on an S3-compatible API for Blob
  Storage; that Nexus Repository and Artifactory offer Azure blob stores, and in which editions; the first `azure-identity` release with
  AKS workload identity support (assumed 1.9.0); the order of the `DefaultAzureCredential` chain in the pinned SDK; the retention period
  of uncommitted blocks (assumed seven days); the sub-request limit of Blob Batch (assumed 256); whether `azure-core-http-jdk-httpclient`
  is GA; the error codes `AuthorizationFailure`, `AuthorizationPermissionMismatch`, `ContainerNotFound`, `BlobNotFound` and `ServerBusy`;
  and the RBAC role names `Storage Blob Data Contributor` and `Storage Blob Data Reader`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-azure-blob-storage-plugin`: this change and nothing else. Verify:
  `openspec validate add-azure-blob-storage-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Module, SDK and settings (red, then green)

- [ ] 2.1 `build(storage)`: add the subproject `:reposilite-plugins:azure-storage-plugin` to `settings.gradle.kts` with the plugin
  build file copied from `opensearch-plugin`: `compileOnly` the backend, Jackson `compileOnly` at the server version, the test
  fixtures of `reposilite-backend`, the `com.coditory.integration-test` plugin as in `reposilite-backend` for the container tests, and
  the shadow jar with `mergeServiceFiles()`. Import `com.azure:azure-sdk-bom` as a platform,
  and add `azure-storage-blob` and `azure-identity` (at least the version that task 0.1 confirmed). Pin the newest GA release of the BOM and record the version
  and its release date in the commit body. If `azure-core-http-jdk-httpclient` is GA at that time, use it; otherwise use the Netty
  client and record that decision. Verify: `heavy ./gradlew :reposilite-plugins:azure-storage-plugin:compileKotlin --max-workers=2`
  passes, and the resolved versions match the recorded ones.
- [ ] 2.2 `test(storage)`: write failing unit tests of the settings: `type` is always `azure-blob`; the defaults of every field of
  D10 (empty endpoint, `default-credential`, empty prefix, 8 MiB block size); the endpoint is derived from the account name when
  empty; each authentication mode reads only its own field; a missing field is refused with a message that names the field and not
  its value; the three secret fields are marked sensitive. Verify: they fail on the missing classes.
- [ ] 2.3 `feat(storage)`: add the settings data class, the `IngotStorageProviderFactory` implementation with type `azure-blob` and
  `apiVersion = STORAGE_PROVIDER_API_VERSION`, opted in with `@OptIn(ExperimentalIngotStorageApi::class)`, and its entry in
  `META-INF/services/com.reposilite.storage.StorageProviderFactory`, so that the registry of `add-storage-provider-registration` finds it.
  Verify: the tests of 2.2 pass, and with the jar absent a repository of type `azure-blob` is `UNAVAILABLE` with the core reason.

## 3. Provider behaviour (red, then green)

- [ ] 3.1 `test(storage)`: write failing unit tests against the fake of `AzureBlobClient`, one behaviour per test: an upload that
  fits one block is one Put Blob; a three-block upload stages three blocks and commits once; a failure before commit leaves no
  visible file; at most one block is held during an upload; two uploads to the same blob stage their blocks under different ids of
  equal length; a range of length 0 returns an empty stream without a read; an offset
  at or after the end returns an empty stream; a range past the end returns the bytes up to the end; a 416 answer is never an
  internal server error; a negative offset is refused before any call; a missing blob with a range is not found; a prefix with
  children is a directory and its probe asks for one result; an empty location is not found; the root removal is refused with no
  call; an interrupted prefix removal reports the error and a repeated removal tolerates the missing children; a 403 is reported
  through the failure facade and is not a not found; a 404 for the blob or the container is not found. Verify: they fail.
- [ ] 3.2 `feat(storage)`: add `AzureStorageProvider` and the SDK-backed `AzureBlobClient` implementation, with the range rules of D5,
  the block chunking of D4, the error mapping of D7 and the prefix probe of D6. Verify: the tests of 3.1 pass.
- [ ] 3.3 `test(storage)` then `feat(storage)`: failing tests of the readiness probe (empty container healthy, refused identity
  reported with the reason `authorization` and the role, missing container reported as missing), of the support detail (account,
  container, prefix and mode present, no secret in the rendered block), of the shared namespace key `azure:<account>/<container>/<prefix>`,
  and of the usage capability (-1, unlimited). Then the implementation. Verify: the tests pass.
- [ ] 3.4 `test(storage)` then `feat(storage)`: verify the paging question of D13 first. Read the List Blobs documentation for a cursor
  that starts after a key and check it against Azurite. Write a failing test for the chosen listing behaviour: the native source
  when the cursor is confirmed, otherwise a test that the walking fallback is used and that the provider does not claim
  `StorageEntrySource` natively. Then the implementation for the chosen path. Record the answer in the commit body. Verify: the test
  passes and the answer is recorded.

## 4. Container contract and jar (red, then green)

- [ ] 4.1 `test(storage)`: add `AzureStorageContractTest` in the integration source set. It extends the storage contract kit from the
  `reposilite-backend` test fixtures and starts Azurite through a Testcontainers `GenericContainer` with the image
  `mcr.microsoft.com/azure-storage/azurite` pinned by tag and digest, the blob port 10000 mapped to a random host port, the
  well-known `devstoreaccount1` account and its public development key, and account-key authentication. A fresh container per test
  class, no sleeps. Verify: `heavy ./gradlew :reposilite-plugins:azure-storage-plugin:integration --tests '*AzureStorageContractTest*' --max-workers=2`
  fails on the missing provider.
- [ ] 4.2 `feat(storage)`: make the contract kit pass, including the range cases and the empty prefix case of D5 and D6, and the
  prefix isolation of two repositories in one container. Verify: the container test passes.
- [ ] 4.3 `build(storage)`: configure the shadow jar to relocate the SDK's Reactor, Netty (when present) and Jackson modules under
  `net.onelitefeather.ingot.storage.azure.shaded`, and measure the jar size with the dependencies unshaded and relocated. Write both
  numbers into the commit body and the task output. Verify: the integration test of 4.1 runs against the shaded jar, and no class of
  the server is shadowed.

## 5. Documentation

- [ ] 5.1 `docs(storage)`: add `docs/gitbook/how-to/use-azure-blob-storage.md` with the install from the plugin store (`INGOT_PLUGINS` or the dashboard) and the install of the Maven artifact by hand into `plugins/`, the RBAC role `Storage Blob Data Contributor` (and `Storage Blob Data Reader` for read-only mirrors), an
  AKS workload identity example, a settings JSON example, the Azurite example for local tests, the limits (no quota, non-atomic
  prefix delete, last writer wins, uncommitted blocks removed after the period verified in 0.1), the
  hierarchical namespace status, the measured jar size, and the manual checklist (real flat namespace account, two replicas on one
  container, an RBAC denial). Link the page from `docs/gitbook/SUMMARY.md`. This task depends on `add-plugin-store` and `publish-plugin-catalogue` for the store path; the manual path needs neither. Verify: the links resolve.
- [ ] 5.2 `docs(storage)`: add `azure-blob` to the storage types in `docs/gitbook/reference/storage-providers.md`, the variables
  `INGOT_AZURE_*` with the `REPOSILITE_AZURE_*` fallback to `docs/gitbook/reference/environment-variables.md`, and the statement that
  no existing storage setting changes to `docs/gitbook/reference/upgrade-changes.md`. Verify: the variable names match the code.

## 6. Code pull request

- [ ] 6.1 Open the code pull request under the title `feat(storage): add an azure blob storage provider plugin`, from a branch of the
  remote main branch that already contains the merged proposal and the merged predecessor changes
  (`add-storage-provider-registration` and `add-storage-capability-hooks`), with no file under `openspec/`.

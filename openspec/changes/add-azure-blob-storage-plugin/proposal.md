# Proposal

## Open maintainer questions

Each question has a suggested default. The default is what the tasks assume unless a maintainer answers otherwise before the
code pull request opens.

1. **Which GA version of the Azure SDK?** Default: the newest GA release of `com.azure:azure-storage-blob` in the
   `com.azure:azure-sdk-bom` at implementation time, with a beta never pinned. Task 2.1 records the version and its date.
2. **Which HTTP client?** Default: `azure-core-http-jdk-httpclient`, so the jar carries no Netty. It is used only when it is GA
   at implementation time. If it is not GA, the default falls back to Netty and the jar size is measured again.
3. **Native paging?** Default: the walking fallback of `StorageEntrySource` only. A native implementation needs a `startFrom`
   style cursor on List Blobs, which is unverified. Task 3.4 verifies it against Azurite and the service documentation; the
   native source is added only when the cursor is confirmed.
4. **Hierarchical namespace (ADLS Gen2)?** Default: not claimed. Version one targets flat namespace accounts through the Blob
   endpoint. The docs claim HNS support only after the contract kit passes against a real HNS account in the manual checklist.
5. **Distribution.** Default: the store path and the manual path of the distribution bullet in What Changes. The Maven
   artifact `net.onelitefeather.ingot:azure-storage-plugin` is a signed catalogue entry of `add-plugin-store`, selectable
   with `INGOT_PLUGINS` or in the dashboard, and the jar stays installable by hand into `plugins/`, where it wins over a
   store entry with the same id.
6. **Shade or relocate the SDK's libraries?** Default: relocate the transitive libraries that clash with the server (reactor,
   Netty if present, Jackson modules) under `net.onelitefeather.ingot.storage.azure.shaded`. Task 4.3 measures the jar size with
   and without relocation, and the documented size is the measured one.

## Why

Ingot stores repository files on the filesystem or on S3, configured per repository. Many operators who run Azure ask for Azure
Blob Storage directly. Azure Blob has no native S3 API. Microsoft is said to have stated in 2023 that there is no official S3
support; the source is not cited here, and task 0.1 verifies the current stance before release. Today an operator reaches Azure through the S3 provider only with an extra gateway such as
S3Proxy or Flexify, which they have to run, secure and upgrade. Nexus and Artifactory both ship Azure blob stores (to be verified for the
current editions of each product, task 0.1), so the gap shows up in comparisons. User demand in the Reposilite tracker was not verified and this proposal does not rely on it.

The provider is shipped as a plugin, not in the image. The storage registration SPI of `add-storage-provider-registration` lets a
plugin register a factory, and `add-storage-capability-hooks` replaces the provider type checks with capability interfaces. This
change is the first provider that uses both, which proves the seam for the later providers (Google Cloud Storage and the cache).

## What Changes

- **A plugin module `reposilite-plugins/azure-storage-plugin`** with the package `net.onelitefeather.ingot.storage.azure` and the
  plugin id `azure-storage`. It registers the storage type `azure-blob` through `IngotStorageProviderFactory` from
  `add-storage-provider-registration`, with `apiVersion = STORAGE_PROVIDER_API_VERSION` (1), opted in with
  `@OptIn(ExperimentalIngotStorageApi::class)`, and listed in the jar's `META-INF/services/com.reposilite.storage.StorageProviderFactory`.
  Without the jar, a repository of type `azure-blob` is `UNAVAILABLE` with the reason `storage provider 'azure-blob' is not installed`,
  as that change specifies.
- **Settings per repository** in the existing `storageProvider` block of a repository, with `type` always written as `azure-blob`:
  `endpoint`, `accountName`, `container`, `prefix`, `authentication` with one of `default-credential`, `account-key`, `sas` or
  `connection-string`, and `blockSize`. The secrets (`accountKey`, `sasToken`, `connectionString`) carry `@Sensitive` and work with
  `add-settings-secret-references` and `encrypt-settings-at-rest`.
- **Blob semantics that match the SPI.** Uploads stream into a block blob. Small payloads are one Put Blob, larger ones are staged
  blocks committed by Put Block List, so a reader never sees a partial object. Reads use ranged Get Blob. Listings use the hierarchy
  with the `/` delimiter. Removing a prefix deletes its children and is documented as non-atomic.
- **Capabilities.** On the settings type: `SharedNamespaceAware` with the key `azure:<account>/<container>/<prefix>` and `SupportDetails`
  with a support detail block with the
  account, container and authentication mode and never a secret. On the provider: `UsageCapability` and a `ReadinessProbe` that tells
  an authorization failure apart from an empty container.
- **Distribution.** The Maven artifact `net.onelitefeather.ingot:azure-storage-plugin` is the release unit, and the jar is
  not bundled in the image. The plugin store of `add-plugin-store` lists it as a signed catalogue entry, selectable with
  `INGOT_PLUGINS` or in the dashboard and published by the release pipeline of `publish-plugin-catalogue`. The jar also
  stays installable by hand into `plugins/`, and a jar there wins over a store entry with the same id.
- **Documentation.** A new how-to `docs/gitbook/how-to/use-azure-blob-storage.md` (install, the RBAC role, the AKS workload identity
  example, a settings example, the limits), the new type added to `docs/gitbook/reference/storage-providers.md`, and the new
  process variables (`INGOT_AZURE_*`, with `REPOSILITE_AZURE_*` as fallback) added to `docs/gitbook/reference/environment-variables.md`.

### Out of scope

- The Azure archive tier and rehydration. Blobs in the archive tier are reported as they are and not read.
- Immutability policies, legal holds and version-level retention.
- Presigned SAS redirects. Ingot streams every file through itself, as it does for S3 today.
- Append blobs and page blobs. Only block blobs are written.
- Leases, `If-Match` and `If-None-Match` conditional writes. Coordination stays in Ingot's database, as in the design of the cluster.
- A metadata cache. Version one sends every HEAD to Azure. A cache is a later change that reuses the rules of `CachableS3Client`.
- Azure Monitor based usage and quota. `usage()` and `canHold()` report unlimited, as the S3 provider does.
- Bundling the plugin in the image, and any change to the plugin store (`add-plugin-store`).
- Any change to the S3 provider, the filesystem provider or the database.

## Capabilities

### New Capabilities

- `azure-blob-storage`: the `azure-blob` storage type, its settings and secrets, the SPI mapping of uploads, reads, listings and
  removal, the error mapping, the readiness probe, the distribution as a plugin, and the documented limits.

## Impact

- **Type and scope.** The change ships under `feat(storage)`. Its commits are `test(storage)`, `feat(storage)`, `build(storage)`
  and `docs(storage)`. The pull request title is:

  `feat(storage): add an azure blob storage provider plugin`

- **Breaking marker.** No breaking change. Nothing changes for an installation that does not install the jar: no existing key,
  environment variable, volume, manifest, route or database schema changes, and the image gains no file. An operator who wants
  Azure installs the jar into `plugins/` and sets `type` to `azure-blob` on a repository. Existing `fs` and `s3` settings keep
  working unchanged.
- **Build.** A new Gradle subproject `:reposilite-plugins:azure-storage-plugin` with the shadow jar, the backend as `compileOnly`,
  Jackson as `compileOnly` at the server version, and the test fixtures of `reposilite-backend` for the contract kit. The layout
  copies `opensearch-plugin`.
- **Order.** Depends on `add-storage-provider-registration` (the factory interface and its registration) and on
  `add-storage-capability-hooks` (the capability interfaces). It uses `add-settings-secret-references` and
  `encrypt-settings-at-rest` for the secrets, and it does not wait for them: without them the secrets are stored as today's S3 secrets are. The distribution tasks depend on `add-plugin-store` and `publish-plugin-catalogue`; no other task does.

## Verification

- Unit tests against a thin adapter interface with a fake client, with no network.
- The storage contract kit passes against Azurite, with the cases of the design for ranges, empty prefixes and missing containers.
- The contract kit passes against a real flat namespace account in the manual checklist of the docs, together with a two replica
  run against one container.
- `openspec validate add-azure-blob-storage-plugin --strict` passes, and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-azure-blob-storage-plugin` prints nothing.

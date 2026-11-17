# Design

## Context

A repository's storage is one `StorageProvider` built from its settings. The SPI has ten operations plus `usage`, `canHold`
and `shutdown`, and two optional interfaces: `StorageEntrySource` for paged listings and the capability hooks that the sibling
change adds. The S3 provider is the closest model. It maps object keys under an optional prefix, lists with
`ListObjectsV2`, treats a missing key as not found, and reports `usage` as -1 and `canHold` as `Long.MAX_VALUE`.

Azure Blob differs in three ways that matter here. There is no S3 compatibility layer, so the provider speaks the Blob REST API
through the Azure SDK. Blocks are the only way to write a large blob, and uncommitted blocks are invisible to readers, which gives
atomic visibility for free. Authentication is Entra ID first, with shared keys and SAS as alternatives.

The plugin is loaded by a separate class loader (`PluginLoader`), so its factory is found through the registration of the sibling
change and not through the `ServiceLoader` of the core. Its classes must not clash with the server's, which is why the SDK is shaded.

## Goals and non-goals

Goals: an operator can point one repository at an Azure container with the same guarantees the S3 provider gives (no partial
reads, last writer wins, prefix isolation), with Entra ID authentication by default, and with a jar that the operator installs
without changing the server.

Non-goals are the ones in the proposal: archive tier, immutability, presigned redirects, append and page blobs, leases, metadata
caching, quota.

## Decisions

### D1: the Azure SDK, pinned to GA, with the JDK HTTP client

The plugin depends on `com.azure:azure-storage-blob` and `com.azure:azure-identity`, both versioned by `com.azure:azure-sdk-bom`.
The BOM is imported with `platform`, so every Azure artifact moves together. The version is the newest GA release at
implementation time. A beta is never pinned, even if the BOM lists it. The licence is MIT, which is compatible with Apache 2.0.

The HTTP client is `azure-core-http-jdk-httpclient`, which uses `java.net.http` and adds no Netty to the jar. Netty is the SDK
default. If the JDK client is not GA when the task runs, Netty is used and the decision is recorded in the task output.
`azure-identity` is at least the first release with the workload identity support that AKS uses. The version 1.9.0 is the
assumed floor and is unverified; task 0.1 confirms it from the release notes.

Rejected: the Netty client (larger jar, a second HTTP stack in the server), and the REST API by hand (it would need our own
SharedKey signing and a parser for the XML listing format, which the SDK already does correctly).

### D2: relocation of the SDK's transitive libraries

The SDK pulls in Reactor, Jackson modules and possibly Netty. Each of these can clash with a version that the server or another
plugin loads, so the shadow jar relocates them under `net.onelitefeather.ingot.storage.azure.shaded`: `reactor`, `io.netty`
(when present), and the SDK's own Jackson modules. The server's Jackson is not relocated, because the plugin is compiled against
the server version through `compileOnly` and uses it at runtime, so a second copy would be a clash by design.

The jar size is measured with the dependencies unshaded and relocated. The number in the how-to page is the relocated one, and
the measurement is written into the task output so that it can be checked on the next SDK bump.

### D3: a thin adapter between the provider and the SDK

The provider does not call `BlobContainerClient` directly. It talks to a small internal interface `AzureBlobClient` that has the
operations the SPI needs: `upload(key, bytes or stream, options)`, `stageBlock`, `commitBlocks`, `openRange(key, offset, count)`,
`properties(key)`, `delete(key)`, `listHierarchy(prefix, delimiter, limit, marker)`, `exists(key)`, and `close()`. The SDK-backed
implementation is a single class. The provider is tested against a fake of the interface, which keeps the unit tests in-process,
fast, and free of network.

The adapter also owns the mapping from `BlobStorageException` to the outcome the provider uses (see D7). That mapping is tested
against the SDK's exception type through constructed exceptions, so no network is needed.

### D4: uploads are block blobs, staged and committed

`putFile` reads the stream in blocks of `blockSize` (default 8 MiB). The first block decides the request, as the S3 provider does:
a stream that ends within the first block is one Put Blob with its exact length. A longer stream is staged block by block with
Put Block, and committed once with Put Block List. The block id is a random token per upload followed by a fixed-width counter,
encoded in Base64, so that every id of one blob has the same length and two concurrent uploads to the same blob never stage a block
under the same id. A shared counter alone would let one writer's blocks replace the other's uncommitted blocks, and a commit could then
mix the content of both writers.

Consequences that the SPI relies on:

- An uncommitted block is invisible to `getFile`, `getFileDetails` and listings, so a reader never sees a partial object, and a
  failed upload leaves no visible file. Uncommitted blocks are garbage-collected by Azure after about a week (assumed seven days,
  verified in task 0.1 before it is documented).
- Memory is one block per concurrent upload. The stream is never spooled to the temporary directory. The option that buffers to
  disk, `INGOT_S3_BUFFER_TO_DISK`, is S3-only and has no Azure counterpart.
- Concurrent writers to one blob are not coordinated. The last Put Block List wins, as with S3 multipart uploads today. The
  Ingot database coordinates the writes that matter, as in the cluster design.
- The content type comes from the location, as the S3 provider does.

### D5: reads are ranged

`getFile(location)` opens a Get Blob without a range and streams the body. `getFile(location, offset, length)` overrides the
default from the SPI and asks for `BlobRange(offset, count)`, so the skipped bytes are never transferred.

The edge cases are fixed here, because a range request is where a 416 becomes a 500 by accident:

- `length = 0`: an empty stream, answered without a request, when the blob exists. A missing blob is still not found.
- `offset >= size`: the empty stream as well, not an error. The blob exists, so the caller gets an empty body for the range it
  asked for. The SDK's `RANGE_NOT_SATISFIABLE` (416) answer is caught and mapped to the empty stream, and a 416 never reaches the
  caller as an internal server error.
- `offset + length > size`: the range is truncated to the size, as HTTP ranges are.
- A negative offset or length is an argument error from the SPI caller and is refused before the request.

Each of these is a case of the contract kit, so Azurite and the unit tests both check it.

### D6: properties, listings and the directory answer

`getFileDetails`, `getFileSize`, `getLastModifiedTime` and `exists` use Get Blob Properties. Content-Length and Last-Modified come
from the response, and a 404 means the blob is not there.

A location that is not a blob is a prefix when a listing by hierarchy with `maxResults = 1` returns a child. The answer is a
`DirectoryInfo` synthesised from that listing, as the S3 provider does. An empty prefix (no blob and no child) is NOT_FOUND, which
matches S3. The probe costs one request, and it is only made after the blob lookup has said 404.

`getFiles` uses `listBlobsByHierarchy` with the delimiter `/` and returns the locations of the children, with the prefix removed.
Folder markers that end in `/` are skipped.

### D7: errors

- `BlobNotFound` and `ContainerNotFound` (404) become `notFoundError`.
- 403 (`AuthorizationFailure`, `AuthorizationPermissionMismatch`) is reported through `failureFacade.throwException` and answered as
  an internal server error. This is deliberate: the S3 provider maps 403 to 404 because a missing ListBucket permission must not
  break misses. On Azure a 403 means the identity is wrong, so hiding it as a miss would hide the outage. The how-to page says so.
- 503 `ServerBusy` and 500 `InternalError` are retried by the SDK retry policy with its defaults. After the retries they are
  reported as internal server errors, and the readiness probe reports them.
- Any other failure is an internal server error with the operation named in the log, never the credential.

### D8: readiness and support detail

The provider implements `ReadinessProbe` of `add-storage-capability-hooks`. Every failure is a `ReadinessOutcome.Failed(reason)`, and
a healthy container is `Ok`. The probe calls `getProperties` on the container. An `AuthorizationFailure` is reported as
`Failed` with the reason `authorization` and the RBAC role named in the message. An empty container is a healthy probe, because an empty container is
the normal state of a new repository. Only a 404 for the container (`ContainerNotFound`) is reported as "container missing", so that
an operator can tell a typo from a permission problem. It is `Failed("container missing")`, not `NotFoundOk`, because a missing
container cannot serve any file.

`SupportDetails` and `SharedNamespaceAware` sit on the settings type, as `add-storage-capability-hooks` decides (its D2). The support
detail block carries the account name, the container, the prefix and the authentication mode. It never carries the
account key, the SAS token or the connection string, and a test asserts that a support bundle of a configured repository does not
contain the secret values.

### D9: shared namespace

`SharedNamespaceAware` returns the key `azure:<account>/<container>/<prefix>`, with the prefix normalised as the S3 provider
normalises its prefix. Two repositories that map to one key share a namespace, and core refuses both, as it does for S3 today
(`add-storage-capability-hooks`, D4). Repositories in different containers never collide.

### D10: endpoint, authentication and settings

The settings are a data class with defaults, `type` always written. `endpoint` defaults to the empty string, which means
`https://<accountName>.blob.core.windows.net`. It is set for Azurite (`http://127.0.0.1:10000/devstoreaccount1`) and for sovereign
clouds.

`authentication` selects one of four modes, and each mode reads only its own fields:

- `default-credential` (the default): `DefaultAzureCredential`. It covers managed identity, AKS workload identity, environment
  variables and service principals. The order in which the SDK tries them is not claimed here; task 0.1 reads it from the SDK
  documentation of the pinned version, and the how-to page quotes that.
- `account-key`: `accountKey` (sensitive).
- `sas`: `sasToken` (sensitive). A user delegation SAS is recommended, because it is bound to an Entra ID identity and can be revoked.
- `connection-string`: `connectionString` (sensitive).

A repository whose mode lacks its field is refused at start-up with a message that names the field and never its value: the
factory throws, and the registration rule for a failing factory applies (the failure is reported and the repository is not loaded). Process-wide
variables use the `INGOT_AZURE_` prefix with `REPOSILITE_AZURE_` as the fallback, read through `StorageProviderContext.environment`
and, for a secret, `StorageProviderContext.secret` of `add-storage-provider-registration`, never through `System.getenv`. They are optional and per-repository settings are
preferred; the docs say so.

RBAC: the identity needs `Storage Blob Data Contributor` on the container, which covers read, write and delete. Read-only mirrors
need `Storage Blob Data Reader`, which the how-to page names as a supported narrower role.

### D11: testing

- Unit tests (`:reposilite-plugins:azure-storage-plugin:test`): the settings parser, the authentication selection (with a mocked
  credential for `default-credential`), the range rules of D5, the chunking of D4, the error mapping of D7 with constructed
  exceptions, the probe of D8, and the key of D9. All use the fake of `AzureBlobClient`, a `Clock` where time matters, and a
  temporary directory where a file is needed.
- Contract tests (the plugin module's own `integration` source set, applied with `com.coditory.integration-test` as in
  `reposilite-backend`, run through `heavy`): the contract kit from the `reposilite-backend` test fixtures,
  run against Azurite. The image is `mcr.microsoft.com/azure-storage/azurite`, pinned by tag and digest, started with a
  Testcontainers `GenericContainer`. The blob port is mapped to a random host port. The account is the well-known
  `devstoreaccount1` with its public development key, and the auth mode is `account-key`. Each test class starts its own
  container, and no test sleeps.
- Azurite does not check OAuth, is single-node, and some error bodies differ. Default-credential is therefore tested only with the
  mocked credential, and the error mapping is tested by the constructed exceptions where the body would differ.
- Manual checklist in `how-to/use-azure-blob-storage.md`: the contract kit against a real flat namespace account, a two replica run
  against one container, an RBAC denial, and (if claimed) a hierarchical namespace account.

### D12: hierarchical namespace

Version one targets flat namespace accounts through the Blob endpoint. An HNS account accepts the same calls, but its directories
are real objects, so listings and deletes behave differently for folder markers. The docs do not claim HNS support until the contract
kit passes against an HNS account in the manual checklist. Until then the how-to page says the account type is unverified.

### D13: native paging is conditional

`StorageEntrySource` is implemented natively only if List Blobs supports a cursor that starts after a key. The task verifies the
parameter against Azurite and the service documentation. If it is confirmed, the native source uses it with `maxResults` capped at
the service page size. If not, the provider relies on `WalkingEntrySource` over the hierarchy, and the how-to page documents the
cost: one listing request per directory level, not one per page.

### D14: removal of a prefix is non-atomic

`removeFile` refuses the repository root with an argument error, so a misconfigured call cannot empty a container. For a blob it
deletes the blob. For a prefix it lists the children by hierarchy and deletes each one. Blob Batch (assumed limit of 256
sub-requests per batch, verified in task 0.1) is used when the listing has more than one child, and it is optional in version one: plain deletes are the fallback and are always
correct. The removal is not atomic. A failure part way leaves some children gone, the error is reported, and the caller can retry
because a repeated delete of a missing blob is a 404 that the removal treats as done.

### D15: usage and capacity

`usage()` returns -1 and `canHold()` returns `Long.MAX_VALUE`, as the S3 provider does. Azure has no capacity API without Azure
Monitor, and reading Monitor would need a further role and a further dependency. The capability `UsageCapability` declares this, so
the dashboard shows "unlimited" for the repository and does not show a misleading zero.

## Risks

- **The SDK moves faster than the plugin.** Mitigation: the BOM pins every Azure artifact together, and the contract kit runs
  against Azurite on every change to the plugin.
- **Shading breaks at runtime rather than at build time.** Mitigation: the integration tests run the shaded jar, not the classes
  on the test classpath, loaded through a separate `URLClassLoader` as the plugin loader does.
- **Azure's stance on S3 changes.** The proposal rests on "no official S3 support in 2023". Mitigation: the claim is re-checked
  before release, and the proposal is revised if it has changed.
- **Non-atomic prefix delete.** Documented, and a repeated call converges.
- **Last writer wins.** Documented. Repositories that need stronger guarantees use the database coordination of the cluster.
- **Azurite differs from Azure.** Mitigation: the manual checklist runs against a real account before the docs claim support.

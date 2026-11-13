# Spec Delta

## Purpose

An operator can keep a repository's files in an Azure Blob container. The provider ships as a plugin that the operator installs
by hand, it is selected per repository with the type `azure-blob`, it authenticates with Entra ID by default, and it keeps the
guarantees the S3 provider gives: a reader never sees a partial file, a missing file is not found, and prefixes isolate
repositories that share a container.

## ADDED Requirements

### Requirement: Plugin distribution and registration

The Azure provider SHALL be built as the plugin `azure-storage` in the package `net.onelitefeather.ingot.storage.azure`. It SHALL
register the storage type `azure-blob` through `IngotStorageProviderFactory` of `add-storage-provider-registration`, with the API
version `STORAGE_PROVIDER_API_VERSION` (1), and it SHALL NOT be included in the server image. The plugin SHALL be published as `net.onelitefeather.ingot:azure-storage-plugin` and installed by hand into `plugins/`.

#### Scenario: Plugin installed by hand

- **WHEN** the jar is placed in `plugins/` and a repository sets `type` to `azure-blob`
- **THEN** the repository uses the Azure provider and the type appears in the storage settings schema

#### Scenario: Plugin absent

- **WHEN** the jar is not installed and a repository sets `type` to `azure-blob`
- **THEN** the server starts, the repository is `UNAVAILABLE` with the reason `storage provider 'azure-blob' is not installed` and
  answers `503`, its storage block is kept unchanged, and no other repository is affected

#### Scenario: Image contents unchanged

- **WHEN** the server image is built with this change
- **THEN** the image contains no Azure classes and no additional jar

### Requirement: Settings and defaults

The settings SHALL be a data class with defaults, and `type` SHALL always be written as `azure-blob`. The fields SHALL be `endpoint`
(default empty, meaning `https://<accountName>.blob.core.windows.net`), `accountName`, `container`, `prefix` (default empty),
`authentication` (default `default-credential`) and `blockSize` (default 8 MiB). The secret fields `accountKey`, `sasToken` and
`connectionString` SHALL carry the sensitive marker.

#### Scenario: Minimal settings

- **WHEN** a repository's `storageProvider` block sets only `type`, `accountName` and `container`
- **THEN** the endpoint is the public Blob endpoint of the account, the prefix is empty and the authentication is `default-credential`

#### Scenario: Endpoint override

- **WHEN** `endpoint` is set to an Azurite address
- **THEN** every request goes to that address and the public endpoint is not contacted

#### Scenario: Secrets are sensitive

- **WHEN** the settings are written to the shared configuration or shown in the dashboard
- **THEN** `accountKey`, `sasToken` and `connectionString` are masked, and they are encrypted at rest when `encrypt-settings-at-rest` is enabled

### Requirement: Authentication modes

The provider SHALL support the modes `default-credential`, `account-key`, `sas` and `connection-string`. A mode SHALL read only
its own fields. The `default-credential` mode SHALL use `DefaultAzureCredential`, which covers managed identity, AKS workload
identity, environment variables and service principals.

#### Scenario: Default credential chain

- **WHEN** the authentication is `default-credential` and no secret is set
- **THEN** the provider authenticates through `DefaultAzureCredential` and never reads the secret fields

#### Scenario: Missing field for the mode

- **WHEN** the authentication is `account-key` and `accountKey` is empty
- **THEN** the repository is not loaded, the reported failure names `accountKey` without its value, and no other repository is affected

#### Scenario: Shared key never logged

- **WHEN** a request fails with an authentication error
- **THEN** the log names the authentication mode and the container, and does not contain the account key, the SAS token or the connection string

### Requirement: Atomic upload through block blobs

`putFile` SHALL write a block blob. A stream that ends within the first block SHALL be written with one Put Blob. A longer stream
SHALL be staged block by block and committed once with Put Block List. A reader SHALL NOT see a partially written blob. The
provider SHALL NOT spool the whole stream to the temporary directory.

#### Scenario: Small file

- **WHEN** a file that fits in one block is uploaded
- **THEN** one Put Blob request is made and the file is visible when the request succeeds

#### Scenario: Large file

- **WHEN** a file of three blocks is uploaded
- **THEN** three blocks are staged and one Put Block List commits them, and the file is visible only after the commit

#### Scenario: Failure before commit

- **WHEN** a staged upload fails before Put Block List
- **THEN** the upload reports an internal server error, no file becomes visible, and the caller sees no partial content

#### Scenario: Concurrent uploads to one blob

- **WHEN** two uploads of different content to the same blob stage blocks at the same time
- **THEN** the blob holds the complete content of one of them, never a mix, because each upload stages its blocks under its own ids

#### Scenario: Memory bounded by one block

- **WHEN** a stream of any length is uploaded
- **THEN** at most one block of the stream is held in memory by the upload at a time

### Requirement: Ranged reads and the edge cases of a range

`getFile` with an offset and a length SHALL use a ranged Get Blob, so that the skipped bytes are not transferred. A range that is
empty, starts at or after the end of the blob, or extends past the end SHALL NOT produce an internal server error. A 416 answer of
the service SHALL NOT reach the caller as an internal server error.

#### Scenario: Length zero

- **WHEN** a range of length 0 is requested for an existing blob
- **THEN** an empty stream is returned without a read of the content

#### Scenario: Offset at or after the end

- **WHEN** the offset equals or exceeds the size of an existing blob
- **THEN** an empty stream is returned and the response is not an error

#### Scenario: Range past the end

- **WHEN** the offset and length extend past the end of the blob
- **THEN** the bytes up to the end of the blob are returned

#### Scenario: Missing blob with a range

- **WHEN** a range is requested for a blob that does not exist
- **THEN** the answer is not found

#### Scenario: Negative offset refused

- **WHEN** a negative offset or length is requested
- **THEN** the request is refused as an argument error before any call to the service

### Requirement: Properties, prefixes and listings

`getFileDetails`, `getFileSize`, `getLastModifiedTime` and `exists` SHALL use Get Blob Properties. A location that is not a blob
but has children under it SHALL be reported as a directory built from a hierarchy listing. `getFiles` SHALL list by hierarchy with
the delimiter `/`. An empty location, with neither a blob nor a child, SHALL be not found, as in the S3 provider.

#### Scenario: File details

- **WHEN** the details of an existing blob are requested
- **THEN** the content length and the last modified time come from the service properties

#### Scenario: Prefix reported as directory

- **WHEN** a location has no blob but children
- **THEN** it is reported as a directory and the listing for it needs no more than one result to decide

#### Scenario: Empty prefix not found

- **WHEN** a location has neither a blob nor a child
- **THEN** it is reported as not found

#### Scenario: Shared container isolation

- **WHEN** two repositories use the same container with different prefixes
- **THEN** a listing of one returns no file of the other

### Requirement: Removal

`removeFile` SHALL refuse the repository root with an argument error. For a blob it SHALL delete the blob. For a prefix it SHALL delete
every child by hierarchy listing. The removal SHALL be documented as non-atomic: a failure part way leaves some children removed,
and a repeated removal SHALL complete the work.

#### Scenario: Repository root refused

- **WHEN** the root of the repository is removed
- **THEN** no request is made and the call fails with an argument error

#### Scenario: Prefix removal interrupted

- **WHEN** a prefix removal fails after some children are deleted
- **THEN** the error is reported and a repeated removal deletes the remaining children without failing on the already missing ones

### Requirement: Error mapping

A missing blob or container (`BlobNotFound`, `ContainerNotFound`) SHALL be reported as not found. An authorization failure (403) SHALL
be reported through the failure facade and answered as an internal server error, and SHALL NOT be answered as not found. Throttling
and server errors (503 `ServerBusy`, 500) SHALL be retried by the SDK retry policy with its defaults before they are reported as
internal server errors.

#### Scenario: Missing blob

- **WHEN** a blob that does not exist is requested
- **THEN** the answer is not found

#### Scenario: Authorization failure

- **WHEN** the identity lacks the blob data role
- **THEN** the answer is an internal server error, the failure facade receives the exception, and the answer is not a not found

#### Scenario: Server busy

- **WHEN** the service answers 503 `ServerBusy`
- **THEN** the SDK retries the request with its default policy and the caller sees an error only after the retries are exhausted

### Requirement: Usage and capacity

`usage` SHALL return -1 and `canHold` SHALL return `Long.MAX_VALUE`, as the S3 provider does. The provider SHALL declare this through the
usage capability, so that a dashboard shows the repository as unlimited.

#### Scenario: Usage unknown

- **WHEN** the usage of an Azure repository is requested
- **THEN** the value is -1 and the dashboard does not show a zero

### Requirement: Readiness

The provider SHALL report readiness through a `ReadinessProbe` that calls the container properties. An authorization failure SHALL be
reported as `Failed` with the reason `authorization`. An empty container SHALL be reported as healthy. A missing container SHALL be
reported as `Failed` with the reason `container missing`, which is distinct from an authorization failure.

#### Scenario: Empty container is healthy

- **WHEN** the container exists and holds no blob
- **THEN** the probe is healthy

#### Scenario: Authorization failure reported

- **WHEN** the container exists but the identity is refused
- **THEN** the probe fails with the reason `authorization` and names the role to grant

### Requirement: Support detail

The support detail SHALL contain the account name, the container, the prefix and the authentication mode, and SHALL NOT contain any secret.

#### Scenario: Support detail without secrets

- **WHEN** a support bundle is collected for a repository that uses the account key
- **THEN** the bundle names the account and the container and does not contain the account key

### Requirement: Shared namespace key

The provider SHALL report the shared namespace key `azure:<account>/<container>/<prefix>`, with the prefix normalised as the S3 provider
normalises its prefix, through `SharedNamespaceAware` on its settings type. Two repositories with the same key SHALL both be refused by
core with the message of the settings.

#### Scenario: Conflicting prefixes

- **WHEN** two repositories use the same account, container and prefix
- **THEN** core refuses both repositories and neither is silently merged

#### Scenario: Different containers

- **WHEN** two repositories use the same account with different containers
- **THEN** their keys differ and they do not conflict

### Requirement: Compatibility of existing storage settings

Adding this provider SHALL NOT change the behaviour of existing `fs` and `s3` settings, the defaults of any storage key, or the
configuration files. A repository that does not use `azure-blob` SHALL behave exactly as before this change.

#### Scenario: Existing S3 repository

- **WHEN** an installation with an `s3` repository installs the Azure plugin
- **THEN** the S3 repository is unchanged and reads and writes as before

#### Scenario: Existing configuration file

- **WHEN** a configuration file from before this change is loaded
- **THEN** it loads without edits and without a warning about an unknown storage type

### Requirement: Documented limits

The documentation SHALL state the limits of the provider: no quota reporting, a non-atomic prefix removal, last writer wins for
concurrent writers to one blob, uncommitted blocks that Azure removes after the period that task 0.1 verified, and that a hierarchical namespace account is
supported only after the manual checklist has passed against it.

#### Scenario: Limits page

- **WHEN** an operator reads `docs/gitbook/how-to/use-azure-blob-storage.md`
- **THEN** every limit in this requirement is stated, and the hierarchical namespace claim is marked as unverified until the checklist passes

### Requirement: Contract tests against Azurite

The provider SHALL pass the storage contract kit of the `reposilite-backend` test fixtures when run against Azurite, pinned by tag and
digest, with the well-known development account and account-key authentication. Default-credential authentication SHALL be tested
with a mocked credential only.

#### Scenario: Contract kit passes

- **WHEN** the contract kit runs against a fresh Azurite container
- **THEN** every case passes, including the range cases and the empty prefix case

#### Scenario: Fresh container per class

- **WHEN** a contract test class starts
- **THEN** it starts its own container on a random port, and no test waits by time

### Requirement: Plugin store distribution

The plugin SHALL be listed in the plugin store of `add-plugin-store` as a signed catalogue entry, selectable with `INGOT_PLUGINS` or in the dashboard. A jar installed by hand in `plugins/` SHALL win over a store entry with the same id.

#### Scenario: Plugin selected from the store

- **WHEN** the store entry is selected with `INGOT_PLUGINS` or in the dashboard and a repository sets `type` to `azure-blob`
- **THEN** the repository uses the provider of this plugin, as when the jar is installed by hand

#### Scenario: Manual jar and store entry share an id

- **WHEN** the jar is installed by hand in `plugins/` and a store entry with the same id is selected
- **THEN** the manual jar is loaded and the store entry is listed as shadowed

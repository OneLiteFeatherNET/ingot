# Spec Delta

## Purpose

An operator can keep a repository's files in a Google Cloud Storage bucket through a plugin. The plugin is not bundled,
it authenticates with Application Default Credentials or a service account key, its errors never look like an empty
repository, and the existing `fs` and `s3` providers behave as before.

## ADDED Requirements

### Requirement: Plugin and storage type

The plugin SHALL register the storage type `gcs` through `IngotStorageProviderFactory` of `add-storage-provider-registration`, with
the API version `STORAGE_PROVIDER_API_VERSION` (1), the plugin id `gcs-storage` and the package `net.onelitefeather.ingot.storage.gcs`.
The plugin SHALL NOT be part of the image, SHALL be published as `net.onelitefeather.ingot:gcs-storage-plugin` and installed by hand into `plugins/`, and no bundled switch SHALL be required for it.

#### Scenario: Plugin installed

- **WHEN** the plugin jar is in the plugins directory and a repository sets `type` to `gcs`
- **THEN** the factory is discovered and the repository uses the Google Cloud Storage provider

#### Scenario: Plugin not installed

- **WHEN** no plugin registers `gcs` and a repository sets `type` to `gcs`
- **THEN** the server starts, the repository is `UNAVAILABLE` with the reason `storage provider 'gcs' is not installed` and answers
  `503`, its storage block is kept unchanged, and no other repository is affected

#### Scenario: Existing providers unchanged

- **WHEN** the plugin is installed and a repository uses `fs` or `s3`
- **THEN** that repository behaves as before, with the same keys, defaults and error responses

### Requirement: Settings and validation

The settings of a `gcs` repository SHALL be `type`, `projectId`, `bucket`, `prefix`, `endpoint`, `authentication`,
`credentialsJson` and `chunkSize`, with the defaults of the design. `type` SHALL always be written. `credentialsJson` SHALL be a
sensitive value. `bucket` SHALL be required, and `chunkSize` SHALL be a multiple of 256 KiB between 256 KiB and 64 MiB.

#### Scenario: Defaults

- **WHEN** a repository sets only `type` and `bucket`
- **THEN** the authentication is `adc`, the prefix and the endpoint are empty, and the chunk size is 8 MiB

#### Scenario: Invalid chunk size

- **WHEN** `chunkSize` is not a multiple of 256 KiB
- **THEN** the repository is not loaded and the reported failure names `chunkSize`

#### Scenario: Missing bucket

- **WHEN** `bucket` is empty
- **THEN** the repository is not loaded and the reported failure names `bucket`

#### Scenario: Unknown authentication mode

- **WHEN** `authentication` is neither `adc` nor `service-account-key`
- **THEN** the repository is not loaded and the reported failure names `authentication`

### Requirement: Authentication modes

With `authentication` set to `adc`, the plugin SHALL use Application Default Credentials and SHALL refuse a non-empty
`credentialsJson`. With `service-account-key`, the plugin SHALL use the key in `credentialsJson`, which MAY be a secret
reference or a file reference. No credential SHALL appear in a log, a status response, a problem body or the support bundle.

#### Scenario: Application Default Credentials

- **WHEN** `authentication` is `adc` and no `credentialsJson` is set
- **THEN** the client uses the credentials the Google environment provides, including workload identity

#### Scenario: Leftover key with ADC

- **WHEN** `authentication` is `adc` and `credentialsJson` is not empty
- **THEN** the repository is not loaded and the reported failure names both keys

#### Scenario: Key not echoed

- **WHEN** a service account key is invalid
- **THEN** the error message and the log do not contain any part of the key

### Requirement: Emulator endpoint guard

The `endpoint` setting SHALL be used only when the process-wide variable `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT` (fallback
`REPOSILITE_GCS_ALLOW_EMULATOR_ENDPOINT`) is `true`, read through `StorageProviderContext.environment`. The variable SHALL default to
`false`, and any value other than `true` or `false` SHALL keep every `gcs` repository from loading, with a message that names the variable.

#### Scenario: Endpoint without the switch

- **WHEN** `endpoint` is set and the switch is unset
- **THEN** the repository is not loaded and the reported failure names `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT`

#### Scenario: Endpoint with the switch

- **WHEN** `endpoint` is set and the switch is `true`
- **THEN** the client sends its requests to the endpoint

#### Scenario: Invalid switch value

- **WHEN** the switch is set to a value other than `true` or `false`
- **THEN** no `gcs` repository is loaded, the reported failure names the variable, and repositories of other types are not affected

### Requirement: Streamed uploads visible only when finalised

`putFile` SHALL stream the content without a full spool to disk. Content that fits in one chunk SHALL be sent in one request
with its exact length. Longer content SHALL be sent as a resumable upload of `chunkSize` chunks. An object SHALL become visible
only when its upload is finalised, and a reader SHALL never see a partial object.

#### Scenario: Small file

- **WHEN** a file of 1 KiB is written
- **THEN** one request is sent and the file is readable with the same bytes

#### Scenario: Large file

- **WHEN** a file of 20 MiB is written with the 8 MiB chunk size
- **THEN** the content is sent as resumable chunks and the file is readable with the same bytes

#### Scenario: Interrupted upload

- **WHEN** an upload fails before it is finalised
- **THEN** no object is created at the location, the write reports a failure, and the session is left to expire

### Requirement: Ranged reads

`getFile` with an offset and a length SHALL read only the requested range, through a ranged channel. A zero length SHALL return
an empty stream without a request. An offset at or beyond the end of the object SHALL return the empty answer of the storage contract
kit, and a 416 of the service SHALL NOT reach the caller as an error.
A range that ends past the object SHALL be truncated to the end of the object.

#### Scenario: Range inside the object

- **WHEN** bytes 10 to 19 of a 100-byte object are requested
- **THEN** exactly those 10 bytes are returned

#### Scenario: Zero length

- **WHEN** a length of zero is requested
- **THEN** an empty stream is returned and no read request is sent

#### Scenario: Offset past the end

- **WHEN** the offset is 100 for a 100-byte object and the length is 1
- **THEN** an empty stream is returned and the result is not an error

#### Scenario: Range past the end

- **WHEN** a range of 50 bytes starts at offset 80 of a 100-byte object
- **THEN** 20 bytes are returned

### Requirement: Directories and metadata

A location with an object SHALL report its size and last modification time from the object metadata. A location without an
object SHALL be reported as a directory when at least one object lies below it. A directory without any object SHALL be
`NOT_FOUND`. The repository root SHALL always be a directory.

#### Scenario: Object metadata

- **WHEN** the details of an existing object are requested
- **THEN** the size and the update time of the object are returned

#### Scenario: Directory from prefix

- **WHEN** a location has no object but objects lie below it
- **THEN** the location is reported as a directory with its files

#### Scenario: Empty directory

- **WHEN** no object lies below a location
- **THEN** the result is `NOT_FOUND`

#### Scenario: Repository root

- **WHEN** the details of the repository root are requested in an empty bucket
- **THEN** the root is reported as an empty directory and not as a file

### Requirement: Native paging with a path cursor

The provider SHALL implement `StorageEntrySource` natively, with a page size of at most 1000 objects and the root-relative
path as the cursor. The cursor SHALL be exclusive: the entry whose path equals the cursor SHALL NOT appear on the next page.

#### Scenario: Page boundary

- **WHEN** a listing of five objects is read in pages of two
- **THEN** the pages contain the objects in order, each object exactly once, and the last page has no next cursor

#### Scenario: Cursor is exclusive

- **WHEN** the cursor is the path of an existing object
- **THEN** that object is not part of the returned page

### Requirement: Removal

`removeFile` SHALL refuse the repository root with a bad request before any call is made. A prefix SHALL be removed by deleting
its objects. The removal is not atomic, and a partial failure SHALL be reported as a failure.

#### Scenario: Root refused

- **WHEN** the repository root is removed
- **THEN** a bad request is returned and no object is deleted

#### Scenario: Partial failure

- **WHEN** one object of a prefix cannot be deleted
- **THEN** the failure is reported and the remaining objects stay in place for a later removal

### Requirement: Errors are not empty listings

A 404 from the service SHALL become `NOT_FOUND` for the requested location. A 401 or 403 SHALL be reported through the failure
facade and SHALL be returned as a 403 error, and it SHALL NOT be reported as `NOT_FOUND`. Retries of 429 and 5xx SHALL use the
client defaults.

#### Scenario: Missing object

- **WHEN** a 404 is returned for a requested object
- **THEN** the result is `NOT_FOUND` for that location

#### Scenario: Missing permission

- **WHEN** a 403 is returned for a listing
- **THEN** the result is a 403 error, the failure facade records it, and the repository is not reported as empty

#### Scenario: Error text is redacted

- **WHEN** a service error text contains the credential or the endpoint's userinfo
- **THEN** the mapped message contains neither

### Requirement: Capabilities

The provider SHALL report usage as `-1` and capacity as `Long.MAX_VALUE` through `UsageCapability`. It SHALL declare the shared
namespace key `gcs:<bucket>/<prefix>` through `SharedNamespaceAware` on its settings type, and it SHALL report the bucket, the project and the
authentication mode through `SupportDetails` without any credential.

#### Scenario: Shared namespace

- **WHEN** two repositories declare the same bucket and prefix
- **THEN** core refuses both repositories with the message of the settings

### Requirement: Readiness

The provider's `ReadinessProbe` SHALL answer `Ok` for a bucket that answers, empty or not, and SHALL answer `Failed` with its own reason for
a missing permission, a missing bucket and an unreachable bucket. It SHALL NOT run on the request path.

#### Scenario: Readiness with missing permission

- **WHEN** the readiness probe receives a 403 for the bucket
- **THEN** the probe reports that the permission is missing and does not report the bucket as empty

#### Scenario: Readiness timeout

- **WHEN** the bucket does not answer within the probe timeout
- **THEN** the probe reports the bucket as unreachable

### Requirement: Consistency and concurrent writers

The provider SHALL rely on the strong consistency of Google Cloud Storage for reads after writes, overwrites, deletes and listing
of new objects. Two writers of the same key SHALL resolve as last writer wins, as with the `s3` provider. The provider SHALL NOT use
a conditional create in version one.

#### Scenario: Read after write

- **WHEN** a file is written and read back at once
- **THEN** the read returns the written bytes

#### Scenario: Concurrent writers

- **WHEN** two replicas write the same key
- **THEN** the object holds the content of the later write and no error is raised by the provider

### Requirement: Test requirements

The provider SHALL have unit tests behind a thin adapter with a fake, which need no network and no system clock. The contract cases
SHALL run against `fsouza/fake-gcs-server` in a container pinned by tag and digest, with the client pointed at the emulator endpoint
and `NoCredentials`. A case the emulator does not support SHALL be skipped with a reason that names the feature.

#### Scenario: Unit tests offline

- **WHEN** the unit tests run without network access
- **THEN** every test passes

#### Scenario: Unsupported emulator feature

- **WHEN** the emulator does not support resumable uploads
- **THEN** the affected cases are skipped, and the test report names the feature as the reason

#### Scenario: Contract cases on the emulator

- **WHEN** the integration tests run against the emulator
- **THEN** every case that the emulator supports passes with the same expected values as the `fs` and `s3` providers

### Requirement: Documentation and step 0

The documentation SHALL contain a how-to `docs/gitbook/how-to/use-google-cloud-storage.md` with the install steps, a GKE workload
identity example, a settings example, the recorded step 0 result, and the limits. Every new key and the process-wide variable SHALL
appear in the matching reference page. The interoperability note of `use-s3-storage.md` SHALL link to the how-to.

#### Scenario: Reference complete

- **WHEN** the documentation is reviewed
- **THEN** every key of the settings table and the process-wide variable appear in the reference pages with their defaults

#### Scenario: Step 0 recorded

- **WHEN** the proposal is reviewed before the proposal pull request is opened
- **THEN** the proposal states the result of the interoperability run per test, or states that it is pending

### Requirement: Plugin store distribution

The plugin SHALL be listed in the plugin store of `add-plugin-store` as a signed catalogue entry, selectable with `INGOT_PLUGINS` or in the dashboard. A jar installed by hand in `plugins/` SHALL win over a store entry with the same id.

#### Scenario: Plugin selected from the store

- **WHEN** the store entry is selected with `INGOT_PLUGINS` or in the dashboard and a repository sets `type` to `gcs`
- **THEN** the repository uses the provider of this plugin, as when the jar is installed by hand

#### Scenario: Manual jar and store entry share an id

- **WHEN** the jar is installed by hand in `plugins/` and a store entry with the same id is selected
- **THEN** the manual jar is loaded and the store entry is listed as shadowed

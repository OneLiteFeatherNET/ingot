# Design

This design depends on `add-storage-provider-registration` (factories discovered from plugin jars, the
`IngotStorageProviderFactory` type) and `add-storage-capability-hooks` (the capability interfaces `UsageCapability`,
`SharedNamespaceAware`, `SupportDetails` and `ReadinessProbe`). Names below are those of those changes.

## Context

- The SPI is `com.reposilite.storage.StorageProvider`: `putFile`, `getFile` (with a ranged overload), `getFileDetails`,
  `removeFile`, `getFiles`, `getLastModifiedTime`, `getFileSize`, `exists`, `usage`, `canHold` and `shutdown`. The
  factory contract is `StorageProviderFactory<PROVIDER, SETTINGS>` with `type`, `settingsType` and `create(journalist,
  failureFacade, workingDirectory, repositoryName, settings)`.
- Today `StorageProviderSerdes` reads its discovery map from `ServiceLoader.load(StorageProviderFactory::class.java)` once,
  in a companion object. A factory inside a plugin jar, which the plugin loader places in its own `URLClassLoader`, is not
  in that map. The registration change fixes this, and this plugin depends on that fix.
- The `s3` provider is the closest model. Its settings (`S3StorageProviderSettings`) carry `type`, the bucket, a prefix and
  sensitive keys. Its `putStreamed` reads one part ahead to decide between a single request and a multipart upload, and its
  `S3EntrySource` pages with `startAfter`. This design keeps both patterns.
- The image sets no `USER` and the entrypoint handles privileges, so nothing in the plugin depends on the process user.
- HA: several replicas share one database. Storage is per repository, and every replica creates its own client.

## Decisions

### D1: client library, transport and shading

- The plugin depends on `com.google.cloud:google-cloud-storage`, with the version taken from `com.google.cloud:libraries-bom`
  at implementation time. The licence is Apache 2.0 (verified on the `googleapis/java-storage` repository). That repository
  states that it is deprecated and that its content moved to `googleapis/google-cloud-java`; the coordinates are confirmed
  at implementation time (open question 5 of the proposal).
- The transport is JSON over HTTP. The plugin never builds a gRPC transport, and a unit test asserts that the storage options
  carry the HTTP transport, so the gRPC stack never enters the runtime path.
- Guava, protobuf and gax are relocated to `net.onelitefeather.ingot.storage.gcs.shaded.*` in the shaded jar. The jar size
  is measured twice: once with the relocation and once without, and both numbers go into the how-to page. The plugin does not
  bundle the server's Jackson or Javalin; those are compile-only, as in the OpenSearch plugin.
- The plugin is a separate Gradle subproject with `com.gradleup.shadow`, following `reposilite-plugins/opensearch-plugin`.
  Its jar is published as `net.onelitefeather.ingot:gcs-storage-plugin`.

### D2: settings, secrets and the process-wide variable

Per repository, in the `storageProvider` block of `configuration.shared.json`:

| Key | Default | Meaning |
|---|---|---|
| `type` | `gcs` | Always written. Selects the factory. |
| `projectId` | empty | Optional project for billing and quota. Empty leaves the client default. |
| `bucket` | empty | Required. The bucket name, validated as a bucket name when the repository starts. |
| `prefix` | empty | Optional object name prefix. Leading and trailing slashes are trimmed, as in `resolveKeyPrefix`. |
| `endpoint` | empty | Emulator only. Used only when the process-wide switch below is `true`. |
| `authentication` | `adc` | `adc` or `service-account-key`. Any other value stops the repository start and names the key. |
| `credentialsJson` | empty | Sensitive. The service account key, required when `authentication` is `service-account-key`. A secret reference or file reference is accepted through `add-settings-secret-references` and `add-file-secrets`. |
| `chunkSize` | `8388608` (8 MiB) | Bytes per resumable chunk. Must be a multiple of 256 KiB (verified, see the vendor claim check below), at least 256 KiB and at most 64 MiB. |

Rules:

- Every secret is annotated `@get:Sensitive`. Settings are never written to a log, a status response, a problem body or the
  support bundle in clear. The credential key is masked with `maskSecret` where the factory logs its configuration.
- `endpoint` is refused, naming `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT`, unless the process-wide switch is `true`. An `endpoint`
  without the switch stops the start of that repository only. The `s3` `endpoint` key is not sensitive; the GCS key is not
  sensitive either, because it names a host, not a credential.
- `authentication: "adc"` does not read `credentialsJson`. A non-empty `credentialsJson` with `adc` is refused, so that a
  leftover key is not silently ignored.
- The process-wide variable `INGOT_GCS_ALLOW_EMULATOR_ENDPOINT` (fallback `REPOSILITE_GCS_ALLOW_EMULATOR_ENDPOINT`, `INGOT_`
  wins) accepts `true` or `false` and defaults to `false`. It is read through `StorageProviderContext.environment` of
  `add-storage-provider-registration`, never through `System.getenv`. Any other value makes the factory throw for every `gcs`
  repository, with a message that names the variable.
- `GOOGLE_APPLICATION_CREDENTIALS` is read by the client itself, as the Google documentation describes. It is not an Ingot
  variable and it is not renamed.

The schema for the dashboard form comes from the settings data class, through `StorageProviderSerdes`, so the form shows the
keys of this table with the plugin installed and no others.

### D3: factory and registration

- `GcsStorageProviderFactory` implements `IngotStorageProviderFactory<GcsStorageProvider, GcsStorageProviderSettings>` with
  `type = "gcs"` and `settingsType = GcsStorageProviderSettings::class.java`.
- `apiVersion` is `STORAGE_PROVIDER_API_VERSION` (1), and the factory opts in with `@OptIn(ExperimentalIngotStorageApi::class)`.
- The factory is listed in the jar's `META-INF/services/com.reposilite.storage.StorageProviderFactory`. The registry of
  `add-storage-provider-registration` finds it after every plugin's `load()` and before the first `initialize()`; the plugin does not
  register anything from its own `initialize`. A duplicate type from another plugin is refused with a warning that names both sources,
  as that change specifies.
- Without the jar, a repository of type `gcs` is `UNAVAILABLE` with the reason `storage provider 'gcs' is not installed`. A setting
  error makes the factory throw, and the registration rule for a failing factory applies: the failure is reported and the repository
  is not loaded.
- The plugin declares no dependency on the search plugins and no bundled switch. Its plugin id is `gcs-storage`.

### D4: writes

The method `putFile(location, inputStream)`:

- Reads up to `chunkSize` bytes first, as `S3StorageProvider.putStreamed` does. If the stream ends inside that first chunk,
  the provider sends one request with the exact content length.
- Otherwise it opens a resumable upload through a `WriteChannel` with the configured `chunkSize`, copies the stream in
  chunks, and closes the channel. The object becomes visible only when the upload is finalised. The documented atomicity rule
  states that an object becomes visible only once the entire upload has completed (verified on the consistency page, which
  does not name resumable uploads; the resumable case is confirmed by the emulator test and by step 0 on a real bucket).
- No full spool to disk. Memory use is one chunk per concurrent upload.
- A failure before the channel closes is reported as a write failure. The resumable session is abandoned and expires on the
  service side; no object is created. The JSON API cancels a resumable session with `DELETE` on the session URI (verified, see the
  vendor claim check below); the provider sends that request on failure; if the request fails, the session expires one week after it
  was started. The client library method for cancel is not named in the documentation and is confirmed at implementation time.
- The content type is derived from the extension, as the `s3` provider does.

### D5: reads, ranges and directories

- `getFile(location)` opens a `ReadChannel` and returns its stream. A missing object is `NOT_FOUND`.
- `getFile(location, offset, length)` opens a `ReadChannel`, calls `seek(offset)` and `limit(offset + length)` (`limit(long)` is a
  documented method of `ReadChannel` and takes an absolute end position, verified below; the pinned client version is confirmed at
  implementation time), so that the skipped bytes are never transferred. The override replaces the default in `StorageProvider`.
- `length == 0` returns an empty stream without a request. `offset` at or beyond the object size returns the empty answer of the
  storage contract kit of `add-storage-provider-registration` (D9), not an error; a 416 of the service is caught and never reaches
  the caller. This matches the Azure provider. A range that ends past the object is truncated to the end, as an HTTP range is. Each case is a
  unit test against the fake and an integration test against the emulator.
- `getFileDetails`: an object gives a `DocumentInfo` from its size and `updateTime`. A location without an object is checked as a
  directory prefix with one listing request (`pageSize` 1). A directory prefix with no object below it is `NOT_FOUND`
  (open question 6). The repository root, the empty location, is always a directory and never a HEAD on the prefix key.
- `getLastModifiedTime` and `getFileSize` read the object metadata, with `updateTime` and `size`.
- `exists` is `getFileDetails` without the error text.

### D6: listing and native paging

- `getFiles(location)` lists with the directory prefix and the delimiter `/`. Objects become `Location` values and common
  prefixes become directory entries.
- `StorageEntrySource.listEntries(prefix, after, limit)` is implemented natively on the provider, so that the provider does
  not fall back to `WalkingEntrySource`. The request lists the directory prefix with no delimiter (a flat listing), with the
  `startOffset` set to the root-relative cursor plus the key prefix, and with a page size of `min(limit, 1000)`.
- GCS `startOffset` is inclusive, while the SPI cursor `after` is exclusive. The implementation therefore drops the entry
  whose path equals `after` before it builds the page. A unit test pins this, because an off-by-one here repeats one file on
  every page boundary.
- The next cursor is the root-relative path of the last object of a truncated page. The SDK pages through `pageToken`
  inside one call, so no page token leaves the provider and the cursor stays path-based, as the SPI requires.
- `paginate` (the shared helper of `StorageEntrySource.kt`) computes `next`, as it does for the other natively paged providers.

### D7: removal

- `removeFile(location)` refuses the repository root with a bad request, as the `s3` provider does, before any call is made.
- A file is removed with a per-object delete. A prefix is removed by listing its objects and deleting them, in pages of at
  most 1000 objects, with a per-object delete request. The removal is not atomic, and the how-to says so. A partial failure
  reports the error and leaves the remaining objects in place; a later removal finishes the job.
- The client batch API is not used in version one.

### D8: errors and retries

- 404 becomes `NOT_FOUND` for the object or prefix that was requested.
- 401 and 403 are reported through `failureFacade` as a failure, with a message that names the bucket and the permission
  needed, and they are returned as a 403 error response. They are deliberately not `NOT_FOUND`, so that a missing permission
  is never mistaken for an empty repository. The message names the needed role, `roles/storage.objectUser` (open question 3).
- 429 and 5xx use the client's default retry policy. The plugin adds no retry layer of its own.
- Any other exception is reported through `failureFacade.throwException` and returned as an internal server error, as the `s3`
  provider does.
- Error texts never contain the credential, the key or the endpoint's userinfo, and a unit test checks the redaction of the
  mapped messages.

### D9: capabilities and readiness

- `usage()` returns `-1` and `canHold(contentLength)` returns `Long.MAX_VALUE`, through `UsageCapability`, as the `s3` provider
  does. GCS has no quota that the plugin reads.
- `SharedNamespaceAware` and `SupportDetails` sit on the settings type, as `add-storage-capability-hooks` decides (its D2);
  `UsageCapability` and `ReadinessProbe` sit on the provider.
- `SharedNamespaceAware` declares the key `gcs:<bucket>/<prefix>`, so that core refuses both repositories that write to the same
  namespace (`add-storage-capability-hooks`, D4).
- `SupportDetails` reports the bucket, the project, and the authentication mode (`adc` or `service-account-key`). It never reports
  the key, the endpoint's userinfo or the token.
- `ReadinessProbe` makes one bounded bucket metadata request with a timeout of 10 seconds and maps the answer onto
  `ReadinessOutcome`: a bucket that answers is `Ok`; every other answer is `Failed(reason)`: a 403 is reported as "no permission on the bucket" and not as "empty"; a 404 is reported as
  "bucket missing"; a timeout is reported as "unreachable". The probe never runs on the request path.
- There is no metadata cache in version one.

### D10: consistency and HA

- The consistency page states that writes are strongly consistent, that a successful delete is visible at once, and that object
  listing is strongly consistent for a newly created object (verified 2026-11-18, see the vendor claim check below). The plugin relies on this for the read-after-write of a
  deploy and for listing after a deploy.
- Two replicas that write the same key: the last writer wins, as with `s3`. No conditional create with
  `ifGenerationMatch=0` is used in version one. Coordination of concurrent writers stays in the database, which is the single
  source of the outbox and the metadata.
- Each replica creates its own client. The clients share no state.

### D11: testing

- Unit tests run with no network and no clock read. The client is hidden behind a thin adapter interface `GcsObjectClient`
  with the operations the provider needs (create, open for read, get metadata, list, delete, get bucket). The production
  adapter wraps the SDK; the tests use an in-memory fake. Every SPI method has a test in the fake.
- The settings rules of D2 are unit tested: the defaults, the `type` written, the `chunkSize` multiple, the `authentication`
  values, the refused `endpoint` without the switch, the refused `credentialsJson` with `adc`, the switch values and its
  fallback.
- Integration tests run the storage contract kit against `fsouza/fake-gcs-server` (BSD-2-Clause, verified) started as a
  `GenericContainer` pinned by tag and digest, through `heavy`, in the plugin module's own `integration` source set (applied with
  `com.coditory.integration-test` as in `reposilite-backend`). The SDK points at the emulator endpoint with `NoCredentials`,
  and the test project id is fixed.
- The emulator's support of resumable uploads and of `startOffset` is not confirmed by the project page. The spike in task 0.3
  checks both. A case the emulator does not support is skipped with a reason that names the feature, through an assumption that
  appears in the test report. It is never removed silently.
- No `Thread.sleep`. Waits for the container use the container's own wait strategy. The clock is injected into the readiness
  probe, so the timeout is tested without a system clock.
- Step 0 runs the existing `s3` integration tests and, where present, the contract kit against a real bucket through
  interoperability, and records the result in the proposal.

## Risks

- **Shading.** Relocation of Guava, protobuf and gax is the largest source of error: a missed package fails at run time, not at
  compile time. The integration tests run from the shaded jar, not from the classpath, to catch that.
- **Emulator divergence.** `fake-gcs-server` may differ from the service in resumable uploads, listing and error bodies. The
  real-bucket check of step 0 and the manual run in the pull request cover the gap, and skipped cases are listed.
- **Permission scope.** `roles/storage.objectUser` may not work at bucket scope. Until step 0 confirms it, the how-to names
  `roles/storage.objectAdmin` as the fallback.
- **Resumable visibility.** The consistency page states the atomicity rule for objects in general. The resumable case is tested
  and confirmed in step 0 before the code pull request.
- **Upstream repository.** The library's home has moved. A build that follows the old coordinates breaks, so the coordinates are
  confirmed at implementation time (open question 5).
- **Archived objects.** Reads of archived objects fail with the client's error. This is out of scope and documented.

## Vendor claim check (task 0.5, 2026-11-18)

Checked against the current Google Cloud documentation (served from `docs.cloud.google.com`, the former `cloud.google.com` links
redirect there). None of the pages shows a last-updated date, so the date of the check is the one recorded here.

| Claim | Result | Source |
|---|---|---|
| `roles/storage.objectUser` covers create, read, update and delete of objects; lowest grantable level is a managed folder, so a bucket binding is allowed | Confirmed. The table lists managed folder as the lowest resource, which includes bucket scope. The functional check at bucket scope stays with step 0 (task 0.2) | https://docs.cloud.google.com/storage/docs/access-control/iam-roles |
| `roles/storage.objectAdmin` is the fallback role | Confirmed as a broader role with the same lowest resource (managed folder). It adds object IAM policy and retention controls | same page |
| Writes, deletes and listing of a new object are strongly consistent (D10) | Confirmed: an object is readable as soon as the success response arrives, a successful delete returns 404 at once, and a new object appears in a list made right after creation | https://docs.cloud.google.com/storage/docs/consistency |
| An object becomes visible only once the entire upload has completed (D4) | Confirmed for uploads in general. The page does not name resumable uploads; the resumable case stays with the emulator test and step 0 | same page |
| Resumable chunk size is a multiple of 256 KiB (D2, `chunkSize`) | Confirmed: "The buffer size must be a multiple of 256 KiB (256 x 1024 bytes)" | https://docs.cloud.google.com/storage/docs/resumable-uploads |
| A resumable session can be cancelled (D4) | Confirmed. `DELETE` on the session URI returned in the `Location` header, with `Content-Length` 0 (JSON and XML APIs). The client library method is not named on the page | https://docs.cloud.google.com/storage/docs/performing-resumable-uploads |
| Expiry of an abandoned resumable session (D4) | One week after the session is started ("the session URI expires after a week") | both resumable pages |
| `objects.list` `startOffset` is inclusive (D6) | Confirmed: "lexicographically equal to or after `startOffset`", range is `startOffset` inclusive to `endOffset` exclusive | https://docs.cloud.google.com/storage/docs/json_api/v1/objects/list |
| `ReadChannel.limit` exists (D5) | Confirmed in the current client javadoc: `limit(long)` caps reads at an absolute position counted from the start of the object (example: `seek(2)` then `limit(5)` yields bytes 2 to 4), so `limit(offset + length)` is the right call. The javadoc page labels its version 2.73.0; the version that the BOM pin (`libraries-bom` 26.93.0) resolves to is confirmed at implementation time from the dependency tree | https://docs.cloud.google.com/java/docs/reference/google-cloud-core/latest/com.google.cloud.ReadChannel |
| Nexus Repository and Artifactory both offer native GCS blob stores (proposal, "Why") | Partly confirmed, with an edition caveat. Artifactory has a Google Storage binary provider (native client template); on a Pro license the endpoint must end in `.googleapis.com`, and all endpoints need Enterprise. Nexus has an official GCS blob store for Pro deployments (release 3.74.0 notes); Community Edition support is not confirmed, and a community plugin exists outside Sonatype support | https://docs.jfrog.com/installation/docs/google-cloud-storage ; https://community.sonatype.com/t/sonatype-nexus-repository-3-74-0-released/13793 |

Corrections made in this file:

- D4: the cancel question is answered (`DELETE` on the session URI, one-week expiry). The session expiry sentence now states the
  week, so that the how-to page can name it.
- D5: `limit(offset + length)` is confirmed as an absolute end position. The client version is still checked at implementation.
- D4 and D10: the consistency page covers writes in general, not resumable uploads by name. The resumable case stays with the
  emulator test and step 0.

Open after this check: the `google-cloud-core` version that `libraries-bom` 26.93.0 resolves to, and the exact role permission
list (the page gives the description and the grouped permissions, not a full line-by-line list). The Nexus sentence in the
proposal needs the edition caveat in the proposal pull request; this file records it, the proposal is not edited here.

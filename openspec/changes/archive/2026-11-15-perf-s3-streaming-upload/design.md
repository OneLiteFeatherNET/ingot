# Design

## Context

Verified against `origin/main`:

- `S3StorageProvider.putFile` (`reposilite-backend/src/main/kotlin/com/reposilite/storage/s3/S3StorageProvider.kt:101-128`)
  copies the whole stream into `File.createTempFile("reposilite-", "-s3-put")` (the system temporary directory), then calls
  `putObject` once with `RequestBody.fromFile` and `contentLength(temporary.length())`.
- `StorageProvider.putFile(location, inputStream)` (`storage/StorageProvider.kt:29`) has no length parameter. Callers are
  the deploy path (`RepositoryService.kt:89`), metadata writes (`MetadataService.kt:87,107`), checksums (`Repository.kt:95`)
  and mirror copies (`MirrorService.kt:190`). Every S3 upload is therefore a stream of unknown length from the provider's
  point of view.
- `CachableS3Client` (`storage/s3/CachableS3Client.kt:37-41`) invalidates the HEAD cache in `putObject` and `deleteObject`
  only. `DelegatingS3Client` passes every other call through unchanged.
- The S3 client is the synchronous `S3Client` from `software.amazon.awssdk:s3:2.46.3` (`reposilite-backend/build.gradle.kts:125`).
  The comment at `build.gradle.kts:46-47` says the netty runtime arrives with the SDK's `netty-nio-client`, so the
  asynchronous client adds no new jar family.
- The integration backend is Floci (`src/integration/.../SharedContainers.kt:27-29`). MinIO and Ceph RGW are not run in CI.
- No reader of the S3 object ETag exists in `reposilite-backend/src/main`. Maven conditional requests use length and
  modification time (`MavenFacade.kt:99`).

## Decision 1: probe the first part, do not ask for the length

The provider cannot know the length in advance, and the interface is not changed for this. It reads up to one part
(8 MiB). If the stream ends inside that part, the buffered bytes go out in one `PutObject` with their exact length, which is
the request that is sent today. If the part is full, the provider reads one more byte: at the end of the stream the part goes out as a single put, and
otherwise the provider starts a multipart upload and keeps reading.

This keeps every small upload at one request and no extra round trips, and it needs no change to any caller.

## Decision 2: the synchronous client and manual multipart calls

The provider uses the `S3Client` it already holds, with `createMultipartUpload`, `uploadPart`, `completeMultipartUpload` and
`abortMultipartUpload`. It does not add `S3AsyncClient` or `S3TransferManager`, for three reasons:

- The `StorageProvider` contract is blocking, and the existing `CachableS3Client` and retry configuration wrap the sync
  client. A second, asynchronous client would need its own wrapper and its own retry and invalidation rules.
- The netty runtime is already on the classpath through the `s3` artifact, so the asynchronous client would not save a jar.
- A sequential loop holds exactly one part at a time. The async path would allow several parts in flight, and memory would
  scale with that.

The open verification from the roadmap (async streaming, jar footprint, Ceph RGW behaviour) is answered by this choice for
the first two points. Ceph RGW behaviour is checked by task 4.3.

## Decision 3: part size and ceiling

The part size is 8 MiB. S3 requires at least 5 MiB for every part except the last and allows 10,000 parts, so one object can
be up to 80 GiB. A single `PutObject` was capped at 5 GiB, so the ceiling rises. An upload that reaches 10,000 parts fails
with the same generic write error, and the multipart upload is aborted. Growing the part size for very large objects is not
done here; it is a follow-up if an operator needs more than 80 GiB.

Memory: one part buffer is held per in-flight upload. This is 8 MiB of heap for each concurrent upload. The escape hatch
`INGOT_S3_BUFFER_TO_DISK=true` removes that cost and restores the temporary file path.

## Decision 4: invalidate the HEAD cache on completion

`CachableS3Client` gets an override of `completeMultipartUpload` that invalidates the key and applies the same
`maven-metadata.xml` rule as `putObject`. Invalidation on each part is not needed, because no part is visible as an object
until completion. Abort writes nothing, so it does not invalidate.

Without this override, a HEAD answer cached before a multipart upload would survive it. The existing single-put path
would not show that bug, which is why the test for it must use a multipart upload.

## Decision 5: failure and abandoned uploads

- A failed part or completion calls `abortMultipartUpload`. If the abort fails, the failure is logged with its exception and
  the original generic error is still returned.
- A process killed mid-upload cannot abort. Those parts stay in the bucket, invisible to listings, and cost storage. The
  documentation gives an `AbortIncompleteMultipartUpload` lifecycle rule as the fix. No startup sweep is added here.

## Decision 6: the escape hatch is a process setting

`INGOT_S3_BUFFER_TO_DISK` is read once when the provider is created, through a reader that takes the environment as a
parameter, so tests pass a map. The name follows the drop-in rule: `INGOT_` first, `REPOSILITE_` as a fallback. It is not a
repository setting. S3 repositories are stored in the database and the new behaviour should not depend on which repository
was created when.

## Risks

- Storage providers that accept multipart uploads only partly (some GCS and older compatible services) can fail on the
  first multipart upload. The escape hatch is the operator fallback, and the documentation says so.
- The default flips only if the spike in task 2.2 passes. Otherwise the default stays on the temporary file path.

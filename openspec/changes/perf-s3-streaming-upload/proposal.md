# Proposal

## Why

A deploy to a repository that keeps its files in S3 first copies the whole artifact onto the server's disk, then uploads that
copy in one request. A large jar therefore needs its full size twice: once in the temporary directory of the instance, and
again in the bucket. The copy also has to finish before the first byte leaves the server, so a slow disk delays every
deploy by the time it takes to write the file.

In a Kubernetes pod the temporary directory is usually an `emptyDir` or the container's writable layer, sized for the
working set and not for artifacts. A deploy of a few gigabytes can fill it, and the upload then fails with a message that
does not say why. The bucket itself has no such limit: a single upload request is capped at 5 GiB by S3, and a multipart
upload is the way past that cap.

The upload path has no reason to touch the disk. The provider can send the bytes as they arrive, in parts, and keep only one
part in memory at a time.

## What Changes

- **Streaming upload.** `S3StorageProvider.putFile` reads the first 8 MiB of the stream. When the stream ends before that
  point, the bytes go to S3 in one `PutObject` with their exact length, as they do today, but without a temporary file. When
  the stream is longer, the provider starts a multipart upload and sends each 8 MiB part as it is read. The last part may be
  smaller. The content type is set on the upload as it is on a single put.
- **Clean failure.** When a part or the completion fails, the provider aborts the multipart upload, so no incomplete upload is
  left behind by a failure Ingot can see. The client receives the same status and generic message as a failed write today.
- **Cache coherence.** The client wrapper that holds the HEAD cache invalidates the key when a multipart upload completes, as
  it already does after a single put. Without that, an entry cached before the upload would survive it.
- **Escape hatch.** `INGOT_S3_BUFFER_TO_DISK` (fallback `REPOSILITE_S3_BUFFER_TO_DISK`) restores the temporary file path for
  every upload when it is `true`. The default is `false`. The setting is read once when the provider is created, and an
  unreadable value stops the start with the name of the variable.
- **Documentation.** `docs/gitbook/how-to/use-s3-storage.md` describes the multipart behaviour, the 80 GiB object ceiling and
  the lifecycle rule that removes incomplete uploads left by a killed process. `docs/gitbook/reference/environment-variables.md`
  lists the new variable. `docs/gitbook/reference/upgrade-changes.md` notes that S3 uploads no longer use the temporary
  directory.

### Conditional on a spike

The default switch is conditional on task 2.2. The spike runs the S3 integration tests against Floci (the container the
repository already uses) and against MinIO, and checks that a multipart upload completes and a failed one leaves nothing
behind. If either run fails, the default becomes `true` (the temporary file path), and streaming is reached by setting the
variable to `false`. Ceph RGW and GCS interoperability are checked by hand before the release note is written. The GCS
interop note in the documentation says to set the escape hatch if multipart uploads fail there.

### Out of scope

- A staged upload SPI, resumable uploads across restarts, and upload sessions. Those belong to the format SPI (Strategic).
- Presigned or redirected downloads, and any change to reads, listings or the HEAD cache policy beyond the invalidation above.
- Per-repository settings for the escape hatch. It is a process setting, like the other S3 transport settings.
- Retry policy, retry counts and the AWS SDK version. The retry jitter change belongs to `fix-s3-head-cache`.
- Changes to the filesystem provider and to the Maven deploy and mirror logic. Mirror copies to S3 take the new path because they
  call the same `putFile`, and nothing else changes for them.

## Capabilities

### New Capabilities

- `s3-streaming-upload`: how an upload of unknown length reaches an S3 bucket, how it fails, and when the temporary file path is
  used.

## Impact

- **Type and scope.** The change ships under `perf(storage)`. The tests go in their own commits as `test(storage)`. The pull
  request title, squash-merged onto main, is:

  `perf(storage): stream s3 uploads as multipart instead of a temp file`

- **Breaking marker.** No breaking change. No configuration key has to be set, no volume has to change and no manifest or
  plugin has to change. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer. The new variable is
  optional and the old behaviour is one value away.
- **Behaviour that changes by default.** Uploads above 8 MiB go out as multipart uploads and no longer pass through the
  temporary directory. The bytes, the key, the content type and the status codes do not change. Ingot does not read the S3
  object ETag anywhere on main (`reposilite-backend/src/main` has no reader), so the ETag format of a multipart object does
  not reach clients. The object ceiling rises from 5 GiB to 80 GiB.
- **Memory.** Each in-flight upload holds one 8 MiB part in memory. The chart's default heap is small (`-Xmx64M`,
  `charts/ingot/values.yaml:177`), so a heap that small holds only a few concurrent uploads. The escape hatch covers that
  case.
- **Dependencies on other changes.** `fix-s3-head-cache` (wave 1, on `docs/openspec-wave1`) also changes `CachableS3Client`
  and the `storage/s3` package. Whichever change merges second rebases on the other. The retry change in that proposal does
  not affect this one.
- **Code.** `reposilite-backend`: `storage/s3` (`S3StorageProvider`, `CachableS3Client`, the new settings reader) and the S3
  integration tests. `docs/gitbook/`.

## Verification

- Red tests first for the single put, the multipart path, the abort path, the cache invalidation and the setting, each seen to
  fail before the production code.
- Targeted runs through `heavy` with `--max-workers=2`: unit tests for the storage package, and
  `:reposilite-backend:integration --tests 'com.reposilite.storage.S3StorageProviderIntegrationTest'`.
- `openspec validate perf-s3-streaming-upload --strict` passes.
- `git grep -nP '[\x{2013}\x{2014}]' -- openspec/changes/perf-s3-streaming-upload` prints nothing.

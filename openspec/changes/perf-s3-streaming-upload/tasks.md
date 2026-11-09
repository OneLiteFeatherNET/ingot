# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests (`test(storage)`) are written first and seen to
fail before the production code (`perf(storage)`) exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no shared mutable state, a temporary directory per test where a file is involved, the environment passed in as a map,
and assertions with messages. A fake `S3Client` (a `DelegatingS3Client` that records calls) stands in for S3 in unit tests.
Gradle runs go through `heavy` with `--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`. Kind or Testcontainers runs happen one at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose perf-s3-streaming-upload`: this change and nothing else. Verify:
  `openspec validate perf-s3-streaming-upload --strict` passes and the dash grep from the proposal prints nothing.

## 2. Spike and red tests

- [ ] 2.1 `test(storage)`: write failing unit tests against a recording fake `S3Client`: an upload of 20 MiB of unknown length
  is sent as one `putObject` with a temporary file body today, so the test asserts the multipart calls and fails. Add the
  small-upload test (1 KiB, one put, exact length) and the exactly-8-MiB test, which pass today and pin the behaviour to keep.
  Verify: the large-upload test fails with a message that names the missing multipart call.
- [ ] 2.2 Spike, no commit unless it leads to a change: run the S3 integration tests against Floci and against a MinIO container
  started by hand, with a 20 MiB upload and a forced part failure. Record in the pull request whether a multipart upload
  completes and whether a failed one leaves no incomplete upload (`listMultipartUploads` is empty). Also record the runtime
  classpath of `software.amazon.awssdk:s3` through `heavy` with `--no-daemon --max-workers=2`, to confirm that netty
  arrives with the `s3` artifact. If Floci does not support multipart, the gate in 5.4 applies.

## 3. Multipart upload path

- [ ] 3.1 `test(storage)`: write failing tests for `S3StorageProvider.putFile`: a part failure calls `abortMultipartUpload`
  and returns the generic failed write with no exception text; a completion failure aborts too; the abort failing is logged
  through a captured appender and the generic error is still returned; the content type is set on `createMultipartUpload`;
  the stream is closed in every case; parts are 8 MiB in order with a smaller last part; a stream of exactly 8 MiB is one put.
  Verify: they fail before 3.2.
- [ ] 3.2 `perf(storage)`: implement the probe-and-multipart upload in `S3StorageProvider.putFile`, replacing the temporary
  file for the default path. Keep the existing `failed("write $location", ...)` message. Verify: the tests of 2.1 and 3.1 pass,
  and the existing `S3StorageProvider` unit tests pass unedited.
- [ ] 3.3 `test(storage)`: write a failing test in `CachableS3ClientTest` that a HEAD answer cached for a key is gone after
  `completeMultipartUpload` to that key, and that a `maven-metadata.xml` answer in the parent directory is removed too. A
  second test shows that `createMultipartUpload` and `uploadPart` do not invalidate. Verify: the first fails before 3.4.
- [ ] 3.4 `perf(storage)`: override `completeMultipartUpload` in `CachableS3Client` with the invalidation that `putObject` uses.
  Verify: the tests of 3.3 pass, and the existing cache tests pass unedited.

## 4. Escape hatch and integration

- [ ] 4.1 `test(storage)`: write failing tests for the settings reader that takes the environment as a map: `INGOT_S3_BUFFER_TO_DISK=true`
  selects the temporary file path; `REPOSILITE_S3_BUFFER_TO_DISK=true` alone selects it too; `INGOT_` wins over `REPOSILITE_`; the
  default is `false`; `yes` stops with an error that names `INGOT_S3_BUFFER_TO_DISK`. Verify: they fail on the missing reader.
- [ ] 4.2 `perf(storage)`: add the reader and pass its value into `S3StorageProviderFactory` (process-wide, read once). When the
  value is `true`, `putFile` uses the temporary file path unchanged. Verify: the tests of 4.1 pass.
- [ ] 4.3 `test(storage)`: add integration tests to `S3StorageProviderIntegrationTest` against Floci, one bucket per test: a
  20 MiB upload reads back byte for byte (SHA-1 compared); a 5 MiB plus one byte upload uses two parts; a forced part failure
  leaves no incomplete upload. Verify: `:reposilite-backend:integration --tests 'com.reposilite.storage.S3StorageProviderIntegrationTest'`
  passes through `heavy`. Ceph RGW is checked by hand against a Rook cluster before the release note is written; the result
  goes in the pull request.

## 5. Documentation and default

- [ ] 5.1 `docs(storage)`: update `docs/gitbook/how-to/use-s3-storage.md`: the multipart behaviour, the 80 GiB ceiling, the
  `AbortIncompleteMultipartUpload` lifecycle rule for killed processes, and the GCS note that says to set the escape hatch when
  multipart uploads fail. Verify: the page names the variable exactly as the code reads it.
- [ ] 5.2 `docs(storage)`: add `INGOT_S3_BUFFER_TO_DISK` (fallback `REPOSILITE_S3_BUFFER_TO_DISK`) to
  `docs/gitbook/reference/environment-variables.md` and mention the change in `docs/gitbook/reference/storage-providers.md`.
- [ ] 5.3 `docs(storage)`: note in `docs/gitbook/reference/upgrade-changes.md` that S3 uploads no longer use the system temporary
  directory, and that operators can drop any sizing made for it. No setting is required.
- [ ] 5.4 Decision gate from 2.2, no commit unless it fails: if the spike failed, change the default in 4.2, the spec scenario
  and the docs to `true` (the temporary file path) in one `perf(storage)` commit before the code pull request is opened, and say
  so in the pull request. Otherwise this task is skipped.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `perf(storage): stream s3 uploads as multipart instead of a temp file`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (a deploy that
  needed the artifact's size twice on the server, and a pod disk that filled) without class names. It carries no session link
  and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and
  every commit, and the body has no em or en dash.

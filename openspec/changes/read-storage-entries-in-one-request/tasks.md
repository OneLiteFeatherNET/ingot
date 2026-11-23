# Tasks

The code ships as one pull request of one Conventional Commits type, `perf(storage)`. Tests are written and seen to fail before
the code they test. Every test follows F.I.R.S.T.: no sleeps, no system clock, no fixed ports, no shared mutable state, a
temporary directory per test, assertions with messages. No code pull request contains a file under `openspec/`.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose read-storage-entries-in-one-request`: this change and nothing else, branched from the remote
  main branch. Verify: `openspec validate read-storage-entries-in-one-request --strict` passes and the dash grep prints nothing.

## 2. Storage API

- [x] 2.1 Write failing tests for the default `readEntry` (equal to details plus content, same error for a missing file).
- [x] 2.2 Add `StorageEntry` and `StorageProvider.readEntry`, both `@ExperimentalIngotPluginApi`, with the default.

## 3. S3 and Maven

- [x] 3.1 Write a failing test that a read on S3 issues one request (recording client).
- [x] 3.2 Override `readEntry` in `S3StorageProvider` with one GET.
- [x] 3.3 Use `readEntry` in the Maven file endpoint for non-ranged reads; responses unchanged.

## 4. Pull request

- [x] 4.1 Open the code pull request titled `perf(storage): read storage entries in one request`.

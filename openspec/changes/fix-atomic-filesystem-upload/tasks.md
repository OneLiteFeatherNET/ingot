# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `storage`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Observations made inside a stream are recorded and asserted after the upload returns, not
by waiting. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`, and the container suite as
`:reposilite-backend:containerTest --tests '<pattern>'`, one container job at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-atomic-filesystem-upload`: this change and nothing else. Verify:
  `openspec validate fix-atomic-filesystem-upload --strict` passes and the dash grep from the proposal prints nothing.

## 2. Failing unit tests

- [ ] 2.1 `test(storage)`: write failing tests in a new class `FileSystemUploadAtomicityTest` under
  `reposilite-backend/src/test/kotlin/com/reposilite/storage/filesystem/`:
  the target is absent while the body is still streaming and a `.ingot-*.part` sibling exists in the same directory;
  an overwrite keeps the previous bytes readable while the body streams; a body that fails part way leaves the target
  unchanged and no `.part` file; the commit passes `ATOMIC_MOVE` through the internal move function; when that function
  reports `AtomicMoveNotSupportedException`, the plain replacing move runs and one warning is captured by an appender;
  listings, directory information, the search entry walk and usage exclude a `.ingot-*.part` file; a direct read of such a
  name answers `404`; an upload to such a name answers `400`; the start-time sweep deletes a partial file modified 25 hours
  before a fixed clock and keeps one modified one hour before it, and keeps a non-partial file of the same age.
  Verify: they fail on the current code, and the failure messages name the behaviour.

## 3. Atomic commit and hidden in-progress files

- [ ] 3.1 `fix(storage)`: create the temporary file with `createTempFile(parent, ".ingot-", ".part")` in the target
  directory, commit with `ATOMIC_MOVE` and replace, fall back on `AtomicMoveNotSupportedException` with one warning, and
  accept an injected `Clock` in the provider constructor with the system clock as default. Verify: the tests of 2.1 for the
  write location, overwrite, failure and move pass.
- [ ] 3.2 `fix(storage)`: hide and reject the reserved name in `FileSystemEntrySource` (walk), `toDirectoryInfo`,
  `getFiles`, `sizeOf` used by usage, `toFileDetails` and `putFile`. Check `StorageWalk.kt` uses only the filtered calls.
  Verify: the listing, usage, `404` and `400` tests of 2.1 pass.
- [ ] 3.3 `fix(storage)`: delete stale `.ingot-*.part` files when the provider starts, comparing the modification time
  with the injected clock and the 24-hour threshold, and log the number removed. Measure the start time on a tree of staging
  size and note it in the pull request. Verify: the sweep test of 2.1 passes, and
  `:reposilite-backend:integration --tests 'com.reposilite.storage.FileSystemStorageProviderIntegrationTest'` passes
  unedited.

## 4. Rollback check against the upstream image

- [ ] 4.1 `test(storage)`: add a container test in `reposilite-backend/src/containerTest` beside the existing upgrade
  scenario. Start Ingot on a volume, complete one artifact upload and record its SHA-1, start a second upload from a
  client that sends slowly, and stop the Ingot container once a `.ingot-*.part` file exists in the volume. Start the upstream
  image on the same volume and assert that it starts, serves the completed artifact with the same SHA-1, and lists its
  directory without error. Verify: the precondition that the partial file exists fails before 3.1, so the test is red first,
  and it passes after 3.3.

## 5. Documentation

- [ ] 5.1 `docs(storage)`: add to `docs/gitbook/reference/upgrade-changes.md` the three operator-visible points of the
  proposal (free space on the repository volume during an upload, the `400` for the reserved name, the 24-hour sweep). Add
  one paragraph on the atomic commit to `docs/gitbook/reference/storage-providers.md`. No environment variable or manifest
  changes, so no other page changes. Verify: the dash grep prints nothing for `docs/gitbook`.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `fix(storage): move uploads into place atomically`, after the proposal
  pull request is merged. Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of
  the reader who received a half-written artifact and the operator whose container storage filled up, without class names.
  It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.

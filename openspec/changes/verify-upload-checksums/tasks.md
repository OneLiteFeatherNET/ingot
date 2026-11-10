# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests are written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (any time source is injected
as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages.
Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`, unit tests as `:reposilite-backend:test --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose verify-upload-checksums`: this change and nothing else. Verify:
  `openspec validate verify-upload-checksums --strict` passes and the dash grep from the proposal prints nothing.

## 2. Policy and parsing

- [ ] 2.1 `test(maven)`: write failing tests for the policy resolution as a pure function of the repository value, the
  `INGOT_` value, the `REPOSILITE_` value and the system property (precedence, default `ignore`, unknown value refused with the
  key name), and for the digest parser (bare, `digest  name`, upper case, wrong length, non-hex, over 1024 bytes). Verify: they
  fail on the missing classes.
- [ ] 2.2 `feat(maven)`: add `UploadChecksumPolicy`, the nullable `uploadChecksumPolicy` key in `RepositorySettings` with its
  `@Doc`, the instance default and the parser. Verify: the tests of 2.1 pass and the existing settings tests pass unedited.
- [ ] 2.3 `docs(maven)`: document the variable in `docs/gitbook/reference/environment-variables.md` and the key in
  `docs/gitbook/reference/repository-settings.md`.

## 3. Verification on deploy

- [ ] 3.1 `test(maven)`: write failing tests with an in-memory storage provider and a fake event sink: match is stored under
  `warn` and `reject`; mismatch under `reject` gives `400`, stores nothing and leaves the artifact; mismatch under `warn` stores
  and emits the event with outcome `accepted`; checksum before artifact is stored unchecked; `.jar` is not digested; a refused
  redeployment still gives `409`; default `ignore` computes no digest (counting provider). Add integration tests with a real
  Maven deploy (artifact then checksums) on a filesystem repository, and on S3 with the existing S3 test setup, asserting that
  the digest reads the object and not a cache entry. Verify: they fail before 3.2.
- [ ] 3.2 `feat(maven)`: add `UploadChecksumVerifier`, call it in `RepositoryService.deployFile` between `acceptsDeploymentOf`
  and `putFile`, store a verified checksum body from memory, emit `UploadChecksumMismatchEvent`, and record the `checksum_mismatch`
  outcome. Verify: the tests of 3.1 pass and the existing deploy tests pass unedited.
- [ ] 3.3 `docs(maven)`: add the `400` case to `docs/gitbook/reference/api-errors.md`.

## 4. Audit

- [ ] 4.1 `test(audit)`: write failing tests that an `UploadChecksumMismatchEvent` is recorded as `UPLOAD_CHECKSUM_MISMATCH` with
  actor, repository, path, `algorithm` and `outcome`, and that the new enum entry is appended after the existing ones. Verify:
  they fail before 4.2.
- [ ] 4.2 `feat(audit)`: add the action and the listener in the audit plugin. Verify: the tests of 4.1 pass.
- [ ] 4.3 `docs(audit)`: add the action to `docs/gitbook/reference/audit-events.md`.

## 5. How-to

- [ ] 5.1 `docs(maven)`: add `docs/gitbook/how-to/verify-uploaded-checksums.md` (turn it on, what is checked, the order rule and
  checksum-first gap, the S3 read cost, the redeployment race, reading the audit events) and list it in `docs/gitbook/SUMMARY.md`.

## 6. Rollback and release

- [ ] 6.1 `test(maven)`: extend the container rollback check so that the upstream Reposilite image starts and serves artifacts
  with `uploadChecksumPolicy` present in the shared maven settings. If it does not, move the key out of stored settings and report.
- [ ] 6.2 `docs(openspec)`: open the pull request under the title `feat(maven): verify uploaded checksum files against the stored artifact`.
  The description follows the repository template and tells the story from the operator's side.

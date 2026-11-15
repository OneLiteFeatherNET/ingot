# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: a temporary directory per test (`@TempDir`), an injected `Clock`, no sleeps, no shared mutable state, assertions that name the input. Test runs go through the slot runner with `--max-workers=2`.

The canonical path is always enforced. There is no `INGOT_AUTHORIZATION_PATHS` switch and no `warn` or `off` mode (see design, Decision).

## 1. Verify the premise

- [ ] 1.1 Write a verify test first: uploads with escaped characters, escaped dot segments, escaped slashes, doubled slashes and backslashes through a running in-process server, sent as raw URIs. Verify: the tests record whether the stored location can differ from the location the decision judged.
- [ ] 1.2 Record whether any released version is affected. Verify: the answer is written into the pull request description, without a step-by-step reproduction.
- [ ] 1.3 Inventory the enforcement points that judge the raw path (REST and dashboard upload endpoints, proxy caching, `/check`). Verify: each one is listed as "in this change" or "follow-up" in the pull request.

## 2. Canonicalizer tests

- [ ] 2.1 `test(maven)`: write failing tests first. Table of inputs and outputs: a plain path; one encoded character; a double-encoded character (decoded once only); a `+` kept; a malformed escape (400); `%2e%2e`, `.` and `..` (400); `%2F` (400); a backslash (400); doubled slashes; NUL and control characters (400); mixed case kept. Verify: the tests fail because `RepositoryPath` does not exist.

## 3. Canonicalizer

- [ ] 3.1 `fix(maven)`: add `RepositoryPath.canonicalize`. Verify: 2.1 passes.
- [ ] 3.2 `fix(maven)`: judge uploads and deletes by the canonical path in the route dispatcher and hand the same repository and location to the handler. Verify: the in-process tests of 1.1 show no write outside the judged location.
- [ ] 3.3 `fix(maven)`: the legacy route rule receives the canonical string. Verify: `LegacyAuthorizationParityTest` passes unedited.

## 4. Verification

- [ ] 4.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.format.*' --max-workers=2`, the new integration class, `ArtifactPermissionIntegrationTest`, `MavenIntegrationTest` and `RepositoryDispatchCharacterisationIntegrationTest`.
- [ ] 4.2 Run the "Container images" job's upgrade rehearsal and check the three drop-in questions: an unmodified upstream compose file still starts, an existing `/app/data` is adopted, and `PUID`, `PGID` and `REPOSILITE_OPTS` still take effect. Verify: the job is green.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `fix(maven): canonicalize repository paths before authorization`.

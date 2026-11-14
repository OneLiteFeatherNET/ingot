# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: a temporary directory per test (`@TempDir`), an injected `Clock`, no sleeps, no shared mutable state, assertions that name the input. Test runs go through the slot runner with `--max-workers=2`. Configuration changes update the matching page under `docs/gitbook/`.

## 1. Verify the premise

- [ ] 1.1 Write a verify test first: an upload to `%2e%2e` and to `%2F` through a running in-process server, recording the raw path the authorizer receives and the `gav` the route receives. Verify: the test records both values; it is not a pass-or-fail behaviour test.
- [ ] 1.2 Record whether any encoded form reaches storage outside the authorized location. Verify: the answer is written into the pull request description, whichever way it goes.
- [ ] 1.3 Inventory the enforcement points that judge the raw path (REST and dashboard upload endpoints, proxy caching, `/check`). Verify: each one is listed as "in this change" or "follow-up" in the pull request.

## 2. Canonicalizer tests

- [ ] 2.1 `test(maven)`: write failing tests first. Table of inputs and outputs: a plain path; one encoded character; a double-encoded character (decoded once only); a malformed escape (400); `%2e%2e` and `..` (400); `%2F` inside a segment (400 in `enforce`); doubled slashes; NUL and control characters (400); mixed case kept. Verify: the tests fail because `RepositoryPath` does not exist.
- [ ] 2.2 `test(maven)`: write failing corpus updates for the rows of `record-decision-corpus` that name this change, with the expected canonical outcome.

## 3. Canonicalizer and switch

- [ ] 3.1 `fix(maven)`: add `RepositoryPath.canonicalize`, the mode enum with `off`, `warn` and `enforce`, and the reader of `INGOT_AUTHORIZATION_PATHS` with the `REPOSILITE_AUTHORIZATION_PATHS` fallback. Verify: 2.1 passes.
- [ ] 3.2 `fix(maven)`: judge uploads and deletes by the decision of the active mode in the route dispatcher and the repository security provider. In `warn`, compute the canonical decision, and log and count a difference. Metadata writes (`canModifyResource`) follow the same mode. Verify: the corpus rows pass in `warn` and show the canonical outcome in `enforce`.
- [ ] 3.3 `fix(maven)`: pass the active string to the legacy route rule. Verify: `LegacyAuthorizationParityTest` passes unedited in `warn` and `off`.

## 4. Documentation

- [ ] 4.1 `docs(maven)`: document `INGOT_AUTHORIZATION_PATHS`, the three modes and the refusal in `docs/gitbook/reference/access-tokens-and-routes.md` and `docs/gitbook/explanation/permission-evaluation.md`. Verify: the page names the default `warn` and the drop-in effect.

## 5. Verification

- [ ] 5.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.maven.*Path*' --max-workers=2`, plus the corpus and `LegacyAuthorizationParityTest`.
- [ ] 5.2 Run the "Container images" job's upgrade rehearsal and check the three drop-in questions: an unmodified upstream compose file still starts, an existing `/app/data` is adopted, and `PUID`, `PGID` and `REPOSILITE_OPTS` still take effect. Verify: the job is green.

## 6. Pull request

- [ ] 6.1 After the proposal pull request is merged, open the code pull request with the title `fix(maven): canonicalize repository paths before authorization`, or with the `!` variant if question 1 is answered that way.

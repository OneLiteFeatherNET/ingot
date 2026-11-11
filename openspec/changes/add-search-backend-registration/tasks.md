# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected), no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages.
Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-search-backend-registration`: this change and nothing else. Verify:
  `openspec validate add-search-backend-registration --strict` passes and the dash grep from the proposal prints nothing.

## 2. Registry and states (red, then green)

- [ ] 2.1 `test(search)`: write failing unit tests for the registry: a factory of the current API version registers; another
  version is refused with a warning naming both numbers; a duplicate id is refused naming both classes; `database` cannot be
  replaced; an unknown id yields `NOT_INSTALLED`. Verify: they fail on the missing types.
- [ ] 2.2 `feat(search)`: add the public types of `net.onelitefeather.ingot.search.backend` with the marker, the registration
  event, the registry and the state of D4. Verify: the tests of 2.1 pass.
- [ ] 2.3 `test(search)` then `feat(search)`: failing tests of the transitions with a fake clock and the fake backend: `OPEN_FAILED`
  retried on each 30-second tick until it opens; `DOWN` drains with backoff; each transition logged once. Then the transitions in
  the holder that the poller and the routes read. Verify: the tests pass.

## 3. Cluster agreement (red, then green)

- [ ] 3.1 `test(search)`: failing tests of the rule of D5: two replicas with different ids, the stored id decides; an absent
  stored id is written by the first available replica; a stored id with a heartbeat younger than three minutes blocks the
  switch; a heartbeat exactly three minutes old allows it; the switch clears `LAST_FULL_REBUILD` and starts a `BACKEND` run.
  Verify: they fail.
- [ ] 3.2 `feat(search)`: add `BACKEND_ID`, the per-instance heartbeat keys, the rule, and the `BACKEND` run trigger. Verify: the
  tests of 3.1 pass and the `StartupRules` tests pass unedited.

## 4. Outbox and access (red, then green)

- [ ] 4.1 `test(search)`: failing tests: a `BackendBusyException` releases its batch with the retry delay and one attempt fewer; a
  tenth real failure still makes a dead letter; the access check drops a file outside the access value, logs it and increments
  `ACCESS_MISMATCHES`. Verify: they fail.
- [ ] 4.2 `feat(search)`: add `OutboxStore.release` in one transaction, the busy handling in `OutboxWorker`, and the core access
  check in the files and groups responses. Verify: the tests of 4.1 pass.

## 5. Wiring after plugin initialisation (red, then green)

- [ ] 5.1 `test(search)`: failing tests that the routes answer `503` with problem details and `Retry-After` for each non-available
  state, `404` when `searchEnabled` is false, and that the Maintenance block carries the state and a redacted detail. Verify: they
  fail.
- [ ] 5.2 `feat(search)`: move the registry lookup, the `open()` call, the poller and the schedulers to the post-initialise listener,
  keep the routes and the listeners in `initialize`, and add the `backend` block of `SearchIndexResponse`. Verify: the tests of 5.1
  pass and the existing integration tests of `search` pass with unchanged assertions.
- [ ] 5.3 `test(search)`: add the contract suite of `extract-search-backend-spi` as an integration test that runs against the
  database backend through the registry, including the access cases. Verify: `heavy ./gradlew :reposilite-backend:integration
  --tests '*SearchBackendRegistry*' --max-workers=2` passes.

## 6. Build and fixtures

- [ ] 6.1 `build(search)`: apply `java-test-fixtures` to `reposilite-backend` and move the abstract contract suite into
  `src/testFixtures`, so that plugin modules can depend on it. Verify: `heavy ./gradlew :reposilite-backend:compileTestFixturesKotlin
  --max-workers=2` passes and the database contract tests still run.

## 7. Documentation

- [ ] 7.1 `docs(search)`: add `docs/gitbook/how-to/choose-a-search-backend.md` (what each state means, what to do for each, the
  cluster rule, the three-minute switch) and link it from `docs/gitbook/SUMMARY.md`.
- [ ] 7.2 `docs(search)`: add `docs/gitbook/how-to/write-a-search-backend-plugin.md` (factory, context, the contract suite, the
  busy signal, the blocking open, the redaction rule) and link it from `docs/gitbook/SUMMARY.md`.
- [ ] 7.3 `docs(search)`: update `docs/gitbook/reference/configuration-and-startup.md` and
  `docs/gitbook/reference/search-filters-and-api.md` for the `searchEngine` values, the `503` cases, the `backend` block and the
  changed answer for an unknown value; list the event in `docs/gitbook/reference/plugin-api.md`; add the change to
  `docs/gitbook/reference/upgrade-changes.md` with the experimental marking.
- [ ] 7.4 `docs(search)`: update `docs/gitbook/how-to/rebuild-search-index.md` with the `BACKEND` trigger and the rule that a
  switch rebuilds the index.

## 8. Code pull request

- [ ] 8.1 Open the code pull request under the title `feat(search): let plugins register search backends and report their health`,
  from a branch of the remote main branch that already contains the merged proposal and the merged predecessor changes, with no
  file under `openspec/`.

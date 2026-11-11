# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected, and the circuit breaker and the poll loops take it as a parameter), no fixed ports (the container maps a random
port), no shared mutable state, a temporary directory per test, and assertions with messages. Unit tests run as
`:reposilite-plugins:opensearch-plugin:test --tests '<pattern>'`, container tests as `:reposilite-backend:integration --tests
'<pattern>'` through `heavy`, always with `--max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-opensearch-search-plugin`: this change and nothing else. Verify:
  `openspec validate add-opensearch-search-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Configuration and secrets (red, then green)

- [ ] 2.1 `test(search)`: write failing unit tests for the variable rules of D2: the `INGOT_` name wins over `REPOSILITE_`; the
  switch defaults to off; URLs must be `https` unless the HTTP switch is on; user information in a URL is refused without echoing
  it; a password file is read once and stripped of trailing line breaks; the plain variable wins over the file; an unreadable file
  names its variable; a user without password, or the reverse, is refused. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(search)`: add the configuration parser of the plugin, the factory `opensearch` with apiVersion 1, and the registration
  on `ArtifactSearchBackendRegistrationEvent`, with the switch in `PluginLoader.BUNDLED_SWITCHES`. Verify: the tests of 2.1 pass.
- [ ] 2.3 `test(search)` then `feat(search)`: failing test that a disabled plugin registers nothing and that the status block never
  contains a password, a user name or a URL with credentials, even when the cluster returns an error that echoes them. Then the
  redaction of error texts. Verify: the tests pass.

## 3. Query translation (red, then green)

- [ ] 3.1 `test(search)`: write failing golden tests of the query builder: one golden JSON per query shape (word, coordinates, each
  filter, facet with its own-kind exclusion, the group window, the total, the latest version, the files list), and the access
  translation for every `PathPredicate` variant, using the rows of the existing `SearchAccessParityTest` table. Add the first-match
  test for a precedence list with a denial. Verify: they fail.
- [ ] 3.2 `feat(search)`: add the query builder and the access translation. Move `latestOf` and the group comparator into core, shared
  with the database engine, and check with the database tests that their results do not change. Verify: the tests of 3.1 pass and the
  database tests pass unedited.

## 4. Write path (red, then green)

- [ ] 4.1 `test(search)`: write failing unit tests of the bulk body builder (the size limits of 500 actions and 5 MB, the order of
  actions, the stable document id), the mapping of `429`, `503` and item-level throttling to `BackendBusyException` with the
  retry delay, the rejection of a document as a batch failure, the circuit breaker with an injected clock (open after five failures,
  30 seconds closed, one half-open request). Verify: they fail.
- [ ] 4.2 `feat(search)`: add the HTTP layer (`OpenSearchHttp`), the bulk writer, the removal and reorigin requests with the stored
  script, and the circuit breaker. Verify: the tests of 4.1 pass.

## 5. Container contract (red, then green)

- [ ] 5.1 `test(search)`: add `OpenSearchSearchBackendContractTest` in the integration source set. It extends the abstract contract
  suite from `reposilite-backend` test fixtures and starts a secured OpenSearch 2.x container through Testcontainers, with the
  test authority trusted by the test `SSLContext`. It also covers the restart case of the design. Verify:
  `heavy ./gradlew :reposilite-backend:integration --tests '*OpenSearchSearchBackendContractTest*' --max-workers=2` fails on the
  missing backend.
- [ ] 5.2 `feat(search)`: implement `open()` (connect, create the index and the alias, check the mapping version), `health()`,
  `count()`, `existing()`, `touch()`, `deleteStale()`, `deleteRepository()`, `repositories()`, `pathsAt()`, `sample()` and the reads.
  Verify: the container tests pass, including the restart case.
- [ ] 5.3 `test(search)` then `feat(search)`: the plugin's contribution to the Maintenance status (`backend` block with the refresh
  interval) and the behaviour of `DOWN` in the registration states. Verify: the status tests pass.

## 6. Build, image and CI

- [ ] 6.1 `build(search)`: add `:reposilite-plugins:opensearch-plugin` to `settings.gradle.kts` in the list of plugins that are
  included when present, with the dependency on the test fixtures of `reposilite-backend`, and the test container dependency.
  Verify: `heavy ./gradlew :reposilite-plugins:opensearch-plugin:compileKotlin --max-workers=2` passes.
- [ ] 6.2 `build(search)`: copy the shadow jar of the plugin into `/app/bundled-plugins` in `Dockerfile`, next to the nexus import
  jar. Verify: the image build job copies it and the upgrade rehearsal with the switch off passes.
- [ ] 6.3 `ci(search)`: add the job "Search backends" to the workflow, running the container contract tests on pull requests that
  touch the plugin or the search backend, and nightly otherwise. Verify: the workflow passes its own lint.

## 7. Documentation

- [ ] 7.1 `docs(search)`: add `docs/gitbook/reference/opensearch-search-backend.md` with every variable of D2 and its default, the
  index layout and alias, the mapping version procedure, the access rule, the security obligations, and the errors.
- [ ] 7.2 `docs(search)`: add `docs/gitbook/how-to/use-opensearch-for-search.md` with the steps for a cluster, the switches, the
  initial rebuild after the switch, and the way back to the database. Link both pages from `docs/gitbook/SUMMARY.md`.
- [ ] 7.3 `docs(search)`: add the variables to `docs/gitbook/reference/environment-variables.md`, the switch and the search backend
  to `docs/gitbook/reference/configuration-and-startup.md`, and the release to `docs/gitbook/reference/upgrade-changes.md`.

## 8. Code pull request

- [ ] 8.1 Open the code pull request under the title `feat(search): add an opt-in opensearch backend plugin`, from a branch of the
  remote main branch that already contains the merged proposal and the merged predecessor changes, with no file under `openspec/`.

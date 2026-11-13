# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected), no fixed ports (the container maps a random port), no shared mutable state, a temporary directory per test,
and assertions with messages. Unit tests run as `:reposilite-plugins:elasticsearch-plugin:test --tests '<pattern>'`, container
tests as `:reposilite-backend:integration --tests '<pattern>'` through `heavy`, always with `--max-workers=2`.

## 0. Preconditions in other pull requests (not part of this change's commits)

- [ ] 0.1 `refactor(search)`: extract `reposilite-search-http` from the OpenSearch plugin code (design D3), in its own pull request
  merged before section 2. Verify: the OpenSearch tests pass unedited against the extracted module.
- [ ] 0.2 `feat(search)`: the additive method `indexIdentity(): String? = null` of `ArtifactSearchBackend` and its cursor reset rule
  are merged by section 2 of `add-lucene-search-plugin`. This change does not add them again; it implements `indexIdentity()` in the
  plugin (section 5.2). Verify: that change is merged before section 5 of this one.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-elasticsearch-search-plugin`: this change and nothing else. Verify:
  `openspec validate add-elasticsearch-search-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Configuration and secrets (red, then green)

- [ ] 2.1 `test(search)`: failing unit tests for the variable rules of D4: `INGOT_` wins over `REPOSILITE_`; URLs must be `https`
  unless the HTTP switch is on; user information in a URL is refused without echoing it; an API key with a user name is refused;
  a password file is read once and stripped of trailing line breaks; the plain variable wins over the file; an unreadable file
  names its variable; a user without password, or the reverse, is refused. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(search)`: add the configuration parser, the factory `elasticsearch` with apiVersion 1, the version gate of D2 at open,
  and the registration on `ArtifactSearchBackendRegistrationEvent`. The jar is not added to `PluginLoader.BUNDLED_SWITCHES`.
  Verify: the tests of 2.1 pass.
- [ ] 2.3 `test(search)` then `feat(search)`: a failing test that no status block, problem body or log line contains the API key,
  the password or a URL with credentials, even when the cluster answers with an error that echoes them; then the redaction.
  Verify: the tests pass.

## 3. Query translation (red, then green)

- [ ] 3.1 `test(search)`: failing golden tests of the query builder against the Elasticsearch golden set (`src/test/resources/golden/elasticsearch`):
  one file per query shape (words, coordinates, each filter, facet with its own-kind exclusion, the group window, the total, the
  latest version, the files list) and the access translation for every `PathPredicate` variant, with the rows of
  `SearchAccessParityTest`. A first-match test for a precedence list with a denial. Verify: they fail.
- [ ] 3.2 `feat(search)`: add the query builder and the access translation. Verify: the tests of 3.1 pass, and the database and
  OpenSearch tests pass unedited.

## 4. Write path (red, then green)

- [ ] 4.1 `test(search)`: failing unit tests of the bulk body builder (limits of 500 actions and 5 MB, order, stable document id),
  the mapping of `429`, `503` and item-level throttling to `BackendBusyException` with the retry delay, a rejected document as a
  batch failure, the reorigin script as a constant (no request value in its text), and the delete filter with the staleness
  condition. Verify: they fail.
- [ ] 4.2 `feat(search)`: add the HTTP layer of the plugin on the shared module, the bulk writer, the removal and reorigin requests.
  Verify: the tests of 4.1 pass.

## 5. Container contract (red, then green)

- [ ] 5.1 `test(search)`: add `ElasticsearchSearchBackendContractTest` for 8.x and `ElasticsearchSearchBackend9ContractTest` for
  9.x in the integration source set. Each extends the abstract contract suite of `reposilite-backend` test fixtures, starts a secured
  container through Testcontainers, and trusts the test authority through the test `SSLContext`. The API key is created with exactly
  the privileges of D10; the test fails if the plugin needs more. It also covers the restart case and the index replacement case of
  D8. Verify: `heavy ./gradlew :reposilite-backend:integration --tests '*ElasticsearchSearchBackend*' --max-workers=2` fails on the
  missing backend.
- [ ] 5.2 `feat(search)`: implement `open()` (version gate, connect, create the index and the alias, check the mapping version,
  read the index UUID), `health()`, `indexIdentity()`, `count()`, `existing()`, `touch()`, `deleteStale()`, `deleteRepository()`,
  `repositories()`, `pathsAt()`, `sample()` and the reads. Verify: the container tests pass for both majors, including the restart
  and index replacement cases.
- [ ] 5.3 `test(search)` then `feat(search)`: the plugin's contribution to the Maintenance status (a `backend` block with the refresh
  interval and the major version) and the states of registration D4 for `OPEN_FAILED`, `DOWN` and an unsupported major. Verify: the
  status tests pass.

## 6. Build and CI

- [ ] 6.1 `build(search)`: add `:reposilite-plugins:elasticsearch-plugin` to `settings.gradle.kts` with the dependency on the test
  fixtures of `reposilite-backend`, the shared module and the Testcontainers Elasticsearch module (test scope only). The shadow jar
  goes next to the other plugin jars and is not copied into the image. Verify:
  `heavy ./gradlew :reposilite-plugins:elasticsearch-plugin:compileKotlin --max-workers=2` passes, and the jar contains no class of
  `co.elastic` or `org.elasticsearch`.
- [ ] 6.2 `ci(search)`: add the Elasticsearch rows to the job "Search backends", running the contract tests for 8.x and 9.x on pull
  requests that touch the plugin or the search backend, and nightly otherwise. Verify: the workflow passes its own lint.

## 7. Documentation

- [ ] 7.1 `docs(search)`: add `docs/gitbook/reference/elasticsearch-search-backend.md` with every variable of D4 and its default, the
  index layout and alias, the mapping version procedure, the API key privileges of D10, the security obligations, the result window
  limit of D7, the licence statement of D1 and the errors.
- [ ] 7.2 `docs(search)`: add `docs/gitbook/how-to/use-elasticsearch-for-search.md` with the steps for a cluster, the certificate
  authority, the switch of `searchEngine`, the initial rebuild after the switch, and the way back to the database. Link both pages
  from `docs/gitbook/SUMMARY.md`.
- [ ] 7.3 `docs(search)`: add the variables to `docs/gitbook/reference/environment-variables.md`, the backend selection to
  `docs/gitbook/reference/configuration-and-startup.md`, and the release to `docs/gitbook/reference/upgrade-changes.md`. State that
  the plugin is installed by hand or from the plugin store and is not part of the image.

## 8. Code pull request

- [ ] 8.1 Open the code pull request under the title `feat(search): add an elasticsearch backend plugin`, from a branch of the
  remote main branch that already contains the merged proposal and the merged preconditions of section 0, with no file under
  `openspec/`.

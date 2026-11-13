# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps in a test body (the `Waiter` is a fake in unit
tests and a bounded wait in container tests), no system clock (the `Clock` is injected into the breaker), no fixed ports (the container
maps a random port), no shared mutable state, a temporary directory per test, a random index uid per test, and assertions with
messages. Unit and contract tests run as `:reposilite-plugins:meilisearch-plugin:test --tests '<pattern>'`; container tests run as
`:reposilite-backend:integration --tests '<pattern>'` through `heavy`, always with `--max-workers=2`.

## 0. Preconditions in other pull requests (not part of this change's commits)

- [ ] 0.1 `refactor(search)`: the shared module `reposilite-search-http` is extracted (section 0 of `add-elasticsearch-search-plugin`).
  Verify: the OpenSearch tests pass unedited against it.
- [ ] 0.2 `feat(search)`: `indexIdentity()` is merged by section 2 of `add-lucene-search-plugin`. Verify: that change is merged before
  section 5 of this one.
- [ ] 0.3 Core: the outbox claim lease is longer than 60 seconds, so that the task wait of `apply` (design D4) cannot outlive its claim.
  Verify: the constant is asserted in a core test that names this change.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-meilisearch-search-plugin`: this change and nothing else. Verify:
  `openspec validate add-meilisearch-search-plugin --strict` passes and the dash grep from the proposal prints nothing.

## 2. Configuration and secrets (red, then green)

- [ ] 2.1 `test(search)`: failing unit tests for the variable rules of D2: `INGOT_` wins over `REPOSILITE_`; the URL must be `https`
  unless the HTTP switch is on; user information in the URL is refused without echoing it; the key file is read once and stripped of
  trailing line breaks; the plain key wins over the file; an unreadable file names its variable; the task wait is refused above 60
  seconds; the batch size is bounded. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(search)`: add the configuration parser, the factory `meilisearch` with apiVersion 1, and the registration on
  `ArtifactSearchBackendRegistrationEvent`. The jar is not added to `PluginLoader.BUNDLED_SWITCHES`. Verify: the tests of 2.1 pass.
- [ ] 2.3 `test(search)` then `feat(search)`: no status block, problem body or log line contains the API key or a URL with credentials,
  including when the server answers with an error that echoes them. Verify: the redaction tests pass.

## 3. Filter translation and group computation (red, then green)

- [ ] 3.1 `test(search)`: failing unit tests with golden filter strings: every `PathPredicate` variant with the rows of
  `SearchAccessParityTest` (`Legacy` is refused with its reason), the first-match rule of `Precedence` with a denial, the quoting and
  escaping of paths, the `directories` array of a path (`a/b/c.jar` gives `["a", "a/b"]`), and the empty result of `Never` without a
  request. Verify: they fail.
- [ ] 3.2 `test(search)`: failing unit tests of the group computation on fixture responses: the window with the distinct attribute,
  the aggregates of the page, the sort with the shared comparator, the total up to the cap, the latest version with `latestOf`, and the
  `503` for a group sort over more files than `AGGREGATE_SORT_LIMIT`. Verify: they fail.
- [ ] 3.3 `feat(search)`: add the filter translation, the query builder, the group computation and the facet multi-search. Verify: the
  tests of 3.1 and 3.2 pass, and the database tests pass unedited.

## 4. Task handling and writes (red, then green)

- [ ] 4.1 `test(search)`: failing unit tests with a fake `Waiter`: a batch whose tasks succeed is applied; a failed task is a batch
  failure with the server's error code; a task still `enqueued` or `processing` after the bound is `BackendBusyException` with 10
  seconds and no attempt; a replay after a busy answer leaves the same documents; the order of actions is kept; a removal of a missing id
  succeeds; the stale removal repeats its condition in the filter. Verify: they fail.
- [ ] 4.2 `test(search)`: failing unit tests of the circuit breaker with an injected clock and of the mapping of a server `503` to a busy
  answer. Verify: they fail.
- [ ] 4.3 `feat(search)`: add the HTTP calls of the writes, the task polling with the injected `Waiter`, the delete-by-filter requests and
  the partial updates. Verify: the tests of 4.1 and 4.2 pass.

## 5. Container contract (red, then green)

- [ ] 5.1 `test(search)`: confirm the **verify** points of the design against the pinned Meilisearch minor version in a container test
  (distinct search and its total, equality on array attributes, the prefix setting, the index creation time, the key actions, the
  delete-by-filter and the partial update endpoints). Record the result in the reference page and update the gap register in the same
  change if a point differs. Verify: the confirmation test passes.
- [ ] 5.2 `test(search)`: add `MeilisearchSearchBackendContractTest` in the integration source set. It extends the abstract contract
  suite of `reposilite-backend` test fixtures, starts a Meilisearch container through Testcontainers with a random port, and
  declares the named exceptions G2, G3, G4 and G7 with their reasons. It also covers a restart during a drain, a task that fails, a task
  that outlives the bound, a settings change that reindexes, and an index replaced by the operator. Verify:
  `heavy ./gradlew :reposilite-backend:integration --tests '*MeilisearchSearchBackendContractTest*' --max-workers=2` fails on the missing
  backend.
- [ ] 5.3 `feat(search)`: implement `open()` (version gate, index creation, settings with the logged changes, waiting for the settings
  task), `health()`, `indexIdentity()`, `count()`, `existing()`, `touch()`, `deleteStale()`, `deleteRepository()`, `repositories()`,
  `pathsAt()`, `sample()`, `sampleStart()` and the reads. Verify: the container tests pass, with the named exceptions only.
- [ ] 5.4 `test(search)` then `feat(search)`: the plugin's contribution to the Maintenance status (the consistency mode, the unfinished
  and failed task counts, the settings of D3) and the `Down` state with "settings task running". Verify: the status tests pass.

## 6. Build and CI

- [ ] 6.1 `build(search)`: add `:reposilite-plugins:meilisearch-plugin` to `settings.gradle.kts` with the dependency on the test fixtures
  of `reposilite-backend`, the shared module and the Testcontainers generic container (test scope only). The shadow jar goes next to the
  other plugin jars and is not copied into the image. Verify:
  `./gradlew :reposilite-plugins:meilisearch-plugin:compileKotlin --max-workers=2` passes.
- [ ] 6.2 `feat(search)`: add the check that the outbox claim lease is longer than the task wait bound of `apply` (design D4), and
  name the dependency on precondition 0.3 in the reference page. Verify: a unit test fails when the bound is set above the lease.
- [ ] 6.3 `ci(search)`: add the Meilisearch rows to the job "Search backends": the in-process contract suite on pull requests that touch
  the plugin, and the container tests for the pinned version on pull requests that touch the plugin or the search backend, and nightly
  otherwise. Verify: the workflow passes its own lint.

## 7. Documentation

- [ ] 7.1 `docs(search)`: add `docs/gitbook/reference/meilisearch-search-backend.md` with every variable of D2 and its default, the index
  layout and settings, the gap register of the design in operator words, the named exceptions, the minimal key actions, the pinned minor
  version, the security obligations and the errors. The page lists the exceptions first.
- [ ] 7.2 `docs(search)`: add `docs/gitbook/how-to/use-meilisearch-for-search.md` with the steps for a server, the key, the switch of
  `searchEngine`, the initial rebuild after the switch, the way back to the database, and when to prefer another backend. Link both pages
  from `docs/gitbook/SUMMARY.md`.
- [ ] 7.3 `docs(search)`: add the variables to `docs/gitbook/reference/environment-variables.md`, the backend selection to
  `docs/gitbook/reference/configuration-and-startup.md`, and the release to `docs/gitbook/reference/upgrade-changes.md`. State that the
  plugin is installed by hand or from the plugin store and is not part of the image.

## 8. Code pull request

- [ ] 8.1 Open the code pull request under the title `feat(search): add a meilisearch backend plugin`, from a branch of the remote main
  branch that already contains the merged proposal and the merged preconditions of section 0, with no file under `openspec/`.

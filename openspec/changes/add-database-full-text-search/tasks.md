# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected, and the sweep interval is a parameter), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-database-full-text-search`: this change and nothing else. Verify:
  `openspec validate add-database-full-text-search --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tokeniser and query rules (red, then green)

- [ ] 2.1 `test(search)`: write failing unit tests for `SearchTokens.of`: dotted groupIds, hyphenated artifactIds, versions such
  as `1.0-SNAPSHOT`, Unicode letters, duplicates, a token of 65 characters dropped, 33 tokens capped at 32, and the split of a
  word into parts with the refusal above four parts. Verify: they fail on the missing class.
- [ ] 2.2 `feat(search)`: add `SearchTokens` in core, pure, with no database or clock, and the word-part split used by
  `SearchFilters`. Verify: the tests of 2.1 pass.

## 3. Schema and writes (red, then green)

- [ ] 3.1 `test(search)`: write failing tests on SQLite and H2 file databases: a database created at `schema-1` with rows is
  opened, keeps its rows, and gains `gav_key` and `search_token`; the generated statements contain only `ADD COLUMN`,
  `CREATE INDEX` and `CREATE TABLE`; two starters racing on `schema-2` run the DDL once; a replayed upsert leaves one set of
  tokens. Verify: they fail.
- [ ] 3.2 `feat(search)`: add the `gav_key` column, `search_token` and the `schema-2` marker step to `SearchSchema` and the
  table definitions, and write the key and tokens in the upsert of the database backend. Verify: the tests of 3.1 pass and the
  `schema-1` tests pass unedited.
- [ ] 3.3 `test(search)` then `feat(search)`: failing test first for the sweep: a fixed clock and a batch size of two fill the keys
  of five unkeyed rows in three transactions, `tokensComplete` turns true, and a read-only instance runs no sweep. Then the sweep
  on the 30-second scheduler tick of `SearchPlugin`, and the `tokensComplete` field and the `BUILDING` rule in
  `indexStateOf`. Verify: the tests pass.

## 4. Query (red, then green)

- [ ] 4.1 `test(search)`: add the word cases to the abstract `ArtifactSearchBackendContract` of `extract-search-backend-spi`:
  `core` finds `spring-core`; `spring kafka` requires both; a previous-query table keeps every group it matched before; a stale
  token of a removed GAV returns nothing; the 400 for five parts. Run them on SQLite and H2. Verify: they fail on the new cases.
- [ ] 4.2 `feat(search)`: add clause 4 of the word rule to `SearchFilters` (one `EXISTS` per part on `search_token`). Verify:
  the tests of 4.1 pass on SQLite and H2.
- [ ] 4.3 `test(search)`: add the same contract cases as integration tests for MariaDB and PostgreSQL on the shared containers.
  Verify: `heavy ./gradlew :reposilite-backend:integration --tests '*DatabaseSearchBackendContractTest*' --max-workers=2` passes.
- [ ] 4.4 `test(search)`: run the benchmark described in the design on a generated dataset of one million GAVs through `heavy`,
  record the numbers of the query and of the sweep, and attach them to the pull request description. No test asserts a time.

## 5. Documentation

- [ ] 5.1 `docs(search)`: update the `q` row and the query rules in `docs/gitbook/reference/search-filters-and-api.md` (words,
  parts, quotes, `OR` and `NOT`, the 400 for five parts, `tokensComplete`, and the `BUILDING` state during the sweep).
- [ ] 5.2 `docs(search)`: update `docs/gitbook/how-to/search-for-artifacts.md` with word examples (`core` finds `spring-core`) and
  the limits of version one, and add the broader results to the upgrade notes in `docs/gitbook/reference/upgrade-changes.md`.
- [ ] 5.3 `docs(search)`: confirm with `grep -rn "starts with" docs/gitbook` that no page still describes `q` as prefix only.

## 6. Code pull request

- [ ] 6.1 Open the code pull request under the title `feat(search): match words of the coordinates in artifact search`, from a
  branch of the remote main branch that already contains the merged proposal and the merged seam change, with no file under
  `openspec/`.

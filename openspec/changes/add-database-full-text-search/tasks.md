# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected, and the sweep interval is a parameter), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite.

The golden table of queries is the one characterisation test: it is captured against the code of main and passes before the query
change. Its name says so.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-database-full-text-search`: this change and nothing else. Verify:
  `openspec validate add-database-full-text-search --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tokeniser and token rule (red, then green)

- [ ] 2.1 `test(search)`: write failing unit tests for `SearchTokens.of`: dotted groupIds, hyphenated artifactIds, versions such
  as `1.0-SNAPSHOT`, Unicode letters, duplicates, a token of 65 characters dropped, 33 tokens capped at 32. Write failing unit tests
  for the parts of a token, for whether a token has a word branch (no dot or colon, one to four parts), and for whether a token is
  the last token of the query. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(search)`: add `SearchTokens` in core, pure, with no database or clock, and the token parts and word-branch rule used
  by `SearchFilters`. Verify: the tests of 2.1 pass.

## 3. Schema and writes (red, then green)

- [ ] 3.1 `test(search)`: write failing tests on SQLite and H2 file databases: a database created at `schema-1` with rows is
  opened, keeps its rows, and gains `gav_key` and `search_token`; the generated statements contain only `ADD COLUMN`,
  `CREATE INDEX` and `CREATE TABLE`; two starters racing on `schema-2` run the DDL once; a replayed upsert leaves one set of
  tokens. Verify: they fail.
- [ ] 3.2 `feat(search)`: add the `gav_key` column (nullable `binary(16)`, the first 16 bytes of the SHA-256 of the coordinates, D1 and
  D11), `search_token` and the `schema-2` marker step to `SearchSchema` and the table definitions, and write the key and tokens in the
  upsert of the database backend. Verify: the tests of 3.1 pass and the
  `schema-1` tests pass unedited.
- [ ] 3.3 `test(search)` then `feat(search)`: failing test first for the sweep: a fixed clock and a batch size of two fill the keys
  of five unkeyed rows in three transactions, a run drains them all, `tokensComplete` turns true, and a read-only instance runs no
  backfill. Then the keyset-paginated backfill on the 30-second scheduler tick of `SearchPlugin`, with a six-hour re-check, and the
  `tokensComplete` field and the `BUILDING` rule in `indexStateOf`. Verify: the tests pass.

## 4. Query (red, then green)

- [ ] 4.1 `test(search)`: capture the golden table against the code of main: a fixed fixture and queries in four groups (dotted-only
  and colon-only; the coordinates word with facets; several plain tokens; mixed dotted and plain), with the groups and their order
  that main returns, committed as test data. This is the characterisation test; it passes before the query change and must keep
  passing after it, in the form stated in the spec. Verify: it passes on main.
- [ ] 4.2 `test(search)`: add the word cases to the abstract `ArtifactSearchBackendContract` of `extract-search-backend-spi`:
  `co foo` still matches as on main; `jackson databind` matches `jackson-databind` by words; `spring core` matches after the rows of
  main; `frame core` does not match `spring-core`; `org.framework core` does not match it by word; `org.bench1` returns exactly the
  groups main returns; a stale token of a removed GAV returns nothing; a token with five parts returns what main returns and no
  error; six tokens give the 400 of main. Run them on SQLite and H2. Verify: they fail on the new cases.
- [ ] 4.3 `feat(search)`: add the per-token predicate to `SearchFilters`: each token keeps its main predicate, and a plain token with
  a word branch is unioned with it through index-driven subqueries, one per part, with the last part of the last token as a prefix,
  and the main rows are ordered before the word-only rows. Verify: the tests of 4.1 and 4.2 pass on SQLite and H2.
- [ ] 4.4 `test(search)`: add the word cases and the golden table as integration tests for MariaDB and PostgreSQL on the shared
  containers. Verify: `heavy ./gradlew :reposilite-backend:integration --tests '*DatabaseSearchBackendContractTest*' --max-workers=2`
  passes.

## 5. Benchmark gate (no commit to the code, then a decision)

- [ ] 5.1 `test(search)`: the benchmark harness, outside the unit suite and not run in CI. It generates synthetic GAVs with a fixed
  seed and runs 50 queries per kind (dotted-only, colon, one plain token, several plain tokens, mixed dotted then plain) on SQLite and
  PostgreSQL, against main as shipped and against this branch, each kind in a fresh JVM, through `heavy`. The gate runs at 200 000 GAVs.
  One run at one million GAVs records the load time, the backfill time and the token table size, and checks the PostgreSQL 10 second
  timeout. Verify: the table of numbers is produced for both builds.
- [ ] 5.2 Gate, decided by the maintainer if it fails: at 200 000 GAVs, p95 of the coordinate queries of this branch at most 1.2 times
  main's, and of the one-word queries at most 1.5 times main's, on both databases; at one million GAVs, no PostgreSQL statement over
  10 s. The several-token and mixed kinds are recorded against main and not gated yet. The numbers go in the code pull request
  description. If the gate fails, task 7.1 does not run.

## 6. Documentation

- [ ] 6.1 `docs(search)`: update the `q` row and the query rules in `docs/gitbook/reference/search-filters-and-api.md`: the rule per
  token, the dotted and colon tokens with no word match, the last token as a prefix, the ANDing, quotes, `OR`, `NOT` and `AND`, the
  limits of main, `tokensComplete`, and the `BUILDING` state during the sweep.
- [ ] 6.2 `docs(search)`: update `docs/gitbook/how-to/search-for-artifacts.md` with word examples (`jackson databind` finds
  `jackson-databind`, `frame core` does not find `spring-core`), the limits of version one, and state that a query with dotted tokens
  returns what it returns today. Add the word-only results to the upgrade notes in `docs/gitbook/reference/upgrade-changes.md`.
- [ ] 6.3 `docs(search)`: confirm with `grep -rn "starts with" docs/gitbook` that no page still describes `q` as prefix only.

## 7. Code pull request

- [ ] 7.1 Open the code pull request under the title `feat(search): match words of the coordinates in artifact search`, from a
  branch of the remote main branch that already contains the merged proposal and the merged seam change, with no file under
  `openspec/`, and with the benchmark numbers of 5.1 in its description. It opens only when the gate of 5.2 passes.

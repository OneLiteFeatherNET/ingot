# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `search`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
`Clock` is injected, and the sweep interval is a parameter), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite.

The golden table of coordinate queries is the one characterisation test: it is captured against the code of main and passes before
the query change. Its name says so.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-database-full-text-search`: this change and nothing else. Verify:
  `openspec validate add-database-full-text-search --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tokeniser and query kind (red, then green)

- [ ] 2.1 `test(search)`: write failing unit tests for `SearchTokens.of`: dotted groupIds, hyphenated artifactIds, versions such
  as `1.0-SNAPSHOT`, Unicode letters, duplicates, a token of 65 characters dropped, 33 tokens capped at 32. Write failing unit tests
  for the query kind (a dot or a colon gives a coordinate query, one word a single-word query, two or more words a word query), for
  the split of a word into parts, and for a word with more than four parts or no parts having no clause T. Verify: they fail on the
  missing classes.
- [ ] 2.2 `feat(search)`: add `SearchTokens` in core, pure, with no database or clock, and the query kind and the word-part split
  used by `SearchFilters`. Verify: the tests of 2.1 pass.

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

- [ ] 4.1 `test(search)`: capture the golden table of coordinate queries against the code of main: a fixed fixture, the dotted,
  colon, `g:a:v` and faceted queries, with the groups and their order that main returns, committed as test data. This is the
  characterisation test; it passes before the query change and must keep passing after it. Verify: it passes on main.
- [ ] 4.2 `test(search)`: add the word cases to the abstract `ArtifactSearchBackendContract` of `extract-search-backend-spi`:
  `core` finds `spring-core`; `spring core` finds it; `spring cor` finds it; `frame core` does not; `spring kafka` requires both;
  a single word puts the rule rows before the token rows; a previous single-word table keeps every group it matched before; a stale
  token of a removed GAV returns nothing; the five-part word `a-b-c-d-e` returns 200 with today's rule and no 400. Run them on SQLite
  and H2. Verify: they fail on the new cases.
- [ ] 4.3 `feat(search)`: add the three predicates to `SearchFilters`: a coordinate query gets no token clause, a single word gets
  the union with its last part as a prefix, a word query gets an exact clause per part and a prefix for the last part of the last
  word, with one `EXISTS` per part on `search_token`. Verify: the tests of 4.1 and 4.2 pass on SQLite and H2.
- [ ] 4.4 `test(search)`: add the word cases and the golden table as integration tests for MariaDB and PostgreSQL on the shared
  containers. Verify: `heavy ./gradlew :reposilite-backend:integration --tests '*DatabaseSearchBackendContractTest*' --max-workers=2`
  passes.

## 5. Benchmark gate (no commit to the code, then a decision)

- [ ] 5.1 `test(search)`: the benchmark harness, outside the unit suite and not run in CI. It generates one million synthetic GAVs
  with a fixed seed and runs 50 queries per kind (coordinate prefix, colon, single word, two-word word query) on SQLite and
  PostgreSQL, against main and against this branch, in one run through `heavy`. It records p50 and p95 per kind, the load time, the
  sweep time and the token table size. Verify: the table of numbers is produced for both builds.
- [ ] 5.2 Gate, decided by the maintainer if it fails: coordinate p95 of this branch no more than 10 percent above main's on both
  databases; no PostgreSQL statement over 10 s for any kind; single-word p50 and p95 recorded against 0.85 s and 1.53 s. The numbers
  go in the code pull request description. If the gate fails, task 7.1 does not run.

## 6. Documentation

- [ ] 6.1 `docs(search)`: update the `q` row and the query rules in `docs/gitbook/reference/search-filters-and-api.md`: the three
  kinds (a dot or a colon, one word, several words), the parts, the rule that every word but the last must be a whole token, quotes,
  `OR`, `NOT` and `AND`, a word of five parts matching by today's rule, `tokensComplete`, and the `BUILDING` state during the sweep.
- [ ] 6.2 `docs(search)`: update `docs/gitbook/how-to/search-for-artifacts.md` with word examples (`core` finds `spring-core`,
  `spring cor` finds it, `frame core` does not), the limits of version one, and state that coordinate queries are unchanged. Add the
  broader results of single-word and word queries to the upgrade notes in `docs/gitbook/reference/upgrade-changes.md`.
- [ ] 6.3 `docs(search)`: confirm with `grep -rn "starts with" docs/gitbook` that no page still describes `q` as prefix only.

## 7. Code pull request

- [ ] 7.1 Open the code pull request under the title `feat(search): match words of the coordinates in artifact search`, from a
  branch of the remote main branch that already contains the merged proposal and the merged seam change, with no file under
  `openspec/`, and with the benchmark numbers of 5.1 in its description. It opens only when the gate of 5.2 passes.

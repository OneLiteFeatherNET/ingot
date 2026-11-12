# Proposal

## Why

The search box of the dashboard and the `q` parameter of the search API are called full text search, but they are not. Today
a word matches when the groupId or the artifactId starts with it, or when the version equals it
(`docs/gitbook/reference/search-filters-and-api.md`, the `q` row; `database/SearchFilters.kt`, `freeText` and `plainToken`).
A search for `core` does not find `spring-core`, and a search for `framework` does not find `org.springframework`. Nothing
in the database index is tokenised: the tables have no full text index on any dialect. The artifact search spec ruled out
word matching for version 1 (`docs/superpowers/specs/2026-10-16-artifact-search.md`, question Q-9), and this change adds it.

An operator who searches for a library by a word of its name expects the word to match, on every database Ingot supports,
without setting up an extra service. The change keeps the database as the search engine, so nothing new has to run.

The change has one constraint above the others: pointing an existing installation at this version must not lose a result.
Every token of every query keeps exactly today's predicate, so a result that main returns is still returned.

The first version of this change decided the rule per query: a query with a dot or a colon anywhere kept today's rule, and any
other query matched by word. That broke the constraint. A query of plain words such as `co foo` lost the groups that match it
today, because it was treated as a word query, and the word rule replaced the prefix rule instead of adding to it. The decision
moved to the token, and the reasons and the rejected variants are in `design.md`, decision D10.

Word matching has a cost, which the benchmark measured. An earlier draft let every word also match by prefix, OR-ed with the
coordinate prefix. A dotted query such as `org.bench1` is split into words, and the word `bench1` prefix-matches `bench10` to
`bench199`: about 250 000 GAVs, against about 2 500 for the coordinate prefix. The numbers:

- Prefix p95 on SQLite went from 2.20 s on main to 12.72 s, about 5.8 times slower. On PostgreSQL it exceeded the 10 s
  statement timeout.
- Load time went from 264 s to 537 s on SQLite. The token table held 7.76 million rows and 739 MB on SQLite, and 2.04 GB
  on PostgreSQL.
- Sweeping the backlog once, with keyset pagination, took 295 s on SQLite and 1 100 s on PostgreSQL.

The token rule removes that cost for dotted tokens: a token with a dot or a colon gets nothing extra.

## What Changes

- **Every match is kept.** Each whitespace token keeps main's predicate exactly: a plain token matches the groupId prefix, the
  artifactId prefix or the exact version; a token with a colon is a coordinates token. Results only grow. Every row that main
  returns for a query is returned, in main's order, before any row that matches only by word. A query whose tokens all contain
  `.` or `:` returns exactly what main returns.
- **Word matching per token.** A whitespace token without `.` or `:` also matches by word, as a union with its main predicate.
  Its parts (the letter and digit runs, lower-cased) must each equal a token of the group exactly. The last part of the last
  token of the query may instead be the start of a token, so the search box keeps its type-ahead. Tokens are ANDed, as on main.
  Tokens are the letter and digit runs of the coordinates: `spring-core` gives `spring` and `core`.
- **Ordering.** The rows main returns come first, in main's order. The rows that match only by word follow, in the existing
  order.
- **Portable inverted index.** A table `search_token` maps each token of a GAV to the GAV's key. The lookup is a prefix
  range on a B-tree, the same technique the index already uses for prefixes (no `LIKE`, no database full text feature), so
  one implementation runs on SQLite, H2, MariaDB, MySQL and PostgreSQL.
- **Additive migration.** A nullable column `gav_key` on `search_artifact`, the new table, and their indexes. Nothing is
  renamed, dropped or rewritten. A version without this change ignores both.
- **Backfill.** Rows written before the migration have no key. A keyset-paginated backfill fills keys and tokens for them, and one
  run drains the whole backlog (about 5 minutes per million rows on SQLite, 18 minutes on PostgreSQL). The index state reads
  `BUILDING` while any row lacks a key. A six-hour re-check only finds work after a rollback.
- **Benchmark gate.** Before the code pull request opens, the benchmark runs on this branch and on main as shipped, p95 in a fresh
  JVM per kind. Coordinate queries may take at most 1.2 times main's p95, one-word queries at most 1.5 times. The implementation
  measured 1.04x and 1.12x on SQLite and 1.00x and 1.03x on PostgreSQL, at 200 000 GAVs (`design.md`, D10 and D11).
- **Documentation.** The query syntax as it is in version 1 (the per-token rule, the state `BUILDING` during the sweep), and
  the broader results in the upgrade notes.

### Out of scope

- Searching inside files and reading POM files (name, description, packaging). Both need a parser with a size limit and
  are separate changes.
- Relevance ranking. The sort keys stay as they are (question Q-5 of the search spec). Word-only rows follow the main rows
  and nothing else changes in the order.
- Phrases, field names such as `artifactId:`, `OR` and `NOT` operators, and wildcards. In version 1 quotes are ignored,
  and `OR`, `NOT` and `AND` are plain words that must match too.
- The limits of main: five tokens, 200 characters, the 400 responses and the facets keep their behaviour exactly.
- Native database full text (SQLite FTS5, MariaDB `FULLTEXT`, PostgreSQL `tsvector`). The design explains why not.
- A configuration key. Nothing is switched on or off; the feature is part of the database backend.

## Capabilities

### New Capabilities

- `search-full-text`: word and token matching of the `q` parameter, the per-token rule and its ordering, the token index, its
  migration and backfill, the benchmark gate, and the states that report it.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)` and
  `docs(search)`. The pull request title is:

  `feat(search): match words of the coordinates in artifact search`

- **Breaking marker.** No breaking change. No configuration key, environment variable, route, error or response field is
  removed or renamed. `indexStatus` gains `tokensComplete` as an additional field, and `state` can be `BUILDING` for the duration
  of the sweep on an upgraded installation. Every query returns every row it returns today, and word-only rows can be added
  after them, which the upgrade notes state. A query whose tokens all contain `.` or `:` is unchanged. The migration only adds,
  so a rollback to the previous version works without a step.
- **Code.** `reposilite-backend`, package `net.onelitefeather.ingot.search`: the tokeniser, the token predicate, the tables, the
  backend, the sweep and the schema. Frontend: none. The dashboard already sends `q` unchanged.
- **Order.** Depends on `extract-search-backend-spi`, because the table and the query live in the database backend that the
  seam introduces.

## Verification

- Unit tests for the tokeniser, the token predicate, the query translation and the migration statements, on SQLite and H2 file
  databases.
- A golden table of queries captured from main before the query change: the coordinate queries must return identical groups in
  identical order, and the plain-token queries must return every main row in main's order, followed by any word-only rows.
- The contract suite of `extract-search-backend-spi` with the word cases added, on SQLite, H2, MariaDB and PostgreSQL, run
  through `heavy`.
- The benchmark gate of `design.md`, run through `heavy`, with its numbers in the pull request description. At one million GAVs the
  PostgreSQL 10 second timeout is still to be checked.
- The upgrade rehearsal in the container job keeps passing: a database from the previous image is opened by this image,
  searched, and read back with the previous image after a rollback.
- `openspec validate add-database-full-text-search --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-database-full-text-search` prints nothing.

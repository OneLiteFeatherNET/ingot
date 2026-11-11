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

The first version of this change let every word also match tokens by prefix, OR-ed with the existing coordinate prefix. A
benchmark on one million synthetic GAVs (50 queries per kind) showed that this makes existing searches much slower. A dotted
query such as `org.bench1` is split into words, and the word `bench1` prefix-matches `bench10` to `bench199`: about 250 000 GAVs,
against about 2 500 for the coordinate prefix `org.bench1`. The OR forces all of them into the result. The numbers:

- Prefix p95 on SQLite went from 2.20 s on main to 12.72 s, about 5.8 times slower. On PostgreSQL it exceeded the 10 s
  statement timeout.
- Load time went from 264 s to 537 s on SQLite. The token table held 7.76 million rows and 739 MB on SQLite, and 2.04 GB
  on PostgreSQL.
- Sweeping the backlog once, with keyset pagination, took 295 s on SQLite and 1 100 s on PostgreSQL.

The maintainer decided to split the query by its input. A query that names a coordinate keeps today's behaviour exactly. A
query of loose words gets word matching. The decision and the rejected alternatives are in `design.md`, decision D10.

## What Changes

- **Query split by input.** A query that contains `.` or `:` is a coordinate query. It runs today's rules and no predicate on
  the token table: same results, same order, same speed. This is a drop-in change for every existing coordinate search.
- **Word matching for loose words.** A query of two or more whitespace-separated words, with no `.` or `:`, matches a group
  when every word matches it (AND). Within a word, every part (the letter and digit runs, lower-cased) except the last part of
  the last word must equal a token exactly. The last part of the last word matches a token by prefix, so the search box keeps
  its type-ahead. Tokens are the letter and digit runs of the coordinates: `spring-core` gives `spring` and `core`.
- **Single word.** A query of one word, with or without a hyphen, returns the rows that today's rule matches first, then the
  rows that match by token (prefix on the last part). Each part keeps its existing order. This is the union of the two rules
  for one word. Its cost is measured and recorded below.
- **Portable inverted index.** A table `search_token` maps each token of a GAV to the GAV's key. The lookup is a prefix
  range on a B-tree, the same technique the index already uses for prefixes (no `LIKE`, no database full text feature), so
  one implementation runs on SQLite, H2, MariaDB, MySQL and PostgreSQL.
- **Additive migration.** A nullable column `gav_key` on `search_artifact`, the new table, and their indexes. Nothing is
  renamed, dropped or rewritten. A version without this change ignores both.
- **Backfill.** Rows written before the migration have no key. A periodic sweep fills keys and tokens for them, and the
  index state reads `BUILDING` while any row lacks a key.
- **Benchmark gate.** Before the code pull request opens, the benchmark of the design runs on this branch and on main in the
  same run. Coordinate queries must not get slower beyond a margin, and no PostgreSQL statement may pass the timeout.
- **Documentation.** The query syntax as it is in version 1 (the split, the parts, the state `BUILDING` during the sweep),
  and the broader results of single-word and word queries in the upgrade notes. Coordinate queries are documented as unchanged.

### Out of scope

- Searching inside files and reading POM files (name, description, packaging). Both need a parser with a size limit and
  are separate changes.
- Relevance ranking. The sort keys stay as they are (question Q-5 of the search spec). Single-word results keep the
  prefix-first split described above and nothing else.
- Phrases, field names such as `artifactId:`, `OR` and `NOT` operators, and wildcards. In version 1 quotes are ignored,
  and `OR`, `NOT` and `AND` are plain words that must match too.
- Changing the rules of coordinate queries in any way, including the `g:a:v` coordinates word and the facets.
- Native database full text (SQLite FTS5, MariaDB `FULLTEXT`, PostgreSQL `tsvector`). The design explains why not.
- A configuration key. Nothing is switched on or off; the feature is part of the database backend.

## Capabilities

### New Capabilities

- `search-full-text`: word and token matching of the `q` parameter, the split by query kind, the token index, its migration
  and backfill, the benchmark gate, and the states that report it.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)` and
  `docs(search)`. The pull request title is:

  `feat(search): match words of the coordinates in artifact search`

- **Breaking marker.** No breaking change. No configuration key, environment variable, route or response field is removed
  or renamed. `indexStatus` gains `tokensComplete` as an additional field, and `state` can be `BUILDING` for the duration of
  the sweep on an upgraded installation. Coordinate queries return exactly what they returned before. Single-word and word
  queries can return groups they did not return before, which the upgrade notes state. The migration only adds, so a
  rollback to the previous version works without a step.
- **Code.** `reposilite-backend`, package `net.onelitefeather.ingot.search`: the tokeniser, the query kind, the tables, the
  backend, the sweep and the schema. Frontend: none. The dashboard already sends `q` unchanged.
- **Order.** Depends on `extract-search-backend-spi`, because the table and the query live in the database backend that the
  seam introduces.

## Verification

- Unit tests for the tokeniser, the query kind, the query translation and the migration statements, on SQLite and H2 file
  databases.
- A golden table of coordinate queries, captured from main before the query change, that must return the same groups in the
  same order after it.
- The contract suite of `extract-search-backend-spi` with the word cases added, on SQLite, H2, MariaDB and PostgreSQL, run
  through `heavy`.
- The benchmark gate of `design.md`, run through `heavy`, with its numbers in the pull request description.
- The upgrade rehearsal in the container job keeps passing: a database from the previous image is opened by this image,
  searched, and read back with the previous image after a rollback.
- `openspec validate add-database-full-text-search --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-database-full-text-search` prints nothing.

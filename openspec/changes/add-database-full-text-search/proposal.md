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

## What Changes

- **Word matching over coordinates.** A plain word matches a group, artifact or version when today's rule matches it, and
  also when every part of the word starts a token of the group, artifact or version. Tokens are the letter and digit runs
  of the coordinates, lower-cased: `spring-core` gives `spring` and `core`. Results only grow: every row a query matches
  today still matches.
- **Portable inverted index.** A table `search_token` maps each token of a GAV to the GAV's key. The lookup is a prefix
  range on a B-tree, the same technique the index already uses for prefixes (no `LIKE`, no database full text feature), so
  one implementation runs on SQLite, H2, MariaDB, MySQL and PostgreSQL.
- **Additive migration.** A nullable column `gav_key` on `search_artifact`, the new table, and their indexes. Nothing is
  renamed, dropped or rewritten. A version without this change ignores both.
- **Backfill.** Rows written before the migration have no key. A periodic sweep fills keys and tokens for them, and the
  index state reads `BUILDING` while any row lacks a key.
- **Documentation.** The query syntax as it is in version 1, the state `BUILDING` during the sweep, and the broader results
  in the upgrade notes.

### Out of scope

- Searching inside files and reading POM files (name, description, packaging). Both need a parser with a size limit and
  are separate changes.
- Relevance ranking. The sort keys stay as they are (question Q-5 of the search spec).
- Phrases, field names such as `artifactId:`, `OR` and `NOT` operators, and wildcards. In version 1 quotes are ignored,
  and `OR` and `NOT` are plain words that must match too.
- Native database full text (SQLite FTS5, MariaDB `FULLTEXT`, PostgreSQL `tsvector`). The design explains why not.
- A configuration key. Nothing is switched on or off; the feature is part of the database backend.

## Capabilities

### New Capabilities

- `search-full-text`: word and token matching of the `q` parameter, the token index, its migration and backfill, and the
  states that report it.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)` and
  `docs(search)`. The pull request title is:

  `feat(search): match words of the coordinates in artifact search`

- **Breaking marker.** No breaking change. No configuration key, environment variable, route or response field is removed
  or renamed. `indexStatus` gains `tokensComplete` as an additional field, and `state` can be `BUILDING` for the duration of
  the sweep on an upgraded installation. Search results can contain groups they did not contain before, which the upgrade
  notes state. The migration only adds, so a rollback to the previous version works without a step.
- **Code.** `reposilite-backend`, package `net.onelitefeather.ingot.search`: the tokeniser, the tables, the backend, the
  sweep and the schema. Frontend: none. The dashboard already sends `q` unchanged.
- **Order.** Depends on `extract-search-backend-spi`, because the table and the query live in the database backend that the
  seam introduces.

## Verification

- Unit tests for the tokeniser, the query translation and the migration statements, on SQLite and H2 file databases.
- The contract suite of `extract-search-backend-spi` with the word cases added, on SQLite, H2, MariaDB and PostgreSQL, run
  through `heavy`.
- The upgrade rehearsal in the container job keeps passing: a database from the previous image is opened by this image,
  searched, and read back with the previous image after a rollback.
- `openspec validate add-database-full-text-search --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-database-full-text-search` prints nothing.

# Design

Paths are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/` on the current main branch.
After `extract-search-backend-spi`, the classes named `DatabaseArtifactSearchEngine` and `DatabaseArtifactIndex` are parts of
`database/DatabaseSearchBackend.kt`; the line references below are to the current files.

## Context: what a query does today

- `q` is split on whitespace, at most 5 words, each at most 200 characters in total (`database/SearchFilters.kt:67-68`,
  `:81-89`). Each word is lower-cased. A word with a colon is a coordinates word (`:98-115`), otherwise
  `plainToken` matches a prefix of the groupId, a prefix of the artifactId or an equal version (`:94-95`).
- Prefix matches are half-open ranges on the lower-cased columns, `column >= p AND column < next(p)` (`:167-171`). No
  `LIKE` is used anywhere in the search code. Grep of the repository finds no `FTS5`, `FULLTEXT`, `tsvector`, `MATCH AGAINST`
  or `pg_trgm` in the backend.
- Result groups are ordered by an explicit sort key, and the tie-breakers are fixed (`database/DatabaseArtifactSearchEngine.kt:214-250`).
  There is no score.
- The index state comes from `indexStateOf` (`database/DatabaseArtifactSearchEngine.kt:454-469`), a function of the state
  inputs. `IndexStatus` has no field for the search tokens (`ArtifactSearchEngine.kt:186-193`).

## Scope of "full text" in version 1

Full text here means word matching over the coordinates of every indexed GAV: groupId, artifactId and version. These are
the fields the search returns and the only text the index holds. File names are in the path, and POM fields are not indexed
at all (search spec, non-goal 1.2). Searching inside files is not part of this change.

## Decisions

### D1: tokens are a derived inverted index keyed by GAV

`search_token(gav_key, token)` holds the tokens of each GAV. The key is the lower-case form of `groupId`, `artifactId` and
`version` joined with NUL, hashed with SHA-256 to 64 hex characters. A GAV has three to about seven tokens, so the table
grows with GAVs, not files: a repository of ten million files and one million GAVs gives about seven million token rows.

Tokens are computed by one pure function in core, `SearchTokens.of(groupId, artifactId, version)`. It splits on every
character that is not a letter or a digit (Unicode classes), lower-cases with `Locale.ROOT`, removes duplicates, drops a
token longer than 64 characters, and keeps at most 32 tokens per GAV. The function is shared by every backend, so a second
backend (the OpenSearch plugin) produces the same tokens.

### D2: query semantics

For each plain word `w` (lower-cased, as today), the row matches when any of these holds:

1. the groupId starts with `w` (existing),
2. the artifactId starts with `w` (existing),
3. the version equals `w` (existing),
4. every part of `w` split on non-letter and non-digit characters starts some token of the GAV.

Clause 4 is an `EXISTS` per part on `search_token` with `gav_key` equal to the row's key and `token` in the half-open range of
the part. A word without separators has one part, so `core` matches `spring-core` by its token. A word with more than four
parts is refused with 400 naming `q`, so the number of `EXISTS` clauses stays bounded.

Coordinates words (`g:a:v`), the limit of 5 words, the 200 character limit, the 400 responses and the facets keep their
behaviour. The predicate of clause 4 is added as an `OR` to the existing predicate of the word, so every row matched before is
still matched. The contract test of D7 checks this for a table of previous queries.

### D3: why no native full text

| Option | Why not in version 1 |
|---|---|
| SQLite FTS5 | Its availability in the bundled driver (`sqlite-jdbc` 3.53.2.1, `reposilite-backend/build.gradle.kts:146`) is unverified, and its tokenizer, prefix syntax and ranking differ from the other dialects, so results would differ by database. |
| MariaDB and MySQL `FULLTEXT` | Words shorter than `innodb_ft_min_token_size` (3 by default) and stop words are not indexed; hyphens and dots split differently from the rules of D1. |
| PostgreSQL `tsvector` with GIN | A stemming configuration changes matches (`simple` does not stem, `english` does). Results would differ from the other databases. |
| H2 full text | Experimental database in Ingot's support table; no integration coverage. |

The token table is portable: a B-tree range works on every supported dialect. So the fallback that the question of
databases without full text would need is the default route itself, and no dialect needs a second implementation. A native
index can be added later as an accelerator, if it returns exactly the rows of D2; the contract suite decides that.

### D4: schema migration is additive and recorded with its own marker

- Fresh installations: the `schema-1` transaction creates `search_artifact` with the new column and the new index, and
  `search_token`, and then inserts the marker `schema-2`.
- Installations at `schema-1`: a second marker transaction `schema-2` runs `ALTER TABLE search_artifact ADD COLUMN gav_key`,
  `CREATE INDEX ix_search_gav_key`, and `CREATE TABLE search_token` with its index. It uses the same insert-ignore race
  protection as `SearchSchema.ensure` (`database/SearchSchema.kt:35-53`). Two starters serialise on the marker row.
- The statements are generated and checked: a test asserts that the statements for each dialect contain only `ADD COLUMN`,
  `CREATE INDEX` and `CREATE TABLE`, with no `DROP`, `RENAME`, `MODIFY` or type change of an existing column.
- Column type: `char(64)`, nullable. Key sizes on MariaDB: the token primary key is `char(64)` plus `varchar(64)` at four
  bytes each, 512 bytes, well inside the 3072-byte limit that `SearchDialect.maxIndexKeyBytes` checks.

### D5: writes keep tokens in step with rows

`Upsert` writes the row with its `gav_key` and inserts the GAV's tokens with insert-ignore, in the same transaction, so a
replayed action changes nothing. `RemovePath` and `RemovePrefix` do not delete tokens. A stale token is harmless: every
search reads tokens through a row with the same `gav_key`, and a GAV without rows is not returned. Tokens of a GAV are a
pure function of its coordinates, so a GAV that comes back has the same tokens. A later compaction may remove tokens with no
row; it is not needed for correctness and is not part of this change.

### D6: backfill is a periodic sweep, not a one-off flag

A sweep runs every six hours on the existing 30-second scheduler tick of `SearchPlugin` (`application/SearchPlugin.kt:200`),
and only when the instance is not read-only. It reads up to 1000 rows with `gav_key IS NULL` in id order, fills their keys
and inserts their tokens in one transaction, and stops when none is left. The query uses `ix_search_gav_key`, so a database
with no unkeyed rows pays one index probe per sweep.

The sweep is a sweep, not a flag, because a rollback can leave unkeyed rows behind: the previous version writes rows without
a key. A roll-forward then finds them and fixes them.

`IndexStatus` gets `tokensComplete: Boolean`, computed per request as "no unkeyed row exists" (one indexed probe). While it is
false, `indexStateOf` returns `BUILDING` unless the state is already `DEGRADED`. The field defaults to `true` in the JSON
model, so an older client reads the same as before.

### D7: tests

- Unit (fast, no database): `SearchTokens.of` for dotted groupIds, hyphenated artifactIds, snapshot and classifier-free
  versions, Unicode letters, duplicates, the 64 character and 32 token caps; the parts split of a word; the refusal above
  four parts.
- Unit on SQLite and H2 file databases (`@TempDir`): every row matched by the previous query table still matches; `core`
  finds `spring-core`; `spring core` requires both words; a word that matches no token gives no row; a stale token of a
  removed GAV does not return it; a replayed upsert leaves one token row.
- Migration: a database created with the `schema-1` layout and filled with rows, then opened by this version, keeps its
  rows; `tokensComplete` is false; one sweep makes it true; the search finds the old GAVs afterwards. A test checks the
  generated statements of D4.
- Contract suite (`extract-search-backend-spi`): the word cases are added to the abstract suite, so every backend that
  implements the interface must pass them.
- Integration on MariaDB and PostgreSQL: the migration and the word cases, on the shared containers.
- No test reads the wall clock: the sweep takes the injected `Clock`, and its interval is a parameter of the function
  that decides whether a sweep is due.

### D8: query syntax exposed to users

- Plain words: prefix match on the words of the coordinates, as in D2. Case does not matter.
- Coordinates words: `group:artifact:version`, unchanged.
- Punctuation inside a word splits it into parts that must all match, as in D2.
- Quotes are ignored. A phrase search is not supported.
- `OR`, `NOT`, `AND` are plain words and must match like any other word.
- No wildcard characters. A `*` is ignored like punctuation.

### D9: ranking

No relevance ranking. Results keep the explicit sort keys and the fixed tie-breakers. A "best match first" sort needs a
ranking function that exists in every dialect, which D3 showed does not exist, so it is a question for a later change.

## Risks

- **Query cost.** Each word part adds a correlated `EXISTS`. The window of 10 000 entries and the 10 second statement timeout
  remain the bounds. A benchmark on a synthetic dataset of one million GAVs runs through `heavy` before the change is merged,
  and its numbers go in the pull request description; no test asserts a time.
- **Broader results.** A query that matched five groups may match fifty. This is the requested behaviour, and the upgrade
  notes say it. The contract suite pins that no previous match is lost.
- **Index size.** About seven token rows per GAV. Measured in the benchmark above.

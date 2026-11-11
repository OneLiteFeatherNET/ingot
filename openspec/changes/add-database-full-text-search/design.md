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

### D2: query semantics by query kind

The kind of a query is decided from the trimmed `q` before any SQL is built. There are three kinds.

1. **Coordinate query**: `q` contains `.` or `:` anywhere. It runs today's rules unchanged: the groupId and artifactId prefix,
   the equal version, the `g:a:v` coordinates word, the limit of 5 words, the 200 character limit, the facets. It adds no
   predicate on `search_token`. A golden table of coordinate queries, captured from main before the change, pins the groups
   and their order (D7).
2. **Single word**: `q` is one whitespace-separated word without `.` or `:`. A hyphen is an ordinary character here, so
   `spring-co` is a single word. A row matches when today's rule matches it, or when its token predicate (clause T below)
   matches. Rows of today's rule come first, in the existing order; rows that match only by token follow, in the existing
   order. This split applies under every sort key, and the sort key orders each of the two parts.
3. **Word query**: two or more whitespace-separated words, without `.` or `:`. Every word must match the same row (AND).

For a word, the parts are the letter and digit runs of the lower-cased word. In a word query, every part of every word must
equal a token of the row exactly, except the last part of the last word, which must be the start of a token. A word with no
parts (only punctuation) has no token clause.

Clause T of a word with parts `p1` to `pn` is the conjunction, over its parts, of an `EXISTS` on `search_token` with
`gav_key` equal to the row's key and `token` equal to the part (exact), or in the half-open range of the part (prefix). A word
with more than four parts gets no clause T and matches by today's rule only. This keeps the number of `EXISTS` clauses bounded
at four per word, and it adds no error response for a `q` that is accepted today.

Single word: the row matches when `rule(w) OR T(w)`. Word query: the row matches when, for every word `w`, `T(w)` holds if `w`
has clause T, and `rule(w)` holds otherwise. Today's rule is not added to a word that has clause T; that is Open question 1.

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

The write path pays for the tokens whatever queries are run: the load measured in D10 is the cost of every installation,
including one that only searches coordinates.

### D6: backfill is a periodic sweep, not a one-off flag

A sweep runs every six hours on the existing 30-second scheduler tick of `SearchPlugin` (`application/SearchPlugin.kt:200`),
and only when the instance is not read-only. It reads up to 1000 rows with `gav_key IS NULL` in id order, fills their keys
and inserts their tokens in one transaction, and stops when none is left. The query uses `ix_search_gav_key`, so a database
with no unkeyed rows pays one index probe per sweep.

The sweep is a sweep, not a flag, because a rollback can leave unkeyed rows behind: the previous version writes rows without
a key. A roll-forward then finds them and fixes them.

`IndexStatus` gets `tokensComplete: Boolean`, computed per request as "no unkeyed row exists" (one indexed probe). While it is
false, `indexStateOf` returns `BUILDING` unless the state is already `DEGRADED`. The field defaults to `true` in the JSON
model, so an older client reads the same as before. The sweep of one million unkeyed rows measured in D10 is the time the
index stays in `BUILDING`.

### D7: tests

- Unit (fast, no database): `SearchTokens.of` for dotted groupIds, hyphenated artifactIds, snapshot and classifier-free
  versions, Unicode letters, duplicates, the 64 character and 32 token caps; the parts split of a word; the query kind of a
  query (dot or colon, one word, several words); a word with more than four parts or no parts has no clause T.
- Golden table (characterisation, written against main before the query change and kept): coordinate queries of the
  dashboard and the API, dotted, colon, `g:a:v` and with facets, on a fixed fixture. The groups and their order must be the
  same after the change. This is the one test that passes before the production change; it is a characterisation test and
  says so in its name.
- Unit on SQLite and H2 file databases (`@TempDir`): a single word finds `spring-core` by its token; a single word keeps the
  rule rows first; `spring core` requires both words; `spring cor` matches by the prefix of the last word; `frame core` does not
  match `org.springframework:spring-core`; a word that matches no token gives no row; a stale token of a removed GAV does not
  return it; a replayed upsert leaves one token row; a five-part word returns the rows of today's rule and no error.
- Migration: a database created with the `schema-1` layout and filled with rows, then opened by this version, keeps its
  rows; `tokensComplete` is false; one sweep makes it true; the search finds the old GAVs afterwards. A test checks the
  generated statements of D4.
- Contract suite (`extract-search-backend-spi`): the word cases are added to the abstract suite, so every backend that
  implements the interface must pass them.
- Integration on MariaDB and PostgreSQL: the migration, the word cases and the golden table, on the shared containers.
- No test reads the wall clock: the sweep takes the injected `Clock`, and its interval is a parameter of the function
  that decides whether a sweep is due. The benchmark of D10 measures time by design; it is a harness, not a unit test, and
  no test asserts a time.

### D8: query syntax exposed to users

- Coordinate queries (contain `.` or `:`): as today. Prefix of the groupId and artifactId, equal version, `g:a:v`.
- Single word: prefix of the groupId or artifactId, equal version, or a match of its parts among the tokens, the last part as a
  prefix. Rows of the first kind come first.
- Several words: every word matches the same group. Every part of every word must be a whole token, except the last part of the
  last word, which may be the start of a token. Type `frame core` and the search finds nothing for `spring-core`, because
  `frame` is not a token; type `spring cor` and it finds `spring-core`, because `spring` is a token of its artifactId.
- Quotes are ignored. A phrase search is not supported.
- `OR`, `NOT`, `AND` are plain words and must match like any other word.
- No wildcard characters. A `*` is ignored like punctuation.

### D9: ranking

No relevance ranking. Results keep the explicit sort keys and the fixed tie-breakers. A "best match first" sort needs a
ranking function that exists in every dialect, which D3 showed does not exist, so it is a question for a later change.

### D10: split by input, and the numbers that decided it

**Context.** The first version of this change matched every word by token and by prefix, OR-ed with the coordinate prefix. A
benchmark on one million synthetic GAVs, 50 queries per kind, showed the cost. A dotted query is split into words, and the word
`bench1` prefix-matches `bench10` to `bench199`: about 250 000 GAVs, against about 2 500 for the coordinate prefix. The OR forced
all of them into the result.

| Measure | main | first version | source |
|---|---|---|---|
| Prefix p95, SQLite | 2.20 s | 12.72 s (about 5.8 times) | benchmark |
| Prefix p95, PostgreSQL | not in the summary | above the 10 s statement timeout | benchmark |
| Load, SQLite | 264 s | 537 s | benchmark |
| Token table, SQLite | none | 7.76 million rows, 739 MB | benchmark |
| Token table, PostgreSQL | none | 2.04 GB | benchmark |
| Sweep of the backlog, SQLite and PostgreSQL | none | 295 s and 1 100 s (keyset pagination) | benchmark |
| Single word p50 / p95, SQLite | 0.85 s / 1.53 s | 0.96 s / 1.73 s (about 13 percent slower) | benchmark |

**Options.**

- A. Keep the first version: every word also matches by token and prefix, OR-ed with the coordinate prefix. Rejected. A
  dotted search, which is the common case for an operator who knows the coordinate, gets about 5.8 times slower on SQLite and
  times out on PostgreSQL.
- B. Keep A and raise the statement timeout. Rejected. It moves the cost into request latency and connection occupancy, the
  250 000 candidate rows still reach the query, and SQLite stays 5.8 times slower.
- C. Exact-only words always on, with no prefix. Rejected. The search box searches while the user types. The last word must
  stay a prefix, as it is today on groupId and artifactId. Exact-only would make `cor` find nothing, and it would remove
  type-ahead that works today.
- D. Split by input (chosen). A query with `.` or `:` keeps today's rules and adds no predicate, so its cost stays the same. A
  query of loose words gets word matching: exact for every part but the last, prefix for the last, AND across words. A single
  word keeps the union, and its cost is recorded.

**Consequences.**

- Coordinate queries are unchanged. This is checked by the golden table (D7) and by the benchmark gate below.
- A single word costs about 13 percent more at p50 and p95 on SQLite (0.96 s and 1.73 s against 0.85 s and 1.53 s). The
  maintainer accepted this for the single word search, which is the one that changes behaviour the most.
- The write path and the disk cost stay for every installation, including one that only searches coordinates. The load time
  is 537 s against 264 s, and the token table is 739 MB on SQLite and 2.04 GB on PostgreSQL per million GAVs. Only a change of
  the write path (for example, tokens written only for GAVs that are searched by word) could remove it. That is not part of
  this change.
- Users who type a word that is not a whole token before the last one do not get the group. `spring cor` finds
  `org.springframework:spring-core`, `frame core` does not. Stated in the user documentation (task 6.1).

**Benchmark gate.** Before the code pull request opens (task 5.2), the benchmark runs on this branch and on main, on the same
dataset and the same hardware, in one run. It covers 50 queries per kind: coordinate prefix, colon, single word and two-word
word query, on SQLite and PostgreSQL. The gate is:

- coordinate queries: p95 of this branch no more than 10 percent above the p95 of main in the same run, on both databases;
- no PostgreSQL statement over the 10 s timeout for any kind;
- single word: p50 and p95 recorded against 0.85 s and 1.53 s, and not gated beyond the timeout;
- load time, sweep time and token table size recorded.

If the gate fails, the code pull request does not open, and the maintainer decides. The 10 percent margin is a proposal of this
change; the maintainer can set another value.

## Risks

- **Query cost.** Each word part adds a correlated `EXISTS`, bounded by four parts per word and five words per query. The
  window of 10 000 entries and the 10 second statement timeout remain the bounds. The benchmark gate of D10 runs before the code
  pull request opens, and its numbers go in the pull request description; no test asserts a time.
- **Broader results.** Single-word and word queries can return groups they did not return before. Coordinate queries do not.
  The upgrade notes say it. The golden table and the contract suite pin the coordinate results.
- **Index size and load.** About seven token rows per GAV, and the load time of D10, are paid by every installation.
- **Several-word queries lose some previous matches.** Open question 1 below.

## Open questions

Open questions for the maintainer. Each has a default in the spec and tasks, so the change can proceed without an answer, but the
default is not final until the maintainer confirms it.

1. **Several-word queries and the previous rule.** Under the rule of D2, a word query matches a word only by its exact tokens
   (and the last part by prefix). The previous version matched each word by the groupId prefix and the artifactId prefix. So
   `co foo` loses a group whose groupId is `com.x` and whose artifactId starts with `foo`, which today matches, and
   `spring cor` finds nothing for `springframework`, because `spring` is not a token of `org.springframework`. Default: keep the
   rule as the maintainer wrote it, and document the loss. Alternative: each word of a word query also matches by today's
   rule, which restores "results only grow" for several-word queries; its cost would be checked by the same benchmark gate. The
   spec's "results only grow" requirement covers coordinate and single-word queries until the maintainer decides.
2. **Hyphenated single word.** D2 treats `spring-co` as one word, so it keeps today's rule rows and adds the token rows. The
   brief named the single word with no separator; a hyphen is a separator, so this is a reading of the brief. Default: one
   word, hyphen allowed, union. Confirm or say that a hyphenated word is a word query.
3. **Words of more than four parts.** The earlier design refused them with 400 naming `q`. This revision answers the question
   differently: such a word has no clause T and matches by today's rule only, so no request that is accepted today is refused.
   The bound on `EXISTS` clauses is kept. This replaces the 400 of the earlier design and needs no further answer unless the
   maintainer prefers the refusal.
4. **Explicit sort keys for single words.** The prefix rows come first, and the sort key orders each part. Whether a user who
   passes `sort` expects one merged order is not decided. Default: the split holds under every sort key.

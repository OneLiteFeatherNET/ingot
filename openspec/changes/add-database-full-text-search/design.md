# Design

Paths are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/` on the current main branch.
After `extract-search-backend-spi`, the classes named `DatabaseArtifactSearchEngine` and `DatabaseArtifactIndex` are parts of
`database/DatabaseSearchBackend.kt`; the line references below are to the current files.

## Context: what a query does today

- `q` is split on whitespace. More than 5 tokens is a 400 naming `q`, and a query longer than 200 characters is a 400 (`database/SearchFilters.kt`,
  `freeText`, `:81-89`). Each token is lower-cased and all tokens are ANDed (`:88-89`).
- A token with a colon is a coordinates token (`coordinates`, `:98-115`), with at most three parts. Any other token is a plain token,
  `plainToken`: the groupId starts with it, or the artifactId starts with it, or the version equals it (`:94-95`).
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

`search_token(gav_key, token)` holds the tokens of each GAV. The key is the first 16 bytes of the SHA-256 of the lower-case form of
`groupId`, `artifactId` and `version` joined with NUL, stored as `binary(16)`. The 16-byte key replaced a 64-character hex key in the
implementation, for the width reason in D11. A GAV has three to about seven tokens, so the table
grows with GAVs, not files: a repository of ten million files and one million GAVs gives about seven million token rows.

Tokens are computed by one pure function in core, `SearchTokens.of(groupId, artifactId, version)`. It splits on every
character that is not a letter or a digit (Unicode classes), lower-cases with `Locale.ROOT`, removes duplicates, drops a
token longer than 64 characters, and keeps at most 32 tokens per GAV. The function is shared by every backend, so a second
backend (the OpenSearch plugin) produces the same tokens.

### D2: the rule is per whitespace token, and every main predicate is kept

Let `q` be split into tokens as on main: whitespace, at most 5, each lower-cased. For each token `t`:

- **Main predicate `M(t)`** is exactly today's predicate: `plainToken` if `t` has no colon, `coordinates` if it has one. Nothing
  about it changes.
- **Word predicate `W(t)`** exists only when `t` contains neither `.` nor `:`. Split `t` into parts, the runs of letters and
  digits. If `t` has no parts, or more than four, `W(t)` does not exist. Otherwise `W(t)` holds for a row when every part
  matches a token of the row's GAV in the same GAV: exactly, or, for the last part of the last token of the query, as the
  start of a token. "Last token of the query" is the last whitespace token that main keeps, whatever its kind.
- **Token predicate** `T(t) = M(t) OR W(t)`, or `T(t) = M(t)` when `W(t)` does not exist.

The query matches a row when `T(t)` holds for every token. This is the AND of main, with each operand widened by its own word
match. A token with a dot or a colon has `T(t) = M(t)`, so a query whose tokens all have dots or colons is exactly main.

**Ordering.** A row that matches the query under `M` alone for every token is returned first, in main's order. A row that matches
only through word predicates follows, in main's order. The sort key of the request orders each of the two parts.

**SQL shape.** Each token is one predicate, built from index-driven subqueries that are combined by `UNION`, never by an SQL `OR`
between the main predicate and a correlated `EXISTS`:

```
id IN ( <M(t) rows from search_artifact>
        UNION
        ( <part 1 rows> INTERSECT <part 2 rows> ... ) )   -- the word branch, only when W(t) exists
```

Each part row set is `SELECT a.id FROM search_token t JOIN search_artifact a ON a.gav_key = t.gav_key WHERE t.token = :p` (exact)
or the half-open range of the prefix. The main branch uses the indexes it uses on main. The token branch uses the token index.
The planner then sees one branch per index, and the main branch cannot be dropped by the optimiser when a word is present.

**PostgreSQL.** The implementation evaluates the word arm there as a materialised set of word ids. The word-only rows are that set
`EXCEPT` the main-predicate rows, and they follow the main rows. The `OR` form of the same predicate made the planner choose a
sequential scan, so it was not used (D11). The result order is the same as the union form gives.

**Limits and errors.** The 5 token limit, the 200 character limit, the 400 responses and the coordinates limits are main's, and they
are checked in the same places. This version adds no error response. A token with more than four parts, or with no parts, has no
word branch and is matched by main alone, so no query that main accepts is refused.

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
- Column type: `binary(16)`, nullable (D1, D11). Key sizes on MariaDB: the token primary key is `binary(16)` plus `varchar(64)` at four
  bytes per character, 272 bytes, well inside the 3072-byte limit that `SearchDialect.maxIndexKeyBytes` checks.

### D5: writes keep tokens in step with rows

`Upsert` writes the row with its `gav_key` and inserts the GAV's tokens with insert-ignore, in the same transaction, so a
replayed action changes nothing. `RemovePath` and `RemovePrefix` do not delete tokens. A stale token is harmless: every
search reads tokens through a row with the same `gav_key`, and a GAV without rows is not returned. Tokens of a GAV are a
pure function of its coordinates, so a GAV that comes back has the same tokens. A later compaction may remove tokens with no
row; it is not needed for correctness and is not part of this change.

The write path pays for the tokens whatever queries are run: the load measured in D10 is the cost of every installation,
including one that only searches with dotted tokens.

### D6: backfill is a periodic sweep, not a one-off flag

The backfill is keyset-paginated. A run starts on the existing 30-second scheduler tick of `SearchPlugin`
(`application/SearchPlugin.kt:200`), and only when the instance is not read-only. It reads batches of up to 1000 rows with
`gav_key IS NULL` in id order, each batch starting after the last id of the previous one, fills the keys and inserts the tokens
of a batch in one transaction, and continues until no unkeyed row is left. One run therefore drains the whole backlog. The
measured cost is about 5 minutes per million rows on SQLite and 18 minutes on PostgreSQL. The six-hour interval governs only the
re-checks after a drained backlog; a re-check on a database with no unkeyed rows is one index probe on `ix_search_gav_key`.

The sweep is a sweep, not a flag, because a rollback can leave unkeyed rows behind: the previous version writes rows without
a key. A roll-forward then finds them and fixes them.

`IndexStatus` gets `tokensComplete: Boolean`, computed per request as "no unkeyed row exists" (one indexed probe). While it is
false, `indexStateOf` returns `BUILDING` unless the state is already `DEGRADED`. The field defaults to `true` in the JSON
model, so an older client reads the same as before. The index stays in `BUILDING` for the length of one backfill run, which is the
time quoted in D6.

### D7: tests

- Unit (fast, no database): `SearchTokens.of` for dotted groupIds, hyphenated artifactIds, snapshot and classifier-free
  versions, Unicode letters, duplicates, the 64 character and 32 token caps; the parts of a token; whether a token has a word
  branch (no separator, one to four parts); whether a token is the last one of the query.
- Golden table (characterisation, captured against main before the query change and kept): queries of the dashboard and the API
  on a fixed fixture, in four groups: dotted-only and colon-only queries, the coordinates word with facets, plain-token queries
  with several tokens, and mixed queries. For dotted-only and colon-only queries the groups and their order must be identical. For
  the others, every group main returns must still be returned, in main's relative order, before any word-only group. This is the
  test that passes before the production change; its name says it is a characterisation test.
- Unit on SQLite and H2 file databases (`@TempDir`): `co foo` still matches a group whose groupId starts with `co` and whose
  artifactId starts with `foo`; `jackson databind` matches `com.fasterxml.jackson.core:jackson-databind` by words; `spring core`
  matches `org.springframework:spring-core` after the rows main returns; `frame core` does not match it; `org.framework core` does
  not match it by word, because the dotted token gets no word branch; `org.bench1` returns exactly the groups main returns, with no
  token predicate in its statement; a stale token of a removed GAV does not return it; a replayed upsert leaves one token row; a
  token with five parts returns exactly what main returns and no error.
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

- A token with a dot or a colon: as today. Prefix of the groupId and artifactId, equal version, or `group:artifact:version`.
- A token without a dot or a colon: as today, and also a word match. Its parts must each be a whole token of the same artifact, the
  last part of the last token of the query may be the start of a token.
- Tokens are ANDed, as today. Each token may match in its own way.
- Example: `jackson databind` finds `jackson-databind` by words, because `jackson` is a whole token and `databind` is the start of
  the last token. `frame core` does not find `spring-core`, because `frame` is not a token. `org.framework core` does not find
  `org.springframework:spring-core` by words, because `org.framework` is a dotted token.
- Quotes are ignored. A phrase search is not supported.
- `OR`, `NOT`, `AND` are plain words and must match like any other word.
- No wildcard characters. A `*` is ignored like punctuation.

### D9: ranking

No relevance ranking. Results keep the explicit sort keys and the fixed tie-breakers, and the main rows precede the word-only rows
(D2). A "best match first" sort needs a ranking function that exists in every dialect, which D3 showed does not exist, so it is a
question for a later change.

### D10: why the rule is per token, and what was rejected

**Context.** Two findings constrain the rule. First, the constraint of the repository: CLAUDE.md states that pointing an existing
installation at Ingot must change no configuration, and that a change that makes this untrue is a bug. A result that main returns
and that a change drops is such a change. Second, the cost measured on one million synthetic GAVs (50 queries per kind): a dotted
query is split into words, and the word `bench1` prefix-matches `bench10` to `bench199`, about 250 000 GAVs, against about 2 500
for the coordinate prefix `org.bench1`. An OR that lets such a word match widens the result by that amount.

| Measure | main | first draft (word and prefix on every word, OR-ed) | source |
|---|---|---|---|
| Prefix p95, SQLite | 2.20 s | 12.72 s (about 5.8 times) | benchmark |
| Prefix p95, PostgreSQL | not in the summary | above the 10 s statement timeout | benchmark |
| Load, SQLite | 264 s | 537 s | benchmark |
| Token table, SQLite | none | 7.76 million rows, 739 MB | benchmark |
| Token table, PostgreSQL | none | 2.04 GB | benchmark |
| Sweep of the backlog, SQLite and PostgreSQL | none | 295 s and 1 100 s (keyset pagination) | benchmark |
| Single word p50 / p95, SQLite | 0.85 s / 1.53 s | 0.96 s / 1.73 s (about 13 percent slower) | benchmark |

**Options.**

- A. The first version of this change, decided per query: a query with a dot or a colon anywhere keeps today's rule, and any other
  query is matched by word, exact for every word but the last. Rejected, and this is why the decision moved to the token. A
  query of plain tokens such as `co foo` is a word query, so its group-prefix and artifact-prefix matches are no longer tested:
  the word rule replaces main's prefix rule instead of adding to it. `co foo` loses the groups it matches today. A query with a
  single dotted token changes the rule for all its other tokens too. The variant breaks the drop-in rule of CLAUDE.md, and the
  maintainer's review found it.
- B. Per query, OR the word match of any token into the whole query. Rejected. One word widens every token, and the first draft's
  cost follows from it.
- C. Word matching for dotted tokens too. Rejected. `org.bench1` would prefix-match `bench1` tokens: the cost measured above.
- D. Exact-only words, no prefix. Rejected. The search box searches while the user types. The last token must stay a prefix, as
  it is today on groupId and artifactId. Without it, `cor` would find nothing, and type-ahead that works today would stop.
- E. Keep the first draft and raise the statement timeout. Rejected. It moves the cost into request latency and connection
  occupancy, the 250 000 candidate rows still reach the query, and SQLite stays 5.8 times slower.
- F. Per token, union of main and word (chosen). Each token keeps main's predicate, so no row main returns is lost, and results
  only grow. A token with a dot or a colon gets no word branch, so dotted searches cost what they cost today. A plain token gets
  a word branch, as a separate index-driven subquery, unioned with the main subquery. Tokens are ANDed as on main.

**Consequences.**

- A query whose tokens all contain a dot or a colon is exactly as on main. This is checked by the golden table (D7) and by the
  benchmark gate (D10).
- A plain token costs what a single word costs: about 13 percent more at p50 and p95 on SQLite (0.96 s and 1.73 s against 0.85 s and
  1.53 s). Accepted for the words that change behaviour.
- A mixed query such as `org.bench1 bench1` has a dotted token, which gets no branch, and a plain last token, which gets the prefix
  branch. Its cost is that of the plain token, and the benchmark records it as its own kind.
- The write path and the disk cost stay for every installation. Load is 537 s against 264 s, and the token table is 739 MB on SQLite
  and 2.04 GB on PostgreSQL per million GAVs. Only a change of the write path could remove them. That is not part of this change.
- A plain token before the last token matches only a whole token. `spring cor` finds `org.springframework:spring-core`, because
  `spring` is a token of its artifactId, and it still finds the groups the main rule finds for `spring`. `frame core` does not find
  it, because `frame` is not a token. Stated in the user documentation (task 6.1).

**Benchmark gate.** The gate compares the implementation branch with main as shipped, on the same hardware, with p95 of each query kind
measured in a fresh JVM. The implementation run used 200 000 GAVs. Its results:

| Kind | SQLite, main / branch (ratio) | PostgreSQL, main / branch (ratio) |
|---|---|---|
| Coordinate (dotted-only query) | 433 / 448 ms (1.04x) | 74.9 / 74.7 ms (1.00x) |
| One word (a single plain token) | 362 / 404 ms (1.12x) | 82.6 / 85.0 ms (1.03x) |

The gate is:

- coordinate queries: p95 of the branch at most 1.2 times the p95 of main, on both databases;
- one-word queries: p95 of the branch at most 1.5 times the p95 of main, on both databases.

These values replace the 10 percent margin of the earlier draft of this design, which was a proposal and not a measurement. The
thresholds are the values the maintainer set for the gate after these numbers. The gate does not yet cover the several-token and
mixed kinds: they were not in the reported run, so they stay recorded-only until they are measured. The PostgreSQL 10 second
statement timeout is still to be checked at one million GAVs, and the load, sweep and token table sizes come from the one-million
measurements in the table above, not from the 200 000 run.

If the gate fails, the code pull request does not open, and the maintainer decides.

### D11: implementation outcome

The implementation fixed three points that the design left open. Each is measured, and each has a rejected alternative.

- **Key width.** `gav_key` is a nullable `binary(16)`: the first 16 bytes of the SHA-256 of the lower-case coordinates joined with
  NUL. A 64-character hex key made each `search_artifact` row wider: 290 bytes per row became 363 bytes allocated at 200 000 GAVs.
  SQLite's default page cache is 2 MiB, so a query read about 25 percent more pages. The 16-byte key brings the row to 308 bytes,
  1.06 times main. Rejected: raising the SQLite cache and mmap pragmas. That would add up to 64 MiB of native memory per connection,
  and the change would reach every existing installation, which the drop-in rule forbids.
- **PostgreSQL word arm.** The word-only rows are a materialised set of word ids, `EXCEPT` the main-predicate rows. The `OR` form of
  the same predicate made the planner choose a sequential scan, which is why the set is materialised (D2). Rejected: the `OR` form.
- **Backfill.** Keyset-paginated, and one run drains the whole backlog: about 5 minutes per million rows on SQLite and 18 minutes on
  PostgreSQL in the earlier measurement. The six-hour interval governs only the re-checks (D6). Rejected: a one-off flag, because a
  rollback can leave unkeyed rows behind (D6).

## Risks

- **Query cost.** A plain token adds a union branch with one subquery per part, bounded by four parts per token and five tokens per
  query. The window of 10 000 entries and the 10 second statement timeout remain the bounds. The benchmark gate of D10 runs before
  the code pull request opens, and its numbers go in the pull request description; no test asserts a time.
- **Broader results.** A plain-token query can return groups it did not return before, after the rows main returns. A dotted-only
  query does not. The upgrade notes say it. The golden table pins that every main row is still returned.
- **Index size and load.** About seven token rows per GAV, and the load time of D10, are paid by every installation.

## Open questions

Open questions for the maintainer. Each has a default in the spec and tasks, so the change can proceed without an answer, but the
default is not final until the maintainer confirms it.

1. **Last token that is dotted.** The "last token" that gets the prefix branch is the last whitespace token, whatever its kind. So in
   `jackson org.x` the token `jackson` is matched by exact word only, because the last token is dotted and gets no word branch.
   Default: as written, to keep the rule of D2 simple. Alternative: the last token that has a word branch gets the prefix.
2. **Ordering under an explicit sort.** The main rows come first and the sort key orders each of the two parts. Whether a visitor who
   passes `sort` expects one merged order is not decided. Default: the split holds under every sort key.
3. **Tokens of more than four parts.** These have no word branch and match by main alone, with no error. Default: as written. Alternative:
   a longer word branch, which needs a bound on the number of subqueries decided by the maintainer.

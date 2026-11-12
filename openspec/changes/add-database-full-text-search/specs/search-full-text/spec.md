# Spec Delta

## Purpose

The `q` parameter of artifact search also matches words of a group's coordinates, on every database Ingot supports, with no extra
service. Nothing that the previous version returns is lost: every token keeps its previous predicate, and word matches are added
after the previous results. The index is upgraded in place, and an older version can still read the database.

## ADDED Requirements

### Requirement: Every previous match is kept

For every query, every group that the previous version returns SHALL still be returned, in the previous relative order, before any
group that matches only by word. Each whitespace token SHALL keep exactly the previous predicate: a plain token matches the groupId
prefix, the artifactId prefix or the exact version; a token with a colon is a coordinates token.

#### Scenario: Two plain tokens keep their prefix matches

- **WHEN** the query is `q=co foo` and the group `com.x:foo-lib` is indexed
- **THEN** the group is returned, because its groupId starts with `co` and its artifactId starts with `foo`, as on the previous version

#### Scenario: Dotted query is unchanged

- **WHEN** the query is `q=org.bench1` on an index of one million GAVs
- **THEN** the groups returned are the groups of the previous version, in the same order, and no group is returned only because
  one of its tokens starts with `bench1`

#### Scenario: Golden table holds

- **WHEN** the golden table captured on the previous version is run against this version on a fixed fixture
- **THEN** every dotted-only and colon-only query returns the same groups in the same order, and every other query returns every
  group of the previous version in the previous order, followed only by word-only groups

### Requirement: Plain tokens also match by word

A whitespace token without `.` or `:` SHALL also match a group by word, as a union with its previous predicate. Each part of the token,
where parts are the runs of letters and digits, SHALL equal a token of the group exactly. The last part of the last token of the query
MAY instead be the start of a token. The tokens of a query SHALL be ANDed, as before. A token with no parts, or with more than four
parts, SHALL NOT have a word match.

#### Scenario: Words match whole tokens

- **WHEN** the query is `q=jackson databind` and the group `com.fasterxml.jackson.core:jackson-databind` is indexed
- **THEN** the group is returned by word: `jackson` is a whole token and `databind` starts a token, although the groupId does not start
  with `jackson`

#### Scenario: Earlier token must be a whole token

- **WHEN** the query is `q=frame core` and the group `org.springframework:spring-core` is indexed
- **THEN** the group is not returned, because `frame` is not a token of it

#### Scenario: Every token must match

- **WHEN** the query is `q=spring kafka` and only `org.springframework:spring-core` is indexed
- **THEN** that group is not returned

#### Scenario: Word-only groups come after the previous results

- **WHEN** the query is `q=spring core` and both a group matched by the previous rule and a group matched only by word are indexed
- **THEN** the group matched by the previous rule is returned first

### Requirement: A token with a separator gets no word match

A token that contains `.` or `:` SHALL match only by its previous predicate.

#### Scenario: Dotted token is not matched by word

- **WHEN** the query is `q=org.framework core` and the group `org.springframework:spring-core` is indexed
- **THEN** the group is not returned, because `org.framework` is a dotted token with no word match and it is not a prefix of the groupId

### Requirement: Limits and errors are unchanged

The limit of five tokens, the limit of 200 characters, the 400 responses and the coordinates limits SHALL be those of the previous
version. This version SHALL NOT add an error response.

#### Scenario: Token with five parts

- **WHEN** the query is `q=a-b-c-d-e`
- **THEN** the response is the same as on the previous version, and it is not an error caused by this change

#### Scenario: Six tokens

- **WHEN** the query has six tokens
- **THEN** the response is the 400 of the previous version, naming `q`

### Requirement: Results only grow, and the same on every supported database

The word predicate SHALL be a union with the previous predicate, built from index-driven subqueries, and SHALL NOT be an SQL `OR` between
the previous predicate and a correlated `EXISTS`. It SHALL NOT use `LIKE`, a database full text index or a ranking function. The same
cases SHALL pass on SQLite, H2, MariaDB and PostgreSQL.

#### Scenario: Cross database contract

- **WHEN** the word cases and the golden table run against SQLite, H2, MariaDB and PostgreSQL
- **THEN** each case returns the same groups on every database

### Requirement: Benchmark gate before the code pull request

Before the code pull request opens, the benchmark gate SHALL be measured on this branch and on main as shipped, in the same hardware,
with the p95 of each query kind taken in a fresh JVM. The numbers SHALL go in the pull request description. No test SHALL assert a time.

#### Scenario: Gate passes

- **WHEN** the benchmark runs on 200 000 GAVs on SQLite and PostgreSQL
- **THEN** the coordinate queries (dotted-only) have a p95 at most 1.2 times main's p95, and the one-word queries have a p95 at most
  1.5 times main's p95, on both databases

#### Scenario: One million GAVs

- **WHEN** the benchmark runs on one million GAVs
- **THEN** no PostgreSQL statement exceeds the 10 second statement timeout, and the load time, the backfill time and the token table
  size are recorded next to the query numbers

#### Scenario: Gate fails

- **WHEN** the p95 of a coordinate query on this branch is more than 1.2 times main's, a one-word query is more than 1.5 times main's,
  or a PostgreSQL statement at one million GAVs exceeds 10 seconds
- **THEN** the code pull request does not open, and the maintainer decides how to proceed

### Requirement: Token index is additive and kept with the rows

The migration SHALL only add a nullable column, a table and indexes, and record the step with the marker `schema-2`. Every upsert
SHALL write the tokens of its GAV in the same transaction, so a replay leaves one set of tokens.

#### Scenario: Installation at the previous schema

- **WHEN** a database at `schema-1` is opened by this version
- **THEN** only additive statements run, and the existing rows are kept

#### Scenario: Replayed upsert

- **WHEN** the same upsert is applied twice
- **THEN** the token rows of its GAV are not duplicated

### Requirement: Backfill and status while rows lack a key

A keyset-paginated backfill SHALL fill the key and the tokens of rows that have none, and one run SHALL drain the whole backlog. A
re-check SHALL run at a six-hour interval. While any row lacks a key, the index state SHALL be `BUILDING` and `tokensComplete` SHALL be
false, unless the state is already `DEGRADED`.

#### Scenario: Sweep completes

- **WHEN** rows without a key exist and the sweep runs to its end
- **THEN** the search finds their groups by their words and `tokensComplete` is true

#### Scenario: Read-only instance

- **WHEN** the instance is read-only
- **THEN** the sweep does not run and the state stays as it is

### Requirement: Query syntax of version one

Quotes SHALL be ignored, and `OR`, `NOT` and `AND` SHALL be plain words. No relevance ranking SHALL be added, and the sort keys SHALL
stay as they are.

#### Scenario: Operator words are plain words

- **WHEN** the query is `q=core OR`
- **THEN** `or` must match like any other token, by its previous predicate or as a word

#### Scenario: Sort unchanged

- **WHEN** a query has no `sort` parameter
- **THEN** the groups are ordered by groupId as before, the word-only groups after the previous results

# Spec Delta

## Purpose

The `q` parameter of artifact search matches words of a group's coordinates, on every database Ingot supports, with no extra
service. A query that names a coordinate keeps its behaviour exactly. The index is upgraded in place, and an older version can
still read the database.

## ADDED Requirements

### Requirement: Coordinate queries keep their rules

A query whose `q` contains `.` or `:` SHALL match exactly the groups it matched on the previous version, in the same order. Its
query SHALL contain no predicate on the token table.

#### Scenario: Dotted query is unchanged

- **WHEN** the query is `q=org.bench1` on an index of one million GAVs
- **THEN** the groups returned are the groups of the previous version, in the same order, and no group is returned only because
  one of its tokens starts with `bench1`

#### Scenario: Coordinates word is unchanged

- **WHEN** the query is `q=org.springframework:spring-core`
- **THEN** the groups returned are the groups of the previous version, in the same order

#### Scenario: Golden table holds

- **WHEN** the golden table of coordinate queries captured on the previous version is run against this version on a fixed fixture
- **THEN** every query returns the same groups in the same order

### Requirement: Loose words match tokens of the coordinates

A query of two or more whitespace-separated words, without `.` or `:`, SHALL match a group only when every word matches it. Each word is split
into parts on characters that are neither letters nor digits. Every part SHALL equal a token of the group exactly, except the last part of the last
word, which SHALL be the start of a token. Tokens are the lower-cased letter and digit runs of the groupId, artifactId and version.

#### Scenario: Whole token matches

- **WHEN** the artifact `spring-core` is indexed and the query is `q=spring core`
- **THEN** the group `org.springframework:spring-core` is returned

#### Scenario: Last word matches by prefix

- **WHEN** the artifact `spring-core` is indexed and the query is `q=spring cor`
- **THEN** the group `org.springframework:spring-core` is returned

#### Scenario: Earlier word must be a whole token

- **WHEN** the artifact `spring-core` is indexed and the query is `q=frame core`
- **THEN** the group `org.springframework:spring-core` is not returned, because `frame` is not a token of it

#### Scenario: Every word must match

- **WHEN** the query is `q=spring kafka` and only `spring-core` is indexed
- **THEN** that group is not returned

### Requirement: Single word matches by today's rule and by token

A query of one whitespace-separated word, with or without a hyphen, and without `.` or `:`, SHALL return the groups that today's
rule matches, and the groups whose tokens match the word, split into parts as above with the last part as a prefix. The groups
of today's rule SHALL come first, each part in the existing order, and the groups that match only by token SHALL follow in the
existing order.

#### Scenario: Token of an artifactId matches

- **WHEN** the artifact `spring-core` is indexed and the query is `q=core`
- **THEN** the group `org.springframework:spring-core` is returned

#### Scenario: Rule rows come first

- **WHEN** the query is `q=jackson`, a group whose groupId starts with `jackson` exists, and a group matches only by a token `jackson`
- **THEN** the group whose groupId starts with `jackson` is returned before the group that matches only by token

#### Scenario: Hyphenated word keeps the prefix match

- **WHEN** the query is `q=spring-co` and an artifactId starts with `spring-co`
- **THEN** that group is returned, as it is today

### Requirement: Results only grow for coordinate and single-word queries

Every group that a coordinate query or a single-word query matched before this version SHALL still match the same query after the
upgrade. The contract suite SHALL hold a table of earlier queries and expected groups. The same requirement for several-word queries
is an open question of `design.md`, not a requirement of this version.

#### Scenario: Previous match survives for a single word

- **WHEN** a single-word query matched a group under the previous version and the same data is searched with this version
- **THEN** the group is still returned

### Requirement: Words without parts or with many parts match by today's rule

A word that has no letters or digits, or that has more than four parts, SHALL match by today's rule only, with no token predicate and
no error response. A query that is accepted today SHALL NOT be refused by this version.

#### Scenario: Five-part word

- **WHEN** the query is `q=a-b-c-d-e` (a single word with five parts)
- **THEN** the response is 200 with the groups of today's rule, and it is not 400

### Requirement: Same results on every supported database

The word matching SHALL use the same predicate on SQLite, H2, MariaDB and PostgreSQL. It SHALL NOT use `LIKE`, a database full text
index or a ranking function. The same cases SHALL pass on each database.

#### Scenario: Cross database contract

- **WHEN** the word cases and the golden table run against SQLite, H2, MariaDB and PostgreSQL
- **THEN** each case returns the same groups on every database

### Requirement: Benchmark gate before the code pull request

Before the code pull request opens, the benchmark gate SHALL be measured on this branch and on main in one run. The numbers SHALL go in
the pull request description. No test SHALL assert a time.

#### Scenario: Gate passes

- **WHEN** the benchmark runs on a synthetic dataset of one million GAVs, with 50 queries per kind (coordinate prefix, colon, single
  word, two-word word query) on SQLite and PostgreSQL, on the same hardware
- **THEN** the coordinate queries have a p95 no more than 10 percent above main's p95 on both databases, no PostgreSQL statement
  exceeds the 10 second statement timeout, and the single-word p50 and p95 are recorded against 0.85 s and 1.53 s

#### Scenario: Gate records the cost

- **WHEN** the benchmark runs
- **THEN** the load time, the sweep time and the token table size are recorded next to the query numbers

#### Scenario: Gate fails

- **WHEN** the p95 of a coordinate query on this branch is more than 10 percent above main's, or a PostgreSQL statement exceeds 10 seconds
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

A periodic sweep SHALL fill the key and the tokens of rows that have none. While any row lacks a key, the index state SHALL be
`BUILDING` and `tokensComplete` SHALL be false, unless the state is already `DEGRADED`.

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
- **THEN** the word `or` must match like any other word, and no group is returned for it unless a token or a prefix matches

#### Scenario: Sort unchanged

- **WHEN** a query has no `sort` parameter
- **THEN** the groups are ordered by groupId as before

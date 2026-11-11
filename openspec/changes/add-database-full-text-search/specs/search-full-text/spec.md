# Spec Delta

## Purpose

A plain word of the search query matches the words of a group's coordinates, on every database Ingot supports, with no extra
service. Matches that exist today stay. The index is upgraded in place, and an older version can still read the database.

## ADDED Requirements

### Requirement: Words match tokens of the coordinates

A plain word of `q` SHALL match a group when the word matches today's rule, or when every part of the word, split on characters
that are neither letters nor digits, starts a token of the group's groupId, artifactId or version. Tokens are lower-cased.

#### Scenario: Token of an artifactId matches

- **WHEN** the artifact `spring-core` is indexed and the query is `q=core`
- **THEN** the group `org.springframework:spring-core` is returned

#### Scenario: Every part must match

- **WHEN** the query is `q=spring%20kafka` and only `spring-core` is indexed
- **THEN** that group is not returned

#### Scenario: Existing prefix match still works

- **WHEN** the query is `q=org.spring` and a groupId starts with `org.spring`
- **THEN** that group is returned as before

### Requirement: Results only grow

Every group that a query matched before this version SHALL still match the same query after the upgrade. The contract suite SHALL
hold a table of earlier queries and expected groups.

#### Scenario: Previous match survives

- **WHEN** a query matched a group under the previous version and the same data is searched with this version
- **THEN** the group is still returned

### Requirement: Same results on every supported database

The word matching SHALL use the same predicate on SQLite, H2, MariaDB and PostgreSQL. It SHALL NOT use `LIKE`, a database full text
index or a ranking function. The same cases SHALL pass on each database.

#### Scenario: Cross database contract

- **WHEN** the word cases run against SQLite, H2, MariaDB and PostgreSQL
- **THEN** each case returns the same groups on every database

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

Quotes SHALL be ignored, `OR`, `NOT` and `AND` SHALL be plain words, and a word with more than four parts SHALL be refused with 400
that names `q`. No relevance ranking SHALL be added, and the sort keys SHALL stay as they are.

#### Scenario: Too many parts

- **WHEN** a word of `q` splits into five parts
- **THEN** the response is 400 and the message names `q`

#### Scenario: Sort unchanged

- **WHEN** a query has no `sort` parameter
- **THEN** the groups are ordered by groupId as before

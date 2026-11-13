# Spec Delta

## Purpose

An operator can answer artifact search from an index on the local disk of one Ingot instance, without any other service. The index is
a derived copy of the database, it is written by exactly one replica at a time, and a write is durable before its outbox entry is
removed.

## ADDED Requirements

### Requirement: Opt-in by installation and selection

The plugin SHALL register the factory `lucene` only when its jar is in the plugins directory. Search SHALL use the Lucene index only
when `searchEngine` is `lucene` as well. The plugin SHALL NOT be part of the image.

#### Scenario: Default installation

- **WHEN** no Lucene jar is installed and `searchEngine` is unset
- **THEN** the database serves search and nothing of this plugin runs

#### Scenario: Jar installed, selection off

- **WHEN** the jar is installed and `searchEngine` is `database`
- **THEN** the database serves search and the plugin does not open the index

### Requirement: Index location and configuration

The plugin SHALL keep its index in `INGOT_LUCENE_DIRECTORY` or, when that variable is unset, in `search/lucene` below the data
directory of the server. It SHALL write no file outside that directory. The minimum free space SHALL default to 1 GiB and be
configurable by `INGOT_LUCENE_MIN_FREE_BYTES`. Every variable SHALL accept the `REPOSILITE_LUCENE_` spelling as a fallback.

#### Scenario: Default location

- **WHEN** neither `INGOT_LUCENE_DIRECTORY` nor `REPOSILITE_LUCENE_DIRECTORY` is set
- **THEN** the index is created in `search/lucene` below the data directory

#### Scenario: Directory is a file

- **WHEN** the configured directory is a regular file
- **THEN** the state is `OPEN_FAILED` and the message names `INGOT_LUCENE_DIRECTORY`

### Requirement: One writer at a time

Only the replica that holds the lease `search.lucene.writer` SHALL write the index, drain the outbox or answer search. Every other
replica SHALL report the backend as down with the reason, SHALL NOT write, and SHALL answer search with `503`. The holder SHALL check
its lease at its epoch before every commit.

#### Scenario: Second replica

- **WHEN** a replica opens while another replica holds the lease
- **THEN** the second replica reports the reason, its outbox claims are not made, and it writes nothing

#### Scenario: Lost lease

- **WHEN** the holder's lease is taken over before its commit
- **THEN** the holder rolls its batch back, counts no attempt, and stops writing

### Requirement: Rebuild after a takeover

A replica that takes over the lease from another holder SHALL start a full rebuild before it serves search, because it does not know
which writes the previous holder acknowledged. A replica that keeps the lease SHALL serve at once.

#### Scenario: Takeover

- **WHEN** a replica acquires the lease at an epoch different from the epoch of its last commit
- **THEN** the state is `BUILDING` until the full rebuild completes, and the rebuild cursor starts at the first page

#### Scenario: Restart of the holder

- **WHEN** the holder restarts on its own volume and the lease epoch has not changed
- **THEN** the index serves search at once without a rebuild

### Requirement: Durable batches with at-least-once delivery

Each applied batch SHALL be committed before `apply` returns, and the outbox entries SHALL be removed only after that. A replay of a
batch SHALL leave the same documents. A commit that fails for lack of disk space or for a free-space floor breach SHALL release the
batch without consuming attempts.

#### Scenario: Crash after commit

- **WHEN** the process stops after a commit and before the outbox entries are removed
- **THEN** the entries are applied again after restart, and the number of documents equals the number of rows

#### Scenario: Visible at once

- **WHEN** `apply` returns
- **THEN** a search on the same instance finds the batch without any refresh delay

#### Scenario: Disk below the floor

- **WHEN** the free space of the index volume is below `INGOT_LUCENE_MIN_FREE_BYTES`
- **THEN** the batch is released with a retry delay, health is down with the reason, and downloads and uploads are not blocked by the plugin

### Requirement: Queries apply the caller's access value

Every query SHALL carry the caller's `SearchAccess` as a clause of the Lucene query. No row outside that value SHALL be returned by
search, facets or files. A precedence list SHALL be translated into the first-match rule.

#### Scenario: Private repository

- **WHEN** an anonymous caller searches and a `PRIVATE` repository holds the artifact
- **THEN** the artifact is not returned and the facets do not count its files

#### Scenario: Precedence rules translated

- **WHEN** the access value holds a precedence list with a denial and an allow
- **THEN** the query returns the same rows as the database engine for the same data

### Requirement: Same answers as the database engine

The plugin SHALL pass every contract case of the database engine in-process, with the same expected values, including the grouping,
the sort order and its ties, the capped total, the latest version, the repository presence with its origin, the facets with their
own-kind exclusion and the files of one version. The plugin SHALL have no named exception unless the reference page lists it with its
reason.

#### Scenario: Contract suite

- **WHEN** the contract suite runs against the Lucene backend on a temporary directory
- **THEN** every case passes with the same expected values as the database

### Requirement: Documentation of the single-writer rule

The reference page SHALL state that the backend serves exactly one replica, SHALL state what the operator sees with more than one replica
(search unavailable on the others, downloads unaffected), SHALL state that the index is a copy of the database and is rebuilt after a
takeover, and SHALL state the permissions the index directory needs.

#### Scenario: Operator reads the reference

- **WHEN** an operator opens the Lucene reference before configuring a backend
- **THEN** the single-writer rule and its consequences are stated before the variables are listed

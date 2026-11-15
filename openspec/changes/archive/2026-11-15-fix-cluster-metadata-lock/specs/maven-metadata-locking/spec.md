# Spec Delta

## Purpose

Two writers that update the same `maven-metadata.xml` never lose each other's entries, on one instance or on several
instances sharing one database. Readers and clients see the same responses as before whenever no write is in conflict.

## ADDED Requirements

### Requirement: Serialised metadata updates per artifact

The server SHALL hold one lock per repository and artifact directory for the whole read-modify-write of
`maven-metadata.xml` in the generation of a POM, the removal of a version, the pruning of snapshot entries and the
preserved builds listener. The lock SHALL be held until the updated metadata and its checksums are written or the
operation fails.

#### Scenario: Two deploys of different versions

- **WHEN** two deploys of different versions of the same artifact are processed at the same time
- **THEN** the artifact-level `maven-metadata.xml` lists both versions afterwards

#### Scenario: Different artifacts do not wait for each other

- **WHEN** a deploy to one artifact is in progress and a deploy to another artifact of the same repository starts
- **THEN** the second deploy does not wait for the first

### Requirement: Lease rows on a shared SQL database

When a shared SQL database is configured, the server MUST take the metadata lock as a lease row in the `ingot_lock`
table, with an owner and an expiry. The server MUST NOT use `GET_LOCK` or hold a `SELECT ... FOR UPDATE` across
storage I/O. An expired lease SHALL be taken over by the next writer.

#### Scenario: Holder crashes mid-update

- **WHEN** the instance holding a metadata lease stops before it releases the lease
- **THEN** another instance takes the lease after it expires and completes its own update

#### Scenario: Lease is lost before commit

- **WHEN** the lease of a write is taken over before the updated metadata is committed
- **THEN** the write is not committed and the server answers `503` with a `Retry-After` header

### Requirement: Retry on database conflicts

Lease acquisition and release SHALL be retried when the database reports a deadlock (MariaDB error 1213) or a
certification failure (SQLSTATE 40001 on Galera). The number of retries SHALL be bounded.

#### Scenario: Deadlock during acquisition

- **WHEN** lease acquisition fails with error 1213 once and then succeeds
- **THEN** the write proceeds and no error reaches the client

### Requirement: Bounded wait with a retryable answer

A write that cannot take the lock within the wait bound MUST NOT write any metadata. It MUST answer `503 Service
Unavailable` with a `Retry-After` header, in the error response shape the server already uses.

#### Scenario: Lock held for longer than the wait bound

- **WHEN** a metadata lease is held by another writer for longer than the wait bound
- **THEN** the waiting request answers `503` with `Retry-After`, and the metadata file is unchanged

### Requirement: Default single-process behaviour

When no shared SQL database is configured, the server SHALL serialise metadata updates with the in-process lock only and
SHALL NOT issue any statement against `ingot_lock`.

#### Scenario: SQLite default

- **WHEN** Ingot runs on SQLite with one instance and a deploy updates `maven-metadata.xml`
- **THEN** no statement is sent to `ingot_lock` and the response is the same as before this change

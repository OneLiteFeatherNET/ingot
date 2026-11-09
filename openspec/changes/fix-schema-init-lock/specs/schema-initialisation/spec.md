# Spec Delta

## Purpose

Replicas that start against one shared database create their tables one at a time, and a replica never serves requests
before its schema is ready. Embedded databases keep their current behaviour.

## ADDED Requirements

### Requirement: One initialiser at a time on a shared database

The server SHALL hold one database-wide schema lock for the whole schema initialisation on MariaDB, MySQL and PostgreSQL.
No other replica SHALL run schema creation while the lock is held.

#### Scenario: Two replicas start together

- **WHEN** two instances start against the same MariaDB or PostgreSQL database at the same time
- **THEN** only one instance creates tables at any moment, the other waits for the lock, and both finish with the same complete schema

### Requirement: Lock held until the schema is ready and released before serving

The server MUST hold the schema lock from the start of plugin initialisation until the post-initialise event has run. It
MUST release the lock before the HTTP server binds, and MUST release it when the start fails.

#### Scenario: Initialisation completes

- **WHEN** the schema initialisation finishes successfully
- **THEN** the lock is released before the HTTP server binds its port

#### Scenario: Initialisation fails

- **WHEN** a store fails while creating its tables
- **THEN** the lock is released, the start stops, and no port is bound

### Requirement: Lease expiry lets another replica take over

The lock on MariaDB and MySQL SHALL be a lease with an expiry. The holder SHALL renew the lease while it initialises. When
the holder stops renewing, another replica SHALL take over the lease after its expiry and complete the initialisation.

#### Scenario: Holder crashes mid-initialisation

- **WHEN** the replica that holds the lease stops without releasing it
- **THEN** another replica acquires the lease after it expires and completes the initialisation

#### Scenario: Slow but healthy holder

- **WHEN** the holder takes longer than the lease lifetime and keeps renewing the lease
- **THEN** no other replica takes over the lease

### Requirement: Bounded wait with a named failure

A replica that cannot acquire the schema lock SHALL wait no longer than `INGOT_LOCAL_SCHEMALOCKTIMEOUT` seconds, which
defaults to `300`. When the wait ends without the lock, the start SHALL fail with a message that names the schema lock,
and the replica SHALL NOT bind its port.

#### Scenario: Lock never becomes free

- **WHEN** another replica holds the lock for longer than the configured timeout
- **THEN** the replica stops its start with a message that names the schema lock and does not bind its port

### Requirement: Retry on deadlock and certification failure

The lease operations on MariaDB and MySQL MUST retry when the database reports error 1213, which Galera also uses for
certification failures. Retries SHALL stop when the timeout is reached.

#### Scenario: Certification failure while acquiring

- **WHEN** acquiring the lease fails with error 1213 and a later attempt succeeds
- **THEN** the replica acquires the lease and continues the initialisation without reporting an error

### Requirement: Embedded databases take no lock

On SQLite and H2 the server SHALL NOT create the `ingot_lock` table and SHALL NOT take a lock. The initialisation SHALL
behave as it does today.

#### Scenario: Single instance on SQLite

- **WHEN** the server starts on the default SQLite database
- **THEN** no `ingot_lock` table is created and the start behaves as before

### Requirement: Lock table creation tolerates a race

The server SHALL create the `ingot_lock` table with a statement that succeeds when another replica has just created it.

#### Scenario: Two replicas create the lock table

- **WHEN** two replicas on MariaDB create `ingot_lock` at the same moment
- **THEN** both replicas continue and the table exists once

### Requirement: Timeout configuration with the drop-in fallback

The setting `INGOT_LOCAL_SCHEMALOCKTIMEOUT` SHALL be read first, `REPOSILITE_LOCAL_SCHEMALOCKTIMEOUT` SHALL be read as a
fallback, and an unset value SHALL mean `300` seconds.

#### Scenario: Only the legacy variable is set

- **WHEN** only `REPOSILITE_LOCAL_SCHEMALOCKTIMEOUT=60` is set
- **THEN** the timeout is 60 seconds

#### Scenario: Both variables are set

- **WHEN** `INGOT_LOCAL_SCHEMALOCKTIMEOUT=90` and `REPOSILITE_LOCAL_SCHEMALOCKTIMEOUT=60` are set
- **THEN** the timeout is 90 seconds

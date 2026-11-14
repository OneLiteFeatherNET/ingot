# Spec Delta

## Purpose

An operator who runs several replicas of Ingot can tell from the documentation which shared database to use, and every
statement about a database names the evidence behind it. No default changes: SQLite stays the default, and an existing
installation keeps the database it has.

## ADDED Requirements

### Requirement: PostgreSQL is the recommended shared database

The documentation SHALL name PostgreSQL as the recommended database for several replicas and for production. The database
page, the high availability page, the Kubernetes guide and the chart values comments SHALL say the same. MariaDB SHALL be
described as supported and not recommended. MySQL and H2 SHALL stay described as experimental.

#### Scenario: Operator reads the database choice

- **WHEN** an operator reads the database page or the Kubernetes guide to choose a database for more than one replica
- **THEN** PostgreSQL is named as the recommended choice, MariaDB is named as supported and not recommended, and MySQL and H2 are named as experimental

### Requirement: Defaults are unchanged

The default database SHALL remain SQLite, the default pool size SHALL remain `1`, and the chart default SHALL remain no
database secret. No value in an existing configuration file, environment variable or manifest SHALL change meaning.

#### Scenario: Installation without a database setting

- **WHEN** Ingot starts with no database setting in the configuration, the environment or the chart values
- **THEN** it opens the SQLite file in the working directory, and `databaseThreadPool` is `1`

### Requirement: Every PostgreSQL claim names its evidence

A statement in the documentation or in a code comment about PostgreSQL SHALL name the test, the CI job or the check that backs
it, or SHALL say that it is not tested. The text SHALL NOT call PostgreSQL experimental, and it SHALL NOT say that no test
starts two replicas on PostgreSQL while such a test exists.

#### Scenario: Stale statement is corrected

- **WHEN** the change is complete
- **THEN** a search of the documentation, the chart comments and the code comments for PostgreSQL labelled experimental finds nothing, and every test named in a PostgreSQL claim exists in the repository

#### Scenario: High availability row for several replicas

- **WHEN** the high availability page describes PostgreSQL with several replicas
- **THEN** the row names each test or CI leg that starts more than one replica on PostgreSQL, and its state is "partly tested" until the PostgreSQL legs of the smoke test and the container rehearsal are on main

### Requirement: PgBouncer transaction mode is not supported

The documentation SHALL state that a connection pooler in transaction mode is not supported in front of Ingot's PostgreSQL
connection, because the schema lock is a session-level advisory lock with a session-level `lock_timeout`. It SHALL name
session mode as the pooler mode that works, and it SHALL give the reason in one sentence.

#### Scenario: Operator places PgBouncer in front of PostgreSQL

- **WHEN** an operator looks for pooler guidance for Ingot
- **THEN** the page names transaction mode as unsupported and session mode as the setting to use, with the advisory lock as the reason

### Requirement: Labels follow the evidence of the matrix

The "experimental" label SHALL be removed from PostgreSQL only after the PostgreSQL integration matrix has merged on main.
The label SHALL remain on MySQL and H2 until each has its own integration evidence.

#### Scenario: Label removal waits for the matrix

- **WHEN** the stale-claim section is prepared
- **THEN** its pull request depends on the merged `test-server-database-matrix` change, and the MySQL and H2 labels are unchanged

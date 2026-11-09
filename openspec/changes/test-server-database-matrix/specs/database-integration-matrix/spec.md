# Spec Delta

## Purpose

Every database-backed behaviour that Ingot's integration suite proves on MariaDB is also proved on PostgreSQL and on SQLite, so
that an operator can choose any of the supported databases without relying on the other two for evidence.

## ADDED Requirements

### Requirement: Feature areas run on every supported database

The integration suite SHALL run each database-backed feature area on SQLite, MariaDB and PostgreSQL. A feature area is one
abstract specification class with one concrete test class per database. Each concrete class SHALL use the same specification
and the same storage settings as its siblings, so that the database is the only axis that changes.

#### Scenario: Feature area with a MariaDB run

- **WHEN** a feature area has a concrete class that runs against MariaDB
- **THEN** the same feature area also has a concrete class that runs against PostgreSQL and one that runs against SQLite

#### Scenario: Same storage across databases

- **WHEN** the PostgreSQL class of a feature area is compared with its MariaDB class
- **THEN** both use S3 storage through the shared S3 test endpoint, and neither changes the specification it inherits

### Requirement: Each test owns its PostgreSQL schema

The PostgreSQL extension SHALL create a new schema for every test method before the test runs and SHALL drop that schema after
the test, so that no test reads data written by another test. The schema name SHALL be unique for each test. The PostgreSQL
server SHALL be shared by the whole test JVM.

#### Scenario: Two tests in one class

- **WHEN** two test methods of the same PostgreSQL class run in either order
- **THEN** each one starts with an empty schema and the results of the two methods are the same as when they run alone

#### Scenario: Test ends

- **WHEN** a test method of a PostgreSQL class finishes, whether it passes or fails
- **THEN** its schema no longer exists on the server

### Requirement: The matrix changes no production behaviour

The change SHALL NOT modify any file under `reposilite-backend/src/main`, any configuration key, environment variable, manifest or
documentation page. The support status of the databases in `DatabaseConnection` and in the gitbook SHALL stay as it is until
a later change removes the experimental label on evidence from this matrix.

#### Scenario: Support label

- **WHEN** the matrix change is merged
- **THEN** the comment that labels the PostgreSQL and MySQL branches as experimental is still present, and the gitbook page
  still lists the same support status

### Requirement: A failing database run blocks the change

A test that fails only on one database SHALL NOT be skipped, ignored or weakened in order to pass. The failure SHALL be
reproduced by that test and fixed in its own change, and the matrix change SHALL NOT merge while a test of the matrix is red.

#### Scenario: PostgreSQL-only failure

- **WHEN** a PostgreSQL class fails while its MariaDB and SQLite siblings pass
- **THEN** the test stays enabled, the failing test name is recorded for a follow-up `fix(database)` change, and the matrix change waits for that fix

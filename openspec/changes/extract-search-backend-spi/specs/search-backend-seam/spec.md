# Spec Delta

## Purpose

Artifact search reaches its index through one internal backend interface. The database backend is the only implementation
in this change, and the core keeps every part that does not depend on where the index lives. An operator sees no difference.

## ADDED Requirements

### Requirement: Core reaches the index through the backend interface

The outbox worker, the rebuild, the verify job, the startup rules and the REST layer SHALL read and write the index only
through the internal backend interface. No core class SHALL name a table or a row store of the database backend.

#### Scenario: Outbox applies through the interface

- **WHEN** the outbox worker drains a batch
- **THEN** the actions are applied through the backend interface and the outbox entries are completed afterwards

#### Scenario: Startup decision reads the backend count

- **WHEN** the startup rules decide whether a rebuild is due
- **THEN** the row count they use comes from the backend's count operation

### Requirement: Database backend is the default and the only backend

The registry SHALL resolve the local key `searchEngine` against the id `database` only. An unknown value SHALL keep today's
behaviour: search is switched off and a warning is logged at start.

#### Scenario: Default selection

- **WHEN** `searchEngine` is unset
- **THEN** the database backend serves search and the outbox

#### Scenario: Unknown value

- **WHEN** `searchEngine` is set to a value the registry does not know
- **THEN** search is switched off as before and the warning names the value

### Requirement: Behaviour and storage stay unchanged

The database backend SHALL keep the SQL, the table layout, the schema marker `schema-1` and the creation order of the
tables. The REST responses, status codes and headers SHALL be byte-for-byte the responses of the previous version for the
same data.

#### Scenario: Fresh installation

- **WHEN** a database without search tables is started with this version
- **THEN** the same tables, indexes and marker are created as the previous version creates

#### Scenario: Existing installation

- **WHEN** a database created by the previous version is started with this version
- **THEN** no schema statement runs and the existing rows are served unchanged

### Requirement: Rebuild order follows the transaction capability

The rebuild SHALL write a page's rows and its cursor in one transaction when the backend joins database transactions. When it
does not, the rebuild SHALL apply the page to the backend first and then commit the cursor.

#### Scenario: Transactional backend

- **WHEN** the backend joins database transactions and a page fails halfway
- **THEN** neither the rows nor the cursor of that page are committed

#### Scenario: Non-transactional backend

- **WHEN** the backend does not join database transactions and the cursor commit fails after the backend applied the page
- **THEN** the page is applied again on the next run without changing the result

### Requirement: Every backend passes one contract suite

Each backend SHALL pass the same abstract contract suite. The suite SHALL cover idempotent replay of every action, the
removal rules, paging stability, sampling with a cursor, and the access rule of search, facets and files.

#### Scenario: Replayed upsert

- **WHEN** the same upsert is applied twice
- **THEN** exactly one row exists afterwards

#### Scenario: Access rule holds for every read

- **WHEN** a caller with `SearchAccess` reads through search, facets or files
- **THEN** no returned row lies outside that access value

#### Scenario: Contract run for the database

- **WHEN** the suite runs against SQLite, H2, MariaDB and PostgreSQL
- **THEN** every case passes on each of them

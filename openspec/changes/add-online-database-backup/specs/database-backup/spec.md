# Spec Delta

## Purpose

An administrator can take a consistent copy of a running SQLite database without stopping the instance, and the copy is
a complete database file that can be restored by copying it back.

## ADDED Requirements

### Requirement: Consistent snapshot of a running SQLite database

The system SHALL write a snapshot of the SQLite database with `VACUUM INTO` while the instance keeps serving requests. The
snapshot SHALL be a standalone SQLite file that passes an integrity check and holds every row committed before the snapshot
started, and no partial write.

#### Scenario: Snapshot taken while the instance serves requests

- **WHEN** an administrator takes a snapshot while another request writes an artifact
- **THEN** the snapshot file opens as a SQLite database, passes the integrity check, and holds the rows committed before the
  snapshot started

#### Scenario: Snapshot file restored by copy-back

- **WHEN** the snapshot file is copied to the database file name in the working directory and the instance starts
- **THEN** the instance starts with the users, tokens and settings of the snapshot

### Requirement: Location and naming of snapshots

The system SHALL write each snapshot into a `backups` directory inside the working directory. The file name SHALL be
`ingot-snapshot-<yyyyMMdd-HHmmss>.db`, with the time in UTC taken from an injected `java.time.Clock`. The system MUST NOT
overwrite an existing file and SHALL refuse a snapshot whose file name is already taken.

#### Scenario: Name from the injected clock

- **WHEN** a snapshot is taken with a clock fixed at 2026-11-09T12:00:00Z
- **THEN** the file is named `ingot-snapshot-20261109-120000.db`

#### Scenario: Existing file is never replaced

- **WHEN** a snapshot would be written under a file name that already exists
- **THEN** the system refuses the snapshot, and the existing file keeps its content

### Requirement: Administrative endpoint

The system SHALL offer `POST /api/database/snapshots`, available only to principals with the maintenance area
(`ingot.admin.maintenance`). A successful request SHALL answer with the file name, the size in bytes and the creation time.
The answer MUST NOT contain an absolute path.

#### Scenario: Principal without the maintenance area

- **WHEN** a principal without the maintenance area calls `POST /api/database/snapshots`
- **THEN** the response is 403 and no file is written

#### Scenario: Snapshot created

- **WHEN** a principal with the maintenance area calls `POST /api/database/snapshots` on an SQLite instance
- **THEN** the response is 200, names the new file and its size, and the file exists in the `backups` directory

### Requirement: Command line snapshot

The system SHALL accept a `--snapshot-database` flag that writes one snapshot into the working directory and exits without
starting the web server. The flag SHALL exit with status 0 on success. On failure the flag MUST remove any partial file and
SHALL exit with a non-zero status.

#### Scenario: Flag writes a snapshot and exits

- **WHEN** the instance is started with `--snapshot-database` on an SQLite database
- **THEN** a snapshot is written to the `backups` directory, the process exits with status 0, and no web server is started

#### Scenario: Failed snapshot leaves no partial file

- **WHEN** writing the snapshot fails part way
- **THEN** the partial file is removed and the process exits with a non-zero status

### Requirement: SQLite only

The system MUST NOT write a snapshot for any database other than SQLite. For such a database the endpoint SHALL refuse
with a conflict answer that names the documented dump tool, and the flag SHALL exit with a non-zero status and the same
reason.

#### Scenario: MariaDB instance

- **WHEN** an administrator calls `POST /api/database/snapshots` on an instance that uses MariaDB
- **THEN** the response is 409, no file is written, and the answer names the dump tool in the backup page

### Requirement: Audit of snapshots

The system SHALL record every snapshot in the audit log with the acting principal and the file name. The audit record and
the answer MUST NOT contain the database connection string or the contents of the snapshot.

#### Scenario: Successful snapshot is audited

- **WHEN** a principal takes a snapshot through the endpoint
- **THEN** the audit log holds one record naming the principal and the snapshot file name

### Requirement: Defaults unchanged

Without a call to the endpoint or the flag, the system MUST NOT create the `backups` directory or any file in it. Every
existing endpoint, setting and database connection SHALL keep its behaviour.

#### Scenario: Instance started with the default settings

- **WHEN** the instance starts without the flag and no snapshot is requested
- **THEN** the working directory holds no `backups` directory and the database connection is opened as before

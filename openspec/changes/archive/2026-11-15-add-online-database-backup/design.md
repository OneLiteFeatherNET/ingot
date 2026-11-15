# Design

## Context

On main, `DatabaseConnection.createConnection` opens SQLite through a Hikari pool of one connection, with
`PRAGMA foreign_keys=ON` as the connection init statement, and runs transactions at serializable isolation. The working
directory holds `reposilite.db` (default name). `VACUUM INTO` is not allowed inside a transaction, so it cannot run through
an Exposed transaction. Admin routes already use `managerOnly("maintenance")`, as the cleanup and maintenance routes do.

## Decisions

### 1. VACUUM INTO, not a file copy

A copy of the file races with writers. `VACUUM INTO` writes a new database with the same content, in one read transaction,
and the result is valid on its own. The service takes a raw JDBC connection from the Hikari pool with auto-commit on and
sets a busy timeout, so a concurrent writer waits instead of failing at once. Hikari hands out the single connection, so
the snapshot serialises with other database work, which is the behaviour the pool already has.

### 2. SQLite only

Other databases have their own dump tools, and the backup page already documents them. Emulating a dump for MariaDB or
PostgreSQL would add a second, untested format. The service refuses for them, and the refusal names the documented tool.

### 3. Fixed location, no new setting

Snapshots go to `backups/` inside the working directory, named `ingot-snapshot-<UTC timestamp>.db` from the injected
`java.time.Clock`. Operators gain no new environment variable and no path that could point outside the volume. The volume
is `/app/data` in the image, so the snapshot survives a container restart. A name that already exists is refused, so a
snapshot can never replace another.

### 4. One service behind two entry points

The admin route and the command line flag both call the same service. The flag starts no server and returns after the file
is written. On failure the partial file is removed, so no half-written snapshot stays in the directory.

### 5. Secrets stay where they were

A snapshot holds the same secrets as the database: S3 keys and the session signing key. This change does not encrypt it.
It creates the file with owner-only permissions where the file system supports them, and the backup page warns the operator
to store it as carefully as the database. The API answers with the file name only, so the response and the audit record do
not reveal the path.

## Risks

- `VACUUM INTO` on a large database takes time and reads the whole file. The endpoint returns after the snapshot is
  complete, so the caller waits. The proposal does not add a background job.
- A snapshot taken while the instance writes is consistent but may miss the writes that come after it. The docs say so.

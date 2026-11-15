# Proposal

## Why

An operator who runs Ingot on the default SQLite database has one backup path today: stop the container, copy
`reposilite.db`, start it again. `docs/gitbook/how-to/back-up-and-restore.md` says so, and that stop is why a backup costs
downtime. A copy taken while the instance runs is unsafe, because SQLite keeps writing to the file and a plain copy can
catch it half-written.

The database holds more than metadata. It has the users, the tokens, the shared settings, the S3 keys and the session
signing key. Operators who want a backup every night without downtime have to script a stop, or they skip the backup.

Nothing checks that a backup can be restored. The repository has no restore test, and the only restore path is the manual
copy-back described in the docs.

This change covers the first part: a consistent snapshot of a running SQLite instance, on demand, from the API or the
command line. The restore drill and the Kubernetes hooks are separate follow-up changes.

## What Changes

- **B1 Online snapshot.** A new service writes a consistent copy of the SQLite database with `VACUUM INTO` while the
  instance keeps serving requests. The copy is a standalone SQLite file. For every other database the service writes
  nothing and refuses, and the refusal names `mariadb-dump` as the documented route.
- **B2 Admin endpoint.** `POST /api/database/snapshots` for principals with the maintenance area
  (`ingot.admin.maintenance`). It writes a snapshot into `backups/` inside the working directory and answers with the file
  name, the size and the creation time. It never answers with an absolute path.
- **B3 Command line.** `--snapshot-database` writes one snapshot into the working directory and exits without starting the
  server. It exits with status 0 on success and with a non-zero status on failure, and it removes a partial file on failure.
- **B4 Audit and safety.** Each snapshot is recorded in the audit log with the actor and the file name. A snapshot never
  overwrites a file.
- **Documentation.** `docs/gitbook/how-to/back-up-and-restore.md` gets the online snapshot for SQLite, the warning that the
  file holds secrets, and the restore by copy-back. `docs/gitbook/reference/http-api.md` gets the endpoint.

### Out of scope

- The restore drill: a CI job that backs up, restores and asserts bytes, tokens and search. It is the follow-up change
  `add-backup-restore-drill` (`ci(backup)`).
- The `--verify-storage` read-only report. The roadmap folds it into the drill.
- Velero backup hooks and the `backup.velero.enabled` chart value. They are the follow-up change `add-velero-backup-hooks`
  (`feat(chart)`).
- Online backup for MariaDB, MySQL, PostgreSQL, H2 and custom JDBC URLs. Their documented route stays the dump tools in the
  backup page (`mariadb-dump` for MariaDB).
- A restore command. Restore stays the documented copy-back.
- Retention, scheduling, encryption of snapshots and a dashboard button. Operators remove old snapshots themselves.

## Capabilities

### New Capabilities

- `database-backup`: online snapshots of the SQLite database, where they are written, who may take one, and how the command
  line and the API report the result.

## Impact

- **Type and scope.** The change ships under `feat(backup)`. The pull request title, squash-merged onto main, is:

  `feat(backup): add online sqlite database snapshots`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. Pointing an existing Reposilite installation at Ingot behaves as before. Nothing is written until an administrator
  calls the endpoint or passes the flag. The endpoint and the flag are additive, and the title carries no `!`.
- **Dependencies.** The roadmap lists `add-read-only-mode` (N12) as a dependency. The snapshot is consistent without it,
  because `VACUUM INTO` reads one transaction. N12 is useful for a cold copy during a restore, and the restore section of
  the docs will refer to it once it exists. No other change is needed.
- **Code.** `reposilite-backend` (a new `net.onelitefeather.ingot.backup` package, the admin route, the command line
  parameters and the launcher) and `docs/gitbook/`. Package names of the existing code stay as they are.

## Verification

- Failing tests first. Targeted tests run through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`. The endpoint
  test runs as `:reposilite-backend:integration --tests '<pattern>'`. No open verification from the roadmap applies to
  this item.
- `openspec validate add-online-database-backup --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-online-database-backup` prints nothing.

# Proposal

## Why

Two deploys of the same artifact that land close together, on one instance or on two instances behind the same
address, can lose a version. Each request reads `maven-metadata.xml`, adds or removes its own entry, and writes the
whole file back. The request that writes last decides what the file says. The other version is still in the
repository, but it is gone from the listing, from `latest` and `release`, and a Maven client that resolves the
version range never sees it.

The read-modify-write lives in `MetadataService` (the metadata update after a POM is generated, the removal of a
version whose files are gone, and the pruning of snapshot entries) and in `PreservedBuildsListener`, which deletes old
snapshot files based on a timestamp it reads from the same metadata. None of these paths takes a lock on `main`. The
only lock in the storage layer is a read-write lock per location in `FileSystemStorageProvider`, which covers the commit
of one file in one process. Its own comment says it is not respected across processes
(`FileSystemStorageProvider.kt:154`). So the lost update already happens inside one process, and it happens on every
storage backend, S3 included.

## What Changes

- A `MetadataLocks` port takes one lock per repository and artifact directory around the whole read-modify-write of
  `maven-metadata.xml`. `MetadataService` and `PreservedBuildsListener` use it.
- Inside one process the lock is a JVM lock. When a shared SQL database is configured (MariaDB, MySQL or PostgreSQL),
  the same lock is also a lease row in `ingot_lock`, with an expiry, takeover after a crashed holder, and retries on
  deadlock (1213) and Galera certification failure. It does not use `GET_LOCK` (not supported by Galera) and it does not
  hold a `SELECT ... FOR UPDATE` across storage I/O.
- A write that cannot take the lock within the wait bound answers `503` with `Retry-After` and writes nothing. The client
  retries.
- The lease timings (60 seconds lease, 10 seconds wait) are fixed in this change. No new configuration key is added.
- The HA design document (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, section 1.2) proposed
  `SELECT ... FOR UPDATE`. It is amended to the lease row, as decided in `design.md`.

### Out of scope

- Client uploads of `maven-metadata.xml` itself (`saveMetadata`). A client that uploads its own copy stays last writer
  wins, as today. Only the server-side updates above are serialised.
- Job leases for cleanup and audit (`fix-job-leases`), the schema lock (`fix-schema-init-lock`, which provides the
  `ingot_lock` table and the lease primitive), and atomic file writes (`fix-atomic-filesystem-upload`).
- Galera routing. The HA design recommends routing writes to one node. This change only makes concurrent writes
  correct, and documents what Galera does with them.
- Read paths. Readers still see the last committed file.

## Capabilities

### New Capabilities

- `maven-metadata-locking`: which metadata updates are serialised, how the lock is taken on a shared database, and what
  a client sees when the lock cannot be taken.

## Impact

- **Type and scope.** The change ships under `fix(maven)`. The pull request title, squash-merged onto main, is:

  `fix(maven): serialise metadata updates across instances`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has
  to change. A Reposilite installation pointed at Ingot keeps working for every Maven and Gradle client. The title
  carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes.** On SQLite and H2 (the default) the only change is that metadata updates of one artifact
  run one after another. On MariaDB, MySQL and PostgreSQL each metadata update adds a lease acquire and a release, two
  small statements against `ingot_lock`. A write that waits longer than 10 seconds gets `503` with `Retry-After`
  instead of racing silently.
- **Dependencies.** `fix-schema-init-lock` (F3 in the roadmap) must be merged first. It creates `ingot_lock` and the
  lease primitive that this change reuses. Neither exists on `main` yet. The lease code is shared with `fix-job-leases`
  and lives in `net.onelitefeather.ingot`, so there is one implementation.
- **Docs.** `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` (section 1.2) and
  `docs/gitbook/how-to/configure-database.md`.
- **Code.** `reposilite-backend` (maven package, the lease store under the database package).

## Verification

- A race test reproduces the lost version before any production code changes. If it does not reproduce on `main`, this
  change is revised before it is implemented. The SQL lease part depends on Galera behaviour (roadmap open verification
  2): the retry rules are checked against MariaDB Galera on staging before the pull request is merged.
- `openspec validate fix-cluster-metadata-lock --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-cluster-metadata-lock` prints nothing.

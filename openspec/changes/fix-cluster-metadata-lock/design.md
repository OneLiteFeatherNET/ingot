# Design

## Context

The HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, section 1.2) proposed a lock row taken
with `SELECT ... FOR UPDATE` for the whole read-modify-write. The roadmap review found that this does not hold on the
database Ingot runs against in staging. MariaDB Galera certifies a `FOR UPDATE` only at commit, so the row lock does
not keep a second writer out while the metadata is read and written. `GET_LOCK` is listed as unsupported on Galera.
This change takes the lock another way.

## Decisions

### Lease row, not `GET_LOCK` and not `FOR UPDATE`

The lock is a row in `ingot_lock` (`name`, `owner`, `expires_at`). A writer takes it with a conditional update, and
inserts the row when it does not exist. The row is not held inside a transaction, so no transaction stays open across
storage I/O. If a pod is killed, nothing renews its lease, so the lease expires and the next writer takes over. The retry rules
(1213 and SQLSTATE 40001) are the price of Galera's optimistic certification.

### Key: repository and artifact directory

The key covers the artifact-level `maven-metadata.xml`, the version-level metadata of the `-SNAPSHOT` directories under
it, and the version directories the removal touches. Deploys of different artifacts never wait for each other. Deploys
of one artifact do, which is the contention this change is about.

### Lease held across I/O, ownership checked before commit

The critical section is a few small file operations, so the lease is not renewed by a background thread. Instead, the
writer checks that it still owns the lease before it commits the metadata. If the lease was taken over, the write is
dropped and the request answers `503`. A renewal thread would add timing logic that the F.I.R.S.T. rules make hard to
test without sleeps. If metadata writes ever grow long (for example, large S3 multipart uploads inside the critical
section), a renewal is the next step and is a separate change.

### In-process lock stays

Each process keeps its JVM lock as the fast path. The SQL lease is added on top only when a shared database is
configured. On SQLite and H2 (single writer) the JVM lock is enough, so the default issues no database statement.

### Fixed timings, no new setting

The lease lasts 60 seconds and a writer waits up to 10 seconds. These are constants. A setting would widen the contract
for operators, and nobody has asked to tune them yet. They become configuration only with a real need.

## Alternatives considered

- `GET_LOCK` (as in the HA design, section 1.1 for schema setup). Rejected: unsupported on Galera, and a session lock
  dies with a dropped connection in a way the pool does not report.
- `SELECT ... FOR UPDATE` in a transaction around the whole update. Rejected: Galera certifies it at commit, and the
  transaction would stay open across storage I/O.
- Redis or Valkey lock. Rejected by the HA design: no fencing token, and Redis is not part of the default install.
- Serialising only the storage layer. Rejected: the storage layer does not know which writes belong together, and it
  does not cover S3.

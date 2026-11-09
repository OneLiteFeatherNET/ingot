# Design

## Context

Schema creation happens in the `init` blocks of stores. Those stores are built during plugin initialisation
(`ReposiliteFactory.createReposilite`, `PluginLoader.initialize`) and while the `ReposiliteInitializeEvent` runs in
`Reposilite.launch`. The HTTP server binds only after `ReposilitePostInitializeEvent` in `launch`. So a lock that
"covers initialisation" has to start in one method and end in another.

The database pool is sized by `databaseThreadPool`, which defaults to `1` (`LocalConfiguration.kt:118`), and that
size also applies to MariaDB and PostgreSQL. Any design that holds a pooled connection for the whole lock and then
asks the pool for another one deadlocks at the default size.

## Decision 1: one lock primitive per vendor

- MariaDB and MySQL use a lease row in `ingot_lock(name, owner, expires_at)`. The row is acquired by an insert, or by a
  conditional update when the existing lease has expired. Each attempt is a short transaction on a pooled connection,
  so the lock holds no connection between attempts.
- PostgreSQL uses `pg_advisory_lock` on a connection that stays checked out for the initialisation.
- SQLite and H2 use a no-op, as the design document already says.

`GET_LOCK()` is not used. The HA design (section 1.1) proposed it, but Galera's known limitations list it as
unsupported, and staging runs Galera. `SELECT ... FOR UPDATE` is not used either, because Galera only certifies it at
commit, so a conflict surfaces too late to retry cleanly. The HA design and the stage-1 plan are amended to match.

## Decision 2: the lock spans two methods

The lock is acquired at the top of `ReposiliteFactory.createReposilite`, before `PluginLoader` loads and initialises
plugins. It is released at the end of `Reposilite.launch`, after `ReposilitePostInitializeEvent` and before
`webServer.start`. The handle is stored on the `Reposilite` instance. `Reposilite.shutdown()` releases it too, and
release is idempotent, so a start that fails in either method cannot leak it.

Rejected alternative: move all plugin initialisation into `launch`. That would change the order in which the public
factory API behaves, and the drop-in plugin contract depends on that order.

## Decision 3: the connection budget

The lease path needs one pooled connection at a time, so it works with the default pool of one.

The PostgreSQL path holds a connection. The open question is whether the initialisation DDL can run on that same
connection. If it can, the pool of one is enough. If it cannot, the pool needs one extra connection for the duration of
the initialisation only. The first spike task decides which holds and the implementation follows it. The lock must
never ask for a connection that the lock itself is holding.

## Decision 4: lease lifetime and renewal

A lease has a time-to-live of 60 seconds. While the holder initialises, it renews the lease every 10 seconds on the
existing scheduler, reading time from an injected `java.time.Clock`. A holder that dies stops renewing, so another
replica takes over at most 60 seconds later. A healthy but slow migration is not taken over, because it keeps renewing.

The timeout `INGOT_LOCAL_SCHEMALOCKTIMEOUT` (default 300 seconds) only bounds how long a replica waits for the lock.
It is not the lease lifetime.

## Decision 5: the lock cannot protect its own table

`ingot_lock` is created before the lock is first taken. Two replicas may create it at the same moment. The creation
therefore uses `CREATE TABLE IF NOT EXISTS`, and a duplicate-object error on that statement counts as success. The
rest of the schema is still created under the lock.

## Risks

- Galera single-writer routing is needed for the lease to be reliable across nodes. The lease is a row, so a
  certification failure is retried, and error 1213 is the code to retry on. The staging check confirms this before
  release.
- DDL under total order isolation still stalls the cluster while it runs. Serialising it removes the concurrent
  case, but does not make a single migration cheap.
- Testcontainers runs one MariaDB node, so the Galera-specific retry path is verified on staging and not in CI.

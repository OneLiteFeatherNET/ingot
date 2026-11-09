# Proposal

## Why

Ingot is meant to run as several replicas against one shared database: a rolling update, or a first install with
`replicas: 3`, starts several pods at the same moment. Every pod that boots creates or alters its tables before it
serves a request. Fourteen stores do this from their initialisation code, and each one issues `CREATE TABLE IF NOT
EXISTS` or `ALTER TABLE` statements against tables the other pods are touching at the same time. Nothing serialises
them.

The effect shows up at start-up. On MariaDB Galera, DDL runs under total order isolation, so concurrent DDL stalls the
cluster while it runs. On PostgreSQL two sessions that create the same table or type can collide, and one of them fails.
A pod that loses that race does not start, restarts, and the rollout looks unhealthy for no reason an operator can see
in the configuration.

The HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, section 1.1) names the fix: one advisory lock
around the whole initialisation. Nothing implements it yet. A single instance on SQLite never hits the problem, which is
why the gap went unnoticed.

## What Changes

- **Schema lock.** Before the first store creates a table, the instance takes one lock for the database. On MariaDB and
  MySQL the lock is a lease row in a new table `ingot_lock`, taken with retry on deadlock (error 1213) and on Galera
  certification failure. On PostgreSQL it is `pg_advisory_lock`. On SQLite and H2 it is a no-op, because those already
  have a single writer.
- **Release before serving.** The lock is held from the start of plugin initialisation until the post-initialise event
  has run, and is released before the HTTP server binds. Every failure path releases it as well.
- **Crash safety.** A lease has an expiry. A healthy holder renews it while it initialises, so a slow migration is not
  taken over. A holder that dies loses the lease, and another replica takes over after the expiry.
- **Timeout.** A replica that cannot take the lock within `INGOT_LOCAL_SCHEMALOCKTIMEOUT` (seconds, default `300`,
  `REPOSILITE_LOCAL_SCHEMALOCKTIMEOUT` as fallback) stops its start with a message that names the lock. It does not bind.
- **Table creation.** The `ingot_lock` table itself is created with a tolerant `CREATE TABLE IF NOT EXISTS`, because the
  lock cannot protect its own creation.
- **Docs.** The environment variable, the start-up behaviour and the amended lock wording of the HA design.

The fix is conditional on the first task. If the failing integration test passes on unmodified `main` for both MariaDB
and PostgreSQL, the change is narrowed to what that test shows and this proposal is revised before the code pull request
opens. Galera's handling of the lease rows is confirmed on staging before release, because Testcontainers runs a single
MariaDB node.

### Out of scope

- Serialising metadata writes (`maven-metadata.xml`, `PreservedBuildsListener`). That is `fix-cluster-metadata-lock`.
- Leases for cleanup, audit and job claims. That is `fix-job-leases`. This change only provides the lease primitive they reuse.
- A pre-upgrade migration Job or an external schema mode. Those are strategic items.
- Choosing a database for new installs, changing the default database, or adding Flyway or Liquibase.
- Any change to the Helm chart.

## Capabilities

### New Capabilities

- `schema-initialisation`: the lock held during start-up, its release, the timeout, and the no-op on embedded databases.

## Impact

- **Type and scope.** The change ships under `fix(database)`. The pull request title, squash-merged onto main, is:

  `fix(database): serialise schema initialisation across replicas`

- **Breaking marker.** No breaking change. No existing setting, environment variable, volume, manifest or plugin has to
  change. The new variable is optional and has a default. A single instance on SQLite behaves as before. On MariaDB or
  PostgreSQL the `ingot_lock` table is added on the first start, which is additive.
- **New failure mode.** Under contention a replica waits for the others. If it waits longer than the timeout, its start
  fails with a clear message. That only happens when several replicas start together against a stuck or very slow
  initialisation, and it is listed in the upgrade notes.
- **Dependencies on other changes.** None. This change provides the lease primitive that `fix-cluster-metadata-lock`
  and `fix-job-leases` depend on, so those two come after it.
- **Code.** `reposilite-backend` (the database package, `ReposiliteFactory`, `Reposilite`, `LocalConfiguration`, and the
  stores that create tables only through the shared initialiser) and `docs/gitbook/`, plus the two HA design documents.
  The new lock code goes under `net.onelitefeather.ingot`, and existing packages keep `com.reposilite.*`.

## Verification

- The first task is a failing integration test that runs concurrent initialisations against MariaDB and PostgreSQL. Every
  later task is written against it.
- Targeted tests per commit, run through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`.
- `openspec validate fix-schema-init-lock --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-schema-init-lock` prints nothing.

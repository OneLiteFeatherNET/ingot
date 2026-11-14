# Design

## Context

The support statement for PostgreSQL is currently split between three sources that disagree: the gitbook pages, the code
comments that end up in `configuration.cdn`, and the test suite. Each source was right when it was written, and then
`test-server-database-matrix` and the existing two-replica integration tests moved the evidence. This design gives one
statement, and it names the test or CI job that backs each part of it.

Verified on origin/main (`189012696`):

- Integration tests reach PostgreSQL through `PostgresSpecificationJunitExtension`, which `test-server-database-matrix`
  added (#121), and through the `Remote.POSTGRESQL` enum of
  `ConcurrentSchemaInitialisationIntegrationTest` (every replica that starts together completes its start) and
  `DatabaseClusterTransportIntegrationTest`. `TwoInstanceClusterIntegrationTest` starts two instances on one PostgreSQL schema
  with `startPair(postgres = true)`.
- `SchemaLock.kt:74-80` chooses the schema lock by vendor. MariaDB and MySQL take a lease row in `ingot_lock`. PostgreSQL
  takes `pg_advisory_lock` in `PostgresAdvisorySchemaLock`, on a connection of its own opened with `DriverManager`, outside
  the Hikari pool.
- `MetadataLocks.kt:74` treats PostgreSQL like MariaDB and MySQL for the metadata locks. Those locks are lease rows, so
  `ingot_lock` exists on PostgreSQL too. The upgrade guide says it does not.
- No test covers `PostgresAdvisorySchemaLock` directly. The concurrent start test is its only coverage.
- `LocalConfiguration.kt:124` sets `databaseThreadPool` to 1. The keys `INGOT_LOCAL_DATABASETHREADPOOL` and
  `REPOSILITE_LOCAL_DATABASETHREADPOOL` already exist. `DatabaseConnection.kt` passes the value to `maximumPoolSize`, and no
  test asserts that mapping.
- Hikari keeps its own defaults for lifetime (1800 seconds), connection timeout (30 seconds) and keepalive (off). None of them
  is configurable in Ingot today.
- The `ha-smoke` job (`.github/workflows/ci.yml`, driver `charts/ingot/tests/ha_smoke.py`) installs `ha/values.yaml` against
  the MariaDB manifest `charts/ingot/tests/ha/dependencies.yaml`. Its images are pinned by tag and digest.
- The container suite (`reposilite-backend/src/containerTest/`) starts `mariadb:11.4` in four places and has no PostgreSQL
  engine. `UpgradeScenario` takes `DatabaseEngine { SQLITE, MARIADB }`.
- `SharedContainers.kt` starts `mariadb:latest` and `postgres:latest`, which are not pinned.

## Decisions

### 1. One statement, backed by names

Every sentence about PostgreSQL in the docs names the test, CI job or check that backs it, or it says "not tested". The
high availability page keeps its rule that each row names its evidence. After section 2 the PostgreSQL rows read:

- one instance on PostgreSQL: the PostgreSQL integration run and the Coverage job (already true);
- several replicas on PostgreSQL: the concurrent start and the two-instance cluster tests in the Coverage job, plus the
  `ha-smoke` PostgreSQL leg (section 3) and the container rehearsal (section 4) once they merge. Until then the row says
  "partly tested" and names only the tests that exist.

The label "experimental" stays on MySQL and H2 only, because those have no evidence of the same kind. It is removed from
PostgreSQL in section 2, after `test-server-database-matrix` has merged, since that change is what makes the claim checkable.

### 2. Recommendation, not a default change

PostgreSQL is named "the recommended database for several replicas and for production" in the database page, the high
availability page, the Kubernetes guide and the chart comments. Nothing else changes. SQLite stays the default, so an
installation that is pointed at Ingot without a database setting still starts on SQLite. MariaDB is "supported, not
recommended". The reason is in the docs: PostgreSQL has the broader evidence in the suite, and PostgreSQL's schema lock
uses session state that the operator has to keep (see decision 4), so the recommendation carries a condition that the
MariaDB page does not need.

### 3. Pool: keys added, size key unchanged

`databaseThreadPool` is already the pool-size key with both environment names. This change adds no second size key, because
two keys for one value would break the rule that a configuration value has one place. It adds three keys for the Hikari
settings that the operator needs to keep a PostgreSQL connection healthy behind a load balancer or a firewall:

| Key | Environment | Default | Meaning |
| --- | --- | --- | --- |
| `databaseMaxLifetime` | `INGOT_LOCAL_DATABASEMAXLIFETIME` (fallback `REPOSILITE_LOCAL_`) | `1800` seconds | Hikari `maxLifetime`, the age after which a connection is replaced. |
| `databaseConnectionTimeout` | `INGOT_LOCAL_DATABASECONNECTIONTIMEOUT` (fallback `REPOSILITE_LOCAL_`) | `30` seconds | Hikari `connectionTimeout`, how long a request waits for a free connection. |
| `databaseKeepalive` | `INGOT_LOCAL_DATABASEKEEPALIVE` (fallback `REPOSILITE_LOCAL_`) | `0`, off | Hikari `keepaliveTime`, a ping for idle connections. |

The defaults are the Hikari defaults that the pool uses today, so no installation changes. The keys apply to MariaDB,
MySQL and PostgreSQL. SQLite and H2 keep one connection and ignore them. The pool budget is written down in the database
page: a replica needs `databaseThreadPool` connections, plus one more while it starts for the PostgreSQL schema lock, so the
server's connection limit has to be at least the replica count times that number.

### 4. PgBouncer: session mode only

`PostgresAdvisorySchemaLock` takes a session-level advisory lock and sets `lock_timeout` on the same session. A pooler in
transaction mode hands the server connection to another client after each transaction, so the lock and the timeout do not
stay with the replica that took them. The docs say so and name session mode as the setting that works. The change does not
attempt to make transaction mode work.

### 5. Test evidence for PostgreSQL

- The `ha-smoke` job gets a second leg. The PostgreSQL leg reuses `ha/values.yaml` and swaps the dependency manifest and the
  connection secret. The driver takes the database as an argument and has no branch of its own per check.
- The upgrade rehearsal and the cluster bus test get a PostgreSQL engine. The PostgreSQL upgrade test is run only if the
  upstream image accepts a `postgresql` connection string (open question 4).
- The settings secret references run through `PostgresSpecificationJunitExtension`. This is one subclass in the file that
  already has the MariaDB and SQLite subclasses. If `test-server-database-matrix` adds it first, section 5 only pins images.
- Integration database images are pinned by tag and digest in `SharedContainers.kt`, like the chart's dependencies are.
  The digests are resolved when the section starts, not in this proposal.

Tests added by these sections follow F.I.R.S.T. The pure parts (the driver's database argument, the engine's connection
string, the Hikari mapping of the pool keys) get unit tests with no container and no clock. The container and integration
runs are the proof for behaviour, and they run through `HEAVY_SLOTS=1 heavy`, one at a time.

### 6. Documentation-only sections need a check, not a test

Sections 2, 7 and 8 change no behaviour. For these, the failing check is a grep that finds the stale sentence before the
change and nothing after it, plus a check that every test named in a PostgreSQL claim exists in the tree. The existing
chart golden tests must pass unchanged in section 8, which shows that the comment change does not alter rendering.

## Open questions and defaults

1. **MySQL.** The maintainer decision says "MariaDB and MySQL stay supported, not recommended". MySQL is "experimental" in the
   docs today and has no integration coverage of its own. Default: MySQL stays experimental, and MariaDB is the only
   "supported, not recommended" entry. Promoting MySQL needs its own tests and its own change.
2. **Pool size key.** The maintainer asked for a pool-size switch. `databaseThreadPool` with both environment names already
   exists. Default: no new size key. Section 6 adds the three Hikari keys, which are the missing part. If the maintainer
   wants a separate size key for PostgreSQL, that is a second key for one value and needs its own decision.
3. **Pool default.** The default stays 1 for every database, as the drop-in rule requires. The docs give the budget formula
   and do not recommend a number, because the right size depends on the workload and the server limit. Default: no number
   in the docs.
4. **Upstream image on PostgreSQL.** The upgrade rehearsal needs the upstream image to start on PostgreSQL. Default: section 4
   first checks that it does. If it does not, the PostgreSQL rehearsal runs for the new image only, and the docs say that the
   upgrade from upstream to PostgreSQL is not tested.
5. **PostgreSQL version.** The docs name no version. Default: pin the current stable major at the time section 4 starts,
   by tag and digest, and name it in the docs as the tested version. The bug template asks for PostgreSQL 16, which stays a
   valid report.
6. **Settings secret variant.** Default: if `test-server-database-matrix` has merged and already contains the variant, section 5
   pins images only.
7. **Label timing.** The matrix proposal says the label goes after the pool change, once the matrix is green on main. The
   matrix is on main (#121), so the evidence exists. Default: section 2 removes the PostgreSQL label now, without waiting for
   section 6, and its pull request description names this deviation from the matrix proposal for the maintainer to confirm.
8. **Mixed-version test on PostgreSQL.** Not requested. Default: not added. The high availability page keeps its row for
   mixed versions on MariaDB.
9. **Test class names.** `RecommendedRemoteSpecificationJunitExtension` runs MariaDB under a name that now says the opposite.
   Default: keep the names. A rename is a refactor and changes 49 test files for no change in behaviour.
10. **Maintainer wording "bug fixes".** The maintainer called the stale statements bug fixes. Default: section 2 is
    `fix(database)` because it corrects what the product says about itself. The text-only sections are `docs`.

## Risks

- The PostgreSQL leg of the smoke test can fail for reasons that the MariaDB leg does not show, such as the lock timeout on
  the schema lock. The first runs are read before the leg is made required.
- The Hikari keys change the pool that every PostgreSQL installation uses. The defaults are the existing values, and a test
  pins them, so the risk is in a wrong mapping, which the unit test catches.
- The generated comment changes for every installation at its next start in AUTO mode. The text does not change a value, and
  no test reads it as a setting.

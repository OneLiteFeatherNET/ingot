# Proposal

## Why

An operator who runs more than one Ingot replica has to choose a shared database, and the documentation does not tell them
which one to choose. The gitbook pages call PostgreSQL "supported", while the code comment in `DatabaseConnection.kt` and the
configuration file generated from `LocalConfiguration.kt` still file it under "experimental, not covered with tests". The
high availability page says that no test starts two replicas on PostgreSQL, and two integration tests do exactly that. The
two-replica smoke test, the container upgrade rehearsal and the container tests run on MariaDB only.

The result is that PostgreSQL has the most complete evidence for a shared database in the integration suite and the least
visible support statement. Operators who want a database server pick MariaDB because the pages describe it as the tested
path, and the tests that would justify PostgreSQL as the recommendation are not in the places that the documentation names.

The maintainers have decided that PostgreSQL is the recommended production database for several replicas. MariaDB and MySQL
stay usable and are described as supported or experimental, not recommended. SQLite stays the default for existing
installations. This change makes the documentation, the chart comments and the CI evidence say the same thing, and it fixes
the statements that are already wrong.

Facts checked against origin/main (`189012696`):

- `DatabaseConnection.kt:56` labels the `mysql`, `postgresql` and `h2` branches as "Experimental implementations (not covered
  with integration tests)". The `postgresql` branch at line 58 is covered by `PostgresSpecificationJunitExtension` and by the
  PostgreSQL legs of `ConcurrentSchemaInitialisationIntegrationTest`, `TwoInstanceClusterIntegrationTest` and
  `DatabaseClusterTransportIntegrationTest`.
- `LocalConfiguration.kt:53` puts `postgresql` under "Experimental providers (not covered with tests)" in the text written to
  `configuration.cdn`.
- `docs/gitbook/explanation/high-availability-support.md:41` says "no test starts two replicas on PostgreSQL" and marks that
  row "not tested". `docs/gitbook/how-to/run-on-kubernetes.md:431` says the lock tests run on MariaDB.
  `docs/gitbook/how-to/upgrade-ingot.md:16` names MariaDB and MySQL as the databases that create `ingot_lock`.
- `docs/gitbook/how-to/run-on-kubernetes.md:47` and `docs/gitbook/reference/configuration-and-startup.md:157` still say that
  the configuration comment lists PostgreSQL as experimental. `.github/README.md:78` says PostgreSQL is "marked experimental".
- The `ha-smoke` job runs on MariaDB only (`charts/ingot/tests/ha/dependencies.yaml`). The container tests start MariaDB
  (`MariaDbUpgradeTest`, `ClusterBusImageTest`, `MixedVersionClusterTest`, `UpgradeScenario`). Neither has a PostgreSQL leg.
- `SettingsSecretReferenceIntegrationTest` runs through `RecommendedLocalSpecificationJunitExtension` and
  `RecommendedRemoteSpecificationJunitExtension`, which use MariaDB and not PostgreSQL.
- `SharedContainers.kt` starts `mariadb:latest` and `postgres:latest`. Neither tag is pinned. The container tests use
  `mariadb:11.4` without a digest.
- `databaseThreadPool` (`LocalConfiguration.kt:124`) defaults to `1` and already has the keys `INGOT_LOCAL_DATABASETHREADPOOL`
  and `REPOSILITE_LOCAL_DATABASETHREADPOOL`. Hikari pool lifetime, timeout and keepalive are not configurable.
- `PostgresAdvisorySchemaLock` takes `pg_advisory_lock` on its own connection, outside the pool, and sets `lock_timeout` on
  that session. Both are session state, which is why PgBouncer in transaction mode is not a supported setup.

## What Changes

The change ships as seven code pull requests. Each section has one Conventional Commits type and one scope, and each section
ends with a task that opens its pull request under the title below. No section mixes types.

| Tasks section | Type(scope) | Pull request title |
| --- | --- | --- |
| 2 | `fix(database)` | `fix(database): correct the stale postgresql support claims` |
| 3 | `test(chart)` | `test(chart): run the two-replica smoke test on postgresql` |
| 4 | `test(image)` | `test(image): run the upgrade rehearsal and cluster bus test on postgresql` |
| 5 | `test(database)` | `test(database): run settings secret references on postgresql` |
| 6 | `feat(database)` | `feat(database): add pool lifetime, timeout and keepalive settings` |
| 7 | `docs(database)` | `docs(database): recommend postgresql for shared databases` |
| 8 | `docs(chart)` | `docs(chart): recommend postgresql in the values and install notes` |

- **Section 2, stale claims.** Correct the statements listed under Why: the `DatabaseConnection.kt` comment, the
  `LocalConfiguration.kt` text, the high availability rows, the lock statement on Kubernetes, the lock table statement in
  the upgrade guide and the README. Its evidence is `test-server-database-matrix` (merged as #121), so the claim can be checked
  against that suite.
- **Section 3, two-replica smoke test on PostgreSQL.** A second database leg of the `ha-smoke` job. It installs the same
  `ha/values.yaml` with a PostgreSQL dependency manifest and the same five checks. Both legs run on pull requests.
- **Section 4, container tests on PostgreSQL.** A PostgreSQL leg of the upgrade rehearsal (`UpgradeScenario` gets a PostgreSQL
  engine) and of the two-replica cluster bus test. The MariaDB legs stay as they are. The container test images get pinned
  by tag and digest.
- **Section 5, settings secret references on PostgreSQL.** A PostgreSQL variant of `SettingsSecretReferenceIntegrationTest`
  through `PostgresSpecificationJunitExtension`, and digests for the integration database images in `SharedContainers.kt`.
  If `test-server-database-matrix` has already added this variant when section 5 starts, section 5 only pins the images.
- **Section 6, pool settings.** Three optional keys for the Hikari pool, `databaseMaxLifetime`, `databaseConnectionTimeout`
  and `databaseKeepalive`, each read as `INGOT_LOCAL_<KEY>` with `REPOSILITE_LOCAL_<KEY>` as the fallback. Their defaults are
  the Hikari defaults that the pool already uses, so no installation changes. `databaseThreadPool` stays the one pool-size
  key and its default stays `1`.
- **Section 7, recommendation in the docs.** PostgreSQL is named as the recommended database for several replicas, MariaDB and
  MySQL are described as supported or experimental and not recommended, and the PgBouncer limit is written down with its
  reason. The high availability row for PostgreSQL with several replicas moves to tested, with the names of the tests that
  now back it.
- **Section 8, chart comments.** The `database` block of `charts/ingot/values.yaml` and the replica warning in
  `charts/ingot/templates/NOTES.txt` name PostgreSQL as the recommended shared database. No default changes.

Each section that changes a configuration key, an environment variable or a manifest also updates its page under
`docs/gitbook/` (section 6 for the pool keys, section 8 for the chart).

### Out of scope

- A change of the default database, of the default pool size or of any chart default. Existing installations keep SQLite and
  a pool of one.
- Removing MariaDB, MySQL or H2, and any migration tool between them.
- Promoting MySQL to "supported". Its label stays "experimental" until it has tests of its own (see the open questions in
  `design.md`).
- Supporting PgBouncer in transaction mode. The change documents that it is unsupported. Making it work would need the schema
  lock to move off session state, which is a separate design.
- New two-replica tests on MariaDB. The MariaDB legs that exist stay as they are.
- The mixed-version test on PostgreSQL. It is a follow-up when the upgrade rehearsal on PostgreSQL is green.
- Renaming the `Recommended*SpecificationJunitExtension` classes. Their names are misleading after this change, because the
  remote one runs MariaDB. A rename is a refactor and is not needed for the claims.
- The fixture `reposilite-test/workspace/configuration.cdn`, which holds the old comment as test data. It changes only if a test
  compares it with the generated text.
- The matrix itself (`test-server-database-matrix`) and the pool work that the matrix proposal names as its own change.

## Impact

- **Breaking: no.** No configuration key is removed or renamed, no environment variable changes meaning, no default changes,
  no manifest or volume changes and no data migration runs. An installation pointed at Ingot needs no edit. The three pool keys
  are new and optional.
- **Operator-visible text.** The generated `configuration.cdn` comment names PostgreSQL as supported. The configuration file
  is rewritten in AUTO mode on each start, so existing installations see the new comment on their next start. Values do not change.
- **Dependencies.** `test-server-database-matrix` is on main (#121) and is the evidence for section 2. Section 6 follows it,
  as the matrix proposal orders the pool work after the matrix. Section 3 depends on `add-ha-smoke-test`, which is already on main. Section 7 runs
  after sections 2, 3, 4 and 6 are merged, so that its statements are true when they land.
- **CI.** The `ha-smoke` job gets one more leg, and the container job gets one more upgrade rehearsal and one more cluster bus
  test. Both run on pull requests and nowhere else changes.
- **Code touched.** `reposilite-backend/src/main/kotlin/com/reposilite/configuration/local/` (section 2 comment, section 6 keys),
  `reposilite-backend/src/main/kotlin/com/reposilite/configuration/local/infrastructure/DatabaseConnection.kt` (sections 2 and 6),
  `reposilite-backend/src/integration/` (section 5 and the pinned images), `reposilite-backend/src/containerTest/` (section 4),
  `charts/ingot/tests/` (sections 3 and 4), `.github/workflows/ci.yml` (sections 3 and 4), `charts/ingot/values.yaml` and
  `charts/ingot/templates/NOTES.txt` (section 8), and the pages of `docs/gitbook/` named in each section.

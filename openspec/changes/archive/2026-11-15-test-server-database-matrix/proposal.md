# Proposal

## Why

An operator who runs several Ingot replicas on PostgreSQL relies on the same settings, tokens, statistics and Maven metadata
behaviour that the MariaDB deployments rely on. Today that behaviour is tested on SQLite and MariaDB for every feature
area, but on PostgreSQL in only three of them. The other eleven feature areas that run against MariaDB have no PostgreSQL run,
so a PostgreSQL-only defect in those areas would reach the operator first.

The support status says the wrong thing in both directions. The code comment above the PostgreSQL branch in
`DatabaseConnection.kt` (line 56) calls the server databases experimental and "not covered with integration tests". The
gitbook page `configure-database.md` lists PostgreSQL as supported. Neither statement can be checked against a matrix,
because there is no matrix. Before the experimental label is removed, the suite has to prove the claim on PostgreSQL in the
same places it proves it on MariaDB.

Facts checked against origin/main (`c0f01846`):

- `DatabaseConnection.kt:56` is the comment `Experimental implementations (not covered with integration tests)`. It covers
  the `mysql` branch (line 57) and the `postgresql` branch (line 58). The `mariadb` branch (line 55) carries no label, so the
  roadmap's "server DBs" is only half right.
- Integration tests pick their database through a JUnit extension. `RecommendedLocalSpecificationJunitExtension` (SQLite) is
  used by 49 test files, `RecommendedRemoteSpecificationJunitExtension` (MariaDB, S3 through Floci) by 14 files,
  `ExperimentalRemoteSpecificationJunitExtension` (PostgreSQL, filesystem) by 3 files (Audit, Statistics, Status) and
  `ExperimentalLocalSpecificationJunitExtension` (H2) by 3 files.
- Every one of the 14 MariaDB files also has a SQLite subclass. The three PostgreSQL files are a subset of them.
- `SharedContainers.postgres` and `IsolatedResources.createPostgresSchema` already exist, with one schema per test. No new
  container or isolation code is needed.
- The `coverage` job of `ci.yml` runs `:reposilite-backend:testCoverage`, which depends on `:reposilite-backend:integration`.
  New integration classes therefore run in CI without a workflow change.

## What Changes

- **Matrix extension.** Add `PostgresSpecificationJunitExtension` next to the MariaDB extension. It creates one PostgreSQL
  schema per test, uses S3 through Floci as storage (the same as the MariaDB extension, so that the database is the only
  axis that changes), and drops the schema after the test.
- **PostgreSQL subclasses.** For each of the 14 spec families that run against MariaDB, add one `Postgres<Name>IntegrationTest`
  subclass in the same file as its siblings. Audit, Statistics and Status already have a PostgreSQL run on filesystem
  storage, which stays as it is. The new subclass adds the S3 run beside it.
- **Matrix rule.** Each of the 14 spec families runs on SQLite, MariaDB and PostgreSQL with the same spec class and the same
  storage. The rule is checked in review by listing the subclasses of each file, not by a new test.
- **No product code.** No file under `reposilite-backend/src/main` changes. The experimental label and the documentation stay
  as they are; the decision to drop them follows the first green matrix run (see Out of scope).

## Out of scope

- Hikari pool settings (`maxLifetime`, keepalive, `connectionTimeout`, a `poolName`) and the pool budget documentation. They ship
  as a separate `feat(database)` change, `add-database-pool-settings`, that depends on this one.
- Removing the experimental label from `DatabaseConnection.kt:56` and the PostgreSQL row of `configure-database.md`. This
  follows the pool change, once the matrix has been green on main. The label is a support claim, and this change only
  produces the evidence for it.
- MySQL. It has no integration coverage and no extension, and adding one is a new database, not a matrix.
- H2. It stays on its three existing classes. `configure-database.md` says H2 is "not covered by integration tests", which is
  inaccurate for those three classes; the correction belongs with the label change.
- Galera. CI runs a standalone MariaDB. Certification failures and deadlocks (1213) are covered by the lease changes
  `fix-schema-init-lock` and `fix-cluster-metadata-lock`, with their own tests.
- Fixing a defect that the matrix exposes. A PostgreSQL-only failure is reproduced by the failing test and then fixed in a
  separate `fix(database)` change with its own proposal. This change does not merge red.
- Load, performance and the default pool size (`databaseThreadPool` stays 1).

## Capabilities

### New Capabilities

- `database-integration-matrix`: the database types that each integration behaviour runs on, and the isolation rules for the
  per-test databases and schemas.

## Impact

- **Type and scope.** The change ships under `test(database)`. It has one type. The pool settings are a second change.
  The pull request title, squash-merged onto main, is:

  `test(database): run integration tests on postgresql and mariadb`

- **Breaking marker.** No breaking change. No configuration key, environment variable, manifest, volume, data file or plugin
  changes, and no runtime code changes. A Reposilite installation pointed at Ingot behaves as before. The title carries no `!`
  and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None for operators.
- **CI.** Fourteen more integration classes run in the existing `coverage` job. The runtime of that job grows; the
  PostgreSQL container is shared and each test pays for one schema create and drop. No new job, no new image, no new secret.
- **Dependencies.** None before this change. `add-database-pool-settings` depends on this change. The Wave E change
  `add-online-database-backup` may touch the same integration files; if both are in review, the later one rebases.
- **Code.** `reposilite-backend/src/integration` only.

## Verification

- The new subclasses are run through `HEAVY_SLOTS=1 heavy` with `./gradlew :reposilite-backend:integration --tests '<pattern>'
  --no-daemon --max-workers=2`, one pattern per feature area, never the full suite.
- A full `:reposilite-backend:testCoverage` run on the pull request is the proof for CI.
- `openspec validate test-server-database-matrix --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/test-server-database-matrix` prints nothing.

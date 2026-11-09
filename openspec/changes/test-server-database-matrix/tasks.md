# Tasks

Every group is one commit of one Conventional Commits type, all with the scope `database`. The change only adds and renames
test code, so there is no docs group: no setting, environment variable or manifest changes. Tests follow F.I.R.S.T.: no
sleeps or waits by time, no system clock, no shared mutable state between test classes, one schema per test created and
dropped in `beforeEach` and `afterEach`, and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy` with
`--no-daemon --max-workers=2`, targeted with `:reposilite-backend:integration --tests '<pattern>'`, one container-backed run at
a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose test-server-database-matrix`: this change and nothing else. Verify:
  `openspec validate test-server-database-matrix --strict` passes and the dash grep from the proposal prints nothing.

## 2. Matrix extension and PostgreSQL subclasses

- [ ] 2.1 `test(database)`: add `PostgresSpecificationJunitExtension` in `reposilite-backend/src/integration/kotlin/com/reposilite`,
  a copy of `RecommendedRemoteSpecificationJunitExtension` that takes the database from `SharedContainers.postgres` and
  `IsolatedResources.createPostgresSchema`, with `postgres.schemaToken(schema)` and the same S3 settings through Floci. Its
  schema is dropped in `afterEach`. Add the `PostgresStatusIntegrationTest` subclass in `status/StatusIntegrationTest.kt` first
  and run it on its own. Verify: the run is green, or a red run is recorded as a finding (see 2.4). The class names follow the
  existing `RemoteStatusIntegrationTest` pattern.
- [ ] 2.2 `test(database)`: add one `Postgres<Name>IntegrationTest` subclass with `@ExtendWith(PostgresSpecificationJunitExtension::class)`
  to each of the other 13 files that already hold a MariaDB subclass: `audit/AuditIntegrationTest.kt`,
  `statistics/StatisticsIntegrationTest.kt`, `token/AccessTokenIntegrationTest.kt`,
  `cleanup/CleanupMetadataIntegrationTest.kt`, `cleanup/CleanupMirrorCacheIntegrationTest.kt`,
  `javadocs/JavadocsIntegrationTest.kt`, `maven/MavenApiIntegrationTest.kt`, `maven/MavenIntegrationTest.kt`,
  `maven/MavenMetadataDeleteIntegrationTest.kt`, `maven/MavenMirrorsIntegrationTest.kt`,
  `maven/MavenRangeIntegrationTest.kt`, `maven/MavenSnapshotPruningIntegrationTest.kt` and
  `configuration/SettingsIntegrationTest.kt`. The existing `ExperimentalRemote` subclasses in Audit, Statistics and Status stay
  unchanged. Verify: each file lists a SQLite, a MariaDB and a PostgreSQL subclass of the same abstract spec.
- [ ] 2.3 `test(database)`: run the new subclasses per feature area with `--tests '*Postgres*'` and a pattern per directory,
  then fix only test-side problems (for example a missing `@TempDir` or a fixture that assumed the MariaDB name). A test that
  fails on PostgreSQL only is a product finding, not a test problem: do not change the assertion to make it pass. Verify: the
  MariaDB and SQLite subclasses of the same files still pass in the same run.
- [ ] 2.4 Finding handling (no commit): if a PostgreSQL-only failure is a product defect, record the failing test name and the
  error text in the pull request description, open a `fix(database)` proposal under `openspec/changes/` in a separate
  proposal pull request, and keep this change open until the defect is merged. Do not skip the test and do not mark it
  ignored.

## 3. Review

- [ ] 3.1 Review the new files for F.I.R.S.T.: no `Thread.sleep`, no `System.currentTimeMillis` or `Instant.now()` in new code, no
  `companion object` state added to the test classes, no schema name shared between tests (the suffix comes from
  `IsolatedResources`). Verify: `git diff origin/main -- reposilite-backend/src/integration | grep -nE 'sleep|currentTimeMillis|Instant.now|companion'`
  prints nothing for added lines.
- [ ] 3.2 Confirm that the `coverage` job of `.github/workflows/ci.yml` runs `:reposilite-backend:testCoverage` and that
  `testCoverage` depends on `:reposilite-backend:integration`. No workflow file changes. Verify: the grep in
  `reposilite-backend/build.gradle.kts` shows the dependency.
- [ ] 3.3 Run the targeted integration patterns for the 14 feature areas once more on the final branch, one at a time.
  Verify: all subclasses of the 14 files pass on SQLite, MariaDB and PostgreSQL.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `test(database): run integration tests on postgresql and mariadb`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator who runs
  replicas on PostgreSQL and finds that most features were only tested on the other servers, in plain words, without class
  names. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.

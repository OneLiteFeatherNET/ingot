# Tasks

Each section below is one code pull request with one Conventional Commits type and scope, and it ends with the task that
opens that pull request. Every commit of a section has the section's type and scope. Tests are written first and seen to fail
before the code they check exists, except in the text-only sections 2, 7 and 8, where the failing check is a search for the
stale sentence (see design.md, decision 6). Every test follows F.I.R.S.T.: no sleeps, no system clock (an injected `Clock`
stands in for `java.time.Clock`), no shared mutable static state, a temporary directory per test, and assertions with messages.
Heavy jobs go through `HEAVY_SLOTS=1 heavy`, one at a time, and Gradle runs with `--no-daemon --max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose recommend-postgresql`: only the files under `openspec/changes/recommend-postgresql/`,
  branched from the remote main branch. Verify: `openspec validate recommend-postgresql --strict` passes and no dash
  character appears in the change.

## 2. Stale PostgreSQL claims

Type and scope: `fix(database)`. Prerequisite: `test-server-database-matrix` is on main (#121). Its proposal orders the label
removal after the pool change, so the pull request description of 2.4 names that deviation for the maintainer to confirm.

- [ ] 2.1 `fix(database)`: search the documentation, the chart comments and the code comments for the stale sentences and
  record the lines. Verify: the search finds `DatabaseConnection.kt` (the comment above the `postgresql` branch),
  `LocalConfiguration.kt` (the "Experimental providers" comment that lists `postgresql`), `high-availability-support.md`
  (the row "PostgreSQL with several replicas" and the Coverage sentence), `run-on-kubernetes.md` (the lock hint and the
  sentence about the code comment), `upgrade-ingot.md` (the `ingot_lock` sentence), `configuration-and-startup.md` (the
  sentence about the generated comment) and `.github/README.md` (the list of experimental databases).
- [ ] 2.2 `fix(database)`: move `postgresql` out of the experimental comment in `DatabaseConnection.kt` and out of the
  "Experimental providers" text in `LocalConfiguration.kt`. Keep `mysql` and `h2` in it. Verify: the grep of 2.1 finds no
  PostgreSQL label, and the Kotlin unit tests still compile and pass with `./gradlew :reposilite-backend:test --no-daemon --max-workers=2`.
- [ ] 2.3 `fix(database)`: correct the high availability row to "partly tested" and name
  `ConcurrentSchemaInitialisationIntegrationTest` (Remote.POSTGRESQL), `TwoInstanceClusterIntegrationTest` (`startPair(postgres = true)`)
  and `DatabaseClusterTransportIntegrationTest` (PostgreSQL case). Correct the Coverage sentence, the lock sentence in
  `run-on-kubernetes.md`, the `ingot_lock` sentence in `upgrade-ingot.md` (PostgreSQL creates the table too, the schema lock
  is advisory there), the README list and the two sentences of the Kubernetes and configuration pages that refer to the
  code comment. Verify: every test name on the page exists (grep), and the search of 2.1 is empty for PostgreSQL.
- [ ] 2.4 `fix(database)`: open the code pull request with the title `fix(database): correct the stale postgresql support claims`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what an operator reads that is wrong
  today (PostgreSQL is called experimental, and a reader is told that no test covers two replicas on it), and what the
  correction points to. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact
  title, the commit-style hook accepts the title and every commit, and the body has no em or en dash.

## 3. Two-replica smoke test on PostgreSQL

Type and scope: `test(chart)`. Prerequisite: `add-ha-smoke-test` (on main).

- [ ] 3.1 `test(chart)`: write a failing unit test in `charts/ingot/tests/test_ha_dependencies.py` that reads
  `charts/ingot/tests/ha/postgres-dependencies.yaml` and asserts one PostgreSQL Service on 5432, a database secret whose
  connection string names that Service with the `postgresql` engine, and the image pinned by digest. Verify: the test fails
  before 3.2 because the manifest is missing.
- [ ] 3.2 `test(chart)`: add `charts/ingot/tests/ha/postgres-dependencies.yaml` and `charts/ingot/tests/ha/postgres-values.yaml`
  (the second only overrides the secret and the readiness of the database). Verify: the test of 3.1 passes.
- [ ] 3.3 `test(chart)`: write a failing unit test in `charts/ingot/tests/test_ha_smoke.py` that the driver takes the database
  as an argument (`mariadb` or `postgresql`), chooses the manifest and the wait names from it, and rejects any other value,
  using a fake `kubectl`. Verify: the test fails before 3.4.
- [ ] 3.4 `test(chart)`: add the database argument to `charts/ingot/tests/ha_smoke.py`. Verify: the tests of 3.3 pass with
  `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`, and no test opens a socket.
- [ ] 3.5 `test(chart)`: make the `ha-smoke` job of `.github/workflows/ci.yml` a matrix over `mariadb` and `postgresql`, applying
  the matching manifest and running the driver with the argument. Verify: both legs are green on the pull request, and a
  deliberate break of the PostgreSQL Service name fails only the PostgreSQL leg (checked locally, not committed).
- [ ] 3.6 `test(chart)`: open the code pull request with the title `test(chart): run the two-replica smoke test on postgresql`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what a reviewer would see go wrong with two
  PostgreSQL replicas (a start that waits on the schema lock, a metadata version that is lost), and names the MariaDB leg as
  unchanged. No session link, no attribution line. Verify: the title, the hook and the body as in 2.4.

## 4. Container tests on PostgreSQL

Type and scope: `test(image)`. No prerequisite beyond main, where `test-mixed-version-cluster` has merged.

- [ ] 4.1 `test(image)`: check that the upstream image starts on PostgreSQL with the connection string of the new image. Verify:
  the result is written in the pull request. If it does not start, the rehearsal of 4.3 is for the new image only, and the
  docs of section 7 say so (open question 4 in design.md).
- [ ] 4.2 `test(image)`: write a failing unit test for the connection string that the engine produces, with `DatabaseEngine`
  extended by `POSTGRESQL` and the `postgres:` image pinned by tag and digest in `UpgradeScenario`. Verify: the test fails
  before 4.3 because the engine does not exist.
- [ ] 4.3 `test(image)`: add `DatabaseEngine.POSTGRESQL`, a PostgreSQL container helper beside `MariaDb` in
  `MixedVersionReplicas.kt`, `PostgresUpgradeTest` beside `MariaDbUpgradeTest` and a PostgreSQL leg of `ClusterBusImageTest`.
  Pin `MARIADB_IMAGE` by digest in the same files. Verify: `HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:containerTest --no-daemon --max-workers=2 --tests '*PostgresUpgradeTest*' --tests '*ClusterBusImageTest*'` passes against the
  images of this commit.
- [ ] 4.4 `test(image)`: open the code pull request with the title
  `test(image): run the upgrade rehearsal and cluster bus test on postgresql`. Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its
  sections. The description says what an operator upgrading onto PostgreSQL would lose if a rollback did not keep the tokens,
  and says that the mixed-version test stays on MariaDB. No session link, no attribution line. Verify: the title, the hook
  and the body as in 2.4.

## 5. Settings secret references on PostgreSQL and pinned integration images

Type and scope: `test(database)`. Prerequisite: `test-server-database-matrix` is on main (#121). It did not add a PostgreSQL
variant of `SettingsSecretReferenceIntegrationTest` (only the Local and Remote subclasses exist), so 5.1 is needed.

- [ ] 5.1 `test(database)`: add `PostgresSettingsSecretReferenceIntegrationTest` in
  `reposilite-backend/src/integration/kotlin/com/reposilite/configuration/SettingsSecretReferenceIntegrationTest.kt`, annotated
  with `PostgresSpecificationJunitExtension`, beside the Local and Remote subclasses. Verify: the test fails first if the
  extension is missing, and then `HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:integration --no-daemon --max-workers=2 --tests '*SettingsSecretReference*'` passes.
- [ ] 5.2 `test(database)`: pin `mariadb:latest` and `postgres:latest` in `SharedContainers.kt` by tag and digest. Verify: the
  search for `latest` in the test code finds nothing, and the same integration pattern passes.
- [ ] 5.3 `test(database)`: open the code pull request with the title `test(database): run settings secret references on postgresql`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what a secret reference looks like to an
  operator who runs on PostgreSQL, and that the test covers the same assertions as the other databases. No session link, no
  attribution line. Verify: the title, the hook and the body as in 2.4.

## 6. Pool lifetime, timeout and keepalive keys

Type and scope: `feat(database)`. Prerequisite: `test-server-database-matrix` is on main (#121), which its proposal orders first.

- [ ] 6.1 `feat(database)`: write a failing unit test for a pure mapping function in `DatabaseConnection.kt` that takes the three
  values and returns the Hikari settings in milliseconds, with a test for the defaults (1800000, 30000, 0), a test that a
  whole-number value maps to milliseconds, and a test that SQLite and H2 ignore the values. Verify: the tests fail before 6.2
  because the function does not exist.
- [ ] 6.2 `feat(database)`: add `databaseMaxLifetime`, `databaseConnectionTimeout` and `databaseKeepalive` to
  `LocalConfiguration.kt` with the `Int` type and the defaults of design.md decision 3, and apply them in `createDataSource`.
  Verify: the tests of 6.1 pass, and a unit test for the environment mapping reads `INGOT_LOCAL_DATABASEMAXLIFETIME` and the
  `REPOSILITE_LOCAL_` fallback with a fixed environment map.
- [ ] 6.3 `feat(database)`: write a failing unit test that `INGOT_LOCAL_DATABASETHREADPOOL` sets the maximum pool size and that
  `REPOSILITE_LOCAL_DATABASETHREADPOOL` is the fallback, then make it pass without a new key. Verify: the test passes
  and no second size key exists in `LocalConfiguration.kt`.
- [ ] 6.4 `feat(database)`: update `docs/gitbook/reference/environment-variables.md` and `docs/gitbook/reference/configuration-and-startup.md`
  with the three keys, the defaults and the units, and `docs/gitbook/how-to/configure-database.md` with the pool budget of
  design.md decision 3. Verify: each key appears in both reference pages with the code's default, and the budget sentence
  gives 18 at the start and 15 in the steady state for three replicas with a pool of five.
- [ ] 6.5 `feat(database)`: open the code pull request with the title `feat(database): add pool lifetime, timeout and keepalive settings`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what an operator sees when a connection
  through a firewall dies after an idle hour, and that the defaults are the values the server already uses. No session link,
  no attribution line. Verify: the title, the hook and the body as in 2.4.

## 7. Recommendation in the documentation

Type and scope: `docs(database)`. Prerequisite: sections 2, 3, 4 and 6 are merged, so that the statements are true when they land.

- [ ] 7.1 `docs(database)`: write the recommendation into `docs/gitbook/how-to/configure-database.md` (the support table, the
  "for several replicas" paragraph and the PostgreSQL tab), `docs/gitbook/explanation/multi-replica-operation.md`,
  `docs/gitbook/how-to/run-on-kubernetes.md` (the database requirement and the support hint) and `.github/README.md`. Add the
  PgBouncer section to `configure-database.md` with the transaction-mode limit and the reason of design.md decision 4. Keep
  MariaDB "supported, not recommended" and MySQL and H2 experimental. Verify: a search for "recommended" in those pages finds
  PostgreSQL in each recommendation, and the sentences match `specs/database-recommendation/spec.md`.
- [ ] 7.2 `docs(database)`: flip the high availability row "PostgreSQL with several replicas" to "tested on every pull request"
  and name the PostgreSQL legs of the smoke test and the container suite. Verify: the names exist in `ci.yml` and in the
  container suite, found by grep.
- [ ] 7.3 `docs(database)`: open the code pull request with the title `docs(database): recommend postgresql for shared databases`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what an operator who is choosing a database
  for several replicas reads now, and what stays the same for existing installations. No session link, no attribution line.
  Verify: the title, the hook and the body as in 2.4.

## 8. Recommendation in the chart

Type and scope: `docs(chart)`. Prerequisite: section 7 is merged.

- [ ] 8.1 `docs(chart)`: update the comment of the `database` block in `charts/ingot/values.yaml` and the replica warning in
  `charts/ingot/templates/NOTES.txt` to name PostgreSQL as the recommended shared database. No value changes. Verify: the
  golden tests in `charts/ingot/tests/test_golden.py` pass unchanged, which shows that rendering did not change, and
  `helm lint charts/ingot --strict -f charts/ingot/ci/allinone-values.yaml` passes.
- [ ] 8.2 `docs(chart)`: open the code pull request with the title `docs(chart): recommend postgresql in the values and install notes`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description says what a Helm user sees when the install
  notes warn about replicas, and that the chart default is unchanged. No session link, no attribution line. Verify: the title,
  the hook and the body as in 2.4.

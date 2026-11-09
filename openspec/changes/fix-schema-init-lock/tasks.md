# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `database`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock (time is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Waits are bounded by latches. Gradle runs go through `HEAVY_SLOTS=1 heavy` with
`--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-schema-init-lock`: this change and nothing else. Verify:
  `openspec validate fix-schema-init-lock --strict` passes and the dash grep from the proposal prints nothing.

## 2. Failing tests first

- [ ] 2.1 `test(database)`: write a failing integration test, `ConcurrentSchemaInitialisationIntegrationTest`, that starts
  several initialisations at once against Testcontainers MariaDB and PostgreSQL, gated on a start latch, and asserts that
  every one completes and the schema is complete. Run it on unmodified main. If both vendors pass there, stop and revise
  the proposal and design before group 3, because the problem is not shown. Verify: the result for each vendor is
  recorded in the pull request description.
- [ ] 2.2 `test(database)`: write failing unit tests for the lease as a pure component with an injected `Clock` and an
  in-memory fake of the lease table: acquire when free; refuse while held and unexpired; take over after expiry; renewal
  keeps the lease; release only by the owner; a waiter gives up at the injected deadline; error 1213 is retried and
  another error is not. Verify: they fail on the missing class.
- [ ] 2.3 `test(database)`: write failing unit tests that the embedded dialects (SQLite, H2) return a no-op lock, create no
  `ingot_lock` table, and leave the initialisation calls unchanged. Verify: they fail before 3.1.
- [ ] 2.4 `test(database)`: write a failing test of the start order with recording fakes: the lock is taken before the first
  store is created, released after the post-initialise event and before the server binds, and released when a store
  throws. Verify: it fails before 3.2.
- [ ] 2.5 `test(database)`: write failing tests for `INGOT_LOCAL_SCHEMALOCKTIMEOUT` with the `REPOSILITE_LOCAL_` fallback,
  precedence and the default of 300 seconds, using the existing local configuration test fixture. Verify: it fails before
  3.2.
- [ ] 2.6 `test(database)`: write a failing integration test for lease takeover: a holder that stops renewing is replaced
  after the injected expiry, and a healthy holder that keeps renewing is not. Verify: it fails before 3.1.

## 3. Lock primitive and wiring

- [ ] 3.1 `fix(database)`: add the `ingot_lock` table with a tolerant creation statement, the `SchemaLock` port, the lease
  implementation for MariaDB and MySQL with retry on 1213, the PostgreSQL advisory implementation and the no-op for
  SQLite and H2. Verify: the tests of 2.2, 2.3 and 2.6 pass.
- [ ] 3.2 `fix(database)`: acquire the lock at the top of `ReposiliteFactory.createReposilite`, release it at the end of
  `Reposilite.launch` before `webServer.start`, release it in `Reposilite.shutdown()` idempotently, and add
  `schemaLockTimeout` to `LocalConfiguration`. Verify: the tests of 2.4 and 2.5 pass, the existing lifecycle tests pass
  unedited, and the test of 2.1 passes on MariaDB and PostgreSQL.

## 4. Docs

- [ ] 4.1 `docs(database)`: amend section 1.1 of `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` from `GET_LOCK`
  to the lease row on MariaDB and MySQL, and the lock wording in `docs/superpowers/plans/2026-08-08-ha-stage-1-correctness.md`.
  Verify: no remaining `GET_LOCK` recommendation in either file for the schema lock.
- [ ] 4.2 `docs(database)`: document `INGOT_LOCAL_SCHEMALOCKTIMEOUT` in `docs/gitbook/reference/environment-variables.md`, the
  start-up lock and its timeout failure in `docs/gitbook/explanation/multi-replica-operation.md`, and the new table in
  `docs/gitbook/how-to/configure-database.md`. Verify: the three pages name the same default and fallback.
- [ ] 4.3 `docs(database)`: add the upgrade note in `docs/gitbook/how-to/upgrade-ingot.md` for the `ingot_lock` table and the
  new start-up wait. Verify: the note states that no setting has to change.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `fix(database): serialise schema initialisation across replicas`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the operator's story (pods that
  restarted during a rollout with no visible cause) without class names. It carries no session link and no attribution
  line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit,
  and the body has no em or en dash.

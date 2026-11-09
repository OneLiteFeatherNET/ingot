# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `maven`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (the
race test uses latches, and a bounded await is only a failure guard), no system clock (time is injected as a
`java.time.Clock`), no shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs
go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests
run as `:reposilite-backend:integration --tests '<pattern>'`. Containers (MariaDB) run one at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-cluster-metadata-lock`: this change and nothing else. Verify:
  `openspec validate fix-cluster-metadata-lock --strict` passes and the dash grep from the proposal prints nothing.

## 2. Reproduce the lost update first

- [ ] 2.1 `test(maven)`: write a race test: two `generatePom` calls for different versions of one artifact in a fresh
  repository in a temporary directory. A storage decorator parks the first call after it has read `maven-metadata.xml`
  (CountDownLatch) until the second call has finished or is parked on the lock. Assert that both versions are listed
  afterwards, with a message naming the missing version. Verify: it fails on `main` with one version missing. If it does
  not fail, stop and revise the proposal before any production change.
- [ ] 2.2 `test(maven)`: write failing unit tests for the `MetadataLocks` port with an in-memory `MetadataLeaseStore` and an
  injected `Clock`: exclusive per key; a different artifact is not blocked; re-entry on the same thread passes through;
  the lock is released when the block throws; an expired lease is taken over; a release by a non-owner is ignored; a
  lease lost before commit is reported; a deadlock (1213) and a certification failure are retried up to the bound; a
  wait beyond the bound yields a timeout result. Verify: they fail because the port does not exist yet.
- [ ] 2.3 `test(maven)`: write a failing integration test for the SQL lease store against MariaDB (Testcontainers): two
  connections racing for one key, takeover of an expired row, release by the owner only, and the retry on 1213 under a
  forced deadlock. Verify: it fails because the store does not exist yet. Run with
  `HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:integration --tests '<pattern>' --no-daemon --max-workers=2`.

## 3. Serialise metadata updates

- [ ] 3.1 `fix(maven)`: add the `MetadataLocks` port and the in-process implementation (keyed by repository and artifact
  directory). Wrap the POM generation, the version removal, the snapshot prune in `MetadataService` and the body of
  `PreservedBuildsListener` in `withLock`. Verify: 2.1 and 2.2 pass for the in-process implementation, and the existing
  `MavenFacadeTest`, `PreservedBuildsListenerTest` and `WritePathEventCharacterisationTest` pass unedited.
- [ ] 3.2 `fix(maven)`: add the SQL lease implementation on `ingot_lock` (acquire with expiry and takeover, ownership check
  before commit, release by owner, bounded retries on 1213 and SQLSTATE 40001) and select it when a shared SQL database
  is configured. SQLite and H2 keep the in-process lock only. Map the timeout and a lost lease to `503` with
  `Retry-After`. Verify: 2.3 passes, 2.1 passes with the SQL store wired in, and the SQLite default issues no statement
  against `ingot_lock` (a test asserts it).

## 4. Docs

- [ ] 4.1 `docs(database)`: amend section 1.2 of `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` from
  `SELECT ... FOR UPDATE` to the lease row and its retry rules, and add the note on concurrent metadata writes and
  Galera to `docs/gitbook/how-to/configure-database.md`. Verify: no em or en dashes, and the links resolve.

## 5. Staging evidence

- [ ] 5.1 Run the concurrent-deploy check on staging (MariaDB Galera, two replicas): N parallel deploys of different
  versions of one artifact, then compare the listing against the uploads. Record the result in the pull request
  description. This is a manual check and adds no commit. It confirms roadmap open verification 2 (Galera retry
  behaviour) before the pull request is merged.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `fix(maven): serialise metadata updates across instances`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of a version that vanished
  from the listing after two deploys, and how a write now waits or answers `503` instead. It carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and
  every commit, and the body has no em or en dash.

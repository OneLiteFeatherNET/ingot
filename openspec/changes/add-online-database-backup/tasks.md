# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `backup`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock (the time source is an injected `java.time.Clock`), no fixed ports, no shared mutable state, a temporary
directory per test, and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon
--max-workers=2`, targeted and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-online-database-backup`: this change and nothing else. Verify:
  `openspec validate add-online-database-backup --strict` passes and the dash grep from the proposal prints nothing.

## 2. B1 Snapshot service

- [ ] 2.1 `test(backup)`: write failing tests for the snapshot service in a temporary directory: the file name for a
  fixed clock instant (UTC, `ingot-snapshot-<yyyyMMdd-HHmmss>.db`); a name that exists is refused and the old file keeps its
  bytes; a non-SQLite database writes no file and reports the dump tool; the snapshot of a fixture SQLite database opens,
  passes `PRAGMA integrity_check` and holds the fixture rows; a failing `VACUUM INTO` (fake connection) removes the partial
  file. Verify: they fail on the missing class.
- [ ] 2.2 `feat(backup)`: add `net.onelitefeather.ingot.backup` with the snapshot service. It takes a raw connection from the
  pool with auto-commit on, sets a busy timeout, runs `VACUUM INTO` into `backups/`, creates the file with owner-only
  permissions where supported, and removes the partial file on failure. Verify: the tests of 2.1 pass.

## 3. B2 and B3 Endpoint and command line

- [ ] 3.1 `test(backup)`: write failing tests: 403 for a principal without `ingot.admin.maintenance`; 200 with file name, size
  and creation time and no absolute path; 409 on MariaDB and on a name collision; one audit record that names the principal
  and the file name (captured through a fake audit facade); `--snapshot-database` is parsed and selects the snapshot path
  without starting the server. Add an integration test on a temporary SQLite file that calls the endpoint and reads the
  snapshot back. Verify: they fail before 3.2.
- [ ] 3.2 `feat(backup)`: add the `POST /api/database/snapshots` route in the same style as the cleanup routes
  (`managerOnly("maintenance")`), the `--snapshot-database` option in `ReposiliteParameters`, the early exit in the launcher
  and the audit record. Verify: the tests of 3.1 pass and the existing route and parameter tests pass unedited.

## 4. Documentation

- [ ] 4.1 `docs(backup)`: update `docs/gitbook/how-to/back-up-and-restore.md` with the online snapshot for SQLite, the
  endpoint and the flag, the warning that the file holds the S3 keys and the signing key, and the copy-back restore. Update
  `docs/gitbook/reference/http-api.md` with the endpoint and its answers. Verify: the pages build with the docs tooling used
  in the repository, and the dash grep prints nothing.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `feat(backup): add online sqlite database snapshots`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator who needed a
  backup without a stop, without class names. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.

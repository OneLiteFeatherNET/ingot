# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `statistics`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (blocking
points use latches with a bounded await), no system clock (the bucket date comes from an injected `java.time.Clock`), no fixed
ports, no shared mutable state, a temporary directory per test for the SQLite file, and assertions with messages. Gradle runs go
through `heavy`, with `--max-workers=2`, targeted and never the full suite. Unit tests run as
`:reposilite-backend:test --tests 'com.reposilite.statistics.*'`, integration tests as
`:reposilite-backend:integration --tests 'com.reposilite.statistics.*'`.

No group changes a configuration key, environment variable or deployment manifest, so no page under `docs/gitbook/` is updated.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-statistics-shutdown-flush`: this change and nothing else. Verify:
  `openspec validate fix-statistics-shutdown-flush --strict` passes and the dash grep from the proposal prints nothing.

## 2. Failing tests

- [ ] 2.1 `test(statistics)`: write failing tests against a fake `StatisticsRepository` that can block on a latch or throw:
  - the dispose event writes buffered counts, also when statistics is disabled at that moment;
  - a count added while a flush is blocked in the repository is still buffered after the flush;
  - a repository failure keeps the counts, and the next flush writes them once;
  - two flushes started at the same time write each count once;
  - a `40001` error on the first attempt is retried and each count is written once;
  - a failing dispose write is logged through the journalist and does not throw out of the handler;
  - with a fixed `Clock`, the bucket date of a flush is that date (no system time read).
  In the SQL integration tests, with SQLite in a `@TempDir`:
  - an identifier row inserted by a second repository between the lookup and the insert does not fail the batch (the lookup
    seam returns "not found" once);
  - two repositories writing the same new artifact both store their counts.
  Verify: they fail to compile or fail before 3.1 to 3.4. Each test names one behaviour.

## 3. Fix

- [ ] 3.1 `fix(statistics)`: give the date providers an injectable `java.time.Clock` with the system zone as default, and pass
  it from the facade. Verify: the existing `StatisticsFacadeTest` and `StatisticsIntegrationTest` pass unedited.
- [ ] 3.2 `fix(statistics)`: drain by subtraction under a flush monitor, add failed counts back with `merge`, and retry `40001`
  up to three times before merging back. Verify: the drain, concurrency and retry tests of 2.1 pass.
- [ ] 3.3 `fix(statistics)`: replace the identifier insert with `insertIgnore` and read the row back by its id, behind the
  internal lookup seam. Verify: the identifier tests of 2.1 pass.
- [ ] 3.4 `fix(statistics)`: register a `ReposiliteDisposeEvent` handler in `StatisticsPlugin` that flushes synchronously,
  catches and logs its own failures, and runs regardless of the enabled flag. Verify: the dispose tests of 2.1 pass and
  `StatisticsPlugin` still registers the periodic flush unchanged.

## 4. Verification (no commit)

- [ ] 4.1 After `fix-entrypoint-signal-handling` is merged: start the container, count one request, run `docker stop -t 30`,
  start it again, and check the count through `GET /api/statistics/resolved/all`. This proves roadmap P3 for statistics. It is
  not a gate for this pull request.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `fix(statistics): flush resolved request counts on shutdown`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the operator's story (the counts that went
  missing around a restart) without class names. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.

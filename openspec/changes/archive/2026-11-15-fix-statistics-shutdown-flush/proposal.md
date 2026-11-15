# Proposal

## Why

Ingot counts resolved artifact requests in memory and writes the counts to the database every ten seconds. Only that timer
writes them. When Ingot stops, the buffer is not written, so every restart or rolling update drops up to ten seconds of
counts. An operator who compares the numbers before and after a deploy finds them short by the requests of the last window.

The drain loses counts as well. A flush copies the buffer and then clears it. A request that lands between the copy and the
clear is removed without being written. When the database write fails, the copied counts are already gone. This happens
more often in a cluster: when two pods first see the same artifact at the same moment, one of them fails to insert the
artifact's identifier row, the whole batch rolls back, and the cleared buffer cannot be replayed.

Checked against origin/main (2026-11-09):

- `StatisticsFacade.kt:52-58` copies the buffer with `toMap()`, writes it, and then calls `clear()`. The code comment on line 56
  says records can be dropped between the two calls.
- `StatisticsPlugin.kt:50-57` only schedules the ten-second flush. It registers no `ReposiliteDisposeEvent` handler.
- `Reposilite.kt:79-89` shuts down in this order: scheduler, web server, IO executor, extensions (dispose event), database.
  The database is still open when the dispose event runs.
- `SqlStatisticsRepository.kt:190-194` looks up the identifier and inserts it when the lookup finds nothing. A row created by
  another writer in between makes the insert fail.

## What Changes

- **Flush on dispose.** Statistics writes its buffer when the dispose event is emitted, before the database closes. The
  write runs on the shutdown thread, because the IO executor has already stopped at that point. Buffered counts are written
  even when statistics has been switched off since they were counted. A failed write is logged and the counts stay buffered,
  so shutdown is not blocked by it and the other extensions still receive their dispose event.
- **Lossless drain.** A flush writes what it read and then subtracts exactly that amount from each identifier. A request that
  arrives during a flush stays in the buffer for the next flush. A failed write puts its counts back. Flushes run one at a
  time inside the process, so the periodic flush and the shutdown flush never write the same counts twice. A write that
  fails with a deadlock or certification error (SQLState `40001`, as Galera reports it) is retried a few times before the
  counts go back into the buffer.
- **Idempotent identifier insert.** Creating an identifier row ignores a row that another writer created first. The
  identifier's id is a name-based UUID of its repository and path, so both writers insert the same row, and the batch
  continues.
- **Settings and manifests.** None. Ingot keeps the ten-second interval, the defaults and the HTTP and CLI behaviour.

### Out of scope

- Changing the flush interval, the statistics defaults, or any `INGOT_` or `REPOSILITE_` setting.
- Reconciling statistics against storage after a disaster recovery. That belongs to the backup and restore drill (roadmap N13).
- The date of a count. The day is taken when the count is written, so counts buffered across midnight land on the next day.
  Noted for a later change, not fixed here.
- A cluster-wide lock for statistics writes. The row update is additive (`count = count + n`) and atomic per row, so two pods
  adding to the same row cannot lose counts. No new lock is added.
- Proving in the container that counts survive `docker stop -t 30`. That check needs the entrypoint fix (see Impact).
- Any change to the statistics endpoints or to the phrase lookup.

## Capabilities

### New Capabilities

- `statistics-flush`: when buffered resolved-request counts are written, and how the drain and the identifier insert behave
  under concurrent writers and during shutdown.

## Impact

- **Type and scope.** The change ships under `fix(statistics)`. The pull request title, squash-merged onto main, is:

  `fix(statistics): flush resolved request counts on shutdown`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. A Reposilite installation pointed at Ingot behaves the same for every client. The only visible difference is that
  the statistics no longer lose the counts of a restart. The title carries no `!` and no commit carries a `BREAKING CHANGE:`
  footer.
- **Opt-in.** Not opt-in. This is a correctness fix with no new surface. Its effect is the same with and without a cluster.
- **Dependencies.** No code dependency on another change. The end-to-end proof that counts survive a container stop (roadmap
  P3) depends on `fix-entrypoint-signal-handling`, because `runuser` may send SIGKILL to the JVM before the dispose event runs.
  The unit and integration tests of this change do not depend on it. Run the container check after that change merges.
- **Overlap.** `fix-statistics-limit` (on `docs/openspec-wave1`) changes the phrase lookup and its endpoint. It may touch
  `StatisticsFacade.kt` as well, in a different function. Expect at most a textual merge, not a semantic conflict.
- **Code.** `reposilite-backend/src/main/kotlin/com/reposilite/statistics/` (facade, plugin, SQL repository, date providers) and
  the statistics tests under `reposilite-backend/src/test` and `reposilite-backend/src/integration`.
- **Documentation.** No page under `docs/gitbook/` changes, because no setting, environment variable or manifest changes.

## Verification

- Failing tests first, one behaviour each, run through `heavy` with `--max-workers=2`.
- Existing statistics tests pass unedited.
- `openspec validate fix-statistics-shutdown-flush --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-statistics-shutdown-flush` prints nothing.

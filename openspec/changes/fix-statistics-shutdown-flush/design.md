# Design

## Context

`StatisticsFacade` keeps the counts in a `ConcurrentHashMap<Identifier, Long>`. Two code paths write the map to the database:
the ten-second timer, which runs on the IO executor, and the shutdown. At shutdown the scheduler and then the IO executor are
stopped before the dispose event is emitted, and the database closes after it (`Reposilite.kt:79-89`). An IO task submitted
after the IO executor has stopped is rejected, so a flush on the dispose event has to run on the calling thread.

## Decisions

1. **The shutdown flush runs synchronously on the shutdown thread.** The dispose handler calls the facade directly. It does not
   use the IO executor, which has already stopped. The database is still open, so the write succeeds when the database is up.

2. **The drain subtracts what it wrote; it does not swap the map.** A flush takes a snapshot, writes it, and then, per key,
   runs `computeIfPresent(key) { _, current -> current - written }` and removes the key when the result is zero. An increment
   that lands during the write is kept, because it is added to the current value and the subtraction only removes the
   snapshot amount. Replacing the map would race with `merge` calls that still hold the old map.

3. **A failed write merges the counts back.** On an exception the snapshot is added back with `merge(key, written, Long::plus)`.
   The next flush writes them again. A write that fails with SQLState `40001` (deadlock, or certification failure on Galera) is
   retried up to three times with no delay before the counts go back into the buffer.

4. **Flushes are serialised inside one process.** A monitor around the flush keeps the periodic flush and the shutdown flush from
   reading the same counts. The dispose handler waits for a periodic flush that is still running, and the database calls are
   the only thing it can wait on, so the wait is bounded by the database timeouts.

5. **No cross-replica lock.** The count update is `count = count + n` on one row. The database applies it atomically, so two pods
   writing the same row cannot lose each other's increments. MariaDB Galera does not support `GET_LOCK` (roadmap conflict 1), and
   the statistics writes do not need one.

6. **The identifier insert is idempotent.** `createIdentifier` uses `insertIgnore`. The row is then read back by its id, which is a
   name-based UUID of the identifier and is the same for every writer. Only this insert can collide between pods, because the
   resolved-count row is written by upsert.

7. **Clock injection.** The date providers read `LocalDate.now()` today. They take a `java.time.Clock` with the system zone as the
   default, so the tests fix the bucket date. The production call sites do not change.

8. **Two test seams, both internal.** `SqlStatisticsRepository` takes an internal identifier lookup with the current lookup as the
   default. A test replaces it with one that returns "not found" once, which reproduces the lost race deterministically. The
   facade takes its repository through the existing constructor, so the failure and concurrency tests use a fake repository that
   blocks on a latch or throws.

## Risks

- A shutdown whose database is unreachable still loses the buffered counts after the failed retry. The failure is logged. This is
  the residual loss; a durable buffer would be a separate change.
- Errors from a handler are caught inside the statistics handler. `emitEvent` runs every handler in one call, so an uncaught
  exception would skip the handlers of the other extensions.

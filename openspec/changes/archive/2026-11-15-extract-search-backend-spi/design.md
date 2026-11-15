# Design

## Context: what the index is today

All paths below are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/`.

- **Indexed fields.** One row per stored file in `search_artifact` (`database/SearchTables.kt:48-69`): repository, path and
  its lower-cased copy, groupId, artifactId, version (with lower-cased copies for the GAV index), classifier, extension,
  snapshot flag, origin and source host, size, last modified, sha1 and sha256 read from the checksum sidecars
  (`OutboxWorker.kt:119-120` reads `$path.sha1` and `$path.sha256`), and the index time. POM fields are not indexed: the
  artifact search spec lists them as non-goals (`docs/superpowers/specs/2026-10-16-artifact-search.md:61-68`, question
  Q-3 at line 1622).
- **Write path.** `OutboxArtifactIndexSink` inserts outbox rows (`outbox/OutboxArtifactIndexSink.kt:24-31`). `OutboxWorker.tick`
  claims a batch, reads storage, calls `index.apply(...)` and then `outbox.complete` (`outbox/OutboxWorker.kt:56-86`). The
  writer is `DatabaseArtifactIndex` (`database/DatabaseArtifactIndex.kt:34-51`), an upsert keyed by `(repository, path_hash)`.
- **Rebuild and verify.** `RebuildService.writePage` runs the row writes, the cursor and the counters in one
  `runs.atomically { ... }` block (`rebuild/RebuildService.kt:220-227`). `deleteStale` works on `IndexRowStore`
  (`rebuild/RebuildService.kt:261`). `VerifyService.drawSample` pages by row id (`verify/VerifyService.kt:164-167`).
  `StartupRules` reads `rows.count()` (`rebuild/RebuildService.kt:111-113`).
- **Read path.** `ArtifactSearchEngine` (`ArtifactSearchEngine.kt:27-28`) has `id`, `search`, `facets`, `files` and `status`.
  Its only implementation is `DatabaseArtifactSearchEngine` (`database/DatabaseArtifactSearchEngine.kt:76`). The REST
  layer gets it through `SearchFacade.engine` (`application/SearchFacade.kt:33`).
- **Selection.** `SearchPlugin.wire` compares `local.searchEngine` with the constant `database` and otherwise returns a
  disabled facade (`application/SearchPlugin.kt:91-94`, `:231`). The key itself is `LocalConfiguration.kt:178`.
- **Schema.** `SearchSchema.ensure` creates `search_state` first, then in one transaction inserts the marker `schema-1`
  and creates `search_artifact`, `search_outbox` and `search_rebuild_run` (`database/SearchSchema.kt:25`, `:35-53`).

## Goals and non-goals

Goals: one seam with the database as its only implementation; core code that never names a table class; no observable
change; a contract suite that a second implementation can run unchanged.

Non-goals: a second implementation, a public or plugin-facing type, a new key, a new table, and changes to the SQL.

## Decisions

### D1: the seam is an interface that extends the read contract

`ArtifactSearchBackend : ArtifactSearchEngine` in `net.onelitefeather.ingot.search.backend`, internal in this change.
Extending keeps the REST layer and every existing read test untouched: the REST layer keeps receiving an
`ArtifactSearchEngine`. The write side is added, not split into a second interface, so a backend cannot hold a read
side that disagrees with its write side.

```kotlin
internal interface ArtifactSearchBackend : ArtifactSearchEngine {
    val joinsDatabaseTransaction: Boolean                 // D5
    fun health(): BackendHealth                           // UP, or DOWN with a message; core caches it
    fun apply(actions: List<IndexAction>)                 // ordered, idempotent per action
    fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String>
    fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow>
    fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int
    fun deleteStale(repository: String, before: Long, limit: Int): Int
    fun deleteRepository(repository: String, limit: Int): Int
    fun repositories(): List<String>
    fun count(): Long
    fun sample(cursor: String?, limit: Int): SamplePage   // D7
}
```

`IndexAction`, `StoredRow`, `SampledRow` and `SamplePage` move from `outbox/IndexPorts.kt` and `database/IndexRowStore.kt`
into the backend package. They carry no Exposed types. `ArtifactIndexWriter` is replaced by the interface; its two
methods become `apply` and `pathsAt`.

### D2: what moves into the database backend

| Today | After |
|---|---|
| `DatabaseArtifactSearchEngine` | `DatabaseSearchBackend` (read methods, unchanged) |
| `DatabaseArtifactIndex` | `DatabaseSearchBackend` (`apply`, `pathsAt`) |
| `IndexRowStore` | `DatabaseSearchBackend` (`existing`, `touch`, `deleteStale`, `deleteRepository`, `repositories`, `count`, `sample`) |
| `SearchTables`, `SearchDialect`, `SearchFilters`, `SearchAccessSql` | `database` package, used only by `DatabaseSearchBackend` |
| `SearchSchema` (artifact table part) | the backend's `ensure` step, same marker transaction (D4) |
| `search_outbox`, `search_rebuild_run`, `search_state` tables | stay in the core (`OutboxStore`, `RunStore`, `SearchStateStore`) |

The database backend keeps `SearchDialect`, which knows the vendors, because the core's own tables need the collation too.

### D3: what stays in the core

- Outbox: `OutboxStore`, `OutboxWorker` (claims, leases, batch collapse, backoff, heartbeat, dead letters). The worker
  calls `backend.apply` and `backend.pathsAt` where it called the writer.
- Run ledger, rebuild and verify orchestration, `StartupRules`, `RebuildScheduler`, `VerifyScheduler`, `SearchIndexAdmin`.
- `SearchAccess` and `SearchAccessCompiler`. The backend receives the value and never the subject (`access/SearchAccess.kt`).
- `SearchStateStore`, the heartbeat, `indexStateOf` and the Maintenance status (`IndexStatus`, `SearchIndexResponse`).
- REST routes in `api/SearchApiEndpoints.kt` and their error mapping.

### D4: schema ownership keeps the marker

The marker `schema-1` and its single transaction stay as they are. The database backend's `ensure` creates
`search_artifact` inside the transaction that inserts the marker; the core creates its three tables there too. The
creation order and the DDL are identical, so an installation created by this version is byte for byte the same as one
created by the version before it. `SearchSchemaTest` and `SearchIndexDialectIntegrationTest` keep their assertions.

### D5: transactional and non-transactional writes

Today `RebuildService.writePage` writes rows, the cursor and the counters in one transaction (`RebuildService.kt:220-227`),
so a crash leaves either all or nothing. An external index cannot join that transaction. The seam therefore declares
`joinsDatabaseTransaction`.

- `true` (the database): the core keeps today's single transaction.
- `false` (future external backends): the core applies to the backend first, then commits the cursor and counters. A
  crash in between repeats the page, and the actions are idempotent (D6).

This change implements only the `true` branch in production code. The `false` branch exists in the core behind the flag,
and the fake backend of the tests sets it to `false` to prove the order. Nothing in production takes that branch in this
change.

### D6: idempotency is a contract

Every action must be safe to apply twice: an upsert replaces the row with the same key, a removal of a missing path is a
no-op, a prefix removal is bounded by `notAfter`, and a reorigin rewrites the same values. The contract suite checks
replay for each action. The database already satisfies this (`DatabaseArtifactIndex.kt:65-90`, the key is
`pathHash`).

### D7: sampling uses an opaque cursor

Verify draws a sample by row id today (`VerifyService.kt:164-167`, `IndexRowStore.idRange` and `sample(startId, limit)`).
An external index has no row id, so the seam takes `cursor: String?` and returns the next cursor in `SamplePage`. The
database backend encodes its id as the cursor, so the verify job's behaviour and its random start are unchanged. Verify
tests keep their seeds.

### D8: the count comes from the backend

`StartupRules.decide` takes `rowCount` (`rebuild/RebuildService.kt:95-101`). It gets `backend.count()`. For the database
that is `rows.count()`, unchanged.

### D9: selection through a registry with one entry

`SearchPlugin.wire` asks a registry for `local.searchEngine`. The registry is a map of id to a constructor, and it holds
`database` only. An unknown id returns the same disabled facade as today, with the same warning. The registry is internal
and has no plugin entry point; `add-search-backend-registration` adds one.

### D10: tests

- `ArtifactSearchBackendContract`, an abstract JUnit class in test sources. Each contract case is one behaviour, named after
  it: an upsert replayed leaves one row; a removal of a path removes only that path; a prefix removal keeps rows indexed
  after `notAfter`; a reorigin rewrites rows below one directory only; paging of `pathsAt` is stable; `search`, `facets`
  and `files` never return a row outside `SearchAccess`; `sample` returns rows that exist and the cursor advances.
- Subclasses: `FakeSearchBackendContractTest` (in-memory fake, the reference), `SqliteDatabaseSearchBackendContractTest`
  and `H2DatabaseSearchBackendContractTest` (file databases in `@TempDir`), and in the integration source set
  `MariaDbDatabaseSearchBackendContractTest` and `PostgresDatabaseSearchBackendContractTest` on the shared containers.
- Core unit tests use the fake backend only. No test reads the system clock; `Clock` is injected as today.
- Every test is written and seen to fail before the production code exists.

## Risks

- A move of this size can change the order of an effect. Mitigation: the existing tests run unedited, and the rebuild
  order test (D5) pins the transactional branch.
- An accidental public type leaks into the plugin API. Mitigation: `internal` modifiers, and a test-free review item in
  `tasks.md` that greps for `public` in the new package.

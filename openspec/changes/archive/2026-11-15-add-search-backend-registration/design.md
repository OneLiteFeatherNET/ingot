# Design

Paths are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/` on the current main branch, with the
seam of `extract-search-backend-spi` applied.

## Context

- The plugin wiring happens in `SearchPlugin.initialize`, before every other plugin has initialised. The lifecycle emits
  `ReposiliteInitializeEvent`, then `ReposilitePostInitializeEvent`, then `ReposiliteStartedEvent`
  (`reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt:76-86`). A plugin's listener registered in its
  `initialize` is in place at the post-initialise event, so the search plugin can collect factories there.
- Plugin events and facades are the extension points that exist (`com/reposilite/plugin/ReposilitePluginExtensions.kt:10-14`).
  Factories loaded from plugin jars by `ServiceLoader` would need a change in `PluginLoader.loadPluginsByServiceFiles`
  (`com/reposilite/plugin/PluginLoader.kt:55-77`); the event avoids it.
- Today an unknown `searchEngine` value returns a disabled facade (`application/SearchPlugin.kt:91-94`), and `whenEnabled`
  answers `404` on every route (`api/SearchApiEndpoints.kt:289-297`).
- The outbox is drained by a poller on every replica (`application/SearchPlugin.kt:212`). Claims are exclusive through
  conditional updates (`outbox/OutboxStore.kt:77-91`), so every replica that drains writes to whichever index it has.
- The repository format SPI marks its types with an opt-in annotation and starts experimental (`add-repository-format-spi`,
  design D2). This change uses the same convention.

## Decisions

### D1: the public surface

```kotlin
package net.onelitefeather.ingot.search.backend

@RequiresOptIn(message = "Artifact search backend API is experimental", level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalIngotSearchApi

const val SEARCH_BACKEND_API_VERSION = 1

@ExperimentalIngotSearchApi
interface ArtifactSearchBackendFactory {
    val id: String                       // the value of searchEngine
    val apiVersion: Int                  // must equal SEARCH_BACKEND_API_VERSION
    fun create(context: SearchBackendContext): ArtifactSearchBackend
}

@ExperimentalIngotSearchApi
interface SearchBackendContext {
    val clock: Clock
    val instanceId: String               // this replica
    fun environment(name: String): String?   // INGOT_ name, then REPOSILITE_ fallback
    fun secret(name: String): String?        // reads NAME, or the file named by NAME_FILE
    fun logger(): BackendLogger               // messages are prefixed with SEARCH | and the backend id
}

@ExperimentalIngotSearchApi
interface ArtifactSearchBackend : ArtifactSearchEngine {
    val joinsDatabaseTransaction: Boolean
    fun open()                            // may block; the core runs it off the request threads
    fun close()
    fun health(): BackendHealth
    fun apply(actions: List<IndexAction>) // idempotent per action; throws BackendBusyException for back pressure
    fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String>
    fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow>
    fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int
    fun deleteStale(repository: String, before: Long, limit: Int): Int
    fun deleteRepository(repository: String, limit: Int): Int
    fun repositories(): List<String>
    fun count(): Long
    fun sample(cursor: String?, limit: Int): SamplePage
}

sealed interface BackendHealth { object Up; data class Down(val message: String) }
class BackendBusyException(message: String, val retryAfter: Duration) : RuntimeException(message)
```

The types `IndexAction`, `IndexRow`, `ArtifactFileRecord`, `Origin`, `Provenance`, `StoredRow`, `SamplePage` and `SampledRow`
become public in the same package with the marker. Read types already public (`ArtifactSearchQuery`, `SearchAccess`, the page
types) are unchanged. `SearchBackendContext.secret` reads `NAME_FILE` as the file form of a secret, with the same rules as
`add-file-secrets`; that change may land first, and this change then calls its reader.

### D2: registration through the plugin event

`ArtifactSearchBackendRegistrationEvent` carries `fun register(factory: ArtifactSearchBackendFactory)`. The search plugin emits
it from a listener on `ReposilitePostInitializeEvent`. A plugin registers in `initialize` or `load`. The registry refuses:

- a factory whose `apiVersion` is not `SEARCH_BACKEND_API_VERSION`, with a warning that names the class and both numbers;
- a second factory with an existing id, with a warning that names both classes. The first registered wins, and the database
  factory is registered before any plugin, so a plugin cannot replace `database`.

The built-in `database` factory is always present. The plugin's jar still has to be loaded by the plugin loader, which it is
when it sits in the plugins directory or in the bundled directory with its switch on.

### D3: the wiring moves after plugin initialisation

`SearchPlugin.initialize` still registers the routes, the deploy and delete listeners, and the sink that writes outbox rows. Those
touch only the database outbox, so they do not need a backend. The following move to the post-initialise listener, which runs
after every plugin has initialised:

1. the registry lookup for `searchEngine`,
2. the backend `open()` on the IO pool, with the outcome stored,
3. the outbox poller, the rebuild and verify schedulers.

The engine behind the REST layer becomes a provider: `SearchFacade` returns the current backend or a reason. The routes check it per
request.

### D4: states of a backend

| State | Meaning | Search | Outbox | Index state |
|---|---|---|---|---|
| `AVAILABLE` | opened, health `Up` | served | drained | from the usual inputs |
| `NOT_INSTALLED` | no factory for the id (or its plugin is switched off) | 503 | kept, not drained | `DEGRADED` |
| `REFUSED` | factory of another API version or a duplicate id | 503 | kept, not drained | `DEGRADED` |
| `OPEN_FAILED` | `open()` threw | 503 | kept, not drained | `DEGRADED` |
| `DOWN` | open succeeded, health reports `Down` | 503 | drained with backoff | `DEGRADED` |
| `CLUSTER_MISMATCH` | another backend id is stored | served only if it matches; otherwise 503 | kept, not drained | `DEGRADED` |

`OPEN_FAILED` and `DOWN` are retried: open every 30 seconds, health every 10 seconds (cached in the core). Each transition is
logged once, not per tick. `REFUSED` and `NOT_INSTALLED` are logged at start and every five minutes.

The 503 body is an RFC 9457 problem (`add-problem-details`): the title "Search backend not available", the detail "The search
backend `opensearch` is not installed, its plugin is switched off" or the reason, and `Retry-After: 30`. Administration routes
answer the same 503. Secrets never appear in the detail; the backend's messages pass a redaction step that removes URL user
information and any value read through `secret`.

### D5: selection and cluster agreement

Selection is the local key `searchEngine`, not a shared setting. A shared setting is replicated by the 10-second settings poll,
so one replica could switch backend while another still drains; that is the split the design must prevent. The local key is
read at start, which makes a change a restart, the same as today.

Agreement is kept in the database:

- `search_state` key `BACKEND_ID` holds the backend that owns the index. The first replica that becomes `AVAILABLE` and finds no
  value writes its id with insert-ignore semantics.
- Each replica writes `BACKEND_HEARTBEAT.<instance>` with its id and the time, in the heartbeat cycle of the worker.
- A replica drains only when `BACKEND_ID` equals its own id. Otherwise it is `CLUSTER_MISMATCH` and drains nothing.
- The stored id changes only when no heartbeat of the stored id is younger than three minutes. The replica that sees this
  writes its id, clears `LAST_FULL_REBUILD`, and starts a rebuild with the new trigger `BACKEND`. Until the rebuild completes,
  the state is `BUILDING`.

A three-minute window is longer than the heartbeat interval of one minute, so a replica that is still running cannot be mistaken
for a dead one. The window is a parameter of the rule, injected for tests.

### D6: the access check is repeated in core

After a backend answers, the core checks every returned file (`files`) against the caller's `SearchAccess` with
`SearchAccess.matches` and every returned repository of a group against the repository level of the same value. A failing row is
dropped from the response, logged at error level, and counted in `SearchStateKey.ACCESS_MISMATCHES`. The count is shown on the
Maintenance page. The contract suite requires that the count stays zero for every backend. The check costs one loop over at most
100 items per request.

### D7: busy backends do not burn attempts

`OutboxStore.claim` counts every claim as an attempt (`outbox/OutboxStore.kt:95`). A `BackendBusyException` therefore releases the
claims of the batch with a new `OutboxStore.release`, which sets the lease to `now + retryAfter` and decreases the attempt count by
one, both in one transaction. After ten real failures an entry is still a dead letter, as today. The release happens only for the
batch that failed, so one busy response does not hold back the rest of the queue.

### D8: the Maintenance status

`SearchIndexResponse` gains `backend`: `{ id, state, detail, health, healthDetail, accessMismatches }`. The field is additive. The
state string is one of the states of D4. The existing fields keep their meaning; `engine` reports the configured id.

### D9: build and tests

- `java-test-fixtures` is applied to `reposilite-backend`, and the contract suite moves to `src/testFixtures`. Plugin modules
  depend on it with `testImplementation(testFixtures(project(":reposilite-backend")))`, as `nexus-import-plugin` already does.
- Unit tests with a fixed `Clock` and the fake backend: registration accepted, refused for version and for duplicate id, the
  database factory cannot be replaced, the state of every row of D4, the three-minute rule on both sides of the boundary, the
  release that does not count an attempt, the access check that drops and counts a row.
- Integration tests through the API: 503 with problem details and `Retry-After` for each non-available state; 404 when
  `searchEnabled` is false (unchanged); the Maintenance block; a redacted detail.
- No test sleeps. Retries are driven by explicit calls of the tick functions, as the schedulers already are.

## Risks

- **A plugin that opens slowly.** `open()` blocks an IO thread, not a request thread. A plugin that never returns holds that
  thread. Documented in the plugin how-to; the core cannot interrupt a plugin.
- **Event ordering.** A plugin that registers after the post-initialise event is not seen. Documented; the registry logs the
  ids it has when it starts.
- **The API is new and experimental.** It is marked, and the first release that includes it says so in the upgrade notes.

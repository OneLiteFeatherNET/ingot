# Design

Paths are relative to `reposilite-backend/src/main/kotlin/` unless stated otherwise. Line numbers are from `origin/main` at
`3614657d0`.

## Context

Every way a file enters the search index today is listed here, because the hook must sit where all of them meet.

| Entry | Call | Index write | Source |
| --- | --- | --- | --- |
| Deployment | `RepositoryService` emits `DeployEvent` after `putFile` (`maven/RepositoryService.kt:83`); `DeployIndexListener` (`search/application/ArtifactIndexListeners.kt:33-39`) calls the sink | outbox entry, drained by `OutboxWorker` | `OutboxArtifactIndexSink.kt:34-40` |
| Checksum upload | `RepositoryService.kt:91` indexes directly | outbox, as above | same sink |
| Proxied file | `MirrorService.putMirroredFile` stores the file (`maven/MirrorService.kt:292-296`), then `artifactIndexSink.indexChanged(...)` (`:301`) | outbox | same sink |
| Rebuild | `RebuildService.writeRows` (`search/rebuild/RebuildService.kt:257`) builds rows through `rowOf` (`:268`) and calls `backend.apply` directly | backend, not the outbox | none |

Facts the design relies on:

- **The sink never throws** (`repository/index/ArtifactIndexSink.kt:23`). It only records that a path needs another look.
- **The outbox worker builds the row** in `changedPath` (`search/outbox/OutboxWorker.kt:116`): storage metadata, the first bytes of the
  checksum heads, then `Upsert(row)`. This is the single place where a changed file becomes a row for the deploy and proxy paths.
- **Rebuild skips unchanged files.** `rowOf` keeps the stored checksums only when size and modification time match
  (`RebuildService.kt:268-288`). A new enricher therefore does not see files that were indexed before it was installed, unless the
  change is made explicit. `add-search-document-attributes` decides that rule with a version.
- **Plugin events have no isolation.** `Extensions.emitEvent` calls listeners directly (`plugin/Extensions.kt:59-72`), and the
  comment at `search/application/ArtifactIndexListeners.kt:29-31` says so. An enricher must therefore not be an event listener; it is
  a registered object called by the search plugin, which isolates it.
- **Ranged reads exist.** `StorageProvider.getFile(location, offset, length)` (`storage/StorageProvider.kt:37`) reads a range. A
  bounded stream helper exists (`com/reposilite/shared/stream/BoundedInputStream.kt:30`). A plugin-facing content handle that
  combines the two does not exist.
- **Registration pattern.** The search plugin emits `ArtifactSearchBackendRegistrationEvent` after every plugin has initialised
  (`search/application/SearchPlugin.kt:148`). A plugin registers from its `initialize`. Enrichers use the same pattern.
- **Experimental marker.** `@ExperimentalIngotSearchApi` (`search/backend/SearchBackendApi.kt:32`) is the opt-in convention for search
  SPI types. It is reused, not invented.

## Goals and non-goals

Goals: one hook that is called on all four entries with the same contract; no work and no storage read when no enricher matches;
failure of an enricher can never change what a client sees; a plugin can read the bytes it needs and nothing more.

Non-goals: writing attributes (the next change); enrichment of the request path; a plugin-owned database; a new configuration key;
changing a backend.

## Decisions

### D1: the enricher contract

```kotlin
package net.onelitefeather.ingot.search.enrichment

@ExperimentalIngotSearchApi
interface ArtifactEnricher {
    val id: String                       // namespace, [a-z0-9][a-z0-9-]{1,31}, unique
    val apiVersion: Int                  // must equal ARTIFACT_ENRICHER_API_VERSION (1)
    val version: String                  // changes when the output of the enricher changes
    val extensions: Set<String>          // lower case, for example setOf("jar")
    fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult
}

@ExperimentalIngotSearchApi
class ArtifactEnrichmentRequest internal constructor(
    val repository: String,
    val path: String,
    val file: ArtifactFileRecord,        // existing type, coordinates
    val provenance: Provenance,          // HOSTED or MIRROR, existing type
    val sizeBytes: Long,
    val lastModified: Long,
    val sha1: String?,
    val sha256: String?,
    val trigger: EnrichmentTrigger,
    val content: ArtifactContent,
)

enum class EnrichmentTrigger { CHANGED, REBUILD }

interface ArtifactContent {
    val sizeBytes: Long
    fun range(offset: Long, length: Long): InputStream   // throws if the budget is exhausted
}

sealed interface EnrichmentResult {
    data object Completed : EnrichmentResult
    data class Skipped(val reason: String) : EnrichmentResult
    data class Failed(val reason: String) : EnrichmentResult
}
```

`ArtifactFileRecord` and `Provenance` are the types the index already uses (`search/ArtifactFileRecord.kt:35`, `search/IndexModel.kt`).
The `reason` strings are short, bounded to 200 characters and stripped of control characters by the core. The request class is
built by the core only.

`ArtifactEnrichmentRequest` has an `internal` constructor so that the content handle can only be created by the core. The result type
is sealed, so `add-search-document-attributes` can add a variant without a new interface.

### D2: registration through the event, with the backend rules

The search plugin emits `ArtifactEnricherRegistrationEvent` after the plugin initialisation, carrying a registry. A plugin registers
in its `initialize`. The registry refuses:

- an `apiVersion` other than `ARTIFACT_ENRICHER_API_VERSION`, with a warning naming the version and the class;
- a duplicate `id`, with a warning naming both classes;
- an `id` that does not match the pattern.

A refused enricher is never called. The registry is read once per index pipeline start; enrichers registered later take effect on the
next start, as backends do. The event is not a listener of the enricher's work, so a failing enricher cannot break the event.

### D3: three invocation points, one contract

1. **Changed paths.** `OutboxWorker.changedPath` (`search/outbox/OutboxWorker.kt:116`) calls the enrichers matching the extension of
   the row after the `IndexRow` is built and before `Upsert(row)`. This covers deployments and proxied files, because both are
   enqueued through `OutboxArtifactIndexSink` and the mirror path (`MirrorService.kt:301`) uses the same sink. The trigger is
   `CHANGED`, and the provenance tells `HOSTED` from `MIRROR`.
2. **Rebuild.** `RebuildService.writeRows` calls the enrichers for each writable row whose `rowOf` result is not `unchanged`
   (`RebuildService.kt:268-288`), before `backend.apply`. The trigger is `REBUILD`. Unchanged rows are not enriched in this change.
3. **Removal.** Not an entry. Nothing is called for removed paths.

Held rows (`pending` in the rebuild, `RebuildService.kt:257-262`) are not enriched; they are touched and wait for the outbox.

### D4: the content handle and its budget

`ArtifactContent` reads through `StorageProvider.getFile(location, offset, length)`. The core creates the handle per call with:

- a byte budget per call (default 16 MiB, counted over every range read of that call; a read beyond it throws);
- a deadline per call (default 5 seconds, applied by the executor of D5);
- no write access, and no way to name another path than the one of the call.

The defaults are constants in this change. A configuration key would be a new public surface, so it is deferred (Q2). The budget counts
bytes read from storage, not bytes inflated, so a compressed jar is charged for its compressed size.

Access rights are not re-checked for the enricher. It reads the stored bytes as the system, the way the index itself does. Its output is
therefore data that a viewer may see through the search layer only after the access check of that layer. This is a deliberate trust
decision and is restated in the spec.

### D5: execution, deadline and isolation

Each enricher runs on a single executor owned by the search plugin with a bounded queue (default 64 tasks) and one thread for the whole
pipeline. The worker submits the call and waits at most the deadline. Then:

- a normal return sets the result;
- an exception becomes `Failed("exception: <class simple name>")`, never the message, because messages can carry paths or content;
- a deadline expiry cancels the task with interruption and becomes `Failed("timeout")`;
- a full queue becomes `Skipped("busy")` without calling the enricher.

In every case the row is still upserted, without enrichment output. The worker never waits longer than the deadline. The deadline is the
only wall-clock wait; in tests it is replaced by a fake executor that completes, times out or rejects on command, so no test waits.
Exceptions of type `Error` are not caught, because catching them would hide a failing JVM.

Only one call per enricher runs at a time. Enrichers are therefore not required to be thread safe, but the registry documents that they
may hold no state that outlives a call.

### D6: failure rules

A failed call never fails the deployment (which has already succeeded), never fails the proxied download (which has already been
served), never fails the rebuild page (the rebuild counts the failure and continues), and never blocks the outbox for longer than one
deadline per entry. Each failure is logged once as a warning with the enricher id, repository and path, and counted. The warning text
does not contain content.

### D7: status

The existing index status response (`GET /api/maven/search/index`, see `add-search-backend-registration`) gains an additive block
`enrichers`, one entry per registered enricher id with the counters `calls`, `completed`, `skipped`, `failed` and `busy`, and the
`failed` reasons as counts per reason. No field of the response changes.

### D8: cluster

Every replica that drains the outbox runs the enrichers it has registered. Replicas with different enricher sets therefore write
different rows for the same file. The change records this and does not solve it: the next change stores the enricher version in
each row, and a rebuild or a version mismatch can detect it (Q3). Operators install the same plugin jars on every replica, as they do
for any plugin.

## Plugin API gaps closed and not closed

| Gap | Closed here | Note |
| --- | --- | --- |
| No hook on index, reindex or proxy cache with content | yes | D3 |
| Bounded content handle for plugins | yes | D4 |
| Attributes written from the hook | no | `add-search-document-attributes` |
| Enrichment for files indexed before the plugin was installed | partly | rebuild of changed rows only (D3); full re-enrichment by version in the next change |

## Alternatives considered

- **An event per file (`ArtifactIndexedEvent`).** Rejected: plugin events have no isolation (`plugin/Extensions.kt:59-72`), and an event
  would carry no content handle, so the plugin would call storage itself with no budget.
- **Enrichment on `ResolvedFileDataEvent`.** Rejected: it fires on reads, not on index writes, and not on rebuild.
- **Enrichment inside the backend SPI.** Rejected: every backend would have to implement it, and the Lucene, Elasticsearch, OpenSearch,
  Meilisearch and database backends would each repeat the isolation rules.
- **Enrichment in a plugin-owned background job scanning storage.** Rejected: a second scheduler and a second source of truth, and it
  misses the event time of a proxied file.

## Risks

- **A slow enricher slows indexing.** Mitigation: the deadline per call, a bounded queue and the `busy` skip. Throughput per enricher is
  one file per deadline at worst.
- **Budget too small for some jars.** Mitigation: the default is a constant that the first code pull request makes testable; Q2 decides
  whether it becomes a key.
- **Rebuild gap.** Files indexed before the plugin existed stay unenriched until they change. Mitigation: explicit in D3, closed by the
  next change.

## Open questions (each has a default)

- **Q1. Where the executor lives.** Default: one executor owned by the search plugin, shared by all enrichers.
- **Q2. Configuration of the budget and the deadline.** Default: constants. If an operator needs to change them, the key takes the
  `INGOT_SEARCH_ENRICHMENT_` prefix with the `REPOSILITE_` spelling as fallback, and it is added in a separate change.
- **Q3. Version mismatch across replicas.** Default: the next change stores the enricher version and re-enriches on mismatch.
- **Q4. Enrichment of unchanged files on rebuild.** Default: no in this change. Full re-enrichment needs the version of Q3.

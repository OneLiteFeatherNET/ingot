---
description: The artifact enrichment hook of the plugin API. Covers the enricher contract, the registration event, the two points where the core calls an enricher, the byte budget, the deadline, the failure rules, the rebuild gap and the counters in the index status.
---

# Artifact enrichment

An enricher is a plugin hook on the search index. The core calls it for a stored artifact file while it indexes that file, and hands it a read-only view of the stored bytes. A plugin uses it to read something out of a file, for example the descriptor inside a plugin jar, without walking the storage itself.

The hook is experimental. Its types carry `@ExperimentalIngotSearchApi`, the same opt-in marker as the [search backend API](../../manage/plugins/write-a-search-backend-plugin.md). Code that uses them needs `@OptIn(ExperimentalIngotSearchApi::class)`, or the compiler warns, and the members may change before the API is declared stable. The types are listed in [plugin-api-classes.txt](https://github.com/OneLiteFeatherNET/ingot/blob/main/reposilite-backend/plugin-api-classes.txt), so a plugin can compile against the slim plugin API artifact alone, the way the [consumer fixture](https://github.com/OneLiteFeatherNET/ingot/tree/main/reposilite-test/plugin-api-consumer) does. See [Plugin API](plugin-api.md) for the coordinates.

## The enricher

An enricher implements `ArtifactEnricher` from `net.onelitefeather.ingot.search.enrichment`:

| Member | Meaning |
| --- | --- |
| `id` | The namespace of the enricher. Lower case letters, digits and hyphens, 2 to 32 characters, starting with a letter or a digit. Unique on the server. |
| `apiVersion` | The version of the enricher API the enricher was built for. It must equal `ARTIFACT_ENRICHER_API_VERSION` of the server. In this release that constant is `1`. |
| `version` | The version of the enricher's output. Change it when the output changes. |
| `extensions` | The lower case file extensions the enricher is called for, for example `jar`. |
| `enrich(artifact)` | Enriches one file and returns an `EnrichmentResult`. |

The request carries the repository and the path, the parsed coordinates (`file`), the origin (`HOSTED` or `MIRROR`) with the source host of a mirrored file, the size and modification time, the stored SHA-1 and SHA-256 when they exist, the trigger, and the content handle.

`enrich` returns one of:

* `EnrichmentCompleted`, when the enricher finished.
* `EnrichmentSkipped(reason)`, when the enricher chose not to handle this file.
* `EnrichmentFailed(reason)`, when it could not handle the file.

A reason is short and never carries file content or a path, because it ends up in the log and in the index status.

The enricher must not keep state that outlives one call. Only one call runs at a time, so it does not have to be thread safe.

## The registration event

The search plugin emits `ArtifactEnricherRegistrationEvent` after every plugin has initialised. Register the listener in `initialize`:

```kotlin
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.enrichment.ARTIFACT_ENRICHER_API_VERSION
import net.onelitefeather.ingot.search.enrichment.ArtifactEnricher
import net.onelitefeather.ingot.search.enrichment.ArtifactEnricherRegistrationEvent
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentRequest
import net.onelitefeather.ingot.search.enrichment.EnrichmentCompleted
import net.onelitefeather.ingot.search.enrichment.EnrichmentResult
import net.onelitefeather.ingot.search.enrichment.EnrichmentSkipped

@OptIn(ExperimentalIngotSearchApi::class)
class JarHeaderEnricher : ArtifactEnricher {

    override val id = "jar-header"
    override val apiVersion = ARTIFACT_ENRICHER_API_VERSION
    override val version = "1"
    override val extensions = setOf("jar")

    override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult {
        val header = artifact.content.range(offset = 0, length = 4).use { it.readBytes() }
        return if (header.size == 4) EnrichmentCompleted else EnrichmentSkipped("too short")
    }

}

@Plugin(name = "jar-header")
class JarHeaderPlugin : ReposilitePlugin() {

    @OptIn(ExperimentalIngotSearchApi::class)
    override fun initialize(): Facade? {
        event { event: ArtifactEnricherRegistrationEvent ->
            event.register(JarHeaderEnricher())
        }
        return null
    }

}
```

The registry refuses an enricher, with a warning in the log, when:

* its `apiVersion` is another one. The warning names the class and both versions.
* its `id` does not match the pattern above.
* its `id` is already registered. The warning names both classes, and the first enricher keeps the id.

A refused enricher is never called. The registry is read once when the index pipeline starts, so an enricher registered later takes effect on the next start.

## Where the core calls an enricher

The core calls the enrichers whose `extensions` contain the extension of the file, at two points:

1. **Changed paths.** When the outbox worker indexes a new or changed file, after it has built the index row and before it writes it. This covers deployments and files a mirror repository caches from its upstream. The trigger is `CHANGED`, and the origin tells a hosted file from a mirrored one.
2. **Rebuild.** When a rebuild writes a row that is new or whose size or modification time changed. The trigger is `REBUILD`. A row that is unchanged is not enriched, and neither is a row the rebuild holds back for the outbox.

Nothing is called when a file is removed. A file that no enricher matches is not read at all.

## The byte budget and the deadline

The content handle reads through the storage of the server, limited to the file of the call. It has no write access and no way to name another path.

* **Byte budget:** 16 MiB per call, counted over every `range` read of that call. A read beyond it throws. The budget counts bytes read from storage, so a compressed jar is charged for its compressed size.
* **Deadline:** 5 seconds per call. When it passes, the call is cancelled with an interrupt and counts as failed with the reason `timeout`.
* **Queue:** all enrichers share one thread with a queue of 64 calls. When the queue is full, the call is skipped with the reason `busy` and the enricher is not called.

These limits are constants in this release and cannot be configured.

The enricher reads the stored bytes as the system, the way the index does, so it also reads files of private repositories. Its output reaches a viewer only through the access checks of the search layer.

## Failure rules

A failing enricher never fails anything else:

* An exception thrown by `enrich` becomes `EnrichmentFailed` with the reason `exception: <class simple name>`. The message of the exception is never used, because it can carry paths or content. An `Error` is not caught.
* The row of the file is written in every case, with or without the output of the enricher. A deployment or a proxied download has already succeeded and is not affected.
* A rebuild counts the failure and continues with the next row.
* The outbox waits at most one deadline per entry.

Each failure is logged once as a warning with the enricher id, the repository and the path.

## The rebuild gap

Files that were indexed before the enricher was installed are not enriched until they change, because a rebuild only enriches rows that are new or changed. Re-enriching every file when the version of an enricher changes is planned for a later release.

In a cluster, every replica that drains the outbox runs the enrichers it has registered. Install the same plugin jars on every replica.

## Counters in the index status

The index status of the Maintenance area (`GET /api/maven/search/index`) carries an `enrichers` block with one entry per registered enricher id. Each entry has the counters `calls`, `completed`, `skipped`, `failed` and `busy` since the replica started, and `failures` with the number of failures per reason. `skipped` includes the `busy` skips. The first 16 distinct failure reasons are counted on their own, later ones under `other`. Without a registered enricher the block is empty, and no other field of the response changes.

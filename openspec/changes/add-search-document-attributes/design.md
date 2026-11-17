# Design

Paths are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/` unless stated otherwise. Line numbers are
from `origin/main` at `3614657d0`. The enrichment types and invocation points are those of `add-artifact-enrichment-hook`.

## Context

- **The row is fixed.** `IndexRow` holds `file`, `provenance`, `sizeBytes`, `lastModified`, `sha1`, `sha256` and `indexedAt`
  (`IndexModel.kt:33`). `ArtifactFileRecord` holds `repository, path, groupId, artifactId, version, classifier, extension, snapshot`
  (`ArtifactFileRecord.kt:35`).
- **The SPI moves rows by action.** `ArtifactSearchBackend` (`backend/ArtifactSearchBackend.kt:34`) has `apply(actions)` (`:74`),
  `existing(repository, paths): Map<String, StoredRow>` (`:80`), `touch` (`:83`), `deleteStale` (`:86`), `deleteRepository` (`:89`),
  and `IndexAction` with `Upsert`, `RemovePath`, `RemovePrefix` and `Reorigin` (`:127`). `StoredRow` (`:142`) carries the checksums and
  sizes that the rebuild compares.
- **Who writes rows.** The outbox worker builds the row for deployments and proxied files (`search/outbox/OutboxWorker.kt:116`). The
  rebuild builds it in `rowOf` (`search/rebuild/RebuildService.kt:268`) and skips the enrichment of rows that did not change
  (`:268-288`). Both are the invocation points of the hook.
- **Storage per backend.**
  - Database: fixed table `search_artifact` with lower-cased columns and a `gav_key` (`search/database/SearchTables.kt:52-73`).
  - Elasticsearch: explicit mapping of fixed fields (`elasticsearch-plugin/.../ElasticsearchIndexDefinition.kt:44-65`), document from the
    row (`ElasticsearchDocument.kt:40`).
  - OpenSearch: the same shape (`opensearch-plugin/.../OpenSearchIndexDefinition.kt`, `OpenSearchDocument.kt:40`).
  - Meilisearch: explicit settings with filterable, sortable and searchable lists (`meilisearch-plugin/.../MeilisearchIndexSettings.kt:28`),
    document from the row (`MeilisearchDocument.kt:41`).
  - Lucene: open pull requests #431 to #449, the module exists only as a stub on `main` in the open pull request #431.
- **No capability declaration.** A grep of `search/` for capability or support declarations returns nothing. A backend cannot say it
  cannot store something.
- **Bounds already used.** The Minecraft portal design bounds descriptions to 2000 characters and strips control and bidirectional
  override characters (`openspec/changes/add-minecraft-plugin-portal-plugin/design.md`, D3 and D6). This change takes those bounds
  and enforces them in the core, so that every plugin gets them.

## Goals and non-goals

Goals: a namespaced, bounded attribute map per row; write rules that never leave stale plugin output on a row; a rebuild that picks up a
new enricher version; a backend that cannot store attributes is visible and does not lose other data.

Non-goals: filtering, faceting, sorting, text search over values; numeric or nested attributes; attributes for a viewer without a read
right to the file (the search access layer already filters rows and the attributes travel with them).

## Decisions

### D1: keys, namespaces and values

- An attribute key is `<enricher-id>.<name>`. The enricher id matches `[a-z0-9-]{2,32}`. The name matches `[a-z][a-z0-9_]{0,31}`.
- The namespace `core` is reserved. An enricher whose id is `core` is refused by the registry (`add-artifact-enrichment-hook`, D2).
- The core writes one attribute per enricher that ran: `core.enricher.<enricher-id>` with the enricher `version`.
  This is the only key the core writes, and it is what the rebuild rule of D5 reads.
- A row carries at most 32 keys, at most 16 values per key, at most 2000 characters per value and at most 8 KiB of key and value text
  together. These limits are constants. The core refuses an attribute set that exceeds them, and does not truncate it.
- A value may not contain a control character or one of the bidirectional override and isolate characters (U+202A to U+202E and
  U+2066 to U+2069). These are the spoofing vectors listed in the Minecraft design (D6), so the core rejects them instead of cleaning
  them. Values are case preserved; the database backend keeps a lower-cased copy for later filters.
- Values are strings. Numbers and booleans are written as strings by the enricher.

A refused attribute set counts as `Failed("invalid attributes: <rule>")`, where the rule is one of this fixed list: `too-many-keys`, `too-many-values`,
`value-too-long`, `set-too-large`, `control-character`, `foreign-namespace` and `invalid-key`. The message never contains a value.

### D2: the enrichment result

`EnrichmentResult` gains:

```kotlin
data class Attributes(val values: Map<String, List<String>>) : EnrichmentResult
```

The keys must start with the enricher's own id. A key of another namespace is refused with `foreign-namespace`. An empty map is
`Completed`. Because `EnrichmentResult` is sealed and experimental, the variant is an addition; a `when` over the result in a plugin
that does not handle `Attributes` fails to compile, which is the intended signal and is recorded in the impact section.

### D3: capability declaration

`ArtifactSearchBackend` gains a default member:

```kotlin
val capabilities: Set<SearchCapability> get() = emptySet()
```

`SearchCapability` is an enum in `backend`, experimental, with the values `ATTRIBUTES` (this change), `ATTRIBUTE_FILTERS`,
`ATTRIBUTE_FACETS` and `ATTRIBUTE_SORT` (the next change). The default is empty, so an existing backend keeps working and stores no
attributes. The database backend, which lives in the core, declares `ATTRIBUTES`. A plugin backend declares it only when its own module
writes the attributes, in the same pull request.

When a backend lacks `ATTRIBUTES`, the core drops the attribute set at the boundary, counts the enrichment as
`Skipped("unsupported-backend")`, and the row is written without attributes. The status block (`add-artifact-enrichment-hook`, D7) shows
the count, so a portal can say why it is empty. Nothing fails.

### D4: write rules

| Event | Row attributes after the event |
| --- | --- |
| Changed file (`CHANGED`), every matching enricher completed | replaced by the union of the results of this call, plus `core.enricher.<id>` for each enricher that ran |
| Changed file, an enricher failed or was skipped | the keys of that enricher are removed; its `core.enricher.<id>` is removed; other enrichers keep their results |
| Touch of an unchanged or held row | kept as stored |
| `Reorigin` (provenance change of a directory) | kept as stored |
| `RemovePath`, `RemovePrefix`, `deleteStale`, `deleteRepository` | the row and its attributes are removed together |
| Rebuild, row changed | as "Changed file" |
| Rebuild, row unchanged and every matching enricher has the stored version | kept as stored |
| Rebuild, row unchanged and a matching enricher has no or another stored version | enricher runs with trigger `REBUILD` (D5) |

A failed enricher removes its own output, so a stale result from an earlier version of a jar cannot survive a failure.

### D5: the rebuild rule by enricher version

`rowOf` (`RebuildService.kt:268-288`) currently treats a row as unchanged when size and modification time match and keeps the checksums.
This change adds one condition: a row is also enriched when a matching enricher's version differs from `core.enricher.<id>` stored for
it, or when no such key exists. The stored versions are read with `existing`, which already returns each stored row, extended with its
attributes.

This is the only place where an unchanged file is enriched. The rebuild of a large repository therefore enriches only the rows of a new
or upgraded enricher, not every file.

### D6: storage per backend

- **Database.** A child table `search_attribute` with columns `repository`, `path`, `attr_key`, `attr_value`, `attr_value_lc`, a primary
  key over the first four columns and an index on `(attr_key, attr_value_lc)` for the next change. Every write of a row replaces its
  child rows in the same transaction. Deletion of a row deletes its child rows in the same transaction, in every delete path that
  `SearchTables.kt` already has. The table is created at start by the same schema setup that creates the existing tables. Rows that
  exist before the change have no children until they are enriched.
- **Elasticsearch and OpenSearch.** The document gets an `attributes` object. An explicit dynamic template maps `attributes.*` to
  `keyword`, so the keys do not need a mapping update per enricher. The core limits the number of distinct keys in an index to 256
  (counted from the stored `core.enricher.*` and attribute keys it writes). The change is a mapping addition; no reindex is required.
- **Meilisearch.** The document carries `attributes` as a JSON object. Filterable settings are not set in this change, so the field is
  stored and ignored by queries until the next change.
- **Lucene.** Not declared in this change. The Lucene pull requests (#431 to #449) add a mapping version; the attribute field lands in
  that mapping, and the capability is declared in the same pull request (Q2).

### D7: response

The search response (file results of the existing search API in `api/SearchApiEndpoints.kt`) includes an `attributes` object per file
only when the request carries `attributes=true`. Without the parameter the response is unchanged. With it, only the attributes of rows
that the caller may read appear, because the rows are filtered before they are returned (the existing access layer, `search/access/`).

### D8: operations

No configuration key is added. The distinct-key limit of 256 is a constant. The database table is the only schema change; it is
additive, so a Reposilite data directory is adopted as before and a downgrade to a build without the table ignores it.

### D9: upgrade of existing installations

After the change, rows indexed before it have no attributes. The rebuild rule (D5) fills them on the next rebuild, or when the enricher
version changes. The change does not start a rebuild by itself (Q5).

## Plugin API gaps closed and not closed

| Gap | Closed here | Note |
| --- | --- | --- |
| Index rows have no attributes | yes | D1, D6 |
| No per-project document | no | a project is still grouped by the portal from file rows |
| No declared capabilities of a backend | yes | D3 |
| No filter, facet or sort on attributes | no | `add-search-attribute-facets` |
| Full text over descriptions | no | open question for the facets change |

## Alternatives considered

- **Attributes in a separate plugin-owned table.** Rejected: a second source of truth that the delete paths do not know about, and the
  access layer cannot filter it.
- **A JSON column per row.** Rejected for the database backend: it prevents the lookups the facets change needs. Elasticsearch and
  OpenSearch would store the same JSON as one unindexed field.
- **Unbounded attributes.** Rejected: a row is read by every search, and the bounds are the only protection against a hostile enricher.

## Risks

- **Index growth.** Mitigation: the bounds of D1, and attributes returned only on request (D7).
- **Stale output across versions.** Mitigation: D4 and D5.
- **A backend declares the capability and then loses attributes.** Mitigation: the contract suite of `search-backend-registration`
  includes attribute cases and runs for every declared backend.

## Open questions (settled 2026-11-17 with the recommended default)

- **Q1. Response parameter.** Settled 2026-11-17: `attributes=true` opts in (D7). Attributes in every file result are rejected, so the
  default response does not change.
- **Q2. Lucene.** Settled 2026-11-17: Lucene declares `ATTRIBUTES` in the pull request that adds the attribute field to its mapping version, owned
  by the Lucene pull request author. Not a blocker for this change.
- **Q3. Limits.** Settled 2026-11-17: 32 keys, 16 values per key, 2000 characters per value, 8 KiB per row, 256 distinct keys per index.
- **Q4. Rebuild by version.** Settled 2026-11-17: yes (D5). An explicit operator rebuild only is rejected.
- **Q5. Automatic run on a new enricher version.** Settled 2026-11-17: no automatic rebuild. An operator starts it, or the next
  scheduled rebuild picks rows up. A run triggered by enricher changes is not part of this change.

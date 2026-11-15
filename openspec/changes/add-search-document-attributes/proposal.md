# Proposal

## Why

The index stores one row per file with fixed fields: coordinates, provenance, size, modification time and two checksums
(`net/onelitefeather/ingot/search/IndexModel.kt:33`). A plugin that enriches a file (`add-artifact-enrichment-hook`) can compute
"this jar is a Paper plugin for 1.21" or "this POM is the marker of a Gradle plugin", but the index has no place to keep it. The
backend SPI has no attribute on a row (`backend/ArtifactSearchBackend.kt`, `IndexAction.Upsert(row: IndexRow)` and `StoredRow`), and
the Elasticsearch, OpenSearch and Meilisearch mappings are fixed (`ElasticsearchIndexDefinition.kt:44-65`,
`MeilisearchIndexSettings.kt:28`). The database table is fixed too (`search/database/SearchTables.kt:52-73`).

Both portal plugins need the result to be stored next to the file, so that a listing can be answered from the index and not by reading
every jar again on each page view. Without attributes the portal plugins would keep their own store, which the portal proposals rule
out as a second system of record.

This change adds a namespaced attribute set per row, written by enrichers, stored by every backend that declares it, and kept correct
when files are touched, moved or deleted. It does not add filters, facets or sorts. Those are `add-search-attribute-facets`.

## What Changes

- An index row carries an attribute map: keys are namespaced by the enricher that wrote them (`<enricher-id>.<name>`), values are lists
  of strings. The namespace `core` is reserved and used for the version of each enricher that ran (design D1).
- `EnrichmentResult` gains an `Attributes` variant (additive, sealed). An enricher writes its attributes through it; the core checks
  the keys, the number of values and the length and rejects control characters and bidirectional overrides.
- The write rules are fixed: a changed file replaces the attributes of the enrichers that ran; a failed or skipped enricher removes its
  stale attributes; a touched, moved or reorigined row keeps them; a deleted row loses them with the row (design D4).
- A rebuild re-enriches a row whose stored enricher version differs from the registered one, even when the file did not change. This
  closes the gap of `add-artifact-enrichment-hook` for files indexed before a plugin was installed or upgraded (design D5).
- The backend SPI declares which capabilities a backend supports (`SearchCapability.ATTRIBUTES` in this change). Attributes are dropped
  at the core boundary for a backend that does not declare the capability, and that is counted.
- Storage in the database backend (a child table written in the same transaction as the row), in Elasticsearch and OpenSearch (keyword
  fields under `attributes.*` through a dynamic template), and in Meilisearch (a document object). Lucene declares the capability only
  after its mapping version is in place (open question Q2).
- The search response includes attributes only when the caller asks for them with `attributes=true`.

### Out of scope

- Filtering, faceting and sorting on attributes, and declaring which keys are filterable (`add-search-attribute-facets`).
- Full text search over attribute values.
- Attributes that are numbers, booleans or nested objects. Every value is a string.
- Any change to what a deployment or a download returns.
- Attributes on directories, or attributes that are not per file.

## Capabilities

### New Capabilities

- `search-document-attributes`: the attribute map of a row, the namespace rules, the value limits, the write rules, the rebuild rule by
  enricher version, the backend capability, the storage per backend and the response parameter. It also adds the `Attributes` variant
  of `EnrichmentResult` that `add-artifact-enrichment-hook` made possible. The variant is recorded here as an addition, not as a change of
  the `artifact-enrichment` capability, which stays as proposed in its own change.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)` (none
  expected) and `docs(search)`, one type each. The pull request titles are:

  `feat(search): store plugin attributes with indexed artifacts` (the model, the database backend and the rebuild rule)

  `feat(search): store plugin attributes in elasticsearch and opensearch` and
  `feat(search): store plugin attributes in meilisearch` (one pull request per plugin module)

  `docs(search): describe plugin attributes and the response parameter` (the reference page)

- **Breaking marker.** No breaking change for operators. The database gains one table, created at start. The table is empty until an
  enricher writes to it. No configuration key, environment variable, volume, manifest or image has to change. The Elasticsearch and
  OpenSearch mappings gain a dynamic template and do not need a reindex. Meilisearch stores the new field without a settings change in
  this change (filterable settings come with `add-search-attribute-facets`).
- **Binary surface.** `IndexAction.Upsert`, `StoredRow` and `EnrichmentResult` gain members with defaults in public experimental types.
  Source compatible for Kotlin callers; binary compatible only after a rebuild of plugins. Because the types are experimental the change
  records this in the proposal and the ABI report, and does not treat it as a break.
- **Order.** Depends on `add-artifact-enrichment-hook`, which defines the result type, the registry and the invocation points. It is a
  prerequisite of `add-search-attribute-facets`. It does not depend on `expose-plugin-access-checks`.

## Verification

- Unit tests of the validation rules, the write rules and the rebuild rule with fake backends and a fake clock.
- Contract tests of the search SPI (the abstract suite in `src/testFixtures`) extended with attribute cases, run for the database backend
  and for each plugin backend that declares the capability.
- Integration tests: deploy, proxy, rebuild, touch, move and delete of a row with attributes, each leaving the expected attributes.
- `openspec validate add-search-document-attributes --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-search-document-attributes` prints nothing.

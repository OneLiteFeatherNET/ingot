# Design

Paths are relative to `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/` unless stated otherwise. Line numbers are from
`origin/main` at `3614657d0`. Attributes, their storage and the capabilities are those of `add-search-document-attributes`.

## Context

- **The query is fixed.** `ArtifactSearchQuery` (`ArtifactSearchEngine.kt:47`) has `q` (`:49`), a lower-cased group prefix (`:51`), an
  artifact prefix (`:53`), exact extensions as an OR list (`:61`), and repositories, classifiers, sha1, snapshot, origin, time and size
  bounds, sort and paging. None of them reads an attribute.
- **The facets are fixed.** `SearchFacets` holds repository, extension, classifier, origin and snapshot (`ArtifactSearchEngine.kt:132`),
  each a list of `FacetBucket(value, count)` (`:141`).
- **The facet rule exists.** The database engine computes each facet with the filter of its own kind left out and the access condition
  added (`database/DatabaseArtifactSearchEngine.kt:119-128`). Buckets are `GROUP BY` counts, ordered by count descending then value
  ascending, capped at `FACET_BUCKETS` (`:356`, 20).
- **The access layer is outside the engine.** `AccessCheckedSearchEngine` (`access/`) applies the access value again to the results, and
  the backends apply it in the query (`ElasticsearchQueries.kt:396` `accessClause`; `OpenSearchQueries.kt:396`; `MeilisearchFilters.kt:47`
  `access(access)`).
- **Backends.** Elasticsearch facets are terms aggregations (`elasticsearch-plugin/.../query/ElasticsearchQueries.kt:82`). OpenSearch is the
  same shape. Meilisearch has explicit filterable, sortable and searchable lists (`meilisearch-plugin/.../MeilisearchIndexSettings.kt:28`)
  and a facet path guarded by the settings state (`MeilisearchSearchBackend.kt:222`). A setting change is an asynchronous task on the
  Meilisearch side. Lucene is in open pull requests (#431 to #449); #447 answers facets from the Lucene index.
- **The portal side.** The Minecraft design asks for counts per loader and per game version beside a filter bar, computed after the access
  filter, and never with a count that includes an unreadable row (`openspec/changes/add-minecraft-plugin-portal-plugin/design.md`, D5).

## Goals and non-goals

Goals: filter, facet and sort on declared attributes; counts that are correct under multi-select and under access; refusals that name the
cause; no change for requests that do not use the new parameters.

Non-goals: text search over values, range or version comparison, project counts, new facets on the fixed fields.

## Decisions

### D1: declarations

`ArtifactEnricher` gains:

```kotlin
val declaredAttributes: List<AttributeDeclaration> get() = emptyList()

data class AttributeDeclaration(
    val key: String,          // must be in the enricher's namespace, as in add-search-document-attributes
    val filterable: Boolean,
    val facetable: Boolean,
    val sortable: Boolean,
)
```

The registry (`add-artifact-enrichment-hook`, D2) refuses a declaration whose key is outside the enricher's namespace or that duplicates a
key of another enricher, with a warning that names both classes. The declarations form the registry's attribute catalogue. A query may use
a key only if it is declared with the matching flag. Undeclared keys are refused with `400` and the problem type `undeclared-attribute`,
naming the key.

The declaration is the contract for backends: Meilisearch derives its filterable and sortable settings from it (D5), and the database
backend does not need one (it indexes every stored value, see D4).

### D2: the query and the response

Request parameters on the existing search API (`api/SearchApiEndpoints.kt`), all optional and repeatable:

- `attribute=<key>:<value>`: a filter. The same key given several times is an OR over its values, and different keys are ANDed. At most
  4 distinct keys and 16 values per key.
- `attributeSort=<key>:asc|desc`: sorts by one sortable key. Rows without the value sort last. Ties are broken by the existing order of
  groups and versions (`GroupOrder.kt`, `ArtifactVersions.kt`), so pages are stable.
- `attributeFacets=<key>,<key>`: the keys whose buckets are returned, at most 8 keys.

The model types are `AttributeFilter(key, values)`, `AttributeSort(key, descending)` and a set of facet keys on `ArtifactSearchQuery`. The
response gains `facets.attributes: { <key>: [ { value, count } ] }`, present only when `attributeFacets` is given, with at most 20 buckets
per key (`FACET_BUCKETS`). Nothing else in the response changes.

### D3: the counting rule

For each facet key the counts are taken over the rows that match every attribute filter of the query except the filters on that same
key, and over the rows the viewer may read. This is the rule of `DatabaseArtifactSearchEngine.kt:119-128` applied to attributes. A selected
value therefore keeps showing its alternatives, and a count never includes a row the viewer cannot read, because the access condition is
part of every count, not a separate step.

The counts are file counts. A file with two values for one key is counted once per value it has, so a facet of a multi-valued key can add
up to more than the number of files. The portal states this (Q5).

### D4: database backend

- A filter on key `k` with values `v1..vn` is `EXISTS (SELECT 1 FROM search_attribute a WHERE a.repository = r.repository AND a.path = r.path
  AND a.attr_key = k AND a.attr_value_lc IN (...))`. Values are compared lower-cased, as the table stores `attr_value_lc`
  (`add-search-document-attributes`, D6). One `EXISTS` per distinct key.
- A facet on key `k` is a `GROUP BY a.attr_value` over `search_attribute` joined to the rows that satisfy the other filters and the access
  condition, with `COUNT` and the existing ordering and cap.
- A sort on key `k` is a scalar subquery on `attr_value_lc` for the row, with `NULLS LAST`. This is bounded by the index on
  `(attr_key, attr_value_lc)` created in `add-search-document-attributes`; the change records the cost and measures it on the test
  fixture with the target row count (Q2).

### D5: Elasticsearch, OpenSearch and Meilisearch

- **Elasticsearch and OpenSearch.** A filter is a `bool.filter` `terms` query on `attributes.<key>` (keyword, per the dynamic template of
  `add-search-document-attributes`). A facet is a `terms` aggregation on the same field inside a filtered aggregation that carries the other
  filters and the access clause, so the count respects access exactly as the database does. A sort is a `sort` on the keyword field with
  `missing: _last`.
- **Meilisearch.** The declarations give the filterable and sortable lists. When `open()` or a declaration change sees a difference from
  the current settings, the backend updates the settings and marks the settings state as pending, which the status shows, as the existing
  settings state does. A filter is a filter string on `attributes.<key>`; a facet is `facetDistribution`, computed with the same filter
  string minus the facet's own key. The access filter is added as it is today (`MeilisearchFilters.kt:47`).
- A filter on a key that is declared but not yet applied in Meilisearch returns `503` with `Retry-After` while the settings state is
  pending, the same answer the search gives for an unavailable backend today.

### D6: capabilities and refusals

A backend declares `ATTRIBUTE_FILTERS`, `ATTRIBUTE_FACETS` and `ATTRIBUTE_SORT` (`add-search-document-attributes`, D3). A query that uses a
feature whose capability the backend lacks is refused before the backend is called, with `400` and the problem type
`unsupported-search-capability` naming the capability. The refusal is never an empty result, so a portal cannot mistake a missing backend
feature for an empty catalogue.

### D7: access

No change to the access compiler or to `SearchAccess`. The attribute filters and facets run in the same query as the access condition, and
the core applies the access check to the returned rows once more, as the search SPI already does (`add-search-backend-registration`, D6).
The test fixture contains a private repository with rows that match every filter, and asserts that no count and no result includes them.

### D8: compatibility

Requests without `attribute`, `attributeSort` and `attributeFacets` are executed as before, and the response is byte-compatible with the
previous version, apart from the field order of JSON, which the API does not promise. No configuration key is added.

## Plugin API gaps closed and not closed

| Gap | Closed here | Note |
| --- | --- | --- |
| No attribute filter in the query | yes | D2 |
| No multi-valued facet | yes | D2 and D3 |
| No sort by attribute | yes, declared keys only | D2 |
| No declared capability of a backend for these features | yes, used | D6 |
| Full text over descriptions | no | Q1 |
| Counting projects | no | Q5 |

## Alternatives considered

- **Facets computed by the plugin from a full scan.** Rejected: the scan reads every row, bypasses the access condition unless the plugin
  reimplements it, and cannot page.
- **A facet endpoint separate from the search.** Rejected: two queries with two access evaluations can disagree; one query keeps the
  counts and the results on the same rows.
- **Free-form attribute filters without declarations.** Rejected: Meilisearch needs the filterable settings up front, and an undeclared key
  would silently return nothing on one backend and work on another.

## Risks

- **Meilisearch settings change is asynchronous.** Mitigation: the pending state and `503` of D5.
- **Sort by attribute on the database backend is a scalar subquery.** Mitigation: the index of `add-search-document-attributes`, and the
  measurement of Q2 before the change is merged.
- **Multi-valued counts can exceed the file count.** Mitigation: the portal says so (Q5).

## Open questions (each has a default)

- **Q1. Text search over one attribute.** Default: no. Alternative: a text capability (`ATTRIBUTE_TEXT`) with a bounded `LIKE` over a
  lower-cased column in the database backend and a full text field in Elasticsearch and OpenSearch. Decided with the portal, not here.
- **Q2. Sort cost on the database backend.** Default: allowed for declared sortable keys, measured on a fixture of the expected size before
  merge. Alternative: no attribute sort on the database backend.
- **Q3. Version and range comparison.** Default: no. Game versions are normalised minors stored as strings, as the Minecraft design (Q3)
  decides; ranges stay text and are not facetable.
- **Q4. Bucket cap.** Default: 20, the existing `FACET_BUCKETS`.
- **Q5. Project counts.** Default: file counts, documented as such. Alternative: a distinct count by the portal's grouping key, which the
  index cannot compute in one query today.

# Proposal

## Why

Once a plugin stores attributes next to a file (`add-search-document-attributes`), a portal can show them but cannot narrow a list by
them. The Minecraft portal needs "all Paper plugins for 1.21", with counts beside each loader and each game version, and the Gradle
portal needs "plugin id is X" to find every version of one plugin. The search query can filter only on fixed fields: prefixes of
coordinates, extensions, repositories, classifiers, origin and snapshot (`net/onelitefeather/ingot/search/ArtifactSearchEngine.kt:47`).
The facets are fixed too: repository, extension, classifier, origin and snapshot (`ArtifactSearchEngine.kt:132`). There is no way to
ask a backend for a count over values of an attribute.

The existing facet engine already has the rule that matters here: each facet counts the rows that match every other filter, applied
to readable rows only (`database/DatabaseArtifactSearchEngine.kt:119-128`). This change extends that rule to declared attributes, so that
a count never includes a file the viewer may not read and never drops a value the user has just selected.

## What Changes

- An enricher declares the attributes it uses, with three flags: filterable, facetable and sortable. Undeclared keys cannot be filtered,
  faceted or sorted, and the request is refused with a problem response, not answered with an empty list.
- The search query gains attribute filters (AND across keys, OR across the values of one key), an optional sort on one sortable key, and
  a set of facet keys. The response gains `facets.attributes`, present only when requested.
- Backends declare the three new capabilities (`ATTRIBUTE_FILTERS`, `ATTRIBUTE_FACETS`, `ATTRIBUTE_SORT`) of `add-search-document-attributes`.
  A query that needs a capability the backend lacks is refused with the name of the capability.
- Each backend implements the query: the database backend with `EXISTS` subqueries and grouped counts, Elasticsearch and OpenSearch with
  filters and terms aggregations, and Meilisearch with declared filterable and sortable settings and facet distribution.
- Counts are computed in the same query as the results and over readable rows only, with multi-select semantics: a facet counts the rows
  that match all other filters.

### Out of scope

- Full text search over attribute values, for example a description. Open question Q1.
- Numeric, range and version-order comparison of values. Values are strings and compared as strings.
- Counting projects instead of files. The portal groups files into projects; the index counts files, as it does today (Q5).
- Any change to the existing filters, facets and sort. They keep their meaning and their order.
- A new configuration key.

## Capabilities

### New Capabilities

- `search-attribute-facets`: the declarations, the query parameters, the facet semantics, the capability refusals and the per-backend
  behaviour.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)` and `docs(search)`, one type
  each. The pull request titles are:

  `feat(search): filter and facet on plugin attributes` (the declarations, the query model, the database backend)

  `feat(search): filter and facet on plugin attributes in the search engines` (Elasticsearch, OpenSearch and Meilisearch, one pull request
  per module if needed; the title names the modules in the pull request)

  `docs(search): describe attribute filters and facets` (the reference page)

- **Breaking marker.** No breaking change. Requests without the new parameters are answered as before, and the response has no new field
  unless it is asked for. Meilisearch settings for declared keys are applied on start and change once, which Meilisearch performs as an
  asynchronous task. No configuration key, environment variable, volume, manifest or image has to change.
- **Binary surface.** `ArtifactSearchQuery`, `SearchFacets` and `ArtifactSearchBackend.capabilities` (from the previous change) gain members
  with defaults in public experimental types. `ArtifactEnricher` gains `declaredAttributes` with a default of an empty list.
- **Order.** Depends on `add-search-document-attributes`, which stores the values and declares the capabilities this change uses. Depends
  on `add-artifact-enrichment-hook` through it. It does not depend on `expose-plugin-access-checks`: the portal reads with the access value
  the core already computes for each request. It is the last of the four search changes in the portal order.

## Verification

- Unit tests of the declarations, the query validation and the multi-select rule with fake backends and a fake clock.
- Contract tests of the search SPI extended with facet cases: a count never includes an unreadable row; a selected value stays in its own
  facet; an undeclared key is refused; a missing capability is refused by name.
- Integration tests with the database backend and with each plugin backend that declares the capabilities, with a private repository in
  the fixture.
- `openspec validate add-search-attribute-facets --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-search-attribute-facets` prints nothing.

---
description: Filter, sort and count search results by the attributes that an artifact enricher declared. Covers the declaration, the three request parameters, the counting rule, the refusals, the backends that support them and the 503 of a pending Meilisearch settings change.
---

# Search attribute filters and facets

A plugin that stores [attributes](search-document-attributes.md) next to a file can also let the search narrow a list by them: all plugins for one game version, with the number of files for each loader beside it. This page describes what the plugin declares and what the search API then answers.

These queries are experimental, like the attributes they read. A request without the new parameters is answered as before, and the response has no new field unless it is asked for.

## Declaring the attributes

An enricher lists the keys that may be queried in `declaredAttributes`, each with three flags:

```kotlin
@OptIn(ExperimentalIngotSearchApi::class, ExperimentalIngotPluginApi::class)
override val declaredAttributes = listOf(
    AttributeDeclaration("jar-header.platforms", filterable = true, facetable = true, sortable = false),
    AttributeDeclaration("jar-header.versions", filterable = true, facetable = true, sortable = true),
)
```

* A key must be in the namespace of the enricher, as for the attributes themselves. A declaration outside it is refused with a warning, and so is a key that another enricher already declared; the warning names both classes.
* An undeclared key cannot be filtered, counted or sorted. The default is an empty list, so nothing is queryable until a plugin says so.
* A request that uses a key without the matching flag is refused. It is never answered with an empty list.

## Request parameters

All parameters are optional and can be repeated. They are added to the [search request](../http-api/search-filters-and-api.md#query-parameters).

| Parameter | Form | Meaning |
| --- | --- | --- |
| `attribute` | `<key>:<value>` | A filter. The same key given several times is an OR over its values, and different keys are ANDed. At most 4 keys and 16 values per key. Values are compared without regard to case. |
| `attributeSort` | `<key>:asc` or `<key>:desc` | Sorts by one sortable key. A group sorts by the smallest value of its files ascending and by the largest descending. Groups without a value come last in both directions, and ties follow the usual order of groups and versions, so pages stay stable. |
| `attributeFacets` | `<key>,<key>` | The keys whose buckets are returned, at most 8. |

The response gains `facets.attributes`, a map from each requested key to a list of `{ value, count }` buckets, at most 20 per key, the largest first and ties by value. The field is absent unless `attributeFacets` was given.

## The counting rule

Each facet counts the files that match every other filter of the request, except the filters on its own key, and only files the viewer may read. Two consequences follow:

* A value the user has selected keeps showing its alternatives, so a selection can be widened.
* A count never includes a file in a repository or path the viewer cannot read. The access condition is part of every count, not a step after it.

The counts are file counts, as everywhere in the index. A file with two values for one key counts once for each of them, so the buckets of a multi-valued key can add up to more than the number of files. A portal that groups files into projects should say so next to the numbers.

Values are strings and are compared as strings. There is no text search over values, no range or version-order comparison and no project count.

## Refusals

A query is checked before any backend is called:

| Cause | Answer |
| --- | --- |
| A key that is not declared, or declared without the flag the parameter needs | `400`, problem type `urn:ingot:problem:undeclared-attribute` |
| More than 4 filter keys, 16 values for one key or 8 facet keys | `400` |
| A feature whose capability the active backend lacks | `400`, problem type `urn:ingot:problem:unsupported-search-capability`, naming the capability |

## Backends

A backend declares `ATTRIBUTE_FILTERS`, `ATTRIBUTE_FACETS` and `ATTRIBUTE_SORT` next to `ATTRIBUTES`.

| Backend | Filters, facets and sort | How |
| --- | --- | --- |
| Database | yes | One `EXISTS` condition per key, grouped counts over the same conditions, and a sort by a subquery on the value. |
| Elasticsearch | yes | A `terms` filter on `attributes.<key>`, a filtered terms aggregation that carries the access condition, and a sort with the missing values last. |
| OpenSearch | yes | The same as Elasticsearch. |
| Meilisearch | yes | See below. |
| Lucene | no | It declares nothing until its attribute mapping lands. |
| Backends of other plugins | no, unless they declare it | |

### Meilisearch

Meilisearch needs the filterable and sortable attributes up front, so the backend derives them from the declarations: a key that is filterable or facetable becomes filterable, and a sortable key becomes sortable. When the declarations change, the backend sends the new settings once. Meilisearch applies a settings change as an asynchronous task and reindexes while it runs. Until the task has finished, every search answers `503` with `Retry-After`, as it does for any other settings change, and the health of the backend reports `settings task running`. Operators do not need to do anything, and no setting, variable or volume changes.

A filter is an `IN` clause on `attributes.<key>`, a facet is the facet distribution computed with the same filters minus the facet's own key and plus the access condition, and a sort is applied by the plugin to the groups of the request.

## Upgrading

Nothing has to be changed. An installation that has no plugin with declared attributes behaves as before. The first start of Meilisearch after a plugin declares keys applies the settings once, and searches wait for it with the `503` described above.

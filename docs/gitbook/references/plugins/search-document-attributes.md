---
description: Attributes that an artifact enricher writes onto an index row. Covers the namespaces, the limits, the write rules, the rebuild by enricher version, which search backends store attributes and the planned response parameter.
---

# Search document attributes

An [artifact enricher](artifact-enrichment.md) can do more than finish: it can return a small set of string attributes, and the search index stores them with the row of the file. A plugin uses this to keep what it read out of a file, for example the name and the supported versions inside a plugin jar, next to the coordinates the index already has.

Attributes are experimental. `EnrichmentAttributes` carries both `@ExperimentalIngotSearchApi` and `@ExperimentalIngotPluginApi`, so code that returns it opts in to both. The type is listed in [plugin-api-classes.txt](https://github.com/OneLiteFeatherNET/ingot/blob/main/reposilite-backend/plugin-api-classes.txt) and ships in the slim plugin API artifact.

## Returning attributes

`enrich` returns `EnrichmentAttributes(values)` instead of `EnrichmentCompleted`. `values` maps each key to a list of strings:

```kotlin
@OptIn(ExperimentalIngotSearchApi::class, ExperimentalIngotPluginApi::class)
override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult =
    EnrichmentAttributes(
        mapOf(
            "jar-header.name" to listOf("Example"),
            "jar-header.versions" to listOf("1.20", "1.21"),
        )
    )
```

The index status counts an `EnrichmentAttributes` result as `completed`. An empty map is a completion without attributes of its own.

## Keys and namespaces

* A key is `<enricher-id>.<name>`. The enricher id is the `id` of the enricher that returns it: lower case letters, digits and hyphens, 2 to 32 characters, starting with a letter or a digit. The name starts with a lower case letter and continues with lower case letters, digits and underscores, 32 characters at most.
* An enricher writes only into its own namespace. A key whose part before the first dot is another id is refused.
* The namespace `core` belongs to the server. For every enricher that completed on a row, the server writes `core.enricher.<enricher-id>` with the `version` of that enricher. This is the only key the server writes, and the rebuild reads it (see [Rebuild by enricher version](#rebuild-by-enricher-version)). Do not use `core` as an enricher id. The registry does not refuse that id yet.

Values are strings. Write numbers and booleans as strings. The case of a value is kept as returned.

## Limits

The server checks every attribute set before it reaches a backend. The limits are constants and cannot be configured:

| Limit | Value |
| --- | --- |
| Keys per set | 32 |
| Values per key | 16 |
| Characters per value | 2000 |
| Keys and values together, in UTF-8 bytes | 8 KiB (8192 bytes) |

A value may not contain a control character, or one of the bidirectional override and isolate characters U+202A to U+202E and U+2066 to U+2069. They are refused, not removed.

A set that breaks a rule is refused as a whole and never truncated. The call then counts as `EnrichmentFailed` with the reason `invalid attributes: <rule>`, and the usual warning names the enricher, the repository and the path. The rule is one of:

| Rule | Cause |
| --- | --- |
| `too-many-keys` | more than 32 keys |
| `invalid-key` | a key without a dot, or a name that does not match the pattern above |
| `foreign-namespace` | a key in the namespace of another enricher |
| `too-many-values` | more than 16 values for one key |
| `value-too-long` | a value of more than 2000 characters |
| `control-character` | a control character or a bidirectional override or isolate character in a value |
| `set-too-large` | more than 8 KiB of keys and values |

The checks run in this order, and the first rule that fails is reported. Neither the reason nor the log line ever contains a value.

## Write rules

The attributes of a row only change when its enrichers run, and a failure never leaves an old result behind:

| Event | Attributes of the row afterwards |
| --- | --- |
| A changed file is indexed (trigger `CHANGED`) | Replaced by the union of the sets returned in this call, plus `core.enricher.<id>` for each enricher that completed. |
| An enricher fails or skips during that call, including `timeout`, `busy` and a refused set | Its keys and its `core.enricher.<id>` are gone. The results of the other enrichers stay. |
| An unchanged or held row is touched | Kept as stored. |
| The origin of a directory changes (reorigin) | Kept as stored. |
| A path, a prefix, a stale row or a repository is removed | Removed together with the row. |
| A rebuild writes a new or changed file | As for a changed file, with the trigger `REBUILD`. |
| A rebuild finds an unchanged row whose enrichers all have their current version stored | Kept as stored. The enrichers are not called. |
| A rebuild finds an unchanged row with a missing or older version of a matching enricher | The enrichers run with the trigger `REBUILD`, and the row is written as for a changed file. |

## Rebuild by enricher version

A rebuild compares, for every unchanged row, the `version` of each enricher that matches the file with the version stored in `core.enricher.<id>`. When one is missing or different, the enrichers of that file run again. A rebuild of a large repository therefore reads only the files of a new or upgraded enricher, not every file.

Change the `version` of your enricher whenever its output changes. Installing a new version does not start a rebuild by itself: an operator starts one, or the next scheduled rebuild picks the rows up.

This rule needs a backend that stores attributes. On a backend without them, an unchanged row is never enriched again, as described in [the rebuild gap](artifact-enrichment.md#the-rebuild-gap).

## Backends

A search backend says whether it stores attributes through `capabilities`, a set of `SearchCapability` values on `ArtifactSearchBackend`. The default is empty. `ATTRIBUTES` is the capability of this page. `ATTRIBUTE_FILTERS`, `ATTRIBUTE_FACETS` and `ATTRIBUTE_SORT` are the capabilities for filtering, faceting and sorting by attributes. The database backend, OpenSearch and Elasticsearch declare all three. Meilisearch does not declare them yet.

| Backend | `ATTRIBUTES` | `ATTRIBUTE_FILTERS`, `ATTRIBUTE_FACETS`, `ATTRIBUTE_SORT` | How the attributes are stored |
| --- | --- | --- | --- |
| Database (H2, SQLite, MariaDB, MySQL, PostgreSQL) | yes | yes | The table `search_attribute`, one row per value with the repository, the path, the key, the position of the value, the value and a lower-cased copy of it. |
| OpenSearch | yes | yes | An `attributes` object in the document. |
| Meilisearch | yes | no | An `attributes` object in the document. |
| Elasticsearch | yes | yes | An `attributes` object in the document. |
| Lucene | no | no | The Lucene plugin declares nothing until the pull request that adds the attribute field to its mapping lands. |
| Backends of other plugins | no, unless they declare it | no, unless they declare it | See [Write a search backend plugin](../../manage/plugins/write-a-search-backend-plugin.md). |

When the active backend does not declare `ATTRIBUTES`, the server drops the attribute set before it reaches the backend and counts the call as skipped with the reason `unsupported-backend`. The row is written without attributes, exactly as before, and nothing fails. The call shows up in the `skipped` counter of the enricher in the index status. A portal that shows no attributes on such an installation can point there.

### Database

The table `search_attribute` is created at start next to the other search tables. A database that an earlier build created gets the table added on the next start, once, with no migration step and no change to the existing rows. A row is written and deleted together with its attributes in one transaction, in every delete path. A downgrade to a build without the table ignores it.

### OpenSearch and Elasticsearch

The index mapping gains an `attributes` object and the dynamic template `ingot_attributes`, which maps every string under `attributes.*` to a `keyword`. A new enricher therefore needs no mapping change. An index that an earlier build created takes this as a mapping addition when the backend opens it. The mapping version stays `1`, and no reindex is needed.

An upsert that carries attributes indexes the whole document, so no older attribute survives. An upsert without attributes merges the fixed fields into the stored document and keeps the attributes.

### Meilisearch

The document carries an `attributes` object. A document write replaces the whole document, so the backend reads the stored attributes before it writes an upsert that carries none, and keeps them that way. The field is not in the filterable, sortable or searchable settings, so queries ignore it.

## Writing a backend that stores attributes

A backend declares `ATTRIBUTES` only when its own module stores them:

```kotlin
override val capabilities: Set<SearchCapability> = setOf(SearchCapability.ATTRIBUTES)
```

It then follows two rules:

* `IndexAction.Upsert` has an `attributes` member. When it is set, it replaces the stored attributes of the row, and an empty map removes them. When it is `null`, the stored attributes stay. The server sets it only at a backend that declares the capability.
* `existing` returns a `StoredRow` whose `enricherVersions` maps each enricher id to the single value of its stored `core.enricher.<id>` key. The rebuild decides from it which unchanged rows to enrich.

Touch, reorigin and every delete path keep or remove the attributes together with the row. The attribute cases of the backend contract tests (`AttributeStorageContract` in the test fixtures of the backend module) check these rules.

## Response parameter

The search API does not return attributes in this release, and its responses are unchanged. A later release adds the request parameter `attributes=true`: only a request that carries it gets an `attributes` object per file result, and only for rows the caller may read, because the access checks filter the rows before they are returned. Without the parameter the response stays as it is.

## Upgrading

Rows indexed before attributes existed have none. On a backend that stores attributes, the first rebuild after an enricher is installed finds no stored version on those rows and enriches every file the enricher matches, once. Nothing starts that rebuild automatically.

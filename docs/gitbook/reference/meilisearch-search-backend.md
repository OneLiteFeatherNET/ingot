# Meilisearch search backend

The Meilisearch backend answers artifact search from a Meilisearch server. It is an experimental plugin. It is not part of the image: install `meilisearch-plugin.jar` into the plugins directory, or from the plugin store once it exists, and set `searchEngine` to `meilisearch`. Without the jar, nothing of this backend runs.

## Known differences from the database backend

The shared search contract passes in full on this backend. These are the named exceptions, the places where the backend still differs from the database backend, each with its reason.

- **Paging of sampled and path lists (G2).** The verify sample and the path walk read every row of the index and page through it in memory. A walk over a large index is expensive, and a change between two calls can repeat or skip a path.
- **Totals and pages beyond the server limit (G3).** The file total is exact up to 100 000 matching files. A group query fetches a window of 10 101 groups, one more than the page bound of 10 000, so the group total is exact up to 10 000 groups. A page that ends beyond 10 000 groups is refused, and the request has to narrow the filters.
- **Group sorts by modification and size (G4).** These sorts read every file of the window. Above `INGOT_MEILISEARCH_AGGREGATE_SORT_LIMIT` files the request is answered `503` with the hint to narrow the filters.
- **Legacy path grants (G7).** A caller holding a plain string prefix grant gets `503` with the reason. The plugin does not guess an answer.
- **Words (G6).** Typo tolerance is off, and every word of a query must match. A word before the last one matches a prefix of the groupId or the artifactId, the exact version, or a whole token of the coordinates, as on the database. The last word also matches a prefix of a token. A query with a dot anywhere is a coordinate prefix and gets no word match here, whereas the database still gives its plain words a word match. This is the one word difference that remains.
- **Facets and versions.** A version prefix is applied to groups in memory. A version prefix in a facet request is refused with `503`.
- **Writes are visible after the task succeeds.** A write is confirmed by its server task within `INGOT_MEILISEARCH_TASK_WAIT_SECONDS`. A task still running after that bound releases the batch without an attempt, and the replay is idempotent.

## Operator obligations

- Use a **dedicated key** for the dedicated index. The key needs exactly these actions: `indexes.create`, `indexes.get`, `settings.get`, `settings.update`, `documents.add`, `documents.get`, `documents.delete`, `search`, `stats.get`, `tasks.get` and `version`. The container tests run every contract case with a key that holds only these actions on the `ingot-*` indexes, and all cases pass. The health route `/health` is read without a key. The plugin does not check the key, because it cannot detect a master key without a privileged call. Restrict the key to the index uid.
- Do not share the index with another application: the plugin sets the index settings and reindexes it when they change.
- The server holds the paths of every repository, including hidden and private ones, as the other backends do. Put it behind a network policy that admits only the Ingot replicas.
- Use TLS. Plain HTTP is refused unless `INGOT_MEILISEARCH_ALLOW_HTTP` is `true`, and a warning is logged at every start while it is.
- Rotate the key through the file form (`INGOT_MEILISEARCH_API_KEY_FILE`). The key is never written to a log, a status response, a problem body or the support bundle.

## Server version

The plugin opens only a Meilisearch server of minor version 1.15. Any other version is `OPEN_FAILED`, and the message names both versions. The image used by the container tests is pinned by tag and digest.

## Variables

| Variable | Default | Meaning |
|---|---|---|
| `INGOT_MEILISEARCH_URL` | none | Base URL of the server, for example `https://search.example.org`. Required when selected. |
| `INGOT_MEILISEARCH_ALLOW_HTTP` | `false` | Accepts an `http://` URL. Logged as a warning at every start. |
| `INGOT_MEILISEARCH_API_KEY` | none | API key, sent only in the `Authorization` header. |
| `INGOT_MEILISEARCH_API_KEY_FILE` | none | File holding the key. The plain variable wins when both are set. |
| `INGOT_MEILISEARCH_INDEX` | `ingot-artifacts` | Index uid: 1 to 64 letters, digits, `-` or `_`. |
| `INGOT_MEILISEARCH_TIMEOUT_SECONDS` | `30` | Timeout of one request, 1 to 300. |
| `INGOT_MEILISEARCH_TASK_WAIT_SECONDS` | `20` | Bound of the wait for the tasks of one batch, 1 to 60. |
| `INGOT_MEILISEARCH_BATCH_SIZE` | `1000` | Documents per request, 1 to 10 000. |
| `INGOT_MEILISEARCH_AGGREGATE_SORT_LIMIT` | `100000` | Files read for a group sort by modification or size, 1 to 1 000 000. |

Every variable also reads its `REPOSILITE_MEILISEARCH_` spelling when the `INGOT_` name is unset or blank. The `INGOT_` name wins.

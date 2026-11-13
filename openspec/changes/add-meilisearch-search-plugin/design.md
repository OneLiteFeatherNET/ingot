# Design

This design depends on `extract-search-backend-spi` (the seam), `add-database-full-text-search` (the word cases),
`add-search-backend-registration` (the public types, the event, the states, the cluster agreement and the access check of core),
`add-search-backend-registration` D6 (the core check), and the shared module of `add-elasticsearch-search-plugin` (section 0). The
additive method `indexIdentity()` comes from `add-lucene-search-plugin`.

Meilisearch behaviour is described here from the public documentation of the community edition. The points marked **verify** are
features whose exact form depends on the minor version pinned at code time; the code pull request confirms each of them against that
version before the code relies on it (task 5.1). A point that does not hold changes the gap register, and the reference page is updated
in the same pull request.

## Context

- Meilisearch is a search engine with an HTTP API. Writes are asynchronous: a request returns a task, and the task is processed later
  by the engine, one index at a time. A task has the statuses `enqueued`, `processing`, `succeeded` and `failed`.
- A search reads the documents of finished tasks. A search made after a task succeeded sees its documents.
- Its filters are typed expressions over attributes that must be declared `filterable`. Sorting needs `sortable`. Faceting counts
  documents, not groups.
- It has no grouping and no aliases. An atomic replacement of an index is a swap of two indexes, which this change does not need.
- The database engine and the seam expect groups, string cursors and exact totals. Each of these is covered in the gap register below.
- The image sets no `USER`, and nothing here depends on the process user.

## Decisions

### D1: own HTTP client, MIT licence

The plugin calls the HTTP API with the JDK client and the server's Jackson, through the shared module (`reposilite-search-http`). No
Meilisearch client library is a dependency. The reasons are the class loader clash of the OpenSearch design (D1) and the size of the
used surface: about a dozen endpoints.

Licence. The Meilisearch community edition is MIT licensed. The plugin uses only the community edition; enterprise features are not
used, and the reference page says so. The plugin is Apache 2.0 and contains no Meilisearch code. Testcontainers (MIT) and the pinned
Meilisearch image are used in the test source set only, and the image is pinned by its digest.

### D2: configuration and secrets

Every variable has the `INGOT_MEILISEARCH_` prefix; `REPOSILITE_MEILISEARCH_` is the fallback and `INGOT_` wins. The jar is the switch;
the selection is `searchEngine`.

| Variable | Default | Meaning |
|---|---|---|
| `INGOT_MEILISEARCH_URL` | none | Base URL of the server, for example `https://search.example.org`. Required when selected. |
| `INGOT_MEILISEARCH_ALLOW_HTTP` | `false` | Accepts an `http://` URL. Logged as a warning at every start. |
| `INGOT_MEILISEARCH_API_KEY` | none | API key. Also read from `INGOT_MEILISEARCH_API_KEY_FILE`; the plain variable wins. |
| `INGOT_MEILISEARCH_INDEX` | `ingot-artifacts` | Index uid. Letters, digits, `-` and `_`, 1 to 64 characters. |
| `INGOT_MEILISEARCH_TIMEOUT_SECONDS` | `30` | Timeout of one request, 1 to 300. |
| `INGOT_MEILISEARCH_TASK_WAIT_SECONDS` | `20` | Bound of the wait for the tasks of one batch, 1 to 60 (D4). |
| `INGOT_MEILISEARCH_BATCH_SIZE` | `1000` | Documents per request, 1 to 10 000. |
| `INGOT_MEILISEARCH_AGGREGATE_SORT_LIMIT` | `100000` | Files read for the group sorts by modification and size (gap G4). |

Rules, shared with the other plugins where the text is the same. A URL with user information is refused and the message names the
variable. The API key is never written to a log, a status response, a problem body or the support bundle. An unreadable key file stops
the plugin with a message that names the variable. A key is sent in the `Authorization: Bearer` header only, never in a URL.

### D3: index layout and settings

- One index, `INGOT_MEILISEARCH_INDEX`, with the primary key `id`. The id is the database path hash in lower-case hexadecimal, which
  Meilisearch accepts as a document id. One document per file.
- Document fields: `id`, `repository`, `path`, `path_lc`, `directories` (the array of every ancestor directory of the path, for example
  `["a", "a/b"]` for `a/b/c.jar`), `group_id`, `group_id_lc`, `artifact_id`, `artifact_id_lc`, `version`, `version_lc`, `gav` (the group
  key), `artifact_key`, `classifier`, `extension`, `snapshot`, `origin`, `source_host`, `size_bytes`, `last_modified`, `indexed_at`,
  `sha1`, `sha256`.
- `directories` replaces every prefix filter of the other backends. The path predicates of the access value become equality tests on
  `directories` and `path`, so no string range or `STARTS WITH` filter is needed (**verify** that equality on an array attribute matches
  any element, which the public filter syntax describes). The cost is one array entry per directory level.
- Settings, applied by `open()` when they differ from the stored ones, each change logged with the old and new value (open question 6):
  - `filterableAttributes`: `id`, `repository`, `path`, `directories`, `gav`, `group_id`, `artifact_id`, `version`, `classifier`,
    `extension`, `snapshot`, `origin`, `source_host`, `size_bytes`, `last_modified`, `indexed_at`, `group_id_lc`, `artifact_id_lc`,
    `version_lc`, `artifact_key`.
  - `sortableAttributes`: `path`, `repository`, `group_id`, `artifact_id`, `version`, `size_bytes`, `last_modified`.
  - `searchableAttributes`: `group_id`, `artifact_id`, `version`, `tokens` (the tokens of `add-database-full-text-search`, written as a
    field of their own).
  - `typoTolerance`: disabled (open question 7).
  - `faceting.maxValuesPerFacet`: 10 000 (open question 5).
  - `pagination.maxTotalHits`: 100 000 (open question 4), so that the group window of 10 100 and the file totals are countable.
  - The distinct attribute for group windows: `gav` (**verify** that the distinct form of a search and its exact count behave as in D6).
- Changing `filterableAttributes` makes Meilisearch reindex the whole index, a task that can take long. `open()` waits for it, and the
  health is `Down` with "settings task running" meanwhile, so search answers `503` with the reason until it is done.
- `open()` also checks the version of the server (`GET /version`). A major version other than the pinned one is `OPEN_FAILED`.

### D4: writes as tasks, awaited within a bound

- `apply(actions)` keeps the order of the actions and sends them in order. Upserts go as `POST /indexes/{uid}/documents` (add or replace)
  in requests of `BATCH_SIZE` documents. Removals of single paths are `DELETE /indexes/{uid}/documents/{id}`. Prefix removals and stale
  removals are delete-by-filter requests. Partial updates (`touch`, reorigin) are `PUT /indexes/{uid}/documents` (add or update).
- Each request returns a task uid. `apply` polls the tasks of its batch with `GET /tasks/{uid}` until each one is `succeeded` or
  `failed`, or until `TASK_WAIT_SECONDS` has passed. The poll interval and the wait come through an injected `Waiter`; production uses a
  real delay, and the tests use a fake whose time is driven by the test.
- `succeeded`: the batch is done, and the outbox entries may be removed. Because the wait ends only at `succeeded`, a search after `apply`
  returns sees the batch, and the rebuild cursor, which is committed after the apply, never runs ahead of the index. This closes the
  eventual-consistency question between the task queue and the rebuild cursor (gap G1).
- `failed`: the batch fails with the error code and message of the task (for example an invalid document). The attempts count, and the
  entry becomes a dead letter after ten failures, as the database does.
- Still `enqueued` or `processing` after the bound: `BackendBusyException` with a retry delay of 10 seconds. The task keeps running. The
  outbox releases the batch without an attempt and applies it again later. The replay is idempotent (D5), so the second application of a
  task that finished meanwhile changes nothing.
- The core's outbox claim lease must be longer than `TASK_WAIT_SECONDS`, or a second replica claims a batch that is still being waited
  for. The plugin validates at open that the bound is at most 60 seconds, and the core check of the outbox lease is named as a
  dependency in the tasks (task 6.2).
- Several replicas may drain the outbox at once, as today. Claims are exclusive in the database, and the writes are idempotent.

### D5: idempotency of every write

- An upsert replaces the document with the same id. Two writes of one file give one document.
- A removal of a missing id is a no-op for Meilisearch.
- A prefix removal and a stale removal are delete-by-filter with the staleness condition: `indexed_at <= notAfter` or
  `indexed_at < before`. A late replay cannot remove a row indexed again, because its `indexed_at` is newer.
- A stale removal with a limit reads the ids first and deletes them by a filter that repeats the condition, `id IN [...] AND
  repository = ... AND indexed_at < before`, so a row touched between the two requests survives.
- `touch` is a partial update of `indexed_at` only. Reorigin is a partial update of `origin` and `source_host`.

### D6: reads, the access value and the group computation

The query builder takes `ArtifactSearchQuery` and `SearchAccess`, and builds a filter expression and, where needed, several requests.

Access, per repository (`RepositoryAccess`): `repository = "R"` plus the path condition.

- `All`: nothing more.
- `Exact(p)` and `Subtree(segments)` (the joined path `p`): `(path = "p" OR directories = "p")`, the same rule as the database's
  "the path itself or the path as a directory". The string is quoted and escaped as the filter syntax requires.
- `Conjunction`: `AND` of the parts.
- `Never`: the plugin returns the empty result without a request.
- `Precedence`: the first-match rule as `OR` over the allowed rules of `(cond_i AND NOT (cond_0 OR ... OR cond_{i-1}))`.
- `Legacy(p)`: refused (open question 2). The plugin answers the caller `503` with the reason. The check of the core is not reached.

Words and coordinates: `q` for the words, with `matchingStrategy` set to match every word, and the coordinate filters on `group_id_lc`,
`artifact_id_lc`, `version_lc`. Only the last word is a prefix in Meilisearch by default (gap G6).

Facets: one search per facet kind, each with the filters of the other kinds, as in the database, sent in one multi-search request. The
counts count files, the unit of the database.

Groups (the page of GAVs). Meilisearch has no grouping, so the plugin computes it in three steps:

1. Window. A search with the query filters and the distinct attribute `gav`, limited to the window of the page (`page * size + size`,
   at most 10 101 so that one extra distinct value shows that the total is above the cap). It returns one file per group.
2. Aggregates for the page. A facet search on `gav` with the filters gives the file count of each group of the page, exactly. The files
   of the page's groups give the repositories with their origin and the maximum `last_modified`, and the sum of `size_bytes`.
3. Order and slice. The plugin sorts the window with the shared comparator of the database engine and slices the page. For the sorts
   by modification and size, which are aggregates over a group, step 2 must run over every group of the window; the files read for it
   are bounded by `AGGREGATE_SORT_LIMIT` (gap G4).

Total: the number of distinct groups of step 1, exact up to the cap, because the window of the total is the cap plus one. **Verify**
whether `totalHits` of a distinct search is exact; if it is, the total is one request instead of a window of 10 101 (see the gap register).

Latest version (ignores the filters): the distinct versions of `group_id = G AND artifact_id = A`, at most 5001 of them, then the shared
`latestOf`.

Files: a search with the filters, sorted by `path` and `repository` (both sortable), `offset` and `limit`. The total is `totalHits`,
exact up to `pagination.maxTotalHits` (gap G3).

Status: the core computes the state from the health and the outbox, as today. The backend's block shows the consistency mode
("after task success"), the number of tasks not yet finished and the settings of D3 that matter for the index.

### D7: count, sampling, pathsAt and repositories

- `count()`: `GET /indexes/{uid}/stats`, `numberOfDocuments`. It counts files only, because the index holds only files. It is exact once
  the tasks are finished, which `apply` ensures.
- `repositories()`: a facet search on `repository` with no query, the facet keys.
- `pathsAt(repository, prefix, after, limit)`: a search with the filter `repository = "R" AND (path = "P" OR directories = "P")`, sorted by
  `path`, with `offset` as the cursor (gap G2).
- `sample(cursor, limit)` and `sampleStart(random)`: the sort is `repository`, `path`; the cursor is an offset into that sort (gap G2).
- `existing(repository, paths)`: a search with `repository = "R" AND path IN [...]`, in batches of at most 1000 paths, retrieving the stored
  fields. **Verify** the documents-fetch endpoint with a filter as the alternative.
- `touch`: `existing()` first to count the rows that exist, then a partial update of `indexed_at` for them.
- `deleteStale(repository, before, limit)` and `deleteRepository(repository, limit)`: as D5, ids first, then the delete-by-filter with the
  repeated condition.
- `keyRows`, the word backfill: not needed. Every word is indexed by Meilisearch at write time, and `tokensComplete()` returns true.

### D8: health and the state of tasks

- `health()`: `GET /health` returns `available` when the server answers. Any other answer, a transport failure or a failed open is `Down`
  with the message. The circuit breaker of the shared module (five transport failures, 30 seconds) applies.
- A `failed` task that is older than the current batch does not change the health. Failed tasks are reported in the status block with
  their count, so an operator sees them.
- Backlog: the number of `enqueued` and `processing` tasks is read from `GET /tasks` at each status request and reported as the lag of the
  backend. It does not change the state.

### D9: security

- The index holds the paths of every repository, including `HIDDEN` and `PRIVATE` ones, as the other backends do. The access value is a
  filter of every request, and the core check counts any row that fails it.
- Minimal key. The key needs the actions `indexes.create`, `indexes.get`, `indexes.update`, `settings.update`, `settings.get`,
  `documents.add`, `documents.get`, `documents.delete`, `search`, `stats.get`, `tasks.get`, `version` and `health` (**verify** the action
  names at the pinned version), restricted to the index uid `INGOT_MEILISEARCH_INDEX`. A key with wildcard actions is not checked by the
  plugin (open question 8).
- The key is sent only in the header, and it never appears in an error text: the shared redaction removes it from every message.
- The operator's responsibility, which the reference page states: a dedicated key on the dedicated index, a network policy so that only
  Ingot replicas reach the server, TLS, and the rotation of the key through the file form.

### D10: testing

- Unit tests, no network, no clock reads: the variable rules and fallbacks; the URL and key rules; the filter expressions for every
  access predicate against golden strings; the first-match rule; the `Legacy` refusal; the group computation on fixture responses; the
  batch sizes; the task state machine (`enqueued`, `processing`, `succeeded`, `failed`, past the bound) driven by a fake `Waiter`; the
  mapping of task failures to batch failures and of busy to `BackendBusyException`; the redaction of error texts.
- Contract suite against a fake Meilisearch in-process, with the same cases as the database engine. The fake applies filter expressions
  it recognises and records the requests. The fake is the reference for the contract cases, and the container test confirms them.
- Container tests (integration source set), through `heavy` with `--max-workers=2`, against the pinned Meilisearch image, started through
  Testcontainers with a random port: a restart during a drain; a task that fails; a task that outlives the bound, after which the replay
  leaves the index equal to the database; a settings change that reindexes; an index replaced by the operator, which changes
  `indexIdentity()`.
- Every test is F.I.R.S.T.: the `Waiter` is a fake in unit tests and a bounded real wait in the container tests, with no `Thread.sleep` in
  any test body; each test uses its own index uid with a random suffix; no fixed port.

## Gap register

Each row is a method or rule of the seam, the feature used, the gap, and what the plugin does. The contract suite lists the rows marked
with an exception as named exceptions, each with its reason.

| ID | Method or rule | Meilisearch feature | Gap | What the plugin does |
|---|---|---|---|---|
| G1 | `apply`, rebuild cursor | Asynchronous tasks | A write is visible only after its task succeeded, and the core commits the rebuild cursor after `apply`. | `apply` waits for the tasks of its batch within `TASK_WAIT_SECONDS`. A task still running after the bound releases the batch without an attempt, and a replay is idempotent. Nothing is committed ahead of the index. |
| G2 | `pathsAt`, `sample`, `sampleStart` | Offset paging on a sort | No string range filter, so a path cursor cannot be expressed. An offset shifts when rows are inserted or removed between two calls. | The cursor is an offset. A walk can repeat or skip a path while the index changes. The rebuild is therefore followed by the verify job's sample, and the reference page names this. Exception in the contract suite. |
| G3 | `search` files total, pagination | `totalHits` capped by `pagination.maxTotalHits` | The total of a file list is exact only up to the setting (100 000 by default). Pages beyond the setting are refused by the server. | The setting is raised as D3 states. Above it the total is the server's estimate, and a page beyond the limit is refused with `400` and the hint. Exception in the contract suite. |
| G4 | `search` group sorts by modification and size | Sorting of files, not groups | The sort is over an aggregate of a group, so every file of the window must be read. | Bounded by `AGGREGATE_SORT_LIMIT`. Above the bound the request is `503` with the hint to narrow the filters. Exception in the contract suite. |
| G5 | `search` groups and totals | No grouping; distinct search | Distinct returns one document per value, and whether its total is exact is **verify**. | Step 1 of D6 fetches the window, and the total is counted from the distinct values up to the cap. If `totalHits` is exact for distinct searches, the total is one request. |
| G6 | `search` words | Last-word prefix and typo tolerance | Only the last word is a prefix by default, and typo tolerance matches words the database does not. | Typo tolerance is disabled. Prefixes of every word are indexed by the `prefixSearch` setting, **verify**; if the setting does not exist at the pinned version, the exception is listed. |
| G7 | `search` `Legacy` path grants | Filter operators | No plain string prefix without a boundary. | Such callers get `503` with the reason (open question 2). Exception in the contract suite, and the reference page names it. |
| G8 | `search` facets | Facet value limit | `maxValuesPerFacet` truncates a facet with more values. | The limit is 10 000 (open question 5). Above it the reference page states the truncation. |
| G9 | `existing`, `touch` | Document reads and partial updates | `touch` counts the existing rows and then updates them; another write can land between the two. | The count is advisory and the update is idempotent. The contract suite allows the count to differ by rows written during the call. |
| G10 | `reorigin` | Partial updates over a filter | A directory larger than the total limit cannot be walked by offset. | The plugin repeats the update over the filter `origin != NEW OR source_host != NEWHOST` until no row matches, so no offset is needed. The result is idempotent. |
| G11 | `deleteStale`, `deleteRepository` with a limit | Delete-by-filter has no limit | A limit needs ids first, and a row can change between the two requests. | Ids first, then delete-by-filter with the repeated condition (D5). A changed row survives. |
| G12 | `count` | Index statistics | Exact after tasks finish; during a running task it lags. | `apply` waits, so a count after `apply` is exact. The verify job reads the same backend. |
| G13 | `indexIdentity` | Index creation time | The creation time of the index changes only when the index is recreated. | **Verify** that the index info carries `createdAt`; it is the identity. If it does not, the plugin stores a random id in a document of its own. |
| G14 | `tokensComplete`, `keyRows` | Tokens at write time | None. | `tokensComplete()` is true and `keyRows` keeps its default. |
| G15 | Settings reindex | Reindexing on filter changes | A change of `filterableAttributes` reindexes the whole index, which can take hours. | `open()` waits; health is `Down` with "settings task running" meanwhile, and search answers `503` with the reason. |

## Risks

- **Group computation cost.** Every group search costs a window request and an aggregate request, and the total costs up to the cap. The
  benchmark of `add-database-full-text-search` runs against this plugin, and the numbers go into the code pull request. If the cost is
  too high for the operator, the reference page recommends the database or Elasticsearch.
- **Meilisearch versions.** The behaviour of distinct, the prefix setting and the key actions depends on the version. The pinned minor
  version is recorded in the reference page, and a new minor is tested before Renovate merges it.
- **Experimental status.** The gap register is long. The plugin is marked experimental, the reference page lists the exceptions first, and
  the maintainers can decline to ship it (open question 1).

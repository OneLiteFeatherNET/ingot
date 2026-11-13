# Design

This design depends on the changes before it: `extract-search-backend-spi` (the seam), `add-database-full-text-search` (word cases)
and `add-search-backend-registration` (the public types, the event, the states and the cluster agreement). The reference
implementation is the OpenSearch plugin, `add-opensearch-search-plugin` and its code in `reposilite-plugins/opensearch-plugin`.
Names below are those of those changes.

## Context

- The seam and its rules (`ArtifactSearchBackend`, `IndexAction`, `BackendBusyException`, the states of registration D4) apply to
  this plugin unchanged. The plugin adds no call to the core and no route.
- Plugin jars get their own `URLClassLoader` whose parent is the server class loader (`PluginLoader.kt:71`, `:104`). A client
  library bundled into the plugin can clash with the server's Jackson or HTTP classes. This decides D1.
- Elasticsearch 8 and later enable TLS and authentication on the HTTP layer by default, and they accept API keys in addition to
  basic credentials. Elasticsearch 7 is refused at open (D2).
- The image sets no `USER`, and nothing in this plugin depends on the process user.

## Decisions

### D1: own HTTP layer, no Elastic client

The plugin speaks the REST API with `java.net.http.HttpClient` and the server's Jackson. It does not depend on
`elasticsearch-java`, `elasticsearch-rest-client` or any Elastic artifact at run time. The reasons:

- The Elastic client brings its own transport, JSON mapping and HTTP stack. Those copies would sit next to the server's in the
  plugin's class loader, the clash of the OpenSearch design (D1).
- The request surface is the same small set as for OpenSearch: `_bulk`, `_search`, `_count`, `_delete_by_query`,
  `_update_by_query`, `_aliases`, the index create call, `GET /` for the version and `_cluster/health`.
- Request bodies are JSON trees, so golden files pin each shape without a network.

Licence check, which the maintainers asked for. Elasticsearch itself is not distributed by Ingot and is not a dependency of
the plugin. The server is under the Elastic License 2.0 or the Server Side Public License, and since 8.16 also under the GNU
Affero General Public License 3.0 as an option; the operator chooses which terms apply to their cluster. The plugin's own code
is Apache 2.0 and contains no Elastic code. The Elastic Java client is Apache 2.0, so it would be allowed, but D1 rules it out
for the class loader reason. Testcontainers (MIT) and the Elasticsearch container image are used only by the test source set,
and the image is pinned with its digest.

If the maintainers prefer the official client, the replacement is confined to the HTTP file of the plugin, and the contract suite
is the same.

### D2: versions and wire differences

Supported: Elasticsearch 8.x and 9.x, each tested at its latest minor version. At open the plugin reads `GET /` and takes
`version.number`. A major other than 8 or 9 is refused: `OPEN_FAILED`, naming the version the cluster reported and the supported
majors.

Differences the design plans for. Each is a line of the test matrix; a difference found later is added to this list in the
reference page.

| Area | Elasticsearch 8.x and 9.x | Handling in the plugin |
|---|---|---|
| Authentication | `Authorization: ApiKey <base64(id:key)>` or basic | Both schemes built by one function; one of them only |
| Security defaults | TLS and authentication on | `https` by default; a cluster without security works only with `INGOT_ELASTICSEARCH_ALLOW_HTTP` and an unauthenticated URL, with a warning at each start |
| Certificate authority | The HTTP certificate is signed by the cluster's own authority, written at installation | Operator adds that authority to `INGOT_EXTRA_CA_CERTS`; the test injects it into the test `SSLContext` |
| Bulk response | `errors` flag per request and a status per item; throttling as `429` and as an item `429` | Both mapped to `BackendBusyException` (D6) |
| Result window | `index.max_result_window`, 10 000 by default | Deep file pages refused with a clear error (D7) |
| Buckets | `search.max_buckets`, 65 536 by default | Above the total cap of the seam (D7) |

Pinned test images are Renovate-managed, as the other test containers are, and at least two weeks old when pinned.

### D3: what is shared with the OpenSearch plugin

Two plugins now need the same security and transport rules. Copying them twice invites drift, and a fix in one would miss the
other. The decision is therefore to extract the shared part into a library module and to keep the vendor-specific part in each
plugin.

| Part | Decision | Reason |
|---|---|---|
| Environment and secret rules (`INGOT_` over `REPOSILITE_`, `..._FILE`, trailing line breaks, the password rules) | Shared | Identical for every plugin with secrets; the rule text of `add-file-secrets` must not diverge |
| URL rules (`https` only, user information refused without echoing it) | Shared | Security rule, identical for OpenSearch, Elasticsearch and Meilisearch |
| JSON-over-HTTP client with timeout, the injected clock and the circuit breaker | Shared | The breaker is the one of the OpenSearch plugin; its clock is injected |
| Redaction of error texts | Shared | One list of redacted shapes for every backend |
| NDJSON bulk body builder with size limits | Shared | `_bulk` has the same shape in both products |
| Query translation and the golden files | Not shared, copied and adapted | The shapes differ by version and product in aggregations, paging and the result window; one golden set would hide that |
| Mapping (`IndexDefinition`) | Not shared | Each plugin owns its mapping and its version number |

The shared module is `reposilite-search-http`. It is a library, not a plugin: it has no `ServiceLoader` entry and no event. Each
plugin's shadow jar contains its own copy of the classes, because the plugin loader gives every plugin its own class loader and
one plugin cannot see another plugin's classes. No plugin depends on another plugin at run time.

Sequencing. The extraction is a `refactor(search)` pull request, made from the OpenSearch code once it is merged, and merged
before this change's code pull request. This proposal does not perform it. If the maintainers decide against the extraction
(open question 5), the Elasticsearch plugin copies the helpers, and the copies are listed in the reference page as code to keep
in step.

### D4: configuration and secrets

Every variable has the `INGOT_ELASTICSEARCH_` prefix. The `REPOSILITE_ELASTICSEARCH_` spelling is read as the fallback, and
`INGOT_` wins when both are set. No variable enables the plugin: its jar is its switch. The selection is the existing key
`searchEngine` (`INGOT_LOCAL_SEARCHENGINE`).

| Variable | Default | Meaning |
|---|---|---|
| `INGOT_ELASTICSEARCH_URLS` | none | Comma-separated base URLs. Required when the plugin is selected. |
| `INGOT_ELASTICSEARCH_ALLOW_HTTP` | `false` | Accepts `http://` URLs. Logged as a warning at every start. |
| `INGOT_ELASTICSEARCH_API_KEY` | none | Encoded API key. Also read from `INGOT_ELASTICSEARCH_API_KEY_FILE`; the plain variable wins. |
| `INGOT_ELASTICSEARCH_USERNAME` | none | Basic authentication user. Refused together with an API key. |
| `INGOT_ELASTICSEARCH_PASSWORD` | none | Password. Also read from `INGOT_ELASTICSEARCH_PASSWORD_FILE`; the plain variable wins. |
| `INGOT_ELASTICSEARCH_INDEX` | `ingot-artifacts` | Name prefix of the index and the alias. Lower case, letters, digits, `-` and `_`, 1 to 64 characters. |
| `INGOT_ELASTICSEARCH_BULK_SIZE` | `500` | Actions per bulk request, 1 to 5000, and at most 5 MB of body. |
| `INGOT_ELASTICSEARCH_TIMEOUT_SECONDS` | `30` | Timeout of one request, 1 to 300. |
| `INGOT_ELASTICSEARCH_REPLICAS` | `1` | Replicas of the physical index. |

Rules, shared with the OpenSearch plugin where the text is the same. A URL with user information is refused at start and the
message names the variable, not the value. An API key with a user name, or a user name without a password, or a password
without a user name, is refused. Without any credential the plugin starts only with `INGOT_ELASTICSEARCH_ALLOW_HTTP` and a
cluster that runs without security, with a warning. The file form is read once at start, trailing line breaks are removed, and
an unreadable file stops the plugin with a message that names the variable. The API key and the password never appear in a
log, a status response, a problem body or the support bundle; the plugin reports only "set" or "not set".

TLS. The plugin uses the JVM default `SSLContext` with hostname verification. There is no option to skip verification.

### D5: index layout and alias

- The physical index is `<prefix>-v1`, created by `open()` when it does not exist, with the alias `<prefix>` pointing at it.
  Readers and writers use the alias only, so a later physical index replaces this one with one atomic `_aliases` request.
- The mapping carries `_meta.ingot_mapping_version: 1`. `open()` refuses an index whose version differs and reports
  `OPEN_FAILED` with both versions. Automatic migration is not part of this change.
- Mapping `dynamic: strict`. Fields: `repository`, `path`, `path_lc` (lower-case normaliser), `group_id`, `group_id_lc`,
  `artifact_id`, `artifact_id_lc`, `version`, `version_lc`, `gav` (keyword, the key of a group), `artifact_key`, `classifier`,
  `extension`, `snapshot` (boolean), `origin`, `source_host`, `size_bytes` (long), `last_modified` (long), `indexed_at` (long),
  `sha1`, `sha256`, `tokens` (keyword, multi-valued). Keywords have doc values, except the display-only strings (`group_id`,
  `artifact_id`, `version`), which are not aggregated.
- Document id: the database path hash (SHA-256 of repository, NUL and path). Writing the same file twice replaces one document.
- Settings: one shard, `INGOT_ELASTICSEARCH_REPLICAS` replicas, the refresh interval at its default of one second. The shard
  count is not configurable in version one.
- The plugin creates its index and alias and nothing else (open question 7). It needs the cluster privileges listed in D10.

### D6: writes, idempotency and back pressure

- `apply(actions)` keeps the order of the actions. Upserts and single-path removals go in `_bulk` requests of at most
  `BULK_SIZE` actions and 5 MB. A removal of a prefix runs `_delete_by_query` with `conflicts: proceed` and a filter on the
  repository, on `path` (the path itself or the prefix with `/` as a prefix) and on `indexed_at <= notAfter`.
- A reorigin runs `_update_by_query` with a script that is a constant of the plugin. Its parameters are the origin and the source
  host. No value of a request enters the script text. An inline constant script is chosen over a stored one, so the API key needs
  no right to write scripts.
- Every action is idempotent: the same document id, the same deletion filter with the staleness condition, the same update. A
  replay after a crash changes nothing, and a deletion that arrives late cannot remove a row that was indexed again, because
  its `indexed_at` is newer.
- The outbox removes an entry only after `apply` returned, so delivery is at least once.
- A `429`, a `503`, or an item rejected with `es_rejected_execution_exception` raises `BackendBusyException`. The retry delay is
  the `Retry-After` header or five seconds. The outbox releases the batch without an attempt.
- A rejected document (mapping or parse error) fails its batch with the error text, so the attempts count and the entry becomes a
  dead letter after ten failures, as a database error does.
- Several replicas may drain the outbox at once, as today. Claims are exclusive in the database (`OutboxStore.claim`). Replicas
  of the same cluster and the same `searchEngine` agree by the registration rules.

### D7: reads, the access value and the limits

The query builder takes `ArtifactSearchQuery` and `SearchAccess` and returns a JSON tree.

- **Access.** `SearchAccess` is one filter. Each `RepositoryAccess` gives `term repository` and then its path condition: `All`
  gives nothing more; `Exact` gives `term path` or `prefix path "p/"`; `Subtree` gives the same with the joined segments; `Legacy`
  gives `prefix path_lc`; `Conjunction` gives `bool.filter`; `Never` gives `match_none`. `Precedence` is translated into the
  first-match rule: the path is visible when, for some allowed rule `i`, the condition of `i` holds and no earlier rule's
  condition holds. The repository-level filter of the whole value is a `should` of the repositories.
- **Words and coordinates** become `should` clauses with the rules of `add-database-full-text-search`.
- **Facets** are one `filter` aggregation per facet, each with the filters of the other kinds, as the database does.
- **Groups.** One `terms` aggregation on `gav` sized to the window (`page * size + size`, at most 10 100), with sub-aggregations
  for the display values, the maximum of `last_modified`, the sum of `size_bytes`, the file count and a `terms` on `repository`
  with `origin`. The plugin sorts the window with the comparator of the database engine (moved to core by
  `add-opensearch-search-plugin`) and slices the page.
- **Total.** The number of buckets of a `terms` aggregation sized to `TOTAL_CAP + 1`. The cluster's bucket limit (65 536) is above
  the cap.
- **Latest version.** A `terms` aggregation on `artifact_key` with a sub-aggregation on `version_lc`, then the shared `latestOf`.
- **Files** are `search` requests inside the result window, sorted by `path` and `repository`, which is unique.
- **Gap, deep pages.** A file page whose last row is beyond `index.max_result_window` (10 000) is refused with `400` and the hint
  to narrow the filters. The database has no such limit, so the contract suite marks this case as a named exception, and the
  exception is listed in the reference page. The alternative, `search_after` walking, is not used, because the paging parameter
  of the REST API is a page number.
- **Access values are logged** as counts of repositories and terms, never as paths.

### D8: consistency, identity and the cluster

- A write is visible after the refresh interval (one second). The contract suite calls `_refresh` only in its own fixtures and
  asserts the visibility through a bounded poll with an injected clock. Production code never calls `_refresh`.
- `indexIdentity()` returns the index UUID of the physical index, read from the index settings at open. The core resets the rebuild
  cursor when it changes. This is the additive method of `add-lucene-search-plugin` (D6 of its design), and the reason it is needed
  here is the same: an index recreated by an operator while the cursor sits in the database would otherwise skip rows.
- Health: `_cluster/health` `red` or an unreachable cluster is `Down`; `yellow` is `Up` with a message in the status block, because
  a single-node development cluster is always yellow.
- A replica that starts while the cluster is down is `OPEN_FAILED` and retries every 30 seconds. Its outbox is not drained until
  it opens.

### D9: circuit breaker and failures

The breaker of the OpenSearch plugin is used unchanged, shared through `reposilite-search-http` (D3). It opens after five
consecutive transport failures, reports `Down` for 30 seconds and then lets one request through (half-open). Its clock is injected.
A busy answer does not count as a transport failure.

### D10: security

- The cluster holds the paths of every repository, including `HIDDEN` and `PRIVATE` ones, because visibility is applied at query
  time. The cluster is therefore a sensitive store. Ingot's responsibility is its own filters: no row outside the caller's access
  value is returned, and the contract suite proves it for the translated queries. The core check of registration D6 counts any row
  that fails it, and the count must stay zero.
- The operator's responsibility, which the reference page states. An API key with the cluster privilege `monitor` (for the health
  check) and, on the index pattern `<prefix>-*` and the alias `<prefix>`, the index privileges `create_index`, `read`, `write`,
  `delete` and `manage` (for the alias switch and the index settings read at open). No other index, no `manage_security`, no
  `all`. The privileges are verified against the pinned minor version in the container tests (task 5.1). Network policy so that only Ingot replicas reach the cluster. TLS. Audit logging on the
  cluster. Rotation of the API key, which the file form makes possible without a restart of the cluster.
- Ingot's administration page shows no URL, user name, key or password of the cluster (registration D4).

### D11: testing

- Unit tests, no network and no clock reads: the variable rules and the fallback; the URL, key and file rules; the golden JSON of
  every query shape; the translation of `SearchAccess` against the table of `SearchAccessParityTest`; the first-match rule of
  `Precedence`; the bulk body builder with its limits; the mapping of `429` and of items to `BackendBusyException`; the breaker
  with an injected clock; the redaction of error texts; the version gate of D2.
- The contract suite of the seam, unchanged, in the integration source set, once for Elasticsearch 8.x and once for 9.x, each
  against a secured container started through Testcontainers' Elasticsearch module with a certificate of a test authority that the
  test `SSLContext` trusts (the production code reads the JVM default). Both run through `heavy` with `--max-workers=2`.
- Integration cases: a restart of the container in the middle of an outbox drain, after which the unacknowledged entries are
  delivered once and no document is missing or duplicated; a physical index replaced by an operator, which changes
  `indexIdentity()` and resets the cursor; a rejected document; a cluster answering `429`.
- Every test is F.I.R.S.T.: no sleeps (the poll uses the injected clock), no fixed ports (the container maps a random port), a
  temporary directory per test, and an index name with a random suffix per test class.

## Gaps against the seam

| Method or rule | Gap | What the plugin does |
|---|---|---|
| File pages | Result window of 10 000 rows | Refuses the page with `400` and the hint; named exception in the contract suite |
| Word matching | Analyser of the cluster, not the database | The token field is written by the plugin with the rules of the database; the contract suite compares words |
| Visibility | About one second behind the write | The status reports the refresh interval; the verify job reads the same engine it compares with |
| Cluster replicas | Several replicas drain together | Claims are exclusive in the database; the writes are idempotent |

## Risks

- **Group sorting in memory** is bounded by the window of 10 100 buckets. A page deep in the window fetches many buckets. The
  benchmark of `add-database-full-text-search` runs against this plugin, and the numbers go into the pull request.
- **Version drift.** A new minor can change a response shape. The matrix runs the latest minor of each major, and the golden files
  are regenerated only by a reviewed change.
- **Licence drift.** Elastic may change the licence options again. The reference page names the licence the operator is under and
  the plugin does not depend on any Elastic artifact, so a change affects only the operator's own cluster.

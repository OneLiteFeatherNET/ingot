# Design

This design depends on the three changes before it in the series: `extract-search-backend-spi` (the seam),
`add-database-full-text-search` (tokens and word cases) and `add-search-backend-registration` (the public types, the event and the
states). Names below are those of those changes.

## Context

- The database is the only backend today; its read path is in `DatabaseArtifactSearchEngine` and its write path in
  `DatabaseArtifactIndex` (see the earlier changes for their new locations).
- The plugin loader gives every plugin jar its own `URLClassLoader` whose parent is the server class loader
  (`com/reposilite/plugin/PluginLoader.kt:71`, `:104`). A library bundled into the plugin jar can clash with the server's Jackson or
  HTTP classes of the same package. This decides the client choice below.
- Bundled jars follow a switch each, in `PluginLoader.BUNDLED_SWITCHES` (`PluginLoader.kt:166-168`), with the `INGOT_` name winning
  over the `REPOSILITE_` name (`PluginLoader.kt:130-138`). The nexus import uses the same mechanism.
- Artifact search spec 7.4 describes an external engine as eventually consistent, with the verify job reading the engine it
  compares against (`docs/superpowers/specs/2026-10-16-artifact-search.md:1372-1390`). This design follows that section.
- The image sets no `USER` and the entrypoint handles privileges, so nothing in this plugin depends on the process user.
- The `INGOT_EXTRA_CA_CERTS` change extends the JVM trust store that the image builds at start, so the default `SSLContext` of the
  server already trusts an operator's internal certificate authority (`add-image-extra-ca-bundle`, design, the copy of `cacerts`).

## Decisions

### D1: plain HTTP with the JDK client and the server's Jackson

The plugin speaks the OpenSearch REST API with `java.net.http.HttpClient` and the Jackson `ObjectMapper` the server already has. It
does not add the OpenSearch Java client. The reasons:

- the client library brings its own transport, JSON mapper and HTTP stack, which would clash with the server's copies in the
  plugin's class loader (`PluginLoader.kt:71`);
- the API surface the plugin uses is small: `_bulk`, `_search`, `_count`, `_mget`, `_delete_by_query`, `_update_by_query`,
  `_aliases`, the index create call and `_cluster/health`;
- the request bodies are built as JSON trees, so a golden-file test pins each query shape without a network.

If the maintainers prefer the official client, the replacement is confined to one file of the plugin (`OpenSearchHttp`), and the
contract suite is the same.

### D2: configuration and secrets

Every variable has the `INGOT_OPENSEARCH_` prefix, and the `REPOSILITE_OPENSEARCH_` spelling is read as the fallback, as the
drop-in rule requires. `INGOT_` wins when both are set.

| Variable | Default | Meaning |
|---|---|---|
| `INGOT_OPENSEARCH_ENABLED` | `false` | The bundled switch. `false` loads nothing. |
| `INGOT_OPENSEARCH_URLS` | none | Comma-separated base URLs, `https://` only. Required when enabled. |
| `INGOT_OPENSEARCH_ALLOW_HTTP` | `false` | Accepts `http://` URLs. Logged as a warning at every start. |
| `INGOT_OPENSEARCH_INDEX` | `ingot-artifacts` | Name prefix of the index and the alias. Lower case, letters, digits, `-` and `_`, 1 to 64 characters. |
| `INGOT_OPENSEARCH_USERNAME` | none | Basic authentication user. |
| `INGOT_OPENSEARCH_PASSWORD` | none | Password. Also read from the file `INGOT_OPENSEARCH_PASSWORD_FILE`; the plain variable wins when both are set. |
| `INGOT_OPENSEARCH_BULK_SIZE` | `500` | Actions per bulk request, 1 to 5000. |
| `INGOT_OPENSEARCH_TIMEOUT_SECONDS` | `30` | Timeout of one request, 1 to 300. |
| `INGOT_OPENSEARCH_REPLICAS` | `1` | Replicas of the index. |

Rules: a URL with user information (`https://user:pass@host`) is refused at start, naming the variable and not the value. The password
is never written to a log, a status response, a problem body or the support bundle; the plugin reports only "password set" or "not set".
A missing username with a password, or the reverse, is refused. A missing username without a password is accepted with a warning, for
clusters that run without security. The file form follows the rule of `add-file-secrets`: the file is read once at start, trailing
line breaks are removed, and an unreadable file stops the start of the plugin with a message that names the variable.

TLS: the plugin uses the default `SSLContext` of the JVM with hostname verification on. There is no option to skip verification. An
internal certificate authority is trusted through `INGOT_EXTRA_CA_CERTS`, which the image applies to the JVM trust store.

### D3: index layout and the alias

- The physical index is `<prefix>-v1`, created by `open()` when it does not exist. The alias `<prefix>` points at it. Readers and
  writers use the alias only, so a later physical index can take its place with one atomic `_aliases` call.
- The index metadata holds `ingot_mapping_version: 1`. `open()` refuses an index whose version differs and reports `OPEN_FAILED` with
  the version it found. Automatic migration is not part of this change.
- A mapping change, when it comes, creates `<prefix>-vN`, runs a full rebuild into it while the outbox writes to both indexes, and
  switches the alias at the end. The procedure is written in the reference page. Its implementation waits for the first change of
  the mapping, and no code path in this change needs it.
- Mapping `dynamic: strict`. Fields: `repository`, `path`, `path_lc` (lower-case normalizer), `group_id`, `group_id_lc`,
  `artifact_id`, `artifact_id_lc`, `version`, `version_lc`, `gav` (the GAV key of `search_token`), `artifact_key` (group and artifact,
  lower case), `classifier`, `extension`, `snapshot` (boolean), `origin`, `source_host`, `size_bytes` (long), `last_modified` (long),
  `indexed_at` (long), `sha1`, `sha256`, `tokens` (keyword, multi-valued). All keywords are `keyword` with `doc_values`, except
  the strings that are only displayed (`group_id`, `artifact_id`, `version`), which are `keyword` with `doc_values: false`.
- Document id: the path hash of the database (`pathHash`, the SHA-256 of repository, NUL and path). Writing the same file twice
  replaces one document.
- Settings: one shard, `INGOT_OPENSEARCH_REPLICAS` replicas, `refresh_interval` at its default of one second. The shard count is not
  configurable in version one; a larger index is a later decision with data behind it.

### D4: writes, idempotency and back pressure

- `apply(actions)` keeps the order of the actions. Consecutive upserts and removals of single paths go in `_bulk` requests of at most
  `BULK_SIZE` actions and 5 MB. A prefix removal runs `_delete_by_query` with `conflicts: proceed`, a filter on the repository, on
  `path` (the path itself or `prefix/` as a prefix) and on `indexed_at <= notAfter`. A reorigin runs `_update_by_query` with a stored,
  fixed Painless script whose parameters are the origin and the source host; no value from a request enters the script text.
- Every action is idempotent: the same document id, the same deletion filter, the same update. A replay after a crash changes nothing.
- The outbox removes an entry only after `apply` returned (`OutboxWorker`, as today). The order "write, then delete the entry" gives
  at-least-once delivery. A replay of a batch whose acknowledgement was lost is harmless (D4 above).
- One bulk request is in flight per replica. A `429` or `503` of the cluster, or an item rejected as "too many requests", raises
  `BackendBusyException` with the `Retry-After` header or 5 seconds, and the outbox releases the batch without an attempt.
- A circuit breaker opens after five consecutive transport failures, reports `DOWN` for 30 seconds, and then lets one request through
  (half-open). Its clock is injected.
- A rejected document (mapping or parse error) fails its batch with its error text, so the outbox counts attempts and the entry becomes
  a dead letter after ten failures, as a database error would.

### D5: reads and the access value

The query builder takes `ArtifactSearchQuery` and `SearchAccess` and returns a JSON tree. Its results are compared with the database
engine by the contract suite; the golden tests pin the shape.

- **Access.** `SearchAccess` becomes one filter. Each `RepositoryAccess` gives `term repository` and then its path condition:
  `All` gives nothing more; `Exact` gives `term path` or `prefix path "p/"`; `Subtree` gives the same with the joined segments; `Legacy`
  gives `prefix path_lc`; `Conjunction` gives `bool.filter`; `Never` gives `match_none`. A `Precedence` list is translated into the
  first-match rule: the path is visible when, for some allowed rule `i`, the condition of `i` holds and no earlier rule's condition
  holds. The compiler's term cap (1000) bounds the size of the tree. The repository-level filter of the whole value is the `should` of
  the repositories.
- **Free text and coordinates** become `should` clauses, one per word, with the rules of `add-database-full-text-search`: a prefix on
  `group_id_lc`, a prefix on `artifact_id_lc`, a term on `version_lc`, or a `bool.filter` of one `prefix` on `tokens` per part.
- **Facets** are one `filter` aggregation per facet, each with the filters of the other kinds, as the database does (`SearchFilters`
  predicate with `except`).
- **Groups** are one `terms` aggregation on `gav` sized to the window (`page * size + size`, at most 10 100), with sub-aggregations for the
  minimum display values, the maximum of `last_modified`, the sum of `size_bytes` and the file count, and a `terms` on `repository` with
  `origin`. The plugin sorts the window in memory with the comparator the database uses. The comparator is one function shared with the
  database engine (moved to core in the same change), so the tie order is the same on both. Then the plugin slices the page.
- **Total** is the number of buckets of a `terms` aggregation sized to `TOTAL_CAP + 1`, which is exact up to the cap. The bucket limit of
  the cluster (`search.max_buckets`, 65 536 by default) is above the cap.
- **Latest version** is a `terms` aggregation on `artifact_key` with a sub-aggregation on `version_lc` of size 5001, then the same
  `latestOf` function the database uses, moved to core.
- **Files** are `search` requests with `from` and `size` inside the window, sorted by `path` and `repository`. The pair is unique,
  because the database key is `(repository, path)`, so no tie-breaker is needed.
- **Index status** of the response is computed by core from the health and the outbox, as today. The backend adds its refresh interval
  as a field of its own status block.

The access value never reaches the query log in full: the plugin logs the number of repositories and terms, not the paths.

### D6: consistency and the cluster

- Writes are visible after the refresh interval (one second). The contract suite calls `_refresh` only in its own fixtures, never in
  production code, and it asserts the eventual visibility through a bounded polling loop with the injected clock. Search results can
  lag by about a second behind a deploy. The status reports the interval.
- Every replica may drain the outbox, as today, because claims are exclusive in the database (`OutboxStore.claim`). All replicas must
  have the same `searchEngine` and the same URLs; the backend agreement of `add-search-backend-registration` refuses a replica that
  disagrees.
- A replica that starts while the cluster is down is `OPEN_FAILED` and retries every 30 seconds. Its outbox is not drained until it opens.

### D7: security

- The OpenSearch index holds the paths of every file of every repository, including repositories that are `HIDDEN` or `PRIVATE`, because
  visibility is applied at query time. The cluster is therefore a sensitive store. Ingot's responsibility ends at its own filters: it
  never returns a row outside the caller's access value, and the contract suite proves that for the translated queries.
- The operator's responsibility, which the reference page states: a dedicated OpenSearch user that can read and write only the index
  pattern `<prefix>-*` and the alias; no access from dashboards users; network policy so that only Ingot replicas reach the cluster; TLS;
  audit logging on the cluster; rotating the password, which the file form makes possible without a restart of the cluster.
- Ingot's own administration (`/api/maven/search/index`) shows no URL, user name or password of the cluster.
- The stored script of D4 is a constant in the plugin. No request value enters any script.

### D8: testing

- Unit tests, no network and no clock reads: the variable rules and their fallback; the URL and file rules; the golden JSON of every
  query shape; the access translation against the table of `SearchAccessParityTest`; the first-match rule for `Precedence`; the bulk
  body builder with its size limits; the busy and rejected mapping of responses; the circuit breaker with an injected clock; the
  redaction of error texts.
- Contract suite of the seam, unchanged, in the integration source set against a container of OpenSearch 2.x with the security plugin
  on and a certificate of a test authority trusted by the test `SSLContext` (the test injects it; the production code reads the JVM
  default). The container is started through `heavy` and runs in one Gradle worker.
- A restart test: the container is stopped in the middle of an outbox drain and started again; the entries that were not acknowledged are
  delivered once more, and the count of documents equals the count of rows.
- The image is pinned by Renovate, as the other test containers are; its version is at least two weeks old when pinned.

## Risks

- **Group sorting in memory** is bounded by the window of 10 100 buckets, but a page deep in the window fetches many buckets. The
  benchmark of `add-database-full-text-search` runs again against this backend, and the numbers go into the pull request.
- **Eventual consistency** can show a deleted file for about a second. The contract suite allows the lag, and the verify job repairs
  real drift.
- **Plugin size and startup.** The jar is small, and it loads only with its switch. Startup time with the switch on is measured in the
  container job.
- **Elasticsearch users** will ask for it. The seam allows a plugin of their own today; an official one needs its own change, because
  its wire protocol and its security defaults differ.

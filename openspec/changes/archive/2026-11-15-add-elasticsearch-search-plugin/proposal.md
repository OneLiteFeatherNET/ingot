# Proposal

## Open maintainer questions

1. **Supported majors at launch.** Default: Elasticsearch 8.x and 9.x, each tested at its latest minor. A major leaves the
   test matrix when Elastic ends its support for it, in a release note.
2. **Basic authentication next to the API key.** Default: both are accepted, the API key is preferred and documented first.
   Configuring both at once is refused.
3. **Sharing the query builder with the OpenSearch plugin.** Default: no. The transport, the security rules, the circuit
   breaker and the redaction are shared (design D3). The query translation is copied and tested against its own golden files,
   because the aggregation and paging details differ by version.
4. **Name of the plugin in the store and in the docs.** Default: the identifier `elasticsearch` and the product name in the
   text, with a sentence that Ingot is not affiliated with Elastic. The jar and the docs use nominative use only.
5. **Extraction of the shared helpers.** Default: a separate `refactor(search)` pull request, merged after the OpenSearch code
   and before this change's code pull request. This proposal does not perform it.
6. **Index identity in the backend contract.** Default: the additive method `indexIdentity()` of `add-lucene-search-plugin`
   is used here too, so a rebuild cursor is reset when the physical index is replaced.
7. **Index creation rights.** Default: the plugin creates its own index and alias only. It never creates index templates,
   lifecycle policies or ingest pipelines.

## Why

The OpenSearch plugin (`add-opensearch-search-plugin`) answers an operator who already runs OpenSearch. Many operators run
Elasticsearch instead, and they expect the same search behind the same interface. The OpenSearch proposal kept Elasticsearch
out of scope because its wire protocol and its security defaults differ: Elasticsearch 8 and later ship with TLS and
authentication on, they authenticate with API keys as well as basic credentials, and their release and licence history
differs from OpenSearch. Those differences need their own decisions, so they get their own change.

## What Changes

- **A plugin `elasticsearch-plugin`** in `reposilite-plugins/elasticsearch-plugin`. It registers the factory `elasticsearch`
  through `ArtifactSearchBackendRegistrationEvent` (`add-search-backend-registration`). The jar is not bundled in the image. It
  is installed from the plugin store (`add-plugin-store`) or copied by hand into the plugins directory. Installing the jar is the
  opt-in; no bundled switch is added.
- **Selection stays explicit.** An instance uses Elasticsearch when `INGOT_LOCAL_SEARCHENGINE=elasticsearch` is set on every
  replica. The default `database` is unchanged.
- **Configuration through environment variables only.** The URLs, the index name prefix, an API key or a user and password, the
  bulk size, the timeout and the replica count. Each variable has the `INGOT_ELASTICSEARCH_` name and the `REPOSILITE_ELASTICSEARCH_`
  spelling is read as the fallback. Secrets are read from `..._FILE` variables. No value of this plugin is stored in the shared
  settings.
- **Transport and TLS.** Plain JDK HTTP with the server's Jackson, no Elastic client library. HTTPS only by default. Hostname
  verification is on and cannot be switched off. An internal authority is trusted through `INGOT_EXTRA_CA_CERTS`, which
  `add-image-extra-ca-bundle` applies to the JVM trust store. The authority that Elasticsearch writes for its own HTTP layer is
  one such certificate.
- **Index layout.** One physical index `<prefix>-v1` behind the alias `<prefix>`, a strict mapping and the document id of the
  database path hash. The layout is the one of the OpenSearch plugin, so the two plugins can be compared directly.
- **Queries and writes.** Search, facets, files, status and the write path run as Elasticsearch requests. The caller's access
  value is a filter of the query. Outbox entries are removed only after the cluster acknowledged them. Writes are idempotent.
  A busy cluster releases the batch without counting an attempt.
- **Tests.** Unit tests of the configuration, the request shapes and the error mapping with no network. The contract suite of
  `extract-search-backend-spi` runs against a secured Elasticsearch 8.x container and a secured 9.x container, both through
  `heavy`.
- **Documentation.** A reference page with every variable, the index layout, the privileges an API key needs, the security
  responsibilities and the upgrade notes; a how-to for switching a running instance from the database.

### Out of scope

- OpenSearch, which is `add-opensearch-search-plugin`, and Elasticsearch 7 or older. Elasticsearch 7 uses other security
  defaults and is refused at open.
- Bundling the plugin into the image or a switch for it. The image does not change.
- A Helm chart option. Operators pass the variables through `extraEnv`.
- Cross-cluster search, cross-instance search, cross-cluster replication and per-tenant indexes.
- Index templates, lifecycle policies, snapshots and ingest pipelines. Those are the operator's cluster configuration.
- Indexing POM fields or file contents, relevance ranking and vector search.
- Automatic deletion of an index when the backend is switched back to `database`.
- Mapping migrations beyond the first mapping version. The procedure is written down; its implementation waits for the first
  mapping change.
- Deep file pages beyond the index result window (see design D7); they are answered with a clear error, not fixed here.

## Capabilities

### New Capabilities

- `elasticsearch-search-backend`: the plugin, its variables and secrets, the transport and TLS rules, the index layout, the query
  translation, the write path with its idempotency and back pressure, the access rule and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)`,
  `ci(search)` and `docs(search)`. The pull request title is:

  `feat(search): add an elasticsearch backend plugin`

- **Breaking marker.** No BREAKING item. Operators change nothing unless they opt in: they install the jar and set
  `INGOT_LOCAL_SEARCHENGINE=elasticsearch` with the plugin's variables. An installation that does neither keeps the database
  as the search engine, and the image, the volumes, the manifests and the existing variables are untouched.
- **Build and image.** A Gradle subproject `:reposilite-plugins:elasticsearch-plugin`, its shadow jar written next to the other
  plugin jars, and no line in `Dockerfile`. The jar is not added to `PluginLoader.BUNDLED_SWITCHES`.
- **Distribution.** The jar reaches operators through `add-plugin-store`. This change depends on that proposal for signed
  distribution and for the release process. Until the store exists, the jar is attached to the GitHub release and copied by
  hand, and the docs say so.
- **CI.** A job "Search backends" runs the Elasticsearch contract tests for 8.x and 9.x on pull requests that touch the plugin
  or the search backend, and nightly otherwise.
- **Order.** Depends on `add-opensearch-search-plugin` (its code is the reference for the shared helpers), on
  `add-search-backend-registration` (the event, the states and the agreement), on `extract-search-backend-spi` (the seam and the
  contract suite) and on `add-database-full-text-search` (the word cases). Uses `add-image-extra-ca-bundle` and
  `add-file-secrets`, and it does not wait for either: without them the plugin reads the JVM trust store and its own file reader.

## Verification

- The contract suite passes on the database, on OpenSearch and on Elasticsearch 8.x and 9.x, with the same expected values.
- Integration tests with a secured container: restart of the cluster while the outbox has entries, a replaced physical index
  (identity change), a rejected document, and a cluster that answers `429`.
- The container job's upgrade rehearsal keeps passing with the plugin absent.
- `openspec validate add-elasticsearch-search-plugin --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-elasticsearch-search-plugin` prints nothing.

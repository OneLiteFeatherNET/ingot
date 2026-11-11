# Proposal

## Why

The database index answers search well for an instance of a few million files on a database the operator already runs. Some
operators run Ingot for much larger repositories, or already operate an OpenSearch cluster and want search there, close to their
logs and dashboards, so that the database is not the search engine. They ask for that, and the search spec anticipated it
as a plugin behind the same interface (`docs/superpowers/specs/2026-10-16-artifact-search.md`, section 7.4).

The first external backend is the one that proves the seam. OpenSearch is the choice because it is Apache 2.0 licensed, like
Ingot, and it is the search service a self-hosting operator is most likely to already have. Elasticsearch is out of scope (see
the design).

## What Changes

- **A bundled plugin `opensearch-plugin`**, built from `reposilite-plugins/opensearch-plugin`, that registers the factory `opensearch`
  through the registration event of `add-search-backend-registration`. The jar ships in the image next to the other bundled
  plugins and loads only when `INGOT_OPENSEARCH_ENABLED=true`.
- **Selection is explicit.** An instance uses OpenSearch when both `INGOT_OPENSEARCH_ENABLED=true` and
  `INGOT_LOCAL_SEARCHENGINE=opensearch` are set on every replica. The database index is not written while OpenSearch is the
  backend.
- **Configuration through environment variables only**: the URLs, an index name prefix, a user and a password (or a password
  file), the bulk size, the timeout and the replica count. No value of this plugin is stored in the shared settings.
- **Index layout.** One index per mapping version behind an alias that readers and writers use. The mapping is strict. Each
  file is one document whose id is the path hash used by the database.
- **Queries.** Search, facets, the file list and the status run as OpenSearch queries. The access value of the caller becomes a
  filter in the query, never a filter applied after the page is read.
- **Back pressure and at-least-once delivery.** Outbox entries are removed only after OpenSearch acknowledged them. Writes are
  idempotent. A busy cluster slows the drain without consuming attempts.
- **Tests.** The contract suite of `extract-search-backend-spi` runs against a real OpenSearch container in the integration source
  set, through `heavy`. Unit tests cover the translation of queries and access values with no network.
- **Documentation.** A how-to for choosing and enabling OpenSearch, a reference page with every variable, the index layout and the
  security responsibilities, and the upgrade notes.

### Out of scope

- Elasticsearch and OpenSearch 3.x. Evaluated later as separate changes with their own proposals.
- A Helm chart option for OpenSearch. Operators pass the variables through `extraEnv`; a chart change is a follow-up.
- Cross-instance search and per-tenant indexes.
- Indexing POM fields or file contents.
- Relevance ranking and vector search.
- Automatic deletion of an index when the backend is switched back to `database`.
- Mapping migrations beyond the first mapping version. The procedure is written down; its implementation waits for the first
  mapping change.

## Capabilities

### New Capabilities

- `opensearch-search-backend`: the bundled plugin, its variables and secrets, the index layout, the query translation, the write
  path with its idempotency and back pressure, and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)`,
  `ci(search)` and `docs(search)`. The pull request title is:

  `feat(search): add an opt-in opensearch backend plugin`

- **Breaking marker.** No breaking change. The plugin is off by default, so an installation that does not set
  `INGOT_OPENSEARCH_ENABLED` sees no change, and the database remains the search engine. The image gains one jar, which it loads
  only when the switch is on. No existing configuration key, variable, volume, manifest or route changes.
- **Build and image.** A new Gradle subproject `:reposilite-plugins:opensearch-plugin`, a line in `Dockerfile` that copies its jar
  to `/app/bundled-plugins`, and an entry in `PluginLoader.BUNDLED_SWITCHES` with the switch.
- **CI.** A job "Search backends" runs the OpenSearch contract tests on pull requests that touch the plugin or the search backend,
  and nightly otherwise.
- **Order.** Depends on `add-search-backend-registration` (the event and the public types), on `add-database-full-text-search` (the
  tokens that OpenSearch stores and the contract cases of words), and on `extract-search-backend-spi`. It uses
  `add-image-extra-ca-bundle` for the trust store and `add-file-secrets` for the password file, and it does not wait for either:
  without them the plugin uses the JVM trust store and its own file reader, which the design describes.

## Verification

- The contract suite passes on the database and on OpenSearch with the same cases.
- Integration tests with a secured OpenSearch container, including a restart of the cluster while the outbox has entries.
- The container job's upgrade rehearsal keeps passing with the plugin switched off.
- `openspec validate add-opensearch-search-plugin --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-opensearch-search-plugin` prints nothing.

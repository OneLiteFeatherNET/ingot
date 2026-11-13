# Proposal

## Open maintainer questions

1. **Ship a backend with named contract exceptions.** Default: yes, marked experimental in the reference page. Meilisearch has no
   grouping, no string cursors and no exact totals above a configured limit, so the contract suite has named exceptions (design, the
   gap register). Each exception is a test that states its reason and is not skipped silently. If the maintainers want the full
   contract, the plugin is not shipped and this change is closed.
2. **Legacy access predicates.** Default: a caller whose access value contains a `Legacy` predicate (a plain string prefix without a
   path boundary) gets `503` with the reason "legacy grants are not supported by the meilisearch backend". Meilisearch cannot express a
   plain string prefix in a filter, and the alternative (fetch candidates and drop rows in core) returns short pages and wrong totals.
3. **Limit for the group sorts by modification and size.** Default: 100 000 matched files per request. Above it the request is answered
   `503` with the hint to narrow the filters. Both sorts are aggregates over a group, and Meilisearch sorts files, not groups.
4. **Total limit of the index.** Default: `pagination.maxTotalHits` of 100 000. File totals are exact up to it, and the group total is
   exact up to the cap of 10 100. The value is an index setting, so it applies to every search of the index, and the plugin's index
   must not be shared with other applications.
5. **Facet limit.** Default: `faceting.maxValuesPerFacet` of 10 000. A facet with more values than that is truncated, and the reference
   page says so.
6. **Settings changed by the plugin.** Default: yes, on its own index only, and each change is logged with its old and new value. The
   alternative is to refuse to open until the operator applies the settings, which is more work for operators with no gain in safety.
7. **Typo tolerance.** Default: disabled. Enabled, Meilisearch matches words the database does not, and the contract cannot hold.
8. **Master key.** Default: the reference page lists the minimal actions of the key (design D9). The plugin cannot detect a master key
   without a privileged call, so it does not check, and it says so.
9. **Plain HTTP.** Default: refused unless `INGOT_MEILISEARCH_ALLOW_HTTP` is `true`, with the same rules as the other plugins.

## Why

Meilisearch is a search engine that many self-hosting operators already run for their own applications. It is MIT licensed in its
community edition, it has a small HTTP API, and it is simple to operate. An operator who runs it already has a secured service and a
key handling routine, and an Ingot backend on it saves them a second search technology. The seam of `extract-search-backend-spi`
was designed to take such a backend without changes to the core.

Meilisearch is not a relational engine. It has no grouping, its paging is offset based, it counts with a limit, and its writes are
asynchronous tasks. Each of these differs from the database contract, and this change does not hide the differences. The design maps
every method of the seam to a Meilisearch feature, and it names each place where the mapping is weaker.

## What Changes

- **A plugin `meilisearch-plugin`** in `reposilite-plugins/meilisearch-plugin`, with its own HTTP client on the JDK HTTP client and the
  server's Jackson, and no Meilisearch client library. It registers the factory `meilisearch` through
  `ArtifactSearchBackendRegistrationEvent` (`add-search-backend-registration`). The jar is not bundled in the image; it is installed from
  the plugin store (`add-plugin-store`) or copied by hand.
- **Selection stays explicit.** An instance uses Meilisearch when `INGOT_LOCAL_SEARCHENGINE=meilisearch` is set on every replica. The
  default `database` is unchanged.
- **Configuration through environment variables only.** The URL, the index name, an API key or a file holding it, the timeouts, the
  batch size and the bound of the task wait. Each has the `INGOT_MEILISEARCH_` name and the `REPOSILITE_MEILISEARCH_` spelling as the
  fallback.
- **Writes as tasks, awaited.** Every write returns a task; the plugin waits for the task within a bound before `apply` returns, so the
  outbox entry is removed only after Meilisearch finished the task. A task that is still running after the bound releases the batch
  without an attempt.
- **Queries on the Meilisearch engine.** Search, facets, files, status and the write path are Meilisearch requests with the caller's
  access value as a filter. Groups are computed by the plugin from file documents, because Meilisearch has no grouping.
- **Tests.** The contract suite of `extract-search-backend-spi` runs against a fake Meilisearch in-process for the contract cases and
  against a real Meilisearch container in the integration source set for the task behaviour. The fake has no sleeps; its task completion
  is driven by the test.
- **Documentation.** A reference page with every variable, the index layout, the gap register, the key actions, the security
  responsibilities and the named exceptions; a how-to for switching a running instance.

### Out of scope

- The Meilisearch enterprise edition, its sharding and its replicas. The plugin targets the community edition and one index per
  instance.
- A Meilisearch client library. The HTTP surface is small, and a library would bring a second HTTP stack into the plugin class loader.
- Legacy grants in filters (open question 2), and approximate answers for them.
- Bundling the plugin into the image, or a switch for it. The image does not change.
- Typo tolerance, synonyms, ranking rules and relevance tuning.
- Migration of the index between plugin versions beyond the first mapping version, which is written down and not implemented.
- Searching POM fields or file contents.

## Capabilities

### New Capabilities

- `meilisearch-search-backend`: the plugin, its variables and secret, the index layout and settings, the task handling and its bounds, the
  query translation with its limits, the group computation, the write path and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)`, `ci(search)`
  and `docs(search)`. The pull request title is:

  `feat(search): add a meilisearch backend plugin`

- **Breaking marker.** No BREAKING item. Operators change nothing unless they opt in: they install the jar and set
  `INGOT_LOCAL_SEARCHENGINE=meilisearch` with the plugin's variables. The image, the volumes, the manifests and the existing variables are
  untouched, and the database stays the default.
- **Build and image.** A Gradle subproject `:reposilite-plugins:meilisearch-plugin`, its shadow jar written next to the other plugin jars,
  and no line in `Dockerfile`. The jar is not added to `PluginLoader.BUNDLED_SWITCHES`.
- **Distribution.** Through `add-plugin-store`, which this change depends on for signed distribution. Until the store exists, the jar is
  attached to the release and copied by hand.
- **Shared code.** The environment and secret rules, the URL rules, the JSON client with its circuit breaker and the redaction come from
  `reposilite-search-http`, the module extracted from the OpenSearch plugin (`add-elasticsearch-search-plugin`, section 0). The gap
  register and the query builder are specific to this plugin.
- **CI.** A job "Search backends" runs the in-process contract suite on every pull request that touches the plugin, and the container
  tests for the Meilisearch version pinned by Renovate on pull requests that touch the plugin or the search backend, and nightly otherwise.
- **Order.** Depends on `add-search-backend-registration`, `extract-search-backend-spi`, `add-database-full-text-search` (the word cases),
  `add-plugin-store` (distribution only) and `add-elasticsearch-search-plugin` section 0 (the shared module). It uses the additive method
  `indexIdentity()` of `add-lucene-search-plugin`, and it does not wait for `add-image-extra-ca-bundle` or `add-file-secrets`.

## Verification

- The contract suite passes on the database and on Meilisearch, with the same expected values except the named exceptions, each of which
  is listed in the reference page with its reason.
- Container tests with a Meilisearch container: a restart during a drain, a task that fails, a task that outlives the wait, a settings
  change that reindexes, and an index replaced by the operator.
- The container job's upgrade rehearsal keeps passing with the plugin absent.
- `openspec validate add-meilisearch-search-plugin --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-meilisearch-search-plugin` prints nothing.

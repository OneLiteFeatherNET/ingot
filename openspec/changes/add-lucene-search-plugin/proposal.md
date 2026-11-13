# Proposal

## Open maintainer questions

1. **Ship the Lucene backend at all, given that it serves one writer only.** Default: yes, documented as a single-replica backend
   for installations that want search without any other service. The refusal of more replicas is specified in design D4, and the
   reference page says it first.
2. **Persistent volume.** Default: documented, not enforced. Without a volume the index lives in the container's writable layer or
   an `emptyDir`, so every restart is a full rebuild. The plugin logs one warning at start when the index is empty, and does not
   refuse to start.
3. **Quarantine of a corrupt index.** Default: no automatic action. A corrupt index stays in place, the plugin reports `OPEN_FAILED`
   with the path and the exception class, and the operator moves or deletes the directory. Automatic renaming to
   `*.corrupt-<epoch>` is a follow-up with its own decision.
4. **Free-space floor.** Default: 1 GiB, set by `INGOT_LUCENE_MIN_FREE_BYTES`. Below it the plugin stops adding to the index and
   answers busy, so it never takes the last space of a volume that also stores repositories.
5. **Index identity in the backend contract.** Default: this change adds the additive method `indexIdentity(): String? = null` to
   `ArtifactSearchBackend` (design D6). It is the first code change that needs it, so it is part of this change's first `feat(search)`
   commit. The Elasticsearch and Meilisearch changes reuse it.
6. **Lease primitive.** Default: the lease API of `fix-job-leases` (the claim table with takeover after expiry) with a fencing epoch
   added there. The alternative is the `ingot_lock` lease of the HA design, which is a schema-lock primitive and would need its own
   key space. The choice must be made before the code pull request starts.
7. **Lucene version pin.** Default: the latest 10.x minor at code time, and at least two weeks old, pinned by Renovate. Lucene 9 is
   not supported.

## Why

Some operators want artifact search but do not run a database that answers it well, nor a search cluster. The database index
(`search` tables) is the default and it works for a few million files. A second service is a burden for a single instance of a
self-hosted Ingot. An embedded index on the local disk of the one replica gives search with nothing to operate: no network
hop, no credentials, no cluster to secure. Operators who need more replicas use the Elasticsearch, OpenSearch or Meilisearch
backends instead.

The index is a cache of the database, so it can be rebuilt. That makes an embedded index a sound choice for one writer. It is
not a sound choice for several writers, because each replica would keep its own copy and nothing reconciles them. The change is
therefore built around one writer, and it says what happens when a second replica appears.

## What Changes

- **A plugin `lucene-plugin`** in `reposilite-plugins/lucene-plugin`, using Apache Lucene 10.x. It registers the factory `lucene`
  through `ArtifactSearchBackendRegistrationEvent` (`add-search-backend-registration`). The jar is not bundled in the image. It is
  installed from the plugin store (`add-plugin-store`) or copied by hand into the plugins directory.
- **Selection stays explicit.** An instance uses the Lucene index when `INGOT_LOCAL_SEARCHENGINE=lucene` is set. The default
  `database` is unchanged.
- **Index on local disk.** The index lives under the data directory of the server (`/app/data/search/lucene` in the image), or at
  `INGOT_LUCENE_DIRECTORY`. No external service, no network, no credentials.
- **One writer.** Exactly one replica holds the writer lease at a time. Only the holder drains the outbox, writes the index and
  answers search. Every other replica reports the backend as down with the reason and never writes. Design D4 gives the rules,
  including the takeover and the rebuild that a takeover needs.
- **Crash-safe writes.** Each applied batch is one Lucene commit, made before the outbox entries are removed, so a crash, a
  restart or a power loss replays the unacknowledged entries and never loses one.
- **Access and queries.** Every query carries the caller's access value as a filter of the Lucene query. The core check of
  `add-search-backend-registration` still runs on every returned row.
- **Tests.** The contract suite of `extract-search-backend-spi` runs in-process against the plugin on a temporary directory. The
  tests of the lease, the takeover and the fencing use a fake lease store and an injected clock. No container is needed.
- **Documentation.** A reference page with the variables, the index location, the single-writer rule and what to do with two
  replicas; a how-to for switching a running instance from the database.

### Out of scope

- More than one replica answering search. A shared index on a network volume is not supported: Lucene's lock files and commit
  semantics are for one process per directory, and no shared-volume design is proposed here.
- Read replicas that copy the index from the writer. Possible later, with its own change.
- The OpenSearch, Elasticsearch and Meilisearch backends. They have their own changes.
- Bundling the plugin into the image, or a switch for it. The image does not change.
- Relevance ranking, vector search, and indexing of POM fields or file contents.
- Automatic repair of a corrupt index (open question 3).
- Compression or sharding of the index. One index per instance in version one.

## Capabilities

### New Capabilities

- `lucene-search-backend`: the plugin, its variables, the index location and layout, the single-writer lease and its takeover, the
  write path with its commits and back pressure, the access translation, and the test requirements.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)` and
  `docs(search)`. The pull request title is:

  `feat(search): add an embedded lucene backend plugin`

- **Breaking marker.** No BREAKING item. Operators change nothing unless they opt in: they install the jar and set
  `INGOT_LOCAL_SEARCHENGINE=lucene`. The core gains one additive method with a default (`indexIdentity()`), which an existing
  backend does not override. Existing installations, images, volumes, manifests and variables are untouched.
- **Build and image.** A Gradle subproject `:reposilite-plugins:lucene-plugin` with the Lucene modules it needs (core, the common
  analysis module, faceting and grouping), shadowed into one jar. The jar carries the Lucene `NOTICE` and `LICENSE` files. No line in
  `Dockerfile`, and no entry in `PluginLoader.BUNDLED_SWITCHES`.
- **Distribution.** Through `add-plugin-store`. This change depends on that proposal for signed distribution. Until the store exists,
  the jar is attached to the GitHub release and copied by hand.
- **CI.** The contract suite runs in the unit source set of the plugin, so it runs on every pull request that touches the plugin.
  The container job is not needed.
- **Order.** Depends on `add-search-backend-registration`, on `extract-search-backend-spi`, on `add-database-full-text-search` (the
  token function and the word cases) and on `fix-job-leases` (the lease API, with the epoch of open question 6). Independent of
  `add-image-extra-ca-bundle` and `add-file-secrets`, because the Lucene backend does not reach the network.

## Verification

- The contract suite passes on the database and on the Lucene backend with the same expected values, except the named exceptions in
  design D8 (none are expected).
- Tests of the single-writer rule with two plugin instances sharing one fake lease store and two temporary directories: one writes,
  the other reports down and writes nothing.
- A crash test: the writer stops between the commit and the outbox removal; the entries are delivered once more and the counts match.
- A full rebuild after a takeover, verified by the commit epoch recorded in the index.
- `openspec validate add-lucene-search-plugin --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-lucene-search-plugin` prints nothing.

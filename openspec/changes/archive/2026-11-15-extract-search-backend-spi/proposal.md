# Proposal

## Why

Artifact search has exactly one index, the rows of `search_artifact` in the database, and the plugin is wired to that
table by name. `SearchPlugin` builds `DatabaseArtifactSearchEngine`, `DatabaseArtifactIndex` and `IndexRowStore` directly
(`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/application/SearchPlugin.kt`, lines 103, 115 and 183).
The outbox worker, the rebuild and the verify job write and read the same tables. An operator who wants a dedicated search
service cannot get one without touching every one of those call sites.

The database stays the default, and an external index has to plug in behind one seam later. This change draws that seam
and changes no behaviour: the database implementation moves behind an internal interface, and the core keeps everything
that does not depend on where the index lives. No operator sees a difference.

## What Changes

- An internal interface `ArtifactSearchBackend` in `net.onelitefeather.ingot.search.backend`. It extends the read contract
  `ArtifactSearchEngine` that exists today and adds the write operations the outbox uses, the helpers the rebuild and the
  verify job use, a row count and a health check. It stays internal in this change; the next change in the series
  (`add-search-backend-registration`) makes it public and experimental.
- `DatabaseSearchBackend` implements the interface by delegating to the classes that exist today. No SQL changes.
- The core keeps the outbox (claims, leases, backoff, heartbeat), the run ledger, the state store, the schedulers, the
  startup rules, the compilation of `SearchAccess`, the REST layer and the Maintenance status. It calls the backend through
  the interface only.
- A small registry resolves the local key `searchEngine`. In this change it knows the id `database` only. Every other value
  behaves as today: search is switched off and a warning is logged.
- Tests: an abstract contract suite for the seam, an in-memory fake backend for unit tests of the core, and the existing
  database tests run against the seam. The contract suite runs against SQLite and H2 in unit tests and against MariaDB and
  PostgreSQL in the integration source set, as the current dialect tests do.

### Out of scope

- A second backend of any kind, and any new configuration key, environment variable, route or response field.
- Public types or a plugin-visible API. The interface is internal until `add-search-backend-registration`.
- Full text matching (`add-database-full-text-search`) and the OpenSearch plugin (`add-opensearch-search-plugin`).
- Changing the table layout, the schema marker `schema-1` or the outbox table.
- Changing what an unknown `searchEngine` value does. That is decided in `add-search-backend-registration`.

## Capabilities

### New Capabilities

- `search-backend-seam`: the internal backend interface, the operations the core keeps, the database implementation behind
  it, and the contract suite every backend runs.

## Impact

- **Type and scope.** The change ships under `refactor(search)`. Its commits are `test(search)` and `refactor(search)`.
  The pull request title is:

  `refactor(search): move the database index behind an internal backend interface`

- **Breaking marker.** No breaking change. No configuration key, environment variable, table, route, response, volume,
  manifest or plugin changes. The server behaves the same for every existing installation, and an upgrade or a rollback
  needs no step.
- **Code.** `reposilite-backend`, package `net.onelitefeather.ingot.search` only. Existing search tests keep their
  assertions; only their construction changes where a class moved.
- **Order.** No dependency on another change. It lands before `add-database-full-text-search`, which adds to the database
  backend and needs the seam.

## Verification

- The existing search unit tests pass with the same assertions. The integration tests of `search` pass on MariaDB and
  PostgreSQL, run through `heavy`.
- `openspec validate extract-search-backend-spi --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/extract-search-backend-spi` prints nothing.

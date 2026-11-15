# Proposal

## Why

After `extract-search-backend-spi` the database is one implementation behind an internal interface, but nothing outside
Ingot can supply another. An operator who wants search answered by a dedicated service, or a plugin author who has one, has
no door to walk through. The seam has to become a public, experimental extension point, and the rules for what happens when
the chosen backend is missing, down or disagreeing with the other replicas have to be decided once, for every backend.

The rules matter most for high availability. Several replicas share one database, and the outbox they drain lives there.
If replicas disagree on which index receives the writes, the index splits and nothing reports it. If a missing or unhealthy
search service stops the server from starting, a search problem becomes an outage of artifact downloads, which the search
spec never intended. This change decides both.

## What Changes

- **Public experimental interfaces** in `net.onelitefeather.ingot.search.backend`: `ArtifactSearchBackend`,
  `ArtifactSearchBackendFactory`, `SearchBackendContext`, `BackendHealth`, the write types, and the annotation
  `@ExperimentalIngotSearchApi` (an opt-in marker, as the repository format SPI uses). A factory declares the API version it
  was built for, and the registry refuses a factory of another version.
- **Registration through the plugin event bus.** The search plugin emits `ArtifactSearchBackendRegistrationEvent` after every
  plugin has initialised. A plugin registers its factory in its own `initialize` or `load`. A duplicate id or an unknown API
  version is refused with a warning that names both classes.
- **Selection by the existing key** `searchEngine` (environment `INGOT_LOCAL_SEARCHENGINE`, fallback
  `REPOSILITE_LOCAL_SEARCHENGINE`), default `database`. No new key.
- **Missing or failing backend.** The server starts. Artifact downloads and deploys keep working. Outbox entries are kept
  and not drained. Search answers `503` with `Retry-After` and a problem body that names the backend and the reason. The
  Maintenance page shows the state. Opening is retried every 30 seconds. There is no fallback to the database.
- **Cluster agreement.** Each replica writes its backend id in a heartbeat. A replica drains the outbox only when the stored
  backend id is its own. A change of backend is accepted only after no replica of the stored backend has been seen for three
  minutes, and it starts a full rebuild.
- **Access check in core.** Every file and repository a backend returns is checked against the caller's `SearchAccess` once
  more. A row that fails the check is dropped and logged as an error, and counted in the state.
- **Busy backends.** A backend signal "busy" releases its outbox claims without counting an attempt.
- **Status and documentation.** `GET /api/maven/search/index` gains an additive `backend` block. Pages in `docs/gitbook`
  explain how to choose a backend and how to write a backend plugin, and the plugin API reference lists the event.

### Out of scope

- Any backend implementation. The OpenSearch plugin is `add-opensearch-search-plugin`.
- Starting the server in a strict mode that refuses to start when a backend is missing. Not in this change (see design D3).
- A fallback to the database when another backend is down (see design D3).
- Plugin jars that carry a backend without the event (no `ServiceLoader` discovery of backends from plugin jars).
- Changing the REST routes, the response fields that exist today, or the database schema.

## Capabilities

### New Capabilities

- `search-backend-registration`: the public backend interfaces and their marker, registration through the plugin event, the
  state machine of a backend, the cluster agreement, the access check in core, and the status fields.

## Impact

- **Type and scope.** The change ships under `feat(search)`. Its commits are `test(search)`, `feat(search)`, `build(search)`
  and `docs(search)`. The pull request title is:

  `feat(search): let plugins register search backends and report their health`

- **Breaking marker.** No breaking change for existing installations. One behaviour changes on purpose: a `searchEngine` value
  the server does not know used to switch search off with `404` on every route. It now answers `503` with the reason and shows
  the reason on the Maintenance page. Nobody runs such a value, because `database` is the default. The upgrade notes say so.
  No configuration key, environment variable, volume, manifest, image or plugin has to change.
- **Binary surface.** New public types under `net.onelitefeather.ingot.search.backend`, marked experimental. The plugin API
  packages `com.reposilite.*` gain no member. The event class lives in the Ingot package and is outside the ABI gate until
  the API dump of that package is added (follow-up named in `gate-plugin-abi`).
- **Build.** `java-test-fixtures` is enabled for `reposilite-backend`, so the contract suite is shipped as test fixtures for
  plugin modules. Nothing else in the build changes.
- **Order.** Depends on `extract-search-backend-spi` (the seam) and on `add-database-full-text-search`, so that the contract
  suite of plugins includes the word cases.

## Verification

- Unit tests of the registry, the state machine and the cluster rule with a fixed clock and the fake backend.
- Integration tests of the search API for every state: available, not installed, open failed, down, and cluster mismatch.
- The container job's upgrade rehearsal keeps passing with the default `searchEngine`.
- `openspec validate add-search-backend-registration --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-search-backend-registration` prints nothing.

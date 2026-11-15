# Spec Delta

## Purpose

A search backend can be supplied by a plugin. The server chooses one backend per cluster, keeps running when that backend is
missing or down, and never lets replicas write to different indexes.

## ADDED Requirements

### Requirement: Public experimental backend interfaces

The backend interfaces SHALL live in `net.onelitefeather.ingot.search.backend` and carry the opt-in marker
`ExperimentalIngotSearchApi`. A factory SHALL declare the API version it was built for, and the registry SHALL accept only the
version of this server.

#### Scenario: Matching API version

- **WHEN** a plugin registers a factory with the current API version
- **THEN** the factory is available for selection

#### Scenario: Other API version

- **WHEN** a plugin registers a factory with another API version
- **THEN** the factory is refused and the warning names the class and both versions

### Requirement: Registration through the plugin event

Plugins SHALL register factories in response to `ArtifactSearchBackendRegistrationEvent`, which the search plugin emits after
all plugins have initialised. The built-in `database` factory SHALL NOT be replaced, and a duplicate id SHALL be refused.

#### Scenario: Duplicate id

- **WHEN** a second factory with the id of a registered factory registers
- **THEN** it is refused and the warning names both classes

#### Scenario: Plugin cannot replace the database

- **WHEN** a plugin registers a factory with the id `database`
- **THEN** the database factory stays in use

### Requirement: Missing or failing backend keeps the server running

When the configured backend is not installed, refused, failing to open or down, the server SHALL keep serving downloads and
deploys, SHALL keep outbox entries without draining them, and SHALL answer search and administration routes with `503`,
`Retry-After` and a problem body that names the reason. Search SHALL NOT fall back to another index.

#### Scenario: Backend not installed

- **WHEN** `searchEngine` names an id no factory has registered
- **THEN** the server starts, a download succeeds, and a search answers `503` with the reason in the body

#### Scenario: Backend opens later

- **WHEN** a backend fails to open at start and opens on a later retry
- **THEN** search answers normally from then on and the outbox is drained

#### Scenario: No fallback

- **WHEN** the configured backend is down
- **THEN** search does not read the database index and answers `503`

### Requirement: One backend per cluster

A replica SHALL drain the outbox only when the stored backend id equals its own id. The stored id SHALL change only after no
replica of the stored backend has written a heartbeat for three minutes, and the change SHALL start a full rebuild.

#### Scenario: Replicas disagree

- **WHEN** replica A stores the id `database` and replica B is configured for `opensearch`
- **THEN** replica B drains nothing and reports `CLUSTER_MISMATCH`

#### Scenario: Deliberate switch

- **WHEN** no heartbeat of the stored backend is younger than three minutes
- **THEN** the first replica of the new backend stores its id and starts a rebuild

### Requirement: Access check repeated in the core

The core SHALL check every file and repository a backend returns against the caller's access value. A row that fails SHALL be
dropped, logged at error level and counted.

#### Scenario: Backend returns a row outside the access value

- **WHEN** a backend returns a file the caller may not see
- **THEN** the file is missing from the response and the access mismatch counter increases

### Requirement: Busy backends release claims without an attempt

A backend that signals it is busy SHALL cause its claimed outbox entries to be released with a backoff, and the attempt count
of those entries SHALL NOT increase.

#### Scenario: Busy response

- **WHEN** a batch is refused as busy
- **THEN** the batch is released with the retry delay of the backend and its entries have one attempt fewer than before

### Requirement: Status and selection stay local

The selection key SHALL remain the local configuration key `searchEngine`. The status response SHALL gain a `backend` block with
the state, the health and the access mismatch count, and no existing field SHALL change its meaning.

#### Scenario: Status for the default backend

- **WHEN** the database backend is selected and healthy
- **THEN** `backend.state` is `AVAILABLE` and the existing fields are unchanged

#### Scenario: Redacted detail

- **WHEN** a backend reports an error that contains a URL with user information
- **THEN** the detail shown in the status and in the problem body contains no user information

# Spec Delta

## Purpose

An operator can answer artifact search from a Meilisearch server. The plugin is installed by the operator, every query carries the
caller's access value as a filter, a write is confirmed by its task before its outbox entry is removed, and every difference from the
database engine is named.

## ADDED Requirements

### Requirement: Opt-in by installation and selection

The plugin SHALL register the factory `meilisearch` only when its jar is in the plugins directory. Search SHALL use Meilisearch only when
`searchEngine` is `meilisearch` as well. The plugin SHALL NOT be part of the image.

#### Scenario: Default installation

- **WHEN** no Meilisearch jar is installed and `searchEngine` is unset
- **THEN** the database serves search and nothing of this plugin runs

#### Scenario: Jar installed, selection off

- **WHEN** the jar is installed and `searchEngine` is `database`
- **THEN** the database serves search and the plugin does not open a connection

### Requirement: Connection and key

The plugin SHALL accept only `https` URLs unless `INGOT_MEILISEARCH_ALLOW_HTTP` is `true`, SHALL refuse a URL with user information, and
SHALL read the key from the plain variable or from the file named by `INGOT_MEILISEARCH_API_KEY_FILE`. The key SHALL be sent only in the
`Authorization` header. No key or URL credential SHALL appear in a log, a status response, a problem body or the support bundle.

#### Scenario: Plain HTTP refused

- **WHEN** `INGOT_MEILISEARCH_URL` is an `http://` URL and `INGOT_MEILISEARCH_ALLOW_HTTP` is unset
- **THEN** the plugin does not open and the reason names the variable

#### Scenario: Key file

- **WHEN** `INGOT_MEILISEARCH_API_KEY_FILE` names a readable file and `INGOT_MEILISEARCH_API_KEY` is unset
- **THEN** the file content, without trailing line breaks, is the key

### Requirement: Supported version and index settings

The plugin SHALL open only a server of the minor version it was tested with, SHALL apply its index settings when they differ from the stored
ones, and SHALL log each change with its old and new value. A change of the filterable attributes that reindexes the index SHALL make the
health `DOWN` with the reason until the reindexing task has finished.

#### Scenario: Unsupported major version

- **WHEN** the server reports a major version other than the supported one
- **THEN** the state is `OPEN_FAILED` and the message names both versions

#### Scenario: Settings change

- **WHEN** a filterable attribute is added to the settings
- **THEN** the change is logged with the old and the new value, and search answers `503` with the reason until the task is finished

### Requirement: Writes confirmed by their tasks

The outbox entry SHALL be removed only after every task of its batch succeeded. A task that is still running after the bound SHALL release
the batch without consuming an attempt. A failed task SHALL fail its batch. Every write SHALL be idempotent, so that a replay leaves the same
documents.

#### Scenario: Task outlives the bound

- **WHEN** a task is still processing after the bound
- **THEN** the batch is released with a retry delay and no attempt is counted, and a later replay leaves the index as the database states it

#### Scenario: Task fails

- **WHEN** a task finishes with the status `failed`
- **THEN** the batch fails with the server's error code and the attempts count

#### Scenario: Restart during a drain

- **WHEN** the server restarts while entries are pending
- **THEN** the pending entries are delivered after the server is back, and no document is missing or duplicated

### Requirement: Queries apply the caller's access value

Every query SHALL carry the caller's `SearchAccess` as a filter of the request. No row outside that value SHALL be returned by search,
facets or files. A `Legacy` path predicate SHALL NOT be translated; a caller holding one SHALL receive `503` with the reason.

#### Scenario: Private repository

- **WHEN** an anonymous caller searches and a `PRIVATE` repository holds the artifact
- **THEN** the artifact is not returned and the facets do not count its files

#### Scenario: Legacy grant

- **WHEN** the access value of the caller holds a `Legacy` predicate
- **THEN** the search answers `503` and the reason names the unsupported grant

### Requirement: Named exceptions to the database contract

The plugin SHALL pass the contract cases of the database engine, except the named exceptions G2 (paging by offset), G3 (totals and pages
beyond the server's limit), G4 (group sorts by modification and size above the file bound) and G7 (legacy grants). Each exception SHALL be
listed in the reference page with its reason, and the contract suite SHALL name it in the test that carries it.

#### Scenario: Contract suite on Meilisearch

- **WHEN** the contract suite runs against the pinned Meilisearch container
- **THEN** every case not listed as a named exception passes with the same expected values as the database

### Requirement: Health and consistency statement

The plugin SHALL report `DOWN` when the server does not answer, SHALL keep its circuit open for 30 seconds after five consecutive transport
failures, and SHALL state in its status block that a write is visible once its task has succeeded.

#### Scenario: Circuit opens

- **WHEN** five consecutive requests fail with a transport error
- **THEN** the health is `DOWN` and no request is sent for 30 seconds

#### Scenario: Visible after success

- **WHEN** a file is indexed and its task succeeds
- **THEN** a search finds it, and a search before the task succeeded is not required to find it

### Requirement: Documentation of the gaps and the security responsibilities

The reference page SHALL list the named exceptions first, SHALL state the minimal key actions and the pinned minor version, SHALL state that
the server holds the paths of every repository including hidden and private ones, and SHALL name the operator's obligations: a dedicated key
on the dedicated index, network policy, TLS and key rotation.

#### Scenario: Operator reads the reference

- **WHEN** an operator opens the Meilisearch reference before configuring the backend
- **THEN** the named exceptions and the key actions are stated before the variables are listed

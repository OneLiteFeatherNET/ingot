# Spec Delta

## Purpose

An operator can answer artifact search from an OpenSearch cluster. The plugin is off by default, every query runs on the cluster
with the caller's access applied in the query, and the index is refreshed from the outbox with at-least-once delivery.

## ADDED Requirements

### Requirement: Opt-in bundled plugin

The plugin SHALL load only when `INGOT_OPENSEARCH_ENABLED` is `true` (fallback `REPOSILITE_OPENSEARCH_ENABLED`). Search SHALL use
OpenSearch only when `searchEngine` is `opensearch` as well.

#### Scenario: Default installation

- **WHEN** no OpenSearch variable is set
- **THEN** the plugin is not loaded and the database serves search

#### Scenario: Switch on, selection off

- **WHEN** the plugin is enabled and `searchEngine` is `database`
- **THEN** the database serves search and the plugin registers nothing in use

### Requirement: Connection and secrets

The plugin SHALL accept only `https` URLs unless `INGOT_OPENSEARCH_ALLOW_HTTP` is `true`, SHALL refuse a URL with user information,
and SHALL read the password from the plain variable or from the file named by `INGOT_OPENSEARCH_PASSWORD_FILE`. No password, URL user
information or credential SHALL appear in a log, a status response, a problem body or the support bundle.

#### Scenario: Plain HTTP refused

- **WHEN** `INGOT_OPENSEARCH_URLS` contains an `http://` URL and `INGOT_OPENSEARCH_ALLOW_HTTP` is unset
- **THEN** the plugin does not open and the reason names the variable

#### Scenario: URL with user information

- **WHEN** a URL contains user information
- **THEN** the plugin does not open and the message does not contain the user information

#### Scenario: Password file

- **WHEN** the password file exists and no plain password is set
- **THEN** the file content is the password, without trailing line breaks

### Requirement: Index layout and mapping

The plugin SHALL create `<prefix>-v1` with a strict mapping and the alias `<prefix>`, and SHALL use the alias for every read and write.
It SHALL refuse an index whose recorded mapping version differs from its own, and it SHALL use the database's document key as the
document id.

#### Scenario: Fresh cluster

- **WHEN** the plugin opens a cluster without the index
- **THEN** the index and the alias are created and the state is `AVAILABLE`

#### Scenario: Foreign mapping version

- **WHEN** the alias points to an index recording another mapping version
- **THEN** the plugin reports `OPEN_FAILED` naming both versions and does not write

#### Scenario: Same file twice

- **WHEN** the same file is indexed twice
- **THEN** exactly one document exists

### Requirement: At-least-once writes with back pressure

The outbox entry SHALL be removed only after the cluster acknowledged its actions. Writes SHALL be idempotent. A busy cluster or a
refused bulk request SHALL release the batch without consuming attempts, and a rejected document SHALL fail its batch.

#### Scenario: Acknowledgement lost

- **WHEN** the cluster applied a batch but the response was lost
- **THEN** the batch is applied again and the result is the same

#### Scenario: Cluster busy

- **WHEN** the cluster answers `429` to a bulk request
- **THEN** the batch is released with the retry delay and no attempt is counted

#### Scenario: Restart during a drain

- **WHEN** the cluster restarts while entries are pending
- **THEN** the pending entries are delivered after the cluster is back, and no document is missing or duplicated

### Requirement: Queries apply the caller's access value

Every query SHALL carry the caller's `SearchAccess` as a filter of the query. No row outside that value SHALL be returned by search,
facets or files, and the result paging SHALL be computed by the cluster, not after the access check.

#### Scenario: Private repository

- **WHEN** an anonymous caller searches and a `PRIVATE` repository holds the artifact
- **THEN** the artifact is not returned and the facets do not count its files

#### Scenario: Precedence rules translated

- **WHEN** the access value holds a precedence list with a denial and an allow
- **THEN** the translated query returns the same rows as the database engine for the same data

### Requirement: Same answers as the database engine

The plugin SHALL pass the same contract cases as the database engine: grouping, the sort order and its ties, the capped total, the
latest version, the repository presence with its origin, the facets with their own-kind exclusion and the files of one version.

#### Scenario: Contract suite on OpenSearch

- **WHEN** the contract suite runs against a secured OpenSearch 2.x container
- **THEN** every case passes with the same expected values as the database

### Requirement: Health and refresh

The plugin SHALL report `DOWN` when the cluster is red or unreachable, SHALL keep its circuit open for 30 seconds after five
consecutive transport failures, and SHALL report its refresh interval in the status block. Writes SHALL be visible after the
refresh interval.

#### Scenario: Circuit opens

- **WHEN** five consecutive requests fail with a transport error
- **THEN** the health is `DOWN` and no request is sent for 30 seconds

#### Scenario: Visible after refresh

- **WHEN** a file is indexed
- **THEN** a search finds it after the refresh interval and not before it is due

### Requirement: Documentation of the security responsibilities

The reference page SHALL state that the cluster holds the paths of every repository, including hidden and private ones, and SHALL
name the operator's obligations: a dedicated user, network policy, TLS and audit logging.

#### Scenario: Operator reads the reference

- **WHEN** an operator opens the OpenSearch reference
- **THEN** the page names each variable, the default of each, and the obligations of the cluster

# Spec Delta

## Purpose

An operator can answer artifact search from an Elasticsearch 8.x or 9.x cluster. The plugin is installed by the operator, every
query runs on the cluster with the caller's access applied in the query, and the index is refreshed from the outbox with
at-least-once delivery.

## ADDED Requirements

### Requirement: Opt-in by installation and selection

The plugin SHALL register the factory `elasticsearch` only when its jar is in the plugins directory. Search SHALL use Elasticsearch
only when `searchEngine` is `elasticsearch` as well. The plugin SHALL NOT be part of the image.

#### Scenario: Default installation

- **WHEN** no Elasticsearch jar is installed and no Elasticsearch variable is set
- **THEN** the database serves search and nothing of this plugin runs

#### Scenario: Jar installed, selection off

- **WHEN** the jar is installed and `searchEngine` is `database`
- **THEN** the database serves search and the plugin registers its factory but does not open a connection

### Requirement: Connection, TLS and credentials

The plugin SHALL accept only `https` URLs unless `INGOT_ELASTICSEARCH_ALLOW_HTTP` is `true`. It SHALL refuse a URL with user
information, SHALL refuse an API key together with a user name, and SHALL read secrets from the plain variable or from the file
named by the `_FILE` variable. No credential, URL user information or password SHALL appear in a log, a status response, a problem
body or the support bundle.

#### Scenario: Plain HTTP refused

- **WHEN** `INGOT_ELASTICSEARCH_URLS` contains an `http://` URL and `INGOT_ELASTICSEARCH_ALLOW_HTTP` is unset
- **THEN** the plugin does not open and the reason names the variable

#### Scenario: API key and user name together

- **WHEN** `INGOT_ELASTICSEARCH_API_KEY` and `INGOT_ELASTICSEARCH_USERNAME` are both set
- **THEN** the plugin does not open and the message names both variables without their values

#### Scenario: API key file

- **WHEN** `INGOT_ELASTICSEARCH_API_KEY_FILE` names a readable file and `INGOT_ELASTICSEARCH_API_KEY` is unset
- **THEN** the file content, without trailing line breaks, is the API key

### Requirement: Supported majors only

The plugin SHALL open clusters of major version 8 and 9 only. A cluster of another major SHALL be reported as `OPEN_FAILED`, naming
the version the cluster reported and the supported majors.

#### Scenario: Unsupported major

- **WHEN** the cluster reports major version 7
- **THEN** the state is `OPEN_FAILED`, no index is written, and the message names both versions

### Requirement: Index layout and mapping

The plugin SHALL create `<prefix>-v1` with a strict mapping and the alias `<prefix>`, SHALL use the alias for every read and write,
SHALL refuse an index whose recorded mapping version differs from its own, and SHALL use the database's document key as the document
id.

#### Scenario: Fresh cluster

- **WHEN** the plugin opens a cluster without the index
- **THEN** the index and the alias are created and the state is `AVAILABLE`

#### Scenario: Same file twice

- **WHEN** the same file is indexed twice
- **THEN** exactly one document exists

#### Scenario: Index replaced by the operator

- **WHEN** the physical index behind the alias is replaced and its UUID changes
- **THEN** the reported index identity changes, so that the rebuild cursor is reset

### Requirement: At-least-once writes with back pressure

The outbox entry SHALL be removed only after the cluster acknowledged its actions. Writes SHALL be idempotent. A `429`, a `503` or an
item throttled by the cluster SHALL release the batch without consuming attempts, and a rejected document SHALL fail its batch.

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
facets or files, and the paging SHALL be computed by the cluster, not after the access check.

#### Scenario: Private repository

- **WHEN** an anonymous caller searches and a `PRIVATE` repository holds the artifact
- **THEN** the artifact is not returned and the facets do not count its files

#### Scenario: Precedence rules translated

- **WHEN** the access value holds a precedence list with a denial and an allow
- **THEN** the translated query returns the same rows as the database engine for the same data

#### Scenario: Core check stays silent

- **WHEN** the contract suite runs against the plugin
- **THEN** the access mismatch count of the core stays zero

### Requirement: Same answers as the database engine, with named exceptions

The plugin SHALL pass the same contract cases as the database engine: grouping, the sort order and its ties, the capped total, the
latest version, the repository presence with its origin, the facets with their own-kind exclusion and the files of one version. The
only exception is a file page beyond the index result window, which SHALL be answered with `400` and a hint, and which SHALL be listed
in the reference page.

#### Scenario: Contract suite on Elasticsearch

- **WHEN** the contract suite runs against a secured Elasticsearch 8.x container and against a secured 9.x container
- **THEN** every case passes with the same expected values as the database, except the named exception

### Requirement: Health and refresh

The plugin SHALL report `DOWN` when the cluster is red or unreachable, SHALL keep its circuit open for 30 seconds after five
consecutive transport failures, and SHALL report its refresh interval in the status block. Writes SHALL be visible after the refresh
interval.

#### Scenario: Circuit opens

- **WHEN** five consecutive requests fail with a transport error
- **THEN** the health is `DOWN` and no request is sent for 30 seconds

#### Scenario: Visible after refresh

- **WHEN** a file is indexed
- **THEN** a search finds it after the refresh interval and not before it is due

### Requirement: Documentation of the security responsibilities

The reference page SHALL state that the cluster holds the paths of every repository, including hidden and private ones, SHALL list
the privileges of the API key, SHALL state the licence statement of the plugin, and SHALL name the operator's obligations: a dedicated
key, network policy, TLS and audit logging.

#### Scenario: Operator reads the reference

- **WHEN** an operator opens the Elasticsearch reference
- **THEN** the page names each variable, the default of each, the privileges, and the obligations of the cluster

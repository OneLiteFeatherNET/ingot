# Spec Delta

## Purpose

An administrator can point Ingot at a Nexus 3 instance, learn what it holds and what the credential may do, without any
secret being stored in plain text or written to a log, and without Nexus being changed.

## ADDED Requirements

### Requirement: Connection test

The plugin SHALL provide `POST /api/migration/nexus/connection/test`, for instance admins only. It SHALL report whether Nexus is
reachable, its version and edition when the `Server` header gives them, and for each REST area the plugin needs whether the
credential may read it. It SHALL NOT write to Nexus.

#### Scenario: Reachable with a read-only credential

- **WHEN** the credential may list assets but not read repository settings
- **THEN** the result is a success, lists settings as not readable, and names the effect on the report

#### Scenario: Bad credential

- **WHEN** Nexus answers `401`
- **THEN** the result says the credential was refused and carries no part of the password

#### Scenario: Not an administrator

- **WHEN** a caller who is not an instance admin posts to the route
- **THEN** the answer is `403` and no request goes to Nexus

### Requirement: Credentials are never stored or logged

A literal password SHALL be held only in memory and SHALL NOT be written to a table, the audit log, a log line, an exception
message, an API response or a report. A secret reference SHALL be stored as its text and resolved when used. A URL with user
information SHALL be refused with `400`.

#### Scenario: Canary password

- **WHEN** a job runs with the password `canary-7f3a` and ends in failure
- **THEN** that string appears in no log output, table row, audit event, response body or report

#### Scenario: Reference stored as text

- **WHEN** the password is given as `${env:NEXUS_PASSWORD}`
- **THEN** the job row holds that text and the resolved value appears nowhere

### Requirement: Credentials go to one origin

The `Authorization` header SHALL be sent only to the origin of the connection URL. An asset `downloadUrl` on another origin SHALL be
refused and recorded. A redirect to another origin SHALL be followed without the header, at most three times.

#### Scenario: Foreign download URL

- **WHEN** an asset lists a `downloadUrl` on a different host
- **THEN** the file is recorded as failed with that reason and no request carries the credential to that host

### Requirement: TLS verification stays on

The Nexus client SHALL verify certificates against the JVM trust store and SHALL have no trust setting of its own. In the image that
store includes the certificates imported from `INGOT_EXTRA_CA_CERTS`. No setting SHALL turn verification off. A plain `http` URL
SHALL be accepted with a warning.

#### Scenario: Private CA

- **WHEN** Nexus uses a certificate from a CA that is in the JVM trust store, for example through `INGOT_EXTRA_CA_CERTS`
- **THEN** the connection test succeeds

#### Scenario: Unknown CA

- **WHEN** the certificate chain is not trusted
- **THEN** the test fails with a message that names the trust problem and points at `INGOT_EXTRA_CA_CERTS`

### Requirement: Host allow list

When `INGOT_NEXUS_IMPORT_ALLOWED_HOSTS` is set, the plugin SHALL refuse a connection whose host is not in the list with `400`. When it
is unset, any host SHALL be accepted.

#### Scenario: Host not listed

- **WHEN** the list is `nexus.internal` and the URL names `other.example`
- **THEN** the answer is `400` and no request is sent

### Requirement: Discovery and format histogram

`POST /api/migration/nexus/discover` SHALL list the repositories with name, format and type, and for each the component count, asset
count and total size when the credential can list assets. It SHALL return a histogram by format of repositories, components and bytes.

#### Scenario: Mixed formats

- **WHEN** Nexus holds maven2, npm and docker repositories
- **THEN** the histogram has one entry per format and the non-Maven ones are marked unsupported

#### Scenario: Paging

- **WHEN** the asset listing needs several pages of a size the plugin was not told
- **THEN** the counts include every page and no page size is assumed

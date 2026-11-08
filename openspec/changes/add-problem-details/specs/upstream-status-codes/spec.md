# Spec Delta

## Purpose

A proxy repository can report what actually went wrong with its upstream, but only when the operator asks for it with
`INGOT_PROXY_UPSTREAM_STATUS=strict`. By default the statuses clients get today do not change, because Gradle moves to the next
repository only on a `404`.

## ADDED Requirements

### Requirement: Setting and default

The server SHALL read the setting `INGOT_PROXY_UPSTREAM_STATUS` with the values `legacy` and `strict`, from the system property
`ingot.proxy.upstreamStatus` first, then the environment variable `INGOT_PROXY_UPSTREAM_STATUS`, then the fallbacks
`reposilite.proxy.upstreamStatus` and `REPOSILITE_PROXY_UPSTREAM_STATUS`. When none is set, the value SHALL be `legacy`. Any other
value SHALL stop the startup with a message that names the setting.

#### Scenario: Nothing is set

- **WHEN** the server starts with none of the four sources set
- **THEN** it behaves as `legacy`

#### Scenario: Property wins over environment

- **WHEN** `-Dingot.proxy.upstreamStatus=legacy` and `INGOT_PROXY_UPSTREAM_STATUS=strict` are both set
- **THEN** the server behaves as `legacy`

#### Scenario: Unknown value

- **WHEN** the setting is `loose`
- **THEN** the startup fails and the message names `INGOT_PROXY_UPSTREAM_STATUS`

### Requirement: Legacy mode keeps today's statuses

In `legacy` mode a proxy repository SHALL answer every upstream failure with the status it answers today, and `MirrorService` SHALL
NOT record an upstream miss that it does not record today. The messages SHALL NOT contain an exception class name, an exception
message or a credential.

#### Scenario: Upstream is unreachable, local first

- **WHEN** a client requests an artifact that is not stored locally, the repository looks at local storage first, and the upstream refuses the connection
- **THEN** the answer is `404`, as before this change

#### Scenario: Upstream is unreachable, mirror first

- **WHEN** the repository asks the mirror first and the upstream refuses the connection
- **THEN** the answer is `400` with a message that contains no Java class name

#### Scenario: Upstream answers 404 or 500

- **WHEN** the upstream answers `404` or `500` for an artifact that is not stored locally
- **THEN** the answer is `404` when local storage is asked first and `406` when the mirror is asked first, as before this change

#### Scenario: No new negative entry

- **WHEN** the upstream answers `404` for a metadata file and the resolution cache is enabled
- **THEN** no `MissingMetadata` entry is recorded for that prefix, as before this change

### Requirement: Strict mode reports upstream failures as gateway errors

In `strict` mode a proxy repository SHALL answer `504` when the upstream does not answer within the configured connect or read
timeout, and `502` when the upstream cannot be reached, answers a non-2xx status other than `404` and `410`, or answers a 2xx with an
HTML content type. A malformed upstream URI SHALL still answer `400`. The message SHALL be generic.

#### Scenario: Upstream times out

- **WHEN** a client requests an artifact that is not stored locally and the upstream read times out
- **THEN** the answer is `504` with a generic message

#### Scenario: Upstream is unreachable

- **WHEN** the upstream refuses the connection or its name does not resolve
- **THEN** the answer is `502` and the body does not contain a Java class name

#### Scenario: Upstream answers a server error

- **WHEN** the upstream answers `500` for an artifact that is not stored locally
- **THEN** the answer is `502`

#### Scenario: Upstream answers an HTML page

- **WHEN** the upstream answers `200` with an HTML content type
- **THEN** the answer is `502`

### Requirement: Strict mode keeps not found a miss

In `strict` mode a proxy repository SHALL answer `404` when the upstream answers `404` or `410`, and a group repository SHALL continue
with its next member after a `404`. When every probed host answers `404` for a metadata file and the resolution cache is enabled, the
miss MAY be recorded as `MissingMetadata`, bounded by `resolutionCacheMaxEntries`.

#### Scenario: Upstream has no such artifact

- **WHEN** a client requests an artifact from a proxy repository and the upstream answers `404`
- **THEN** the answer is `404`, which a Maven or Gradle client treats as "try the next repository"

#### Scenario: Group falls through a miss

- **WHEN** the first member of a group answers `404` and the second member has the artifact
- **THEN** the group answers `200` with the artifact

#### Scenario: Group member is down

- **WHEN** the first member of a group answers `502` and the second member has the artifact
- **THEN** the group answers `200` with the artifact

### Requirement: Local copies win and unrelated errors are unchanged

In both modes a stored local copy SHALL be served without contacting the upstream when the repository asks local storage first. No
error outside the proxy fetch path SHALL change its status code.

#### Scenario: Cached copy while upstream is down

- **WHEN** the artifact is stored locally and the upstream is unreachable
- **THEN** the answer is `200` with the stored artifact, in `legacy` and in `strict`

# Spec Delta

## Purpose

A proxy repository tells the client what actually went wrong with its upstream, and keeps answering `404` for a file the upstream
does not have so that Maven and Gradle try the next repository.

## ADDED Requirements

### Requirement: Upstream failures are gateway errors

A proxy repository SHALL answer `504` when the upstream does not answer within the configured connect or read timeout, and `502`
when the upstream cannot be reached or answers a non-2xx status other than `404` and `410`. The message SHALL be generic and SHALL NOT
contain an exception class name, an exception message or a credential.

#### Scenario: Upstream times out

- **WHEN** a client requests an artifact that is not stored locally and the upstream read times out
- **THEN** the answer is `504` with a generic message

#### Scenario: Upstream is unreachable

- **WHEN** the upstream refuses the connection or its name does not resolve
- **THEN** the answer is `502` and the body does not contain a Java class name

#### Scenario: Upstream answers a server error

- **WHEN** the upstream answers `500` for an artifact that is not stored locally
- **THEN** the answer is `502`

### Requirement: Upstream not found stays not found

A proxy repository SHALL answer `404` when the upstream answers `404` or `410`, and a group repository SHALL continue with its next
member after a `404`.

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

A stored local copy SHALL be served without contacting the upstream, as before. A malformed upstream URI SHALL still answer `400`.
No error outside the proxy fetch path SHALL change its status code.

#### Scenario: Cached copy while upstream is down

- **WHEN** the artifact is stored locally and the upstream is unreachable
- **THEN** the answer is `200` with the stored artifact

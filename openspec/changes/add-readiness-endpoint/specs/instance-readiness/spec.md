# Spec Delta

## Purpose

A load balancer or orchestrator asks whether this instance can serve requests now, and receives a yes or a no with the
names of the checks that failed. The answer carries no internal detail.

## ADDED Requirements

### Requirement: Readiness endpoint without credentials

The server SHALL answer `GET /api/status/ready` without authentication. The endpoint SHALL answer `200` when every applicable
check passes and `503` when one fails. Both answers SHALL carry `Content-Type: application/json`.

#### Scenario: Ready instance

- **WHEN** a client without credentials requests `GET /api/status/ready` and every check passes
- **THEN** the response status is `200` and the body is `{"status":"UP","failing":[]}`

#### Scenario: Not ready instance

- **WHEN** a client without credentials requests `GET /api/status/ready` and the database check fails
- **THEN** the response status is `503` and the body has `"status":"DOWN"` and `"failing"` containing `database`

### Requirement: Checks

The readiness answer SHALL be computed from the checks `plugins`, `database`, `storage` and `cluster`. The `cluster` check
SHALL be applied only when a cluster bus URL is configured. A check that fails, that raises an error or that does not answer
within two seconds SHALL be listed in `failing`.

#### Scenario: Plugins not yet started

- **WHEN** the server has not yet emitted its started event
- **THEN** `plugins` is listed in `failing` and the status is `503`

#### Scenario: Database unreachable

- **WHEN** the database does not answer a validation query within two seconds
- **THEN** `database` is listed in `failing` and the status is `503`

#### Scenario: Storage root answers with no content

- **WHEN** every repository's storage root answers a listing with an empty result
- **THEN** `storage` is not listed in `failing`

#### Scenario: Storage root fails

- **WHEN** the storage root of one repository answers a listing with an error
- **THEN** `storage` is listed in `failing` and the status is `503`

#### Scenario: Cluster bus not configured

- **WHEN** no cluster bus URL is configured
- **THEN** `cluster` is not evaluated, is not listed in `failing`, and does not change the status

#### Scenario: Cluster bus reconnecting

- **WHEN** a cluster bus URL is configured and the bus reports `reconnecting` or `disabled`
- **THEN** `cluster` is listed in `failing` and the status is `503`

#### Scenario: Cluster bus connected

- **WHEN** a cluster bus URL is configured and the bus reports `connected`
- **THEN** `cluster` is not listed in `failing`

### Requirement: No internal detail in the body

The body of the readiness answer SHALL contain only the status and the names of the checks listed above. It SHALL NOT
contain a repository name, a storage path, a connection string, a bucket name or an exception message. The detail of a
failing check SHALL be written to the log.

#### Scenario: Storage failure names no repository

- **WHEN** the storage root of a repository named `private-builds` fails
- **THEN** the body lists `storage` and does not contain `private-builds`, a path or an exception message

### Requirement: Liveness and existing endpoints unchanged

The readiness endpoint SHALL NOT change `GET /api/status/health`, the `HEALTHCHECK` of the container image or any manager
endpoint under `/api/status/`. An instance whose database is unreachable SHALL answer `200` on `/api/status/health` while
`/api/status/ready` answers `503`.

#### Scenario: Health stays up while not ready

- **WHEN** the database check fails and a client requests `GET /api/status/health`
- **THEN** the response is `200` with `{"status":"UP"}`

#### Scenario: Readiness is not a manager endpoint

- **WHEN** a client with a valid manager token requests `GET /api/status/ready` and a client without a token requests the same path
- **THEN** both receive the same status and body for the same instance state

# Spec Delta

## Purpose

An operator can stop writes across every replica for a while, for a consistent backup, a failover or a cutover, without
stopping the instances. Reads keep working. The switch is off by default and changes nothing until it is turned on.

## ADDED Requirements

### Requirement: Switch precedence

The instance SHALL read `INGOT_READ_ONLY` (fallback `REPOSILITE_READ_ONLY`, system property `ingot.readOnly`). When the value
is `true`, the instance SHALL be read-only whatever the stored state says. When the value is unset or `false`, the stored state
SHALL decide, and the stored state SHALL default to off.

#### Scenario: Environment forces the state

- **WHEN** `INGOT_READ_ONLY=true` is set and the stored state is off
- **THEN** the instance is read-only and reports the source as `environment`

#### Scenario: Stored state decides when the variable is unset

- **WHEN** `INGOT_READ_ONLY` is unset and the stored state is on
- **THEN** the instance is read-only and reports the source as `stored`

#### Scenario: Default is off

- **WHEN** neither the variable nor a stored state exists
- **THEN** the instance is not read-only

#### Scenario: Fallback spelling

- **WHEN** only `REPOSILITE_READ_ONLY=true` is set
- **THEN** the instance is read-only

### Requirement: Stored state shared across replicas

The stored state SHALL live in the database. Each instance SHALL re-read it at most five seconds after the last read, and the
read SHALL use an injected clock. A change SHALL be written with a compare-and-save, so that concurrent changes do not
overwrite each other silently. When the cluster bus is configured, a change SHALL also be published as a `read-only.changed`
event, and receiving instances SHALL re-read the state at once. A lost event SHALL delay the change by no more than five
seconds.

#### Scenario: Change seen by another replica without the bus

- **WHEN** replica A stores the state as on and no cluster bus is configured
- **THEN** replica B reports read-only within five seconds

#### Scenario: Change announced over the bus

- **WHEN** replica A stores the state as on and a cluster bus is configured
- **THEN** replica B re-reads the state without waiting for the five-second limit

#### Scenario: Lost event

- **WHEN** the bus drops the `read-only.changed` event
- **THEN** replica B still reports the new state within five seconds

#### Scenario: Concurrent changes

- **WHEN** two toggles are saved against the same revision
- **THEN** exactly one save succeeds and the other is answered with `409`

### Requirement: Refused writes

While the instance is read-only, `PUT`, `POST`, `PATCH` and `DELETE` requests on the API and the Maven protocol SHALL be answered
with `503`, a `Retry-After` header, and an `application/problem+json` body. `GET` and `HEAD` SHALL be served as before. Login,
session refresh and the toggle endpoint SHALL NOT be refused. Authentication and authorization SHALL run before the refusal, so
that a caller without access still receives `401` or `403`.

#### Scenario: Deploy refused

- **WHEN** read-only is on and a Maven client sends `PUT` for an artifact with a valid token
- **THEN** the response is `503` with a `Retry-After` header and a problem body, and nothing is written

#### Scenario: Delete refused

- **WHEN** read-only is on and an authorized principal sends `DELETE` to `/api/tokens/{name}`
- **THEN** the response is `503` with a `Retry-After` header and nothing is deleted

#### Scenario: Read still served

- **WHEN** read-only is on and a client sends `GET` for an existing artifact
- **THEN** the artifact is served as before

#### Scenario: Login allowed

- **WHEN** read-only is on and a user submits the login request with valid credentials
- **THEN** the login succeeds as before

#### Scenario: Unauthorized write

- **WHEN** read-only is on and a request without credentials sends `PUT` to a private repository
- **THEN** the response is `401`, not `503`

### Requirement: Toggle endpoint

The API SHALL offer `GET /api/admin/read-only` to report the state, `POST /api/admin/read-only` to turn it on and
`DELETE /api/admin/read-only` to turn it off. Changing the state SHALL require the maintenance area (`ingot.admin.maintenance`).
A `POST` or `DELETE` while the environment variable forces the state SHALL be answered with `409` and a problem body that names
the variable. Every change that is stored SHALL write one audit event with the actor and the new state.

#### Scenario: Report state

- **WHEN** a principal with the maintenance area sends `GET /api/admin/read-only`
- **THEN** the response carries the state and its source

#### Scenario: Enable without the area

- **WHEN** a token without the maintenance area sends `POST /api/admin/read-only`
- **THEN** the response is `403` and the state does not change

#### Scenario: Enable with the area

- **WHEN** a principal with the maintenance area sends `POST /api/admin/read-only`
- **THEN** the state is stored as on, one audit event is written, and the response is `200`

#### Scenario: Toggle forced by the environment

- **WHEN** `INGOT_READ_ONLY=true` is set and a principal sends `DELETE /api/admin/read-only`
- **THEN** the response is `409` naming `INGOT_READ_ONLY`, and the stored state does not change

### Requirement: Scheduled runs wait

While the instance is read-only, a cleanup policy run, the nightly search verify and the search index rebuild SHALL NOT claim
their run and SHALL NOT start. A run that was due while read-only SHALL NOT be started later, and the next scheduled time SHALL
apply as before. Status and health reads SHALL keep working.

#### Scenario: Cleanup due during read-only

- **WHEN** a cleanup policy is due and read-only is on at the tick
- **THEN** no claim row is written and no deletion happens

#### Scenario: Scheduler resumes

- **WHEN** read-only is turned off and the next cleanup time comes
- **THEN** the policy runs once as scheduled

### Requirement: Visibility

Each instance SHALL expose a gauge `ingot_read_only` that is `1` while read-only is on and `0` otherwise. The instance status
SHALL carry a `readOnly` field and the source of the state. `GET /api/status/health` SHALL keep answering `UP` while read-only
is on. The dashboard SHALL show a banner while the state is on, and SHALL show the toggle only to principals with the
maintenance area.

#### Scenario: Gauge while read-only

- **WHEN** read-only is on
- **THEN** the gauge `ingot_read_only` reports `1`

#### Scenario: Health unchanged

- **WHEN** read-only is on and a client calls `GET /api/status/health`
- **THEN** the response is `200` with status `UP`

### Requirement: Default behaviour unchanged

When the environment variable is unset or `false` and no stored state exists, every endpoint, route, status code and header SHALL
behave as it did before this change, and the scheduled runs SHALL run as before. The cluster bus SHALL NOT start because of
this change.

#### Scenario: Fresh install without the switch

- **WHEN** an instance starts with no read-only variable and no stored state
- **THEN** a `PUT` deploy succeeds as before and no read-only event is published

# Spec Delta

## Purpose

An operator can have every audit event written as one JSON line to a file or to standard output, so a SIEM or log collector
can ingest it, without calling the admin API. The sink is off by default and never slows or fails a request.

## ADDED Requirements

### Requirement: The sink is opt-in

The server SHALL write no sink output and start no sink thread unless `INGOT_AUDIT_SINK_STDOUT=true` (fallback
`REPOSILITE_AUDIT_SINK_STDOUT`) or a non-blank `INGOT_AUDIT_SINK_FILE` (fallback `REPOSILITE_AUDIT_SINK_FILE`) is set. The
`INGOT_` name SHALL win when both spellings are set.

#### Scenario: Default

- **WHEN** the server starts with no audit sink variable and an audit event occurs
- **THEN** the event is stored in the table as today and nothing is written to standard output or a file by the sink

#### Scenario: Legacy spelling

- **WHEN** only `REPOSILITE_AUDIT_SINK_FILE=/data/audit.jsonl` is set and an event occurs
- **THEN** the line is appended to `/data/audit.jsonl`

### Requirement: Line format

Each audit event SHALL be written as one UTF-8 JSON object on one line ending in `\n`, with the fields `schema` (`ingot.audit`),
`version` (integer `1`), `time` (ISO-8601 UTC), `node`, `action`, `actor`, `actorAddress`, `repository`, `path`, `details`
(object or null), `success` and `occurrences`, all always present, with `null` for a value that does not apply.

#### Scenario: Deploy event

- **WHEN** a file is deployed to repository `releases` and the sink is on
- **THEN** one line is written with `action` `ARTIFACT_DEPLOYED`, `repository` `releases`, the coordinates in `path`, and `details` null

#### Scenario: Newline in a value

- **WHEN** an actor name contains a line feed
- **THEN** the line still contains exactly one `\n`, at its end, and the name is escaped inside the JSON string

#### Scenario: Details are an object

- **WHEN** a `SETTINGS_CHANGED` event with details is written
- **THEN** `details` is a JSON object, not a string holding JSON

### Requirement: Schema stability

Adding a field or an action name SHALL NOT change `version`. Renaming, removing or retyping a field SHALL raise `version`. The
documentation SHALL tell consumers to ignore unknown fields.

#### Scenario: Pinned field list

- **WHEN** a test writes one line
- **THEN** its field names and their order equal the documented list exactly

### Requirement: Node identity

Each line SHALL carry in `node` the instance identifier that the cluster bus uses: the system property
`ingot.cluster.instance`, then `INGOT_CLUSTER_INSTANCE_ID`, then the host name.

#### Scenario: Two replicas

- **WHEN** two replicas each handle one admin action
- **THEN** each writes its own line only, and the two lines carry different `node` values

### Requirement: Address follows recordActorAddress

The sink SHALL write `actorAddress` only while the `audit` setting `recordActorAddress` is `true`, read for each event.

#### Scenario: Address recording off

- **WHEN** `recordActorAddress` is `false` and an event with a known client address occurs
- **THEN** the line has `actorAddress` null

### Requirement: Failure never reaches the request

Recording an event SHALL NOT block, delay or fail the caller because of the sink. A full queue or a write error SHALL drop
the line, increment a counter and cause at most one warning per minute. The table insert SHALL happen whether or not the
sink succeeded.

#### Scenario: Unwritable file

- **WHEN** the sink file cannot be written and an event occurs
- **THEN** the caller returns normally, the table row exists, and one warning that names the path and the error is logged

#### Scenario: Full queue

- **WHEN** the queue is full and an event occurs
- **THEN** the call returns without waiting, the line is dropped and the dropped counter grows by one

#### Scenario: Database down

- **WHEN** the table insert fails and the sink is on
- **THEN** the line is still written

### Requirement: Rotation by the collector

The sink SHALL append to the file and SHALL reopen it when the path no longer refers to the open file, so an external rotation
needs no signal. The sink SHALL NOT rotate, compress or delete the file.

#### Scenario: File renamed

- **WHEN** an external tool renames the file and a new event occurs
- **THEN** a new file is created at the original path and the line is written there

### Requirement: Failed logins are not folded

The sink SHALL write one line per `LOGIN_FAILED` event with `occurrences` `1`. The folding of the table SHALL NOT change.

#### Scenario: Three failures in one minute

- **WHEN** three failed logins of one name and address occur within one minute
- **THEN** the sink has three lines and the table has one row with `occurrences` 3

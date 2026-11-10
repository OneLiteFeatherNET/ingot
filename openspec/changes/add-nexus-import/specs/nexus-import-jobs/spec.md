# Spec Delta

## Purpose

An import is a long job. It can be stopped, started again and moved to another replica without losing progress, without writing a
file twice, and without two replicas working on one job.

## ADDED Requirements

### Requirement: Persisted jobs

Job state SHALL be stored in plugin-owned tables: the job, its repositories with a listing cursor and counters, one result row per
asset, and the report rows. The tables SHALL be created only when the plugin starts. Any replica SHALL be able to answer a status
request from them.

#### Scenario: Status from another replica

- **WHEN** replica B is asked for a job that replica A runs
- **THEN** B answers with the current counters from the database

#### Scenario: Plugin off

- **WHEN** the switch is unset
- **THEN** no table is created

### Requirement: Fenced lease

One replica SHALL run a job under a lease with a random token. Each commit of progress SHALL be conditional on that token and
extend the lease. A replica that lost the lease SHALL NOT commit progress and SHALL NOT start another file.

#### Scenario: Lease lost

- **WHEN** replica A stalls past the lease and replica B takes over
- **THEN** A's next commit changes no row, A starts no further file, and B continues

#### Scenario: Two takers

- **WHEN** two replicas try to take over an expired lease at the same moment
- **THEN** exactly one succeeds

### Requirement: Takeover resumes from committed progress

A replica that takes over SHALL continue from the committed repository status and result rows. A file in flight on the old replica
SHALL NOT be counted twice, and a path already written SHALL end as `SKIPPED_SAME` or `COPIED` exactly once.

#### Scenario: Takeover mid-repository

- **WHEN** a job loses its replica after 400 of 1000 files
- **THEN** the new replica ends with 1000 target files, each with one final result row, and no duplicate

#### Scenario: Invalid cursor

- **WHEN** Nexus refuses the stored `continuationToken`
- **THEN** the listing restarts and paths with a final result are skipped

### Requirement: Credentials after a replica loss

A job created with a secret reference SHALL be taken over by any replica that can resolve it. A job created with a literal password
SHALL pause with the reason `CREDENTIALS_NEEDED` when its replica is lost, and `PUT /api/migration/nexus/jobs/{id}/credentials` SHALL
resume it on the replica that receives it.

#### Scenario: Literal password and restart

- **WHEN** the replica restarts during a job created with a literal password
- **THEN** the job is `PAUSED` with `CREDENTIALS_NEEDED` and no password is read from storage

#### Scenario: Reference

- **WHEN** the same happens with a reference
- **THEN** another replica takes the job over without operator action

### Requirement: Read-only mode

A job SHALL NOT start or be taken over while the instance is read-only. A running job SHALL pause after the batch in flight with the
reason `READ_ONLY` and SHALL resume when read-only is off.

#### Scenario: Freeze during import

- **WHEN** read-only mode is turned on while a job runs
- **THEN** the job pauses with `READ_ONLY` and writes no further file

### Requirement: Throttle, concurrency and retries

Requests to Nexus SHALL be limited to `INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND` (default 5) and files in flight to
`INGOT_NEXUS_IMPORT_CONCURRENCY` (default 4). `429`, `5xx` and I/O errors SHALL be retried with backoff, five attempts, honouring
`Retry-After`. `401` and `403` on a download SHALL pause the job with `CREDENTIALS_NEEDED`.

#### Scenario: Rate limited by Nexus

- **WHEN** Nexus answers `429` with `Retry-After`
- **THEN** the request is repeated after that delay, measured on the injected clock

#### Scenario: Invalid setting

- **WHEN** `INGOT_NEXUS_IMPORT_CONCURRENCY` is `0`
- **THEN** the plugin refuses to start and the message names the variable

### Requirement: Operator control and access

The routes under `/api/migration/nexus` SHALL need an instance admin. A job SHALL be pausable, resumable and cancellable, and a
finished job SHALL be deletable with its rows. No route SHALL take a credential in the query string or echo one.

#### Scenario: Cancel

- **WHEN** an admin cancels a running job
- **THEN** it stops after the batch in flight and keeps its result rows

#### Scenario: Token without admin

- **WHEN** a token without instance admin calls any route
- **THEN** the answer is `403`

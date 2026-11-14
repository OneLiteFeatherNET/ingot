# Spec Delta

## Purpose

The deploy target `worker` runs the background jobs of an Ingot cluster and serves no repository or dashboard traffic. A switch
lets `server` replicas leave those jobs to a worker. It extends the requirement "Deploy target values and default" of the
`deploy-modes` capability of the change `add-deploy-modes`.

## ADDED Requirements

### Requirement: Target worker is a valid deploy target

The server SHALL accept the deploy target `worker` from the same sources and with the same precedence as `all` and `server`.
Unknown values SHALL stop the start with a message that names the source and lists `all, server, worker`. The legacy
`defaultFrontend` setting SHALL NOT select `worker`.

#### Scenario: Worker from the environment

- **WHEN** the server starts with `INGOT_TARGET=worker` and the requirements of the shared state requirement are met
- **THEN** the effective target is `worker`

#### Scenario: Unknown value still stops the start

- **WHEN** the server starts with `INGOT_TARGET=reader`
- **THEN** the process prints `Ingot did not start: Unknown deploy target 'reader' from INGOT_TARGET. Valid values: all, server, worker.` and exits with code 1

#### Scenario: Existing targets are unchanged

- **WHEN** the server starts with no target or with `INGOT_TARGET=server`
- **THEN** it registers the same routes and starts the same jobs as before this change

### Requirement: The worker serves only probes and metrics

On target `worker` the server SHALL answer `/api/status/health`, `/api/status/ready` and `/api/status/instance`, and the
`/metrics` route when the Prometheus plugin is installed. It SHALL register no Maven route, no repository, token, settings,
audit, cleanup or search API route and no dashboard route. Every other request SHALL answer `404`.

#### Scenario: Probes answer

- **WHEN** the worker is running and ready
- **THEN** `GET /api/status/health` answers `200` and `GET /api/status/ready` answers `200`

#### Scenario: Repository traffic is not served

- **WHEN** a client sends `GET /releases/org/example/lib/1.0/lib-1.0.jar`, `PUT` of the same path, `GET /api/settings` or `GET /` to the worker
- **THEN** each request answers `404` and nothing is written to storage

### Requirement: The worker runs the singleton background jobs

On target `worker` the server SHALL start the scheduled cleanup runs, the audit retention purge, the search rebuild, verify and
token sweep schedulers, and the search outbox drain. It SHALL keep claiming each job through the existing job claims, so that
two workers never run the same key twice. The per-process duties (statistics flush, shared settings reload, status snapshots,
cluster bus plumbing) SHALL run on every target.

#### Scenario: A due cleanup runs on the worker

- **WHEN** a cleanup policy is due and a worker and a server with `INGOT_BACKGROUND_JOBS=false` share the database
- **THEN** the run is executed by the worker and the server never claims the day key

#### Scenario: Two workers share the day

- **WHEN** two workers tick at the scheduled minute
- **THEN** exactly one of them runs the policy

### Requirement: Switch to stop background jobs on server replicas

The server SHALL read the boolean `INGOT_BACKGROUND_JOBS` (command line option `--background-jobs`, system property
`ingot.background-jobs`, environment variable `INGOT_BACKGROUND_JOBS`, in that precedence), default `true`, with no
`REPOSILITE_` spelling. With `false` on target `server`, the server SHALL NOT start the singleton jobs. With `false` on
target `all` or `worker`, or with another value, the start SHALL stop with one line and exit code 1.

#### Scenario: Default is unchanged

- **WHEN** the server starts on target `server` with `INGOT_BACKGROUND_JOBS` unset
- **THEN** the singleton jobs start as before this change

#### Scenario: Jobs are left to the worker

- **WHEN** the server starts on target `server` with `INGOT_BACKGROUND_JOBS=false`
- **THEN** no cleanup, audit purge, rebuild, verify, token sweep or outbox drain is scheduled, and the statistics flush is

#### Scenario: Contradictory combination

- **WHEN** the server starts on target `all` with `INGOT_BACKGROUND_JOBS=false`
- **THEN** the process prints `Ingot did not start: INGOT_BACKGROUND_JOBS=false is only valid on target server.` and exits with code 1

### Requirement: Shared state is required

The server SHALL refuse to start on target `worker`, and on target `server` with `INGOT_BACKGROUND_JOBS=false`, when any of
these holds: the database is embedded SQLite, no cluster bus URL is set, or a repository is stored on the local file system
while `INGOT_ALLOW_SHARED_FILESYSTEM` is not `true`. The refusal SHALL be one line with exit code 1 that names every unmet
requirement. The check SHALL run before the web server binds and SHALL NOT run on `all` or on `server` with default settings.

#### Scenario: Worker on the default install

- **WHEN** the server starts with `INGOT_TARGET=worker`, the default SQLite database, no `INGOT_CLUSTER_URL` and a repository on the local file system
- **THEN** the process prints one `Ingot did not start:` line that names the embedded database, the missing cluster bus and the repository on the local file system, and exits with code 1

#### Scenario: Shared setup starts

- **WHEN** the server starts with `INGOT_TARGET=worker`, a PostgreSQL database, `INGOT_CLUSTER_URL` set and every repository on object storage
- **THEN** the server starts and answers `/api/status/ready`

#### Scenario: Defaults never check

- **WHEN** the server starts with the default target on SQLite with no bus
- **THEN** it starts as before this change

# Spec Delta

## Purpose

A named deploy target for the Ingot server. `all` keeps today's behaviour and `server` serves the API and the Maven
endpoints without the dashboard. The legacy `defaultFrontend` switch keeps working and means the same thing.

## ADDED Requirements

### Requirement: Deploy target values and default

The server SHALL accept the deploy target `all` or `server`. When no target is given, the server SHALL behave as target
`all`, which serves the dashboard together with the API and the Maven endpoints.

#### Scenario: No target is given

- **WHEN** the server starts with none of `--target`, `-Dingot.target` and `INGOT_TARGET` set, and `defaultFrontend` is not set
- **THEN** the server serves the dashboard at `/` and the API at `/api`, as it did before this change

#### Scenario: Target server serves no dashboard

- **WHEN** the server starts with `INGOT_TARGET=server`
- **THEN** `GET /api/status/health` answers `200`, a Maven artifact request under `/<repository>/<gav>` is served, and `GET /browse`
  is not answered with the dashboard document

### Requirement: Sources and precedence of the target

The server SHALL take the target from the first of these sources that is set: the command line option `--target`, the system
property `ingot.target`, and the environment variable `INGOT_TARGET`. The target is a new setting, so it has no `REPOSILITE_` spelling, and the server SHALL NOT add a
target key to the local configuration file in this change.

#### Scenario: The command line option beats the environment variable

- **WHEN** `INGOT_TARGET=server` is set and the server is started with `--target all`
- **THEN** the effective target is `all`

#### Scenario: The system property beats the environment variable

- **WHEN** `INGOT_TARGET=server` is set and the JVM is started with `-Dingot.target=all`
- **THEN** the effective target is `all`

### Requirement: Refusal of unknown targets

The server SHALL refuse to start when a target source carries a value other than `all` or `server`. The refusal SHALL name
the source and list the valid values. The server SHALL NOT fall back to another target.

#### Scenario: A misspelled target stops the start

- **WHEN** the server starts with `INGOT_TARGET=sever`
- **THEN** the process exits before it binds a port, and the message names `INGOT_TARGET` and lists `all` and `server`

#### Scenario: Blank values count as not set

- **WHEN** `INGOT_TARGET` is set to an empty string and no other source is set
- **THEN** the server behaves as if the variable were not set

### Requirement: Compatibility with the legacy frontend switch

The server SHALL keep honouring `defaultFrontend`, from the configuration file, `INGOT_LOCAL_DEFAULTFRONTEND`,
`REPOSILITE_LOCAL_DEFAULTFRONTEND` and the matching system properties. When no target source is set, `defaultFrontend=false`
SHALL mean target `server`, and any other value SHALL mean target `all`. When a target source is set and `defaultFrontend`
disagrees with it, the target SHALL win and the server SHALL log exactly one warning at start that names both values.

#### Scenario: The legacy switch alone means server

- **WHEN** `INGOT_LOCAL_DEFAULTFRONTEND=false` is set and no target source is set
- **THEN** the effective target is `server`, and no dashboard route is registered

#### Scenario: The target wins over the legacy switch with one warning

- **WHEN** `INGOT_TARGET=all` and `INGOT_LOCAL_DEFAULTFRONTEND=false` are both set
- **THEN** the effective target is `all`, the dashboard is served, and one warning names `all` and `defaultFrontend=false`

#### Scenario: Agreement logs nothing

- **WHEN** `INGOT_TARGET=server` and `INGOT_LOCAL_DEFAULTFRONTEND=false` are both set
- **THEN** the effective target is `server` and no mismatch warning is logged

### Requirement: Drop-in compatibility of the target

Adding the target SHALL NOT change any name or path of the drop-in contract. The image entrypoint, the paths `/app/data` and
`/var/log/reposilite`, the `reposilite` service user, the port `8080`, the environment variables `JAVA_OPTS`,
`REPOSILITE_OPTS`, `PUID`, `PGID` and `REPOSILITE_FORCE_CHOWN`, and the default behaviour without a target SHALL stay as they are.

#### Scenario: An unmodified upstream compose file still starts

- **WHEN** an unmodified upstream Reposilite compose file is run with only its image line replaced by the Ingot image
- **THEN** the server starts, adopts the existing data directory, and serves the dashboard as before

#### Scenario: The target is not needed for an upgrade

- **WHEN** an existing deployment is upgraded to the release that contains this change without any new variable
- **THEN** the effective target is `all` and no operator action is needed

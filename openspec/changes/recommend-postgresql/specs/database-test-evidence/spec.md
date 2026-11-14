# Spec Delta

## Purpose

The evidence for PostgreSQL as the shared database runs where the documentation says it runs: the two-replica smoke test, the
container upgrade rehearsal, the cluster bus test and the settings secret references. The test database images are pinned, so
a test run on the same day as the last one uses the same bytes.

## ADDED Requirements

### Requirement: The two-replica smoke test runs on PostgreSQL

The `ha-smoke` job SHALL run the two-replica smoke test on PostgreSQL as well as on MariaDB, on every pull request. The
PostgreSQL leg SHALL use the same values file and the same five checks as the MariaDB leg. Its dependency manifest SHALL define
one PostgreSQL Service and a connection secret that names that Service.

#### Scenario: Both database legs run on a pull request

- **WHEN** a pull request changes the server
- **THEN** the `ha-smoke` job runs a MariaDB leg and a PostgreSQL leg, and each leg runs the checks for rolling restart, lease takeover, concurrent deploys, sessions and cluster view

#### Scenario: Manifest names its Service

- **WHEN** the unit tests of the chart read the PostgreSQL dependency manifest
- **THEN** they find a Service on port 5432 and a database secret whose connection string names that Service

### Requirement: The upgrade rehearsal runs on PostgreSQL

The container suite SHALL run the upgrade rehearsal against the upstream image with a PostgreSQL database, beside the SQLite and
MariaDB rehearsals. The rehearsal SHALL keep the tokens, the routes and the settings across the switch and the rollback.

#### Scenario: Installation on PostgreSQL survives the switch

- **WHEN** the rehearsal starts the upstream image on PostgreSQL, switches to the new image and rolls back
- **THEN** the tokens, the routes and the settings are the same after each step

#### Scenario: Upstream image cannot start on PostgreSQL

- **WHEN** the upstream image does not accept a PostgreSQL connection string
- **THEN** the PostgreSQL rehearsal is not added, and the docs state that the upgrade from upstream to PostgreSQL is not tested

### Requirement: The cluster bus test runs on PostgreSQL

The container suite SHALL run the two-replica cluster bus test with a PostgreSQL database and the NATS broker, beside the
MariaDB run. Both replicas SHALL find each other through the bus.

#### Scenario: Two replicas on PostgreSQL see each other

- **WHEN** two replicas of the new image share one PostgreSQL database and one NATS broker
- **THEN** each replica lists the other in its cluster view

### Requirement: Settings secret references run on PostgreSQL

The settings secret reference tests SHALL have a PostgreSQL variant that runs through `PostgresSpecificationJunitExtension`,
with the same test class and no change to its assertions.

#### Scenario: PostgreSQL variant

- **WHEN** the integration suite runs
- **THEN** a PostgreSQL subclass of `SettingsSecretReferenceIntegrationTest` runs, and it uses one schema per test

### Requirement: Test database images are pinned

Every database image that a test starts SHALL be named by tag and digest. A tag alone or `latest` SHALL NOT be used. The
integration images in `SharedContainers.kt`, the container suite images and the images of the chart dependency manifests SHALL
all follow this rule. Renovate SHALL be able to raise the tag and the digest together.

#### Scenario: No floating image

- **WHEN** the repository is searched for database image references in test code and test manifests
- **THEN** every reference names a digest, and none uses `latest`

### Requirement: Tests of this change follow the F.I.R.S.T. rules

Each unit test added by this change SHALL need no container, no network, no system clock and no shared static state. Each
container or integration test added by this change SHALL own its database schema or database and drop it after the test.

#### Scenario: Unit tests need no server

- **WHEN** the unit tests of the chart and of the pool mapping run without Docker or Kubernetes
- **THEN** they pass, and they assert the values with messages

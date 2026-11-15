# Spec Delta

## Purpose

A Helm chart for Ingot in this repository, published as OCI on every release. It installs either one workload of the
all-in-one image or a split deployment of the server and the dashboard, and it can take over a release of the upstream
Reposilite chart with a values diff.

## ADDED Requirements

### Requirement: Publication on release

The chart SHALL be published to `oci://ghcr.io/onelitefeathernet/charts/ingot` on every release and SHALL NOT be published
for other pushes. Its `version` and `appVersion` SHALL equal the application release, set by release-please.

#### Scenario: A release publishes the chart with the release version

- **WHEN** release-please creates release `1.2.0`
- **THEN** the chart is pushed with version `1.2.0` and appVersion `1.2.0`

#### Scenario: A nightly push publishes no chart

- **WHEN** a push to main changes nothing that is a release
- **THEN** no chart is pushed

### Requirement: Modes of the chart

The value `mode` SHALL accept `allInOne` (the default) or `split`, and the schema SHALL reject any other value. In `allInOne`
the chart SHALL render one workload of the all-in-one image. In `split` it SHALL render a server workload with
`INGOT_TARGET=server` and a dashboard workload of the dashboard image, with a Service for each.

#### Scenario: The default renders one workload

- **WHEN** the chart is rendered with no mode set
- **THEN** exactly one Deployment and one Service are rendered, with the all-in-one image

#### Scenario: Split renders two workloads

- **WHEN** the chart is rendered with `mode: split`
- **THEN** two Deployments and two Services are rendered, the server Deployment sets `INGOT_TARGET=server`, and the dashboard
  Deployment sets `INGOT_BACKEND` to the server Service

#### Scenario: An unknown mode is rejected

- **WHEN** the chart is installed with `mode: both`
- **THEN** the schema validation fails before anything is rendered

### Requirement: Routing in split mode is generated from the segment list

In `split` mode the chart SHALL render routing that sends `/` as an exact path, the assets, the favicon and every dashboard
segment to the dashboard Service, and every other path to the server Service. The segments SHALL come from the chart's copy of
the shared segment list, and a CI check SHALL fail when that copy differs from the source.

#### Scenario: The overview goes to the dashboard

- **WHEN** the split routing is rendered
- **THEN** the path `/` is routed to the dashboard Service with an exact match, and the path `/` prefix goes to the server Service

#### Scenario: A repository path goes to the server

- **WHEN** a request for `/releases/...` reaches the ingress in split mode
- **THEN** the request is routed to the server Service

#### Scenario: A drifted segment list fails CI

- **WHEN** the chart's copy of the segment list differs from the source
- **THEN** the drift check fails

### Requirement: Hardened dashboard defaults

The dashboard workload SHALL default to UID `65532`, a read-only root filesystem, all capabilities dropped, no privilege
escalation, `runAsNonRoot` and the `RuntimeDefault` seccomp profile.

#### Scenario: The dashboard is hardened by default

- **WHEN** the chart is rendered in `split` mode with no security values
- **THEN** the dashboard container has `runAsUser: 65532`, `readOnlyRootFilesystem: true` and dropped capabilities

### Requirement: Server keeps its upstream security defaults

The server workload SHALL keep the upstream defaults, which set no security context, so that `PUID` and `PGID` keep working.
The chart SHALL NOT set `runAsUser` on the server. The chart documentation SHALL describe it as an opt-in for a volume that is
already owned by that user.

#### Scenario: The server keeps its defaults

- **WHEN** the chart is rendered with no security values
- **THEN** the server container has no security context and the pod has none set by default

### Requirement: Migration from the upstream chart is a values diff

The chart SHALL keep the value keys of the upstream `reposilite` chart 1.4.5 that the staging environment uses, with the same
meaning and the same defaults. With the migration values, the rendered selector labels, the Deployment name, the Service name,
the PersistentVolumeClaim name and the container port SHALL equal those of the upstream chart rendered with the same values.
The migration how-to SHALL list every key and its equivalent.

#### Scenario: Staging values render the same identity

- **WHEN** the staging values are rendered through the upstream chart 1.4.5 and through this chart with `nameOverride` and
  `fullnameOverride` set to `reposilite`
- **THEN** the selector labels, the Deployment name, the Service name, the PersistentVolumeClaim name and the container port are equal

#### Scenario: The data volume is kept

- **WHEN** a release with an existing PersistentVolumeClaim named by the upstream chart is upgraded to this chart with the migration values
- **THEN** the same claim is mounted at `/app/data` and no new claim is created

#### Scenario: Rolling back uses the upstream chart

- **WHEN** a migrated release is rolled back to the upstream chart 1.4.5 with the old values
- **THEN** the same resource names are rendered and the data volume is mounted as before

### Requirement: Optional resources default to off

A ServiceMonitor, a PodDisruptionBudget and an HTTPRoute SHALL be disabled by default. When a PodDisruptionBudget is enabled
it SHALL set `maxUnavailable: 1`. The Ingress and the HTTPRoute SHALL NOT be enabled together.

#### Scenario: Defaults render no optional resource

- **WHEN** the chart is rendered with no optional values
- **THEN** no ServiceMonitor, PodDisruptionBudget or HTTPRoute is rendered

#### Scenario: Enabling both routing forms fails

- **WHEN** `ingress.enabled` and `httpRoute.enabled` are both true
- **THEN** rendering fails with a message that names both keys

### Requirement: Probes match the workload

The probes of the all-in-one workload and of the server workload in `split` mode SHALL use the probe path of the upstream chart
unless the values set another path. The probes of the dashboard SHALL use `/healthz`. In `split` mode the server SHALL be probed
on `/api/status/health`, because the root path is not a page of the server.

#### Scenario: The dashboard probes use the health endpoint

- **WHEN** the chart is rendered in `split` mode
- **THEN** the dashboard liveness and readiness probes use `GET /healthz`

#### Scenario: The server probe avoids the dashboard path

- **WHEN** the chart is rendered in `split` mode
- **THEN** the server liveness and readiness probes use `GET /api/status/health`

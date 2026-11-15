## ADDED Requirements

### Requirement: Readiness and liveness paths per mode

The chart SHALL send the readiness probe of the server to `probes.readinessPath` when that value is set. When it is empty, the
chart SHALL use `probes.path` in `allInOne` and `/api/status/ready` in `split`. The liveness probe of the server SHALL use
`probes.path` in `allInOne` and `/api/status/health` in `split`, in both cases unaffected by `probes.readinessPath`.

#### Scenario: The all-in-one defaults render the root path

- **WHEN** the chart is rendered with no probe value set
- **THEN** the readiness and liveness probes of the server use the path `/`, with the timeout and thresholds of the chart
  defaults

#### Scenario: Split readiness uses the readiness endpoint

- **WHEN** the chart is rendered with `mode: split`
- **THEN** the readiness probe of the server uses `/api/status/ready` and the liveness probe uses `/api/status/health`

#### Scenario: An explicit readiness path wins

- **WHEN** the chart is rendered with `probes.readinessPath` set to `/api/status/ready` in `allInOne`
- **THEN** the readiness probe uses that path and the liveness probe still uses `/`

### Requirement: Probe budget of the split server

In `split` the chart SHALL render the readiness probe of the server with a timeout of 3 seconds and a failure threshold of 2.
In `split` the chart SHALL render a startup probe of the server by default, with a budget of 150 seconds. In `allInOne` the
chart MUST keep the timeouts and thresholds of its defaults and MUST NOT render a startup probe unless `probes.startup.enabled`
is set.

#### Scenario: Split readiness tolerates one slow answer

- **WHEN** the chart is rendered with `mode: split` and no probe value set
- **THEN** the readiness probe has `timeoutSeconds: 3` and `failureThreshold: 2`

#### Scenario: Split starts with a startup probe

- **WHEN** the chart is rendered with `mode: split` and no probe value set
- **THEN** the server has a startup probe on `/api/status/health` with `periodSeconds: 5` and `failureThreshold: 30`

#### Scenario: All-in-one keeps its probe budget

- **WHEN** the chart is rendered with no mode and no probe value set
- **THEN** no startup probe is rendered and the readiness probe keeps `timeoutSeconds: 1` and `failureThreshold: 1`

### Requirement: Pre-stop sleep

The chart SHALL render a `preStop` `exec` action that runs `sleep` for `deployment.shutdown.preStopSeconds` seconds when that
value is above zero and `deployment.lifecycle` is empty. When `deployment.lifecycle` is set, the chart SHALL keep the operator's
own `preStop` action and render no sleep of its own.

#### Scenario: The preStop sleep is rendered

- **WHEN** the chart is rendered with `deployment.shutdown.preStopSeconds: 15` and `deployment.lifecycle` empty
- **THEN** the server has a `preStop` `exec` action with `sleep 15`

#### Scenario: An operator lifecycle wins over the preStop sleep

- **WHEN** the chart is rendered with `preStopSeconds: 15` and `deployment.lifecycle` set to its own `preStop` action
- **THEN** the server keeps the operator's `preStop` action and renders no sleep of its own

### Requirement: Drain timeout variable

The chart SHALL render `INGOT_SHUTDOWN_TIMEOUT` with the value of `deployment.shutdown.drainSeconds` when that value is above
zero and the operator has not set `INGOT_SHUTDOWN_TIMEOUT` in `env`.

#### Scenario: The drain timeout is rendered

- **WHEN** the chart is rendered with `deployment.shutdown.drainSeconds: 30` and `env` does not set `INGOT_SHUTDOWN_TIMEOUT`
- **THEN** the server container sets `INGOT_SHUTDOWN_TIMEOUT` to `30`

#### Scenario: An operator value in env is not duplicated

- **WHEN** the chart is rendered with `drainSeconds: 30` and `env` sets `INGOT_SHUTDOWN_TIMEOUT` to `10`
- **THEN** the server container names `INGOT_SHUTDOWN_TIMEOUT` exactly once, with the value `10`

### Requirement: Derived grace period

When either `deployment.shutdown.preStopSeconds` or `deployment.shutdown.drainSeconds` is above zero, the chart MUST set
`terminationGracePeriodSeconds` to `preStopSeconds + drainSeconds + 10`. When both values are zero, the chart MUST keep the
grace period of the chart defaults and MUST render no `preStop` action and no `INGOT_SHUTDOWN_TIMEOUT` variable.

#### Scenario: Defaults keep the grace period

- **WHEN** the chart is rendered with no shutdown value set
- **THEN** `terminationGracePeriodSeconds` is `60`, and the server has no `preStop` action and no `INGOT_SHUTDOWN_TIMEOUT`
  variable

#### Scenario: The preStop sleep and the grace period are derived

- **WHEN** the chart is rendered with `deployment.shutdown.preStopSeconds: 15` and `drainSeconds: 30`
- **THEN** the server has a `preStop` `exec` action with `sleep 15`, `INGOT_SHUTDOWN_TIMEOUT` is `30`, and
  `terminationGracePeriodSeconds` is `55`

### Requirement: Replica warnings in the notes

When the server runs more than one replica or the autoscaler is enabled, the chart's notes SHALL warn about each of these
conditions that holds: persistence uses a `ReadWriteOnce` claim without `existingClaim`; persistence is disabled, so each pod
keeps its own data; `env` sets neither `INGOT_LOCAL_DATABASE` nor `REPOSILITE_LOCAL_DATABASE`; and the autoscaler lists a
`memory` Resource metric. The chart MUST NOT fail the install for any of these conditions.

#### Scenario: A single replica with defaults gets no new warning

- **WHEN** the chart is installed with `--dry-run=client` and no replica or autoscaling value set
- **THEN** the notes contain none of the new warnings

#### Scenario: Several replicas on a ReadWriteOnce claim are warned about

- **WHEN** the chart is installed with two replicas and a `ReadWriteOnce` persistence claim without `existingClaim`
- **THEN** the notes warn about the claim and the install succeeds

#### Scenario: A configured external database silences the database warning

- **WHEN** the chart is installed with two replicas and `env` sets `INGOT_LOCAL_DATABASE`
- **THEN** the notes contain no database warning

#### Scenario: A memory metric is warned about only with the autoscaler on

- **WHEN** the chart is installed with the autoscaler enabled and its default metrics
- **THEN** the notes warn about the memory metric, and with the autoscaler disabled the notes do not

#### Scenario: Warnings never fail the install

- **WHEN** any combination of the replica values is installed with `--dry-run=client`
- **THEN** the command exits with status 0

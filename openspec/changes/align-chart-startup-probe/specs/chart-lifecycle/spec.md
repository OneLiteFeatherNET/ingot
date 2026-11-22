# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Probe budget of the split server`
- TO: `### Requirement: Probe budget of the server`

## MODIFIED Requirements

### Requirement: Probe budget of the server

The chart SHALL render the readiness probe of the server with a timeout of 3 seconds and a failure threshold of 2 in `split` and in
`allInOne`. The chart SHALL render a startup probe of the server on the probe path of the server in every mode by default, with
`periodSeconds` 5, `timeoutSeconds` 2 and `failureThreshold` 60, a budget of 300 seconds. The chart SHALL NOT render the startup probe
when `probes.startup.enabled` is `false`, and SHALL keep the timeouts and thresholds of the liveness probe unchanged.

#### Scenario: Split readiness tolerates one slow answer

- **WHEN** the chart is rendered with `mode: split` and no probe value set
- **THEN** the readiness probe has `timeoutSeconds: 3` and `failureThreshold: 2`

#### Scenario: Split starts with a startup probe

- **WHEN** the chart is rendered with `mode: split` and no probe value set
- **THEN** the server has a startup probe on `/api/status/health` with `periodSeconds: 5` and `failureThreshold: 60`

#### Scenario: All-in-one keeps its probe budget

- **WHEN** the chart is rendered with no mode and no probe value set
- **THEN** the server has a startup probe on the probe path of the server with `periodSeconds: 5` and `failureThreshold: 60`, the
  readiness probe has `timeoutSeconds: 3` and `failureThreshold: 2`, and the liveness probe keeps its defaults

#### Scenario: The startup probe can be turned off

- **WHEN** the chart is rendered with `probes.startup.enabled: false`
- **THEN** no startup probe is rendered

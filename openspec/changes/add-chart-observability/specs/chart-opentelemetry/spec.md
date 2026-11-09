# Spec Delta

## Purpose

The OpenTelemetry Java agent is a supported, opt-in option of the chart, so a cluster does not copy staging-only wiring.

## ADDED Requirements

### Requirement: Opt-in agent wiring

The chart SHALL render the OpenTelemetry Java agent only when `opentelemetry.enabled` is `true`. When it is enabled, the
server workload SHALL run an init container from `opentelemetry.agentImage` that copies the agent jar into an emptyDir,
SHALL mount that emptyDir read-only in the server container, and SHALL set the agent flag in `JDK_JAVA_OPTIONS`.

#### Scenario: Default render has no agent

- **WHEN** the chart is rendered with no `opentelemetry` value
- **THEN** no init container, no agent volume, no agent mount and no `-javaagent` flag are rendered, and the server
  environment is the same as before this change

#### Scenario: Enabled render wires the agent

- **WHEN** the chart is rendered with `opentelemetry.enabled: true`
- **THEN** the server Deployment has an init container with the configured agent image, an emptyDir volume that the
  init container and the server container mount, and `JDK_JAVA_OPTIONS` contains `-javaagent:/otel/javaagent.jar` once

### Requirement: Agent flag merged into one environment entry

The chart MUST NOT render a second `JDK_JAVA_OPTIONS` entry. When the operator already sets `JDK_JAVA_OPTIONS` in `env`,
the rendered value SHALL keep the operator's text unchanged and append the chart flags.

#### Scenario: Operator value is kept

- **WHEN** `env` sets `JDK_JAVA_OPTIONS` to `-Dfoo=bar` and `opentelemetry.enabled` is `true`
- **THEN** exactly one `JDK_JAVA_OPTIONS` entry is rendered, and its value starts with `-Dfoo=bar` and contains the agent flag

### Requirement: Agent settings from values

When the agent is enabled, the server SHALL receive `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT` and
`OTEL_EXPORTER_OTLP_PROTOCOL` from `opentelemetry` values, and the trace and metric exporters from the same block. The
log exporter SHALL default to `none`.

#### Scenario: Endpoint from values

- **WHEN** the chart is rendered with `opentelemetry.enabled: true` and an endpoint
- **THEN** the server environment contains `OTEL_EXPORTER_OTLP_ENDPOINT` with that endpoint and `OTEL_LOGS_EXPORTER` set to `none`

### Requirement: Server only

The agent SHALL be rendered on the server workload only. The dashboard workload of the split mode SHALL NOT receive the
agent, the agent volume or the agent variables.

#### Scenario: Split mode

- **WHEN** the chart is rendered with `mode: split` and `opentelemetry.enabled: true`
- **THEN** the server Deployment carries the agent and the dashboard Deployment does not

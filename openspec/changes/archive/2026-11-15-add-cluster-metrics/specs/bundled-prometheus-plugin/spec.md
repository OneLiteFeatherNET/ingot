# Spec Delta

## Purpose

The image carries the metrics plugin, and an operator turns it on with one switch and no file to copy. Without the switch, and with
an operator's own copy of the plugin, nothing changes.

## ADDED Requirements

### Requirement: Image ships the plugin outside the data volume

The image SHALL contain `prometheus-plugin.jar` in `/app/bundled-plugins/` and SHALL NOT place it in `/app/data`.

#### Scenario: Image contents

- **WHEN** the image is built
- **THEN** `/app/bundled-plugins/prometheus-plugin.jar` exists and `/app/data/plugins` contains no file that the image added

### Requirement: Switch controls loading

The loader SHALL load the bundled plugin only when `INGOT_METRICS_ENABLED` is `true`, or when the legacy `REPOSILITE_METRICS_ENABLED`
is `true` and `INGOT_METRICS_ENABLED` is unset. Without the switch the loader SHALL NOT open the bundled jar.

#### Scenario: Switch unset

- **WHEN** the image starts with no metrics variable set
- **THEN** no plugin is loaded from `/app/bundled-plugins`, no metrics endpoint is registered and the start-up log is the same as before the change

#### Scenario: Switch on

- **WHEN** the image starts with `INGOT_METRICS_ENABLED=true` and the credentials set
- **THEN** the plugin is loaded from `/app/bundled-plugins` and the metrics endpoint answers at `/metrics`

#### Scenario: Legacy spelling

- **WHEN** the image starts with `REPOSILITE_METRICS_ENABLED=true` and no `INGOT_METRICS_ENABLED`
- **THEN** the plugin is loaded as in the switch-on scenario

### Requirement: The operator's own copy takes precedence

When a jar in the plugins directory declares a plugin with the same id as the bundled plugin, the loader SHALL NOT load the bundled
copy, and it SHALL register exactly one plugin with that id.

#### Scenario: Operator's jar present

- **WHEN** `prometheus-plugin.jar` is in the plugins directory and the switch is on
- **THEN** the operator's jar is the one that starts, the bundled copy is not registered, and start-up succeeds

### Requirement: Plugin settings accept the INGOT spelling

The plugin SHALL read `INGOT_PROMETHEUS_PATH`, `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD` before the existing
`REPOSILITE_PROMETHEUS_*` names and the matching system properties. The plugin SHALL keep reading the existing names.

#### Scenario: Existing deployment

- **WHEN** a deployment sets only `REPOSILITE_PROMETHEUS_USER` and `REPOSILITE_PROMETHEUS_PASSWORD`
- **THEN** the plugin starts with those credentials and the endpoint answers as before

#### Scenario: Missing credentials

- **WHEN** the switch is on and no user is set in any spelling
- **THEN** start-up fails with the message `Prometheus user is not defined`, as it does today

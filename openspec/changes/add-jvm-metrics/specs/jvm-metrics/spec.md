# Spec Delta

## Purpose

An operator who enables the metrics endpoint can leave out the JVM series, and existing scrapes keep working without a change.

## ADDED Requirements

### Requirement: JVM series switch

The plugin SHALL register the JVM series (memory, garbage collection, threads, class loading, process) unless `INGOT_PROMETHEUS_JVM_METRICS`
is `false`, or, when that is unset, the legacy property `reposilite.prometheus.jvm_metrics` or `REPOSILITE_PROMETHEUS_JVM_METRICS` is `false`.

#### Scenario: Default

- **WHEN** the plugin starts with no JVM metrics setting
- **THEN** the JVM series are registered as before this change

#### Scenario: Switched off

- **WHEN** the plugin starts with `INGOT_PROMETHEUS_JVM_METRICS=false`
- **THEN** no JVM series is registered and the application series, including `ingot_http_server_request_duration_seconds`, are unchanged

#### Scenario: Other values

- **WHEN** the setting holds any value other than `false`
- **THEN** the JVM series are registered

### Requirement: Documented series

The metrics reference SHALL list the JVM series families and the switch.

#### Scenario: Reference page

- **WHEN** an operator opens the metrics reference
- **THEN** it names the families, the switch, its default and its legacy spellings

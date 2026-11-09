# Spec Delta

## Purpose

An operator with the Prometheus Operator and Grafana gets alerts and dashboards for Ingot with one flag each.

## ADDED Requirements

### Requirement: Base alert rules behind a flag

The chart SHALL render a PrometheusRule with the base alerts only when `prometheusRule.enabled` is `true`. The base alerts
SHALL cover the server being down, the error ratio of responses, upstream repositories being unreachable, failed deploys,
heap pressure and the cluster bus being disconnected. Every alert SHALL reference only metric names that the image exports.

#### Scenario: Default render has no rule

- **WHEN** the chart is rendered with no `prometheusRule` value
- **THEN** no PrometheusRule is rendered

#### Scenario: Enabled render

- **WHEN** the chart is rendered with `prometheusRule.enabled: true`
- **THEN** one PrometheusRule is rendered with the base alerts, and each alert expression names a metric listed in the
  exported-metrics list checked by CI

### Requirement: Grafana dashboards as ConfigMaps behind a flag

The chart SHALL render one ConfigMap per dashboard when `grafanaDashboards.enabled` is `true`, for the overview, mirroring
and JVM dashboards. Each ConfigMap SHALL carry the label `grafana_dashboard: "1"` and hold valid JSON.

#### Scenario: Default render has no dashboard

- **WHEN** the chart is rendered with no `grafanaDashboards` value
- **THEN** no dashboard ConfigMap is rendered

#### Scenario: Enabled render

- **WHEN** the chart is rendered with `grafanaDashboards.enabled: true`
- **THEN** three ConfigMaps are rendered, each labelled `grafana_dashboard: "1"`, and each data value parses as JSON

### Requirement: Rules checked in CI

The rules SHALL pass `promtool check rules` in CI, and the CI job SHALL fail when an alert names a metric that is not in the
checked-in list of exported names.

#### Scenario: Unknown metric

- **WHEN** an alert expression names a metric that is not in the exported-metrics list
- **THEN** the CI job fails and names the metric

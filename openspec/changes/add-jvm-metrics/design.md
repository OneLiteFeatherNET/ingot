# Design

## Context

`PrometheusSettings.read` takes the environment and a property lookup and resolves each key from `INGOT_PROMETHEUS_<NAME>`, then the
legacy property, then the legacy environment name. `PrometheusPlugin.initialize` registers `JvmMetrics` on the default registry
unconditionally.

## Decisions

### D1. One optional key

`jvmMetrics: Boolean` with default `true`, resolved with the same three-step order through the same `setting` helper. Only the exact
value `false` turns the series off; any other value, and absence, keep them on, so a typo never removes series silently.

### D2. Registration takes a registry

The JVM registration goes through a function that takes the registry, so a test uses a fresh registry and the default registry is
touched only by the plugin.

### D3. Documentation lists series by family

The reference page names the families and the metric name prefixes the client library registers, and says the names come from the
library. It does not copy every name, so it does not go stale on a library upgrade.

## Open questions

- **Q1.** Whether the maintainer wants JVM series off by default for new installs only. The proposal assumes not, because the plugin
  cannot tell a new install from an existing one without a marker file, which adds state.

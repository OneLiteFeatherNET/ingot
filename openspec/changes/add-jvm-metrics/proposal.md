# Proposal

## Why

The maintainer asked for opt-in JVM metrics (garbage collection, heap, threads) and HTTP handler timings for Prometheus, off by default.
Most of that exists. The bundled metrics plugin is off unless `INGOT_METRICS_ENABLED=true` (`bundled-prometheus-plugin`), it registers
the JVM series of the Prometheus client (`PrometheusPlugin.kt:44`, `JvmMetrics.builder().register()`), and
`ingot_http_server_request_duration_seconds` records handler timings (`http-server-metrics`). What is missing is control: an operator
who scrapes only the application series cannot leave the JVM series out, and nothing documents which JVM series appear.

Making the JVM series off by default would remove series from every deployment that already has the plugin on and scrapes them. That
would break dashboards, which the drop-in rule forbids. This change therefore adds an opt-out that defaults to today's behaviour.

## What Changes

- A setting `INGOT_PROMETHEUS_JVM_METRICS` (legacy spelling `REPOSILITE_PROMETHEUS_JVM_METRICS`, property
  `reposilite.prometheus.jvm_metrics`) with default `true`. When it is `false`, the plugin does not register the JVM series.
- The metrics reference page lists the JVM series (memory, garbage collection, threads, class loading, process) and the HTTP series.
- No new series, no renamed series, no changed labels.

### Out of scope

- Changing what is on by default, and the switch that loads the plugin.
- New HTTP series; the existing histogram and counter stay as specified.
- Other exporters and OpenTelemetry.

## Capabilities

### New Capabilities

- `jvm-metrics`: the switch for the JVM series and their documentation.

## Impact

- **Type and scope.** The change ships under `feat(prometheus)`. Its commits are `test(prometheus)`, `feat(prometheus)` and
  `docs(prometheus)`, one type each. The pull request title is `feat(prometheus): let an operator leave out the jvm metrics`.
- **Breaking marker.** None. The default keeps today's series. The new variable has the `INGOT_` prefix with the legacy spelling as
  fallback, as the other plugin settings do.
- **Order.** Independent of the other open changes.

## Verification

- Unit tests of the settings reading with an injected environment and property lookup, and of the registration with a fresh registry
  per test.
- `openspec validate add-jvm-metrics --strict --no-interactive` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-jvm-metrics` prints nothing.

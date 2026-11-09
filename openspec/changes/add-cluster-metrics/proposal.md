# Proposal

## Why

Ingot's metrics come from the `prometheus` plugin, and the plugin is not part of the image. A default install exposes no
metrics at all, so the chart's ServiceMonitor scrapes nothing until an operator builds the plugin, copies it into the
plugins directory and sets its credentials by hand. When an operator does get it running, the numbers are hard to use for
several replicas. Response times are kept as Prometheus summaries (`JettyMetrics.kt:153`), which cannot be aggregated across
pods, and they are timed with wall-clock milliseconds (`PrometheusPlugin.kt:71,80`), which move when the clock is adjusted. The
plugin also exposes nothing about the things that matter when several replicas share one database: failed or refused deploys,
upstream health, the cluster bus, the search outbox and the batch jobs.

This change adds those signals in a form that aggregates across replicas, and it ships the plugin in the image behind a switch
so that an operator can turn metrics on without building anything. Nothing changes until the switch is set.

Facts checked against origin/main (`git show origin/main:...`):

- `reposilite-plugins/prometheus-plugin/.../metrics/JettyMetrics.kt:15,153` declares `responseTimeSummary` as a `Summary`
  named `jetty_response_time_seconds`.
- `reposilite-plugins/prometheus-plugin/.../PrometheusPlugin.kt:71,80` times requests with `System.currentTimeMillis()`.
- `reposilite-plugins/prometheus-plugin/.../PrometheusPlugin.kt:32-42` reads `REPOSILITE_PROMETHEUS_PATH`, `_USER` and `_PASSWORD`
  (and the matching system properties) and throws when user or password is missing. It does not read any `INGOT_` name.
- The `Dockerfile` builds only `:reposilite-backend:shadowJar` and copies only `ingot-*.jar`, so the image has no
  `prometheus-plugin.jar`. `charts/ingot/values.yaml:155-157` says so.
- `reposilite-backend/.../plugin/PluginLoader.kt:65-84` loads plugins only from the plugins directory (`-pd`, default
  `plugins` in the working directory, `/app/data/plugins` in the image).
- `reposilite-backend/.../plugin/Extensions.kt:47` calls `check(!plugins.containsKey(name))` in `registerPlugin`. A second
  plugin with the same id therefore stops start-up with "Plugin prometheus is already registered". It does not register
  collectors twice. The roadmap's wording for open verification 5 is corrected accordingly; see design D3.
- The `ingot_*` prefix is not used anywhere on main yet, and no metric in the plugin reads a cluster, outbox or job value.
- The cluster bus (`reposilite-backend/.../net/onelitefeather/ingot/cluster/`, change `add-cluster-bus`) and the search
  outbox (`LocalConfiguration.kt:161`) are on main.

## What Changes

- **Request histograms (`feat(metrics)`).** Add `ingot_http_server_request_duration_seconds` (histogram) and
  `ingot_http_server_requests_total` (counter), labelled with method, the route template and the status code. They are
  timed with an injected monotonic ticker, not with the wall clock. The route label is the matched route template, and an
  unmatched request is labelled `unmatched`, so no raw path or client-chosen string becomes a label. The existing
  `jetty_*` and `reposilite_*` series keep their names and types, and the new series are added next to them (the roadmap's
  decision to keep summaries and add histograms).
- **Operational gauges and counters (`feat(metrics)`).** Add these, each read at scrape time from the facade that owns the
  value, so no background thread is added:
  - `ingot_deploy_results_total{result}` for accepted and rejected deploys.
  - `ingot_upstream_requests_total{outcome}` for proxy upstream fetches (`success`, `not_found`, `failure`, `timeout`).
  - `ingot_mirror_cache_entries` for the mirror cache.
  - `ingot_cluster_connected` (0 or 1) and `ingot_cluster_dropped_events_total`, only when the cluster bus is configured.
  - `ingot_batch_job_last_success_timestamp_seconds{job}` for the cleanup and audit purge jobs.
  - `ingot_search_outbox_pending` and `ingot_search_outbox_oldest_age_seconds`.
  Label values come from fixed sets. No label carries a repository, path, user or token name.
- **Opt-in bundling (`feat(metrics)`).** The image ships `prometheus-plugin.jar` in `/app/bundled-plugins/`, outside
  `/app/data`. The loader reads that directory only when `INGOT_METRICS_ENABLED=true` (fallback `REPOSILITE_METRICS_ENABLED`).
  Without the switch the image starts exactly as it does today. A jar with the same plugin id in the plugins directory wins,
  and the bundled copy is then not loaded.
- **Setting names (`feat(metrics)`).** The plugin reads `INGOT_PROMETHEUS_PATH`, `INGOT_PROMETHEUS_USER` and
  `INGOT_PROMETHEUS_PASSWORD` first, then the existing `REPOSILITE_PROMETHEUS_*` spellings and the system properties. Nothing
  that works today stops working.
- **Documentation (`docs(metrics)`).** The new switch and variables go in `docs/gitbook/reference/environment-variables.md`,
  and the scrape how-to in `docs/gitbook/how-to/monitor-status-and-failures.md` loses its "build the plugin" step for the
  image. The metric list goes on the same how-to page.

## Impact

- **Operators.** No BREAKING item. The default image, default settings and every existing endpoint are unchanged. An
  operator who wants metrics sets `INGOT_METRICS_ENABLED=true` and the credentials (`INGOT_PROMETHEUS_USER` and
  `INGOT_PROMETHEUS_PASSWORD`, or the existing `REPOSILITE_` names). With the switch on and no credentials, start-up fails
  with the plugin's existing message, as it does today when the plugin is installed without them.
- **Operators who already run the plugin.** Their own `prometheus-plugin.jar` keeps working, and the bundled copy does not
  load. The metric names of the existing series do not change. The new series are additive.
- **Image.** The image grows by the size of one jar. The image contract (entrypoint, paths, `PUID`, `PGID`, adoption of an
  existing `/app/data`) is unchanged, and the "Container images" CI job must stay green.
- **Chart.** Not changed here. `charts/ingot` still needs `serviceMonitor.basicAuth` and the switch, and the chart work is
  `add-chart-observability` (N10, N11), which depends on this change.
- **Other changes.** `add-chart-observability` depends on this change. `add-cluster-bus` is on main and supplies the cluster
  gauges. The plugin loader change touches `PluginLoader.kt`, which no other change in waves A to E touches.

## Out of scope

- Trace spans and the OpenTelemetry agent (N10 and later).
- Chart values, the PrometheusRule and Grafana dashboards (N11, `add-chart-observability`).
- Active-request gauges for KEDA (S9, which builds on these gauges).
- JSON logs and the access log (N9).
- Making the plugin part of the default image, or turning metrics on by default.
- SLO definitions (Sloth, Strategic).
- Changing the metric names or types of existing `jetty_*` and `reposilite_*` series.

## Conditional on verification

The roadmap's open verification 5 asks whether a user's `prometheus-plugin.jar` causes a duplicate load. The code points to a
start-up failure, not to double registration. The first implementation task in `tasks.md` (2.1) is a spike test that proves the current
behaviour. The precedence rule in this proposal is implemented only in the form that test shows is needed.

## Type, scope and pull request

- Type and scope: `feat(metrics)`. The commits in the code pull request use `test(metrics)`, `build(metrics)` and
  `docs(metrics)` as well, one type per commit, and the change ships under `feat(metrics)`.
- Pull request title: `feat(metrics): add request histograms and health gauges behind a flag`

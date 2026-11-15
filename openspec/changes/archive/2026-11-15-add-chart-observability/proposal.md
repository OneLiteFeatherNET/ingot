# Proposal

## Why

An operator who runs Ingot on Kubernetes with the Helm chart has to build four things by hand, and the chart can do none of them.

Tracing works only in one cluster, the staging one. The OpenTelemetry Java agent is wired through a hand-written init
container and a list of `OTEL_*` variables in the staging values (`charts/ingot/tests/fixtures/staging-values.yaml:43-45`,
`:169-190`). A new install has to copy that and keep it in step with the chart by hand.

The heap is sized by one default, `JAVA_OPTS=-Xmx64M` (`charts/ingot/values.yaml:176-177`). Nothing ties it to the
container limit. Staging runs `-Xmx256M` under a 2Gi limit, and the docs admit those numbers were never measured. An
operator who raises the memory limit gets no larger heap.

The chart ships a ServiceMonitor and nothing to act on it. There is no alert rule and no dashboard, so a scraped Ingot
shows numbers that nobody reads.

Shared settings can be declarative in the backend, but the chart has no value that supplies the file. A ConfigMap
mounted by hand is read-only, and a save in the dashboard would then fail. The reload change adds the read-only guard
that makes such a mount safe.

## What Changes

- **Opt-in OpenTelemetry agent (N10).** `opentelemetry.enabled` (default `false`) renders the agent init container, an
  emptyDir that holds the agent jar, a read-only mount in the server container and the `OTEL_*` variables. The
  `-javaagent` flag is merged into `JDK_JAVA_OPTIONS`. Only the server workload gets the agent.
- **Opt-in JVM options (JVM block).** A `jvm` block with `maxRAMPercentage`, `initialRAMPercentage`, `gc`,
  `activeProcessorCount` and `extraOptions`. Nothing is rendered unless a key is set. The flags go into `JDK_JAVA_OPTIONS`,
  the same merged variable as the agent. NOTES.txt warns when `JAVA_OPTS` still carries an `-Xmx`, because an explicit
  `-Xmx` overrides the percentage.
- **Shared settings wiring (N1 chart part).** `sharedConfiguration` with `enabled`, `content` or `existingConfigMap`. The
  file is mounted as a read-only directory at `/app/shared-config` (never with `subPath`). The server gets
  `INGOT_SHARED_CONFIGURATION_RELOAD=true` and `INGOT_SHARED_CONFIGURATION_FILE` pointing at it. A checksum annotation
  restarts the pods when chart-owned content changes. An existing ConfigMap is picked up by the reload, without a restart.
- **Alert rules and dashboards (N11).** `prometheusRule.enabled` and `grafanaDashboards.enabled`, both off by default. The
  rules cover availability, error ratio, upstream reachability, deploy failures, heap pressure and the cluster bus. The
  dashboards are overview, mirroring and JVM, shipped as ConfigMaps labelled for the Grafana sidecar. The same JSON is
  linked from the monitoring how-to for Compose users.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the new variable,
  `docs/gitbook/how-to/run-on-kubernetes.md` for the chart values, and `docs/gitbook/how-to/monitor-status-and-failures.md`
  for the rules and dashboards.

Pull request type and title. The change ships as one `feat(chart)` change. The pull request title, squash-merged onto main,
is:

`feat(chart): add opt-in telemetry, jvm options and shared settings`

### Out of scope

- Domain spans and the `opentelemetry-api` dependency in the backend. They come later as `feat(observability)`.
- The `ingot_*` metrics, the histograms and shipping the prometheus plugin in the image. These belong to `add-cluster-metrics`.
- The reload itself, the content hash, the 409 guard for file-managed domains and the new environment variable. These belong
  to `add-shared-configuration-reload`.
- Measured heap and CPU figures, the sizing tables and the "JVM sizing in containers" page. These belong to the
  documentation bundle in `docs-cloud-native-guides`.
- Changing any default value, `JAVA_OPTS`, the staging fixture or the dashboard workload.

## Capabilities

### New Capabilities

- `chart-opentelemetry`: the opt-in agent wiring and its environment.
- `chart-jvm-options`: the opt-in JVM flags and their merge into `JDK_JAVA_OPTIONS`.
- `chart-shared-configuration`: the opt-in mount of the shared settings file and the reload switch.
- `chart-monitoring-rules`: the opt-in PrometheusRule and the Grafana dashboard ConfigMaps.

## Impact

- **Breaking marker.** No breaking change. Every new value renders nothing when unset, and the default render keeps every
  object, environment variable and argument it has today. Nothing has to change for an operator who swaps the image line,
  and the title carries no `!` and no `BREAKING CHANGE:` footer.
- **New keys.** `opentelemetry`, `jvm`, `sharedConfiguration` and `prometheusRule`, `grafanaDashboards` in
  `charts/ingot/values.yaml`, each documented there and typed in `values.schema.json`. The new environment variable
  `INGOT_SHARED_CONFIGURATION_FILE` is read by the backend change named above, with the `REPOSILITE_` spelling as fallback.
- **Dependencies.** `add-cluster-metrics` (the metric names the rules and dashboards use, and the image that ships the
  plugin). `add-shared-configuration-reload` (the reload switch, the 409 guard for read-only files and the file variable).
  This change must not be merged before either. It also rebases on the Wave C chart changes that touch
  `templates/deployment.yaml` and `values.yaml`: `add-chart-lifecycle-and-warnings`, `add-chart-ha-values` and the chart part of
  `add-file-secrets`.
- **Code.** `charts/ingot/` (templates, values, schema, `files/` for dashboard JSON, tests) and the three docs pages above.

## Verification

- Render tests in `charts/ingot/tests` run with `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`,
  including the default render that must stay equal to today's, and `test_equivalence.py` must stay green.
- The promtool check of the rules runs in CI.
- `openspec validate add-chart-observability --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r` on this change prints nothing.

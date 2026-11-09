# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `chart`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock, no shared mutable state, a temporary directory per test, and assertions with messages. Render tests run
`helm template` through `charts/ingot/tests` with `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`.
No Gradle run is needed. Each test names one behaviour.

Start the group work only after `add-cluster-metrics` and `add-shared-configuration-reload` are merged (see the proposal).

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-chart-observability`: this change and nothing else. Verify:
  `openspec validate add-chart-observability --strict` passes and the dash grep from the proposal prints nothing.

## 2. JVM options

- [ ] 2.1 `test(chart)`: write failing render tests: no `jvm` value renders no flag and keeps `JAVA_OPTS`; `maxRAMPercentage: 70`
  renders `-XX:MaxRAMPercentage=70` once in `JDK_JAVA_OPTIONS`; an operator `JDK_JAVA_OPTIONS` with the same flag fails the
  render and names it; `JAVA_OPTS` with `-Xmx` gives the NOTES warning and no failure. Verify: they fail before 2.2.
- [ ] 2.2 `feat(chart)`: add the `jvm` block to `values.yaml` and `values.schema.json`, the flag helper and the single merged
  `JDK_JAVA_OPTIONS` entry in `templates/_helpers.tpl` and `templates/deployment.yaml`, and the NOTES warning. Verify: the
  tests of 2.1 pass and `test_equivalence.py` stays green.
- [ ] 2.3 `docs(deploy)`: document the `jvm` keys and the `-Xmx` precedence in `docs/gitbook/how-to/run-on-kubernetes.md` and
  the variable in `docs/gitbook/reference/environment-variables.md`. Do not add measured figures; those belong to the sizing docs.

## 3. OpenTelemetry agent

- [ ] 3.1 `test(chart)`: write failing render tests: default render has no init container, agent volume or `-javaagent`; enabled
  render has the init container with the configured image, an emptyDir mounted read-only at `/otel`, one `JDK_JAVA_OPTIONS`
  with the agent flag; an operator `JDK_JAVA_OPTIONS` value is kept and extended; the `OTEL_*` variables come from values
  with `OTEL_LOGS_EXPORTER=none` by default; split mode puts the agent on the server only. Add a test that the enabled values
  render the same agent init container and `OTEL_*` variables as `charts/ingot/tests/fixtures/staging-values.yaml`, so the
  fixture stays the reference. Verify: they fail before 3.2.
- [ ] 3.2 `feat(chart)`: add the `opentelemetry` block to `values.yaml` and `values.schema.json`, and the init container, volume,
  mount and variables in `templates/deployment.yaml` through the helper of 2.2. Verify: the tests of 3.1 pass.
- [ ] 3.3 `docs(deploy)`: document the block and the Operator annotation as an alternative in
  `docs/gitbook/how-to/run-on-kubernetes.md`. The agent image is pinned in values by tag, as Renovate does for other images.

## 4. Shared settings wiring

- [ ] 4.1 `test(chart)`: write failing render tests: `content` renders one ConfigMap with key `configuration.shared.json`;
  `existingConfigMap` renders no ConfigMap and names that volume source; enabled with neither key fails the render and names
  both keys; the mount is read-only at `/app/shared-config` with no `subPath`; `/app/data` is unchanged; the server gets
  `INGOT_SHARED_CONFIGURATION_RELOAD=true` and `INGOT_SHARED_CONFIGURATION_FILE`; the checksum annotation changes with the
  content and is absent for an existing ConfigMap; the dashboard gets none of it; the default render has none of it. Verify:
  they fail before 4.2.
- [ ] 4.2 `feat(chart)`: add the `sharedConfiguration` block to `values.yaml` and `values.schema.json`, the ConfigMap template,
  the volume, mount, variables and checksum in `templates/deployment.yaml`. Verify: the tests of 4.1 pass.
- [ ] 4.3 `docs(deploy)`: document the GitOps flow (mounted ConfigMap as a directory, reload without a restart for an existing
  ConfigMap, the restart for chart-owned content) in `docs/gitbook/how-to/run-on-kubernetes.md`, and the variable in
  `docs/gitbook/reference/environment-variables.md`.

## 5. Alert rules and dashboards

- [ ] 5.1 `test(chart)`: write failing render tests: default render has no PrometheusRule and no dashboard ConfigMap; enabled
  PrometheusRule has the base alert names; each dashboard ConfigMap has the label `grafana_dashboard: "1"` and valid JSON in
  its data; the rules name only metrics in `charts/ingot/tests/fixtures/exported-metrics.txt`. Verify: they fail before 5.2.
- [ ] 5.2 `feat(chart)`: add `prometheusRule` and `grafanaDashboards` to `values.yaml` and `values.schema.json`, the rule template,
  the dashboard ConfigMaps, and the JSON under `charts/ingot/files/dashboards/`. The exported-metrics list follows the names
  that `add-cluster-metrics` registers. Verify: the tests of 5.1 pass.
- [ ] 5.3 `ci(chart)`: add a `promtool check rules` step to the chart workflow and a check that the metric names in the rules
  appear in `charts/ingot/tests/fixtures/exported-metrics.txt`. Verify: the step fails on a deliberately unknown name in a
  scratch copy and passes on the change.
- [ ] 5.4 `docs(deploy)`: document the two flags in `docs/gitbook/how-to/monitor-status-and-failures.md` and link the dashboard
  JSON there for Compose users.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `feat(chart): add opt-in telemetry, jvm options and shared settings`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (the cluster
  that copied staging-only wiring, the heap that did not follow the limit, the dashboard that could not save) without template
  names. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.

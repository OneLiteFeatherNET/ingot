# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `metrics`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the
request timer uses an injected monotonic ticker, and any other time source is an injected `java.time.Clock`), no fixed ports, no shared
mutable state (a fresh `PrometheusRegistry` and a fresh fixture per test), a temporary directory per test, bounded waits through latches,
and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted with `--tests`,
and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-cluster-metrics`: this change and nothing else. Verify: `openspec validate add-cluster-metrics --strict`
  passes and the dash grep over the change folder prints nothing.

## 2. Verify the duplicate load (open verification 5)

- [ ] 2.1 `test(metrics)`: write a test in `reposilite-backend` that puts two jars with the same plugin id into a temporary plugins
  directory and calls `PluginLoader.loadPluginsByServiceFiles()`. Use two small test-fixture plugins built from the test sources, not
  the Prometheus jar. Assert the observed behaviour: start-up fails with `Plugin <id> is already registered`. Verify: the test passes
  against main, and the result is written into the proposal's "Conditional on verification" section. If the observed behaviour is not a
  start-up failure, stop and revise design D3 before 5.2.

## 3. Request histograms and counter

- [ ] 3.1 `test(metrics)`: write failing tests in `reposilite-plugins/prometheus-plugin` against a fresh `PrometheusRegistry`: the
  histogram and counter names; the `route` label is the route template and not the raw path; an unmatched request gets
  `route="unmatched"`; `/metrics` is not observed; the observed duration equals the advance of an injected ticker; `jetty_response_time_seconds`
  is still registered with its `code` label. Verify: they fail before 3.2.
- [ ] 3.2 `feat(metrics)`: add a request timer that takes a `LongSupplier` (nanoseconds) in place of `System.currentTimeMillis()`
  at `PrometheusPlugin.kt:71,80`, register the histogram and counter next to the existing summary, and keep the existing summary.
  Verify: the tests of 3.1 pass and the existing plugin tests pass.

## 4. Operational series

- [ ] 4.1 `test(metrics)`: write failing tests with fake facades for each series in the spec `http-server-metrics`: rejected and accepted
  deploys, each upstream outcome, mirror cache entries, cluster connected and dropped events (absent without a bus), the three job
  timestamps, and the search outbox pending count and oldest age. Include the bounded-label test: ten repositories give the same number
  of `ingot_deploy_results_total` series. Verify: they fail before 4.2.
- [ ] 4.2 `feat(metrics)`: register `GaugeWithCallback` and `CounterWithCallback` series that read the owning facades at scrape time. Where
  a value has no read-only accessor on main, add the smallest one in its owner, in the same commit, with a test. Verify: the tests of 4.1
  pass and no series is registered without its facade.
- [ ] 4.3 `docs(metrics)`: list every new series with its labels and type in `docs/gitbook/how-to/monitor-status-and-failures.md`
  (section "Scrape Prometheus metrics"). Verify: every name on the page appears in the tests of 3.1 and 4.1.

## 5. Opt-in loading and precedence

- [ ] 5.1 `test(metrics)`: write failing tests for `PluginLoader` with a temporary bundled directory (system property
  `ingot.bundledPlugins`): switch unset loads nothing from the bundled directory; `INGOT_METRICS_ENABLED=true` loads it; the legacy
  `REPOSILITE_METRICS_ENABLED=true` loads it when the new name is unset; a jar with the same plugin id in the plugins directory wins and
  the bundled copy is not registered; the setting names resolve `INGOT_PROMETHEUS_*` before `REPOSILITE_PROMETHEUS_*`. The environment is
  read through an injected map so no test changes process state. Verify: they fail before 5.2.
- [ ] 5.2 `fix(metrics)`: in `PluginLoader`, load the bundled directory after the plugins directory only when the switch is on, read the
  `@Plugin` annotation of each bundled service instance and skip it when `extensions.getPlugins()` already holds that name, and read the
  `INGOT_PROMETHEUS_*` names first in the plugin. Verify: the tests of 5.1 pass and the test of 2.1 still passes.
- [ ] 5.3 `docs(metrics)`: document `INGOT_METRICS_ENABLED`, its legacy spelling, the bundled directory, and the `INGOT_PROMETHEUS_PATH`,
  `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD` names in `docs/gitbook/reference/environment-variables.md` (section "Prometheus
  plugin"), with the precedence order. Verify: each variable named on the page appears in the tests of 5.1.

## 6. Image

- [ ] 6.1 `test(metrics)`: write a failing container test in `reposilite-backend/src/containerTest` that starts the image with no metrics
  variable and checks that `/metrics` is not served and the start-up log has no line from the prometheus plugin; and with the switch and
  credentials that `/metrics` answers with `401` without credentials. Verify: the first assertion fails before 6.2 because the jar is not in the
  image yet, and the test runs through `HEAVY_SLOTS=1 heavy` one at a time.
- [ ] 6.2 `build(metrics)`: in `Dockerfile`, build `prometheus-plugin.jar` in the build stage (the existing Gradle module
  `reposilite-plugins:prometheus-plugin`) and copy it to `/app/bundled-plugins/` in the run stage, owned by `reposilite`. Keep the build stage on
  `--platform=$BUILDPLATFORM` and do not add a `USER`. Verify: the image builds for `linux/amd64` and `linux/arm64`, the test of 6.1 passes,
  and the "Container images" CI job is green, including the upgrade rehearsal against the upstream image.
- [ ] 6.3 `docs(metrics)`: update the image section of `docs/gitbook/how-to/monitor-status-and-failures.md` so the "build the plugin" step
  applies to non-image installs only. Verify: the page says where the jar is in the image.

## 7. Pull request

- [ ] 7.1 `feat(metrics)`: open the code pull request with the title `feat(metrics): add request histograms and health gauges behind a flag`,
  from a branch that holds groups 2 to 6, based on the remote main branch that already contains the merged proposal, with no file under
  `openspec/`. Read `.github/pull_request_template.md` and fill its sections, and describe the change from the operator's point of view.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, the body has no em or
  en dash and no session link.

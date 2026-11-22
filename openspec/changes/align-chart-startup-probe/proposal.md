# Proposal

## Why

The `chart-lifecycle` spec says that in `allInOne` the chart MUST NOT render a startup probe unless `probes.startup.enabled` is set, and
that the split startup probe has a budget of 150 seconds. The chart does neither. Under a small CPU limit the JVM opens its first start
slower than the liveness budget, so the chart now renders the startup probe in every mode and by default, with a budget of 300 seconds
(`probes.startup`: `periodSeconds: 5`, `timeoutSeconds: 2`, `failureThreshold: 60`), and the readiness budget of 3 seconds and 2
failures applies to `allInOne` as well. The chart tests pin this (`charts/ingot/tests/test_chart.py`,
`test_startup_probe_is_rendered_by_default_in_all_in_one` and its split counterpart). The spec still describes the old behaviour, so a
reader of the spec gets the opposite of what the chart renders. The maintainer decided on 2026-11-23 that the spec follows the chart.

## What Changes

- The requirement "Probe budget of the split server" of `chart-lifecycle` is renamed "Probe budget of the server" and states the
  behaviour of the chart: a startup probe on the probe path of the server in both modes, on by default, off with
  `probes.startup.enabled: false`, a budget of 300 seconds, and a readiness probe of 3 seconds and 2 failures in both modes.
- The scenario "All-in-one keeps its probe budget" keeps its name, because the spec tooling requires it, and now expects the startup probe in `allInOne` besides the unchanged liveness budget.
- No chart, template, value or test changes. The chart already behaves this way.

### Out of scope

- Any change to the rendered manifests. This change only corrects the spec.
- The requirement "Readiness and liveness paths per mode" of the same spec. It also names defaults older than the chart (the
  `allInOne` path default), and a separate change should align it.

## Capabilities

### Modified Capabilities

- `chart-lifecycle`: the probe budget requirement.

## Impact

- **Type and scope.** The change ships under `docs(chart)`, and there is no code pull request. The proposal pull request title is:

  `docs(openspec): propose align-chart-startup-probe`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin changes, and the
  rendered chart is untouched.
- **Behaviour that changes by default.** None.
- **Code.** None.
- **Dependencies.** None on another open change. No active change has a delta for `chart-lifecycle`; the archived
  `add-chart-lifecycle-and-warnings` created the spec.

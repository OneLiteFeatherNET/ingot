# Tasks

## 1. Spec

- [ ] 1.1 `docs(openspec)`: add this change, with the modified `chart-lifecycle` requirement. Verify: `openspec validate align-chart-startup-probe --strict`
  passes, and a grep for em and en dashes over the change prints nothing.
- [ ] 1.2 Check the spec against the chart tests: `charts/ingot/tests/test_chart.py` already pins the startup probe in both modes and the
  readiness budget. Verify: the startup and readiness tests of that file are green on `main`.

## 2. Pull request

- [ ] 2.1 Open the proposal pull request `docs(openspec): propose align-chart-startup-probe`. There is no code pull request: the chart
  already renders what the spec will say. If 1.2 finds a gap, the fix is a separate `fix(chart)` change.

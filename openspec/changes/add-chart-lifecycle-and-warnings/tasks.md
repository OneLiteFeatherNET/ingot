# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `chart`. Tests are written first
and seen to fail before the production code changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no fixed ports, no shared mutable state, a temporary directory per test (the render helpers already write their values
files there), and assertions with messages. The chart tests run with `python3 -m unittest discover -s charts/ingot/tests
-t charts/ingot/tests -v`. They need `helm` only, so `heavy` and Gradle are not used here.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-chart-lifecycle-and-warnings`: this change and nothing else. Verify:
  `openspec validate add-chart-lifecycle-and-warnings --strict` passes and the dash grep from the proposal prints nothing.

## 2. Readiness path and split probe budget

- [ ] 2.1 `test(chart)`: write failing render tests: the all-in-one defaults keep `/` with timeout 1 and threshold 1 and no
  startup probe; `mode: split` puts readiness on `/api/status/ready` with timeout 3 and threshold 2; split liveness stays on
  `/api/status/health`; split renders a startup probe with `periodSeconds: 5` and `failureThreshold: 30`; an explicit
  `probes.readinessPath` wins in both modes and leaves liveness alone. Replace the two existing assertions that pin the old
  split behaviour (`test_split_server_probes_use_the_api_health_endpoint` for readiness, and
  `test_startup_probe_is_absent_by_default` for split), and say in each message why it changed. Verify: the new and replaced
  tests fail before 2.2.
- [ ] 2.2 `feat(chart)`: add `probes.readinessPath` (empty) and `probes.split.readiness` (timeout 3, threshold 2) to
  `values.yaml` with comments, resolve the readiness path in a helper next to `ingot.serverProbePath`, and make the startup
  probe default to on in split and off in all-in-one (`probes.startup.enabled` unset means the mode default). Verify: the
  tests of 2.1 pass, the existing all-in-one probe tests pass unedited, and `test_equivalence.py` stays green.

## 3. Opt-in shutdown budget

- [ ] 3.1 `test(chart)`: write failing render tests: no shutdown value gives grace `60`, no `preStop` action and no
  `INGOT_SHUTDOWN_TIMEOUT`; `preStopSeconds: 15` gives `exec` `sleep 15`; `drainSeconds: 30` gives `INGOT_SHUTDOWN_TIMEOUT`
  `30`; both together give grace `55`; an `INGOT_SHUTDOWN_TIMEOUT` already in `env` is kept once with its own value; an
  operator `deployment.lifecycle` wins over `preStopSeconds`. Verify: they fail before 3.2.
- [ ] 3.2 `feat(chart)`: add the `deployment.shutdown` block (`preStopSeconds: 0`, `drainSeconds: 0`) to `values.yaml`, and
  render the `preStop` action, the variable and the derived grace period in `templates/deployment.yaml`. Do not merge this
  commit before `add-graceful-drain` is on main, because `INGOT_SHUTDOWN_TIMEOUT` must exist. Verify: the tests of 3.1 pass
  and the tests of 2.1 still pass.

## 4. Replica warnings

- [ ] 4.1 `test(chart)`: write failing notes tests with the existing `notes()` helper: each of the four warnings appears with
  two replicas or the autoscaler on; a single replica with defaults gets none of them; `INGOT_LOCAL_DATABASE` in `env` removes
  the database warning; the memory warning appears only with the autoscaler on; every combination exits 0 with
  `--dry-run=client`. Verify: they fail before 4.2.
- [ ] 4.2 `feat(chart)`: add the four warnings to `templates/NOTES.txt` beside the existing cluster bus warning, as plain
  warnings and never `fail()`. Verify: the tests of 4.1 pass and `test_notes_warn_about_autoscaling_without_the_cluster_bus`
  passes unedited.

## 5. Documentation

- [ ] 5.1 `docs(deploy)`: update `docs/gitbook/how-to/run-on-kubernetes.md` (the probe section, which says both probes use
  `GET /`, and the replica section, which says shutdown does not wait for requests), `docs/gitbook/how-to/run-split-with-helm.md`
  (the table row that puts both server probes on `/api/status/health`) and `docs/gitbook/explanation/multi-replica-operation.md`
  (the warnings and the shutdown budget). Document `probes.readinessPath`, `probes.split.readiness`,
  `deployment.shutdown.*` and the split image requirement. Verify: each changed page names only keys that exist in
  `charts/ingot/values.yaml`. Coordinate with the wave 1 documentation edits to the multi-replica page before rebasing.

## 6. Pull request

- [ ] 6.1 Open the pull request titled `feat(chart): add readiness path, shutdown budget and replica warnings`, from the code
  branch and against main, after the proposal PR is merged and the dependencies in the proposal are on main. Fill the sections
  of `.github/pull_request_template.md` and write the description from the operator's point of view, without session links.

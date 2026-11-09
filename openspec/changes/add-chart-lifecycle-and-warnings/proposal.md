# Proposal

## Why

The Ingot chart gives the server the probes and the shutdown budget of a single pod, and the split deployment inherits them
unchanged. Three things go wrong in production because of that.

A replica whose database is unreachable answers `UP` on `/api/status/health`, so the load balancer keeps sending it
requests and clients get errors until someone reads the logs. The readiness probe has a one second timeout and fails on the
first miss, so a slow but healthy replica flaps out of the rotation.

A rollout removes the pod from the load balancer and sends SIGTERM in the same moment, and the grace period is a fixed 60
seconds with no delay before the signal. In-flight requests are cut when the endpoint change has not yet reached the proxy.

Several replicas on a `ReadWriteOnce` claim, on a pod-local volume or on the default SQLite file, or an autoscaler that
reads memory for a heap capped at 64 MiB, do not work as the operator expects. `NOTES.txt` warns about only one of these
cases, the missing cluster bus, so the mistake shows after the rollout and not at install time.

This change makes the probes and the shutdown budget configurable without changing the all-in-one render, and it adds
warnings for the replica mistakes. A warning never stops an install.

## What Changes

- **Readiness path per mode.** `probes.readinessPath` (empty by default) selects the readiness endpoint. In `allInOne` the
  empty value keeps today's `/`. In `split` the empty value resolves to `/api/status/ready`, the endpoint that
  `add-readiness-endpoint` adds. Liveness stays on `/api/status/health` in both modes, so a database outage never restarts a
  pod.
- **Split probe budget.** In `split`, readiness uses a timeout of 3 seconds and a failure threshold of 2, from
  `probes.split.readiness`. The startup probe is on by default in `split` with a budget of 150 seconds
  (`periodSeconds` 5 times `failureThreshold` 30). `allInOne` keeps its current numbers and its startup probe stays off.
- **Opt-in shutdown budget.** `deployment.shutdown.preStopSeconds` (default `0`) renders a `preStop` `exec` sleep, unless
  `deployment.lifecycle` is set. `deployment.shutdown.drainSeconds` (default `0`) renders `INGOT_SHUTDOWN_TIMEOUT`, unless
  the operator sets that variable in `env`. When either value is above zero, `terminationGracePeriodSeconds` becomes
  `preStopSeconds + drainSeconds + 10`. With both values at `0` the grace period stays at 60 seconds.
- **Warnings in `NOTES.txt`.** When the server runs more than one replica, or the autoscaler is on, the notes warn about:
  a `ReadWriteOnce` claim without `existingClaim`; persistence disabled, which gives each pod its own data; no
  `INGOT_LOCAL_DATABASE` or `REPOSILITE_LOCAL_DATABASE` in `env`; and a memory metric in the autoscaler. The notes never
  call `fail()`.
- **Documentation.** The chart and split how-to pages and the multi-replica explanation describe the new keys, the probe
  paths and the warnings.

### Out of scope

- The readiness endpoint, its checks and the draining check. These belong to `add-readiness-endpoint`.
- The shutdown behaviour inside Ingot: the draining flag, `INGOT_SHUTDOWN_TIMEOUT` and `INGOT_SHUTDOWN_DELAY` in the
  server. These belong to `add-graceful-drain`.
- Typed HA values, `database.existingSecret` and the schema. These belong to `add-chart-ha-values`. The database warning
  reads `env` only, and that change must teach it about the Secret reference when it lands.
- Pre-upgrade migration Jobs, PrometheusRule, KEDA and the HPA metric defaults. The memory metric is warned about and is not
  removed from the defaults.

### Breaking

Not breaking for operators. No key is removed or renamed, and no environment variable is required.

Two rendered behaviours change for `split` releases, and the release notes say so:

- The split server's readiness probe fails while the database or storage is unavailable, where it answered `UP` before.
  The pod template changes, so each split server pod restarts once on upgrade.
- The split server gets a startup probe by default.

The all-in-one render is unchanged when no new value is set. `split` operators who pin `image.tag` below the release that
ships `/api/status/ready` get a readiness probe that never succeeds. The release notes must say that split readiness needs
the matching image.

## Capabilities

### New Capabilities

- `chart-lifecycle`: the readiness path and probe budget per mode, the opt-in shutdown budget, and the replica warnings of
  the chart.

## Impact

- **Type and scope.** The change ships under `feat(chart)`. Tests and docs are separate commits of their own types
  (`test(chart)`, `docs(deploy)`). The pull request title, squash-merged onto main, is:

  `feat(chart): add readiness path, shutdown budget and replica warnings`

- **Dependencies.**
  - `add-readiness-endpoint` (wave 1, PR #85) must be merged first, with the draining check the roadmap asks for. Without
    it the split readiness probe never sees a shutdown.
  - `add-graceful-drain` (F8) must be merged before the `drainSeconds` task lands on main, because it introduces
    `INGOT_SHUTDOWN_TIMEOUT`.
  - `add-deploy-modes` is merged, so `split` exists.
  - `add-chart-ha-values` (N6, wave C) edits the same `values.yaml` and `deployment.yaml`. Whichever lands second rebases.
- **Code.** `charts/ingot/values.yaml`, `charts/ingot/templates/deployment.yaml`, `charts/ingot/templates/NOTES.txt`,
  `charts/ingot/tests/test_chart.py`, and `docs/gitbook/`. No change in `reposilite-backend`.
- **Operators.** No configuration key, environment variable, volume or data changes. The all-in-one default render stays
  identical, and `test_equivalence.py` stays green.
- **Opt-in.** The shutdown budget and the warnings are opt-in or informational. The split readiness path is the only change
  that applies without a new value.

## Verification

- Render tests run with `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v` and `helm template`.
  They need no cluster and no Gradle, so `heavy` is not required.
- `helm lint charts/ingot` passes, and the chart-testing job in CI installs the all-in-one and split values on kind.
- `openspec validate add-chart-lifecycle-and-warnings --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-chart-lifecycle-and-warnings` prints nothing.

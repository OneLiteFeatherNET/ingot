# Proposal

## Why

Every rollout of an Ingot replica cuts whatever it is doing. A Maven client that is halfway through uploading an artifact, or a
proxied download that a replica fetched from upstream, gets a dropped connection when the pod receives its stop signal. The
client retries, and for an upload that retry may not be safe.

The cause is the stop sequence. `Reposilite.shutdown()` stops the scheduler, then calls `HttpServer.stop()` at
`reposilite-backend/src/main/kotlin/com/reposilite/web/HttpServer.kt:79`, which calls `javalin?.stop()` and nothing else. The
server is not told to finish requests in flight, no stop timeout is set on Jetty (12.1.12, `build.gradle.kts:52`), and nothing
marks the instance as leaving. A load balancer keeps sending it new requests until its probe fails, and the only probe Ingot
has answers `UP` for the whole shutdown (`/api/status/health`).

This change lets a replica leave the rotation first and finish what it has already accepted, bounded by an operator-chosen
time. Both new behaviours are opt-in. With the defaults the stop sequence stays what it is today.

## What Changes

- **D1 Draining flag.** `Reposilite.shutdown()` sets a draining flag before it stops anything. From that moment the readiness
  answer of `GET /api/status/ready` lists a new check `draining` as the first failing check, with status `503`. The liveness
  answer of `GET /api/status/health` does not change, so an orchestrator does not restart a replica that is finishing work.
- **D2 Graceful stop, opt-in.** New setting `INGOT_SHUTDOWN_TIMEOUT` (seconds, default `0`, fallback
  `REPOSILITE_SHUTDOWN_TIMEOUT`). When it is above `0`, the handler chain is wrapped in Jetty's `GracefulHandler`, and the
  server stop waits up to that many seconds for requests in flight. New requests that arrive during the wait get `503`. When
  the wait ends, the server is stopped as today. When it is `0`, the handler chain is not wrapped and the stop call is the same
  as today.
- **D3 Pre-stop delay, opt-in.** New setting `INGOT_SHUTDOWN_DELAY` (seconds, default `0`, fallback
  `REPOSILITE_SHUTDOWN_DELAY`). After the draining flag is set, the instance keeps serving for this many seconds before the
  graceful stop starts, so that load balancers see the `503` on readiness first.
- **Settings reading.** Both values are read once at start from the environment by a pure function with the environment passed
  in, the same pattern as `UpstreamStatusMode`. A value that is not a non-negative whole number stops the start with the name
  of the variable.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the two settings, `docs/gitbook/how-to/monitor-status-and-failures.md`
  and `docs/gitbook/reference/web-routes.md` for the `draining` check, and `docs/gitbook/explanation/multi-replica-operation.md` for the
  rollout sequence.

### Dependency on add-readiness-endpoint

The `draining` check is a check of the readiness endpoint, and that endpoint is proposed in `add-readiness-endpoint` (on
`origin/docs/openspec-wave1`, PR #85), which is not on main yet. This change adds the check as a MODIFIED requirement of the
capability `instance-readiness`. It can be applied only once `add-readiness-endpoint` is archived, and it does not edit that
change's files. The roadmap suggested amending `add-readiness-endpoint` before it merges. This change keeps that change
untouched instead, so both can go in on their own schedule.

### Out of scope

- Receiving the stop signal. The JVM must get `SIGTERM` directly, which is `fix-entrypoint-signal-handling` (roadmap F1).
  Until that change lands, the entrypoint's `runuser` can kill the JVM before the drain finishes.
- The Helm chart: the readiness probe, `preStop` hook, `terminationGracePeriodSeconds` and the NOTES warnings. Those are
  `add-chart-lifecycle-and-warnings` (roadmap F9), which reads the values this change defines.
- Shedding load under pressure (`INGOT_HTTP_MAXCONCURRENT`), which is `add-overload-protection` (roadmap N15).
- Draining the cluster bus, the IO executor's timeout (one minute, unchanged), and a `--shutdown-timeout` command line flag.
- A non-zero default. The roadmap leaves the default timeout to the maintainer (0 keeps strict parity, 10 seconds fits the
  Docker stop timeout). This proposal uses 0.

## Capabilities

### New Capabilities

- `graceful-shutdown`: the draining flag, the opt-in drain timeout and pre-stop delay, the two settings and the liveness rule
  during a drain.

### Modified Capabilities

- `instance-readiness` (from `add-readiness-endpoint`): a new `Draining check` requirement adds the `draining` check, evaluated first.

## Impact

- **Type and scope.** The change ships under `feat(web)`. Its tests and docs are separate commits of their own types
  (`test(web)`, `docs(web)`). The pull request title, squash-merged onto main, is:

  `feat(web): drain in-flight requests and report draining on readiness`

- **Breaking marker.** No breaking change. No configuration key, volume, manifest or plugin has to change. The new settings are
  optional, and with their defaults the stop sequence is the same as today. The title carries no `!` and no commit carries a
  `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** During shutdown, `/api/status/ready` answers `503` with `draining`. That endpoint is
  new in `add-readiness-endpoint`, so no existing client sees a change. Nothing else changes by default.
- **Opt-in.** `INGOT_SHUTDOWN_TIMEOUT` and `INGOT_SHUTDOWN_DELAY` change nothing until set to a value above `0`.
- **Dependencies (by change name).** `fix-entrypoint-signal-handling` (F1, the signal must reach the JVM for the drain to run
  under `docker stop`), `add-readiness-endpoint` (the readiness endpoint and the `instance-readiness` capability must be archived
  first). `add-chart-lifecycle-and-warnings` (F9) depends on this change.
- **Code.** `reposilite-backend` (web, status, `Reposilite.kt` shutdown), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `HEAVY_SLOTS=1 heavy` with Gradle `--no-daemon --max-workers=2`, never the full suite.
- Integration tests with `:reposilite-backend:integration --tests '<pattern>'`.
- `openspec validate add-graceful-drain --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-graceful-drain` prints nothing.

# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `web`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (the drain is released
through latches, and the delay is an injected function), no system clock (any time source is injected as a `java.time.Clock`),
no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through
`HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`. Kind or Testcontainers runs one at a time.

Ordering: group 3 may start only after `add-readiness-endpoint` is archived, because the MODIFIED delta needs its capability.
Group 4 may start only after `fix-entrypoint-signal-handling` is merged, so that the signal reaches the JVM in the container test.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-graceful-drain`: this change and nothing else. Verify:
  `openspec validate add-graceful-drain --strict` passes and the dash grep from the proposal prints nothing.

## 2. D3 Shutdown settings

- [ ] 2.1 `test(web)`: write failing unit tests for the pure settings reader, with the environment passed in: unset gives a timeout
  and a delay of `0`; `INGOT_SHUTDOWN_TIMEOUT=10` gives `10` seconds; `REPOSILITE_SHUTDOWN_TIMEOUT` is the fallback; the `INGOT_`
  name wins when both are set; `soon`, `-1` and `1.5` are refused with the variable name in the message and no other value in it.
  Verify: they fail on the missing class.
- [ ] 2.2 `feat(web)`: add the settings reader and the two variables. Verify: the tests of 2.1 pass.
- [ ] 2.3 `docs(web)`: document `INGOT_SHUTDOWN_TIMEOUT` and `INGOT_SHUTDOWN_DELAY` with their `REPOSILITE_` fallbacks in
  `docs/gitbook/reference/environment-variables.md`, in the same table style as the deploy target section. Verify: the two keys
  appear in the table and the default is stated as `0`.

## 3. D1, D4 Draining flag and readiness check

- [ ] 3.1 `test(web)`: write failing unit tests: the draining flag is false on a new status facade and true after it is set, and
  it stays true; the readiness evaluator lists `draining` first when the flag is set, and does not list it when the flag is not
  set; a draining instance keeps `isAlive()` true. Verify: they fail before 3.2.
- [ ] 3.2 `feat(web)`: add the flag to `StatusFacade` and the `draining` check as the first check of the readiness evaluator, and
  set the flag as the first statement of `Reposilite.shutdown()`. Verify: the tests of 3.1 pass and the readiness tests from
  `add-readiness-endpoint` pass unedited.
- [ ] 3.3 `docs(web)`: list the `draining` check in `docs/gitbook/reference/web-routes.md` and
  `docs/gitbook/how-to/monitor-status-and-failures.md`, and state that `/api/status/health` stays `200` during a drain. Verify: the
  check list on both pages matches the spec's order.

## 4. D2, D3 Graceful stop and pre-stop delay

- [ ] 4.1 `test(web)`: write failing integration tests on a running instance, with the delay function injected: with a timeout above
  `0`, a request blocked on a latch completes with its normal response after shutdown starts, and the latch is released by the test
  after the shutdown call has begun; a request sent after shutdown starts gets `503`; with a timeout of `0`, the handler chain has no
  graceful wrapper (checked through the server's handler type) and the stop call returns without a drain; the injected delay
  receives the configured duration once, before the graceful stop, and is not called when it is `0`. Verify: they fail before 4.2.
- [ ] 4.2 `feat(web)`: wrap the handler chain in `GracefulHandler` in `HttpServer.start` only when the timeout is above `0`, set the
  Jetty stop timeout from the same value, and in `Reposilite.shutdown()` call the injected delay and then `webServer.stop()`.
  Verify: the tests of 4.1 pass, and `StatusFacadeTest` passes unedited.
- [ ] 4.3 `test(web)`: write a failing integration test that a request blocked past the timeout does not hold the stop beyond it, and
  that the test completes without a sleep because the blocked handler is released in `finally`. Verify: it fails before 4.4.
- [ ] 4.4 `fix(web)`: bound the graceful stop by the configured timeout and log the number of requests that were cut off. Verify:
  the test of 4.3 passes.
- [ ] 4.5 `docs(web)`: describe the rollout sequence (draining, delay, drain, stop) in
  `docs/gitbook/explanation/multi-replica-operation.md`, next to the readiness paragraph. Verify: the page states that the defaults
  keep the stop sequence of earlier versions.

## 5. Pull request

- [ ] 5.1 `feat(web)`: open the code pull request with the title `feat(web): drain in-flight requests and report draining on readiness`,
  from a branch that holds groups 2 to 4, based on the remote main branch that already contains the merged proposal and the merged
  `add-readiness-endpoint` and `fix-entrypoint-signal-handling`, with no file under `openspec/`. Read `.github/PULL_REQUEST_TEMPLATE.md`
  and fill its sections. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, the body has no em or en dash and no session link, and the "Container images" CI job is green.

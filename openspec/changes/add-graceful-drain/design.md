# Design

## Context

On main, `Reposilite.shutdown()` (`reposilite-backend/src/main/kotlin/com/reposilite/Reposilite.kt:77`) sets `alive` to false,
stops the scheduler (line 81), calls `webServer.stop()` (line 82), and then stops the IO executor, emits the dispose event and
closes the database. `HttpServer.stop()` (`web/HttpServer.kt:79`) calls `javalin?.stop()`. Jetty is 12.1.12 under Javalin, and no
graceful handler and no stop timeout are configured. The JVM shutdown hook (`Reposilite.kt:54-55`, registered at line 67) calls
the same `shutdown()`, so a `SIGTERM` that reaches the JVM takes this path. The liveness answer (`StatusFacade.isAlive()`,
`status/StatusFacade.kt:185`, served at `/api/status/health`) reads the web server state only.

## Decisions

### D1. The flag is the first statement of `shutdown()`

The draining flag is set before the scheduler stops, so the readiness answer changes at the same moment the instance starts
leaving. It stays set through the optional delay and the graceful stop, which is the window a load balancer needs to notice the
`503`. The flag is an `AtomicBoolean` owned by the status plugin and set through a method on `StatusFacade`. A second call to
`shutdown()` already exits early through `alive`, so the flag is never cleared.

### D2. The graceful handler is installed only when the timeout is above zero

The handler chain is wrapped in `GracefulHandler` at start only when `INGOT_SHUTDOWN_TIMEOUT` is above `0`. With the default,
the handler chain and the stop call are the same as today. The alternative, always wrapping and using a zero timeout, would
turn the connection refused during a stop into a `503` by default. That is a client-visible change, and the drop-in rule rules
it out without a strong reason.

The timeout is set on the Jetty server (`setStopTimeout`) through Javalin's Jetty server hook in `HttpServer.start`. The exact
calls are verified by the first failing test of group 4, not assumed here.

### D3. Settings come from a pure reader, and the delay is injected

`INGOT_SHUTDOWN_TIMEOUT` and `INGOT_SHUTDOWN_DELAY` are read once through a function that takes the environment as a parameter,
following `shared/http/UpstreamStatusMode.kt`. The delay is awaited through an injected function, `(Duration) -> Unit`, and the
production value sleeps. Tests inject a recording function, so no test waits on the wall clock. The drain wait itself is a
bounded wait on a future, and the tests release it through a latch.

### D4. Liveness does not change during a drain

A draining replica is alive. If the liveness answer turned `503` during the drain, the kubelet would restart the pod in the
middle of the work the drain exists to finish. The readiness endpoint is the only place where draining is visible.

### D5. The readiness check is a MODIFIED delta on an unarchived capability

`instance-readiness` does not exist under `openspec/specs/` on main, so the `Checks` requirement can only be changed by a delta
that follows `add-readiness-endpoint` being archived. This change therefore must not run before that archive. The alternative,
amending `add-readiness-endpoint` on `docs/openspec-wave1` as the roadmap suggests, would edit a proposal that belongs to another
open pull request. The MODIFIED delta keeps that proposal intact, at the price of an ordering rule that the tasks state.

### D6. The default timeout stays at zero

A non-zero default would change how long every stop takes, for every operator, without a request from one. The roadmap leaves the
choice to the maintainer. The proposal ships `0`, and a one-line change in a later commit can set `10` if the maintainer decides so.

## Risks

- A drain longer than the orchestrator's grace period is cut off by `SIGKILL`. The chart change (`add-chart-lifecycle-and-warnings`)
  derives the grace period from the same values, and until then the operator sets it.
- Under `docker stop`, the drain runs only when the JVM receives `SIGTERM`. Until `fix-entrypoint-signal-handling` lands, the
  `runuser` in the entrypoint can end the process first.
- `GracefulHandler` answers new requests with `503` and no `Retry-After`. The overload change may add that header later. This
  change does not add it.

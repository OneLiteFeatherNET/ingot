# Design

## Context

The limit has to sit where the thread pool is consumed, so before Javalin routes a request. The refusal has to be cheap,
and the health probe has to bypass it. Jetty 12 completes requests asynchronously, so a request can outlive the handler
call that started it: an artifact stream and an upload both run after `handle` has returned.

## Decisions

### D1. The limit is a Jetty handler wrapper on the server

The wrapper is inserted into the Jetty server that `JavalinConfiguration.configure` creates (`JavalinConfiguration.kt:55-56`),
so it wraps the whole Javalin chain. A Javalin before handler would run after Jetty has already dispatched the request to a
thread and would still be behind the routing and the body parsing. The wrapper refuses at the Jetty level, with no
routing, no body read and no database work.

### D2. Own permit counter, unless the spike proves QoSHandler fits

Jetty ships a `QoSHandler`. This change does not use it by default, for two reasons that the spike (task 1.2) must check
against jetty-server 12.1.12, because neither was verified while writing this proposal:

- its over-limit path suspends a request in a queue before it rejects it, where the operator asked for immediate refusal;
- its rejection answer is Jetty's own error response, and the `Retry-After` header and the `ErrorResponse` body would need a
  hook that we could not confirm exists.

If the spike shows that `QoSHandler` can refuse at once and answer with a header and a body, the implementation may use it
instead, and the tests of section 3 stay the same. Otherwise the wrapper uses a non-blocking permit count
(`Semaphore.tryAcquire`, never a blocking acquire).

### D3. A permit is released on completion, not on return

The wrapper takes its permit when the request arrives and releases it from the Jetty `Callback` that completes the request.
A stream that is still sending a 2 GB artifact holds its permit until the last byte is written. Releasing on return would
let the count say "free" while the server is still busy, which defeats the limit.

Each request releases its permit once. A second completion of the same callback is ignored.

### D4. Exempt paths match exactly, after the base path

Only `/api/status/health` (and `/api/status/ready` once that change is merged) bypass the counter. The match is on the exact
path after `basePath` is removed, so `/api/status/healthy` and `/api/status/health/extra` are counted. Dashboard assets and
artifacts are counted. The exemption is a list in code, not a setting: a setting would let an operator make the probe
shed.

### D5. Mirror single-flight already exists and is not changed

`MirrorService.findRemoteFile` coalesces concurrent fetches through `singleFlight` when every host stores
(`MirrorService.kt:98-112`). This change adds a test that pins the behaviour. The non-storing case stays uncoalesced, because
sharing a stream needs an unbounded buffer. The roadmap item asked for "a single-flight per path"; that part is already
there, so the item shrinks to a test.

### D6. Counting covers every request

Uploads and downloads hold a permit for their full duration, so a limit below the number of concurrent uploads refuses
uploads. The documentation says so and tells operators to size the limit above their expected concurrent streams. Per-class
limits are out of scope.

## Open verifications

1. Does jetty-server 12.1.12 contain `QoSHandler`, and how does it reject: suspend, queue or answer at once? Decides D2.
2. Does Javalin 7.2.2 keep a handler that was inserted into the server before start, or does it replace the server handler?
   If it replaces it, the wrapper goes in through Javalin's own handler hook. Task 1.2 proves which.

The fix is conditional on these results. If the second check fails, the wrapper moves into the Javalin configuration and
the proposal is updated before any code is merged.

## Risks

- A limit set too low refuses legitimate traffic. Mitigation: unset by default, and the refusal is visible in the status code.
- A permit leak (a callback that never completes) would shrink the server until it refuses everything. Mitigation: the
  leak test in section 3 completes a request in two ways, normal and failed, and checks the free count afterwards.

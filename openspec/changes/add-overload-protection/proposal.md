# Proposal

## Why

A repository manager is often the busiest thing in a build pipeline. A CI system that starts a few hundred Maven
resolves at once, or a fleet of builds that all miss the same mirrored artifact, sends a burst that the server has to
absorb. Ingot has no limit on how many requests it works on at the same time. When the burst arrives, every request waits
for the same few resources: the web thread pool, the single database connection (`databaseThreadPool` defaults to 1), and
the upstream connections of the mirrors. Responses slow down for everyone, including the health probe. In a cluster the
probe then fails, the orchestrator restarts the pod, the remaining pods take the load, and they fail the same way. That
cascade is the one an operator is trying to prevent with replicas and autoscaling.

Two parts of the answer are missing. The server never says "not now" to a client it cannot serve in time, so the client
retries later without knowing it should wait. And the health probe shares the queue with the artifact traffic.

Facts checked against origin/main (`c0f01846`):

- `reposilite-backend/src/main/kotlin/com/reposilite/web/HttpServer.kt` creates the Jetty server on the web thread pool and
  registers no request limit. Its only limit is the thread count from `webThreadPool`
  (`configuration/local/LocalConfiguration.kt:110`, default 16).
- `LocalConfiguration.kt:118` sets `databaseThreadPool` to 1 by default.
- No `QoSHandler` and no request limit exist anywhere in `reposilite-backend`.
- `maven/MirrorService.kt:110-112` already coalesces concurrent fetches of the same artifact through `singleFlight`
  (`MirrorService.kt:120-146`, the map `inFlightFetches` at line 67), but only when every host stores its copy. A host
  that streams without storing is fetched once per caller, because waiters cannot share a stream (comment at lines 98-99).
- `/api/status/health` (`status/infrastructure/StatusEndpoints.kt:113-120`) is the only health route. The readiness route
  `/api/status/ready` exists only in the unmerged proposal `add-readiness-endpoint`.
- Jetty 12.1.12 and Javalin 7.2.2 are the versions in `reposilite-backend/build.gradle.kts`.

## What Changes

- **O1 Opt-in request limit.** New setting `INGOT_HTTP_MAXCONCURRENT` (system property `ingot.http.maxConcurrent`, fallback
  `REPOSILITE_HTTP_MAXCONCURRENT`). Unset means no limit, and then nothing changes. A positive whole number limits the
  requests in progress at the Jetty level. A request above the limit is refused at once with `503 Service Unavailable`,
  `Retry-After: 1` and the `ErrorResponse` body (`status`, `message`), with `application/problem+json` when the client
  prefers it, as `add-problem-details` defines. The refusal does not read the request body, does not route and does not
  touch the database.
- **O2 Liveness and readiness are never shed.** `/api/status/health` (and `/api/status/ready` once `add-readiness-endpoint`
  is merged) is answered whatever the load. A probe does not count against the limit.
- **O3 Mirror fetches.** No new single-flight code. The coalescing that exists for stored mirror copies is pinned by a test
  so that the limiter cannot break it. Mirror hosts that stream without storing keep one upstream fetch per caller. Sharing
  a stream would mean buffering it, and that buffer would be unbounded.
- **O4 Invalid values.** A value that is not a positive whole number (`0`, `-1`, `many`) stops the start with a message
  that names the variable. Silent unlimited mode is not an option.
- **Documentation.** `docs/gitbook/reference/environment-variables.md` lists the setting, and
  `docs/gitbook/reference/http-api.md` describes the `503` answer and its `Retry-After` header.

### Shipping

One code pull request under the type `feat` and the scope `web`. The proposal pull request is
`docs(openspec): propose add-overload-protection` and contains only the files under `openspec/changes/add-overload-protection/`.

Pull request title (squash-merged, so it is the commit on main):

`feat(web): shed requests above an opt-in concurrency limit`

### Out of scope

- A per-route or per-repository limit, a priority queue and a queue with waiting. A refused request is refused at once.
- Rate limiting by client address. The login limiter already exists and keeps its behaviour.
- A metric for refused requests. It belongs to `add-cluster-metrics` (N8), which owns the metric names.
- Configuring `Retry-After`. The value is fixed at one second in this change.
- Changing the Helm chart. Chart values for the limit come with the chart observability work (N10 and N11).
- Caching or sharing mirror responses between requests that do not overlap in time.
- The non-storing mirror path (see O3) and the single `HEAD` request of `findRemoteDetails`.

## Capabilities

### New Capabilities

- `http-overload-protection`: the opt-in request limit, the refusal answer, the exempt health paths and the setting rules.
- `mirror-fetch-coalescing`: concurrent fetches of one artifact through stored mirror copies share one upstream fetch.

## Impact

- **Type and scope.** `feat(web)`. The branches use `test(web)`, `feat(web)`, `test(maven)` and `docs(web)` commits. Each
  commit has one type. The pull request title is the one above.
- **Breaking marker.** No breaking change. The setting is opt-in and its default changes nothing. No configuration file,
  volume, manifest or plugin has to change, so a Reposilite installation pointed at Ingot behaves as before. The title
  carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Dependencies.** `add-graceful-drain` (F8, Wave B) must merge first. It changes `HttpServer.kt` and the shutdown order,
  and the limiter is installed next to the same server setup. `add-readiness-endpoint` (wave 1, still open on
  `docs/openspec-wave1`) supplies the path `/api/status/ready`. Until it merges, only `/api/status/health` is exempt, and
  the exemption for the readiness path is added in the same commit that merges it.
- **Code.** `reposilite-backend` (`web`, `configuration/local` settings reader, `maven` mirror test) and `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- The open verifications in `design.md` are settled by task 1.2 before any production code is written.
- `openspec validate add-overload-protection --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-overload-protection` prints nothing.

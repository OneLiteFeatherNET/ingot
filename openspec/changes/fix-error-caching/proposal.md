# Proposal

## Why

An error answer from Ingot has a body that depends on the request's `Accept` header. By default it is `application/json`.
A client that names `application/problem+json` gets the same body under that content type, as shipped with
`add-problem-details`. The response does not say so. A shared cache in front of Ingot, such as a reverse proxy or a CDN,
can store the JSON answer and hand it to a client that asked for problem details, because the answer carries no
`Vary: Accept` (RFC 9110, section 12.5.5).

The second problem is on repository paths. A missing file answers `404` with no `Cache-Control`. RFC 9111, section 3,
lists `404` as cacheable by default, so a cache may keep the miss. A release that a build fetched before its upload
finished can keep answering "not found" behind such a cache until the stored copy expires.

Facts checked against origin/main (`c0f01846`) while writing this proposal:

- `reposilite-backend/src/main/kotlin/com/reposilite/shared/extensions/JavalinExtensions.kt`: `Context.error(error: ErrorResponse)`
  at line 176 and `Context.error(status, error)` at line 201 set `Content-Type` from `prefersProblemJson` and never touch
  `Vary` or `Cache-Control`.
- `Vary` is set in one place only, `frontend/infrastructure/FrontendHandler.kt:121`, through
  `DashboardRouteDecision.varyHeader` (`frontend/DashboardRouteDecision.kt:63`), and only on paths that a repository shares
  with the dashboard.
- `maven/infrastructure/MavenEndpoints.kt:115-122`: for a path outside `/api`, the `findFile` error branch answers
  `ctx.status(it.status).html(...)` with no `Cache-Control`.
- Repository answers that are `ErrorResponse`s, such as the `401` with `WWW-Authenticate` from `auth/infrastructure/PostAuthHandler.kt`,
  reach the client through `ReposiliteRouting.kt` and `Context.error` as well.
- `docs/gitbook/reference/web-routes.md:327`: `bypassExternalCache` already adds `no-cache` to `/api/*` responses. Its
  behaviour does not change here.

## What Changes

- **Vary on negotiated errors (`fix(http)`).** Every `ErrorResponse` answer carries `Accept` in `Vary`, merged into any value
  the response already has. This holds whether or not the client asked for problem details, because the content type depends
  on `Accept` in both cases. Bodies and content types do not change.
- **No-cache on error answers outside `/api` (`fix(http)`).** An error answer outside `/api` gets `Cache-Control: no-cache`
  when the response has no `Cache-Control` yet. That covers the repository `404` page, repository refusals such as `401` and
  `403`, and dashboard routes. Success answers keep their headers. Answers under `/api` keep what they have today.

### Shipping

The proposal is one pull request, `docs(openspec): propose fix-error-caching`, with only the files under
`openspec/changes/fix-error-caching/`, tasks unchecked, branched from the remote main branch. It is merged first.

The code is one pull request, titled `fix(http): add vary accept and no-cache to error responses`. It contains the
implementation only, no file under `openspec/`, and is branched from the remote main branch that contains the merged
proposal. Its commits are `test(http)` followed by `fix(http)` for each of the two groups. The change ships one type
(`fix`) and one scope (`http`).

### Out of scope

- Error answers that Javalin writes itself, such as its default `404` for a path no route matches. They do not pass
  through `Context.error`.
- `Cache-Control` on successful repository content, including `private` for access-controlled content (roadmap Q3,
  `fix-http-cache-semantics`).
- Status codes, bodies and the negotiation rules of `add-problem-details`.
- The values that `bypassExternalCache` sets, and the `Vary` values of dashboard routes. The only shared code is the
  `Vary` merge, which keeps the dashboard's result unchanged.
- `ETag` or validators on error answers.

## Capabilities

### New Capabilities

- `http-error-caching`: the `Vary` and `Cache-Control` headers of error answers, and the guarantee that success answers are
  unchanged.

## Impact

- **Operators:** nothing to change. There is no new setting, environment variable, volume, port or manifest entry, so
  `docs/gitbook` needs no update for this change. This change is not BREAKING. An operator who puts a cache in front of
  Ingot sees fewer stored error answers and error answers split by `Accept`, which is what the cache should have done.
- **Existing clients:** an error answer gains `Vary: Accept` and, outside `/api`, `Cache-Control: no-cache`. The status
  code, body and `Content-Type` do not change. Success answers are byte-identical in their headers.
- **Plugins:** `Context.error` keeps its signature. No plugin needs a recompile or a source change.
- **Code:** `shared/extensions/JavalinExtensions.kt`, `maven/infrastructure/MavenEndpoints.kt`, the shared `Vary` merge
  (moved out of `frontend/DashboardRouteDecision.kt`), and their tests.

## Verification

- Each group starts with failing tests that are run and seen to fail before the code exists.
- `heavy ./gradlew :reposilite-backend:test --tests '<pattern>' --max-workers=2` for the unit tests of the merge and the
  Cache-Control decision, including `DashboardRouteDecisionTest`.
- `heavy ./gradlew :reposilite-backend:integration --tests '<pattern>' --max-workers=2` for the in-process server tests,
  including the existing `Cache-Control` assertions for `/api` answers.
- `openspec validate fix-error-caching --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-error-caching`
  prints nothing.

# Proposal

## Why

An API client that talks to Ingot gets three different kinds of error and has to guess which one it is holding. A refused
login, a missing repository and a validation failure come back as `{status, message}` JSON. A request that Javalin rejects
itself comes back as a second JSON shape (`{title, type, status, details}`). A path under `/api` that matches no route comes
back as the dashboard's HTML 404 page, and an unhandled failure comes back as a 500 with no body at all. A script that wants
to log "why did this fail" needs a branch for each, and none of them follows RFC 9457 (Problem Details), which tools and
gateways already understand.

Two more things go wrong for the people who run mirrors and download large files. When a proxy repository cannot reach its
upstream, the client is told "400 Bad Request" or "406 Not Acceptable" with the Java class name of the exception in the
text, although the request was fine and the upstream is the problem. And a 2 GB artifact cannot be resumed after a dropped
connection, because the server ignores `Range`, sends no `ETag` and ignores `If-None-Match`, so every build downloads
every file again.

This change fixes the three with additive, negotiated behaviour. Existing clients, the dashboard and plugins see the same
fields and the same status codes as before, unless they ask for more.

Facts checked against origin/main while writing this proposal (`d3de29c8`):

- `reposilite-backend/src/main/kotlin/com/reposilite/shared/ErrorResponse.kt` is `data class ErrorResponse(status, message,
  problems?)`, and `Context.error(...)` in `shared/extensions/JavalinExtensions.kt` writes it with `status(...).json(error)`.
  It is the public plugin API (`docs/gitbook/reference/plugin-api.md`, the "Set `response` to a `Result<R, ErrorResponse>`"
  section).
- `web/infrastructure/ReposiliteRouting.kt` catches a throwable in a route and in an exception route, records it through
  `failureFacade` and calls `ctx.status(INTERNAL_SERVER_ERROR)` with no body.
- `web/HttpServer.kt` registers exception handlers only for `EofException` and `UnsupportedLocationException`. Javalin's own
  handling answers every other `HttpResponseException`.
- `frontend/application/FrontendPlugin.kt` registers `NotFoundHandler` for `NotFoundResponse` and for the 404 error status,
  and `frontend/infrastructure/NotFoundHandler.kt` answers with the dashboard's HTML page whatever the path is.
- `shared/http/HttpRemoteClient.kt` answers a thrown exception with `400` and the text "An error of type <class> happened:
  <message>", and a non-2xx upstream (404 included) or an HTML content type with `406`. `maven/MirrorService.kt` already
  answers an upstream 401 or 403 with `502` ("refused the configured credentials"), and treats only status `404` as a miss
  in `searchInRemoteRepositories`. A real upstream 404 therefore reaches it as `406`, not `404`; see design D6.
- `maven/ResolutionProvider.kt` (`tryLocalFirst`) already answers an upstream error with its own status when that status is
  `429` or at least `500`, and falls back to the local result for anything below `500`.
- `shared/extensions/JavalinExtensions.kt` (`attachmentHeaders`) sets `Content-Length`, `Last-Modified`, `Cache-Control` and
  `Content-Type`; there is no `ETag`, no `Accept-Ranges`, and `maven/infrastructure/MavenEndpoints.kt` (`findFile`) never reads
  `Range`, `If-None-Match`, `If-Modified-Since` or `If-Range`.
- `S3StorageProvider.getFile` issues a plain `GetObjectRequest` with no range. `StorageProvider.getFile` returns a whole
  `InputStream`.

## What Changes

- **A1, problem details (`feat(api)`).** `ErrorResponse` gains the optional members `type` (URI, default `about:blank`),
  `title` (the status's reason phrase) and `instance` (the request path), plus `detail` that mirrors `message`. `status`,
  `message` and `problems` stay as they are. When the request's `Accept` header prefers `application/problem+json`, the answer
  carries that content type; otherwise it stays `application/json` as today. Javalin `HttpResponseException`s, unmatched
  `/api/*` paths, an over-large request body (413) and the unhandled-failure 500 all answer through the same writer, so every
  error under `/api` has one shape. The dashboard's HTML 404 page is unchanged outside `/api`.
- **A2, upstream failures (`feat(api)`).** A proxy fetch that times out answers `504`, one that cannot connect or gets an
  upstream non-2xx other than `404` and `410` answers `502`, with a generic message and no Java class name. An upstream `404`
  or `410` is a miss and answers `404`, so Maven and Gradle fall through to the next repository as before.
- **A3, ranges and conditional downloads (`feat(api)`).** Artifact `GET` and `HEAD` answer `ETag` and `Accept-Ranges: bytes`.
  A single `Range` answers `206` with `Content-Range`, an unsatisfiable one `416` with `Content-Range: bytes */<size>`, and a
  multi-range request is served in full with `200`. `If-Range` is honoured. `If-None-Match` and `If-Modified-Since` answer
  `304`. It works for filesystem and S3 storage. Snapshot and `maven-metadata.xml` caching rules do not change.

### Shipping

One code pull request titled `feat(api): add problem details, range requests and conditional artifact downloads`, assembled
from three work packages (A1, A2, A3) that are developed on their own branches and combined before the pull request is opened.
The commits are `test(api)`, `feat(api)` and `docs(api)`; the change carries one type (`feat`) and one scope (`api`). The proposal
pull request is `docs(openspec): propose add-problem-details` and contains only the files under
`openspec/changes/add-problem-details/`, with the tasks unchecked.

### Out of scope

- RFC 3339 dates in JSON. Changing the date format breaks every client that parses the current one.
- Removing the `GET` side effect of `/api/maven/storage` (`maven/infrastructure/MavenStorageEndpoints.kt`). That is a behaviour
  change for existing callers and is deferred.
- `201 Created` with a `Location` header on creation endpoints. Existing endpoints answer `200`; changing the code is breaking.
- Pagination `Link` headers.
- `RateLimit` response headers.
- The security items of the change `harden-http-security` (trusted proxies, security headers, `WWW-Authenticate` and `Allow`,
  removal of internal messages, body limits). That change owns the 413 limit itself; this change only makes a 413 answer in the
  common error shape once it exists. It also owns replacing raw exception messages in error bodies; this change must not
  reintroduce them, and A2 writes generic upstream messages from the start.
- Any change to the status code of an existing error other than the upstream failures of A2.
- Multi-range responses (`multipart/byteranges`).

## Capabilities

### New Capabilities

- `api-problem-details`: the common error shape, its content negotiation and the paths that must use it.
- `upstream-status-codes`: the status codes a proxy repository answers when its upstream fails, and the 404 passthrough.
- `artifact-conditional-ranges`: `ETag`, `Accept-Ranges`, `Range`, `If-Range` and the conditional headers on artifact downloads.

## Impact

- **Operators:** nothing to change. There is no configuration key, environment variable, volume, container path, port or
  manifest change. This change is not BREAKING. No `BREAKING CHANGE:` footer and no `!` are used.
- **Existing clients:** an error answer keeps `status`, `message` and `problems` with the same values and `Content-Type:
  application/json` unless the client sends `Accept: application/problem+json`. The new members are extra JSON fields, which
  the dashboard, the prometheus plugin and scripts that read `status` and `message` ignore. `Range`, `If-None-Match`,
  `If-Modified-Since` and `If-Range` change the answer only for requests that send them. The headers `ETag` and
  `Accept-Ranges` are added to artifact answers.
- **Behaviour that does change:** an unmatched `/api/*` path answers a JSON 404 instead of the HTML page; an unhandled failure
  answers a 500 with a body; a proxy whose upstream is unreachable answers `502` or `504` instead of `400` or `406` when no local
  copy exists (design D7 explains how the resolution layer reacts to this).
- **Plugins:** `ErrorResponse` stays binary compatible: the constructors and properties that exist today are kept, and new
  members have defaults. The prometheus plugin must still compile unchanged.
- **Code:** `shared/ErrorResponse.kt`, `shared/extensions/JavalinExtensions.kt`, `web/HttpServer.kt`,
  `web/infrastructure/ReposiliteRouting.kt`, `frontend/infrastructure/NotFoundHandler.kt`, `shared/http/HttpRemoteClient.kt`,
  `maven/MirrorService.kt`, `maven/infrastructure/MavenEndpoints.kt`, `storage/` providers (ranged read), and their tests.
  Docs: `docs/gitbook/reference/plugin-api.md`, a new `docs/gitbook/reference/api-errors.md`, `http-api.md`.

## Verification

- Each work package starts with a failing test that is run and seen to fail before the code exists.
- `heavy ./gradlew :reposilite-backend:test --max-workers=2` for the unit tests of the touched classes, and
  `heavy ./gradlew :reposilite-backend:integration --tests '<pattern>' --max-workers=2` for the range tests against the
  filesystem and the S3 test container. `:reposilite-plugins:prometheus-plugin:compileKotlin` proves plugin compatibility.
- `openspec validate add-problem-details --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-problem-details`
  prints nothing.

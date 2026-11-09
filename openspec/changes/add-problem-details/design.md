# Design

This design covers three work packages, A1 to A3, of one code pull request. It is written against origin/main at `d3de29c8`.
The decisions D1 to D10 are the ones this change takes. The open questions at the end are for the maintainer.

## Context

- Every error under `/api` is written by `Context.error(status, body)` as `status(...).json(body)`, with `ErrorResponse` as the
  body. Routes return `Result<R, ErrorResponse>` and `Context.response(...)` consumes it.
- `ErrorResponse(status, message, problems?)` is a public type of the plugin API. The prometheus plugin and third party plugins
  construct and read it.
- The frontend reads only `message` and `status` of an error body, and the `Retry-After` header.
- Artifacts are served by `MavenEndpoints.findFile`, which looks up `DocumentInfo` (name, type, length, last modified), opens the
  data with `mavenFacade.findData` and sets headers through `attachmentHeaders`.
- Storage is `StorageProvider` with a filesystem and an S3 implementation. `getFile` returns a whole `InputStream`.
- The resolution layer (`ResolutionProvider.tryLocalFirst`) asks the local storage first and then the mirror hosts, and surfaces
  an upstream error only when its status is `429` or at least `500`.

## Goals

- One error shape under `/api`, in RFC 9457 form for clients that ask for it, and the legacy form for everyone else.
- Honest status codes when a proxy's upstream fails, available as an opt-in so that artifact resolution fall-through is never
  broken for an existing client.
- Resumable and cacheable artifact downloads on both storage back ends.
- No change an existing Reposilite client, plugin or deployment has to react to.

## Non-goals

- The items listed as out of scope in the proposal.

## Decisions

### D1. Negotiate by `Accept`, keep the existing fields

`application/problem+json` is returned only when the request's `Accept` header prefers it (quality values respected, an absent
header or `*/*` means JSON). Otherwise the content type stays `application/json`. The body is the same object in both cases:
`status` and `message` are unchanged, `problems` is unchanged, and `type`, `title`, `detail`, `instance` are added.

Drop-in reasoning: a client that has never heard of Problem Details sends `Accept: application/json` or nothing, gets the
content type it always got, and ignores the extra fields. The dashboard reads `message` and `status`. The prometheus plugin
and scripts that parse `status` and `message` keep working. A client that wants RFC 9457 asks for it. Alternative rejected:
switching the content type for everyone, which breaks strict clients that check `Content-Type: application/json`.

### D2. Field values

- `type`: `about:blank` (RFC 9457's default for "no more specific type"). No type registry is invented here.
- `title`: the HTTP reason phrase of `status`.
- `detail`: the same text as `message`.
- `instance`: the request path (no query string, no host).

The members are optional in Kotlin (`val type: String = "about:blank"`, nullable `title` and `instance`), so a plugin that
builds an `ErrorResponse` without them still compiles and answers correctly: the writer fills `title` and `instance` from the
status and the request when they are null. `copy` and `componentN` of a data class are binary visible; the existing
three-argument constructor and the `(HttpStatus, String)` constructor are kept explicitly, and a test pins the old signatures
by reflection.

### D3. One writer for every error path

A single function (extending `Context.error`) decides the content type and fills `instance`. These paths all call it:

1. Routes that return an `ErrorResponse` (already do, through `Context.error`).
2. Javalin `HttpResponseException`s. An exception handler for `HttpResponseException` in `HttpServer` maps `status` to an
   `ErrorResponse` with the exception's message, or the reason phrase when it has none. The Javalin default body shape
   `{title, type, status, details}` is replaced under `/api` only. Outside `/api` Javalin's handling is left alone.
3. Unmatched `/api/*`. `NotFoundHandler` answers JSON through the writer when the path starts with `/api/`; any other path still
   gets the dashboard page, so dashboard deep links are unaffected.
4. 413. The writer is what the 413 of `harden-http-security` uses; this change adds no limit of its own. The test uses
   Javalin's own `maxRequestSize` 413 as the fixture so it does not depend on the other change.
5. The empty 500. `ReposiliteRouting` writes a generic body (`Internal server error`) after `failureFacade.throwException`. The
   exception message is never written (`harden-http-security` rule, and also the safe default here).

A `HEAD` request still gets no body, as `Context.response` already guarantees.

### D4. Where `instance` and `Accept` come from

Both are read from the `Context` inside the writer, so no route changes. The path is `ctx.path()`, which excludes the
forwarded prefix; a reverse proxy prefix is not added, matching what routes already see.

### D5. Upstream status codes (A2) and the setting that guards them

Gradle moves to the next repository only on a `404`; a `502` or `504` from a repository fails the build. Maven behaves the same
way for its own repository list. A different status for the same failure is therefore not harmless, so the new mapping is
opt-in.

**The setting.** `INGOT_PROXY_UPSTREAM_STATUS`, values `legacy` (default) and `strict`, compared without regard to case; any
other value is refused at startup with a clear message naming the setting. It is read once at startup, in this order: the
system property `ingot.proxy.upstreamStatus`, then the environment variable `INGOT_PROXY_UPSTREAM_STATUS`, then the legacy
fallbacks `reposilite.proxy.upstreamStatus` and `REPOSILITE_PROXY_UPSTREAM_STATUS`. This is the order the code uses for the
other `ingot.*` properties (`ingot.status.remote-version-check`, `ingot.javadocs.max-entry-bytes`: property first, then the
`reposilite.` spelling) and for the prometheus plugin (property, then environment). The setting is not part of
`LocalConfiguration`, so it has no `INGOT_LOCAL_` form and no entry in `configuration.cdn`. It is passed into
`HttpRemoteClient` and `MirrorService` as a constructor argument so tests set it without touching the environment.

**Exact statuses a client gets today (`legacy`), traced through `HttpRemoteClient`, `MirrorService` and `ResolutionProvider`.**
The real client yields `400` for a thrown exception (timeout, refused connection, DNS, TLS) and for a malformed URI, and `406`
for any upstream non-2xx (404, 429 and 5xx included) and for an HTML content type. `MirrorService` turns that into
`MirrorResolution.Failed` (`asRemoteFailure` rewrites only `401` and `403`, which the real client never produces because it
maps them to `406` first). `ResolutionProvider` then decides by lookup order:

| Case, no local copy | Local first (default) | Mirror first, or a pinned remote host |
| --- | --- | --- |
| Timeout, refused connection, DNS, TLS | `404` (the local miss) | `400` |
| Malformed upstream URI | `404` | `400` |
| Upstream `404`, `410`, `429`, `5xx`, other non-2xx | `404` | `406` |
| Upstream 2xx with an HTML content type | `404` | `406` |
| Every host missing and test double returns `404` | `404` | `404` |
| An unexpected exception in the single-flight fetch | `500` ("Mirror fetch failed: ...") | `500` |

With a local copy the client gets the copy in all rows (local first returns it before any remote call). In `legacy` all of these
stay exactly as listed. The `500` row keeps its status; its message is the business of `harden-http-security`.

**`strict` mapping**, in `HttpRemoteClient.execute`:

| Situation | `legacy` | `strict` |
| --- | --- | --- |
| `SocketTimeoutException` (connect or read) | `400` | `504`, "Upstream did not answer in time" |
| Other `IOException` (refused, DNS, TLS) | `400` | `502`, "Upstream is not reachable" |
| Upstream `404` or `410` | `406` | `404`, "Not found upstream" |
| Upstream other non-2xx | `406` | `502`, "Upstream answered <code>" |
| Upstream 2xx with an HTML content type | `406` | `502`, "Upstream answered an unexpected content type" |
| Malformed upstream URI | `400` | `400` |

**Messages in both modes.** The text never contains an exception class, an exception message or a credential, because that
changes no status code. The `legacy` `400` carries "Upstream request failed"; the detail goes to the debug log and the existing
failure facade, as before. The `406` texts ("Unsuccessful request (<code>)", "Illegal file type (<type>)") name no class and
stay. `MirrorService.asRemoteFailure` stays in both modes.

### D6. Upstream 404 stays a miss, and `legacy` does not change MirrorService

The code treats only status `404` as a miss in `MirrorService.searchInRemoteRepositories`, which lets a host list that is
entirely missing become `MirrorResolution.NotFound`, which `ResolutionProvider.recordTo` records as `MissingMetadata` in the
resolution cache. The real `HttpRemoteClient` never answers `404` (it answers `406`), so against a real upstream that path is
never taken; only the test double produces it.

- **`legacy`:** `HttpRemoteClient` and `MirrorService` behave exactly as today, including this gap. No upstream miss is newly
  recorded, so the resolution cache sees the same entries as before. A test pins this: a real-shaped upstream `404` yields
  `Failed(406)` and records no `MissingMetadata`.
- **`strict`:** the upstream `404` and `410` answer `404`, so `allMissing` becomes true and `NotFound` is recorded as a
  `MissingMetadata` entry for metadata files. This is a side effect of the mapping and is stated here on purpose. It is bounded
  by what the cache already bounds: the cache exists only when `resolutionCacheMaxEntries` is above `0` (default `0`, so off),
  holds at most that many entries, and the entry is valid only for the exact prefix. There is no time to live on the resolution
  cache today and this change does not add one; see Q1.

### D7. 502 and 504 reach the client only in `strict`

`tryLocalFirst` answers an upstream error with its own status when it is `429` or at least `500`. In `legacy` the upstream
failures are `400` and `406`, so a local first lookup shows the local `404`, as in D5. In `strict` the same failures are `502`
or `504`, so a client asking a proxy for an artifact that is not stored locally sees `502` or `504` while the upstream is down.
That is the point of `strict`, and it matches what that code already does for an upstream that answers `500` in the test
double. Gradle then marks that repository as failing, which is why `strict` is a choice of the operator and not a default.
A local copy still wins in both modes.

Group repositories: a group asks its members in order. The group code is not changed. A test per mode fixes the order of
precedence: in `legacy` a failing member does not stop a later member that has the artifact and an all-failing group answers
`404`; in `strict` a `200` from a later member beats an earlier member's `502`, and `404` from every member stays `404`. If the
current group code turns out to stop at the first non-404 in `strict`, A2 records it in the pull request instead of changing
group semantics.

### D8. ETag source (A3)

The strong `ETag` is the checksum Ingot already stores beside the artifact when one exists (`.sha256`, then `.sha1`, then
`.md5`, read through the storage provider), quoted. Without a stored checksum the tag is derived from size and last-modified
time (`"<size hex>-<mtime hex>"`). Checksums are the content's hash, so the tag changes exactly when the content changes.
Reading a sibling file costs one extra small read, so the checksum lookup is skipped for `maven-metadata.xml` and snapshots,
whose content changes in place and whose caching rules do not change; for those the size and time tag is used. Existing
`EntityTag` (the HMAC based tag for settings, `If-Match`) is not reused: it protects secrets and needs the file content.

### D9. Range and conditional semantics

Follows RFC 9110 sections 13 and 14, for `GET` and `HEAD` only:

- `Accept-Ranges: bytes` on every artifact answer.
- Evaluation order: `If-None-Match` (then `If-Modified-Since` only when `If-None-Match` is absent) answers `304` with `ETag`,
  `Last-Modified` and `Cache-Control`; then `Range`.
- One byte range (`bytes=a-b`, `bytes=a-`, `bytes=-n`) answers `206` with `Content-Range: bytes a-b/size` and the partial
  `Content-Length`.
- Unsatisfiable (start at or past the size, or a zero suffix) answers `416` with `Content-Range: bytes */size`.
- More than one range, or an unknown unit, or a malformed value: ignore the header and answer the full `200`. RFC 9110 allows
  ignoring `Range`, and `multipart/byteranges` is out of scope.
- `If-Range` with an `ETag` that matches (strong comparison) or a date equal to `Last-Modified` keeps the range; otherwise the
  full `200` is served.
- A request that sends none of these headers gets the same status, body and header values as before plus `ETag` and
  `Accept-Ranges`.
- Compression: `Content-Length` is only set when `compressionStrategy` is `none`. A ranged answer is always served uncompressed
  with its own `Content-Length`, because byte offsets refer to the stored bytes.

### D10. Ranged reads in storage

A ranged read needs the storage back end to skip or fetch only the range, or a 2 GB artifact is read in full to serve the last
kilobyte. `StorageProvider` gets a new method with a default (`getFile(location, offset, length)`) that opens the stream and
skips, so providers and plugin implementations that do not override it still work. The filesystem provider seeks, and the S3
provider sends `GetObjectRequest.range("bytes=a-b")`. Filesystem tests use a `@TempDir`; S3 tests use the S3 test container
(`SharedContainers`) in the `integration` source set. The unit tests use an in-memory provider and never open a socket.

### Test approach

Pure logic (Accept parsing, range parsing, ETag derivation, status mapping) gets plain unit tests with no server. Writer and
negotiation tests drive Javalin through the existing in-process test server with a fresh instance per test and an injected
`Clock` where a date is compared. Nothing sleeps, no port is fixed, and no test depends on another.

## Risks and trade-offs

- **R1, 502 instead of 404 for an unreachable upstream** (D7). Only with `strict`, chosen by the operator; local copies still win.
- **R2, real upstream 404 starts to be a recorded miss** (D6). Only with `strict`, and only when the resolution cache is enabled; confirm with Q1.
- **R6, a setting is read in two places.** `HttpRemoteClient` and `MirrorService` both depend on it. It is parsed once into an enum
  and passed in, so the two cannot disagree.
- **R3, ETag lookup cost** (D8). One extra read, skipped for metadata; if it shows up in profiles the tag can fall back to size
  and time with no API change.
- **R4, plugin binary compatibility** (D2). Pinned by a reflection test and by compiling the prometheus plugin.
- **R5, conflict with `harden-http-security`.** Both touch error writing. This change owns the shape and the codes; the other owns
  the removal of internal messages. Whichever merges second rebases; neither changes the other's tests.

## Open questions

- **Q1.** In `strict`, recording a real upstream miss as `MissingMetadata` has no time to live, because the resolution cache has
  none. Is the size bound (`resolutionCacheMaxEntries`, off by default) enough, or should `strict` skip recording the miss
  until a time to live exists? Default: record it, as D6 says.
- **Q2.** Settled by the maintainer: an upstream HTML 2xx stays `406` in `legacy` and is `502` in `strict`.
- **Q3.** Is the size plus time fallback tag acceptable as "strong"? Default: yes, with the checksum preferred; it changes whenever
  size or time changes, which is what the filesystem and S3 can tell us.

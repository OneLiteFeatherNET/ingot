# Tasks

This change ships as one code pull request titled `feat(api): add problem details, range requests and conditional artifact downloads`.
The work packages A1, A2 and A3 are developed on the branches `feat/api-problem-details`, `feat/api-upstream-status` and
`feat/api-artifact-ranges`, each from the remote main branch, and are combined on one branch before the pull request is opened.
A1 and A3 touch different files; A2 touches `HttpRemoteClient` and `MirrorService` only.

Every test follows F.I.R.S.T.: no sleeps, no system-time reads (inject `java.time.Clock`), no shared mutable static state, no fixed
ports, a `@TempDir` per filesystem test, a fresh server per test, and assertions with messages. Each test is written before the code it
covers and is seen to fail. Heavy runs use `heavy` with `--max-workers=2`; integration tests use `:reposilite-backend:integration --tests`.
The only new setting is `INGOT_PROXY_UPSTREAM_STATUS` (group 3), and it defaults to the current behaviour. No task changes a
deployment manifest.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose add-problem-details: this change (proposal, design, capability specs, tasks) and nothing else.
  Verify: `openspec validate add-problem-details --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-problem-details`
  prints nothing.

## 2. A1 problem details (`test(api)` then `feat(api)`)

- [ ] 2.1 `test(api)`: write failing unit tests for the Accept negotiation function: absent header and `*/*` give JSON,
  `application/problem+json` gives problem JSON, quality values decide between the two, an unrelated type gives JSON. Verify: the tests
  fail on the missing function.
- [ ] 2.2 `test(api)`: write failing tests for the writer: `type`, `title`, `detail` and `instance` are filled in; `status`, `message` and
  `problems` are unchanged; the old constructors `ErrorResponse(Int, String)` and `ErrorResponse(HttpStatus, String)` still exist (checked
  by reflection); a `HEAD` request gets no body. Verify: the tests fail on the missing members.
- [ ] 2.3 `test(api)`: write failing in-process server tests for each path of D3: an unmatched `/api/...` path answers JSON 404 while an unmatched
  dashboard path still answers the HTML page; a Javalin `HttpResponseException` answers the common shape; the request-too-large 413 answers the
  common shape; a throwing route answers 500 with a generic body that contains neither the exception message nor its class name. Verify: the
  tests fail on the current HTML page, the Javalin shape and the empty 500.
- [ ] 2.4 `feat(api)`: add the optional members to `ErrorResponse`, the negotiation function and the single writer (D1 to D4), and route the
  `HttpResponseException` handler, `NotFoundHandler` (for `/api/` only) and `ReposiliteRouting` through it. Verify: 2.1 to 2.3 pass and the
  existing tests that read error bodies pass with no assertion changed.
- [ ] 2.5 `feat(api)`: compile `:reposilite-plugins:prometheus-plugin` and the other plugins against the change. Verify: they compile with no
  source change.

## 3. A2 upstream status codes (`test(api)` then `feat(api)`)

- [ ] 3.1 `test(api)`: write failing tests for reading the setting: default is `legacy`; property beats environment beats the
  `reposilite` spellings; case is ignored; an unknown value fails the startup naming `INGOT_PROXY_UPSTREAM_STATUS`. The reader takes the
  property and environment maps as arguments, so no test touches the real environment. Verify: the tests fail on the missing reader.
- [ ] 3.2 `test(api)`: write `legacy` tests that pin today's behaviour, against a local in-process HTTP server on an OS-chosen port and a
  fake client for resolution: the table of design D5 (timeout and refused connection give `400` mirror first and `404` local first;
  upstream `404`, `500` and an HTML answer give `406` mirror first and `404` local first), a malformed URI gives `400`, a local copy is
  served while the upstream is down, and an upstream `404` records no `MissingMetadata` entry. A separate failing test asserts that no
  message contains a class name. Timeouts are small and bounded by latches, not sleeps. Verify: the pinning tests pass on the current
  code, and only the class name test fails.
- [ ] 3.3 `test(api)`: write failing `strict` tests, one behaviour per test: read timeout gives `504`, refused connection gives `502`,
  upstream `500` gives `502`, upstream `404` and `410` give `404`, an HTML answer gives `502`, a malformed URI gives `400`; a `404`
  from every host resolves to `NotFound` and the client sees `404`; a `404` from the first group member and a hit from the second serves
  the hit; a `502` from the first member and a hit from the second serves the hit; with no local copy a `502` or `504` is the answer.
  Verify: the tests fail on the current `400` and `406`.
- [ ] 3.4 `feat(api)`: add the setting reader and pass the mode into `HttpRemoteClient` and `MirrorService`; replace the exception text
  with generic messages in both modes; apply the `strict` mapping of D5 only in `strict`; leave `MirrorService.asRemoteFailure` and the
  resolution code unchanged. Verify: 3.1 to 3.3 pass, and the `legacy` tests of 3.2 still pass. If the group code stops at the first
  non-404 in `strict` (D7), record it in the pull request.


## 4. A3 ranges and conditional downloads (`test(api)` then `feat(api)`)

- [ ] 4.1 `test(api)`: write failing unit tests for the range parser (`a-b`, `a-`, `-n`, beyond the end, zero suffix, several ranges, unknown
  unit, malformed) and for the validator logic (`If-None-Match` list and `*`, `If-Modified-Since` with an injected `Clock`-free date,
  `If-Range` by tag and by date, order of evaluation of D9). Verify: the tests fail on the missing classes.
- [ ] 4.2 `test(api)`: write failing tests for the ETag source: checksum file preferred in the order `.sha256`, `.sha1`, `.md5`; size and
  time fallback; metadata and snapshot files use the fallback; the tag changes when the checksum changes. Verify: the tests fail.
- [ ] 4.3 `test(api)`: write failing in-process server tests against the filesystem storage in a `@TempDir`: plain download keeps its old headers and
  adds `ETag` and `Accept-Ranges`; 206 with `Content-Range`; 416; multi-range gives 200; `HEAD` with `Range`; 304 by tag and by date; `If-Range`
  mismatch gives 200; a download with no new header sends the same body and the same `Content-Length`, `Last-Modified` and `Cache-Control` as before.
  Verify: the tests fail on the current 200 answers.
- [ ] 4.4 `test(api)`: write failing integration tests against the S3 test container (`SharedContainers`): 206 and 416 for an object, the
  storage request carries a `Range`, 304, and the filesystem and S3 answers have identical status and validator headers. Verify: they fail on
  the current plain 200.
- [ ] 4.5 `feat(api)`: add the range parser, the validator logic and the ETag source; add `getFile(location, offset, length)` to `StorageProvider`
  with a default that skips, a seeking filesystem implementation and an S3 `GetObjectRequest.range`; wire them in `MavenEndpoints.findFile` and
  `attachmentHeaders`. Verify: 4.1 to 4.4 pass.

## 5. Documentation (`docs(api)`)

- [ ] 5.1 `docs(api)`: update `docs/gitbook/reference/plugin-api.md` for the new `ErrorResponse` members and the Accept negotiation.
  Add `docs/gitbook/reference/api-errors.md` (shape, `Accept: application/problem+json`, the gateway codes, examples) and link it in
  `docs/gitbook/SUMMARY.md`. Verify: every field named on the page exists in `ErrorResponse`.
- [ ] 5.2 `docs(api)`: document `Accept-Ranges`, `Range`, `ETag` and the conditional headers for artifacts in `docs/gitbook/reference/http-api.md`.
  Verify: the status codes on the page match the scenarios in `specs/artifact-conditional-ranges`.
- [ ] 5.3 `docs(api)`: document `INGOT_PROXY_UPSTREAM_STATUS` in `docs/gitbook/reference/environment-variables.md` (section "Other system
  properties": property `ingot.proxy.upstreamStatus`, the environment variable, the `reposilite.` and `REPOSILITE_` fallbacks, default
  `legacy`, order of precedence), and describe both modes with the table of design D5 and the Gradle reason in
  `docs/gitbook/how-to/create-proxy-repositories.md`. Verify: the statuses on both pages match the scenarios in `specs/upstream-status-codes`.

## 6. Pull request

- [ ] 6.1 `feat(api)`: open the code pull request with the title `feat(api): add problem details, range requests and conditional artifact downloads`,
  from a branch that holds A1 to A3, based on the remote main branch that already contains the merged proposal, with no file under `openspec/`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. Verify: `gh pr view --json title` shows the exact title, the commit-style hook
  accepts the title and every commit, the body has no em or en dash and no session link, and the "Container images" CI job is green.

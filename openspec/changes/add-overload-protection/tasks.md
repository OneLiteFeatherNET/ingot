# Tasks

Each group is one or more commits of one Conventional Commits type each. The scopes are `web` and `maven`, as named in each
group. Tests are written first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps
or waits by time (blocking is driven by latches with a bounded wait), no system clock, no fixed ports (port 0), no shared
mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `HEAVY_SLOTS=1 heavy`
with `--no-daemon --max-workers=2`, targeted and never the full suite. Integration tests run as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal and verification

- [ ] 1.1 `docs(openspec): propose add-overload-protection`: this change and nothing else. Verify:
  `openspec validate add-overload-protection --strict` passes and the dash grep from the proposal prints nothing.
- [ ] 1.2 Spike, not committed: check in jetty-server 12.1.12 whether `QoSHandler` exists and how it rejects, and in Javalin
  7.2.2 whether a handler inserted into the server before start survives start. Write the outcome into the pull request
  description (task 6.1) and, if it differs from `design.md` D2 or the second open verification, update `design.md` in the
  proposal pull request first. Verify: the outcome names a class, a method and a line of the library source or a test that
  shows the behaviour.

## 2. Setting (`web`)

- [ ] 2.1 `test(web)`: write failing unit tests for the setting reader as a pure function of an environment lookup and a
  property lookup: unset gives no limit; `5` gives 5; `0`, `-1` and `many` are refused with the variable name in the
  message; `INGOT_HTTP_MAXCONCURRENT` wins over `REPOSILITE_HTTP_MAXCONCURRENT`; the system property wins over both; blank
  values count as unset. Verify: they fail on the missing class.
- [ ] 2.2 `feat(web)`: add the setting reader in `web/` with the lookup order of the spec, and call it at start so that
  an invalid value stops the start. Verify: the tests of 2.1 pass and the existing `LocalConfigurationFactoryTest` passes unedited.
- [ ] 2.3 `docs(web)`: add `INGOT_HTTP_MAXCONCURRENT` and its fallback to `docs/gitbook/reference/environment-variables.md`,
  with the note that uploads and downloads hold a slot for their full duration.

## 3. Request limiter (`web`)

- [ ] 3.1 `test(web)`: write failing unit tests for the permit counter (no Jetty in it): permits are granted up to the limit;
  the next request is refused without waiting; a completed request frees one permit; a second completion of the same request
  frees nothing; an exempt path is never counted; the count returns to zero after a request fails. Add an integration test
  against a real server on port 0 with limit 1: a blocked artifact request (held by a latch, released in the test body)
  makes a second artifact request answer `503` with `Retry-After: 1` and an `ErrorResponse` body; `GET /api/status/health`
  answers `200` during the block; after the latch is released a third request answers normally. Add a test with
  `Accept: application/problem+json` that expects the problem content type. Verify: they fail before 3.2.
- [ ] 3.2 `feat(web)`: add the limiter as a Jetty handler wrapper, installed in `JavalinConfiguration.configure` only when
  the setting is set, with the choice from task 1.2 (own permit counter by default, `QoSHandler` only when the spike allows it).
  The permit is released from the completion callback. Verify: the tests of 3.1 pass and the existing integration tests of the
  web layer pass unedited.
- [ ] 3.3 `docs(web)`: add the `503` answer, its `Retry-After` header and the exempt health paths to
  `docs/gitbook/reference/http-api.md`, and a sizing note to `docs/gitbook/reference/environment-variables.md`.

## 4. Mirror coalescing (`maven`)

- [ ] 4.1 `test(maven)`: write failing tests against `MirrorService` with the fake remote client and a latch: two concurrent
  requests for one artifact through a storing host make one upstream request and both receive the stored file; a non-storing
  host makes one upstream request per caller. Verify: the first test passes on today's code (it pins existing behaviour), and the
  second states its expectation with a message.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `feat(web): shed requests above an opt-in concurrency limit`. Read
  `.github/pull_request_template.md` and fill its sections. The description tells the operator's story (a burst of builds
  that made the probe fail and the pod restart) without class names, and it records the outcome of task 1.2. It carries no
  session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts
  the title and every commit, and the body has no em or en dash.

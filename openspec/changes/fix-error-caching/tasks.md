# Tasks

This change ships as one code pull request titled `fix(http): add vary accept and no-cache to error responses`. It is
developed from the remote main branch after the proposal pull request is merged. Commits: `test(http)` before `fix(http)`
in each group. The change adds no setting, environment variable or manifest key, so no `docs/gitbook` page changes.

Every test follows F.I.R.S.T.: no sleeps, no system-time reads, no shared mutable static state, a fresh in-process server on an
OS-chosen port per test, and assertions with messages. Each test is written before the code it covers and is seen to fail.
Heavy runs use `heavy` with `--max-workers=2`; integration tests use `:reposilite-backend:integration --tests`.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose fix-error-caching: this change (proposal, design, capability spec, tasks) and nothing else.
  Verify: `openspec validate fix-error-caching --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-error-caching`
  prints nothing.

## 2. Vary on error answers (`test(http)` then `fix(http)`)

- [ ] 2.1 `test(http)`: write failing unit tests for the shared Vary merge (design D2): an absent value gives `Accept`; `Accept-Encoding`
  gives `Accept-Encoding, Accept`; a value that already lists `accept` in any letter case is unchanged; `*` stays `*`. Verify: the tests
  fail on the missing function.
- [ ] 2.2 `test(http)`: write failing in-process server tests, in the style of `ProblemDetailsTest`: an `/api` `404` with and without
  `Accept: application/problem+json` carries a `Vary` that includes `Accept`, and its content type and body are the same as before; a
  repository request refused with an `ErrorResponse` carries `Vary` with `Accept`. Verify: the tests fail on the missing header.
- [ ] 2.3 `fix(http)`: in `Context.error(ErrorResponse)` merge `Accept` into `Vary`; move the generic merge out of
  `DashboardRouteDecision.varyHeader` and have the dashboard call it with its two fetch metadata headers (design D1, D2). Verify: 2.1 and
  2.2 pass, and `DashboardRouteDecisionTest` and the dashboard `Vary` integration tests pass unchanged.

## 3. No-cache on error answers outside `/api` (`test(http)` then `fix(http)`)

- [ ] 3.1 `test(http)`: write a failing unit test for the `Cache-Control` decision as a pure function of the path and the existing value:
  a path under `/api` is unchanged; any other path with no value gets `no-cache`; an existing value is kept. Verify: the test fails on the
  missing function.
- [ ] 3.2 `test(http)`: write failing in-process server tests: a missing file in a repository answers `404` with the HTML page and
  `Cache-Control: no-cache`; a repository request refused with `401` carries `no-cache` and still `WWW-Authenticate`; a successful
  download keeps its `Cache-Control`, `Content-Type` and `Content-Length` (this one passes before the change and guards it). Verify: the
  first two fail, the third passes.
- [ ] 3.3 `fix(http)`: apply the decision in `Context.error(ErrorResponse)` (design D3) and in the `findFile` error branch of
  `maven/infrastructure/MavenEndpoints.kt` for paths outside `/api`, with `no-cache` (design D4). Verify: 3.1 and 3.2 pass, and the existing
  integration tests for `/api` `Cache-Control` and `bypassExternalCache` pass unchanged.

## 4. Pull request

- [ ] 4.1 Open the code pull request titled `fix(http): add vary accept and no-cache to error responses` from the branch that holds the
  commits of groups 2 and 3. It contains no file under `openspec/` and no session link. Verify: `gh pr view` shows that exact title, and
  `git diff --name-only origin/main` lists no path under `openspec/`.

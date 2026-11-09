# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `security`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the policy takes no time source at all), no fixed ports, no shared mutable state, a temporary directory per test where
one is needed, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-fetch-metadata`: this change and nothing else. Verify:
  `openspec validate add-fetch-metadata --strict` passes and the dash grep from the proposal prints nothing.

## 2. F1 Resource isolation for cookie requests

- [ ] 2.1 `test(security)`: write failing unit tests for `FetchMetadataPolicy` as a pure function of method, path, base path,
  the authorization flag, the session and refresh cookies and the three `Sec-Fetch-*` values. Cases: `same-site` and
  `cross-site` refused on a `GET` as well as a `POST`; `same-origin`, `none` and a missing header allowed; any other value
  allowed; `navigate` with `document` allowed, `navigate` with another destination refused; an `Authorization` header exempts;
  a request without a cookie exempts; the refresh cookie alone counts; `/api` at the root and below `/ingot`; `/apix` and a
  repository path exempt; the switch parsed from the environment, from the property, `off` in any case, `of` keeps it on.
  Verify: they fail on the missing class.
- [ ] 2.2 `test(security)`: write failing integration tests against the running server with the existing login harness:
  cross-site `GET /api/auth/me` with `ingot_session` answers `403` with the `ErrorResponse` body; same-origin answers `200`;
  a matching `X-CSRF-Token` on a `POST` with `same-site` answers `403`; a bearer token with `cross-site` is not refused; the
  OIDC callback as a top-level navigation is not refused; `INGOT_FETCH_METADATA=off` lets the cross-site request through.
  Use a fresh instance per test and no fixed port. Verify: the cross-site tests fail before 2.3.
- [ ] 2.3 `feat(security)`: add `FetchMetadataPolicy` and `FetchMetadataSettings` in `net.onelitefeather.ingot.identity.session`,
  shaped like `SecurityHeadersSettings`, and call the policy in the route handler of `ReposiliteRouting.kt` before the CSRF
  check, with the same error body as the CSRF refusal. Verify: the tests of 2.1 and 2.2 pass, and `CsrfPolicyTest`,
  `SessionCookiesTest` and the existing login integration tests pass unedited.

## 3. Documentation

- [ ] 3.1 `docs(security)`: add the row for `INGOT_FETCH_METADATA` (system property `ingot.fetchMetadata`, default `on`) to
  the server variables in `docs/gitbook/reference/environment-variables.md`, and state that there is no `REPOSILITE_` spelling.
- [ ] 3.2 `docs(security)`: add a short section on cross-site requests to `docs/gitbook/explanation/sessions-and-login.md`
  beside the CSRF section, a sentence in `docs/gitbook/reference/web-routes.md` next to the `ingot_csrf` rule, and the new
  `403` with its exceptions in `docs/gitbook/reference/upgrade-changes.md`, including the unsupported cross-site dashboard
  that needs the switch. Verify: `grep -nP '[\x{2013}\x{2014}]'` over the three files prints nothing.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `feat(security): refuse cross-site cookie requests to the api`.
  Read the repository's pull request template and fill its sections. The description tells the story of the operator
  (a session that a page on another site could use, and how it is refused now) without class names. It carries no session
  link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the
  title and every commit, and the body has no em or en dash.

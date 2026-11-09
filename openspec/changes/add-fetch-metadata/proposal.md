# Proposal

## Why

A person who signs in to the Ingot dashboard holds the session in three cookies. The browser sends them with every request
to the server, including requests that a page on another site starts. The double-submit check (`X-CSRF-Token`) stops a
forged change, but it does not look at reads. A page on another site can still make the browser fetch an `/api` answer
with the cookies of the victim attached, and nothing on the server refuses it.

The double-submit check also has a known gap. Ingot sets its cookies without a `Domain` attribute, so a page on a sibling
subdomain of the same registrable domain can set a cookie that the browser then sends to Ingot. That lets the attacker
choose the `ingot_csrf` value, and the two values then match. The research notes rate this as defence in depth rather than
a demonstrated exploit, but the fix is cheap.

The W3C Fetch Metadata headers tell the server where a request came from. A sibling subdomain is `same-site`, not
`cross-site`, so the check has to refuse `same-site` as well as `cross-site`. The roadmap item said `cross-site` only; that
would not close the gap above. See design D1.

Facts checked against origin/main (`git grep` and `git show`):

- `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/session/CsrfPolicy.kt` checks only state-changing
  methods (`SAFE_METHODS`, line 31), exempts requests with an `Authorization` header (line 59) and requires a session or
  refresh cookie (line 62). It is double-submit only. The roadmap is right on this.
- `reposilite-backend/src/main/kotlin/com/reposilite/web/infrastructure/ReposiliteRouting.kt` calls `CsrfPolicy.violation`
  in the route handler (line 123) and answers a refusal with `FORBIDDEN.toErrorResponse(...)`. The cookie is read as
  `ingot_session` in the same file (line 93). This is where a second refusal belongs.
- `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/session/SessionCookies.kt` names the cookies
  `ingot_session`, `ingot_refresh` and `ingot_csrf` (lines 29-31). The session and refresh cookies are HttpOnly and
  `SameSite=Lax`.
- `reposilite-backend/src/main/kotlin/com/reposilite/auth/infrastructure/PostAuthHandler.kt` only adds `WWW-Authenticate`
  to a `401` (lines 30-34). It refuses nothing, so the refusal cannot go there.
- `Sec-Fetch-*` is read only by the dashboard routing (`frontend/DashboardRouteDecision.kt` line 44 and
  `frontend/infrastructure/FrontendHandler.kt` lines 127-128). Nothing checks it for `/api`.
- The cookie sessions were introduced by Ingot commits (`0173f708`, `9f9c3a6d`, both `feat(identity)`). Reposilite has no
  such cookies, which decides D5.
- The split dashboard (`docs/gitbook/how-to/run-split-dashboard-container.md`, line 48, and
  `run-split-with-helm.md`) serves the dashboard and `/api` from one origin, so the browser sends `same-origin` for the
  dashboard's own calls.
- `openspec/specs/` holds no capability yet, so this change adds `fetch-metadata`.

## What Changes

- **F1 Resource isolation for cookie requests (`feat(security)`).** A request under `/api` (also below the configured base
  path) is refused with `403` when all of these hold:
  - it has no `Authorization` header;
  - it carries the `ingot_session` or the `ingot_refresh` cookie;
  - its `Sec-Fetch-Site` header is `same-site` or `cross-site`;
  - it is not a top-level navigation, that is `Sec-Fetch-Mode: navigate` together with `Sec-Fetch-Dest: document`.

  The check applies to every method, not only to changes. A missing header is allowed, and so are `same-origin` and
  `none`. The refusal uses the same error body as the CSRF refusal, and it happens before the request is authenticated or
  routed, so no handler runs.
- **F2 Switch.** `INGOT_FETCH_METADATA=off`, or the system property `-Dingot.fetchMetadata=off`, turns F1 off. The value is
  matched without regard to case. Any other value keeps F1 on, so a typo cannot switch it off. There is no `REPOSILITE_`
  spelling (D5).
- **Documentation.** `docs/gitbook/reference/environment-variables.md` for the switch, `docs/gitbook/explanation/sessions-and-login.md`
  and `docs/gitbook/reference/web-routes.md` for the rule, and `docs/gitbook/reference/upgrade-changes.md` for the new `403`.

### Out of scope

- Repository paths. Cookie authentication also reaches them, but Maven, Gradle and sbt clients never send cookies, and the
  answer for a cookie request to an artifact needs its own decision. A follow-up can take it.
- Replacing the double-submit check or changing the cookies (names, `SameSite=Lax`, paths, lifetimes).
- Requests with an `Authorization` header, which are token requests and are exempt by definition.
- The body shape of the `403`. It goes through the existing error writer, which already answers
  `application/problem+json` when the client prefers it (`add-problem-details`, merged).
- Metrics, audit events or a report endpoint for refused requests.
- The Nitro dashboard server and the Helm chart, which need no change because their browser traffic is same-origin.

## Capabilities

### New Capabilities

- `fetch-metadata`: refusal of cookie-authenticated `/api` requests that come from another site, the navigation exemption,
  and the `INGOT_FETCH_METADATA` switch.

## Impact

- **Type and scope.** The change ships under `feat(security)`, and the proposal is `docs(openspec)`. The pull request
  title, squash-merged onto main, is:

  `feat(security): refuse cross-site cookie requests to the api`

  The proposal PR title is `docs(openspec): propose add-fetch-metadata`.
- **Breaking marker.** Not breaking. No operator has to change a configuration key, environment variable, volume, manifest
  or plugin to keep a documented deployment working. Single-container, split compose and split Helm setups send same-origin
  requests and are unaffected. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** A cookie request to `/api` from another site, or from a sibling subdomain, now
  gets `403`. No Maven, Gradle, sbt or token client is affected. A dashboard served from another site than its API, which
  no documented deployment does, needs `INGOT_FETCH_METADATA=off`. The upgrade notes say so.
- **Code.** `reposilite-backend` (identity session, web routing, settings) and `docs/gitbook/`.
- **Overlap.** `harden-http-security` (merged) already changed `CsrfPolicy.kt` and the refusal branch in
  `ReposiliteRouting.kt`. The new check sits next to that branch and leaves its behaviour unchanged.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate add-fetch-metadata --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-fetch-metadata` prints nothing.

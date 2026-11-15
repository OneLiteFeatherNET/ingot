# Design

## D1 Refuse `same-site` as well as `cross-site`

The roadmap item refuses `cross-site` only. The threat it names, cookie tossing from a sibling subdomain, produces
`Sec-Fetch-Site: same-site`, so a `cross-site`-only rule would let that request through. The rule refuses both values. A
dashboard and its API on one origin send `same-origin` and are not affected, and a sibling application that calls the API
with cookies is not a documented setup. The switch covers the rest.

## D2 Every method, not only changes

`CsrfPolicy` exempts safe methods because double-submit protects changes. Resource isolation is about reads: a cross-site
page must not be able to fetch an `/api` answer with the victim's cookies. So the new check applies to `GET` and `HEAD` as
well. The two checks stay separate, so `CsrfPolicy` keeps its meaning and its tests.

## D3 Navigation is exempt, and only the exact pair

A top-level navigation (`Sec-Fetch-Mode: navigate`, `Sec-Fetch-Dest: document`) carries the external login callbacks, which
arrive from the identity provider as a redirect. Refusing them would break sign-in. The exemption needs both values, so an
embedded frame (`Sec-Fetch-Dest: iframe`) is still refused. This is the same pair that `DashboardRouteDecision` uses to
recognise a dashboard navigation.

## D4 Placement

The check is a pure policy in `identity/session`, called from the route handler in `ReposiliteRouting.kt` before the CSRF
check. It is not in `PostAuthHandler`, which only adds challenge headers and never refuses. It is not in `CsrfPolicy`,
which `harden-http-security` also edits. The refusal runs before authentication, so a refused request never reaches a
handler and never touches a session.

## D5 No `REPOSILITE_` fallback for the switch

The fallback exists for settings that Reposilite already had. Cookie sessions and their `ingot_*` names were introduced by
Ingot commits, so Reposilite has no setting to fall back to. The environment-variable reference already documents this case
for `INGOT_TRUSTED_PROXIES`. The switch is read from `INGOT_FETCH_METADATA`, then the property `ingot.fetchMetadata`, as in
`SecurityHeadersSettings`. Only the value `off` disables it, so a typo keeps the protection.

## Alternatives not taken

- Blocking by `Origin` header instead. It is absent on many same-origin `GET` requests and so is weaker for reads.
- A cookie with the `__Host-` prefix. It would close cookie tossing, but it changes the cookie names, which breaks
  existing sessions and the drop-in rule. This change keeps the names.

# Proposal

## Why

A static audit of the HTTP layer found five places where Ingot, like the Reposilite it is forked from, trusts its caller too
much or says too much to it.

An operator who puts Ingot behind a proxy sees the first one. The login lockout is keyed on the client address, and the
address is read raw from the forwarded header (`X-Forwarded-For` by default). Anyone who can reach the server can send that
header themselves, put a new value in it on every attempt, and get a fresh lockout key each time, so the brute-force
protection counts nothing. There is no way to tell Ingot which proxies to believe.

The second is what a browser is told. The backend sends no `Referrer-Policy`, `X-Content-Type-Options`, frame protection or
`Permissions-Policy`, and nothing for HSTS. Files served from a repository already carry `Content-Security-Policy: sandbox`
(`attachmentHeaders`), and the Javadoc view carries `sandbox allow-scripts`, but the dashboard document, the `/api` answers
and the generated directory index carry none of it.

The third is how Ingot answers when it refuses. A `401` on `/api` has no `WWW-Authenticate` header, which RFC 9110 section
11.6.1 requires. A `405` for an upload to a proxy or group repository has no `Allow` header (section 15.5.6). The CSRF
exemption for login and setup matches the end of the path rather than the path.

The fourth is what a failure reveals. Storage, S3, filesystem, settings parsing and the remote client put the raw exception
message, or even the exception class name, into the body of the response. An unauthenticated caller can read internal
paths, bucket names and library messages from it.

The fifth is that the small JSON endpoints (login, setup, refresh, token and principal) accept a body as large as the global
request limit, which exists for artifact uploads. A login request has no business being megabytes long.

## What Changes

- **S1 Trusted proxies.** New optional setting `INGOT_TRUSTED_PROXIES` / `-Dingot.trustedProxies`, a comma separated list
  of addresses and CIDR ranges (IPv4 and IPv6). When it is set, the forwarded header is honoured only when the direct peer
  is in the list, and the client address is the rightmost entry of the chain that is not itself a trusted proxy. When it is
  not set, the client address is resolved exactly as today. The server logs one warning at start when the forwarded header
  is in use and the list is empty. The login limiter, the authentication brute-force ban and the audit log all take the
  address from the one resolver and so follow the setting.
- **S2 Security headers.** The backend adds `X-Content-Type-Options: nosniff` and
  `Referrer-Policy: strict-origin-when-cross-origin` to every response. Dashboard documents and `/api` answers also get
  `X-Frame-Options: DENY` and `Content-Security-Policy` with `frame-ancestors 'none'`. The dashboard document gets a policy
  that the built application satisfies, and a minimal `Permissions-Policy`. The existing `sandbox` policy on repository
  content stays and is extended to the directory index pages that lack it; a switch `INGOT_REPOSITORY_SANDBOX`
  (default `true`) turns it off. `Strict-Transport-Security` is opt-in through `INGOT_HSTS_MAX_AGE` (seconds, unset by
  default) and is sent only on requests that arrived over HTTPS.
- **S3 Challenge and method headers.** A `401` under `/api` carries `WWW-Authenticate: Bearer realm="Ingot"`. Repository
  paths keep the `Basic` challenge. A `405` for an upload or delete on a proxy or group repository carries `Allow`. The CSRF
  exemption for login and setup matches the exact paths, with the base path taken into account.
- **S4 No internal messages.** Errors from storage, S3, the filesystem, Jackson and the remote client answer with a generic
  message for the status. The detail is logged and recorded through the existing failure facade. Status codes do not change.
- **S5 Body limits.** The JSON endpoints of login, setup, refresh, token and principal management refuse a body above
  64 KiB with `413` in the `ErrorResponse` shape, before the body is read (a `Content-Length` check, and a bounded read for
  chunked bodies). Artifact uploads are not affected. A test pins that `X-Forwarded-Proto: https` marks the session cookie
  `Secure`, which is already implemented.
- **Documentation.** `docs/gitbook/how-to/run-behind-reverse-proxy.md` and `docs/gitbook/reference/environment-variables.md`
  for the three new settings, `docs/gitbook/reference/http-api.md` for the headers and the `413`, and the upgrade notes.

### Out of scope

- Problem Details (RFC 9457), the `application/problem+json` content type, and any status code change. Those belong to the
  change `add-problem-details`. This change keeps every status code and the `ErrorResponse` body shape.
- The Nitro dashboard server and the Helm chart, which pull request #77 owns. Their security headers and trusted proxy
  values are set there. The docs here only mention the setting.
- The contact address in `SECURITY.md`, which needs the maintainer.
- A configurable Content Security Policy, a report endpoint, and per-route rate limiting beyond the login limiter.
- Changing the shape or the lifetime of cookies, tokens or sessions.

## Capabilities

### New Capabilities

- `client-address`: how the client address is resolved from the peer and the forwarded header, and the trusted proxy list.
- `security-headers`: the headers on every response, on the dashboard and `/api`, on repository content, and HSTS.
- `http-challenges`: the `WWW-Authenticate` and `Allow` headers and the exact CSRF exemption.
- `error-disclosure`: what error bodies may and may not contain.
- `request-limits`: the body cap on small JSON endpoints and the `Secure` cookie rule.

## Impact

- **Type and scope.** The change ships under `fix(security)`. The branches use `test`, `fix` and `docs` commits with the scope
  `security`. The pull request title, squash-merged onto main, is:

  `fix(security): harden http authentication, headers and error bodies`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change, and a Reposilite installation pointed at Ingot behaves as before for every Maven and Gradle client, plugin and the
  dashboard. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None needs action from an operator: `nosniff`, `Referrer-Policy` and frame
  protection appear on responses; a `401` on `/api` gains a header; a `405` gains a header; error bodies lose internal
  detail (a client that matched on the old text stops matching, which is the intent and is listed in the upgrade notes); a
  JSON body above 64 KiB on the listed endpoints is refused.
- **Opt-in.** `INGOT_TRUSTED_PROXIES` and `INGOT_HSTS_MAX_AGE` change nothing until set. `INGOT_REPOSITORY_SANDBOX` defaults
  to the behaviour of today.
- **Code.** `reposilite-backend` (web, identity, auth, maven, storage, shared http, configuration) and `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`.
- `openspec validate harden-http-security --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/harden-http-security` prints nothing.

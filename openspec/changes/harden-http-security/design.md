# Design

## Context

Verified against the code on main:

- The client address comes from `config.contextResolver.ip` in `com.reposilite.web.application.JavalinConfiguration`, which
  returns the raw value of the header named by `WebSettings.forwardedIp` (default `X-Forwarded-For`), else the peer. Callers
  go through `ctx.ip()`: the login and setup endpoints (`AuthLoginEndpoints`) hand it to `LoginService`, which keys
  `LoginAttemptLimiter.userKey` and `addressKey` on it, and the audit actor (`AuditEvent`), the console and the access log
  use it too.
- The console handlers also read the forwarded header on their own (`ConsoleWebSocketHandler`, `ConsoleSseHandler`).
- `Context.attachmentHeaders` already sets `Content-Security-Policy: sandbox` on every repository file. `JavadocEndpoints`
  sets `sandbox allow-scripts` on Javadoc pages. The directory index page (`ctx.html(createDirectoryIndexPage(...))` in
  `MavenEndpoints`) sets neither. No other security header is sent by the backend.
- `PostAuthHandler` adds `WWW-Authenticate: Basic` only outside `/api`, on purpose, so browsers do not show a Basic popup.
- `MavenFacade.checkAcceptsUploads` answers `405` for typed proxy and group repositories on the deploy routes (`POST`, `PUT`)
  and the delete route.
- `CsrfPolicy` exempts a path with `endsWith("/api/auth/login")` and `endsWith("/api/auth/setup")`.
- `SessionCookies.isSecure` already treats `X-Forwarded-Proto: https` as secure.
- Raw `exception.message` or the class name reaches the body in `StorageEntrySource`, `S3EntrySource`,
  `FileSystemEntrySource`, `S3StorageProvider`, `SettingsEndpoints` (Jackson) and `HttpRemoteClient`.
- Only the global `javalin.maxRequestSize` limits request bodies.

## Goals

- A lockout that an attacker cannot reset by writing a header.
- Browsers are told what they may do with Ingot's responses.
- Error responses follow the HTTP rules for challenge and allowed methods.
- Error bodies carry no internal detail.
- Everything that could change what an existing client sees is opt-in, or is a pure addition of a header.

## Non-goals

See "Out of scope" in the proposal. In particular no status code and no body shape changes here.

## Decisions

### D1 (S1): trusted proxies are opt-in, and unset means today's behaviour

The setting is `INGOT_TRUSTED_PROXIES` / `-Dingot.trustedProxies`, a comma separated list of IPs and CIDRs. It is a new
setting, so it takes the `INGOT_` prefix and has no `REPOSILITE_` spelling.

Why opt-in: Ingot cannot know the address of the proxy in front of it. Defaulting to "trust nobody" would make every
existing deployment behind nginx, Traefik or a Kubernetes ingress see every request as coming from the proxy, which locks
all users out together after a few failures and puts the proxy address in every audit entry. That breaks the drop-in
contract. So an unset list keeps the current resolution, and one start-up warning tells the operator that client addresses
can be spoofed and what to set.

When set, resolution is: if the direct peer is not trusted, use the peer and ignore the header. Otherwise walk the header
entries from the right, skip every entry that is trusted, and take the first one that is not. If all entries are trusted,
take the leftmost. An entry that does not parse as an address is treated as untrusted and ends the walk, so garbage is
never trusted.

Alternatives: take the leftmost entry (what the header looks like, and exactly what is spoofable); trust a hop count
(breaks when the chain length changes); read the `Forwarded` header too (not used by the common proxies, can be added later).

The resolver is one pure function with the peer, the header value and the list as inputs. It is used by
`contextResolver.ip`, the console handlers, the brute-force ban and the audit actor, so the setting cannot be applied in one
place and forgotten in another.

### D2 (S2): headers on every response, sandbox kept, HSTS opt-in

`nosniff` and `Referrer-Policy: strict-origin-when-cross-origin` go on every response through a Javalin before handler.
Neither changes how a Maven or Gradle client reads a response.

`X-Frame-Options: DENY` with `frame-ancestors 'none'` goes on dashboard documents and `/api`. The Javadoc view embeds
Javadoc in an iframe, but the embedded document is a repository path, not the dashboard, so denying framing of the dashboard
does not affect it. A test serves the Javadoc view to show that.

The dashboard CSP is built from what the built `index.html` contains. Inline scripts and styles and the placeholder
injection (`{{REPOSILITE.*}}`) are checked first; where an inline block is needed, the policy carries its hash or a nonce.
Plugins register values through the placeholders, so the policy must not forbid what they inject. A backend test serves the
built index and checks it against the policy.

Repository content: the sandbox policy already exists on files and Javadoc pages, so Javadoc keeps its scripts today
(`allow-scripts`) and runs in an opaque origin without the session. This change keeps those values, so it never loosens,
and adds the same policy to the directory index page, which is HTML generated from file names. The switch
`INGOT_REPOSITORY_SANDBOX` (default `true`) removes the sandbox header for operators who host something that needs the origin
of the instance. It has an off switch because the sandbox is the one header here that can break a working site, for example
a static site published out of a repository that uses cookies or storage. See the open questions on widening the token list.

HSTS is opt-in because it is sticky: a browser that sees it refuses plain HTTP for the whole period, so enabling it on an
instance that is also reachable over HTTP (internal name, test environment) locks people out. `INGOT_HSTS_MAX_AGE` in
seconds, unset by default, is sent only when the request is HTTPS, decided by the TLS scheme or by `X-Forwarded-Proto`
through the same rule that `SessionCookies.isSecure` uses. `includeSubDomains` and `preload` are not added; they are
decisions for the operator's domain and not for the application.

`Permissions-Policy` is a minimal deny list (camera, microphone, geolocation, payment, usb) on dashboard documents only.

### D3 (S3): Bearer on `/api`, Basic stays on repositories

RFC 9110 requires a `WWW-Authenticate` on `401`. The current code omits it so that browsers do not open a Basic
authentication popup when the dashboard calls `/api` with an expired session. A `Bearer` challenge keeps the rule and
raises no popup, because browsers only prompt for `Basic` and `Digest`. It also describes what `/api` accepts (tokens and
sessions) better than `Basic` would. Repository paths keep `Basic realm="Ingot"`, because Maven and Gradle need the challenge
to send credentials on retry.

`Allow` on the `405` lists `GET, HEAD`, the methods a proxy or group repository answers. It is set where the `405` is
produced, next to `checkAcceptsUploads`.

The CSRF exemption becomes an exact comparison against the two paths with the configured base path, because
`endsWith("/api/auth/login")` also matches `/anything/api/auth/login`.

### D4 (S4): generic message per status, detail to the log

Each site returns a fixed message for its status (for example `Storage failure`, `Invalid settings`,
`Upstream request failed`). The exception goes to the logger and to the failure facade, so operators lose nothing. Status
codes stay as they are: `add-problem-details` owns code changes, and the two changes touch the same lines in
`HttpRemoteClient`, so this change replaces only the message text. Tests assert that the body contains neither the exception
message nor the class name. The Jackson parse message in settings goes too, because it echoes submitted content and the
structure of the settings classes.

### D5 (S5): 64 KiB per route, checked before reading

The cap is a constant of 64 KiB for the login, setup, refresh, token and principal JSON endpoints. A request with a
`Content-Length` above it is answered `413` in the existing `ErrorResponse` shape before the body is read. A chunked body is
read through a bounded stream that stops at the limit plus one byte. Artifact uploads and the global `maxRequestSize` do not
change. It is a constant, not a setting, because there is no legitimate larger login body and a knob invites misuse.

The session cookie keeps `Secure` when the request is HTTPS or `X-Forwarded-Proto` says so. A test pins it. It is not
changed, because requiring TLS would break plain-HTTP development and internal installs. When trusted proxies are set (D1),
the forwarded scheme is honoured only from a trusted peer, which closes the same spoofing hole for the cookie.

## Risks and trade-offs

- A deployment that sets trusted proxies wrongly sees every client as the proxy. The start-up warning and the documentation
  name this, and unset remains the default.
- A dashboard with `frame-ancestors 'none'` cannot be embedded in another site. There is no setting for it in this change.
- A generic error message makes support harder. The log carries the request path and the exception, and operators keep it.

## Open questions

1. The audit brief proposes `sandbox allow-scripts allow-popups allow-forms allow-downloads` on repository HTML, SVG and
   XML. The code already sends plain `sandbox` on files and `sandbox allow-scripts` on Javadoc pages, which is stricter for
   plain files. Keep today's values, as written here, or widen to the proposed list?
2. Should a later setting allow embedding the dashboard in a frame from named origins?

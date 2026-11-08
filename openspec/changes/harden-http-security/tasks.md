# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `security`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose harden-http-security`: this change and nothing else. Verify:
  `openspec validate harden-http-security --strict` passes and the dash grep from the proposal prints nothing.

## 2. S1 Trusted proxies

- [ ] 2.1 `test(security)`: write failing tests for the address resolver as a pure function of peer, header value and list:
  unset list keeps today's result; untrusted peer ignores the header; rightmost untrusted entry wins; spoofed prefix is
  ignored; all-trusted chain gives the leftmost; garbage entry ends the walk; IPv6 and CIDR parsing; an invalid list entry
  is refused with its name; one start-up warning when the header is in use without a list (captured appender). Add a test
  that varying `X-Forwarded-For` from an untrusted peer does not change the lockout key. Verify: they fail on the missing class.
- [ ] 2.2 `fix(security)`: add the resolver and the `ingot.trustedProxies` / `INGOT_TRUSTED_PROXIES` setting, use it in
  `contextResolver.ip`, the console handlers, the brute-force ban and the audit actor, and apply it to the forwarded scheme
  of the session cookie. Verify: the tests of 2.1 pass and the existing login tests pass unedited.
- [ ] 2.3 `docs(security)`: update `docs/gitbook/how-to/run-behind-reverse-proxy.md` and
  `docs/gitbook/reference/environment-variables.md`; mention the setting for the Helm chart and the split compose file
  without changing them.

## 3. S2 Security headers

- [ ] 3.1 `test(security)`: write failing tests: `nosniff` and `Referrer-Policy` on an artifact, an API and a dashboard
  response; `X-Frame-Options` and `frame-ancestors` on `/api` and the dashboard document; the built `index.html` against the
  dashboard policy; `Permissions-Policy` on the dashboard only; `sandbox` on the directory index; Javadoc keeps
  `allow-scripts` without `allow-same-origin`; `INGOT_REPOSITORY_SANDBOX=false` removes the sandbox; HSTS absent when unset,
  absent on plain HTTP, present with `X-Forwarded-Proto: https`. Verify: they fail before 3.2.
- [ ] 3.2 `fix(security)`: add a before handler for the common headers, the dashboard and API headers, the dashboard policy
  (hashes or nonces from the built index), the sandbox on directory index pages, `INGOT_REPOSITORY_SANDBOX` and
  `INGOT_HSTS_MAX_AGE`. Verify: the tests of 3.1 pass, the frontend tests (`npx vitest run`) pass and the dashboard loads in
  the backend test that serves the built index.
- [ ] 3.3 `docs(security)`: document the headers in `docs/gitbook/reference/http-api.md`, the two settings in
  `docs/gitbook/reference/environment-variables.md`, and the HTTPS requirement of HSTS in the reverse proxy how-to.

## 4. S3 Challenge and method headers

- [ ] 4.1 `test(security)`: write failing tests for the `Bearer` challenge on `/api` (and no `Basic` there), the unchanged
  `Basic` challenge on a repository path, `Allow: GET, HEAD` on the `405` for proxy and group repositories, and the CSRF
  exemption for the exact paths, a look-alike path and a base path. Verify: they fail before 4.2.
- [ ] 4.2 `fix(security)`: set the challenge in `PostAuthHandler`, `Allow` where `checkAcceptsUploads` produces the `405`,
  and compare exact paths in `CsrfPolicy`. Verify: the tests of 4.1 pass.
- [ ] 4.3 `docs(security)`: update the response headers in `docs/gitbook/reference/http-api.md`.

## 5. S4 No internal messages

- [ ] 5.1 `test(security)`: write failing tests for the filesystem, S3, storage source, settings (Jackson) and remote client
  paths: the body holds the generic message, holds neither the exception message nor the class name, the status is
  unchanged, and the detail is in a captured log appender. Verify: they fail before 5.2.
- [ ] 5.2 `fix(security)`: replace the messages at each site, log the exception and record it through the failure facade.
  Verify: the tests of 5.1 pass and no existing test depended on the old text, or it is updated.
- [ ] 5.3 `docs(security)`: list the changed messages in `docs/gitbook/reference/upgrade-changes.md`.

## 6. S5 Body limits and cookie

- [ ] 6.1 `test(security)`: write failing tests for `413` on a declared length above 64 KiB, on a chunked body above 64 KiB,
  the normal login still working, a 10 MiB artifact upload still working, and the `Secure` cookie on
  `X-Forwarded-Proto: https`. Verify: the `413` tests fail before 6.2.
- [ ] 6.2 `fix(security)`: add the per-route cap with the bounded read to the login, setup, refresh, token and principal
  endpoints, answering in the `ErrorResponse` shape. Verify: the tests of 6.1 pass.
- [ ] 6.3 `docs(security)`: document the `413` and the limit in `docs/gitbook/reference/http-api.md`.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `fix(security): harden http authentication, headers and error bodies`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (the
  lockout that a header reset, the files and answers that said too much) without class names. It carries no session link and
  no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and
  every commit, and the body has no em or en dash.

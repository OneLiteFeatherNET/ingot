# Tasks

Each group is one or more commits of one Conventional Commits type each. The switch is `feat(status)` and its tests are
`test(status)`; the pages are `docs(status)`. Tests are written first and seen to fail before the production
code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no fixed ports (the local server binds
port 0), no shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `heavy`,
with `--max-workers=2`, targeted and never the full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-version-check-switch`: this change and nothing else, branched from the remote main.
  Verify: `openspec validate add-version-check-switch --strict` passes and the dash grep from the proposal prints nothing.
  The proposal PR is merged before the code PR is opened.

## 2. Switch

- [ ] 2.1 `test(status)`: write failing tests for `RemoteVersionCheckSetting.read(properties, environment)` as a pure function:
  default on; `INGOT_` variable `false` turns it off, case ignored; legacy variable fallback; empty value falls through; each of
  the four precedence pairs from the spec; a property with value `no` is off; a variable with value `flase` falls through and
  produces one warning (captured appender). Verify: they fail on the missing class.
- [ ] 2.2 `feat(status)`: add `RemoteVersionCheckSetting` in `com.reposilite.status` and make `StatusFacade` take it as a
  defaulted `remoteVersionCheck: () -> Boolean` parameter in place of the private property reader. Verify: the tests of 3.1 pass
  and the existing `StatusFacadeTest` passes unedited.
- [ ] 2.3 `test(status)`: write failing tests with a local JDK HTTP server on port 0 that counts and records requests, `testEnv`
  false and the facade pointed at it: with the check off the status request opens no connection and reports the running version;
  with it on one request arrives, and it has no body and no header or query value naming the instance or its version; two status
  requests within the hour cause one request. Use a fake clock for the expiry, not a wait. Verify: the first two fail before the
  wiring of 3.2 is complete, the third documents the current behaviour. If the recorded request does carry an identifier, stop
  and report it before the statement is written.
- [ ] 2.4 `docs(status)`: document `INGOT_STATUS_REMOTE_VERSION_CHECK` and its fallback, the precedence and the value rules in
  `docs/gitbook/reference/environment-variables.md` (a new variable table next to the system property table at line 288, and the
  property row pointing to it), and rewrite the sentence at `docs/gitbook/how-to/monitor-status-and-failures.md:65` to name the
  variable first and the property second.

## 3. Outbound statement

- [ ] 3.1 `docs(status)`: re-run the search for outbound connections on the commit being worked on (`HttpClient`,
  `fetchContent`, `RemoteClient`, `InitialDirContext`, `S3Client`, `Nats.connect`, JDBC drivers, external URLs in
  `reposilite-frontend/src` and `reposilite-plugins/*/src/main`) and compare the result with the table in the design. Verify:
  every hit is a row of the table or is explained, and the page is written from this result, not from the design.
- [ ] 3.2 `docs(status)`: add `docs/gitbook/reference/outbound-connections.md` with the table, the statement of what does not
  exist, the allow list hosts and the facts listed in the design, and list it in `docs/gitbook/SUMMARY.md` under Reference. Link
  it from `docs/gitbook/reference/environment-variables.md` and from `docs/gitbook/how-to/monitor-status-and-failures.md`.
  Verify: every relative link resolves, and each code reference named on the page still exists.

## 4. Pull request

- [ ] 4.1 Open the pull request with the title `feat(status): add environment switch for the remote version check`. The
  description follows `.github/PULL_REQUEST_TEMPLATE.md`, carries no session link, states that the change is opt-in with no
  breaking change, and names the risk that a later outbound call must update the page.

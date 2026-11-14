# Tasks

Every commit of a code pull request follows its own Conventional Commits type. The tests of a group are committed first, as
`test(identity)`, and seen to fail before the production commit, which is `feat(identity)`. Every test follows F.I.R.S.T.:
no `Thread.sleep`, the clock is an injected `java.time.Clock`, a temporary directory per test, no shared mutable static state,
and waits through latches only. The proposal pull request carries only this change directory and leaves every task unchecked.

Five code pull requests come from this change, in the order of section 7. Pull requests 3 and 4 depend on pull request 1.
Pull request 2 and pull request 5 are independent of the others. Each code pull request is opened from the remote main branch
that contains the merged proposal, and contains no file under `openspec/`.

## 1. Proposal and verification

- [ ] 1.1 `docs(openspec): propose add-login-protection`: this change only, in its own proposal pull request from the remote main
  branch. Verify: `openspec validate add-login-protection --strict` passes and the dash grep prints nothing.
- [ ] 1.2 Record the behaviour of Maven 3.9 and Gradle 8 on a `429` with `Retry-After` from a remote repository, with a Basic
  credential, and record the result in the description of pull request 1 (design open question 2). Verify: both clients are run
  against a local stub server in a temporary directory, and the exit code and the retry count are recorded.
- [ ] 1.3 Confirm with the maintainer the default of design open questions 1 to 9 before pull request 1 is opened. Verify: the
  answer, or the silence that the defaults stand, is noted in the description of pull request 1.

## 2. Pull request 1: per-account throttling

Title: `feat(identity): throttle sign-in attempts per account across every credential path`

- [ ] 2.1 `test(identity)`: failing unit tests for `LoginDelayPolicy`, one behaviour each, on an injected clock: the failures
  below the threshold are not delayed; failure `n` sets `min(cap, 2^(n-threshold))` seconds; a delay ends when the clock reaches
  it; a quiet period resets the count; a success clears the state. Verify: the tests fail because the class does not exist.
- [ ] 2.2 `test(identity)`: failing tests for the known-address store on an in-memory database, in a temporary directory: an
  address is known within `knownAddressDays` and unknown after it; a successful login records the address for its principal;
  a token identifier is recorded for a name that is not a principal. Verify: the tests fail before 2.4.
- [ ] 2.3 `test(identity)`: failing tests across two `LoginAttemptLimiter` instances on one database (the existing pattern of
  `LoginAttemptLimiterTest`): a failure on one counts on the other; the account key delays a request from a new address and
  does not delay a request from a known address; a `mfa|account|` failure does not increase `account|`. Verify: the tests
  fail before 2.4.
- [ ] 2.4 `test(identity)`: failing tests for the paths: a Basic request for a delayed name gets `429` with `Retry-After` and
  its secret is not verified (a stub authenticator counts its calls); the same for a Bearer secret; the dashboard login gets
  the same `429`; a delayed unknown name and an existing name get the same answer; the mirror identity is neither counted nor
  delayed. Verify: the tests fail on main.
- [ ] 2.5 `test(identity)`: failing test for the dummy hash: the first unknown-user verification computes exactly one hash, and
  no hash is computed lazily during it. Verify: the test fails on the lazy `unusedHash` of `LocalLoginProvider.kt`.
- [ ] 2.6 `feat(identity)`: implement `LoginDelayPolicy`, the `login_known_address` table in `IdentityTables.kt` and
  `LoginStore`, the account key in `LoginService` and in the authenticate path of `AuthenticationFacade`, the `429` with
  `Retry-After` for Basic and Bearer, and the eager dummy hash. Verify: 2.1 to 2.5 pass, and
  `:reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*'` stays green.
- [ ] 2.7 `feat(identity)`: move the per-address counter of `authentication.bruteForceProtection` from the Guava cache of
  `AuthenticationFacade` into `login_attempt` under `address|<ip>`, keep its default off, use the injected clock, and add the
  atomic increment after the fifth conflicting write of `LoginAttemptLimiter.recordFailure`. Verify: a test for a counter that
  conflicts five times (2.3 style) and the existing `AuthenticationFacadeTest` pass.
- [ ] 2.8 `test(identity)`: an integration test (Testcontainers only here, with the existing harness) that two replicas on one
  PostgreSQL or MariaDB database share the delay. Verify: `heavy` with `HEAVY_SLOTS=8 HEAVY_MIN_MEM_MB=8192` and
  `:reposilite-backend:integration --tests '...LoginThrottle*'` pass.
- [ ] 2.9 `docs(identity)`: describe the account throttle, the known-address window, the 429 answer and the legacy switch in the
  matching page under `docs/gitbook/`. Verify: the page names every new key with its default.
- [ ] 2.10 Container job: the upstream compose file starts with only the image line swapped, adopts an existing `/app/data`, and
  the upgrade rehearsal passes. Verify: the "Container images" CI job is green on the pull request.

## 3. Pull request 2: breached-password check and length policy

Title: `feat(identity): check new passwords against breached password lists`

- [ ] 3.1 `test(identity)`: failing tests for the length policy: the default minimum is 12; 11 characters are refused with the
  message naming 12; `minLength` below 8 or above 256 is a settings violation on the key; the maximum is 256 code points.
  Verify: the tests fail on main.
- [ ] 3.2 `test(identity)`: failing tests for the breached-password check with a fake range client behind an interface: the
  request carries the five character prefix only, the suffix match refuses the password, `OFF` makes no call, the `OFFLINE`
  file in a temporary directory refuses a listed hash, a missing offline file fails the startup with the key named, and a
  timeout with `failClosed=false` writes `PASSWORD_BREACH_CHECK_UNAVAILABLE` and allows the change. No test touches the network.
  Verify: the tests fail before 3.3.
- [ ] 3.3 `test(identity)`: failing test that the check does not run at login (a fake client counts zero calls on a sign-in).
  Verify: the test fails before 3.4 because the check is not wired yet, and passes after 3.4.
- [ ] 3.4 `feat(identity)`: implement the `passwordPolicy` settings with validation, the `BreachCheck` interface with `OFF`,
  `RANGE` (the production client, with the 3 second timeouts and no identity headers) and `OFFLINE` (a sorted array loaded at
  startup), and the calls in the set, change, reset and setup paths of `LoginService`. Verify: 3.1 to 3.3 pass.
- [ ] 3.5 `docs(identity)`: document the modes, the range URL, the offline file format and the fail-open default in
  `docs/gitbook/`. Verify: the page states that the range service is an external call and that the default is `OFF`.
- [ ] 3.6 Container job: as 2.10.

## 4. Pull request 3: throttled sign-ins in the audit trail, counters and plugin events

Title: `feat(identity): record throttled sign-ins in the audit trail and plugin events`

Depends on pull request 1.

- [ ] 4.1 `test(identity)`: failing tests: a delayed request writes exactly one `LOGIN_THROTTLED` with the path and no
  `LOGIN_FAILED`; the enum values of `AuditAction` keep their serialised values (a golden test over the existing names); the
  counters increase per path and outcome and carry no name; `LoginThrottledEvent` is published after the state is stored and
  a throwing listener does not change the answer; `NewSignInEvent` is published for the first sign-in and for an unknown
  address, and not for a known one. Verify: the tests fail before 4.2.
- [ ] 4.2 `feat(identity)`: add `LOGIN_THROTTLED` to `AuditAction`, the counters, the two plugin events in
  `com.reposilite.plugin.api` next to `SessionFamilyRevokedEvent`, and the publication. Verify: 4.1 passes and the audit JSON
  sink test (`add-audit-json-sink` if merged, otherwise the existing audit tests) still passes.
- [ ] 4.3 `docs(identity)`: document the new audit action, the counters on the status endpoint and the two events for plugin
  authors in `docs/gitbook/`. Verify: the page lists the fields of each event and states that no secret is carried.

## 5. Pull request 4: login challenge extension point

Title: `feat(identity): add a login challenge extension point for plugins`

Depends on pull request 1.

- [ ] 5.1 `test(identity)`: failing tests: with no provider registered, a delayed dashboard login is answered `429` with no
  challenge; with a provider registered, it is asked only for a delayed dashboard login and never for Basic or Bearer; a
  verified challenge lets one password check through, and a wrong password continues the delay; a plugin compiled against the
  previous plugin API still loads (the default methods). Verify: the tests fail before 5.2.
- [ ] 5.2 `feat(identity)`: add `LoginChallengeProvider` to the plugin API with default methods, the selection in `PluginLoader`
  (at most one, the first by load order, with a warning when several are registered), and the branch in `LoginService.login`.
  Verify: 5.1 passes and `gate-plugin-abi` reports only additive changes.
- [ ] 5.3 `docs(identity)`: document the interface for plugin authors in `docs/gitbook/`, with the statement that Ingot ships no
  provider and calls no third-party service. Verify: the page says so in its first paragraph.

## 6. Pull request 5: optional idle timeout

Title: `feat(identity): add an optional idle timeout for browser sessions`

- [ ] 6.1 `test(identity)`: failing tests, written first and passing on main for the pinned rules: the cookie flags of
  `SessionCookies` (`HttpOnly`, `SameSite=Lax`, `Secure` on a secure request, refresh path limited, CSRF not `HttpOnly`); reuse of
  a spent refresh token revokes the family and audits `SESSION_REFRESH_REUSE_DETECTED`; a password change revokes every family;
  an anonymous request sets no cookie. Verify: these tests pass on main before any change, so they are a characterisation.
- [ ] 6.2 `test(identity)`: failing tests for the idle timeout on an injected clock: the default leaves the 168 hour idle
  limit; a 30 minute timeout refuses a refresh at minute 31; the value 2 is a violation; the value above the refresh lifetime
  is a violation; the absolute end still wins. Verify: the tests fail before 6.3.
- [ ] 6.3 `feat(identity)`: add `login.idleTimeoutMinutes` to `LoginSettings` with the validation, and the expiry rule in
  `SessionService.issue`. Verify: 6.1 and 6.2 pass and `SessionServiceTest` is unchanged and green.
- [ ] 6.4 `docs(identity)`: document the timeout and the recommended value of 30 for instances with administrators in
  `docs/gitbook/`, as an opt-in. Verify: the default stated on the page is `0`.

## 7. Open the pull requests

- [ ] 7.1 Open the pull request for section 2 against `main` with the title `feat(identity): throttle sign-in attempts per account
  across every credential path`, with the sections of `.github/PULL_REQUEST_TEMPLATE.md` filled in. Verify: `gh pr view` shows the
  title and the template sections, and the `Container images` check runs.
- [ ] 7.2 Open the pull request for section 3 against `main` with the title `feat(identity): check new passwords against breached
  password lists`. Verify: `gh pr view` shows the title.
- [ ] 7.3 Open the pull request for section 4 after pull request 1 is merged, with the title `feat(identity): record throttled
  sign-ins in the audit trail and plugin events`. Verify: `gh pr view` shows the title and the base is `main`.
- [ ] 7.4 Open the pull request for section 5 after pull request 1 is merged, with the title `feat(identity): add a login challenge
  extension point for plugins`. Verify: `gh pr view` shows the title and the base is `main`.
- [ ] 7.5 Open the pull request for section 6 with the title `feat(identity): add an optional idle timeout for browser sessions`.
  Verify: `gh pr view` shows the title, and the pull request description contains no session link or attribution line.

## 8. Follow-up changes (not part of this proposal)

- `restructure-sign-in-protection-page` (`feat(ui)`): the administration page "Sign-in protection" with the sections per address,
  per account, credential paths, password policy, notifications and session. Proposed separately.
- `fix-legacy-brute-force-ban-window` (`fix(auth)`), `fix-basic-token-timing` (`fix(auth)`) and `fix-setup-code-oracle`
  (`fix(identity)`): the defects named in the proposal. Each is proposed separately, and the first of them can merge before
  pull request 1.
- `docs(openspec): archive add-login-protection`: only when the maintainer asks for the follow-up that ticks these tasks.

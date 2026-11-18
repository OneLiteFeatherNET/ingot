# Design

## Current state (cited, origin/main 40788b1d7)

Two limiters run side by side, and they do not cover the same paths.

- **Dashboard password login.** `net/onelitefeather/ingot/identity/login/LoginService.kt:91-92` keys every attempt by
  `userKey(name, address)` and `addressKey(address)`. `LoginAttemptLimiter.kt:41-49` answers `retryAfter`, and the
  endpoint answers `429` with `Retry-After` (`login/api/AuthLoginEndpoints.kt:102`). Defaults from
  `login/LoginSettings.kt:25-30`: five failures, lockout 15 minutes, doubling up to 24 hours. Counters live in the
  `login_attempt` table (`storage/LoginTables.kt:68`), so replicas share them. The locks are hard: a locked address
  refuses every user name from that address.
- **Basic and Bearer tokens, LDAP over Basic, the console, the loopback mirror.** They all reach
  `AuthenticationFacade.authenticateByCredentials` (`com/reposilite/auth/AuthenticationFacade.kt:112`), through
  `ReposiliteRouting.kt:107` and `:116`, `ConsoleAuthenticator.kt:41` and `RepositoryLoopbackClient.kt:66`. Their only
  limit is the per-address switch `authentication.bruteForceProtection` (`auth/application/AuthenticationSettings.kt:29-41`),
  **off by default**, counted in a Guava cache in the memory of one instance (`AuthenticationFacade.kt:73-79`).
- **Client address.** `ctx.ip()` is set in `JavalinConfiguration.kt:112` from `TrustedProxies.resolve`. With no trusted
  proxy configured, `TrustedProxies.kt:35-36` returns the raw `X-Forwarded-For` value, so a client can choose its address.
  `startupWarning` (`JavalinConfiguration.kt:111`) only logs this.
- **Enumeration.** The dashboard path is uniform: `LocalLoginProvider.kt:43` verifies a password against a dummy hash for
  an unknown user, and the answer is "Invalid username or password" in both cases (`AuthLoginEndpoints.kt:100`).
  The Basic path is not (see D3).
- **Passwords.** The minimum is 12 and the maximum 256 characters, both in `LoginService.kt:263-264`
  (`MIN_PASSWORD_LENGTH`, `MAX_PASSWORD_LENGTH`), checked by `passwordProblem` at `LoginService.kt:172-173` with
  `password.length`, which counts UTF-16 code units. No composition rule. No breach check exists (no reference to a
  breached-password list or service in the backend). Line numbers re-checked on origin/main c0447e4c3; the earlier
  `LoginService.kt:249-250` reference is stale.
- **Audit.** `LOGIN_FAILED` and `LOGIN_SUCCEEDED` exist (`com/reposilite/audit/api/AuditAction.kt:31,44`). A throttled
  request is audited as `LOGIN_FAILED` (`LoginService.kt:259`, `recordRejected`). `SESSION_REFRESH_REUSE_DETECTED` exists
  (`:46`). No mail component exists in the backend build.
- **Sessions.** Cookies are `HttpOnly` for the session and the refresh token, the refresh cookie is limited to
  `/api/auth`, the CSRF cookie is readable by the page, all with `SameSite=Lax` and `Secure` when the request is secure
  (`identity/session/SessionCookies.kt:36-39,71-75`). The session token lives 15 minutes (`LoginSettings.kt:17`). The
  refresh token rotates in a family; reuse revokes the family (`session/SessionService.kt:82-100`). Logout revokes the
  family. A password change or reset revokes every session of the user (`login/LoginFacade.kt:69-71,79-81`). The refresh
  lifetime of 168 hours is the idle time (`LoginSettings.kt:19`), and 30 days is the absolute time (`:21`).
- **The second factor (merged, not PR #231).** PR #231 is closed without a merge. The second factor is on main through
  #320 (users manage their factors) and #321 (administrators reset them). Its keys `mfa|user|<name>|<address>`,
  `mfa|address|<address>` and `mfa|account|<name>` are defined in `LoginAttemptLimiter.kt:102-110` (digested like the
  other keys, the name lower case) and used by `mfa/SecondFactorService.kt:393`. Checked on origin/main c0447e4c3: the
  `mfa|` namespace is the one this change assumes, and no key of this change uses the `mfa|` prefix. The `account|` key
  does not collide with any existing key.

## Decisions

### D1. Per-account throttling: a progressive delay, not a lock, and a known-address exemption

A new key `account|<name, lower case>`, digested like the existing keys, counts failed credentials for a name from any
address. Its rule is a pure function of the state and the injected clock, in a new `LoginDelayPolicy`:

- Failures count while they are less than `quietMinutes` (default 60) apart.
- Failure number `n` at or above `threshold` (default 10) sets `lockedUntil = now + min(maxDelay, 2^(n - threshold)
  seconds)`. The first delay is one second, the cap is `maxDelaySeconds` (default 300).
- A success for the name clears the state (`limiter.forget`), as the existing user key does.
- The delay is answered with `429` and `Retry-After`. The state stores the delay in the `lockedUntil` column of the existing
  table, so no schema change is needed for the state itself.

**The exemption.** A delay that applies to every address is a denial of service against the owner: an attacker sends ten
wrong passwords for `alice` and keeps the delay running. So the account key delays only requests from an address that
the name has never signed in from. Addresses that signed in during the last `knownAddressDays` (default 30) are checked
against the per-(name, address) key only. Stuffing from a botnet is always from new addresses, so it is slowed; the owner
at home is not delayed by the global key.

The known addresses live in a new table `login_known_address` (`principal_id`, `address_hash`, `first_seen_at`,
`last_success_at`, primary key `(principal_id, address_hash)`), written on every successful login and every successful
token authentication while the switch is on. While it is off the table is neither read nor written. Addresses are stored as SHA-256 digests, as the existing keys are. For a Basic or Bearer name that is
not a principal, the table is keyed by the token identifier.

**Why not a hard lock.** OWASP's guidance is that a lock turns a password guess into a lock of the owner. Here the
progressive delay is bounded (five minutes), it ends without action, and the exemption keeps the owner out of it.

**Switch and default.** The whole rule is off by default. It is turned on by `INGOT_LOGIN_ACCOUNT_THROTTLE=true`, with
`REPOSILITE_LOGIN_ACCOUNT_THROTTLE` as the fallback (D10). While it is off: no `account|` key is read or written, no delay
is applied, no `429` is sent for it, and `login_known_address` is neither read nor written. Known addresses are recorded
only while the switch is on, so the table starts empty when it is turned on (the upgrade caveat in D10 and in the proposal).

### D2. Coverage of every credential path

| Path | Entry point | Keys checked and counted | Answer when delayed |
| --- | --- | --- | --- |
| Dashboard password (local, LDAP, OIDC is not a password path) | `LoginService.login` | `user\|name\|address`, `address`, `account\|name` (new) | `429`, `Retry-After` |
| Basic (Maven, Gradle, CI) | `AuthenticationFacade.authenticateByCredentials` via `ReposiliteRouting` | `account\|name` (new), `address\|ip` (legacy switch, now in the shared store) | `429`, `Retry-After` |
| Bearer `ingot_<id>_...` | same, name taken from the token id | `account\|name` (new), `address\|ip` | `429`, `Retry-After` |
| LDAP over Basic | `LdapAuthenticator` through the same chain | as Basic | as Basic |
| Console (token or session) | `ConsoleAuthenticator` | as Basic, the session token is a JWT and is not counted | as Basic |
| Loopback mirror | `RepositoryLoopbackClient` | not counted: the identity is the instance's own mirror identity and comes from configuration | none |
| MFA codes and recovery codes (#231) | `mfa` verification | `mfa\|user`, `mfa\|address`, `mfa\|account` only | `429` |
| Setup code | `LoginService.completeSetup` | `address` (existing) | `429` |

The MFA keys stay in their own namespace. A guess of codes must not lock the password path of the same name, and a
guess of passwords must not lock the second factor. Both are delays, so neither is a lock; the separation keeps one attack
from reaching the other counter.

A throttled request never reaches the password or token check, so it costs no hash. A request that is refused by the
delay is audited as `LOGIN_THROTTLED` (D5) and does not increase the failure count. With the switch off, every row of
this table keeps its present check and answer: the dashboard keeps its own limit of `LoginService.kt:91-99`, and Basic and
Bearer keep `401` unless `authentication.bruteForceProtection` is on.

**Mirror identity.** The loopback credentials are neither counted nor delayed. A misconfigured mirror would otherwise delay
its own group, and its identity is the instance's own, taken from configuration, so an outside attacker cannot use it.

### D3. Uniform answers and timing

- **Answers.** A delayed name gets the same `429` whether or not the name exists, because every failure is recorded under
  the name as typed, existing or not (`LoginService.kt:97-105` already does this for the address and user keys). The
  dashboard answers `Invalid username or password` for an unknown user, a wrong password and a user without a password
  (`AuthLoginEndpoints.kt:100`), and this change keeps that.
- **Basic and Bearer timing.** `BasicAuthenticator.kt:33-37` returns before the secret check for an unknown, disabled or
  expired token, and `AccessTokenFacade.secretMatches` (`token/AccessTokenFacade.kt:413-416`) runs only for an existing
  token. The time of a reply therefore shows whether a name exists. This is a defect with its own change
  (`fix-basic-token-timing`). This change does not depend on it, and the throttle does not add a shortcut.
- **Dashboard timing.** `LocalLoginProvider.kt:53-61` makes `unusedHash` lazy. The first unknown-user request pays one extra
  hash. The fix is an eager dummy hash created in the constructor. Included in the first code pull request. It is the one
  change that is not behind a switch: it changes the timing of one answer and no answer content, and gating a timing fix
  would keep the difference on by default. Open question 9 asks the maintainer to confirm this.
- **LDAP.** `LdapAuthenticator.kt:84` answers "User not found" before any bind, while a wrong password needs a bind. The
  time difference is network-bound and cannot be padded without sleeping on the request thread. Default: not changed,
  recorded as a residual risk; the operator can turn LDAP off or use the local directory for admins.

### D4. Password policy

- **Length.** The default minimum is 12, the value Reposilite and Ingot enforce today (`LoginService.kt:263`). It is
  configurable with `login.passwordPolicy.minLength`, validated to be between 8 and 256, and an unset value means 12. The
  OWASP Authentication Cheat Sheet asks for at least 8, and recommends 15 when the password is the only factor; NIST SP
  800-63B-4 sets the same minimums. The docs recommend 12 to 15 for new installations. The default stays 12, because a
  higher default would refuse what the default install accepts today. The maximum is 256, as today.
- **Length measure.** Measured as today, with `password.length` in UTF-16 code units (`LoginService.kt:172`). The earlier
  draft counted code points. That changes which passwords a default install accepts in both directions (a short run of
  characters outside the Basic Multilingual Plane is refused, and a long one is accepted), so it is dropped from this
  change. Counting code points is a separate, later change.
- **No composition rules.** Nothing requires symbols, digits or mixed case. Nothing expires. Existing passwords are not
  checked again. No stored password is affected.
- **Breached passwords.** `login.passwordPolicy.breachCheck.mode` is one of `OFF` (default), `RANGE`, `OFFLINE`.
  - `RANGE` computes the SHA-1 of the password, sends the first five hex characters to
    `https://api.pwnedpasswords.com/range/{prefix}` with `Add-Padding: true`, and compares the suffixes locally. Only the
    prefix leaves the instance (k-anonymity). The URL is a setting, so a mirror of the API can be used.
  - `OFFLINE` reads a file the operator provides at `login.passwordPolicy.breachCheck.file`, one SHA-1 hex per line, sorted or
    not. The file is loaded once at startup into a sorted array; a missing or unreadable file fails startup with a clear
    message when the mode is `OFFLINE`. No list is bundled.
  - When the service cannot be reached, the change is refused with `503` and a retry hint only if the mode is `RANGE` and
    `breachCheck.failClosed` is `true`. The default is fail-open: a password change is allowed and an audit event
    `PASSWORD_BREACH_CHECK_UNAVAILABLE` is written. The reason is that an outage of an external service must not block a
    user who is locked out and must reset.
  - The check runs in `setPassword` paths that take a new password: change, reset, setup. It never runs at login, so no
    existing user is locked out by it.
  - The mode is opt-in because `RANGE` calls an external service. Default: `OFF`.
- **Timing of the check.** It runs before the hash is written and outside the transaction of the credential row.

### D5. Sign-in events and counters

- A new audit action `LOGIN_THROTTLED` marks a request answered with a delay, with the name and the address. Dashboard
  and Basic requests both use it. It is written only while the account throttle is on, because a delay exists only then.
  `LOGIN_FAILED` keeps its meaning for the wrong credential itself. The enum constant is additive; the audit JSON sink
  writes its name as text.
- A counter per path and outcome (`throttled`, `failed`, `succeeded`) is kept in memory per instance and exposed through the
  status facility, as the Maven operation counters are (`com/reposilite/maven/MavenOperationCounters.kt`). Counters are
  per instance by design: a cluster total is the sum that a dashboard computes. While the switch is off the counters are
  not kept and the status response is unchanged, so no new field appears. Verify the status facility before the code
  pull request.
- Plugin events in `com.reposilite.plugin.api`, following the pattern of `SessionFamilyRevokedEvent`:
  - `LoginThrottledEvent(name, address, path, retryAfterSeconds)` when a delay answers a request. Published only while the
    switch is on, as the delay is.
  - `NewSignInEvent(principal, address, userAgentHash, path)` when a successful login comes from an address outside the known
    list of D1. Published only while the switch is on: while it is off, nothing records the known addresses, so every
    sign-in would look new and every plugin would receive an event by default. A user notification is built by a plugin
    from this event. Ingot does not send mail.
- Events are published after the state is stored, so a listener that fails does not change the answer.
- Event payloads do not contain the password, the token secret or the raw user agent (the hash only).

### D6. Login challenge extension point (deferred)

Moved to `add-login-challenge-extension` by maintainer decision; not part of this change.

### D7. Sessions: idle timeout (opt-in) and the verified rules

- `login.idleTimeoutMinutes`, default `0` (off). When set, a refresh token expires at the earlier of its refresh lifetime,
  `now + idleTimeoutMinutes` after its last use, and the family's absolute end. Validation: `0` or between 5 and the refresh
  lifetime in minutes. The recommended value for instances with administrators is 30, documented in
  `docs/gitbook`, not set by default.
- **Verified, not changed:** the cookie flags of "Current state", the refresh cookie path, rotation and reuse detection,
  logout revocation, revocation on password change and reset (the change revokes the current session too, which is kept:
  the user signs in again after a change), no pre-login session, so no fixation.
- A tests-only item checks each of these rules against the current code before any change, so that a later refactor
  cannot silently weaken them.

### D8. Administrator enforcement

Owned by `add-multi-factor-authentication` D10: `login.mfa.requiredForAdmins`, default false, grace period 14 days. This
change adds no switch. Two gaps remain and are recorded, not fixed here:

- Basic and Bearer tokens of administrators stay single-factor (MFA D9). Protection for them is the account throttle (D1)
  and the token strength, which is random 256-bit.
- The default of `requiredForAdmins` stays false. Turning it on is a documented step after the grace period, not a default.

### D9. Cluster: counters are shared, and the legacy counter moves into the shared store

- The account throttle's counters live in `login_attempt` (`LoginAttemptLimiter`), so replicas count together. The `login_known_address` table is
  shared too. No Redis, no NATS for counting: NATS remains an optional fan-out for events only, as the HA design says.
- The legacy per-address switch (`authentication.bruteForceProtection`) keeps its keys and its default (off), and its
  counting moves from the in-memory cache to `basic-address|<ip>` in `login_attempt` when the switch is on. The memory cache
  is removed in the same code pull request. The key is not `address|<ip>`: the dashboard's per-address counter already
  digests exactly that string (`LoginAttemptLimiter.addressKey`, `LoginService.kt:97`), so sharing it would let Basic
  failures lock dashboard logins. The answer when this switch refuses a request stays `401` (`AuthenticationFacade.kt:152-154`).
  With the switch off nothing is counted, as today.
- **Coupling.** The legacy counter is coupled only to `authentication.bruteForceProtection.enabled`. Turning on
  `INGOT_LOGIN_ACCOUNT_THROTTLE` does not move it into the shared store, and turning on the legacy switch does not enable the account
  throttle. The two switches can be set independently, and the tests of task 2.7 cover each combination.
- **Contention.** `LoginAttemptLimiter.recordFailure` tries five compare-and-set writes and then drops the failure
  (`LoginAttemptLimiter.kt:62-73`). Under a burst from one address, a failure can be lost. The code pull request replaces
  the drop with an atomic increment (`failures = failures + 1` in one statement) after the fifth conflict, so a failure is
  never lost.
- **Cost.** Basic authentication of a valid token writes one row per request only for the known-address update, which is
  throttled to one write per address per hour. Failed attempts write one row per key, as the dashboard already does.
- **Ban window.** The legacy per-address ban is wrong today (`fix-legacy-brute-force-ban-window`, see below). This change
  does not copy the bug to the shared store: the shared store counts from the moment the limit is reached.
- **Time.** Every rule takes the injected `Clock` (`LoginAttemptLimiter` does; the legacy facade does not, which the fix
  change corrects).

### D10. Settings, migration and environment

- New settings live in the existing blocks: `login.passwordPolicy`, `login.accountThrottle`, `login.idleTimeoutMinutes`,
  and `login.knownAddressDays`. The existing keys `login.maxFailedAttempts`, `login.lockoutMinutes` and
  `authentication.bruteForceProtection.*` keep their names and defaults.
- Every new key has a default equal to today's behaviour. Nothing is on by default. The account throttle has one switch,
  the environment variable `INGOT_LOGIN_ACCOUNT_THROTTLE` (`true` turns it on; unset, blank or `false` leaves it off), with
  `REPOSILITE_LOGIN_ACCOUNT_THROTTLE` as the fallback, in the style of `INGOT_AUDIT_SINK_STDOUT`
  (`audit/sink/AuditSinkSettings.kt:28`). Its thresholds are in the settings block `login.accountThrottle` with the keys
  `threshold` (10), `quietMinutes` (60), `maxDelaySeconds` (300) and `knownAddressDays` (30); they have no effect while the
  switch is off. The switch is a deployment decision, not an admin page toggle, so it is not in the shared settings.
- The audit action is additive code, not a default; it is written only by a delayed request.
- **Upgrade caveat for the switch.** `login_known_address` is empty when the switch is turned on. An owner who has never
  signed in from an address counts that address as new, which matters only while the name is already delayed. Warm-up:
  the owner's next successful sign-in from each address records it. The operator's step is: turn the switch on, then let
  the owners sign in. A grace period is not built in, because it would need the known-address table written while the
  switch is off, and the default must not write it (Alternatives).
- Settings are read through `Reference` on every use, so changes apply without a restart, as the login settings are today.

### D11. The dashboard and the page (follow-up)

The page "Sign-in protection" (`reposilite-frontend/src/navigation/registry.js:201`, `components/access/SecurityPanel.vue`)
is restructured in the follow-up change. This change provides the settings with `@Doc` descriptions, which the settings
schema shows. The field descriptions must say what a value does to a person, for example "Delays a password guess for one
name from addresses it has never signed in from. The delay ends by itself, and a success clears it."

## Data model

| Table | Change | Notes |
| --- | --- | --- |
| `login_attempt` | none in schema | new keys `account\|<name>` (switch on), `basic-address\|<ip>` (legacy switch on), `mfa\|...` (merged, #320, #321) |
| `login_known_address` | new | `principal_id` (or token id), `address_hash` (SHA-256 hex), `first_seen_at`, `last_success_at`; primary key on the first two. Created always, written only while the account throttle is on |

The new table is created by `SchemaUtils.createMissingTablesAndColumns` in `LoginStore`, as `login_attempt` was. It is
added to `IdentityTables.kt`. Retention: rows whose `last_success_at` is older than `knownAddressDays` are deleted by the
existing cleanup job.

## Cluster behaviour

- Counters and delays are read and written in the database, so an attempt on replica A is counted for replica B at once.
- A delay that starts on replica A is seen by replica B at its next check. There is no cache of a delay, so no replica
  keeps a stale one.
- The known-address table is written on success only, so two replicas writing one row is a last-write-wins on
  `last_success_at`, which is correct for the purpose.
- The setup code of `SetupCode.kt` lives in memory per instance. This is the defect `fix-setup-code-oracle` describes.

## Alternatives considered

- **Hard lockout of accounts (the current model for addresses).** Rejected for accounts: a remote attacker could lock every
  owner. Kept for addresses as it is.
- **CAPTCHA by default.** Rejected: it needs a third party, which breaks the drop-in promise that nothing new is called by
  default, and it blocks Maven clients, which cannot answer one.
- **Sleeping on the request thread (a true progressive delay).** Rejected: thread pool exhaustion under attack. The
  `Retry-After` answer gives the same effect for compliant clients without holding a thread.
- **Redis for counters.** Rejected by the HA design (database for locks and counters).
- **Global account key without the known-address exemption.** Rejected because it turns a password guess into a denial of
  service against the owner.
- **Raising the minimum length to 15 by default.** Rejected for this change: it changes what existing users can set at their
  next change, and the default is a documented recommendation instead. Operators set `minLength: 15`.
- **Account throttle on by default.** Rejected: it answers a correct credential from a new address with `429`, and its
  known-address table starts empty on upgrade. An installation that upgrades must answer as Reposilite does until the
  operator opts in.
- **Grace period (record known addresses while the switch is off, enforce later).** Rejected: it writes a table on every
  login of an installation that did not ask for it, and it needs a third mode between off and on. The smallest option is
  the one chosen: the table is warmed by the sign-ins that follow the switch.
- **Code point length.** Rejected for this change: it changes what a default install accepts. It is a separate change.
- **Composition rules.** Rejected: NIST SP 800-63B-4 and the OWASP cheat sheet advise against them.

## Risks

- A bad threshold can delay a legitimate CI job. The defaults (10 failures, 1 second doubling, 300 seconds cap, exemption for
  known addresses) and the `429` answer with `Retry-After` make the delay visible and short.
- The known-address table grows with the number of addresses per name. It is bounded by `knownAddressDays` and by the
  number of principals times the addresses they use.
- A proxy that is not trusted makes every client look like one address. The account key does not depend on the address, so
  it still works; the address keys do not. The startup warning remains, and the docs recommend `INGOT_TRUSTED_PROXIES`.

## Open questions

The defaults are the design. Each question below has a default, which the code pull requests implement unless the
maintainer changes it before they are opened. Every default keeps the answers of Reposilite today, so an upgraded
installation changes nothing until an operator opts in.

1. **Known addresses for Basic and Bearer.** Default: a token counts as known from its addresses of the last 30 days,
   not from a separate enrolment. Recorded only while the account throttle is on.
2. **Maven and Gradle behaviour on `429`.** Not verified in this proposal. Default: the server answers `429` with
   `Retry-After` regardless, because it is the standard answer, and this applies only while the account throttle is on.
   Task 1.2 records what Maven 3.9 and Gradle 9 do before the first code pull request is merged.
3. **Legacy per-address switch default.** Default: off, as today. Turning it on is a documented choice, because it can ban
   an office behind one address. Its counter is shared across replicas once `authentication.bruteForceProtection` is on (D9), whatever the account throttle switch is set to.
4. **Minimum password length.** Default: 12 in code (`LoginService.kt:263`), the value enforced today, measured as today.
   The docs recommend 12 to 15. Raising the default later needs its own change.
5. **Breached-password fail mode.** Default: the check is off (mode `OFF`), so there is no outage to fail on. When an
   operator sets mode `RANGE`, the default for an outage is fail-open with an audit event (D4).
6. **Idle timeout default.** Default: off (`0`), as today. The recommended value is 30 for instances with administrators,
   set by the operator.
7. **Admin machine tokens and MFA.** Default: unchanged, single factor (MFA D9). This is a deliberate residual risk and is
   the first thing to revisit after `add-multi-factor-authentication` is merged.
8. **Standards mapping.** ASVS 4.0 V2 and V3 correspond to ASVS 5.0 V6 and V7. The OWASP and NIST minimums are cited from
   the versions known at the time of writing; verify the current text before the code pull request for D4.
9. **Dashboard dummy hash timing.** Decided 2026-11-18: the eager dummy hash (D3) is kept on and ungated, with no switch, because it
    changes the timing of one answer and no content.

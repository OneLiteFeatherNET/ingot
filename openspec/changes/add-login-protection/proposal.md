# Proposal

## Why

Ingot accepts a password or a token from anybody who can reach the HTTP port, and the protection against guessing is
uneven. The browser login has a failure limit per user name and address, on by default. Maven, Gradle and CI clients
that authenticate with Basic or Bearer tokens have only a per-address brute force switch, `authentication.bruteForceProtection`,
which is off, counts in memory per instance, and can ban a whole office behind one address. A password list tried from
many addresses against one account is not limited at all, because every limit is keyed by the address as well.

The OWASP Authentication Cheat Sheet asks for throttling that does not lock the owner out, for the same answer whether or
not a user name exists, and for session timeouts that match the risk. The Credential Stuffing Prevention Cheat Sheet asks
for limits per account, not only per address, and for a check of new passwords against breached lists. The OWASP ASVS
4.0 chapters V2 (authentication) and V3 (session management) carry the requirements behind them; ASVS 5.0 renames
the same chapters V6 and V7. The gaps found while reading the code are listed in `design.md` and in the Related defects
section below.

This change closes the gaps in one umbrella. Every new behaviour is off until an operator switches it on, so an existing
installation answers every request as Reposilite does today. Ingot is a drop-in replacement, and the switches are the only
way its answers change.

## What Changes

The change ships as four code pull requests, each one an area that can be reviewed and merged on its own. All of them
are `feat(identity)`. Every item is off by default: with no switch set, each request is answered as it is today. The
switches and their effects are listed under Impact.

1. **Per-account throttling for every credential path.** A name that gets too many failed credentials, from any address,
   is answered with `429` and `Retry-After` for a delay that doubles with every further failure, up to five minutes. The
   delay is never a lockout: it ends by itself, a success clears it, and it applies to requests from addresses the account
   has never signed in from. The same limiter covers the dashboard password, Basic and Bearer tokens and LDAP binds. The
   legacy per-address counter, once its own switch is on, moves from memory into the `login_attempt` table so that
   replicas count together. Off by default. It is turned on with `INGOT_LOGIN_ACCOUNT_THROTTLE=true` (fallback
   `REPOSILITE_LOGIN_ACCOUNT_THROTTLE`).
2. **Password policy.** The minimum length stays at 12 characters, measured as today, and can be raised, never lowered
   below 8. The maximum stays at 256. No composition rules are added. A new breached-password check, off by default,
   queries the Pwned Passwords range API with the k-anonymity prefix, or a local list the operator provides, and runs when
   a password is set, changed, reset or created during setup. It never runs at login.
3. **Sign-in events.** While the account throttle is on, throttled requests get their own audit action, `LOGIN_THROTTLED`,
   and counters on the status endpoint. Two plugin events, `LoginThrottledEvent` and `NewSignInEvent`, let a plugin send a
   webhook or a mail; they are published only while the account throttle is on. Ingot has no mail component, so a user
   notification on a sign-in from a new address exists only as that event.
4. **Optional idle timeout.** A browser session can end after a configurable idle time, shorter than the refresh lifetime.
   It is off by default, so the existing seven-day idle time stays. The cookie flags and the rotation rules are verified
   by tests and not changed.

The login challenge extension point (a plugin offers a challenge to a delayed dashboard login) is deferred to
`add-login-challenge-extension` by maintainer decision. It is not part of this change, and no code is planned for it until a
plugin needs it.

Administrator enforcement of a second factor is not a part of this change: `add-multi-factor-authentication` (D10) already
owns the policy and its grace period, and its default stays off.

The administration page "Sign-in protection" is restructured in the follow-up change `restructure-sign-in-protection-page`
(`feat(ui)`), which is proposed separately because a change that mixes types is two changes.

### Related defects (separate fix changes, not part of this proposal)

Reading the code for this change found defects that are not feature work. Each is a `fix(...)` change with its own proposal
pull request. They are listed here so that the reviewer sees them, and they are not fixed in the code pull requests above:

- `fix-legacy-brute-force-ban-window` (`fix(auth)`): the per-address ban of `AuthenticationFacade` runs from the first
  failure instead of the moment the limit is reached, so a slow attacker gets a ban of a few seconds, and the window uses
  the system clock instead of the injected one. Cited in `design.md`, D9.
- `fix-basic-token-timing` (`fix(auth)`): Basic authentication returns early for unknown, disabled and expired token names,
  and runs the secret check only for existing active tokens. A timing difference reveals which token names exist.
- `fix-setup-code-oracle` (`fix(identity)`): after the first administrator exists, the setup code is compared without
  throttling. On an instance that did not complete the setup, the code of that instance still answers "already completed"
  for a right guess.

### Out of scope

- A blanket lockout of accounts or addresses. The existing `login.maxFailedAttempts` and `login.lockoutMinutes` keep their
  meaning and their defaults.
- Changing the password hash, its cost, or the rehashing of existing passwords.
- Re-checking existing passwords against breach lists at login.
- Mail, SMS or any notification channel in core. A plugin supplies them.
- A default CAPTCHA or proof-of-work provider, and any default call to a third-party service.
- Passwordless sign-in and passkeys, which belong to `add-multi-factor-authentication`.
- Making admin machine tokens second-factor protected. Their use in CI is unchanged; see the open questions.
- Renaming any `reposilite` runtime contract. The configuration keys stay in the `login` and `authentication` blocks.

## Capabilities

### New Capabilities

- `identity-login-throttling`: the per-account progressive delay, the credential paths it covers, the `Retry-After` answer,
  uniform answers and timing, and the per-address counter in the shared store.
- `identity-password-policy`: minimum and maximum length, the absence of composition rules, and the breached-password check
  with its modes and failure behaviour.
- `identity-sign-in-events`: the audit action of a throttled request, the counters, and the plugin events.
- `identity-session-policy`: the optional idle timeout and the verified cookie and rotation rules.

## Impact

- **Type and scope.** Every code pull request of this change ships under `feat(identity)`. The proposal pull request is
  `docs(openspec): propose add-login-protection`. Code pull request titles are listed in `tasks.md`, section 6.
- **Breaking marker.** No breaking change. Nothing an operator must edit: no configuration file, volume, manifest, plugin or
  environment variable has to change, and the new keys have defaults. The changes an operator can observe are listed in the
  next section and are not breaking.
- **Drop-in analysis** (CLAUDE.md, the three checks):
  1. An unmodified upstream compose file starts with only the image line swapped. Nothing in this change touches the image,
     the entrypoint or the volume. The new `login_known_address` table is created by the schema initialisation on every
     supported database, the same way `login_attempt` was added.
  2. An existing `/app/data` is adopted without a migration step. Existing `login_attempt` rows keep their meaning; the new
     keys and the new table start empty.
  3. `PUID`, `PGID` and `REPOSILITE_OPTS` are not read by any of the new code.
  Observable changes for existing installations: none without an opt-in. With no switch set, a wrong password or token
  gets `401` as today, and the dashboard keeps its existing limit (`login.maxFailedAttempts`, `login.lockoutMinutes`)
  unchanged. Three additions run by default, and none of them changes an answer:
  - The table `login_known_address` is created empty. It is neither read nor written while the account throttle is off.
  - The audit action `LOGIN_THROTTLED` exists in the enum. Only a delayed request writes it, and a delay exists only while
    the account throttle is on.
  - The dashboard's dummy hash for an unknown user is created at startup, not on the first unknown user. It changes the
    timing of one answer and no content. It is kept on, as the maintainer decided on 2026-11-18 (open question 9).

  What each switch changes once it is on:
  - `INGOT_LOGIN_ACCOUNT_THROTTLE=true` (fallback `REPOSILITE_LOGIN_ACCOUNT_THROTTLE`; the thresholds are in the
    `login.accountThrottle` block). From the tenth failure for one name within an hour, a Basic, Bearer or dashboard request
    for that name from an address the name has not signed in from within 30 days receives `429` with `Retry-After` instead
    of `401`. A correct credential from a known address is not delayed by the account key. The delay lasts at most five
    minutes. A Maven or Gradle client that retries a stale token sees `429` for that long at most. The plugin events,
    the `LOGIN_THROTTLED` audit entries and the status counters start with the switch.
    Upgrade caveat: `login_known_address` starts empty. Until an owner has signed in once from an address, that address
    counts as new. An owner whose name is already delayed by an attack waits out the delay from such an address. The table
    is warmed by the owner's next successful sign-in from each address, so the operator enables the switch and lets the
    owners sign in. No grace period is built in: a grace period would need writes while the switch is off, which the
    default must not do (design D1 and D9).
  - `authentication.bruteForceProtection.enabled=true` (the existing switch, still off by default): the per-address counter
    of Basic authentication is kept in the `login_attempt` table under `basic-address|<ip>` instead of memory, so replicas
    count together. The answer stays `401`.
  - `login.passwordPolicy.minLength` (unset: 12): a higher value applies to the next password a user sets. No stored
    password is checked again.
  - `login.passwordPolicy.breachCheck.mode` (unset: `OFF`): `RANGE` sends a five character hash prefix to the range URL
    when a password is set, changed, reset or created. `OFFLINE` requires its file, and the startup fails without it.
  - `login.idleTimeoutMinutes` (unset: 0): a browser session ends after this much idle time.
  - Deferred, not a switch: the `LoginChallengeProvider` is not part of this change. See `add-login-challenge-extension`.
  - The second factor keys (`mfa|...`, merged through #320 and #321) are not changed by any of these switches.

## Verification

- `openspec validate add-login-protection --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-login-protection` prints nothing.
- The default-off tests of tasks 2.4, 3.1 and 4.1 run with no switch set and show the answers of Reposilite today.
- Every code pull request runs the tests named in its section of `tasks.md` red before green, and the container job
  (upgrade rehearsal against the upstream image) stays green, because the drop-in contract is checked there.

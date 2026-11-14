# Design

This design adds second factors to the browser login that phases 2a and 3 of the identity model built. It keeps the
drop-in contract of `CLAUDE.md`: a deployment without a factor, without a policy and without a settings key behaves as
it does today. Where this document and the identity model differ, this document says so and section 15 of the model
gets the contract as implemented.

## Current state (cited)

Paths are relative to `reposilite-backend/src/main/kotlin/`.

- **Password login.** `net/onelitefeather/ingot/identity/login/LoginFacade.kt:54-55` routes `login(providerId, ...)` to
  `LoginService.login` (`LoginService.kt:89-118`). A success writes a session directly in
  `api/AuthLoginEndpoints.kt:99` (`startLoginSession`). There is no point between the password and the session where a
  second check could run.
- **Local provider.** `login/LocalLoginProvider.kt:40-51` verifies the argon2id hash of `local_credential`, and runs one
  verification for unknown users too. `login/PasswordHasher.kt` holds the parameters (OWASP minimum, 19 MiB, two passes).
- **Failure limit.** `login/LoginAttemptLimiter.kt:18-105` keeps counters in `login_attempt` keyed by SHA-256 of
  `user|<name>|<address>` and `address|<address>` (`LoginAttemptLimiter.kt:75-80`). Writes are conditional on `version`.
  The rules are pure functions in `login/LoginAttemptPolicy.kt:27-72`. Failures lock for `login.lockoutMinutes`, doubling
  up to 24 hours.
- **Bearer secrets.** `login/BearerSecret.kt:15-39` defines `ingot_<tokenId>_<random>`. Token secrets are bcrypt hashes
  (`login/OwnTokens.kt:58`). Creation is `OwnTokens.kt:71`.
- **Storage.** `login/storage/LoginTables.kt:10-77` holds `local_credential`, `refresh_token` (SHA-256 of the token,
  single use via `used_at`), `login_setup_marker` and `login_attempt`. The identity tables are created with
  `createMissingTablesAndColumns`; no versioned migration is needed for new tables.
- **Sessions.** `session/SessionService.kt:56-79` issues a short session JWT plus a refresh token in one family.
  `session/SessionSigner.kt:42-77` signs HS256 with a key shared through the database (`jwt-signing-key`). The session
  token is not looked up in the database, so a revocation takes effect at its `exp`.
- **CSRF.** `session/CsrfPolicy.kt:32` exempts `/api/auth/login` and `/api/auth/setup`, because they create the cookies.
- **Routes.** `api/AuthLoginEndpoints.kt`: providers (`:66`), login (`:89`), setup (`:139`), refresh (`:172`), logout
  (`:194`). `api/SelfServiceEndpoints.kt`: `/api/identity/me/password` (`:79`), `/api/identity/me/tokens` (`:104`, `:118`,
  `:136`, `:154`), and the administrator password reset (`:175`). `api/IdentityApiEndpoints.kt`: principals, teams,
  instance-admin grants and nodes (`:51`-`:537`).
- **Legacy session details.** `com/reposilite/auth/infrastructure/AuthenticationEndpoint.kt:37-56` serves `/api/auth/me`
  for Basic and session callers. It is not part of the login package.
- **Access tokens of Maven and Gradle.** Basic authentication through `com/reposilite/auth/AuthenticationFacade.kt`.
  Its brute-force counter is off by default (`login` and identity spec 13.8 describe the separate password limit).
- **Read-only mode.** `com/reposilite/shared/ContextDsl.kt:115-121` refuses writes while read-only, except the routes the
  read-only feature exempts. Login is not refused; setup and logout are (`AuthLoginEndpoints.kt:140`, `:195`).
- **Settings.** `login/LoginSettings.kt:17-53` is the `login` domain. Secrets are marked `@Sensitive` and masked by
  `com/reposilite/configuration/shared/SettingsSecrets.kt` (`HIDDEN_SECRET`). Encryption at rest is
  `settings/encryption/SettingsEnvelopeCodec.kt` (AES-256-GCM, bound to the domain and property path). Its keys come from
  `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE`, and the mode `INGOT_SETTINGS_ENCRYPTION` defaults to `optional`
  (`settings/encryption/SettingsKeySources.kt:57-61`, `:110-111`).
- **Public address.** `login/PublicUrl.kt` validates `login.publicUrl`. Redirect URIs and the callback shown to admins
  are built from it, or from `Host` and `X-Forwarded-Proto` when it is blank (identity model 14.2, O-23).
- **Audit.** `com/reposilite/audit/api/AuditAction.kt:23-66`. Names are stored, so new values are appended only.
- **Break-glass.** `docs/gitbook/how-to/recover-admin-access.md` uses `--token name:secret`, a Basic credential with all
  rights, and a lockout wait. Both must keep working.
- **Dashboard.** `reposilite-frontend/src/components/header/LoginModal.vue` (providers and the password form, `:35-37`,
  `:83-96`) and `src/store/client.js:71`, `:225-241` (login, setup, logout). The account page is
  `src/components/account/AccountView.vue` with `ProfileCard.vue` and `PasswordCard.vue` (`:78` calls
  `changePassword`). The dashboard part is in `add-multi-factor-dashboard`.
- **HA.** `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` puts locks and counters in the database and makes
  NATS optional fan-out only. Several replicas share one database. Every new piece of state in this change therefore lives
  in the database.

## Decisions

### D1. Three factor kinds, each opt-in per user

Authenticator codes (TOTP), passkeys (WebAuthn) and recovery codes. Nothing is enabled for anyone by default. A user
may hold several factors of each kind, and at least one recovery code set once a factor exists. Reason: the request asks
for all three, and a recovery path is what keeps a second factor from locking a person out.

### D2. Passkeys use WebAuthn4J, not Yubico's java-webauthn-server

Checked on 2026-11-14:

| | WebAuthn4J (`com.webauthn4j:webauthn4j-core`) | Yubico java-webauthn-server (`com.yubico:webauthn-server-core`) |
|---|---|---|
| Licence | Apache-2.0 (GitHub SPDX) | BSD, "Revised 2-clause" (POM and repository) |
| Maintenance | pushed 2026-11-11, 11 open issues, release 2.10.1 in Maven Central | pushed 2026-11-10, 28 open issues, release 2.10.1 |
| Runtime dependencies | not verified (the POM path did not resolve; to be checked at apply time) | Jackson, Guava, `com.upokecenter:cbor`, httpclient5, slf4j, yubico-util (all runtime) |
| Fit | attestation and assertion verification, Bouncy Castle based, same provider family as the pinned `bcprov` | attestation and assertion verification, bundles an HTTP client for metadata that Ingot does not need |

**Choice: WebAuthn4J.** The licence is Apache-2.0, the same as Ingot, so no new licence category enters the report. The
Yubico library is BSD-2-Clause, which is also permissive, but it brings Guava and an HTTP client for metadata services
that a `none` attestation never uses. The apply task must check the transitive dependencies of WebAuthn4J and drop the
library if it duplicates a second Bouncy Castle or pulls a larger set than Yubico's; the fallback is Yubico.

### D3. Authenticator codes are implemented in-house

RFC 6238 over HMAC-SHA1, six digits, a thirty second step, and a window of one step either side. About a hundred lines on
`javax.crypto.Mac`, with the RFC's test vectors as tests. Reason: no dependency, no licence question, and the algorithm is
small enough to test completely. The clock is injected (`java.time.Clock`), as the F.I.R.S.T. rules require.

Seeds are 20 random bytes. They are stored encrypted with `SettingsEnvelopeCodec` under the settings key (D10), because a
TOTP check needs the seed and cannot use a hash. Without a settings key, TOTP enrolment answers `409` with the reason
(open question 6).

### D4. Recovery codes: ten per set, SHA-256 hashed, single use

Ten codes of 80 random bits, shown once, displayed in groups of four characters. Stored as SHA-256 of the normalised code
in `mfa_recovery_code`, like refresh tokens (identity model 13.3). Reason: the input has 80 bits of entropy, so a fast hash
is enough and the code can be checked without a slow hash on every failed attempt. A code is spent by a conditional update
of `used_at` (one of ten winners across replicas). Regenerating the set invalidates all earlier codes and requires step-up
(D8).

### D5. Login challenge, shared across replicas

A password or LDAP login that needs a second factor does not create a session. It creates a row in `auth_challenge`
(kind `MFA_LOGIN`, the principal id, SHA-256 of a random id, `expires_at` five minutes ahead, `used_at`) and sets the
cookie `ingot_mfa` (HttpOnly, SameSite=Lax, Path = base path + `/api/auth`, Max-Age 300). `POST /api/auth/mfa/verify`
accepts a TOTP code, a recovery code or a passkey assertion, spends the challenge with a conditional update, and then
starts the session. A wrong code keeps the challenge until its expiry. Reason: the challenge must be single use and
visible to every replica, and the database already is the shared state (HA design).

`CsrfPolicy.kt:32` gains `/api/auth/mfa/verify` and `/api/auth/passkey/login`. Both create cookies, and the same reason
that exempts login applies.

### D6. Passkey challenges in the same table, with expiry

WebAuthn registration and authentication challenges are rows of `auth_challenge` too (kinds `WEBAUTHN_REGISTER` and
`WEBAUTHN_LOGIN`, two minutes, single use). The challenge bytes are stored with the row. The relying party identifier and
the origin are taken from D11 and checked on every ceremony. Reason: a challenge must not be valid on a second replica or
twice on the same one.

Each passkey row keeps the signature counter. An assertion whose counter does not exceed the stored one is refused, and
the counter is written with a conditional update on the old value. This is the only clone detection WebAuthn offers and
it is cheap.

### D7. Rate limiting reuses the failure limiter

Verification failures (TOTP, recovery codes) count through `LoginAttemptLimiter`, with keys `mfa|user|<name>|<address>`
and `mfa|address|<address>`. The same `login.maxFailedAttempts` and `login.lockoutMinutes` apply. Blocked attempts
answer `429` with `Retry-After`, like the password login (identity model 13.8), and are audited as `MFA_CHALLENGE_FAILED`.
A correct code does not reset the counter of the address. Passkey assertions are not counted, because a wrong assertion
needs the authenticator and a fresh challenge.

### D8. Step-up and the authentication time of a session

Session tokens gain three claims: `ath` (epoch seconds of the last authentication), `mfa` (true when a second factor
completed in this family) and `scope` (`full` or `enrol`, see D10). Refresh carries them over unchanged, and the
renewal does not extend `ath`. A new `POST /api/identity/me/step-up` takes a TOTP code, a recovery code or a passkey
assertion from a signed-in session, and reissues the session JWT of the same family with a new `ath` and `mfa=true`.

Sensitive actions require:

- for a user with at least one factor: `mfa` true and `now - ath <= login.mfa.stepUpMinutes` (default 5). Otherwise `403`
  with the problem `step_up_required` and the methods the user has.
- for a user without a factor: a session. Enrolling a first factor additionally needs `ath` within the step-up window,
  so the password was typed recently.

The sensitive actions are: enrolling or removing a factor, regenerating recovery codes, creating, rotating or deleting
the user's own access tokens, changing the password, and instance admin operations (grant or revoke instance admin, reset
factors, password reset of another user). Reads are not sensitive. Reason: the session JWT is not looked up in the
database (`SessionService.kt` comment), so freshness has to be a claim checked against the injected clock.

### D9. Access tokens stay single-factor, and token creation needs step-up

Basic and Bearer requests are authenticated as today, with no prompt, so Maven, Gradle and CI keep working (drop-in
contract). Creating or rotating a token is a sensitive action (D8): a Basic or Bearer request that tries it for a user
who has a factor is refused with `step_up_required`. That stops a stolen token from minting another token, and it does
not affect any existing pipeline, which creates tokens from the dashboard or through the administrator API.

Known gap, recorded on purpose: an administrator's access token with `ingot_admin.*` (or the legacy instance admin
flag) is not covered by `requiredForAdmins`. Covering it would break every pipeline that uses such a token. The policy
covers browser sessions only. The gap is listed in the identity model open points with this change.

### D10. The policy, its grace period and the restricted session

Settings in the existing `login` domain, with defaults that keep everything off:

```
login.mfa: {
  requiredForAdmins: false
  requiredForTeams: []            team names; members of these teams are covered
  graceDays: 14
  stepUpMinutes: 5
  passkeyFirstFactor: false
  requiredSince: null             set by the server when a policy first covers someone; not written by the client
}
```

`OidcProviderSettings` gains `mfaAcrValues: []` and `mfaAmrValues: []` (D12). A covered user is one who is an instance
admin (directly or through a team) or a member of a listed team.

When a policy covers a user without a factor:

- **Grace.** Until `requiredSince + graceDays`, the session is `full`. The security status (D14) tells the dashboard to
  show a banner with the end date.
- **After grace.** Refresh issues a session with `scope: enrol`. Such a session may call only the factor routes of
  `/api/identity/me/`, the security status and `/api/auth/logout`. Every other route answers `403` with
  `mfa_enrolment_required`. Refresh is the place where the change takes effect, so a live session ends within
  `login.sessionLifetimeMinutes` (default 15). No session is revoked by the server.
- **Covered user with a factor.** The login gate of D5 applies at every sign-in, so a policy cannot be bypassed by
  signing in without the factor.

`requiredSince` is set when the policy first covers a user, not when the setting is saved. That keeps the grace per user
and stops an administrator from shortening it by toggling the setting. The administrator who saves the policy is warned
that the last administrator may need a break-glass token if they cannot enrol.

### D11. Relying party identifier and origin

The relying party identifier is the host of `login.publicUrl`, and the expected origin is its scheme and host (and port).
When `publicUrl` is blank, the request host and `X-Forwarded-Proto` are used, as the redirect URIs already do (identity
model O-23). Passkey ceremonies refuse an origin that does not match. The guide states that a passkey is bound to its host:
moving the dashboard to another host makes its passkeys unusable, and the factor can then be removed by an administrator
(D13). Reason: WebAuthn requires the identifier to match the origin, and `publicUrl` is the one value the operator sets
deliberately.

### D12. External logins

- **LDAP users** (`login.ldap`) sign in with the directory password, and then pass the same login gate as local users.
  They can enrol Ingot factors. A directory user has no `local_credential` row, so enrolment relies on the password login
  of the same session (`ath`, D8).
- **OpenID Connect and GitHub users** rely on their identity provider by default. No Ingot factor is requested at a
  callback, unless the policy covers the user. A policy-covered user passes the gate only when the ID token's `acr` is in
  `mfaAcrValues` or its `amr` is in `mfaAmrValues` and the provider is configured with those lists, which are empty by
  default, so nothing is trusted until an operator says so. Otherwise the user is asked for an Ingot factor, like a local
  user. GitHub issues no ID token, so GitHub users always need an Ingot factor under a policy.

Reason: the identity provider is the source of truth for its own factors, and an Ingot prompt on top of an already strong
sign-in is an unwelcome second prompt. The policy stays a hard requirement, which is why the trust is opt-in.

### D13. Administrative reset

`DELETE /api/identity/principals/{name}/factors` removes all factors and recovery codes of a user, and
`DELETE /api/identity/principals/{name}/factors/{id}` removes one. Both are instance admin operations (step-up applies to
the caller, D8). The response and the audit event carry the ids and kinds only; no seed, no code and no key material is
ever returned or logged. The user's sessions end at once, through `SessionService.revokeAllSessions`, as a password reset
already does (`LoginFacade.kt:77-84`). The reset is audited as `MFA_FACTORS_RESET` with the administrator and the target
name.

The administrator list (`GET /api/identity/principals/{name}/factors`) returns names and kinds, creation times and
last-use times, never secrets.

### D14. The REST contract

All paths relative to the base path. Errors use `{"status", "message"}` and a `problems` list where useful, as in the
identity model.

| Method and path | Purpose | Authentication |
|---|---|---|
| `POST /api/auth/mfa/verify` | complete a login with a TOTP code, a recovery code or a passkey assertion | challenge cookie |
| `POST /api/auth/passkey/options` | start a passkey login, with `passkeyFirstFactor` on | none |
| `POST /api/auth/passkey/login` | complete a passkey login as a first factor | none |
| `GET /api/identity/me/security` | status: factor counts, recovery codes left, policy, `graceUntil`, `scope`, `stepUpUntil` | session or Basic |
| `GET /api/identity/me/factors` | list own factors (names, kinds, created, last used) | session or Basic |
| `POST /api/identity/me/factors/totp` | start TOTP enrolment; returns the seed once as an `otpauth` URI | session, step-up for first factor |
| `POST /api/identity/me/factors/totp/confirm` | confirm with a code; creates the factor and the recovery codes | session, challenge from the start |
| `POST /api/identity/me/factors/passkey/options` | start passkey registration | session, step-up |
| `POST /api/identity/me/factors/passkey` | complete passkey registration | session, challenge from the options |
| `PATCH /api/identity/me/factors/{id}` | rename a factor | session, step-up |
| `DELETE /api/identity/me/factors/{id}` | remove a factor | session, step-up |
| `POST /api/identity/me/recovery-codes` | regenerate the recovery codes, returned once | session, step-up |
| `POST /api/identity/me/step-up` | fresh second factor for the session | session |
| `GET /api/identity/principals/{name}/factors` | administrator view, no secrets | instance admin |
| `DELETE /api/identity/principals/{name}/factors[/{id}]` | administrator reset | instance admin |

The login response (`api/AuthLoginEndpoints.kt`, `LoginResponse`) gains `mfaRequired: boolean` and `methods: [...]`. The
field is absent or false for every user who does not need a factor, so an older client reads the same answer as before.
Every route is added to the OpenAPI description, and `docs/openapi` is regenerated.

### D15. Audit

New `AuditAction` values, appended to the enum: `MFA_FACTOR_ENROLLED`, `MFA_FACTOR_REMOVED`, `MFA_CHALLENGE_FAILED`,
`MFA_RECOVERY_CODE_USED`, `MFA_RECOVERY_CODES_REGENERATED` and `MFA_FACTORS_RESET`. Each event carries the user name and
the address, never a code, a seed, a recovery code or a session id. A successful step-up is not audited, because it
happens on every sensitive action; the factor-level events cover it.

### D16. Storage and dialects

Three additive tables in `login/storage/LoginTables.kt`, created with the identity tables:

- `mfa_factor`: `id`, `principal_id` (fk, cascade), `kind` (`TOTP` or `PASSKEY`), `name`, `secret` (encrypted seed, null
  for passkeys), `credential_id` (unique for passkeys), `public_key` (bytes), `sign_count`, `last_used_step` (TOTP replay
  guard), `created_at`, `last_used_at`.
- `mfa_recovery_code`: `id`, `principal_id` (fk, cascade), `code_hash` (unique), `used_at`.
- `auth_challenge`: `id_hash` (pk, SHA-256), `kind`, `principal_id` (nullable), `challenge` (bytes, passkeys only),
  `expires_at`, `used_at`.

Byte columns map to `binary` on SQLite and H2, `varbinary` or `blob` on MariaDB and MySQL, and `bytea` on PostgreSQL. Exposed
chooses the type per dialect. The test matrix (change `test-server-database-matrix`) runs the create-and-use round trip on all
five dialects in CI. Expired challenges are removed on write, as login attempts are.

### D17. Replay and concurrency across replicas

- **TOTP replay.** A code is accepted only for a step greater than `last_used_step`, written with a conditional update
  on the old value. Two replicas that receive the same code see exactly one success.
- **Recovery codes.** Single use through the conditional update of `used_at`.
- **Challenges.** Single use through the conditional update of `used_at` (D5, D6).
- **Passkey counter.** Conditional on the previous `sign_count` (D6).

No lock and no sticky routing is required. This is the same database-first approach as the identity model and the HA design.

### D18. Secrets

- TOTP seeds: encrypted with `SettingsEnvelopeCodec` (AES-256-GCM, additional data bound to the row, so a seed cannot be moved
  to another user). Without a settings key, enrolment is refused (D3).
- Recovery codes: SHA-256 (D4). Challenge ids: SHA-256 (D5).
- Passkeys: public keys only.
- Nothing secret is logged. Failed attempts log the user name and address, as password logins do.

The settings key is optional today (`INGOT_SETTINGS_ENCRYPTION` defaults to `optional`). An operator who enables TOTP sets
the key; the how-to page says so, and `encrypt-settings-secrets.md` is updated to say that the key also protects TOTP
seeds.

### D19. Read-only mode

Verification, sign-in, step-up and the status routes are reads of identity and stay available while read-only, as login
does today. Enrolment, removal, regeneration, token creation and the administrator routes are writes and are refused by
`ContextDsl.refuseWhileReadOnly` (`ContextDsl.kt:115-121`) like every other write.

### D20. High availability

Every piece of new state is a database row, so a challenge started on one replica can be verified on another, a recovery
code spent on one is spent everywhere, and a factor reset by an administrator takes effect on the next request of any
replica. The session JWT remains self-contained and is verified with the shared signing key.

### D21. Break-glass

The `--token` flag is a Basic credential with all rights. It bypasses the login gate, the policy and step-up, as it does
today. It is an operator's startup choice on the host, and the recovery guide describes it. The guide gains a section:
when every administrator is locked out by a policy, start with the token, reset the factors of the user with the
administrator call (D13), and remove the flag.

## Risks

- **Lockout by policy.** Mitigated by the grace period, by recovery codes, by the break-glass token, and by the warning
  shown when the policy is saved.
- **Stale passkeys after a host change.** Documented (D11). The administrator reset (D13) is the way out.
- **Clock drift.** TOTP accepts one step either side. A device more than 30 seconds off is refused, and the message says
  to check the device clock.
- **Settings key not set.** TOTP is refused with a clear reason (D3). Passkeys and recovery codes still work.
- **Library surface.** WebAuthn4J is a new dependency. Its transitive set is checked before the code pull request
  (D2).

## Verification

- Unit tests with an injected `Clock` for TOTP (RFC 6238 vectors, drift, replay), recovery codes (single use), challenge
  expiry, step-up windows, grace and the restricted session. No sleeps, no system time, a fresh database per test.
- Integration tests for the login gate, passkeys with a software authenticator built from fixed test vectors, the
  administrator reset and the token rule, all with `heavy` and `--max-workers=2`.
- The database matrix for the three tables on all five dialects.
- `openspec validate add-multi-factor-authentication --strict`.

# Tasks

Each group is one Conventional Commits type, and each commit carries one type. Tests land in the same commit as the code
they test and are written first: each test is seen to fail before the production code exists. Every test follows
F.I.R.S.T.: a fresh database per test, an injected `java.time.Clock`, no sleeps, no shared mutable state, a temporary
directory per test, bounded waits through latches, and assertions with messages. Test runs are targeted and go through
`heavy` with `--max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose multi-factor authentication`: this change (proposal, design, capability spec, tasks)
  and the sibling change `add-multi-factor-dashboard`. Verify: `openspec validate add-multi-factor-authentication --strict`
  and `openspec validate add-multi-factor-dashboard --strict` pass.

## 2. Authenticator codes and recovery codes

- [ ] 2.1 `test(identity)`: failing tests for the RFC 6238 vectors (SHA-1, six digits, thirty second step), the drift
  window of one step, the replay guard (a code is accepted once per step, written as a conditional update), recovery codes
  (ten per set, hashed, single use, normalised input), and the refusal of TOTP enrolment without a settings key (`409`
  with the reason). Verify: the tests fail on the missing classes.
- [ ] 2.2 `feat(identity)`: add `TotpAlgorithm` (RFC 6238 on `javax.crypto.Mac`), `RecoveryCodes`, the tables `mfa_factor`
  and `mfa_recovery_code` in `login/storage/LoginTables.kt`, and the store methods, with the seed encrypted through
  `SettingsEnvelopeCodec`. Verify: the tests of 2.1 pass, and `LocalLoginProviderTest` and `LoginServiceTest` still pass.

## 3. Passkeys

- [ ] 3.1 `test(identity)`: failing tests for the registration and assertion ceremonies with fixed test vectors, the origin
  and relying party checks (a mismatch is refused), the signature counter rule (equal or lower is refused, the stored value
  is unchanged), and single-use challenges expiring after two minutes on an injected clock. Verify: the tests fail before
  3.2.
- [ ] 3.2 `feat(identity)`: add `com.webauthn4j:webauthn4j-core` to `reposilite-backend/build.gradle.kts` (check its
  transitive dependencies first, design D2; fall back to Yubico's library if it duplicates Bouncy Castle), the `auth_challenge`
  table, the passkey ceremonies and the `WEBAUTHN_REGISTER` and `WEBAUTHN_LOGIN` challenge kinds. Verify: 3.1 passes and the
  licence report lists the one new Apache-2.0 library.

## 4. Login challenge, verification and step-up

- [ ] 4.1 `test(identity)`: failing tests, one behaviour each, for the login gate (a user with a factor gets `mfaRequired`
  and no session), a user without a factor (unchanged cookies), the challenge expiry, the single-use spend across two store
  instances on a shared database, the rate limit of verifications (keys `mfa|user|...`, `mfa|address|...`, `429` with
  `Retry-After`), and the step-up window on an injected clock (`ath` older than `stepUpMinutes` is refused with
  `step_up_required`). Verify: the tests fail before 4.2.
- [ ] 4.2 `feat(identity)`: add the `ingot_mfa` cookie, `POST /api/auth/mfa/verify`, `POST /api/auth/passkey/options` and
  `POST /api/auth/passkey/login`, the claims `ath`, `mfa` and `scope` in `SessionSigner` and `SessionService`, the step-up
  route `POST /api/identity/me/step-up`, the exemptions in `CsrfPolicy.kt:32`, and the `mfaRequired` and `methods` fields of
  the login response. Verify: 4.1 passes, `SessionServiceTest`, `CsrfPolicyTest` and `AuthLoginEndpointsTest` still pass.

## 5. Policy, grace period and restricted sessions

- [ ] 5.1 `test(identity)`: failing tests for the settings defaults (every policy off), `requiredSince` written once per
  user, the grace period on an injected clock, the restricted session (scope `enrol`) refused outside the allowed routes
  with `mfa_enrolment_required`, and the external rules (an OpenID Connect `acr` or `amr` in the provider's list passes,
  otherwise a challenge; GitHub always challenged under a policy). Verify: the tests fail before 5.2.
- [ ] 5.2 `feat(identity)`: add `login.mfa` to `LoginSettings.kt`, `mfaAcrValues` and `mfaAmrValues` to `OidcProviderSettings`,
  the `requiredSince` bookkeeping, the restricted session in refresh, and the external trust rules in `OidcLoginService`
  and the GitHub login. Verify: 5.1 passes, `OidcLoginServiceTest` and `LoginSettingsTest` still pass.

## 6. Factor management, tokens and administrative reset

- [ ] 6.1 `test(identity)`: failing endpoint tests for the own factor routes (list, rename, remove, regenerate recovery codes),
  the step-up requirement on each of them, `GET /api/identity/me/security`, the token rule (a Basic or Bearer request that
  creates a token for a user with a factor is refused with `step_up_required`), the administrator routes (list without
  secrets, reset of all or one factor, sessions ended, `MFA_FACTORS_RESET` written once), and the read-only refusal of
  writes through `ContextDsl.refuseWhileReadOnly`. Verify: the tests fail before 6.2.
- [ ] 6.2 `feat(identity)`: add the own and administrator factor routes to `SelfServiceEndpoints.kt` and
  `IdentityApiEndpoints.kt`, the token rule in `OwnTokens.kt`, the new `AuditAction` values appended in
  `com/reposilite/audit/api/AuditAction.kt`, and the OpenAPI annotations. Verify: 6.1 passes, `OwnTokensTest` and
  `IdentityApiEndpointsTest` still pass, and the OpenAPI description lists every new path.

## 7. Documentation

- [ ] 7.1 `docs(identity)`: add section 15 to `docs/superpowers/specs/2026-10-15-identity-model.md` with the contract as
  implemented, the open points (the administrator access token gap of D9, the trust rule of D12), and the downgrade note.
  Verify: every path and field in section 15 matches a route in `IdentityApiEndpoints.kt` or `SelfServiceEndpoints.kt`.
- [ ] 7.2 `docs(identity)`: gitbook pages. Add `docs/gitbook/how-to/configure-second-factors.md` (settings key, enrolment,
  recovery codes, policy, grace period, the passkey host rule), update `recover-admin-access.md` (a policy that locks out
  every administrator, the reset call, the break-glass token), `configure-oidc-login.md` and `configure-ldap-login.md` (the
  `mfaAcrValues` and `mfaAmrValues` settings and the LDAP factor enrolment), `encrypt-settings-secrets.md` (the key also
  protects authenticator seeds), `use-tokens-in-ci.md` (token creation needs a fresh second factor for users who have one),
  and `SUMMARY.md`. Verify: every setting named in the pages exists in `LoginSettings` or `OidcProviderSettings`.
- [ ] 7.3 `docs(identity)`: upgrade notes. Operators need no action; a second factor needs `INGOT_SETTINGS_KEY` for
  authenticator codes. Operator-facing text only. Verify: the note sits where the earlier identity upgrade notes sit.

## 8. Pull request

- [ ] 8.1 Open the code pull request with the title `feat(identity): add passkeys, authenticator codes and recovery codes
  for sign-in`, a body that links the proposal, names the dashboard change `add-multi-factor-dashboard` as its follow-up, and
  lists the new dependency. Verify: the title passes the commit style hook and carries no attribution line.

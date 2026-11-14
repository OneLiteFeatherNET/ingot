# Proposal

## Open maintainer questions

Each question has a default. The design follows the default unless the maintainer changes it before the code pull request.

1. **Commit scope.** The request named `feat(auth)`. Login work in this repository ships under `feat(identity)`
   (the identity model, the external login providers and the permission nodes all do), while `fix(auth)` covers the
   legacy credential cache. Default: `feat(identity)`. Pull request title: `feat(identity): add passkeys, authenticator codes and recovery codes for sign-in`.
2. **Passkeys as a first factor (passwordless sign-in).** Default: off, behind `login.mfa.passkeyFirstFactor`. Passkeys
   are a second factor unless the switch is on.
3. **Policy default.** Default: no policy. `login.mfa.requiredForAdmins` and `login.mfa.requiredForTeams` are empty or
   false, so an existing installation sees no change until an administrator turns them on.
4. **Grace period.** Default: 14 days from the moment a policy first covers a user. The banner counts down during it.
5. **Trust in the identity provider's second factor.** Default: not trusted. An OpenID Connect sign-in does not count as
   a second factor for the policy unless the provider is configured with `mfaAcrValues` or `mfaAmrValues` that the ID
   token carries. Without that, a policy-covered OpenID Connect user is asked for an Ingot factor.
6. **Authenticator codes need a settings key.** Default: a TOTP factor cannot be enrolled while neither
   `INGOT_SETTINGS_KEY` nor `INGOT_SETTINGS_KEY_FILE` is set, because the seed must be stored reversibly. The request
   answers `409` with the reason. Passkeys and recovery codes need no key.
7. **Access tokens of admins stay single-factor.** Default: yes. The policy covers browser sessions and sensitive actions
   only. A Maven, Gradle or CI token with `ingot.admin.*` keeps working unchanged. This is a deliberate gap, recorded in
   `design.md` (D9).
8. **Break-glass token.** Default: the `--token` startup flag keeps working without any second factor, as
   `docs/gitbook/how-to/recover-admin-access.md` describes.
9. **Relying party identifier.** Default: the host of `login.publicUrl`. When it is blank, the host of the request is
   used, the same rule the redirect URIs already follow. Changing the host later makes existing passkeys unusable, and
   the guide says so.

## Why

Ingot signs browser users in with a password, an LDAP directory or an OpenID Connect provider (`docs/superpowers/specs/2026-10-15-identity-model.md`,
sections 13 and 14). A password alone is the only protection of a local account. Administrators of a shared repository
manager are the most attractive target in the instance: an instance admin can delete repositories, grant rights and
read the audit log. Failed logins are already limited (`login.maxFailedAttempts`, default 5), but a leaked or reused
password still opens the dashboard.

Second factors close that gap for local and LDAP accounts, and give administrators a policy they can turn on without
touching the deployment. Passkeys remove the phishing risk of codes, authenticator codes cover the common case, and
recovery codes keep a lost device from locking a person out. Non-interactive clients (Maven, Gradle, CI) use access tokens
and never see a prompt, so the drop-in contract holds.

## What Changes

- **Second factors for local and LDAP accounts.** Three kinds of factor: authenticator codes (TOTP, RFC 6238), passkeys
  (WebAuthn, with WebAuthn4J), and single-use recovery codes stored as hashes. A user may hold several factors of each
  kind. Passkeys may also be the first factor when `login.mfa.passkeyFirstFactor` is on.
- **Login gate.** A password or LDAP login that succeeds for a user with an enrolled factor, or for a user the policy
  covers, does not start a session yet. It starts a short-lived, single-use challenge (table `auth_challenge`, cookie
  `ingot_mfa`). The second factor completes the login. Existing users without factors and without a policy log in exactly
  as before.
- **Step-up.** Sensitive actions require a second factor completed within `login.mfa.stepUpMinutes` (default 5): adding
  or removing factors, creating or rotating access tokens from a browser session, changing the password, and instance
  admin operations. A session carries the time of its last authentication and whether a second factor was used.
- **Policy.** `login.mfa.requiredForAdmins` and `login.mfa.requiredForTeams` (a list of team names). A covered user without
  a factor gets the grace period, then a restricted session that may only manage factors and sign out.
- **Administration.** An instance admin can list a user's factors (names and kinds, never secrets) and remove one or all
  of them. The change is audited and ends that user's sessions.
- **REST API** under `/api/auth/` (challenge, verification, passkey login) and `/api/identity/me/` (factors, recovery
  codes, step-up, security status), plus `/api/identity/principals/{name}/factors` for administrators. All new routes are
  documented in the OpenAPI description.
- **Audit.** Additive `AuditAction` values: `MFA_FACTOR_ENROLLED`, `MFA_FACTOR_REMOVED`, `MFA_CHALLENGE_FAILED`,
  `MFA_RECOVERY_CODE_USED`, `MFA_RECOVERY_CODES_REGENERATED`, `MFA_FACTORS_RESET`. Stored names keep their meaning.
- **Documentation.** A new how-to page `docs/gitbook/how-to/configure-second-factors.md`, and updates to
  `recover-admin-access.md`, `configure-oidc-login.md`, `configure-ldap-login.md`, `encrypt-settings-secrets.md`,
  `use-tokens-in-ci.md`, and the identity model (new section 15).

### Pull request

This change ships under `feat(identity)`, because it is login work in the identity model. Pull request title, squash-merged onto main:

    feat(identity): add passkeys, authenticator codes and recovery codes for sign-in

The dashboard part is a separate change, `add-multi-factor-dashboard`, with its own pull request
`feat(ui): add second factor steps to the dashboard sign-in and account page`. Its code pull request follows this one.

## Breaking changes

**BREAKING**: none. Nothing here changes what an existing installation does until an operator acts:

- No environment variable, configuration file, volume, port, manifest or image reference is added or changed. The new
  settings live in the existing shared settings domain `login`, with defaults that keep every policy off.
- New tables `mfa_factor`, `mfa_recovery_code` and `auth_challenge` are created on start with the other identity tables.
  No existing table is altered.
- Users without a factor and without a covering policy keep the login they have today. Access tokens, Basic
  authentication and the legacy `/api/tokens` endpoint are unchanged.
- An operator who wants TOTP must set the existing `INGOT_SETTINGS_KEY` (or `INGOT_SETTINGS_KEY_FILE`). This is an
  opt-in requirement of a new feature, not a change to an existing one. The upgrade notes say so.

## Out of scope

- The dashboard. The sign-in steps, the account page section and the administration controls are the change
  `add-multi-factor-dashboard`. This change publishes the contract that change builds against.
- The account page itself. The `Sign-in and security` section is provided by the change `rework-account-menu-and-profile`,
  which is proposed in parallel and is not yet on main. The dashboard change references it by name.
- Enforcing a second factor on access tokens or on the legacy Basic authentication of Maven and Gradle clients. Tokens stay single-factor (design D9).
- SMS, e-mail codes and push approvals. Only authenticator codes, passkeys and recovery codes are added.
- Second factors for OpenID Connect and GitHub logins beyond the trust rule in the open questions. The identity provider
  owns its own factors (design D12).
- Session listing and revocation for users (open point O-20 of the identity model). The administrative factor reset
  ends sessions, but a general revocation API is not added here.
- Rotation of the session signing key (O-18).
- Passkey attestation checks against a metadata service. Attestation is `none`.

## Capabilities

### New Capabilities

- `identity-multi-factor-authentication`: authenticator codes, passkeys and recovery codes for local and LDAP accounts,
  the login challenge, step-up for sensitive actions, the policy and its grace period, the token rule, the administrative
  reset and the REST contract.

### Modified Capabilities

- None in the openspec sense. The password login, the sessions and the access tokens keep their requirements. Additions
  to the session claims and to the login response are recorded in the new capability and in section 15 of the identity model.

## Impact

- **Operators:** no action is required to upgrade. To use authenticator codes, set `INGOT_SETTINGS_KEY` (see question 6). To
  require second factors, set `login.mfa` in the shared settings, which is documented in the new how-to page.
- **Code:** `reposilite-backend` only, in `net.onelitefeather.ingot.identity` (login, session, storage, api) and in
  `com.reposilite.audit.api.AuditAction`. `ContextDsl` gains a step-up check for the sensitive routes.
- **Dependencies:** one new Apache-2.0 library, `com.webauthn4j:webauthn4j-core` (design D2). The licence report and the
  dependency list change by that one library. Authenticator codes need no dependency.
- **Database:** three additive tables on all supported dialects (SQLite, H2, MariaDB, MySQL, PostgreSQL). The migration
  test runs in the existing server database matrix.
- **Maven and Gradle clients:** unchanged. Their tokens do not prompt for anything.

## Verification

- `openspec validate add-multi-factor-authentication --strict` passes.
- Targeted tests only, run through `heavy` with `--max-workers=2`, never the full suites.
- The code pull request runs the existing "Container images" CI job's login checks and the server database matrix on all
  five dialects.

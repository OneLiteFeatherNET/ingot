---
description: Turn on second factors for dashboard sign-in in Ingot. Enrol an authenticator app, a passkey or recovery codes, set the policy for administrators and teams, and plan the grace period and the passkey host.
---

# Configure second factors

Ingot can ask for a second factor when someone signs in to the dashboard. There are three kinds: an authenticator app that shows six-digit codes, a passkey on a device or security key, and recovery codes for when the other two are not at hand. Nothing changes for anyone until a user enrols a factor, and nothing is required until an administrator sets a policy. Maven, Gradle, CI and other access tokens keep working without a prompt.

## Before you begin

* An instance administrator account. Changing the policy needs the settings area, `ingot.admin.settings`.
* The public address of the dashboard in `login.publicUrl`, for example `https://repo.example.org`. Set it before anyone enrols a passkey, see [The passkey host rule](#the-passkey-host-rule).
* The settings key, if you want authenticator codes. See the next section.

## The settings key for authenticator codes

The seed of an authenticator app is stored encrypted, so it needs a settings key. Set `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE` before a user enrols an authenticator app. Without a key, the enrolment is refused with `409` and the reason `Authenticator codes need the settings key: set INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE first`.

Passkeys and recovery codes need no key. The key itself, its file and its rotation are described in [Encrypt the settings secrets](../../manage/operate/encrypt-settings-secrets.md).

{% hint style="warning" %}
Rotating the settings key does not re-seal the authenticator seeds in this release. Keep the previous key configured (`INGOT_SETTINGS_KEY_PREVIOUS` or `INGOT_SETTINGS_KEY_PREVIOUS_FILE`) for as long as users hold authenticator codes that were enrolled under the old key. Removing it earlier stops those codes from working. Recovery codes, passkeys and the administrator reset still work.
{% endhint %}

## Enrol a factor

The dashboard has no second factor screen in this release, so enrolment runs through the API. The factor routes accept a browser session only, not an access token. Sign in with the password, then make the calls with the session cookies. The calls that change state also send the CSRF token from the `ingot_csrf` cookie in the `X-CSRF-Token` header.

Start the enrolment right after the sign-in. A first factor needs the password entry to be recent, inside the step-up window (`login.mfa.stepUpMinutes`, five minutes by default). The example uses `curl` and a cookie jar:

```bash
BASE=https://repo.example.org

# Sign in. The jar receives ingot_session, ingot_refresh and ingot_csrf.
curl -c jar.txt -H "Content-Type: application/json" \
  -d '{"provider": "local", "username": "alice", "password": "<password>"}' \
  "$BASE/api/auth/login"

CSRF=$(awk '$6 == "ingot_csrf" {print $7}' jar.txt)

# Authenticator app: the answer holds an otpauth URI, which is returned once.
curl -b jar.txt -H "X-CSRF-Token: $CSRF" -X POST "$BASE/api/identity/me/factors/totp"

# Confirm with a code from the app. The first factor also returns the recovery codes.
curl -b jar.txt -H "X-CSRF-Token: $CSRF" -H "Content-Type: application/json" \
  -X POST -d '{"code": "123456"}' "$BASE/api/identity/me/factors/totp/confirm"
```

Turn the `otpauth` URI into a QR code with any tool, or enter the secret in the app. A pending enrolment that is never confirmed is replaced by the next start.

Some points that apply to every factor:

* **Recovery codes.** The first confirmed factor returns ten recovery codes, once. Each code works once. Store them offline. A new set replaces the old one and needs a recent second factor: send a code to `POST /api/identity/me/step-up` first, then `POST /api/identity/me/recovery-codes`.
* **A user with a factor** starts another factor with a current code of one of the existing authenticator factors. Removing a factor (`DELETE /api/identity/me/factors/{id}`) also takes a current code in the body.
* **Passkeys** are registered by the browser. `POST /api/identity/me/factors/passkey/options` returns the challenge, the browser creates the credential with the WebAuthn API, and `POST /api/identity/me/factors/passkey` completes the registration. A passkey cannot be registered with `curl` alone.
* **The list** of the factors of a user is `GET /api/identity/me/factors`. It shows kinds, names and dates, never a secret.

## Sign in with a factor

A user with at least one factor does not get a session from a password sign-in alone. `POST /api/auth/login` answers `200` with `{"mfaRequired": true, "methods": [...]}` and sets the cookie `ingot_mfa`, which lives five minutes. The methods are `AUTHENTICATOR`, `PASSKEY` and `RECOVERY_CODE`.

Complete the sign-in with `POST /api/auth/mfa/verify`. It takes `{"code": "..."}` for an authenticator app or a recovery code, or `{"passkey": {...}}` for a passkey assertion. The call reads the `ingot_mfa` cookie of the sign-in, so send the same cookie jar. The dashboard has no prompt for this in this release, so a user with a factor finishes the sign-in through the API until the dashboard change lands:

```bash
curl -c jar.txt -b jar.txt -H "Content-Type: application/json" \
  -d '{"code": "123456"}' "$BASE/api/auth/mfa/verify"
```

Wrong codes count towards the failure limit of the user name and of the address, as wrong passwords do. A blocked attempt answers `429` with `Retry-After`.

## Set the policy

The policy is the `mfa` object of the `login` settings domain. Everything is off by default.

| Setting | Default | Meaning |
| --- | --- | --- |
| `login.mfa.requiredForAdmins` | `false` | Instance administrators, directly or through a team, must hold a second factor. |
| `login.mfa.requiredForTeams` | `[]` | Direct members of the listed team names must hold a second factor. Names are compared without case. |
| `login.mfa.graceDays` | `14` | Days after a user is first covered by the policy during which the user may sign in without a factor. Between 0 and 365. |
| `login.mfa.stepUpMinutes` | `5` | Minutes a second factor stays recent for sensitive actions. Between 1 and 60. |
| `login.mfa.passkeyFirstFactor` | `false` | Lets a passkey sign a user in without a password. While it is off, the passkey sign-in routes answer `404`. |

The dashboard has no form for the policy in this release. Change it through the settings API. Read the `login` domain, change the `mfa` object and write the domain back. The answer of the read is the whole domain, and the write takes the whole domain too:

```bash
ADMIN_TOKEN="<access token with the settings area>"

curl -u "$ADMIN_TOKEN" "$BASE/api/settings/domain/login" -o login.json

jq '.mfa.requiredForAdmins = true | .mfa.graceDays = 14' login.json > login-new.json

curl -u "$ADMIN_TOKEN" -X PUT -H "Content-Type: application/json" \
  --data-binary @login-new.json "$BASE/api/settings/domain/login"
```

{% hint style="warning" %}
Before you turn on `requiredForAdmins`, enrol a factor for every administrator, or be ready to start with the break-glass token. An administrator who has no factor after the grace period gets a restricted session that can only enrol. The steps are in [Recover admin access](../../manage/access/recover-admin-access.md#a-policy-locks-out-every-administrator). The save answers with a `Warning` header that says the same.
{% endhint %}

## The grace period

The grace period is kept per user. It starts at the first sign-in after the policy covers the user, not when the policy is saved. Switching the policy off and on again does not restart it.

* **During the grace period** the user signs in as before and can use the dashboard fully. The user should enrol a factor now.
* **After the grace period** the next refresh of the session gives a restricted session. The restricted session can list and enrol factors, create recovery codes, refresh and log out. Every other route answers `403` with `mfa_enrolment_required`. A session that is already open keeps its scope until it is renewed, which happens within `login.sessionLifetimeMinutes` (15 minutes by default). The status of a user, including `graceUntil`, is `GET /api/identity/me/security`.
* **A user with a factor** keeps a full session and is challenged at every sign-in.

## Step-up

A session of a user with a factor must confirm a second factor shortly before a sensitive action. The sensitive actions are changes of the factors, regenerating recovery codes, the administrator reset of factors, changes of the password and of the own tokens, and the grant or revoke of instance admin. A session that is too old gets `403` with `step_up_required` and the methods it can use. Confirm with `POST /api/identity/me/step-up`, then repeat the action. The window is `login.mfa.stepUpMinutes`.

Calls that carry an access token are not asked for a second factor, with one exception: creating, rotating or deleting an own token of a user who has a factor is refused for a token caller, and needs a browser session with a fresh second factor. Existing tokens keep working. See [Use tokens in CI](../../publish-and-consume/use-tokens-in-ci.md#tokens-of-users-with-a-second-factor).

## The passkey host rule

A passkey is bound to the address of the dashboard. The browser checks it on each sign-in, so the address must stay the same.

* The relying party of the passkeys is the host of `login.publicUrl`. The browser compares the scheme, host and port of the address with the origin in the passkey.
* Without `login.publicUrl`, Ingot uses the `Host` header and `X-Forwarded-Proto` of the request. Passkeys then follow whatever address the users type. Set `login.publicUrl` before users enrol passkeys.
* **A changed host makes the passkeys unusable.** Moving the dashboard to another name, or changing the host that the reverse proxy sends, means the browser no longer accepts the old passkeys. A user whose only factor is a passkey cannot sign in then.
* The way out is the administrator reset of the factors of that user, see [Recover admin access](../../manage/access/recover-admin-access.md#reset-the-factors-of-a-user). The user enrols again on the new address. Authenticator codes and recovery codes are not bound to the host and keep working.

## Passkeys as the first factor

With `login.mfa.passkeyFirstFactor` set to `true`, a user with a passkey can sign in without a password. The dashboard does not offer this in this release. The routes are `POST /api/auth/passkey/options`, which takes `{"username": "..."}`, and `POST /api/auth/passkey/login`, which completes the sign-in.

## Check the result

1. Enrol a factor for a test user and sign out.
2. Sign in with the password. The answer is `{"mfaRequired": true, ...}` and no session is created.
3. Complete the sign-in with `POST /api/auth/mfa/verify`. The answer is the signed-in user, and the session cookies are set.
4. Check `GET /api/identity/me/security`. It shows the factor counts, the recovery codes left and the policy that covers the user.

## Related

* [Configure generic OIDC login](configure-oidc-login.md#second-factors-and-the-policy)
* [Configure LDAP login](configure-ldap-login.md#second-factors)
* [Recover admin access](../../manage/access/recover-admin-access.md)
* [Encrypt the settings secrets](../../manage/operate/encrypt-settings-secrets.md)
* [Sessions, login and CSRF](../../references/architecture/sessions-and-login.md)

---
description: Reference for the login providers of Ingot. Lists the settings of the login domain, the local password login, OpenID Connect and GitHub providers, the LDAP directory login, the legacy authentication.ldap fallback, the login endpoints and the login error codes.
---

# Login providers

Ingot has four ways to sign in to the dashboard and the identity API: a local password login, OpenID Connect (OIDC) providers, GitHub, and an LDAP directory. The login settings live in the `login` settings domain. The LDAP settings of the Maven Basic authentication live in the `authentication` domain, and they are a separate setting.

A provider appears on the login page only when it is enabled and complete. The list of announced providers is returned by `GET /api/auth/providers`.

## Login settings

Key names are the JSON names of the `login` domain.

| Key | Default | Meaning |
| --- | --- | --- |
| `sessionLifetimeMinutes` | `15` | Lifetime of a browser session token. Logout and a disabled user take effect at the latest when it expires. |
| `refreshLifetimeHours` | `168` | Lifetime of a refresh token. Every renewal issues a new one, so this bounds the idle time of a browser session. |
| `refreshAbsoluteDays` | `30` | Longest time a login stays renewable, counted from the login. Renewals cannot extend it. |
| `maxFailedAttempts` | `5` | Failed password logins that lock a user name from one address, and the same number of failures that lock an address. |
| `lockoutMinutes` | `15` | How long a lockout lasts after the first lockout. Every further lockout without a quiet period doubles it, up to 24 hours. |
| `idleTimeoutMinutes` | `0` | Minutes a browser session may stay idle before it must sign in again. `0` keeps the refresh lifetime as the only idle limit. See the idle timeout section below. |
| `oidcProviders` | `[]` | The OIDC and GitHub providers. See the next section. |
| `ldap` | see below | The LDAP directory of the browser login. |
| `publicUrl` | empty | Public address of the dashboard, for example `https://repo.example.org/`. Empty means the address is built from each request. |

`publicUrl` must be an absolute `https://` address, or `http://` for `localhost`. It may not contain a query, a fragment or a user name. Other values are reported as settings violations.

The lockout keys apply to the password login of the dashboard. They are independent of the brute force protection of Basic and token authentication.

### Idle timeout

`idleTimeoutMinutes` ends a browser session after that many minutes without a renewal. The default is `0`, which changes nothing: the refresh lifetime of `refreshLifetimeHours` (168 hours) is the only idle limit, as before. A value from `5` up to the refresh lifetime in minutes shortens it. Any other value is reported as a settings violation.

Every renewal restarts the idle time, and the absolute end of the login (`refreshAbsoluteDays`) still applies first. When the timeout has passed, the next renewal is refused and the browser must sign in again.

For instances with administrators, we recommend `30`. It is an opt-in: Ingot does not set it, and the operator sets it in the `login` settings. The timeout applies to every browser session of the instance, so a dashboard left idle for half an hour asks for a new sign-in.

### Account throttle

The account throttle delays guesses against one user name from every address, for the dashboard login, Basic, Bearer and console tokens. It is off by default, and nothing changes until the environment variable `INGOT_LOGIN_ACCOUNT_THROTTLE` is set to `true`. The variable `REPOSILITE_LOGIN_ACCOUNT_THROTTLE` is read when `INGOT_` is unset or blank. The switch is read once at start, so it is a deployment choice and not a setting of the admin page.

With the switch on, a name that has failed `threshold` times (default 10) within `quietMinutes` (default 60) gets `429` with `Retry-After` for its next guess from an address it has not signed in from. The first delay is one second, each further failure doubles it, and `maxDelaySeconds` (default 300) caps it. The delay ends by itself and a success clears it. The answer is the same for an existing and an unknown name. A request from an address the name signed in from within `knownAddressDays` (default 30) is not held back by this rule. A delayed request does not count as a failure.

| Key in `accountThrottle` | Default | Meaning |
| --- | --- | --- |
| `threshold` | `10` | Failed credentials of one name, from any address, before a new address is delayed. |
| `quietMinutes` | `60` | Minutes without a failure after which the count starts again. |
| `maxDelaySeconds` | `300` | Longest delay of one request. |
| `knownAddressDays` | `30` | Days an address stays known after its last successful sign-in. |

Upgrade note: the table of known addresses starts empty when the switch is turned on. Until an owner has signed in once from an address, that address counts as new. An owner whose name is already delayed by an attack waits out the delay from such an address, so turn the switch on and let the owners sign in. A Maven client that retries a stale token sees `429` for at most `maxDelaySeconds`. Gradle 9 does not wait for `Retry-After` (Maven 3.9 does), so a Gradle build that meets the delay fails at once and has to be started again after the delay.

### Password policy

The password policy applies to a password that is chosen in three places: the first administrator at setup, a user who changes their own password, and an administrator who resets a user's password. It never applies at sign-in, so a stored password is not checked again and no user is locked out by a new rule. Creating a user through the identity API stores no password. The **New user** dialog of the dashboard creates the user and then sets the first password through the reset route, so the rule applies to that password at once. With the defaults, a new password needs at least 12 characters, as it always has, and no breached-password check runs.

The key path of the minimum is `login.passwordPolicy.minLength`. The other keys are in the table below.

| Key in `passwordPolicy` | Default | Meaning |
| --- | --- | --- |
| `minLength` | `12` | Fewest characters of a new password, counted in UTF-16 code units as before. Between 8 and 256. A higher value applies to the next password that is set; stored passwords are not checked again. |
| `breachCheck.mode` | `OFF` | `OFF` makes no check. `RANGE` asks the range service. `OFFLINE` compares with a file. |
| `breachCheck.file` | empty | `OFFLINE` only: a file with one SHA-1 hash in hexadecimal per line, optionally followed by `:COUNT`. It is read once at start, and the start fails when it cannot be read. |
| `breachCheck.rangeUrl` | `https://api.pwnedpasswords.com/range/` | `RANGE` only: base address of the range service, ending in a slash. The five characters are appended. Must be an `https://` address, so a mirror of the API can be used. |
| `breachCheck.failClosed` | `false` | `RANGE` only: when the service does not answer, the change is refused with `503` and `Retry-After`. When `false`, the change is allowed and `PASSWORD_BREACH_CHECK_UNAVAILABLE` is written to the audit trail. |

`RANGE` is a call to an outside service, which is why it is off by default. The instance computes the SHA-1 of the new password and sends only its first five hexadecimal characters with `Add-Padding: true`. The suffixes that come back are compared on the instance, so the password never leaves it. The request has no user name, address, cookie or authorization header. It waits at most three seconds to connect and three seconds for the answer.

`OFFLINE` makes no call. The whole file is held in memory, so a list of the size of the full public collection needs a heap that holds it. A breached password is refused with `400` and the message that it appears in a breached password list. A change refused for an outage of `RANGE` gets `503` with `Retry-After`.

### Local login

The local provider has the id `local` and the label "Username and password". It has no settings of its own. It is announced once a user exists or the first administrator has been claimed. Users with a password sign in with `POST /api/auth/login` and the provider `local`.

## OpenID Connect and GitHub providers

Each entry of `oidcProviders` describes one provider.

| Key | Default | Meaning |
| --- | --- | --- |
| `id` | empty | Slug of the provider. Lowercase letters, digits and dashes, at most 64 characters, unique in the list. It is part of every group reference, so keep it stable. |
| `enabled` | `false` | Announces the provider on the login page and serves its login routes. |
| `label` | empty | Text of the login button. Empty shows the id. |
| `icon` | `GENERIC` | Logo of the button: `GENERIC`, `GITHUB`, `GITLAB`, `GOOGLE`, `MICROSOFT`, `KEYCLOAK`, `AUTHENTIK`, `OKTA`, `AUTH0` or `GITEA`. `GITHUB` with an empty issuer or `https://github.com` selects the GitHub endpoints. |
| `issuerUrl` | empty | Base URL of the provider. Discovery is read from `<issuerUrl>/.well-known/openid-configuration`. Required for OIDC. |
| `clientId` | empty | Client id registered at the provider. Required. |
| `clientSecret` | empty | Client secret registered at the provider. Sensitive. Sent with HTTP Basic authentication. If it is empty, the client id is sent in the form instead (a public client). |
| `scopes` | `openid profile email` | Scopes requested. Replaced by `read:user user:email read:org` for GitHub when the value is empty or still the OIDC default. |
| `usernameClaim` | `preferred_username` | Claim that gives the user name of a new user. |
| `displayNameClaim` | `name` | Claim that gives the display name. |
| `emailClaim` | `email` | Claim that gives the email address, used by `allowedEmailDomains`. |
| `groupsClaim` | `groups` | Claim that lists the groups of the user. It may be an array or a single string. |
| `allowedEmailDomains` | `[]` | When not empty, the email must be in one of these domains. Checked at every login, case insensitive. |
| `autoCreateUsers` | `true` | Creates a user at the first login of a subject. When off, only users that are already linked can sign in. |
| `adminGroups` | `[]` | Groups whose members become instance admins through a synchronised team. |

### Usable and announced

An enabled provider is announced only when it has the fields it needs. For OIDC these are `issuerUrl` and `clientId`. For GitHub these are `clientId` and `clientSecret`. An enabled entry without them is not announced, and the server logs the first missing field. Entries with an empty or duplicate id, or an id that is not a slug, are skipped and logged.

### OIDC login flow

- The login starts at `GET /api/auth/oidc/<id>/login`. The request uses the authorization code flow with PKCE (S256), a state value and a nonce. The nonce is not sent to GitHub.
- The provider calls back to `GET /api/auth/oidc/<id>/callback`.
- The redirect URI is `<publicUrl>/api/auth/oidc/<id>/callback` when `publicUrl` is set. Otherwise it is built from the scheme, the `Host` header and the base path of the request. The scheme is `https` when the request is secure, or when `X-Forwarded-Proto` says so. Register this exact URI at the provider.
- The issuer must be `https://`. `http://` is accepted only for `localhost`, `127.0.0.1` and `[::1]`.
- The discovered `issuer` must equal the configured `issuerUrl`. A trailing slash does not count as a difference.
- The ID token must verify against the keys of the provider. It must have the client id in its audience (and `azp` when there is more than one audience), must not be expired, and must carry the nonce of the login.

### GitHub

A GitHub provider is an entry with `icon` set to `GITHUB`. GitHub has no ID token, so Ingot reads the identity from the GitHub API.

- The subject is the numeric user id. The user name is the GitHub login. The display name is the profile name. The email is the public email, or else the primary verified email.
- The claim settings `usernameClaim`, `displayNameClaim`, `emailClaim` and `groupsClaim` are not read for GitHub.
- Groups are `<org>` for each organisation and `<org>/<team-slug>` for each team of the user. Use these names in `adminGroups`.
- The teams are read 100 per page, for at most 50 pages. A user with more teams fails the login, so that no membership is dropped silently. The login then fails with `provider_unavailable`.

### Provider guides

Step-by-step guides for Keycloak, Authentik, Okta, Google Workspace, Microsoft Entra ID and GitHub are linked from [Configure generic OIDC login](../../configure/authentication/configure-oidc-login.md). The rules for groups are in [Map provider groups to teams](../../configure/authentication/map-provider-groups-to-teams.md), and the messages behind the codes below are in [Troubleshoot external login](../../configure/authentication/troubleshoot-external-login.md).

### Microsoft Entra ID

There is no Entra specific code. Entra ID is configured as a generic OIDC provider.

- `issuerUrl` is `https://login.microsoftonline.com/<tenant>/v2.0`, with the tenant id. The `common`, `organizations` and `consumers` placeholders fail the issuer check, because the discovered issuer contains the tenant id.
- `usernameClaim` stays `preferred_username`.
- Register the redirect URI of the callback in the app registration.
- The `groups` claim carries group object ids by default in Entra ID. Use those ids in `adminGroups`. This is Entra's behaviour and is not tested against a tenant in this repository.

## LDAP login

The browser login can check passwords against an LDAP directory. The settings are in `login.ldap`.

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Announces the LDAP login and accepts LDAP passwords on `POST /api/auth/login`. |
| `label` | `LDAP` | Text of the LDAP login on the login page. |
| `url` | empty | `ldap://` or `ldaps://` address of the directory. Required. |
| `startTls` | `false` | Upgrades an `ldap://` connection with StartTLS before the first bind. It has no effect on `ldaps://`. |
| `bindDn` | empty | Account of the service bind that searches the directory. Empty means an anonymous search. |
| `bindPassword` | empty | Password of the service bind. Sensitive. |
| `userBaseDn` | empty | Where users are searched. Required. |
| `userFilter` | `(uid={username})` | Filter that finds a user. `{username}` is replaced by the escaped user name. |
| `usernameAttribute` | `uid` | Attribute that gives the user name of a new user. |
| `displayNameAttribute` | `cn` | Attribute that gives the display name. |
| `emailAttribute` | `mail` | Attribute that gives the email address. |
| `groupBaseDn` | empty | Where groups are searched. Empty disables the group synchronisation. |
| `groupFilter` | `(member={dn})` | Filter that finds the groups of a user. `{dn}` and `{username}` are replaced by their escaped values. |
| `groupNameAttribute` | `cn` | Attribute that gives the name of a group. |
| `autoCreateUsers` | `true` | Creates a user at the first login of a directory entry. When off, only linked users can sign in. |
| `adminGroups` | `[]` | Groups whose members become instance admins through a synchronised team. |

How the login works:

- The user filter must match exactly one entry, or the login is rejected. The entry's distinguished name is then bound with the password. The subject of the login is that distinguished name.
- An empty user name or password is rejected before the directory is asked.
- Groups are found with the group filter. Each group that has a team with the reference `ldap:<group>` gets the membership at each login.
- `allowedEmailDomains` does not exist for LDAP.
- An enabled `login.ldap` that lacks `url` or `userBaseDn` serves no login at all. It does not fall back to `authentication.ldap`.
- The test endpoint `POST /api/auth/providers/ldap/test` checks the connection, the search, the user bind when a password is given, and the groups. It writes nothing to the directory. It needs the instance admin status.

## Legacy LDAP fallback: authentication.ldap

Before 1.2, the LDAP settings of the Maven Basic authentication also served the dashboard login. They still do, while `login.ldap` is off. Nothing has to change on an upgrade.

The settings are in the `authentication` domain, under the key `ldap`.

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | LDAP authenticator enabled. |
| `ssl` | `false` | Use LDAPS to connect. |
| `hostname` | `ldap.domain.com` | LDAP server address. |
| `port` | `389` | LDAP server port. |
| `baseDn` | `dc=company,dc=com` | Base DN with users. |
| `searchUserDn` | `cn=reposilite,ou=admins,dc=domain,dc=com` | User that searches the directory. |
| `searchUserPassword` | a placeholder | Password of the search user. Sensitive. |
| `typeAttribute` | `person` | Attribute that represents the type or group (objectClass). |
| `userAttribute` | `cn` | Attribute that gives the unique user name. |
| `userFilter` | `(&(objectClass=person)(ou=Maven Users))` | LDAP user filter. |
| `userType` | `PERSISTENT` | Type of the access token created for a directory user: `PERSISTENT` or `TEMPORARY`. |

The legacy login does not create a human principal. The directory user gets an access token, and the dashboard signs in as the machine principal that owns that token. Its rights are the same as for Maven clients. This login has no group synchronisation and no StartTLS.

`login.ldap` and `authentication.ldap` are independent. Nothing is copied from one to the other. Once `login.ldap` is enabled and complete, it takes precedence for the dashboard login.

## Login endpoints

| Method and path | Purpose | Auth |
| --- | --- | --- |
| `GET /api/auth/providers` | Lists the announced providers and whether the first administrator must still be created. | None |
| `POST /api/auth/login` | Signs in with a provider. The body carries `provider`, `username` and `password`. | None |
| `POST /api/auth/refresh` | Renews the browser session with the refresh cookie. | Refresh cookie |
| `POST /api/auth/logout` | Ends the browser session. | None |
| `GET /api/auth/oidc/{id}/login` | Starts an OIDC or GitHub login. | None |
| `GET /api/auth/oidc/{id}/callback` | Finishes an OIDC or GitHub login. | None |
| `POST /api/auth/providers/oidc/{id}/test` | Tests discovery, the redirect and the token endpoint of a provider. | Instance admin |
| `POST /api/auth/providers/ldap/test` | Tests the LDAP directory. | Instance admin |

`POST /api/auth/setup` creates the first administrator from the one-time setup code printed in the startup log. It works only while no user exists. `GET /api/auth/setup` tells whether setup is still required.

## Login error codes

A failed OIDC or GitHub login redirects to the dashboard with `?loginError=<code>`. A refused `POST /api/auth/login` answers with status `403` and the code as its message. The other answers are: a wrong password `401`, a disabled user `403` with "This user is disabled", too many failed attempts `429`, and an unreachable provider `503`. A login body above 64 KiB answers `413` in the same error shape as the other answers, before the body is read. The same 64 KiB limit applies to setup, refresh, logout, token and principal and team requests on the JSON API.

| Code | Meaning |
| --- | --- |
| `invalid_state` | The state of the login is missing, expired or does not match. |
| `access_denied` | The user refused the login at the provider. |
| `provider_error` | The provider returned an error or no code. |
| `provider_unavailable` | The provider, or GitHub's API, could not be reached or answered badly. |
| `token_exchange_failed` | The provider refused the authorization code. |
| `invalid_id_token` | The ID token is not valid, or it lacks the username claim while `autoCreateUsers` is on. |
| `no_database` | The login could not start a session. |
| `setup_required` | No user exists yet. The first administrator must be created locally. |
| `user_not_provisioned` | The user is new and `autoCreateUsers` is off. |
| `username_taken` | The user name is taken by another user. Ingot does not link users by name. |
| `invalid_username` | The user name is not valid. |
| `user_disabled` | The linked user is disabled. |
| `email_domain_not_allowed` | The email is missing or not in `allowedEmailDomains`. |


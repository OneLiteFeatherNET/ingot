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
| `oidcProviders` | `[]` | The OIDC and GitHub providers. See the next section. |
| `ldap` | see below | The LDAP directory of the browser login. |
| `publicUrl` | empty | Public address of the dashboard, for example `https://repo.example.org/`. Empty means the address is built from each request. |

`publicUrl` must be an absolute `https://` address, or `http://` for `localhost`. It may not contain a query, a fragment or a user name. Other values are reported as settings violations.

The lockout keys apply to the password login of the dashboard. They are independent of the brute force protection of Basic and token authentication.

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

A failed OIDC or GitHub login redirects to the dashboard with `?loginError=<code>`. A refused `POST /api/auth/login` answers with status `403` and the code as its message. The other answers are: a wrong password `401`, a disabled user `403` with "This user is disabled", too many failed attempts `429`, and an unreachable provider `503`.

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


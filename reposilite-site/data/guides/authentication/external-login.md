---
id: external-login
title: External login providers
---

Browsers can sign in to Ingot through an external identity provider instead of a local password. Every provider is
disabled until you enable it, so an existing installation keeps its behaviour.

Three kinds of provider are supported:

- **OpenID Connect**: any provider that publishes a discovery document (Keycloak, Authentik, Okta, Auth0, Google,
  Microsoft Entra ID, GitLab, Gitea and so on).
- **GitHub**: an OpenID Connect entry with the icon `GITHUB` and no issuer. It uses GitHub's OAuth endpoints directly.
- **LDAP**: a user name and password checked against a directory, see [LDAP login](#ldap-login).

Provider settings live in the shared settings, in the domain `login`, under `oidcProviders`. Changes apply without a restart.

## Before you start

The first administrator is always created with the setup code that Ingot prints in its log on the first start. An external
provider cannot create the first user of an empty instance, so complete the local setup first. After that, users who sign in
through a provider are created on their first login.

Ingot builds the redirect URI that you register with the provider from the request:

```
https://<your host>/<base path>/api/auth/oidc/<provider id>/callback
```

Behind a reverse proxy that terminates TLS, forward `Host` and `X-Forwarded-Proto: https`. Otherwise the redirect URI does not
match the one you registered, and the provider refuses the login.

## OpenID Connect settings

Each entry of `oidcProviders` has these keys.

|        Key         |           Example            |                                              Description                                              |
|:------------------:|:----------------------------:|:-----------------------------------------------------------------------------------------------------:|
|         id         |          `corp`              | Slug, unique in the list (`[a-z0-9-]`). It appears in the login URL and in group references. Keep it stable |
|      enabled       |           `true`             | Shows the provider on the login page. Defaults to `false`                                              |
|       label        |     `Corporate SSO`          | Text of the login button                                                                               |
|        icon        |          `KEYCLOAK`          | `GENERIC`, `GITHUB`, `GITLAB`, `GOOGLE`, `MICROSOFT`, `KEYCLOAK`, `AUTHENTIK`, `OKTA`, `AUTH0` or `GITEA` |
|     issuerUrl      | `https://sso.example.org/realms/main` | Base URL of the provider. Discovery is read from `<issuer>/.well-known/openid-configuration`. Must be `https` |
|      clientId      |        `ingot`              | Client id registered with the provider                                                                 |
|    clientSecret    |     (secret)                | Client secret. Masked in the settings API                                                              |
|       scopes       | `openid profile email`      | Scopes requested                                                                                       |
| usernameClaim      | `preferred_username`        | Claim that gives the user name of a new user                                                           |
| displayNameClaim   | `name`                      | Claim that gives the display name                                                                      |
|    emailClaim      | `email`                     | Claim that gives the email address, used by `allowedEmailDomains`                                      |
|    groupsClaim     | `groups`                    | Claim with the groups of the user, a list or a single string                                           |
| allowedEmailDomains | `["example.org"]`          | When not empty, only these email domains can sign in. Checked at every login                           |
| autoCreateUsers    | `true`                      | Creates a user at the first login. With `false`, only users that already exist can sign in             |
|    adminGroups     | `["ingot-admins"]`          | Members of these groups become instance administrators (see below)                                     |

The provider must issue an ID token that contains the claims above. Groups are read from the ID token only, so configure the
provider to include the groups claim there.

Example, with Keycloak:

```json
{
  "oidcProviders": [
    {
      "id": "corp",
      "enabled": true,
      "label": "Corporate SSO",
      "icon": "KEYCLOAK",
      "issuerUrl": "https://sso.example.org/realms/main",
      "clientId": "ingot",
      "clientSecret": "<client secret>",
      "allowedEmailDomains": ["example.org"],
      "adminGroups": ["ingot-admins"]
    }
  ]
}
```

## GitHub

GitHub is not OpenID Connect, so it has its own entry. Create an OAuth app in GitHub with the callback URL above, then:

```json
{
  "oidcProviders": [
    {
      "id": "github",
      "enabled": true,
      "label": "GitHub",
      "icon": "GITHUB",
      "clientId": "<OAuth app client id>",
      "clientSecret": "<OAuth app client secret>"
    }
  ]
}
```

Leave `issuerUrl` empty. The scopes default to `read:user user:email read:org`. Groups are the organisations of the user
(`<org>`) and their teams (`<org>/<team-slug>`). The user name is the GitHub login, and the subject is the numeric GitHub id,
so a renamed account keeps its Ingot user.

## Groups and teams

At each login, Ingot connects the user to the teams whose external reference matches one of the groups of the user:

- OpenID Connect: `oidc:<id>:<group>`, for example `oidc:corp:platform-team`
- GitHub: `oidc:github:<org>` and `oidc:github:<org>/<team-slug>`

Create the team in Ingot with that reference (the identity API takes it as `external_ref`). Ingot never creates a team for an
ordinary group. When the group is gone at the next login, the synchronised membership is removed. Memberships that you
added by hand are never changed.

`adminGroups` work the same way. For each listed group Ingot creates a synchronised team with the reference, and that team
holds the instance administrator role. Members of the group are instance administrators while they are in it. When the group is
removed from `adminGroups`, the role is taken away again at the next login. Ingot does not take over a team of the same name
that was created by hand without the reference; it logs the group as skipped.

Group changes in the provider take effect at the user's next login. A session that is already open keeps its memberships until
it ends.

## Login errors

When a login fails, the browser returns to `/?loginError=<code>`. The codes are:

| Code | Meaning |
|---|---|
| `invalid_state` | The login took too long, or the browser did not keep the state cookie. Try again |
| `access_denied` | The user refused the login at the provider |
| `provider_error` | The provider returned an error |
| `provider_unavailable` | Ingot cannot read the discovery document or the keys of the provider. Check `issuerUrl` and the network |
| `token_exchange_failed` | The provider refused the code. Check the client id and the client secret |
| `invalid_id_token` | The ID token failed a check. Usually the issuer, the audience (client id) or the clock differs |
| `email_domain_not_allowed` | The email is missing or not in `allowedEmailDomains` |
| `user_disabled` | The Ingot user is disabled |
| `user_not_provisioned` | No user exists for this login and `autoCreateUsers` is `false` |
| `username_taken` | A local user already has the user name from the claim. Ingot never links the two |
| `invalid_username` | The user name claim is not a valid user name |
| `setup_required` | No user exists yet. Complete the first administrator setup |
| `no_database` | Browser sessions need a database |

The login page receives the code. The detail of each failure is written to the log at debug level, never a secret.

## LDAP login

The LDAP login checks the password of a user in a directory and signs the user in with the same session as the other
providers. It is configured under `login.ldap`:

|        Key         |                    Example                    |                                    Description                                    |
|:------------------:|:---------------------------------------------:|:---------------------------------------------------------------------------------:|
|      enabled       |                    `true`                     | Shows the LDAP login and accepts LDAP passwords. Defaults to `false`              |
|        url         |      `ldaps://ldap.example.org:636`           | `ldap://` or `ldaps://` address                                                   |
|      startTls     |                   `false`                     | Upgrades an `ldap://` connection with StartTLS before the first bind             |
|      bindDn        | `cn=ingot,ou=services,dc=example,dc=org`      | Service account for the search. Blank searches anonymously                        |
|   bindPassword     |                 (secret)                      | Password of the service account. Masked in the settings API                       |
|    userBaseDn     |          `ou=people,dc=example,dc=org`        | Where users are searched                                                          |
|    userFilter     |               `(uid={username})`              | Finds the user. `{username}` is replaced by the escaped user name                 |
| usernameAttribute |                    `uid`                      | Attribute that gives the user name of a new user                                  |
| displayNameAttribute |                  `cn`                      | Attribute that gives the display name                                             |
|   emailAttribute   |                    `mail`                     | Attribute that gives the email address                                            |
|   groupBaseDn      |          `ou=groups,dc=example,dc=org`        | Where groups are searched. Blank disables the group synchronisation               |
|   groupFilter      |               `(member={dn})`                 | Finds the groups of a user. `{dn}` and `{username}` are replaced by escaped values |
|  groupNameAttribute |                   `cn`                       | Attribute that gives the name of a group                                          |
| autoCreateUsers    |                   `true`                      | Creates a user at the first login. With `false`, only users that already exist    |
|    adminGroups    |               `["ingot-admins"]`              | Members of these groups become instance administrators, as for OpenID Connect    |

A login searches for the user with the service account, requires exactly one match, and then binds as that user with the
password. A blank password is refused without asking the directory. Failed passwords count towards the same limit as the local
login, so five wrong passwords for one user name from one address lock it for a while.

Group references work as for OpenID Connect, with `ldap:<group>` instead of `oidc:<id>:<group>`.

`startTls` is provided for `ldap://` addresses. Verify it against your directory before you rely on it, because it is not
covered by the automated tests of this release.

## Relation to LDAP for Maven clients

The LDAP settings of [authentication](ldap) (`authentication.ldap`) let Maven clients authenticate with Basic authentication.
They are separate from the LDAP login provider, which is configured under `login.ldap`. Ingot does not copy one into the
other. To use one directory for both, enter the same server and search details in both places. Changing one does not change
the other.

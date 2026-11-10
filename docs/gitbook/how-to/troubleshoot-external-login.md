---
description: Find the cause of a failed login through an OIDC, GitHub or LDAP provider from the loginError code, the connection test, the HTTP answer or the server log, with the exact message of each.
---

# Troubleshoot external login

A login through a provider fails in one of four places, and each place shows its reason differently. Find the place first, then the message:

* **The redirect.** The browser comes back to the dashboard with `?loginError=<code>`. The redirect carries only the code. The reason is in the server log, if it is logged at all.
* **The connection test.** The **Test connection** button of an OIDC provider, or the test of LDAP, shows the reason on the screen, after `The test failed:`.
* **The HTTP answer.** A route answers with a status code and a message in its body. The browser shows it on a blank page, and a script or `curl` reads it directly.
* **The server log.** Ingot writes the reasons of the ID token checks, the settings check and the group sync to the log only. Read the log of the instance, for example `docker logs`, or the console.

The tables below are grouped by place. The **Message** column holds the exact text, which is the text that appears in the product. The reasons in the log are not shown to the person who signs in.

## The loginError codes

Shown in the redirect as `?loginError=<code>`.

| Message | Meaning and what to do |
| --- | --- |
| `invalid_state` | The state cookie of the login is missing or expired, or the public address differs from the address in the browser. Try again in the same browser, and compare the public address with the address you use, see [Set the public address](configure-oidc-login.md#set-the-public-address). |
| `access_denied` | The person refused the consent at the provider, or the provider's assignment does not allow the person. Check the assignment at the provider. |
| `provider_error` | The provider returned an error, or no code. Read the log of the provider. |
| `provider_unavailable` | The discovery document, the key set or the token endpoint cannot be reached, or GitHub lists more teams than the login reads. Run **Test connection**. |
| `token_exchange_failed` | The token endpoint refused the code. Check the client id, the client secret and the redirect URI at the provider. An expired client secret of Entra ID gives this code too. |
| `invalid_id_token` | The ID token failed a check. Its reason is in the log, see the table of log lines below. A missing user name with automatic creation on gives this code as well. |
| `no_database` | The login could not start a session. The database is not reachable, check the database settings. |
| `setup_required` | No user exists yet. Create the first administrator locally, see [Set up the first administrator](set-up-first-admin.md). |
| `user_not_provisioned` | The person has no linked account and automatic creation is off. Turn it on, or create the account by hand. |
| `username_taken` | A local user has the same name. A provider login never takes over a local account. Rename the local user, or the provider login will keep failing. |
| `invalid_username` | The user name from the username claim is not valid. A user name may contain `@`, but not a colon, a space or a control character. Set **Username claim** to another claim. |
| `user_disabled` | The linked account is disabled. Enable it in **Access**, then **Users**. |
| `email_domain_not_allowed` | The email is missing, or its domain is not in **Allowed email domains**. |

## The connection test

Shown after `The test failed:` in the **Test connection** part of the dialog. The OIDC test reads the discovery document and the key set again, and it reports the first problem that it finds.

| Message | Meaning and what to do |
| --- | --- |
| `The setting` | A required setting is empty. The rest of the text names it, for example `The setting clientId is missing`. Fill it in and save. |
| `issuer must use https` | The issuer URL starts with `http://` and the host is not a loopback host. Use `https://`. |
| `issuer is not a URL` | The issuer URL cannot be parsed. Copy it again from the provider. |
| `not reachable` | The discovery document, the key set or the token endpoint cannot be reached from the server. Check the network, the proxy and the DNS of the server. |
| `discovery answered` | The discovery URL answered with an error status, for example `discovery answered 404` for a wrong realm or application name in the issuer. |
| `discovery document is not JSON` | The discovery URL answered, but not with a JSON document. The issuer URL probably points to a web page. |
| `discovery issuer does not match the configured issuer` | The `issuer` value of the document differs from the issuer URL, ignoring a trailing slash. Copy the issuer from the provider, exactly. |
| `discovery document lacks an endpoint` | The document has no authorization, token or key set endpoint. The issuer is not an OpenID Connect provider. |
| `key set answered` | The key set URL answered with an error status. |
| `the provider cannot be reached` | The test failed without a more specific reason. Check the network and the issuer URL. |
| `The service bind was refused` | LDAP: the bind DN or the bind password of the service account is wrong. |
| `The directory cannot be reached` | LDAP: the server address or the port is wrong, or a firewall blocks it. |
| `The directory search failed` | LDAP: the search of the user or the groups failed. Check the base DNs. |
| `No entry matches the user name` | LDAP: the user filter finds no entry. Check the user base DN and the user filter. |
| `entries match the user name, one is required` | LDAP: the user filter finds several entries. Narrow the filter. |
| `The user bind with this password was refused` | LDAP: the directory refused the password of the test user. |
| `GitHub lists more teams than the login reads` | GitHub: the user is in 5000 teams or more, so the login is refused rather than drop memberships. Narrow the organisations of the OAuth app, or reduce the teams of the user. |

## The HTTP answers

These answers come from the routes of the login. A failed `POST /api/auth/login` answers with a status code and this message in its body.

| Message | Status and meaning |
| --- | --- |
| `The login provider is not configured:` | `503` on the start route of an OIDC or GitHub login. The provider lacks a required setting, named after the colon. |
| `Unknown login provider` | `404` on the start route or the test, `400` on the login. No enabled provider has that id. |
| `The login provider cannot be reached, try again later` | `503` on the login. The provider cannot be reached right now. |
| `Login requires provider, username and password` | `400` on the login. The body lacks a field. |
| `Invalid username or password` | `401` on the login. The password is wrong, or the user does not exist. |
| `This user is disabled` | `403` on the login. |
| `Too many failed login attempts, try again later` | `429` on the login, with a `Retry-After` header in seconds. Wait out the lockout. |

## The server log

These lines are written to the server log only. They say why a `loginError` was given, or what the settings check and the group sync did. Search the log for the message.

| Message | Where and meaning |
| --- | --- |
| `the id token is not a signed JWT` | ID token check. The token is not a signed JWT, which happens when the provider returns an encrypted or unsigned token. |
| `the id token algorithm` | ID token check. The signing algorithm is not an asymmetric one. Set the provider to sign with RS256, not HS256. |
| `the id token signature does not verify` | ID token check. The signature does not match the key set. Check the issuer, and the key set of the provider. |
| `the issuer of the id token is not the configured issuer` | ID token check. The issuer in the token differs from **Issuer URL**. With Okta, the token came from another authorization server. |
| `the audience of the id token does not include this client` | ID token check. The **Client id** is not the client the token was issued to. |
| `the authorized party is not this client` | ID token check. The token has several audiences, and the `azp` claim names another client. |
| `the id token is expired` | ID token check. The clock of the server or of the provider is off. Synchronise the clocks. |
| `the nonce of the id token is not the nonce of this login` | ID token check. The login was started in another browser or the state cookie is from another login. |
| `the id token has no subject` | ID token check. The provider sent no `sub` claim. |
| `is enabled but not announced:` | Settings check, at startup and after a change. An enabled provider lacks its issuer or client id, and the line names it. |
| `is not a slug` | Settings check. The id of a provider has characters other than lowercase letters, digits and dashes, or it is longer than 64 characters. The entry is skipped. |
| `is already used by an earlier entry` | Settings check. Two providers have the same id. The second is skipped. |
| `Created the synchronised admin team` | Group sync. The admin group had no team yet, and Ingot created it. This is information, not a problem. |
| `skipped: a team of that name exists without the external reference` | Group sync. A team with the name of an admin group exists by hand. Rename or delete it, see [Map provider groups to teams](map-provider-groups-to-teams.md#when-a-team-of-that-name-exists-already). |
| `removed the last usable instance admin` | Group sync, a warning. A provider removed the last admin. The audit log holds `LAST_ADMIN_REVOKED_BY_PROVIDER` too. See [Recover admin access](recover-admin-access.md#after-a-provider-removed-the-last-admin). |

## Where to go next

* For the steps of a provider, see its guide, for example [Configure Keycloak login](configure-keycloak-login.md), [Configure Okta login](configure-okta-login.md) or [Configure Microsoft Entra ID login](configure-entra-id-login.md).
* For the groups, see [Map provider groups to teams](map-provider-groups-to-teams.md).
* For the settings and the codes in one place, see [Login providers](../reference/login-providers.md).
* If nobody can sign in as an administrator any more, see [Recover admin access](recover-admin-access.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Configure LDAP login](configure-ldap-login.md)
* [Login providers](../reference/login-providers.md)

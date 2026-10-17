---
description: Add a generic OpenID Connect provider to Ingot, register its callback URL, map claims and groups, and test the connection.
---

# Configure generic OIDC login

Ingot can let people sign in to the dashboard with an account from an OpenID Connect (OIDC) provider such as Keycloak, Authentik, Okta, Auth0 or Gitea. This guide adds one provider with the generic settings. Microsoft Entra ID, GitHub and LDAP have their own guides.

## Before you begin

* An instance administrator account that you can sign in with.
* At least one local user exists. Ingot does not create the first administrator from SSO. Create it with the setup code first, see [Enable local login and claim the first admin](set-up-first-admin.md).
* The public address of the dashboard, for example `https://repo.example.org`. Use `https://`. Plain `http://` is accepted only for the loopback hosts `localhost`, `127.0.0.1` and `[::1]`.
* A confidential client registered at your provider, with a client id and a client secret. Ask the provider for the issuer URL. Ingot reads the rest from `<issuer>/.well-known/openid-configuration`.

## Set the public address

The callback URL is built from the public address, so set it first and keep it stable. Without it, Ingot builds the callback from the address the dashboard is opened at, which can change when the dashboard is reached through another name.

1. Open **Access**, then **SSO**.
2. In the **Public address** section, enter the address in **Public URL** and select **Save public address**.

This writes the `login.publicUrl` setting.

## Register the callback URL

Register this address at your provider as the redirect URI of the client:

```text
<publicUrl>/api/auth/oidc/<id>/callback
```

Replace `<publicUrl>` with the public address and `<id>` with the provider id you choose in step 3 below. For example, a provider with the id `corp` on `https://repo.example.org` uses `https://repo.example.org/api/auth/oidc/corp/callback`.

The **Add provider** dialog shows the exact callback URL for the id you typed, with a copy button.

{% hint style="warning" %}
The provider compares the redirect URI byte for byte. A trailing slash, a different host name or `http://` instead of `https://` makes the provider refuse the login.
{% endhint %}

## Add the provider

1. Open **Access**, then **SSO**, and select **Add provider**.
2. Choose **Generic OIDC** under **Which provider is it?**. The kind fills in the default icon and scopes.
3. Enter an **Id**. Use lower case letters, digits and dashes, up to 64 characters. The id is part of the callback URL and of every group reference (`oidc:<id>:<group>`). Choose it once and do not change it later.
4. Enter the **Label on the sign in button**. The sign-in page shows a button that reads `Continue with <label>`.
5. Enter the **Issuer URL**. It must be `https://`, or `http://` for a loopback host. Ingot fetches the discovery document from it. The `issuer` value in that document must match the configured issuer, ignoring a trailing slash.
6. Enter the **Client id** and the **Client secret**.
7. Keep the **Scopes** default, `openid profile email`, unless your provider needs more. Ingot needs `openid` and an ID token.
8. Select **Save**.
9. Select **Test connection** in the dialog. It fetches the discovery document and the key set again and reports the endpoints it found. Fix any error before you continue.
10. Switch on **Enabled: offer this provider at sign in** and save again.

A provider that is enabled but lacks the issuer or the client id is not shown on the sign-in page. Ingot logs the missing field at startup.

## Claims

The claims tell Ingot which values of the ID token to use. The defaults fit most providers. Open **Advanced: claims** in the dialog to change them.

| Setting | Default claim | Used for |
| --- | --- | --- |
| Username claim | `preferred_username` | The user name of a new account. It must not contain colons, spaces or control characters. |
| Display name claim | `name` | The display name of a new account. |
| Email claim | `email` | The address that the allowed domains rule checks. |
| Groups claim | `groups` | The group names that feed team synchronisation. A claim can be a list or a single string. |

The user name and display name are read when the account is created. After that, the account stays linked to the subject of the provider, so a later change of the username claim does not rename or relink it.

## Limit who can sign in

* **Allowed email domains**: when you add domains, the email must belong to one of them. The check runs on every login, including logins of linked accounts. Letter case of the domain does not matter. A login without an email is refused when domains are set.
* **Create an account at the first sign in**: when this is on, a subject without a linked account gets a new user. When it is off, only subjects that already have a linked account can sign in. Ingot never links an existing local user to a provider subject, so switching this off does not connect people who already have a local account.

{% hint style="info" %}
A provider login never takes over a local account. If a local user has the same name as the username claim, the login is refused with `username_taken`.
{% endhint %}

## Map groups to teams and admin groups

Each team that should follow a provider group needs an external reference of the form `oidc:<id>:<group>`. Set it in **Access**, then **Teams**, in **New team** under **External reference (optional)**, or in the team detail.

At each login, Ingot does the following for the teams of this provider:

* adds the user to every team whose group the user has,
* removes the user from the teams whose group the user no longer has.

Memberships that an administrator added by hand are never changed by the sync. Group changes at the provider take effect at the next login of the user.

To make a group an instance admin group, list it under **Admin groups**. Ingot then creates a team named `oidc:<id>:<group>` if it does not exist yet, and gives that team the instance admin right. Members of the team are instance admins. The right follows the list in the settings: after you remove the group from the list, the next login through this provider removes the right from the team.

{% hint style="warning" %}
A provider that removes its last admin group member can lock every admin out of the dashboard. Ingot still applies the change, logs a warning and writes an audit event `LAST_ADMIN_REVOKED_BY_PROVIDER`. Keep a local instance admin, or keep the admin group populated.
{% endhint %}

If a team with the same name already exists without an external reference, Ingot does not take it over. It logs `Admin group ... skipped` on each login that synchronises the groups, not at startup. Rename or delete that team.

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with <label>`.
2. Select it, sign in at the provider, and check that you return to the dashboard as the expected user.
3. Open **Access**, then **Users** to check the account.

## When a login fails

After a failed login, Ingot redirects to the dashboard with `?loginError=<code>`. The codes below are the ones this provider can return.

| Code | Meaning and what to check |
| --- | --- |
| `invalid_state` | The state cookie is missing or has expired. The cookie lives 10 minutes. Try again in the same browser, and check that the public address matches the address you use. |
| `access_denied` | The user refused the consent at the provider. |
| `provider_error` | The provider returned an error, or no code. Check the provider log. |
| `provider_unavailable` | The discovery document, the token endpoint or the key set cannot be reached, or the provider is not usable. Run **Test connection**. |
| `token_exchange_failed` | The token endpoint refused the code. Check the client secret, the client id and the redirect URI. |
| `invalid_id_token` | The ID token failed a check: issuer, audience, nonce, expiry, a missing subject or a missing username. Check the issuer URL and the username claim. |
| `setup_required` | No local user exists yet. Create the first administrator, see [Enable local login and claim the first admin](set-up-first-admin.md). |
| `user_not_provisioned` | Automatic account creation is off and the subject has no linked account. |
| `username_taken` | A local user already has that name. |
| `invalid_username` | The username claim is not a valid user name. |
| `user_disabled` | The linked account is disabled. |
| `email_domain_not_allowed` | The email is missing or not in an allowed domain. |

## Related

* [Configure Microsoft Entra ID login](configure-entra-id-login.md)
* [Configure GitHub login](configure-github-login.md)
* [Configure LDAP login](configure-ldap-login.md)
* [Recover admin access](recover-admin-access.md)

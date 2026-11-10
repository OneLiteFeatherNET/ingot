---
description: Sign in to Ingot with Authentik, create the OAuth2 provider and application, keep the trailing slash of the issuer and send the groups in the ID token.
---

# Configure Authentik login

Ingot signs people in through Authentik as a generic OpenID Connect provider. The **Authentik** kind in the **Add provider** dialog sets the icon and the default scopes. The generic guide applies to everything not named here, see [Configure generic OIDC login](configure-oidc-login.md).

{% hint style="warning" %}
These steps were checked against the Authentik documentation on 2026-11-10, not against a live account. Authentik's menus change between releases, so the labels below may differ slightly in yours. The issuer and the group setting are the two points where a wrong value is easy to make.
{% endhint %}

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, set in **Access**, then **SSO**, see [Set the public address](configure-oidc-login.md#set-the-public-address).
* An Authentik administrator account, and the address of your Authentik instance, for example `https://authentik.example.org`.

## At the provider

1. In the Authentik admin interface, open **Applications**, then **Providers**, and select **Create**. Choose **OAuth2/OpenID Provider**.
2. Enter a name, for example `Ingot`. Under **Protocol settings**, set:
   * **Client type**: **Confidential**. Authentik then issues a client secret.
   * **Client ID** and **Client Secret**: keep the generated values and copy them for Ingot.
   * **Redirect URIs**: the exact callback URL of the provider id you choose in Ingot, with the mode **Strict**:

     ```text
     <publicUrl>/api/auth/oidc/<id>/callback
     ```

     For example `https://repo.example.org/api/auth/oidc/authentik/callback`.
   * **Signing Key**: an RSA key, for example the default certificate of Authentik. Ingot accepts RS256 and the other RSA and elliptic curve algorithms. It refuses HS256, so do not select a symmetric setup.
3. Under **Advanced protocol settings**, check the scopes. The provider must include the scope mapping that sends the `groups` claim. The default `profile` scope carries the group membership in a standard installation. If your installation lists separate scope mappings, add the mapping that sends the group names.
4. Keep **Issuer mode** at **Per provider**. The issuer of this mode contains the slug of the application and ends with a slash, see the next part.
5. Select **Create**. Then open **Applications**, select **Create**, enter the name `Ingot`, and choose the provider from step 2. The **Slug** of the application, for example `ingot`, appears in the issuer URL.

## In Ingot

1. Open **Access**, then **SSO**, and select **Add provider**. Choose **Authentik** under **Which provider is it?**.
2. Fill in the fields and the settings they write:

   | JSON key | Value for this provider | Dialog field |
   | --- | --- | --- |
   | `id` | `authentik`, or another slug you choose once | **Id** |
   | `enabled` | `true` after the test passes | **Enabled: offer this provider at sign in** |
   | `label` | `Authentik`, or your company name | **Label on the sign in button** |
   | `icon` | `AUTHENTIK` (set by the kind) | the kind picker |
   | `issuerUrl` | `https://authentik.example.org/application/o/ingot/` | **Issuer URL** |
   | `clientId` | the client id from step 2 | **Client id** |
   | `clientSecret` | the client secret from step 2 | **Client secret** |
   | `scopes` | `openid profile email` (the default) | **Scopes** |
   | `usernameClaim` | `preferred_username` (the default) | **Username claim**, under **Advanced: claims** |
   | `groupsClaim` | `groups` | **Groups claim**, under **Advanced: claims** |
   | `allowedEmailDomains` | your domains, for example `example.org` | **Allowed email domains** |
   | `autoCreateUsers` | `true` (the default) | **Create an account at the first sign in** |
   | `adminGroups` | the group names that are admins, if any | **Admin groups** |

3. Select **Save**, then **Test connection**. The result shows `Connected. Issuer` with the issuer that Authentik publishes.
4. Switch on **Enabled: offer this provider at sign in** and save again.

### The issuer and its trailing slash

Authentik publishes the issuer with a trailing slash, `https://authentik.example.org/application/o/ingot/`. Write it exactly like that. Ingot compares the issuer of the discovery document with the configured one and ignores a trailing slash, so both forms work. The test fails when the host or the slug differs. The **Authentik** kind shows the same shape as its hint.

The **Issuer mode** of the provider decides the shape. With **Same identifier is used for all providers**, the issuer is `https://authentik.example.org/` without the slug, and that form works as well. Use the value shown in the provider overview of Authentik, and keep it unchanged afterwards, because a change of issuer fails every login until the setting is updated.

## Claims

| Claim | Authentik sends | Ingot uses it as |
| --- | --- | --- |
| `sub` | the subject of the user | the subject that links the account |
| `preferred_username` | the username of the user | the user name of a new account |
| `name` | the name of the user | the display name |
| `email` | the email of the user, with the `email` scope | the value of the allowed domains rule |
| `groups` | the names of the groups of the user | the team sync and the admin groups |

The group names are the names of the Authentik groups, not their ids. A group named `ingot-admins` is sent as `ingot-admins`.

## Groups

Each Ingot team that should follow an Authentik group needs the external reference `oidc:authentik:<group>`, for example `oidc:authentik:developers`. The id `authentik` must match the id you chose in Ingot. Set the reference in **Access**, then **Teams**, under **External reference**.

To make a group an instance admin group, list its name under **Admin groups**, for example `ingot-admins`. Ingot then creates the team `oidc:authentik:ingot-admins` with the instance admin right. The rules for the sync, the last-admin warning and the recovery path are in [Map provider groups to teams](map-provider-groups-to-teams.md).

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with Authentik`, or the label you chose.
2. Select it, sign in with an Authentik user, and check that you return to the dashboard as the expected user.
3. Open **Access**, then **Users** to check the account. If you set a team reference, open the team and check that the user is a member.

## Troubleshooting

* **The test fails with `discovery issuer does not match the configured issuer`.** The issuer URL differs from the one Authentik publishes. Copy it again from the provider overview, including the slug and the trailing slash.
* **The login ends in `?loginError=invalid_id_token`.** The ID token failed a check. Check the issuer first, then the audience: the client id in Ingot must be the **Client ID** of the provider.
* **The login ends in `?loginError=token_exchange_failed`.** Authentik refused the code. Check the client secret, and check that the redirect URI matches exactly, including the scheme and the trailing path.
* **The user logs in, but no team is synchronised.** The `groups` claim is missing from the ID token. Check the scope mapping of step 3.
* **The test fails with `discovery answered 404`.** The application slug in the issuer URL does not exist.

For every other code and message, see [Troubleshoot external login](troubleshoot-external-login.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Map provider groups to teams](map-provider-groups-to-teams.md)
* [Troubleshoot external login](troubleshoot-external-login.md)
* [Recover admin access](recover-admin-access.md)

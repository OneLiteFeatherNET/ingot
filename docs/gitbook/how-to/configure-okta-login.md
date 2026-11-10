---
description: Sign in to Ingot with Okta, create the OIDC web application, choose an authorization server, add the groups claim to the ID token and map the groups to teams.
---

# Configure Okta login

Ingot signs people in through Okta as a generic OpenID Connect provider. The **Okta** kind in the **Add provider** dialog sets the icon and the default scopes. The generic guide applies to everything not named here, see [Configure generic OIDC login](configure-oidc-login.md).

{% hint style="warning" %}
These steps were checked against the Okta developer documentation on 2026-11-10, not against a live account. Okta changes its admin console often, so the labels below may differ slightly in yours. The authorization server and the groups claim are the two points where the recipe differs from the defaults.
{% endhint %}

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, set in **Access**, then **SSO**, see [Set the public address](configure-oidc-login.md#set-the-public-address).
* An Okta administrator account and the domain of your org, for example `example.okta.com`.

## At the provider

1. In the Okta admin console, open **Applications**, then **Applications**, and select **Create App Integration**.
2. Choose **OIDC - OpenID Connect** and the application type **Web Application**, then select **Next**.
3. Enter an app integration name, for example `Ingot`. Under **Sign-in redirect URIs**, enter the exact callback URL of the provider id you choose in Ingot:

   ```text
   <publicUrl>/api/auth/oidc/<id>/callback
   ```

   For example `https://repo.example.org/api/auth/oidc/okta/callback`. Okta compares the URI exactly.
4. Under **Assignments**, choose who may sign in, for example **Allow everyone in your organization** or specific groups. Users outside the assignment cannot sign in, whatever Ingot allows.
5. Under **Client authentication**, keep **Client secret**. This makes the client confidential. Save the application and copy the **Client ID** and the **Client secret**.

### Choose the authorization server

Okta issues the ID token from one of two authorization servers, and the choice sets the issuer that Ingot must match:

| Server | Issuer | Use it when |
| --- | --- | --- |
| The org authorization server | `https://<org-domain>` | you only need the standard claims |
| A custom authorization server, for example `default` | `https://<org-domain>/oauth2/default` | you need the groups claim |

The groups claim of this recipe is a custom claim, so the recipe uses a custom authorization server. The server `default` exists in every org. Its issuer is shown under **Security**, then **API**, then the server, as **Issuer URI**. Use that value for Ingot.

### Add the groups claim to the ID token

1. Open **Security**, then **API**, select the custom authorization server, and open the **Claims** tab.
2. Select **Add Claim** and set:

   | Field | Value |
   | --- | --- |
   | Name | `groups` |
   | Include in token type | **ID Token**, with **Always** |
   | Value type | **Groups** |
   | Filter | **Matches regex**, with the value `.*` |

3. Select **Create**.

The filter `.*` sends every group of the user. Ingot synchronises only the groups that have a team reference, so the other groups are ignored. A claim that is set to **Access Token** only gives no group sync, because Ingot reads the groups from the ID token.

## In Ingot

1. Open **Access**, then **SSO**, and select **Add provider**. Choose **Okta** under **Which provider is it?**.
2. Fill in the fields and the settings they write:

   | JSON key | Value for this provider | Dialog field |
   | --- | --- | --- |
   | `id` | `okta`, or another slug you choose once | **Id** |
   | `enabled` | `true` after the test passes | **Enabled: offer this provider at sign in** |
   | `label` | `Okta`, or your company name | **Label on the sign in button** |
   | `icon` | `OKTA` (set by the kind) | the kind picker |
   | `issuerUrl` | `https://<org-domain>/oauth2/default` | **Issuer URL** |
   | `clientId` | the Client ID from the provider part | **Client id** |
   | `clientSecret` | the Client secret from the provider part | **Client secret** |
   | `scopes` | `openid profile email` (the default) | **Scopes** |
   | `usernameClaim` | `preferred_username` (the default) | **Username claim**, under **Advanced: claims** |
   | `groupsClaim` | `groups` | **Groups claim**, under **Advanced: claims** |
   | `allowedEmailDomains` | your domains, for example `example.org` | **Allowed email domains** |
   | `autoCreateUsers` | `true` (the default) | **Create an account at the first sign in** |
   | `adminGroups` | the group names that are admins, if any | **Admin groups** |

3. Select **Save**, then **Test connection**. The result shows `Connected. Issuer` with the issuer of the server.
4. Switch on **Enabled: offer this provider at sign in** and save again.

If you use the org authorization server instead, set `issuerUrl` to `https://<org-domain>` and add the groups claim there as well, if your org offers custom claims on it. Otherwise use the custom server as above.

## Claims

| Claim | Okta sends | Ingot uses it as |
| --- | --- | --- |
| `sub` | the Okta user id | the subject that links the account |
| `preferred_username` | the Okta login, usually an email address | the user name of a new account |
| `name` | the display name of the user | the display name |
| `email` | the primary email of the user, with the `email` scope | the value of the allowed domains rule |
| `groups` | the names of the groups of the user, after the claim of step 2 | the team sync and the admin groups |

Okta usually sends an email address as `preferred_username`. Ingot accepts the `@` in a user name, but refuses colons, spaces and control characters. If the login name of your org contains such a character, set **Username claim** to another claim, for example `email`.

## Groups

Each Ingot team that should follow an Okta group needs the external reference `oidc:okta:<group>`, where `<group>` is the exact name of the Okta group. The match is case sensitive, so `Developers` and `developers` are different groups. Set the reference in **Access**, then **Teams**, under **External reference**.

To make an Okta group an instance admin group, list its exact name under **Admin groups**, for example `ingot-admins`. Ingot then creates the team `oidc:okta:ingot-admins` with the instance admin right. The rules for the sync, the last-admin warning and the recovery path are in [Map provider groups to teams](map-provider-groups-to-teams.md).

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with Okta`, or the label you chose.
2. Select it, sign in with an Okta user, and check that you return to the dashboard as the expected user.
3. Open **Access**, then **Users** to check the account. If you set a team reference, open the team and check that the user is a member.

## Troubleshooting

* **The login ends in `?loginError=invalid_id_token`, with the log line `the issuer of the id token is not the configured issuer`.** The issuer URL is the org domain, but the token came from the custom server, or the other way round. The issuer of the ID token is the server that issued it. Set `issuerUrl` to the server that holds the groups claim.
* **The login ends in `?loginError=token_exchange_failed`.** Okta refused the code. Check the client secret and the sign-in redirect URI.
* **The user logs in, but no team is synchronised.** The `groups` claim is missing from the ID token. Check that the claim is set to **ID Token**, not only to **Access Token**, and that the user is in a group that the filter matches.
* **The test fails with `discovery answered 404`.** The server id in the issuer URL is wrong, for example a custom server that was renamed.
* **The login ends in `?loginError=invalid_id_token`, and the log line names the audience.** The **Client ID** in Ingot does not match the application.

For every other code and message, see [Troubleshoot external login](troubleshoot-external-login.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Map provider groups to teams](map-provider-groups-to-teams.md)
* [Troubleshoot external login](troubleshoot-external-login.md)
* [Recover admin access](recover-admin-access.md)

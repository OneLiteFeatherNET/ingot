---
description: Sign in to Ingot with a Keycloak realm, register the confidential client, send the group names in the ID token and map them to teams.
---

# Configure Keycloak login

Ingot signs people in through Keycloak as a generic OpenID Connect provider. The **Keycloak** kind in the **Add provider** dialog sets the icon and the default scopes. The generic guide applies to everything not named here, see [Configure generic OIDC login](configure-oidc-login.md).

{% hint style="warning" %}
These steps were checked against the Keycloak server documentation on 2026-11-10, not against a live account. Menu labels change between Keycloak versions, so the names below may differ slightly in yours. The claims in the table are the ones Ingot reads from the ID token.
{% endhint %}

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, set in **Access**, then **SSO**, see [Set the public address](configure-oidc-login.md#set-the-public-address). For example `https://repo.example.org`.
* A Keycloak realm that you administer, and the realm's issuer URL: `https://<keycloak-host>/realms/<realm>`.

## At the provider

1. Sign in to the Keycloak admin console and select the realm. Keep the realm name, it is part of the issuer URL.
2. Open **Clients**, then **Create client**. Choose the client type **OpenID Connect** and enter a client id, for example `ingot`.
3. On the capability step, switch on **Client authentication**. This makes the client confidential, so Ingot sends the client secret. Leave **Standard flow** on and the other flows off.
4. On the login settings step, enter the **Valid redirect URIs** with the exact callback URL of the provider id you choose in Ingot:

   ```text
   <publicUrl>/api/auth/oidc/<id>/callback
   ```

   For example `https://repo.example.org/api/auth/oidc/keycloak/callback`. Keycloak compares the URI exactly, so do not use a wildcard for production.
5. Save the client. Open the **Credentials** tab and copy the **Client secret**.
6. Make the groups appear in the ID token. Open the client, then **Client scopes**, then the dedicated scope of the client (its name ends in `-dedicated`). Select **Add mapper**, then **By configuration**, then **Group Membership**, and set:

   | Field | Value |
   | --- | --- |
   | Name | `groups` |
   | Token Claim Name | `groups` |
   | Full group path | Off |
   | Add to ID token | On |

   With **Full group path** off, the group arrives as `developers`, not as `/developers`. Ingot matches the group name exactly, so the team references in [Groups](#groups) use the plain name.

{% hint style="info" %}
Ingot reads the groups from the ID token only. A mapper that adds the groups to the access token or to the userinfo response alone gives no group sync.
{% endhint %}

## In Ingot

1. Open **Access**, then **SSO**, and select **Add provider**. Choose **Keycloak** under **Which provider is it?**.
2. Fill in the fields and the settings they write:

   | JSON key | Value for this provider | Dialog field |
   | --- | --- | --- |
   | `id` | `keycloak`, or another slug you choose once | **Id** |
   | `enabled` | `true` after the test passes | **Enabled: offer this provider at sign in** |
   | `label` | `Keycloak`, or your company name | **Label on the sign in button** |
   | `icon` | `KEYCLOAK` (set by the kind) | the kind picker |
   | `issuerUrl` | `https://<keycloak-host>/realms/<realm>` | **Issuer URL** |
   | `clientId` | the client id from step 2 | **Client id** |
   | `clientSecret` | the secret from step 5 of the provider part | **Client secret** |
   | `scopes` | `openid profile email` (the default) | **Scopes** |
   | `usernameClaim` | `preferred_username` (the default) | **Username claim**, under **Advanced: claims** |
   | `groupsClaim` | `groups` | **Groups claim**, under **Advanced: claims** |
   | `allowedEmailDomains` | your domains, for example `example.org` | **Allowed email domains** |
   | `autoCreateUsers` | `true` (the default) | **Create an account at the first sign in** |
   | `adminGroups` | the group names that are admins, if any | **Admin groups** |

3. Select **Save**, then **Test connection**. The result shows `Connected. Issuer` with the realm URL. A realm URL with a different host name or realm name fails with `discovery issuer does not match the configured issuer`.
4. Switch on **Enabled: offer this provider at sign in** and save again.

The **Keycloak** kind has the default scopes `openid profile email`. Keep them: `profile` carries the user name and the display name.

## Claims

| Claim | Keycloak sends | Ingot uses it as |
| --- | --- | --- |
| `sub` | the user id of the realm | the subject that links the account |
| `preferred_username` | the Keycloak user name | the user name of a new account |
| `name` | the full name, when set on the user | the display name |
| `email` | the email of the user, with the `email` scope | the value of the allowed domains rule |
| `groups` | the group names, after the mapper of step 6 | the team sync and the admin groups |

Ingot does not read `email_verified`. Keycloak sends it, but Ingot does not check it. Anyone who can set an email address in the realm can therefore pass the allowed domains rule with that address. To close the gap, let the realm require verification: in the realm settings, under **Login**, switch on **Verify email**, and keep self-registration off or require the verification for it.

## Groups

Each Ingot team that should follow a Keycloak group needs the external reference `oidc:keycloak:<group>`. For a group `developers`, that is `oidc:keycloak:developers`. The id `keycloak` must be the id you chose in Ingot. Set the reference in **Access**, then **Teams**, under **External reference**.

To make a group an instance admin group, list its plain name under **Admin groups**, for example `ingot-admins`. Ingot then creates the team `oidc:keycloak:ingot-admins` with the instance admin right. The sync rules, the last-admin warning and the recovery path are in [Map provider groups to teams](map-provider-groups-to-teams.md).

Subgroups are sent by their plain name too. A user in `/ops/developers` gets the group `developers` only when the mapper is set up as in step 6 and the subgroup has that name.

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with Keycloak`, or the label you chose.
2. Select it, sign in at the realm, and check that you return to the dashboard as the expected user.
3. Open **Access**, then **Users** to check the account. If you set a team reference, open the team and check that the user is a member.

## Troubleshooting

* **The login ends in `?loginError=invalid_id_token`.** The ID token failed a check, most often the issuer. Compare the issuer URL with the `issuer` value at `https://<keycloak-host>/realms/<realm>/.well-known/openid-configuration`. Also check that the user has a user name, because `preferred_username` must be present while automatic creation is on.
* **The login ends in `?loginError=token_exchange_failed`.** Keycloak refused the code. Check the client secret, and check that **Client authentication** is on. Without it, the client is public and sends no secret.
* **`?loginError=invalid_state`.** The state cookie expired or the public address differs from the address you used. Try again in the same browser.
* **The user logs in, but no team is synchronised.** The `groups` claim is missing from the ID token. Check the mapper of step 6, and check **Add to ID token**. In the test login, the group must appear with its plain name in the ID token.
* **The test fails with `discovery answered 404`.** The realm name in the issuer URL is wrong.

For every other code and message, see [Troubleshoot external login](troubleshoot-external-login.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Map provider groups to teams](map-provider-groups-to-teams.md)
* [Troubleshoot external login](troubleshoot-external-login.md)
* [Recover admin access](recover-admin-access.md)

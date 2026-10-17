---
description: Let people sign in to Ingot with their GitHub account, register the OAuth app and map GitHub organisations and teams to Ingot teams.
---

# Configure GitHub login

Ingot can let people sign in with GitHub. Ingot uses GitHub's OAuth endpoints directly. GitHub issues no ID token, so the identity comes from GitHub's API. The provider is configured in the same list as the other OIDC providers, with the **GitHub** kind.

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, see [Set the public address](configure-oidc-login.md#set-the-public-address). The callback URL is built from it.
* Owner rights on the GitHub organisation, or on your user account, to register an OAuth app.

## Register the OAuth app

1. In GitHub, open **Settings**, then **Developer settings**, then **OAuth Apps**, and select **New OAuth App**. For an organisation, use the organisation's settings instead.
2. Enter an application name and a homepage URL, for example the dashboard address.
3. Set the **Authorization callback URL** to the callback of the provider:

   ```text
   <publicUrl>/api/auth/oidc/github/callback
   ```

   The id `github` is the one you choose in Ingot below. Use the same id in the address.
4. Register the application, then generate a **client secret**.
5. Record the **Client ID** and the client secret.

## Add the provider in Ingot

1. Open **Access**, then **SSO**, and select **Add provider**.
2. Choose **GitHub** under **Which provider is it?**. The kind sets the icon and the scopes.
3. Enter the **Id**, for example `github`.
4. Enter the **Label on the sign in button**, for example `GitHub`.
5. Leave the **Issuer URL** empty. Ingot then uses GitHub's endpoints.
6. Enter the **Client id** and the **Client secret**.
7. Keep the scopes empty. Ingot then requests `read:user user:email read:org`. These scopes are needed to read the profile, the verified email and the organisation teams.
8. Select **Save**.
9. Select **Test connection**. For GitHub, the test does not call GitHub. It reports the endpoints that Ingot is configured to use, so a successful test does not prove that the client id or the secret is right. Only a real sign-in does.
10. Switch on **Enabled: offer this provider at sign in** and save again.

{% hint style="info" %}
An issuer is needed only for an OpenID Connect bridge in front of GitHub. When you enter one other than `https://github.com`, Ingot treats the provider as generic OIDC and uses discovery. See [Configure generic OIDC login](configure-oidc-login.md).
{% endhint %}

## What Ingot reads from GitHub

| Value | Source | Used as |
| --- | --- | --- |
| Subject | Numeric user id | Link to the Ingot account. It does not change when the login is renamed. |
| User name | GitHub login | The Ingot user name of a new account. The username claim setting does not apply. |
| Display name | Profile name | The display name of a new account. |
| Email | Public email, or else the primary verified email | The allowed email domains rule. |
| Groups | Organisations and teams of the user | Team synchronisation. |

For each team of the user, Ingot adds two group names: the organisation login, and `<organisation>/<team-slug>`. A user who is a member of an organisation but of no team of it gets no organisation group.

Ingot reads up to 50 pages of 100 teams. A user with 5000 teams or more fails the login with `provider_unavailable`, because a partial list would remove memberships.

## Map groups to teams

Give the Ingot team the external reference of the group it follows:

* `oidc:github:<organisation>` for all members of the organisation that belong to any team,
* `oidc:github:<organisation>/<team-slug>` for one GitHub team.

Set the reference in **Access**, then **Teams**, under **External reference**. The sync and admin group behaviour is described in [Configure generic OIDC login](configure-oidc-login.md#map-groups-to-teams-and-admin-groups).

Use the **Admin groups** list with the same names, for example `my-org/admins`. Ingot then makes the team and gives it the instance admin right.

{% hint style="warning" %}
Some organisations require an owner to approve an OAuth app before members can share their organisation data with it. Until approval, the teams of those members may not appear in the answer, and the group sync removes their memberships. Check the approval status of the app in the organisation settings. This behaviour comes from GitHub and was not checked against the code.
{% endhint %}

## When a login fails

The codes are the same as for the other providers, see [Configure generic OIDC login](configure-oidc-login.md#when-a-login-fails). For GitHub, these are the most common ones:

* `token_exchange_failed`: GitHub refused the code. Check the client id, the client secret and the callback URL.
* `provider_unavailable`: GitHub could not be reached, or the user has 5000 teams or more.
* `email_domain_not_allowed`: the account has no verified primary email, and allowed email domains are set.

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Configure Microsoft Entra ID login](configure-entra-id-login.md)
* [Recover admin access](recover-admin-access.md)

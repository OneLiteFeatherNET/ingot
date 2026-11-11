---
description: Let people sign in to Ingot with their GitHub account, register the OAuth app and map GitHub organisations and teams to Ingot teams.
---

# Configure GitHub login

Ingot can let people sign in with GitHub. Ingot uses GitHub's OAuth endpoints directly. GitHub issues no ID token, so the identity comes from GitHub's API. The provider is configured in the same list as the other OIDC providers, with the **GitHub** kind.

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, see [Set the public address](configure-oidc-login.md#set-the-public-address). The callback URL is built from it.
* Owner rights on the GitHub organisation, or on your user account, to register an OAuth app.

## At the provider

1. In GitHub, open **Settings**, then **Developer settings**, then **OAuth Apps**, and select **New OAuth App**. For an organisation, use the organisation's settings instead.
2. Enter an application name and a homepage URL, for example the dashboard address.
3. Set the **Authorization callback URL** to the callback of the provider:

   ```text
   <publicUrl>/api/auth/oidc/github/callback
   ```

   The id `github` is the one you choose in Ingot below. Use the same id in the address.
4. Register the application, then generate a **client secret**.
5. Record the **Client ID** and the client secret.

## In Ingot

1. Open **Access**, then **SSO**, and select **Add provider**.
2. Choose **GitHub** under **Which provider is it?**. The kind sets the icon and the scopes.
3. Fill in the fields and the settings they write:

   | JSON key | Value for this provider | Dialog field |
   | --- | --- | --- |
   | `id` | `github`, or another slug you choose once | **Id** |
   | `enabled` | `true` after the test passes | **Enabled: offer this provider at sign in** |
   | `label` | `GitHub`, or your company name | **Label on the sign in button** |
   | `icon` | `GITHUB` (set by the kind) | the kind picker |
   | `issuerUrl` | empty, so Ingot uses GitHub's endpoints | **Issuer URL** |
   | `clientId` | the Client ID from the provider part | **Client id** |
   | `clientSecret` | the client secret from the provider part | **Client secret** |
   | `scopes` | empty, so Ingot requests `read:user user:email read:org` | **Scopes** |
   | `adminGroups` | the names of the admin organisations or teams, if any | **Admin groups** |
   | `allowedEmailDomains` | your domains, if you want the rule | **Allowed email domains** |
   | `autoCreateUsers` | `true` (the default) | **Create an account at the first sign in** |

4. Keep the scopes empty. Ingot then requests `read:user user:email read:org`. These scopes are needed to read the profile, the verified email and the organisation teams.
5. Select **Save**.
6. Select **Test connection**. For GitHub, the test does not call GitHub. It reports the endpoints that Ingot is configured to use, so a successful test does not prove that the client id or the secret is right. Only a real sign-in does.
7. Switch on **Enabled: offer this provider at sign in** and save again.

{% hint style="info" %}
An issuer is needed only for an OpenID Connect bridge in front of GitHub. When you enter one other than `https://github.com`, Ingot treats the provider as generic OIDC and uses discovery. See [Configure generic OIDC login](configure-oidc-login.md).
{% endhint %}

## Claims

Ingot does not read the claim settings for GitHub. It reads the following values from GitHub's API:

| Value | Source | Used as |
| --- | --- | --- |
| Subject | Numeric user id | Link to the Ingot account. It does not change when the login is renamed. |
| User name | GitHub login | The Ingot user name of a new account. The username claim setting does not apply. |
| Display name | Profile name | The display name of a new account. |
| Email | Public email, or else the primary verified email | The allowed email domains rule. |
| Groups | Organisations and teams of the user | Team synchronisation. |

For each team of the user, Ingot adds two group names: the organisation login, and `<organisation>/<team-slug>`. A user who is a member of an organisation but of no team of it gets no organisation group.

Ingot reads up to 50 pages of 100 teams. A user with 5000 teams or more fails the login with `provider_unavailable`, because a partial list would remove memberships.

## Groups

Give the Ingot team the external reference of the group it follows:

* `oidc:github:<organisation>` for all members of the organisation that belong to any team,
* `oidc:github:<organisation>/<team-slug>` for one GitHub team.

Set the reference in **Access**, then **Teams**, under **External reference**. The sync and admin group behaviour is described in [Map provider groups to teams](map-provider-groups-to-teams.md).

Use the **Admin groups** list with the same names, for example `my-org/admins`. Ingot then makes the team and gives it the instance admin right.

{% hint style="warning" %}
Some organisations require an owner to approve an OAuth app before members can share their organisation data with it. Until approval, the teams of those members may not appear in the answer, and the group sync removes their memberships. Check the approval status of the app in the organisation settings. This behaviour comes from GitHub and was not checked against the code.
{% endhint %}

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with GitHub`, or the label you chose.
2. Select it, authorise the app, and check that you return to the dashboard as the expected user.
3. Open **Access**, then **Users** to check the account. If you set a team reference, open the team and check that the user is a member.

## Troubleshooting

* **`?loginError=token_exchange_failed`.** GitHub refused the code. Check the client id, the client secret and the callback URL.
* **`?loginError=provider_unavailable`.** GitHub could not be reached, or the user has 5000 teams or more. The log line `GitHub lists more teams than the login reads` names the second case.
* **`?loginError=email_domain_not_allowed`.** The account has no verified primary email, and allowed email domains are set.
* **The user logs in, but no team is synchronised.** The organisation has not approved the OAuth app, see the warning above.

For every other code and message, see [Troubleshoot external login](troubleshoot-external-login.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Map provider groups to teams](map-provider-groups-to-teams.md)
* [Troubleshoot external login](troubleshoot-external-login.md)
* [Configure Microsoft Entra ID login](configure-entra-id-login.md)
* [Recover admin access](recover-admin-access.md)

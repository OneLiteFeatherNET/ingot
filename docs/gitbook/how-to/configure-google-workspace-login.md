---
description: Sign in to Ingot with Google Workspace accounts, create the OAuth web client, use the email as user name, restrict the domains and understand why Google gives no groups.
---

# Configure Google Workspace login

Ingot signs people in with Google accounts through the generic OpenID Connect provider. The **Google** kind in the **Add provider** dialog sets the icon, the issuer hint and the default scopes. Two things differ from the other providers: Google sends no user name claim, and Google sends no groups. Both are covered below. The generic guide applies to everything else, see [Configure generic OIDC login](configure-oidc-login.md).

{% hint style="warning" %}
These steps were checked against the Google Identity documentation on 2026-11-10, not against a live account. The Google Cloud console is reorganised from time to time, so the menu names below may differ in yours. The claims were checked against Google's list of ID token claims.
{% endhint %}

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, set in **Access**, then **SSO**, see [Set the public address](configure-oidc-login.md#set-the-public-address).
* A Google Cloud project that you can administer, and the primary domain of your Google Workspace, for example `example.org`.

## At the provider

1. In the Google Cloud console, select the project and open **Google Auth Platform**, then **Branding**. Enter an application name, for example `Ingot`, and a support email.
2. Open **Audience**. If your Google Workspace organization offers the user type **Internal**, choose it. An internal application can be used only by accounts of your organization, which is the restriction a company login needs. An external application can be used by any Google account, and the domain rule of Ingot is then the only limit.
3. Open **Data access** and add the scopes `openid`, `profile` and `email`. These are the default scopes of Ingot.
4. Open **Clients**, then **Create client**. Choose the application type **Web application**.
5. Under **Authorized redirect URIs**, add the exact callback URL of the provider id you choose in Ingot:

   ```text
   <publicUrl>/api/auth/oidc/<id>/callback
   ```

   For example `https://repo.example.org/api/auth/oidc/google/callback`. Google compares the URI exactly.
6. Select **Create**. Copy the **Client ID** and the **Client secret**.

{% hint style="info" %}
A test application that is not yet published accepts sign-ins only from the test users you add. Add the accounts that should sign in, or publish the application for your organization.
{% endhint %}

## In Ingot

1. Open **Access**, then **SSO**, and select **Add provider**. Choose **Google** under **Which provider is it?**.
2. Fill in the fields and the settings they write:

   | JSON key | Value for this provider | Dialog field |
   | --- | --- | --- |
   | `id` | `google`, or another slug you choose once | **Id** |
   | `enabled` | `true` after the test passes | **Enabled: offer this provider at sign in** |
   | `label` | `Google`, or your company name | **Label on the sign in button** |
   | `icon` | `GOOGLE` (set by the kind) | the kind picker |
   | `issuerUrl` | `https://accounts.google.com` | **Issuer URL** |
   | `clientId` | the Client ID from the provider part | **Client id** |
   | `clientSecret` | the Client secret from the provider part | **Client secret** |
   | `scopes` | `openid profile email` (the default) | **Scopes** |
   | `usernameClaim` | `email` | **Username claim**, under **Advanced: claims** |
   | `emailClaim` | `email` (the default) | **Email claim**, under **Advanced: claims** |
   | `allowedEmailDomains` | your Workspace domain, for example `example.org` | **Allowed email domains** |
   | `autoCreateUsers` | `true` (the default) | **Create an account at the first sign in** |

3. Select **Save**, then **Test connection**. The result shows `Connected. Issuer https://accounts.google.com`.
4. Switch on **Enabled: offer this provider at sign in** and save again.

{% hint style="warning" %}
Set **Username claim** to `email`. Google sends no `preferred_username` claim. With the default value and automatic creation on, every first login fails with `?loginError=invalid_id_token`.
{% endhint %}

Leave **Allowed email domains** set to your domain. Without it, any Google account can create a user in Ingot. Ingot compares the domain of the `email` claim with the list, ignoring letter case.

## Claims

| Claim | Google sends | Ingot uses it as |
| --- | --- | --- |
| `sub` | the Google account id, which is never reused | the subject that links the account |
| `email` | the email address, with the `email` scope | the user name of a new account, and the value of the allowed domains rule |
| `name` | the full name, when Google provides it | the display name. Without it, the user name is the display name |
| `email_verified` | `true` or `false` | not read by Ingot, see the next part |
| `hd` | the primary domain of a Workspace account | not read by Ingot |

### Email verification is not checked

Ingot does not read `email_verified`. The domain rule trusts the `email` claim as Google sends it. The addresses of a Workspace domain are managed by your organization, so an account of that domain is the expected sign-in. With an external application, a Google account outside your organization can still send an address of your domain that it does not control, and Ingot would accept it. An internal application closes that gap, because only accounts of the organization can sign in, see step 2 of the provider part.

## Groups

Google sends no groups in the ID token. Ingot therefore cannot synchronise teams from Google, and the groups settings of this provider have no effect. Nothing fails at login: the user signs in, and the user gets no team membership from Google.

What follows from that:

* Add the people to teams by hand, in **Access**, then **Teams**. See [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md). A manual membership is never changed by a login.
* Do not list groups under **Admin groups** for this provider. Grant the instance admin right to a team of your choice by hand instead, or keep a local instance admin, see [Set up the first administrator](set-up-first-admin.md).
* If the company needs group synchronisation with Google as the sign-in, put a provider in front that sends groups, such as Keycloak or Okta, and let it use Google as its own identity provider. The recipes for [Keycloak](configure-keycloak-login.md) and [Okta](configure-okta-login.md) cover the group side.

## Check the result

1. Open the dashboard in a private browser window. The sign-in page shows the button `Continue with Google`, or the label you chose.
2. Select it and sign in with an account of your Workspace domain. Check that you return to the dashboard as the expected user, named by the email address.
3. Open **Access**, then **Users** to check the account.
4. Sign in once with an account from another domain. The login must end in `?loginError=email_domain_not_allowed`.

## Troubleshooting

* **The login ends in `?loginError=invalid_id_token`.** Most often the username claim is still `preferred_username`. Set it to `email`. The log line names the check that failed, for example `the audience of the id token does not include this client` when the client id in Ingot is not the Client ID of the Google client.
* **The login ends in `?loginError=email_domain_not_allowed`.** The account is not in the allowed domains, or the Google account has no email. This is the expected answer for an account of another domain.
* **The login ends in `?loginError=token_exchange_failed`.** Google refused the code. Check the client secret, and check that the redirect URI in Google matches the callback URL exactly, including the scheme.
* **Google shows `Error 400: redirect_uri_mismatch`.** The redirect URI in the Google client differs from the callback of Ingot. Copy the URI from the **Add provider** dialog again.
* **The test fails with `discovery issuer does not match the configured issuer`.** The issuer must be `https://accounts.google.com` with no trailing path.

For every other code and message, see [Troubleshoot external login](troubleshoot-external-login.md).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Map provider groups to teams](map-provider-groups-to-teams.md)
* [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md)
* [Troubleshoot external login](troubleshoot-external-login.md)

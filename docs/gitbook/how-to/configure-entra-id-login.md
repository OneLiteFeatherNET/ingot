---
description: Sign in to Ingot with Microsoft Entra ID by configuring it as a generic OpenID Connect provider with the tenant issuer and the groups claim.
---

# Configure Microsoft Entra ID login

Ingot has no Entra ID code of its own. You sign in with Entra ID through the generic OpenID Connect provider, with the issuer of your tenant. The **Microsoft** kind in the **Add provider** dialog only sets the icon and the scopes. The steps in the generic guide apply, and this page lists what is specific to Entra ID.

{% hint style="warning" %}
The Entra ID steps below were checked against Microsoft's Entra ID documentation. They were not checked against a live tenant, and the portal labels may differ slightly from the names used here.
{% endhint %}

## Before you begin

* An instance administrator account and at least one local user, see [Configure generic OIDC login](configure-oidc-login.md#before-you-begin).
* The public address of the dashboard, set in **Access**, then **SSO**, see [Set the public address](configure-oidc-login.md#set-the-public-address).
* An account in the Microsoft Entra admin center with at least the Application Developer role.

## Register the application

1. Sign in to the [Microsoft Entra admin center](https://entra.microsoft.com) and make sure the right tenant is selected.
2. Go to **Entra ID**, then **App registrations**, and select **New registration**.
3. Enter a name, for example `Ingot`.
4. Under **Supported account types**, select **Single tenant only** for a single organization. Microsoft recommends this option for most applications.
5. Select **Register**.
6. On the **Overview** page, record the **Application (client) ID** and the **Directory (tenant) ID**.

Ingot needs the tenant ID for the issuer URL, and the application ID as the client id.

{% hint style="info" %}
Do not use the multitenant `common` or `organizations` endpoint as the issuer. Ingot compares the issuer in the discovery document with the configured one, and a multitenant issuer does not match. Use the tenant ID of one tenant.
{% endhint %}

## Add the redirect URI

1. Open the registration and go to the authentication settings. Add a **Web** platform with the redirect URI:

   ```text
   <publicUrl>/api/auth/oidc/<id>/callback
   ```

   For example `https://repo.example.org/api/auth/oidc/entra/callback`.
2. Save the change.

The id `entra` is a choice you make in Ingot. It is used in this address and in the team references, so it must match the id in the Ingot dialog.

## Create a client secret

1. Open **Certificates & secrets** and select **New client secret**.
2. Copy the secret **value** when it is shown. Entra ID does not show it again.
3. Enter it as the **Client secret** in Ingot.

Secrets expire. Record the expiry date and create the next secret in time, then update it in Ingot. A refused secret makes the login fail with `token_exchange_failed`.

## Add the provider in Ingot

1. Open **Access**, then **SSO**, and select **Add provider**.
2. Choose **Microsoft** under **Which provider is it?**.
3. Enter the **Id**, for example `entra`.
4. Enter the **Label on the sign in button**, for example `Microsoft`.
5. Enter the **Issuer URL** with your tenant ID:

   ```text
   https://login.microsoftonline.com/<tenant-id>/v2.0
   ```

   The `/v2.0` ending is required. Entra ID puts the same value in the `iss` claim of v2.0 tokens, and Ingot checks it.
6. Enter the **Client id** (the application ID) and the **Client secret**.
7. Keep the scopes `openid profile email`.
8. Select **Save**, then **Test connection**. A correct setup reports the issuer and the endpoints.
9. Switch on **Enabled: offer this provider at sign in** and save again.

## Claims

The defaults fit Entra ID. Check them under **Advanced: claims**:

| Setting | Value | Notes |
| --- | --- | --- |
| Username claim | `preferred_username` | Present in v2.0 ID tokens when the `profile` scope is requested. It is usually the sign-in name, which may contain `@`. Ingot accepts `@`, but refuses colons, spaces and control characters. |
| Display name claim | `name` | Requires the `profile` scope. |
| Email claim | `email` | Not every account has an email claim. When you set allowed email domains, an account without one is refused. |
| Groups claim | `groups` | Only present when you configure a groups claim, see below. |

Microsoft documents `preferred_username` as mutable and says that it must not be used to make authorization decisions. Ingot uses it only to name a new account. After that, the account stays linked to the subject, see [Configure generic OIDC login](configure-oidc-login.md#claims).

## Send groups in the token

Ingot reads group membership from the `groups` claim of the ID token. Entra ID does not send it unless you configure it.

1. In the app registration, open **Token configuration** and select **Add groups claim**. If the label differs in your portal, look for the groups claim option in the token settings.
2. Choose **Groups assigned to the application**, or **Security groups** for a small tenant.
3. Save.

According to Microsoft, the groups claim carries group **object IDs** by default. Ingot therefore names the teams after object IDs, not after group display names:

```text
oidc:entra:<group-object-id>
```

Set that value as **External reference** on the Ingot team, see [Configure generic OIDC login](configure-oidc-login.md#map-groups-to-teams-and-admin-groups). Use the object ID from the Entra ID group page.

{% hint style="warning" %}
Entra ID stops sending the groups list in the token when a user is in more than 200 groups, and sends a link to Microsoft Graph instead. Ingot reads only the `groups` claim, so such a user gets no group sync at that login. Choose **Groups assigned to the application** to keep the list short, and assign only the groups that Ingot uses.
{% endhint %}

## Admin groups

To make an Entra ID group an instance admin group, add its object ID under **Admin groups**. Ingot then creates the team `oidc:entra:<group-object-id>` with the instance admin right. A user who is removed from that group at Entra ID loses the membership at the user's next login. If that user was the last admin, the instance has no admin until another one signs in, so keep a second instance admin. See [Configure generic OIDC login](configure-oidc-login.md#map-groups-to-teams-and-admin-groups).

## Verify the result

1. Open the dashboard in a private browser window and select the **Continue with Microsoft** button, or the label you chose.
2. Sign in with an account of the tenant.
3. Check that the dashboard opens as the expected user. If the login fails, the URL shows `?loginError=<code>`. The codes are listed in [Configure generic OIDC login](configure-oidc-login.md#when-a-login-fails).

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Recover admin access](recover-admin-access.md)

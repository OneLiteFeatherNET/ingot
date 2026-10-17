---
description: Tutorial. Create a team, add a user, give the team permission nodes on a private repository, and add a generic OpenID Connect provider so that people can sign in with it.
---

# Set up users, teams and single sign on

In this tutorial you give a person access to one private repository without making that person an administrator. You create a team, grant the team read access through a permission node, add a user to the team and check what the user can see. Last, you add an OpenID Connect provider, so that people can sign in with an account from your identity provider.

Plan for about 45 minutes.

## What you need

* An Ingot instance where you are signed in as an instance administrator. Finish [First steps with Docker](first-steps-with-docker.md) first, or use any instance with an administrator account.
* A repository named `private` with visibility **Private**. A fresh instance has one. If yours does not, create one with the repository wizard and set **Visibility** to **Private**.
* For steps 6 and 7 only: an OpenID Connect provider where you can register a client, for example Keycloak, Authentik or Microsoft Entra ID.

## 1. Create a team

A team groups people, so that you can give access to the group instead of to each person.

1. Select **Access**, then select the **Teams** tab.
2. Select **New team**.
3. Enter `readers` in **Name**. Leave the other fields empty.
4. Select **Create team**.

You should see `readers` in the list of teams.

## 2. Give the team read access to the private repository

Ingot grants access with permission nodes. A node names an action on a repository. The node `ingot.repository.private.read` lets the members of a team read the repository `private`. The actions are `read`, `deploy`, `delete` and `manage`.

1. Select the team `readers` in the list.
2. In **Permission nodes**, enter `ingot.repository.private.read` in **Add a node**, then press Enter.
3. Check that the node shows **Allowed**.
4. Select **Save** in the bar at the bottom of the page.

You should see the node after you reload the page.

## 3. Create a user

1. Select **Access**, then select the **Users** tab.
2. Select **New user**.
3. Enter `alice` in **Username**.
4. Enter a password of at least 12 characters in **Initial password**.
5. Select **Create user**.

You should see `alice` in the list of users. Alice has to change the initial password at her first sign in.

## 4. Add the user to the team

1. Open the team `readers` and find the **Members** card.
2. Select **Add member**.
3. Enter `alice` in **Search by name or display name** and tick her in the list.
4. Select **Add member**.

You should see `alice` under **Members**. Alice now has the permission node of the team.

## 5. Check what the user can see

1. Select **Logout** in the account menu.
2. Select **Sign in**, then sign in as `alice` with the initial password.
3. The dashboard opens **My account** and asks for a new password. Set one and keep it safe.
4. Select **Overview**. The repository `private` is listed.
5. Select **Logout**, then open the **Overview** again without signing in. The repository `private` is not listed.

Alice has no **Access** tab, because she has no permission to manage users, teams or settings.

## 6. Add a generic OpenID Connect provider

Users can sign in with an account from an identity provider, so they do not need a password in Ingot. Ingot creates the user the first time that person signs in.

Sign in as the administrator again, then follow these steps.

1. Select **Access**, then select the **SSO** tab.
2. Under **Public address**, enter the address that users open the dashboard at, for example `https://repo.example.org`. A plain `http://` address works only for `localhost`. Select **Save public address**.
3. Select **Add provider**, then select the kind **Generic OIDC**.
4. Fill in the fields:
   * **Label on the sign in button**: `Example SSO`.
   * **Id**: `example`. Use lowercase letters, digits and dashes. The id is part of the callback URL, so you cannot change it later.
   * **Issuer URL**: the base URL of your provider, for example `https://sso.example.org/realms/ingot`. Ingot reads the discovery document from `/.well-known/openid-configuration` below this address.
   * **Client id** and **Client secret**: the values of the client that you registered at the provider.
   * Keep the scopes and the claim names at their defaults unless your provider uses other names.
5. Turn on **Enabled: offer this provider at sign in**.
6. Select **Save**.
7. Copy the **Callback URL** that the dialog shows and register it as a redirect URI of the client at your provider. The two must match exactly.
8. Open the provider again and select **Test connection**. The list shows the result as **Connected** or **Test failed**.

{% hint style="info" %}
For Microsoft Entra ID, use the issuer `https://login.microsoftonline.com/<tenant-id>/v2.0` with the ID of your tenant. The multi-tenant address `common` does not work, because its issuer does not match.
{% endhint %}

You should see the provider in the list with its **Enabled** switch on.

## 7. Sign in with the provider

1. Select **Logout**.
2. Select **Sign in**. A button named **Continue with Example SSO** appears in the dialog.
3. Select the button and sign in at your provider.

You should see your name after the word **Welcome** in the header.

The new user has no permission nodes and no team yet. Add the user to a team, as in step 4, before the user can read anything.

{% hint style="warning" %}
If the button does not appear, the provider is off or it lacks a required value, such as the issuer URL or the client id. The server log names the missing field at startup.
{% endhint %}

## Next steps

* [First steps with Docker](first-steps-with-docker.md) shows how to create the repository and the deploy token that this tutorial relies on.
* [Set up the first administrator](../how-to/set-up-first-admin.md) explains the setup code in detail.

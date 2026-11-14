---
description: Map the groups of an OIDC, GitHub or LDAP provider to Ingot teams, let a group grant the instance admin right, and recover when a provider removes the last admin.
---

# Map provider groups to teams

A login through an external provider can keep the teams of Ingot in step with the groups of the person at the provider. This guide explains the reference that links a team to a group, what a login adds and removes, how an admin group grants the instance admin right, and what to do when the last admin loses it. The provider guides link here instead of repeating these rules.

## Before you begin

* The provider is enabled and a test login works, see the guide of your provider, for example [Configure generic OIDC login](configure-oidc-login.md) or [Configure LDAP login](configure-ldap-login.md).
* You know the exact name of each group as the provider sends it. The match is exact and case sensitive.
* An instance administrator who is not synchronised from a provider, so that a group change at the provider cannot lock you out.

## The external reference of a team

A team follows a group when its external reference has this form:

| Provider | Reference | Example |
| --- | --- | --- |
| OpenID Connect (generic, Keycloak, Authentik, Okta, Google, Entra ID) | `oidc:<id>:<group>` | `oidc:keycloak:developers` |
| GitHub, all members of an organisation with a team | `oidc:<id>:<organisation>` | `oidc:github:my-org` |
| GitHub, one team of an organisation | `oidc:<id>:<organisation>/<team-slug>` | `oidc:github:my-org/admins` |
| LDAP | `ldap:<group>` | `ldap:developers` |

The `<id>` is the id of the provider in **Access**, then **SSO**, and it cannot change without breaking the references. For LDAP, `<group>` is the value of the group name attribute of the directory entry.

To set the reference, open **Access**, then **Teams**, and select **New team**. Enter the reference under **External reference (optional)**. For a team that already exists, open its detail and set the reference there. A team without a reference is never touched by a login.

Setting, changing or clearing a reference needs the admin area `ingot.admin.idp`, or `ingot.admin.*`. The area `ingot.admin.teams` alone is not enough, because a linked team passes its rights to every member of the group at the next login. Without the area the write is refused with 403, and the audit log records it as `EXTERNAL_REF_UNGUARDED`. Repeating the current reference is no change and needs nothing. References that exist already are kept as they are; the check applies to later writes. The switch `INGOT_IDENTITY_IDP_GUARD` can set `warn` as a temporary fallback for automation that is not yet granted the area, see [Environment variables](../reference/environment-variables.md#identity-variables).

{% hint style="info" %}
Entra ID sends the group object ids by default, not the group names. Its reference is `oidc:entra:<group-object-id>`, see [Configure Microsoft Entra ID login](configure-entra-id-login.md#groups).
{% endhint %}

## What a login does

At each login through a provider, Ingot compares the groups of the person with the teams that have a reference of that provider:

* the person is added to every such team whose group the person has,
* the person is removed from the teams of that provider whose group the person no longer has.

Memberships that an administrator added by hand are never changed by a login. A team of another provider is not touched either. Changes at the provider take effect at the next login of the person, not while a session is running.

Each membership that the sync adds or removes is written to the audit log as `TEAM_MEMBERSHIP_CHANGED`, with the actor `system:<provider>`. A login that changes nothing writes nothing.

## Admin groups

To make a group grant the instance admin right, list its name under **Admin groups** in the provider settings. The list takes the names as the provider sends them, for example `ingot-admins` for OIDC, `my-org/admins` for GitHub or `developers` for LDAP.

For each listed group, Ingot creates a team with the reference of that group if it does not exist yet. The team is named after the reference, for example `oidc:keycloak:ingot-admins`, and it holds the instance admin right. Members of the team are instance admins.

The right follows the list. When a group is removed from **Admin groups**, the next login through that provider removes the right from its team. The team itself stays, and its members are not removed by the admin rule.

{% hint style="info" %}
A person never receives the admin right directly. It always comes through a team, so the way to remove it is to remove the person from the group at the provider, or to remove the group from the list.
{% endhint %}

### When a team of that name exists already

If a team with the same name exists without an external reference, Ingot does not take it over. It logs `skipped: a team of that name exists without the external reference` on each login that synchronises the groups, and creates nothing. Rename or delete the team, and the next login creates the synchronised one.

## The last admin

A provider can remove the last instance admin from the instance, for example when the admin group is emptied at the provider. Ingot still applies the change, so that the provider stays authoritative. It then:

* logs a warning that names the principal and says that the synchronised admin group no longer grants the admin right,
* writes the audit event `LAST_ADMIN_REVOKED_BY_PROVIDER` with the provider and the principal.

{% hint style="warning" %}
With no instance admin left, nobody can change the teams in the dashboard. Keep a local instance admin, or keep at least two members in each admin group at the provider.
{% endhint %}

If it happens anyway, follow [Recover admin access](recover-admin-access.md#after-a-provider-removed-the-last-admin). It describes how to restore the group at the provider, or how to start Ingot with a temporary admin token and grant the right again.

## Check the result

1. Sign in through the provider with a person who is in a group that has a team. Open **Access**, then **Teams**, and check that the person is a member.
2. Remove the person from the group at the provider, and sign in again. The membership must be gone.
3. Open the audit log, see [Review the audit log](review-audit-log.md), and check the `TEAM_MEMBERSHIP_CHANGED` events of the test.

## Related

* [Configure generic OIDC login](configure-oidc-login.md)
* [Configure LDAP login](configure-ldap-login.md)
* [Recover admin access](recover-admin-access.md)
* [Login providers](../reference/login-providers.md)

---
description: Create users, machine accounts and teams in Ingot, add members and parent teams, grant or remove the instance admin role, and disable or delete a principal, in the dashboard or with the identity API.
---

# Manage users, teams and machine accounts

Ingot has three kinds of identity. A user is a person who signs in with a password or through a login provider. A machine account is a build or a script that signs in with tokens. A team groups principals and holds the permission nodes that give them their rights. Users and machine accounts are both principals.

This page covers the everyday tasks. To decide what a team may do, see [Grant permissions with nodes](grant-permissions-with-nodes.md).

## Prerequisites

* Ingot 1.2.0 or later, with the identity model in use.
* A signed-in instance admin. A principal that holds only `ingot.admin.users`, `ingot.admin.teams` or `ingot.admin.tokens` can manage that area, but changing permission nodes and reading the repository access list need the instance admin role.

## Steps in the dashboard

Open **Access** in the sidebar. It has the tabs **Users**, **Machine accounts** and **Teams**.

1. To add a user, open **Users** and select **New user**. Enter a username, a display name and a password of at least 12 characters. The user must change the password at the first sign in.

2. To add a machine account, open **Machine accounts** and select **New machine account**. Enter a name that has no colon. Tick **Create it disabled** if the account should stay unusable until you enable it. Ingot creates a private team named `machine:<name>` for the account.

3. To add a team, open **Teams** and select **New team**. Enter a name and, optionally, a description and an external reference. A team name cannot start with `machine:`, because that prefix belongs to the private teams of machine accounts.

4. To add members, open the team and select **Add member** in the **Members** card. Members that come from a synchronised group have no remove button here. The synchronisation owns them.

5. To add a parent team, open the **Parents** card of the team, add the parent, and select **Save**. The members of the team then receive the nodes of the parent.

6. To make a principal an instance admin, open its actions menu and select **Make instance admin**, then confirm. Select **Remove instance admin** to take the role back.

7. To stop a principal from working, open its actions menu and select **Disable**. A disabled principal cannot sign in, and its tokens stop working. Select **Enable** to undo it.

8. To delete a principal, select **Delete** and type its name to confirm. This removes its tokens, its team memberships and its instance role.

## Steps with the identity API

Every call below needs a token of an instance admin. Send it as a Bearer secret. The secret of a token created in Ingot starts with `ingot_`.

```bash
export INGOT_URL=https://ingot.example.com
export INGOT_TOKEN=ingot_<id>_<random>
```

Create a user, then set its password. The password call is the same one the dashboard uses:

```bash
curl -X POST "$INGOT_URL/api/identity/principals" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"kind": "USER", "name": "alice", "displayName": "Alice"}'

curl -X POST "$INGOT_URL/api/identity/principals/alice/password" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword": "replace-with-a-password-of-12-or-more"}'
```

Create a machine account:

```bash
curl -X POST "$INGOT_URL/api/identity/principals" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"kind": "MACHINE", "name": "ci-deploy"}'
```

Create a team, replace its manual members, and set its parent team. The parent must exist first:

```bash
curl -X POST "$INGOT_URL/api/identity/teams" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "oss-deployers", "description": "Deploys the open source libraries"}'

curl -X PUT "$INGOT_URL/api/identity/teams/oss-deployers/members" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"members": ["alice", "ci-deploy"]}'

curl -X PUT "$INGOT_URL/api/identity/teams/oss-deployers/parents" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"parents": ["oss"]}'
```

Disable a principal, then grant and revoke the instance admin role:

```bash
curl -X PATCH "$INGOT_URL/api/identity/principals/alice" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"disabled": true}'

curl -X PUT "$INGOT_URL/api/identity/principals/alice/instance-admin" \
  -H "Authorization: Bearer $INGOT_TOKEN"

curl -X DELETE "$INGOT_URL/api/identity/principals/alice/instance-admin" \
  -H "Authorization: Bearer $INGOT_TOKEN"
```

The `PUT` on `members` and on `parents` replaces the whole list. An empty list removes every manual member or parent. A `PUT` on `members` never removes a member that a synchronisation added.

Reads of one principal, one team, or the nodes or parents of a principal or team return an `ETag` header. The list endpoints and the token list return none, so take the value from a read of the single resource. Send that value back as `If-Match` on the next `PATCH`, `PUT` or `DELETE` of the same resource. If the resource changed in between, the server answers `412 Precondition Failed`, and you read it again. Without `If-Match`, the write goes through.

## Result

The team answers with its members. Each one has `"source": "MANUAL"` when you added it:

```bash
curl "$INGOT_URL/api/identity/teams/oss-deployers" \
  -H "Authorization: Bearer $INGOT_TOKEN"
```

The team page in the dashboard shows the same members, and the principal list shows the new user and the machine account.

{% hint style="warning" %}
Deleting a machine account stops every build that uses one of its tokens, and the tokens cannot be restored. Revoke a token first when you only want to rotate it. See [Use tokens in CI](use-tokens-in-ci.md).
{% endhint %}

{% hint style="warning" %}
The server counts an instance admin as usable only when it is enabled and holds an unexpired token whose node list allows instance admin. Removing the instance admin role from, or disabling, the last usable instance admin returns `409 Conflict`. Before you change an admin, make sure that another admin holds such a token.
{% endhint %}

A team cannot become its own ancestor, and its parent chain can be at most eight levels deep. A parent list that breaks either rule returns `409 Conflict`.

Users who sign in through an OIDC provider or LDAP are created at their first sign in when the provider's `autoCreateUsers` setting is `true`, which is the default. When the setting is `false`, create the user here first, with the username the provider sends. See [Configure generic OIDC login](configure-oidc-login.md).

## Related

* [Use tokens in CI](use-tokens-in-ci.md) creates the token that a machine account signs in with.
* [Grant permissions with nodes](grant-permissions-with-nodes.md) gives teams and machine accounts their rights.
* [Check what a principal can do](check-access.md) shows the effect of a team change before a build depends on it.
* [Permission nodes](../reference/permission-nodes.md) lists the node grammar.

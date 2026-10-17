---
description: Recipes for granting rights in Ingot with permission nodes. Covers a read-only team, deploying into one namespace, denying one artifact inside an allowed domain, and inheriting rights from a parent team, with a check for each.
---

# Grant permissions with nodes

A permission node says that a team or a principal may do one thing, such as reading a repository or deploying an artifact group. This page gives four recipes. Each one ends with a check that shows the result.

## Prerequisites

* A signed-in instance admin. Changing permission nodes needs the instance admin role.
* The team and its members exist. See [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md).
* The node grammar, in [Permission nodes](../reference/permission-nodes.md).

## How nodes are applied

Nodes are stored per subject: a principal, or a team. To decide one request, Ingot looks at the levels of the principal in this order:

1. The principal's own nodes.
2. The nodes of the teams it belongs to.
3. The nodes of the parent teams of those teams, one level at a time.

The first level that has a matching node decides. Inside that level, the most specific node wins, and a denial wins a tie. Two rules follow from the order, and every recipe relies on them:

* **Keep an allow and the denial that narrows it on the same subject.** A broad allow on the principal's own level decides before a denial on a team. Put both on one team, or both on the principal.
* **A child team decides before its parent.** A matching node on the member's team beats the parent's node, whatever the parent says.

Nodes are edited on the team page, in the **Permission nodes** card. Add a node in **Add a node**, or pick one from **Templates**. Each row has **Allow** and **Deny**. Select **Save** in the bar at the bottom to store the changes. **Discard** drops them.

The API replaces the whole list. Send every node of the subject each time:

```bash
curl -X PUT "$INGOT_URL/api/identity/teams/readers/nodes" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nodes": [{"node": "ingot.repository.releases.read", "allowed": true}]}'
```

A principal has the same endpoint at `/api/identity/principals/<name>/nodes`.

## Recipe 1: a read-only team

Give a team the right to read, and nothing more.

1. Create the team `readers` and add its members.
2. On the team, add the node `ingot.repository.*.read` as **Allow**. The `*` covers every repository. To cover one repository, use its name instead, such as `ingot.repository.releases.read`.
3. Select **Save**.

Check one read and one deploy on a repository. Ask the check about a member, such as `alice`:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&node=ingot.repository.releases.read"

curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&node=ingot.repository.releases.deploy"
```

The first answer has `"allowed": true`, and the second has `"allowed": false`.

## Recipe 2: deploy into one namespace

Let a team deploy into the `net.onelitefeather` group of the `releases` repository, subgroups included. Other repositories and other groups stay closed.

1. Create the team `oss-deployers` and add its members.
2. Add these two nodes as **Allow**:

   ```
   ingot.repository.releases.artifact.net.onelitefeather.read
   ingot.repository.releases.artifact.net.onelitefeather.deploy
   ```

3. Select **Save**.

The read node is needed as well. It lets the team see the group in the listings of the repository. A subject that holds only deploy nodes does not see the repository at all.

Check a path inside the group and a path outside it. Use the artifact path mode of the check, which is described in [Check what a principal can do](check-access.md):

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&repository=releases&path=net/onelitefeather/tools/lib/1.0.0/lib-1.0.0.pom&action=deploy"

curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&repository=releases&path=org/example/lib/1.0.0/lib-1.0.0.pom&action=deploy"
```

The first answer has `"allowed": true`, and the second has `"allowed": false`.

## Recipe 3: deny one artifact inside an allowed domain

Let a team deploy every artifact of `net.onelitefeather` except `internal`. The denial and the allow must sit on the same subject, as the rules above explain.

1. On the team `oss-deployers`, add these two nodes:

   | Node | Setting |
   | --- | --- |
   | `ingot.artifact.net.onelitefeather.deploy` | Allow |
   | `ingot.artifact.net.onelitefeather:internal.deploy` | Deny |

2. Select **Save**.

The artifact node that names `internal` is more specific than the group node, so it decides for that artifact. Every other artifact of the group stays allowed.

{% hint style="warning" %}
If the broad allow is on the user, and the denial on the team, the user's allow decides first and the denial has no effect. Put both nodes on the team, or both on the user.
{% endhint %}

{% hint style="warning" %}
Instance admins are not restricted by denials. A denial limits the team members, but an instance admin still passes. Only the node list of a token narrows an admin.
{% endhint %}

Check the denied artifact, which must answer `false`, and an artifact next to it, which must answer `true`:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&repository=releases&path=net/onelitefeather/internal/1.0.0/internal-1.0.0.pom&action=deploy"

curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&repository=releases&path=net/onelitefeather/public-api/1.0.0/public-api-1.0.0.pom&action=deploy"
```

## Recipe 4: inherit rights from a parent team

Let the members of a team receive the rights of another team, without copying its nodes. Changing the parent then changes every child.

1. Create the parent team `oss`, and give it its nodes, for example `ingot.repository.releases.read` as **Allow**.
2. Create the team `oss-deployers` and add its members.
3. On the team `oss-deployers`, open the **Parents** card, add `oss`, and select **Save**.

Parent teams can also be set with the identity API. The list replaces the parents of the team:

```bash
curl -X PUT "$INGOT_URL/api/identity/teams/oss-deployers/parents" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"parents": ["oss"]}'
```

The members of `oss-deployers` now receive the nodes of `oss`. The nodes of `oss-deployers` itself decide first. A team can inherit through up to eight levels of parents. A parent chain that loops back on itself, or is deeper than eight levels, is refused with `409 Conflict`.

Check a member. The decision names the team the rule came from:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=alice&node=ingot.repository.releases.read"
```

The answer has `"allowed": true`. Its `decision.source` is `INHERITED_TEAM`, and its `decision.subject` is `oss`.

## Result

After the recipes, each check answers as the recipe says. The team pages show the nodes, and the parent teams.

## Role grants

The API also accepts path grants with a role, at `PUT /api/identity/teams/<name>/grants`. The dashboard does not edit them. It lists the grants that came before permission nodes, read only. Use nodes for the rights in this page.

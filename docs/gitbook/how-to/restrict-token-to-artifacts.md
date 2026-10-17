---
description: Limit an Ingot token to some artifacts, groups or repositories, so that a leaked build token cannot touch the rest of the instance. Covers the dashboard dialog, the API node list, and the rules that apply when a token narrows.
---

# Restrict a token to artifacts

A token normally has the rights of the account that owns it. You can limit a token to a list of permission nodes. Then it can use only the artifacts in that list, even when its owner may do more.

## Prerequisites

* A token creation right: the instance admin role, or the `ingot.admin.tokens` node.
* A machine account that owns the token. See [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md).
* The artifact paths you want to allow. Write them as groupId and optional artifactId, for example `net.onelitefeather` and `mycelium-bom`.

## Steps in the dashboard

1. Open **Access**, then **Machine accounts**, then the account. In the **Tokens** section, select **Create token**.
2. Enter a name and an expiry.
3. Open **Advanced: limit to artifacts** and tick **Limit this token to the artifacts below**. The access choice above the section is disabled while the limit is on.
4. Enter the **Repository** to limit the token to that one repository. Leave it empty to cover every repository.
5. Enter the **groupId**. The groupId covers its subgroups. For example, `net.onelitefeather` covers `net/onelitefeather/tools/`.
6. Optionally enter an **artifactId**. Leave it empty to cover every artifact of the groupId.
7. Tick at least one action: **Read**, **Deploy** or **Delete**. The dialog refuses to create the token without one.
8. Select **Create**, and copy the secret from the next dialog.

The dialog builds one node per action you tick. The table shows what it builds:

| Repository | groupId | artifactId | Actions | Nodes |
| --- | --- | --- | --- | --- |
| empty | `net.onelitefeather` | empty | Deploy | `ingot.artifact.net.onelitefeather.deploy` |
| `releases` | `net.onelitefeather` | `mycelium-bom` | Read, Deploy | `ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.read` and `ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy` |

## Steps with the identity API

Send the nodes in the `nodes` list of the token request. Each entry needs the node text and `"allowed": true`:

```bash
curl -X POST "$INGOT_URL/api/identity/principals/ci-deploy/tokens" \
  -H "Authorization: Bearer $INGOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "github-releases",
    "expiresAt": "2027-01-15T23:59:59Z",
    "nodes": [
      { "node": "ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.read", "allowed": true },
      { "node": "ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy", "allowed": true }
    ]
  }'
```

Omit `nodes` for a token that is not limited. An empty `nodes` list is refused. If a node is invalid, the server answers `400 Bad Request` and names each bad node in a `problems` list.

## Rules of a narrowed token

* **A token only removes rights.** It can never allow more than its owner may do. A node for an artifact the owner may not use gains nothing.
* **A path without a node is denied.** A narrowed token that lists `mycelium-bom` cannot deploy `mycelium-api`, even when its owner may deploy the whole group.
* **Instance admins are narrowed too.** Negated nodes do not restrict an instance admin, but the node list of a token does. An admin's token that lists only deploy nodes cannot read.
* **The node list is set at creation.** No endpoint changes the list of an existing token. To change it, create a new token, update the CI secrets, and delete the old token.
* **Read only is a shortcut for one node.** The access choice **Read only** sends the scope `READ`, and the server turns it into the node `ingot.repository.*.read`, which allows reading in every repository. The artifact limit replaces that choice, so the dialog disables it while the limit is on.

Node grammar is in [Permission nodes](../reference/permission-nodes.md). The reference for artifact nodes, including the coordinate rules, is [Artifact permission nodes](../reference/artifact-permission-nodes.md).

## Verify the limit

Use the check endpoint with the token name. Test one path that the token should reach and one that it should not. Replace the group and the artifact with your own:

```bash
curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=ci-deploy&token=github-releases&repository=releases&path=net/onelitefeather/mycelium-bom/1.0.0/mycelium-bom-1.0.0.pom&action=deploy"

curl -H "Authorization: Bearer $INGOT_TOKEN" \
  "$INGOT_URL/api/identity/check?principal=ci-deploy&token=github-releases&repository=releases&path=net/onelitefeather/mycelium-api/1.0.0/mycelium-api-1.0.0.pom&action=deploy"
```

The first answer has `"allowed": true`. The second has `"allowed": false`. The dashboard shows the same question under **Access**, then **Check**, when you pick the token in the form. See [Check what a principal can do](check-access.md).

## Result

The token works for the artifacts in its node list and for nothing else. The check answers match the list: the artifact in the list is allowed, and the other one is not.

{% hint style="warning" %}
A token with a node list and no node for a repository path cannot read or deploy there. Add a read node as well as a deploy node when the build reads the artifact too.
{% endhint %}

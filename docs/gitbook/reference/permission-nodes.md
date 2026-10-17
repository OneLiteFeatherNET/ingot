---
description: Reference for permission node text in Ingot. Lists the node forms, actions, admin areas, wildcards, negation with allowed false, the specificity order and the validation messages of the identity API.
---

# Permission nodes

A permission node is a text that names one right, such as `ingot.repository.releases.deploy`. Operators store nodes on a principal, on a team, or on a token that narrows its owner. Every stored node has an `allowed` flag. A node with `allowed` set to `false` is a denial.

This page describes the node text. For the artifact forms, see [Artifact permission nodes](artifact-permission-nodes.md). For how stored nodes combine into one decision, see [Permission evaluation](../explanation/permission-evaluation.md).

## Node forms

| Form | Covers |
| --- | --- |
| `*` | Every repository action and every admin area |
| `ingot.repository.<repository>.<action>` | One action on one repository |
| `ingot.repository.<repository>.*` | Every repository action on one repository |
| `ingot.admin.<area>` | One administrative area |
| `ingot.admin.*` | Every administrative area. Holding it makes a principal an instance admin. |
| `ingot.artifact.<groupId>[:<artifactId>].<action>` | Part of the tree of every repository |
| `ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<action>` | Part of the tree of one repository |

The two artifact forms are described in [Artifact permission nodes](artifact-permission-nodes.md).

## Repository nodes

The repository is the text between `ingot.repository.` and the last dot. A repository name may contain dots, so `ingot.repository.team.releases.read` names the repository `team.releases`. The name is not checked against the repositories that exist when a node is saved.

| Action | Covers | Request methods that need it |
| --- | --- | --- |
| `read` | Reading files and browsing a repository | GET and HEAD |
| `deploy` | Writing files into a repository | PUT and POST |
| `delete` | Deleting files from a repository | DELETE |
| `manage` | Managing the repository. It is the action of the ADMIN role. | None. No request path checks it. The check endpoint answers it. |

The action `*` covers all four actions.

## Admin nodes

`ingot.admin.<area>` grants one administrative area. The areas are:

| Area | Endpoints that check it |
| --- | --- |
| `users` | Principal endpoints: list, create, read, update, delete, instance admin role |
| `tokens` | Token endpoints of a principal: list, create, delete |
| `teams` | Team endpoints: list, create, read, update, delete, grants, members, parents, instance admin role |
| `settings` | None. Accepted in node text. Its endpoints need the instance admin status. |
| `maintenance` | None. Accepted in node text. |
| `audit` | None. Accepted in node text. The audit endpoints need the instance admin status. |
| `console` | None. Accepted in node text. The console needs the instance admin status. |
| `dashboard` | None. Accepted in node text. |

The areas `users`, `tokens` and `teams` are checked by their own endpoints. Every other endpoint that requires an admin checks the instance admin status, which means `ingot.admin.*` (or `*`). The HTTP API page lists which endpoint needs which check.

`ingot.admin.<area>` alone does not make a principal an instance admin. For example, `ingot.admin.users` lets a principal manage users, but it does not open the audit log.

{% hint style="warning" %}
A denial of an area works against an instance admin. A principal that holds `ingot.admin.*` and also a denial of `ingot.admin.users` cannot use the users endpoints, while it stays an instance admin. Request paths of repositories and artifacts are not affected by admin area denials. This differs from the shortcut for request paths described in [Artifact permission nodes](artifact-permission-nodes.md#instance-admins).
{% endhint %}

{% hint style="warning" %}
The areas `settings`, `maintenance`, `dashboard` and `console` are valid node text, but no endpoint checks them by area. Granting one of them has no effect on any endpoint today.
{% endhint %}

## Wildcards

- `*` alone covers every request. It is the least specific node.
- A wildcard is only valid as a whole segment. `ingot.repository.rel*.read` is refused.
- `ingot.repository.*.<action>` covers the action on every repository.
- `ingot.repository.<repository>.*` covers every action on one repository.
- `ingot.admin.*` covers every admin area.
- An artifact node with the groupId `*` covers the action on every path of a repository. Only that form answers a request for a whole repository, see [Artifact permission nodes](artifact-permission-nodes.md).
- In an artifact node, an artifactId of `*` is the same as no artifactId. A groupId of `*` together with an artifactId is refused.

A node that is checked with the check endpoint must be concrete. A node with `*` in it is refused there.

## Negation

Negation is not part of the name. A denial is the same node text saved with `allowed` set to `false`. A name that starts with `-` is refused.

```
PUT /api/identity/principals/alice/nodes

{
  "nodes": [
    { "node": "ingot.repository.releases.deploy", "allowed": true },
    { "node": "ingot.repository.releases.delete", "allowed": false }
  ]
}
```

The request replaces the whole node list of the principal. Send the full list every time. The response carries an `ETag`, and a later `PUT` may send it back in `If-Match` to refuse a write that was made in between. A `PUT` without `If-Match` skips that check.

Within one precedence level, the most specific matching node decides, and a denial wins a tie. A denial of a more specific node removes part of a wider allowance. The levels are the principal's own rules first, then its teams by distance. A principal's own allowance therefore beats a team denial. See [Permission evaluation](../explanation/permission-evaluation.md).

## Specificity

Within a level, every matching node gets a rank. Higher ranks decide.

| Rank | Nodes |
| --- | --- |
| 0 | `*` |
| 2 | `ingot.repository.*.*`, `ingot.admin.*` |
| 3 | `ingot.repository.*.<action>`, `ingot.repository.<repository>.*`, `ingot.admin.<area>` |
| 4 | `ingot.repository.<repository>.<action>`. A team role grant also ranks 4. |
| 5 | Every artifact node |

Artifact nodes rank above every repository node, so an artifact node decides over a repository node at the same level. Between artifact nodes the order is:

1. A node qualified with a repository beats an unqualified node.
2. A node with an artifactId beats a node with only a groupId.
3. A longer groupId (more dot-separated segments) beats a shorter one. The groupId `*` has none.
4. A literal action beats the action `*`.

## Team roles

Team grants are not nodes. A grant gives a role on a repository and a path prefix, and a path prefix is required. Repository-wide access is given with nodes instead.

| Role | Actions |
| --- | --- |
| `READ` | read |
| `DEPLOY` | read, deploy |
| `MAINTAIN` | read, deploy, delete |
| `ADMIN` | read, deploy, delete, manage |

`ADMIN` is refused as a grant: "ADMIN applies to a whole repository, grant it with permission nodes". The legacy roles from before the identity model are not assignable.

## Catalog and templates

Two manager-only endpoints help to pick nodes:

- `GET /api/identity/nodes/catalog` lists the nodes an operator can pick. The optional `prefix` query parameter filters the list. The list contains the admin areas, `ingot.admin.*`, `*`, the repository nodes of the repositories that exist now, `ingot.repository.*.<action>` and `ingot.artifact.*.<action>`.
- `GET /api/identity/nodes/templates` lists predefined bundles for the dashboard. The placeholders `{repository}`, `{groupId}` and `{artifactId}` stand for the values of the artifact or repository the bundle is applied to.

Reading either list changes no rights.

## Validation

The identity API checks every node when a node list is saved, when a token is created with nodes, and when a check is asked. Each invalid node produces one problem. A save that has invalid nodes fails with status `400` and the problems in the response body.

Checks and messages, with the placeholders as they appear in the message:

| Check | Message |
| --- | --- |
| Empty text | The node is empty |
| Longer than 255 characters | The node '...' is longer than 255 characters |
| Whitespace or control characters | The node '...' contains whitespace or control characters |
| Starts with `-` | The node '...' starts with '-', negation is stored as allowed false and not in the name |
| Unknown prefix | The node '...' is unknown, nodes start with 'ingot.repository.', 'ingot.artifact.' or 'ingot.admin.', or are '*' |
| Repository node without an action | The repository node '...' needs a repository and an action, for example ingot.repository.releases.read |
| Unknown repository action | The action '...' of '...' is unknown, supported: read, deploy, delete, manage or * |
| Wildcard inside a repository name | The repository '...' of '...' is invalid, a wildcard must be the whole repository segment |
| Empty repository segment | The repository '...' of '...' is invalid, a wildcard must be the whole repository segment |
| Repository name with an `artifact` segment | The repository node '...' is invalid. A repository name may not contain a segment named 'artifact', because the first '.artifact.' after 'ingot.repository.' separates the repository from the artifact part |
| Unknown admin area | The admin area '...' of '...' is unknown, supported: settings, users, teams, tokens, maintenance, audit, console, dashboard or * |
| Artifact node without an action | The artifact node '...' needs a groupId and an action, for example ingot.artifact.net.example.deploy |
| Unknown artifact action | The action '...' of '...' is unknown for an artifact node, supported: read, deploy, delete or * (manage is not scoped to a path, use a repository node) |
| More than one colon | The coordinate '...' of '...' can contain only one ':' between the groupId and the artifactId |
| Wildcard groupId with an artifactId | The groupId * with the artifactId '...' in '...' is ambiguous, name the groupId of the artifact |
| Invalid character in a name | The groupId or artifactId '...' of '...' has an invalid character in '...', allowed are letters, digits, '_', '-' and '.' |
| Node with `*` in a check | The node to check must be concrete, without wildcards |
| Same node twice in one list | One problem per repeated node: `Node '...' is listed twice` |

A node list with a repeated node is refused as a whole. The same node text must not appear twice, even with different `allowed` values.

Creating a token with an empty node list is refused with "Nodes cannot be empty, omit them for an unrestricted token". Replacing the node list of a principal or team with an empty list is allowed and removes all of its nodes.

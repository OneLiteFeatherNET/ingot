---
description: Reference for artifact scoped permission nodes. Lists the two forms, the actions, how nodes rank against each other, how tokens narrow them, how directory listings react, and what the permission check returns.
---

# Artifact permission nodes

An artifact node gives an action on part of a repository tree instead of on a whole repository. Use it to let a person or a build deploy one artifact or one group of artifacts, and nothing else.

## The two forms

| Form | Applies in |
| --- | --- |
| `ingot.artifact.<groupId>[:<artifactId>].<action>` | Every repository |
| `ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<action>` | The named repository only |

The `:<artifactId>` part is optional. Both forms are stored like any other node: a node text and an `allowed` flag. The general grammar of all nodes, including the repository and admin nodes, is described in [Permission nodes](permission-nodes.md).

## Coordinates

| Part | Meaning |
| --- | --- |
| `<groupId>` | A domain. It maps to a path prefix and covers the whole subtree below it. `net.onelitefeather` covers `net/onelitefeather/` and every subgroup, such as `net/onelitefeather/tools/`. |
| `<groupId>:<artifactId>` | A single artifact. It covers `<group path>/<artifactId>/` only. `net.onelitefeather:mycelium-bom` covers `net/onelitefeather/mycelium-bom/`. |
| `*` as the groupId | Every path of the repository. |
| `*` as the artifactId | The same as no artifactId. |

Rules for the coordinates:

* Matching compares whole path segments and is case sensitive. `net.onelitefeather` does not cover `net/onelitefeatherX/` and does not cover `Net/OneLiteFeather/`.
* A groupId or artifactId may contain letters, digits, `_` and `-`. Both are split into segments at `.`, and a segment may not be empty.
* `*` is only allowed as the whole groupId or the whole artifactId, never inside a name such as `net.*`.
* The groupId `*` together with an artifactId, such as `ingot.artifact.*:lib.read`, is ambiguous and refused. Name the groupId.
* A node is at most 255 characters long and contains no whitespace.
* A request path with a parent segment (`..`) is covered by no artifact node.

## Actions

| Action | Allows |
| --- | --- |
| `read` | Reading files and browsing directories in the covered tree |
| `deploy` | Deploying into the covered tree |
| `delete` | Deleting from the covered tree |
| `*` | `read`, `deploy` and `delete` |

There is no `manage` action for artifact nodes. Managing a repository is not scoped to a path, so use a repository node (`ingot.repository.<repository>.manage`) for it.

## Repository names

In the qualified form the first segment named `artifact` after `ingot.repository.` separates the repository name from the artifact part. A repository name therefore may not contain a segment named `artifact`. A repository named `artifact` or `team.artifact` cannot be addressed by an artifact node, and such a node is refused when it is saved. Repository nodes that name such a repository are refused as well. The wildcard `*` is not allowed as the repository of an artifact node; use the unqualified form to cover every repository.

## Negation

A node is denied by saving it with `allowed` set to `false`. Inside one precedence level, a denial removes the covered tree from the less specific allowances of that level, and a more specific allowance beats a wider denial. A denial wins a tie between two nodes of the same specificity.

The precedence levels are decided in order: the principal's own rules first, then the rules of its teams by distance. The first level with a matching node decides, so a denial on a team does not override the principal's own allowance, even when the team's denial is more specific.

* Allowing `ingot.artifact.net.onelitefeather.deploy` on a principal and denying `ingot.artifact.net.onelitefeather:internal.deploy` on it allows every artifact of the group except `internal`.
* Allowing `ingot.artifact.net.onelitefeather.deploy` on a principal and denying `ingot.artifact.net.onelitefeather:internal.deploy` on one of its teams still allows `internal`, because the principal's own level decides first.
* A path that no node covers is denied.

## Instance admins

An instance admin is a principal whose rules allow `ingot.admin.*` (or `*`). On request paths (reads, uploads and deletes of files), an instance admin is allowed whatever the repository and artifact nodes deny. Only a token that narrows the admin limits it. A denial of `ingot.admin.*` itself removes the instance admin status.

{% hint style="warning" %}
The check endpoint decides an artifact node without a path, and an admin area node, from the rules alone. For those two kinds it can show a denial that a request path would not enforce for an instance admin. For what a request does, use the path check described below.
{% endhint %}

## Specificity

Within the first precedence level that has a matching node, the most specific match decides, and a denial wins a tie. The key is compared field by field, in this order:

1. Tier. `*` is 0. The repository and admin nodes keep their literal count (2 to 4). A team role grant counts 4. An artifact node is tier 5, so an artifact node always decides over a repository node at the same level.

Within the artifact tier:

2. Qualified. A repository qualified node beats an unqualified one.
3. Artifact id. A node with an artifactId (not `*`) beats a groupId only node.
4. Group segments. A longer groupId (more dot separated segments) beats a shorter one. The groupId `*` has none.
5. Literal action. A literal action beats the action `*`.

## Tokens can only narrow

A token may carry its own node list. A token without a node list narrows nothing. A token with a node list can only remove rights: a request is allowed only when the owner of the token and the token both allow it.

* A token that lists `ingot.artifact.net.onelitefeather:mycelium-bom.deploy` deploys that artifact and no sibling, even when its owner may deploy the whole group.
* A token that lists an artifact node its owner lacks gains nothing. The owner's rights stay the limit.
* A token with a node list and no node for a path is denied that path, even when its owner may use it.

## Directory listings

Artifact read nodes change what a subject sees when it browses a repository.

* A repository is listed for a subject that may read at least one subtree of it through artifact nodes. A subject with only `deploy` nodes does not see the repository.
* The subject can open only the directories on the way to a readable subtree and the readable subtree itself. With `ingot.artifact.net.onelitefeather.read`, the root of the repository shows `net/`, `net/` shows `onelitefeather/`, and `net/onelitefeather/` shows its artifacts. `org/` and `net/other/` are not shown.
* Inside a readable subtree an entry is shown unless a node denies it. A denial such as `ingot.artifact.net.onelitefeather:internal.read` with `allowed` set to `false` hides `net/onelitefeather/internal/` from the listing.
* Opening a directory that is not shown answers like any other directory the subject may not read: 403 Forbidden for a signed in subject, 401 Unauthorized for an anonymous request.
* The same filter applies to the HTML directory index and to the details API.
* HIDDEN and PRIVATE repositories keep their rules. A HIDDEN repository is listed in the repository list only for instance admins and for subjects that hold a read node on the repository, or a team grant with role READ or higher on it, with or without a path prefix. An artifact read node with a groupId covers only a subtree, so it does not list a HIDDEN repository. The node `ingot.artifact.*.read`, whose groupId is `*`, covers the whole repository and does list it. A PRIVATE repository is listed for every subject that can see it, which includes a subject that may read a subtree through artifact nodes.
* Repository wide rights and legacy route grants are unchanged. A subject that holds no artifact read node for a repository sees that repository exactly as before.

## Examples

Nodes are saved on a principal or a team, or on a token to narrow it. The identity API takes them as a list:

```
PUT /api/identity/principals/alice/nodes

{ "nodes": [ { "node": "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", "allowed": true } ] }
```

### Deploy one artifact

```
ingot.artifact.net.onelitefeather:mycelium-bom.read     allowed
ingot.artifact.net.onelitefeather:mycelium-bom.deploy   allowed
```

The subject reads and deploys `net/onelitefeather/mycelium-bom/` in every repository. Deploying `net/onelitefeather/mycelium-api/` is denied.

### Deploy a whole domain

```
ingot.artifact.net.onelitefeather.deploy   allowed
```

The subject deploys everything below `net/onelitefeather/`, subgroups included, in every repository. It does not deploy `net/onelitefeatherX/`. Add `ingot.artifact.net.onelitefeather.read` to let it read and browse the same tree.

### Limit a grant to one repository

```
ingot.repository.releases.artifact.net.onelitefeather.deploy   allowed
```

The subject deploys below `net/onelitefeather/` in `releases` only. Deploying the same path in `snapshots` is denied.

### Deny one artifact inside an allowed domain

```
ingot.artifact.net.onelitefeather.deploy                 allowed
ingot.artifact.net.onelitefeather:internal.deploy        not allowed
```

The subject deploys every artifact of the domain except `net/onelitefeather/internal/`. The artifactId node is more specific than the groupId node, so it decides for its artifact.

## Checking a decision

`GET /api/identity/check?principal=<name>&node=<node>` decides one concrete node for a principal. Add `&token=<token name>` to apply the narrowing of one token. An instance admin may check any principal; anyone else may check only itself. The node must be concrete, without `*`.

* An unqualified artifact node, such as `ingot.artifact.net.onelitefeather:mycelium-bom.deploy`, asks whether the artifact is allowed in every repository. Use the qualified form to ask about one repository.
* A repository node such as `ingot.repository.releases.deploy` asks about the whole repository. Only an artifact node with the groupId `*` covers the whole repository and answers it. Other artifact nodes cover part of a repository and do not answer it.

To ask about one path inside a repository, use the path form: `GET /api/identity/check?principal=<name>&repository=<repository>&path=<path inside the repository>&action=<read, deploy or delete>`. The action can come from a `node` parameter instead, such as `ingot.repository.releases.deploy`. The path form decides the path as a request for it would, so it includes the instance admin shortcut. Its answer has `node` set to null, and names `repository`, `path` and `action`.

The answer has `principal`, `node`, `allowed`, a `decision` and a `trace`.

| Field | Content |
| --- | --- |
| `decision.decidingNode` | The text of the node that decided. Null when no node matched and the default denial applied, and null for a role grant, which has no node. |
| `decision.decidingKind` | The kind of the deciding rule: `ARTIFACT`, `REPOSITORY`, `ADMIN`, `EVERYTHING`, `GRANT` for a role grant, or `INSTANCE_ADMIN` when the instance admin shortcut allowed the request. Null when no node matched. |
| `decision.explanation` | The decision in one sentence, such as `Denied by the artifact node 'ingot.artifact.net.onelitefeather.deploy' of alice (USER)`. |
| `decision.source`, `decision.subject`, `decision.depth` | Where the deciding rule came from: the level (user, team, inherited team, token), the owner of the rule, and its distance. |
| `trace[]` | Every rule or grant that was considered, with `source`, `subject`, `depth`, `node`, `kind`, `allowed`, `matched` and a `reason` that names the specificity or why the node did not match. |

---
description: Explains how Ingot decides one request. Covers the order of the checks, token narrowing, the instance administrator shortcut, precedence levels with team inheritance, specificity and ties, legacy route grants, role grants with path prefixes, and why the access check gives the same answer.
---

# How permissions are evaluated

This page explains how Ingot decides whether one request is allowed. It describes the order of the checks, and why the order is what it is. The node grammar and the full list of values are in [Permission nodes](../reference/permission-nodes.md). The identities and objects the decision uses are described in [Identity model](identity-model.md).

## The order of one request

Take a request from a Maven client, a read of `/releases/net/example/app/1.0/app-1.0.pom` with a token. Ingot decides it in this order:

1. **Disabled subject.** A disabled principal is denied everything, through any token or session.
2. **Token narrowing.** A token without a node list narrows nothing. A token with a node list must allow the request. If no node of the token covers the request, the token denies it, and the request is denied. A token that covers the request with a denial is denied too.
3. **Instance administrator shortcut.** If the owner of the token is an instance administrator, and the token did not deny the request in step 2, the request is allowed. The owner's repository and artifact nodes are not consulted here.
4. **Owner rules, level by level.** Ingot looks at the owner's own nodes first. Then it looks at the owner's direct teams. Then at the parents of those teams, one step up, two steps up, and so on up to eight steps. The first level that holds a matching entry decides the request.
5. **Most specific entry in that level.** Inside the deciding level, the most specific match decides. A denial wins a tie. Details follow below.
6. **Result.** If the owner's decision allows the request and the token did not deny it, the request is allowed. If no entry matched at all, the request is denied.

Steps 2 and 4 are both needed for an ordinary subject. A token can only remove rights. The owner is always the upper limit.

## Which entries take part

Four kinds of entry can match a request. They share the same ranking, which is described in the next section.

* **Permission nodes** of the owner and its teams. A repository node such as `ingot.repository.releases.read` covers reads of that repository. A wildcard such as `ingot.repository.*.read` covers reads of every repository.
* **Artifact nodes** such as `ingot.artifact.net.example.read`. They cover a part of a repository tree, by Maven group and artifact. See [Artifact permission nodes](../reference/artifact-permission-nodes.md).
* **Role grants** of a team. A grant gives a role on one repository, or on every repository when it has none. It can also be limited to a path prefix inside a repository. A grant with a prefix only covers paths under that prefix, matched on whole path segments and case-sensitively. So a prefix `net/example` covers `net/example/app` but not `net/examples`.
* **Legacy route grants.** These exist only on private teams of migrated tokens. They match the request path as a plain case-insensitive prefix, without a segment boundary. A route `/rel` therefore also covers `/releases`. This is kept on purpose, so that migrated tokens keep their exact rights. See [Token migration](token-migration.md).

## Specificity and ties

Each matching entry gets a rank. The higher rank wins inside its level.

From the lowest rank to the highest:

| Entry | Rank |
| --- | --- |
| `*` (every node) | 0 |
| Repository node with a wildcard repository, such as `ingot.repository.*.read` | 3 |
| Repository node with a literal repository and a wildcard action, such as `ingot.repository.releases.*` | 3 |
| Repository node with a literal repository and a literal action, such as `ingot.repository.releases.read` | 4 |
| Role grant, repository-wide or with a path prefix | 4 |
| Artifact node | 5 and up |

Admin nodes (`ingot.admin.*` and `ingot.admin.<area>`) rank 2 or 3. An `ingot.admin.<area>` node, such as `ingot.admin.users`, grants the endpoints of that area. Only `ingot.admin.*` takes part in the instance administrator check, so `ingot.admin.users` never makes a principal an instance admin.

Among artifact nodes, the rank is compared field by field, in this order:

1. A node that names a repository beats one that applies in every repository.
2. A node with an artifact id beats one with only a group.
3. A longer group beats a shorter one, so `net.example.tools` beats `net.example`.
4. A literal action beats the action `*`.

When two matching entries have the same rank, a denial wins. So if one team allows and another team denies at the same rank in the same level, the request is denied.

A higher rank beats a lower rank of the other sign. A team that allows `ingot.repository.releases.read` (rank 4) overrides a denial of `ingot.repository.*.read` (rank 3) in the same level. The reverse does not hold.

## Levels decide before ranks

Ranks compare only inside one level. A level decides as soon as it holds any matching entry, and the levels are looked at in order: the owner's own nodes, then its direct teams, then the inherited teams by distance.

This has one consequence that often surprises operators. Suppose a principal has a personal denial of `ingot.repository.*.read`, and one of its teams allows `ingot.repository.releases.read`. The personal level decides first. The read of `releases` is denied, even though the team entry is more specific.

Inheritance follows the same rule. A team's own entries outrank its parents' entries, whatever their rank. A parent's entry at distance 1 is looked at after the direct teams, and a grandparent's entry at distance 2 after that.

## Why instance administrators are not restricted by denials

An instance administrator is allowed every request, unless the token of the request narrows it away (step 2). Denials of repository nodes and artifact nodes do not apply to an administrator.

The reason is recovery. The administrator is the person who has to repair access when a rule is wrong. If a repository denial could lock the administrator out of that repository, the administrator could not undo the denial with the dashboard or the API. The administrator role is an override by design, and a token is the place where an administrator can choose to limit it.

## Tokens can only narrow

A token with a node list may remove rights, never add them. Take an owner with the `ADMIN` role on `releases`. A token that lists only `ingot.repository.releases.read` can read `releases`, but it cannot deploy there, even though the owner can. A token that lists a node the owner does not have gains nothing.

The node list of a token is set when the token is created. The API has no call to change it later, so a narrowed token keeps its limits for its whole life.

## A worked example

Alice is a member of the team `builders`, which has the role `DEPLOY` on `releases`. Alice also has a personal node `ingot.repository.releases.deploy` with `allowed = false`.

Alice deploys a file to `/releases/net/example/app/1.0/app-1.0.jar` with her own token, which has no node list.

1. Alice is not disabled, and her token narrows nothing.
2. Alice is not an instance administrator, so the shortcut does not apply.
3. The personal level comes first. Its denial of `releases.deploy` matches the request, and it has rank 4.
4. The personal level decides. The deploy is denied.

The team grant of `builders` is never consulted, because a level that decided is not overruled by a level further away. To let Alice deploy, remove her personal denial.

## The access check gives the same answer

The access check (`GET /api/identity/check`, and the access check page of the dashboard) calls the same decision as a real request. For a repository node, the check answers the request for the repository root. For a path check, it answers a read, deploy or delete of the path.

The check can name one token of the principal. Its node list is then applied as well, as it is for a request made with that token. The answer lists the entries that were considered, and names the one that decided. An operator can therefore see why a request is allowed or denied, and the answer will not differ from what a client gets.

## Related pages

* [Identity model](identity-model.md) explains the objects that the decision uses.
* [Permission nodes](../reference/permission-nodes.md) lists the node grammar and the rules for each form.
* [Artifact permission nodes](../reference/artifact-permission-nodes.md) describes the artifact form in detail.
* [Access tokens and routes](../reference/access-tokens-and-routes.md) describes token formats and legacy route grants.
* [Token migration](token-migration.md) explains why legacy route matching is kept.

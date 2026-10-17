---
description: Explains the identity model of Ingot 1.2.0. Covers principals, users and machine accounts, teams and their private teams, tokens that belong to a principal, roles, the instance administrator, and why access moves away from free tokens.
---

# Identity model

Ingot decides who may do what with a small set of objects. This page explains what those objects are and why they exist. For the exact fields and values, see [Permission nodes](../reference/permission-nodes.md). For the order in which a request is decided, see [How permissions are evaluated](permission-evaluation.md).

The model is recorded in the decision records ADR-INGT-0001 (identity model) and ADR-INGT-0003 (permission nodes), which are kept outside this wiki.

## The objects

* A **principal** is an identity that can own tokens and hold rights. There are two kinds. A **user** is a person. A **machine** principal is a CI job, a bot or any other program.
* A **team** is a named group of principals. A team holds rights in three forms: grants, permission nodes and an instance role. A team can have parent teams, and it passes its rights on to its members.
* A **grant** gives a team a role on one repository, or on a path inside a repository. A grant without a repository applies to every repository.
* A **permission node** is a text such as `ingot.repository.releases.read`. A node allows or denies one kind of access. Nodes can sit on a principal or on a team.
* A **token** is a secret that belongs to one principal. A token can narrow what its owner may do. It cannot add to it.

Rights are never attached to a token. They belong to the principal and to its teams. A token is only a key that the principal uses.

## Users and machine accounts

A user signs in to the dashboard with a password or with an external login provider. A user can also own tokens for scripts, for example to publish from a laptop.

A machine account is the principal for automated use. It authenticates with its tokens, so a build that deploys artifacts uses a machine account and not a person's account. When the build is replaced, the machine account can be disabled or deleted without touching anyone else's access.

Both kinds are subject to the same rights. A machine account gets its access from its teams and nodes, like a user does.

## Teams and private teams

Teams are how most access is given. A team groups principals, such as all maintainers of one project, and gives the group a role on the repositories it needs. Adding a person to the team gives that person the rights at once. Removing the person takes them away.

A team can inherit from parent teams. A member of a team gets the rights of its parents too, up to eight levels above. Ingot refuses a set of parents that loops back on itself or is deeper than eight levels.

Every machine account has a **private team**. Its name starts with `machine:` and it has exactly one member, the machine account. The private team holds the rights that belong only to that account. The team list does not show private teams, and the team endpoints refuse to change them. Deleting the machine account deletes its private team.

Private teams also hold the rights of tokens that came from Reposilite 3.x. See [Token migration](token-migration.md).

## Why tokens belong to a principal

In Reposilite 3.x, an access token carried its own routes. The token was the only owner of its rights, and nothing else in the instance knew about it. Rights could not be shared, inherited or removed as a group.

Ingot moves the rights to the principal and its teams. Every token now has an owner, and the owner's teams and nodes decide what the token may reach. This brings three things:

* One place changes the rights of a whole group. Changing a team changes every member's access, and every token of every member follows.
* A token can be narrowed for one job, such as a single artifact path, and the narrowing is enforced by the server. The narrowing is set when the token is created. It cannot be widened later, because there is no update call for it.
* Every token of a machine account is visible under that account, so an operator can see which automation holds which access.

The owner check is also why a token cannot grant more than its owner has. A token narrows, and the owner is always the upper limit.

## Roles

A grant holds one of four roles. Each role includes the ones before it:

* `READ` allows reading.
* `DEPLOY` allows reading and writing (deploying).
* `MAINTAIN` also allows deleting.
* `ADMIN` also allows managing the repository, such as its settings and the team grants on it.

Two more roles exist for migrated tokens. They keep the old route behaviour, and they are only used on private teams. Operators assign only the four roles above.

Roles can be limited to a path prefix inside a repository. A grant on `releases` with the prefix `net/example` covers `net/example` and everything below it, and nothing else in that repository.

## The instance administrator

An instance administrator can do everything on the instance. The role is called `INSTANCE_ADMIN`. It replaces the MANAGER permission of Reposilite 3.x, and it can be given to a principal or to a team.

The role is also a permission node, `ingot.admin.*`. An instance administrator is not limited by denials of repository nodes. The one limit is the token: if the administrator uses a token with a node list, the token decides what it may do. See [How permissions are evaluated](permission-evaluation.md) for the reason.

The API refuses a change that would leave the instance without a usable administrator. A usable administrator is an enabled principal that holds the admin right and has an unexpired token that still allows it.

## One evaluator

Requests, the access check and artifact search all ask the same evaluator for a decision. The evaluator is a pure function of the principal, its teams and its tokens. Keeping one evaluator means one answer to the question "may this identity do this?", whichever part of Ingot asks it.

## Related pages

* [How permissions are evaluated](permission-evaluation.md) explains the order of the decision.
* [Token migration](token-migration.md) explains how old tokens became machine accounts.
* [Drop-in compatibility with Reposilite](drop-in-compatibility.md) explains what stays the same for existing installations.
* [Permission nodes](../reference/permission-nodes.md) lists the node grammar.
* [Access tokens and routes](../reference/access-tokens-and-routes.md) lists the token formats and the legacy route grants.

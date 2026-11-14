---
description: Defines the terms that the Ingot documentation uses, such as repository types, principal, team, token, permission node, area, group sync, cluster bus, lease, search backend and catalogue, with a link to the page that explains each one.
---

# Glossary

The terms are listed in the order of the identity model first, then the repositories, the operation of the instance and the plugins. Each entry names the page that explains it in full.

## Identity and access

**Principal.** An identity that can own tokens and hold rights. A principal is either a user or a machine account. See [Identity model](../explanation/identity-model.md).

**User.** A principal for a person. A user signs in to the dashboard with a password or an external login provider. See [Identity model](../explanation/identity-model.md#users-and-machine-accounts).

**Machine account.** A principal for automated use, such as a CI job or a bot. It authenticates with its tokens. Every machine account has a private team. See [Manage users, teams and machine accounts](../how-to/manage-users-teams-and-machine-accounts.md).

**Team.** A named group of principals. A team holds grants, permission nodes and the instance role, and it can inherit from parent teams. See [Identity model](../explanation/identity-model.md#teams-and-private-teams).

**Private team.** The team of one machine account, named with the prefix `machine:`. It holds the rights that belong to that account only, and the team endpoints do not change it. See [Identity model](../explanation/identity-model.md#teams-and-private-teams).

**Token.** A secret that belongs to one principal and authenticates requests as that principal. A token can narrow the rights of its owner, but it cannot add to them. See [Use tokens in CI](../how-to/use-tokens-in-ci.md) and [Restrict a token to artifacts](../how-to/restrict-token-to-artifacts.md).

**Grant.** The role that a team holds on one repository, or on a path inside a repository. The roles are `READ`, `DEPLOY`, `MAINTAIN` and `ADMIN`, and each one includes the ones before it. See [Identity model](../explanation/identity-model.md#roles).

**Permission node.** A text such as `ingot.repository.releases.read` that allows or denies one kind of access. Nodes sit on a principal or on a team. See [Permission nodes](permission-nodes.md).

**Area.** A part of the administration that a principal may manage, named in the node `ingot.admin.<area>`. The console area is `ingot.admin.console`, for example. An instance administrator holds the node `ingot.admin.*` and is not limited to one area. See [Permission nodes](permission-nodes.md#admin-nodes).

**Instance administrator.** A principal that may administer the whole instance. It is the successor of the MANAGER permission of Reposilite. See [Identity model](../explanation/identity-model.md#the-instance-administrator).

**Group sync.** The mapping of the groups of an external login provider, such as OIDC, GitHub or LDAP, to Ingot teams, updated at each login. A group can also grant the instance administrator right. See [Map provider groups to teams](../how-to/map-provider-groups-to-teams.md).

## Repositories and artifacts

**Repository type.** The declared purpose of a repository: `HOSTED` stores uploads, `PROXY` copies remote URLs and `GROUP` combines local repositories. A repository without a declared type keeps the behaviour of earlier versions, and Ingot derives its type from its references. See [Repository types](repository-types.md).

**Hosted repository.** A repository that stores what is uploaded to it. See [Create hosted repositories](../how-to/create-hosted-repositories.md).

**Proxy repository.** A repository that fetches files from remote URLs, and can store a copy of them. See [Create a proxy repository](../how-to/create-proxy-repositories.md).

**Group repository.** A repository that answers with the first of its local members that has the file. See [Create a group repository](../how-to/create-a-group-repository.md).

**Upload checksum policy.** The rule that decides what happens to an uploaded checksum file that does not match its artifact: `ignore`, `warn` or `reject`. See [Verify uploaded checksums](../how-to/verify-uploaded-checksums.md).

**Read-only mode.** The instance-wide switch that refuses every write with `503` while reads keep working. It is not the same as the read-only setting of one repository. See [Freeze writes for maintenance](../how-to/freeze-writes-for-maintenance.md).

## Operation

**Deploy target.** What the instance serves. `all` serves the dashboard, the API and the repositories. `server` serves the API and the repositories only. See [Deploy targets](deploy-targets.md).

**Cluster bus.** The optional channel through which replicas share cache invalidations, revocations and log lines. It is off unless a bus URL is set. See [Configure the cluster bus](../how-to/configure-cluster-bus.md).

**Replica.** One running instance of Ingot that shares a database and storage with others. See [Multi-replica operation](../explanation/multi-replica-operation.md).

**Lease.** A time-limited claim that one replica holds on a batch of queued work, such as search index updates. The replica extends the lease while it works. If the replica dies, the lease runs out and another replica takes the batch over. Scheduled jobs are claimed in the database in the same way, so one replica runs each job. See [Multi-replica operation](../explanation/multi-replica-operation.md).

**Support bundle.** A zip file that describes one instance, its configuration and its recent log lines, without its secrets. It is downloaded by a principal with the console area. See [Collect a support bundle](../how-to/collect-a-support-bundle.md).

## Search

**Search backend.** The component that holds the artifact index and answers search. Ingot ships the `database` backend, and a plugin can register another one, such as OpenSearch. All replicas of a cluster use the same backend. See [Choose a search backend](../how-to/choose-a-search-backend.md).

**Index state.** The state of the search index, such as `Building` while the first full rebuild runs. See [Rebuild the search index](../how-to/rebuild-search-index.md).

## Plugins

**Plugin.** A jar that Ingot loads at start from its plugins directory. A plugin runs inside the server process with the same access. See [Install a plugin](../how-to/install-a-plugin.md) and [Write a plugin](../how-to/write-a-plugin.md).

**Catalogue.** An index of plugins, named `ingot-plugin-catalogue.json`, that a Maven repository publishes. The plugin store installs plugins from the catalogues of the sources that you list. A catalogue is verified with OpenPGP keys unless you allow unsigned ones. See [Environment variables](environment-variables.md#plugin-store).

**Repository format.** The protocol that a repository speaks. Ingot ships Maven only, and the format API is experimental. See [Plugin API](plugin-api.md#repository-formats-experimental).

## Related pages

* [Frequently asked questions](faq.md) answers common questions with links to these pages.
* [Identity model](../explanation/identity-model.md) explains how the identity terms fit together.

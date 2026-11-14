---
description: Explains what Ingot is, who it is for, what the drop-in promise means in practice, what Ingot adds over Reposilite, and under which licence it is released.
---

# What is Ingot

Ingot is a self-hosted repository manager for Maven artifacts. It stores the artifacts your builds produce, proxies and caches the ones they download, and serves them over the plain Maven HTTP protocol. An ingot is a cast bar of metal: a standard, immutable, valuable unit that is stored and passed on. A build artifact is the same thing, and that is the name.

Ingot is a fork of [Reposilite](https://github.com/dzikoysk/reposilite) by dzikoysk and contributors. It is developed by OneLiteFeather as a separate product with its own releases and maintainers, and it is not affiliated with or endorsed by the upstream project. The original copyright notices are kept in the source tree.

## Who it is for

* **Teams that publish JVM libraries.** `mvn deploy`, `gradle publish` and sbt work against Ingot without a custom plugin or client.
* **Operators who run it themselves.** Ingot is one JVM process. A small instance runs on a modest virtual machine, and the same image scales to several replicas behind a shared database.
* **Organisations that need identity and audit.** Users, teams, machine accounts, single sign on and an audit log are part of the product, not an add-on.
* **Current Reposilite users.** Ingot is built so that you can move to it without reworking your installation.

Ingot serves Maven repositories only. If you need npm, container images, PyPI or other formats from the same server, read [How Ingot compares](how-ingot-compares.md) before you decide.

## The drop-in promise

Moving from Reposilite to Ingot costs one changed line: the image reference in a deployment, or the dependency coordinate in a build. No configuration file, volume, manifest or plugin is edited. Ingot keeps the image paths, the port, the data directory, the configuration file names and the `com.reposilite.*` plugin packages. An existing data directory is adopted as it is, and old tokens are carried over with their secrets and rights.

New settings get the `INGOT_` prefix, and the `REPOSILITE_` spelling stays valid as a fallback. When both are set, the `INGOT_` value wins. New deployment shapes are opt-in, so the default behaviour does not change. [Drop-in compatibility with Reposilite](drop-in-compatibility.md) lists what stays the same, what differs under the hood and what a rollback cannot undo.

## What Ingot adds over Reposilite

* **An identity model.** Users, teams, machine accounts and permission nodes, with a role for instance administrators. See [Identity model](identity-model.md) and [How permissions are evaluated](permission-evaluation.md).
* **External login.** Generic OpenID Connect, GitHub and LDAP sign-in for the dashboard, with provider groups mapped to teams. See [Login providers](../reference/login-providers.md).
* **Repository types.** Hosted, proxy and group repositories that enforce their rules. See [Repository types](../reference/repository-types.md).
* **Artifact search** across all repositories, with a built-in database backend and a plugin point for others, including an OpenSearch backend. See [Search for artifacts](../how-to/search-for-artifacts.md).
* **Several replicas.** A shared database, an optional cluster bus and an S3 storage provider support more than one replica. See [High availability support](high-availability-support.md) for what is supported and what is tested.
* **Operations.** An audit log with CSV export, cleanup policies, a write freeze for maintenance, a support bundle and a Helm chart. See [Review the audit log](../how-to/review-audit-log.md) and [Run on Kubernetes](../how-to/run-on-kubernetes.md).
* **Import.** A bundled importer that copies Maven repositories from Sonatype Nexus Repository 3. See [Import from Nexus](../how-to/import-from-nexus.md).
* **A rebuilt dashboard** in Material Design 3, which can also run in its own container. See [Dashboard pages](../reference/dashboard-pages.md).

## Licence

Ingot is free and open source software under the Apache License 2.0, the same licence as Reposilite. The `LICENSE` and `NOTICE` files in the repository hold the licence text, the attribution to the upstream project and the statement of changes. There is no paid edition, and the features on this page are all in the open source release.

## Where to go next

* [How Ingot compares](how-ingot-compares.md) sets Ingot beside Reposilite, Sonatype Nexus Repository and JFrog Artifactory.
* [Architecture overview](architecture-overview.md) shows the parts and how a request moves through them.
* [First steps with Docker](../tutorials/first-steps-with-docker.md) starts an instance.

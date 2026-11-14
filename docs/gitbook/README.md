---
description: Start page of the Ingot documentation. Pitches what Ingot is, gives a reading path for each audience, and explains how the four sections are organized.
---

# Ingot documentation

Ingot is a self-hosted Maven repository manager. It hosts, proxies and caches artifacts over the plain Maven protocol, adds users, teams, single sign on, search and an audit log, and runs as one JVM process or as several replicas on a shared database. It is a drop-in replacement for Reposilite 3.x: moving over costs one changed line, and existing Maven, Gradle, sbt and Ivy setups keep working. Ingot hosts Maven repositories only, and it is free software under the Apache License 2.0.

## Start here

Pick the path that matches your role.

* **Evaluator.** Read [What is Ingot](explanation/what-is-ingot.md), then [How Ingot compares](explanation/how-ingot-compares.md) and the [Architecture overview](explanation/architecture-overview.md). Start an instance with [First steps with Docker](tutorials/first-steps-with-docker.md). It takes about 30 minutes.
* **Operator.** Start with [Install with Docker](how-to/install-with-docker.md) or [Run on Kubernetes](how-to/run-on-kubernetes.md). Then read [Size the deployment](how-to/size-the-deployment.md), [Back up and restore](how-to/back-up-and-restore.md) and [Monitor status and failures](how-to/monitor-status-and-failures.md). For more than one replica, read [High availability support](explanation/high-availability-support.md).
* **Administrator.** Follow [Set up users, teams and single sign on](tutorials/set-up-users-teams-and-sso.md). Then read [Identity model](explanation/identity-model.md), [Grant permissions with nodes](how-to/grant-permissions-with-nodes.md) and [Review the audit log](how-to/review-audit-log.md).
* **Developer.** Read [Publish and consume artifacts](how-to/publish-and-consume-artifacts.md) and [Use tokens in CI](how-to/use-tokens-in-ci.md). Use [Search for artifacts](how-to/search-for-artifacts.md) to find what you need.
* **Plugin author.** Read [Write a plugin](how-to/write-a-plugin.md), then the [Plugin API](reference/plugin-api.md) and [Backend architecture](explanation/backend-architecture.md). To extend the dashboard, see [Extend the dashboard from a plugin](how-to/extend-the-dashboard-from-a-plugin.md).

## Choose by task

| I want to | Start with |
|---|---|
| Install and run Ingot | [Install with Docker](how-to/install-with-docker.md) or [Run the JAR](how-to/install-jar.md) |
| Publish and consume artifacts | [Publish and consume artifacts](how-to/publish-and-consume-artifacts.md) |
| Manage access and logins | [Manage users, teams and machine accounts](how-to/manage-users-teams-and-machine-accounts.md) |
| Create tokens for CI | [Use tokens in CI](how-to/use-tokens-in-ci.md) |
| Search for artifacts | [Search for artifacts](how-to/search-for-artifacts.md) |
| Operate a running instance | [Monitor status and failures](how-to/monitor-status-and-failures.md) |
| Look up a setting | [Environment variables](reference/environment-variables.md) or [Configuration file and startup parameters](reference/configuration-and-startup.md) |

## Upgrade an existing installation

To upgrade, start with [Back up before upgrading](how-to/back-up-before-upgrading.md). Then follow [Upgrade from Ingot 1.1.x](how-to/upgrade-ingot.md) if you run Ingot, or [Upgrade from Reposilite 3.x](how-to/upgrade-from-reposilite.md) if you run Reposilite. Verify the result with [Verify an upgrade](how-to/verify-an-upgrade.md).

## What changed in 1.2.0

* The identity model adds users, teams, machine accounts and permission nodes. See [Identity model](explanation/identity-model.md).
* Artifact search runs across all repositories. See [Search for artifacts](how-to/search-for-artifacts.md).
* The dashboard is rebuilt with Material Design 3. See [Dashboard pages](reference/dashboard-pages.md).

## Coming from Reposilite

Ingot keeps the image, the data directory and the port of Reposilite. It also reads the `REPOSILITE_` names of the local settings, the options and the file ownership variables when the `INGOT_` name is not set. [Drop-in compatibility with Reposilite](explanation/drop-in-compatibility.md) lists what stays the same and what differs. Tokens from Reposilite 2.x are migrated with the steps in [Upgrade from Reposilite 2.x](how-to/upgrade-from-reposilite-2.md).

## Sections

* **Tutorials** are guided lessons with a known result. Use them for a first contact.
* **How-to guides** finish one task for a reader who already runs Ingot.
* **Reference** lists the settings, routes, commands and tables to look up.
* **Explanation** describes why things work the way they do.

Each page covers one of these modes. To add a page, pick the mode first, then add it to [SUMMARY.md](SUMMARY.md).

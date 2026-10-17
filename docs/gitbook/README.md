---
description: Start page of the Ingot documentation. Explains what Ingot is, where to start, and how the four sections are organized.
---

# Ingot documentation

Ingot is a Maven repository manager. It is a drop-in replacement for Reposilite 3.x, so existing Maven, Gradle, sbt and Ivy setups keep working.

## Start here

* [First steps with Docker](tutorials/first-steps-with-docker.md) starts Ingot, creates the first administrator and publishes a library. Plan for about 30 minutes.
* [Set up users, teams and single sign on](tutorials/set-up-users-teams-and-sso.md) adds people, teams and a login provider.

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

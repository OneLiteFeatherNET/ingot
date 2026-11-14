---
description: Answers to the questions that come up before and after adopting Ingot, covering drop-in compatibility, Reposilite plugins, databases, going back to Reposilite, high availability, formats, the licence and where to ask for help.
---

# Frequently asked questions

Each answer is short and links to the page that gives the details. For a failure you are seeing now, go to [Troubleshoot Ingot](../how-to/troubleshoot.md) instead.

## Is Ingot really a drop-in replacement for Reposilite?

For the image, the data directory, the configuration and the clients, yes. Moving a deployment means changing one line: the image reference, or the dependency coordinate in a build. The paths, the port, the volume, the health check and the variables that Reposilite reads stay the same, and a data directory written by Reposilite is read as it is.

Some things do change under the hood, and they are visible to an operator:

* Tokens become machine accounts, and their rights move to a private team. See [Token migration](../explanation/token-migration.md).
* The old MANAGER permission becomes the instance administrator role. See [Identity model](../explanation/identity-model.md).
* The dashboard has a login, and users sign in with a password or an external provider.
* Some features are on by default, such as artifact search. Check the defaults before you assume that a feature is off.

The full list is in [Drop-in compatibility with Reposilite](../explanation/drop-in-compatibility.md). Each upgrade path has its own steps in [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md) and [Upgrade from Reposilite 2.x](../how-to/upgrade-from-reposilite-2.md).

## Do Reposilite plugins work with Ingot?

Plugins that were built against Reposilite keep their package names, `com.reposilite.*`, so their code is unchanged. A plugin that compiles against Reposilite compiles against Ingot after the dependency changes from `com.reposilite:reposilite` to `net.onelitefeather.ingot:ingot`. That is one line in the build file.

Test your plugin before you rely on it. Ingot documents its plugin API in [Plugin API](plugin-api.md), and parts of it are marked experimental. A plugin that uses an internal class instead of the documented API can break in a later release.

## Which databases does Ingot support?

* **SQLite** is the default and needs no setup. It is for one instance on one machine.
* **MariaDB** and **PostgreSQL** are supported. Use them for several replicas or when a database server is part of your backups.
* **MySQL** and **H2** are experimental. They are not covered by Ingot's integration tests.

See [Configure a database](../how-to/configure-database.md) for the connection string of each one.

## Can I go back to Reposilite after upgrading?

Yes, if you take a backup before the upgrade. The old version reads the state it had before the first start of Ingot, so a rollback restores that state. Anything created in Ingot after the upgrade is not visible to Reposilite, and tokens created in Ingot do not work there.

The steps and the list of what is lost are in [Roll back an upgrade](../how-to/roll-back-an-upgrade.md). Read it before you start the upgrade.

## What does high availability need?

Several replicas need four things:

* A shared database: MariaDB, or PostgreSQL. SQLite and H2 are not supported with more than one replica.
* Storage that every replica sees: S3, or a volume that several replicas mount. The shared volume is not tested, so the object storage path is the one covered by tests.
* The cluster bus, which is optional. It shares cache invalidations, revocations and log lines between replicas. See [Configure the cluster bus](../how-to/configure-cluster-bus.md).
* A rolling upgrade, one replica at a time, with the order that [High availability support](../explanation/high-availability-support.md#rolling-upgrade-rule) describes.

The page also lists which setups are tested on every pull request, and which are not supported.

## Does Ingot support formats other than Maven?

Ingot ships with the Maven format only. The repository format is an extension point for plugins, and it is marked experimental. See [Repository formats](plugin-api.md#repository-formats-experimental) in the plugin API reference.

## Does Ingot send data to other services?

Ingot makes a few outbound requests of its own, for example to check for a newer release. Each one, its address and how to turn it off are listed in [Outbound connections](outbound-connections.md). Requests made for repositories that proxy a remote host are the ones you configure yourself.

## What is the licence?

Ingot is licensed under the Apache License 2.0. It is a fork of [Reposilite](https://github.com/dzikoysk/reposilite) by dzikoysk, and the copyright notices of the upstream files are kept. The `LICENSE` and `NOTICE` files in the repository hold the details.

## Where do I ask for help?

* **Bugs and feature requests:** open an issue at [OneLiteFeatherNET/ingot](https://github.com/OneLiteFeatherNET/ingot/issues). The bug report form asks for the Ingot version, how you run Ingot, the environment, the relevant log output and the relevant configuration. A [support bundle](../how-to/collect-a-support-bundle.md) describes the instance without its secrets.
* **Security problems:** report them privately through the [security policy](https://github.com/OneLiteFeatherNET/ingot/security/policy), not in a public issue.

The issue templates do not list a chat channel yet.

## Related pages

* [Troubleshoot Ingot](../how-to/troubleshoot.md) gives the cause and the fix of common failures.
* [Glossary](glossary.md) explains the terms used across these pages.

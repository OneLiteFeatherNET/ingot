---
description: Explains what it means that Ingot is a drop-in replacement for Reposilite. Covers what stays the same for the image, the data, the configuration, the clients and the plugins, what changes under the hood, and what an operator should still look at.
---

# Drop-in compatibility with Reposilite

Ingot is a drop-in replacement for Reposilite. Moving an installation to Ingot should change one line: the image reference in a deployment, or the dependency coordinate in a build. Nothing else should need to change. No configuration file, volume, manifest or plugin is edited.

This page explains that promise in terms of what an operator and a plugin author can rely on. It also says where the promise stops. The steps for an actual upgrade are in [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md).

## What stays the same

### The image

The container image is `ghcr.io/onelitefeathernet/ingot`. It replaces the Reposilite image one for one. It keeps:

* The entrypoint, so existing commands and overrides still work.
* The paths `/app/data` for data and `/var/log/reposilite` for logs.
* The port `8080`.
* The volume for `/app/data`, so an existing data directory is adopted as it is.
* The health check.
* The environment variables that Reposilite reads, such as `JAVA_OPTS`, `PUID`, `PGID` and the options variable.

The server still serves the dashboard itself. A separate dashboard container is available, but it is opt-in.

The image starts as root, as Reposilite does. The entrypoint then drops to the service account, and it applies `PUID` and `PGID`. An operator who already runs the container as a fixed user (for example `user: "977:977"`) keeps doing so, and the entrypoint detects it.

The server file inside the image is called `ingot.jar`. The path `/app/reposilite.jar` stays as a symbolic link to it. Anything that names the jar by the old path keeps working.

### The data

A data directory written by Reposilite is read by Ingot as it is. The configuration and state files keep their names: `configuration.cdn`, `configuration.shared.json` and the SQLite database `reposilite.db`.

Access tokens are carried over with their names, secrets, expiry dates and descriptions. Their rights are carried over exactly, including the old way of matching routes. The migration runs on the first start, inside one database transaction, and nothing needs to be run by hand. See [Token migration](token-migration.md) for the design.

### The configuration

Settings from Reposilite still apply. Ingot reads the same `configuration.cdn` file and the same environment variables.

Environment variables and system properties can use the new `INGOT_` prefix, and the `REPOSILITE_` spelling is still read. If the same setting is given under both prefixes, the `INGOT_` value wins. The same holds for `INGOT_OPTS` and `REPOSILITE_OPTS`, and for `INGOT_FORCE_CHOWN` and `REPOSILITE_FORCE_CHOWN`.

### The clients

Maven, Gradle and other build tools keep their setup. Basic authentication with a token name and secret still works, including the browser variant. Bearer authentication works as well. A client that deploys with a migrated token keeps deploying, because the token keeps its secret and its rights.

### The plugins

Plugin packages keep the names `com.reposilite.*`, so the plugin code is unchanged. A plugin that compiles against Reposilite compiles against Ingot after the dependency changes from `com.reposilite:reposilite` to `net.onelitefeather.ingot:ingot`. That is one line in the build file.

## What changes under the hood

Some things work differently, while the outside stays the same.

* **Tokens belong to accounts.** A token from Reposilite becomes a machine account, which holds the token and a private team. The rights of the token move to that team. Rights are now shown and managed per account.
* **Administrators have a role.** The old MANAGER permission becomes the instance administrator role. See [Identity model](identity-model.md).
* **The dashboard has a login.** Users sign in with a password or an external login provider. The dashboard then keeps a browser session in cookies. See [Sessions, login and CSRF](sessions-and-login.md).
* **Settings are written back.** In the default mode, Ingot rewrites `configuration.cdn` at every start. Settings that come from environment variables or system properties are not written into the file.
* **Unknown environment settings are reported.** An `INGOT_LOCAL_` or `REPOSILITE_LOCAL_` variable that Ingot does not know is logged as a warning and ignored. It does not stop the start.
* **Some new features are on by default.** Artifact search, for example, is enabled by default. Check the defaults before you assume a feature is off.

Ingot adds to Reposilite. New environment variables get the `INGOT_` prefix, new deployment shapes are opt-in, and a change that breaks an existing installation without a good reason is treated as a bug.

## Checking an upgrade

Ingot keeps the promise for the image, the data and the clients. It does not replace checking the result. The checks after an upgrade are listed in [Verify an upgrade](../how-to/verify-an-upgrade.md).

## What cannot be undone

A rollback to Reposilite restores the old state, and nothing created in Ingot survives it. Users, machine accounts, tokens and rights created in Ingot are not visible to Reposilite. Tokens created in Ingot do not work there. Take a backup before you upgrade, and read [Roll back an upgrade](../how-to/roll-back-an-upgrade.md) before you start.

## Related pages

* [Token migration](token-migration.md) explains how old tokens become machine accounts.
* [Upgrade from Reposilite 3.x](../how-to/upgrade-from-reposilite.md) lists the steps.
* [Upgrade changes and supported paths](../reference/upgrade-changes.md) lists each change.
* [Identity model](identity-model.md) explains the new objects.

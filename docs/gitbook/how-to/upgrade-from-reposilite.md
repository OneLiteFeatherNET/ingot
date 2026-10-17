---
description: Upgrade an existing Reposilite 3.x installation to Ingot 1.2.0 by changing the image and starting it on the same data directory, including Reposilite 3.6.x settings.
---

# Upgrade from Reposilite 3.x

This guide covers Reposilite 3.x. Ingot is based on Reposilite 3.5.28. The container upgrade tests cover 3.5.28 on SQLite and on MariaDB 11.4. Reposilite 3.6.x is not covered by these tests, see [Reposilite 3.6.x settings](#reposilite-36x-settings).

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the data directory and, for MariaDB, of the database.
* The image `ghcr.io/onelitefeathernet/ingot:1.2.0`. Earlier Ingot releases do not run this migration.
* The image reference of your current Reposilite release, for a rollback.

## Steps

1. Stop the server.

2. Change the image in your deployment to 1.2.0. Leave the rest of the file as it is.

   ```yaml
   services:
     reposilite:
       # image: dzikoysk/reposilite:3.5.28
       image: ghcr.io/onelitefeathernet/ingot:1.2.0
   ```

3. Start the server on the same data directory. The existing data is adopted. The first start adds the identity and search tables and the configuration keys described in [Upgrade from Ingot 1.1.x](upgrade-ingot.md#what-changes-on-the-first-start).

4. Read the log of the first start. If the instance had persistent tokens, it contains a line of this shape:

   ```
   Identity | migrated 7 access token(s) to machine principals
   ```

5. Run the checks in [Verify an upgrade](verify-an-upgrade.md).

## Dashboard login with LDAP

The dashboard login keeps working when your configuration uses the `authentication.ldap` settings of Reposilite 3.x, as long as `login.ldap` is off:

* If `login.ldap` is off and `authentication.ldap` is enabled, the dashboard login uses `authentication.ldap`. No change is needed.
* If `login.ldap` is enabled, it is the only LDAP directory for the dashboard login. An enabled `login.ldap` that is incomplete serves no LDAP login at all.

## Reposilite 3.6.x settings

Ingot does not know the settings that Reposilite 3.6.x added, for example `parallelMetadataLookup`, `resolutionCacheLevel`, the mirror `blockedGroups`, or an S3 signer setting. Ingot ignores them. Startup does not fail because of them. What happens depends on where the setting is stored:

* **`configuration.cdn`:** an unknown key is not read. In the default AUTO mode, Ingot rewrites the file on start from its own settings, so the key is removed from the file.
* **Shared settings** (in the database, or in `configuration.shared.json`): an unknown field of a known settings domain is not read. The next save of that domain removes the field.
* **Environment variables** with the `INGOT_LOCAL_` or `REPOSILITE_LOCAL_` prefix that Ingot does not know: Ingot logs `Unknown local configuration property: <KEY>` as a warning and the variable has no effect.

Before you upgrade from 3.6.x, keep a copy of `configuration.cdn` and of the shared settings. After the upgrade, check the behaviour that those settings controlled. The upgrade tests do not cover 3.6.x, so treat this part as unverified until you have checked it on your own installation.

## Result

The server answers on its port. If the instance had persistent tokens, the log shows the migration line with their count, and the checks in [Verify an upgrade](verify-an-upgrade.md) pass.

To return to Reposilite, follow [Roll back an upgrade](roll-back-an-upgrade.md).

---
description: Upgrade an existing Reposilite 3.x installation to Ingot by changing the image and starting it on the same data directory.
---

# Upgrade from Reposilite 3.x

This guide covers Reposilite 3.x. The migration was tested with Reposilite 3.5.28 on SQLite and on MariaDB 11.4.

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the data directory and, for MariaDB, of the database.
* The first Ingot release that includes the identity model. Replace `<version>` in the steps with its tag. Earlier Ingot releases do not run this migration.
* The image reference of your current Reposilite release, for a rollback.

## Steps

1. Stop the server.

2. Change the image in your deployment. Leave the rest of the file as it is.

   ```yaml
   services:
     reposilite:
       # image: dzikoysk/reposilite:3.5.28
       image: ghcr.io/onelitefeathernet/ingot:<version>
   ```

3. Start the server on the same data directory. The existing data is adopted as it is.

4. Read the log of the first start. If the instance had persistent tokens, it contains a line of this shape:

   ```
   Identity | migrated 7 access token(s) to machine principals
   ```

5. Run the checks in [Verify an upgrade](verify-an-upgrade.md).

## Result

The server answers on its port. If the instance had persistent tokens, the log shows the migration line with their count, and the checks in [Verify an upgrade](verify-an-upgrade.md) pass.

To return to Reposilite, follow [Roll back an upgrade](roll-back-an-upgrade.md).

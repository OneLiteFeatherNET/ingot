---
description: Upgrade an Ingot 1.1.x installation to the first release that includes the identity model.
---

# Upgrade from Ingot 1.1.x

Ingot 1.1.0 has no identity tables. The first start under the identity release therefore runs the identity migration on your data. The migration was tested with Ingot 1.1.0 on SQLite.

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the data directory and, for MariaDB, of the database.
* The first Ingot release that includes the identity model. Replace `<version>` in the steps with its tag.
* The image reference of your current Ingot release, for a rollback.

## Steps

1. Stop the server.

2. Change the image in your deployment to the identity release. Leave the rest of the file as it is.

   ```yaml
   image: ghcr.io/onelitefeathernet/ingot:<version>
   ```

3. Start the server on the same data directory.

4. Read the log of the first start for the migration line, and run the checks in [Verify an upgrade](verify-an-upgrade.md).

## Result

The server answers on its port. If the instance had persistent tokens, the log shows the migration line with their count, and the checks in [Verify an upgrade](verify-an-upgrade.md) pass. Your tokens keep their names, secrets and routes.

To return to Ingot 1.1.x, follow [Roll back an upgrade](roll-back-an-upgrade.md).

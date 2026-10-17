---
description: Upgrade an Ingot 1.1.x installation to Ingot 1.2.0, the first release with the identity model and artifact search.
---

# Upgrade from Ingot 1.1.x

Ingot 1.2.0 is the first release with the identity model and artifact search. Ingot 1.1.x has neither. The first start of 1.2.0 on your data creates the new tables, copies your persistent tokens to machine principals, and builds the search index. The migration was tested with Ingot 1.1.0 on SQLite.

## What changes on the first start

* **Tables.** The identity tables and the search tables are created. The legacy token tables stay in the database, read-only, so that you can roll back.
* **Configuration.** `configuration.cdn` gains `hideSettingsSecrets` (default `false`), `searchEnabled` (default `true`) and `searchEngine` (default `database`). The values of the settings you already have do not change. In the default AUTO mode, Ingot rewrites the file on each start.
* **Mirror cache.** The `mirror_cache_entry` table gains a nullable `source_host` column. Existing rows keep working without a value.
* **Tokens.** If the instance has persistent tokens, they are copied once to machine principals. The log shows a line of the form `Identity | migrated 7 access token(s) to machine principals`.
* **Search index.** The index is empty, so the first start runs a full rebuild in the background. Until it completes, the index state is **Building**.

## Prerequisites

* A [backup](back-up-before-upgrading.md) of the data directory and, for MariaDB, of the database. With S3 storage, a copy of the bucket too.
* The image reference of your current Ingot release, for a rollback.
* The image `ghcr.io/onelitefeathernet/ingot:1.2.0`.

{% hint style="warning" %}
A rolling upgrade, with replicas of the old and the new release on one database, is not tested. Choose one of these instead: keep a single replica running for a short window, or freeze token changes (creating, rotating and revoking tokens) until every replica runs 1.2.0.
{% endhint %}

## Steps

1. Stop the server. With the Compose file of Ingot, run `docker compose stop ingot`.

2. Change the image in your deployment to 1.2.0. Leave the rest of the file as it is.

   ```yaml
   image: ghcr.io/onelitefeathernet/ingot:1.2.0
   ```

3. Start the server on the same data directory.

4. Read the log of the first start. If the instance had persistent tokens, the migration line is there. Run the checks in [Verify an upgrade](verify-an-upgrade.md).

5. Open the **Maintenance** page and wait for the **Search index** rebuild to finish. The state then reads **Ready**. If it reads **Degraded**, see [Rebuild the search index](rebuild-search-index.md#index-states).

## Result

The server answers on its port. If the instance had persistent tokens, the log shows the migration line with their count, and the checks in [Verify an upgrade](verify-an-upgrade.md) pass. Your tokens keep their names, secrets and routes.

To return to Ingot 1.1.x, follow [Roll back an upgrade](roll-back-an-upgrade.md).

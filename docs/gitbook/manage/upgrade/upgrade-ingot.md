---
description: Upgrade an Ingot installation from back up to roll back. Covers the backup of a Reposilite or Ingot installation, the upgrade from Ingot 1.1.x to 1.2.0 with what changes on the first start, the checks after an upgrade, and the rollback.
---

# Upgrade Ingot

This page takes an upgrade from the backup to the rollback, in the order you need the parts. The backup, the checks and the rollback apply to a Reposilite or an Ingot installation. The upgrade steps are for Ingot 1.1.x. For a Reposilite installation, follow [Move from Reposilite to Ingot](../../set-up/migrate/move-from-reposilite.md), then come back for the checks.

Ingot 1.2.0 is the first release with the identity model and artifact search. Ingot 1.1.x has neither. The first start of 1.2.0 on your data creates the new tables, copies your persistent tokens to machine principals, and builds the search index. The migration was tested with Ingot 1.1.0 on SQLite.

## Back up before upgrading

### Prerequisites

* Shell access to the host that runs the server.
* The image reference of the release you run now. You need it for a rollback, and it is the one you restore in [Roll back an upgrade](#roll-back-an-upgrade).

{% hint style="warning" %}
The upgrade rewrites the database. A rollback is only as good as the copy you kept, so do not start the upgrade before the archive is complete.
{% endhint %}

### Steps

1. Stop the server. With the Compose file of Ingot, the service is `ingot`:

   ```bash
   docker compose stop ingot
   ```

   For a container you started with `docker run`, use `docker stop <container>` with the name of your container.

2. Back up the database and the data directory, for your database:

   {% tabs %}
   {% tab title="SQLite" %}
   The database is a file in the data directory, so the archive of the data directory includes it.

   ```bash
   tar -czf ingot-backup-$(date +%F).tar.gz -C /path/to/data .
   ```
   {% endtab %}

   {% tab title="MariaDB" %}
   Dump the database first. The data directory does not contain the database.

   ```bash
   mariadb-dump -h <host> -u <user> -p <database> > ingot-backup-$(date +%F).sql
   ```

   Then archive the data directory. It contains `configuration.cdn`, the plugins and, with file storage, the repositories.

   ```bash
   tar -czf ingot-backup-$(date +%F).tar.gz -C /path/to/data .
   ```
   {% endtab %}
   {% endtabs %}

3. If the repositories are in S3, back up the bucket as well. The data directory does not hold the artifacts of an S3 repository. Use the copy tool of your object storage, and keep the copy from the same time as the database dump.

The [back up and restore](../operate/back-up-and-restore.md) page describes how to restore these copies.

### Result

The archive exists and is not empty. For SQLite, `tar -tzf ingot-backup-<date>.tar.gz` lists `reposilite.db` and `configuration.cdn`. For MariaDB, the `.sql` file exists and is not empty. For S3, the copy of the bucket is complete. Keep the copies next to the old image reference.

## Upgrade from Ingot 1.1.x

### What changes on the first start

* **Tables.** The identity tables and the search tables are created. The legacy token tables stay in the database, read-only, so that you can roll back.
* **Configuration.** `configuration.cdn` gains `hideSettingsSecrets` (default `false`), `searchEnabled` (default `true`) and `searchEngine` (default `database`). The values of the settings you already have do not change. In the default AUTO mode, Ingot rewrites the file on each start.
* **Mirror cache.** The `mirror_cache_entry` table gains a nullable `source_host` column. Existing rows keep working without a value.
* **Tokens.** If the instance has persistent tokens, they are copied once to machine principals. The log shows a line of the form `Identity | migrated 7 access token(s) to machine principals`.
* **Search index.** The index is empty, so the first start runs a full rebuild in the background. Until it completes, the index state is **Building**.
* **Schema lock.** On MariaDB, MySQL and PostgreSQL, the first start creates the `ingot_lock` table. On PostgreSQL the schema lock is a session lock, and the table holds the metadata leases. Replicas that start together take turns on the schema, and a replica may wait for the others for a short time. No setting has to change. `INGOT_LOCAL_SCHEMALOCKTIMEOUT` (seconds, default `300`) is optional. See [Multi-replica operation](../../references/architecture/multi-replica-operation.md#schema-initialisation).

### Upgrade prerequisites

* A [backup](#back-up-before-upgrading) of the data directory and, for MariaDB, of the database. With S3 storage, a copy of the bucket too.
* The image `ghcr.io/onelitefeathernet/ingot:1.2.0`.

{% hint style="warning" %}
A rolling upgrade, with replicas of the old and the new release on one database, is covered only for the order and the limits in [High availability support](../../references/architecture/high-availability-support.md#rolling-upgrade-rule). Start one replica of 1.2.0 first and replace the others after it. Until every replica runs 1.2.0, enable no scheduled cleanup policy and freeze token changes (creating, rotating and revoking tokens), because token changes are not tested. Alternatively, keep a single replica running for a short window.
{% endhint %}


### Upgrade steps

1. Stop the server. With the Compose file of Ingot, run `docker compose stop ingot`.

2. Change the image in your deployment to 1.2.0. Leave the rest of the file as it is.

   ```yaml
   image: ghcr.io/onelitefeathernet/ingot:1.2.0
   ```

3. Start the server on the same data directory.

4. Read the log of the first start. If the instance had persistent tokens, the migration line is there. Run the checks in [Verify an upgrade](#verify-an-upgrade).

5. Open the **Maintenance** page and wait for the **Search index** rebuild to finish. The state then reads **Ready**. If it reads **Degraded**, see [Rebuild the search index](../../configure/search/rebuild-search-index.md#index-states).


### Upgrade result

The server answers on its port. If the instance had persistent tokens, the log shows the migration line with their count, and the checks in [Verify an upgrade](#verify-an-upgrade) pass. Your tokens keep their names, secrets and routes.

To return to Ingot 1.1.x, follow [Roll back an upgrade](#roll-back-an-upgrade).


## Verify an upgrade

Run these checks after every upgrade, before you rely on the new instance.

### What the checks need

* The upgraded server is running.
* A token of an instance admin, or a token with the `ingot.admin.users` node. The principal list needs one of these. See [Permission nodes](../../references/identity/permission-nodes.md).
* A token with routes, and one action it is allowed to take and one it is not.
* The answers from before the upgrade, for comparison.
* The name of one artifact you know exists. Read it anonymously if it is in a public repository.

### Checks

1. Read the log of the first start and look for the migration line:

   ```
   Identity | migrated 7 access token(s) to machine principals
   ```

   The line appears on the first start when the instance has persistent tokens. A later start prints it only when it copies tokens that an older version created after a rollback.

2. List the principals with an instance admin token. Each migrated token appears as a machine principal.

   ```bash
   curl -u <admin>:<secret> https://<your-host>/api/identity/principals
   ```

3. Authenticate with the token that has routes. Try the action it is allowed to take and the one it is not. Each answers as it did before the upgrade.

4. Read the artifact you know. It answers as it did before the upgrade.

5. Check the search index state with an instance admin token. The response shows the state, the lag in seconds and the row count. `GET /api/maven/search/index` answers `404` when search is turned off on the instance.

   ```bash
   curl -u <admin>:<secret> https://<your-host>/api/maven/search/index
   ```

### What a pass looks like

All checks answer as they did before the upgrade.

If a token that worked before no longer authenticates, do not delete anything. Leave the data as it is and follow [Roll back an upgrade](#roll-back-an-upgrade).

### Run the Helm test hook

If you run Ingot with the Helm chart in `charts/ingot`, the chart can run a check pod with `helm test`. The hook is off by default, so the chart renders no test pod until you turn it on:

```bash
helm upgrade ingot charts/ingot --reuse-values --set tests.enabled=true
helm test ingot
```

The pod calls the health endpoint and the public page of the server. A failed check fails `helm test`, and the pod is kept for its logs.

To check an authenticated request as well, store a token in a Secret in the release namespace and name the Secret and its key:

```bash
kubectl create secret generic ingot-test --from-literal=token=<token>
helm upgrade ingot charts/ingot --reuse-values --set tests.enabled=true --set tests.tokenSecret.name=ingot-test --set tests.tokenSecret.key=token
helm test ingot
```

The chart reads the token from the Secret and never renders its value. The pod sends it as a bearer token to `/api/status/instance`, so use a token that endpoint accepts. Delete the Secret when you are done.

## Roll back an upgrade

### What the rollback needs

* The backup from [Back up before upgrading](#back-up-before-upgrading), if you want to restore it.

{% hint style="warning" %}
Changes made in Ingot after the upgrade are not visible to the old version. Recreate them there, as described in step 4.
{% endhint %}

### Rollback steps

1. Stop the server.

2. Change the image back to the previous release. Leave the rest of the file as it is.

3. Start the server on the same data directory. The old version reads the state it had before the first start of Ingot.

4. Recreate the changes you made in Ingot:
   * Tokens you created in Ingot do not authenticate on the old version. Create them again there.
   * Tokens you revoked in Ingot are active again on the old version. Revoke them again there.
   * Tokens you rotated in Ingot keep the secret they had before the rotation on the old version. The new secret does not work there. Rotate the token again on the old version and update the clients.
   * Rights you changed in Ingot are back to their earlier values. Change them again there.
   * Permission nodes you set in Ingot are not visible on the old version, which has no nodes. A token you limited with nodes in Ingot gets back the rights it had before the upgrade. Set the rights you need again there.

5. Check that the tokens and artifacts you had before the upgrade work.

To be certain, stop the server, restore the backup you took before the upgrade, and start the old image on the restored data.

### After the rollback

The old version starts on the same data directory, and the tokens and artifacts you had before the upgrade work again.

A token you create on the old version is imported again the next time you upgrade.

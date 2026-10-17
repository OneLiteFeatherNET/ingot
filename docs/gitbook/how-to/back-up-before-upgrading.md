---
description: Create a backup of a Reposilite or Ingot installation before an upgrade, for SQLite or MariaDB, with S3 storage included.
---

# Back up before upgrading

## Prerequisites

* Shell access to the host that runs the server.
* The image reference of the release you run now. You need it for a rollback.

{% hint style="warning" %}
The upgrade rewrites the database. A rollback is only as good as the copy you kept, so do not start the upgrade before the archive is complete.
{% endhint %}

## Steps

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

The [back up and restore](back-up-and-restore.md) page describes how to restore these copies.

## Result

The archive exists and is not empty. For SQLite, `tar -tzf ingot-backup-<date>.tar.gz` lists `reposilite.db` and `configuration.cdn`. For MariaDB, the `.sql` file exists and is not empty. For S3, the copy of the bucket is complete. Keep the copies next to the old image reference.

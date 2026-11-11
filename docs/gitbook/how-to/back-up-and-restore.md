---
description: Back up everything an Ingot instance needs (database, configuration, filesystem repositories, S3 buckets) and restore it in the right order.
---

# Back up and restore

An Ingot instance keeps its state in three places: the database, the working directory and, for S3 repositories, the bucket. A backup is complete only when all three match each other. This page lists what to copy, how to copy each part, and the order for a restore.

## What to back up

| Item | Where it lives | What it holds |
|:-----|:---------------|:--------------|
| Database | The SQLite file `reposilite.db` in the working directory, or the MariaDB or PostgreSQL database | Users, teams, tokens, shared settings (including S3 keys and the session signing key), the search index, the audit log and the cleanup state |
| `configuration.cdn` | Working directory | The database connection string, the port, the search switches and the other local settings |
| `configuration.shared.json` | Working directory | The shared settings. Only if you start Ingot with `-sc`. Without `-sc` the shared settings are in the database. |
| `repositories/` | Working directory | The files of every repository that uses filesystem storage |
| `plugins/` | Working directory | Installed plugins |
| S3 bucket | Your S3 service, under the key prefix of each S3 repository | The files of every repository that uses S3 storage |

In the Docker image the working directory is `/app/data`, and that path is the only volume. The database file, `configuration.cdn`, `repositories/` and `plugins/` are all there. S3 objects are not.

{% hint style="warning" %}
The database holds the S3 keys and the session signing key. Store the backups with the same care as the database itself.
{% endhint %}

## Keep the parts in step

* Take the database copy and the bucket copy at the same time. The search index lives in the database. Files that the bucket received after the database copy are missing from the index, and the index can list files that the bucket does not hold.
* Stop the instance for the copy when you can. That gives the cleanest result, especially for SQLite.
* If you cannot stop the instance, copy the database first, then the bucket. Rebuild the search index after a restore that mixes older parts. See [Rebuild the search index](rebuild-search-index.md).
* The session signing key is stored in the database. If you restore the files without the database, or start with a new database, the signing key changes, every browser session stops working, and users sign in again.

## Back up the database

{% tabs %}
{% tab title="SQLite" %}
You can take the copy while the instance runs. Use an online snapshot. Do not copy `reposilite.db` directly while the instance runs: SQLite keeps writing to the file, so the copy can be half-written.

**Online snapshot.** A snapshot writes a consistent copy of the database into the `backups/` directory of the working directory. The instance keeps serving requests while it runs. Each snapshot is a complete SQLite file. It gets a new name with the UTC time, so an existing snapshot is never replaced.

Call the endpoint with a token that has the maintenance area (`ingot.admin.maintenance`):

```bash
curl -u "<token>:<secret>" -X POST https://<your-host>/api/database/snapshots
```

The answer names the file, its size and the creation time. It does not show the path:

```json
{"fileName":"ingot-snapshot-20261109-120000.db","sizeBytes":1048576,"createdAt":"2026-11-09T12:00:00Z"}
```

In the Docker image the file is `/app/data/backups/ingot-snapshot-<date>-<time>.db`. Copy it out of the volume with `docker cp`, or read it from the host directory that backs `/app/data`.

You can also take a snapshot from the command line. The command writes the snapshot and exits. It does not start the web server, so it works while another instance uses the same database. Run it as the service user of the instance, so that the file belongs to the user that runs Ingot:

```bash
docker exec --user reposilite <container> java -jar /app/ingot.jar --working-directory=/app/data --snapshot-database
```

The command exits with status `0` when the snapshot is written. On failure it removes the partial file and exits with a non-zero status.

Online snapshots work for SQLite only. For MariaDB, MySQL, PostgreSQL and custom JDBC URLs the endpoint answers with status `409` and the command exits with a non-zero status. Use the dump tools of those databases, as described in the tabs above.

{% hint style="warning" %}
A snapshot holds the same secrets as the database: the S3 keys and the session signing key. Ingot creates the file readable only by its owner where the file system supports it. Store the file with the same care as the database. Ingot does not delete old snapshots. Remove them yourself when you no longer need them.
{% endhint %}

A snapshot contains the writes committed before it started. Writes that arrive during the snapshot may be missing from it. For the cleanest result, stop the instance and copy the file.

**Copy the file with the instance stopped.** This is the other option. Use it when the copy must match the working directory archive exactly.

1. Stop the instance so that the file is closed.

   ```bash
   docker stop <container>
   ```

2. Copy the database file to a backup location. Use the file name from your `database` setting. The default is `reposilite.db`.

   ```bash
   cp /path/to/data/reposilite.db ./backup/reposilite-$(date +%F).db
   ```
{% endtab %}

{% tab title="MariaDB" %}
1. Dump the database. The option `--single-transaction` takes a consistent copy of InnoDB tables while the instance runs.

   ```bash
   mariadb-dump --single-transaction -h <host> -P <port> -u <user> -p ingot > ingot-$(date +%F).sql
   ```

2. Check that the dump is not empty and ends with a completed dump.

   ```bash
   tail -n 3 ingot-$(date +%F).sql
   ```
{% endtab %}

{% tab title="PostgreSQL" %}
1. Dump the database in the custom format.

   ```bash
   pg_dump -Fc -h <host> -p <port> -U <user> -f ingot-$(date +%F).dump ingot
   ```

2. List the contents of the dump to check that it is readable.

   ```bash
   pg_restore --list ingot-$(date +%F).dump | head
   ```
{% endtab %}
{% endtabs %}

### Without stopping the instance

Turn on read-only mode, so that no write lands while the copy is taken. Reads keep working, and the instance stays up. Wait at least 10 seconds after the change, then copy the database. Turn the mode off after the copy. This works for MariaDB and PostgreSQL. For SQLite, keep stopping the instance for the file copy, because read-only mode does not make a file that is open for the running instance consistent.

```bash
curl -u "<token-name>:<token-secret>" -X POST https://ingot.example.com/api/admin/read-only
# copy the database with your database tool, then
curl -u "<token-name>:<token-secret>" -X DELETE https://ingot.example.com/api/admin/read-only
```

The steps and the caveats are in [Freeze writes for maintenance](freeze-writes-for-maintenance.md).

## Back up the files in the working directory

Archive the files of the working directory. For a filesystem repository, the archive must include `repositories/`.

```bash
tar -czf ingot-files-$(date +%F).tar.gz -C /path/to/data configuration.cdn repositories plugins
```

Add `configuration.shared.json` if you start with `-sc`. Remove any name your instance does not have, because `tar` reports an error for a missing path. With SQLite, add the database file as well, and take the archive while the instance is stopped, so that the database file and the archive come from the same moment.

## Back up the S3 bucket

Copy the bucket with [rclone](https://rclone.org/). rclone is not part of Ingot. Its commands follow rclone's own syntax. The remote names `ingot-s3` and `ingot-backup` are examples that you define with `rclone config`.

```bash
rclone copy ingot-s3:my-bucket/reposilite ingot-backup:ingot-backups/$(date +%F)/s3
```

Use `copy`, not `sync`. `sync` deletes files at the destination that are missing at the source, which can remove an older backup. Use the key prefix in the source path if you set one. Leave it out if the bucket belongs to Ingot alone.

Compare the two sides after the copy.

```bash
rclone check ingot-s3:my-bucket/reposilite ingot-backup:ingot-backups/$(date +%F)/s3
```

## Sealed secrets

When a settings key is set, the secret values in the database and in `configuration.shared.json` are sealed. A backup then holds the sealed values, and it restores only with the key that wrote it. Keep the key with the backup, or in the same secret store, and check that the restored instance is given that key. A restore without the key stops at start with a message that names the domain and the property. See [Settings encryption](../reference/settings-encryption.md).

## Restore

Restore in this order. The database defines which storage each repository uses, so it must be in place before the instance starts.

1. Stop the instance. Make sure no other instance uses the same database.

2. Restore the database.

   * SQLite: copy the backup file to the working directory under the name of your `database` setting. A snapshot from `backups/` is restored the same way. Copy it over the database file. The instance must be stopped while you do this.
   * MariaDB or PostgreSQL: create an empty database and load the dump into it. For MariaDB, use `mariadb -h <host> -u <user> -p ingot < ingot-<date>.sql`. For PostgreSQL, use `pg_restore --clean --if-exists -h <host> -U <user> -d ingot ingot-<date>.dump`.

3. Restore the files in the working directory: `configuration.cdn`, `configuration.shared.json` if you use one, `repositories/` and `plugins/`. Check that the database value in `configuration.cdn` points at the database you restored.

4. Restore the S3 objects into the bucket. Create the bucket first if it is missing and you turned off bucket creation.

   ```bash
   rclone copy ingot-backup:ingot-backups/<date>/s3 ingot-s3:my-bucket/reposilite
   ```

5. Start the instance.

6. Check the restore. Sign in. Open a repository and download an artifact that you know. Search for it. If the search does not find files that the repositories hold, [rebuild the search index](rebuild-search-index.md).

## Result

The instance starts with the users, tokens and settings from the backup. The repositories list the same artifacts as before the backup, and downloads return the same bytes. Browser sessions from before the backup keep working only when the database was restored, because the signing key is stored there.

## Related

* [Back up before upgrading](back-up-before-upgrading.md) covers the copy that you take before a new version.
* [Roll back an upgrade](roll-back-an-upgrade.md) describes returning to an older version with the copy.
* [Use S3 storage](use-s3-storage.md) and [Configure a database](configure-database.md) describe the two storage parts.

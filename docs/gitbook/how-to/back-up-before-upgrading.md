---
description: Create a backup of a Reposilite or Ingot installation before an upgrade, for SQLite or MariaDB.
---

# Back up before upgrading

## Prerequisites

* Shell access to the host that runs the server.
* The image reference of the release you run now. You need it for a rollback.

{% hint style="warning" %}
The upgrade rewrites the database. A rollback is only as good as the copy you kept, so do not start the upgrade before the archive is complete.
{% endhint %}

## Steps

{% tabs %}
{% tab title="SQLite" %}
1. Stop the server.

   ```bash
   docker stop reposilite
   ```

2. Archive the whole data directory. It contains `reposilite.db`, `configuration.cdn`, the repositories and the plugins.

   ```bash
   tar -czf reposilite-backup-$(date +%F).tar.gz -C /path/to/data .
   ```
{% endtab %}

{% tab title="MariaDB" %}
1. Dump the database.

   ```bash
   mariadb-dump -h <host> -u <user> -p <database> > reposilite-backup-$(date +%F).sql
   ```

2. Archive the data directory. It contains `configuration.cdn`, the repositories and the plugins.

   ```bash
   tar -czf reposilite-backup-$(date +%F).tar.gz -C /path/to/data .
   ```
{% endtab %}
{% endtabs %}

## Result

The archive exists and is not empty. For SQLite, `tar -tzf reposilite-backup-<date>.tar.gz` lists `reposilite.db` and `configuration.cdn`. For MariaDB, the `.sql` file exists and is not empty. Keep it next to the old image reference.

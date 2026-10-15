---
id: upgrading
title: Upgrading
---

This guide shows how to move an existing installation to Ingot, from Reposilite 3.x, from an earlier Ingot release, or from
Reposilite 2.x. It also shows how to check the result and how to go back if you have to.

### Before you upgrade

Take a backup first. The upgrade rewrites the database, and a rollback is only as good as the copy you kept.

For an installation on SQLite, stop the server and copy the whole data directory. It contains `reposilite.db`,
`configuration.cdn`, the repositories and the plugins:

```bash
docker stop reposilite
tar -czf reposilite-backup-$(date +%F).tar.gz -C /path/to/data .
```

For an installation on MariaDB, dump the database as well as the data directory:

```bash
mariadb-dump -h <host> -u <user> -p <database> > reposilite-backup-$(date +%F).sql
```

Keep the old image reference next to the backup. A rollback needs both.

### Upgrade from Reposilite 3.x

Change the image in your deployment and start it again. Nothing else changes: the entrypoint, the paths, the port, the
volume, the environment variables and the configuration file names stay the same, and your existing data directory is
adopted as it is.

```yaml
services:
  reposilite:
    # image: dzikoysk/reposilite:3.5.28
    image: ghcr.io/onelitefeathernet/ingot:<version>
```

Use the first release that includes the identity model. Earlier Ingot releases do not run the migration described here.

On the first start, Ingot copies every persistent access token into its identity tables. Each token is copied in its own
step, inside a single database transaction, and the old tables are not changed. In the log you will see a line of this shape:

```
Identity | migrated 7 access token(s) to machine principals
```

What the migration does to your data:

- Each persistent token becomes a machine principal with the same name. Its secret and its expiry date are kept, so every
  client keeps authenticating with the token it already has.
- A token with the manager permission becomes an instance admin. The manager keeps every right it had.
- Each route of a token becomes a legacy grant of the same token, with read or write as before. The routes keep their
  behaviour, including the prefix matching of Reposilite 3.x.
- The settings you changed in 3.x stay as they are. Ingot adds one setting, `hideSettingsSecrets`, to `configuration.cdn`
  with its default value of `false`. Nothing else in the file changes.
- Ingot adds the `audit`, `cleanup` and `login` settings domains with their defaults.

Temporary tokens, which you pass with `--token`, are not stored and are not migrated. Pass them again as before.

### Upgrade from an earlier Ingot release

The same steps apply. Ingot 1.1.0 has no identity tables, so its first start under the identity release is the first run
of the identity migration on your data. Your tokens keep their names, secrets and routes as described above.

### Upgrade from Reposilite 2.x

Reposilite 2.x stored its tokens in `tokens.dat`, which Ingot cannot read directly. The migration plugin converts that file,
and the `token-import` command brings the tokens in:

1. Put the `migration-plugin` jar into the `plugins` directory of the data directory, and start the server on the data
   directory that holds `tokens.dat`.
2. The plugin writes `tokens.json` next to `tokens.dat` and logs the next step.
3. In the console of the running server, run:

   ```
   token-import tokens.json
   ```

4. Check the tokens, as described below, and then remove the plugin jar.

The secrets of Reposilite 2.x are stored as BCrypt hashes, and those hashes are copied unchanged, so clients keep the
secret they already use. Only the token names, secrets and rights are imported. Statistics from 2.x are not imported.

### Check the upgrade

Run these checks after every upgrade, before you rely on the new instance.

1. Read the log of the first start and look for the migration line. It appears on the first start, and again only when a
   later start copies tokens that an older version created after a rollback.
2. Authenticate with a manager token. This lists every token as a machine principal:

   ```bash
   curl -u <manager>:<secret> https://<your-host>/api/identity/principals
   ```

3. Authenticate with a token that has routes, and try an action it is allowed to take and one it is not. Each should
   answer as it did before the upgrade.
4. Read one artifact that you know, anonymously if it is in a public repository.

If a token that worked before no longer authenticates, keep the instance running, do not delete anything, and roll back
as described below.

### Roll back

Stop the server, change the image back to the previous release, and start it on the same data directory. The old version
reads the state it had before the first start of Ingot, so the tokens and artifacts you had before the upgrade still work.

Changes made in Ingot after the upgrade are not visible to the old version. This includes tokens you created, tokens you
revoked and rights you changed in Ingot. Recreate those on the old version after the rollback. A token created on the old
version after the rollback is picked up again when you upgrade a second time, because Ingot copies every legacy token it
has not seen before.

If you need to be certain, restore the backup you took before you upgraded.

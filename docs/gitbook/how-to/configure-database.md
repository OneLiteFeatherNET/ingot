---
description: Choose and set the database of an Ingot instance (SQLite, MariaDB or PostgreSQL), with the connection string format, the places where it is set, and the experimental types.
---

# Configure a database

Ingot keeps its state in one database: users, teams, tokens, shared settings, the search index, the audit log and the cleanup state. SQLite is the default and needs no setup. Use MariaDB or PostgreSQL when you run several instances or want a database server that you back up with your usual tools.

## Support status

| Type | Connection string starts with | Status |
|:-----|:------------------------------|:-------|
| SQLite | `sqlite` | Supported. Default. |
| MariaDB | `mariadb` | Supported. |
| PostgreSQL | `postgresql` | Supported. |
| MySQL | `mysql` | Experimental. Not covered by Ingot's integration tests. |
| H2 | `h2` | Experimental. Not covered by Ingot's integration tests. |
| Custom JDBC URL | `jdbc:` | Uses the database type of the URL. See [Custom JDBC URLs](#custom-jdbc-urls). |

Use the experimental types only to evaluate Ingot. Back up before you try them.

## Before you begin

* For MariaDB or PostgreSQL: a server that the Ingot host can reach, an empty database, and a user with the rights to create and change tables in that database. Ingot creates its tables on start.
* For SQLite: write access to the working directory. The file is created there.

## The connection string

The database is one setting, `database`. It is a single string with the type first, then the values of that type, separated by single spaces.

| Type | Format | Example |
|:-----|:-------|:--------|
| SQLite | `sqlite <file-name>` | `sqlite reposilite.db` |
| SQLite, temporary | `sqlite --temporary` | `sqlite --temporary` |
| MariaDB | `mariadb <host>:<port> <database> <user> <password>` | `mariadb db.internal:3306 ingot ingot secret` |
| PostgreSQL | `postgresql <host>:<port> <database> <user> <password>` | `postgresql db.internal:5432 ingot ingot secret` |
| MySQL (experimental) | `mysql <host>:<port> <database> <user> <password>` | `mysql db.internal:3306 ingot ingot secret` |
| H2 (experimental) | `h2 <file-name>` | `h2 ingot` |

Notes on the format:

* The file name of SQLite is relative to the working directory. In the Docker image the working directory is `/app/data`. The default name `reposilite.db` is kept for existing installations.
* `--temporary` creates a file in the temporary directory and deletes it when Ingot stops. Use it only for tests.
* The host and the port are one value, `host:port`. A host without a port uses the default port of the driver.
* Ingot splits the string at spaces. A value that contains a space cannot be written in this form, and a password with a space does not work. Use a custom JDBC URL for that case.
* The pool size for MariaDB, PostgreSQL and MySQL comes from `databaseThreadPool`, which defaults to `1`. SQLite and H2 always use one connection.

### Custom JDBC URLs

A string that starts with `jdbc:` is used as the JDBC URL as it is. Ingot does not split it, and it does not read a user or a password from the rest of the string. The URL must start with one of these prefixes: `jdbc:mariadb`, `jdbc:mysql`, `jdbc:postgresql`, `jdbc:sqlite` or `jdbc:h2`. Any other prefix stops the start.

```text
jdbc:postgresql://db.internal:5432/ingot?<credential parameters of the driver>
```

Put the credentials into the URL in the form your driver documents. Ingot does not check those parameters.

## Where the value is set

Ingot reads the database value from these places. The first one that is set wins.

1. The `--database` command line option. In the Docker image pass it in `INGOT_OPTS`.
2. The system property `-Dingot.local.database` or `-Dreposilite.local.database`. In the Docker image pass it in `JAVA_OPTS`. If both are set, `ingot.local.database` wins.
3. The environment variable `INGOT_LOCAL_DATABASE` or `REPOSILITE_LOCAL_DATABASE`. If both are set, `INGOT_LOCAL_DATABASE` wins.
4. The key `database` in `configuration.cdn`, in the working directory.
5. The default, `sqlite reposilite.db`.

Environment variables and system properties are not written back to `configuration.cdn`.

{% hint style="warning" %}
The comment for `database` in a generated `configuration.cdn` lists the user and the password as separate values after the database name. That is the order of the values inside the one string. It does not mean that the user and the password are separate settings. Put all of them in the same string.
{% endhint %}

## Steps

{% tabs %}
{% tab title="MariaDB" %}
1. Create the database and a user on the MariaDB server. Use a password you generate for this database.

   ```sql
   CREATE DATABASE ingot;
   CREATE USER 'ingot'@'%' IDENTIFIED BY '<password>';
   GRANT ALL PRIVILEGES ON ingot.* TO 'ingot'@'%';
   ```

2. Set the value. For a Docker deployment, add it to the environment of the container.

   ```yaml
   environment:
     INGOT_LOCAL_DATABASE: "mariadb db.internal:3306 ingot ingot <password>"
   ```

3. Start Ingot. Then list the tables of the database. The list contains a table named `settings`.

   ```sql
   USE ingot;
   SHOW TABLES;
   ```
{% endtab %}

{% tab title="PostgreSQL" %}
1. Create the database and a user on the PostgreSQL server.

   ```sql
   CREATE USER ingot WITH PASSWORD '<password>';
   CREATE DATABASE ingot OWNER ingot;
   ```

2. Set the value. For a Docker deployment, add it to the environment of the container.

   ```yaml
   environment:
     INGOT_LOCAL_DATABASE: "postgresql db.internal:5432 ingot ingot <password>"
   ```

3. Start Ingot. Then list the tables of the database with `\dt`. The list contains a table named `settings`.
{% endtab %}

{% tab title="SQLite" %}
1. Leave the value unset, or set it to the file name you want.

   ```text
   sqlite reposilite.db
   ```

2. Start Ingot. The file appears in the working directory. In the Docker image that is `/app/data`, which is the volume of the container.
{% endtab %}
{% endtabs %}

## Result

The instance starts, and the dashboard opens. The database contains the `settings` table. With SQLite, the file exists in the working directory. Restart the instance once and check that the users and tokens you created are still there. If they are not, the instance uses a different database than you think. Check the value in the list of places above.

## Next steps

* Back up the database before you upgrade. See [Back up and restore](back-up-and-restore.md).
* Moving existing data from one database type to another is not described here.

---
description: Reference for the Ingot configuration files, the keys of the local configuration, the command line parameters, the database connection formats and the order in which configuration sources are applied.
---

# Configuration file and startup parameters

Ingot reads two kinds of configuration. The local configuration holds the settings of one instance and is read at start. The shared settings are kept in the database and are shared by all instances. This page covers the local configuration and the command line. The shared settings are in [Settings domains](settings-domains.md).

## Configuration sources

| Source | Default location | Content |
| --- | --- | --- |
| Local configuration file | `configuration.cdn` in the working directory | Keys listed in [Local configuration keys](#local-configuration-keys). The file uses the CDN format, not YAML. |
| Environment variables | `INGOT_LOCAL_<KEY>`, `REPOSILITE_LOCAL_<KEY>` | The same keys. See [Environment variables](environment-variables.md). |
| System properties | `ingot.local.<key>`, `reposilite.local.<key>` | The same keys. |
| Command line | The parameters of the `ingot` command | Startup options, and overrides for `hostname`, `port` and `database`. |
| Shared settings | Database table `settings`, row `remote-shared-configuration` | Settings domains. A file is used instead only with `--shared-configuration`. |

The names `configuration.cdn`, `configuration.shared.json` and `reposilite.db` keep the Reposilite spelling on purpose, so an existing Reposilite data directory is read without changes.

## Order of precedence

Later steps override earlier steps for the same key.

1. Default value of the key.
2. Value in `configuration.cdn`.
3. Environment variable `REPOSILITE_LOCAL_<KEY>`, or the file it names with `REPOSILITE_LOCAL_<KEY>_FILE`.
4. Environment variable `INGOT_LOCAL_<KEY>`, or the file it names with `INGOT_LOCAL_<KEY>_FILE`.
5. System property `reposilite.local.<key>`, or the file it names with `reposilite.local.<key>.file`.
6. System property `ingot.local.<key>`, or the file it names with `ingot.local.<key>.file`.
7. Command line `--hostname`, `--port` and `--database`, when given. These three parameters are the only command line overrides of local keys.

Within one pair, the `INGOT_` spelling wins over the `REPOSILITE_` spelling. A system property wins over an environment variable. A file spelling takes the place of the plain spelling of the same source, so it sits at the same step: a file read from `INGOT_LOCAL_<KEY>_FILE` is overridden by a system property in step 5 or 6, as a plain variable is. When one source sets both the plain and the file spelling, the plain one is used. See [File values](environment-variables.md#file-values).

Overrides from the environment and from system properties are applied after the file has been loaded and rewritten. They are never written back to `configuration.cdn`.

The log shows each applied override as `Local configuration has been updated by external property: <key>=<first character>***<last character>`, so the value is not printed in full. A name that matches no key is logged as `Unknown local configuration property` and ignored.

## Local configuration file

| Property | Behavior |
| --- | --- |
| Location | `configuration.cdn` in the working directory. `--local-configuration` changes the path. A relative path is resolved against the working directory. |
| Mode `auto` (default) | After the file is loaded, Ingot renders it again. Keys that are missing get their default value and the header comments are refreshed. |
| Mode `none` | The file is read and never written. Set it with `--local-configuration-mode none`. |
| Template | `--generate-configuration` writes a template and exits. |

{% hint style="info" %}
Another Ingot or Reposilite version may rewrite the file with different header comments. Values you set are kept.
{% endhint %}

## Local configuration keys

Each key is a property of the local configuration. Types are `Boolean`, `Int`, `Long` and `String`.

### General and network

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `hostname` | String | `0.0.0.0` | Address to bind. `127.0.0.1` accepts local connections only. |
| `port` | Int | `8080` | Port to bind. |
| `idleTimeout` | Long | `30000` | Idle timeout of the HTTP connectors, in milliseconds. |
| `bypassExternalCache` | Boolean | `true` | Adds cache bypass headers to every response of the `/api/` scope, so proxies and browsers do not cache API answers. |

### Database

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `database` | String | `sqlite reposilite.db` | Database connection. See [Database connection](#database-connection). |
| `databaseThreadPool` | Int | `1` | Size of the database connection pool. The `sqlite` and `h2` connection strings always use 1. |

### TLS

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `sslEnabled` | Boolean | `false` | Opens an encrypted connector next to the plain one. |
| `sslPort` | Int | `443` | Port of the encrypted connector. |
| `keyPath` | String | `${WORKING_DIRECTORY}/cert.pem ${WORKING_DIRECTORY}/key.pem` | Key material. See [Key path](#key-path). |
| `keyPassword` | String | empty | Password of the key or the key store. |
| `enforceSsl` | Boolean | `false` | Redirects plain HTTP requests to `sslPort`. |

#### Key path

`${WORKING_DIRECTORY}` is replaced with the absolute working directory. The extension decides the format.

| Extension of the value | Format |
| --- | --- |
| `.pem` | Two paths separated by one space: certificate first, private key second. |
| `.jks` | One path to a Java key store. |
| Anything else | Start fails with `Provided key extension is not supported.` |

### Performance

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `webThreadPool` | Int | `16` | Threads that accept and parse HTTP connections. |
| `ioThreadPool` | Int | `8` | Threads for tasks that use blocking IO. |
| `compressionStrategy` | String | `none` | `none` or `gzip`. The code also accepts `brotli`. Any other value stops the start with `Unknown compression strategy`. |
| `cachedLogSize` | Int | `64` | Number of log messages kept for the dashboard console. |

### Frontend

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `defaultFrontend` | Boolean | `true` | Serves the dashboard from this instance. Set `false` when the dashboard runs in its own container. |
| `pluginUi` | Boolean | `true` | Lets server plugins contribute dashboard pages, menu entries and settings sections as data. `false` shows only what Ingot ships: the plugin manifest is empty and contributed pages are not served. The routes of the plugins are not changed. See [Plugin API](plugin-api.md#dashboard-pages). |
| `basePath` | String | `/` | Base path of the instance. A subdomain is preferred over a custom base path. |

### Shared settings and search

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `hideSettingsSecrets` | Boolean | `false` | Replaces every non-empty secret in the settings API with `********`. Sending `********` back keeps the stored value. See [Settings domains](settings-domains.md#settings-api). |
| `searchEnabled` | Boolean | `true` | `false` makes the search endpoints answer `404` and stops the index workers. The search tables stay. |
| `searchEngine` | String | `database` | Search backend that answers artifact search and holds the index. `database` ships with Ingot, and a plugin can register more ids. Environment variable `INGOT_LOCAL_SEARCHENGINE`, with `REPOSILITE_LOCAL_SEARCHENGINE` as fallback. An id that no backend has registered answers `503` on the search routes, instead of `404`. See [Choose a search backend](../how-to/choose-a-search-backend.md). |

### Debug

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `debugEnabled` | Boolean | `false` | Enables Javalin development logging and writes the JSON schema of each settings domain to the debug log at start. |

## Database connection

The `database` key and the `--database` parameter take the same value. The first word selects the database.

| Database | Status | Format |
| --- | --- | --- |
| SQLite | Supported | `sqlite <file-name>` or `sqlite --temporary` |
| MariaDB | Supported | `mariadb <host>:<port> <database> <user> <password>` |
| PostgreSQL | Supported | `postgresql <host>:<port> <database> <user> <password>` |
| MySQL | Experimental | `mysql <host>:<port> <database> <user> <password>` |
| H2 | Experimental | `h2 <file-name>` |
| Custom JDBC URL | Depends on the driver | `jdbc:<driver>:...` |

SQLite is the default. A relative SQLite or H2 file name is resolved against the working directory. `--temporary` (aliases `--temp` and `-t`) creates a temporary file that is deleted when the process ends.

A value that starts with `jdbc:` is used as the JDBC URL as it is. Only the prefixes `jdbc:mariadb`, `jdbc:mysql`, `jdbc:postgresql`, `jdbc:sqlite` and `jdbc:h2` select a driver.

{% hint style="warning" %}
For a `jdbc:` value, Ingot reads no separate user name and password. Put the credentials into the URL. The comment that Ingot writes into a new `configuration.cdn` shows `user password` after a JDBC URL. The code does not split them off.
{% endhint %}

{% hint style="warning" %}
The comment in a generated `configuration.cdn` still lists `postgresql` under the experimental providers. PostgreSQL is a supported database. MySQL and H2 are experimental.
{% endhint %}

## Shared settings source

| Aspect | Behavior |
| --- | --- |
| Default source | The `settings` table of the database, under the name `remote-shared-configuration`. All instances that use the same database share it. |
| Refresh | Each instance checks the database every 10 seconds and loads a changed document. |
| File source | `--shared-configuration <file>` reads a JSON file instead. An empty value selects `configuration.shared.json`. By default the file is read at start only, so a change needs a restart. With `INGOT_SHARED_CONFIGURATION_RELOAD=true` the file is checked every 10 seconds, a changed document is applied, and the API refuses writes to the domains the file holds, see [Shared settings reload](environment-variables.md#shared-configuration-reload). |
| Template | `--generate-configuration shared` writes a template for the plugins that declare settings. |
| Load errors | A document that cannot be loaded stops the start. `--ignore-shared-configuration-errors` starts with default values instead. |

## Settings secret references

A secret field of the shared settings can name the secret instead of holding it. That keeps the secret out of the database, out of a shared configuration file in Git and out of the settings API. Secret fields are the S3 endpoint, access key and secret key, the mirror password and HTTP proxy, the LDAP bind password, the OIDC client secret and the search user password. Other fields are never resolved.

References are off by default. Turn them on with `INGOT_SETTINGS_SECRET_REFERENCES=true`, and list what they may read, see [Environment variables](environment-variables.md#settings-secret-references).

| Text in a secret field | Meaning |
| --- | --- |
| `${env:NAME}` | The value of the variable `NAME`. The variable must be listed in `INGOT_SETTINGS_SECRET_ENV` and must be set. |
| `${file:/absolute/path}` | The content of the file, without the line breaks at its end. The file must lie in a directory listed in `INGOT_SETTINGS_SECRET_FILES`, must hold UTF-8 text and must exist. |
| `$${` | A literal `${`. Write `$${env:NAME}` to store the text `${env:NAME}` as a value. |
| any other `${` | Plain text, unchanged. |

Several references can appear in one value, for example an endpoint that embeds its credentials:

```
https://${env:S3_USER}:${file:/run/secrets/s3-password}@s3.example.test
```

Rules that apply to every reference:

* The document keeps the reference as written. The settings API returns it as written, even when `hideSettingsSecrets` is on, because a reference is not a secret. A literal secret is still shown as `********`. A value that mixes a literal with a reference is treated as a literal secret.
* A reference is resolved when the document is loaded: at start, on a reload of the shared configuration and when a newer document is read from the database. Ingot does not read a file again for each request, so a rotated file takes effect at the next load. Changing the file alone does not start a load. Restart the instance, or save the shared settings, after you rotate a secret.
* Every instance that shares a document must be able to resolve its references, because each one reads its own environment and its own mounted files. An instance that cannot resolve a reference stops its own start, unless `--ignore-shared-configuration-errors` is set, and says so in its log.
* A write through the settings API that names a reference which cannot be resolved on that instance is answered with `400`. The message names the field and the reference, and nothing is stored.
* The message of a failing reference names the domain, the property and the reference. It never contains a resolved value.

| Moment | A reference cannot be resolved | Effect |
| --- | --- | --- |
| Start | The start stops, as it does for any broken shared configuration | `--ignore-shared-configuration-errors` starts with default settings instead |
| Reload or a newer document from the database | The new document is not applied | The previous settings stay in place and one error is logged |
| Write through the API | The write is refused | `400`, nothing is stored |

Setting `INGOT_SETTINGS_SECRET_REFERENCES` to `false` again returns every value to plain text. A stored reference is then used as the text it is, so check the secret fields before you switch it off.

## Command line parameters

Run the jar as `java -jar ingot-<version>.jar [parameters]`. The container passes the value of `INGOT_OPTS` or `REPOSILITE_OPTS` to the same command, see [Environment variables](environment-variables.md#container-variables).

| Parameter | Aliases | Argument | Default | Meaning |
| --- | --- | --- | --- | --- |
| `--help` | | none | | Prints the usage and exits. |
| `--version` | `-v` | none | | Prints the version and exits. |
| `--working-directory` | `-wd` | path | current directory | Working directory of the instance. Relative paths below are resolved against it. |
| `--plugin-directory` | `-pd` | path | `<working directory>/plugins` | Directory that plugins are loaded from. |
| `--generate-configuration` | `-gc` | `configuration.cdn` or `local`, `configuration.shared.json` or `shared` | | Writes the template into the working directory and exits. The server does not start. |
| `--local-configuration` | `--local-config`, `-lc` | path | `configuration.cdn` | Location of the local configuration file. An empty value selects `configuration.cdn`. |
| `--local-configuration-mode` | `--local-config-mode`, `-lcm` | `auto` or `none` | `auto` | Whether the local configuration file is rewritten at start. The value is not case sensitive. |
| `--shared-configuration` | `--shared-config`, `-sc` | path | not set | Reads the shared settings from a file. An empty value selects `configuration.shared.json`. |
| `--ignore-shared-configuration-errors` | | none | off | Starts with default shared settings when the shared settings cannot be loaded. |
| `--hostname` | `-h` | text | value of `hostname` | Overrides `hostname`. |
| `--port` | `-p` | number | value of `port` | Overrides `port`. |
| `--database` | | text | value of `database` | Overrides `database`. |
| `--target` | | `all` or `server` | `all` | Deploy target of the instance. Takes precedence over `ingot.target`, `INGOT_TARGET` and the legacy `defaultFrontend` switch. Any other value stops the start. See [Deploy targets](deploy-targets.md). |
| `--token` | `-t` | `name:secret` | none | Creates a temporary token with manager permission. The token is created again at every start and the creation is not audited. Repeat the parameter for more tokens. |
| `--channel` | `--level` | text | `INFO` | Default logging channel. An unknown name falls back to `INFO`. |
| `--run-migrations` | | `001`, `002`, `003`, `004` | none | Runs the named data migrations. Repeat the parameter for more than one. See below. |
| `--test-env` | `--debug`, `-d` | none | off | Test mode. The interactive console and the status output at start are skipped, and log output goes to a temporary file. |
| `--disable-interactive-console` | `--disable-it` | none | off | Does not read commands from standard input. |
| `--no-color` | | none | off | Disables ANSI colors in the console output. |
| `--snapshot-database` | | none | off | Writes one online snapshot of the SQLite database into `backups/` in the working directory, then exits without starting the server. Exits with status `0` on success. See [Back up and restore](../how-to/back-up-and-restore.md). |

### Data migrations

The identity migrations run automatically at start. The migrations of `--run-migrations` are opt in.

| Id | Effect |
| --- | --- |
| `001` | Changes the size of the `repository` identifier column from 32 to 64. |
| `002` | Removes `.module` entries from the recorded requests. |
| `003` | Converts timestamp dates in SQLite to the ISO format. Other databases are skipped. |
| `004` | Removes orphaned access token permissions and routes. |

## Files that Ingot writes at start

| Path | Content |
| --- | --- |
| `<working directory>/.local/reposilite.address` | `http://<hostname>:<port>`. The container health check reads it. |
| `<working directory>/configuration.cdn` | The rendered local configuration, in mode `auto`. |

## Related pages

* [Environment variables](environment-variables.md)
* [Settings domains](settings-domains.md)
* [Storage providers](storage-providers.md)
* [Upgrade changes and supported paths](upgrade-changes.md)

---
description: Reference for the environment variables and JVM system properties that Ingot and its container images read, with the mapping rules of the local configuration and the names that do not follow them.
---

# Environment variables

Ingot reads environment variables in four places: the local configuration, the entrypoint of the server image, the dashboard image and a few individual features. Only the first follows a general rule. This page lists all of them.

## Local configuration variables

Every key of the local configuration can be set from the environment. The keys and their defaults are in [Configuration file and startup parameters](configuration-and-startup.md#local-configuration-keys).

### Mapping rules

| Rule | Behavior |
| --- | --- |
| Name | `INGOT_LOCAL_<KEY>`, where `<KEY>` is the key name, for example `INGOT_LOCAL_SSLENABLED`. |
| Case | The whole variable name is matched without regard to case. Ingot upper-cases the names of all environment variables, then removes the prefix, then compares the rest with the key names without regard to case. `INGOT_LOCAL_SSLENABLED` and `ingot_local_sslEnabled` are the same variable. |
| No separators | The key name is written as it is, without extra underscores. `INGOT_LOCAL_SSL_ENABLED` matches no key. |
| Fallback prefix | `REPOSILITE_LOCAL_<KEY>` is read as well. When both prefixes set the same key, `INGOT_LOCAL_` wins. |
| Values | `Boolean` keys are `true` when the value is `true` in any case and `false` for every other value. `Int` and `Long` keys must be numbers, otherwise the start fails. `String` keys take the value as it is. |
| Unknown names | A variable with the prefix and no matching key is logged as a warning and ignored. |
| Persistence | The override is applied in memory. It is not written to `configuration.cdn`. |
| Logging | The log shows the key, the first character and the last character of the value. |

System properties follow the same rules with the lower-case prefixes `ingot.local.` and `reposilite.local.`, for example `-Dingot.local.sslEnabled=true`. A system property wins over an environment variable. See [Order of precedence](configuration-and-startup.md#order-of-precedence).

### Variable names

| Key | `INGOT_` variable | `REPOSILITE_` fallback |
| --- | --- | --- |
| `hostname` | `INGOT_LOCAL_HOSTNAME` | `REPOSILITE_LOCAL_HOSTNAME` |
| `port` | `INGOT_LOCAL_PORT` | `REPOSILITE_LOCAL_PORT` |
| `database` | `INGOT_LOCAL_DATABASE` | `REPOSILITE_LOCAL_DATABASE` |
| `sslEnabled` | `INGOT_LOCAL_SSLENABLED` | `REPOSILITE_LOCAL_SSLENABLED` |
| `sslPort` | `INGOT_LOCAL_SSLPORT` | `REPOSILITE_LOCAL_SSLPORT` |
| `keyPath` | `INGOT_LOCAL_KEYPATH` | `REPOSILITE_LOCAL_KEYPATH` |
| `keyPassword` | `INGOT_LOCAL_KEYPASSWORD` | `REPOSILITE_LOCAL_KEYPASSWORD` |
| `enforceSsl` | `INGOT_LOCAL_ENFORCESSL` | `REPOSILITE_LOCAL_ENFORCESSL` |
| `webThreadPool` | `INGOT_LOCAL_WEBTHREADPOOL` | `REPOSILITE_LOCAL_WEBTHREADPOOL` |
| `ioThreadPool` | `INGOT_LOCAL_IOTHREADPOOL` | `REPOSILITE_LOCAL_IOTHREADPOOL` |
| `databaseThreadPool` | `INGOT_LOCAL_DATABASETHREADPOOL` | `REPOSILITE_LOCAL_DATABASETHREADPOOL` |
| `compressionStrategy` | `INGOT_LOCAL_COMPRESSIONSTRATEGY` | `REPOSILITE_LOCAL_COMPRESSIONSTRATEGY` |
| `idleTimeout` | `INGOT_LOCAL_IDLETIMEOUT` | `REPOSILITE_LOCAL_IDLETIMEOUT` |
| `bypassExternalCache` | `INGOT_LOCAL_BYPASSEXTERNALCACHE` | `REPOSILITE_LOCAL_BYPASSEXTERNALCACHE` |
| `cachedLogSize` | `INGOT_LOCAL_CACHEDLOGSIZE` | `REPOSILITE_LOCAL_CACHEDLOGSIZE` |
| `defaultFrontend` | `INGOT_LOCAL_DEFAULTFRONTEND` | `REPOSILITE_LOCAL_DEFAULTFRONTEND` |
| `basePath` | `INGOT_LOCAL_BASEPATH` | `REPOSILITE_LOCAL_BASEPATH` |
| `hideSettingsSecrets` | `INGOT_LOCAL_HIDESETTINGSSECRETS` | `REPOSILITE_LOCAL_HIDESETTINGSSECRETS` |
| `searchEnabled` | `INGOT_LOCAL_SEARCHENABLED` | `REPOSILITE_LOCAL_SEARCHENABLED` |
| `searchEngine` | `INGOT_LOCAL_SEARCHENGINE` | `REPOSILITE_LOCAL_SEARCHENGINE` |
| `debugEnabled` | `INGOT_LOCAL_DEBUGENABLED` | `REPOSILITE_LOCAL_DEBUGENABLED` |

## Container variables

The entrypoint of the server image (`/app/entrypoint.sh`) reads these variables.

| Variable | Fallback | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_OPTS` | `REPOSILITE_OPTS` | empty | Command line parameters for the server, see [Command line parameters](configuration-and-startup.md#command-line-parameters). The value is split at white space. When `INGOT_OPTS` is empty or unset, `REPOSILITE_OPTS` is used. The two are not combined. |
| `JAVA_OPTS` | none | empty | Options for the JVM, for example `-Xmx1g` or `-Dingot.local.port=9090`. There is no `INGOT_` spelling. |
| `PUID` | none | `977` | User id of the `reposilite` service account. Read only when the container starts as root. |
| `PGID` | none | `977` | Group id of the service account. Read only when the container starts as root. |
| `INGOT_FORCE_CHOWN` | `REPOSILITE_FORCE_CHOWN` | unset | `true` changes the owner of `/app` and `/var/log/reposilite` at every start. Any other value does nothing. Read only when the container starts as root. |

Behavior of the entrypoint:

| Case | Behavior |
| --- | --- |
| No `--working-directory` or `-wd` in the options | `--working-directory=/app/data` is added in front of the options. |
| Container starts as a user other than root | `PUID`, `PGID` and `INGOT_FORCE_CHOWN` are not read. The server starts as that user. |
| Container starts as root | The `reposilite` account is set to `PUID` and `PGID`. Then the data directory owner is compared with these ids. When they differ, or when `INGOT_FORCE_CHOWN` is `true`, `/app` and `/var/log/reposilite` are changed to the service account. A warning about the changed owner is printed when the data directory owner differs. The server then runs as `reposilite`. |
| Log files | The entrypoint sets `-Dtinylog.writerFile.file=/var/log/reposilite/log_{date}.txt` and `-Dtinylog.writerFile.latest=/var/log/reposilite/latest.log`. |

The Compose files in the repository read `PORT`, `MEMORY`, `JAVA_COMPOSE_OPTS` and `INGOT_COMPOSE_OPTS` from a `.env` file and build `JAVA_OPTS` and `INGOT_OPTS` from them. These four names are Compose variables. The server does not read them.

## Dashboard image variables

The separate dashboard image (`ghcr.io/onelitefeathernet/ingot-dashboard`) renders its web server configuration from these variables when it starts.

| Variable | Default | Meaning |
| --- | --- | --- |
| `INGOT_BACKEND` | `http://ingot-backend:8080` | Address of the Ingot server. The dashboard container proxies everything that is not its own content to it. |
| `INGOT_RESOLVER` | `127.0.0.11` | DNS resolver used to look up the server address for each request. |
| `INGOT_MAX_UPLOAD_SIZE` | `1024m` | Largest request body that the dashboard container accepts. |

The image has a build argument `INGOT_BASE_PATH` (default `/`) that sets the base path of the bundled dashboard when the image is built. It is not read at run time.

## Names that do not follow the rule

These settings are read directly. They do not accept the `INGOT_LOCAL_` prefix, and most of them do not accept an `INGOT_` spelling at all.

### Prometheus plugin

The `prometheus` plugin (module `reposilite-plugins/prometheus-plugin`) reads a system property first and the environment second.

| System property | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `reposilite.prometheus.path` | `REPOSILITE_PROMETHEUS_PATH` | `/metrics` | Path of the metrics endpoint. |
| `reposilite.prometheus.user` | `REPOSILITE_PROMETHEUS_USER` | none | User name for the metrics endpoint. Required: without it the plugin throws `Prometheus user is not defined`. |
| `reposilite.prometheus.password` | `REPOSILITE_PROMETHEUS_PASSWORD` | none | Password for the metrics endpoint. Required: without it the plugin throws `Prometheus password is not defined`. |

### S3 storage system properties

These are JVM system properties. There is no environment variable form. Pass them in `JAVA_OPTS`, for example `JAVA_OPTS=-Dreposilite.s3.pathStyleAccessEnabled=true`. Set them before the server starts. Only the exact value `true` turns a property on.

| System property | Default | Meaning |
| --- | --- | --- |
| `reposilite.s3.pathStyleAccessEnabled` | `false` | Uses path-style S3 addresses (`https://host/bucket/key`). Needed by many S3-compatible servers. |
| `reposilite.s3.use-s3-v4-signer` | `false` | Signs requests with the AWS S3 V4 signer. |
| `reposilite.s3.skip-bucket-creation` | `false` | Does not create the bucket when it does not exist. |

The S3 client also reads the standard `AWS_REGION` environment variable when a repository sets no `region`. See [Storage providers](storage-providers.md).

### Other system properties

| System property | Default | Meaning |
| --- | --- | --- |
| `ingot.status.remote-version-check` | `true` | `false` turns off the hourly check for a newer Ingot version. The older name `reposilite.status.remote-version-check` is read when the `ingot.` property is not set. |
| `ingot.status.remote-version-url` | `https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/latest` | Address that the version check asks for the latest release. There is no `reposilite.` fallback. |
| `reposilite.maven.maxAge` | `3600` | Seconds used in `Cache-Control: public, max-age=<value>` of file responses that are cacheable. |
| `reposilite.redirect.default-repository` | empty | Name of a repository. When set, the `redirect` plugin serves the top-level directories of that repository from the root path too. Empty turns the plugin off. |
| `reposilite.ldap.disable-user-password-authentication` | `false` | `true` makes the LDAP Basic authenticator skip the check of the user password against the directory. See the warning below. |
| `javalin.maxRequestSize` | Javalin default | Largest HTTP request body in bytes. |

{% hint style="warning" %}
`reposilite.ldap.disable-user-password-authentication=true` accepts an LDAP user without verifying the password that the client sent. Use it only when something else in front of Ingot already verified the user.
{% endhint %}

## Related pages

* [Configuration file and startup parameters](configuration-and-startup.md)
* [Storage providers](storage-providers.md)
* [Upgrade changes and supported paths](upgrade-changes.md)

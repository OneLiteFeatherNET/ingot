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
| File spelling | `INGOT_LOCAL_<KEY>_FILE` names a file whose content is the value. `REPOSILITE_LOCAL_<KEY>_FILE` is read as well, and `INGOT_` wins when both are set. See [File values](#file-values). |

System properties follow the same rules with the lower-case prefixes `ingot.local.` and `reposilite.local.`, for example `-Dingot.local.sslEnabled=true`. A system property wins over an environment variable. See [Order of precedence](configuration-and-startup.md#order-of-precedence).

### File values

A key can take its value from a file instead of a variable. A container platform mounts a secret as a file, and the variable names its path, so the secret does not have to be written into the manifest.

| Rule | Behavior |
| --- | --- |
| Name | `INGOT_LOCAL_<KEY>_FILE`, with `REPOSILITE_LOCAL_<KEY>_FILE` as the fallback. The system property form is `ingot.local.<key>.file`, and `reposilite.local.<key>.file` is its fallback. |
| Content | The content of the file, read as UTF-8, is the value. The line breaks at its end are removed. Nothing else is changed, so spaces inside the value and at its start are kept. An empty file gives the empty string. |
| Relative paths | A relative path is resolved against the working directory, as the path of `configuration.cdn` is. An absolute path is used as it is. |
| Plain variable wins | When one source sets both `INGOT_LOCAL_<KEY>` and `INGOT_LOCAL_<KEY>_FILE`, the plain variable is used, the file is not read, and the log has one warning that names both. The environment and the system properties are two sources, so a system property still wins over a file variable of the environment. |
| Unknown keys | A `_FILE` variable of a key that is not a local setting is logged as an unknown setting, and its file is not read. |
| Missing or unreadable file | The start stops before any port is bound. The process prints one line that names the variable and the path, and exits with status 1. The line never contains the content of the file. A file is refused when it does not exist, is a directory, cannot be read, or is not valid UTF-8. |
| Timing | The file is read once, at start. A change to the file takes effect at the next start, as a changed variable does. |

For example, `INGOT_LOCAL_KEYPASSWORD_FILE=/run/secrets/key-password` takes the key password from the file at that path.

### Variable names

| Key | `INGOT_` variable | `REPOSILITE_` fallback |
| --- | --- | --- |
| `hostname` | `INGOT_LOCAL_HOSTNAME` | `REPOSILITE_LOCAL_HOSTNAME` |
| `port` | `INGOT_LOCAL_PORT` | `REPOSILITE_LOCAL_PORT` |
| `database` | `INGOT_LOCAL_DATABASE` | `REPOSILITE_LOCAL_DATABASE` |
| `schemaLockTimeout` | `INGOT_LOCAL_SCHEMALOCKTIMEOUT` | `REPOSILITE_LOCAL_SCHEMALOCKTIMEOUT` |
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

`schemaLockTimeout` is the number of seconds a replica waits for the schema lock before its start fails. It defaults to `300`. The `REPOSILITE_LOCAL_` name is read only when the `INGOT_LOCAL_` name is not set. See [Multi-replica operation](../explanation/multi-replica-operation.md#schema-initialisation).

## Deploy target

The deploy target is read once at start. See [Deploy targets](deploy-targets.md) for the values, the precedence and the legacy switch.

| Variable | Default | Meaning |
| --- | --- | --- |
| `INGOT_TARGET` | unset, which means `all` | Deploy target of the instance. `all` serves the dashboard, the API and the repositories. `server` serves the API and the repositories only. An empty value counts as unset. Any other value stops the start. The command line option `--target` and the system property `ingot.target` take precedence over it. |

## Server variables

The server process reads these variables itself, not the entrypoint.

| Variable | System property | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_TRUSTED_PROXIES` | `-Dingot.trustedProxies` | unset | Comma-separated IPv4 and IPv6 addresses and CIDR ranges of your reverse proxies, for example `10.0.0.0/8,fd00::/8,192.168.1.5`. When set, the client address header is only read from a connection that comes from one of them, and the client is the rightmost address of that header which is not a trusted proxy. When unset, the header is used exactly as it arrives and the server logs a warning at startup. A malformed entry stops the start with a message that names it. The property wins over the variable. There is no `REPOSILITE_` spelling because Reposilite has no such setting. See [Run behind a reverse proxy](../how-to/run-behind-reverse-proxy.md#trust-your-proxies). |
| `INGOT_FETCH_METADATA` | `-Dingot.fetchMetadata` | `on` | `off` lets a request to the API that comes from another site or a sibling subdomain through when it carries the session or refresh cookie. By default such a request is refused with `403`, unless it is a top-level navigation. The value is matched without regard to case, and any other value keeps the refusal on. The variable wins over the property. There is no `REPOSILITE_` spelling because the cookie sessions are Ingot's own. See [Cross-site requests](../explanation/sessions-and-login.md#cross-site-requests). |

## Shutdown variables

The server reads these variables once at start. Both are optional, and with their default the stop sequence is the same as in earlier versions. Each has a `REPOSILITE_` fallback that is read when the `INGOT_` name is unset or empty. A value that is not a whole number of seconds from `0` upwards stops the start, and the message names the variable.

| Variable | Fallback | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_SHUTDOWN_TIMEOUT` | `REPOSILITE_SHUTDOWN_TIMEOUT` | `0` | Seconds the server waits for requests in flight after it has begun to drain. While it waits, new requests get `503`, except the health and readiness answers under `/api/status/`. `0` keeps the strict stop: the server stops at once, as before. Above `0`, the stop waits up to this many seconds and then stops whatever is still running. |
| `INGOT_SHUTDOWN_DELAY` | `REPOSILITE_SHUTDOWN_DELAY` | `0` | Seconds the instance keeps serving after it has begun to drain and before the drain wait starts. During this time `/api/status/ready` answers `503` with `draining`, so a load balancer can take the instance out of the rotation first. `0` starts the drain wait at once. |

## Container variables

The entrypoint of the server image (`/app/entrypoint.sh`) reads these variables.

| Variable | Fallback | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_OPTS` | `REPOSILITE_OPTS` | empty | Command line parameters for the server, see [Command line parameters](configuration-and-startup.md#command-line-parameters). The value is split at white space. When `INGOT_OPTS` is empty or unset, `REPOSILITE_OPTS` is used. The two are not combined. |
| `JAVA_OPTS` | none | empty | Options for the JVM, for example `-Xmx1g` or `-Dingot.local.port=9090`. There is no `INGOT_` spelling. |
| `JDK_JAVA_OPTIONS` | none | empty | Options that the JVM reads before `JAVA_OPTS`. The Helm chart writes one entry of it when its `jvm` or `opentelemetry` values are set, and keeps the text that `env` sets. See [Run on Kubernetes](../how-to/run-on-kubernetes.md#size-the-heap-from-the-memory-limit). |
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

## Security header variables

The server adds security headers to its responses. Two of them can be changed. The variables are read once when the server starts. Each has a system property as an alternative, used when the variable is unset.

| Variable | System property | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_REPOSITORY_SANDBOX` | `ingot.repository.sandbox` | `true` | Repository files and the generated directory index are served with `Content-Security-Policy: sandbox`, so a browser runs an uploaded page without the origin of the dashboard. `false` leaves them without this header. The value is matched without regard to case, any other value keeps the sandbox. |
| `INGOT_HSTS_MAX_AGE` | `ingot.hsts.maxAge` | unset | A positive number of seconds turns on `Strict-Transport-Security: max-age=<seconds>`. The header is sent only on HTTPS requests, that is a request over TLS served by Ingot or a request with `X-Forwarded-Proto: https`. Unset, zero or a value that is not a number sends no header. |

The complete list of headers is in [Security headers](../explanation/security-headers.md).

## Dashboard image variables

The separate dashboard image (`ghcr.io/onelitefeathernet/ingot-dashboard`) reads these variables when its container starts.

| Variable | Default | Meaning |
| --- | --- | --- |
| `INGOT_BACKEND` | `http://ingot-backend:8080` | Address of the Ingot server, an absolute `http` or `https` URL. The dashboard container proxies everything that is not its own content to it. |
| `INGOT_BASE_PATH` | `/` | Path the dashboard is served under, such as `/ingot/`. Read at run time. |
| `INGOT_MAX_UPLOAD_SIZE` | `1024m` | Largest request body that the dashboard container accepts. |
| `INGOT_RESOLVER` | not set | Not used any more. A value that is set is accepted and ignored. |

The image has no build argument for the base path, so one image serves any base path.

## Cluster bus variables

The cluster plugin reads these settings at start. Without `INGOT_CLUSTER_URL` or its fallback, none of them has an effect. See [Configure the cluster bus](../how-to/configure-cluster-bus.md).

| Variable | Fallback | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_CLUSTER_URL` | `REPOSILITE_CLUSTER_URL`, and the system property `ingot.cluster.url` wins over both | unset, bus off | Bus URL with one of the schemes `nats`, `tls`, `redis`, `rediss`, `valkey`, `valkeys`, `database`. Any other scheme stops the start. `database:` uses the instance database and is refused on SQLite and H2, which keeps the instance running without the bus. |
| `INGOT_CLUSTER_PASSWORD` | `REPOSILITE_CLUSTER_PASSWORD` | unset | Password for the bus when the URL carries none. A password in the URL wins. |
| `INGOT_CLUSTER_CREDENTIALS` | system property `ingot.cluster.credentials`, which wins | unset | Path to a NATS `.creds` file for brokers in operator mode. Used with `nats` and `tls` only. |
| `INGOT_CLUSTER_INSTANCE_ID` | system property `ingot.cluster.instance`, which wins | host name | Identifier of this replica in the bus and in the status output. |
| `INGOT_CLUSTER_CONSOLE` | none | `true` when a bus is configured, `false` on the `database` transport | `false` turns off the forwarding of live console log lines between replicas. `true` turns it on, which the `database` transport needs explicitly. |
| `INGOT_CLUSTER_POLL_INTERVAL_MS` | none | `1000` | Poll interval of the `database` transport in milliseconds. Values below 200 are raised to 200 with one warning. Other transports ignore it. |
| `INGOT_CLUSTER_FAILURES` | none | `false` | `true` aggregates recorded failures across replicas. |

Boolean variables accept `true`, `false`, `1`, `0`, `on`, `off`, `yes` and `no`, in any case. Credentials are never printed. The log shows the URL with the credentials replaced by `REDACTED`.

## Names that do not follow the rule

These settings are read directly. They do not accept the `INGOT_LOCAL_` prefix, and most of them do not accept an `INGOT_` spelling at all.

### Prometheus plugin

The `prometheus` plugin (module `reposilite-plugins/prometheus-plugin`) reads the `INGOT_PROMETHEUS_` environment variable first, then a system property, then the `REPOSILITE_PROMETHEUS_` environment variable. The first value that is set wins. Existing deployments that set only the `REPOSILITE_` names keep working.

| Environment variable | Legacy environment variable | System property | Default | Meaning |
| --- | --- | --- | --- | --- |
| `INGOT_PROMETHEUS_PATH` | `REPOSILITE_PROMETHEUS_PATH` | `reposilite.prometheus.path` | `/metrics` | Path of the metrics endpoint. |
| `INGOT_PROMETHEUS_USER` | `REPOSILITE_PROMETHEUS_USER` | `reposilite.prometheus.user` | none | User name for the metrics endpoint. Required: without it the plugin throws `Prometheus user is not defined`. |
| `INGOT_PROMETHEUS_PASSWORD` | `REPOSILITE_PROMETHEUS_PASSWORD` | `reposilite.prometheus.password` | none | Password for the metrics endpoint. Required: without it the plugin throws `Prometheus password is not defined`. |

#### Metrics switch

The image ships the plugin as `prometheus-plugin.jar` in `/app/bundled-plugins/`, outside the data volume. It is loaded only when the switch is on.

| Environment variable | Legacy environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_METRICS_ENABLED` | `REPOSILITE_METRICS_ENABLED` | unset (off) | `true` loads the bundled plugin. When `INGOT_METRICS_ENABLED` is set, it decides on its own and the legacy name is ignored. |

With the switch on and no user or password, start-up fails with the message above, as it does for an installed plugin without credentials. A `prometheus-plugin.jar` in the plugins directory with the same plugin id takes precedence: the bundled copy is then not loaded. The directory can be changed with the JVM property `ingot.bundledPlugins`, which is meant for tests and unusual images.

### S3 upload variable

| Variable | Fallback | Default | Meaning |
| --- | --- | --- | --- |
| `INGOT_S3_BUFFER_TO_DISK` | `REPOSILITE_S3_BUFFER_TO_DISK`, read when the `INGOT_` variable is not set | `false` | `true` writes every S3 upload to a temporary file before it is sent, as before large uploads were streamed. `false` streams uploads as multipart uploads above 8 MiB. Read once at start, for every S3 repository of the instance. Any value other than `true` or `false`, in any case, stops the start with a message that names the variable. See [Large uploads](../how-to/use-s3-storage.md#large-uploads). |

### S3 storage system properties

These are JVM system properties. There is no environment variable form. Pass them in `JAVA_OPTS`, for example `JAVA_OPTS=-Dreposilite.s3.pathStyleAccessEnabled=true`. Set them before the server starts. Only the exact value `true` turns a property on.

| System property | Default | Meaning |
| --- | --- | --- |
| `reposilite.s3.pathStyleAccessEnabled` | `false` | Uses path-style S3 addresses (`https://host/bucket/key`). Needed by many S3-compatible servers. |
| `reposilite.s3.use-s3-v4-signer` | `false` | Signs requests with the AWS S3 V4 signer. |
| `reposilite.s3.skip-bucket-creation` | `false` | Does not create the bucket when it does not exist. |

The S3 client also reads the standard `AWS_REGION` environment variable when a repository sets no `region`. See [Storage providers](storage-providers.md).

### Proxy upstream status

Read once at startup, in this order: the system property `ingot.proxy.upstreamStatus`, the environment variable `INGOT_PROXY_UPSTREAM_STATUS`, the system property `reposilite.proxy.upstreamStatus`, the environment variable `REPOSILITE_PROXY_UPSTREAM_STATUS`. The first one that is set wins. It is not a local configuration key, so it has no `INGOT_LOCAL_` form.

| Value | Meaning |
| --- | --- |
| `legacy` (default) | A proxy repository answers upstream failures with the statuses Reposilite always used. Existing Maven and Gradle clients keep working unchanged. |
| `strict` | A proxy answers `504` when the upstream times out, `502` when it is unreachable, answers an error status other than `404` or `410`, or answers an HTML page, and `404` when the upstream answers `404` or `410`. |

The value is compared without regard to case. Any other value stops the startup with a message that names `INGOT_PROXY_UPSTREAM_STATUS`. See [Create proxy repositories](../how-to/create-proxy-repositories.md#choose-the-status-for-upstream-failures).

### Maven cache control

Read once at startup from `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`, or from the legacy `REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC` when the `INGOT_` name is not set. It is not a local configuration key, so it has no `INGOT_LOCAL_` form.

| Value | Meaning |
| --- | --- |
| `false` (default) | A file of a `HIDDEN` or `PRIVATE` repository, or of a group that reaches one, is answered with `Cache-Control: private`. A shared cache does not store it. |
| `true` | Those answers are answered with `Cache-Control: public` again, as before. Use it only when the cache in front of Ingot may hold access-controlled files for other clients. |

The value is compared without regard to case, and an empty value counts as not set. Any other value stops the startup with a message that names `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`. See [Visibility](repository-settings.md#visibility).

### Shared configuration reload

Read once at startup from `INGOT_SHARED_CONFIGURATION_RELOAD`, or from the legacy `REPOSILITE_SHARED_CONFIGURATION_RELOAD` when the `INGOT_` name is not set or empty. It applies only with `--shared-configuration`. It is not a local configuration key, so it has no `INGOT_LOCAL_` form.

| Value | Meaning |
| --- | --- |
| `false` (default) | The file is read at start only. A change needs a restart, and a save on the dashboard or through the API writes the file back. |
| `true` | The file is checked every 10 seconds and a changed document is applied, including a rewrite that keeps the modification time and a swap of a Kubernetes `..data` link. The API refuses writes to the domains the file holds with `409`, names the file in the message and writes nothing. A document that does not parse keeps the settings in force and logs one error. |

The value is compared without regard to case, and an empty value counts as not set. Any other value stops the startup with a message that names `INGOT_SHARED_CONFIGURATION_RELOAD`. With the database as the shared source the variable has no effect, and the instance logs one warning at startup. See [Shared settings source](configuration-and-startup.md#shared-settings-source).

### Other system properties

| System property | Default | Meaning |
| --- | --- | --- |
| `ingot.status.remote-version-check` | `true` | `false` turns off the hourly check for a newer Ingot version. The older name `reposilite.status.remote-version-check` is read when the `ingot.` property is not set. |
| `ingot.status.remote-version-url` | `https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/latest` | Address that the version check asks for the latest release. There is no `reposilite.` fallback. |
| `ingot.javadocs.max-entry-bytes` | `10485760` (10 MiB) | Largest size in bytes of one file inside a javadoc jar that Ingot unpacks for the javadoc viewer. Files above it are rejected. The default was 2 MiB before. The older name `reposilite.javadocs.max-entry-bytes` is read when the `ingot.` property is not set. |
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

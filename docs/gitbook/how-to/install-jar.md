---
description: Run the Ingot server from its JAR file with Java 17 or newer, choose the working directory and data layout, and use the startup flags.
---

# Run the JAR

This page runs Ingot directly on a JVM, without a container. You need Java 17 or newer. The build compiles Ingot for Java 17, the continuous integration builds it on Java 17 and 21, and the container images run Java 21.

## Before you begin

* Java 17 or newer, on the path as `java`. Check with `java -version`.
* The server JAR of the release, which the build names `ingot-<version>.jar`.
* A directory for the data, owned by the user that runs Ingot.

## Steps

1. Create the data directory and put the JAR in it:

   ```bash
   sudo mkdir -p /opt/ingot
   sudo cp ingot-<version>.jar /opt/ingot/
   cd /opt/ingot
   ```

2. Start Ingot from that directory:

   ```bash
   java -jar ingot-<version>.jar
   ```

   Ingot uses the current directory as its working directory unless you pass `--working-directory` or `-wd`. Every file of the instance is created below it.

3. Read the first lines of the log. Ingot writes the configuration file, the database and the repositories on the first start, and it prints the address it listens on.

4. Check that the server answers. The health endpoint needs no login:

   ```bash
   curl -f http://localhost:8080/api/status/health
   ```

   The answer is `200 OK`.

## Data layout

Below the working directory Ingot creates:

| Path | Content |
|------|---------|
| `configuration.cdn` | The local configuration. Ingot writes it on every start, so edit it between runs. |
| `reposilite.db` | The embedded SQLite database, with tokens, identity data and statistics. The name is the default of the `database` setting. |
| `repositories/` | The artifacts of each repository, one directory per repository. |
| `plugins/` | Plugin JARs that Ingot loads at start. |
| `.local/reposilite.address` | The address of the running server. The container health check reads it. |
| `logs/` | Rolling log files, `log_<date>.txt`, with a `latest.log` link. |

Logs go to `./logs` relative to the directory that the JVM starts in. Passing `--working-directory` does not move them. The log directory and the rolling policy are set in `tinylog.properties`, which is part of the JAR.

{% hint style="info" %}
The data directory of an existing Reposilite instance can be used by Ingot. For the steps and checks, follow [Upgrade from Reposilite 3.x](upgrade-from-reposilite.md) and [Back up before upgrading](back-up-before-upgrading.md) first.
{% endhint %}

## Startup flags

| Flag | Short | What it does |
|------|-------|--------------|
| `--working-directory <path>` | `-wd` | Directory for all instance files. Default: the current directory. |
| `--local-configuration <file>` | `-lc` | Path of the local configuration file, relative to the working directory unless absolute. Default: `configuration.cdn`. |
| `--local-configuration-mode <auto\|none>` | `-lcm` | `auto` writes the file on every start. `none` leaves the file alone. |
| `--hostname <address>` | `-h` | Address to bind, overriding `hostname`. |
| `--port <port>` | `-p` | Port to bind, overriding `port`. |
| `--database <value>` | | Database, overriding `database`, for example `sqlite reposilite.db` or `mariadb localhost:3306 db user password`. |
| `--disable-interactive-console` | `--disable-it` | Turns off the console on standard input. Use it when no terminal is attached. |
| `--no-color` | | Disables ANSI colours in console output. |
| `--run-migrations <id>` | | Runs the named opt-in data migrations. Repeat the parameter for more than one, for example `--run-migrations 001 --run-migrations 004`. Used only for the upgrade paths. |

Pass JVM options before `-jar`, for example the heap size:

```bash
java -Xmx512m -jar ingot-<version>.jar --working-directory=/opt/ingot --disable-interactive-console
```

Every local setting can also come from an environment variable named `INGOT_LOCAL_` followed by the setting name in capitals, for example `INGOT_LOCAL_HOSTNAME`. The variable overrides the file and is not written back to it. The precedence is: defaults, then the file, then environment variables, then `-D` system properties, then the flags above.

## Result

* The server listens on the address set by `hostname` and `port`, by default `0.0.0.0:8080`.
* The working directory contains `configuration.cdn`, `reposilite.db`, `repositories/` and `logs/`.
* `curl -f http://localhost:8080/api/status/health` returns `200 OK`.

To stop Ingot in the foreground, type `stop` in the console, or send the process a termination signal.

## Related

* [Run as a service](run-as-service.md), to start Ingot at boot.
* [Enable TLS](enable-tls.md), to serve HTTPS from Ingot itself.
* [Run behind a reverse proxy](run-behind-reverse-proxy.md), to serve Ingot under a host name.

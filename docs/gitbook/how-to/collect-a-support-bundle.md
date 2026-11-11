# Collect a support bundle

A support bundle is a zip file that describes one Ingot instance: what it runs on, how it is configured, which cluster members and plugins it knows, and its most recent log lines. Attach it to a ticket instead of pasting settings into it. This page says what the bundle contains, what it leaves out, and what you still check yourself.

## Who can download it

Only principals with the console area (`ingot.admin.console`) can open the System information page and the Support bundle page, or download the bundle. Anonymous callers get `401`, and principals without the area get `403`. Downloads are recorded in the audit log as `SUPPORT_BUNDLE_DOWNLOADED`, with the name of the person who downloaded it.

Open the pages under **Administration**, in the group **Support**, or call `GET /api/admin/support/bundle` directly with the token of an administrator.

## What the bundle contains

| File | Content |
| --- | --- |
| `manifest.json` | Every other file with its size, the version of the redaction rules, the instance name, the time of the bundle and the number of values that were withheld, masked or scrubbed. |
| `system-info.json` | The version and build of Ingot (`unknown` when the jar carries no build time or commit), the JVM vendor, version, uptime, heap and input arguments, the operating system, the database kind and endpoint, the storage kind and region of each repository, the cluster section and the environment and system properties that the rules allow. |
| `configuration-local.json` | The local configuration (the file the instance reads at start), one value per key. |
| `configuration-shared.json` | Every shared settings domain, with its secrets masked. |
| `cluster.json` | The cluster section: the bus state and transport, the redacted bus URL and the members. `{"enabled": false}` on an instance without a bus. |
| `plugins.json` | The plugins with their version and dependencies. |
| `logs/<instance>.log` | The cached log lines of the instance that answered, scrubbed as described below. |

The bundle describes one instance, the one that answers the request. The cluster members appear in it, but the logs of the other members do not. The log holds the most recent lines kept in memory, 64 by default, set by `cachedLogSize` in the local configuration. Raise that value when a ticket needs more history.

## What is removed

The bundle is built from an allowlist, not from a list of secret names. A value is written only when a rule says it is safe.

- **Local configuration.** Only the keys on the allowlist keep their values, such as the port, the thread pools and the cache size. Every other key appears by name with the value `[withheld]`, so a new key is never written by accident. The TLS key password and the key path are always withheld.
- **Database.** The setting is reduced to its kind and, for MariaDB, MySQL and PostgreSQL, its host and port. The user, the password, the file path of an embedded database and every query parameter of a JDBC URL are dropped. A setting that cannot be read is written as `unparsed`.
- **Environment and system properties.** Only the variables and properties on the allowlist keep their values, such as `PUID`, `PGID` and the Ingot switches. A property whose name looks like a secret is withheld even under the `ingot.` prefix. Every other variable is written as `[withheld]`.
- **JVM arguments.** Memory and option flags keep their values. A `-D` property keeps its value only under the `ingot.` or `reposilite.` prefix and only when its name does not look like a secret. Other arguments, such as the class path or an agent, show their name and `[withheld]`.
- **Shared settings.** Every secret of every settings domain is masked with `********`, whether or not **Hide settings secrets** is on. A secret reference such as `${env:NAME}` is masked too. A domain of unknown type is withheld entirely.
- **Cluster bus.** The URL is written without its user and password.
- **Log lines.** Every known secret of four or more characters is replaced by `[redacted]`. Authorization headers, bearer and basic tokens, credentials in URLs and assignments such as `password=`, `secret:` or `token=` are replaced too, in plain text and in JSON lines.
- **Never read.** Access tokens, sessions, refresh material, password hashes, the contents of any table, artifact files, request bodies and the audit log are not read by the bundle code at all.

Before a bundle is served, every file is searched once more for each known secret. If one is still present, the download is refused with `500`, nothing is served, and the refusal is written to the audit log without the value. A bundle is served whole or not at all. A bundle larger than 8 MiB is refused the same way.

## What you still check

The rules remove known secrets and recognisable patterns. They cannot recognise everything that a free-text log line may hold, for example a customer name in a file path or a message that quotes a request body. The bundle is therefore a draft for you to read.

Before you attach it to a public issue:

1. Unzip it and read `logs/` line by line.
2. Look at `configuration-shared.json` and `system-info.json` for anything you would not post in public.
3. Remove or replace whatever does not belong in the ticket, then attach the rest.

If you do not want the bundle to exist on an instance at all, turn it off with `INGOT_SUPPORT_BUNDLE_ENABLED=false`. Both data endpoints then answer `404` and the pages disappear. See [Environment variables](../reference/environment-variables.md#support-bundle-switch).

## Related

- [HTTP API: Support](../reference/http-api.md#support)
- [Audit events](../reference/audit-events.md)
- [Permission nodes](../reference/permission-nodes.md)

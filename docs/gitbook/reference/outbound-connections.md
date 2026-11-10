---
description: Every connection Ingot opens to another host, what triggers it, what it sends, whether it is on by default and how to turn it off. A fresh installation makes one outbound call, the check for a newer version.
---

# Outbound connections

Ingot opens a connection to another host only in the cases listed on this page. A fresh installation with the default repositories, filesystem storage and SQLite makes exactly one outbound call: the hourly check for a newer version. Ingot has no telemetry, no usage reporting, no analytics and no licence or entitlement call. No code path reads a licence file.

## Summary

| Connection | Trigger | On by default | How to turn it off |
| --- | --- | --- | --- |
| Version check, GET to the GitHub releases API | An instance status request or the `status` console command, at most once an hour | Yes | `INGOT_STATUS_REMOTE_VERSION_CHECK=false` |
| Mirror and proxy repositories, HEAD and GET to the hosts they proxy | A client asks for a file that the proxy repository does not hold | No, the default repositories proxy nothing | Remove the entry from the `proxied` list |
| OIDC and GitHub login, discovery, keys, token exchange and user lookup | A login attempt, or the connection test of a configured provider | No, no provider is configured | Remove the provider |
| LDAP bind and search | A login with LDAP enabled | No | Disable LDAP |
| S3 requests to the configured endpoint | Any read or write in an S3 backed repository | No, filesystem storage is the default | Use filesystem storage |
| Database connection, MariaDB, MySQL or PostgreSQL | Startup and every query | No, the default is a local SQLite file | Use SQLite or H2 |
| NATS cluster bus | Startup, when a bus URL with the `nats` or `tls` scheme is set | No | Unset `INGOT_CLUSTER_URL` |

The database cluster bus, which is set with `database:`, reuses the database connection and opens no further host.

## Version check

Ingot asks `https://api.github.com/repos/OneLiteFeatherNET/ingot/releases/latest` for the newest release. Another address can be set with `-Dingot.status.remote-version-url=<url>`.

The check runs when an instance status request or the `status` console command needs the latest version, and at most once an hour. It does not run at startup. The request is a plain `GET` with no body, and it carries no Ingot identifier, instance identifier or version number. The JDK adds its own default `User-Agent` header and the source address of the host, and Ingot cannot remove either.

To turn it off, set `INGOT_STATUS_REMOTE_VERSION_CHECK=false` in the environment, or `-Dingot.status.remote-version-check=false` in the JVM options. The property wins when both are set. While the check is off, the instance reports its own version as the latest one. See [Remote version check](environment-variables.md#remote-version-check) and [Monitor status and failures](../how-to/monitor-status-and-failures.md).

## Mirror and proxy repositories

A proxy repository asks the hosts listed in its `proxied` settings for files that it does not hold. Ingot names no host of its own here. Only the hosts that an operator configured are contacted.

Requests use the proxy from the environment only when `INGOT_HTTP_USE_ENV_PROXY=true` is set. A proxy configured for a single mirror takes precedence over the environment. See [Create a proxy repository](../how-to/create-proxy-repositories.md).

## OIDC and GitHub login

A configured OIDC provider is contacted for its discovery document, its key set, the token exchange and the user lookup. GitHub login uses the fixed hosts `github.com`, for authorization and the token exchange, and `api.github.com`, for the user lookup. A generic OIDC provider uses the issuer address that its operator entered.

These requests never follow redirects, and each one times out after five seconds. They happen during a login attempt or when the connection test in the settings is run. See [Configure generic OIDC login](../how-to/configure-oidc-login.md) and [Configure GitHub login](../how-to/configure-github-login.md).

## LDAP

With LDAP enabled, a login opens a connection to the configured LDAP server to bind and search for the user. See [Configure LDAP login](../how-to/configure-ldap-login.md).

## S3

With an S3 backed repository, every read and write goes to the configured S3 endpoint, and so do the metadata lookups of the cache in front of it. Filesystem storage opens no connection. See [Use S3 storage](../how-to/use-s3-storage.md).

## Database

Startup and every query use the configured MariaDB, MySQL or PostgreSQL server. SQLite and H2 are files in the data directory and open no network connection. See [Configure a database](../how-to/configure-database.md).

## Cluster bus

With a bus URL set in `INGOT_CLUSTER_URL`, the NATS transport connects to that server when the instance starts. Without a URL, no bus is opened. See [Configure the cluster bus](../how-to/configure-cluster-bus.md).

## What does not exist

- No telemetry, usage report, analytics, licence or entitlement call.
- The migration plugin reads local data only. The Prometheus plugin answers scrapes and opens no connection of its own.
- The dashboard loads no script, style or font from another host. A visitor's browser fetches the default organization logo from `avatars.githubusercontent.com` unless the branding settings replace it. The server makes no such request.
- The container healthcheck and the base image call only `localhost`. Image pulls and package installs happen at build time, not when the server runs.

## Hosts for an egress allow list

On a fresh installation the only fixed host is `api.github.com`, for the version check. Every other host on this page is one that an operator configured. When GitHub login is enabled, add `github.com` and `api.github.com`.

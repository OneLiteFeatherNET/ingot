---
description: Find the cause of common Ingot problems, from a start that is refused to a 401, 403 or 413 on deploy, a failing proxy, a search that is not available or an upgrade that stops. Each entry gives the symptom, the cause, the fix and the page that goes deeper.
---

# Troubleshoot Ingot

This page starts from what you see: a message at start, an HTTP status, or a search that returns nothing. Each entry names the cause that the code produces and the fix. For a login that fails through an external provider, see [Troubleshoot external login](troubleshoot-external-login.md), which covers the `loginError` codes in detail.

## Where to look first

* **The console and the log files.** Every failure is written to the log. In the container image the files are in `/var/log/reposilite`. See [Monitor status and failures](monitor-status-and-failures.md).
* **The failure list.** `GET /api/status/failures` with an instance admin token lists the recorded failures, newest first, with their message and stack trace. The console command `failures` prints the same list.
* **The support bundle.** If you need help from someone else, collect a bundle instead of pasting settings. See [Collect a support bundle](collect-a-support-bundle.md).
* **The API answer.** Errors under `/api` and on the repository paths use the problem shape described in [API errors](../reference/api-errors.md). The `message` member holds the text quoted below.

## Start refused

When Ingot refuses to start for a configuration reason, it prints one line that starts with `Ingot did not start:` and exits with code `1`. Read the rest of the line, then fix the value it names.

| Line after `Ingot did not start:` | Cause | Fix |
|---|---|---|
| `Unknown deploy target '<value>' from <source>. Valid values: all, server.` | The deploy target is set to a value other than `all` or `server`. The source is `--target`, the property `ingot.target` or the variable `INGOT_TARGET`. There is no fallback. | Set the target to `all` or `server` in that source. See [Deploy targets](../reference/deploy-targets.md). |
| `<variable> names <file>, which does not exist`, `is a directory`, `is not a readable file`, `cannot be read` or `does not contain valid UTF-8 text` | A variable with the `_FILE` suffix, such as `INGOT_LOCAL_KEYPASSWORD_FILE`, names a file that Ingot cannot read as text. | Point the variable at a readable UTF-8 file, or set the plain variable instead. See [Environment variables](../reference/environment-variables.md#file-values). |
| `<variable> names <value>, which is not a valid path` | The path in a `_FILE` variable cannot be parsed. | Correct the path. |
| `Set either INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE, not both` | Both the inline key and the key file are set. | Keep one of them. |
| `... is not valid base64`, `must hold base64 of 32 bytes` or `names a file that cannot be read` | The settings key is malformed or unreadable. The message names the variable, never the key. | Generate the key again with 32 random bytes, base64 encoded, and check the file. See [Settings encryption](../reference/settings-encryption.md). |
| `INGOT_SETTINGS_ENCRYPTION is 'required', but no settings key is configured.` | The mode is `required` and no key is set. | Set `INGOT_SETTINGS_KEY` or `INGOT_SETTINGS_KEY_FILE`, or set the mode to `optional`. |
| `Settings domain '<domain>', property '<path>' is encrypted, but no settings key is configured.` | The stored settings were sealed with a key, and this start has none. | Start with the key that wrote them. |
| `The settings key does not match the key of this database (key id <id>).` or `... the key that sealed the stored settings (key id <id>).` | The configured key is not the one the database or the other replicas use. | Start with the key the other instances use. Do not change the key to get past this, because the stored secrets stay unreadable. |

Some start refusals are not one line. A value that Ingot cannot parse at start, such as an unknown database type, a malformed cluster bus URL or an unknown `INGOT_PROXY_UPSTREAM_STATUS`, stops the start with an exception. The JVM prints its message and a stack trace. The entries below name the message.

## Database

**Symptom:** the process stops at start with `Unknown database: <value>`.

* **Cause:** the `database` value does not begin with `sqlite`, `mariadb`, `postgresql`, `mysql`, `h2` or `jdbc:`.
* **Fix:** write the value in one of the formats of [Configure a database](configure-database.md). Remember that the `--database` option, the system property, the variable, and the `configuration.cdn` key are read in that order, and the first one that is set wins.

**Symptom:** `Unsupported JDBC driver for URL: <url>`.

* **Cause:** a custom JDBC URL starts with a prefix other than `jdbc:mariadb`, `jdbc:mysql`, `jdbc:postgresql`, `jdbc:sqlite` or `jdbc:h2`.
* **Fix:** use one of these prefixes. The message carries the URL, so remove any password from it before you paste the log anywhere.

**Symptom:** the start stops while the pool opens, with an exception from the JDBC driver or the connection pool. The text usually names the host, the port, the database or the user.

* **Cause:** the database server is not reachable from the host or the container, the database does not exist, the user or password is wrong, or the user has no right to create tables. Ingot creates its tables on start, so the user needs that right.
* **Fix:** test the host and port from the same network as Ingot. The database must exist and be empty, as the prerequisites of [Configure a database](configure-database.md) say. For MariaDB and PostgreSQL the user needs rights to create and change tables. A password with a space does not work in the simple form, so use a custom JDBC URL for it.

**Symptom:** a SQLite instance fails with two replicas, or a replica shows a database error after a restart.

* **Cause:** SQLite is one file on one machine. It is not supported for more than one replica. See [High availability support](../explanation/high-availability-support.md#not-supported).
* **Fix:** move to MariaDB or PostgreSQL before you add a replica.

## Storage and S3

**Symptom:** uploads or downloads answer `500` with `Failed to write <path>`, `Failed to read <path>`, `Failed to HEAD <key>`, `Failed to list <path>` or `Failed to remove <path>`.

* **Cause:** the S3 request failed for a reason other than a missing key or a `403`. That covers the network, the endpoint, the region, the credentials and server errors. The log holds the exception under `S3 Storage Provider failed to <operation>`.
* **Fix:** read the exception in the log. Check the endpoint, the region, the access key and the secret key in the storage settings. Test the bucket with the same credentials from the host. See [Use S3 storage](use-s3-storage.md).

**Symptom:** files that you know are in the bucket answer `404`.

* **Cause:** S3 answers a missing key with `403` when the credentials lack the `ListBucket` permission. Ingot answers any `403` on a read as "not found", so that a missing file looks the same as a denied one. The reason is written at debug level as `S3 denied ... answering as not found (the credentials may lack ListBucket)`.
* **Fix:** give the credentials `s3:GetObject` and `s3:ListBucket` on the bucket. The reason is written to the log file at debug level. To see it on the console too, run the console command `level debug`.

**Symptom:** the start stops with an S3 error while the bucket is checked.

* **Cause:** at start Ingot checks the bucket and creates it only when the check reports that it does not exist. Any other error, such as `403` from a bucket owned by someone else, stops the start.
* **Fix:** make the bucket visible to the credentials, or create it yourself. If the bucket is managed outside Ingot, set the system property `reposilite.s3.skip-bucket-creation=true`, which skips the check.

## Deploy: 401, 403 and 409

A deploy authenticates with the token name and secret, sent in an HTTP basic `Authorization` header. See [Use tokens in CI](use-tokens-in-ci.md).

**Symptom:** `401` with `Missing authorization credentials`.

* **Cause:** the request has no `Authorization` header, so the build tool sent no credentials for this server.
* **Fix:** configure the token in the build tool for the server id that the repository uses, and check that the build tool sends it.

**Symptom:** `401` with `Invalid authorization credentials`.

* **Cause:** the token name or the secret is wrong, the token has been revoked, or the address is blocked. When brute force protection is on and an address has failed too often, Ingot answers the same message for every request from that address, including correct ones, until the ban ends. The protection is off by default. See [Sessions, login and CSRF](../explanation/sessions-and-login.md).
* **Fix:** check the secret in the build tool. If the token is correct, wait for the ban to end. It lasts 300 seconds by default.

**Symptom:** `403` with `This token is not authorized to access this path`.

* **Cause:** the token has no write right for the path, or the token has been narrowed to other artifacts. A token can only narrow the rights of its owner, so the owner must have the right as well.
* **Fix:** check the rights of the token's owner with [Check what a principal can do](check-access.md). Widen the grant with [Grant permissions with nodes](grant-permissions-with-nodes.md), or create a token that is not narrowed for this path. A token cannot be widened after it is created.

**Symptom:** `409` with `Redeployment is not allowed`.

* **Cause:** the file exists and the repository does not accept redeployment. Redeployment is off by default.
* **Fix:** deploy a new version. Change the repository only when you need to replace published files, see [Repository settings](../reference/repository-settings.md).

**Symptom:** `405` with `is read-only and does not accept deployments or deletions`, or with `is a proxy` or `is a group and does not accept uploads`.

* **Cause:** the repository is read-only, or it is a proxy or a group repository, which take no uploads. See [Repository types](../reference/repository-types.md).
* **Fix:** deploy to a hosted repository, or clear the read-only setting if it was set on purpose by mistake.

**Symptom:** `503` with `The instance is read-only, writes are refused until read-only mode is turned off`, and a `Retry-After` header of 30 seconds.

* **Cause:** the instance-wide read-only mode is on. It is a different switch from the read-only repository setting, and it answers before the repository is checked.
* **Fix:** turn it off once the maintenance is done. See [Freeze writes for maintenance](freeze-writes-for-maintenance.md).

**Symptom:** `400` with a checksum message, for example `SHA1 checksum file ... does not match the stored artifact, so it was refused`.

* **Cause:** the repository has the `reject` upload checksum policy, and the uploaded checksum file does not match the artifact. See [API errors](../reference/api-errors.md#checksum-file-refused).
* **Fix:** check that the checksum was computed from the same bytes as the artifact. Upload the artifact again if it was changed after the checksum was made.

**Symptom:** `400` with a version policy message on a deploy.

* **Cause:** the repository has a version policy that refuses this version, for example a snapshot in a release repository.
* **Fix:** deploy to the repository that matches the version, or change the version policy. See [Version policy and read-only](../reference/repository-settings.md#version-policy-and-read-only).

**Symptom:** `507`, which the API documents as "not enough disk space".

* **Cause:** the storage of the instance is full.
* **Fix:** free space or add storage capacity.

## Large uploads: 413

**Symptom:** a deploy of a large file fails with `413 Request Entity Too Large`, or the proxy in front of Ingot answers `413` before Ingot logs the request.

* **Cause:** a request body limit is too small. The edge (ingress, nginx, a gateway) has its own limit, such as `client_max_body_size` in nginx. If the edge allows the body, Ingot has a limit too. Its default is the Javalin default, and it is raised with the system property `javalin.maxRequestSize` in bytes.
* **Fix:** raise the limit at the edge first, then in Ingot if the `413` still comes from Ingot. See [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md).

## Proxy and mirror

A proxy repository fetches a file from its upstream and, when `store` is set, keeps a copy. The statuses it answers depend on `INGOT_PROXY_UPSTREAM_STATUS`. The default, `legacy`, keeps the answers that Reposilite always gave. `strict` reports what went wrong. The full table is in [Create a proxy repository](create-proxy-repositories.md#choose-the-status-for-upstream-failures).

**Symptom:** a file that the upstream has answers `404`.

* **Cause:** the proxy did not contact the upstream at all. A mirror host is skipped when its allowed extensions or groups do not match the path, or when it is set to fetch only with a token and the request is anonymous. When no host is left, the answer is `404`.
* **Fix:** check the allowed extensions and groups of the mirror, and whether the request carries a token when the host needs one. Then check the upstream URL with `curl -I`.

**Symptom:** `502` with `<host> refused the configured credentials`.

* **Cause:** the upstream answered `401` or `403` to the credentials of the proxy. Ingot does not pass that status on, because a browser would read it as a refused session.
* **Fix:** update the credentials on the proxied entry. Test them with `curl -u` against the upstream URL.

**Symptom:** `502` with `Upstream copy could not be verified against its checksum`.

* **Cause:** the repository uses the `fail` checksum policy, and the upstream has no checksum file or a copy that does not match its checksum. The copy is not stored.
* **Fix:** check that the upstream publishes a checksum for the artifact. If it does not, use the `warn` policy, which stores the copy and records the event. See [Storage and mirror caching](../explanation/storage-and-mirror-caching.md).

**Symptom:** Gradle or Maven fails with `502` or `504` from the proxy, and does not try the next repository.

* **Cause:** Gradle goes to the next repository only on a `404`. Under `strict`, a timeout answers `504` and an unreachable upstream answers `502`, so the build fails.
* **Fix:** find out why the upstream is slow or down. If you want the build to continue past a failing mirror, keep the `legacy` mode, or order the repositories so that the healthy one comes first.

**Symptom:** a metadata request answers `400` with `Upstream request failed`.

* **Cause:** in the default `legacy` mode, an unreachable or slow upstream answers a metadata request with `400`.
* **Fix:** check the upstream, or switch to `strict` to see the real status. The message is not a problem with the request.

**Symptom:** the start stops with `Unknown value '<value>' for INGOT_PROXY_UPSTREAM_STATUS, expected 'legacy' or 'strict'`.

* **Cause:** the variable holds another value. The comparison ignores case.
* **Fix:** set it to `legacy` or `strict`, or unset it.

## Search

Artifact search has its own state, which you can read from the index status endpoint. The meaning of each backend state, and what to do about it, is in [Choose a search backend](choose-a-search-backend.md#backend-states).

**Symptom:** search answers `404` with `Artifact search is turned off on this instance`.

* **Cause:** the search setting `searchEnabled` is `false`.
* **Fix:** turn it on, or use the artifact listing in the dashboard, which does not depend on the index.

**Symptom:** search answers `503` with the title `Search backend not available`, and `Retry-After: 30`.

* **Cause:** the search backend of this replica is not available. The detail names the state, such as `not installed`, `not open, its last attempt failed`, `down` or `not the backend of this cluster`.
* **Fix:** follow the table in [Choose a search backend](choose-a-search-backend.md#backend-states) for the named state. Downloads and deploys keep working while search is down.

**Symptom:** search returns fewer artifacts than the repository holds, or nothing for a new upload.

* **Cause:** the index is still building. After an upgrade the index is empty, and the first start runs a full rebuild in the background. The index state is `Building` until the run completes. A start that finds a stale heartbeat may also queue a gap rebuild, which the search setting `gapRebuild` controls.
* **Fix:** wait for the run to finish. Check its progress on the Maintenance page or with the search index endpoint. If the result stays wrong, start a rebuild with [Rebuild the search index](rebuild-search-index.md).

**Symptom:** starting a rebuild or a verify run answers `409` with `A rebuild or verify run is already running.`

* **Cause:** only one run may run at a time.
* **Fix:** wait for the running one to finish, or cancel it, then start the new one.

**Symptom:** search answers `503` with `The search timed out, narrow the filters`.

* **Cause:** the query matched too much, or the backend was slow.
* **Fix:** narrow the query with the filters in [Search filters and API](../reference/search-filters-and-api.md).

## Cluster bus

The cluster bus is optional. Without `INGOT_CLUSTER_URL` it is off, and nothing in this section applies. The settings are in [Configure the cluster bus](configure-cluster-bus.md).

**Symptom:** the start stops with `Cluster bus URL cannot be parsed`, `Cluster bus URL has no host`, or `Cluster bus URL uses an unsupported scheme`.

* **Cause:** the URL is malformed, or its scheme is not one of `nats`, `tls`, `redis`, `rediss`, `valkey`, `valkeys` or `database`. The message does not print the URL.
* **Fix:** write the URL in one of the accepted forms. The stack trace from the JVM is expected here.

**Symptom:** the start stops with `<name> must be true or false`, or with `INGOT_CLUSTER_POLL_INTERVAL_MS must be a whole number of milliseconds`.

* **Cause:** a boolean switch or the poll interval holds a value that does not parse.
* **Fix:** use `true` or `false`, or a whole number of milliseconds.

**Symptom:** the log shows `Cluster | The database cluster bus needs MariaDB, MySQL or PostgreSQL`, or `takes no host and no credentials`, and the instance runs without the bus.

* **Cause:** the `database` transport was chosen on a database that cannot share it, or the URL carries a host or credentials. The start goes on with the bus off, so the replicas do not share events.
* **Fix:** use `database:` alone as the URL on MariaDB, MySQL or PostgreSQL, or choose another bus. See [High availability support](../explanation/high-availability-support.md).

**Symptom:** the log shows `no transport is available for <scheme> in this build`.

* **Cause:** the build of Ingot does not contain a transport for that scheme.
* **Fix:** use a scheme that the build supports. The bus URL reference in [Configure the cluster bus](configure-cluster-bus.md) lists the schemes.

**Symptom:** the status endpoint shows a bus state of `reconnecting`, and the other replicas look out of date.

* **Cause:** the bus broker is unreachable, or the credentials were rejected.
* **Fix:** check the broker from the host. The status endpoint shows the `cluster` section only when a bus is configured, with the bus state and the instances it knows.

## Upgrade

Read [Back up before upgrading](back-up-before-upgrading.md) first, and keep the backup until [Verify an upgrade](verify-an-upgrade.md) passes.

**Symptom:** the start stops with `Identity migration copied <n> token(s), expected <m>`, `created <n> machine principal(s)`, `created <n> grant(s)` or `grant plan disagrees with the legacy routes`.

* **Cause:** a check of the migration found that the copied rows do not match the legacy rows. The check rolls the migration back, so the data is in the state of the previous release.
* **Fix:** collect the log and a support bundle. Then either retry the start after the cause is fixed, or restore the backup you took before the upgrade. See [Roll back an upgrade](roll-back-an-upgrade.md) and [Token migration](../explanation/token-migration.md).

**Symptom:** a token from Reposilite does not work, or it works with other rights.

* **Cause:** the token became a machine account. Its rights moved to the private team of that account, and the old route matching is kept in that team. A token whose name differs from another token only in case gets a principal with a different name, and the log says so.
* **Fix:** check the account with [Check what a principal can do](check-access.md). See [Token migration](../explanation/token-migration.md).

**Symptom:** a setting that you changed in an environment variable has no effect.

* **Cause:** the name is misspelled, or a `REPOSILITE_` name is set where an `INGOT_` name is also set. The `INGOT_` name wins. An unknown `INGOT_LOCAL_` or `REPOSILITE_LOCAL_` variable is logged as a warning and ignored.
* **Fix:** search the log for `Local configuration`. Correct the name. See [Environment variables](../reference/environment-variables.md).

**Symptom:** the log warns `Deploy target 'all' from <source> overrides the legacy setting defaultFrontend=false`.

* **Cause:** the old setting `defaultFrontend=false` means `server`, but a target set to `all` takes precedence over it.
* **Fix:** remove the legacy setting, or set the target to `server`.

**Symptom:** the replicas of the old and the new release run side by side and one of them fails.

* **Cause:** starting replicas of both releases at the same moment is not supported. Only adjacent releases are tested together.
* **Fix:** follow the order in [High availability support](../explanation/high-availability-support.md#rolling-upgrade-rule).

**Symptom:** the search index is `Building` for a long time after an upgrade.

* **Cause:** the first start builds the index from storage, which takes longer on a large repository.
* **Fix:** wait, and check the progress. It is expected, and it does not block downloads or deploys.

## Related pages

* [Monitor status and failures](monitor-status-and-failures.md) describes the status, the failure list and the logs.
* [Verify an upgrade](verify-an-upgrade.md) lists the checks after an upgrade.
* [Troubleshoot external login](troubleshoot-external-login.md) covers the login errors of external providers.
* [Collect a support bundle](collect-a-support-bundle.md) describes what to attach to a ticket.

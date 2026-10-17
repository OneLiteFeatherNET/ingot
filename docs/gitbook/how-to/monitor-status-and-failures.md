---
description: Check that an Ingot instance is up, read its status and recorded failures, find its log files and, optionally, scrape Prometheus metrics.
---

# Monitor status and failures

Ingot offers four ways to see what an instance is doing: a health endpoint for probes, a status endpoint for the instance and its recorded failures, log files, and optional Prometheus metrics. This page shows how to use each one.

## Before you begin

* For the health check, nothing. The endpoint needs no credentials.
* For the status and the failures, the credentials of an instance admin token. Send them with HTTP basic authentication, as the other API calls do. Other callers are refused.
* For the log files, access to the host or the container. For Prometheus, the ability to build the plugin jar and to restart the instance.

## Check that the instance answers

1. Request the health endpoint:

   ```
   curl -i https://repo.example.org/api/status/health
   ```

2. Read the result.
   * `200` with `{"status":"UP"}` means that the web server is running and answers.
   * `503` with `{"status":"DOWN"}` means that the web server is not ready or not alive.

There is no separate readiness endpoint. `/api/status/health` is the only health check that Ingot provides.

The container image has its own health check. It reads the address that Ingot writes to `/app/data/.local/reposilite.address` when it starts, and requests the root of the instance.

## Read the instance status

1. Request the instance status with an instance admin token:

   ```
   curl -u "$TOKEN_ID:$TOKEN_SECRET" https://repo.example.org/api/status/instance
   ```

2. Read the fields:

| Field | Meaning |
|-------|---------|
| `version` | The version of the running Ingot. |
| `latestVersion` | The newest release that Ingot found, or `<unknown>` when it could not find one. |
| `uptime` | Milliseconds since the instance started. |
| `usedMemory` | Memory in use by the process, in megabytes. |
| `maxMemory` | The maximum heap, in megabytes. |
| `usedThreads` | The number of live threads. |
| `maxThreads` | The sum of the sizes of the web, I/O and database thread pools. |
| `failuresCount` | The number of recorded failures. Read the list in the next section. |

The console command `status` prints the same values, plus whether the instance is active.

`latestVersion` comes from the GitHub releases API. Ingot asks at most once an hour. To stop these outbound requests, for example on a network without internet access, start the instance with `-Dingot.status.remote-version-check=false` in `JAVA_OPTS`. To ask another endpoint, set `-Dingot.status.remote-version-url=<url>`. That endpoint can answer with a plain version string or with a GitHub release document.

## Look at memory and threads over time

Request `GET /api/status/snapshots` with an instance admin token. The answer holds up to 12 snapshots, oldest first. Each one has `at` (epoch milliseconds), `memory` (megabytes) and `threads`.

The first snapshots are taken a minute apart after the start. After 12 snapshots, one is taken every hour. The snapshots are kept in memory, so a restart clears them.

## Find recorded failures

Ingot records a failure when a request ends with an unhandled exception, or when a component reports one. Identical failures are folded into one record.

1. List them with an instance admin token:

   ```
   curl -u "$TOKEN_ID:$TOKEN_SECRET" https://repo.example.org/api/status/failures
   ```

2. Read the answer. The list is newest first. Each record has `id`, `timestamp` (ISO-8601), `source`, `type`, `message`, `stacktrace` and `count`.
   * `count` is the number of times the same failure happened.
   * `timestamp` is the latest occurrence.

3. Clear the list when you have read it:

   ```
   curl -X DELETE -u "$TOKEN_ID:$TOKEN_SECRET" https://repo.example.org/api/status/failures
   ```

The console command `failures` prints the recorded exceptions in the same way.

The list is kept in memory. A restart clears it, so the log files are the record of older failures.

## Read the log files

Every failure is also written to the log files, together with the rest of the log.

* **In the container image**, the files are in `/var/log/reposilite`. The current file is `latest.log` and the daily files are named `log_<date>.txt`. Mount that directory as a volume if the logs must outlive the container.
* **Outside the container**, the files are in a `logs` directory, created relative to the directory the process starts in. The file names are the same.

The files hold all levels, from debug upwards. A new file starts at each start of the instance, at 03:00 each month, and when a file reaches 100 MB. Ingot keeps 10 backups and compresses them with gzip.

To change the level of the messages that the console shows while the instance runs, use the console command `level <level>`.

## Scrape Prometheus metrics (optional)

The Prometheus plugin is not part of the Ingot image. Build it from this repository and add it to the instance.

1. Build the plugin jar. The Gradle module is `reposilite-plugins:prometheus-plugin`, and the shadow jar is `prometheus-plugin.jar`.

2. Copy `prometheus-plugin.jar` into the plugins directory. By default that is `plugins` in the working directory. In the container image that is `/app/data/plugins`. The directory is created if it does not exist. The option `-pd` (`--plugin-directory`) changes it.

3. Set the credentials for the metrics endpoint. Use either the environment variables `REPOSILITE_PROMETHEUS_USER` and `REPOSILITE_PROMETHEUS_PASSWORD`, or the JVM properties `reposilite.prometheus.user` and `reposilite.prometheus.password`. The plugin does not read the `INGOT_` spelling of these names. It throws at start when either value is missing.

   The path defaults to `/metrics`. Change it with `REPOSILITE_PROMETHEUS_PATH` or `reposilite.prometheus.path`.

4. Restart the instance, then scrape the endpoint with the credentials:

   ```
   curl -u "$PROMETHEUS_USER:$PROMETHEUS_PASSWORD" https://repo.example.org/metrics
   ```

   A request with other credentials is refused. To read only some metrics, add `?name[]=<name>` for each one.

The plugin exports these metric families:

| Metric | What it covers |
|--------|----------------|
| `reposilite_uptime_seconds` | Uptime of the instance. |
| `reposilite_failures_total` | Recorded failures. |
| `reposilite_responses_total` | Responses, labelled by status code. |
| `reposilite_resolved_total` | Files resolved for a client. |
| `reposilite_deploy_total` | Deployments. |
| `reposilite_response_file_size_bytes` | Sizes of the files that were served. |
| `jetty_*` | The web server: requests and thread pool. |

The plugin also exports the JVM metrics.

## What Ingot does not offer

* A readiness probe. Use `/api/status/health` for both checks.
* Alerting. Ingot records and exposes the values, and an external system such as Prometheus with Alertmanager decides when to alert.
* A history of failures or snapshots across restarts. Use the log files or an external scrape for that.

## Related

* [Verify an upgrade](verify-an-upgrade.md) lists the checks to run after an upgrade.
* [Search filters and API](../reference/search-filters-and-api.md) describes the search index status, which is a separate endpoint.

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

`/api/status/health` answers only whether the web server is running. A load balancer that must not send traffic to a replica that cannot serve it should probe the readiness endpoint instead:

   ```
   curl -i https://repo.example.org/api/status/ready
   ```

   * `200` with `{"status":"UP","failing":[]}` means that every readiness check passes.
   * `503` with `{"status":"DOWN","failing":["database"]}` means that at least one check fails. The `failing` list names the checks, in this order: `draining`, `plugins`, `database`, `storage` and `cluster`. The `cluster` check counts only when a cluster bus URL is configured.

Each check has two seconds to answer, and a check that does not answer counts as failing. The body names no repository, path or error message. The details of a failing check are written to the log at `WARN`. While the database check fails, `/api/status/health` still answers `200` and `/api/status/ready` answers `503`.

While an instance shuts down, `/api/status/ready` answers `503` with `draining` first in `failing`, and `/api/status/health` stays `200`. The health answer does not change during a drain on purpose: an orchestrator that saw it fail would restart the instance while it still finishes its requests. Use the readiness probe to take an instance out of rotation.

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

`latestVersion` comes from the GitHub releases API. Ingot asks at most once an hour. To stop these outbound requests, for example on a network without internet access, set `INGOT_STATUS_REMOTE_VERSION_CHECK=false` in the environment of the instance. The older system property `-Dingot.status.remote-version-check=false` in `JAVA_OPTS` does the same and wins when both are set. To ask another endpoint, set `-Dingot.status.remote-version-url=<url>`. That endpoint can answer with a plain version string or with a GitHub release document. Every other connection that Ingot can open is listed in [Outbound connections](../reference/outbound-connections.md).

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

The container image ships the Prometheus plugin as `/app/bundled-plugins/prometheus-plugin.jar`, outside the data volume. It stays off until you set the switch. The steps below are for the image. An installation that does not use the image has to build the plugin first, see [Installations without the image](#installations-without-the-image).

1. Set `INGOT_METRICS_ENABLED=true`, and set the credentials for the metrics endpoint with `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD`. The older `REPOSILITE_PROMETHEUS_USER` and `REPOSILITE_PROMETHEUS_PASSWORD` names, and the JVM properties `reposilite.prometheus.user` and `reposilite.prometheus.password`, still work. The plugin throws at start when a value is missing.

   The path defaults to `/metrics`. Change it with `INGOT_PROMETHEUS_PATH`, or with the older `REPOSILITE_PROMETHEUS_PATH` or `reposilite.prometheus.path`.

2. Restart the instance, then scrape the endpoint with the credentials:

   ```
   curl -u "$PROMETHEUS_USER:$PROMETHEUS_PASSWORD" https://repo.example.org/metrics
   ```

   A request with other credentials is refused. To read only some metrics, add `?name[]=<name>` for each one.

### Installations without the image

1. Build the plugin jar. The Gradle module is `reposilite-plugins:prometheus-plugin`, and the shadow jar is `prometheus-plugin.jar`.

2. Copy `prometheus-plugin.jar` into the plugins directory. By default that is `plugins` in the working directory. In the container image that is `/app/data/plugins`. The directory is created if it does not exist. The option `-pd` (`--plugin-directory`) changes it.

3. Set the credentials as in step 1 above. A plugin in the plugins directory is always loaded, so `INGOT_METRICS_ENABLED` is not needed for this copy.

4. Restart the instance, then scrape the endpoint as in step 2 above.

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
| `ingot_http_server_request_duration_seconds` | Histogram of request durations, labelled `method`, `route` and `code`. The route is the route template, or `unmatched`. |
| `ingot_http_server_requests_total` | Counter of requests, labelled `method`, `route` and `code`. |
| `ingot_deploy_results_total{result}` | Counter of deploys, `result` is `accepted` or `rejected`. |
| `ingot_upstream_requests_total{outcome}` | Counter of fetches from upstream hosts of proxy repositories, `outcome` is `success`, `not_found`, `failure` or `timeout`. |
| `ingot_mirror_cache_entries` | Gauge of the rows in the mirror cache, across all proxy repositories. |
| `ingot_cluster_connected` | Gauge, `1` when the cluster bus is connected and `0` when it is not. Present only when a cluster bus is configured. |
| `ingot_cluster_dropped_events_total` | Counter of cluster bus messages dropped because they exceeded the transport limits. Present only when a cluster bus is configured and its transport reports drops. |
| `ingot_batch_job_last_success_timestamp_seconds{job}` | Gauge, Unix time of the last successful run on this instance, `job` is `cleanup` or `audit-purge`. A job with no success yet has no sample. |
| `ingot_search_outbox_pending` | Gauge of search outbox entries that may still be processed. Present only when search is enabled. |
| `ingot_search_outbox_oldest_age_seconds` | Gauge, age of the oldest pending search outbox entry, `0` when there is none. Present only when search is enabled. |

Counters and histograms count the work of one instance, so sum them across replicas with `sum by (...)`. Gauges and the job timestamps describe one instance as well. No series carries a repository name, a path, a user name or a token name, so the number of series does not grow with the number of repositories.

The plugin also exports the JVM metrics.

## Alert rules and dashboards

The Helm chart can render alert rules and Grafana dashboards for these metrics. Both read the metrics of the bundled plugin, so turn on the plugin first (step 1 above) and the ServiceMonitor of the chart, which provides the `up` series that the availability alert reads.

* `prometheusRule.enabled: true` renders a PrometheusRule. It needs the Prometheus Operator CRDs, and it has six alerts:

  | Alert | Fires when |
  |-------|------------|
  | `IngotDown` | Prometheus cannot scrape the server for 5 minutes. |
  | `IngotHighErrorRatio` | More than 5% of the responses are server errors for 10 minutes. |
  | `IngotUpstreamUnreachable` | More than half of the upstream fetches fail or time out for 15 minutes. |
  | `IngotDeployRejections` | Deploys are rejected by the repository rules at a sustained rate for 30 minutes. |
  | `IngotHeapPressure` | The heap stays above 90% of its maximum for 10 minutes. |
  | `IngotClusterBusDisconnected` | A replica reports no cluster bus connection for 5 minutes. |

* `grafanaDashboards.enabled: true` renders three ConfigMaps for the Grafana sidecar, each labelled `grafana_dashboard: "1"`: an overview, a mirroring and deploys dashboard, and a JVM dashboard. The sidecar loads them into Grafana.

Compose users, or anyone who runs Grafana without the sidecar, can import the same dashboard JSON files by hand. They are in the repository under [charts/ingot/files/dashboards](https://github.com/OneLiteFeatherNET/ingot/tree/main/charts/ingot/files/dashboards): `overview.json`, `mirroring.json` and `jvm.json`. Each one has a `datasource` and a `namespace` and `service` variable, so pick the Prometheus data source and the scraped instance when you import it. For the alert rules outside the chart, copy the expressions from [the rule template](https://github.com/OneLiteFeatherNET/ingot/blob/main/charts/ingot/templates/prometheusrule.yaml) and replace the namespace and service selectors with yours.

## What Ingot does not offer

* Alerting in the server. Ingot records and exposes the values. The chart can render alert rules for Prometheus, see [Alert rules and dashboards](#alert-rules-and-dashboards), and Alertmanager decides where they go.
* A history of failures or snapshots across restarts. Use the log files or an external scrape for that.

## Related

* [Verify an upgrade](verify-an-upgrade.md) lists the checks to run after an upgrade.
* [Search filters and API](../reference/search-filters-and-api.md) describes the search index status, which is a separate endpoint.

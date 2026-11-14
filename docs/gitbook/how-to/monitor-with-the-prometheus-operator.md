---
description: Scrape the metrics of Ingot with a Prometheus Operator ServiceMonitor, load the six base alerts as a PrometheusRule and the three Grafana dashboards from the Helm chart, and check that each part reads the same metrics.
---

# Monitor with the Prometheus Operator

The Helm chart can render three monitoring objects for the server: a ServiceMonitor that scrapes the metrics, a PrometheusRule with six base alerts, and three Grafana dashboards. All three are off by default. The metrics come from the Prometheus plugin, which the image ships. This page turns the objects on in the order they depend on each other. The metric names and the meaning of each one are in [Monitor status and failures](monitor-status-and-failures.md#scrape-prometheus-metrics-optional).

## Before you begin

* The Prometheus Operator CRDs, `monitoring.coreos.com`, in the cluster. The ServiceMonitor and the PrometheusRule need them.
* A Prometheus instance that selects ServiceMonitors and PrometheusRules by a label. You need that label for the values below.
* For the dashboards, a Grafana with a sidecar that loads ConfigMaps labelled `grafana_dashboard`, and a Prometheus data source.

## Step 1: Turn on the metrics on the server

The plugin stays off until the server sets `INGOT_METRICS_ENABLED=true`. The endpoint also needs credentials, and the plugin does not start without them. Store the credentials in a Secret, and read them into the server environment:

```bash
kubectl -n ingot create secret generic ingot-metrics-scrape \
  --from-literal=username='<user>' \
  --from-literal=password='<password>'
```

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xmx64M"
  - name: INGOT_METRICS_ENABLED
    value: "true"
  - name: INGOT_PROMETHEUS_USER
    valueFrom:
      secretKeyRef:
        name: ingot-metrics-scrape
        key: username
  - name: INGOT_PROMETHEUS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: ingot-metrics-scrape
        key: password
```

The variables `REPOSILITE_PROMETHEUS_USER` and `REPOSILITE_PROMETHEUS_PASSWORD` still work as fallbacks. The `INGOT_` names take precedence. The path defaults to `/metrics`. If you change it with `INGOT_PROMETHEUS_PATH`, set `serviceMonitor.path` to the same path.

The `env` list above replaces the default list of the chart, so keep the `JAVA_OPTS` entry that you need.

## Step 2: Render the ServiceMonitor

The ServiceMonitor scrapes the `http` port of the server Service. It sends basic auth from the same Secret. The chart reads the two keys of that Secret through `serviceMonitor.basicAuth`:

```yaml
serviceMonitor:
  enabled: true
  path: /metrics
  interval: 30s
  labels:
    release: kube-prometheus
  basicAuth:
    enabled: true
    secretName: ingot-metrics-scrape
    usernameKey: username
    passwordKey: password
```

Replace the label `release: kube-prometheus` with the label that your Prometheus instance selects its ServiceMonitors with. The Secret must be in the release namespace, which it is when you create it as in step 1.

Without `basicAuth`, the scrape sends no credentials, and the endpoint answers `401`.

## Step 3: Render the alerts and the dashboards

```yaml
prometheusRule:
  enabled: true
  labels:
    release: kube-prometheus
grafanaDashboards:
  enabled: true
```

The PrometheusRule holds six alerts. Each one reads the metrics of this release, selected by the `namespace` and `service` labels of the release:

| Alert | Fires when | Severity |
|---|---|---|
| `IngotDown` | Prometheus cannot scrape the server for 5 minutes. | critical |
| `IngotHighErrorRatio` | More than 5% of the responses are server errors for 10 minutes. | warning |
| `IngotUpstreamUnreachable` | More than half of the upstream fetches of proxy repositories fail or time out for 15 minutes. | warning |
| `IngotDeployRejections` | Deploys are rejected by the repository rules at a sustained rate for 30 minutes. | warning |
| `IngotHeapPressure` | The heap stays above 90% of its maximum for 10 minutes. | warning |
| `IngotClusterBusDisconnected` | A replica reports no connection to the cluster bus for 5 minutes. | warning |

`IngotClusterBusDisconnected` only fires when the cluster bus is configured. The gauge that it reads exists only then.

The three ConfigMaps of the dashboards are labelled `grafana_dashboard: "1"`. The sidecar of your Grafana must watch that label. Each dashboard has the variables `datasource`, `namespace` and `service`. Choose your Prometheus data source, the release namespace and the server service when you open a dashboard.

## Check the objects

```bash
kubectl -n ingot get servicemonitor,prometheusrule
kubectl -n ingot get configmap -l grafana_dashboard=1
```

In the Prometheus web interface, the target of the server must show as `UP`. This query must return one series per replica:

```promql
up{namespace="ingot", service="ingot"}
```

Replace `ingot` with your release namespace and release name. A `401` in the target error means that the credentials of the Secret and the server do not match.

## Troubleshoot

* **The target is down with `401`.** The Secret of the ServiceMonitor and the `INGOT_PROMETHEUS_USER` and `INGOT_PROMETHEUS_PASSWORD` of the server differ, or `basicAuth.enabled` is `false`.
* **The endpoint never answers `200`.** Without `INGOT_METRICS_ENABLED=true`, the image serves no metrics. Set the switch on the server.
* **Prometheus does not load the ServiceMonitor.** Its label does not match the selector of your Prometheus instance. Set `serviceMonitor.labels` to that label.
* **`IngotDown` fires right after the install.** Prometheus has not scraped the server yet, or the policy of [Restrict network traffic](restrict-network-traffic.md) blocks the scrape. The alert clears once Prometheus scrapes the server successfully.
* **A dashboard is empty.** The data source variable points at a Prometheus that does not scrape the release, or the time range is before the first scrape.

## Related

* [Monitor status and failures](monitor-status-and-failures.md) lists the metrics and the alert rules, and explains installations without the image.
* [Size the deployment](size-the-deployment.md) uses the heap and request metrics to choose the sizes.
* [Restrict network traffic](restrict-network-traffic.md) lists how to admit the scrape.
* [Helm chart values](../reference/helm-values.md#monitoring) lists the keys of the three objects.

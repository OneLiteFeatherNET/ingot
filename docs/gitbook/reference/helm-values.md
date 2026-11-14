---
description: Reference of every key in the values.yaml of the Ingot Helm chart, grouped by area, with its type, its default and what it renders. Use it to write a values file or to check what a key does.
---

# Helm chart values

The Ingot chart is published as `oci://ghcr.io/onelitefeathernet/charts/ingot`. This page lists every key of its `values.yaml`, grouped by area. The default of each key is the value in that file. The type, the enumeration and the pattern come from `values.schema.json`.

To install the chart, see [Run on Kubernetes](../how-to/run-on-kubernetes.md). To run the dashboard as its own workload, see [Run the dashboard split from the server with Helm](../how-to/run-split-with-helm.md).

## How to read the tables

* A key is a dotted path. `deployment.replicas` is the key `replicas` under `deployment`.
* The schema declares no required key. Every key has a default, so the chart renders with no values file at all.
* An empty value means either that nothing is rendered, or that the chart uses a fallback. The description of each key says which.
* Helm checks the values against the schema before it renders them. A value of the wrong type fails the install.
* The chart fails the render for some combinations. They are listed in [Combinations that fail the render](#combinations-that-fail-the-render).
* The values of this page are the ones of the chart in the repository. To read the values of the release you install, run `helm show values oci://ghcr.io/onelitefeathernet/charts/ingot --version <version>`, because a release can change keys or defaults.
* The environment variables that the chart writes use the `INGOT_` names. Where a variable also has a `REPOSILITE_` name, the `REPOSILITE_` name is read as a fallback, and the `INGOT_` name takes precedence. See [Environment variables](environment-variables.md).

## Deploy mode and images

`mode` chooses the shape. In `allInOne`, the default, one Deployment serves the dashboard and the API. In `split`, the server runs with `INGOT_TARGET=server`, and a dashboard Deployment from the dashboard image sits in front of it. The dashboard keys are in [Dashboard workload](#dashboard-workload-split-mode).

Set `image.digest` to pin the server image. The chart then renders `name@digest` and ignores `image.tag`. The dashboard image has no digest key.

| Key | Type | Default | Description |
|---|---|---|---|
| `mode` | string, one of allInOne, split | `"allInOne"` | Deploy shape. `allInOne` runs one workload that serves the dashboard and the API. `split` runs the server and a separate dashboard workload in front of it. See [Run the dashboard split from the server with Helm](../how-to/run-split-with-helm.md). |
| `image.name` | string | `"ghcr.io/onelitefeathernet/ingot"` | Repository of the server image. |
| `image.tag` | string | `""` | Tag of the server image. Empty uses the chart `appVersion`. Ignored while `image.digest` is set. |
| `image.digest` | string | `""` | Digest of the server image, for example `sha256:...`. When set, the image is rendered as `name@digest` and `image.tag` is not used. |
| `image.pullPolicy` | string | `"IfNotPresent"` | Pull policy of the server container. |
| `nameOverride` | string | `""` | Replaces the chart name in resource names. Set it together with `fullnameOverride` to `reposilite` to take over an upstream Reposilite release, see [Migrate from the upstream Helm chart](../how-to/migrate-from-upstream-chart.md). |
| `fullnameOverride` | string | `""` | Replaces the full resource name. Set it together with `nameOverride` to take over an upstream release. |


## Server pods

With `autoscaling.enabled`, the autoscaler owns the replica count, and `deployment.replicas` is not rendered.

The server image starts as root, and its entrypoint switches to the service user. That path keeps `PUID`, `PGID` and the ownership fix of a volume working, so the security contexts are off by default. For a namespace that enforces the restricted Pod Security level, use `deployment.securityProfile` instead of the two `enabled` switches. See [Run under the restricted Pod Security level](../how-to/run-on-kubernetes.md#run-under-the-restricted-pod-security-level).

`service.port` is also the container port of the server. Keep `probes.port` equal to it.

| Key | Type | Default | Description |
|---|---|---|---|
| `deployment.replicas` | integer, min 0 | `1` | Replicas of the server. Not rendered while `autoscaling.enabled` is true, because the autoscaler sets the count. |
| `deployment.terminationGracePeriodSeconds` | integer | `60` | Seconds before Kubernetes sends SIGKILL after SIGTERM. A non-zero `deployment.shutdown` value replaces it with `preStopSeconds + drainSeconds + 10`. |
| `deployment.minReadySeconds` | integer | `0` | Seconds a new pod must be ready before the rollout counts it as available. |
| `deployment.annotations` | object | `{}` | Extra annotations on the server Deployment. |
| `deployment.labels` | object | `{}` | Extra labels on the server Deployment. |
| `deployment.podAnnotations` | object | `{}` | Extra annotations on the server pods. |
| `deployment.podLabels` | object | `{}` | Extra labels on the server pods. |
| `deployment.topologySpreadConstraints` | array | `[]` | Topology spread constraints of the server pods, passed through unchanged. Empty renders no field. |
| `deployment.initContainers` | array | see below | Init containers of the server pod, passed through unchanged. |
| `deployment.additionalContainers` | array | see below | Extra containers of the server pod, passed through unchanged. |
| `deployment.additionalVolumes` | array | see below | Extra volumes of the pod, for init containers, extra containers and the server. |
| `deployment.additionalVolumeMounts` | array | see below | Extra mounts of the server container. Each one refers to a volume in `additionalVolumes`. |
| `deployment.imagePullSecrets` | array | see below | Pull Secrets of the server pods. |
| `deployment.lifecycle` | object | `{}` | Lifecycle hooks of the server container, passed through unchanged. When it is set, `shutdown.preStopSeconds` renders no sleep, because your own `preStop` action is kept. |
| `deployment.shutdown.preStopSeconds` | integer | `0` | Seconds of a `preStop` sleep, so the pod stays in the endpoints while the proxy removes it. `0` renders no sleep. Ignored while `deployment.lifecycle` is set. |
| `deployment.shutdown.drainSeconds` | integer | `0` | Seconds Ingot waits for requests in flight, rendered as `INGOT_SHUTDOWN_TIMEOUT`. `0` renders nothing. Ignored while `env` sets `INGOT_SHUTDOWN_TIMEOUT`. |
| `deployment.podSecurityContext.enabled` | boolean | `false` | Renders the pod security context. Off by default, so the image starts as root and its entrypoint switches to the service user, which keeps `PUID`, `PGID` and the volume ownership fix working. |
| `deployment.podSecurityContext.fsGroup` | integer | `999` | Group that owns the mounted volumes. Used only when `enabled` is `true`. |
| `deployment.containerSecurityContext.enabled` | boolean | `false` | Renders the container security context. Off by default. Turn it on only for a volume that is already owned by the UID you set. |
| `deployment.containerSecurityContext.runAsUser` | integer | `999` | UID of the server container. Used only when `enabled` is `true`. |
| `deployment.containerSecurityContext.runAsGroup` | integer | `999` | GID of the server container. Used only when `enabled` is `true`. |
| `deployment.securityProfile` | string, one of empty, restricted, restricted-openshift | `""` | Pod Security profile of the server. `restricted` runs the pod as UID and GID `977` and mounts `emptyDir` volumes at `/tmp` and `/var/log/reposilite`. `restricted-openshift` sets the same fields without fixed IDs, for clusters that assign the UID. Do not combine a profile with either `enabled` switch above. See [Run on Kubernetes](../how-to/run-on-kubernetes.md#run-under-the-restricted-pod-security-level). |
| `service.type` | string | `"ClusterIP"` | Type of the server Service. |
| `service.port` | integer | `8080` | Port of the server Service. The server container listens on this port too, under the name `http`, so keep `probes.port` equal to it. |
| `service.annotations` | object | `{}` | Extra annotations on the server Service. |
| `resources.requests.cpu` | number | `1.0` | CPU request of the server container. Also the base of CPU utilization targets in `autoscaling`. |
| `resources.requests.memory` | string | `"128Mi"` | Memory request of the server container. |
| `resources.limits.cpu` | number | `2.0` | CPU limit of the server container. |
| `resources.limits.memory` | string | `"256Mi"` | Memory limit of the server container. The heap in `JAVA_OPTS` must fit inside it. |
| `env` | array | see below | Environment variables of the server container. A variable that a typed value also sets, such as `INGOT_LOCAL_DATABASE` with `database.existingSecret`, fails the render when it is set here too, and the message names both keys. The default sets `JAVA_OPTS` to `-Xmx64M`. |
| `envFrom` | array | see below | Sources of environment variables for the server container (`configMapRef` or `secretRef`), passed through unchanged. |
| `rollingUpdate.maxUnavailable` | integer | `0` | Server pods that may be unavailable during a rollout. `0` keeps the old pod until its replacement is ready. |
| `rollingUpdate.maxSurge` | integer | `1` | Server pods that may run above the replica count during a rollout. |
| `serviceAccount.create` | boolean | `true` | Creates the ServiceAccount that the server and the dashboard share. |
| `serviceAccount.annotations` | object | `{}` | Annotations on the ServiceAccount. |
| `serviceAccount.name` | string | `""` | Name of the ServiceAccount. Empty uses the generated name when `create` is `true`. |
| `serviceAccount.automountServiceAccountToken` | boolean | `false` | Sets `automountServiceAccountToken` on the ServiceAccount. Off by default. |
| `affinity` | object | `{}` | Pod affinity of the server pods, passed through unchanged. |
| `nodeSelector` | object | `{}` | Node selector of the server pods. |
| `tolerations` | array | `[]` | Tolerations of the server pods. |


## Probes

In `allInOne` mode the liveness and readiness probes send `GET probes.path`, which is `/` by default. In `split` mode the liveness probe of the server uses `probes.serverPath`, and the readiness probe uses `/api/status/ready` unless `probes.readinessPath` is set. The readiness of the split server takes its values from `probes.split.readiness`, which tolerates one slow answer, because the check runs a database query and a storage listing.

The startup probe is on in `split` mode and off in `allInOne` mode. `probes.startup.enabled` overrides that. Its budget is `periodSeconds * failureThreshold`, which is 150 seconds by default. Liveness does not run before the startup probe succeeds.

| Key | Type | Default | Description |
|---|---|---|---|
| `probes.port` | integer | `8080` | Container port that the probes call. Keep it equal to `service.port`. |
| `probes.scheme` | string | `"HTTP"` | Scheme of the probe requests. |
| `probes.path` | string | `"/"` | Path of the probes in `allInOne` mode. |
| `probes.serverPath` | string | `"/api/status/health"` | Path of the liveness probe of the server in `split` mode, `/api/status/health`. |
| `probes.readinessPath` | string | `""` | Path of the readiness probe. Empty uses `probes.path` in `allInOne` mode and `/api/status/ready` in `split` mode. Liveness is not affected. |
| `probes.readiness.initialDelaySeconds` | integer | `2` | Seconds after the container starts before the first probe. |
| `probes.readiness.periodSeconds` | integer | `10` | Seconds between two probes. |
| `probes.readiness.timeoutSeconds` | integer | `1` | Seconds before a probe counts as failed. |
| `probes.readiness.successThreshold` | integer | `1` | Successful probes in a row that mark the check as passing. |
| `probes.readiness.failureThreshold` | integer | `1` | Failed probes in a row before the check counts as failing. |
| `probes.split.readiness.initialDelaySeconds` | integer | `2` | Seconds after the container starts before the first probe. |
| `probes.split.readiness.periodSeconds` | integer | `10` | Seconds between two probes. |
| `probes.split.readiness.timeoutSeconds` | integer | `3` | Seconds before a probe counts as failed. |
| `probes.split.readiness.successThreshold` | integer | `1` | Successful probes in a row that mark the check as passing. |
| `probes.split.readiness.failureThreshold` | integer | `2` | Failed probes in a row before the check counts as failing. |
| `probes.liveness.initialDelaySeconds` | integer | `2` | Seconds after the container starts before the first probe. |
| `probes.liveness.periodSeconds` | integer | `10` | Seconds between two probes. |
| `probes.liveness.timeoutSeconds` | integer | `2` | Seconds before a probe counts as failed. |
| `probes.liveness.successThreshold` | integer | `1` | Successful probes in a row that mark the check as passing. |
| `probes.liveness.failureThreshold` | integer | `3` | Failed probes in a row before the check counts as failing. |
| `probes.startup.periodSeconds` | integer | `5` | Seconds between startup probes. The startup budget is `periodSeconds * failureThreshold`. |
| `probes.startup.timeoutSeconds` | integer | `2` | Seconds before a startup probe counts as failed. |
| `probes.startup.failureThreshold` | integer | `30` | Failed startup probes before the server is restarted. Liveness is held back until the startup probe succeeds. |


## Storage

`persistence.enabled` mounts a claim at `persistence.path`, which is `/app/data` by default. The claim is named after the release, and it carries `helm.sh/resource-policy: keep`, so `helm uninstall` keeps the data. `persistence.name` only names the volume in the pod.

With more than one replica, the access mode must let pods on several nodes mount the claim, or `persistence.existingClaim` must name a volume that every replica reaches. The install notes warn about a `ReadWriteOnce` claim with more than one replica. See [Running more than one replica](../how-to/run-on-kubernetes.md#running-more-than-one-replica).

| Key | Type | Default | Description |
|---|---|---|---|
| `persistence.enabled` | boolean | `true` | Mounts a PersistentVolumeClaim at `persistence.path`. `false` uses an `emptyDir`, which every replica keeps for itself. |
| `persistence.name` | string | `"data"` | Name of the data volume in the pod. |
| `persistence.size` | string | `"4Gi"` | Requested size of the claim. |
| `persistence.accessMode` | string | `"ReadWriteOnce"` | Access mode of the claim. Several replicas on different nodes need `ReadWriteMany` or an existing claim, see [Running more than one replica](../how-to/run-on-kubernetes.md#running-more-than-one-replica). |
| `persistence.annotations` | object | `{}` | Extra annotations on the claim. The chart always adds `helm.sh/resource-policy: keep`, so an uninstall keeps the data. |
| `persistence.path` | string | `"/app/data"` | Mount path of the data volume in the server container. |


## Scaling and availability

`autoscaling` renders a HorizontalPodAutoscaler. The chart uses `autoscaling/v2` when the cluster offers it, and an older version otherwise. See [Autoscale the server](../how-to/autoscale-the-server.md).

`podDisruptionBudget` renders a budget with `maxUnavailable: 1`, so a node drain can evict one pod at a time. See [Set a disruption budget](../how-to/set-a-disruption-budget.md).

| Key | Type | Default | Description |
|---|---|---|---|
| `autoscaling.enabled` | boolean | `false` | Renders a HorizontalPodAutoscaler for the server and removes `deployment.replicas` from the Deployment. See [Autoscale the server](../how-to/autoscale-the-server.md). |
| `autoscaling.minReplicas` | integer | `1` | Lower bound of the replica count. Not rendered when `0`. |
| `autoscaling.maxReplicas` | integer | `10` | Upper bound of the replica count. |
| `autoscaling.metrics` | array | see below | Metrics of the autoscaler, passed through unchanged. The default list targets 60% average CPU utilization and 60% average memory utilization. The install notes warn about the memory metric, see the autoscaling guide. |
| `autoscaling.behavior.scaleDown.stabilizationWindowSeconds` | integer | `300` | Seconds of recommendations that the autoscaler considers before it scales down. |
| `autoscaling.behavior.scaleDown.policies` | array | see below | Scale-down policies, passed through unchanged. |
| `podDisruptionBudget.enabled` | boolean | `false` | Renders a PodDisruptionBudget with `maxUnavailable: 1` for the server, and for the dashboard in `split` mode. See [Set a disruption budget](../how-to/set-a-disruption-budget.md). |


## Routing and network

Route the traffic with `ingress` or with `httpRoute`, not with both. In `split` mode, either form sends the dashboard paths to the dashboard Service and everything else to the server. The per-host `paths` of `ingress.hosts` apply in `allInOne` mode only.

`networkPolicy` is off by default. See [Restrict network traffic](../how-to/restrict-network-traffic.md) and [Route with a Gateway API HTTPRoute](../how-to/route-with-gateway-api.md).

| Key | Type | Default | Description |
|---|---|---|---|
| `networkPolicy.enabled` | boolean | `false` | Renders a NetworkPolicy for the server, and for the dashboard in `split` mode. Off by default. See [Restrict network traffic with a NetworkPolicy](../how-to/restrict-network-traffic.md). |
| `networkPolicy.ingress.fromNamespaces` | array | `[]` | Label maps that select the namespaces allowed to reach the Ingot port, for example `kubernetes.io/metadata.name: gateway-system`. An empty map is refused, because it would select every namespace. |
| `networkPolicy.ingress.fromPods` | array | `[]` | Label maps that select pods of the release namespace allowed to reach the Ingot port, for example `app.kubernetes.io/name: prometheus`. An empty map is refused. |
| `networkPolicy.egress.enabled` | boolean | `false` | Restricts the egress of the server, and of the dashboard in `split` mode, to DNS and the rules below. Off by default. |
| `networkPolicy.egress.allowInternet` | boolean | `true` | With egress restricted, allows public addresses on every port. `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` and `fc00::/7` stay excluded. |
| `networkPolicy.egress.extra` | array | see below | Extra egress rules, rendered unchanged on the server policy only. Use them for the database, the cluster bus or an in-cluster S3 endpoint. |
| `ingress.enabled` | boolean | `false` | Renders an Ingress for the server. Cannot be combined with `httpRoute.enabled`. |
| `ingress.className` | string | `""` | Ingress class, rendered as `ingressClassName`. Empty leaves the class to the cluster default. |
| `ingress.annotations` | object | `{}` | Annotations on the Ingress. |
| `ingress.hosts` | array | see below | Host rules of the Ingress. In `split` mode the Ingress routes the dashboard paths to the dashboard Service and everything else to the server, and the per-host `paths` are not used. |
| `ingress.tls` | array | see below | TLS entries of the Ingress, each with a `secretName` and the `hosts` it covers. Empty renders no TLS block. |
| `httpRoute.enabled` | boolean | `false` | Renders a Gateway API `HTTPRoute` for the server. Cannot be combined with `ingress.enabled`. See [Route with a Gateway API HTTPRoute](../how-to/route-with-gateway-api.md). |
| `httpRoute.parentRefs` | array | `[]` | Gateways that the route attaches to, passed through as `parentRefs`. |
| `httpRoute.hostnames` | array | `[]` | Host names that the route answers for. Empty matches every host. |


## Database, bus, settings and runtime

`database`, `objectStorage` and `clusterBus` refer to Secrets by name. The chart never renders their values. The variables that these keys set fail the render when `env` sets them too. See [Configure a database](../how-to/configure-database.md), [Use S3 storage](../how-to/use-s3-storage.md) and [Configure the cluster bus](../how-to/configure-cluster-bus.md).

`sharedConfiguration` mounts a settings file read-only, and turns on its reload. A change to `content` restarts the pods, while a change to a ConfigMap named by `existingConfigMap` does not. `jvm` and `opentelemetry` render nothing until a key is set.

| Key | Type | Default | Description |
|---|---|---|---|
| `database.existingSecret` | string | `""` | Name of a Secret in the release namespace. Renders `INGOT_LOCAL_DATABASE` from the key `secretKey`. Empty renders nothing. |
| `database.secretKey` | string | `"database"` | Key of the database Secret that holds the connection string. |
| `objectStorage.existingSecret` | string | `""` | Name of a Secret with `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, and the optional `AWS_REGION`. Empty repository keys fall back to the AWS default credential chain. |
| `clusterBus.url` | string | `""` | URL of the NATS broker. Renders `INGOT_CLUSTER_URL`. See [Configure the cluster bus](../how-to/configure-cluster-bus.md). |
| `clusterBus.passwordSecret` | string | `""` | Name of a Secret with the key `password`. Renders `INGOT_CLUSTER_PASSWORD` from it. |
| `clusterBus.credentialsSecret` | string | `""` | Name of a Secret with the key `nats.creds`. Mounted read-only, and `INGOT_CLUSTER_CREDENTIALS` points at the file. |
| `extraCaCerts.enabled` | boolean | `false` | Mounts the PEM bundle read-only and sets `INGOT_EXTRA_CA_CERTS`. Set exactly one source. The server image must read that variable. |
| `extraCaCerts.configMapName` | string | `""` | Name of a ConfigMap in the release namespace with PEM files. Set this or `secretName`, not both. |
| `extraCaCerts.secretName` | string | `""` | Name of a Secret in the release namespace with PEM files. Set this or `configMapName`, not both. |
| `jvm.maxRAMPercentage` | string or number | `""` | Renders `-XX:MaxRAMPercentage` in `JDK_JAVA_OPTIONS`. Empty renders nothing. An explicit `-Xmx` in `JAVA_OPTS` overrides it. |
| `jvm.initialRAMPercentage` | string or number | `""` | Renders `-XX:InitialRAMPercentage` in `JDK_JAVA_OPTIONS`. Empty renders nothing. |
| `jvm.gc` | string, empty or Use<Name>GC | `""` | Collector as a HotSpot flag name without the prefix, for example `UseG1GC`. Renders `-XX:+<name>`. Empty renders nothing. |
| `jvm.activeProcessorCount` | string or integer | `""` | Renders `-XX:ActiveProcessorCount`. Empty renders nothing. |
| `jvm.extraOptions` | array | `[]` | JVM options appended unchanged to `JDK_JAVA_OPTIONS`. |
| `opentelemetry.enabled` | boolean | `false` | Adds the OpenTelemetry Java agent to the server through an init container. The dashboard gets no agent. The server image must carry the agent at `/javaagent.jar` and a shell with `cp`. |
| `opentelemetry.agentImage` | string | `"ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:2.30.0"` | Image of the init container. Pinned by tag, which Renovate updates like the other images. |
| `opentelemetry.serviceName` | string | `""` | Sets `OTEL_SERVICE_NAME`. Empty uses the release fullname. |
| `opentelemetry.endpoint` | string | `""` | Sets `OTEL_EXPORTER_OTLP_ENDPOINT`. Empty leaves the agent default. |
| `opentelemetry.protocol` | string, one of grpc, http/protobuf | `"grpc"` | Sets `OTEL_EXPORTER_OTLP_PROTOCOL`. |
| `opentelemetry.tracesExporter` | string | `"otlp"` | Sets `OTEL_TRACES_EXPORTER`. |
| `opentelemetry.metricsExporter` | string | `"otlp"` | Sets `OTEL_METRICS_EXPORTER`. |
| `opentelemetry.logsExporter` | string | `"none"` | Sets `OTEL_LOGS_EXPORTER`. `none` keeps the logs of the agent off. |
| `opentelemetry.resources.requests.cpu` | string | `"10m"` | CPU request of the init container that copies the agent. |
| `opentelemetry.resources.requests.memory` | string | `"32Mi"` | Memory request of the init container that copies the agent. |
| `opentelemetry.resources.limits.cpu` | string | `"100m"` | CPU limit of the init container that copies the agent. |
| `opentelemetry.resources.limits.memory` | string | `"64Mi"` | Memory limit of the init container that copies the agent. |
| `sharedConfiguration.enabled` | boolean | `false` | Mounts the shared settings file read-only at `/app/shared-config` and turns on its reload. Set exactly one source. |
| `sharedConfiguration.content` | string | `""` | The shared settings JSON as text. Rendered as a ConfigMap owned by the release. A change restarts the pods. |
| `sharedConfiguration.existingConfigMap` | string | `""` | Name of a ConfigMap that you manage, with the key `configuration.shared.json`. The chart does not restart the pods when it changes, and the reload applies the change. |


## Monitoring

The three switches are off by default. The ServiceMonitor and the PrometheusRule need the Prometheus Operator CRDs. The alerts and the dashboards read the metrics of the Prometheus plugin, which the image ships behind the switch `INGOT_METRICS_ENABLED`. The server must run with that switch and with the scrape credentials. See [Monitor with the Prometheus Operator](../how-to/monitor-with-the-prometheus-operator.md).

| Key | Type | Default | Description |
|---|---|---|---|
| `serviceMonitor.enabled` | boolean | `false` | Renders a Prometheus Operator ServiceMonitor for the server. Needs the `monitoring.coreos.com` CRDs. See [Monitor with the Prometheus Operator](../how-to/monitor-with-the-prometheus-operator.md). |
| `serviceMonitor.path` | string | `"/metrics"` | Path that Prometheus scrapes on the server. |
| `serviceMonitor.interval` | string | `"30s"` | Scrape interval, as a Prometheus duration. |
| `serviceMonitor.labels` | object | `{}` | Extra labels on the ServiceMonitor, for example the label that your Prometheus instance selects its monitors with. |
| `serviceMonitor.basicAuth.enabled` | boolean | `false` | Sends basic auth credentials with the scrape. The metrics endpoint answers `401` without them. |
| `serviceMonitor.basicAuth.secretName` | string | `""` | Name of a Secret in the release namespace that holds both credentials. |
| `serviceMonitor.basicAuth.usernameKey` | string | `"username"` | Key of the user name in that Secret. |
| `serviceMonitor.basicAuth.passwordKey` | string | `"password"` | Key of the password in that Secret. |
| `prometheusRule.enabled` | boolean | `false` | Renders a PrometheusRule with six base alerts. Needs the `monitoring.coreos.com` CRDs and the metrics switch of the server. |
| `prometheusRule.labels` | object | `{}` | Extra labels on the PrometheusRule, for example the label that your Prometheus instance selects its rules with. |
| `grafanaDashboards.enabled` | boolean | `false` | Renders three ConfigMaps (`overview`, `mirroring`, `jvm`), each labelled `grafana_dashboard: "1"` for the Grafana sidecar. |


## Dashboard workload (split mode)

These keys render only in `split` mode. The dashboard is hardened by default: a non-root user with UID and GID `65532`, a read-only root filesystem, and every Linux capability dropped. Its probes call `/healthz` on `dashboard.service.port`. The dashboard reaches the server through the server Service on `service.port`, which the chart sets as `INGOT_BACKEND`.

| Key | Type | Default | Description |
|---|---|---|---|
| `dashboard.image.name` | string | `"ghcr.io/onelitefeathernet/ingot-dashboard"` | Repository of the dashboard image. Used in `split` mode only. |
| `dashboard.image.tag` | string | `""` | Tag of the dashboard image. Empty uses the chart `appVersion`. The dashboard has no digest key. |
| `dashboard.image.pullPolicy` | string | `"IfNotPresent"` | Pull policy of the dashboard container. |
| `dashboard.replicas` | integer, min 0 | `1` | Replicas of the dashboard. |
| `dashboard.terminationGracePeriodSeconds` | integer | `30` | Seconds before Kubernetes sends SIGKILL to a dashboard pod. |
| `dashboard.annotations` | object | `{}` | Extra annotations on the dashboard Deployment. |
| `dashboard.labels` | object | `{}` | Extra labels on the dashboard Deployment. |
| `dashboard.podAnnotations` | object | `{}` | Extra annotations on the dashboard pods. |
| `dashboard.podLabels` | object | `{}` | Extra labels on the dashboard pods. |
| `dashboard.imagePullSecrets` | array | `[]` | Pull Secrets of the dashboard pods. |
| `dashboard.env` | array | `[]` | Extra environment variables of the dashboard container. The chart sets `INGOT_BACKEND` itself. |
| `dashboard.podSecurityContext.enabled` | boolean | `true` | Renders the pod security context of the dashboard. On by default. |
| `dashboard.podSecurityContext.runAsNonRoot` | boolean | `true` | Refuses to start a dashboard pod as root. |
| `dashboard.podSecurityContext.runAsUser` | integer | `65532` | UID of the dashboard pods. |
| `dashboard.podSecurityContext.runAsGroup` | integer | `65532` | GID of the dashboard pods. |
| `dashboard.podSecurityContext.seccompProfile.type` | string | `"RuntimeDefault"` | Seccomp profile of the dashboard pods. |
| `dashboard.containerSecurityContext.enabled` | boolean | `true` | Renders the container security context of the dashboard. On by default. |
| `dashboard.containerSecurityContext.allowPrivilegeEscalation` | boolean | `false` | Whether the dashboard container may gain privileges over its parent process. |
| `dashboard.containerSecurityContext.readOnlyRootFilesystem` | boolean | `true` | Mounts the root filesystem of the dashboard container read-only. The image writes nothing at run time. |
| `dashboard.containerSecurityContext.runAsNonRoot` | boolean | `true` | Refuses to start the dashboard container as root. |
| `dashboard.containerSecurityContext.runAsUser` | integer | `65532` | UID of the dashboard container. |
| `dashboard.containerSecurityContext.runAsGroup` | integer | `65532` | GID of the dashboard container. |
| `dashboard.containerSecurityContext.capabilities.drop` | array | `["ALL"]` | Linux capabilities dropped from the dashboard container. `ALL` by default. |
| `dashboard.containerSecurityContext.seccompProfile.type` | string | `"RuntimeDefault"` | Seccomp profile of the dashboard container. |
| `dashboard.service.type` | string | `"ClusterIP"` | Type of the dashboard Service. The ingress and the HTTPRoute send the dashboard paths to it in `split` mode. |
| `dashboard.service.port` | integer | `8080` | Port of the dashboard Service, also the container port of the dashboard. |
| `dashboard.service.annotations` | object | `{}` | Extra annotations on the dashboard Service. |
| `dashboard.probes.scheme` | string | `"HTTP"` | Scheme of the dashboard probe requests. |
| `dashboard.probes.path` | string | `"/healthz"` | Path of the dashboard readiness and liveness probes, `/healthz`. |
| `dashboard.probes.readiness.initialDelaySeconds` | integer | `2` | Seconds after the container starts before the first probe. |
| `dashboard.probes.readiness.periodSeconds` | integer | `10` | Seconds between two probes. |
| `dashboard.probes.readiness.timeoutSeconds` | integer | `1` | Seconds before a probe counts as failed. |
| `dashboard.probes.readiness.successThreshold` | integer | `1` | Successful probes in a row that mark the check as passing. |
| `dashboard.probes.readiness.failureThreshold` | integer | `3` | Failed probes in a row before the check counts as failing. |
| `dashboard.probes.liveness.initialDelaySeconds` | integer | `2` | Seconds after the container starts before the first probe. |
| `dashboard.probes.liveness.periodSeconds` | integer | `10` | Seconds between two probes. |
| `dashboard.probes.liveness.timeoutSeconds` | integer | `2` | Seconds before a probe counts as failed. |
| `dashboard.probes.liveness.successThreshold` | integer | `1` | Successful probes in a row that mark the check as passing. |
| `dashboard.probes.liveness.failureThreshold` | integer | `3` | Failed probes in a row before the check counts as failing. |
| `dashboard.resources.requests.cpu` | string | `"50m"` | CPU request of the dashboard container. |
| `dashboard.resources.requests.memory` | string | `"64Mi"` | Memory request of the dashboard container. |
| `dashboard.resources.limits.cpu` | string | `"500m"` | CPU limit of the dashboard container. |
| `dashboard.resources.limits.memory` | string | `"128Mi"` | Memory limit of the dashboard container. |
| `dashboard.rollingUpdate.maxUnavailable` | integer | `0` | Dashboard pods that may be unavailable during a rollout. |
| `dashboard.rollingUpdate.maxSurge` | integer | `1` | Dashboard pods that may run above the replica count during a rollout. |
| `dashboard.affinity` | object | `{}` | Pod affinity of the dashboard pods. |
| `dashboard.nodeSelector` | object | `{}` | Node selector of the dashboard pods. |
| `dashboard.tolerations` | array | `[]` | Tolerations of the dashboard pods. |
| `dashboard.topologySpreadConstraints` | array | `[]` | Topology spread constraints of the dashboard pods, passed through unchanged. Empty renders no field. |


## Defaults that are lists or maps

These keys have a structured default. Copy the block into your values file to change one element of it.

`env`, the default environment of the server:

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xmx64M"
```

`autoscaling.metrics`, the metrics of the autoscaler:

```yaml
autoscaling:
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 60
```

`autoscaling.behavior`, the scale-down behaviour:

```yaml
autoscaling:
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
```

`ingress.hosts`, the host rules of the ingress:

```yaml
ingress:
  hosts:
    - host: reposilite.local
      paths:
        - path: /
          pathType: Prefix
```

## Keys that are not in values.yaml

The chart reads these keys, but `values.yaml` has no entry for them. Set them only when you need them.

| Key | Type | Effect |
|---|---|---|
| `persistence.storageClass` | string | Storage class of the claim. Rendered as `storageClassName` when set. |
| `persistence.existingClaim` | string | Name of a claim that already exists. The server mounts it, and the chart creates no claim. |
| `persistence.subPath` | string | Mounts only a subdirectory of the data volume at `persistence.path`. |
| `probes.startup.enabled` | boolean | Turns the startup probe on or off. Unset means on in `split` mode and off in `allInOne` mode. See [Probes](#probes). |

## Combinations that fail the render

Helm stops with an error, and the message names the keys to change, when:

* `ingress.enabled` and `httpRoute.enabled` are both `true`.
* `deployment.securityProfile` is set and `deployment.podSecurityContext.enabled` or `deployment.containerSecurityContext.enabled` is `true`.
* `networkPolicy.ingress.fromNamespaces` or `fromPods` contains an empty map.
* `extraCaCerts.enabled` is `true` without exactly one of `configMapName` and `secretName`.
* `sharedConfiguration.enabled` is `true` without exactly one of `content` and `existingConfigMap`.
* `env` sets a variable that a typed value of the chart also sets, for example `INGOT_LOCAL_DATABASE` next to `database.existingSecret`, or `INGOT_CLUSTER_URL` next to `clusterBus.url`. The message names both keys. Remove the variable from `env`, or unset the typed value.
* `env` sets a JVM option in `JDK_JAVA_OPTIONS` that a `jvm` key also renders.

The NOTES printed after an install warn about some settings without failing. For example, they warn when more than one replica runs with a `ReadWriteOnce` claim, or when `env` does not set `INGOT_CLUSTER_URL`. The NOTES read only `env`, so they can warn about a variable that a typed value such as `clusterBus.url` renders. See [Set Helm values for high availability](../how-to/set-helm-values-for-high-availability.md).

## Related

* [Run on Kubernetes](../how-to/run-on-kubernetes.md) installs the chart step by step.
* [Migrate from the upstream Helm chart](../how-to/migrate-from-upstream-chart.md) explains the two name overrides and the upstream keys.
* [Environment variables](environment-variables.md) lists the variables of the server, and the names that the chart writes.

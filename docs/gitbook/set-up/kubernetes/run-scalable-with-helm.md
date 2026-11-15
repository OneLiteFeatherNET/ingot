---
description: Run Ingot in scalable mode with the Ingot Helm chart, with read pods, write pods and one worker, the routing by HTTP method, the values it needs and the limit of a plain Ingress.
---

# Run the scalable mode with Helm

The scalable mode splits the Ingot server into three workloads built from the server image, plus the dashboard. Read pods serve the `GET` and `HEAD` requests, write pods serve every other method, and one worker pod runs the scheduled jobs, such as cleanup and the audit purge. The read and write pods do not run those jobs, so a job runs once per cluster, not once per replica. Each workload scales on its own.

The mode needs a database and object storage that every pod can reach, and a NATS cluster bus, because a write or a cleanup on one pod must reach the caches of the others. The chart refuses to render the mode without them. For a single replica, or a cluster without shared state, use the [split mode](run-split-with-helm.md) or the default mode instead.

## Before you begin

* Helm 3.8 or later, which installs charts from an OCI registry.
* A database that all pods share. See [Configure a database](../cluster/configure-database.md).
* S3 compatible object storage that all pods share, because a pod cannot share a local volume. See [Use S3 storage](../cluster/use-s3-storage.md).
* A NATS broker that all pods reach. See [Configure the cluster bus](../cluster/configure-cluster-bus.md).
* A Gateway API implementation for method routing, or an ingress controller that accepts the limit described below.

## Write the values

Create the Secrets in the release namespace first. The chart reads them by name and never renders their values.

Create `ingot-scalable-values.yaml`:

```yaml
mode: scalable

database:
  existingSecret: ingot-database
  secretKey: database

objectStorage:
  existingSecret: ingot-object-storage

clusterBus:
  url: nats://nats.ingot.svc.cluster.local:4222

persistence:
  enabled: false

httpRoute:
  enabled: true
  parentRefs:
    - name: public-gateway
      namespace: gateway-system

scalable:
  read:
    replicas: 2
    autoscaling:
      enabled: true
      minReplicas: 2
      maxReplicas: 6
  write:
    replicas: 2
  worker:
    replicas: 1
```

The top-level keys of the chart, such as `image`, `env`, `probes` and `jvm`, apply to every workload. The `scalable` block sets the replicas, the resources and the budget of each workload. A workload with empty `resources` takes the top-level `resources`.

The top-level `autoscaling` and the top-level `podDisruptionBudget` are refused in this mode. Set `scalable.read.autoscaling` for the read pods, and `scalable.<role>.podDisruptionBudget` for each workload instead.

## The values the chart requires

The chart stops the render with one of these messages when a value is missing or set against the mode. Each message names the value to set:

* `scalable mode needs database.existingSecret: pods share one database`
* `scalable mode needs objectStorage.existingSecret: pods cannot share a local volume`
* `scalable mode needs clusterBus.url: a write or a cleanup on one pod has to reach the caches of the others`
* `scalable mode does not use persistence: unset persistence.enabled`
* `scalable mode ignores the top-level autoscaling: set scalable.read.autoscaling instead`
* `scalable mode ignores the top-level podDisruptionBudget: set scalable.<role>.podDisruptionBudget for the read, write or worker workload instead`
* `ingress.enabled and httpRoute.enabled cannot both be true: enable one routing form, either ingress or httpRoute`

The data volume is not mounted in this mode, because a claim cannot be shared by pods on several nodes. The temporary directory of each pod is an `emptyDir`.

## Install the chart

```bash
helm upgrade --install ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  --create-namespace \
  -f ingot-scalable-values.yaml
```

Replace `1.1.1` with the release you deploy.

## What the chart renders

| Workload | Name | Set by the chart |
|----------|------|------------------|
| Read Deployment and Service | `ingot-read` | `INGOT_TARGET=server`, `INGOT_BACKGROUND_JOBS=false`, default 2 replicas |
| Write Deployment and Service | `ingot-write` | `INGOT_TARGET=server`, `INGOT_BACKGROUND_JOBS=false`, default 2 replicas |
| Worker Deployment | `ingot-worker` | `INGOT_TARGET=worker`, one replica, no HPA, no route |
| Combined Service | `ingot` | Selects the read and the write pods together, so the `service`, `serviceMonitor` and NetworkPolicy values keep working |
| Dashboard Deployment and Service | `ingot-dashboard` | As in the [split mode](run-split-with-helm.md#what-the-chart-renders) |

The pod selectors of the four Deployments are disjoint. The worker has no HPA and no route.

## Routing with the Gateway API

With `httpRoute.enabled`, the rules are checked in this order, and the first match wins:

| Order | Matches | Goes to |
|-------|---------|---------|
| 1 | The dashboard paths, as in the [split mode](run-split-with-helm.md#routing) | The dashboard |
| 2 | `PathPrefix /` with method `GET` | `ingot-read` |
| 3 | `PathPrefix /` with method `HEAD` | `ingot-read` |
| 4 | `PathPrefix /` without a method | `ingot-write` |

Rule 4 catches `PUT`, `POST`, `DELETE`, `PATCH`, `OPTIONS` and every other method. The admin API `GET` requests, such as settings, tokens and status pages, and the console event stream therefore reach the read pods. Those requests only read, and the read caches are fed by the cluster bus. Login, deploys and every change reach the write pods.

## Routing with an Ingress, and its limit

A plain Ingress cannot match an HTTP method. With `ingress.enabled`, the chart sends every non-dashboard path to the Service `ingot`, which selects both the read and the write pods. Read and write traffic then share all the pods evenly. The split gives you independent sizing, but no isolation between reads and writes.

The install notes print this limit:

> NOTE: scalable mode with ingress.enabled sends all non-dashboard traffic to the Service ingot, which selects
> the read and the write pods. A plain Ingress cannot match an HTTP method, so read and write traffic share the pods and the split
> gives sizing but no isolation. httpRoute routes by method (GET and HEAD to the read pods, every other method to the write pods)
> and needs a Gateway API controller. This note does not stop the install.

The chart renders no controller specific annotations, so method routing needs one of these:

* **Gateway API.** Use `httpRoute` as above. This is the portable option.
* **ingress-nginx.** Its method routing is not part of the standard Ingress. The chart does not render the `configuration-snippet` annotation that some setups use for it. Current ingress-nginx releases disable that annotation by default, and enabling it is a security risk, so this chart does not rely on it.
* **Traefik.** Traefik can match on the method, but through its own `IngressRoute` resource, not through a standard Ingress. Traefik also serves the Gateway API. Use `httpRoute` with a Traefik Gateway, or render your own `IngressRoute` that sends `GET` and `HEAD` to the `ingot-read` Service and the other methods to `ingot-write`.

The chart still renders the `ingot-read` and `ingot-write` Services, so an ingress controller's own routing can target them directly.

## Scaling

* Scale the read pods with `scalable.read.replicas`, or with `scalable.read.autoscaling`. Reads are usually the larger share of the traffic, so the autoscaler belongs on this workload. The chart renders no autoscaler for the write pods, so scale them by hand.
* Keep at least two write pods, so that writes survive the loss of one pod.
* Keep one worker pod. It is the only pod that runs the scheduled jobs. A worker that is down means no scheduled cleanup until it returns, and the chart runs it with a liveness probe.
* Set `podDisruptionBudget.enabled` on `scalable.read` and `scalable.write` when they run with several replicas, so a node drain evicts one pod at a time.
* Every pod uses the same database and the same object storage. Size the database connections and the bucket requests for the total replica count, not for one pod.

## Check the deployment

Wait for the workloads:

```bash
kubectl -n ingot rollout status deployment/ingot-read
kubectl -n ingot rollout status deployment/ingot-write
kubectl -n ingot rollout status deployment/ingot-worker
kubectl -n ingot rollout status deployment/ingot-dashboard
```

Check the health answer through the route, with the method you want to test:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://ingot.example.org/api/status/health
curl -sS -o /dev/null -w '%{http_code}\n' -I https://ingot.example.org/api/status/health
```

Both answer `200`. Check the worker with its log:

```bash
kubectl -n ingot logs deployment/ingot-worker --tail=50
```

## Back to another mode

To return to the split or default mode, set `mode` back and remove the `scalable` block. Check the rendered changes first with `helm diff upgrade` if you have the plugin installed.

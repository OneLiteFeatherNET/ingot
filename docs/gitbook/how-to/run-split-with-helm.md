---
description: Run the Ingot server without its dashboard and the dashboard in its own hardened workload, with the Ingot Helm chart in split mode, its routing and the commands that show each part works.
---

# Run the dashboard split from the server with Helm

The split deployment runs the Ingot server without its dashboard, and the dashboard in a second workload built from the dashboard image. An ingress sends the dashboard paths to the dashboard and every other path to the server, so the browser sees one address. The [separate container](run-split-dashboard-container.md) page describes the same split with Docker Compose. This page does it with the Ingot Helm chart, where the split is one value.

## Before you begin

* Helm 3.8 or later, which installs charts from an OCI registry.
* A cluster with an ingress controller, or with a Gateway API implementation if you use an `HTTPRoute`.
* A database for anything beyond one replica. See [Run on Kubernetes](run-on-kubernetes.md).
* If the chart package on `ghcr.io` is private, log in first with `helm registry login ghcr.io`.

## Write the values

Create `ingot-split-values.yaml`:

```yaml
mode: split

env:
  - name: JAVA_OPTS
    value: "-Xmx512M"

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: ingot.example.org
```

The image tags default to the release of the chart, so the server and the dashboard run the same version. Set `image.tag` and `dashboard.image.tag` only to pin them apart. The `paths` of an ingress host are not used in split mode, because the chart generates the paths from the dashboard segment list.

## Install the chart

```bash
helm upgrade --install ingot oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace ingot \
  --create-namespace \
  -f ingot-split-values.yaml
```

Replace `1.1.1` with the release you deploy. The chart version is the release version.

## What the chart renders

| Workload | Name | Image | Set by the chart |
|----------|------|-------|------------------|
| Server | Deployment and Service `ingot` | `ghcr.io/onelitefeathernet/ingot` | `INGOT_TARGET=server`, probes on `GET /api/status/health` |
| Dashboard | Deployment and Service `ingot-dashboard` | `ghcr.io/onelitefeathernet/ingot-dashboard` | `INGOT_BACKEND=http://ingot:8080`, probes on `GET /healthz` |

The server keeps its own Service and selector, so an existing release keeps its names. The dashboard has its own selector, so the server Service never sends traffic to a dashboard pod.

## Routing

| Path | Goes to |
|------|---------|
| `/`, matched exactly | The dashboard |
| `/assets`, `/favicon.png` | The dashboard |
| `/browse`, `/search`, `/dashboard`, `/analytics`, `/tokens`, `/console`, `/maintenance`, `/audit`, `/settings`, `/access`, `/account`, and everything below them | The dashboard, so that a reload of a dashboard address works |
| Everything else, such as `/api/...` and repository paths | The server |

The list of dashboard paths comes from the chart's copy of the segment list, which is checked against the source in the repository on every change. A repository whose id is one of these words cannot be downloaded through its top-level path. Its contents stay reachable in the dashboard under `/browse/<id>`, which is the same limit as the separate container.

To use the Gateway API instead of an ingress, set `httpRoute.enabled: true` and `httpRoute.parentRefs` in the values, and leave `ingress.enabled` at `false`. The chart rejects both routing forms at once.

## Check the deployment

Wait for both workloads:

```bash
kubectl -n ingot rollout status deployment/ingot
kubectl -n ingot rollout status deployment/ingot-dashboard
```

Check the dashboard and the routing through a port forward of the dashboard Service:

```bash
kubectl -n ingot port-forward svc/ingot-dashboard 8080:8080
```

In a second shell:

```bash
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' http://localhost:8080/
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' http://localhost:8080/analytics
curl -fsS http://localhost:8080/api/status/health
```

The first two answer `200` with `text/html`, and the health answer comes from the server through the dashboard. Check the server on its own with a second port forward:

```bash
kubectl -n ingot port-forward svc/ingot 8081:8080
```

```bash
curl -fsS http://localhost:8081/api/status/health
curl -sS http://localhost:8081/browse | head -c 200
```

The health endpoint answers `200`. The `/browse` request must not return the dashboard document, because the server runs without its dashboard.

## Hardening

The dashboard workload is hardened by default. It runs as UID `65532`, with a read-only root filesystem, all Linux capabilities dropped, no privilege escalation and the `RuntimeDefault` seccomp profile. It needs no writable volume.

The server keeps the defaults of the chart, which set no security context. It starts as root and its entrypoint switches to the service user, so `PUID`, `PGID` and the ownership fix of an existing volume keep working. Set `deployment.containerSecurityContext.enabled: true` with `runAsUser` and `runAsGroup` only for a volume that this user already owns.

## Replicas

Set `dashboard.replicas` for the dashboard. The server has the same limits as in [Run on Kubernetes](run-on-kubernetes.md#running-more-than-one-replica), so keep `deployment.replicas` at `1` unless those requirements hold.

## Move an existing release to split

An all-in-one release can move to split with an upgrade. The server Deployment and Service keep their selectors, so Helm updates them in place. The server pods restart once, because the chart version label and the `INGOT_TARGET` variable change. Run `helm diff upgrade` first if you have the plugin installed, and check that the dashboard workload is the only new resource.

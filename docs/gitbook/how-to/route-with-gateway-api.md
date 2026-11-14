---
description: Route the traffic to Ingot with the Gateway API HTTPRoute that the Helm chart renders, in allInOne and split mode, attach it to a Gateway you run, check that it is accepted, and learn its limits.
---

# Route with a Gateway API HTTPRoute

The chart can render a Gateway API `HTTPRoute` instead of an ingress. The route attaches to a Gateway that you already run, and it sends the requests of the host names you list to the Ingot Service. This page sets the route up. For the certificate on the Gateway listener, see [Terminate TLS at the gateway](terminate-tls-at-the-gateway.md).

## Before you begin

* The Gateway API CRDs in the cluster, with the `gateway.networking.k8s.io/v1` version of `HTTPRoute`. The chart does not check for them before it renders the route.
* A Gateway with a listener for your host name, and permission to attach routes from the release namespace. The Gateway's `allowedRoutes` decides which namespaces can attach.
* A DNS record for the host name that points to the Gateway address.

## Turn on the route

Name the Gateway in `parentRefs` and the host name in `hostnames`:

```yaml
httpRoute:
  enabled: true
  parentRefs:
    - name: public-gateway
      namespace: gateway-system
  hostnames:
    - ingot.example.org
```

Apply the change with the same chart version as before. The chart rejects a render that enables both `httpRoute` and `ingress`, so disable the ingress when you turn the route on. `hostnames` may stay empty, which matches every host name. Use the host name in production.

## What the chart renders

The route contains the `parentRefs` and `hostnames` that you set, and rules that depend on the deploy mode.

In `allInOne` mode, one rule sends every path to the server Service on `service.port`.

In `split` mode, the first rule sends the dashboard paths to the dashboard Service on `dashboard.service.port`:

* the exact path `/`,
* the prefix `/assets`,
* the exact path `/favicon.png`,
* a prefix for each dashboard segment, such as `/analytics`.

A second rule sends every other path, as a prefix of `/`, to the server Service. The chart keeps the list of dashboard segments in the chart files, so you do not write it yourself.

The route has no timeout, no header rule and no redirect. Keep those in a resource that the chart does not own. The next section explains why.

## Check the route

```bash
kubectl -n ingot get httproute ingot -o jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'
curl -fsS https://ingot.example.org/api/status/health
```

The first command prints `True` when the Gateway accepts the route. The second prints `{"status":"UP"}`. If the first one prints nothing or `False`, read the conditions of the route with `kubectl -n ingot describe httproute ingot`. The usual causes are a wrong `parentRefs` entry or a listener that does not allow the namespace.

## Changes and upgrades

`helm upgrade` replaces the rendered route with the one the chart renders. A timeout that you add to the route by hand is therefore lost on the next upgrade. For uploads that need a longer timeout or a larger body, see [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md).

## Related

* [Terminate TLS at the gateway](terminate-tls-at-the-gateway.md) creates the certificate and the HTTPS listener.
* [Run on Kubernetes](run-on-kubernetes.md#expose-the-instance-with-ingress) describes the ingress that you use instead of the route.
* [Run behind a reverse proxy](run-behind-reverse-proxy.md) explains the client address that Ingot sees behind a proxy.
* [Helm chart values](../reference/helm-values.md#routing-and-network) lists the keys of the route.

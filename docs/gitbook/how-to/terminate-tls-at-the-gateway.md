---
description: Serve HTTPS for Ingot on Kubernetes with a cert-manager certificate on a Gateway listener, attach the chart's HTTPRoute to it, or use an ingress instead, and why the in-app TLS connector is not needed there.
---

# Terminate TLS at the gateway

In a Kubernetes cluster the gateway or the ingress controller usually serves HTTPS. cert-manager issues and renews the certificate, and Ingot serves plain HTTP on port `8080` behind it. This setup does not need the TLS connector of Ingot itself, so leave `sslEnabled` at its default `false`.

Use this page with [Run on Kubernetes](run-on-kubernetes.md). The chart's Gateway API route, `httpRoute`, attaches to a Gateway that you already run.

## Before you begin

* A Gateway API implementation with a `GatewayClass` for your cluster, and a `Gateway` that you may attach routes to.
* cert-manager with an `Issuer` or a `ClusterIssuer` that can issue for your host name.
* A DNS record for the host name that points to the Gateway address.

## Steps

1. Create a cert-manager `Certificate`. cert-manager writes the certificate and its key to the Secret `ingot-tls` in the namespace of the Gateway:

   ```yaml
   apiVersion: cert-manager.io/v1
   kind: Certificate
   metadata:
     name: ingot-tls
     namespace: gateway-system
   spec:
     secretName: ingot-tls
     dnsNames:
       - ingot.example.org
     issuerRef:
       name: <your-issuer>
       kind: ClusterIssuer
   ```

   The Secret must be in the namespace of the Gateway, because a listener reads its certificate from there. The Gateway API allows a reference to a Secret in another namespace only with a `ReferenceGrant`.

2. Add an HTTPS listener to the Gateway that references the Secret:

   ```yaml
   apiVersion: gateway.networking.k8s.io/v1
   kind: Gateway
   metadata:
     name: public-gateway
     namespace: gateway-system
   spec:
     gatewayClassName: <your-gateway-class>
     listeners:
       - name: https
         protocol: HTTPS
         port: 443
         hostname: ingot.example.org
         tls:
           mode: Terminate
           certificateRefs:
             - kind: Secret
               name: ingot-tls
         allowedRoutes:
           namespaces:
             from: All
   ```

   `allowedRoutes` lets routes from any namespace attach. To limit attachment, select the namespace of the release by its name label instead.

3. Turn on the route of the chart and name the Gateway in `parentRefs`. The chart renders an `HTTPRoute` that sends every path to the Ingot Service on `service.port`:

   ```yaml
   httpRoute:
     enabled: true
     parentRefs:
       - name: public-gateway
         namespace: gateway-system
     hostnames:
       - ingot.example.org
   ```

   Apply the change with `helm upgrade --install` and the same `--version` and values file as before. The chart rejects a render that enables both `httpRoute` and `ingress`, so use one of the two.

4. Check that the route is accepted and that the certificate is served:

   ```bash
   kubectl -n ingot get httproute ingot -o jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'
   curl -fsS https://ingot.example.org/api/status/health
   ```

   The first command prints `True`. The second prints `{"status":"UP"}`.

The HTTPRoute of the chart has no timeout field. For uploads that run for a long time, see [Configure the edge for large uploads](configure-the-edge-for-large-uploads.md).

## Ingress instead of a Gateway

An ingress controller can serve the same host name with the same certificate. Set `ingress.enabled: true`, your `ingress.className`, and a `tls` entry that names the Secret of cert-manager. The ingress setup is described in [Run on Kubernetes](run-on-kubernetes.md#expose-the-instance-with-ingress). Do not set `httpRoute.enabled` at the same time.

## Client addresses

The gateway or the ingress is the proxy in front of Ingot. Ingot then sees the address of the proxy, not the client, unless you list the proxies in `INGOT_TRUSTED_PROXIES`. See [Run behind a reverse proxy](run-behind-reverse-proxy.md) and the [environment variables](../reference/environment-variables.md) reference.

## In-app TLS

Ingot can also serve HTTPS itself with the `sslEnabled` and `keyPath` settings. Only use it when no gateway or ingress is in front of Ingot. See [Enable TLS](enable-tls.md).

The `keyPath` value has to end in `.pem` or `.jks`. A PEM pair is written as a certificate file and a key file separated by a space, and the whole value must end in `.pem`. The check in Ingot accepts only those two extensions. cert-manager writes its files as `tls.crt` and `tls.key`, and Ingot rejects them with `Provided key extension is not supported.` This is a known limit: until the change `fix-tls-key-file-names` is merged, do not mount cert-manager's files into Ingot for in-app TLS. Serve TLS from the gateway instead, as described above.

## Related

* [Run on Kubernetes](run-on-kubernetes.md)
* [Run behind a reverse proxy](run-behind-reverse-proxy.md)
* [Enable TLS](enable-tls.md)

---
description: Set the request body size and the read and send timeouts of an ingress controller or Gateway API implementation so that large artifact uploads reach Ingot, and see which of these values the Ingot chart cannot set.
---

# Configure the edge for large uploads

A Maven or Gradle deploy sends the whole artifact in one request. A proxy in front of Ingot has to allow the largest artifact you deploy, and it has to keep the connection open for as long as the upload takes. Both limits belong to the edge, which is the ingress controller or the Gateway implementation, and not to the Ingot chart.

## What the edge must allow

| Limit | What to allow | Symptom when it is too small |
|:------|:--------------|:-----------------------------|
| Request body size | At least the size of the largest artifact you deploy | The upload fails with `413` before it reaches Ingot |
| Read timeout | Longer than the longest upload takes to send | The proxy closes the connection while the transfer is still running |
| Send timeout | Longer than the longest download takes to send | A large download stops in the middle |

Ingot sets its own asynchronous request timeout to 10 minutes, so an upload that runs longer than that is not a case the edge settings alone can cover.

## nginx ingress controller

Set the annotations on the ingress that the chart renders. The chart passes the `ingress.annotations` map through unchanged:

```yaml
ingress:
  enabled: true
  className: nginx
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "1024m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
  hosts:
    - host: ingot.example.org
      paths:
        - path: /
          pathType: Prefix
```

* `proxy-body-size` sets the largest request body. Its value must be at least the largest artifact. A value without a unit is read as bytes, so use a suffix such as `1024m`.
* `proxy-read-timeout` and `proxy-send-timeout` are in seconds. They apply to each read and write on the connection, not to the whole transfer.

A `413` from a plain nginx reverse proxy has the same cause and the same fix, `client_max_body_size` in place of the annotation. See the 413 entry in [Run behind a reverse proxy](run-behind-reverse-proxy.md).

## Gateway API

The Gateway API `HTTPRoute` has a `timeouts` field on its rules. It is an extended-support feature, and the implementation decides whether it honours the field. The Ingot chart renders no timeout field on `httpRoute`, so the route keeps the implementation's default.

Set the timeouts with the policy resource of your implementation, and check its documentation for the resource and field names.

{% hint style="warning" %}
`helm upgrade` replaces the rendered `HTTPRoute` with the one the chart renders, so a timeout added by hand to the route is lost on the next upgrade. Keep such a setting in a resource that the chart does not own.
{% endhint %}

## Limit of the Ingot server itself

If the edge allows the body and the server still answers `413`, the limit is in Ingot. Its request body limit is the Javalin default unless you set the system property `javalin.maxRequestSize` to a value in bytes. Set it in `JAVA_OPTS`:

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xmx768M -Djavalin.maxRequestSize=1073741824"
```

The value is an example of 1 GiB. Set it to at least the size of the largest artifact. See the [environment variables](../reference/environment-variables.md) reference for the setting.

## Check the limits

Upload an artifact close to your largest size through the edge, and read the status:

```bash
curl -u "<token-name>:<token-secret>" -T large-artifact.jar \
  -o /dev/null -w '%{http_code}\n' \
  https://ingot.example.org/releases/com/example/large/1.0/large-1.0.jar
```

A `413` means that a body limit of the edge or of Ingot is too small. A `502` or `504` that appears only for large files points to a timeout of the edge.

## Related

* [Run on Kubernetes](run-on-kubernetes.md)
* [Terminate TLS at the gateway](terminate-tls-at-the-gateway.md)
* [Run behind a reverse proxy](run-behind-reverse-proxy.md)
* [Use S3 storage](use-s3-storage.md) for the memory that one large upload holds

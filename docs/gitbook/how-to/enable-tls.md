---
description: Serve HTTPS from Ingot itself with a PEM certificate or a JKS keystore, choose the TLS port, redirect HTTP to HTTPS, or terminate TLS at a proxy instead.
---

# Enable TLS

Ingot can serve HTTPS on its own. Most deployments terminate TLS at a proxy instead, which also handles certificate renewal. If you use a proxy, follow [Run behind a reverse proxy](run-behind-reverse-proxy.md) and skip this page.

With TLS enabled, Ingot listens on two ports: the HTTP port `port`, and the HTTPS port `sslPort`.

## Before you begin

* A certificate and its key as PEM files, or a JKS keystore that holds both.
* The files readable by the user that runs Ingot.
* A TLS port that user can bind. Ports below 1024 need a capability that a non-root user does not have. The compose file of the container image drops all capabilities and runs as `977`, so use a port such as `8443` there and publish it under the port you want.

## Steps

1. Put the certificate and the key in the working directory, for example `/opt/ingot/cert.pem` and `/opt/ingot/key.pem`. `${WORKING_DIRECTORY}` in a key path stands for the working directory.

2. Edit `configuration.cdn` in the working directory:

   ```yaml
   sslEnabled: true
   sslPort: 8443
   keyPath: ${WORKING_DIRECTORY}/cert.pem ${WORKING_DIRECTORY}/key.pem
   keyPassword:
   enforceSsl: false
   ```

   Ingot reads the file on each start, so edit it while Ingot is stopped. Use the environment variables `INGOT_LOCAL_SSLENABLED`, `INGOT_LOCAL_SSLPORT`, `INGOT_LOCAL_KEYPATH`, `INGOT_LOCAL_KEYPASSWORD` and `INGOT_LOCAL_ENFORCESSL` in a container, to keep secrets out of the file.

3. For a JKS keystore, set `keyPath` to the keystore and `keyPassword` to its password:

   ```yaml
   keyPath: ${WORKING_DIRECTORY}/keystore.jks
   keyPassword: <keystore-password>
   ```

4. Start Ingot and read the log. It prints `Enabling SSL connector at ::<sslPort>` when TLS starts.

5. Optional: set `enforceSsl: true` to redirect HTTP requests to the HTTPS port.

## Settings

| Key | Default | What it does |
|-----|---------|--------------|
| `sslEnabled` | `false` | Starts the HTTPS connector on `sslPort`. |
| `sslPort` | `443` | Port of the HTTPS connector. |
| `keyPath` | `${WORKING_DIRECTORY}/cert.pem ${WORKING_DIRECTORY}/key.pem` | A certificate and a key file separated by a space, for PEM. A single `.jks` file for a keystore. Any other extension stops the start with an error. |
| `keyPassword` | empty | Password of the key, or of the keystore. |
| `enforceSsl` | `false` | Redirects HTTP requests to HTTPS on `sslPort`. |

The HTTP connector on `port` stays open when TLS is enabled.

{% hint style="warning" %}
`enforceSsl` redirects every HTTP request, including requests from `localhost`. A health check that uses plain HTTP against the HTTP port then gets a redirect, not `200 OK`. The health check of the container image uses `curl -sf`, which does not follow redirects. Check the health status after you enable this setting.
{% endhint %}

## Verify

* The HTTPS endpoint answers. Replace the host and port with yours:

  ```bash
  curl -sk -o /dev/null -w '%{http_code}\n' https://localhost:8443/api/status/health
  ```

  The output is `200`. The `-k` flag skips the certificate check, which is only useful for a self-signed test certificate. Without it, `curl` checks the certificate chain against the system store.

* With `enforceSsl: true`, `curl -sI http://localhost:8080/` returns a redirect with a `Location` header that starts with `https://`.

## Troubleshooting

* `Provided key extension is not supported`: `keyPath` ends in neither `.pem` nor `.jks`. A PEM pair needs `.pem` at the end of the first path. Check the file names in the value.
* Ingot does not start on port 443: the user cannot bind it. Use a port above 1023 as in the steps above.
* Session cookies are missing `Secure` behind a proxy: that is the proxy's concern. See [Run behind a reverse proxy](run-behind-reverse-proxy.md).

## Related

* [Run the JAR](install-jar.md)
* [Run behind a reverse proxy](run-behind-reverse-proxy.md)

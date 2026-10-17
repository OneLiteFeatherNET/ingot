---
description: Put Ingot behind nginx, Traefik, Caddy or Cloudflare Tunnel, with the client address header, a base path, the forwarded prefix and the login public URL for single sign-on.
---

# Run behind a reverse proxy

Ingot speaks plain HTTP on its own port. A proxy in front of it terminates TLS, forwards requests and, when Ingot lives under a path, removes that path. Ingot needs three things from the proxy: the original scheme, the client address, and the prefix if there is one. This page sets them up.

To let Ingot serve HTTPS itself, read [Enable TLS](enable-tls.md) instead.

## Before you begin

* A running Ingot. The default port is `8080`, set by `port` in `configuration.cdn` or by `-p`.
* A public host name for the instance, for example `repo.example.org`, that points at the proxy.
* A token with the `ingot.admin.settings` permission. Step 3 changes a shared setting.

## Steps

1. Keep Ingot off the public network. Bind it to the loopback address or to a private interface, so that clients reach it only through the proxy. Set `hostname` in `configuration.cdn`, or start with `-h 127.0.0.1`.

2. Configure the proxy to send these headers:

   * `Host`, the public host name. Links in responses are built from it.
   * `X-Forwarded-Proto`, the scheme the client used. When it is `https`, Ingot marks the session cookies `Secure` and builds the OpenID Connect redirect with `https`.
   * A header that carries the client address, for example `X-Real-IP`.

   Ingot uses the value of the client address header exactly as it arrives. It does not take the first entry of a list. Make the proxy send one address, the connecting client, and not a chain that the client could extend.

3. Tell Ingot which header carries the client address. `forwardedIp` is a shared setting in the `web` domain, not a key of `configuration.cdn`. Its default is `X-Forwarded-For`. Read the current `web` settings, change `forwardedIp`, and send the whole object back:

   ```bash
   curl -u <manager>:<secret> https://repo.example.org/api/settings/domain/web
   curl -u <manager>:<secret> -X PUT https://repo.example.org/api/settings/domain/web \
     -H 'Content-Type: application/json' \
     -d '<the object you read, with "forwardedIp" set to "X-Real-IP">'
   ```

4. If Ingot runs under a path, set the base path. See [Serve under a path](#serve-under-a-path).

5. If users sign in with OpenID Connect, set the public address of the dashboard. See [Single sign-on redirect](#single-sign-on-redirect).

6. Check the result. The health endpoint needs no login:

   ```bash
   curl -f https://repo.example.org/api/status/health
   ```

   The answer is `200 OK` while Ingot is up. Ingot answers `503` when it is down.

{% hint style="info" %}
The `bypassExternalCache` setting, `true` by default, adds no-cache headers to `/api/*` responses only. It does not change how a proxy or CDN caches artifact downloads. Set the cache rules for repository paths in the proxy or CDN.
{% endhint %}

## Proxy examples

### nginx

```nginx
server {
    listen 443 ssl;
    server_name repo.example.org;
    # ssl_certificate and ssl_certificate_key, as for any site

    # nginx allows 1 MB request bodies by default. Artifacts are often larger.
    client_max_body_size 1024m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP $remote_addr;

        # Artifacts stream in both directions. The console streams its log as server-sent
        # events, which must not be buffered.
        proxy_request_buffering off;
        proxy_buffering off;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

Set `forwardedIp` to `X-Real-IP` (step 3).

### Traefik

With Docker labels, a host rule forwards the instance to Ingot:

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot:<version>
    labels:
      - traefik.enable=true
      - traefik.http.routers.ingot.rule=Host(`repo.example.org`)
      - traefik.http.services.ingot.loadbalancer.server.port=8080
```

Traefik does not set `X-Real-IP` by default, so keep `forwardedIp` at `X-Forwarded-For` for this setup. Traefik appends to `X-Forwarded-For`, so a client that sends its own value can make it a list. Restrict which addresses Traefik trusts for forwarded headers in the entrypoint configuration.

{% hint style="warning" %}
Not verified in this batch: that Traefik sets `X-Forwarded-Proto` to `https` for a TLS entrypoint. Check it in a request log before you rely on the `Secure` cookie flag.
{% endhint %}

Under a path, add a strip-prefix middleware. Traefik's `stripprefix` middleware also sets `X-Forwarded-Prefix` to the removed prefix:

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot:<version>
    labels:
      - traefik.enable=true
      - traefik.http.routers.ingot.rule=Host(`repo.example.org`) && PathPrefix(`/ingot`)
      - traefik.http.middlewares.ingot-strip.stripprefix.prefixes=/ingot
      - traefik.http.routers.ingot.middlewares=ingot-strip
      - traefik.http.services.ingot.loadbalancer.server.port=8080
```

### Caddy

Caddy obtains its own certificates. A plain site block is enough:

```caddyfile
repo.example.org {
    reverse_proxy 127.0.0.1:8080 {
        header_up X-Real-IP {http.request.remote.host}
    }
}
```

Set `forwardedIp` to `X-Real-IP` (step 3). Caddy sends streamed responses without delay, so the console works without a buffering setting.

Under a path, `handle_path` removes the prefix before the request reaches Ingot:

```caddyfile
repo.example.org {
    handle_path /ingot/* {
        reverse_proxy 127.0.0.1:8080 {
            header_up X-Real-IP {http.request.remote.host}
            header_up X-Forwarded-Prefix /ingot
        }
    }
}
```

{% hint style="warning" %}
Not verified in this batch: the `header_up X-Forwarded-Prefix` line and the default `X-Forwarded-Proto` value of Caddy. Caddy's documentation confirms the prefix removal of `handle_path`, not the header. Check the headers that reach Ingot in a test request.
{% endhint %}

### Cloudflare Tunnel

Cloudflare Tunnel publishes Ingot through `cloudflared`, which opens an outbound connection to Cloudflare. No inbound port is needed. Cloudflare terminates TLS, so the service address stays `http`:

```yaml
tunnel: <tunnel-uuid>
credentials-file: /etc/cloudflared/<tunnel-uuid>.json
ingress:
  - hostname: repo.example.org
    service: http://127.0.0.1:8080
  - service: http_status:404
```

Two settings differ from the other proxies:

* Ingot sees the address of `cloudflared`, not the client. Set `forwardedIp` to `CF-Connecting-IP`, which carries the client address.
* Cloudflare sets `X-Forwarded-Proto` to the protocol the visitor used, so session cookies are `Secure` for visitors on HTTPS.

Use a dedicated host name for the tunnel. Serving Ingot under a path through a tunnel needs a URL rewrite in Cloudflare, which this page does not cover.

## Serve under a path

Ingot has no path prefix of its own. The proxy removes the prefix, so that Ingot receives `/` for a request to `https://example.org/ingot/`. Then tell Ingot the prefix.

{% hint style="info" %}
A subdomain such as `ingot.example.org` is simpler than a path. Use a path only when a subdomain is not possible.
{% endhint %}

1. Set `basePath` in `configuration.cdn`. The environment variable is `INGOT_LOCAL_BASEPATH`.

   ```yaml
   basePath: /ingot/
   ```

   Ingot uses the base path for the dashboard links, the paths of the session cookies and the OpenID Connect callback path.

2. Restart Ingot.

3. Optional: set `forwardedPrefix` in the shared `web` settings. It is the name of a header that the proxy sends with the prefix, for example `X-Forwarded-Prefix`. The default is empty, which turns the feature off. When it is set, the repository index pages and the 404 pages of requests that carry the header link under `basePath`. Requests without the header get links at the root. Only these server-rendered pages change. The dashboard always follows `basePath`.

   ```nginx
   location /ingot/ {
       rewrite ^/ingot/(.*)$ /$1 break;
       proxy_set_header X-Forwarded-Prefix /ingot;
       # the other headers from the nginx example
   }
   ```

   {% hint style="info" %}
   A value of the header is not used. Ingot checks only that the header is present and not blank.
   {% endhint %}

{% hint style="warning" %}
A dashboard under a path in the split container needs its own image build. See [Run the dashboard in a separate container](run-split-dashboard-container.md#build-a-dashboard-for-a-sub-path). Combining that with a path in the proxy was not tested in this batch.
{% endhint %}

## Single sign-on redirect

OpenID Connect providers redirect the browser back to Ingot after the sign-in. The address of that redirect is built from `login.publicUrl`, a shared setting in the `login` domain.

1. Set `publicUrl` to the public address of the dashboard, including any path. Use `https://`, or `http://` only for `localhost`. The value must not contain a query, a fragment or a user name.

   ```json
   "publicUrl": "https://repo.example.org/ingot"
   ```

2. Read the `login` domain with `GET /api/settings/domain/login`, set `publicUrl`, and send the object back with `PUT`, as in step 3.

3. Register the callback URI at the identity provider. Ingot builds it as `<publicUrl>/api/auth/oidc/<provider-id>/callback`, for example `https://repo.example.org/ingot/api/auth/oidc/entra/callback`. The provider ID is the `id` of the provider in the login settings.

With an empty `publicUrl`, Ingot builds the redirect from the `Host` header and `X-Forwarded-Proto`. That works only when the proxy sends both headers correctly.

## Verify

* `curl -f https://repo.example.org/api/status/health` returns `200 OK`.
* Open the dashboard at the public address. A session cookie set by a sign-in carries the `Secure` flag when you use HTTPS.
* With `forwardedIp` set, the address of a request in the audit log is the client address, not the proxy address.

## Troubleshooting

* A sign-in loops back to the login page, or the cookies do not stick: the proxy does not send `X-Forwarded-Proto: https`, or `publicUrl` does not match the address in the browser.
* The audit log shows the proxy address for every actor: `forwardedIp` still names a header the proxy does not send.
* Repository downloads fail with `413` for large files: raise the request body limit in the proxy (`client_max_body_size` in nginx).

## Related

* [Enable TLS](enable-tls.md), for HTTPS served by Ingot itself.
* [Run the dashboard in a separate container](run-split-dashboard-container.md), for a proxy in front of a split dashboard.
* [Run as a service](run-as-service.md), to keep Ingot running behind the proxy.

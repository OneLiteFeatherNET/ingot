---
description: Run the Ingot dashboard in its own nginx container in front of the Ingot server, with its routes, environment variables, build for a sub-path and hardening.
---

# Run the dashboard in a separate container

By default the Ingot server serves the dashboard itself. The split setup moves the dashboard into its own image, `ghcr.io/onelitefeathernet/ingot-dashboard`. That container serves the static dashboard and passes every other request to the server, which runs without its dashboard. Use the split setup when the dashboard should restart, scale or be hardened separately from the artifact store. Otherwise the single container is simpler.

## Before you begin

* Docker with Compose.
* Both images at the same version. Replace `<version>` in the steps with the release tag.
* A `.env` file next to the compose file. The compose file reads these variables:
  * `PORT`, the host port of the dashboard.
  * `MEMORY`, the heap size passed to the server as `-Xmx`, for example `512m`.
  * `JAVA_COMPOSE_OPTS` and `INGOT_COMPOSE_OPTS`, optional extra options for the JVM and the server.

## Steps

1. Download `docker-compose.split.yml` from the repository. Set the image line of the `dashboard` service and of the `server` service to `<version>`.

2. Start both containers:

   ```bash
   docker compose -f docker-compose.split.yml up -d
   ```

3. Check the dashboard. Replace `8080` with the value of `PORT`:

   ```bash
   curl -I http://localhost:8080/
   curl -f http://localhost:8080/api/status/health
   ```

   The first request answers `200 OK` with `text/html`. The second answers `200 OK`, because the dashboard container proxies the health endpoint to the server.

## How the two containers divide the requests

The dashboard container serves these paths itself:

| Path | Answered by |
|------|-------------|
| `/`, `/index.html`, `/assets/*`, `/favicon.png` | The dashboard container, from static files. |
| `/browse`, `/search`, `/dashboard`, `/tokens`, `/console`, `/settings`, `/maintenance`, `/audit`, `/access`, `/account`, and everything below them | The dashboard document, so that a reload of a dashboard address works. |
| Everything else, such as `/api/*`, repository paths, javadocs and badges | The server, through `INGOT_BACKEND`. |

The browser sees one origin, so no request needs CORS.

{% hint style="warning" %}
Repository paths below the dashboard words are answered with the dashboard document, not with artifacts. A repository whose id is one of these words, such as `search`, cannot be downloaded from `/search/...` through this container. Choose repository ids that are not in the table. The server logs a warning for reserved ids. This nginx behaviour was read from the template and not tested in this batch.
{% endhint %}

## Environment variables

The dashboard image sets defaults for these variables. Override them only when your network needs it.

| Variable | Default in the image | Compose example | What it does |
|----------|----------------------|-----------------|--------------|
| `INGOT_BACKEND` | `http://ingot-backend:8080` | `http://server:8080` | Address of the server. Resolved per request, so the dashboard starts whether or not the server is up. |
| `INGOT_RESOLVER` | `127.0.0.11` | `127.0.0.11` | DNS server for `INGOT_BACKEND`. `127.0.0.11` is the Docker resolver. On Kubernetes, use the cluster DNS. |
| `INGOT_MAX_UPLOAD_SIZE` | `1024m` | `1024m` | Largest request body the proxy accepts. An artifact above this size fails at the proxy, before the server sees it. |

The server container needs one change:

| Variable | Value | Why |
|----------|-------|-----|
| `INGOT_LOCAL_DEFAULTFRONTEND` | `false` | The server does not answer for the dashboard as well. Two copies at two addresses are the confusing result. |

The server publishes no port in the compose file. Only the dashboard reaches the network. Add a `ports` entry to the server only if something has to talk to it directly, because that bypasses the dashboard container.

## Hardening

The compose file runs the dashboard with `read_only: true`, drops all Linux capabilities and sets `no-new-privileges`. nginx still needs a few writable paths, which the file mounts as `tmpfs` owned by the image user (`101`). Keep those mounts. Without them nginx starts with no server block, and the container looks healthy while it serves nothing.

The server runs as user `977` with the same capability drop. The data volume is created empty by the compose file, so it is owned by `977` from the start.

## Build the dashboard from source

Build from the repository root, not from `reposilite-frontend/`:

```bash
docker build -f reposilite-frontend/Dockerfile -t ingot-dashboard .
```

Use the local tag `ingot-dashboard` in the compose file in place of the published image.

## Build a dashboard for a sub-path

The published dashboard image serves the dashboard at `/`. The base path is built into the dashboard, because the asset URLs in the page must resolve before any script runs. To serve it under `/ingot/`, build the image with that prefix:

```bash
docker build -f reposilite-frontend/Dockerfile \
  --build-arg INGOT_BASE_PATH=/ingot/ \
  -t my-ingot-dashboard .
```

The server then needs the same prefix in `basePath`, and the proxy in front of the stack has to remove the prefix. See [Serve under a path](run-behind-reverse-proxy.md#serve-under-a-path).

{% hint style="warning" %}
Not tested in this batch: the combination of the split setup, a sub-path and a proxy that removes the prefix. A subdomain for the dashboard avoids all three.
{% endhint %}

## Behind a proxy that terminates TLS

Put the proxy in front of the dashboard container, as in [Run behind a reverse proxy](run-behind-reverse-proxy.md).

{% hint style="warning" %}
Known gap: the dashboard template sets `X-Forwarded-Proto` to nginx's own scheme inside the container. Behind a proxy that terminates TLS, that scheme is `http`, so the server does not mark session cookies `Secure`. Until the template passes the incoming header through, check the cookie flags in the browser after a sign-in.
{% endhint %}

## Verify

* `curl -I http://localhost:<PORT>/browse/` returns `200 OK` with the dashboard document.
* `curl -f http://localhost:<PORT>/api/status/health` returns `200 OK`.
* `docker compose -f docker-compose.split.yml ps` shows both services as running.

## Related

* [Run behind a reverse proxy](run-behind-reverse-proxy.md)
* [Enable TLS](enable-tls.md)

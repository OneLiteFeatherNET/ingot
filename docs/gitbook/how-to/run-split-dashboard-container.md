---
description: Run the Ingot dashboard in its own container in front of the Ingot server, with its routes, environment variables, sub-path and hardening.
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
| `/`, `/index.html`, `/assets/*`, `/favicon.png` | The dashboard container, from its bundled files. |
| `/browse`, `/search`, `/dashboard`, `/analytics`, `/tokens`, `/console`, `/settings`, `/maintenance`, `/audit`, `/access`, `/account`, and everything below them | The dashboard document, so that a reload of a dashboard address works. |
| `/healthz` | The dashboard container, on its own. It answers while the server is down. |
| Everything else, such as `/api/*`, repository paths, javadocs and badges | The server, through `INGOT_BACKEND`. |

The browser sees one origin, so no request needs CORS. The list of dashboard words is the same list the server uses to reserve repository ids.

{% hint style="warning" %}
Repository paths below the dashboard words are answered with the dashboard document, not with artifacts. A repository whose id is one of these words, such as `search`, cannot be downloaded from `/search/...` through this container. Choose repository ids that are not in the table. The server logs a warning for reserved ids.
{% endhint %}

## Environment variables

The dashboard image sets defaults for these variables. They are read when the container starts, so a change needs a restart and no new image. Override them only when your network needs it.

| Variable | Default in the image | Compose example | What it does |
|----------|----------------------|-----------------|--------------|
| `INGOT_BACKEND` | `http://ingot-backend:8080` | `http://server:8080` | Address of the server, an absolute `http` or `https` URL. The container refuses to start without one, and names the variable in its log. Resolved per request, so the dashboard starts whether or not the server is up. |
| `INGOT_BASE_PATH` | `/` | `/ingot/` | The path the dashboard is served under. See [Serve under a sub-path](#serve-under-a-sub-path). Must start with `/`. |
| `INGOT_MAX_UPLOAD_SIZE` | `1024m` | `1024m` | Largest request body the proxy accepts, as bytes or with a `k`, `m` or `g` suffix. An artifact above this size fails at the proxy with `413`, before the server sees it. |
| `INGOT_RESOLVER` | not set | not set | No longer used. The dashboard container resolves the server with the resolver of its network. A value that is still set is accepted and logged as ignored, so an existing compose file starts unchanged. |

The server container needs one change:

| Variable | Value | Why |
|----------|-------|-----|
| `INGOT_LOCAL_DEFAULTFRONTEND` | `false` | The server does not answer for the dashboard as well. Two copies at two addresses are the confusing result. |

The server publishes no port in the compose file. Only the dashboard reaches the network. Add a `ports` entry to the server only if something has to talk to it directly, because that bypasses the dashboard container.

## Hardening

The dashboard image runs as user `65532`, the non-root user of its distroless base. It has no shell and no package manager, and it writes nothing at run time, so the compose file runs it with `read_only: true`, drops all Linux capabilities and sets `no-new-privileges`. It mounts no `tmpfs`.

{% hint style="info" %}
The previous dashboard image ran as user `101`, and its compose file mounted `tmpfs` owned by `101`. An existing compose file that still has those mounts keeps starting: the mounts are simply unused. You can remove them.
{% endhint %}

The server runs as user `977` with the same capability drop. The data volume is created empty by the compose file, so it is owned by `977` from the start.

Because the image has no shell, `docker exec <container> sh` does not work on it. Use `docker logs` for the dashboard container.

## Serve under a sub-path

The dashboard can be served under a path, such as `/ingot/`, without building a second image. Set `INGOT_BASE_PATH` on the dashboard container:

```yaml
  dashboard:
    environment:
      - INGOT_BACKEND=http://server:8080
      - INGOT_BASE_PATH=/ingot/
```

The dashboard then answers below `/ingot/`, and it removes that prefix before it proxies a request, so the server sees `/api/...` as it does at the root. The server itself is still served at `/`, so the reverse proxy in front of the stack should pass the `/ingot/` prefix on unchanged. Do not strip it there. See [Serve under a path](run-behind-reverse-proxy.md#serve-under-a-path) for the proxy side.

The `/healthz` endpoint stays at the root, whatever the base path is, so health checks need no change.

## Build the dashboard from source

Build from the repository root, not from `reposilite-frontend/`:

```bash
docker build -f reposilite-frontend/Dockerfile -t ingot-dashboard .
```

Use the local tag `ingot-dashboard` in the compose file in place of the published image. To build for both published architectures, use `docker buildx build --platform linux/amd64,linux/arm64`.

## Behind a proxy that terminates TLS

Put the proxy in front of the dashboard container, as in [Run behind a reverse proxy](run-behind-reverse-proxy.md). The dashboard passes the `X-Forwarded-Proto` header of that proxy on to the server, so the server sees the scheme the browser used and marks session cookies `Secure` where it should. A proxy that sets the header must do so on every request.

## Verify

* `curl -I http://localhost:<PORT>/browse/` returns `200 OK` with the dashboard document.
* `curl -f http://localhost:<PORT>/healthz` returns `200 OK` with the body `ok`.
* `curl -f http://localhost:<PORT>/api/status/health` returns `200 OK`.
* `docker compose -f docker-compose.split.yml ps` shows both services as running.

## Related

* [Run behind a reverse proxy](run-behind-reverse-proxy.md)
* [Enable TLS](enable-tls.md)

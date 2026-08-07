---
id: split-containers
title: Split containers
---

Ingot ships two images. The server serves the dashboard itself, which is the deployment to
start from, and the dashboard can also run in a container of its own in front of it.

```bash
# Server only, serving the dashboard itself
$ docker compose up -d

# Dashboard and server separately
$ docker compose -f docker-compose.split.yml up -d
```

### When the split is worth it

It is not free: two images to update, and a proxy hop in front of every request. It earns
that when one of these is true.

* **The dashboard is what your users hit, and the artifact traffic is what your builds
  hit.** Separating them lets you scale and restart the human-facing half without touching
  the one CI depends on.
* **You want a smaller thing on the edge.** The container that faces the internet becomes a
  static file server with no writable filesystem, and the JVM moves behind it.
* **Your platform expects it**, because the two halves get different resource limits,
  probes or replica counts.

If none of that applies, run the server on its own. Fewer moving parts is the better
default, and nothing about the bundled dashboard is second class: it is the same build.

### How the pieces fit

Only the dashboard container publishes a port. Everything that is not the dashboard's own
static content is proxied to the server, so both are one origin as far as the browser is
concerned and no repository read needs CORS.

That works because the dashboard routes on the URL hash. Whatever a user navigates to, the
browser only asks the dashboard container for the document root, the hashed assets under
`/assets/` and the favicon. Everything else, which is the API, every repository path,
javadocs and badges, belongs to the server. The default is to proxy and the exceptions are
listed, rather than the other way round: repository names are arbitrary top level paths and
cannot be told apart from dashboard routes by their prefix.

The dashboard has no settings of its own. It reads them from the server at startup, from
`/api/frontend/settings`, which is public because every value in it is already handed to
any anonymous visitor of the bundled dashboard.

### Configuring it

The dashboard container:

| Variable | Default | What it does |
|----------|---------|--------------|
| `INGOT_BACKEND` | `http://ingot-backend:8080` | Where the server is. Resolved per request, so the dashboard starts whether or not the server is up. |
| `INGOT_RESOLVER` | `127.0.0.11` | DNS to resolve it with. Docker's embedded resolver by default; on Kubernetes this wants the cluster resolver. |
| `INGOT_MAX_UPLOAD_SIZE` | `1024m` | Largest artifact that may be uploaded through the proxy. Too small and deploys of big artifacts fail at the proxy, before the server sees them. |

The server container wants `INGOT_LOCAL_DEFAULTFRONTEND=false`, so it does not answer for a
dashboard as well. Two copies at different URLs is a confusing outcome, not a safe one.

It still serves a small static landing page from `static/index.html` in its working
directory, which is the page you replace to put something of your own at the root of a
server that has no dashboard. In this setup the dashboard container answers `/` itself, so
that page is never reached.

#### Mounting under a path

If the dashboard is not at the root of its host, the prefix has to be baked in at build
time. The asset URLs sit in script tags that the browser resolves before any of our code
runs, so nothing can be substituted for them later:

```bash
$ docker build -f reposilite-frontend/Dockerfile \
    --build-arg INGOT_BASE_PATH=/ingot/ \
    -t my-ingot-dashboard .
```

### Hardening

Both containers drop all capabilities and run unprivileged, and the dashboard runs with a
read only root filesystem. The compose file has all of it; two details are easy to get
wrong if you write your own.

**The tmpfs mounts need the image's own uid.** nginx renders its configuration at startup
into `/etc/nginx/conf.d`. Mount a tmpfs there without `uid=101,gid=101` and it lands owned
by root, nginx cannot write into it, and it starts with no server block at all rather than
failing outright. The symptom is a container that looks healthy and refuses every
connection.

**The server is not read only.** It writes artifacts, logs and its database. What it gets
instead is an unprivileged default user, which is the part that matters. It still has a
root path for deployments that set `PUID` or `PGID`, or that hand it a volume owned by
somebody else; those start it with `user: root` deliberately.

Do not publish a port for the server. Reaching it means going through the dashboard, and
adding a port bypasses the proxy along with everything configured on it.

---
description: How to run Ingot in Docker with a pinned image tag, a persistent data volume, the right user and group ids, environment variables and a health check.
---

# Install with Docker

This guide shows how to run the Ingot server in a Docker container for a real deployment. For a first run with a guided walkthrough, use [First steps with Docker](../tutorials/first-steps-with-docker.md).

## Prerequisites

* Docker Engine or Docker Desktop. The `docker` command works without `sudo`.
* A host with a free TCP port for the dashboard and the API. This guide uses 8080.
* A place for the data that outlives the container, such as a named volume or a host directory.
* A host with an `amd64` or `arm64` processor. The image is published for both.

## Choose an image tag

The image is `ghcr.io/onelitefeathernet/ingot`. Every release publishes these tags:

| Tag | Moves when | Use it for |
| --- | --- | --- |
| `1.2.0` (the full version) | Never. It always points to the same build | Production |
| `1.2` | Each new patch release of 1.2 | Patch updates within one minor version |
| `1` | Each new release of the 1.x line | Minor updates, only when you read the release notes |
| `latest` | Each new release | Trying out |

Use the full version in production. A moving tag gives a different version when you pull the image again.

## Run with docker run

1. Start the container. The data lives in the volume `ingot-data`, which Docker creates on first use.

   ```bash
   docker run -d \
     --name ingot \
     --restart unless-stopped \
     -p 8080:8080 \
     -v ingot-data:/app/data \
     ghcr.io/onelitefeathernet/ingot:1.2.0
   ```

2. Ask the server for its health.

   ```bash
   curl -s http://localhost:8080/api/status/health
   ```

   The response is `{"status":"UP"}`. If it fails, wait ten seconds and try again. Then check the log with `docker logs ingot`.

## Run with Compose

1. Create `compose.yaml` in an empty folder:

   ```yaml
   services:
     ingot:
       image: ghcr.io/onelitefeathernet/ingot:1.2.0
       ports:
         - "8080:8080"
       environment:
         - JAVA_OPTS=-Xmx1g
       volumes:
         - ingot-data:/app/data
       restart: unless-stopped
       security_opt:
         - no-new-privileges:true

   volumes:
     ingot-data: {}
   ```

2. Start the service in the background.

   ```bash
   docker compose up -d
   ```

3. Check that the container reports healthy. The health check runs every 30 seconds, so it can take up to a minute.

   ```bash
   docker compose ps
   ```

   The status column shows `healthy`.

{% hint style="info" %}
The repository ships a compose file for a split setup, where the dashboard runs in its own container. The single container above serves the dashboard itself, which is the simpler setup.
{% endhint %}

## Set the user and the group

The image starts as `root`. Its entrypoint then runs the server as the account `reposilite`, which has the user id and group id `977` by default. This is the only way the entrypoint can adjust a data directory that another user owns.

Pick one of these options.

* **Keep the default.** Use this when you start from an empty volume or from a data directory that you do not want to change. If the owner of `/app/data` differs from the server's ids, the entrypoint changes the owner of `/app` and `/var/log/reposilite` once at start. It prints a warning when it does.
* **Set the ids.** Set `PUID` and `PGID` to the ids that own your data directory. The entrypoint then runs the server with those ids and adjusts the account to match.
* **Pin the user.** Use this after the volume is owned by `977`, which is the case after the first start with the default. Add `user: "977:977"` to the compose service, or pass `--user 977:977` to `docker run`. The entrypoint detects the non-root user, skips the ownership change and starts the server directly.

{% hint style="warning" %}
Do not pin the user for a data directory that another user owns. The server cannot write to it, and the entrypoint does not change the owner when the user is pinned.
{% endhint %}

## Environment variables

The image reads these variables.

| Variable | Default | Effect |
| --- | --- | --- |
| `JAVA_OPTS` | empty | Options for the Java virtual machine, for example `-Xmx1g`. |
| `INGOT_OPTS` | empty | Arguments for the server, for example `--port 9090`. If it is empty, the entrypoint reads `REPOSILITE_OPTS`. |
| `INGOT_LOCAL_<NAME>` | none | Overrides one key of the local configuration, for example `INGOT_LOCAL_DEFAULTFRONTEND=false`. The name is not case sensitive. The override is not written back to the file. |
| `PUID` | `977` | The user id the server runs as. |
| `PGID` | `977` | The group id the server runs as. |
| `INGOT_FORCE_CHOWN` | empty | Set to `true` to change the owner of `/app` and `/var/log/reposilite` at every start. If it is empty, the entrypoint reads `REPOSILITE_FORCE_CHOWN`. |

`JAVA_OPTS`, `PUID` and `PGID` have no prefix. The `REPOSILITE_` names still work as fallbacks for `INGOT_OPTS`, `INGOT_FORCE_CHOWN` and `INGOT_LOCAL_<NAME>`.

The entrypoint adds `--working-directory=/app/data` to the server arguments, unless `INGOT_OPTS` already sets a working directory.

## Change the port

The server listens on port 8080 inside the container. To use another port, set the server port in `INGOT_OPTS` and publish that port. For example, for port 9090:

```bash
docker run -d \
  --name ingot \
  -e INGOT_OPTS="--port 9090" \
  -p 9090:9090 \
  -v ingot-data:/app/data \
  ghcr.io/onelitefeathernet/ingot:1.2.0
```

## Read the logs

The server writes its log to the standard output of the container, so `docker logs` shows it.

```bash
docker logs -f ingot
```

The log files of the server are in `/var/log/reposilite` inside the container.

## Stop, start and keep the data

A stop and a start keep the data, because it lives in the volume.

```bash
docker stop ingot
docker start ingot
```

Removing the container with `docker rm` keeps the volume. Removing the volume with `docker volume rm ingot-data` deletes everything this instance stores, including its database and its artifacts.

## Upgrade

Before you change the image tag, read [Back up before upgrading](back-up-before-upgrading.md). Then follow the guide for your current version:

* From Reposilite 3.x: [Upgrade from Reposilite 3.x](upgrade-from-reposilite.md).
* From Ingot 1.1.x: [Upgrade from Ingot 1.1.x](upgrade-ingot.md).

## Result

The health endpoint answers `{"status":"UP"}`. A new instance also reports that it needs its first administrator:

```bash
curl -s http://localhost:8080/api/auth/providers
```

The response contains `"setupRequired":true`. To create that administrator, follow [Set up the first administrator](set-up-first-admin.md).

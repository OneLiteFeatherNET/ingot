---
id: docker
title: Docker
---

The Ingot container image is published to the GitHub Container Registry:
 - [ghcr.io/onelitefeathernet/ingot](https://github.com/OneLiteFeatherNET/ingot/pkgs/container/ingot)

There are three different types of tags used on the images:
 - `X.X.X` (tag-based) - published per release, recommended for production environments.
 - `latest` - always refers to the most recent release, not recommended.
 - `nightly` - published for each commit to the `main` branch, may contain bugs but is useful for testing new features.

First of all, you have to pull the image:

```bash
# released builds, e.g. 1.0.0
$ docker pull ghcr.io/onelitefeathernet/ingot:1.0.0

# nightly builds
$ docker pull ghcr.io/onelitefeathernet/ingot:nightly
```

Then, just run the image in interactive mode _(to enable [interactive CLI](/guide/standalone#interactive-cli))_:

```bash
$ docker run -it -v ingot-data:/app/data -p 80:8080 ghcr.io/onelitefeathernet/ingot:nightly
```

### Startup configuration

#### Data persistence

Ingot stores data in the `/app/data` directory by default.
To make it persistent, use a named volume with the `-v` parameter:

```bash
$ docker run -it -v ingot-data:/app/data -p 80:8080 ghcr.io/onelitefeathernet/ingot
```

#### JVM properties

You can also pass custom configuration values using the environment variables:

```bash
$ docker run -e JAVA_OPTS='-Xmx128M' -p 80:8080 ghcr.io/onelitefeathernet/ingot
```

#### Ingot properties

To pass custom parameters described in [general#parameters](general#parameters), use the `INGOT_OPTS` variable:

```bash
$ docker run -e INGOT_OPTS='--local-configuration=/app/data/custom.cdn' -p 80:8080 ghcr.io/onelitefeathernet/ingot
```

`REPOSILITE_OPTS` is still read when `INGOT_OPTS` is unset, so a deployment migrating from
Reposilite starts unchanged against the Ingot image.

#### External configuration

You can mount external configuration files using the `--mount` parameter.
Before that, you have to make sure the configuration file already exists on the Docker host.
To launch Ingot with a custom configuration, we have to mount the proper file:

```bash
$ docker run -it \
  --mount type=bind,source=/etc/ingot/configuration.cdn,target=/app/configuration.cdn \
  -e INGOT_OPTS='--local-configuration=/app/configuration.cdn' \
  -v ingot-data:/app/data \
  -p 80:8080 \
  ghcr.io/onelitefeathernet/ingot
```

### Using Docker Compose 

See the [default docker-compose.yml file](https://github.com/OneLiteFeatherNET/ingot/blob/main/docker-compose.yml) as a starting point for your configuration.

Because access tokens are created through the console command,
you should generate your first access token using the `--token` startup parameter as follows:

1. Add `- INGOT_OPTS=--token admin:secret` to the environment section in the `docker-compose.yml` file. <br />
  **NOTE**: This is only needed for the first run and should be removed after creating a real security token.
2. Start the container with `docker-compose up -d`
3. Go to the address with your Ingot instance to access the web dashboard.
4. Use your credentials specified in _INGOT_OPTS_ to login _(e.g. admin / secret)_
5. Open the _'Console'_ tab. <br />
   **NOTE**: Your reverse proxy must also support and accept WebSocket connections.
6. Type `token-generate admin m` to create user named 'admin' with **m**anagement permission. 
   A strong password will be generated for you, so you should copy it. 
   If you really need to provide a custom one, use the `--secret=<your-password>` parameter. 
   Please note that this type of password creation is strongly discouraged. 
7. Tear down the container with `docker-compose down` and remove the _INGOT_OPTS_ line with the `--token admin:secret` parameter from your compose file.
8. Restart the container with `docker-compose up -d` and login with your credentials provided by the token-generate command. These credentials will be persisted.

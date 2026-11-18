# syntax=docker/dockerfile:1
#
# The command line client as a container image: ghcr.io/onelitefeathernet/ingot-cli.
# Use it with `docker run -it`, `docker compose run` or `kubectl run -it --rm`. Configure it with INGOT_URL and a token
# from INGOT_TOKEN_FILE. It is a separate image from the server, which is not changed by this file.

# --platform=$BUILDPLATFORM keeps the build stage on the architecture of the builder: a jar holds no machine code.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.13.1_1-jdk-noble AS build
WORKDIR /src
COPY . .
RUN ./gradlew :ingot-cli:shadowJar --no-daemon --max-workers=2 \
 && cp "$(ls ingot-cli/build/libs/ingot-cli-*.jar | head -n 1)" /ingot-cli.jar

# The JRE base is the one the server image uses, so Renovate raises one digest for both.
FROM eclipse-temurin:21.0.13.1_1-jre-noble AS run

# A fixed non-root user. The image has no volume and nothing to chown, so no entrypoint script is needed.
RUN groupadd --gid 10001 ingot \
 && useradd --uid 10001 --gid ingot --create-home --home-dir /home/ingot --shell /usr/sbin/nologin ingot \
 && printf '#!/bin/sh\nexec java -jar /app/ingot-cli.jar "$@"\n' > /usr/local/bin/ingot \
 && chmod 0755 /usr/local/bin/ingot \
 && mkdir -p /app
COPY --from=build --chmod=0644 /ingot-cli.jar /app/ingot-cli.jar

ENV HOME=/home/ingot \
    XDG_CONFIG_HOME=/home/ingot/.config \
    XDG_STATE_HOME=/home/ingot/.local/state

USER 10001:10001
WORKDIR /home/ingot
ENTRYPOINT ["ingot"]
CMD []

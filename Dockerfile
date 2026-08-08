# syntax=docker.io/docker/dockerfile:1.7-labs

# The Ingot server.
#
# This image is a drop-in replacement for the upstream Reposilite image: same entrypoint,
# same paths, same environment variables, same default behaviour. Pointing an existing
# deployment at it must not require editing anything but the image reference.
#
# Serves the dashboard itself by default, which is the simpler deployment and needs nothing
# else running. Set INGOT_LOCAL_DEFAULTFRONTEND=false and put the dashboard image in front
# to split the two; see reposilite-site/data/guides/installation/split-containers.md.

# Build stage. Pinned by digest as well as tag so a rebuild cannot silently land on a
# different base. Renovate raises both together.
FROM eclipse-temurin:21-jdk-noble@sha256:a871f3e3caddad75608fd4531ed8bbca5cc42a27dc1da3ea3a2e554772b0ee15 AS build
COPY --exclude=entrypoint.sh . /home/ingot-build
WORKDIR /home/ingot-build

# The below line will show an Error in some IDE's, It is valid Dockerfile.
RUN --mount=type=cache,target=/root/.gradle <<EOF
  export GRADLE_OPTS="-Djdk.lang.Process.launchMechanism=vfork"
  ./gradlew :reposilite-backend:shadowJar --no-daemon --stacktrace
EOF

# Image labels are not declared here on purpose. The block that used to sit at this
# point was attached to the build stage, so it never reached the published image, and
# it still named the upstream project as vendor. The release workflow now applies
# OCI labels (org.opencontainers.image.*) at push time, which keeps version and
# revision in one place instead of duplicating them into build arguments.

# Run stage
FROM eclipse-temurin:21-jre-noble@sha256:ca397720325ceefe39ce397f186759fc87d9efafb2dc4ce53315980844c2f4f2 AS run

# Setup runtime environment.
#
# /app/data, /var/log/reposilite and the reposilite service user keep their names. They are
# the operational contract of a running instance: bind mounts, volume claims and log
# shippers point at those paths, and renaming them would break an existing deployment on
# upgrade for no functional gain. Only the artifact itself is branded.
#
# The account is created here rather than by the entrypoint at boot, so a deployment that
# pins `user: 977:977` finds a real account and an already correctly owned data directory.
# The entrypoint still creates or adjusts it when PUID or PGID ask for different ids.
#
# The `ubuntu` account the base image ships holds uid and gid 1000, which is the first id a
# desktop Linux hands out and therefore the most common value anyone puts in PUID. Leaving
# it in place makes `PUID=1000` fail on "id already in use" before the server ever starts.
# Nothing here uses that account, so it goes.
RUN <<EOF
    set -eu
    groupadd --gid 977 reposilite
    useradd --uid 977 --gid 977 --system --shell /bin/sh --no-create-home reposilite
    mkdir -p /app/data /var/log/reposilite
    chown -R reposilite:reposilite /app /var/log/reposilite
EOF
VOLUME /app/data
WORKDIR /app

    userdel --remove ubuntu 2>/dev/null || true
    groupdel ubuntu 2>/dev/null || true
# Import application code
COPY --chmod=755 entrypoint.sh entrypoint.sh
COPY --from=build --chown=reposilite:reposilite /home/ingot-build/reposilite-backend/build/libs/ingot-*.jar ingot.jar

HEALTHCHECK --interval=30s --timeout=30s --start-period=15s \
    --retries=3 CMD [ "sh", "-c", "URL=$(cat /app/data/.local/reposilite.address); echo -n \"curl $URL... \"; \
    (\
        curl -sf $URL > /dev/null\
    ) && echo OK || (\
        echo Fail && exit 2\
    )"]

# The artifact is named after this project, but /app/reposilite.jar is part of what an
# upstream deployment may reference: anything that overrides the entrypoint or the command
# names the jar itself. The link keeps those working.
RUN ln -s ingot.jar /app/reposilite.jar

# No USER on purpose. Upstream starts as root and lets the entrypoint drop to the service
# account itself, which is what makes PUID, PGID and the chown of a volume owned by somebody
# else work at all. Declaring a non-root default here would cut off that path and break
# exactly the deployments this image is supposed to be droppable into.
#
# Running unprivileged stays one line away and is the recommended setup for a volume that is
# already owned correctly: `user: "977:977"` in compose, or runAsUser/runAsGroup in a pod
# spec. The entrypoint detects it and execs the server directly.

ENTRYPOINT ["/app/entrypoint.sh"]
EXPOSE 8080

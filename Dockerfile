# syntax=docker.io/docker/dockerfile:1.7-labs

# Build stage
FROM eclipse-temurin:21-jdk-noble AS build
COPY --exclude=entrypoint.sh . /home/reposilite-build
WORKDIR /home/reposilite-build

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
FROM eclipse-temurin:21-jre-noble AS run

# Setup runtime environment
RUN mkdir -p /app/data && mkdir -p /var/log/reposilite
VOLUME /app/data
RUN <<EOF
    mkdir -p /app/data
    mkdir -p /var/log/reposilite
EOF
WORKDIR /app

# Import application code
COPY --chmod=755 entrypoint.sh entrypoint.sh
COPY --from=build /home/reposilite-build/reposilite-backend/build/libs/reposilite-3*.jar reposilite.jar

HEALTHCHECK --interval=30s --timeout=30s --start-period=15s \
    --retries=3 CMD [ "sh", "-c", "URL=$(cat /app/data/.local/reposilite.address); echo -n \"curl $URL... \"; \
    (\
        curl -sf $URL > /dev/null\
    ) && echo OK || (\
        echo Fail && exit 2\
    )"]
ENTRYPOINT ["/app/entrypoint.sh"]
EXPOSE 8080
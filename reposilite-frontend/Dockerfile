# The Ingot dashboard, served on its own.
#
# Splitting it off the server buys three things: the dashboard scales and restarts
# independently of the artifact store, a static file server is a far smaller thing to attack
# than a JVM, and this image needs no writable state at all, so it can run with a read only
# root filesystem.
#
# The server can still serve the dashboard itself, which stays the simpler deployment. See
# reposilite-site/data/guides/installation/split-containers.md for when each is worth it.
#
# Build from the repository root, not from this directory:
#   docker build -f reposilite-frontend/Dockerfile -t ingot-dashboard .
# The image needs the shared branding assets, which live outside this module.

# Pinned by digest as well as tag, so a rebuild cannot silently land on a different base.
# Renovate raises both together.
#
# --platform=$BUILDPLATFORM keeps this stage on the architecture doing the building. What it
# produces is HTML, CSS and JavaScript, which is the same on every target, so emulating the
# npm install and the bundler for a second architecture would buy an identical result at
# several times the cost. Only the nginx stage below is built per target.
FROM --platform=$BUILDPLATFORM node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build

WORKDIR /build

# Dependencies first, so a source-only change does not reinstall them.
COPY reposilite-frontend/package.json reposilite-frontend/package-lock.json ./
# `npm ci` rather than `npm install`: it installs exactly what the lockfile says and fails
# when the two have drifted, instead of quietly resolving something else.
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY reposilite-frontend/ ./

# Where this dashboard will be mounted. It has to be known now rather than at startup: the
# asset URLs live in script tags in index.html and must resolve before any of our code runs.
ARG INGOT_BASE_PATH="/"
ENV INGOT_BASE_PATH=${INGOT_BASE_PATH}
RUN npm run build

# The remaining placeholders sit in the document head, which is rendered before the app can
# fetch anything, so an untouched build would show a raw token in the browser tab until Vue
# takes over. They are replaced with neutral text; the real values arrive with the settings
# and the app applies them.
RUN set -eu; \
    index=build/frontend/reposilite-frontend/index.html; \
    sed -i \
        -e 's/{{REPOSILITE.TITLE}}/Ingot Repository/g' \
        -e 's|{{REPOSILITE.DESCRIPTION}}|Maven repository powered by Ingot|g' \
        "$index"; \
    ! grep -q '{{REPOSILITE\.' "$index"

# nginx-unprivileged already runs as a non-root user and listens above 1024, rather than
# starting as root and dropping privileges the way the stock nginx image does.
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:a6c3ec0c0d249d68b0682df854d4a9e222b90fb607dc3fcf2f1d2fcbc85d347e AS run

# Where to reach the server, and how to resolve it. 127.0.0.11 is Docker's embedded DNS; on
# Kubernetes this wants the cluster resolver instead.
ENV INGOT_BACKEND="http://ingot-backend:8080" \
    INGOT_RESOLVER="127.0.0.11" \
    INGOT_MAX_UPLOAD_SIZE="1024m"

# Rendered by the base image's own entrypoint, which substitutes the INGOT_ variables above
# and leaves nginx's own $uri and $host alone.
COPY reposilite-frontend/docker/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /build/build/frontend/reposilite-frontend/ /usr/share/nginx/html/
# Branding lives outside this module. An operator can still mount their own over this.
COPY reposilite-backend/src/main/resources/static/favicon.png /usr/share/nginx/html/favicon.png

# Inherited from the base image, repeated so it survives a base image change.
USER 101

EXPOSE 8080

# Static content, so being able to answer at all is the whole health question.
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/ || exit 1

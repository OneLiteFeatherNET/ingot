# Serves the Ingot dashboard on its own, in front of a server that has its dashboard switched off.
#
# Splitting it off the server buys three things: the dashboard scales and restarts
# independently of the artifact store, a Node server that answers only the dashboard and the
# proxy is a far smaller thing to attack than a JVM, and this image writes nothing at run time,
# so it can run with a read only root filesystem.
#
# The server can still serve the dashboard itself, which stays the simpler deployment. See
# docs/gitbook/how-to/run-split-dashboard-container.md for when each is worth it.
#
# Build from the repository root, not from this directory:
#   docker build -f reposilite-frontend/Dockerfile -t ingot-dashboard .
# The image needs the shared branding favicon, which lives outside this module.

# Pinned by digest as well as tag, so a rebuild cannot silently land on a different base.
# Renovate raises both together.
#
# --platform=$BUILDPLATFORM keeps this stage on the architecture doing the building. What it
# produces is JavaScript, which is the same on every target, so emulating the npm install and
# the bundler for a second architecture would buy an identical result at several times the cost.
# Only the run stage below is built per target architecture.
FROM --platform=$BUILDPLATFORM node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build

WORKDIR /build

# Dependencies first, so a source-only change does not reinstall them.
COPY reposilite-frontend/package.json reposilite-frontend/package-lock.json ./
# `npm ci` rather than `npm install`: it installs exactly what the lockfile says and fails
# when the two have drifted, instead of quietly resolving something else.
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY reposilite-frontend/ ./

# The branding favicon is picked up from here by the dashboard build (see vite.config.js).
COPY reposilite-backend/src/main/resources/static/favicon.png ./dashboard-branding/favicon.png

# The dashboard build and the Nitro build of its server. The base path is not built in: the
# server writes it into the document when it starts, from INGOT_BASE_PATH.
RUN npm run build:dashboard

# The distroless Node runtime has no shell and no package manager, and runs as the non-root user 65532.
# The Node major matches the build stage. Pinned by tag and digest; Renovate raises both together.
FROM gcr.io/distroless/nodejs24-debian13:nonroot@sha256:088a01637f22908632ba09018be2e0b516f2fa6f7869c191d29491139db43c78 AS run

WORKDIR /app

# The output is self-contained: the server, its bundled dependencies and the dashboard assets.
COPY --from=build /build/.output /app/.output

# Where the server is reached, and the largest request body the proxy takes. Read when the process starts.
# INGOT_RESOLVER is no longer needed and is ignored when it is set.
ENV INGOT_BACKEND="http://ingot-backend:8080" \
    INGOT_MAX_UPLOAD_SIZE="1024m" \
    PORT="8080"

# The numeric user is what the runtime checks for; the image has no user database to resolve a name in.
USER 65532

EXPOSE 8080

# The health endpoint answers from this process alone, so there is no shell here and nothing to
# call but the Node binary itself.
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["/nodejs/bin/node", "-e", "fetch('http://127.0.0.1:8080/healthz').then(r => process.exit(r.ok ? 0 : 1), () => process.exit(1))"]

CMD ["/app/.output/server/index.mjs"]

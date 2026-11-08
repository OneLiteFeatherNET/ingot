# Spec Delta

## Purpose

The separately deployed dashboard image. A Nitro server serves the dashboard and proxies every other path to the Ingot
server. The image is hardened and reads its configuration from the environment when it starts.

## ADDED Requirements

### Requirement: One source for the dashboard segments

The dashboard's top-level route segments SHALL be defined once, in `reposilite-frontend/src/router/dashboard-segments.json`.
The Vue router, the dashboard server, the backend reserved segment check and the Helm chart SHALL read that definition, and
a test SHALL fail when any of them differs from it.

#### Scenario: The analytics segment is in the list

- **WHEN** the segment list is read after PR #69 has merged
- **THEN** it contains `analytics`, and the router and the backend both answer `/analytics` with the dashboard document

#### Scenario: A page without its segment fails the frontend suite

- **WHEN** a route is added to the router with a top-level segment that is not in the segment list
- **THEN** the frontend test suite fails and names the missing segment

#### Scenario: The backend agrees with the segment list

- **WHEN** the backend test suite runs
- **THEN** `DashboardPaths.APP_ROUTES` covers exactly the segments of the shared list

### Requirement: Dashboard routes answer with the document

The dashboard server SHALL answer every dashboard route, including nested addresses below a segment, with the dashboard
document `index.html`. It SHALL answer the static assets and the favicon from the build. It SHALL proxy every other path to
the Ingot server, including `/api/*`, repository paths, javadoc and badges, and the WebSocket upgrade of the console.

#### Scenario: A reload of a nested dashboard address

- **WHEN** a client requests `GET /settings/maven/repositories/releases` with a browser accept header
- **THEN** the response is `200` with the dashboard document and `text/html`

#### Scenario: A repository path is proxied

- **WHEN** a client requests `GET /releases/com/example/app/1.0/app-1.0.jar` and the backend answers with that file
- **THEN** the dashboard server returns the same bytes and status without buffering the body

#### Scenario: The console socket is upgraded

- **WHEN** a client opens a WebSocket on the console path through the dashboard server
- **THEN** the connection is upgraded and messages flow in both directions

### Requirement: Runtime configuration from the environment

The dashboard server SHALL read `INGOT_BACKEND`, `INGOT_BASE_PATH` and `INGOT_MAX_UPLOAD_SIZE` when it starts, with the
defaults `http://ingot-backend:8080`, `/` and `1024m`. It SHALL refuse to start when the backend is not an absolute `http` or
`https` URL, when the base path does not start with `/`, or when the upload size cannot be parsed. It SHALL accept
`INGOT_RESOLVER` without using it and log that it is ignored.

#### Scenario: A base path is applied at start

- **WHEN** the server starts with `INGOT_BASE_PATH=/ingot/`
- **THEN** the document's base element is `/ingot/`, the assets resolve below `/ingot/`, and the router takes `/ingot/` as its base

#### Scenario: An invalid backend stops the start

- **WHEN** the server starts with `INGOT_BACKEND=ingot-backend:8080`
- **THEN** the process exits before it listens and the message names `INGOT_BACKEND`

#### Scenario: An oversized upload is refused at the proxy

- **WHEN** a client sends a body larger than `INGOT_MAX_UPLOAD_SIZE` to a proxied path
- **THEN** the dashboard server answers `413` and does not forward the request

### Requirement: Health endpoint independent of the backend

The dashboard server SHALL answer `GET /healthz` with `200` from its own process, without contacting the Ingot server, so that
a restart of the server does not restart the dashboard.

#### Scenario: The health endpoint answers while the backend is down

- **WHEN** the Ingot server is unreachable and a client requests `GET /healthz`
- **THEN** the response is `200`

### Requirement: Security headers equal to the previous configuration

The dashboard server SHALL send on every response the headers `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: same-origin` and the Content Security Policy that the previous nginx configuration sent, with the same
values. A test SHALL compare the values with a fixture.

#### Scenario: A proxied response carries the headers

- **WHEN** a proxied API response is returned
- **THEN** it carries the same `Content-Security-Policy` value as the dashboard document

### Requirement: Hardened dashboard image

The dashboard image SHALL use a distroless Node runtime pinned by tag and digest, SHALL run as UID `65532`, SHALL start with a
read-only root filesystem, SHALL contain no shell and no package manager, SHALL listen on port `8080`, and SHALL be published for
`linux/amd64` and `linux/arm64`. Releases SHALL carry an SBOM and provenance. The all-in-one image SHALL NOT change.

#### Scenario: The container runs as the non-root user

- **WHEN** the dashboard image is started with `--read-only`, all capabilities dropped and `no-new-privileges`
- **THEN** the process runs as UID `65532`, the root filesystem is writable nowhere, and `GET /` answers `200`

#### Scenario: The image has no shell

- **WHEN** a command that needs a shell is run inside the container
- **THEN** the command fails because no shell exists in the image

### Requirement: Compatibility of the split deployment

The dashboard image SHALL keep no writable state, so that an existing compose file that mounts a `tmpfs` owned by UID `101`
still starts, with the mount unused. The split container how-to SHALL describe the new UID, the ignored `INGOT_RESOLVER` and
the runtime `INGOT_BASE_PATH`, and the compose file in the repository SHALL NOT mount `tmpfs` for the dashboard.

#### Scenario: A compose file with a tmpfs owned by 101 still starts

- **WHEN** an existing split compose file still mounts a `tmpfs` owned by `101` on the dashboard container
- **THEN** the dashboard container starts and serves the dashboard, and the mount is not written to

#### Scenario: The repository compose file has no dashboard tmpfs

- **WHEN** the repository compose file for the split deployment is read
- **THEN** the dashboard service mounts no `tmpfs`

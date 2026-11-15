# Design

This design covers three deliverables that ship together: a deploy target on the server, a Nitro based dashboard image, and
a Helm chart in this repository. The decisions D1 to D12 were made by the maintainer before this proposal was written. They
are recorded here with their alternatives so that a reviewer can see what was weighed. The open questions at the end are
the ones the maintainer still has to answer before the code pull request is opened.

## Context

- The server image `ghcr.io/onelitefeathernet/ingot` is a drop-in replacement for `dzikoysk/reposilite`. Its entrypoint
  starts as root and drops to the service account, so `PUID`, `PGID` and the chown of a foreign volume work. Nothing about
  that changes here.
- The server decides whether it serves the dashboard with `LocalConfiguration.defaultFrontend`, which is read in
  `FrontendPlugin` (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/application/FrontendPlugin.kt`). With
  `false`, the dashboard routes are not registered, but the settings endpoint and the custom frontend handler still are.
- The dashboard image today is `reposilite-frontend/Dockerfile`: a Node build stage and an `nginx-unprivileged` run stage.
  Its configuration is `reposilite-frontend/docker/default.conf.template`, rendered by the base image's envsubst
  entrypoint. Its route regex is checked by `DashboardImageRoutesTest` against `DashboardPaths.APP_ROUTES`.
- The dashboard's own routes live in `reposilite-frontend/src/router/routes.js`. The backend's copy of the same list is
  `DashboardPaths.kt`. Today the two are kept in step by hand, and a test that reads the nginx template is the only check.
- The Helm chart staging uses is `reposilite` 1.4.5 from `https://helm.reposilite.com/` (appVersion 3.6.6). It is pinned in
  the HelmRelease `reposilite` in namespace `reposilite-staging` with `version: =1.4.5`, and it sets the image to
  `ghcr.io/onelitefeathernet/ingot:nightly` with a digest pinned by a Flux post renderer. The post renderer targets the
  Deployment by the name `reposilite`, and adds a priority class.
- The console is a WebSocket (`ConsolePlugin`, `ws` route), and the dashboard's event stream uses `EventSource`
  (`reposilite-frontend/src/store/console/connection.js`). Both must survive the proxy.
- Release versions come from `release-please-config.json` (`release-type: simple`, manifest `1.1.1`). The `changes` job in
  `.github/workflows/release-please.yml` decides whether a push needs a nightly, with a deny list.

## Goals

- One named switch for the deploy shape, usable from the command line, a JVM property and an environment variable.
- A dashboard image that is hardened as far as a web application allows, and whose routes cannot drift from the router.
- A chart that installs either shape, and that an existing staging release can move to with a values diff only.
- Every existing name that the drop-in contract keeps stays exactly as it is.

## Non-goals

- A `worker` target, or any target beyond `all` and `server`. The value list is closed in this change. A later `worker`
  target (search index rebuild, cleanup, audit purge) is the expected next value and needs its own proposal, because it
  changes what a process does and not only what it serves.
- Changing the server image, its entrypoint or any name in the drop-in contract.
- Replicas above one on the server. The chart exposes `replicas`, as the upstream chart does, and the existing warning in
  the Kubernetes how-to remains.
- A separate chart repository, a dashboard-only chart, nightly charts and an operator.

## Decisions

### D1. Deploy target on the server

A new local setting `target` has the values `all` and `server`. The effective value is taken from the first of these that is
set, in this order:

1. the command line option `--target <value>`,
2. the system property `-Dingot.target=<value>`,
3. the environment variable `INGOT_TARGET`.

When none of the three is set, the legacy setting decides: `defaultFrontend=false` (from `configuration.cdn`, or from
`INGOT_LOCAL_DEFAULTFRONTEND`, `REPOSILITE_LOCAL_DEFAULTFRONTEND` or the matching system properties) means `server`, and
anything else means `all`. When a target is set and the legacy setting disagrees with it, the target wins and one warning
is logged at start that names both values. The mapping `server` to `defaultFrontend=false` is one-directional: setting
`target` never writes `defaultFrontend`.

`server` runs the same code path as `defaultFrontend=false` today: the dashboard routes are not registered, the settings
endpoint and the custom frontend handler still are, and the reserved segment warnings in `DashboardPaths` are unchanged.
An empty value counts as not set. The resolver is a pure function of the three sources, so the tests pass maps in and read no environment. An unknown value
in any source stops the server at start with a message that lists `all` and `server`. It does not fall back, because a
silent fallback from `server` to `all` would publish the dashboard on an address that was meant to be API only.

The precedence is decided: the command line option `--target` first, then `-Dingot.target`, then `INGOT_TARGET`, then the
legacy alias (`defaultFrontend`, read from `REPOSILITE_LOCAL_DEFAULTFRONTEND` or `INGOT_LOCAL_DEFAULTFRONTEND`, from the
configuration file, or from their system properties).

Alternatives considered:

- A `--dashboard=false` flag or a second boolean. Rejected: it repeats the problem `defaultFrontend` has, a name that
  describes a switch and not the outcome, and it leaves no room for the next shape.
- Separate jars or separate server bundles per target. Rejected: the jar, its plugins and its database migrations are the
  same, and a second artifact doubles what a release has to publish and test.
- A key in `configuration.cdn`. Rejected for this change: the three sources cover the image, the compose file and the Helm
  chart, and a file key would be a second place to keep in step. Open question 1.
- Loki and Mimir style `-target` with the values `all`, `server` and `worker`. Accepted for the naming. `worker` is not
  built here, which is the non-goal above.

### D2. Dashboard route list from one source

The dashboard's top-level segments move to `reposilite-frontend/src/router/dashboard-segments.json`, a JSON array of
segment names (`browse`, `search`, `dashboard`, `tokens`, `console`, `settings`, `maintenance`, `audit`, `access`,
`account`). Three consumers read it:

- `routes.js` imports it. A Vitest test asserts that every top-level `path` in `ROUTES` has its segment in the JSON (the
  root and the catch-all route have none and are excluded), and that every segment in the JSON has a route. A page added without its segment fails the frontend suite, which is the
  "cannot be forgotten" guarantee.
- The Nitro server imports it at build time to decide what is a dashboard path.
- `DashboardPathsTest` (Kotlin) reads the same file and asserts that `DashboardPaths.APP_ROUTES` covers it. The nginx
  template and `DashboardImageRoutesTest` are removed, because nginx is removed (D3).

The Helm chart cannot read files outside its directory, so `charts/ingot/files/dashboard-segments.json` is a committed copy.
A CI step runs `cmp` against the source and fails on drift. This is the one duplicate, and it is checked rather than
generated.

The `analytics` page lands on main with PR #69, before this change is implemented. The segment list therefore must
include `analytics`, and the frontend test of this section fails if the page is added without its segment. The branch is
rebased on main after PR #69 merges, before task 3.2 is implemented.

Alternatives considered:

- Generating `DashboardPaths.kt` from the JavaScript at build time. Rejected: it adds a generator to the Gradle build and
  makes the Kotlin file a build output that reviewers cannot read in the diff.
- Reading the routes from `routes.js` directly in the Helm chart. Rejected: Helm templates cannot execute JavaScript, and
  the chart still needs a file.

### D3. Nitro instead of nginx for the dashboard image

The dashboard image serves the built single page application with a Nitro server on the `node` preset, started as
`node server/index.mjs` from the Nitro output. Nitro answers the dashboard routes, the static assets and `/healthz`
itself, and proxies every other path to `INGOT_BACKEND` with the same rules that the nginx location block has today: no
request or response buffering, a 300 second read and send timeout, the forwarding headers, and the WebSocket upgrade
headers.

Reasons, all from the constraints in the request. A distroless image has no shell, so nginx's envsubst entrypoint cannot run,
and nginx would need a second process or a generated configuration to read its environment at start. Nitro reads its
environment in Node. nginx also needs writable paths for its temporary files, which the compose file mounts as tmpfs; the
Node server writes nothing. One runtime for both the static and the proxy role keeps one set of security settings.

Alternatives considered:

- Keep nginx and generate its configuration in a build stage. Rejected: the backend URL and the base path are required to
  be runtime values, and a generated file fixes them at build time, which is what the current base path build argument
  already does and what the request asks to change.
- A distroless image with a small Go or Rust reverse proxy. Rejected: a second language and a second toolchain for one
  proxy, when the Node runtime is already in the image for the dashboard.
- Keep nginx as the proxy and serve the static files from a separate container. Rejected: that is the split the request
  is trying to make smaller, and it doubles the number of images for one dashboard.

The known costs are recorded as risks. In particular the proxy is written by hand for the WebSocket upgrade, because h3
does not forward upgrades, and its throughput for large artifacts must be measured against nginx before the release (see
Risks).

### D4. Runtime configuration from the environment at start

The variables are `INGOT_BACKEND` (default `http://ingot-backend:8080`), `INGOT_BASE_PATH` (default `/`) and
`INGOT_MAX_UPLOAD_SIZE` (default `1024m`). They are read when the process starts. The Nitro server checks them and refuses to
start on a value it cannot use, such as a backend that is not an absolute `http` or `https` URL, or a base path that does
not start with `/`.

The base path is applied this way. The build emits assets with a relative base (`base: "./"`), so their URLs resolve against
the document. The `<base href>` element that the existing `mountPoint` plugin writes is filled in at start from
`INGOT_BASE_PATH`: the server reads `index.html` once and replaces one placeholder in memory. The router is created without an
explicit base, so vue-router takes its base from that element. That behaviour of vue-router is to be verified by a test in
the change (task 4.1), and it is why the router is not given a base of its own.

`INGOT_RESOLVER` is no longer needed, because Node uses the resolver of the container or cluster. It is accepted and logged
as ignored, so a compose file that still sets it starts. The sub-path build argument `INGOT_BASE_PATH` in the Dockerfile
goes away, and the published image is built once for every base path. This is a change for the operators of the split
deployment and is listed under Impact.

Alternatives considered:

- An envsubst template, as now. Rejected by the request, and impossible without a shell.
- A `window.__INGOT__` script injected at start. Rejected: a second configuration channel in the page that the router and
  the API client would both have to read, while the `<base>` element already anchors every URL the page uses.

### D5. Health endpoint and probes

`/healthz` is answered by the Nitro server and does not depend on the backend. It returns `200` with a short body once the
server is listening. The image's `HEALTHCHECK` calls it in exec form with the Node binary, since there is no shell. The
Helm chart's dashboard probes use it. The server's probes stay on `/` for `allInOne`, as the upstream chart does, and on
`/api/status/health` for the `server` workload in `split` mode, where `/` is not a page.

Liveness that depends on the backend would restart the dashboard whenever the server restarts, which is the coupling the
split is meant to remove. Readiness does not check the backend either, for the same reason: the dashboard can be up while
the server is not, and it answers with the server's error.

Alternative considered: `GET /` as today. Rejected: it returns the document, which needs the rendering path and a cache
decision for every probe.

### D6. Security headers and caching

The headers are the same values as `default.conf.template`: `X-Content-Type-Options nosniff`, `X-Frame-Options DENY`,
`Referrer-Policy same-origin`, and the Content Security Policy string exactly as it is there. They are added to every
response the dashboard server sends, including responses it proxies, because nginx's `add_header ... always` adds them to
proxied responses too. The server sends no version banner, which is what `server_tokens off` did. Cache rules are the ones of the
template: hashed assets for one year as `immutable`, the document with `no-cache`, the favicon for seven days.

A test in `reposilite-frontend` holds a fixture of those values and compares the header function against it, so a change
to one of them is a visible change in the test.

### D7. Hardened dashboard image

The run stage is a distroless Node runtime, pinned by tag and digest, with Renovate raising both together as for the other
images. The starting point is `gcr.io/distroless/nodejs24-debian13:nonroot`. Its tag and digest are resolved during
implementation (task 4.4), and the Node major must match the build stage. The image sets
`USER 65532`, which is the UID of the distroless non-root account. It has no `RUN` instruction in the run stage, no shell
and no package manager. It exposes port 8080, and the build is for `linux/amd64` and `linux/arm64`. The build stage keeps
`--platform=$BUILDPLATFORM`, as the other Dockerfiles do, because the output is JavaScript.

Its compose and Kubernetes settings are documented, not imposed: `read_only: true`, `cap_drop: [ALL]`,
`security_opt: [no-new-privileges:true]`, and in Kubernetes `readOnlyRootFilesystem`, `runAsNonRoot` and
`seccompProfile: RuntimeDefault`. The Helm chart sets all of them by default for the dashboard (D10). The Node server
writes nothing at run time, so the read-only root filesystem needs no tmpfs mount.

Releases carry SBOM and provenance, and nightlies carry neither, as for the server image. The change detection job in
`release-please.yml` keeps the dashboard image on the nightly path, and `charts/` joins its deny list so a chart-only push
does not rebuild the images (task 4.5).

### D8. Helm chart in this repository

The chart is `charts/ingot`. It is published as OCI to `oci://ghcr.io/onelitefeathernet/charts/ingot` by the release
workflow, after the container job, on releases only. Its `version` and `appVersion` are both set to the application release
by release-please, through two `yaml` entries in `extra-files` of `release-please-config.json`. The chart therefore has the
application's version number and is never published for a commit that is not a release.

Alternatives considered:

- A separate chart repository, as the upstream project has. Rejected: the chart and the image must move together (the
  routing reads the same segment list, and the dashboard image name and tag follow the server), and two repositories mean
  two release trains that have to be kept in step by hand.
- Keep the upstream chart and add split mode through `postRenderers`. Rejected: the upstream chart has no second workload
  and no routing, and a post renderer would be a second copy of the chart's templates in the HelmRelease.
- Nightly charts. Rejected: the charts would have no version that means anything, and every push would publish one.

### D9. Chart values

The value keys are the keys of the upstream chart 1.4.5, with the same meaning and the same defaults, so that a values file
for the upstream chart renders with this chart. The additions are `mode` (`allInOne` by default, or `split`), a `dashboard`
block with its own `image`, `resources`, `probes`, `securityContext`, `env`, `service`, and `replicas`, `serviceMonitor`
(disabled by default, needs the Prometheus Operator CRDs), `podDisruptionBudget` (disabled by default; when enabled it sets
`maxUnavailable: 1`, because `minAvailable: 1` on a single replica blocks every node drain) and `httpRoute` (disabled by
default, mutually exclusive with `ingress`). `values.schema.json` enforces the enum for `mode` and the exclusion.

In `allInOne` mode the chart renders one Deployment, named as the upstream chart names it. In `split` mode it renders two
Deployments and two Services. The server keeps the name and the Service of the upstream chart, and the dashboard gets
`<fullname>-dashboard`. The server workload sets `INGOT_TARGET=server`, and the dashboard workload sets `INGOT_BACKEND` to the
server Service.

### D10. Security defaults

The server's security context keeps the upstream default, `containerSecurityContext.enabled: false` and
`podSecurityContext.enabled: false`. The server starts as root so that `PUID`, `PGID` and the chown of an existing volume
keep working. `runAsUser` and `runAsGroup` are documented as the opt-in for an already correct volume, as the image's
Dockerfile describes, and the chart does not set them. The dashboard's defaults are hardened: the image's UID 65532, read-only
root filesystem, dropped capabilities, `allowPrivilegeEscalation: false`, `runAsNonRoot: true` and the `RuntimeDefault`
seccomp profile. `automountServiceAccountToken` defaults to `false` for both workloads, as upstream does for the server.

### D11. Routing in split mode

The Ingress (or the HTTPRoute) is generated from `charts/ingot/files/dashboard-segments.json`. For each segment there is a
`Prefix` path to the dashboard Service. `/` is an `Exact` path to the dashboard, because the overview is a page. `/assets`
and `/favicon.png` go to the dashboard, and `/healthz` does not need a rule. Everything else falls through to a `Prefix` path
`/` to the server Service. Kubernetes gives an `Exact` match precedence over a `Prefix` match of the same path, so the
overview stays on the dashboard and the repositories stay on the server. A repository whose id is one of the segments cannot
be downloaded through the ingress, which is the same limit the nginx container has, and the how-to says so.

The HTTPRoute form renders the same rules with `PathPrefix` and `Exact` matches and the optional `parentRefs` from values.
Both forms are tested with `helm template` in the chart tests (task 5.1).

### D12. Migration from the upstream chart

The migration is a values diff and reuses the same volume. Decided: the migrated values set `nameOverride` and
`fullnameOverride` to `reposilite`, and the equivalence test of task 5.3 covers it. Three details decide whether it is one:

- **Names.** The upstream helpers name the resources after the release, but only when the release name contains the chart
  name. A release called `reposilite` installed from `reposilite` is named `reposilite`. The Ingot chart is named `ingot`, so
  without an override a release called `reposilite` would render `reposilite-ingot`. The Deployment selector is immutable, so
  that would fail the upgrade. The migrated values therefore set `nameOverride: reposilite` and `fullnameOverride: reposilite`.
  The chart tests render the staging values through both charts and assert that the selector labels, the Deployment name,
  the Service name, the PersistentVolumeClaim name and the container port are identical.
- **Flux.** The HelmRelease post renderer patches the Deployment named `reposilite` and rewrites the image of
  `ghcr.io/onelitefeathernet/ingot` by digest. Both keep matching because the name and the image line are unchanged.
- **Data.** Staging keeps `persistence.enabled: false` and uses an `emptyDir` at `/app/data`, so no volume moves. An operator
  with a PersistentVolumeClaim keeps it, because the chart uses `existingClaim` or the same fullname, and the upstream
  claim carries `helm.sh/resource-policy: keep`.

Rolling back means installing the upstream chart again at version 1.4.5 with the old values. The migration how-to lists the
steps and the values table. The mapping table is in the documentation task (task 5.6), and the equivalence test in task 5.3 is the
check that it is complete for the keys staging uses.

## Risks

- **WebSocket and streaming in the proxy.** h3 does not forward protocol upgrades, so the console WebSocket needs a
  hand-written upgrade handler on the Node HTTP server. It is covered by the container test (task 4.3) that opens the console
  socket through the dashboard image. The event stream (`EventSource`) must not be buffered. The test reads a first event
  before the stream ends.
- **Throughput and memory.** Artifacts stream through Node, which is slower than nginx for large files. Before the release
  task, the change measures a 500 MB download and a 500 MB upload through both images on the same host and records the numbers
  in the pull request. A large regression is a blocker for the release, not for the merge of the branch.
- **Runtime base path depends on vue-router reading the document base.** Covered by the frontend test in task 4.1. If the
  router does not read it, the fallback is an explicit `base` passed to the router from an injected value.
- **Helm name equivalence.** A missed override breaks the upgrade on an immutable selector. The equivalence test (task 5.3)
  is the guard, and the migration how-to says to run `helm diff upgrade` first.
- **The dashboard image UID changes without breaking split deployments.** The image keeps no writable state, so a compose
  file that mounts a tmpfs owned by 101 keeps starting, and the mount is unused. The change still drops those lines from
  `docker-compose.split.yml` and the how-to, and states that `INGOT_RESOLVER` is ignored.
- **Nightly filter.** A chart-only change must not rebuild the images. The deny list gains `charts/`, and a new path that
  reaches the image is still counted as relevant by default.
- **Distroless digests and Renovate.** The digest is resolved when the change is made and is then kept by Renovate. A new
  Node major in the distroless image changes the runtime, so the Node version is pinned to the build stage's version.

## Migration plan

1. Land the proposal (this change, `docs(openspec)`).
2. Land the server target (D1) first. It is additive and changes nothing for a deployment that sets no target.
3. Land the route source (D2) and the Nitro dashboard image (D3 to D7) together, because the image and its test change in one
   step. Both start from main after PR #69 (`analytics`) has merged. The compose file and the split how-to change in the same commit series.
4. Land the chart (D8 to D12) and the release wiring. The first chart version is the next application release.
5. Staging moves last: the values diff from the migration how-to on a staging branch, `helm diff upgrade`, then the
   HelmRelease changes its chart from `reposilite` to the OCI chart. Only then is the upstream repository reference removed.

## Open questions

Decided during review and no longer open: the precedence of the target sources (D1), the chart name overrides (D12),
the `analytics` segment (D2), the distroless tag and digest (resolved in task 4.4), and the breaking marker (none, see
the proposal).

1. **Target in the configuration file.** The proposal leaves `target` out of `configuration.cdn`, so no `INGOT_LOCAL_TARGET`
   exists. Should a file key be added later?
2. **WebSocket proxy.** Is the hand-written upgrade handler (see Risks) accepted? The alternative, the browser opening the
   console socket on the server's own address, would expose the server to the browser in the split deployment, which is the
   case the single origin is meant to avoid. That alternative is not covered by this design.
3. **HTTPRoute or Ingress by default.** The chart defaults to Ingress, as the upstream chart does. Should the default be the
   Gateway API when the cluster has it?
4. **Chart visibility.** The OCI package on `ghcr.io` must be made public once, by hand, after the first push. Is that a
   step the maintainer accepts in the release notes?
5. **Chart tests.** The design uses `helm template` assertions in a small script under `charts/ingot/tests/`, to avoid an
   extra Helm plugin in CI. The maintainer may prefer the `helm-unittest` plugin.

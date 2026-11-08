# Proposal

## Why

Ingot ships one image that serves both the repository and the dashboard, and a second image, `ingot-dashboard`, that
serves the dashboard from nginx in front of a server that has its dashboard switched off. The split is only usable by
hand: the switch is a boolean called `defaultFrontend` (`INGOT_LOCAL_DEFAULTFRONTEND=false`) whose name says nothing
about what the instance becomes, and the dashboard image has no Kubernetes story at all. The staging environment deploys
Ingot with the upstream Reposilite Helm chart (`reposilite` 1.4.5), which has no notion of a split and cannot express the
hardening the dashboard should have.

Three things follow. Operators need a named deploy mode rather than a boolean, in the style of Grafana Loki and Mimir
`-target`. The dashboard image needs a runtime that reads its configuration at start, serves every dashboard route from
one list that the Vue router also uses, and runs as a non-root user with a read-only root filesystem and no shell. And
Ingot needs its own Helm chart, so that the split deployment is one values file and a migration from the upstream chart
is a values diff rather than a new deployment written by hand.

## What Changes

- **Deploy target.** A new server switch `target` with the values `all` (default, today's behaviour) and `server` (API and
  Maven endpoints only, no dashboard). It is read from the command line option `--target`, the system property
  `ingot.target` and the environment variable `INGOT_TARGET`. The existing `defaultFrontend` setting keeps working and
  means the same as `server` when it is `false`. When both are given, the target wins and a mismatch is logged once.
  An unknown value stops the server at start with the list of valid values.
- **Dashboard route list as one source.** The dashboard's top-level segments, including `analytics` from PR #69 which lands on main first, move into one JSON
  file that the Vue router imports. A frontend test fails when a page is added without its segment, and the backend test that compares the
  reserved segments reads the same file. The nginx template and its regex test are removed.
- **Dashboard image on Nitro.** The dashboard image serves the built single page application from a Nitro server (node
  preset) and proxies every non-dashboard path to the server, as the nginx configuration does today. It has a health
  endpoint for probes, the same security headers as the current nginx configuration, and its configuration (backend URL,
  base path, upload limit) comes from environment variables read at start. There is no envsubst template.
- **Hardened dashboard image.** The image is built on a distroless Node runtime pinned by tag and digest, runs as UID
  65532, works with a read-only root filesystem, has no shell and no package manager, listens on port 8080 and is built
  for `linux/amd64` and `linux/arm64`. Releases carry an SBOM and provenance, as the other images do.
- **Helm chart `charts/ingot`.** Published as OCI to `oci://ghcr.io/onelitefeathernet/charts/ingot` on every release,
  with the chart version and appVersion following the application release through release-please. Value `mode` selects
  `allInOne` (default: one workload of the all-in-one image, like the upstream chart) or `split` (a server workload with
  `INGOT_TARGET=server`, a dashboard workload of the Nitro image, Services for both, and an Ingress or HTTPRoute that sends
  dashboard paths to the dashboard and every other path to the server).
- **Migration from the upstream chart.** The chart keeps the value keys of the upstream chart 1.4.5 that staging uses, with
  the same meaning, so moving a release is a values diff and reuses the same volume at `/app/data`. A migration table in
  the documentation lists every key, and a test renders the staging values through both charts and compares selectors,
  resource names, the volume and the container port.
- **CI.** Chart lint and chart-testing install on kind for both modes. A container test for the dashboard image checks the
  route answers, the proxy, the WebSocket upgrade, the upload limit and a run as UID 65532 with a read-only root
  filesystem. The container images job also scans the dashboard image and the nightly change filter ignores `charts/`.
- **Documentation.** A how-to for split mode through the chart, a reference page for `INGOT_TARGET` and its sources, a
  migration how-to from the upstream chart, and updates to the split container how-to, the Kubernetes how-to and the
  environment variables reference.

### Out of scope

- A `worker` target (search index rebuild, cleanup, audit purge). It is named here as the likely next target and is not
  built in this change. The target values are a closed list that a later change can extend.
- A configuration file key for the target. The three sources listed above are the only ones in this change.
- Path-level or per-route dashboard deployment, and any change to the Maven, API or repository behaviour.
- Replicas above one for the server. The Helm chart exposes `replicas`, but the high availability work in
  `docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md` decides when that is supported.
- Renaming any of the names that the drop-in contract keeps (`/app/data`, `/var/log/reposilite`, the `reposilite` user,
  `reposilite.db`, `configuration.cdn`, the `{{REPOSILITE.*}}` placeholders, `REPOSILITE_OPTS`).
- A separate chart repository, a Helm chart for the dashboard alone, and an Operator or CRD.
- Publishing nightly charts. Only releases publish a chart.

## Capabilities

### New Capabilities

- `deploy-modes`: the `target` switch with its three sources, the precedence rules, the legacy `defaultFrontend` mapping,
  the start-up refusal of unknown values, and what the `server` target serves.
- `dashboard-image`: the Nitro dashboard server, the single route source, the runtime environment, the health endpoint,
  the security headers, the proxy behaviour, and the hardened distroless image.
- `helm-chart`: the chart layout and values, the `allInOne` and `split` modes, the generated routing, the security
  defaults, the migration from the upstream chart, and the publishing on release.

## Impact

- **Operators running the all-in-one image:** no change. No configuration key, environment variable, volume, manifest or
  plugin changes. The new `INGOT_TARGET` is optional and its absence means today's behaviour. An unmodified upstream
  compose file still starts with only the image line swapped, a data directory from Reposilite is adopted without a
  migration step, and `PUID`, `PGID` and `REPOSILITE_OPTS` keep their effect. The server Dockerfile does not change.
- **Operators running the split deployment (`ingot-dashboard`):** not breaking. The dashboard image keeps no writable
  state and needs no tmpfs, so a compose file that still mounts a tmpfs owned by 101 keeps working, and the mount is simply
  unused. `INGOT_RESOLVER` is accepted and ignored, because Node resolves names through the container's own resolver. The
  sub-path base is set at start with `INGOT_BASE_PATH` rather than as a build argument. The compose file in the repository
  drops its now useless tmpfs lines, and `docs/gitbook/how-to/run-split-dashboard-container.md` is updated in the same change.
  The all-in-one image is not affected, and the drop-in contract does not cover the dashboard image.
- **Staging and other Helm users of the upstream chart:** no action until they choose to move. The move is described in
  the migration how-to and is a values diff. Rolling back means installing the upstream chart again with the same values.
- **Code:** `reposilite-backend` (the deploy target and the route test, in the existing `com.reposilite` packages),
  `reposilite-frontend` (router segments, the Nitro server, the Dockerfile), `charts/ingot` (new),
  `docker-compose.split.yml`, `release-please-config.json`, `.github/workflows/` and `docs/gitbook/`.
- **Commits:** the change ships under one type and scope. The branch uses commits of the types `test`, `feat`, `docs`,
  `ci` and `build`, all with the scope `deploy`. The pull request title, squash-merged onto main, is:

  `feat(deploy): add deploy modes, a nitro dashboard image and a helm chart`

- **Breaking marker:** none. The title above carries no `!` and no commit carries a `BREAKING CHANGE:` footer.

## Verification

- Targeted tests only for each commit, with `flock ~/.cache/claude-heavy.lock` around Gradle and Testcontainers runs.
- `openspec validate add-deploy-modes --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-deploy-modes` prints nothing.
- The code pull request is green in the container images job for both images and both architectures, and in the chart job
  for both modes.

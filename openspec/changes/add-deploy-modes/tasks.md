# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `deploy`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the resolver and the server take their inputs as values, and any time source is injected), no shared mutable state,
a temporary directory per test, and assertions with messages. Container tests run under
`flock ~/.cache/claude-heavy.lock` and use a fresh container per test class. Gradle runs are targeted, never the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-deploy-modes`: this change (proposal, design, capability specs, tasks) and nothing else.
  Verify: `openspec validate add-deploy-modes --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-deploy-modes`
  prints nothing.

## 2. Deploy target on the server

- [ ] 2.1 `test(deploy)`: write failing tests for the target resolver as a pure function of three inputs (the command line
  value, a map of system properties, a map of environment variables) plus the legacy `defaultFrontend` value. One behaviour per
  test: the default is `all`; each of `all` and `server` is accepted from each source; the precedence flag, property,
  environment; an empty value is not set; `sever` is refused and the message names the source and lists both values; the legacy
  `false` means `server`; a target and a disagreeing legacy value give the target and exactly one warning, captured with a test
  appender; agreement logs nothing. Add container tests (`containerTest`) for `INGOT_TARGET=server` (`/api/status/health` answers
  `200`, `/browse` is not the dashboard document), for `INGOT_LOCAL_DEFAULTFRONTEND=false` (same answers), and for an unmodified
  upstream compose file with only the image swapped (the server starts and adopts `/app/data`). Verify: the resolver tests fail
  on the missing class, and the container target test fails on the image built before 2.2.
- [ ] 2.2 `feat(deploy)`: add `DeployTarget` and its resolver, read `--target` in `ReposiliteParameters`, read `-Dingot.target`
  and `INGOT_TARGET` at start, use the resolved target in `FrontendPlugin` in place of the direct read of `defaultFrontend`, and
  log the one mismatch warning. Refuse an unknown value before binding the port. Verify: the tests of 2.1 pass, the existing
  `FrontendPlugin` and `LocalConfigurationFactory` tests pass without edits, and `LocalConfigurationFactoryTest` shows no new key.
- [ ] 2.3 `docs(deploy)`: add `docs/gitbook/reference/deploy-targets.md` with the three sources, the precedence, the legacy
  mapping and the refusal, add its entry to `docs/gitbook/SUMMARY.md`, and add `INGOT_TARGET` to
  `docs/gitbook/reference/environment-variables.md` and `--target` to `docs/gitbook/reference/configuration-and-startup.md`.
  Verify: every variable, option and property name in the pages appears in the resolver test names or the container tests,
  and the page's examples start a container in the containerTest.

## 3. One source for the dashboard segments

- [ ] 3.1 `test(deploy)`: write failing tests. In `reposilite-frontend`, a Vitest test that the top-level segments of `ROUTES`
  (excluding the root and the catch-all) equal the segments in `src/router/dashboard-segments.json`, with the list read as a
  file. In `reposilite-backend`, a `DashboardPathsTest` that reads the same JSON file (path relative to the Gradle project) and
  asserts the segments of `DashboardPaths.APP_ROUTES` equal it. Verify: both fail, because the JSON file does not exist yet.
- [ ] 3.2 `feat(deploy)`: rebase on main after PR #69 (`analytics`) has merged, then add `reposilite-frontend/src/router/dashboard-segments.json`
  with `analytics` in it, have `routes.js` import it, and leave
  `DashboardPaths.kt` unchanged except where its test reads the list. Verify: 3.1 passes, and the existing router tests
  (`create.test.js`, `routes` consumers) pass without edits.

## 4. Nitro dashboard server and hardened image

- [ ] 4.1 `test(deploy)`: write failing Vitest tests for the dashboard server's pure modules, each fed its inputs: the route
  classifier (every segment, a nested address, `/`, `/assets/x`, `/favicon.png`, `/healthz`, and repository-looking paths);
  the security header function against a fixture of the values in the current nginx template; the environment parser
  (defaults, `INGOT_BACKEND` refused without an absolute `http` or `https` URL, `INGOT_BASE_PATH` refused without a leading slash,
  `INGOT_MAX_UPLOAD_SIZE` parsed as bytes, `INGOT_RESOLVER` accepted and reported as ignored); the upstream URL join; the base
  element substitution. Add a frontend test, with jsdom and an explicit `<base href="/ingot/">` in the document, that the router
  created without a base resolves `/ingot/settings/x` as `settings/x`, which is the assumption of the design. Verify: the tests
  fail on the missing modules.
- [ ] 4.2 `feat(deploy)`: add `reposilite-frontend/server/` with the Nitro handler on the `node` preset, the modules of 4.1, the
  catch-all proxy with the streaming and timeout settings of the nginx template, the WebSocket upgrade handler on the Node HTTP
  server for the console path, the upload limit, the `/healthz` route, the security headers on every response, and the
  placeholder replacement in `index.html` at start. The build runs the Vite build with `base: "./"` and then the Nitro build.
  Verify: 4.1 passes, `npm run build` produces `.output/server/index.mjs`, and `npm run eslint` passes on the new files.
- [ ] 4.3 `test(deploy)`: write failing container tests for the dashboard image, run with `--read-only`, all capabilities dropped
  and `no-new-privileges`, against a real `ingot:ci` backend container: every segment and a nested reload answer the document;
  `/healthz` answers `200` with the backend stopped; `/api/status/health` and a repository path are proxied; a WebSocket on the
  console path is upgraded and receives a message; the first event of the event stream arrives before the stream ends; a body
  above `INGOT_MAX_UPLOAD_SIZE` gets `413`; the process runs as UID `65532`; the security headers equal the fixture of 4.1.
  The suite replaces the nginx checks of the current container tests. Verify: the suite fails on the image built before 4.4.
- [ ] 4.4 `feat(deploy)`: rewrite `reposilite-frontend/Dockerfile` as a build stage on `--platform=$BUILDPLATFORM` and a run
  stage on the distroless Node runtime, pinned by tag and digest (resolve both during this task, starting from
  `gcr.io/distroless/nodejs24-debian13:nonroot`, and match the Node major of the build stage), with `USER 65532`, no `RUN` in the run stage, a `HEALTHCHECK`
  in exec form calling `/healthz`, port `8080`, and no sub-path build argument. Delete `reposilite-frontend/docker/default.conf.template`
  and `DashboardImageRoutesTest.kt`. Update `docker-compose.split.yml`: `read_only`, `cap_drop: [ALL]`, `no-new-privileges`, the
  `tmpfs` lines removed (the image keeps no writable state), and the `INGOT_RESOLVER` line removed. Verify: 4.3 passes against an image built for
  `linux/amd64` locally with buildx, the `linux/arm64` build completes, and `docker compose -f docker-compose.split.yml config`
  validates.
- [ ] 4.5 `ci(deploy)`: in `.github/workflows/ci.yml`, scan the dashboard image with the same Trivy step as the server image, and
  keep the container test command. In `.github/workflows/release-please.yml`, add `charts/` to the deny list of the `changes` job.
  Verify: a local run of the deny-list `grep` on a push that changes only `charts/ingot/Chart.yaml` prints `image=false`, and a
  push that changes `reposilite-frontend/src/` prints `image=true`.
- [ ] 4.6 `docs(deploy)`: rewrite `docs/gitbook/how-to/run-split-dashboard-container.md` for the distroless image: UID `65532`,
  `INGOT_BACKEND`, `INGOT_BASE_PATH` at start, `INGOT_MAX_UPLOAD_SIZE`, `INGOT_RESOLVER` ignored, `/healthz`, and a note
  that an existing compose file with a `tmpfs` owned by `101` keeps starting. Update the sub-path section of the page. Verify: every variable and path
  in the page is exercised by 4.3 or 2.1, and the page's `curl` commands run against the container of 4.3.

## 5. Helm chart

- [ ] 5.1 `test(deploy)`: write failing chart tests in `charts/ingot/tests/`, as a script that runs `helm template` and `helm lint`
  and asserts on the rendered output with messages. Cases: no mode renders one Deployment and one Service; `mode: split` renders
  two Deployments and two Services, the server sets `INGOT_TARGET=server`, the dashboard sets `INGOT_BACKEND`; `mode: both` fails
  the schema; the dashboard security context is hardened and the server has none by default; the probes are `/healthz` for the
  dashboard and `/api/status/health` for the split server; PDB, ServiceMonitor and HTTPRoute are absent by default; the Ingress
  and HTTPRoute together fail; the split routing has `/` exact to the dashboard and `/` prefix to the server, and every segment of
  `charts/ingot/files/dashboard-segments.json` to the dashboard. Verify: the script fails because there is no chart yet.
- [ ] 5.2 `feat(deploy)`: add `charts/ingot` with `Chart.yaml`, `values.yaml` (the keys of upstream chart 1.4.5 that staging uses,
  with the same defaults, plus `mode`, `dashboard`, `serviceMonitor`, `podDisruptionBudget` and `httpRoute`), `values.schema.json`,
  the templates, the name helpers that mirror the upstream helpers, and the committed copy `charts/ingot/files/dashboard-segments.json`.
  Verify: 5.1 passes and `helm lint charts/ingot` is clean in both modes.
- [ ] 5.3 `test(deploy)`: write a failing equivalence test. A fixture under `charts/ingot/tests/fixtures/` holds the staging values
  sanitised from the HelmRelease `reposilite` in `reposilite-staging`, and the identity fields that chart 1.4.5 renders from them
  (captured once, with the chart version in the file name). The test renders the fixture through this chart with
  `nameOverride: reposilite` and `fullnameOverride: reposilite` and asserts the selector labels, the Deployment name, the Service
  name, the PersistentVolumeClaim name and the container port are equal to the fixture. A second case renders without the overrides
  and asserts that the selector differs, which documents why the overrides are part of the migration. Verify: the first case fails
  until the helpers of 5.2 mirror the upstream helpers, and the second case passes.
- [ ] 5.4 `ci(deploy)`: add a chart job to `.github/workflows/ci.yml` that runs `helm lint`, `ct lint`, the script of 5.1 and 5.3,
  the drift check (`cmp charts/ingot/files/dashboard-segments.json reposilite-frontend/src/router/dashboard-segments.json`), and
  `ct install` on a kind cluster for `mode: allInOne` and `mode: split`, with the images of the container job loaded into kind.
  Action references are pinned by commit SHA as the other jobs are. Verify: the job is green on the pull request for both modes.
- [ ] 5.5 `ci(deploy)`: in `release-please-config.json`, add `yaml` extra files for `charts/ingot/Chart.yaml` with the JSON paths
  `$.version` and `$.appVersion`. In `.github/workflows/release-please.yml`, add a job that runs only when `release_created` is
  true, after the container job, that packages the chart and pushes it to `oci://ghcr.io/onelitefeathernet/charts` with the
  workflow token, with `packages: write` on that job only. Verify: the config parses with `jq`, `helm package charts/ingot` in a
  pull request run produces a chart with the version of the manifest, and the job's `if` is false on a push without a release.
- [ ] 5.6 `docs(deploy)`: add `docs/gitbook/how-to/run-split-with-helm.md` (split mode through the chart, with values, the routing
  table and the verification commands), add `docs/gitbook/how-to/migrate-from-upstream-chart.md` (every key of the staging values
  with its equivalent in this chart, the `nameOverride` and `fullnameOverride` lines, `helm diff upgrade` before the change, the
  data volume note and the rollback to chart 1.4.5), and update `docs/gitbook/how-to/run-on-kubernetes.md` to install the OCI chart
  and link to both pages. Add the pages to `docs/gitbook/SUMMARY.md`. Verify: the migration table has a row for every key in the
  fixture of 5.3, and every command on the new pages is one the kind job of 5.4 runs.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `feat(deploy): add deploy modes, a nitro dashboard image and a helm chart`.
  Read `.github/pull_request_template.md` and fill its sections. The description tells the story of the operator (the staging
  release, the dashboard that could not be hardened, the split that was hand-written) without class names, and names the
  migration how-to and the throughput numbers measured for the Nitro proxy against
  the previous nginx image on the same host. It carries no session link and no attribution line. Verify: `gh pr view --json title`
  shows the exact title, the commit-style hook accepts the title and every commit, and the PR body has no em or en dash.

# Proposal

## Why

`add-deploy-modes` gave Ingot the deploy target `INGOT_TARGET` with the values `all` and `server`, in the style of Grafana
Loki and Mimir `-target`, and reserved a later `worker` value. Today every replica of a cluster runs every background job
next to the request traffic. That is safe, because the jobs that must run once already claim a key in the shared database
before they start (see design.md, finding F1), but it has three costs. A cleanup run, a search rebuild or a token sweep
takes IO and database connections away from the replicas that serve artifacts. A read-heavy installation cannot scale the
download path wide without also multiplying the jobs' standby loops. And an operator has no way to say "this pod serves
nothing and only works", which is what Loki and Mimir operators expect from a target.

The high availability design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, section 2.4) already names the
next step: reads and writes as separate deployments from the same image, routed by HTTP method. The chart cannot express
that yet.

## What Changes

- **Deploy target `worker`.** `INGOT_TARGET=worker` (also `--target=worker` and `-Dingot.target=worker`) starts the server
  with the background jobs and a web server that answers only the status routes (`/api/status/health`, `/api/status/ready`,
  `/api/status/instance`) and the `/metrics` route of the Prometheus plugin when that plugin is installed. It registers no
  Maven, no repository API, no dashboard and no admin API route. Every other path answers `404`. The values `all` and
  `server` behave exactly as before.
- **Switch for server replicas.** A new boolean `INGOT_BACKGROUND_JOBS` (system property `ingot.background-jobs`, command line
  option `--background-jobs`), default `true`. With `false` on target `server`, the replica does not start the singleton jobs
  of finding F2 and leaves them to a worker. It is refused on `all` and on `worker`. Opt-in only: unset, nothing changes.
- **Start refusal for cluster requirements.** Target `worker`, and `INGOT_BACKGROUND_JOBS=false` on `server`, need shared
  state. The start stops with one line and exit code 1, as an unknown target does today, when the database is embedded
  SQLite, when no cluster bus URL is set, or when a repository is stored on the local file system. The line names every
  missing requirement at once.
- **No `read` and no `write` target.** Reads and writes are `server` replicas behind routing. design.md D3 gives the reasons.
- **Helm chart mode `scalable`.** Next to `allInOne` and `split`: a read Deployment and a write Deployment (both
  `INGOT_TARGET=server` with `INGOT_BACKGROUND_JOBS=false`), a worker Deployment (`INGOT_TARGET=worker`, one replica), and the
  dashboard workload of `split`. `httpRoute` routes by HTTP method with Gateway API method matches. Plain `ingress` cannot
  match a method, so it sends every non-dashboard request to one Service that selects the read and write pods together, and
  the install notes say so. The chart refuses `scalable` without the typed database, object storage and cluster bus values.
- **Documentation.** A reference page for the targets and the switch, a how-to for scalable mode with the routing table and
  the Ingress limitation, and updates to the Kubernetes and environment variable pages.

### Out of scope

- Targets `read` and `write` (rejected in design.md D3), and any per-plugin target list such as `targets` on `@Plugin`.
- Moving statistics, javadoc or search indexing into their own processes (HA design 4.2). Search indexing stays in the job
  set of the worker as it exists today.
- Leader election, a new lock service or a new claim API. The existing claims are used as they are.
- A configuration file key for the new switches. Only the three sources of `add-deploy-modes` exist.
- Autoscaling of the worker. It is one replica by default and more are safe, but there is nothing to scale on.
- Changes to the defaults: `all`, `allInOne`, the entrypoint, the paths, the compose files.

## Capabilities

### New Capabilities

- `worker-target`: the `worker` target value, what it serves, the `INGOT_BACKGROUND_JOBS` switch, the job set it moves, and the
  start refusal when shared state is missing.
- `helm-chart-scalable`: the `scalable` mode of the chart, its workloads, values, method routing, the Ingress limitation and
  the refusals at render time.

### Modified Capabilities

`deploy-modes` and `helm-chart` are introduced by the still open change `add-deploy-modes`, whose code is on main already but
whose specs are not archived into `openspec/specs`. This change extends them, so its deltas are written as new capabilities
that name the extended requirement instead of editing a spec that does not exist there yet. When `add-deploy-modes` is
archived, the maintainer may fold both into it.

## Conventional Commits and pull requests

The change mixes no types. It ships as two code pull requests, because backend and chart are reviewed separately and the
second one needs the image of the first:

1. `feat(deploy): add the worker target and the background jobs switch` (backend, tests, reference docs)
2. `feat(chart): add the scalable mode` (chart, chart tests, how-to docs)

If the review of design.md F3 finds a defect before the code is written, it ships first as its own `fix(...)` pull request
with a failing test. The investigation found none.

## Impact

- Backend: `net.onelitefeather.ingot.deploy` (target enum, resolver, refusal), `ReposiliteFactory`, the routing setup, and the
  scheduling call sites listed in design.md F2. No package move, no `REPOSILITE_` spelling for the new switch.
- Chart: `charts/ingot` templates, values, schema, golden fixtures. The default render stays byte for byte equal.
- Docs: `docs/gitbook`.
- Conflicts with open pull requests: see design.md, section Conflicts.

# Proposal

## Why

Ingot is a drop-in replacement for Reposilite, and the reason to run it on Kubernetes is capacity: more replicas, a worker
target and a scalable chart mode. Today nobody can say how many builds per second one deployment shape sustains before the
p95 latency of a download climbs, or which resource breaks first. The HA smoke test (`ha-smoke-test`) proves that two replicas
behave as one, but it runs for seconds on kind and says nothing about load. A maintainer who is asked how large a cluster must
be for a given number of parallel builds has to guess, and so does the operator who sizes a deployment with
`docs/gitbook/set-up/size-the-deployment.md`.

We want measured answers. For each deployment shape (`monolith`, `ha`, `split`, `scalable` and the read replica series) and
for each important function (resolve, deploy, mirror, auth, search, dashboard), the goal is the highest request rate that holds
for at least 10 minutes while the thresholds are met. The answers only compare if every shape gets the same fixed CPU and
memory per pod, the same PostgreSQL, bucket and NATS, and a load generator that cannot hide load by slowing down, which is why
the test uses an open arrival model. The results go to a Grafana dashboard that is checked into the repository, so the rates
and the server metrics behind them are read side by side.

## What Changes

- **A new top-level `loadtest/` directory, TypeScript only.** It holds the k6 scenarios, shared libraries, profiles, topology
  overlays, a TypeScript runner and the Grafana dashboard JSON. k6 1.x runs `.ts` files natively, so there is no bundler and no
  JavaScript source file. `npm run typecheck` (`tsc --noEmit`) passes.
- **One scenario per function**, each request tagged `scenario:<name>`: `resolve`, `resolve-miss`, `metadata`, `deploy`,
  `mirror`, `auth`, `search-browse` and `dashboard`, plus a `mix` scenario that combines them with weights. Test data is seeded
  in `setup()` with a fixed seed, so runs are repeatable.
- **A two-phase capacity search per topology and scenario.** A breakpoint run ramps the arrival rate until a threshold breaks,
  which gives an upper bound. Plateau confirmation then holds the steps at 60, 70, 80, 90 and 100 percent of that bound, each
  for at least 10 minutes (15 minutes by default). The maximum sustainable rate is the highest step that passed for its whole
  duration. A soak profile runs that maximum for 2 hours to catch leaks and pool exhaustion.
- **Topology overlays** under `loadtest/topologies/`: `monolith` (`allInOne`, 1 replica), `ha` (`allInOne`, 3 replicas), `split`
  (server and dashboard container), `scalable` (read, write and worker Deployments) and the `scalable` scaling series with 1, 2
  and 4 read replicas. Every Ingot pod has CPU and memory requests equal to its limits, taken from one of five reference sizes
  (N, T, S, M and L, from 0.25 CPU and 256 MiB up to 4 CPU and 4 GiB), and every topology is measured at each size.
- **Execution in its own namespace `ingot-loadtest`**, through the k6-operator `TestRun` resource and a runner:
  `npm run load -- --topology <t> --profile <p> --scenario <s>`. The runner installs the chart with the overlay, waits for
  readiness, applies the `TestRun`, waits for completion, records the result and uninstalls.
- **Metrics.** k6 writes to Mimir through the `experimental-prometheus-rw` output with native histograms. Server metrics come from
  the existing Prometheus plugin, labelled by pod.
- **Reference runs and published values.** A reference run needs nodes labelled for load tests and writes a result record checked
  in under `loadtest/results/`. A non-reference run may share nodes and is never published. The published reference matrix is the core matrix only:
  `mix`, `resolve` and `deploy` in every topology and every size. The other scenarios run on demand and are never published.
- **A Grafana dashboard** `loadtest/grafana/ingot-load-tests.json`, checked in, with the variables `testid`, `topology`, `scenario` and
  `size`.
- **Documentation.** A how-to `docs/gitbook/contributing/run-the-load-tests.md` and a generated page
  `docs/gitbook/references/reference-capacities.md` with the measured reference values, both listed in `docs/gitbook/SUMMARY.md`.

### Breaking changes

None. Nothing in the server, the image, the chart defaults or the configuration changes. The load tests live in a separate
directory that no build, image or chart reads. An existing installation needs no change, and an operator who never runs the
tests sees no difference.

### Out of scope

- Running the load tests in pull request CI. GitHub runners vary too much and cannot reach the cluster.
- A CI job that typechecks `loadtest/`. That belongs to a separate `ci` change.
- Installing the k6-operator, creating the namespace, the PostgreSQL database, the object storage bucket and the Mimir write
  access in the cluster. That happens in the infrastructure repository and is listed as a prerequisite in design.md.
- Comparing against upstream Reposilite. The same scripts can do that later.
- Any change to server code, chart defaults or configuration.
- Maven Central as an upstream. The mirror scenario never leaves the cluster.

## Capabilities

### New Capabilities

- `load-testing`: the scenarios, the capacity search and its pass rule, the topology overlays, the execution in the load test
  namespace, the metrics and the dashboard.

## Conventional Commits and pull requests

The change ships under one type and scope, `test(load)`, so it is one change and not two. The code is split into a stack of small
pull requests, about 400 lines each, all under `test(load)`. Each one is based on the branch of the one before it and is retargeted to
`main` when its predecessor is merged. In order:

1. Proposal pull request `docs(openspec): propose add-load-tests`: only the files under `openspec/changes/add-load-tests/`, tasks
   unchecked, branched from the remote main branch.
2. `test(load): add the typescript scaffolding for k6 load tests`
3. `test(load): add the resolve, deploy and mix scenarios`
4. `test(load): add the remaining function scenarios`
5. `test(load): add the capacity search profiles`
6. `test(load): add the topology overlays and the upstream mock`
7. `test(load): add the k6-operator runner`
8. `test(load): add the load test grafana dashboard`
9. `test(load): add result records and the reference capacities page`

No pull request in the stack touches a file under `openspec/`. The `scalable` topology depends on the `scalable` chart mode of the change
`add-worker-target`. Its overlays follow that change, see task 5.2 of tasks.md. The other topologies do not wait for it.

## Impact

- New: `loadtest/` (TypeScript, k6 scripts, overlays, runner, dashboard JSON).
- Docs: `docs/gitbook/contributing/run-the-load-tests.md` and its entry in `docs/gitbook/SUMMARY.md`.
- Server, image, chart and configuration: no change.
- Cluster: the prerequisites of design.md are created in the infrastructure repository, not by this change.

# Design

This design adds a load test suite that finds the maximum sustainable request rate of every Ingot deployment shape, for every
important function, and shows the results in Grafana. It records the context, the goals and non-goals, the decisions D1 to D9
with their rationale and alternatives, the risks, the prerequisites in the infrastructure repository and the open questions.

## Context

- Ingot runs in four chart shapes today: `allInOne` (monolith with one replica, or HA with three), and `split` (server and
  dashboard container). The change `add-worker-target` adds the `scalable` mode with read, write and worker Deployments
  (`charts/ingot/values.yaml`, `charts/ingot/values.schema.json`).
- The HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`) puts locks and counters in the database and uses
  NATS only as optional fan-out. Those choices decide where a capacity limit can sit: connection pools, the database, object
  storage, or the JVM of one pod.
- `ha-smoke-test` proves correctness of two replicas on kind in seconds. It does not measure throughput, latency under load or
  saturation, and it cannot run in pull request CI on a shared runner with a meaningful result.
- The Prometheus plugin already serves `/metrics` (`bundled-prometheus-plugin`), so server metrics exist and only need to be
  scraped and labelled by pod.
- The cluster that hosts the load tests runs Mimir for metrics and Grafana for dashboards. Writing k6 output into Mimir reuses
  that stack.

## Goals

1. For each topology and each scenario, state the maximum sustainable request rate, and the evidence: a plateau that held for at
   least 10 minutes with every threshold met.
2. Make the results comparable across topologies: the same resources per pod, the same shared services, the same test data.
3. Make a run repeatable: a fixed seed, recorded versions and recorded node pool and time of day.
4. Show the rates and the server metrics that explain them in one Grafana dashboard that lives in the repository.

## Non-goals

- A regression gate. The suite finds capacity; it does not block a pull request.
- A comparison with upstream Reposilite. The scripts are written so that the same run can target upstream later.
- Capacity planning for a specific customer. The open questions name the inputs that such a plan needs.

## Decisions

### D1. TypeScript only, run natively by k6, no bundler

Every scenario, library and profile is a `.ts` file. k6 1.x executes TypeScript directly, so the suite has no build step and
no generated JavaScript. The directory holds no `.js` or `.mjs` source file. `@types/k6` gives the editor and `tsc --noEmit`
the k6 module types, and `typescript` runs the type check. The runner that installs the chart and drives the cluster is Node
code under `loadtest/runner/`, started with `tsx`, so it needs no compile step either. The devDependencies are `typescript`,
`@types/k6` and `tsx`, each at an exact version. The k6 image tag is set in one constant in `loadtest/lib/config.ts`, and the
runner reads it for the `TestRun` image, so the version is stated once.

Alternative: write plain JavaScript and bundle it with esbuild or webpack. Rejected: the maintainer requires TypeScript only, and a
bundler adds a build step and a second artifact that can drift from the source.

### D2. Open model with arrival-rate executors, never a fixed VU count

A closed model (a fixed number of virtual users, each waiting for its response) slows its own request rate as the server slows.
A server that takes ten times longer under load then looks as if it carries a tenth of the traffic, and the saturation hides
itself. The open model starts iterations at a set rate whether or not earlier ones have finished. The breakpoint phase uses
`ramping-arrival-rate` and the plateau phase uses `constant-arrival-rate`. The `dropped_iterations` counter then shows the
moment the rate could not be kept, which is the signal that the server or the load generator has reached its limit.

### D3. Breakpoint first, then plateaus held at least 10 minutes

The breakpoint run ramps the arrival rate of one scenario on one topology until a threshold breaks. Its `abortOnFail` threshold
uses `delayAbortEval` so that the first seconds of warm-up cannot end the run. The rate at which the run stopped gives an upper
bound U. If the ramp reaches its configured ceiling without a break, the run reports no breakpoint and the plateau phase does not
start, because there is nothing to scale from.

The plateau phase runs five constant-rate steps at 60, 70, 80, 90 and 100 percent of U. Each step is held for a configurable
duration, 15 minutes by default, and the code rejects a duration under 10 minutes. The reason is warm-up: connection pools, the
JVM's garbage collector and its heap, the metadata and resolution caches, and database compaction and autovacuum only show their
steady behaviour after several minutes. A one-minute run measures a cold server and reports a rate the server cannot keep.

A step passes only if all three hold for its whole duration: the p95 latency is under the scenario's threshold, the error rate is
under 1 percent, and there are zero dropped iterations. A step that breaks in its last minute does not pass. The maximum
sustainable rate of a topology and scenario is the rate of the highest passing step. The pass rule is a pure function with unit
tests, so the decision does not depend on k6 being present.

The soak profile holds that maximum for 2 hours by default. A leak in the heap, a growing cache or an exhausted pool shows as a
rising trend in the dashboard that a 15-minute step would not show.

Thresholds for each scenario are declared in one file, `loadtest/lib/thresholds.ts`, and can be overridden per run. No number is
chosen in advance. Each entry is provisional until the first measured run on the cluster (task 6.3) sets it from the measured p95 of
its scenario, and the entry then names the `testid` of that run. That first run has its abort on fail disabled, because no limit exists
yet, and its record is marked provisional. The maintainer changes an entry only with a new measured run.

### D4. The same resources for every pod

Every Ingot pod in every topology gets CPU and memory requests equal to its limits, taken from one of three reference sizes
(D11). The JVM options are the same in every run. Without this, a topology with four large pods and one with three small pods
would measure the size of the pods, not the shape of the deployment.

### D5. A separate namespace, never production or staging

The load tests run in the namespace `ingot-loadtest`. The runner refuses any other namespace, so a mistyped flag cannot aim load at
production or at staging, which has real users. The namespace has its own PostgreSQL database, its own bucket and its own NATS
server. The shared services are the same for all topologies within one run series, so a difference between two topologies is a
difference of the shape and not of the dependencies.

### D6. k6-operator TestRun, scripts as a ConfigMap, load generators apart from Ingot

The load generator is a k6-operator `TestRun` with a configurable `parallelism`. The scripts are packed with `k6 archive` and the
archive is passed to the `TestRun` as a ConfigMap, so the pods run exactly the files that were reviewed and nothing is fetched at
run time. The load generator pods carry a pod anti-affinity against the Ingot pods with the topology key of the node hostname, so
that a load generator does not share a node with the server it measures and does not take CPU from it.

Alternative: run k6 from a CI job or a laptop. Rejected: a laptop does not match the cluster network, and a CI runner cannot reach
the cluster and varies too much to compare.

### D7. Mimir through remote write, not InfluxDB

k6 writes its samples through the `experimental-prometheus-rw` output with native histograms enabled
(`K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true`). The percentiles are then computed from the same store as the server metrics, and
the dashboard reads both with one datasource. The cluster already runs Mimir. An InfluxDB store would add a second time series store,
and the dashboard would have to join two datasources to put a rate next to a CPU curve.

### D8. Tags on every sample

Every sample carries `testid`, `topology`, `scenario`, `profile` and `ingot_version`. `testid` is generated per run, and `ingot_version`
is the image tag that the runner installed. The dashboard variables filter on these tags, so a run is found by its tags and not by its
name.

### D9. The step result is a metric, and the dashboard reads it

At the end of every plateau step the test emits `ingot_load_step_passed` with the step rate as its value and a `result` tag of `pass`
or `fail`. The table "maximum sustainable rate per topology and scenario" takes the highest value with `result=pass` for each
topology and scenario. The dashboard also marks step changes as annotations, so a curve can be read against the rate that caused it.

The dashboard JSON is checked in at `loadtest/grafana/ingot-load-tests.json`. Building the dashboard by hand in the Grafana user
interface would lose it on the next rebuild of the instance and leave no review of changes. The JSON is validated by a unit test
that checks the variables and the panel titles.

### D10. Seeded test data and no login throttling

`setup()` creates the fixtures through the Ingot admin API, the same path a user takes, and never writes to the database or the
bucket directly: artifacts of the sizes the scenarios use, versions for the metadata and resolve scenarios, tokens and users for
auth, and the search index inputs. A failed fixture call fails the setup. A fixed seed makes the bytes and the checksums identical
across runs. The 200 MB deploy body is streamed in chunks from the seeded generator and is never held in memory per virtual user.
Login throttling stays at its default, off, so that the auth scenario measures the credential path and not the throttle.

### D11. Reference pod sizes

Five sizes are defined once, in `loadtest/lib/sizes.ts`: N (nano) with 0.25 CPU and 256 MiB, T (tiny) with 0.5 CPU and 512 MiB,
S with 1 CPU and 1 GiB, M with 2 CPU and 2 GiB, and L with 4 CPU and 4 GiB. Requests equal limits. The chart derives the JVM heap
from the memory limit, so the runner does not set heap options per size. The heap at N and T is small, so a pod there may never
become ready, or may be killed by the kernel for memory during a run. Both are valid results: the size does not support the
topology, and the record says so (D13). Every topology is measured at each size. In `scalable` the size is set per Deployment (read,
write and worker), the result record names the size of each, and a published reference row uses one size for all three. The runner
applies the size to the overlay, so the overlays do not carry their own numbers.

### D12. Reference runs need nodes that run nothing else

A run tagged `reference` is valid only when the Ingot pods and the k6 pods land on nodes that carry the load test label and tolerate
the matching taint. The label name is configurable, for example `ingot.onelitefeather.dev/loadtest=true`. Before the install the
runner lists the matching nodes and refuses the run, with the number it found and the number it needs, when they are missing. A
non-reference run may use shared nodes, and its record is marked `reference: false`. Only reference records are published (D13).

### D13. Result records and the published reference capacities

Each reference run writes one JSON record, checked in under `loadtest/results/<ingot_version>/`, one file per topology, size and
scenario. The record holds the Ingot version, topology, pod size, storage backend, database, node type (CPU model, cores and
memory), k6 version, date, the `reference` flag and an outcome per scenario. The outcome is either the maximum sustainable rate with
its p95, or, when the pods never became ready or were OOM-killed, the value `not-ready` or `oom-killed` with the step at which it
happened. The generator `loadtest/runner/capacities.ts` renders `docs/gitbook/references/reference-capacities.md` from the records
with `reference: true`. A rate cell shows the rate and its p95. An outcome cell shows "not supported at this size". No cell is left
empty. The page gives the method, the hardware and the note that these are measured reference values, not guarantees. The Grafana
dashboard has a `size` variable with the five values, and its comparison table groups by topology and size. Alternative: publish the
numbers from the dashboard. Rejected: a dashboard is neither reviewed nor versioned.

## Topologies

| Overlay | Shape |
| --- | --- |
| `monolith.yaml` | chart `allInOne`, 1 replica |
| `ha.yaml` | chart `allInOne`, 3 replicas |
| `split.yaml` | server plus dashboard container |
| `scalable.yaml` | read, write and worker Deployments, 2 read replicas by default |
| `scalable-read-1.yaml`, `scalable-read-2.yaml`, `scalable-read-4.yaml` | the scaling series, 1, 2 and 4 read replicas |

All overlays use the same PostgreSQL, the same bucket and the same NATS server. The storage backend (filesystem for the monolith,
S3, or the storage cache) is a further dimension that is optional and selected by the runner, not a separate overlay.

## Run budget

One plateau series takes at least 80 minutes (the breakpoint plus five steps of 15 minutes), and a soak adds 2 hours. The published
reference matrix is the core matrix only: the scenarios `mix`, `resolve` and `deploy`, in every topology overlay (6) and every size
(5). That is 6 x 3 x 5 = 90 series, about 120 hours of cluster time before any soak. The other scenarios (`resolve-miss`, `metadata`,
`mirror`, `auth`, `search-browse` and `dashboard`) stay in the suite and run on demand, but their results are never published on the
reference page. Reference values are published only from reference runs. The maintainer chooses the subset with `--topology`,
`--scenario` and `--size`.

## Risks and trade-offs

- Shared cluster nodes skew the numbers. Non-reference runs may share nodes, and their records carry `reference: false`, so their
  numbers are never published. Reference runs refuse shared nodes (D12). The record names the node type and the time of day.
- The load generator can saturate. If the k6 pods run out of CPU, the server looks slower than it is. A run with any dropped
  iterations fails its step by definition, and the k6 pod CPU is on the dashboard, so the cause is visible. The fix is a higher
  `parallelism`, not a lower target.
- A breakpoint that is never reached produces no plateau. The ceiling of the ramp is configurable, and a run that reaches it says so.
- Results are only as good as the fixtures. The seed fixes the data, but a fixture that does not resemble real Maven traffic gives a
  real number for an unreal load. The scenarios follow the request mix of Maven and Gradle clients, and the maintainer reviews it.
- The anti-affinity and the labelled node pool need enough nodes. A pool that is too small makes the runner refuse a reference run
  before the install. The maintainer adds nodes rather than relaxing the rule.

## Prerequisites in the infrastructure repository

These are created outside this repository and before the first run. This change does not create them. The infrastructure repository
owns the k6-operator, the namespace, Mimir, the node pool and the services; this repository ships only the overlays, the scripts, the
runner and the upstream mock of task 5.3.

- The k6-operator installed in the cluster, with its CRD.
- The namespace `ingot-loadtest`, a service account for the runner that has rights in this namespace only, and a network policy
  that lets the load generators reach the Ingot Services.
- A PostgreSQL database and a bucket for the load test namespace, separate from production and staging.
- A NATS server for the namespace.
- A Mimir write endpoint and credentials reachable from the namespace, and a Grafana datasource for it.
- Pull access to the image of the upstream mock (nginx) and to the k6 image.
- A node pool labelled for load tests and tainted, sized for the largest topology at size L. Reference runs are refused without it (D12).

## Decisions on the former open questions

1. Target scale: there is no single target scale. As in a reference architecture, the suite publishes reference values per topology,
   pod size and scenario, and does not size a deployment for one customer. The rate of the `mix` scenario comes from its own
   breakpoint.
2. Pod size: N, T, S, M and L as defined in D11.
3. Dedicated nodes: reference runs require them (D12). Non-reference runs may share nodes and are never published.
4. Anti-affinity of the load generators: required, as in D6.
5. Records: checked in under `loadtest/results/` (D13).
6. Worker: one replica in `scalable`, as in `add-worker-target`.

Decisions of 2026-11-18 by the maintainer:

7. Infrastructure: the k6-operator, the namespace `ingot-loadtest`, Mimir, PostgreSQL, the bucket, NATS and the labelled node pool are
   created in the infrastructure repository, not here (see the prerequisites above).
8. Thresholds come from the first measured run and are not chosen in advance (D3).
9. Test data is created in `setup()` through the Ingot admin API (D10).
10. The 200 MB deploy body is streamed and is not held per virtual user (D10).
11. The mirror scenario (task 3.3) runs its live check in group 5 against the in-cluster upstream mock (task 5.3).

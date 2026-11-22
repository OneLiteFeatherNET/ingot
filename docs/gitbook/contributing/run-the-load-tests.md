---
description: How to run the Ingot load tests on the load test cluster, read the maximum sustainable rate and add a result to the reference capacities.
---

# Run the load tests

This page is for a maintainer who measures Ingot under load. It runs the k6 scenarios on the load test cluster, reads the maximum sustainable rate of a run and checks in the result. The suite lives in `loadtest/` in the repository.

## Prerequisites

- `kubectl` with access to the load test cluster, and `helm`.
- Docker, used once per run to pack the scenario with `k6 archive` in the pinned k6 image.
- Node.js and the packages of `loadtest/`, installed with `npm ci` in that folder.
- The infrastructure of the load test cluster: the k6-operator, the `ingot-loadtest` namespace, the metrics store, PostgreSQL, the object storage bucket, NATS, and for reference runs the node pool that carries the load test label. The infrastructure is not created by this repository.
- The Ingot image tag under test, set in `INGOT_IMAGE_TAG`. Topologies that run the separate dashboard container (split and scalable) pin `ingot-dashboard` to the same tag, so both images are one build.
- The Prometheus remote write endpoint of the metrics store, as the load generator reaches it inside the cluster, set in `INGOT_LOADTEST_RW_URL`. The runner refuses to start without it, because k6 would otherwise send its samples nowhere.

The runner refuses any namespace other than `ingot-loadtest` before it calls `kubectl`. Production and staging namespaces are never a valid target.

## Run one scenario

Run the runner from `loadtest/` with one topology, one profile and one scenario:

```sh
INGOT_IMAGE_TAG=2.0.0 INGOT_LOADTEST_RW_URL=<remote write endpoint> npm run load -- --topology monolith --profile breakpoint --scenario resolve
```

The runner installs the chart with the topology overlay in `loadtest/topologies/`, waits until every Ingot pod is ready, packs the scenario into a ConfigMap, starts the TestRun, waits for it to finish, writes the result record and uninstalls the release. The uninstall runs even when a step before it failed.

The options are:

| Option | Values | Default |
| --- | --- | --- |
| `--topology` | `monolith`, `ha`, `split`, `scalable`, `scalable-read-1`, `scalable-read-2`, `scalable-read-4` | required |
| `--profile` | `breakpoint`, `plateau`, `soak` | required |
| `--scenario` | `resolve`, `resolve-miss`, `metadata`, `deploy`, `mirror`, `auth`, `search-browse`, `dashboard`, `mix` | required |
| `--size` | `N`, `T`, `S`, `M`, `L` | chart default resources, and no result record |
| `--reference` | flag | off |
| `--parallelism` | whole number of at least 1 | 1 |

The runner reads further settings from its environment:

| Variable | Meaning |
| --- | --- |
| `INGOT_IMAGE_DIGEST` | Pins the server image by digest. The chart has no digest value for the dashboard image, which therefore follows the tag only. Use it with a rolling tag such as `nightly`, which a node may otherwise resolve to an older cached image. |
| `LOADTEST_CEILING`, `LOADTEST_RAMP_MINUTES` | Ceiling and ramp duration of the breakpoint profile. |
| `LOADTEST_ABORT_ON_FAIL` | `false` runs the whole breakpoint ramp instead of stopping at the first broken threshold. |
| `LOADTEST_BREAKPOINT`, `LOADTEST_HOLD_MINUTES` | Breakpoint rate and step hold of the plateau profile. The plateau needs the breakpoint. |
| `LOADTEST_RATE`, `LOADTEST_SOAK_MINUTES` | Rate and duration of the soak profile. The soak needs the rate. |
| `INGOT_LOADTEST_CPU_MODEL` | CPU model of the load test nodes, kept in the result record. Required with `--size`, because a Kubernetes node does not report it. |
| `INGOT_LOADTEST_QUERY_URL` | Prometheus HTTP API of the metrics store, as the machine of the runner reaches it, for example `http://<host>/prometheus`. The runner reads the maximum and its p95 of the run from it. Without it, the runner uses the query API on the host of `INGOT_LOADTEST_RW_URL`, which works when that host also resolves outside the cluster. |

With `--size`, the runner applies that reference size with requests equal to limits, and after the run it writes the result record. It reads the highest passed plateau step of the run from the metrics store, with the p95 over that step's hold. A run without a passed step writes no record.

Pass `--reference` only for a published run. A reference run first counts the nodes that carry the load test label and refuses to start when there are fewer than its topology needs, with the number found and the number needed. Only a reference run pins the load generator to the labelled nodes. A run without the flag may share nodes, and its record is marked `reference: false`, so its numbers are never published.

## The profiles

- **Breakpoint** ramps the arrival rate up until a step fails or the configured ceiling is reached. It finds an upper bound for the rate. It stops early when a step fails, so it does not hold the cluster for long.

  The ramp starts at 1 request per second after a warm-up of 2 minutes at 5 requests per second. k6 evaluates a threshold over every sample since the start of the run, so without the warm-up the slow first requests of a cold JVM would stay in the p95 and stop the ramp long before the server is saturated. The thresholds that stop the run count only the requests of the ramp, which carry the tag `phase=ramp`, while the warm-up requests carry `phase=warmup`. Read the break rate from the ramp start time, not from the start of the run: subtract the 2 minutes of warm-up from the time the run stopped before you work out the rate the ramp had reached. `breakpointRateAt` in `loadtest/profiles/breakpoint.ts` does this calculation.
- **Plateau** holds steps at 60, 70, 80, 90 and 100 percent of the breakpoint. Each step is held for 15 minutes by default, and never for less than 10 minutes, because the first minutes include warm-up. A step passes only when every minute of its hold passes the rule below.
- **Soak** runs the maximum for 2 hours by default, to find leaks and exhausted connection pools.

A plateau series takes at least 80 minutes: the breakpoint and five steps of 15 minutes. A soak adds 2 hours.

## The pass rule

A plateau step passes when every minute of its hold meets all three limits:

- the p95 latency is under the limit of its scenario,
- the error rate is under 1 percent,
- no iteration was dropped, because the load generator could not start it.

A step that breaks in its last minute does not pass. The limits are kept in `loadtest/lib/thresholds.ts`. Until the first measured run sets them, every limit is provisional.

## Read the maximum table

The maximum sustainable rate is the rate of the highest step that passed for its whole hold. Read it with its p95, not on its own: a rate with a p95 close to the limit has little headroom.

The maximum is reported only when the breakpoint was reached. When the breakpoint was not reached, the plateau did not start from the real limit, and the run has no maximum. When no step passes, the run has no maximum either.

The Grafana dashboard `ingot-load-tests` shows the maximum table grouped by topology and size. Its `size` variable offers the five reference sizes, from N (0.25 CPU and 256 MiB) to L (4 CPU and 4 GiB). Requests always equal limits, so a size is the whole of the pod's resources.

An outcome of `not-ready` or `oom-killed` means that the pods never became ready, or that the kernel killed them, at the step named in the record. Such a cell reads "not supported at this size" in the reference capacities.

## Node pool and time of day

Reference values depend on the machines they ran on. Record the node type, which the result record keeps with its CPU model, cores and memory. Run reference series on the labelled node pool only, and do not run them while other jobs use that pool, because a shared node skews the numbers. Every record also names the date of its run. Run the series of one comparison at the same time of day, so that a difference between two runs is not a difference in the load on the nodes.

## Run budget

The published reference matrix is the core matrix: the scenarios `mix`, `resolve` and `deploy`, in each of the six distinct topologies and each of the five sizes. That is 90 plateau series, about 120 hours of cluster time before any soak. Choose a subset with `--topology`, `--scenario` and `--size` when only part of the matrix changed.

## On-demand scenarios

The scenarios `resolve-miss`, `metadata`, `mirror`, `auth`, `search-browse` and `dashboard` stay in the suite and run on demand. They check behaviour under load, and their results are never published on the reference capacities page. The `mirror` scenario needs the in-cluster upstream mock, which the topology overlays set up.

## Publish a result

A reference run writes one record per topology, size and scenario under `loadtest/results/<ingot version>/`. Check the record into the pull request together with the change that it measures. The reference capacities page is rendered from the records with `reference: true` by `loadtest/runner/capacities.ts`.

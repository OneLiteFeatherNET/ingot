# Tasks

Ordered. Each unit test is written first and seen failing. Task checkboxes stay unchecked in this proposal pull request and are
ticked only in a follow-up that the maintainer asks for. Every task in this change is `test(load)` or `docs`, and the code ships
in one pull request, so the groups are commits of one type.

## 1. Scaffolding (test(load))

- [ ] 1.1 Test: `loadtest/lib/config.test.ts` (Node test runner through `tsx`, no network, no clock) for the option parsing of
  `--topology`, `--profile` and `--scenario`, the default profile values and the rejection of a plateau step shorter than 10 minutes.
  Then add `loadtest/package.json` with the devDependencies `typescript`, `@types/k6` and `tsx`, each pinned, and
  `loadtest/tsconfig.json` with `strict` set. Add the scripts `typecheck` (`tsc --noEmit`) and `load` (`tsx runner/main.ts`).
  Verify: `npm run typecheck` passes and `find loadtest \( -name '*.js' -o -name '*.mjs' \)` prints nothing.
- [ ] 1.2 Add `loadtest/lib/config.ts` with the k6 image tag as the single pinned constant, the namespace `ingot-loadtest` and the
  refusal of any other namespace. Verify: the test of 1.1 passes unchanged, and a unit test asserts that the refusal names the
  rejected namespace.
- [ ] 1.3 Test: `loadtest/lib/artifacts.test.ts` asserts that the same seed yields identical artifact bytes and SHA-1 values on two
  calls and different bytes for a different seed. Then add `loadtest/lib/artifacts.ts` with the seeded generator for the sizes 1 KB,
  1 MB, 50 MB and 200 MB, and `loadtest/lib/auth.ts` and `loadtest/lib/checks.ts` for Basic and token headers and the shared checks.
- [ ] 1.5 Test: `loadtest/lib/sizes.test.ts` asserts the five reference sizes N, T, S, M and L with their CPU and memory values, and
  that the requests of every size equal its limits. Then add `loadtest/lib/sizes.ts`, the one file that defines them.
- [ ] 1.4 Add `loadtest/lib/thresholds.ts`, the one file that declares the thresholds of every scenario, and a unit test that
  reads it and asserts that every scenario name of the design has a threshold entry.

## 2. Scenarios (test(load))

- [ ] 2.1 Test: `loadtest/scenarios/resolve.test.ts` for the request list of a Maven and a Gradle client (pom, jar, `.sha1`, `.module`)
  against a fixture. Then add `loadtest/scenarios/resolve.ts` with the executor, the tag `scenario:resolve` and the expected status set.
- [ ] 2.2 Add `loadtest/scenarios/resolve-miss.ts`: 404 answers on group repositories counted as expected through
  `http.expectedStatuses(200, 404)`. Verify with a unit test of the request list and the expected status set.
- [ ] 2.3 Add `loadtest/scenarios/metadata.ts` (snapshot `maven-metadata.xml`) and `loadtest/scenarios/deploy.ts` (PUT of 1 KB, 1 MB,
  50 MB and 200 MB artifacts, several versions concurrently). Verify the executor and the sizes against the artifacts of 1.3.
- [ ] 2.4 Add `loadtest/scenarios/mirror.ts`: a proxy repository against the in-cluster upstream mock of 4.3 only. A unit test asserts
  that no URL in the scenario points outside the cluster.
- [ ] 2.5 Add `loadtest/scenarios/auth.ts` for Basic and token access to private repositories, and tag the requests so that the
  credential cache can be read per scheme.
- [ ] 2.6 Add `loadtest/scenarios/search-browse.ts` (search API and directory listings) and `loadtest/scenarios/dashboard.ts` (login and
  repository list).
- [ ] 2.7 Add `loadtest/scenarios/mix.ts`, which combines the scenarios of 2.1 to 2.6 with weights set in one file. Test: the weights
  sum to 1 and every scenario is in the mix.
- [ ] 2.8 Verify: each scenario runs with `k6 run --iterations 1` against the `monolith` topology of 4.1 and passes its checks.

## 3. Profiles and capacity search (test(load))

- [ ] 3.1 Test: `loadtest/profiles/plateau.test.ts` for the step list of 60, 70, 80, 90 and 100 percent of a breakpoint, the default
  hold of 15 minutes and the rejection of a hold under 10 minutes. Then add `loadtest/profiles/plateau.ts` as a pure function that
  returns the step list. No clock and no k6 runtime are read.
- [ ] 3.2 Test: `loadtest/lib/pass-rule.test.ts` for the pass rule of design.md D3 with a table: p95 under the threshold, error rate
  under 1 percent, zero dropped iterations, and a step that breaks in its last minute does not pass. Then add
  `loadtest/lib/pass-rule.ts`.
- [ ] 3.3 Test: `loadtest/lib/maximum.test.ts` for the maximum sustainable rate: the highest passing step, no maximum when no step
  passes, and no maximum when the breakpoint was not reached. Then add `loadtest/lib/maximum.ts`.
- [ ] 3.4 Add `loadtest/profiles/breakpoint.ts` (ramping arrival rate with `abortOnFail` and `delayAbortEval`, configurable ceiling),
  `loadtest/profiles/plateau.ts` wiring of 3.1 to `constant-arrival-rate` executors, and `loadtest/profiles/soak.ts` (the maximum for
  2 hours by default, configurable). Verify: the breakpoint profile reports a breakpoint on the monolith with a low ceiling.
- [ ] 3.5 Emit `ingot_load_step_passed` at the end of every plateau step, with the step rate as value and the `result` tag. Test: the
  metric name and the tag values are asserted by a unit test of the emitter with a fake sink.

## 4. Topologies and upstream mock (test(load))

- [ ] 4.1 Add the overlays `loadtest/topologies/monolith.yaml`, `ha.yaml`, `split.yaml` and `scalable.yaml`. Verify: `helm template`
  with each overlay renders, and `scalable.yaml` only renders after `add-worker-target` is merged.
- [ ] 4.2 Add `loadtest/topologies/scalable-read-1.yaml`, `scalable-read-2.yaml` and `scalable-read-4.yaml` for the scaling series.
  Verify: the read replica count of each rendered Deployment matches the file name.
- [ ] 4.3 Add `loadtest/k8s/upstream-mock.yaml`: an nginx Deployment and Service in `ingot-loadtest` that serve fixed artifacts, with the
  latency set by a value. Verify: the mock answers with the bytes of the seeded artifacts of 1.3 at latency 0 and at a set latency.
- [ ] 4.4 Verify on every overlay that all Ingot pods have CPU and memory requests equal to limits, and that PostgreSQL, bucket and NATS
  settings are identical across overlays.

## 5. k6-operator runner (test(load))

- [ ] 5.1 Test: `loadtest/runner/testrun.test.ts` for the `TestRun` manifest built from the options: the namespace, the `parallelism`, the
  ConfigMap name of the archive, the image tag from `config.ts` and the anti-affinity to the Ingot pods on the node hostname. Then add
  `loadtest/runner/testrun.ts`.
- [ ] 5.2 Test: `loadtest/runner/steps.test.ts` for the order of the runner steps (install, wait for readiness, archive, apply, wait for
  completion, record, uninstall) and for the uninstall running after a failed step. Then add `loadtest/runner/main.ts` and
  `loadtest/runner/steps.ts`. No `sleep`: waits use deadlines from an injected clock.
- [ ] 5.3 Add the `k6 archive` step that packs the scenario and the libraries into the ConfigMap, and the record file that names the
  node pool, the time of day, the `testid` and the `ingot_version`. Verify: `npm run load -- --topology monolith --profile breakpoint
  --scenario resolve` runs end to end on the cluster and uninstalls its release.
- [ ] 5.4 Verify: a `TestRun` with a target namespace other than `ingot-loadtest` is refused by the runner before any `kubectl` call.
- [ ] 5.5 Test: `loadtest/runner/nodes.test.ts` for the reference node check: a reference run with fewer labelled nodes than the topology
  needs is refused before the install, with the found and the needed count; a non-reference run skips the check and is marked
  `reference: false`. Then add `loadtest/runner/nodes.ts` with the label name read from `config.ts`.
- [ ] 5.6 Test: `loadtest/runner/record.test.ts` for the result record: the fields of design.md D13, an outcome of `not-ready` or
  `oom-killed` with its step instead of a rate when a pod never became ready or was OOM-killed, and one file per topology, size and
  scenario. Then add `loadtest/runner/record.ts` and write the record at the end of every run.

## 6. Grafana dashboard (test(load))

- [ ] 6.1 Test: `loadtest/grafana/dashboard.test.ts` reads `loadtest/grafana/ingot-load-tests.json` and asserts that the variables
  `testid`, `topology`, `scenario` and `size` exist, and that the panels named in the design exist (target versus achieved rate, p50, p95 and
  p99 per scenario, error rate, dropped iterations, active VUs, bytes per second, server CPU, heap and GC, database pool active and
  pending, storage latency per pod, the maximum table and the topology bar chart).
- [ ] 6.2 Add `loadtest/grafana/ingot-load-tests.json` and the annotations at step changes. Verify: the dashboard loads in Grafana against
  the Mimir datasource and the panels show data for one test run.
- [ ] 6.3 Configure the k6 output: `experimental-prometheus-rw` with `K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true` and the tags
  `testid`, `topology`, `scenario`, `profile` and `ingot_version` on every sample. Test: a unit test of the environment built by the
  runner asserts the variable and the tags.
- [ ] 6.4 Test: the dashboard test of 6.1 asserts that the comparison table groups by `topology` and `size`. Then add the `size` variable with
  its five values and the grouping to `loadtest/grafana/ingot-load-tests.json`. Verify: the table shows `not supported at this size`
  for an outcome record, not an empty cell.

## 7. Documentation (docs)

- [ ] 7.1 Add `docs/gitbook/contributing/run-the-load-tests.md`: a how-to with the prerequisites, the runner command, the profiles, how
  to read the maximum table, the node pool and time-of-day note, and the run budget. Add the page under "Contributing" in
  `docs/gitbook/SUMMARY.md`.
- [ ] 7.3 Test: `loadtest/runner/capacities.test.ts` for the generator: a non-reference record contributes no value, every topology,
  size and scenario has a cell, and an `oom-killed` record reads "not supported at this size". Then add
  `loadtest/runner/capacities.ts` and run it to write `docs/gitbook/references/reference-capacities.md` with the method, the hardware
  and the note that the values are measured reference values, not guarantees. Add the page under "References" in `docs/gitbook/SUMMARY.md`.
- [ ] 7.2 Verify: every link on the page resolves inside `docs/gitbook`, and the page names no cluster host, credential or Mimir
  endpoint.

## 8. Pull request

- [ ] 8.1 Open the code pull request with the title `test(load): add k6 load tests for every deployment shape` from a branch cut from
  the remote main that contains the merged proposal. Fill `.github/PULL_REQUEST_TEMPLATE.md`, tell it as the maintainer's story (why the
  capacity of each deployment shape has to be known), no session link, no em or en dashes, no file under `openspec/`. Verify with
  `gh pr view --json title,body,files`.

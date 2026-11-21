# Tasks

Ordered. Each unit test is written first and seen failing. Task checkboxes stay unchecked in this proposal pull request and are
ticked only in a follow-up that the maintainer asks for. Every task in this change is `test(load)`, so each pull request below is one
commit type.

The code ships as a stack of small pull requests, about 400 lines each. Each pull request is based on the branch of the one before it
in the stack, and is retargeted to `main` when its predecessor is merged. Each group ends with the task that opens its pull request
under its title. The last task of the last group opens the last pull request.

## 1. Typescript scaffolding (test(load))

Pull request: `test(load): add the typescript scaffolding for k6 load tests`

- [x] 1.1 Test: `loadtest/lib/config.test.ts` (Node test runner through `tsx`, no network, no clock) for the option parsing of
  `--topology`, `--profile` and `--scenario`, the default profile values and the rejection of a plateau step shorter than 10 minutes.
  Then add `loadtest/package.json` with the devDependencies `typescript`, `@types/k6` and `tsx`, each pinned, and
  `loadtest/tsconfig.json` with `strict` set. Add the scripts `typecheck` (`tsc --noEmit`) and `load` (`tsx runner/main.ts`).
  Verify: `npm run typecheck` passes and `find loadtest \( -name '*.js' -o -name '*.mjs' \)` prints nothing.
- [x] 1.2 Add `loadtest/lib/config.ts` with the k6 image tag as the single pinned constant, the namespace `ingot-loadtest` and the
  refusal of any other namespace. Verify: the test of 1.1 passes unchanged, and a unit test asserts that the refusal names the
  rejected namespace.
- [ ] 1.3 Test: `loadtest/lib/artifacts.test.ts` asserts that the same seed yields identical artifact bytes and SHA-1 values on two
  calls and different bytes for a different seed, and that the 200 MB artifact is produced as chunks and never as one buffer. Then add
  `loadtest/lib/artifacts.ts` with the seeded generator for the sizes 1 KB, 1 MB, 50 MB and 200 MB, `loadtest/lib/auth.ts` and
  `loadtest/lib/checks.ts` for Basic and token headers and the shared checks, and `loadtest/lib/setup.ts`, which creates the fixtures
  in `setup()` through the admin API only (a unit test with a fake admin client asserts that no other client is called).
- [x] 1.4 Test: `loadtest/lib/sizes.test.ts` asserts the five reference sizes N, T, S, M and L with their CPU and memory values, and
  that the requests of every size equal its limits. Then add `loadtest/lib/sizes.ts`, the one file that defines them.
- [x] 1.5 Add `loadtest/lib/thresholds.ts`, the one file that declares the thresholds of every scenario, and a unit test that
  reads it and asserts that every scenario name of the design has a threshold entry. Every entry starts as `provisional` and is set
  from the first measured run (design.md D3).
- [x] 1.6 Open the pull request `test(load): add the typescript scaffolding for k6 load tests` on main, with the template filled as the
  maintainer's story. Verify with `gh pr view --json title,body,files`.

## 2. Resolve, deploy and mix scenarios (test(load))

Pull request: `test(load): add the resolve, deploy and mix scenarios`

- [x] 2.1 Test: `loadtest/scenarios/resolve.test.ts` for the request list of a Maven and a Gradle client (pom, jar, `.sha1`, `.module`)
  against a fixture. Then add `loadtest/scenarios/resolve.ts` with the executor, the tag `scenario:resolve` and the expected status set.
- [x] 2.2 Add `loadtest/scenarios/deploy.ts` (PUT of 1 KB, 1 MB, 50 MB and 200 MB artifacts, several versions concurrently, the 200 MB
  body streamed from the generator of 1.3 and not held per virtual user). Verify the executor and the sizes against the artifacts of 1.3.
- [x] 2.3 Add `loadtest/scenarios/mix.ts`, which combines `resolve` and `deploy` with weights set in one file. Test: the weights sum to 1
  and every scenario present is in the mix. Later scenarios join the weights in group 3.
- [ ] 2.4 Verify: each scenario runs with `k6 run --iterations 1` against an Ingot started locally with the default settings, and passes
  its checks.
- [x] 2.5 Open the pull request `test(load): add the resolve, deploy and mix scenarios` on the branch of 1.6, with the template filled.
  Verify with `gh pr view --json title,body,files`.

## 3. Remaining function scenarios (test(load))

Pull request: `test(load): add the remaining function scenarios`

- [x] 3.1 Add `loadtest/scenarios/resolve-miss.ts`: 404 answers on group repositories counted as expected through
  `http.expectedStatuses(200, 404)`. Verify with a unit test of the request list and the expected status set.
- [x] 3.2 Add `loadtest/scenarios/metadata.ts` (snapshot `maven-metadata.xml`).
- [x] 3.3 Add `loadtest/scenarios/mirror.ts`: a proxy repository against the in-cluster upstream mock of 5.3 only. A unit test asserts
  that no URL in the scenario points outside the cluster. The live check runs in group 5 (task 5.3), and the scenario joins the mix
  only after that check passes.
- [x] 3.4 Add `loadtest/scenarios/auth.ts` for Basic and token access to private repositories, and tag the requests so that the
  credential cache can be read per scheme.
- [x] 3.5 Add `loadtest/scenarios/search-browse.ts` (search API and directory listings) and `loadtest/scenarios/dashboard.ts` (login and
  repository list).
- [ ] 3.6 Extend `loadtest/scenarios/mix.ts` to all eight function scenarios with the weights of group 2. Test: the weights sum to 1 and
  every scenario is in the mix.
- [x] 3.7 Open the pull request `test(load): add the remaining function scenarios` on the branch of 2.5, with the template filled.
  Verify with `gh pr view --json title,body,files`.

## 4. Capacity search profiles (test(load))

Pull request: `test(load): add the capacity search profiles`

- [x] 4.1 Test: `loadtest/profiles/plateau.test.ts` for the step list of 60, 70, 80, 90 and 100 percent of a breakpoint, the default
  hold of 15 minutes and the rejection of a hold under 10 minutes. Then add `loadtest/profiles/plateau.ts` as a pure function that
  returns the step list. No clock and no k6 runtime are read.
- [x] 4.2 Test: `loadtest/lib/pass-rule.test.ts` for the pass rule of design.md D3 with a table: p95 under the threshold, error rate
  under 1 percent, zero dropped iterations, and a step that breaks in its last minute does not pass. Then add
  `loadtest/lib/pass-rule.ts`.
- [x] 4.3 Test: `loadtest/lib/maximum.test.ts` for the maximum sustainable rate: the highest passing step, no maximum when no step
  passes, and no maximum when the breakpoint was not reached. Then add `loadtest/lib/maximum.ts`.
- [ ] 4.4 Add `loadtest/profiles/breakpoint.ts` (ramping arrival rate with `abortOnFail` and `delayAbortEval`, configurable ceiling),
  wire `loadtest/profiles/plateau.ts` to the `constant-arrival-rate` executors, and add `loadtest/profiles/soak.ts` (the maximum for
  2 hours by default, configurable). Verify: the breakpoint profile reports a breakpoint against the local Ingot of 2.4 with a low ceiling.
- [x] 4.5 Emit `ingot_load_step_passed` at the end of every plateau step, with the step rate as value and the `result` tag. Test: the
  metric name and the tag values are asserted by a unit test of the emitter with a fake sink.
- [x] 4.6 Open the pull request `test(load): add the capacity search profiles` on the branch of 3.7, with the template filled.
  Verify with `gh pr view --json title,body,files`.

## 5. Topology overlays and upstream mock (test(load))

Pull request: `test(load): add the topology overlays and the upstream mock`

- [x] 5.1 Add the overlays `loadtest/topologies/monolith.yaml`, `ha.yaml` and `split.yaml`. Verify: `helm template` with each overlay
  renders and the requests of every Ingot container equal its limits for each size of `loadtest/lib/sizes.ts`.
- [x] 5.2 Add `loadtest/topologies/scalable.yaml` and `scalable-read-1.yaml`, `scalable-read-2.yaml`, `scalable-read-4.yaml` for the
  scaling series. These files render only after `add-worker-target` is merged, so they are added in this pull request only when that
  change is on main, and otherwise in a follow-up pull request. Verify: the read replica count of each rendered Deployment matches the
  file name.
- [ ] 5.3 Add `loadtest/k8s/upstream-mock.yaml`: an nginx Deployment and Service in `ingot-loadtest` that serve fixed artifacts, with the
  latency set by a value. Verify: the mock answers with the bytes of the seeded artifacts of 1.3 at latency 0 and at a set latency,
  and the live check of the mirror scenario (3.3) passes against it.
- [x] 5.4 Verify on every overlay that PostgreSQL, bucket and NATS settings are identical across overlays.
- [x] 5.5 Open the pull request `test(load): add the topology overlays and the upstream mock` on the branch of 4.6, with the template
  filled. Verify with `gh pr view --json title,body,files`.

## 6. k6-operator runner (test(load))

Pull request: `test(load): add the k6-operator runner`

- [x] 6.1 Test: `loadtest/runner/testrun.test.ts` for the `TestRun` manifest built from the options: the namespace, the `parallelism`, the
  ConfigMap name of the archive, the image tag from `config.ts` and the anti-affinity to the Ingot pods on the node hostname. Then add
  `loadtest/runner/testrun.ts`.
- [x] 6.2 Test: `loadtest/runner/steps.test.ts` for the order of the runner steps (install, wait for readiness, archive, apply, wait for
  completion, record, uninstall) and for the uninstall running after a failed step. The record step is an injected writer that group 8
  implements. Then add `loadtest/runner/main.ts` and `loadtest/runner/steps.ts`. No `sleep`: waits use deadlines from an injected clock.
- [ ] 6.3 Add the `k6 archive` step that packs the scenario and the libraries into the ConfigMap. Verify: `npm run load -- --topology
  monolith --profile breakpoint --scenario resolve` runs end to end on the cluster and uninstalls its release. This first cluster run
  sets the provisional thresholds (design.md D3), with abort on fail disabled.
- [x] 6.4 Verify: a `TestRun` with a target namespace other than `ingot-loadtest` is refused by the runner before any `kubectl` call.
- [x] 6.5 Test: `loadtest/runner/nodes.test.ts` for the reference node check: a reference run with fewer labelled nodes than the topology
  needs is refused before the install, with the found and the needed count; a non-reference run skips the check and is marked
  `reference: false`. Then add `loadtest/runner/nodes.ts` with the label name read from `config.ts`.
- [x] 6.6 Test: `loadtest/runner/environment.test.ts` asserts that the environment built by the runner sets
  `K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true`, the `experimental-prometheus-rw` output and the tags `testid`, `topology`,
  `scenario`, `profile` and `ingot_version` on every sample. Then add `loadtest/runner/environment.ts`.
- [x] 6.7 Open the pull request `test(load): add the k6-operator runner` on the branch of 5.5, with the template filled. Verify with
  `gh pr view --json title,body,files`.

## 7. Grafana dashboard (test(load))

Pull request: `test(load): add the load test grafana dashboard`

- [x] 7.1 Test: `loadtest/grafana/dashboard.test.ts` reads `loadtest/grafana/ingot-load-tests.json` and asserts that the variables
  `testid`, `topology`, `scenario` and `size` exist, with `size` offering the five values of `loadtest/lib/sizes.ts`, and that the
  panels named in the design exist (target versus achieved rate, p50, p95 and p99 per scenario, error rate, dropped iterations, active
  VUs, bytes per second, server CPU, heap and GC, database pool active and pending, storage latency per pod, the maximum table and the
  topology bar chart).
- [ ] 7.2 Add `loadtest/grafana/ingot-load-tests.json` with the panels, the annotations at step changes and the maximum table grouped by
  `topology` and `size`. Verify: the dashboard loads in Grafana against the Mimir datasource and the panels show data for one test run.
- [x] 7.3 Open the pull request `test(load): add the load test grafana dashboard` on the branch of 6.7, with the template filled. Verify
  with `gh pr view --json title,body,files`.

## 8. Result records and the reference capacities page (test(load))

Pull request: `test(load): add result records and the reference capacities page`

- [ ] 8.1 Test: `loadtest/runner/record.test.ts` for the result record: the fields of design.md D13, an outcome of `not-ready` or
  `oom-killed` with its step instead of a rate when a pod never became ready or was OOM-killed, and one file per topology, size and
  scenario. Then add `loadtest/runner/record.ts` and wire it as the record step of `loadtest/runner/steps.ts`.
- [x] 8.2 Test: `loadtest/runner/capacities.test.ts` for the generator: only the core scenarios `mix`, `resolve` and `deploy` are
  published, a non-reference record contributes no value, every topology, size and core scenario has a cell (90 cells), and an
  `oom-killed` record reads "not supported at this size". Then add `loadtest/runner/capacities.ts`.
- [ ] 8.3 Run `loadtest/runner/capacities.ts` to write `docs/gitbook/references/reference-capacities.md` with the method, the hardware
  and the note that the values are measured reference values, not guarantees. Add the page under "References" in `docs/gitbook/SUMMARY.md`.
- [x] 8.4 Add `docs/gitbook/contributing/run-the-load-tests.md`: a how-to with the prerequisites, the runner command, the profiles, how
  to read the maximum table, the node pool and time-of-day note, the run budget, and the on-demand scenarios that are not published.
  Add the page under "Contributing" in `docs/gitbook/SUMMARY.md`.
- [ ] 8.5 Verify: every link on the two pages resolves inside `docs/gitbook`, and the pages name no cluster host, credential or Mimir
  endpoint.
- [x] 8.6 Open the pull request `test(load): add result records and the reference capacities page` on the branch of 7.3, with the
  template filled, as the last pull request of the stack. Verify with `gh pr view --json title,body,files`.

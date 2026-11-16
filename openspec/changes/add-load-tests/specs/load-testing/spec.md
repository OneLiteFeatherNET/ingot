# Spec Delta

## Purpose

A load test suite in the top-level directory `loadtest/` finds, for every Ingot deployment shape and every important function, the
highest request rate that holds for at least 10 minutes while the thresholds are met, and shows the result in Grafana. The suite is
TypeScript only, runs in its own Kubernetes namespace, and changes no server code, image, chart default or configuration.

## ADDED Requirements

### Requirement: TypeScript only and no change to the product

The suite SHALL be written in TypeScript and SHALL live in the top-level directory `loadtest/`. The directory SHALL NOT contain a
JavaScript or `.mjs` source file, and `npm run typecheck` (`tsc --noEmit`) SHALL pass. The k6 version SHALL be pinned in one place.
The change SHALL NOT alter the server, the image, the chart defaults or any configuration key.

#### Scenario: Source files

- **WHEN** the files under `loadtest/` are listed
- **THEN** no file ends in `.js` or `.mjs`, and the type check exits with code 0

#### Scenario: Product unchanged

- **WHEN** the change is applied to a checkout of main
- **THEN** the rendered chart defaults, the image contents and the configuration keys are identical to main

### Requirement: One scenario per function

The suite SHALL provide the scenarios `resolve`, `resolve-miss`, `metadata`, `deploy`, `mirror`, `auth`, `search-browse` and
`dashboard`, and a `mix` scenario that combines them with weights. Every request SHALL carry the tag `scenario:<name>`. Test data
SHALL be seeded in `setup()` with a fixed seed.

#### Scenario: Tag per request

- **WHEN** a scenario sends a request
- **THEN** the request is tagged with the name of its scenario

#### Scenario: Repeatable data

- **WHEN** the same scenario is run twice with the same seed
- **THEN** the seeded artifacts have the same bytes and the same SHA-1 values in both runs

#### Scenario: Resolve misses are expected

- **WHEN** `resolve-miss` receives a `404` from a group repository
- **THEN** the response counts as expected and does not count as an error

### Requirement: Open arrival model

Load SHALL be generated with the `constant-arrival-rate` and `ramping-arrival-rate` executors. The suite SHALL NOT use a fixed number
of virtual users to set the load.

#### Scenario: Slow server

- **WHEN** the server answers more slowly than the arrival rate allows
- **THEN** the iterations keep starting at the set rate, and the `dropped_iterations` count rises

### Requirement: Breakpoint phase

The breakpoint phase SHALL ramp the arrival rate of one scenario on one topology until a threshold breaks. Its threshold SHALL use
`abortOnFail` with `delayAbortEval`. When the ramp reaches its configured ceiling without a break, the run SHALL report that no
breakpoint was found and SHALL NOT start the plateau phase.

#### Scenario: Threshold breaks

- **WHEN** the p95 latency of the scenario crosses its threshold during the ramp
- **THEN** the run stops and the rate at which it stopped is recorded as the upper bound

#### Scenario: No breakpoint

- **WHEN** the ramp reaches its ceiling and no threshold breaks
- **THEN** the run reports that no breakpoint was found and the plateau phase does not start

### Requirement: Plateau steps held at least ten minutes

The plateau phase SHALL run the steps at 60, 70, 80, 90 and 100 percent of the breakpoint. Each step SHALL be held at a constant
arrival rate for a configurable duration, 15 minutes by default. A duration shorter than 10 minutes SHALL be refused.

#### Scenario: Default hold

- **WHEN** the plateau profile is started without a hold duration
- **THEN** each of the five steps is held for 15 minutes

#### Scenario: Short hold refused

- **WHEN** the plateau profile is started with a hold of 9 minutes
- **THEN** the profile is refused before any load is sent, with a message that names the 10 minute minimum

### Requirement: Pass rule and maximum sustainable rate

A plateau step SHALL pass only when, for its whole duration, the p95 latency is under the threshold of its scenario, the error rate is
under 1 percent and there are zero dropped iterations. A step that breaks at any point of its duration SHALL NOT pass. The maximum
sustainable rate of a topology and a scenario SHALL be the rate of the highest passing step, and SHALL be absent when no step passes.

#### Scenario: Highest passing step

- **WHEN** the steps at 60, 70 and 80 percent pass and the steps at 90 and 100 percent fail
- **THEN** the maximum sustainable rate is the rate of the 80 percent step

#### Scenario: Break in the last minute

- **WHEN** a step meets every threshold for 14 minutes and breaks in its last minute
- **THEN** the step does not pass

#### Scenario: Dropped iteration fails the step

- **WHEN** a step has one dropped iteration
- **THEN** the step does not pass, even if its latency and error rate are within their limits

### Requirement: Thresholds in one file

The thresholds of every scenario SHALL be declared in one file, `loadtest/lib/thresholds.ts`. Each threshold SHALL be overridable for a run.

#### Scenario: Override for a run

- **WHEN** a run sets a `resolve` p95 threshold of 200 ms
- **THEN** the run uses 200 ms for `resolve` and the other thresholds keep their declared values

### Requirement: Soak profile

The soak profile SHALL run the maximum sustainable rate of a topology and a scenario for 2 hours by default. The duration SHALL be
configurable.

#### Scenario: Soak default

- **WHEN** the soak profile is started for a found maximum without a duration
- **THEN** the load runs for 2 hours at that rate

### Requirement: Deployment shapes as topology overlays

Each deployment shape SHALL be a Helm values overlay under `loadtest/topologies/`: `monolith` (chart mode `allInOne`, one replica),
`ha` (`allInOne`, three replicas), `split` (server and dashboard container), `scalable` (read, write and worker Deployments) and a
scaling series of `scalable` with 1, 2 and 4 read replicas.

#### Scenario: Scaling series

- **WHEN** the overlay `scalable-read-4.yaml` is rendered
- **THEN** the read Deployment has four replicas

### Requirement: Equal pods and shared services across topologies

Every Ingot pod SHALL have CPU and memory requests equal to its limits, taken from the reference size of the run. All overlays SHALL use
the same PostgreSQL database, the same object storage bucket and the same NATS server.

#### Scenario: Equal pods across shapes

- **WHEN** the overlays are rendered
- **THEN** every Ingot container has requests equal to limits, with the values of the size of the run, in every overlay

### Requirement: Reference pod sizes

The load tests SHALL measure every topology at five reference pod sizes, defined in one file, `loadtest/lib/sizes.ts`: N with 0.25 CPU
and 256 MiB, T with 0.5 CPU and 512 MiB, S with 1 CPU and 1 GiB, M with 2 CPU and 2 GiB, and L with 4 CPU and 4 GiB. Requests SHALL
equal limits.

#### Scenario: Size applied

- **WHEN** a run is started with size M
- **THEN** every Ingot pod has 2 CPU and 2 GiB as requests and as limits

#### Scenario: Unsupported size is a result

- **WHEN** the pods of a run at size N never become ready, or are OOM-killed during the run
- **THEN** the run is recorded with the outcome `not-ready` or `oom-killed` and the step at which it happened, and no rate is recorded for that scenario

### Requirement: Reference runs on dedicated nodes

A run tagged `reference` SHALL run only when the Ingot pods and the k6 pods land on nodes that carry the load test label and tolerate its
taint. The runner SHALL refuse such a run before the install when too few matching nodes exist.

#### Scenario: Too few dedicated nodes

- **WHEN** a reference run is started and the labelled node pool has fewer nodes than the topology needs
- **THEN** the runner exits before the install, with the number of nodes it found and the number it needs

#### Scenario: Shared nodes

- **WHEN** a run without the reference tag is started on shared nodes
- **THEN** its result record carries `reference: false`

### Requirement: Result records and reference capacities page

Each reference run SHALL write one JSON record under `loadtest/results/<ingot_version>/` with the Ingot version, topology, pod size, storage
backend, database, node type, k6 version, date, the `reference` flag and the outcome per scenario. The generator SHALL render
`docs/gitbook/references/reference-capacities.md` from the records with `reference: true` only. The page SHALL state the method, the hardware
and that the values are measured reference values, not guarantees.

#### Scenario: Only reference values published

- **WHEN** the page is generated from a directory that holds a record with `reference: false`
- **THEN** that record contributes no value to the page

#### Scenario: Unsupported size shown

- **WHEN** a reference record has the outcome `oom-killed` for size N and scenario `deploy`
- **THEN** the page cell for that topology, size N and `deploy` reads "not supported at this size" and is not left empty

### Requirement: Namespace isolation

The load tests SHALL run only in the namespace `ingot-loadtest`. The runner SHALL refuse any other namespace before it calls the cluster,
so that no load reaches the production namespace or the staging namespace.

#### Scenario: Other namespace refused

- **WHEN** the runner is started with a target namespace other than `ingot-loadtest`
- **THEN** the runner exits with a message that names the refused namespace, and makes no call to the cluster

### Requirement: Execution with the k6-operator

The load generator SHALL be a k6-operator `TestRun` with a configurable parallelism. The scripts SHALL be packed with `k6 archive` and
passed as a ConfigMap. The load generator pods SHALL have an anti-affinity against the Ingot pods, so that they do not share a node with
them.

#### Scenario: Generator apart from the server

- **WHEN** the `TestRun` is applied
- **THEN** the generator pods carry a pod anti-affinity that names the Ingot pods on the node hostname

### Requirement: Runner command

`npm run load -- --topology <topology> --profile <profile> --scenario <scenario>` SHALL run with `tsx`. It SHALL install the chart with the
overlay, wait for readiness, apply the `TestRun`, wait for its completion, record the result and uninstall the release. The waits SHALL end
at deadlines computed from an injected clock.

#### Scenario: Run completes

- **WHEN** the runner is started with a valid topology, profile and scenario
- **THEN** it installs the release, waits for readiness, waits for the `TestRun` to complete, records the result and uninstalls the release

#### Scenario: Failure still uninstalls

- **WHEN** the `TestRun` fails
- **THEN** the runner records the failure and still uninstalls the release

### Requirement: Results in Mimir with native histograms

k6 SHALL write its samples to Mimir through the `experimental-prometheus-rw` output with native histograms enabled. Every sample SHALL
carry the tags `testid`, `topology`, `scenario`, `profile` and `ingot_version`. Each finished plateau step SHALL emit the metric
`ingot_load_step_passed` with the step rate as its value and a `result` tag of `pass` or `fail`.

#### Scenario: Step result recorded

- **WHEN** a plateau step ends
- **THEN** `ingot_load_step_passed` is written with the step rate as its value and the `result` tag set to `pass` or `fail`

#### Scenario: Tags on samples

- **WHEN** a sample is written during a run
- **THEN** it carries the five tags of the run

### Requirement: Grafana dashboard in the repository

The dashboard SHALL be checked in as `loadtest/grafana/ingot-load-tests.json` with the variables `testid`, `topology`, `scenario` and `size`, the last one with the five reference sizes.
The dashboard test SHALL check those variables and the panel titles.

#### Scenario: Dashboard in review

- **WHEN** the dashboard JSON is changed
- **THEN** the change is a diff of the file in the repository and the dashboard test checks its variables and panel titles

### Requirement: Dashboard load panels

The dashboard SHALL show the target and achieved request rate, the p50, p95 and p99 latency per scenario, the error rate, the dropped
iterations, the active virtual users and the bytes per second.

#### Scenario: Load panels present

- **WHEN** the dashboard is opened for one test run
- **THEN** each of these panels shows data for that run

### Requirement: Dashboard server panels and comparison

The dashboard SHALL show the server CPU, heap and garbage collection, the database pool active and pending counts and the storage latency,
per pod. It SHALL show a table of the maximum sustainable rate grouped by topology and size, per scenario, a bar chart that compares the
topologies, and annotations at the step changes.

#### Scenario: Maximum table

- **WHEN** the dashboard is opened with the variable `scenario` set to `resolve`
- **THEN** the maximum table shows, per topology and size, the highest rate with `result=pass`

### Requirement: Not part of pull request continuous integration

The load tests SHALL NOT run in pull request continuous integration. The change SHALL NOT add a continuous integration job for `loadtest/`.

#### Scenario: Pull request

- **WHEN** a pull request is opened or updated
- **THEN** no job starts the load tests and no job starts the type check of `loadtest/`

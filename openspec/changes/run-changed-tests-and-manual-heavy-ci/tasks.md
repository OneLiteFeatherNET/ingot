# Tasks

Each code pull request is one type, roughly under 400 changed lines excluding tests, and builds and passes on its own. Tests are written and
seen to fail first. The code pull requests are opened after the proposal is merged, and after #257, #260, #261, #264, #266 and #268 are
merged. Task checkboxes are ticked in a follow-up pull request, never in a code pull request.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose run-changed-tests-and-manual-heavy-ci`: this change only, from the remote main branch. Verify:
  `openspec validate run-changed-tests-and-manual-heavy-ci --strict` passes and the dash grep prints nothing.

## 2. Measure first

- [ ] 2.1 `ci(heavy)`: record, on a throwaway branch (no pull request), the cold and warm wall clock of
  `Build / Build` and `Coverage (backend)` without integration, the backend compile time, the Gradle cache hit rate, the duration of
  `DCO sign-off` and `OpenAPI breaking changes`, per-test unit timings from the JUnit XML, and a warm amd64-only image build and start.
  Paste the numbers into the pull request of 3.1. Verify: every "not measured" item of `design.md` has a value or a stated reason.

## 3. Scope and light checks

- [x] 3.1 `ci(heavy)`: extend `coverage_changes.sh` into `ci_scope.sh` that also emits `image`, `chart`, `e2e` and `unit` flags, with
  the fail-open rules of Decision 4 and a script test under `.github/scripts/tests/`. Verify: the test fails first, then passes.
- [x] 3.2 `ci(heavy)`: gate `Licenses`, `Vulnerabilities`, `SBOM`, `Plugin API` and the frontend jobs on the scope flags with job-level
  `if`, using the `!cancelled() && (needs.scope.result != 'success' || ...)` pattern. Verify: a docs-only pull request runs none of them
  and the required checks report success or skipped.
- [x] 3.3 `ci(heavy)`: add the `Light checks` aggregate with the duration against budget summary. Verify: a failing light job fails it,
  a skipped one does not, an over-budget job only warns.
- [x] 3.4 `ci(build)`: enable the Gradle build cache in CI with the `--build-cache` flag, and let `setup-gradle` write the cache from main
  only. Verify: the second run of a pull request shows `FROM-CACHE` lines and its job time drops.
- [ ] 3.5 `ci(build)`: trial `--configuration-cache` on the unit test task in CI. Keep it only if the trial is green and faster.
  Verify: the trial result is in the pull request description.
- [x] 3.6 `ci(heavy)`: make `Build / Build` and `Coverage (backend)` run unit tests only on pull requests, with the selection of #257.
  Verify: the integration task does not run, and the light critical path is at most 300 s.
- [ ] 3.7 `ci(heavy)`: only if 3.6 misses the budget, shard `:reposilite-backend:test` into a matrix, and only if that is not enough,
  `test(backend)`: tag the slowest unit tests `@Tag("slow")` and exclude them from `test`. Each is its own pull request.

## 4. Heavy workflow

- [x] 4.1 `ci(heavy)`: add `heavy_gate.py` and its tests: label set, fork rule, release head ref, scope failure and event type in, the list
  of heavy jobs to run out, `full` on a release pull request. Verify: the tests fail first, then pass.
- [x] 4.2 `ci(heavy)`: add `heavy.yml` with the gate, the concurrency group of Decision 1, `contents: read`, and the `Heavy tests`
  aggregate. No heavy job yet. Add a shape test that rejects `pull_request_target`, `secrets.` and `schedule`.
- [x] 4.3 `ci(heavy)`: move `Container images` (with #266 and #268 applied) from `ci.yml` to `heavy.yml`, same job name. Verify: the job
  runs with `ci:images` and is skipped without it.
- [x] 4.4 `ci(heavy)`: move `HA smoke test` to `heavy.yml`, same job name.
- [x] 4.5 `ci(heavy)`: move `Dashboard end-to-end` to `heavy.yml`, same job name.
- [x] 4.6 `ci(heavy)`: move `Helm chart` to `heavy.yml`, started by `ci:images`, same job name.
- [x] 4.7 `ci(heavy)`: add `Integration tests (backend)` to `heavy.yml` with `INGOT_TEST_FORKS` from #261, selected tests for a label and
  the full suite on a release pull request or a dispatch. Remove the integration step from `coverage-backend`.
- [x] 4.8 `ci(heavy)`: start `Search backends` (after #303 and #318 merge) and `CodeQL` on pull requests from a label, replacing the
  workflow-level `paths` filter with a job-level condition. Their own schedules stay.
- [x] 4.9 `ci(heavy)`: add the release gate: on a head ref starting with `release-please--`, `Heavy tests` is red unless every heavy job
  passed in full on the head commit. Verify: a test with a fixture event for a release pull request.
- [x] 4.10 `ci(heavy)`: add `workflow_dispatch` on main for an on-demand full run. If the maintainer answers open question 4 with
  "nightly", a separate `ci(heavy)` pull request adds the schedule and the issue report.

## 5. Light substitute for the contract paths (open question 8)

- [ ] 5.1 `ci(heavy)`: only if 2.1 shows an amd64-only build and start fits 300 s, add `Image smoke` and `Chart lint` as light jobs
  gated on the image and chart scope flags. Verify: an unmodified upstream compose file starts with only the image line swapped.

## 6. Developer guide

- [x] 6.1 `docs(contributing)`: add `docs/contributing/heavy-tests.md` listing each label, its job, its duration and the local command
  through `heavy`. Verify: every command in the guide was run once.

## 7. Repository settings (maintainer, not files)

- [ ] 7.1 Create the labels `ci:heavy`, `ci:integration`, `ci:e2e`, `ci:images`, `ci:ha`, `ci:search`, `ci:codeql` with descriptions.
- [ ] 7.2 After 3.3 and 4.2 are green on main, add `Light checks` and `Heavy tests` to the required checks of "Protect main", and remove
  `Container images` and `Helm chart`. Record the date in the pull request.

## 8. Open the code pull requests

Each is opened against `main` after its predecessors merge, with the sections of `.github/PULL_REQUEST_TEMPLATE.md` filled.

- [x] 8.1 Open `ci(heavy): detect the scope of a pull request for every job` (3.1).
- [x] 8.2 Open `ci(heavy): skip light jobs a pull request cannot affect` (3.2).
- [x] 8.3 Open `ci(heavy): add the light checks aggregate with a time budget` (3.3).
- [x] 8.4 Open `ci(build): read and write the gradle build cache in ci` (3.4).
- [ ] 8.5 Open `ci(build): trial the gradle configuration cache for unit tests` (3.5).
- [x] 8.6 Open `ci(heavy): run unit tests only on pull requests` (3.6), and the pull requests of 3.7 if they are needed, titled
  `ci(heavy): shard the backend unit tests` and `test(backend): tag the slowest unit tests as slow`.
- [x] 8.7 Open `ci(heavy): decide which heavy jobs a pull request asks for` (4.1).
- [x] 8.8 Open `ci(heavy): add the heavy workflow and its aggregate` (4.2).
- [x] 8.9 Open `ci(heavy): start the container images job from a label` (4.3).
- [ ] 8.10 Open `ci(heavy): start the ha smoke test from a label` (4.4).
- [x] 8.11 Open `ci(heavy): start the dashboard end-to-end tests from a label` (4.5).
- [ ] 8.12 Open `ci(heavy): start the helm chart job from a label` (4.6).
- [x] 8.13 Open `ci(heavy): run the integration suite from a label` (4.7).
- [x] 8.14 Open `ci(heavy): start the search backends and codeql jobs from a label` (4.8).
- [ ] 8.15 Open `ci(heavy): require a full heavy run on the release pull request` (4.9).
- [x] 8.16 Open `ci(heavy): allow an on-demand heavy run on main` (4.10).
- [ ] 8.17 Open `ci(heavy): smoke start the image and lint the chart` (5.1), if built.
- [x] 8.18 Open `docs(contributing): explain how to run the heavy tests` (6.1).

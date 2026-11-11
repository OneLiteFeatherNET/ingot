# Tasks

Every commit of the code change is typed `ci(coverage)`, with the scope `coverage`. The proposal pull request carries only this change
directory. Tests are written and seen to fail before the script exists. The code pull request is opened after pull request #186 is merged,
because both edit the `coverage` job, and after the baseline of the design has been recorded.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose select-backend-tests-by-package`: this change only, in its own proposal pull request from the remote
  main branch. Verify: `openspec validate select-backend-tests-by-package --strict` passes and the dash grep prints nothing.

## 2. Baseline

- [ ] 2.1 Record the `Coverage` job duration of the last 20 backend pull requests on main (median and range), and the executed test count
  from the JUnit XML of one full run. Write both into the pull request description of the code change. Verify: the figures are cited
  with their run numbers.

## 3. Tests first

- [ ] 3.1 `ci(coverage)`: write the failing unit tests of `.github/scripts/select_backend_tests.py` under `.github/scripts/tests/`, with
  temporary directories and no network. They assert: a changed file maps to its package; a direct importer is selected and a second-order
  importer is not; each full-run trigger of the spec yields `mode=full`; a selection above 60 percent yields `mode=full`; a smoke class that
  is missing from the tree fails; a path outside the Kotlin roots yields `mode=full`. Verify: the tests fail because the script is missing.
- [ ] 3.2 `ci(coverage)`: write the failing workflow shape test under `.github/scripts/tests/`. It reads `.github/workflows/ci.yml` and
  asserts: the `coverage` job keeps its name and its `needs`; the Codecov upload step is guarded by `mode=full`; the partial run passes
  `--tests` filters to the `test` and `integration` tasks. Verify: the test fails on main.

## 4. Selection script and job

- [ ] 4.1 `ci(coverage)`: implement `.github/scripts/select_backend_tests.py` against the tests of 3.1, with the import graph read from the
  sources, the infrastructure list and the smoke list in the script. Verify: the tests of 3.1 pass, and
  `python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests` stays green.
- [ ] 4.2 `ci(coverage)`: wire the script into the `coverage` job: a full run keeps `./gradlew :reposilite-backend:testCoverage` and the
  Codecov upload; a partial run runs `test` and `integration` with the filters and no report and no upload. Verify: the test of 3.2 passes.
- [ ] 4.3 `ci(coverage)`: check on a pull request that a `--tests` filter reaches the `integration` task (the design's Decision 6), and
  record which form was used. Verify: the job log shows the filter in the `integration` task and the number of tests run is below the
  full count.

## 5. Measurement

- [ ] 5.1 `ci(coverage)`: record the script's decision for the last 20 backend pull requests (partial or full, with the reason) in the code
  pull request description. Verify: the table is in the description, and the full-run share matches the design's measured shares.

## 6. Open the pull request

- [ ] 6.1 Open the code pull request against `main` with the title `ci(coverage): run only the backend tests a change can reach on pull requests`,
  after pull request #186 and the proposal are merged. Fill the sections of `.github/PULL_REQUEST_TEMPLATE.md`. Verify: the pull request
  exists under that title and its `Coverage` check is running.

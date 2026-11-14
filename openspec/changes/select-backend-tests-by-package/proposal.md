# Proposal

## Why

A pull request that changes one part of the backend waits for the whole backend suite. The `Coverage` job runs every unit test
(2,642 test annotations under `reposilite-backend/src/test`) and every integration test (574 annotations under
`reposilite-backend/src/integration`, 21 of them parameterised, so the executed count is higher). On a recent pull request the job ran for
79 minutes (run 43021374786, 12:46 to 14:04 UTC on 2026-11-11), inside the 75 to 100 minute range seen across pull requests. A
reviewer who changed the mapping of one maven repository waits that long for a result that cannot depend on most of the code.

The coverage job is already gated by change detection: `coverage-changes` skips it for documentation-only pull requests
(`.github/workflows/ci.yml`, line 548, with the deny list in `.github/scripts/coverage_changes.sh`). That gate answers "does the
backend need a run at all", not "which part of the backend". Pull request #186 splits the coverage job into backend and frontend
jobs. This change goes one step further for the backend: it runs only the test packages that a change can reach, and keeps the full
run for the cases where a narrow run would be unsafe.

## What Changes

- **Selection.** A new script `.github/scripts/select_backend_tests.py` reads the changed paths of the pull request and prints the
  Gradle `--tests` filters for the `test` and `integration` tasks. A changed file maps to its package (`com.reposilite.<domain>` or
  `net.onelitefeather.ingot.<domain>`). The selection is that package plus every package whose Kotlin sources import a class from it
  (the direct importers). Packages are the domains, so no test has to be tagged by hand.
- **Full run.** The coverage job runs the complete suite whenever one of the full-run triggers in the design applies: a change to a
  shared infrastructure package, to build logic, to resources or to the frontend, a path the script cannot map, a selection above
  60 percent of all test annotations, or a push to main.
- **Smoke set.** Every pull request that runs the backend also runs a fixed smoke set (characterisation, OpenAPI, security headers,
  startup and the dependency rules test), whatever the selection.
- **Coverage upload.** Only full runs upload to Codecov. A partial run would report a lower coverage figure, and that figure must not
  become the base of the next comparison.
- **Job name.** The job stays `Coverage`, so the required check name does not change. A partial run that passes reports success.

### Out of scope

- Changing any test, its assertions or its fixtures. The selection only decides which existing tests run.
- The `containerTest` source set (57 annotations), which runs in the `Container images` job.
- The `Plugin API`, `Frontend`, `Helm chart`, `HA smoke test` and `Vulnerabilities` jobs.
- Selection by JUnit `@Tag` (rejected in the design), by bytecode analysis, or by Gradle's build cache.
- Weighting the selection by measured test duration. The first version counts test annotations; weighting is a later refinement.
- A new scheduled workflow. `ci.yml` has no `schedule` trigger today, so the nightly full run named in the brief is an open question
  in the design, not part of this change.

## Capabilities

### New Capabilities

- `backend-test-selection`: how the coverage job chooses backend test packages for a pull request, the triggers that force the full
  suite, the smoke set that always runs, and the rule that partial runs never upload coverage.

## Impact

- **Type and scope.** The change ships under `ci(coverage)`. All its commits use `ci(coverage)`. The pull request title, squash-merged
  onto main, is:

  `ci(coverage): run only the backend tests a change can reach on pull requests`

- **Breaking marker.** No breaking change. No image, jar, chart, configuration key, environment variable, deployment manifest or data
  changes. Operators change nothing. The `Coverage` job keeps its name and its required status.
- **Dependencies on other changes.**
  - Pull request #186 (`ci(coverage): split backend and frontend coverage into parallel jobs`) is open and edits the same job. The code
    pull request is opened after #186 is merged, so that the coverage job is rebased once.
- **Code.** `.github/scripts/select_backend_tests.py`, its unit tests under `.github/scripts/tests/`, and the `coverage` and
  `coverage-changes` jobs in `.github/workflows/ci.yml`. No change to `reposilite-backend/build.gradle.kts` is expected; if the
  filters do not reach the `integration` task under `testCoverage`, the design names the fallback.

## Verification

- `openspec validate select-backend-tests-by-package --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/select-backend-tests-by-package` prints nothing.
- In the code pull request, the selection script's tests pass, and the measurement in the design is repeated on the current main:
  the table of selected shares is regenerated and the full-run triggers are checked against the file list of a sample of recent
  pull requests.
- After the change is merged, the median duration of the `Coverage` job on backend pull requests is compared with the baseline in the
  design, and the number of defects that reached main after a partial run is recorded.

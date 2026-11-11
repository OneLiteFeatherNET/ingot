# Design

## Starting point on main

- `.github/workflows/ci.yml` triggers on push and on pull request to main (lines 4-8) and has one concurrency group per ref (line 13).
- The `build` job (line 20) is a reusable workflow for pull requests only, on Java 17 (line 44). The Gradle wrapper validation and
  the setup steps of the other Gradle jobs are the pattern to follow.
- The `frontend` job (line 103) uses Node 24 (line 115), `npm ci` with the npm cache keyed on `reposilite-frontend/package-lock.json`,
  and runs lint and unit tests. It is the nearest model for the Node steps.
- The `images` job (line 186) shows the failure-only upload pattern: `Publish the container test results` (line 270) uploads its
  results. The new job uses the same action with `if: failure()`.
- Every action is pinned by commit SHA with a version comment (for example `actions/setup-node` at line 80, `# v5.0.0`).
- The `chart` job (line 340) and `ha-smoke` (line 481, `timeout-minutes: 45`) show the timeout setting; the new job sets 15.
- No job runs a browser today, and nothing in the repository names a required check for the dashboard.

## Decision 1: one job, appended after `frontend`

The job is added as a separate job, not as steps of `frontend`. A failing browser test then shows as its own check, and the
`frontend` job keeps its meaning of "lint and unit tests", the same reason the file already gives for keeping `frontend` apart from
`licenses`. The job sits directly after `frontend` in the file so that the two dashboard checks are read together.

## Decision 2: Java 17

The Gradle build for the jar uses Java 17, the version the `build` job validates against (line 44). Building the jar with a newer
JDK would test a different toolchain than the one the project declares. The coverage and plugin-api jobs use Java 21 (lines 562 and
605), but they test the backend, not the artifact the suite runs.

## Decision 3: the build and the suite run in one job

The job builds the jar and runs the suite in the same runner. Splitting them would need an artifact handoff of the jar between jobs,
which adds upload time and a second place where a stale jar could be tested. The Gradle build is cached by the setup action the other
Gradle jobs already use.

## Decision 4: artifact rule

The job uploads the Playwright report directory with `if: failure()` and nothing on a green run. The report holds traces and
screenshots, which the suite records only for failed tests (`trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`, as set in
`add-dashboard-e2e-tests`). The retention is short, so a failed run is inspectable without keeping the traces of every green run.

## Decision 5: budget

`timeout-minutes: 15` on the job. The suite's own runtime budget is under five minutes (`add-dashboard-e2e-tests`). The rest is the
Gradle build and the browser install, which is cached on the Playwright version. A hung server fails the job at the timeout instead of
holding the runner.

## Decision 6: required check

The job is named `Dashboard end-to-end`, and that name is the check name branch protection shows. Protection is a repository setting
and not in the repository, so the maintainer adds the name after the first green run on main. Adding it before a green run would block
every pull request on a check that has never run.

## Decision 7: workflow shape test

A unit test under `.github/scripts/tests/` reads `ci.yml` and asserts the job's name, its `needs` (none), its `timeout-minutes`, that
every `uses:` line is pinned to a 40-character SHA, and that the only upload step has `if: failure()`. The repository already runs
Python unit tests from `.github/scripts/tests` in the `images` job (line 205), so the test is run in the same way and adds no new
toolchain. It is written before the job, so it fails first.

## Open questions with defaults

1. Java version: 17 (default). Moving to 21 would match the coverage jobs and needs a check that the jar still targets 17.
2. Required check: added by the maintainer after the first green run on main (default).
3. Nightly run against the container image: not in this change (default). It is an open question of `add-dashboard-e2e-tests`.

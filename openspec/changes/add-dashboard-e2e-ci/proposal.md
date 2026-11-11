# Proposal

## Why

The browser suite from `add-dashboard-e2e-tests` protects the dashboard only if it runs on every pull request and its result is a
required check. Without that wiring, a navigation or sign-in regression is found when someone runs the suite by hand, which is how
the regressions reached staging in the first place. The workflow change is small, but it is a separate kind of change: it alters
how the repository's pipeline runs, not what the tests assert.

## What Changes

- **Job.** One job `dashboard-e2e` in `.github/workflows/ci.yml`, named `Dashboard end-to-end`. It runs on pull requests and on
  pushes to main, like the other jobs.
- **Steps.** Checkout, Java 17, Node 24 with the npm cache, `npm ci` in `reposilite-frontend`, `./gradlew :reposilite-backend:shadowJar`,
  a cached Playwright Chromium install, `npm run e2e`, and an upload of the report directory only when a step failed.
- **Limits.** `timeout-minutes: 15`, and every action pinned by commit SHA with a version comment, as the rest of the workflow.
- **Required check.** After the first green run on main, the maintainer adds `Dashboard end-to-end` to the required checks of the
  main branch. That is a repository setting and is recorded in the pull request, not in a file.

### Out of scope

- The contents of the suite, its flows and its accessibility rules. Those are `add-dashboard-e2e-tests`.
- A nightly run against the container image. That is an open question in `add-dashboard-e2e-tests`.
- Firefox or WebKit jobs.
- Any change to the existing jobs, their steps or their required checks.

## Capabilities

### New Capabilities

- `dashboard-e2e-ci`: the job that runs the browser suite on pull requests and pushes to main, its limits, its artifact rule and
  its position as a required check.

## Impact

- **Type and scope.** The change ships under `ci(e2e)`. All its commits use `ci(e2e)`. The pull request title, squash-merged onto main, is:

  `ci(e2e): run the dashboard end-to-end tests on pull requests`

- **Breaking marker.** No breaking change. No image, jar, chart, configuration key, environment variable or runtime path changes.
  The existing jobs keep their names, steps and required status.
- **Dependencies on other changes.**
  - `add-dashboard-e2e-tests`: this job runs `npm run e2e` and needs that suite, its `e2e` script and its devDependencies on main.
    This change is opened after that suite is merged, and its tasks are not runnable before then.
- **Code.** `.github/workflows/ci.yml` and a workflow shape test under `.github/scripts/tests/`.

## Verification

- `openspec validate add-dashboard-e2e-ci --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-dashboard-e2e-ci` prints nothing.
- The `Dashboard end-to-end` job is green on the code pull request, its test steps finish under five minutes, and the report artifact
  is absent on a green run.

# Tasks

Every commit of this change is typed `ci(e2e)`, with the scope `e2e`. Tests are written and seen to fail before the job exists. The
job depends on `add-dashboard-e2e-tests` being merged, because its `npm run e2e` script and devDependencies must be on main first.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-dashboard-e2e-ci`: this change only, in the same proposal pull request as
  `add-dashboard-e2e-tests`. Verify: `openspec validate add-dashboard-e2e-ci --strict` passes and the dash grep prints nothing.

## 2. Tests first

- [ ] 2.1 `ci(e2e)`: write the failing workflow shape test under `.github/scripts/tests/`. It reads `.github/workflows/ci.yml` and
  asserts: a job with the id `dashboard-e2e` and the name `Dashboard end-to-end`; no `needs`; `timeout-minutes: 15`; every `uses:`
  line pinned to a 40-character SHA with a version comment; exactly one upload step, guarded by `if: failure()`; the job runs
  `npm run e2e` in `reposilite-frontend`. Verify: it fails with the message that the job is missing.

## 3. Job

- [ ] 3.1 `ci(e2e)`: add the job to `.github/workflows/ci.yml` directly after `frontend`: checkout, Java 17, Node 24 with the npm
  cache, `npm ci` in `reposilite-frontend`, `./gradlew :reposilite-backend:shadowJar`, a cached
  `npx playwright install --with-deps chromium`, `npm run e2e`, and the failure-only upload of the report directory. Verify: the test
  of 2.1 passes, and `python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests` stays green.
- [ ] 3.2 `ci(e2e)`: confirm on the code pull request that the job is green, that its test steps finish under five minutes, and that
  no artifact is uploaded on the green run. Verify: the three observations are written into the pull request description.

## 4. Required check

- [ ] 4.1 `ci(e2e)`: after the first green run of the job on main, the maintainer adds `Dashboard end-to-end` to the required checks
  of the main branch. This is a repository setting, not a file change. Verify: the name is listed in the branch protection rule, and
  the pull request records the date.

## 5. Open the pull request

- [ ] 5.1 Open the code pull request against `main` with the title `ci(e2e): run the dashboard end-to-end tests on pull requests`,
  after `add-dashboard-e2e-tests` is merged. Fill the sections of `.github/PULL_REQUEST_TEMPLATE.md`. Verify: the pull request exists
  under that title and its `Dashboard end-to-end` check is running.

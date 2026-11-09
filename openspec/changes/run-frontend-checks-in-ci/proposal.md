# Proposal

## Why

The dashboard has 248 unit test files under `reposilite-frontend` (`src/**/*.test.js` and `server/**/*.test.js`), and an
ESLint configuration with an `eslint` script. Neither runs on a pull request. A contributor who breaks a component test gets
a green check, and so does a change that leaves a Vue file with an unused variable. The tests exist, the script exists, and
the pipeline ignores both.

The gap matters beyond the frontend. The accessibility work that follows (WCAG 2.2 landmarks, skip links, reduced motion)
is only protected by assertions in those tests. A test that nothing runs protects nothing.

Today the frontend appears in CI only through the `Licenses` job, which runs `npm ci` and the production licence check
(`.github/workflows/ci.yml:77-90`). There is no frontend job to extend, so this change adds one.

## What Changes

- **Q1a Tests gate pull requests.** A new `Frontend` job in `.github/workflows/ci.yml` runs `npm ci` and `npm test`
  (`vitest run`) in `reposilite-frontend`. A failing test fails the check. The job runs on pull requests and on pushes to
  `main`, like the other jobs.
- **Q1b ESLint runs at warn level first.** The same job runs `npm run eslint`. The rules in `reposilite-frontend/eslint.config.js`
  are `error` today, and a flag cannot lower them, so the configuration is changed for this phase: every rule the file
  enables, including those from `js.configs.recommended` and the Vue preset, reports as `warn`. Findings are printed in the
  job log and do not fail the check. A parse failure or a broken configuration still fails it.
- **Q1c Baseline first.** Before the job is merged, the suite and the linter are run once on `origin/main`. A red test on
  `main` is fixed in its own change before this one merges; it is not skipped to make the gate pass.
- **Q1d Follow-up, not in this change.** A later change flips the ESLint rules back to `error` once the warning count is
  zero, and makes the job fail on warnings. That change is named in the roadmap as its own item and is not part of this
  proposal.

### Out of scope

- Flipping ESLint to `error`. That follows once the warning baseline is zero.
- New unit tests and the accessibility assertions of Q11. They depend on this change and come after it.
- Pinning the `actions/*` tags to commit SHAs (Q14). The new job uses the same tags the other jobs use, so Q14 changes all of them at once.
- Running the Playwright or browser suites, and any Gradle job.
- Changing the frontend's source, its dependencies, its build output or the Nitro server.
- The Docker images. The image workflow and `release-please.yml` are untouched.

## Capabilities

### New Capabilities

- `frontend-quality-gate`: which frontend checks run on a pull request, which of them fail the check, and the lint
  severity during the warn phase.

## Impact

- **Type and scope.** The change ships under `ci(frontend)`. Both commits (the ESLint severity and the workflow job) carry
  that type and scope, so the whole change is one type. The pull request title, squash-merged onto main, is:

  `ci(frontend): run vitest and eslint in the frontend pipeline`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, image or plugin
  changes, and a Reposilite installation pointed at Ingot behaves exactly as before. The title carries no `!` and no
  commit carries a `BREAKING CHANGE:` footer. No `docs/gitbook/` page documents a setting, variable or manifest that
  changes, so no docs page is updated.
- **Behaviour that changes by default.** Pull requests get one more check, `Frontend`. Its only way to fail is a failing
  unit test or a broken lint configuration. Lint findings never fail it in this phase.
- **Code.** `.github/workflows/ci.yml` and `reposilite-frontend/eslint.config.js`. Nothing under `src/` or `server/`.
- **Enables.** Q11 and the `test(frontend)` items, which need the tests to run in CI.
- **Correction to the roadmap.** The roadmap names an existing frontend job and says ESLint "starts at warn level". Neither
  is true on `origin/main`: the frontend steps sit in the `Licenses` job, the ESLint rules are `error`, and there are 248
  test files, not 241. This proposal adapts to that.

## Verification

- The baseline run on `origin/main` (tests and lint) is recorded in the pull request before it is opened.
- A throwaway draft pull request with one failing test turns the `Frontend` check red, then is closed without merging.
- The same throwaway branch with one deliberate lint violation keeps the check green and prints the warning.
- `openspec validate run-frontend-checks-in-ci --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/run-frontend-checks-in-ci` prints nothing.

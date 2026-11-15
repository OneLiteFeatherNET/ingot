# Proposal

## Why

Ingot's dashboard is tested only in isolation. The Vue component tests under `reposilite-frontend` mock the settings store and the
API client, and they pass while the real server and the real dashboard disagree. Several regressions of that kind reached staging
without anyone noticing: the Administration side navigation shows different entries on `/settings` and on `/access`, two entries are
highlighted at once on `/settings/audit`, and a visually hidden label leaks onto the screen. Other layout and status faults show the
same pattern: repository names broken mid-word in the Repositories table, the page heading hidden under the app bar, a build snippet
that scrolls sideways, and a read-only switch shown as a control where it is only a status. An operator sees each of these the moment
they open the page, and no check in the pipeline opens it.

A browser suite that starts the real server, signs in with a real token, and drives the dashboard the way an operator does closes
that gap. Its wiring into the pull request pipeline is a separate change, `add-dashboard-e2e-ci`.

## What Changes

- **Browser suite.** A Playwright suite in `reposilite-frontend/e2e/` with Chromium only. It starts the shaded server jar built in
  the same run, with a fresh temporary data directory, a free port and one temporary admin token passed with `--token`. It needs no
  external service, no database server and no network beyond the loopback interface.
- **Covered flows.** Sign in with a token and sign out. Browse a repository and download a file. Upload a file through the API and
  see it in the browser. The Administration navigation is identical on every Administration page and exactly one entry is current.
  Create, edit and delete a repository on the Repositories page. A visitor without admin rights sees no Administration entry. The
  theme toggle persists. At 320 px the navigation is reachable.
- **Covered layout and status.** At 1280 px and 1920 px the Repositories table breaks no name or address mid-word and keeps each row's
  actions on one line. The page heading and page actions stay visible while the page scrolls. The Browse overview build snippet shows
  the full repository URL without horizontal scrolling, and its filter field is labelled as a filter of the list. The Dashboard shows
  read-only as a status only, and no card shows a used value above its limit.
- **Accessibility.** `@axe-core/playwright` runs on each covered page and fails on serious and critical findings.
- **Known regressions.** Each test for a fault that is not yet fixed is marked as an expected failure with the name of the change that
  fixes it. It runs and reports today, and it fails as an unexpected pass once that change lands, unless its marker is removed in the
  same pull request.
- **Documentation.** A section in `.github/CONTRIBUTING.md`, under "Running", on how to run the suite locally, including
  `heavy npx playwright test --workers=2`.

### Out of scope

- Visual regression screenshots and pixel comparisons.
- Load tests and performance budgets.
- A cross-browser matrix. Firefox and WebKit can be added later as Playwright projects without changing the suite.
- The Nexus import wizard against a real Nexus.
- The container image as test target. The suite runs the jar; the container tests stay with `:reposilite-backend:containerTest`.
- The pull request wiring of the suite, the job and the required check. That is `add-dashboard-e2e-ci`.
- Any product change. The dashboard, the server, the API and the image are not modified.

## Capabilities

### New Capabilities

- `dashboard-e2e-tests`: the browser suite, its isolation and budget rules, the covered flows and layout checks, the expected-failure
  markers, and the accessibility gate.

## Impact

- **Type and scope.** The change ships under `test(e2e)`, and every commit of it uses `test(e2e)` or `docs(e2e)`. The pull request
  title, squash-merged onto main, is:

  `test(e2e): add browser end-to-end tests for the dashboard`

- **Breaking marker.** No breaking change. Nothing ships in the server image, the jar or the Helm chart. The suite adds development
  dependencies to `reposilite-frontend` (`@playwright/test`, `@axe-core/playwright`). The production dependency set and the license
  gate `npm run license-check` do not change. No configuration key, environment variable, volume, manifest or workflow changes.
- **Behaviour that changes by default.** None. No runtime path is touched.
- **Dependencies on other changes.**
  - `add-dashboard-e2e-ci`: runs this suite on pull requests. It follows this change and is opened after this one is merged.
  - `fix-administration-navigation`: not on main, and no openspec change of that name exists yet. Its expected-failure markers are
    removed in that change's pull request. It also fixes the Repositories table layout.
  - `fix-browse-overview`: removes the marker of the Browse snippet and filter test.
  - `fix-dashboard-status`: removes the marker of the Dashboard status test.
  - Heading under the app bar: no change name is assigned yet, so its test carries a marker that names the open question, see the
    design.
  - `restructure-navigation`: the Administration entries and reserved routes the suite asserts come from it. The suite follows the
    routes as they are on main in `reposilite-frontend/src/router/routes.js`.
- **Code.** `reposilite-frontend/e2e/`, `reposilite-frontend/playwright.config.js`, `reposilite-frontend/package.json` and
  `package-lock.json`, `.github/CONTRIBUTING.md`.

## Verification

- `openspec validate add-dashboard-e2e-tests --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-dashboard-e2e-tests` prints nothing.
- The suite runs green locally with `heavy npx playwright test --workers=2`, with every expected-failure test reporting as expected.

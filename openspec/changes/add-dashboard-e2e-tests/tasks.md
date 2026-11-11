# Tasks

Every commit of this change is typed `test(e2e)`, or `docs(e2e)` for the CONTRIBUTING section. Each test follows F.I.R.S.T.: no sleeps
or waits by time, no system clock, no shared mutable state between tests, names derived from the test, assertions with messages, and
no retries. The specs are written and seen to fail before the harness exists. Local runs go through `heavy`, with `--workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-dashboard-e2e-tests`: this change and nothing else. Verify:
  `openspec validate add-dashboard-e2e-tests --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tests first

- [ ] 2.1 `test(e2e)`: write the flow specs under `reposilite-frontend/e2e/`: sign in with a token and sign out; browse a
  repository and download a file; upload through the API and see the file in browse; create, edit and delete a repository on the
  Repositories page; a visitor without admin rights sees no Administration entry; the theme toggle persists across a reload; at
  320 px width the side navigation is reachable. Read the seeding endpoints from `/openapi` of a running jar, not from memory.
  Verify: the specs fail because the harness and its fixtures do not exist yet.
- [ ] 2.2 `test(e2e)`: write the navigation spec: on every Administration page for an instance admin, the side navigation shows the
  same entries in the same order, and exactly one link carries `aria-current="page"`. Wrap it with
  `test.fail(true, 'fix-administration-navigation')`. Verify: it fails for the same harness reason as 2.1 and carries the marker.
- [ ] 2.3 `test(e2e)`: write the accessibility spec for the covered pages (sign-in dialog, browse, search, dashboard, access,
  repositories, audit) with `@axe-core/playwright`, failing on `serious` and `critical` violations only. Verify: it fails before
  the harness exists.
- [ ] 2.4 `test(e2e)`: write the Repositories table spec at 1280 px and 1920 px: no repository name or address breaks mid-word, and
  each row's actions stay on one line. Wrap it with `test.fail(true, 'fix-administration-navigation')`. Verify: it fails for the
  harness reason and carries the marker.
- [ ] 2.5 `test(e2e)`: write the sticky heading spec: while the page scrolls, the page heading and the page actions are visible and
  not covered by the app bar. Wrap it with `test.fail(true, 'rework-administration-layout')`. Verify: it fails for the
  harness reason and carries the marker.
- [ ] 2.6 `test(e2e)`: write the Browse overview spec: the build snippet shows the full repository URL without horizontal scrolling
  of the snippet's container, and the filter field has an accessible name that says it filters the list. Wrap it with
  `test.fail(true, 'fix-browse-overview')`. Verify: it fails for the harness reason and carries the marker.
- [ ] 2.7 `test(e2e)`: write the Dashboard status spec: read-only is shown as a status with no switch control, and no card shows a used
  value above its limit. Wrap it with `test.fail(true, 'fix-dashboard-status')`. Verify: it fails for the harness reason and carries
  the marker.

## 3. Harness

- [ ] 3.1 `test(e2e)`: add `@playwright/test` and `@axe-core/playwright` as development dependencies of `reposilite-frontend`, with
  `reposilite-frontend/playwright.config.js` (Chromium only, `retries: 0`, `trace: 'retain-on-failure'`,
  `screenshot: 'only-on-failure'`, `video: 'off'`, two workers locally) and the script `e2e`. Verify: `npm ci` and `npm run e2e`
  run, and Vitest still reports only its own specs.
- [ ] 3.2 `test(e2e)`: add the global setup and teardown: build directory with `mkdtemp`, free loopback port, the shaded jar started with
  `--working-directory`, `--port`, `--hostname 127.0.0.1` and `--token`, a readiness check on the status endpoint with a bounded
  number of attempts, and removal of the directory at the end. Add the name helper that derives names from the test title and worker
  index, and the seeding helper that uses only the REST API. Verify: the specs of groups 2.1 to 2.3 pass, and the specs of groups 2.4
  to 2.7 pass as expected failures.
- [ ] 3.3 `test(e2e)`: confirm the isolation rules. Run the suite with `--fully-parallel --repeat-each=2` and the same result must come
  out. Check that no spec uses `waitForTimeout`, a fixed sleep or a wall-clock read. Verify: both checks pass, and the findings are
  recorded in the pull request description.

## 4. Documentation

- [ ] 4.1 `docs(e2e)`: add a section "Dashboard end-to-end tests" under "Running" in `.github/CONTRIBUTING.md`. It covers the jar build
  the suite needs, the local command `heavy npx playwright test --workers=2`, the expected-failure markers and the change that removes
  each one, and how a new flow is added with the seeding helper. Verify: every command in the section runs as written.

## 5. Verify and open the pull request

- [ ] 5.1 Verify the whole change: run the suite locally through `heavy` with `--workers=2`, check that the run stays under five
  minutes, run `openspec validate add-dashboard-e2e-tests --strict`, and check the dash grep over
  `openspec/changes/add-dashboard-e2e-tests` and over the changed files. Verify: all four checks pass.
- [ ] 5.2 Open the code pull request against `main` with the title `test(e2e): add browser end-to-end tests for the dashboard`. Fill
  the sections of `.github/PULL_REQUEST_TEMPLATE.md` and describe the change from the reviewer's side. Verify: the pull request exists
  under that title and its checks are running.

# Tasks

Each group names its Conventional Commits type and scope. A group is one pull request, and its last task opens that pull request
under the title given. Tests follow F.I.R.S.T.: no sleeps, no system clock, no shared mutable state, a fresh fixture per test, and the
failing test is written and seen to fail before the production change. No group changes a configuration key, environment variable
or deployment manifest. Frontend jobs run through `heavy`, vitest with `--maxWorkers=2`, Playwright with `--workers=2`.

Before a group starts, run `git diff --name-only origin/main...<pr>` for each pull request named in `design.md` D8, and do not start
a group whose files an open pull request still changes unless that pull request is merged. Each code pull request is branched from
the remote main branch and contains no file under `openspec/`. Task checkboxes are ticked in a follow-up pull request, never in a
code pull request. Groups 1 and 2 do not depend on this proposal; groups 3 to 5 start after the proposal pull request is merged.

## 1. Label of the Factory reset button (`fix(ui)`)

Pull request: `fix(ui): keep the factory reset label on one line`. Independent of the other groups.

- [ ] 1.1 Reproduce first: a browser test that opens `/settings` as an administrator at 320, 600 and 1440 pixels and asserts the
  "Factory reset" label box is one line high (its height equals the height of one line of the label). Run it and record the width
  where it fails today.
- [ ] 1.2 Find which element lets the button shrink or the label wrap (the modal's wrapper around the `button` slot, or the missing
  `whitespace-nowrap`), and apply the default of design question 6 to that element. Keep the text, colour and position.
- [ ] 1.3 Run the new test, the `FactoryResetModal` and `SettingsView` unit tests, and `npm run eslint`. Attach screenshots at 320, 600
  and 1440 pixels, before and after, from the `pr-screenshots` branch.
- [ ] 1.4 Open the pull request `fix(ui): keep the factory reset label on one line`.

## 2. The page title and the top bar (`fix(ui)`)

Pull request: `fix(ui): keep the page title clear of the top bar`. Independent of the other groups.

- [ ] 2.1 Reproduce first at 1440 pixels: scroll Overview, navigate to another Administration page, and take a screenshot of the
  top of the page. Record in the pull request which of the three causes of design D9 it is.
- [ ] 2.2 Write the failing browser test: after a navigation between two Administration pages, after a focus move to `#main`, and at
  scroll position 0, the top edge of the `h1` is at or below the bottom edge of the top bar, at 320, 600 and 1440 pixels.
- [ ] 2.3 Fix it with the smallest rule that makes the test pass (`scroll-padding-top` from the top bar token on the scrolling root,
  and `scroll-margin-top` on `#main` when the focus move is the cause). Do not make the header sticky.
- [ ] 2.4 Run the new test, the `layout` and `navigation-chrome` specs, `IndexPage` tests and `npm run eslint`. Attach the screenshots.
- [ ] 2.5 Open the pull request `fix(ui): keep the page title clear of the top bar`.

## 3. The status on Overview (`feat(ui)`)

Pull request: `feat(ui): show the instance status on the administration overview`. Starts after #250 group 1 is merged. The
Dashboard page and its rail entry still exist after this group, so it can be reverted alone.

- [ ] 3.1 Write the failing unit tests first. `registry.test.js`: the `overview` tree entry opens for the settings node, for the
  dashboard node and for neither (hidden). `create.test.js` and `guard.test.js`: `/settings` without a domain opens for either
  node and is the no access or sign in state for neither; a dashboard-only visitor at `/settings/<domain>` gets the no access state.
  `SettingsView` test: a settings-only visitor mounts no status block and makes no status request; a dashboard-only visitor mounts
  only the status block, with no actions, no Needs attention, no At a glance and no Danger zone; an administrator mounts all of it.
  `DashboardView` test: no `h1`, two `h2` sections with their `aria-labelledby`, the getting started card first.
- [ ] 3.2 Change the `overview` entry and the `settings` route meta to `anyNode: [settings, dashboard]`, and keep the settings node
  check in the view for every page that is not Overview (design D2). Update the forward rule in `create.js` so it applies to a
  visitor with neither node.
- [ ] 3.3 Turn `DashboardView.vue` into the status block (design D1): keep the file, the name and `data-ingot-component="dashboard-view"`,
  drop the `h1` and the page container, and keep the sections, the stores and the polling as they are.
- [ ] 3.4 Mount the block in `SettingsView.vue` for a visitor with the dashboard node, in the order of design D4 and question 2, set
  `:reading="false"` for Overview, and give the block and the counts the grids of D4. Update the description of Overview (question
  3). Keep the Import and Download actions and the unsaved changes bar as they are.
- [ ] 3.5 Remove the link line and the `dashboard` prop from `SettingsHub.vue`, with its `data-test` and its test, and `canDashboard`
  as a prop source (it stays as the gate of the block).
- [ ] 3.6 Move the wide-screen e2e tests of `layout.spec.js` (the two chart columns and the checklist width) and `dashboard.spec.js`
  to `/settings`, with the same assertions, and add a test that an administrator sees the status above the Danger zone at 1440 pixels.
  Add an e2e test that a visitor with only the settings node sees no status block and no failed request.
- [ ] 3.7 Check `src/anchors` (anchor and registry tests) and the style gate. Expect no change to `src/anchors/registry.json`; when
  the test demands one, edit it and the matching table in `docs/gitbook/reference/dashboard-anchors.md` in this group.
- [ ] 3.8 Run the unit suite for `navigation`, `router`, `pages`, `components/settings` and `components/dashboard`, `npm run eslint`,
  and the e2e specs `dashboard`, `layout`, `navigation-chrome`, `accessibility`, `sign-in`. Attach the screenshots of Overview at 320,
  600 and 1440 pixels, before and after, and of a dashboard-only visitor at 1440 pixels.
- [ ] 3.9 Open the pull request `feat(ui): show the instance status on the administration overview`.

## 4. Retire the dashboard page (`feat(ui)`)

Pull request: `feat(ui): retire the dashboard page and redirect it to the overview`. Starts after group 3, #219 and #214 are merged.

- [ ] 4.1 Write the failing tests first. `routes` and `create` tests: `/dashboard` redirects to `settings` with `replace`, keeping
  the query and the hash. `legacy.test.js`: `#/?tab=dashboard` still resolves through the `dashboard` name and ends on Overview.
  `registry.test.js` and the bottom bar test: no `dashboard` rail page, the rail and the bar hold Browse, Search and Administration,
  and the bar has no More item. `routeTree.test.js`: `dashboard` is an exempt route. `IndexPage.test.js`: no `DashboardView` section.
- [ ] 4.2 Remove the `dashboard` page from `PAGES`, add `dashboard` to `EXEMPT_ROUTES`, turn the route into the redirect of design D3,
  and remove the `dashboard` section branch and the `DashboardIcon` import from `IndexPage.vue`. Delete `DashboardIcon.vue` when no
  file imports it. Update the comments in `registry.js` and `routes.js` that name four rail entries or the Dashboard.
- [ ] 4.3 Keep `dashboard` in `dashboard-segments.json`, `DashboardPaths.kt` and the chart file. Confirm with `cmp` that
  `src/router/dashboard-segments.json` still equals `charts/ingot/files/dashboard-segments.json`.
- [ ] 4.4 e2e: `navigation-chrome.spec.js` loops over `/`, `/search`, `/settings`, `/account` (the `/dashboard` entry becomes a
  redirect test), replace the Dashboard heading test with one that opens `/dashboard` and expects the heading "Overview" at
  `/settings`; `accessibility.spec.js` lists `/settings` once; add `dashboard-redirect.spec.js` (or a case in `dashboard.spec.js`)
  that an administrator, an anonymous visitor and a signed in user without the node each land on `/settings` and see the status, the
  sign in prompt and the no access state.
- [ ] 4.5 Run the unit suites named in 3.8, `npm run eslint`, `npm run build`, and the e2e specs `dashboard`, `layout`,
  `navigation-chrome`, `accessibility`, `sign-in`, `repositories`. Attach the screenshots of the rail at 320, 600 and 1440 pixels,
  before and after.
- [ ] 4.6 Open the pull request `feat(ui): retire the dashboard page and redirect it to the overview`.

## 5. Documentation (`docs(gitbook)`)

Pull request: `docs(gitbook): describe the status on the administration overview`. Starts after group 4 and #262 are merged.

- [ ] 5.1 `docs/gitbook/reference/dashboard-pages.md`: the `/dashboard` row becomes the redirect, the Overview row names the status
  and who sees which section, the rail and bottom bar text lists three entries, the layout paragraph describes the wide Overview, and
  the hub paragraph loses the Dashboard link.
- [ ] 5.2 `docs/gitbook/reference/dashboard-anchors.md`: the description of `dashboard-view` and `settings-hub` (design D5).
- [ ] 5.3 Search `docs/gitbook` for "Dashboard" as a page name and fix `how-to/monitor-status-and-failures.md`, `reference/web-routes.md`,
  `README.md` and `SUMMARY.md` where they send the reader to the Dashboard page. The split container and Helm pages only talk
  about the dashboard container and stay as they are.
- [ ] 5.4 Open the pull request `docs(gitbook): describe the status on the administration overview`.

# Tasks

One change, one code pull request, under the type and scope `feat(ui)`. The code pull request is opened by the last task.

Every test is written first and seen to fail on the current build before the production code exists. Every test follows F.I.R.S.T.:
no sleeps or waits by time, no system clock, no network, a fresh component, router and store per test, assertions with messages.
Unit tests run with `npm test` in `reposilite-frontend`, targeted with a file pattern while working. Playwright runs with `--workers=2`.
Heavy jobs run through `heavy`. No file under `reposilite-backend/` changes, and no file under `openspec/` is part of the code pull request.

Screenshots: before and after, of the Overview, each tab and the redirected address, at 320, 600 and 1440 pixels, in light and dark. They are
linked from the pull request through the `pr-screenshots` branch.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose structure-system-and-support-page`: this change and nothing else, tasks unchecked, branched from the remote
  main branch. Verify: `npx -y @fission-ai/openspec validate structure-system-and-support-page --strict` passes and the dash grep prints nothing.

## 2. Tests first (red)

Every task in this group is a test. Each one is run on the current build and seen to fail, and the failure is noted in the pull request.

- [ ] 2.1 `test(ui)`: in `src/anchors/anchors.test.ts`, assert that the registry declares the alias `support-view` with `replacedBy`
  `system-info-view`, and that the component `support-view` is no longer a registered component. Verify: it fails on `main`.
- [ ] 2.2 `test(ui)`: in `src/router/routes.test.ts` (or the nearest routes test), assert that `/settings/support` has a redirect to the route
  `system-info`, that the name `support` resolves, and that the redirect keeps the query and the fragment. Verify: it fails, because
  `support` renders its own page.
- [ ] 2.3 `test(ui)`: in `src/navigation/registry.test.ts`, assert that the Monitoring and support group lists one entry `system-info` labelled
  "System and support", and no entry `page:support`. Update the two expectations that list `page:support` (lines 34, 40, 49 and 313 at the
  time of writing) in the same commit. Verify: it fails on `main`.
- [ ] 2.4 `test(ui)`: a pure unit test for the fragment-to-tab table of design D3 and the query-to-tab function, with the cases `support-ingot`,
  `support-properties`, `support-configuration`, an unknown fragment, a missing query and an unknown query. Verify: it fails, because the
  functions do not exist yet.
- [ ] 2.5 `test(ui)`: a pure unit test for the filter of design D6: a case-insensitive match on key and on value, no match, an empty filter that
  matches everything, and a masked value that matches only its masked text. Verify: it fails, because the function does not exist yet.
- [ ] 2.6 `test(ui)`: in `src/components/support/SystemInfoView.test.ts`, assert that the page has four tabs in the order Overview, Plugins,
  Environment, Configuration, that Overview is selected, that the six cards are labelled by their headings with the ids `support-ingot` ...
  `support-cluster`, and that the Java input arguments are behind a disclosure. Verify: it fails on `main` (no tabs).
- [ ] 2.7 `test(ui)`: in `src/components/support/SystemInfoView.test.ts`, assert that the header action "Download support bundle" is drawn on every
  tab when the availability answers on, is absent when it answers off with the text "The support pages are turned off on this instance.", and
  that "The support bundle cannot be checked right now." replaces it when the check fails. Verify: it fails on `main`.
- [ ] 2.8 `test(ui)`: in `src/components/support/SystemInfoView.test.ts`, assert that the root carries `data-ingot-component="system-info-view"` and
  `data-ingot-component-alias="support-view"`, and that the parts `support-action`, `support-note` and `filter` are present where the design puts
  them. Verify: it fails on `main`.
- [ ] 2.9 `test(ui)`: in `e2e/system-info.spec.js` (Playwright, role selectors, no fixed timeouts), assert the redirect from `/settings/support`, the
  deep link `?tab=environment`, the anchor `#support-properties` opening Environment, the arrow keys and Home and End on the tab list, and axe on each
  tab at 320, 600 and 1440 pixels. Verify: it fails on `main`.
- [ ] 2.10 `test(ui)`: in `e2e/system-info.spec.js`, assert the layout: the Overview has three columns at 1440 pixels and one column at 320 pixels, and no
  page scrolls sideways at 320 pixels. Verify: it fails on `main`.

## 3. Production code (green)

- [ ] 3.1 `feat(ui)`: add the pure functions of design D3 and D6 in a module beside `SystemInfoView.vue`, with the tests of 2.4 and 2.5 passing.
- [ ] 3.2 `feat(ui)`: rebuild `SystemInfoView.vue` with the tab list of `rework-admin-header-and-repository-tabs`: the four tabs, the Overview cards,
  the Java disclosure, the Plugins list with `plugin-tier`, the Environment and Configuration panels and the filter field. Keep the `h2` ids
  `support-*` and the state `loading`. Tests 2.6 and 2.8 pass.
- [ ] 3.3 `feat(ui)`: move the content of `SupportView.vue` into the header of the page: the download button, the disclosure of the note, the
  "turned off" and "cannot be checked" states. Tests 2.7 and 2.8 pass. Then delete `SupportView.vue` and its test file, and move the cases of
  `src/components/support/support.test.ts` that still apply into `SystemInfoView.test.ts`.
- [ ] 3.4 `feat(ui)`: in `src/router/routes.ts`, turn `support` into `{ name: 'support', path: '/settings/support', redirect: { name: 'system-info' } }`
  at the same place in the list, and keep the order before the generic settings route. Test 2.2 passes.
- [ ] 3.5 `feat(ui)`: in `src/navigation/registry.ts`, replace the entries `system-info` and `support` by the one entry of design D2. Test 2.3 passes.
- [ ] 3.6 `feat(ui)`: in `src/anchors/registry.json`, remove the component `support-view`, add the parts of design D9 to `system-info-view`, add the
  alias `support-view` with `replacedBy` `system-info-view` and a concrete `removeIn` (open question 6). Test 2.1 passes. Run
  `npm run anchors:doc` and commit the regenerated `docs/gitbook/references/plugins/dashboard-anchors.md`.
- [ ] 3.7 `docs(ui)`: update every page under `docs/gitbook` that describes `/settings/support` as a page of its own, so that it describes the one page
  and its tabs. Verify: `grep -rn "settings/support" docs/gitbook` lists only the redirect note.

## 4. Verification

- [ ] 4.1 Run `npm test` in `reposilite-frontend`, first targeted by file, then the whole unit suite once, through `heavy`. All tests pass, and the
  tests of group 2 that were seen red are green.
- [ ] 4.2 Run the Playwright tests of `e2e/system-info.spec.js` and of `e2e/accessibility.spec.js` and `e2e/navigation.spec.js` (the nav entry changed),
  with `--workers=2`, through `heavy`. All pass.
- [ ] 4.3 Attach the before and after screenshots of design D10 to the pull request, pushed to the `pr-screenshots` branch.
- [ ] 4.4 Run `npx -y @fission-ai/openspec validate structure-system-and-support-page --strict` again on `main` after the proposal merged, and the
  dash grep prints nothing in the changed files.

## 5. Code pull request

- [ ] 5.1 Open the code pull request from a branch of `main` that already contains the merged proposal, with the title
  `feat(ui): merge system information and support into one tabbed page`. Its body fills `.github/PULL_REQUEST_TEMPLATE.md`, and it links the
  screenshots of 4.3. It contains no file under `openspec/`.

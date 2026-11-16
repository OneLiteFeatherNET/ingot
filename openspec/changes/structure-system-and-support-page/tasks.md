# Tasks

One change, one code pull request, under the type and scope `feat(ui)`. The code pull request is opened by the last task. Its groups run in
order: the tabs component (group 3) comes before the page (group 4).

Every test is written first and seen to fail on the current build before the production code exists. Every test follows F.I.R.S.T.:
no sleeps or waits by time, no system clock, no network, a fresh component, router and store per test, assertions with messages.
Unit tests run with `npm test` in `reposilite-frontend`, targeted with a file pattern while working. Playwright runs with `--workers=2`.
Heavy jobs run through `heavy`. No file under `reposilite-backend/` changes, and no file under `openspec/` is part of the code pull request.

Screenshots: before and after, of the Overview, each tab and the redirected address, at 320, 600 and 1440 pixels, in light and dark. They are
linked from the pull request through the `pr-screenshots` branch.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose structure-system-and-support-page`: this change and nothing else, tasks unchecked, branched from the remote
  main branch. Verify: `npx -y @fission-ai/openspec validate structure-system-and-support-page --strict` passes and the dash grep prints nothing.

## 2. Tests first for the tabs component (red)

- [ ] 2.1 `test(ui)`: in `src/components/tabs/AppTabs.test.ts`, assert the roles: the list is a `tablist` with the given `aria-label`, each tab is a
  `tab` with `aria-selected` and `aria-controls`, and each panel is a `tabpanel` labelled by its tab. Verify: it fails, because the component does
  not exist.
- [ ] 2.2 `test(ui)`: in the same file, assert the roving `tabindex`: 0 on the selected tab and -1 on the others, and that only the selected panel is
  not hidden. Verify: it fails.
- [ ] 2.3 `test(ui)`: assert that Right and Left move the focus and select the next and the previous tab, that they wrap at both ends, and that Home
  and End move to the first and the last. Verify: it fails.
- [ ] 2.4 `test(ui)`: assert that a missing or unknown `tab` query selects the first tab, that a known value selects its tab, and that selecting a tab
  replaces the history entry. Use a memory router, one per test. Verify: it fails.
- [ ] 2.5 `test(ui)`: assert that a change of another query parameter does not change the selected tab, and that two instances on one page have
  different ids. Verify: it fails.

## 3. The tabs component (green)

- [ ] 3.1 `feat(ui)`: add `src/components/tabs/AppTabs.vue` with the API of design D3 (`tabs`, `modelValue`, `queryParam`, `label`), the roles, the
  ids from `useId`, the roving `tabindex`, the keyboard model and the query. Tests 2.1 to 2.5 pass.
- [ ] 3.2 `docs(anchors)`: register `app-tabs` in `src/anchors/registry.json` (the component, with its parts only if a test selects them) and run
  `npm run anchors:doc`, so the generated reference lists it. Verify: `anchors.test.ts` and `source-tree.test.ts` pass.

## 4. Tests first for the page (red)

Every task in this group is a test. Each one is run on the current build and seen to fail, and the failure is noted in the pull request.

- [ ] 4.1 `test(ui)`: in `src/anchors/anchors.test.ts`, assert that the registry declares the alias `support-view` with `replacedBy`
  `system-info-view`, and that the component `support-view` is no longer a registered component. Verify: it fails on `main`.
- [ ] 4.2 `test(ui)`: in the routes test of the router, assert that `/settings/support` has a redirect to the route `system-info`, that the name
  `support` resolves, and that the redirect keeps the query and the fragment. Verify: it fails, because `support` renders its own page.
- [ ] 4.3 `test(ui)`: in `src/navigation/registry.test.ts`, assert that the Monitoring and support group lists one entry `system-info` labelled
  "System and support", and no entry `page:support`. Update the expectations that list `page:support` in the same commit. Verify: it fails on `main`.
- [ ] 4.4 `test(ui)`: a pure unit test for the fragment-to-tab table of design D4, with `support-ingot`, `support-properties`,
  `support-configuration`, an unknown fragment and no fragment. Verify: it fails, because the function does not exist yet.
- [ ] 4.5 `test(ui)`: a pure unit test for the filter of design D7: a case-insensitive match on key and on value, no match, an empty filter that
  matches everything, and a masked value that matches only its masked text. Verify: it fails.
- [ ] 4.6 `test(ui)`: in `src/components/support/SystemInfoView.test.ts`, assert that the page has four tabs in the order Overview, Plugins,
  Environment, Configuration (the `AppTabs` of group 3), that Overview is selected, that the six cards are labelled by their headings with the ids
  `support-ingot` ... `support-cluster`, and that the Java input arguments are behind a disclosure. Verify: it fails on `main`.
- [ ] 4.7 `test(ui)`: in the same file, assert that the header action "Download support bundle" is drawn on every tab when the support pages are on,
  is absent with the text "The support pages are turned off on this instance." when they are off, and that "The support bundle cannot be checked
  right now." replaces it when the check fails. Verify: it fails on `main`.
- [ ] 4.8 `test(ui)`: in the same file, assert that the root carries `data-ingot-component="system-info-view"` and
  `data-ingot-component-alias="support-view"`, and that the parts `support-action`, `support-note` and `filter` are present where the design puts
  them. Verify: it fails on `main`.
- [ ] 4.9 `test(ui)`: in `e2e/system-info.spec.js` (Playwright, role selectors, no fixed timeouts), assert the redirect from `/settings/support`, the
  deep link `?tab=environment`, the anchor `#support-properties` opening Environment, the arrow keys and Home and End on the tab list, and axe on each
  tab at 320, 600 and 1440 pixels. Verify: it fails on `main`.
- [ ] 4.10 `test(ui)`: in `e2e/system-info.spec.js`, assert the layout: the Overview has three columns at 1440 pixels and one column at 320 pixels, and no
  page scrolls sideways at 320 pixels. Verify: it fails on `main`.

## 5. The page (green)

- [ ] 5.1 `feat(ui)`: add the pure functions of design D4 and D7 in a module beside `SystemInfoView.vue`, with the tests of 4.4 and 4.5 passing.
- [ ] 5.2 `feat(ui)`: rebuild `SystemInfoView.vue` with `AppTabs`: the four tabs, the Overview cards, the Java disclosure, the Plugins list with
  `plugin-tier`, the Environment and Configuration panels and the filter field. Keep the `h2` ids `support-*` and the state `loading`. Tests 4.6 and 4.8
  pass.
- [ ] 5.3 `feat(ui)`: move the content of `SupportView.vue` into the header of the page: the download button, the disclosure of the note, the "turned off"
  and "cannot be checked" states. Tests 4.7 and 4.8 pass. Then delete `SupportView.vue` and its test file, and move the cases of
  `src/components/support/support.test.ts` that still apply into `SystemInfoView.test.ts`.
- [ ] 5.4 `feat(ui)`: in `src/router/routes.ts`, turn `support` into `{ name: 'support', path: '/settings/support', redirect: { name: 'system-info' } }`
  at the same place in the list, and keep the order before the generic settings route. Test 4.2 passes.
- [ ] 5.5 `feat(ui)`: in `src/navigation/registry.ts`, replace the entries `system-info` and `support` by the one entry of design D2. Test 4.3 passes.
- [ ] 5.6 `feat(ui)`: in `src/anchors/registry.json`, remove the component `support-view`, add the parts of design D10 to `system-info-view`, and add the
  alias `support-view` with `replacedBy` `system-info-view` and a concrete `removeIn` (design D11, decision 6). Test 4.1 passes. Run `npm run anchors:doc`
  and commit the regenerated `docs/gitbook/references/plugins/dashboard-anchors.md`.
- [ ] 5.7 `docs(ui)`: update every page under `docs/gitbook` that describes `/settings/support` as a page of its own, so that it describes the one page and
  its tabs. Verify: `grep -rn "settings/support" docs/gitbook` lists only the redirect note.

## 6. Verification

- [ ] 6.1 Run `npm test` in `reposilite-frontend`, first targeted by file, then the whole unit suite once, through `heavy`. All tests pass, and the tests of
  groups 2 and 4 that were seen red are green.
- [ ] 6.2 Run the Playwright tests of `e2e/system-info.spec.js`, `e2e/accessibility.spec.js` and `e2e/navigation.spec.js` (the nav entry changed), with
  `--workers=2`, through `heavy`. All pass.
- [ ] 6.3 Attach the before and after screenshots of design D12 to the pull request, pushed to the `pr-screenshots` branch.
- [ ] 6.4 Run `npx -y @fission-ai/openspec validate structure-system-and-support-page --strict` again on `main` after the proposal merged, and the dash
  grep prints nothing in the changed files.

## 7. Code pull request

- [ ] 7.1 Open the code pull request from a branch of `main` that already contains the merged proposal, with the title
  `feat(ui): merge system information and support into one tabbed page`. Its body fills `.github/PULL_REQUEST_TEMPLATE.md`, and it links the
  screenshots of 6.3. It contains no file under `openspec/`.

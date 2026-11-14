# Tasks

Each group is one Conventional Commits type with the scope `ui`, and each group ends with the task that opens its code pull
request under the title named in the group. The groups are merged in order: group 2 starts from `main` after the code PR of
group 1 is merged, and group 3 after the code PR of group 2.

Every test is written first and seen to fail on the current build before the production code exists. Every test follows
F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, a fresh component, router and store per test, assertions
with messages. Layout is measured in Playwright with `getBoundingClientRect`, with role selectors and no fixed timeouts.
`npm test` runs in `reposilite-frontend`, targeted with a file pattern while working. Playwright runs with `--workers=2`. Heavy
jobs run through `heavy`. No file under `reposilite-backend/` changes, and no file under `openspec/` is in a code pull request.

Screenshots: every UI group attaches before and after screenshots at 320, 600 and 1440 pixels, in light and dark. They are
pushed to the `pr-screenshots` branch and linked from the pull request.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose rework-admin-header-and-repository-tabs`: this change and nothing else, tasks unchecked,
  branched from the remote main branch. Verify: `openspec validate rework-admin-header-and-repository-tabs --strict` passes and
  the dash grep prints nothing.

## 2. Group 1: the layout stops jumping (`fix(ui)`)

Pull request title: `fix(ui): keep the administration layout still between pages`

- [ ] 2.1 `test(ui)`: in `src/style.test.js`, assert that the root element of the document has `scrollbar-gutter: stable`.
  Verify: it fails, because no rule in `src/style.css` sets it.
- [ ] 2.2 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js`, assert on the Overview, a group page, a list page and a
  detail page that the first row of the title block is drawn (with the back arrow below 1240 pixels), that the heading has
  one-line truncation with its full text as `title`, and that the description element is drawn with the one-line class even
  when the page has no description. Verify: it fails on `AdministrationFrame.vue` (the row is drawn only when `showsBack || crumbs`,
  and the description only when `lineDescription`).
- [ ] 2.3 `test(ui)`: in `src/components/settings/SettingsView.test.js`, assert that the loading paragraph of the settings page
  reserves the height of the first row of the page (a class with the same minimum height). Verify: it fails on `SettingsView.vue`
  (the paragraph has `mt-6` and no reserved height).
- [ ] 2.4 `test(ui)`: in `e2e/layout.spec.js`, add cases: at 1440 and 600 pixels the header height is the same on the Overview, a
  group page, a list page and a detail page, within 0.5 pixel; the side navigation's top edge equals the content's top edge; and the
  left edge of the frame is the same on a page with a scrollbar and on a page without one. Verify: they fail against the current
  build, on the header of the Overview and of the entry pages.
- [ ] 2.5 `fix(ui)`: in `src/style.css`, set `scrollbar-gutter: stable` on the root element next to the `html, body` rule.
  Verify: 2.1 passes.
- [ ] 2.6 `fix(ui)`: in `AdministrationFrame.vue`, always draw the first row of the title block (minimum height 48 pixels), truncate
  the heading to one line with its full text as `title`, and draw the description element with the one-line class. In
  `SettingsView.vue`, reserve the height of the first row on the loading paragraph. Verify: 2.2 and 2.3 pass, and 2.4 passes.
- [ ] 2.7 `test(ui)`: run `e2e/accessibility.spec.js` and `e2e/layout.spec.js` through `heavy`, with `--workers=2`. Verify: they pass,
  and the axe result of each page is clean.
- [ ] 2.8 Take the before and after screenshots at 320, 600 and 1440 pixels, of the Overview, a group page and a detail page, in
  light and dark. Verify: the header of a detail page and of an index entry no longer moves the side navigation between the two
  screenshots of a page change. Attach them to the code pull request.
- [ ] 2.9 Open the code pull request `fix(ui): keep the administration layout still between pages`, with the body from the
  template and the screenshots. Verify: `gh pr view` shows the exact title, and the body has no em or en dash.

## 3. Group 2: the header sits beside the side navigation (`feat(ui)`)

Pull request title: `feat(ui): place the administration header beside the side navigation`

Starts after the code PR of group 1 is merged and `fix-surface-hierarchy-and-page-header` is on `main`.

- [ ] 3.1 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js` and `SideNavLayout.test.js`, assert that the frame
  places the header, the side navigation and the page with the grid of design D2 from 1240 pixels, and that below 1240 pixels the
  three stack in DOM order. Verify: they fail on `AdministrationFrame.vue` and `SideNavLayout.vue`.
- [ ] 3.2 `test(ui)`: in `e2e/layout.spec.js`, add cases: at 1280 and 1920 pixels the left edge of the heading equals the left
  edge of the page column within 0.5 pixel, and its top edge equals the top edge of the side navigation; at 1000 pixels the heading
  is above the index. Verify: they fail against the group 1 build.
- [ ] 3.3 `test(ui)`: in `SideNavLayout.test.js`, assert that the page column has the key `route name:current entry` and that the
  header is not sticky. Verify: it fails, because the page has no key yet.
- [ ] 3.4 `feat(ui)`: move the header into the grid of `AdministrationFrame.vue` and `SideNavLayout.vue` as design D2 describes. The
  side navigation keeps its sticky rule of `rework-administration-side-navigation`. Verify: 3.1 and 3.2 pass.
- [ ] 3.5 `feat(ui)`: add `--animate-md-page-enter` and the `md-page-enter` keyframes to `src/styles/md3/shape-elevation-motion.css`,
  apply the class to the page column through the key of 3.3, and set `animation: none` under reduced motion. Verify: a unit test
  asserts the class on an entry change and none on a query change, and an e2e test emulating reduced motion reads
  `animation-name: none` on the page column.
- [ ] 3.6 `docs(ui)`: `DESIGN.md` and `docs/gitbook/reference/dashboard-pages.md` describe the header position from 1240 pixels and the
  enter transition. Verify: the page names the header position at 1240 pixels, and the dash grep prints nothing.
- [ ] 3.7 Run the unit tests of the layout through `heavy`, targeted, and `e2e/layout.spec.js` with `--workers=2`. Verify: they pass, and
  the axe checks of `e2e/accessibility.spec.js` are clean.
- [ ] 3.8 Take the before and after screenshots at 320, 600 and 1440 pixels in light and dark, of an entry and the Overview.
  Verify: below 1240 pixels the screenshots are identical; at 1440 pixels the heading has moved beside the side navigation only.
- [ ] 3.9 Open the code pull request `feat(ui): place the administration header beside the side navigation`, with the template body
  and the screenshots. Verify: `gh pr view` shows the exact title, and the body has no em or en dash.

## 4. Group 3: the repository page is split into tabs (`feat(ui)`)

Pull request title: `feat(ui): split the repository settings into tabs`

Starts after the code PR of group 2 is merged and `MdTabs` from `migrate-content-to-components` is on `main`.

- [ ] 4.1 `test(ui)`: in `src/components/repositories/RepositoryDetail.test.js`, assert the six tabs in the order of design D5, each
  with `role="tab"`, `aria-selected`, `aria-controls` and a roving tabindex, and the panel with `aria-labelledby`. Verify: it fails on
  `RepositoryDetail.vue` (no tab list).
- [ ] 4.2 `test(ui)`: in `src/components/settings/EntryDetail.test.js`, assert that every field of `maven.schema.json` for the
  repository entry is in exactly one section of design D5, and that no section names a field the schema does not have. Verify: it
  fails, because `EntryDetail` has no sections.
- [ ] 4.3 `test(ui)`: in `RepositoryDetail.test.js`, assert the keyboard model of design D8 (Left, Right, Home and End move focus and
  selection), that `?tab=storage` opens Storage, that an unknown key opens General, that a tab change does not add a history entry, and
  that the outer `role="tabpanel"` of the repository page is gone. Verify: they fail on the current build.
- [ ] 4.4 `test(ui)`: in `RepositoryDetail.test.js` and `EntryDetail.test.js`, assert that a value typed on General survives a switch
  to Storage and back, that a hidden value is in the saved entry, that a rename on Storage keeps `tab=storage`, and that a problem on
  Storage is counted on its label and linked from the save bar. Verify: they fail on the current build.
- [ ] 4.5 `test(ui)`: in `e2e/repositories-crud.spec.js` and `e2e/accessibility.spec.js`, add cases: a deep link opens its tab; the
  keyboard path works without a pointer; axe reports no violation on each tab at 320, 600 and 1440 pixels. Verify: they fail on the
  current build.
- [ ] 4.6 `feat(ui)`: add the tab list of `RepositoryDetail.vue` (the primitive of `migrate-content-to-components`, design D8) and the
  `section` prop of `EntryDetail.vue` (design D6), with the query of design D7. Remove the outer `role="tabpanel"` for the repository
  page in `SettingsView.vue`. Verify: 4.1 to 4.4 pass.
- [ ] 4.7 `docs(gitbook)`: `docs/gitbook/reference/repository-settings.md` lists the six tabs, the `tab` address and the keyboard
  model. Verify: the page names each tab, and the dash grep prints nothing.
- [ ] 4.8 Run the repository unit tests and `e2e/repositories-crud.spec.js` through `heavy`, with `--workers=2`. Verify: they pass, and
  axe is clean on each tab.
- [ ] 4.9 Take the before and after screenshots at 320, 600 and 1440 pixels of the General tab and of one other tab, in light and dark.
  Verify: the type section and the delete action are at the same place on every tab. Attach them to the code pull request.
- [ ] 4.10 Open the code pull request `feat(ui): split the repository settings into tabs`, with the template body and the screenshots.
  Verify: `gh pr view` shows the exact title, and the body has no em or en dash.

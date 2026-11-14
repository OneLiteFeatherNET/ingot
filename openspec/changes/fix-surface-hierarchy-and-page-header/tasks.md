# Tasks

Each group is one or more commits of one Conventional Commits type, and the group heading names that type. Every test is written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock,
no network, a fresh component, router and store per test, the DOM read from a fresh mount, assertions with messages. Source-scan tests
read files with `fs` and never mount. Layout is measured in Playwright with `getBoundingClientRect`, using role selectors and no fixed
timeouts. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, through `heavy`. Playwright runs through
`heavy`. No file under `reposilite-backend/` changes, and no file under `openspec/` is in the code pull request.

Before any code: `MdPage`, `MdPageHeader`, `MdBreadcrumb` and `MdListDetail` come from `add-md3-component-catalogue`, and the utilities of
`refactor-md3-token-utilities` must be merged. Check both on the remote main branch first.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-surface-hierarchy-and-page-header`: this change, nothing else, tasks unchecked, branched from the
  remote main branch. Verify: `openspec validate fix-surface-hierarchy-and-page-header --strict` passes and the dash grep prints nothing.

## 2. Test (red tests)

- [ ] 2.1 `test(ui)`: in `src/components/md/MdCard.test.js`, assert that the default card is `bg-surface-container-low` with `border-outline`,
  that `outlined` is `bg-surface` with `border-outline`, and that `elevated` keeps `shadow-elevation-1` without a border. Remove the
  assertion that the default is `bg-surface-container-highest`. Verify: it fails on `MdCard.vue:31-35`.
- [ ] 2.2 `test(ui)`: in `src/styles/md3/color.test.js`, assert that dark `surface-dim` is `#080f16`, that it has a lower relative luminance
  than dark `surface`, and that `outline` reaches 3:1 against `surface-container` in both themes. Verify: it fails, because dark
  `surface-dim` equals `surface` (`color.css:114`).
- [ ] 2.3 `test(ui)`: in `src/theme/runtime.test.js`, assert that a generated dark theme gives `surface-dim` a lower luminance than
  `surface`. Verify: it fails, because the generator maps `surfaceDim` to the same value as `surface` (`runtime.js:108`).
- [ ] 2.4 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js`, assert on every administration page (overview, a group
  page, a detail page, `/settings` without an entry): exactly one `h1`; the trail is a `nav` with an `ol`; each separator is an
  `aria-hidden` element; no crumb class contains `after:` or `before:`; the last crumb is the only `aria-current="page"`; below 1240
  pixels the trail draws its last crumb only and its `title` is the full trail; the back control is not inside the text column; the
  reading page root carries `MdPage` with the reading variant. Verify: it fails on `AdministrationFrame.vue:98`, `:103`, `:156`,
  `:170-173` and `:178`.
- [ ] 2.5 `test(ui)`: in `src/components/browser/BreadcrumbNavigation.test.js`, assert that the trail renders `MdBreadcrumb` (its root
  carries the `breadcrumb-navigation` anchor), that the current crumb and the links share one minimum height class, that the parent
  directory button is outside the trail, and that no separator uses `after:`. Verify: it fails on `BreadcrumbNavigation.vue:52`, `:69`
  and `:94`.
- [ ] 2.6 `test(ui)`: in `src/components/repositories/RepositoryDetail.test.js`, assert that no `h2` repeats the repository name, that
  the element with `data-ingot-part="header"` and `data-ingot-repository` holds the repository type section, and that the page emits the
  name through its title. Verify: it fails on `RepositoryDetail.vue:200-212`.
- [ ] 2.7 `test(ui)`: in `src/components/account/AccountView.test.js`, assert that the page root carries the reading variant of `MdPage`
  and that the page has one `h1`. Verify: it fails on `AccountView.vue:43` and `:47`.
- [ ] 2.8 `test(ui)`: in `src/components/settings/EntryDetail.test.js`, assert that the back button is in its own row above the `h3` at
  every width (no `min-[840px]:hidden` on the row that holds the heading). Verify: it fails on `EntryDetail.vue:280`.
- [ ] 2.9 `test(ui)`: in `src/components/access/TeamsPanel.test.js`, `src/components/access/PrincipalsPanel.test.js` and a new
  `src/components/settings/CollectionEditor.test.js`, assert that the detail pane carries the inset class that `MdListDetail` gives both
  panes, and no `max-[839px]:hidden` on the pane's inset. Verify: it fails on `TeamsPanel.vue:196`, `PrincipalsPanel.vue:255` and
  `CollectionEditor.vue:161`.
- [ ] 2.10 `test(ui)`: in `src/components/card/SnippetsCard.test.js`, assert that the card title is a heading of level two or three.
  Verify: it fails on `SnippetsCard.vue:178`.
- [ ] 2.11 `test(ui)`: in `src/styles/md3/window-margins.test.js`, read the views of the page list in the design (D2) and assert that no
  file contains the `container` class outside `MdPage`. Verify: it fails on each file that still writes `container`.
- [ ] 2.12 `test(ui)`: in `src/anchors/anchors.test.js`, assert that `administration-breadcrumb` is registered with the file
  `src/components/layout/AdministrationFrame.vue` and that the `header` part of `repository-detail` is still registered. Verify: it
  fails, because the registry has no `administration-breadcrumb` entry.
- [ ] 2.13 `test(ui)`: in `e2e/layout.spec.js`, add cases: one `h1` per page at 320 and 1440 pixels; at 1280 and 1920 pixels the left
  edges of the repository detail title and its trail match within 0.5 pixel; at 1920 pixels the reading page is centred; at 390 pixels
  the back control of a detail page does not move the title. Replace the level 2 "Repositories" heading check at `layout.spec.js:35`
  with level 1. Selectors are roles and accessible names. Verify: they fail against the current build.

## 3. Refactor (refactor(ui))

- [ ] 3.1 `refactor(ui)`: move every page container that already renders the same spacing onto `MdPage` (`wide` or `reading`), with no
  change of pixels: `DashboardView.vue:37`, `FileBrowserView.vue:208`, `DefaultHeader.vue:37`, `HeaderHero.vue:42`, `NotFoundView.vue:23`,
  `ViewLoadError.vue:27`, `ViewLoading.vue:23`, `IndexPage.vue:378` and `:399`, and `AnalyticsView.vue` (which has none). Verify: the
  before and after screenshots of these pages at 390 and 1280 pixels are identical, pixel for pixel, in light and dark.
- [ ] 3.2 `refactor(ui)`: move the inline page headings onto `MdPageHeader` where the heading keeps its size: `DashboardView.vue:40`,
  `SearchView.vue:253`, `IndexPage.vue:381` and `:402`, and `RepositoriesView.vue:161-184`. The level changes to one, so the page has
  one `h1`. Verify: 2.4 (page count) passes for these pages, and the before and after screenshots are identical.
- [ ] 3.3 `refactor(ui)`: move `BreadcrumbNavigation.vue` onto `MdBreadcrumb` and keep the `breadcrumb-navigation` anchor on the `nav`.
  Verify: 2.5 passes and the file browser screenshots at 390 and 1280 pixels differ only in the trail's separators.

## 4. Fix (fix(ui))

- [ ] 4.1 `fix(ui)`: add the card rows of the surface table to `MdCard.vue` (filled, outlined, elevated) and to the hand-written cards
  listed in design D7. Verify: 2.1 passes.
- [ ] 4.2 `fix(ui)`: set the dark `surface-dim` in `src/styles/md3/color.css` and the seeded path in `src/theme/runtime.js`. Verify: 2.2
  and 2.3 pass.
- [ ] 4.3 `fix(ui)`: apply the menu, side sheet and floating panel elevations of the surface table: `MdSideSheet.vue:126` to
  `shadow-elevation-1`, and the More panel in `IndexPage.vue:517` to `shadow-elevation-2`. `dashboard/PublicRepositoriesDialog.vue:73`
  already matches the dialog row (`surface-container-high`, `shadow-elevation-3`), so it is kept and read as the reference. Verify: the surface test of the element in `src/components/md/MdSideSheet.test.js` passes, and the other
  elevations are read in the screenshots.
- [ ] 4.4 `fix(ui)`: fix the nested blocks of design D1: `maintenance/CleanupPoliciesPanel.vue:175` and `:228`,
  `maintenance/IndexPanel.vue:170` and `:319`, `audit/AuditEventRow.vue:113` inside `audit/AuditView.vue:149`, and the hand-written cards
  of D7. Verify: the nested-step test of the spec passes for each file, and the contrast values are recorded.
- [ ] 4.5 `fix(ui)`: draw the content sheet edge in `IndexPage.vue:359-360` with a 1 pixel `outline` border from 600 pixels. Verify: the
  content sheet requirement passes at 1280 pixels in light and dark.
- [ ] 4.6 `fix(ui)`: `AdministrationFrame.vue`: render `MdPage` (reading or wide) and `MdPageHeader`; put the back control in the back
  row (`-ms-3`); draw the trail with `MdBreadcrumb`, with the Overview crumb as the current section name; keep the Sections button and the
  shortcuts in the actions; give the trail the anchor `administration-breadcrumb` and register it in `src/anchors/registry.json` in the
  same commit. Verify: 2.4 and 2.12 pass.
- [ ] 4.7 `fix(ui)`: `RepositoryDetail.vue`: drop the `header` block with the `h2` (`:200-212`); pass the name to the frame as its title; move
  `data-ingot-part="header"` and `data-ingot-repository` to the type section; turn the "Back to repositories" text button into the frame's
  back control with an `aria-label`; `SettingsView.vue:464-473` passes the title and the back target. Verify: 2.6 and 2.13 pass.
- [ ] 4.8 `fix(ui)`: `settings/EntryDetail.vue`: move the back button into its own row above the `h3` (`:272-295`), with the same offset as
  the frame, so the heading stays put at 840 pixels. Verify: 2.8 passes, and `SettingsView.entries.test.js` still passes.
- [ ] 4.9 `fix(ui)`: `AccountView.vue`: render `MdPage` (reading) and `MdPageHeader` with the one `h1`. Verify: 2.7 passes.
- [ ] 4.10 `fix(ui)`: `TeamsPanel.vue`, `PrincipalsPanel.vue` and `CollectionEditor.vue`: take the inset of `MdListDetail` on both panes
  at every width. Verify: 2.9 passes.
- [ ] 4.11 `fix(ui)`: the remaining page containers that differ from `MdPage` today: `SearchView.vue:250` (wide, `max-w-none`),
  `SiteFooter.vue:31` (centred), `RepositoryOverview.vue:202` (`pb-12` instead of `mb-12`), and the frame's reading variant. Verify: 2.11
  passes, and the screenshot of each at 1280 pixels shows the centred or aligned result.
- [ ] 4.12 `fix(ui)`: `SnippetsCard.vue:178`: draw the card title as a heading of level two or three. Verify: 2.10 passes.

## 5. Docs (docs(ui) and docs(gitbook))

- [ ] 5.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, add the surface table of design D1, the page shell rules (D2), the page header
  rules (D3), the one trail (D4), the card default (D7) and the dark `surface-dim` (D8), and update "Top app bar" and "Adaptive navigation"
  where they describe the content sheet edge. Verify: the dash grep prints nothing.
- [ ] 5.2 `docs(gitbook)`: in `docs/gitbook/reference/dashboard-pages.md`, describe the trail and the page header where that page describes
  them, and regenerate `docs/gitbook/reference/dashboard-anchors.md` from the registry, so `administration-breadcrumb` is listed.
  Verify: the anchor reference lists `administration-breadcrumb` as experimental.

## 6. Check

- [ ] 6.1 Before any code, record the screenshots on the remote main branch at 390, 600, 840, 1280, 1440 and 1920 pixels, in light and dark,
  of the administration overview, the repositories list, a repository detail, a team detail, the account page, the dashboard and the file
  browser. Record the contrast table of design D10 for the same states. Keep them for the pull request, not for a commit.
- [ ] 6.2 After the fixes, record the same screenshots and the same contrast table, the bounding-box comparison of the title and the trail
  at 1280 and 1920 pixels, and the high contrast values of `outline` in the `prefers-contrast: more` scheme. Verify: each value of the
  table is at least the value the spec requires.
- [ ] 6.3 Run `npm test` in `reposilite-frontend` through `heavy`, and the Playwright suites `layout.spec.js`, `navigation.spec.js`,
  `accessibility.spec.js`, `browse.spec.js` and `dashboard.spec.js` through `heavy`. Verify: all pass, including the
  `add-ui-style-gate` tests with the baseline entries of the touched files removed and no entry added.
- [ ] 6.4 Run `openspec validate fix-surface-hierarchy-and-page-header --strict` and the dash grep. Verify: both pass.

## 7. Pull request

- [ ] 7.1 Open the code pull request under the title `fix(ui): separate surfaces and align page headers and breadcrumbs`, from the remote
  main branch that already contains the merged proposal, the merged `refactor-md3-token-utilities`, `add-ui-style-gate` and
  `add-md3-component-catalogue`,
  with no file under `openspec/`. Fill `.github/PULL_REQUEST_TEMPLATE.md` and put the screenshots and the measured values of 6.1 and
  6.2 in it. Verify: the title passes the commit-style hook, and the description has no session link and no dash.

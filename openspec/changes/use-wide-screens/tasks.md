# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Every test is written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps, no network, no system clock, a fresh
router and store per test, assertions with messages. The browser cases wait for a condition in Playwright (auto-waiting locators),
never for a fixed time.

This change starts after `rework-administration-layout` is merged (it is) and shares its proposal PR with `rework-default-theme`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose rework-default-theme and use-wide-screens`: the two changes and nothing else. Verify:
  `openspec validate use-wide-screens --strict` passes, and the dash grep prints nothing.

## 2. Red component tests

- [ ] 2.1 `test(ui)`: write failing tests for the page kinds of design D1: the Repositories table, the file browser listing and the
  Tokens, Access, Audit and Identity tables carry no maximum-width class; the settings domains and the Maintenance page carry the
  reading class `max-w-[960px]`. Verify: they fail on `AdministrationFrame.vue`, which caps every page at 1280 and 1600 pixels.
- [ ] 2.2 `test(ui)`: write failing tests for the dashboard grid of `DashboardView.vue`: the tile and chart container has the classes
  for one column, then two from 1920 pixels, then three from 2560 pixels, and the instance status panel spans the full row. Verify: they
  fail on the single-column container.
- [ ] 2.3 `test(ui)`: write failing tests for the overview of `RepositoryOverview.vue`: at 1920 pixels the browse list and the snippet
  card are in a two-column split with the list in the wider column; below 1920 they stack. Verify: they fail on the stacked layout.
- [ ] 2.4 `test(ui)`: write failing tests for the column visibility of `RepositoriesTable.vue`: Version policy and Visibility carry the
  show-from-1920 class (not 1600); Storage carries the show-from-2560 class (not 1920); Name, Type and Actions carry no hiding class.
  Verify: they fail on the current thresholds.

## 3. Red browser tests

- [ ] 3.1 `test(ui)`: write failing Playwright cases in `reposilite-frontend/e2e/layout.spec.js` at 1920 and 2560 pixels: the repository
  table shows the "Address" column at both widths and the "Storage" column only at 2560; the dashboard shows two tile columns at 1920 and
  three at 2560, measured from the bounding boxes of two tiles; a settings form's main region is at most 960 pixels wide at both widths.
  Selectors are roles and accessible names. Verify: they fail on the current build, which caps the content at 1600 pixels.
- [ ] 3.2 `test(ui)`: write a failing Playwright case at 1920 pixels: the snippet card of a repository overview is beside the browse list,
  not below it. Verify: it fails on the stacked layout.

## 4. Layout changes

- [ ] 4.1 `feat(ui)`: in `AdministrationFrame.vue`, remove the content cap from the wide page kinds and apply the 960 pixel reading
  class to the reading pages (task 2.1). Verify: the test of 2.1 passes.
- [ ] 4.2 `feat(ui)`: add the dashboard grid classes (`DashboardView.vue`) and the overview split (`RepositoryOverview.vue`). Verify: the
  tests of 2.2 and 2.3 pass.
- [ ] 4.3 `feat(ui)`: move the column visibility thresholds of `RepositoriesTable.vue` as in design D4. Verify: the test of 2.4 passes, and
  the existing `RepositoriesAccessibility.test.js` and `RepositoriesView.test.js` still pass.
- [ ] 4.4 `feat(ui)`: apply the full width to the file browser listing (`FileBrowserView.vue`, `FileList.vue`). Verify: the test of 2.1 for
  the listing passes, and the existing browser tests pass.
- [ ] 4.5 Run the browser cases of 3.1 and 3.2 until they pass. Verify: `npx playwright test layout.spec.js` passes with the 1920 and 2560
  cases, run through the slot runner (`heavy`) where a browser is started.

## 5. Documentation

- [ ] 5.1 `docs(ui)`: update the layout section of `docs/gitbook/reference/dashboard-pages.md` to name the wide and reading page kinds, the
  four widths of `rework-administration-layout` and the two wide breakpoints (1920 and 2560 pixels), and the columns shown on wide screens.
  Verify: the page names the 960 pixel reading measure, and the dash grep prints nothing.

## 6. Pull request

- [ ] 6.1 Open the code pull request under the title `feat(ui): use the full width for lists and two grids on wide screens`, from the
  remote main branch that already contains the merged proposal, with no file under `openspec/`. Fill the repository's pull request
  template and name the browser cases of 3.1 and 3.2 under "How was this tested?".

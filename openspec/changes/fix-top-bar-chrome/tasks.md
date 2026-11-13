# Tasks

Each group is one or more commits of one Conventional Commits type, all with the scope `ui`. Every test is written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no
network, a fresh component and router per test, assertions with messages. `npm test` runs in `reposilite-frontend`, targeted with
a file pattern while working, through `heavy`. Playwright runs through `heavy` as well. No file under `reposilite-backend/`
changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the administration area redesign`: this change and its three siblings, nothing else.
  Verify: `openspec validate fix-top-bar-chrome --strict` passes and `grep -rnP '[\x{2013}\x{2014}]'` over the change prints
  nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: in `src/components/header/DefaultHeader.test.js`, replace "draws the brand rule" (lines 86-95) with "draws
  no rule": the rendered header contains no `.olf-brand-bar` and no element with a border class. Keep the test at lines 43-48
  that pins `bg-surface-container`, and add that the header carries no shadow class and no transition class. Verify: the rule
  test fails on `DefaultHeader.vue:62-65`.
- [ ] 2.2 `test(ui)`: in the same file, replace the `find('h1')` assertion at line 68 with: the header has no heading element, the
  instance name is a link to `/`, and the element with `data-ingot-part="title"` holds the name inside the element with
  `data-ingot-component="default-header"`. Verify: it fails on the `h1` at `DefaultHeader.vue:49`.
- [ ] 2.3 `test(ui)`: in `DashboardView.test.js`, `AccountView.test.js`, a `NotFoundView` test and `IndexPage.test.js` (no
  access message), assert exactly one `h1` with the texts Dashboard, My account, Page not found and the section label, and that
  `dashboard-view`, `account-view` and `not-found-view` stay on the roots. Verify: they fail, because each of these starts at
  `h2`.
- [ ] 2.4 `test(ui)`: in `e2e/navigation-chrome.spec.js`:
  - at lines 84 and 94, replace `topBar(page).locator('h1')` with `topBar(page).locator('[data-ingot-part="title"]')`, so the
    two edge cases keep measuring the title;
  - replace the rule edge case (lines 120-129) with: no `.olf-brand-bar` inside the top bar; the bar's computed background
    equals the rail's computed background at the top and after `window.scrollTo(0, 400)` (Playwright's auto-waiting
    `toHaveCSS`, no timeouts); the bar has no border and no box shadow;
  - add: exactly one `h1` on `/`, `/search`, `/dashboard`, `/account` and `/settings`, none inside the top bar.
  Run at 320 and 1280 pixels (at 320 the rail case compares with the `surface-container` role instead). Leave line 93 to
  `rework-administration-information-architecture`. Verify: the new cases fail against the current build, and the two edge
  cases at lines 84 and 94 pass before and after.

## 3. Fix

- [ ] 3.1 `fix(ui)`: delete the rule `div` from `DefaultHeader.vue` and the comment line that describes it. Verify: 2.1 passes,
  and `src/styles/md3/color.test.js` passes unchanged.
- [ ] 3.2 `fix(ui)`: replace the title `h1` with a link, keep the anchor attributes on the same elements, truncate on one line.
  Verify: 2.2 passes, and `src/anchors` tests pass with no registry change.
- [ ] 3.3 `fix(ui)`: add the `h1` "Dashboard" to `DashboardView.vue`, and turn the titles of `AccountView.vue`, `NotFoundView.vue`
  and the no access message in `IndexPage.vue` into `h1` with the same id, text and classes. Verify: 2.3 and 2.4 pass.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: update `reposilite-frontend/DESIGN.md`: "Top app bar" as the frame (`surface-container` at every scroll
  position, no tone change, no decorative rule, identity and account menu only); "MdNavigationRail" a 96 pixel
  `surface-container` rail; "Adaptive navigation" the 96 pixel offset and the `surface` content sheet with its corner pieces;
  "Brand gradient" not drawn in the chrome or by any component; "Known gaps" replaced by the real list (no tabs, list, dialog
  or drawer primitive; Administration information architecture rules pending). Verify: the dash grep over `DESIGN.md` prints
  nothing, and `grep -n "80px\|useWindowScroll" DESIGN.md` finds no line about the rail or the top bar.

## 5. Check

- [ ] 5.1 Run `npm test` and the Playwright suite (`navigation-chrome.spec.js`, `accessibility.spec.js`) through `heavy`. Record
  screenshots of the top bar at rest and scrolled, at 320, 600 and 1440 pixels, before and after, in the pull request. Verify: all
  pass.

## 6. Pull request

- [ ] 6.1 Open the code pull request under the title `fix(ui): drop the brand rule under the top bar`, from the remote main
  branch that already contains the merged proposal, with no file under `openspec/`. Fill the repository's pull request
  template and name the changed title elements for plugin stylesheets in the description.

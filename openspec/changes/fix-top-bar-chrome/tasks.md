# Tasks

Each group is one or more commits of one Conventional Commits type, all with the scope `ui`. Every test is written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no
network, a fresh component and router per test, the scroll position injected through the composable seam instead of a real
scroll, assertions with messages. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, through
`heavy`. Playwright runs through `heavy` as well. No file under `reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the administration area redesign`: this change and its three siblings, nothing else.
  Verify: `openspec validate fix-top-bar-chrome --strict` passes and `grep -rnP '[\x{2013}\x{2014}]'` over the change prints
  nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: in `src/components/header/DefaultHeader.test.js`, replace "draws the brand rule" with "draws no rule": the
  rendered header contains no `.olf-brand-bar` and no element with a border class. Verify: it fails on `DefaultHeader.vue:62-65`.
- [ ] 2.2 `test(ui)`: in the same file, with `useScrolledPast` replaced by a ref, assert `bg-surface` while the ref is false,
  `bg-surface-container` once it is true, the transition classes, `motion-reduce:transition-none`, and no shadow class in either
  state. Add a unit test for `useScrolledPast` with an injected scroll ref (0 is false, 1 is true). Verify: they fail, because
  the bar is always `bg-surface-container` and the composable does not exist.
- [ ] 2.3 `test(ui)`: assert that the header has no heading element, that the instance name is a link to `/`, and that the
  element with `data-ingot-part="title"` holds the name inside the element with `data-ingot-component="default-header"`.
  Verify: it fails on the `h1` at `DefaultHeader.vue:49`.
- [ ] 2.4 `test(ui)`: in `e2e/navigation-chrome.spec.js`, replace the rule edge case (lines 120-129) with: no `.olf-brand-bar`
  inside the top bar; the bar's computed background equals the `surface` role at the top and the `surface-container` role after
  `window.scrollTo(0, 400)` (Playwright's auto-waiting `toHaveCSS`, no timeouts); exactly one `h1` on `/settings`. Run at 320
  and 1280 pixels. Verify: it fails against the current build.

## 3. Fix

- [ ] 3.1 `fix(ui)`: add `src/helpers/useScrolledPast.js` around `useWindowScroll`, bind the tone classes in
  `DefaultHeader.vue`, and delete the rule `div`. Verify: 2.1 and 2.2 pass, and `src/styles/md3/color.test.js` passes unchanged.
- [ ] 3.2 `fix(ui)`: replace the title `h1` with a link, keep the anchor attributes on the same elements, truncate on one line.
  Verify: 2.3 and 2.4 pass, and `src/anchors` tests pass with no registry change.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: update `reposilite-frontend/DESIGN.md`: "Top app bar" as built and "no decorative rule"; "Brand gradient"
  not drawn in the chrome; "Adaptive navigation" rail 96 pixels; "Known gaps" replaced by the real list (no tabs, list, dialog
  or drawer primitive; Administration information architecture rules pending). Verify: the dash grep over `DESIGN.md` prints
  nothing.

## 5. Check

- [ ] 5.1 Run `npm test` and the Playwright suite (`navigation-chrome.spec.js`, `accessibility.spec.js`) through `heavy`. Record
  a screenshot of the top bar at rest and scrolled, at 320 and 1280 pixels, in the pull request. Verify: all pass.

## 6. Pull request

- [ ] 6.1 Open the code pull request under the title `fix(ui): drop the brand rule under the top bar`, from the remote main
  branch that already contains the merged proposal, with no file under `openspec/`. Fill the repository's pull request
  template and name the title element change for plugin stylesheets in the description.

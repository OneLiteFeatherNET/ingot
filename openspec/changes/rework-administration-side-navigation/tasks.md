# Tasks

Each group is one or more commits of one Conventional Commits type. Every test is written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, a fresh component
and router per test, the size observer injected as a fake that the test triggers by hand (jsdom has no `ResizeObserver`), the fit
decision tested as a pure function with fixed heights, assertions with messages. `npm test` runs in `reposilite-frontend`, targeted
with a file pattern while working, through `heavy`. Playwright runs through `heavy`. No file under `reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose the administration area redesign`: this change and its three siblings, nothing else.
  Verify: `openspec validate rework-administration-side-navigation --strict` passes and the dash grep prints nothing.

## 2. Red tests

- [ ] 2.1 `test(ui)`: in a new `src/helpers/treeFits.test.js`, assert `treeFits` for fixed heights: fits with room to spare, fits
  exactly, does not fit by one pixel, and with a save bar that takes the room. Verify: it fails, because the helper does not exist.
- [ ] 2.2 `test(ui)`: in `src/components/layout/SideNavLayout.test.js`, with a fake observer, assert the `nav` has no
  `overflow-y-auto` and no `max-h-` class in either state, is sticky after the fake reports a fitting height and static after it
  reports a taller one, and does not change when a scroll event fires. Verify: it fails on `SideNavLayout.vue:77`.
- [ ] 2.3 `test(ui)`: assert index rows below 1240 pixels show the description under the label, rows without a description show one
  line, and side column rows show labels only. Verify: it fails.
- [ ] 2.4 `test(ui)`: in a new `src/components/md/MdSideSheet.test.js`, assert `role="dialog"`, `aria-modal`, the accessible name,
  focus on open, the focus trap, Escape and the close button returning focus to the opener, the reduced motion class, and the
  `data-ingot-component` name. Verify: it fails, because the component does not exist.
- [ ] 2.5 `test(ui)`: in `src/components/layout/AdministrationFrame.test.js`, assert the Sections button exists only in the 600 to
  1239 pixel range classes and only beside a back arrow (none on `/settings`), opens the sheet with the tree, and that choosing an
  entry navigates and closes the sheet. Verify: it fails.
- [ ] 2.6 `test(ui)`: in `e2e/layout.spec.js`, add cases at 1366 by 768, 1920 by 1080 and 2560 by 1440 (sticky only where the tree
  fits, no element inside the `nav` with `overflow-y: auto`, the last entry reachable by scrolling the window, and at 1366 by 768 no
  element of the page content with `position: sticky`; replace the "Only one sticky column" case at 1280 pixels with a 1280 by 1440
  window), at 900 pixels (Sections opens the sheet, Users opens and closes it, Escape returns focus; no Sections button on
  `/settings`), and at 320 pixels (no Sections button, no horizontal scroll).
  Selectors are roles and accessible names; no timeouts. Verify: they fail against the current build.

## 3. Implementation

- [ ] 3.1 `feat(ui)`: add `src/helpers/treeFits.js` and its composable with the injectable observer, bind the sticky class from it,
  and remove `max-h-[...]` and `overflow-y-auto` from `SideNavLayout.vue`. Verify: 2.1 and 2.2 pass.
- [ ] 3.2 `feat(ui)`: draw the description line in index rows. Verify: 2.3 passes.
- [ ] 3.3 `feat(ui)`: add `components/md/MdSideSheet.vue` and register `md-side-sheet` as `experimental` in
  `src/anchors/registry.json`. Verify: 2.4 passes and the anchors tests pass.
- [ ] 3.4 `feat(ui)`: add the Sections button to `AdministrationFrame.vue` and render the tree in the sheet. Verify: 2.5 and 2.6 pass.

## 4. Documentation

- [ ] 4.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, describe the sticky rule (sticky only while it fits, never an inner scroll
  box), the index rows, and the `MdSideSheet` primitive with its accessibility contract; remove "drawer" from "Known gaps" if it is
  listed there. Verify: the dash grep prints nothing.
- [ ] 4.2 `docs(gitbook)`: in `docs/gitbook/reference/dashboard-pages.md` "Layout at four widths", describe the Sections sheet and the
  sticky rule, and regenerate `docs/gitbook/reference/dashboard-anchors.md` from the registry. Verify: the anchor reference lists
  `md-side-sheet` as experimental.

## 5. Check

- [ ] 5.1 Run `npm test` and `e2e/layout.spec.js`, `e2e/navigation.spec.js` and `e2e/accessibility.spec.js` through `heavy`. Record
  screenshots at 900 pixels with the sheet open and at 1920 by 1080 scrolled to the end in the pull request. Verify: all pass.

## 6. Pull request

- [ ] 6.1 Open the code pull request under the title `feat(ui): keep the administration tree in reach at every width`, from the
  remote main branch that already contains the merged proposal and the merged code of
  `rework-administration-information-architecture`, with no file under `openspec/`. Fill the repository's pull request template.

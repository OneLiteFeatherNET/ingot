# Tasks

Each group is one or more commits of one Conventional Commits type and the scope `ui`. Every test is written first and seen to
fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network,
a fresh mount and a fresh provider per test, fake timers only where a timer is the behaviour, assertions with messages. `npm test`
runs in `reposilite-frontend`. Targeted runs use a file pattern while working. Full runs and Playwright runs go through `heavy`,
with Gradle not involved. No file under `reposilite-backend/` changes.

No configuration key, environment variable or deployment manifest changes, so no page under `docs/gitbook/` is updated.

Task checkboxes are ticked in the follow-up pull request, never in the code pull request.

Visible change: none is intended. Every swap that claims pixel identity is proven by before and after screenshots at 320, 600 and
1440 pixels in light and dark (task 5.3). A swap whose screenshots differ is reverted in its group and listed as not done.

## 1. docs(openspec): proposal

- [ ] 1.1 `docs(openspec): propose add-md3-component-catalogue`: this change only, nothing else. Verify:
  `openspec validate add-md3-component-catalogue --strict` passes and the dash grep (task 5.5) prints nothing.

## 2. test(ui): red tests

Each test below is written, run, seen to fail for the stated reason, and only then followed by its production task in section 3.

- [ ] 2.1 `test(ui)`: in `src/components/md/catalogue.test.js`, read `src/components/md/*.vue` and `reposilite-frontend/DESIGN.md`. Assert that
  every primitive has a catalogue heading, that no heading names a missing file, and that DESIGN.md has no `Known gaps` heading.
  Verify: it fails, because the catalogue has no entries for the new primitives and the gaps list still exists.
- [ ] 2.2 `test(ui)`: in `src/components/md/apiConvention.test.js`, read the declared props and emits of every primitive with the SFC
  compiler (the way `src/anchors/checks.js` reads templates). Assert that no prop accepts a class string (`triggerClass` is the
  only one allowed as a deprecated alias, and it is listed), that emits are kebab-case, and that every icon slot is `leading` or
  `trailing`, with `icon` allowed only on the components named in the proposal. Verify: it fails on `MdOverflowMenu`, `CardMenu`
  and `MdChip`.
- [ ] 2.3 `test(ui)`: in `src/anchors/anchors.test.js`, assert the registry names of the new primitives, that `segmented-control` and
  `segmented-option` keep their names and point at the moved files with no alias entry, and that `dialog-shell` points at `src/components/md/MdDialog.vue`. Verify: it fails, because the entries do not
  exist yet.
- [ ] 2.4 `test(ui)`: in `src/components/md/MdIcon.test.js`, assert the decorative default (`aria-hidden="true"`), the labelled form,
  the 24px default size, and a named glyph from the registry. Verify: it fails, because `MdIcon.vue` does not exist.
- [ ] 2.5 `test(ui)`: in `src/components/md/MdSideSheet.test.js`, assert that the close control renders the `MdIcon` path of the close
  glyph (the same `d` string as `components/icons/CloseIcon.vue`), that `side="end"` puts the sheet at the trailing edge, that
  the width comes from a `--container-md-*` token class, `role="dialog"`, `aria-modal`, the focus trap and the focus return on
  both sides, and that the scrim has no inline style. Verify: it fails on the inline svg and the missing `side` prop.
- [ ] 2.6 `test(ui)`: in `src/components/md/MdFieldShell.test.js`, assert the helper line id pattern, `aria-describedby` on the field,
  the error state's `aria-invalid="true"`, and the `leading` and `trailing` slots at the shell's inset. Verify: it fails, because
  `MdFieldShell.vue` does not exist.
- [ ] 2.7 `test(ui)`: in `src/components/md/MdSelect.test.js` and `MdTextField.test.js`, assert `readonly` on the text field, the
  select `width` prop, and that the select's trailing icon sits 12px from the edge. Verify: it fails on the missing props.
- [ ] 2.8 `test(ui)`: in `src/components/md/MdNavigationItem.test.js`, assert the active indicator, `aria-current="page"` on the active
  item only, a modified click left to the browser (no prevented default, no emit), and that `MdNavigationBar` and
  `MdNavigationRail` keep their anchors. Verify: it fails, because `MdNavigationItem.vue` does not exist.
- [ ] 2.9 `test(ui)`: in `src/helpers/modifiedClick.test.js`, a pure function test: a plain primary click is handled, and a click with
  Ctrl, Shift, Meta, Alt or a non-primary button is not. Verify: it fails, because the helper does not exist.
- [ ] 2.10 `test(ui)`: in `src/components/md/MdCard.test.js`, assert the default render is unchanged (root, classes, padding), `tone`,
  `edge`, `padding`, `as`, the `header` slot and the `danger` tone. Verify: it fails on the new props only.
- [ ] 2.11 `test(ui)`: in `src/components/md/MdButton.test.js` and `MdIconButton.test.js`, assert `to` renders a router link through
  `navigate`, the `inverse` tone, the flush-start text variant, the compact icon button at 24px with a 48px hit area, and the
  deprecated `variant="error"` still rendering the filled error button. Verify: it fails on the new props.
- [ ] 2.12 `test(ui)`: in `src/components/md/MdChip.test.js`, assert the trailing `remove` action with its own `aria-label`, the Boolean
  `toggle` with `selected`, and the deprecated string `toggle` value still working. Verify: it fails.
- [ ] 2.13 `test(ui)`: in `src/components/md/MdRadio.test.js`, `MdCheckbox.test.js` and `MdSwitch.test.js`, assert the default label
  gap of each control, the `supporting` slot of the radio, and that the focus ring is absent by default and present with
  `focus-ring`. Verify: it fails on the new props.
- [ ] 2.14 `test(ui)`: in `src/components/md/MdOverflowMenu.test.js`, assert the `trigger` slot, disabled items (`aria-disabled="true"`,
  skipped by the arrow keys), two-line items whose second line joins the accessible name, the error and checked tones without
  `!` overrides and with the focus ring class, and the item touch target. Verify: it fails on `triggerClass` and `!text-error`.
- [ ] 2.15 `test(ui)`: in `src/components/md/MdSnackbar.test.js`, assert that the component shows the head of an injected queue, that
  it shows nothing from the shared queue when a provider supplies another, that the placement classes switch at the `medium`
  window class (a class check, not a media query read), that the layer is `z-md-snackbar`, and that it has no `<style>` element. Verify: it fails,
  because the queue is a prop default.
- [ ] 2.16 `test(ui)`: in `src/components/md/MdLinearProgress.test.js`, assert `role="progressbar"`, that the indeterminate bar takes
  the animation utility, and that the reduced-motion variant stops it. Verify: it fails, because the style block animates it.
- [ ] 2.17 `test(ui)`: in `src/components/md/MdDialog.test.js`, assert the teleport to the body, the `dialog-shell` anchor on the frame,
  the 24px inset, the sizes `sm`, `md` and `lg` with `lg` as default inside the 280 to 560px range, the `--md-sys-z-dialog`
  layer, `role="alertdialog"`, `aria-labelledby` resolving to the heading of the `header` slot with different ids for two
  instances, focus on the `initialFocus` element or the first focusable element on open, Tab and Shift+Tab kept inside, focus
  returned to the opener on close (to the nearest ancestor when the opener was removed), Escape with `dismissable` true and
  false, and no `overlay-style` binding. Verify: it fails, because `MdDialog.vue` does not exist.
- [ ] 2.18 `test(ui)`: in `src/components/md/MdDialogActions.test.js`, assert the end alignment, one gap, and wrapping. Verify: it fails.
- [ ] 2.19 `test(ui)`: in `src/components/md/MdAlert.test.js`, `MdStatePanel.test.js` and `MdSkeleton.test.js`, assert the tones and their
  roles (`alert` for error, `status` otherwise), the states of the panel with one Retry that calls the handler once, `aria-busy`
  while loading, and the static skeleton under reduced motion. Verify: it fails, because the files do not exist.
- [ ] 2.20 `test(ui)`: in `src/components/md/MdPage.test.js`, `MdPageHeader.test.js` and `MdBreadcrumb.test.js`, assert the gutter with
  no `px-*` class, the title column with `min-w-0` and the back slot outside it, the `nav` with an `ol`, the hidden separators,
  `aria-current` on the last crumb only, the 48px hit area, and the truncation of the middle crumbs below the width token.
  Verify: it fails, because the files do not exist.
- [ ] 2.21 `test(ui)`: in `src/components/md/MdSection.test.js`, `MdListDetail.test.js`, `MdList.test.js`, `MdDivider.test.js` and
  `MdKeyValueList.test.js`, assert the heading level prop, the list links, buttons and selectable options, the leading and trailing
  slots, the divider inset and label, and the one grid template of the key value rows. Verify: it fails.
- [ ] 2.22 `test(ui)`: in `src/components/md/MdDataTable.test.js`, `MdTabs.test.js` and `MdDisclosure.test.js`, assert the caption,
  `aria-sort` on one column only, `th scope="row"` in the first column, the `tablist` with `aria-selected` on one tab, and the
  disclosure summary with its state layer. Verify: it fails.
- [ ] 2.23 `test(ui)`: in `src/components/md/MdBadge.test.js` and `MdAvatar.test.js`, assert each tone's role classes, the single
  height, the hidden avatar without a label, and the named avatar with one. Verify: it fails.
- [ ] 2.24 `test(ui)`: in `src/components/md/MdLink.test.js`, `MdCode.test.js`, `MdCodeBlock.test.js` and `MdCopyableCode.test.js`,
  assert the external rel and new-tab name, the monospace role, the copy action with its label, and the status in a live region after
  the copy helper succeeds (the helper is replaced by a fake that resolves once). Verify: it fails.
- [ ] 2.25 `test(ui)`: in `src/helpers/useCombobox.test.js` (pure, no mount) and `src/components/md/MdListbox.test.js`, assert Down and Up
  moving the active index with wrap, Home and End, Enter choosing the active option, Escape closing the list, clearing the index
  and keeping the typed text, `aria-activedescendant` following the active option, the filtering of the options, and the listbox
  roles and teleport. Verify: it fails, because the composable does not exist.
- [ ] 2.26 `test(ui)`: in `src/components/md/MdSegmentedButton.test.js`, assert the 20px corner from the shape token, the old import path
  re-exporting the new component, and the unchanged `data-ingot-component` values `segmented-control` and `segmented-option`. Verify: it fails, because the new files do not exist.
- [ ] 2.27 `test(ui)`: in `src/components/card/CardMenu.test.js`, assert that choosing a tab emits `select-tab` with the tab name and
  that `SnippetsCard` receives it through `@select-tab`. Verify: it fails, because the component emits `selectTab`.

## 3. refactor(ui): primitives and extensions

Each group below is one commit of type `refactor` with the scope `ui`. Each group makes its red tests of section 2 pass and adds
its registry entries in the same commit as the component. Groups are done in this order, because later groups import the
earlier ones.

- [ ] 3.1 `refactor(ui)`: add `MdIcon` and its glyph registry in `src/components/md/icons.js`; replace the inline svg of
  `MdSideSheet.vue` with `MdIcon` (a pixel-identical swap, task 5.3). Verify: 2.4 and 2.5 pass for the icon, and the screenshots of
  the sheet match.
- [ ] 3.2 `refactor(ui)`: add `MdFieldShell`; move `MdTextField`, `MdTextArea` and `MdChipInput` onto it; add `readonly` to
  `MdTextField`. `MdSelect` joins the shell only after the maintainer accepts the 4px trailing-icon move (proposal question 6). Verify:
  2.6 and the text field part of 2.7 pass, the three renderers' tests pass, and the screenshots of the three fields match.
- [ ] 3.3 `refactor(ui)`: add `MdSelect` `width` and `readonly` props (the shell adoption waits as in 3.2). Verify: the select part of
  2.7 passes.
- [ ] 3.4 `refactor(ui)`: add `src/helpers/modifiedClick.js` (pure); add `MdNavigationItem`; build `MdNavigationBar` and
  `MdNavigationRail` on it, keeping both anchors; use the helper in `MdOverflowMenu`. Verify: 2.8 and 2.9 pass, and the navigation
  screenshots match.
- [ ] 3.5 `refactor(ui)`: extend `MdCard` (tone, edge, padding, as, header slot, danger tone), `MdButton` (to, inverse, flush-start text
  variant, deprecated `variant="error"`), `MdIconButton` (compact size, to, inverse) and `MdChip` (trailing remove, Boolean toggle with
  deprecated string). Every default render stays the same. Verify: 2.10, 2.11 and 2.12 pass.
- [ ] 3.6 `refactor(ui)`: extend `MdRadio` (supporting slot, `focus-ring` prop off by default), and add the shared label part with the
  default gaps to `MdCheckbox`, `MdSwitch` and `MdRadio`. Verify: 2.13 passes.
- [ ] 3.7 `refactor(ui)`: extend `MdSideSheet` (side, the `--container-md-sheet` width token of `refactor-md3-token-utilities`
  and the new `--container-md-sheet-wide` token of 28rem, motion tokens, z token, scrim token). Verify: 2.5 passes in full and
  the sheet screenshots match.
- [ ] 3.8 `refactor(ui)`: extend `MdOverflowMenu` (trigger slot with deprecated `triggerClass`, disabled items, two-line items, error
  and checked tones without `!` overrides, item touch targets, z token, max width token). Verify: 2.14 passes, and the menu screenshots
  match for the existing items.
- [ ] 3.9 `refactor(ui)`: remove the `<style>` blocks of `MdLinearProgress` and `MdSnackbar` and their
  entries in the `add-ui-style-gate` baseline; use the `animate-md-linear-slide`, `animate-md-linear-slide-reduced`,
  `animate-md-snackbar-enter` and `animate-md-snackbar-leave` tokens that `refactor-md3-token-utilities` defines, and replace
  `z-[1100]` with `z-md-snackbar`; provide the snackbar queue through `provide` and `inject` with `App.vue` as the provider and
  the store as the default instance (proposal question 7). Verify: 2.15 and 2.16 pass, and the snackbar and progress screenshots match.
- [ ] 3.10 `refactor(ui)`: promote `DialogShell` to `src/components/md/MdDialog.vue` with the anchor `dialog-shell` on the frame, the
  sizes, the role, focus handling and the scrim utility; keep `src/components/identity/DialogShell.vue` as the re-export; update the
  registry `file` of `dialog-shell` (proposal question 1). Verify: 2.3 and 2.17 pass, and the identity dialog screenshots match.
- [ ] 3.11 `refactor(ui)`: add `MdDialogActions`. Verify: 2.18 passes.
- [ ] 3.12 `refactor(ui)`: add `MdAlert`, `MdStatePanel` and `MdSkeleton`. Verify: 2.19 passes.
- [ ] 3.13 `refactor(ui)`: add `MdPage`, `MdPageHeader` and `MdBreadcrumb`. `BreadcrumbNavigation` and `AdministrationFrame` are not
  changed in this change (their adoption waits for the follow-up). Verify: 2.20 passes.
- [ ] 3.14 `refactor(ui)`: add `MdSection`, `MdListDetail`, `MdList`, `MdListItem`, `MdDivider` and `MdKeyValueList`. Verify: 2.21 passes.
- [ ] 3.15 `refactor(ui)`: add `MdDataTable`, `MdTabs` and `MdDisclosure`. `CardMenu` keeps its tabs in this change. Verify: 2.22 passes.
- [ ] 3.16 `refactor(ui)`: add `MdBadge` and `MdAvatar`. Turn `tokens/TokenTag.vue`, `tokens/RepositoryTypeBadge.vue` and
  `repositories/TypeBadge.vue` into thin wrappers over `MdBadge` only when their before and after screenshots match (task 5.3); otherwise
  leave them as they are and list them as not done. Verify: 2.23 passes.
- [ ] 3.17 `refactor(ui)`: add `MdLink`, `MdCode`,
  `MdCodeBlock` and `MdCopyableCode` (over `components/card/copySnippet.js` as it stands), with `MdCode` and `MdCodeBlock`
  on the `md-code-medium` utility of `refactor-md3-token-utilities` (proposal question 4). Verify: 2.24 passes.
- [ ] 3.18 `refactor(ui)`: add `src/helpers/useCombobox.js` and `MdListbox`. `ComboboxField` keeps its markup in this change. Verify:
  2.25 passes.
- [ ] 3.19 `refactor(ui)`: move `tokens/SegmentedControl.vue` and `tokens/SegmentedOption.vue` to `md/MdSegmentedButton.vue` and
  `md/MdSegmentedOption.vue`; leave re-exports at the old paths; keep `data-ingot-component="segmented-control"` and
  `"segmented-option"` on the moved elements and update only the `file` of both entries in `src/anchors/registry.json`; add the shape token `corner-segmented` (20px) in `src/styles/md3/shape-elevation-motion.css`. Verify:
  2.26 passes, and the segmented control screenshots match.
- [ ] 3.20 `refactor(ui)`: rename the `CardMenu` emit to `select-tab` after the plugin check in task 5.6 finds no consumer (proposal
  question 2); if the check of task 5.6 (run before this group is committed) finds one, keep `selectTab` as an alias and mark the proposal BREAKING for plugin authors before the
  pull request is opened. Verify: 2.27 passes.

## 4. docs(ui): DESIGN.md catalogue

- [ ] 4.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, write the API convention (D2) as one section, with its rules and the names
  that are deprecated aliases and the release they leave in. Verify: the dash grep prints nothing.
- [ ] 4.2 `docs(ui)`: write one catalogue entry per primitive (purpose, props, slots, events, anchor, do and don't) for the primitives
  of sections 3.1 to 3.19, in the order of the groups. Verify: 2.1 passes for the entries written so far.
- [ ] 4.3 `docs(ui)`: replace the `Known gaps` list (DESIGN.md lines 714 to 719) with a pointer to the catalogue and the follow-up
  changes. Verify: 2.1 passes in full.

## 5. Check (no commit)

- [ ] 5.1 Run the unit suite through `heavy`: `npm test` in `reposilite-frontend`, with the anchor tests, the colour tests and the
  window margin test. Verify: all pass, and no test in `src/components/md/` waits by time.
- [ ] 5.2 Run the Playwright suites `accessibility.spec.js`, `navigation.spec.js` and `layout.spec.js` through `heavy` at 320 and 1280
  pixels. Verify: all pass, and the accessibility checks of the navigation, the fields and the sheet are green.
- [ ] 5.3 Before and after screenshots at 320, 600 and 1440 pixels, in light and dark, for every pixel-identical swap: the side sheet
  close control, the field primitives, the navigation bar and rail, the snackbar and progress, the identity dialog, the segmented
  control, and the badge wrappers if they are swapped. Take the before images on the base commit. Verify: every pair is identical;
  a pair that differs is reverted and listed in the pull request.
- [ ] 5.4 A screenshot of each primitive state in light and dark (Playwright, one spec, catalogue states only). Verify: the images
  are attached to the pull request.
- [ ] 5.5 Run `openspec validate add-md3-component-catalogue --strict` and `grep -rnP '\x{2014}|\x{2013}'` over the change directory and
  the changed DESIGN.md, the registry and the source files. Verify: the validation passes and the grep prints nothing.
- [ ] 5.6 Grep `reposilite-frontend/src` and the plugin extension points for `CardMenu` and `selectTab`. Verify: the result is recorded
  in the pull request, and it decides task 3.20.
- [ ] 5.7 Grep `reposilite-frontend/src/components/md/` for `<style` and for class-string props. Verify: no match.

## 6. Pull request (no commit)

- [ ] 6.1 Open the code pull request under the title `refactor(ui): add the md3 component catalogue`, from the remote main branch that
  already contains the merged proposal and the merged changes `refactor-md3-token-utilities` and `add-ui-style-gate`, with no file under `openspec/`. Fill the
  repository's pull request template, list the pixel-identical swaps that were done and the ones that were held back, attach the
  before and after screenshots and the catalogue screenshots, and state that nothing breaks.

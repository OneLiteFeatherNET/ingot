# Proposal

## Open maintainer questions

Each question has a default that this change is written against. The default is what the tasks implement unless the
maintainer picks the alternative before the code PR opens.

1. **Promote DialogShell in place or in a new file.** Default: promote in place. The file moves to
   `src/components/md/MdDialog.vue`, the registry entry `dialog-shell` keeps its name and stability and gets the new
   `file` path, and `src/components/identity/DialogShell.vue` stays as a re-export so the eleven importers need no edit
   in this change. Alternative: a new file `md/MdDialog.vue` with its own anchor `md-dialog`, and `DialogShell.vue` kept
   as the identity wrapper with its own `dialog-shell` element. The alternative adds a second dialog frame until the
   follow-up migration removes the wrapper.
2. **CardMenu emit rename.** Default: rename `selectTab` to `select-tab` with no alias. The only listener,
   `components/card/SnippetsCard.vue:218`, uses `@select-tab`, which Vue resolves for either spelling, so nothing in the
   dashboard changes. The apply step greps the plugin extension points for `CardMenu` before the rename; if a plugin can
   mount it, the old emit stays as an alias for one release and the proposal is marked BREAKING for plugin authors.
   Alternative: keep `selectTab` as an alias now.
3. **Icon family and the two close glyphs.** Default: one `MdIcon` registry that resolves the close action to the stroke
   glyph of `components/icons/CloseIcon.vue`, which `MdSideSheet.vue:138-146` already draws inline. That makes the sheet a
   pixel-identical swap. The filled close glyph in `MdSnackbar.vue:71-76` and `MdChipInput.vue:228-236` changes shape, so
   that swap is a visible change and is held back until the maintainer approves it. Until then both glyphs are registered
   under explicit names, and the registry says which surface uses which. Alternative: keep the Material filled family
   for every close glyph and move the sheet to it.
4. **Code type role.** Default: `MdCode` and `MdCodeBlock` use `md-code-medium`, the monospace utility that
   `refactor-md3-token-utilities` adds to `styles/md3/typography.css` (body-medium size and line height, 14 by 20px, the
   `--font-mono` stack, no webfont). This change adds no second code class. Alternative: use Tailwind's `font-mono`
   utility directly in `MdCode` and `MdCodeBlock`, with no type role.
5. **Radio focus ring in the non-card variant.** Default: `MdRadio` gets a `focus-ring` prop that is off by default, so
   no default render changes in this change. The default flips to on in the follow-up that adopts the component, because
   it is the one visible change this change would otherwise make, and it appears only on keyboard focus. Alternative: flip
   it on now and list it as the single visible exception with its own screenshots.
6. **Trailing icon inset of the select field.** Default: `MdFieldShell` places trailing icons 12px from the edge, the
   value DESIGN.md already documents, and `MdSelect` moves its chevron by 4px (today `right-4`, `MdSelect.vue:379`). That
   is a visible change, so `MdSelect` joins the shell only after the maintainer accepts the move. Alternative: keep 16px for
   every field and change DESIGN.md instead, which needs no visible change at all.
7. **Snackbar queue and the toast helpers.** Default: `MdSnackbar` injects its queue. `App.vue` provides the shared queue
   from `src/store/snackbar.js`, and the default is that same instance, so `src/helpers/toast.js` keeps working where no
   component is mounted. Tests provide their own queue. Alternative: move the toast helpers onto an injected queue as well,
   which needs every caller to run inside a component and is therefore out of this change.
8. **Catalogue screenshots.** Default: no Storybook or harness. Screenshots come from a Playwright spec that renders each
   primitive state in light and dark, and they are attached to the pull request. Alternative: add a catalogue route in the
   dashboard, which adds a route the operator sees and needs its own review.

## Why

The dashboard is built from primitives under `reposilite-frontend/src/components/md/`, and the views build their own
versions of the same parts beside them. The same bar, the same field chrome and the same empty state are written out again
in a dozen views, with small drifts in gap, radius, inset and focus. Each drift is a visual bug waiting for someone to
notice it, and two of the recently reported bugs (the breadcrumb on administration subpages and the title of a repository
detail page) come from a part that exists in two copies and does not share its layout.

The primitives themselves are not yet a catalogue. Their APIs disagree on the same ideas: a destructive flag is a Boolean
on one component and a variant on another, icons are named `icon` in one place and `leading-icon` in another, a toggle is
a string on the chip and a Boolean elsewhere, `MdOverflowMenu` takes a class string, and `MdSnackbar` holds its queue in
module state. DESIGN.md lists the gaps and says that views build their own tabs, lists and dialogs until a primitive
exists. Nothing in the file says what a primitive is for, what it takes, or what not to do with it, so a new contributor
copies the nearest view.

This change adds the missing primitives, extends the existing ones to cover the cases the views need, settles one API
convention, and writes each primitive into DESIGN.md as a catalogue entry. It does not move any view onto them. The views
adopt the primitives in three follow-up changes, so each of those changes can be reviewed against one set of parts.

## What Changes

- **One API convention, documented in DESIGN.md.** Values use `modelValue` with `update:modelValue`, except
  `MdSideSheet`, which keeps `v-model:open`. Emits are kebab-case. Destructive intent is the Boolean `error` prop, and the
  existing `variant="error"` stays as a deprecated alias. Icons go in the named slots `leading` and `trailing` everywhere,
  and `icon` stays as an alias of `leading` on `MdButton` and `MdChip`. A toggle is the Boolean `toggle` with `selected`,
  on every component that has one. Components take no class strings: `MdOverflowMenu`'s `triggerClass` becomes a `trigger`
  slot. No primitive keeps hidden module state: `MdSnackbar` takes its queue from `provide` and `inject`.
- **Extensions to existing primitives.** Every extension is opt-in and leaves today's default render unchanged. The full
  list is in `design.md` (decision D3). In short: `MdCard` gets tone, edge, padding, `as` and a header slot;
  `MdButton` gets `to`, an inverse tone and a flush-start text variant; `MdIconButton` gets a 24px compact size, `to` and
  an inverse tone; `MdChip` gets a trailing remove action; `MdRadio` gets a supporting-text slot and the focus ring option
  above; `MdSideSheet` gets `side`, a width token, token durations, a z token and a scrim token; `MdOverflowMenu` gets
  disabled and two-line items and a z token; `MdSelect` gets `readonly` and `width`; `MdTextField` gets `readonly`;
  `MdLinearProgress` and `MdSnackbar` lose their `<style>` blocks and their Transition class props.
- **One field family.** `MdFieldShell` draws the outlined and filled chrome, the floating label notch, the helper, error
  and counter line and the leading and trailing slots. `MdTextField`, `MdTextArea`, `MdSelect` and `MdChipInput` build on
  it. This removes the duplicated `shadow-[inset_...]` focus and error rules and the three different helper lines.
- **One navigation item.** `MdNavigationItem` carries the indicator, the label, the link or button, `aria-current` and the
  modified-click rule. `MdNavigationBar` and `MdNavigationRail` build on it with their widths. Both anchors stay.
- **Motion from tokens.** `MdLinearProgress`, `MdSnackbar` and the new `MdSkeleton` take their durations and easings from
  the motion tokens and respect `prefers-reduced-motion` with the `motion-reduce:` variant.
- **Segmented button.** `src/components/tokens/SegmentedControl.vue` and `SegmentedOption.vue` move to
  `md/MdSegmentedButton.vue` and `md/MdSegmentedOption.vue`. The old paths re-export the new files, the moved elements keep
  their anchor names `segmented-control` and `segmented-option`, and the 20px corner becomes a named shape token of the same value.
- **Shared control label.** `MdCheckbox`, `MdSwitch` and `MdRadio` read the gap between control and label from one
  shared label part. The default of each is today's value, so the option exists here and the values are changed by the
  adopting view changes.
- **New primitives, one file each under `md/`.** `MdDialog` (promoted from `DialogShell`, see question 1),
  `MdDialogActions`, `MdAlert`, `MdStatePanel`, `MdSkeleton`, `MdPage`, `MdPageHeader`, `MdBreadcrumb`, `MdSection`,
  `MdListDetail`, `MdList`, `MdListItem`, `MdDivider`, `MdKeyValueList`, `MdDataTable`, `MdTabs`, `MdDisclosure`,
  `MdBadge`, `MdAvatar`, `MdIcon`, `MdLink`, `MdCode`, `MdCodeBlock`, `MdCopyableCode`, `MdListbox`, and the composable
  `useCombobox` in `src/helpers/useCombobox.js`. Each has a unit test per behaviour, a registry entry and a catalogue
  entry.
- **Catalogue.** The `Known gaps` list in `reposilite-frontend/DESIGN.md` (lines 714 to 719) is replaced by one catalogue
  entry per primitive: purpose, props, slots, events, anchor, and do and don't.

### Pixel-identical adoptions

These swaps are allowed in this change because their rendering is the same before and after. Each is proven by the
before and after screenshots at 320, 600 and 1440 pixels in light and dark. A swap whose screenshots differ is held back and
listed in the pull request as not done.

- `MdSideSheet.vue:138-146` draws the close glyph inline. It becomes `MdIcon` with the same path.
- `MdNavigationBar` and `MdNavigationRail` build on `MdNavigationItem`.
- `MdTextField`, `MdTextArea`, `MdSelect` and `MdChipInput` build on `MdFieldShell`, with `MdSelect` held back by
  question 6.
- `SegmentedControl` and `SegmentedOption` move, and the moved code keeps its rendering.
- `TokenTag`, `RepositoryTypeBadge` and `repositories/TypeBadge` become thin wrappers of `MdBadge`, and their anchors stay.
  Their heights differ today (the search result pill is `h-6`), so this swap is held back for any tone or height that
  renders differently.

Nothing else is adopted. Views do not change in this change.

### Shipping

Proposal PR `docs(openspec): propose add-md3-component-catalogue`, with only the files under
`openspec/changes/add-md3-component-catalogue/`, tasks unchecked, branched from the remote main branch. It is merged before
the code PR is opened.

Code PR, under the type `refactor` and the scope `ui`, branched from the remote main branch that already contains the
merged proposal and the merged change `refactor-md3-token-utilities`:

`refactor(ui): add the md3 component catalogue`

The commits are `test(ui)` for the red tests, `refactor(ui)` for the primitives and their extensions, and `docs(ui)` for
DESIGN.md. The change is one refactor by its type: it adds primitives and extends them, and every default render stays the
same. No file under `openspec/` is in the code PR.

### Dependencies

- `refactor-md3-token-utilities` must be merged first. This change uses the token utilities that it defines (the
  typography classes, the state layer and focus classes, the motion and easing utilities) and the names it gives them.
  Any token this change needs beyond those is added to `styles/md3/shape-elevation-motion.css` or
  `styles/md3/typography.css` in this change and listed in `design.md`.
- `add-ui-style-gate` merges before this change (order: `refactor-md3-token-utilities`, `add-ui-style-gate`, this
  change). This change removes the baseline entries it fixes in the same pull request, among them the `<style>` blocks of
  `MdLinearProgress.vue` and `MdSnackbar.vue`, and adds no entry.
- `migrate-content-to-components`, `fix-surface-hierarchy-and-page-header` and `unify-dialogs-menus-and-feedback` depend on
  this change. They adopt the primitives and set the values this change leaves at their defaults.

### Not BREAKING

Nothing breaks. No operator changes a configuration key, an environment variable, a deployment manifest, a volume, a route
or stored data. Plugin authors change nothing: the anchor names of every registered component stay on their elements, including
the moved segmented controls, and the one emit rename (question 2) has no plugin consumer, which the apply step
checks before the rename lands. If that check finds a consumer, the change is marked BREAKING for plugin authors and the
alias is kept for one release.

The former `triggerClass` prop of `MdOverflowMenu`, the `variant="error"` value of `MdButton` and the `icon` slot of
`MdButton` and `MdChip` are kept as deprecated aliases for one release, so no caller breaks when this lands.

## Out of scope

- Moving any view, card, page or dialog onto the new primitives. That is the follow-up changes named above.
- Changing surface roles, the card tone a screen uses, or the geometry of an existing dialog. `fix-surface-hierarchy-and-page-header`
  decides the roles, and `unify-dialogs-menus-and-feedback` decides the dialog geometry.
- Changing the anchors of existing components, or adding parts to any component that has none.
- The style gate (`add-ui-style-gate`) and the token utilities (`refactor-md3-token-utilities`).
- The `<style>` blocks outside `src/components/md/`: `FileUpload.vue`, `SnippetsCard.vue`, `SettingsView.vue`,
  `ObjectRenderer.vue` and `OptionalRenderer.vue` keep theirs until the follow-up changes or the gate handle them.
- The copy helper `src/components/card/copySnippet.js`. `MdCopyableCode` uses it as it is.
- A Storybook or any second rendering harness (question 8).
- Changing `src/store/snackbar.js` beyond what question 7 needs.

## Capabilities

### New Capabilities

- `dashboard-component-catalogue`: the rules every dashboard primitive follows (Tailwind and MD3 tokens only, the shared API
  convention, anchor registration, a catalogue entry, a unit test per behaviour) and the contract of each primitive in the
  catalogue.

## Impact

- **Operators:** nothing to change. The dashboard looks the same at 320, 600 and 1440 pixels in light and dark.
- **Plugin authors:** nothing to change (see Not BREAKING). Anchor names do not change, and the new primitives are new
  names that plugins may use.
- **Code:** `reposilite-frontend/src/components/md/` (new files and extensions), `src/components/identity/DialogShell.vue`
  (re-export), `src/components/tokens/SegmentedControl.vue` and `SegmentedOption.vue` (re-export), `src/components/card/CardMenu.vue`
  (emit and trigger slot), `src/components/md/MdSnackbar.vue` and `src/App.vue` (queue injection), `src/helpers/useCombobox.js`
  (new), `src/anchors/registry.json` (additions and the moved file paths of `dialog-shell`, `segmented-control` and `segmented-option`), and
  `src/styles/md3/` (the corner token and any token listed in `design.md`; the code role comes from
  `refactor-md3-token-utilities`).
  No file under `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md` (the catalogue, the API convention and the replaced Known gaps list).
  No page under `docs/gitbook/` changes, because no configuration key, environment variable or manifest changes.

## Verification

- Every red test in `tasks.md` is seen to fail before its production code exists.
- `npm test` in `reposilite-frontend` passes, and the anchor tests in `src/anchors/anchors.test.js` pass with the new
  registry entries.
- Before and after screenshots at 320, 600 and 1440 pixels, in light and dark, of every pixel-identical adoption. The
  pull request lists each pair, and any difference holds that swap back.
- A screenshot of every primitive state in light and dark, attached to the pull request (question 8).
- The Playwright suites `accessibility.spec.js`, `navigation.spec.js` and `layout.spec.js` pass at 320 and 1280 pixels.
- `openspec validate add-md3-component-catalogue --strict` passes, and the dash grep prints nothing.

## Open questions

The questions are at the top of this file, under `## Open maintainer questions`.

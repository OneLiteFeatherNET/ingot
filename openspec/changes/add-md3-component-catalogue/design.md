# Design

## Context

Measured on the base of this change, in `reposilite-frontend/src`. Line numbers are as they stand on the base and may move
when `refactor-md3-token-utilities` lands; the tasks name the symbols, not only the lines.

- `components/md/` holds 17 primitives: MdButton, MdIconButton, MdCard, MdChip, MdChipInput, MdTextField, MdTextArea,
  MdSelect, MdCheckbox, MdRadio, MdSwitch, MdNavigationBar, MdNavigationRail, MdSideSheet, MdOverflowMenu,
  MdLinearProgress and MdSnackbar. Each has a `.test.js` next to it.
- `<style>` blocks inside `md/`: `MdLinearProgress.vue:68` (the indeterminate keyframes) and `MdSnackbar.vue:87` (the
  enter and leave transition). Outside `md/`, `FileUpload.vue`, `SnippetsCard.vue`, `SettingsView.vue`,
  `ObjectRenderer.vue` and `OptionalRenderer.vue` have one each. They are out of scope (proposal).
- Four API splits, verified:
  - Destructive intent: `MdButton.vue:46` is a Boolean `error` and `MdButton.vue:32` also accepts `variant` `error`.
  - Icon slots: `MdButton.vue:114` and `MdChip.vue:106` use the slot `icon`. `MdTextField` uses `leading-icon` and
    `trailing-icon`, and `MdSelect` has its icon in the template only.
  - Toggle: `MdChip.vue:46-49` takes the string `toggle` (`checkbox` or `button`), and `MdIconButton` takes the Boolean
    `toggle` with `selected`.
  - Class strings: `MdOverflowMenu.vue:55` takes `triggerClass` and binds it at `:188`.
  - Hidden state: `MdSnackbar.vue:32-37` defaults its `queue` prop to the module queue of `src/store/snackbar.js`, which
    `App.vue:59` mounts without arguments.
- Two surfaces of drift in the field family: the focus and error shadows are written out in `MdTextField.vue:110-115`,
  `MdTextArea.vue:132-133`, `MdSelect.vue:128-129` and `MdChipInput.vue:167-168`. The helper line is `MdSelect.vue:392-397`
  and the field root is `MdTextArea.vue:158`, with `min-w-0` only there. The trailing icon sits at `right-4` in
  `MdSelect.vue:379` and at `right-3` in `MdTextField.vue:214`, and DESIGN.md documents 12px.
- The navigation pair repeats the modified-click rule: `MdNavigationBar.vue:51` and `MdNavigationRail.vue:92`, and
  `MdOverflowMenu.vue:138` has the same test for its items.
- The gaps between control and label: `MdCheckbox.vue:111` uses `gap-2`, `MdSwitch.vue:116` uses `gap-3`, and the
  radio uses `gap-2` in its plain variant and `gap-3` in its card variant (`MdRadio.vue:124-125`).
- Arbitrary values in primitives that the tokens do not yet name: `MdSideSheet.vue:106` and `:108` (`duration-200`,
  `duration-150`), `:118` (`z-50`), `:122` (`color-mix` of the scrim). `MdOverflowMenu.vue:206` has `z-50` and
  `max-w-[calc(100vw-2rem)]`. `MdOverflowMenu.vue:231`, `:242` and `:252` override with `!text-error` and `!bg-...`.
- `MdSideSheet.vue:138-146` draws the close glyph inline, and its path is the one in `components/icons/CloseIcon.vue:30`.
  `MdSnackbar.vue:71-76` and `MdChipInput.vue:228-236` draw a Material filled close glyph. The check glyph exists as three
  different path strings: `tokens/SegmentedOption.vue:65`, `MdSelect.vue:429` and `dashboard/GettingStartedCard.vue:175`.
- Surfaces: `MdCard.vue:41` fixes `rounded-corner-medium p-4`. Its `filled` variant is `bg-surface-container-highest`,
  while DESIGN.md names `surface-container-low` for cards and panels. The role mapping is settled by
  `fix-surface-hierarchy-and-page-header`. This change adds the mechanism only.
- Duplicated domain parts, which the catalogue must absorb later without changing their anchors:
  - Type badges: `tokens/TokenTag.vue:32-33` (`secondary-container` or `tertiary-container`), `tokens/RepositoryTypeBadge.vue:40-43`
    and `repositories/TypeBadge.vue:32-35`. The four repository-type tones are already role colours, so they map onto
    `MdBadge` without a new colour. The search pill in `search/SearchResultRow.vue:108` is `h-6`. The heights of the
    other badges are not yet compared.
  - Dialog frame: `components/identity/DialogShell.vue` (11 importers) draws `max-w-140` (560px), `min-w-70` (280px),
    `bg-surface-container-high`, and an inline `color-mix` overlay style. The anchor `dialog-shell` sits on its frame.
  - Combobox: `components/access/check/ComboboxField.vue` has `role="combobox"`, `aria-activedescendant` and a `role="listbox"`
    of options (`:151-174`), and its keyboard logic is in `:74-130`. Its consumers are `access/nodes/NodeAddField.vue`,
    `access/TeamParentsCard.vue` and `access/nodes/NodeTemplateMenu.vue`.
  - Tabs: `components/card/CardMenu.vue` (73 lines) draws its tabs with `aria-current`, not with the tab roles, and emits
    `selectTab` at `:28` and `:34`. Its one consumer is `card/SnippetsCard.vue:216-218`, which listens with `@select-tab`.
  - Breadcrumb: `components/browser/BreadcrumbNavigation.vue` (anchor `breadcrumb-navigation` at `:53`) and
    `components/layout/AdministrationFrame.vue` (anchor `administration-frame` at `:105`).
  - Avatar: `components/header/MenuPanel.vue:90-92` draws the initial in a `size-8` circle, with `aria-hidden`.
  - Close and icon buttons: `dashboard/StatTile.vue:101` draws an inline `size-6` icon button.
  - Segmented control: `components/tokens/SegmentedControl.vue:63` uses `rounded-[20px]`, which no shape token names.
  - Dialog dividers and copy: `components/header/LoginModal.vue:441` draws the "or" divider with `data-test="divider"`.
    `components/card/copySnippet.js` holds the copy helper that `MdCopyableCode` will call.
- `src/anchors/registry.json` holds 184 component entries with the fields `name`, `file`, `stability`, `parts`, `states` and
  `ownElement`, and a top-level `aliases` list whose entries have `name` and `replacedBy`. `src/anchors/checks.js` enforces a
  closed set of seven `data-ingot-*` attribute names (among them `data-ingot-component`, `data-ingot-component-alias`,
  `data-ingot-part` and `data-ingot-state`), rejects an alias that no entry declares, and rejects a `data-ingot-component`
  value that is not the registered name. Renderers `md-select-renderer`, `md-text-field-renderer` and `md-text-area-renderer` are `stable` and do not own an
  element (`ownElement: false`), so the field primitives must keep the renderers working unchanged.
- `src/helpers/treeFits.js` and `src/helpers/toast.js` show that composable-style logic lives in `src/helpers/`. The new
  `useCombobox` goes there as well.
- DESIGN.md: "Known gaps" starts at line 714. Its list says that views build their own tabs, lists and dialogs. The
  catalogue replaces it.

## Goals

- One primitive for each part that two or more views build by hand, and no part that exists in two copies.
- One API convention, written down, so that a primitive reads the same as every other.
- Every primitive is a Tailwind utility over the MD3 tokens. No `<style>` block, no class string, no raw colour, no magic
  number that the tokens do not name.
- A catalogue entry per primitive in DESIGN.md, so that a view can be built from the file and nothing else.
- No visible change in this change, proven by screenshots for every swap that claims to be pixel-identical.

## Non-goals

- Migrating views (follow-up changes).
- Changing surface roles, the card tone a screen uses or the geometry of an existing dialog.
- Anchor renames. A moved component keeps its existing `data-ingot-component` value; no name becomes an alias.
- A second rendering harness. The catalogue screenshots come from Playwright (proposal, question 8).

## Decisions

### D1. Primitive inventory

New files, each under `reposilite-frontend/src/components/md/`, each with a `.test.js` next to it, a registry entry and a
catalogue entry:

| Primitive | Replaces or serves | Anchor |
| --- | --- | --- |
| `MdDialog` | `identity/DialogShell.vue` (promoted, D6) | `dialog-shell` (kept) |
| `MdDialogActions` | the action rows of the identity and settings dialogs | `md-dialog-actions` |
| `MdAlert` | the inline notices of the views | `md-alert` |
| `MdStatePanel` | empty, loading and error with Retry (about 6 and 10 copies, counted for the follow-up) | `md-state-panel` |
| `MdSkeleton` | loading placeholders | `md-skeleton` |
| `MdPage` | the page container and its gutter | `md-page` |
| `MdPageHeader` | the page header (h1, description, actions, trail, back action) | `md-page-header` |
| `MdBreadcrumb` | `BreadcrumbNavigation.vue` and `AdministrationFrame.vue` | `md-breadcrumb` |
| `MdSection` | a titled section with actions | `md-section` |
| `MdListDetail` | list and detail with one inset | `md-list-detail` |
| `MdList`, `MdListItem` | the lists of the views | `md-list`, `md-list-item` |
| `MdDivider` | horizontal dividers, labelled or not | `md-divider` |
| `MdKeyValueList` | rows of label and value | `md-key-value-list` |
| `MdDataTable` | table with caption, sortable header and column priority | `md-data-table` |
| `MdTabs` | tab strip (replaces `CardMenu`'s tabs later, keeps `card-menu`) | `md-tabs` |
| `MdDisclosure` | summary and details with chevron | `md-disclosure` |
| `MdBadge` | badges and pills (D9) | `md-badge` |
| `MdAvatar` | the account initial | `md-avatar` |
| `MdIcon` | every glyph, from one registry (D10) | `md-icon` |
| `MdLink` | inline router-aware links | `md-link` |
| `MdCode`, `MdCodeBlock`, `MdCopyableCode` | inline code, block code and block code with copy | `md-code`, `md-code-block`, `md-copyable-code` |
| `MdListbox` | options of a combobox | `md-listbox` |
| `MdFieldShell` | the chrome of the four field primitives (D4) | `md-field-shell` |
| `MdNavigationItem` | the item of the bar and the rail (D5) | `md-navigation-item` |
| `MdSegmentedButton`, `MdSegmentedOption` | moved from `tokens/` (D8) | `md-segmented-button`, `md-segmented-option` |

The composable `useCombobox` lives in `src/helpers/useCombobox.js`. It is a pure state machine over the input and the
options, and it has no DOM of its own, so its test needs no mount.

Names follow the existing registry pattern: `md-` for primitives in `md/`, and the existing name where a component is
moved (`dialog-shell`, D6, and the two segmented names, D8).

### D2. The API convention

This is the rule set that DESIGN.md writes down. Each row is one rule and the primitive that follows it first.

| Rule | Applies to | Current split this removes |
| --- | --- | --- |
| Values use `modelValue` and `update:modelValue`. A panel's open state uses `open` and `update:open`, so `MdSideSheet` keeps `v-model:open` | every value control | none; `MdSideSheet` keeps its name |
| Emits are kebab-case, so `select-tab` | every component | `CardMenu` emits `selectTab` (proposal question 2) |
| Destructive intent is the Boolean `error` | `MdButton`, `MdIconButton` | `variant="error"` on `MdButton` (deprecated alias for one release) |
| Icons go in the slots `leading` and `trailing` | `MdButton`, `MdChip`, `MdTextField`, `MdSelect`, `MdChipInput`, `MdListItem` | `icon` on `MdButton` and `MdChip` (alias of `leading` for one release), `leading-icon` and `trailing-icon` on the field |
| A toggle is the Boolean `toggle` with `selected` | `MdChip`, `MdIconButton` | `MdChip` `toggle` as the string `checkbox` or `button` |
| Components take no class strings. A class goes on the root through `class`, and a changed part of a component is a slot | `MdOverflowMenu` | `triggerClass` becomes the slot `trigger` (alias for one release) |
| No component keeps module state. The state comes from `provide` and `inject`, and the default is provided by the application | `MdSnackbar` | the queue is a module default; `App.vue` provides it (proposal question 7) |
| Attributes that are not props go to the native element, and `class` and `style` stay on the root | every component that wraps a native element | none; the existing rule of `MdCheckbox`, `MdRadio` and `MdSwitch`, now written down |

### D3. Extensions to existing primitives

Every extension is opt-in. Its default reproduces the current render, which the screenshots check.

- `MdCard`: `tone` (`low`, `container`, `high`, `highest`), `edge` (`none`, `outline`, `elevation`), `padding`
  (`sm`, `md`, `lg`; `md` is today's 16px), `as` (`div`, `section`, `li`, `fieldset`), and a `header` slot with `title`,
  `supporting` and `trailing`. A `danger` tone draws `border-error`, the form used at `EntryDetail.vue:472` and
  `SettingsView.vue:618`. The default of `tone` stays `highest`, today's `filled` surface, until the surface change decides it.
- `MdButton`: the prop `to` (router-aware: it renders `RouterLink` and calls `navigate`), an `inverse` tone for a button on
  an inverse surface (the snackbar action, `MdSnackbar.vue:60`), and a flush-start text variant that removes the leading
  inset, which replaces the negative margins `FailuresSheet.vue:234` (`-ml-3`) and `UnsavedChangesBar.vue:58` (`-mx-2`).
- `MdIconButton`: `size="compact"` (24px visible, the hit area stays 48px, as in `touch-target.css`), used at
  `StatTile.vue:101`. `to` as on `MdButton`. An `inverse` tone for the snackbar's close control (`MdSnackbar.vue:68`).
- `MdChip`: a trailing `remove` action, a button in the trailing slot with its own `aria-label`, for the input chips of
  `MdChipInput.vue:217-236` and `TeamParentsCard.vue`. The static read-only chip is `MdBadge` (D9), not an assist chip.
- `MdRadio`: a `supporting` slot under the label (replaces the `ml-12` of `repositories/WritePolicyField.vue:79`), and a
  `focus-ring` prop that is off by default (proposal question 5). The card variant already draws its ring.
- `MdSideSheet`: `side` (`start` or `end`), a `width` from tokens: `--container-md-sheet` (320px, from
  `refactor-md3-token-utilities`) capped at the window less 56px, as DESIGN.md states, and `--container-md-sheet-wide` (28rem,
  added here in `shape-elevation-motion.css`) for the trailing sheets that `FailuresSheet.vue` and `SettingsReviewSheet.vue`
  draw at 28rem today, the
  durations as motion tokens (the enter uses `short4`, 200ms, and the leave uses `short3`, 150ms, the two values the
  component uses today at `MdSideSheet.vue:106` and `:108`; the token names are those of `shape-elevation-motion.css`), a `z`
  token (the dialog stacking value, as `MdSideSheet.vue:118` uses `z-50` today), and a `scrim` token that replaces the
  `color-mix` of `MdSideSheet.vue:122`. The inline close glyph becomes `MdIcon` (D10).
- `MdOverflowMenu`: `disabled` items, two-line items (`label` and `supporting`), an `error` item tone without `!` overrides
  (`:231`, `:252`), a `checked` tone without `!` overrides (`:242`), touch targets on items (`itemClass` at `:175`), a z token
  and a max width token replacing `z-50` and `max-w-[calc(...)]` at `:206`. The trigger slot replaces `triggerClass`.
- `MdSelect`: `readonly`, and `width` (a width prop with the default of the field; `SearchView.vue:395` uses `w-44!` today).
- `MdTextField`: `readonly`, for the value the secret dialog shows (`identity/SecretOnceDialog.vue:120` draws its own input).
- `MdLinearProgress`, `MdSnackbar`: the `<style>` blocks go. The keyframes are the `--animate-md-linear-slide`,
  `--animate-md-linear-slide-reduced`, `--animate-md-snackbar-enter` and `--animate-md-snackbar-leave` tokens that
  `refactor-md3-token-utilities` defines, `z-[1100]` becomes `z-md-snackbar`, and the transition classes move to the `motion-safe:` and `motion-reduce:` variants
  that the component already uses inline. The durations come from the motion tokens.

### D4. The field family: MdFieldShell

`MdFieldShell` owns the chrome that the four field primitives repeat:

- the container: `outlined` or `filled`, the shadows of the focus and error states as one token-based rule, the disabled
  colours at 4%, 12% and 38% as DESIGN.md sets them;
- the floating label and its notch, with the peer placeholder rule that `MdTextField` documents;
- one helper line, `supporting` or `error`, with its id for `aria-describedby`, and the counter when the field has one;
- the slots `leading` and `trailing`, at one inset, 12px from the edge (proposal question 6), and a root with `min-w-0`.

`MdTextField`, `MdTextArea`, `MdSelect` and `MdChipInput` build on it. The renderers `md-text-field-renderer`,
`md-text-area-renderer` and `md-select-renderer` keep working unchanged, because they pass the same props. The rule that
the `Select` trailing icon moves 4px is the one visible change the shell would make, so `MdSelect` joins the shell only
after the maintainer accepts it (proposal question 6). The other three may join when their screenshots match.

### D5. The navigation item

`MdNavigationItem` carries the indicator (the pill), the label, the link or button, `aria-current` and the modified-click
helper of `MdNavigationBar.vue:51` and `MdNavigationRail.vue:92`. The helper moves to `src/helpers/` as a pure function of
the event, so `MdOverflowMenu.vue:138` can use it too. `MdNavigationBar` (80px bar, 64 by 32 pill) and `MdNavigationRail`
(96px rail, 56 by 32 pill) pass their widths in. Both keep their anchors and their `aria-current` contract, and both are
swapped only when their screenshots match.

### D6. The dialog: MdDialog

`MdDialog` is the promoted `DialogShell`. Its frame keeps the anchor `dialog-shell` on the same element, which is the
rule the anchors need. The frame keeps its current geometry as the default (the 280px floor, the 560px cap, the 24px side
inset, `bg-surface-container-high`), and the size prop adds `sm` and `md` below it:

- `size`: `sm` (360px), `md` (480px), `lg` (560px, the default). All sizes are clamped to the 280px floor and the window.
- `labelledby` (required, as today) and `describedby` (optional), `role` (`dialog` or `alertdialog`, default `dialog`).
- `dismissable`, as today. A dialog that must be answered sets it to false.
- The overlay: the inline `color-mix` style of `DialogShell.vue` becomes a `scrim` utility on the modal's overlay class,
  so the component has no inline style.
- Focus: the focus moves to the first focusable element on open (or to an element with `data-autofocus`), Tab and Shift+Tab
  stay inside, and focus returns to the element that opened the dialog on close. This is the contract that `MdSideSheet`
  already keeps, and the test for it is written first.
- The slots `header` (title and description), `default` (the body) and `actions` (the buttons, via `MdDialogActions`).

`MdDialogActions` is end-aligned, wraps on narrow widths and uses one gap token. `components/identity/DialogShell.vue`
becomes `export { default } from '../md/MdDialog.vue'`, so the eleven importers keep working.

### D7. Feedback and state

- `MdAlert`: tones `error`, `warning`, `tertiary` and `info`. `role="alert"` for `error` and `role="status"` for the rest, the
  same split that `MdSnackbar` uses. A title, a body and an optional action slot. Warning and success colours are text
  colours only (DESIGN.md), so the tone uses a role background for the container and a text colour for the icon.
- `MdStatePanel`: `empty`, `loading` and `error` states with one type role and an inset, and a `Retry` action on `error`.
  `loading` renders `MdSkeleton` lines, not a spinner, and the panel carries `aria-busy="true"` while it loads.
- `MdSkeleton`: `bar` and `circle` shapes with width presets (`sm`, `md`, `full`). It uses the skeleton animation token, and
  under `prefers-reduced-motion` it shows a static block.
- `MdSnackbar`: the queue is injected (D2, proposal question 7). Its close glyph becomes `MdIcon` with the decision of
  proposal question 3, and its `<style>` block goes (D3).

### D8. Segmented button

`src/components/tokens/SegmentedControl.vue` and `SegmentedOption.vue` move to `md/MdSegmentedButton.vue` and
`md/MdSegmentedOption.vue`. Their rendering does not change, so the move is one of the pixel-identical adoptions.

- The old paths become `export { default } from ...` re-exports, so the importers keep working.
- The registry entries `segmented-control` and `segmented-option` keep their names, stability and states; only their
  `file` moves to the new paths. The moved elements keep `data-ingot-component="segmented-control"` and
  `data-ingot-component="segmented-option"`, so an existing plugin selector keeps matching. No alias is declared, because
  an alias would move the old value to `data-ingot-component-alias` and a selector on `data-ingot-component` would stop
  matching.
- `rounded-[20px]` at `SegmentedControl.vue:63` becomes the shape token `--md-sys-shape-corner-segmented` at 20px, which is
  added to `styles/md3/shape-elevation-motion.css` with the utility `rounded-corner-segmented`. The value is the same, so
  the rendering is the same. The token is named for its one use, because MD3 names no 20px corner.

### D9. Badges

`MdBadge` has the tones `neutral` (`border border-outline text-on-surface-variant`), `accent` (`secondary-container`),
`success` and `error` (`text-success` and the error roles), and the four repository-type tones `hosted`
(`tertiary-container`), `proxy` (`secondary-container`), `group` (`primary-container`) and `mixed` (outlined). Those are the
classes of `TokenTag.vue:32-33`, `RepositoryTypeBadge.vue:40-43` and `TypeBadge.vue:32-35`, so no new colour is needed.

The badge has one height, `h-6` (24px, the height of the search pill), and one type role, `md-label-medium`. `TokenTag`,
`RepositoryTypeBadge` and `repositories/TypeBadge` keep their files as thin wrappers over `MdBadge`, so their anchors
`token-tag`, `repository-type-badge` and `type-badge` stay on the rendered element. The wrappers are swapped only when a
before and after screenshot of each tone and each of the three wrappers matches. A tone or height that differs is listed in
the pull request and waits.

`MdBadge` is the read-only chip. The static form `MdChip` used for the same purpose is not kept as a second answer.

### D10. Icons

`MdIcon` takes either a `path` (the `d` string of a 24 by 24 viewBox) or a `name` from one registry, a `size` (`sm` 16px,
`md` 20px, `lg` 24px, the default `lg`, because the existing icon slots are 24px), and renders with `aria-hidden="true"`
unless it has a `label`. The registry is one
module, `src/components/md/icons.js`, with the check, the close (stroke, from `CloseIcon.vue`) and the other glyphs in use.
Two glyphs are not decided here: the close family and the check path string (proposal question 3). The registry names each
glyph by its surface until the decision is taken, and it does not duplicate a glyph under two names.

The glyph module `components/browser/glyphs.js` is a second glyph set. It is audited during apply, and its glyphs move into
the registry only when they match an existing path exactly. Otherwise they stay where they are, and the catalogue says so.

### D11. Links, code and listbox

- `MdLink`: router-aware, one colour role (`text-primary`), a focus ring and a state layer, and the external cue:
  `rel="noopener noreferrer"` and `target="_blank"` with a visible icon and a screen-reader label, only when `external` is set.
- `MdCode`: inline code in the monospace utility `md-code-medium` from `refactor-md3-token-utilities` (proposal question 4), with a surface role.
- `MdCodeBlock`: block code in the same role, with horizontal scroll inside its own box and no page scroll.
- `MdCopyableCode`: `MdCodeBlock` with a copy `MdIconButton` (`aria-label` required) and a status line that the copy
  helper's result sets. It uses `components/card/copySnippet.js` as it stands, so the helper's tests keep covering it.
- `MdListbox` and `useCombobox`: the composable holds the active index, the open state, the filtering and the keyboard
  logic that `ComboboxField.vue:74-130` has, and returns the props that the input and the listbox bind. `MdListbox` renders
  `role="listbox"` and the options with `role="option"` and `aria-selected`. The field and the input stay in the
  consumer until `MdFieldShell` is adopted, so the combobox keeps its current markup and keyboard behaviour.

### D12. Layout and data primitives

- `MdPage`: the page container with the gutter that `container` gives (16px below 600px, 24px from 600px, as DESIGN.md
  states), a `measure` option for the reading measure (a max width token), and `centred`. It does not add `px-*`, so the
  gutter rule that `window-margins.test.js` checks still holds.
- `MdPageHeader`: `h1` title, description, a `trail` slot above the title, an `actions` slot on the right, and a `back`
  slot outside the text column. The title column has `min-w-0`, so a long title wraps inside it and never sits left of the
  content edge. The repository detail title, reported as too far left, is built from this part.
- `MdBreadcrumb`: `<nav>` with an `<ol>`, a real separator element with `aria-hidden="true"` between the crumbs, `aria-current="page"` on
  the last crumb, a 48px hit area through `md-touch-target`, and a truncation of the middle crumbs below a width token. It
  serves both `BreadcrumbNavigation` and `AdministrationFrame`, and the anchor `breadcrumb-navigation` stays on the file
  browser element (its wrapper), not on the primitive.
- `MdSection`: a heading at `level` 2 or 3, a `description`, an `actions` slot and one gap token.
- `MdListDetail`: a `list` slot and a `detail` slot, with the expanded breakpoint as a prop. The inset of the two columns is
  one token at every width.
- `MdList` and `MdListItem`: items are links, buttons or selectable options; slots `leading` and `trailing`; `divided` with an
  inset of 16px or 72px (the 72px inset clears a leading avatar).
- `MdDivider`: horizontal, with an `inset` (none, 16, 72) and an optional label that sits in the rule, used for the "or"
  divider at `LoginModal.vue:441`.
- `MdKeyValueList`: rows from `items` or from slots, with one grid template and one gap.
- `MdDataTable`: a `caption`, the first column as the row header (`th scope="row"`), a sortable header with `aria-sort`, a
  `priority` per column for the breakpoint at which it hides, a sticky header on the surface role of the table, and one cell
  padding scale. It does not replace `RepositoriesTable`, which keeps its anchor and its parts.
- `MdTabs`: `tablist`, `tab` with `aria-selected`, an indicator, and a scroll for narrow widths. It replaces `CardMenu`'s
  tabs later, and `CardMenu` keeps its anchor `card-menu`.
- `MdDisclosure`: a `summary` with the state layer, the focus ring and a chevron that rotates with `motion` tokens.
- `MdAvatar`: the initial in a circle (`size` `sm`, `md`), `aria-hidden` by default, with a `label` prop that names it for a
  screen reader when it is the only content.

## Anchors and the registry

- The registry changes are additions, plus three things that the rules name explicitly: the file path of `dialog-shell`
  moves with its component (D6), and the file paths of the two segmented entries move with theirs (D8). No name, `stability` or `states` value of
  an existing entry changes. A new entry starts as `experimental`, as the rest of the `md-*` entries do.
- Every new entry lists the `states` it uses (`loading`, `empty`, `error`, `open`, `current`) from the closed set in `checks.js`.
  Parts are listed only where the component has a stable part, so `MdDataTable` can list `header` and `row` once the tests
  name them.
- A moved component keeps its existing `data-ingot-component` value. `data-ingot-component-alias` is not used by this change.
- `npm test` runs `src/anchors/anchors.test.js`, which runs the static checks over every file in the registry.

## Test plan

Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, a fresh mount per test, the
provider for the snackbar queue created per test, and fake timers only where a timer is the behaviour (the snackbar's own
queue already takes injected timers). Each file has one behaviour per test, named after it.

- Unit tests per primitive: `@vue/test-utils` mounts, with the props, slots and events of the catalogue entry, one test per
  behaviour, and the anchor attribute asserted on the root.
- Pure helpers: the modified-click helper, `useCombobox` and the width logic of `MdSideSheet` are tested with plain input and
  output.
- The API convention has its own test: a file-level test that reads each primitive's props and asserts that no class-string
  prop is declared, that every icon slot is `leading` or `trailing` (with `icon` allowed only where the alias is listed), and
  that every emit is kebab-case.
- Screenshots: a Playwright spec at 320, 600 and 1440 pixels in light and dark, for the pixel-identical adoptions (before and
  after) and for each primitive state of the catalogue. The before images are taken on the base commit of the code PR.

## Visible change and screenshots

The change claims no visible change. That claim is checked, not assumed:

- Before and after images at 320, 600 and 1440 pixels, in light and dark, for every swap in the proposal's pixel-identical
  list. The pair is the same file name in two folders, and a difference of any pixel holds the swap back.
- The catalogue screenshots (one per primitive state, light and dark) are attached to the pull request, so a reviewer sees
  the new parts without running them.
- The only visible exceptions are the ones the proposal's open questions name, and they are held back unless the maintainer
  approves them.

## Risks

- A catalogue entry that is written but not kept in step with the component. The catalogue test reads DESIGN.md and checks
  that each registry entry in `md/` has a heading there.
- The field shell changes the markup of four primitives. The renderers and the e2e tests read the fields by role and label,
  and that is what the tests keep.
- The segmented move could drop an anchor. The anchor test asserts that `segmented-control` and `segmented-option` are still
  the `data-ingot-component` values of the moved files, and the e2e accessibility suite selects them by those values.
- A plugin that mounts `CardMenu` could observe the emit rename. The apply step checks for that before the rename lands (proposal question 2).

## Migration plan

None for operators. A rollback is a revert of the code PR, and the anchors stay the same after the revert.

## Open questions

The open questions are in `proposal.md`, under `## Open maintainer questions`.

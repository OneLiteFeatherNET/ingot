# Spec Delta

## Purpose

The dashboard is built from one catalogue of primitives under `reposilite-frontend/src/components/md/`. Each primitive is
styled with Tailwind utilities over the MD3 tokens, follows one API convention, carries a registered anchor and has a
catalogue entry in `reposilite-frontend/DESIGN.md`. Views build from these primitives and add no parts of their own for the
cases the catalogue covers.

## ADDED Requirements

### Requirement: Primitives are Tailwind and token styled

Every primitive under `src/components/md/` SHALL be styled with Tailwind utilities and the MD3 tokens only. A primitive SHALL NOT
contain a `<style>` block, SHALL NOT accept a class string as a prop, and SHALL NOT use a raw colour or a magic number that the
tokens do not name. A motion value SHALL come from a motion token, and a stacking value SHALL come from a z token.

#### Scenario: Progress bar without a style block

- **WHEN** `MdLinearProgress.vue` is read
- **THEN** the file contains no `<style>` element, and its indeterminate animation is a theme animation utility

#### Scenario: Snackbar without a style block

- **WHEN** `MdSnackbar.vue` is read
- **THEN** the file contains no `<style>` element, and its transitions are the motion variants of the component

#### Scenario: Overflow menu without a class prop

- **WHEN** `MdOverflowMenu` is mounted with a `trigger` slot
- **THEN** the trigger renders the slot content, and no prop of the component accepts a class string

#### Scenario: Side sheet durations from tokens

- **WHEN** `MdSideSheet` is read
- **THEN** its enter and leave durations are motion tokens, and its scrim is a token rather than an inline `color-mix`

### Requirement: One API convention

Every primitive SHALL follow the API convention that DESIGN.md states: values use `modelValue` with `update:modelValue`,
except that a panel's open state uses `open` with `update:open`; emits are kebab-case; destructive intent is the Boolean
`error` prop; icons are the named slots `leading` and `trailing`; and a toggle is the Boolean `toggle` with `selected`.

#### Scenario: Destructive button

- **WHEN** `MdButton` is mounted with the Boolean `error` prop
- **THEN** it renders the error roles of its variant

#### Scenario: Deprecated destructive variant

- **WHEN** `MdButton` is mounted with `variant="error"`
- **THEN** it renders as a filled error button, as before this change

#### Scenario: Card menu emits kebab-case

- **WHEN** the tab of `CardMenu` is chosen
- **THEN** the component emits `select-tab` with the chosen tab, and `SnippetsCard` receives the tab through `@select-tab`

#### Scenario: Snackbar queue is injected

- **WHEN** `MdSnackbar` is mounted inside a provider with its own queue
- **THEN** it shows the head of the provided queue, and it shows nothing from the shared queue

### Requirement: Anchors are registered and stable

Every primitive SHALL carry a `data-ingot-component` attribute whose value is its registered name in
`src/anchors/registry.json`. The registry SHALL change by addition only, except for the file path of a moved component. No
existing name, stability value or state value SHALL change, and no existing `data-ingot-component` value SHALL be renamed or
moved to `data-ingot-component-alias`. A moved component SHALL keep its existing name as its `data-ingot-component` value.

#### Scenario: New primitive registered

- **WHEN** a new primitive file is added under `src/components/md/`
- **THEN** the registry has an entry with its file, its name and the `experimental` stability, and the anchor tests pass

#### Scenario: Dialog anchor kept

- **WHEN** `DialogShell` is promoted to `MdDialog`
- **THEN** the frame element carries `data-ingot-component="dialog-shell"`, and the registry entry `dialog-shell` names the new file

#### Scenario: Segmented anchors kept on the moved files

- **WHEN** `SegmentedControl` and `SegmentedOption` move to `MdSegmentedButton` and `MdSegmentedOption`
- **THEN** the elements still carry `data-ingot-component="segmented-control"` and `data-ingot-component="segmented-option"`, the registry entries keep those names with the new file paths, and no alias is declared

### Requirement: Catalogue entry for every primitive

DESIGN.md SHALL hold one catalogue entry per primitive under `src/components/md/`. Each entry SHALL state the primitive's purpose,
its props, its slots, its events, its anchor, and what to do and what not to do with it. The `Known gaps` list of DESIGN.md SHALL
be replaced by the catalogue.

#### Scenario: Every primitive documented

- **WHEN** the catalogue test reads `src/components/md/` and DESIGN.md
- **THEN** every primitive file has a heading in the catalogue, and no heading names a file that does not exist

#### Scenario: Known gaps removed

- **WHEN** DESIGN.md is read
- **THEN** it contains no `Known gaps` list, and its gaps are the catalogue entries and the notes of the follow-up changes

### Requirement: Extensions keep the default render

Every extension of an existing primitive SHALL be opt-in. With no new prop or slot set, a primitive SHALL render the same markup and
classes that it renders before the extension, so that every view that uses it looks the same.

#### Scenario: Card defaults

- **WHEN** `MdCard` is mounted with no `tone`, `edge`, `padding`, `as` or `header` value
- **THEN** it renders the same root element, classes and padding as before the extension

#### Scenario: Card danger tone

- **WHEN** `MdCard` is mounted with the `danger` tone
- **THEN** its border uses the error role and its other classes are the default ones

#### Scenario: Radio focus ring off by default

- **WHEN** `MdRadio` is mounted without `focus-ring` in its plain variant
- **THEN** it renders no focus ring class, as before the extension

### Requirement: Field family shares one shell

`MdTextField`, `MdTextArea`, `MdSelect` and `MdChipInput` SHALL draw their chrome, their label, their helper line and their
leading and trailing slots through `MdFieldShell`. The helper line SHALL have one structure and one id for `aria-describedby`.
The renderers `md-text-field-renderer`, `md-text-area-renderer` and `md-select-renderer` SHALL keep working with the same props.

#### Scenario: Helper line structure

- **WHEN** the fields show a supporting text or an error text
- **THEN** each field draws the helper line through the shell, with the same id pattern, and the field refers to it through
  `aria-describedby`

#### Scenario: Error state

- **WHEN** a field has `error` set
- **THEN** the field sets `aria-invalid="true"` and draws its error chrome from the shell

#### Scenario: Read only field

- **WHEN** `MdTextField` is mounted with `readonly`
- **THEN** the input is read only, it keeps its value and its label, and it can still be focused

#### Scenario: Select width

- **WHEN** `MdSelect` is mounted with a `width` value
- **THEN** the field takes that width, and without the value it takes the width of the field as before

### Requirement: One navigation item

`MdNavigationBar` and `MdNavigationRail` SHALL build each destination from `MdNavigationItem`. The item SHALL render the active
indicator, the label, a link or a button, and `aria-current="page"` on the active destination. A modified click (a modifier key
or a non-primary button) SHALL be left to the browser. Both primitives SHALL keep their anchors.

#### Scenario: Modified click left to the browser

- **WHEN** a visitor holds Ctrl and clicks a link destination
- **THEN** the primitive does not prevent the default action and does not emit the change event

#### Scenario: Active destination

- **WHEN** the navigation bar is shown with an active key
- **THEN** only the destination with that key carries `aria-current="page"`

### Requirement: Dialog contract

`MdDialog` SHALL keep the anchor `dialog-shell` on its frame element and SHALL offer the sizes `sm`, `md` and `lg`, which lie
between 280px and 560px, with `lg` as the default. It SHALL be named by `labelledby`, SHALL take `role` as `dialog` or
`alertdialog`, SHALL move focus into the dialog on open, SHALL keep Tab and Shift+Tab inside it, and SHALL return focus to the
element that opened it on close.

#### Scenario: Default size

- **WHEN** `MdDialog` is mounted with no `size`
- **THEN** its frame is capped at 560px and never narrower than 280px

#### Scenario: Focus returns to the opener

- **WHEN** a dialog closes after it was opened from a button
- **THEN** focus returns to that button

#### Scenario: Alert dialog role

- **WHEN** `MdDialog` is mounted with `role="alertdialog"`
- **THEN** the frame carries `role="alertdialog"` and is named by `labelledby`

#### Scenario: Actions end aligned

- **WHEN** `MdDialogActions` holds two buttons
- **THEN** the buttons sit at the end of the row with one gap, and they wrap on a narrow width

### Requirement: Feedback and state primitives

`MdAlert` SHALL offer the tones `error`, `warning`, `tertiary` and `info`. An `error` alert SHALL have `role="alert"`, and the
other tones SHALL have `role="status"`. `MdStatePanel` SHALL offer the states `empty`, `loading` and `error`, and its `error` state
SHALL offer a Retry action. `MdSkeleton` SHALL show a static block under `prefers-reduced-motion`.

#### Scenario: Error alert announced

- **WHEN** `MdAlert` is mounted with the `error` tone
- **THEN** the root carries `role="alert"`

#### Scenario: Informational alert polite

- **WHEN** `MdAlert` is mounted with the `info` tone
- **THEN** the root carries `role="status"`

#### Scenario: Error state retry

- **WHEN** `MdStatePanel` is mounted in the `error` state with a retry handler
- **THEN** the panel draws one Retry button, and activating it calls the handler once

#### Scenario: Loading state

- **WHEN** `MdStatePanel` is mounted in the `loading` state
- **THEN** the panel carries `aria-busy="true"` and draws skeleton lines

### Requirement: Layout primitives

`MdPage` SHALL keep the page gutter that `container` gives. `MdPageHeader` SHALL draw the title in a column with `min-w-0`, and the
back action in a slot outside that column. `MdBreadcrumb` SHALL render a `nav` that holds an `ol`, a separator with
`aria-hidden="true"`, and `aria-current="page"` on its last crumb, with a 48px hit area. A breadcrumb SHALL truncate its middle
crumbs below a width token, and it SHALL keep its full trail as its accessible name.

#### Scenario: Breadcrumb semantics

- **WHEN** `MdBreadcrumb` is mounted with three crumbs
- **THEN** it renders a `nav` with an `ol`, the separators are hidden from assistive technology, and only the last crumb carries
  `aria-current="page"`

#### Scenario: Breadcrumb on a narrow width

- **WHEN** `MdBreadcrumb` is mounted with five crumbs below the width token
- **THEN** the middle crumbs are truncated, the first and the last crumbs stay, and the full trail is the accessible name

#### Scenario: Page header title

- **WHEN** `MdPageHeader` is mounted with a long title and a back action
- **THEN** the title wraps inside its column, and the back action sits outside that column

#### Scenario: Page gutter

- **WHEN** `MdPage` is mounted
- **THEN** it has the page gutter of `container` and no `px-*` class of its own

### Requirement: Data primitives

`MdDataTable` SHALL offer a `caption`, a sortable header with `aria-sort` on the sorted column, and a row header in the first
column. `MdList` and `MdListItem` SHALL render links, buttons or selectable options, with `leading` and `trailing` slots.
`MdKeyValueList` SHALL render its rows through one grid template. `MdTabs` SHALL render a `tablist` whose `tab` elements carry
`aria-selected`. `MdDisclosure` SHALL render a `summary` that carries the state layer and the focus ring.

#### Scenario: Sortable column

- **WHEN** `MdDataTable` is mounted with a column sorted in descending order
- **THEN** that column's header carries `aria-sort="descending"` and the other sortable headers do not carry `aria-sort`

#### Scenario: Row header

- **WHEN** `MdDataTable` renders a row
- **THEN** the first cell of the row is a `th` with `scope="row"`

#### Scenario: Tabs selected state

- **WHEN** `MdTabs` is mounted with one tab selected
- **THEN** only that tab carries `aria-selected="true"`

### Requirement: Badges and avatars

`MdBadge` SHALL offer the tones `neutral`, `accent`, `success`, `error`, `hosted`, `proxy`, `group` and `mixed`, each drawn from
the MD3 roles, and one height. `TokenTag`, `RepositoryTypeBadge` and `repositories/TypeBadge` SHALL remain as wrappers over
`MdBadge` only when their before and after screenshots match. `MdAvatar` SHALL draw the initial in a circle and SHALL be hidden
from assistive technology unless it has a label.

#### Scenario: Repository type tone

- **WHEN** `MdBadge` is mounted with the `hosted` tone
- **THEN** it draws the `tertiary-container` role and no colour outside the MD3 roles

#### Scenario: Avatar hidden by default

- **WHEN** `MdAvatar` is mounted without a label
- **THEN** its root carries `aria-hidden="true"`

#### Scenario: Avatar named

- **WHEN** `MdAvatar` is mounted with a label
- **THEN** it has an accessible name equal to the label

### Requirement: One icon registry

`MdIcon` SHALL render a glyph given as a `path` or as a `name` from one registry. It SHALL be hidden from assistive technology unless
it has a label. A glyph SHALL NOT be registered under two names, and a glyph that is not an exact match of an existing path SHALL
stay where it is until the maintainer decides.

#### Scenario: Decorative icon

- **WHEN** `MdIcon` is mounted with a `name` and no label
- **THEN** the root carries `aria-hidden="true"`

#### Scenario: Named glyph

- **WHEN** `MdIcon` is mounted with the name of a registered glyph
- **THEN** it renders that glyph's path in a 24 by 24 viewBox by default

### Requirement: Links, code and copyable code

`MdLink` SHALL be router-aware and SHALL draw one colour role, a focus ring and a state layer. A link with `external` SHALL open in
a new tab with `rel="noopener noreferrer"` and SHALL name the new tab for assistive technology. `MdCode` and `MdCodeBlock` SHALL use
the monospace type role. `MdCopyableCode` SHALL copy its text through `components/card/copySnippet.js` and SHALL show the copy status
in a live region.

#### Scenario: External link

- **WHEN** `MdLink` is mounted with `external`
- **THEN** it opens in a new tab with `rel="noopener noreferrer"`, and its accessible name says that it opens in a new tab

#### Scenario: Copy status

- **WHEN** the copy action of `MdCopyableCode` succeeds
- **THEN** the status line is announced in a polite live region

### Requirement: Listbox and combobox behaviour

`MdListbox` SHALL render `role="listbox"` with options that carry `role="option"` and `aria-selected`. The composable
`useCombobox` in `src/helpers/useCombobox.js` SHALL hold the active index, the open state, the filtering and the keyboard handling
that `ComboboxField` has today, and it SHALL have no DOM of its own.

#### Scenario: Keyboard moves the active option

- **WHEN** the composable receives Down with the list open
- **THEN** the active index moves to the next option, and it wraps at the end of the list

#### Scenario: Escape closes the list

- **WHEN** the composable receives Escape with the list open
- **THEN** the list closes and the active index is cleared

### Requirement: Motion respects reduced motion

`MdLinearProgress`, `MdSnackbar` and `MdSkeleton` SHALL take their durations and easings from motion tokens, and each SHALL stop
its motion under `prefers-reduced-motion`.

#### Scenario: Indeterminate progress under reduced motion

- **WHEN** a visitor prefers reduced motion and `MdLinearProgress` is indeterminate
- **THEN** the bar does not slide, and it still has `role="progressbar"`

#### Scenario: Skeleton under reduced motion

- **WHEN** a visitor prefers reduced motion and `MdSkeleton` is shown
- **THEN** it draws a static block

### Requirement: Segmented button moved, not changed

`MdSegmentedButton` and `MdSegmentedOption` SHALL render the same pixels as `SegmentedControl` and `SegmentedOption` do before the
move, with the 20px corner drawn from the shape token `corner-segmented` instead of an arbitrary value. The old paths SHALL re-export the new files.

#### Scenario: Old import path

- **WHEN** a module imports `src/components/tokens/SegmentedControl.vue`
- **THEN** the import resolves to `MdSegmentedButton` and the segmented control renders as before

#### Scenario: Corner token

- **WHEN** the segmented track is rendered
- **THEN** its corner radius is 20px, and the radius comes from the shape token

### Requirement: Shared control label gap

`MdCheckbox`, `MdSwitch` and `MdRadio` SHALL take the gap between the control and its label from one shared label part. The default of
each control SHALL be the gap it has today (`gap-2` for the checkbox and the plain radio, `gap-3` for the switch and the card radio).

#### Scenario: Default gaps unchanged

- **WHEN** each control is mounted with no gap value
- **THEN** its label gap class is the one the control has before this change

### Requirement: Pixel-identical adoption only

A swap of a view or a primitive onto a new primitive in this change SHALL render the same pixels as before. The swaps allowed in this
change are those listed in the proposal under "Pixel-identical adoptions". Any other swap SHALL wait for its own change, and any
swap whose before and after screenshots at 320, 600 and 1440 pixels, in light and dark, differ SHALL be held back and listed in the
pull request.

#### Scenario: Screenshots match

- **WHEN** a pixel-identical adoption is made
- **THEN** the before and after screenshots at 320, 600 and 1440 pixels, in light and dark, are attached to the pull request and
  are identical

#### Scenario: Screenshots differ

- **WHEN** a before and after screenshot of an adoption differs
- **THEN** the adoption is reverted in this change and listed as not done

### Requirement: Primitive tests follow F.I.R.S.T.

Every primitive SHALL have a unit test file next to it with one behaviour per test. The tests SHALL use `@vue/test-utils`, SHALL NOT
sleep or wait by time, SHALL use fake timers only where a timer is the behaviour under test, SHALL NOT read the system clock, and
SHALL build their own fixtures per test.

#### Scenario: Each behaviour has a test

- **WHEN** a primitive's catalogue entry lists a prop, a slot or an event
- **THEN** a test in the primitive's test file asserts that behaviour

#### Scenario: No wall-clock waits

- **WHEN** the unit tests of `src/components/md/` run
- **THEN** no test calls a sleep or a wait by time

### Requirement: No view migration in this change

This change SHALL NOT move a view, a card, a page or a dialog onto the new primitives, except for the pixel-identical adoptions the
proposal lists. The follow-up changes `migrate-content-to-components`, `fix-surface-hierarchy-and-page-header` and
`unify-dialogs-menus-and-feedback` SHALL adopt the primitives.

#### Scenario: Views unchanged

- **WHEN** the code PR of this change is reviewed
- **THEN** no file under `src/pages/` changes, and no view component changes except the re-export files named in the proposal,
  the `CardMenu` emit and trigger slot named in the proposal, and the `App.vue` provider named in the proposal

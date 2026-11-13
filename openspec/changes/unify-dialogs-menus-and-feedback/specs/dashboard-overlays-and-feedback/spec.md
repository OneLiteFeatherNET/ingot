# Spec Delta

## Purpose

Every dialog, side sheet, menu, listbox and status message of the dashboard is drawn by one shared component, takes and
returns focus the same way, and is announced once, by the element that owns it. Plugins and operators keep the anchors
they style against.

## ADDED Requirements

### Requirement: Every dialog uses the shared dialog frame

Every dialog of the dashboard SHALL be drawn by `MdDialog`. `MdDialog` SHALL teleport to the body, SHALL keep a 24px inset on
its content, SHALL cap its width at 560px and floor it at 280px, and SHALL take the dialog stacking value from the
`--md-sys-z-dialog` token. A view SHALL NOT draw its own dialog frame, scrim, overlay style or stacking literal such as
`z-50` or `fixed inset-0`.

#### Scenario: Dialog is teleported and sized

- **WHEN** a visitor opens any dialog of the dashboard at 320 pixels and at 1440 pixels
- **THEN** the dialog is a child of the document body, its content is 24px from each side of the window at 320 pixels, and its
  width is no more than 560px and no less than 280px

#### Scenario: No per-view frame

- **WHEN** a source file under `reposilite-frontend/src/components/` is checked
- **THEN** it contains no `overlay-style` binding, no `SCRIM_STYLE` import, no `fixed inset-0` dialog root and no `z-50` on a
  dialog root

#### Scenario: Scrim

- **WHEN** a dialog is open
- **THEN** the scrim is the `scrim` role at 32% opacity, set once by `MdDialog`

### Requirement: Dialog heading names the dialog

A dialog SHALL be named by its heading. `MdDialog` SHALL generate the heading id with a stable generated id and SHALL set
`aria-labelledby` on the dialog element to it. A view SHALL NOT use a fixed id for a dialog heading.

#### Scenario: Accessible name

- **WHEN** the dialog opens
- **THEN** the dialog element has `aria-labelledby` that resolves to the text of its heading

#### Scenario: Two dialogs of one view

- **WHEN** two instances of the same dialog are mounted in one page
- **THEN** their heading ids are different and each dialog is named by its own heading

### Requirement: Dialog focus on open and close

When a dialog opens, focus SHALL move to the element named by its `initialFocus`, or to its first focusable element when none
is named. A confirming dialog SHALL focus its cancel action first. When the dialog closes, focus SHALL return to the element that
had focus when it opened, if that element is still in the document.

#### Scenario: Initial focus

- **WHEN** a visitor opens a dialog with a text field and `initialFocus` names that field
- **THEN** the text field has focus

#### Scenario: Confirming dialog focuses cancel

- **WHEN** a visitor opens a destructive confirmation such as the delete of a policy
- **THEN** the cancel action has focus, so that Enter does not confirm the deletion

#### Scenario: Focus returns to the opener

- **WHEN** a visitor opens a dialog from a button and closes it with Escape
- **THEN** the button has focus again

#### Scenario: Opener removed

- **WHEN** the element that opened a dialog is removed while the dialog is open, and the dialog closes
- **THEN** focus goes to the nearest focusable ancestor of the removed element, and is not lost to the document body

### Requirement: Dialog keyboard and motion

Tab and Shift+Tab SHALL stay inside an open dialog. Escape SHALL close a dialog unless its `dismissable` is false. The fade of a
dialog SHALL be off under `prefers-reduced-motion`.

#### Scenario: Escape closes or keeps open

- **WHEN** a visitor presses Escape in a dialog with `dismissable` true, and then in a dialog with `dismissable` false
- **THEN** the first dialog closes and the second stays open

#### Scenario: Tab stays inside

- **WHEN** a visitor presses Tab on the last focusable element of an open dialog
- **THEN** focus moves to the first focusable element of that dialog, not to the page behind it
### Requirement: One filled button per dialog

A dialog SHALL have at most one filled `MdButton`, its confirming action. The cancel action SHALL be a `text` button. A
destructive confirmation SHALL use the filled button with the `error` prop, and its label SHALL name the action, such as
"Delete policy" or "Revoke token". A dialog with a single action SHALL use one filled button.

#### Scenario: Confirmation of a deletion

- **WHEN** a visitor opens the delete confirmation of a cleanup policy
- **THEN** the cancel action is a text button, and the confirming action is a filled button in the error role named "Delete policy"

#### Scenario: Non-destructive confirmation

- **WHEN** a visitor opens the dialog that runs a cleanup policy now
- **THEN** its confirming action is a filled button in the primary role and the cancel action is a text button

### Requirement: Side sheets open from either edge

`MdSideSheet` SHALL accept a `side` prop with the values `start` and `end`, defaulting to `start`. An `end` sheet SHALL open from the
trailing edge of the window, keep `role="dialog"`, `aria-modal` and its accessible name, keep the focus trap and the focus
return of the `start` sheet, and take its width from a token rather than a literal. The failures sheet and the settings review
sheet SHALL open from the trailing edge.

#### Scenario: End side

- **WHEN** a visitor opens the failures sheet at 1440 pixels
- **THEN** the sheet is at the trailing edge of the window, is named by its title, and its width comes from the width token

#### Scenario: Focus in an end sheet

- **WHEN** a visitor opens the settings review sheet and closes it with Escape
- **THEN** focus is inside the sheet while it is open and returns to the button that opened it when it closes

### Requirement: Menus share one item contract

`MdOverflowMenu` items SHALL accept `disabled` and `supportingText`. A disabled item SHALL be drawn in the disabled role, SHALL
carry `aria-disabled="true"`, and SHALL be skipped by the arrow keys while staying readable to assistive technology. An item
with `supportingText` SHALL show it as a second line under the label, and its accessible name SHALL include it. An item in the
error role SHALL keep its focus ring. A menu SHALL NOT use an `!important` modifier to draw an item colour.

#### Scenario: Disabled item

- **WHEN** a visitor opens the actions menu of a principal whose current user cannot remove that principal
- **THEN** the remove item is drawn disabled, is announced as disabled, and the arrow keys move past it

#### Scenario: Two-line item

- **WHEN** a visitor opens the download menu of the settings page
- **THEN** each item with a description shows the description on a second line, and the item's accessible name includes it

#### Scenario: Focus ring on a destructive item

- **WHEN** a visitor moves the focus with the keyboard onto the destructive item of the actions menu
- **THEN** the item shows the focus ring and its text uses the error role

### Requirement: Comboboxes share one keyboard contract

A combobox of the dashboard SHALL be built from `MdListbox` and the `useCombobox` composable, and a view SHALL NOT draw its own
keyboard handling for a listbox. The composable SHALL handle ArrowDown and ArrowUp with wrap, Home and End, Enter to choose, and
Escape to close while keeping the typed text. The input SHALL carry `role="combobox"`, `aria-expanded`, `aria-controls` and
`aria-activedescendant`, and each option SHALL carry `role="option"` and `aria-selected`.

#### Scenario: Keyboard choice

- **WHEN** a visitor types into the permission node field, presses ArrowDown twice and Enter
- **THEN** the second matching option is chosen and the list closes

#### Scenario: Escape keeps the text

- **WHEN** a visitor presses Escape while the list is open
- **THEN** the list closes, the typed text stays in the input, and no option is chosen

#### Scenario: Repository template field

- **WHEN** a visitor opens the template field of the node editor
- **THEN** the repository names are offered by `MdListbox`, not by a native `datalist`, and each name is an option with a role

### Requirement: Listbox placement and layer

The list of a combobox SHALL be teleported and placed from its input, so that a scrolling table cannot clip it. It SHALL NOT use a
`z-10`, `z-20` or `z-50` literal, and it SHALL use the menu layer token.

#### Scenario: List inside a scrolling table

- **WHEN** a visitor opens the list of a combobox in a row of a table that scrolls
- **THEN** the list is drawn above the table rows and is not clipped by the table
### Requirement: Inline errors are alerts with a state attribute

An inline error next to a form or a control SHALL be an `MdAlert` with the error tone. Its root SHALL carry
`data-ingot-state="error"`, and the alert role SHALL be on that root, so a screen reader announces the error once when it
appears. An inline error that the page already announces through a live region SHALL NOT be announced a second time.

#### Scenario: Token creation fails

- **WHEN** creating an API token fails with a server message
- **THEN** the dialog shows an `MdAlert` with the error tone, the root has `data-ingot-state="error"` and `role="alert"`, and the message is announced once

#### Scenario: Attribute on a rotation error

- **WHEN** the rotation of a token fails
- **THEN** the message element carries `data-ingot-state="error"`, as the other token dialogs do

### Requirement: Failed loads show a state panel with Retry

A panel whose data failed to load SHALL show an `MdStatePanel` in the error state. The message SHALL be in the panel, and the
Retry action SHALL be a tonal `MdButton`. The panel's root SHALL carry `data-ingot-state="error"`. A panel SHALL NOT draw a
Retry as a bare text link beside a red paragraph.

#### Scenario: Retry after a failed load

- **WHEN** the list of teams fails to load and the visitor activates Retry
- **THEN** the request is sent again, the panel shows its loading state, and no second error paragraph is drawn

#### Scenario: Error state attribute

- **WHEN** the LDAP panel fails to load
- **THEN** its state panel root has `data-ingot-state="error"` and the Retry button is tonal

### Requirement: Loading and empty states share one primitive

A panel that is loading SHALL show an `MdSkeleton` placeholder of the height of the content it stands in for, and a line of
`MdStatePanel` in the loading state with `role="status"`. A panel with nothing to show SHALL show an `MdStatePanel` in the empty
state, with the same padding and text role as every other empty state. Skeletons SHALL NOT use inline `style` widths or fixed
pixel heights that do not match the content. Skeleton animation SHALL be off under `prefers-reduced-motion`.

#### Scenario: Loading the access teams

- **WHEN** the teams panel is waiting for its first response
- **THEN** it shows a skeleton and a loading line with `role="status"`, and no `animate-pulse` class

#### Scenario: Empty states match

- **WHEN** the search results, the entry list and the run history have no results in turn
- **THEN** each shows an `MdStatePanel` in the empty state with the same vertical padding and the same text role

### Requirement: Static notices carry no alert role

A notice that stays on the page, such as a warning about an encryption key or an instance status line, SHALL be an `MdAlert` with
its tone and one radius and one spacing for all notices. It SHALL NOT carry `role="alert"`. A one-time secret or any other copy
that does not change after it is shown SHALL NOT be announced as an alert.

#### Scenario: One-time secret

- **WHEN** the one-time secret dialog is shown
- **THEN** the secret is a notice without an alert role, and the dialog's heading names the dialog

#### Scenario: Matching notices

- **WHEN** the encryption notice and the import errors are both shown in the settings page
- **THEN** they have the same corner radius and the same padding

### Requirement: Snackbar placement follows the design rules

The snackbar SHALL sit at the bottom centre with a 16px inset below 600 pixels and at the bottom left from 600 pixels. `DESIGN.md`
SHALL state the breakpoint as 600 pixels, the same value the code uses. The snackbar SHALL stay above dialogs, and that
stacking value SHALL be recorded beside the dialog layer in `DESIGN.md`. The snackbar SHALL NOT carry a `<style>` block.

#### Scenario: Placement at the breakpoint

- **WHEN** the window is 599 pixels wide and then 600 pixels wide while a notice is shown
- **THEN** the notice is centred at 599 pixels and at the left at 600 pixels

### Requirement: Dialog and status design rules are written down

`reposilite-frontend/DESIGN.md` SHALL state the dialog frame (the `MdDialog` size range, its inset, its stacking value and its focus
contract), the confirm button rule, the side sheet edges, the menu and listbox contract, and the status primitives with their
states. It SHALL NOT list a dialog or drawer primitive under "Known gaps" once one exists.

#### Scenario: Known gaps

- **WHEN** a reader checks the "Known gaps" section of `DESIGN.md` after this change
- **THEN** it does not list a dialog or a drawer primitive as missing

### Requirement: Anchors and plugin styling are unchanged

The `data-ingot-component` values of dialogs, menus, sheets and messages SHALL stay on the element they are on today, the dialog
heading or the root. No anchor SHALL be renamed or removed by this change. The `dialog-shell` anchor SHALL stay on the box that
`MdDialog` renders. The registry SHALL list every anchor that a migrated file carries.

#### Scenario: Anchor stays on the heading

- **WHEN** the new team dialog is opened
- **THEN** the element with `data-ingot-component="new-team-dialog"` is still its heading

#### Scenario: Registry matches the page

- **WHEN** the anchors test runs over the migrated files
- **THEN** no anchor is reported as missing from `src/anchors/registry.json` and no registry entry points at a file that no longer carries it

### Requirement: No custom CSS classes or style blocks in migrated files

A migrated file SHALL style itself only with Tailwind utilities that use the MD3 tokens. A migrated file SHALL NOT contain a
`<style>` block, a custom CSS class defined in a stylesheet for that file, a raw colour value, or a magic number that bypasses a
token. Each migrated file SHALL leave the `add-ui-style-gate` baseline in the commit that migrates it, and the baseline SHALL NOT
gain an entry.

#### Scenario: Baseline shrinks

- **WHEN** an area's `fix(ui)` commit is checked against the baseline
- **THEN** every migrated file of that area is absent from the baseline, and the baseline has no new entry

#### Scenario: Style gate passes

- **WHEN** the frontend style gate runs after the change
- **THEN** it reports no custom CSS class and no `<style>` block in a migrated file

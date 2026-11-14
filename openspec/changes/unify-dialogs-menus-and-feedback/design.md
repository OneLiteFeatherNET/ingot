# Design

## Context

The dashboard has three families of transient UI and one family of messages, and each is drawn in several ways today:

- **Dialogs.** `components/identity/DialogShell.vue` is the one shared frame. It is a `VueFinalModal` with
  `teleport-to="body"`, `content-class="w-full px-6"`, an inline scrim in the `scrim` role at 32%, and an inner box
  capped at `max-w-140` (560px) and floored at `min-w-70` (280px) with `p-6`. Seven dialogs in `components/access/` and
  four in `components/identity/` use it, each with its own heading, body and action row. About fifteen other dialogs
  (listed in proposal.md) copy the frame by hand, with `max-w-xl`, `z-50`, `fixed inset-0`, `bg-scrim/40`, or the
  `SCRIM_STYLE` object imported from `components/settings/dialog.js`.
- **Menus and listboxes.** `components/md/MdOverflowMenu.vue` is a teleported menu with arrow keys, Escape and focus
  return. It has no disabled items and no second line. `PrincipalActionsMenu.vue` and `SettingsDownloadMenu.vue` draw
  their own menus. Three comboboxes (`ComboboxField.vue`, `NodeAddField.vue`, `TeamParentsCard.vue`) draw their own
  listboxes with their own keyboard handling, and `NodeTemplateMenu.vue` uses a native `datalist`.
- **Side sheets.** `components/md/MdSideSheet.vue` is a modal from the leading edge, named by its title, with a focus trap
  and focus return. `FailuresSheet.vue` and `SettingsReviewSheet.vue` draw trailing panels by hand.
- **Status messages.** Errors, loading lines, empty sentences and banners are hand-written paragraphs and `div`s, with
  varying roles, colours, radii and spacing.

The catalogue change (`add-md3-component-catalogue`) provides the primitives `MdDialog`, `MdAlert`, `MdStatePanel`,
`MdSkeleton` and `MdListbox`. This change uses them. Where a primitive lacks a capability that a migrated view needs, the
gap goes back into the catalogue change (proposal question 4). The one extension made here is the `compact` dialog size of
question 3, and the `wide` size if question 1 is answered with the alternative.

## Goals and non-goals

Goals:

- One frame for every dialog, with the rules in `DESIGN.md` enforced by the component, not by each file.
- One menu primitive and one listbox primitive, with one keyboard contract.
- One status vocabulary: an error is an `MdAlert` with the error tone, an empty or failed panel is an `MdStatePanel`, and
  a loading placeholder is an `MdSkeleton`.
- Anchors and the `ingot-plugins` cascade layer keep working unchanged.
- Each migrated file loses its entry in the `add-ui-style-gate` baseline in the same commit.

Non-goals:

- Restyling the content inside a dialog (cards, lists, tables, forms). Those are `migrate-content-to-components`.
- Changing any behaviour of the server or of the forms.
- Renaming an anchor or a configuration key.

## Decisions

### D1. `MdDialog` is the only dialog frame

`MdDialog` renders a `VueFinalModal` with `teleport-to="body"`, `content-class` carrying the 24px inset (`px-6`), and
`class` on the modal root that gives the `--md-sys-z-dialog` layer through the `.vfm` rule that already exists in
`src/style.css:184`. The content box is capped at 560px and floored at 280px, takes the `surface-container-high` role,
`rounded-corner-extra-large`, `shadow-elevation-3` and `p-6`. The box keeps the anchor `dialog-shell`, which is the name
`DialogShell.vue` carries today. That anchor moves with the frame into `MdDialog`, so no registry entry is renamed.

Props:

- `modelValue` (boolean, for `v-model`).
- `dismissable` (boolean, default `true`). When `false`, Escape and a click outside do not close the dialog. Used by the
  dialogs whose only exit is their own button (the one-time secret, the migration cut-over).
- `role` (`'dialog'` or `'alertdialog'`, default `'dialog'`). `'alertdialog'` is set on the destructive confirmations
  (for example `maintenance/DeletePolicyDialog.vue` today), so that the announcement says that the dialog asks for a decision.
- `size`: the catalogue sizes `sm` (360px), `md` (480px) and `lg` (560px, the default), all inside the 280 to 560px range.
  This change adds `compact`, the full-screen layout of the repository wizard below 600px, if question 3 is answered with
  the default. A `wide` size (840px cap) exists only if question 1 is answered with the alternative.

Slots: `header` (the heading, which the consumer renders with its own `id` and anchor), `default` (the body), `actions`
(the action row, right-aligned, with `gap-2`).

`labelledby` is not a prop any more. `MdDialog` generates the heading id with `useId()`, passes it to the `header` slot
through a scoped slot value (`{ headingId }`), and sets `aria-labelledby` on the dialog element from it. Consumers use the
slot value on their `<h2>`. This removes the fixed ids that `NewMachineDialog.vue` and `DeclareTypeDialog.vue` carry
(`new-machine-title`, `declare-type-title`, `declare-type-body`).

Inline `overlay-style` and `SCRIM_STYLE` go. The scrim is set once in `MdDialog`, in the `scrim` role at 32%, through the `bg-scrim/md-scrim`
utility on the overlay class that the catalogue change puts in place of the inline `color-mix` style of `DialogShell.vue`.
The migration dialog's 40% scrim (`md-scrim-strong`) goes with its own frame, which is one of the visible changes listed
in the proposal.

### D2. Focus contract for every dialog

- **On open:** focus moves to the element named by the `initialFocus` prop (a template ref or an element id), or, when
  none is given, to the first focusable element of the body. A dialog with a text field focuses the field. A confirming
  dialog focuses the cancel button, so an accidental Enter does not confirm a destructive action.
- **Focus trap:** Tab and Shift+Tab stay inside the open dialog. The modal root carries the trap that
  vue-final-modal provides; `MdDialog` tests it.
- **On close:** focus returns to the element that had focus when the dialog opened, if that element is still in the
  document. If it is gone (a row that was deleted), focus goes to the nearest focusable ancestor. A close that the page
  starts with a navigation keeps the focus where the navigation puts it, as `MdSideSheet` does.
- **Escape** closes the dialog unless `dismissable` is `false`.
- **Reduced motion:** the fade is off under `prefers-reduced-motion`, as `MdSideSheet` already does.

`settings/PasswordDialog.vue` does not focus on open today (line 67) and is not teleported. It moves onto `MdDialog`, so
it inherits the contract and loses its own `z-50` and `fixed inset-0`.

### D3. Confirm buttons

A dialog has at most one filled button, its confirming action. The rule is written in `DESIGN.md` as follows (question 2
default):

- The confirming action of a dialog is a filled `MdButton`. The cancel action is `variant="text"`.
- A destructive confirmation (delete, revoke, discard, reset, cut over) uses the filled button with the `error` prop. The
  error role carries the warning, so the colour is not the only signal: the label names the action ("Delete policy",
  "Revoke token").
- An informational dialog with a single button (a notice, the one-time secret) uses one filled button.

This settles the open disagreement in the audit: `OneOfRenderer.vue` uses a text confirm button and
`DeclareTypeDialog.vue` a filled one. Under the rule, the `OneOfRenderer` confirm becomes filled. `MappingStep.vue` and
`CutOverStep.vue` use filled for destructive actions without `error`; they gain the `error` prop.

### D4. Menus and listboxes

`MdOverflowMenu` gains two item fields, specified in the spec:

- `disabled` (boolean): the item is drawn in the disabled role and is skipped by the arrow keys. It is announced with
  `aria-disabled="true"`, not with the `disabled` attribute, so it stays focusable for screen readers that announce
  disabled menu items.
- `supportingText` (string): a second line under the label, in `md-body-small` and `text-on-surface-variant`. The
  accessible name of the item includes it.

The item error colour is applied through the item's own class, not through `!text-error`. The `!` modifier overrides
the state layer and the focus ring in `PrincipalActionsMenu.vue:266` and in the primitive itself, which is why the
focus ring is missing on one item. The primitive draws the error colour with a non-important class in the item's
class list, so the focus ring keeps its place.

`MdListbox` is the listbox primitive of the catalogue. It is used with `useCombobox`, a composable in `src/helpers/`
that owns the keyboard contract once:

- ArrowDown and ArrowUp move the active option and wrap; Home and End move to the first and last option.
- Enter chooses the active option; Escape closes the list and keeps the typed text.
- The input has `role="combobox"`, `aria-expanded`, `aria-controls` and `aria-activedescendant`. The options have
  `role="option"` and `aria-selected`.
- The list is placed from the input's bounding rect and teleported, the same way `MdOverflowMenu` places its menu, so a
  scrolling table cannot clip it. `z-10`, `z-20` and `z-50` literals are removed. The popup uses the menu layer `z-md-menu`
  (`--z-index-md-menu`, 50) that `refactor-md3-token-utilities` defines; no new stacking token is added.

`NodeTemplateMenu.vue` replaces its native `datalist` with `MdListbox`. The `datalist` is not a visible control in every
browser and cannot be styled, so it is the one listbox in the set that breaks the token rule. Its test asserts the
options by role, not by the `datalist option` selector that `NodeEditor.test.js` uses today; that test changes in the same
commit.

### D5. Side sheets

`MdSideSheet` gains a `side` prop with the values `'start'` (default, today's behaviour) and `'end'`. The sheet keeps
`role="dialog"`, `aria-modal`, the focus trap, Escape and the focus return. The width of the `'end'` sheet is a token, not a
literal: `FailuresSheet.vue` draws `min-[840px]:w-[28rem]` today, and it moves to `--container-md-sheet-wide` (28rem),
which the catalogue change adds beside `--container-md-sheet`.

`SettingsReviewSheet.vue` moves onto the `'end'` side. `FailuresSheet.vue` moves onto it too, with the same width.

### D6. Status messages

The vocabulary is fixed, and each situation has one primitive:

| Situation | Primitive | Announcement |
| --- | --- | --- |
| Inline error next to a form or a control | `MdAlert` with the error tone | `role="alert"`, announced once when it appears |
| Notice that stays on the page (a warning, a success, an information note) | `MdAlert` with its tone | No live role. Static copy is read as part of the page |
| A panel whose load failed, with a Retry action | `MdStatePanel` in the error state, the action is a tonal `MdButton` | `role="alert"` on the panel's message |
| A panel that is loading | `MdSkeleton` for the placeholder, and a `MdStatePanel` in the loading state for the line | `role="status"` on the line |
| A panel with nothing to show | `MdStatePanel` in the empty state | No live role |

`MdAlert` and `MdStatePanel` both render their root with `data-ingot-state` set to the state (`error`, `loading`,
`empty`), so the existing attribute contract and the tests that read it keep working. `RotateTokenDialog.vue` gets the
attribute it lacks today.

`role="alert"` on static copy goes. `SecretOnceDialog.vue:176` announces a one-time secret that does not change, so it
becomes a notice. `RepositoryWizard.vue` has four `role="alert"` error paragraphs (lines 383, 645, 763 and 915) in three sizes; they all
become `MdAlert` with the error tone and one size.

The skeleton has a fixed height that matches the content it stands in for, and it is animated only where
`prefers-reduced-motion` allows. The inline `style` widths in `card/SnippetsCard.vue` (lines 254 to 270) and the
`h-[220px]` of `dashboard/ChartCard.vue` (lines 76 and 82) are replaced by `MdSkeleton` sizes. The `h-[88px]` of
`search/SearchView.vue` (line 422) is replaced in the same way.

### D7. Snackbar placement

`MdSnackbar.vue` places the notice with a 16px inset and switches to the left-aligned placement at 600px (the
`medium:` window class, which `refactor-md3-token-utilities` puts in place of `min-[600px]:`). `DESIGN.md` says "the `md` breakpoint" in one place. The sentence is corrected to 600px, because
the code and every other breakpoint in the dashboard use 600px. The snackbar stays at 1100 (`z-md-snackbar`, set by the catalogue change), above dialogs, and that
is recorded in `DESIGN.md` next to the dialog layer.

The snackbar's `<style scoped>` block (`MdSnackbar.vue:87`) is removed by the catalogue change. This change checks that it
is gone and adds no `<style>` block of its own.

### D8. Rollout and the baseline

The rollout is one area at a time, in this order: identity, access, maintenance, settings, dashboard, migration,
renderers, header. Each area is preceded by its own `test(ui)` commit with the red tests, and followed by one `fix(ui)`
commit that makes the tests green and shrinks the `add-ui-style-gate` baseline by the entries the area removes. The
`compact` dialog size (question 3) is one `fix(ui)` commit before the identity area; every other primitive capability comes
from the catalogue change. The removal of `SCRIM_STYLE` from `components/settings/dialog.js` is the last commit of the change, after the header
area, when nothing imports it.

The order follows the dependencies between the areas: identity provides `DialogShell.vue`, which the access area already
imports, so identity moves first and the access area then loses its import of `identity/DialogShell.vue`.

### D9. Tests

Every test follows F.I.R.S.T., as `tasks.md` states. For dialogs the tests cover:

- `labelledby`: the dialog's `aria-labelledby` resolves to the heading text.
- Initial focus: the element named by `initialFocus`, or the first focusable element, has focus after open.
- Focus return: after close, the element that opened the dialog has focus.
- Escape: closes the dialog when `dismissable` is true and keeps it open when false.
- One filled button: the dialog's actions render exactly one `MdButton` with the filled variant.

The vue-final-modal is mounted with a teleport target in the test, and focus is checked with `document.activeElement`.
Nothing waits on time. The `prefers-reduced-motion` check reads the class, not the computed animation.

Each migrated dialog keeps its existing test file and adds the five assertions above where they are missing. Each area's
`test(ui)` commit lists the red tests it adds.

### D10. Screenshots

Before and after screenshots are taken with Playwright at 320, 390, 600, 1280 and 1440 pixels, light and dark. The set is:
each dialog family (identity, access, maintenance, settings, migration, login), one menu, one combobox and each state
panel (error, loading, empty). They are attached to the code PR, not committed to the repository. The widths 320, 600 and
1440 are the binding set for any visible change; 390 and 1280 are added for the dialog families, as the maintainer asked.

## Risks

- **Teleported dialogs and the `ingot-plugins` layer.** Plugin stylesheets live in the `ingot-plugins` cascade layer.
  A plugin that styled the old per-file frame by class loses that rule. The contract is the anchor, and anchors are
  kept, so a plugin that followed the contract is unaffected. This is written in the proposal.
- **The `datalist` change.** A native `datalist` works without JavaScript; `MdListbox` does not. The change is limited to
  the dashboard, which already requires JavaScript, and it is tested with the keyboard.
- **Focus return after a deleted row.** If the opener was removed while the dialog was open, focus goes to the nearest
  ancestor. This is the one case that is not the same as before, and the test in D9 covers it.
- **The baseline.** If `add-ui-style-gate` is merged after this change, the baseline file does not yet exist. Then this
  change does not edit it, and the shrink happens when the gate lands. The tasks say which.

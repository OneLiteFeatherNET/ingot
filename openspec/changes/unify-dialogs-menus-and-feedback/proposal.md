# Proposal

## Open maintainer questions

Each question has a default. The apply phase builds the default unless the maintainer picks the alternative before the
proposal PR is merged.

1. **Wide dialog for tables.** The cleanup policy preview (`CleanupPoliciesPanel.vue`) is a table that does not fit 560px.
   Default: no size above 560px. The table scrolls sideways inside the dialog body. Alternative: a `wide` size capped at
   840px, used by that one preview and by no other dialog.
2. **Confirm button rule.** Default: a confirming action is the one filled button of its dialog, and a destructive
   confirmation uses `MdButton` with `error`; the cancel action is `variant="text"`. Alternative: non-destructive
   confirmations use `text`, as `OneOfRenderer.vue` does today, and only the destructive one is filled.
3. **Compact layout of the repository wizard.** Default: `RepositoryWizard.vue` keeps a full-screen compact size below
   600px, as the one documented exception to the 280 to 560px range. Alternative: the wizard uses the standard range and
   scrolls inside it, like every other dialog.
4. **Shared primitives this change needs.** `MdSideSheet` opens only from the leading edge today, `MdOverflowMenu` has no
   disabled or two-line items, and there is no `useCombobox` composable. Default: `add-md3-component-catalogue` provides
   them, with their tests (the side sheet `side` prop and width tokens, the menu items, `MdListbox` and `useCombobox`, and the
   `MdDialog` focus contract), and this change adds none of them. If one is missing when this change is applied, the gap goes
   back into the catalogue change before any area here is migrated. Alternative: this change adds the missing piece in its
   first `fix(ui)` commit, and the catalogue change is updated to say so.
5. **Retry in error states.** Default: Retry is a `tonal` button in `MdStatePanel`. Alternative: a `text` button, which
   reads lighter but is weaker as the one action of an error.

## Why

An operator who opens a dialog, a menu or a failed panel in Ingot meets a different frame each time. Dialogs in the
identity area are built on a shared frame, while the migration, the password prompt and the login modal draw their own
scrim, their own focus handling and their own z-index. A menu in the access area has a focus ring on most items and none
on one of them. An error message is a red paragraph in one panel, a bold banner in the next, and a silent line in a third.
A keyboard user who tabs into an open dialog cannot be sure where focus will land, and when the dialog closes focus is
sometimes lost.

The cost is not only visual. Each hand-built frame copies the same 24px inset, the same action row, the same scrim and the
same error paragraph. A fix to one of them, such as the focus return, has to be repeated in about thirty places, and the
copies drift. The design rules in `reposilite-frontend/DESIGN.md` already say that every dialog is a `vue-final-modal`
teleported to the body, with a 24px inset, a 280 to 560px range and the `--md-sys-z-dialog` layer. Today about thirty
files do not follow that rule, or follow it through their own copy.

Status messages are the part operators read most. A load error, a missing result and a saved change are the moments when
the dashboard must be clear. Right now the same kind of message uses different roles, different radii and different
spacing: `SettingsEncryptionAlert` and `SettingsImportErrors` have different corner radii, and `ProblemAlert` and
`ConnectStep` in the migration area show the same string in two styles. Some static copy carries `role="alert"`, so a
screen reader announces text that never changed, while some real errors carry no role at all.

This change puts every dialog, menu, listbox and status message on a shared component from the MD3 catalogue
(`add-md3-component-catalogue`), so that a fix made once reaches every use. It does not change what a dialog asks, what a
menu does or what an error says. It changes how they are drawn, how they take focus and which element announces them.

## What Changes

- **Dialogs on `MdDialog`.** Every dialog is built on `MdDialog` from the catalogue. It is a `vue-final-modal` teleported
  to the body, with a 24px inset on the content, a width of 280 to 560px, the dialog z-index token and one heading, body
  and action row slot each. The per-file frame, scrim and action row are deleted. Dialogs that already use
  `components/identity/DialogShell.vue` adopt the `header` and `actions` slots. The dialogs that are not on that shell
  (`browser/AdjustmentsModal.vue`, `browser/DeleteEntryModal.vue`, `dashboard/PublicRepositoriesDialog.vue`,
  `dashboard/ReadOnlyToggle.vue`, `maintenance/*`, `settings/ConflictDialog.vue`, `settings/DeleteEntryDialog.vue`,
  `settings/NewEntryDialog.vue`, `settings/FactoryResetModal.vue`, `settings/PasswordDialog.vue`,
  `renderers/OneOfRenderer.vue`, `repositories/DeclareTypeDialog.vue`, `header/LoginModal.vue` and
  `migration/MigrationDialog.vue`) move onto it. `settings/PasswordDialog.vue`, which is not teleported and draws its
  password inputs by hand, moves to `MdTextField` with `type="password"`.
- **Side sheets.** `dashboard/FailuresSheet.vue` and `settings/SettingsReviewSheet.vue` open from the trailing edge, so they
  move onto `MdSideSheet` with a trailing side. Their 28rem width becomes the `--container-md-sheet-wide` token that the
  catalogue change adds.
- **Menus and listboxes.** `access/PrincipalActionsMenu.vue` and `settings/SettingsDownloadMenu.vue` move onto
  `MdOverflowMenu`, which gains disabled items and items with a second line. `access/check/ComboboxField.vue`,
  `access/nodes/NodeAddField.vue` (which repeats the keyboard logic), `access/TeamParentsCard.vue` and
  `access/nodes/NodeTemplateMenu.vue` (which uses a native `datalist`) move onto `MdListbox` and one `useCombobox`
  composable. The `z-10`, `z-20` and `z-50` literals go.
- **Inline errors.** About fifteen inline error paragraphs (`role="alert"` on `md-body-medium text-error`) become
  `MdAlert` with the error tone. Each rendered element keeps its `data-ingot-state="error"` attribute.
- **Error, loading and empty states.** Each panel that shows a failed load with a Retry, a loading line or an empty
  sentence becomes `MdStatePanel` (error, loading and empty states) or `MdSkeleton` (loading placeholders). Static copy
  loses `role="alert"`.
- **Banners.** Notices that stay on screen (`account/AccountView.vue`, `dashboard/InstanceStatusPanel.vue`,
  `support/SettingsEncryptionAlert.vue`, `settings/SettingsImportErrors.vue`, `migration/ProblemAlert.vue`,
  `migration/ConnectStep.vue`) become `MdAlert` with one radius and one spacing.
- **Snackbar placement.** `MdSnackbar` already uses the 600px breakpoint and a 16px inset. `DESIGN.md` says "the `md`
  breakpoint" and must say 600px. Its `<style scoped>` block is removed by the catalogue change, which this change
  relies on.
- **Decisions in `DESIGN.md`.** The confirm button rule (question 2), the dialog size variants (question 1 and 3), the
  trailing side sheet and the status primitives are written down there, and the "Known gaps" entry that lists a missing
  dialog and drawer primitive is removed.
- **Baseline.** Each migrated file leaves the baseline that `add-ui-style-gate` creates. The baseline shrinks in the same
  commit as the file, and no new entry is added.

Anchors do not change. `data-ingot-component` values (for example `create-token-dialog`, `revoke-token-dialog`,
`dialog-shell`, `new-team-dialog`, `problem-alert`) stay on the element they are on today, the heading or the root. The
`dialog-shell` name stays with the frame that `MdDialog` renders.

Not BREAKING. No configuration key, environment variable, deployment manifest, route, permission node, settings API,
plugin API or stored data changes. Visible changes are the dialog frames (inset, width, elevation, scrim), menu and
listbox behaviour (keyboard, disabled items, focus rings), and the appearance of error and empty messages. These are
listed in the release notes. A plugin that styles an anchor keeps matching, because no anchor is renamed or removed.

### Shipping

Proposal PR `docs(openspec): propose unify-dialogs-menus-and-feedback`, containing only the files under
`openspec/changes/unify-dialogs-menus-and-feedback/`, with tasks unchecked, branched from the remote main branch.

Code PR, after the proposal is merged, under the type `fix` and the scope `ui`:

`fix(ui): put dialogs, menus and status messages on shared components`

Commits follow the rollout in `tasks.md`: one `test(ui)` commit (red tests) and one `fix(ui)` commit per area, in the
order identity, access, maintenance, settings, dashboard, migration, renderers and header, plus a `docs(ui)` commit for
`DESIGN.md`. The type `fix` is used because the change corrects inconsistent behaviour that already ships; the
behaviour that is new (side sheet end side, disabled menu items) is a supporting detail of the fix. No file under
`openspec/` is in the code PR.

### Dependencies

- `add-md3-component-catalogue` must be merged first. It provides `MdDialog`, `MdAlert`, `MdStatePanel`, `MdSkeleton`
  and `MdListbox`, and it removes the `<style>` block of `MdSnackbar`. This change does not add those primitives.
- `add-ui-style-gate` must be merged first, because this change shrinks its baseline file. This
  change does not create the gate.
- `refactor-md3-token-utilities` supplies the Tailwind utilities for the tokens this change uses (the dialog and menu
  stacking utilities `z-md-dialog`, `z-md-menu` and `z-md-snackbar`, the scrim opacity `md-scrim`, and the window classes).
  Where a token is missing, this change adds it to `shape-elevation-motion.css`
  and says so in `DESIGN.md`.
- `fix-surface-hierarchy-and-page-header` and `migrate-content-to-components` run in parallel. Cards, lists, tables and
  forms are out of scope here. Where a dialog contains a form, the form's own fields are not changed by this change.

### Out of scope

- Cards, lists, tables and forms, which belong to `migrate-content-to-components`. A dialog's body is not restyled beyond
  the frame, the action row and the status message.
- The contents, labels and wording of dialogs, menus and messages. The only copy change is that a message that is not an
  error loses its alert role.
- Tabs, drawers that are not side sheets, and the navigation rail.
- The sign-in flow in `header/LoginModal.vue` beyond its frame. Its fields, validation and provider links keep their
  behaviour.
- New colours, new `--md-*` role tokens inside a view, and raw hex values. A missing role goes to `color.css` in the
  catalogue or refactor change, never into a view.
- Any change under `reposilite-backend/`.

## Capabilities

### New Capabilities

- `dashboard-overlays-and-feedback`: how the dashboard draws dialogs, side sheets, menus, listboxes and status messages,
  how they take and return focus, and which element announces them.

## Impact

- **Operators:** nothing to change. Dialogs, menus and messages look and behave the same in content, with a consistent
  frame, consistent focus and consistent status text.
- **Plugin authors:** nothing to change. Anchors are unchanged. A plugin stylesheet that targets the old per-file frame
  classes must use the anchor instead; such selectors are not part of the documented contract.
- **Code:** the files in `reposilite-frontend/src/components/` listed under What Changes, `components/identity/DialogShell.vue`
  (removed once no file imports it), `settings/dialog.js` (`SCRIM_STYLE` removed once no file imports it), `src/anchors/registry.json`
  (aliases only where a name changes, which this change does not plan), and `reposilite-frontend/DESIGN.md`. No file under
  `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md` only. The gitbook reference is not changed, because no operator-facing
  setting changes.

## Verification

- Every red test in `tasks.md` is seen to fail first, in its `test(ui)` commit.
- `npm test` in `reposilite-frontend` passes. Targeted test files run while working, the full suite through `heavy`.
- The Playwright suites `accessibility.spec.js`, `layout.spec.js` and `navigation.spec.js` pass at 320, 1280 and 1440
  pixels.
- Before and after screenshots are attached to the code PR at 320, 390, 600, 1280 and 1440 pixels, in light and dark, for
  each dialog family (identity, access, maintenance, settings, migration, login), one menu, one combobox and each state
  panel. The widths 320, 600 and 1440 are the binding set; 390 and 1280 are added for the dialog families.
- `openspec validate unify-dialogs-menus-and-feedback --strict` passes, and the dash grep prints nothing.

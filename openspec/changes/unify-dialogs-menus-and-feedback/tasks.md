# Tasks

Each group is one or more commits of one Conventional Commits type. The code PR is squash-merged under its title, so the
title is the commit on main. Every test is written first and seen to fail before the production code exists (the `test(ui)`
commit of each area comes before its `fix(ui)` commit). Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no network, no shared static state, a fresh component and teleport target per test, assertions with messages. Focus is
read from `document.activeElement`. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, the
full suite through `heavy`, with Gradle (if any) at `--max-workers=2`. No file under `reposilite-backend/` changes.

Each area's `fix(ui)` commit removes the migrated files from the `add-ui-style-gate` baseline and adds no entry. The gate is
merged before this change, so the baseline exists when the first area is migrated.

## 1. docs(openspec): propose the change

- [ ] 1.1 `docs(openspec): propose unify-dialogs-menus-and-feedback`: this change, nothing else. Verify:
  `openspec validate unify-dialogs-menus-and-feedback --strict` passes and the dash grep prints nothing.


## 2. test(ui): red tests for the shared components

The primitive tests (the `MdDialog` focus contract and size range, the `MdSideSheet` `side` prop and width tokens, the
`MdOverflowMenu` disabled and two-line items, `useCombobox` and `MdListbox`, and the `MdSnackbar` placement, layer and missing
`<style>` block) belong to `add-md3-component-catalogue` (its tasks 2.5, 2.14, 2.15, 2.17 and 2.25) and are not written again
here.

- [ ] 2.1 `test(ui)`: in `src/anchors/anchors.test.js`, assert that `dialog-shell`, `new-team-dialog`, `create-token-dialog`,
  `revoke-token-dialog` and `problem-alert` are still in `registry.json` and that each is reported where its file carries it.
  Verify: it passes before the migration and is kept as the guard.
- [ ] 2.2 `test(ui)`: in `src/components/md/MdDialog.test.js`, assert that `size="compact"` renders full screen below the
  `medium` window class and the `lg` frame from it (a class check), if question 3 is answered with the default. Verify: it
  fails, because the size does not exist.


## 3. Primitive check and the one extension

- [ ] 3.1 No commit. Confirm on the remote main branch that `add-md3-component-catalogue` is merged and that `MdDialog`,
  `MdAlert`, `MdStatePanel`, `MdSkeleton`, `MdListbox`, `src/helpers/useCombobox.js`, the `MdSideSheet` `side` prop with
  `--container-md-sheet-wide`, and the `MdOverflowMenu` disabled and two-line items exist with their tests green. Verify: each
  is present; a missing one stops this change and goes back into the catalogue change (proposal question 4).
- [ ] 3.2 `fix(ui)`: add the `compact` size to `MdDialog.vue` (question 3). Verify: 2.2 passes and the existing `MdDialog`
  tests pass unchanged.


## 4. test(ui): red tests per area, one test(ui) commit per area

- [ ] 4.1 `test(ui)`: in `CreateTokenDialog.test.js`, `RevokeTokenDialog.test.js`, `RotateTokenDialog.test.js` and
  `SecretOnceDialog.test.js`, add the dialog assertions of D9 (labelledby, initial focus, focus return, Escape, one filled button)
  where they are missing, and assert `data-ingot-state="error"` on the rotation error. Verify: they fail on the missing
  attribute and on the hand-built heading.
- [ ] 4.2 `test(ui)`: in the test files of `AddMemberDialog`, `ConfirmActionDialog`, `NewMachineDialog`, `NewTeamDialog`,
  `NewUserDialog`, `ResetPasswordDialog` and `SsoProviderDialog`, assert the dialog assertions of D9; in `NewMachineDialog.test.js`
  assert that two mounted instances have different heading ids (no fixed `new-machine-title`). In `PrincipalActionsMenu.test.js`,
  assert the disabled and two-line items through `MdOverflowMenu` and the focus ring on the destructive item. In
  `check/ComboboxField` and `nodes/NodeTemplateMenu` tests, assert the listbox roles and the keyboard choice. Verify: they fail.
- [ ] 4.3 `test(ui)`: in the test files of `PolicyDialog`, `RebuildIndexDialog`, `RunNowDialog`, `DeletePolicyDialog` and
  `CleanupPoliciesPanel`, assert the dialog assertions of D9, the `alertdialog` role on `DeletePolicyDialog`, the cancel button
  focused first on `DeletePolicyDialog` and the filled error confirm named "Delete policy". Verify: they fail.
- [ ] 4.4 `test(ui)`: in the test files of `ConflictDialog`, `DeleteEntryDialog`, `NewEntryDialog`, `FactoryResetModal`,
  `PasswordDialog` and `RepositoryWizard`, assert the dialog assertions of D9; in `PasswordDialog.test.js` assert that the dialog
  is teleported and focused on open (`PasswordDialog.vue:67` today), and that the password fields are `MdTextField` with
  `type="password"`; in `SettingsReviewSheet` and `SettingsDownloadMenu` tests, assert the `end` side and the menu items. Verify:
  they fail.
- [ ] 4.5 `test(ui)`: in the test files of `PublicRepositoriesDialog`, `ReadOnlyToggle`, `FailuresSheet`, `ChartCard`,
  `GettingStartedCard` and `InstanceStatusPanel`, assert the dialog assertions of D9 for the dialogs, the `end` side and the sheet
  focus return for `FailuresSheet`, the error state attribute on `ReadOnlyToggle`, `GettingStartedCard` and the Retry of
  `ChartCard` and `FailuresSheet`. Verify: they fail.
- [ ] 4.6 `test(ui)`: in `MigrationDialog.test.js`, `MappingStep.test.js` and `CutOverStep.test.js`, assert the dialog assertions of
  D9, that the own focus trap of the dialog is gone (the focus trap is the one of `MdDialog`), and that the destructive confirmations
  use the `error` prop. Verify: they fail.
- [ ] 4.7 `test(ui)`: in `OneOfSelectRenderers.test.js` and `OneOfVariantSwitch.test.js`, assert the dialog assertions of D9, a
  named tab panel (the `tabpanel` at `OneOfRenderer.vue:20` has no name today), no `min-w-[280px]` or `max-w-[560px]` literal, and a
  filled confirm button (D3). Verify: they fail.
- [ ] 4.8 `test(ui)`: in `LoginModal.test.js`, assert the dialog assertions of D9 (the login dialog is named by its heading, focus
  starts on the first field, focus returns to the account button, Escape closes it). Verify: it fails on the own overlay style.

## 5. fix(ui): migration per area, one fix(ui) commit per area in the order identity, access, maintenance, settings, dashboard, migration, renderers, header

- [ ] 5.1 `fix(ui)`: move `CreateTokenDialog.vue`, `RevokeTokenDialog.vue`, `RotateTokenDialog.vue` and `SecretOnceDialog.vue` to
  `MdDialog` with `header` and `actions` slots; keep `create-token-dialog`, `revoke-token-dialog` and their anchors on the
  same elements; replace the inline errors at `CreateTokenDialog.vue:357` and `:373`, `RevokeTokenDialog.vue:112` and
  `RotateTokenDialog.vue:92` with `MdAlert` in the error tone; make the static copy of `SecretOnceDialog.vue:175` a notice without
  an alert role; replace the alert in `identity/TokenList.vue:176` with `MdAlert`. Verify: 4.1 passes.

- [ ] 5.2 `fix(ui)`: migrate the seven dialogs to `MdDialog`, deleting their frames; move `NewMachineDialog` to `useId`
  (its fixed id is at line 98); replace the inline errors (`AddMemberDialog.vue:130` and `:178`, `ConfirmActionDialog.vue:124`,
  `NewMachineDialog.vue:141`, `NewTeamDialog.vue:137`) with `MdAlert`. Delete `identity/DialogShell.vue` in this commit, when
  its last importer is migrated, and leave `dialog-shell` on `MdDialog`.
- [ ] 5.3 `fix(ui)`: move `PrincipalActionsMenu.vue` onto `MdOverflowMenu` (the disabled and two-line items, the focus ring of the
  destructive item without `!text-error`, `PrincipalActionsMenu.vue:201` and `:266`); move `check/ComboboxField.vue:166`,
  `nodes/NodeAddField.vue` (lines 52 to 217, the duplicated keyboard logic) and `TeamParentsCard.vue:317-360` onto `MdListbox` and
  `useCombobox`; move `nodes/NodeTemplateMenu.vue:206-218` from the native `datalist` to `MdListbox`; update
  `nodes/NodeEditor.test.js:400`, which selects `datalist option`; remove the `z-20` and `z-50` literals of these files.
  Verify: 4.2 passes.
- [ ] 5.4 `fix(ui)`: state panels in the access area. Failed loads with Retry move to `MdStatePanel` (error, tonal Retry) in
  `AuthenticationGroupForm.vue:107`, `LdapPanel.vue:126`, `PrincipalDetail.vue:160`, `PermissionNodesCard.vue:137`,
  `PrincipalsPanel.vue:165`, `TeamsPanel.vue:122`, `TeamDetail.vue:236`, `TeamParentsCard.vue:261` and `SsoProvidersPanel.vue:243`.
  Loading lines (`AuthenticationGroupForm.vue:100`, `LdapPanel.vue:119`, `TeamsPanel.vue:135`, `TeamDetail.vue:220`,
  `TeamParentsCard.vue:253`, `SsoProvidersPanel.vue:236`, `check/CheckPermissionView.vue:420`) move to `MdSkeleton` with a
  `MdStatePanel` line. Empty states (`TeamsPanel.vue:187` and `:207`, `TeamMembersCard.vue:164`, `SsoProvidersPanel.vue:304`,
  `check/CheckPermissionView.vue:628`) use the same empty state. Inline errors (`AuthenticationGroupForm.vue:148` and `:155`,
  `LdapPanel.vue:347`) become `MdAlert`. Verify: the tests of these files pass with state assertions.

- [ ] 5.5 `fix(ui)`: migrate `PolicyDialog.vue` (lines 116 to 120, the `max-w-xl` frame), `RebuildIndexDialog.vue` (71 to 75),
  `RunNowDialog.vue` (78 to 82), `DeletePolicyDialog.vue` (47 to 55, `alertdialog`) and the preview of
  `CleanupPoliciesPanel.vue` (337 to 348) to `MdDialog`. The preview keeps the 560px cap unless question 1 is answered with the
  alternative; the table scrolls inside the dialog body. Replace the inline errors in the maintenance area and the state panels of
  `StorageUsagePanel.vue` (149 and 163) and `RunHistory.vue` (84) with `MdStatePanel`. Verify: 4.3 passes.

- [ ] 5.6 `fix(ui)`: migrate `ConflictDialog.vue` (49 to 58, own `overlayStyle` at line 39), `DeleteEntryDialog.vue` (89 to 95),
  `NewEntryDialog.vue` (185 to 190; remove the `[--md-field-container:...]` override, `NewEntryDialog.vue:96`), `FactoryResetModal.vue`
  (15 to 49), `PasswordDialog.vue` (92 to 101, the password inputs at 108 to 133 become `MdTextField`, the inline error at 136
  becomes `MdAlert`) and `RepositoryWizard.vue` (the frame at 325 to 340 becomes the compact size below 600 pixels if question 3
  is answered with the default). The wizard's error paragraphs (`role="alert"` at lines 383, 645, 763 and 915) become `MdAlert`
  in one size. Move `SettingsReviewSheet.vue:95` onto `MdSideSheet` with the `end` side. Move `SettingsDownloadMenu.vue` (375 to 401)
  onto `MdOverflowMenu`; remove its `z-20`. Verify: 4.4 passes.
- [ ] 5.7 `fix(ui)`: status and notices of the settings area. Replace the alerts of `settings/SettingsImportErrors.vue:46` with
  `MdAlert` in the same radius and spacing as `support/SettingsEncryptionAlert.vue:40`; replace the inline errors of
  `settings/EntryDetail.vue:415` and `:428`, `settings/RepositoryMembersPicker.vue:504` and the empty states of `EntryList.vue:199`,
  `EntryDetail.vue:249` and `CollectionEditor.vue:182` with `MdStatePanel`; replace the notices and the loading line of
  `settings/SettingsView.vue` (lines 528, 609, 616) with `MdAlert`, `MdSkeleton` and `MdStatePanel`; remove the `<style>` block at
  `SettingsView.vue:685` and move its rules into utilities or tokens. Verify: the settings tests pass with state assertions, and
  the style gate reports no `<style>` block in `SettingsView.vue`.

- [ ] 5.8 `fix(ui)`: migrate `PublicRepositoriesDialog.vue` (65 to 73) and `ReadOnlyToggle.vue` (155 to 161, the error at 143) to
  `MdDialog`; migrate `FailuresSheet.vue` (130 to 142, the `min-[840px]:w-[28rem]` width) to `MdSideSheet` with the `end` side; the
  Retry at `FailuresSheet.vue:173` becomes a tonal `MdButton` in `MdStatePanel`, and the empty state at `:189` uses `MdStatePanel`.
  Replace `ChartCard.vue` Retry (116), empty state (136) and skeleton (`h-[220px]` at lines 76 and 82) with `MdStatePanel` and
  `MdSkeleton`. Replace `GettingStartedCard.vue:144` with `MdAlert`. In `InstanceStatusPanel.vue`, the banners (220 to 269) become
  `MdAlert` and the skeleton (246 to 254) becomes `MdSkeleton`. Verify: 4.5 passes.
- [ ] 5.9 `fix(ui)`: the state panels of the other dashboard-adjacent views, `console/ConsoleView.vue:231` (Retry) and `:308`
  (empty), `overview/RepositoryOverview.vue:351` (Retry), `search/SearchView.vue:431` and `:451` (Retry), `:467` (empty), and the
  skeleton of `:422` (`h-[88px] animate-pulse`). Verify: the tests of these views pass with state assertions.

- [ ] 5.10 `fix(ui)`: migrate `MigrationDialog.vue` (78 to 108; its own Teleport, focus trap, `bg-scrim/40` and `z-50`) to
  `MdDialog`. Set `error` on the destructive confirmations `MappingStep.vue:266` and `CutOverStep.vue:332`, which are filled today.
  Replace `migration/ProblemAlert.vue:37` and `migration/ConnectStep.vue:148` (the same string, shown in two styles) with one
  `MdAlert`. Verify: 4.6 passes.

- [ ] 5.11 `fix(ui)`: migrate the dialog frame of `OneOfRenderer.vue` (31 to 75) to `MdDialog`, name the tab panel, make the
  confirm button filled, and remove the `SCRIM_STYLE` import at line 98. Verify: 4.7 passes.

- [ ] 5.12 `fix(ui)`: migrate the frame of `LoginModal.vue` (269 to 520, its own overlay style at 268) to `MdDialog`, keeping its
  fields, validation and provider links unchanged. Verify: 4.8 passes.


## 6. docs(ui): design rules

- [ ] 6.1 `docs(ui)`: in `reposilite-frontend/DESIGN.md`, state the `MdDialog` size range, inset, `role` and stacking value
  beside the existing dialog rule; the focus contract of D2; the confirm button rule of D3; the `MdSideSheet` `side` prop and the
  width token; the `MdOverflowMenu` item fields and the `MdListbox` and `useCombobox` contract; the status primitives and their
  states (D6); the snackbar breakpoint, corrected from the `md` breakpoint to 600px; and the `z-md-menu` layer of the listbox popup. Remove "No tabs, list, dialog or drawer primitive" from "Known gaps" for the parts that now exist. Verify: the dash grep
  prints nothing, and `grep -n "md breakpoint" reposilite-frontend/DESIGN.md` finds nothing.


## 7. fix(ui): cleanup

- [ ] 7.1 `fix(ui)`: remove `SCRIM_STYLE` from `settings/dialog.js` once nothing imports it (last importer: `RepositoryWizard.vue`
  or `SettingsReviewSheet.vue`, whichever area migrates last), and delete the file if it holds nothing else. Verify:
  `grep -rn SCRIM_STYLE reposilite-frontend/src` finds nothing, and the frontend tests pass.
- [ ] 7.2 `fix(ui)`: remove any migrated file that the `add-ui-style-gate` baseline still lists, and confirm the baseline has no new
  entry. Verify: the style gate passes with the baseline as shrunk.


## 8. Check, no commit

- [ ] 8.1 Run `npm test` in `reposilite-frontend` through `heavy`, then `accessibility.spec.js`, `layout.spec.js` and
  `navigation.spec.js` at 320, 1280 and 1440 pixels. Verify: all pass, and each changed expectation of an existing test is
  listed in the pull request with the reason.
- [ ] 8.2 Take before and after screenshots with Playwright at 320, 390, 600, 1280 and 1440 pixels, light and dark, of each dialog
  family (identity, access, maintenance, settings, migration, login), one menu, one combobox and each state panel (error, loading,
  empty). Verify: all sets are present, and they are attached to the pull request, not committed.
- [ ] 8.3 Run `openspec validate unify-dialogs-menus-and-feedback --strict` and the dash grep over this change. Verify: both pass.


## 9. Pull request

- [ ] 9.1 Open the code pull request under the title `fix(ui): put dialogs, menus and status messages on shared components`,
  from the remote main branch that already contains the merged proposal and the merged code of `add-md3-component-catalogue`,
  `add-ui-style-gate` and `refactor-md3-token-utilities`, with no file under `openspec/`. Fill the repository's pull request
  template, list the changed test expectations, and attach the screenshot sets of 8.2.

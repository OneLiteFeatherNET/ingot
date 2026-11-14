# Tasks

Each group is one or more commits of one Conventional Commits type. The change ships under `fix(ui)`. Its supporting commits carry
`test(ui)` (the red tests), `build(ui)` (the style gate baseline) and `docs(gitbook)` (the generated anchor reference), as in
`add-administration-overview`. Every red test is written first and seen to fail before the production code exists. Every test follows
F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, a fresh mount or a fresh input per test, no shared module state,
assertions with messages. Vitest and Playwright runs go through the slot runner (`heavy`). Each wave is one pull request, merged before
the next wave starts (open question 1 in `proposal.md`). Line numbers come from the maintainer's inventory and are re-read at the start
of each wave. No file under `reposilite-backend/` and no file under `openspec/` changes in a code commit.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose migrate-content-to-components`: this change only, tasks unchecked, branched from the remote main
  branch. Verify: `openspec validate migrate-content-to-components --strict` passes and the dash grep prints nothing.

## 2. Preconditions and inventory (no commit)

- [ ] 2.1 Check that `add-md3-component-catalogue`, `fix-surface-hierarchy-and-page-header`, `add-ui-style-gate` and
  `refactor-md3-token-utilities` are merged. Verify: `src/components/md/` holds `MdSection`, `MdList`, `MdListItem`, `MdListDetail`,
  `MdKeyValueList`, `MdDisclosure`, `MdBadge`, `MdDataTable`, `MdTabs`, `MdCodeBlock`, `MdCopyableCode`, `MdCode` and
  `MdNavigationItem`, and `src/style.css` declares the named breakpoints of `refactor-md3-token-utilities`. If one is missing,
  stop before wave 1.
- [ ] 2.2 Record the start counts in the wave 1 pull request description: files with `rounded-corner-medium` or `rounded-corner-large`
  outside `src/components/md/`, files with `<style`, files with `sm:`, `md:`, `lg:` or `xl:`, files with `font-mono`, and the
  `data-ingot-component` count. Verify: the counts match the design (60, 7, 15/5/3/2, 24, 177), or the difference is explained.
- [ ] 2.3 Grep `docs/gitbook/` and `src/` for the class names and hooks this change removes (`settings-view`, `settings-save-bar`,
  `settings-hub`, `repository-snippet`, `artifact-snippet`, `vue-upload-component`, `font-mono`). Verify: no documented plugin example
  uses a removed class; the result is recorded in the proposal's Not BREAKING paragraph, and any hit makes the change BREAKING.

## 3. Wave 1: cards and sections

- [ ] 3.1 `test(ui)`: in `src/components/md/MdCard.test.js`, assert that `padding="none"` removes the padding and keeps the radius; in
  `src/components/md/MdSection.test.js` (added by the catalogue change), assert the `md-title-medium` class at `level` 2 and
  `md-title-small` at `level` 3. Verify: they fail, because `none` and the level type classes do not exist yet.
- [ ] 3.2 `test(ui)`: in the existing tests of `LdapPanel`, `PermissionNodesCard`, `TeamMembersCard`, `TeamDetail`, `PasswordCard`,
  `ProfileCard`, `AuditView`, `FileList`, `SettingsView` and `TokenList`, assert that the card is an `md-card` anchor element and that its
  section heading is an `md-section` (level 2 or 3). Select by anchor or `data-test`, never by a utility. Verify: they fail on the
  hand-built containers.
- [ ] 3.3 `test(ui)`: in `src/components/renderers/SectionRenderer.test.js`, assert that nested sections clamp at level 3 and that no
  heading is level 4. Verify: it fails, because the renderer emits a fixed level 3 at every depth.
- [ ] 3.4 `fix(ui)`: add the `none` value to the `padding` prop of `MdCard.vue` and the level type classes to the `level` prop
  of `MdSection.vue` (`src/components/md/`). Verify: 3.1 passes.
- [ ] 3.5 `fix(ui)`: replace hand-built card containers with `MdCard` in `src/components/access/` (LdapPanel, LegacyGrantsPanel,
  PrincipalDetail, PermissionNodesCard, TeamMembersCard, TeamDetail, TeamParentsCard, TeamLegacyGrants, SsoProvidersPanel,
  SsoProviderDialog, check/CheckPermissionView), in `src/components/account/` (PasswordCard, ProfileCard), `src/components/audit/`
  (AuditView, AuditEventRow), `src/components/browser/` (FileUpload, FileBrowserView, FileList), `src/components/dashboard/FailuresSheet.vue`,
  the maintenance tiles (CleanupPoliciesPanel, IndexPanel), `src/components/plugins/PluginBlock.vue`,
  `src/components/renderers/ArrayListRenderer.vue`, `src/components/repositories/WritePolicyField.vue`, the settings cards
  (AccessDomainCard, AppearancePreview, EntryDetail, RepositoryAccessCard, SettingsReviewSheet, SettingsView) and
  `src/components/identity/TokenList.vue`. Split into one commit per folder group for review. Verify: 3.2 passes and no migrated file
  carries `rounded-corner-` or `p-` on its card root.
- [ ] 3.6 `fix(ui)`: replace the section headers with `MdSection` in `LdapPanel`, `PermissionNodesCard`, `SecurityPanel`,
  `CleanupPoliciesPanel`, `IndexPanel`, `StorageUsagePanel`, `DashboardView`, `AnalyticsView`, `SystemInfoView`, `SyncStep`,
  `VerifyStep`, `RepositoryOverview`, and fix the heading sizes in `SettingsHub`, `SupportView`, `SsoProvidersPanel`,
  `SsoProviderDialog` and `AppearancePreview` to the level scale. `SettingsHub` is excluded if `add-administration-overview` has not merged.
  Fix `SectionRenderer` (3.3) and the level 4 heading of `RepositoryAccessCard`. Verify: 3.2 and 3.3 pass.
- [ ] 3.7 `docs(gitbook)`: not needed in wave 1 unless a registry name is added; if `md-section` or `md-card` is added to the registry,
  run `npm run anchors:doc` and commit `docs/gitbook/reference/dashboard-anchors.md` in the same commit as the registry. Verify:
  `anchors.test.js` passes.
- [ ] 3.8 Wave 1 screenshots (no commit): before and after at 320, 600 and 1440 pixels, and the wave's own at 390, 840, 1280 and 1920
  pixels, in light and dark. Verify: the images are attached to the wave 1 pull request description.
- [ ] 3.9 Open the wave 1 pull request under the title `fix(ui): build cards and sections from shared components`, from the remote main
  branch, with the template filled and no file under `openspec/`.

## 4. Wave 2: lists and rows

- [ ] 4.1 `test(ui)`: in `src/components/md/MdListItem.test.js`, assert `aria-current="true"` on the selected row only, the 56 pixel and
  48 pixel minimum height classes through a `data-test` on the row, and the divider drawn by the list. Verify: it fails, because the row
  does not take `selected` yet.
- [ ] 4.2 `test(ui)`: in `src/components/access/PrincipalsPanel.test.js` and `TeamsPanel.test.js`, assert that exactly one row is selected
  for the route, and that the selection is computed once (a spy on the selection function counts one call per render). Verify: it fails
  on the four copies.
- [ ] 4.3 `test(ui)`: in `DetailedListEntry.test.js` and `EntryList.test.js`, assert that each link row is an `md-navigation-item` link
  with the current state when its route is open. Verify: it fails.
- [ ] 4.4 `fix(ui)`: set `aria-current` and the 56 and 48 pixel row heights on the catalogue's `MdListItem`, and draw the divider
  of `MdList` with `divide-y`. Verify: 4.1 passes.
- [ ] 4.5 `fix(ui)`: migrate selectable rows (TeamsPanel, PrincipalsPanel, EntryList, AddMemberDialog) to `MdList` and `MdListItem`,
  and compute the selected key once per list. The `MdListDetail` panes of TeamsPanel, PrincipalsPanel and CollectionEditor are already
  done by `fix-surface-hierarchy-and-page-header` (its task 4.10). Verify: 4.2 passes.
- [ ] 4.6 `fix(ui)`: migrate link rows (DetailedListEntry, FileList, SettingsAttention, the More panel of `IndexPage`) to
  `MdNavigationItem`; migrate divided lists (NodeEditor, NodeInheritedSection, FileUpload, RunHistory, StorageUsagePanel,
  RepositoryOverview's divider, TokenList, LegacyGrantsPanel, InstanceStatusPanel, GettingStartedCard, PublicRepositoriesDialog) to
  `MdList`; remove the arbitrary `[&>li+li]:after` chain of `FileList`. Verify: 4.3 passes and `grep -rn "li+li" src` finds nothing.
- [ ] 4.7 `fix(ui)`: migrate node rows (NodeRow, NodeInheritedSection), renderer rows (ConstantRenderer, UnsupportedRenderer,
  BooleanSwitchRenderer with one `px-4` inset) and checklist items (CutOverStep) to `MdListItem`. Verify: `npm test` passes for the files touched.
- [ ] 4.8 `fix(ui)`: migrate the key and value layouts (PrincipalDetail, TeamDetail, CheckPermissionView, ProfileCard, AuditEventRow,
  InstanceStatusPanel, IndexPanel, PluginBlock, SystemInfoView, SettingsHub, TokenList) to `MdKeyValueList`, the disclosures
  (TeamLegacyGrants, SsoProviderDialog, NodeInheritedSection, RunHistory, ArrayListRenderer) to `MdDisclosure`, and the status labels
  (SsoProvidersPanel, NodeInheritedSection, InstanceStatusPanel, SearchResultRow, RepositoryOverview, SettingsReviewSheet, TypeBadge) to
  `MdBadge`; `TokenTag` and `RepositoryTypeBadge` wrap `MdBadge` and keep their anchors, unless the catalogue change already did so
  (its task 3.16). Verify: the tests of each file pass.
- [ ] 4.9 `test(ui)`: in `src/components/md/MdDisclosure.test.js`, assert that `aria-expanded` toggles, the content is hidden while
  collapsed, and focus stays on the control. Verify: it fails before 4.8 is applied to `RunHistory`, then passes.
- [ ] 4.10 `fix(ui)`: drop the redundant `m-0`, `p-0` and `list-none` resets (TeamsPanel and the ten siblings), and replace
  `[overflow-wrap:anywhere]` with `break-words` or `break-all` on the identifier element (TeamsPanel, TeamMembersCard, TeamParentsCard,
  TeamLegacyGrants, AddMemberDialog, PrincipalsPanel, NodeInheritedSection, MdChipInput, EntryList, RepositoryWizard). Verify: no
  `overflow-wrap` arbitrary value remains in `src`.
- [ ] 4.11 `docs(gitbook)`: regenerate `docs/gitbook/reference/dashboard-anchors.md` with `npm run anchors:doc` if a name was added in
  wave 2. Verify: `anchors.test.js` passes.
- [ ] 4.12 Wave 2 screenshots (no commit), at the same widths as 3.8, attached to the wave 2 pull request.
- [ ] 4.13 Open the wave 2 pull request under the title `fix(ui): build lists and rows from shared components`.

## 5. Wave 3: tables and code

- [ ] 5.1 `test(ui)`: in a new `src/components/md/MdDataTable.sort.test.js`, assert the pure `sortRows(rows, column, direction)` on plain
  arrays: ascending, descending, a stable order for equal keys, and no mutation of the input. Verify: it fails, because the function does
  not exist.
- [ ] 5.2 `test(ui)`: in `src/components/md/MdDataTable.test.js` (added by the catalogue change, which already asserts `aria-sort`),
  assert that a sortable header holds a button, that a second activation reverses the order through `sortRows`, and that a column
  with a priority carries its window class.
  Verify: it fails.
- [ ] 5.3 `test(ui)`: in `src/components/repositories/RepositoriesTable.test.js`, assert the four sortable headers sort the rows, the
  actions cell has the shared cell padding, and the anchors `repositories-table` and `repositories-view` both resolve. Verify: it fails
  on the hand-built table.
- [ ] 5.4 `test(ui)`: in `src/components/card/SnippetsCard.test.js`, assert that each snippet is an `MdCopyableCode` inside an
  `MdCodeBlock`, that the anchors `repository-snippet` and `artifact-snippet` match the snippet roots, and that the component source has no
  `<style` element. Verify: it fails, because the `<style>` block is still there.
- [ ] 5.5 `fix(ui)`: add `src/components/md/MdDataTable.sort.js` and use it, with the window-class priorities, in the catalogue's
  `MdDataTable.vue`. Verify: 5.1 and 5.2 pass.
- [ ] 5.6 `fix(ui)`: migrate `src/components/repositories/RepositoriesTable.vue` to `MdDataTable` inside the `repositories-table`
  component; drop the `min-w-[40rem]` literal and the per-width column classes in favour of priorities. Verify: 5.3 passes.
- [ ] 5.7 `fix(ui)`: migrate the dry-run and migration tables (`src/components/maintenance/DryRunResult.vue`, the migration `DiscoverStep`,
  `MappingStep`, `CutOverStep` with its row header in column 2, `DryRunStep` with its hand-built select, search and sort button, `SyncStep`,
  `VerifyStep`, `PluginBlock`) to `MdDataTable`. Verify: the tests of each file pass.
- [ ] 5.8 `fix(ui)`: migrate `SnippetsCard.vue` to the code primitives, drop its `<style>` block and the app-wide scrollbar rule, and the
  nested `px-2` insets (lines 174, 181 and 211 of the inventory). Verify: 5.4 passes.
- [ ] 5.9 `fix(ui)`: migrate `RepositorySnippet.vue` (four identical `pre` elements to one `MdCodeBlock` with `MdCopyableCode` rows),
  `ArtifactSnippet.vue`, `FailuresSheet.vue` (`text-xs leading-5` to `md-label-medium`), `SecretOnceDialog.vue` (`text-sm`, `text-xs`),
  `CutOverStep.vue`, `RepositoryWizard.vue`, `SystemInfoView.vue`, `ConsoleView.vue`, and every other `font-mono` use. `MdCode` and
  `MdCodeBlock` replace hand-written monospace. `CodeBrackets` and `CodeString` keep their anchors. Verify: `grep -rn "font-mono" src`
  finds only the code primitives.
- [ ] 5.10 `fix(ui)`: add `@utility md-scrollbar` (the thin scrollbar of the removed app-wide rule, from colour tokens) to
  `src/style.css`, and use it only where a code block needs it. Verify: the
  scrollbar of a page without code is the browser default in the Playwright screenshot.
- [ ] 5.11 `docs(gitbook)`: regenerate `dashboard-anchors.md` if wave 3 added a name. Verify: `anchors.test.js` passes.
- [ ] 5.12 Wave 3 screenshots (no commit), including a page with a scrollbar before and after the removal of the app-wide rule.
- [ ] 5.13 Open the wave 3 pull request under the title `fix(ui): build tables and code samples from shared components`.

## 6. Wave 4: forms and controls

- [ ] 6.1 `test(ui)`: in `src/components/md/MdFieldGrid.test.js`, `MdFieldset.test.js` and `MdSaveBar.test.js`, assert one column below
  the `expanded` window class and two from it (class per window), that a fieldset draws a `legend` and not a `span` for its title, and that
  the save bar keeps the bottom clearance as a class. Verify: they fail, because the components do not exist.
- [ ] 6.2 `test(ui)`: in `src/components/renderers/useFieldBinding.test.js`, assert the mapping from JSON Forms props to primitive props for
  a text field, a text area, a select, a colour field and a tags field, with plain input objects. Verify: it fails, because the composable
  does not exist.
- [ ] 6.3 `test(ui)`: in the tests of `CreateTokenDialog`, `PolicyDialog`, `SearchFilters`, `AuditView`, `PasswordCard` and `FileUpload`,
  assert that every text field has variant `outlined` (the default), that radio buttons and checkboxes are primitives, and that the button
  of `DryRunStep` and `SupportView` is an `MdButton`. Verify: it fails on the mixed variants and the hand-built controls.
- [ ] 6.4 `test(ui)`: in `src/components/md/MdTextField.test.js`, assert that the default variant is `outlined` and that a `filled` prop
  still works. Verify: it fails on the current default.
- [ ] 6.5 `fix(ui)`: change the default of `MdTextField` to `outlined`, add `MdFieldGrid`, `MdFieldset` and `MdSaveBar` to
  `src/components/md/`, each with a registry entry and a DESIGN.md catalogue entry as the catalogue spec requires, and add `src/components/renderers/useFieldBinding.js`. Verify: 6.1, 6.2 and 6.4 pass.
- [ ] 6.6 `fix(ui)`: migrate the native controls to primitives: `ConnectStep`, `MappingStep`, `CutOverStep`, `DryRunStep` (including its
  filled button), `AddMemberDialog`, `PluginBlock` (its buttons and the native progress), `SupportView`. Verify: 6.3 passes for these files.
- [ ] 6.7 `fix(ui)`: migrate the buttons and touch targets: `FactoryResetModal` and `ConflictDialog` raw text buttons to `MdButton`,
  `AccessDomainCard` and `RepositoryOverview` router links as buttons to `MdButton` with `href`, `StatTile`, `SearchBar`, `SearchResultRow`,
  `AdministrationFrame` (icon and text buttons), `SideNavTree` (search input). Visible 48-pixel controls become 40-pixel controls with
  `md-touch-target`: `NodeInheritedSection`, `NodeTemplateMenu`, `AdministrationFrame`, `MdSnackbar`. Verify: the tests of each file pass and
  the Playwright accessibility suite passes at 320 pixels.
- [ ] 6.8 `fix(ui)`: move the form scaffolding into the shared components: field grids (LdapPanel and the others), fieldsets (CreateTokenDialog,
  PolicyDialog, SearchFilters), save bars (AuthenticationGroupForm, LdapPanel, UnsavedChangesBar, the `calc` offset of SettingsView). Verify:
  the tests of each file pass.
- [ ] 6.9 `fix(ui)`: split `RepositoryWizard.vue` into `src/components/settings/wizard/` (`WizardStepIndicator`, `WizardChoiceCard` for the
  four copies, `WizardFieldGroup`, `WizardRemoteFields`, `WizardReviewSummary`, `WizardProgressList`). The data flow and the anchor
  `repository-wizard` stay. Verify: `RepositoryWizard.test.js` passes unchanged except for selectors moved to anchors.
- [ ] 6.10 `fix(ui)`: migrate the six renderers to `useFieldBinding` (`MdTextFieldRenderer`, `MdTextAreaRenderer`, `MdSelectRenderer`,
  `MdColourFieldRenderer`, `OneOfRenderer`, `TagsFieldRenderer`). Verify: the renderer tests pass and `grep -rn "onInput" src/components/renderers`
  finds the helper only.
- [ ] 6.11 `fix(ui)`: remove the `<style>` rules of `SettingsView.vue` (read-only inputs and clearance) and move them into the field
  primitives and utilities; drop the `settings-view` and `settings-save-bar` class hooks. The `settings-view` anchor stays in the registry.
  Verify: 3.2 for SettingsView passes, the read-only input is still drawn in the Playwright screenshot.
- [ ] 6.12 `fix(ui)`: remove the `<style>` rule of `FileUpload.vue` (line 302 of the inventory) after the `vue-upload-component` class is
  confirmed in the rendered drop zone by a Playwright snapshot before and after. Verify: the drop zone looks the same in the snapshot.
- [ ] 6.13 `docs(gitbook)`: regenerate `dashboard-anchors.md` for the new names (`md-field-grid`, `md-fieldset`, `md-save-bar`, the wizard
  parts if they carry names). Verify: `anchors.test.js` passes.
- [ ] 6.14 Wave 4 screenshots (no commit), including the touch target of a 40-pixel control at 390 pixels.
- [ ] 6.15 Open the wave 4 pull request under the title `fix(ui): build forms and controls from shared components`.

## 7. Wave 5: breakpoints and leftovers

- [ ] 7.1 `test(ui)`: in a new `src/styles/md3/breakpoints.test.js`, read `src/style.css` as text and assert that `--breakpoint-*: initial`
  is declared and that the four `--breakpoint-*` values are the window classes (600, 840, 1200, 1600 pixels), with no other breakpoint.
  Verify: it fails, because the Tailwind defaults are still there.
- [ ] 7.2 `test(ui)`: in `src/components/md/typescale.test.js`, assert that the font sizes in `src/components/md/typescale.js` equal the
  values of `src/styles/md3/typography.css`. Verify: it fails, because the module does not exist.
- [ ] 7.3 `test(ui)`: in `src/pages/IndexPage.test.js`, assert that the anchor `page-footer` appears once, and that the icon lookup returns a
  value for every entry of the index without a fallback literal. Verify: it fails on the duplicated footer.
- [ ] 7.4 `test(ui)`: in `src/components/md/MdBadge.test.js` and `StatusLabel`'s test, assert that `StatusLabel` takes a status value of
  the closed set and rejects a colour prop. Verify: it fails.
- [ ] 7.5 `fix(ui)`: replace the breakpoints by the window classes in the order of the mapping table of `design.md` (D7), one commit per
  folder group. Verify: the Playwright layout suite passes at 320, 600, 840, 1280 and 1920 pixels, and each changed threshold is in the
  wave's screenshots on both sides of the threshold.
- [ ] 7.6 `fix(ui)`: change `src/style.css` `@theme` to `--breakpoint-*: initial` plus the four window classes, after 7.5 so that no view
  depends on a default. Verify: 7.1 passes, and the `.container` gutter is still 16 pixels below 600 pixels and 24 pixels from it (the
  `window-margins.test.js` assertions pass).
- [ ] 7.7 `fix(ui)`: remove the arbitrary values and `!important` overrides (`size-6!` in `LoginModal`, `!px-2` in `MenuPanel`, the two in
  `SearchView`, any `shadow-[inset...]` left in `MdFieldShell` by the catalogue change), the opacity fades (`opacity-70` in `SearchFilters`, `opacity-80` in
  `RepositoryTypeBadge`, `opacity-60` in `MigrationView`) and the hand-set font weights (`RevokeTokenDialog`, `RotateTokenDialog`,
  `SecretOnceDialog`, `ResetPasswordDialog`, `SettingsImportErrors`, `SyncStep`) in favour of tokens. Verify: `grep -rnE 'class="[^"]*!' src --include=*.vue`
  shows no important modifier, and `grep -rn "opacity-" src` finds nothing outside the state layers.
- [ ] 7.8 `fix(ui)`: `StatusLabel` takes a status value of a closed set instead of a colour prop; `StatTile` takes its meter colour as
  classes from a closed set; `TimeSeriesChart` takes its font sizes from `src/components/md/typescale.js`. Verify: 7.2 and 7.4 pass.
- [ ] 7.9 `fix(ui)`: `IndexPage.vue` (`src/pages/`): draw the footer once (the anchor `page-footer` appears once) and replace the icon lookup
  (lines 548, 566, 584 of the inventory) with a lookup table keyed by the entry's icon name. Verify: 7.3 passes.
- [ ] 7.10 `build(ui)`: empty the style gate baseline of `add-ui-style-gate` and remove the allowlist entries that no longer apply. Verify:
  the gate passes with an empty baseline, and `npm test` passes.
- [ ] 7.11 `docs(gitbook)`: regenerate `dashboard-anchors.md` if a name changed in wave 5, and note the window class names in the dashboard
  reference if that page lists breakpoints. Verify: `anchors.test.js` passes and the dash grep prints nothing.
- [ ] 7.12 Wave 5 screenshots (no commit): before and after at 320, 600 and 1440 pixels, and at each side of every changed threshold.
- [ ] 7.13 Open the wave 5 pull request under the title `fix(ui): use md3 window classes only and empty the style gate`, with the list of
  changed test expectations in the description.

## 8. Pull requests

- [ ] 8.1 Check that the five wave pull requests are merged in order, that the proposal is merged, and that the change's `openspec/`
  files are not in any code pull request. Verify: `git log --oneline` on the remote main branch shows the five wave commits in order.
- [ ] 8.2 Open the final pull request under the title named in the proposal, `fix(ui): build cards, lists, tables and forms from shared components`,
  only if the maintainer chose the one pull request alternative (open question 1). Otherwise open no further pull request and record in the
  wave 5 description that the umbrella title was not used. The last task of the change is this decision.

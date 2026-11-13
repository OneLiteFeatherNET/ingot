---
description: Reference for the data-ingot-* names on the Ingot dashboard, which components and parts carry them, the state values and the stability of each name.
---

# Dashboard anchors

<!-- Generated from reposilite-frontend/src/anchors/registry.json by `npm run anchors:doc`. Change the registry, not this page. -->

This page lists every stable name that the dashboard puts into the DOM, contract 1. A plugin author or an operator who builds a dashboard can target these names instead of the utility classes, which change with each restyle. For what a plugin can use today, see [Extend the dashboard from a plugin](../how-to/extend-the-dashboard-from-a-plugin.md).

## Attributes

| Attribute | Meaning |
| --- | --- |
| `data-ingot-component` | The component, on its root element. The value is the name in the table below. |
| `data-ingot-part` | A named inner part of a component, such as its header or its actions. Listed per component. |
| `data-ingot-state` | The state a part or component is in. One of `current`, `loading`, `error`, `empty` or `open`. Listed per component. |
| `data-ingot-component-alias` | The old name of a renamed component, kept for one minor release. Present only while an alias is listed below. |
| `data-ingot-repository` | The repository name, on the repository row and on the repository header. The name is already on screen. |
| `data-ingot-block` | The type of a plugin block, one of the closed set of block types. |
| `data-ingot-plugin-page` | The id of a plugin page, when it passes the same validation as on the server. |

## Stability

A `stable` name is part of the contract. It is added in a minor release, and it is renamed or removed only through an alias that lives for one minor release. An `experimental` name may change in any release.

## Components

| Name | Stability | Source | Parts | States |
| --- | --- | --- | --- | --- |
| `access-domain-card` | stable | `src/components/settings/AccessDomainCard.vue` | none | none |
| `access-view` | experimental | `src/components/access/AccessView.vue` | none | none |
| `account-view` | experimental | `src/components/account/AccountView.vue` | none | none |
| `add-member-dialog` | experimental | `src/components/access/AddMemberDialog.vue` | none | `error` |
| `adjustments-modal` | experimental | `src/components/browser/AdjustmentsModal.vue` | none | none |
| `administration-frame` | stable | `src/components/layout/AdministrationFrame.vue` | none | none |
| `all-of-renderer` | stable | `src/components/renderers/AllOfRenderer.vue` | none | none |
| `analytics-view` | experimental | `src/components/dashboard/AnalyticsView.vue` | none | none |
| `app-shell` | experimental | `src/App.vue` | none | none |
| `appearance-preview` | stable | `src/components/settings/AppearancePreview.vue` | none | none |
| `appearance-section-renderer` | stable | `src/components/renderers/AppearanceSectionRenderer.vue` | none | none |
| `array-list-renderer` | stable | `src/components/renderers/ArrayListRenderer.vue` | none | none |
| `artifact-snippet` | experimental | `src/components/card/ArtifactSnippet.vue` | none | none |
| `audit-event-row` | experimental | `src/components/audit/AuditEventRow.vue` | none | none |
| `audit-view` | experimental | `src/components/audit/AuditView.vue` | none | `loading` |
| `authentication-group-form` | experimental | `src/components/access/AuthenticationGroupForm.vue` | none | none |
| `boolean-switch-renderer` (no element of its own) | stable | `src/components/renderers/BooleanSwitchRenderer.vue` | none | none |
| `breadcrumb-navigation` | experimental | `src/components/browser/BreadcrumbNavigation.vue` | none | none |
| `browser-icon` | experimental | `src/components/browser/BrowserIcon.vue` | none | none |
| `card-menu` | experimental | `src/components/card/CardMenu.vue` | none | none |
| `chart-card` | experimental | `src/components/dashboard/ChartCard.vue` | none | `loading` |
| `check-permission-view` | experimental | `src/components/access/check/CheckPermissionView.vue` | none | `loading` |
| `cleanup-policies-panel` | experimental | `src/components/maintenance/CleanupPoliciesPanel.vue` | none | `error` |
| `code-brackets` | experimental | `src/components/card/CodeBrackets.vue` | none | none |
| `code-string` | experimental | `src/components/card/CodeString.vue` | none | none |
| `collection-editor` | stable | `src/components/settings/CollectionEditor.vue` | none | none |
| `combobox-field` | experimental | `src/components/access/check/ComboboxField.vue` | none | none |
| `confirm-action-dialog` | experimental | `src/components/access/ConfirmActionDialog.vue` | none | none |
| `conflict-dialog` | stable | `src/components/settings/ConflictDialog.vue` | none | none |
| `connect-step` | experimental | `src/components/migration/ConnectStep.vue` | none | `error` |
| `console-view` | experimental | `src/components/console/ConsoleView.vue` | none | none |
| `constant-renderer` | stable | `src/components/renderers/ConstantRenderer.vue` | none | none |
| `create-token-dialog` | experimental | `src/components/identity/CreateTokenDialog.vue` | none | `error` |
| `cut-over-step` | experimental | `src/components/migration/CutOverStep.vue` | none | none |
| `dashboard-view` | experimental | `src/components/dashboard/DashboardView.vue` | none | none |
| `declare-type-dialog` | experimental | `src/components/repositories/DeclareTypeDialog.vue` | none | none |
| `default-header` | stable | `src/components/header/DefaultHeader.vue` | `title` | none |
| `delete-entry-dialog` | stable | `src/components/settings/DeleteEntryDialog.vue` | none | none |
| `delete-entry-modal` | experimental | `src/components/browser/DeleteEntryModal.vue` | none | none |
| `delete-policy-dialog` | experimental | `src/components/maintenance/DeletePolicyDialog.vue` | none | none |
| `detailed-list-entry` | experimental | `src/components/browser/DetailedListEntry.vue` | none | none |
| `dialog-shell` | experimental | `src/components/identity/DialogShell.vue` | none | none |
| `discover-step` | experimental | `src/components/migration/DiscoverStep.vue` | none | none |
| `dry-run-result` | experimental | `src/components/maintenance/DryRunResult.vue` | none | none |
| `dry-run-step` | experimental | `src/components/migration/DryRunStep.vue` | none | none |
| `entry-detail` | stable | `src/components/settings/EntryDetail.vue` | `header` | none |
| `entry-list` | stable | `src/components/settings/EntryList.vue` | none | none |
| `factory-reset-modal` | stable | `src/components/settings/FactoryResetModal.vue` | none | none |
| `failures-sheet` | experimental | `src/components/dashboard/FailuresSheet.vue` | none | none |
| `file-browser-view` | experimental | `src/components/browser/FileBrowserView.vue` | none | none |
| `file-list` | experimental | `src/components/browser/FileList.vue` | none | `error` |
| `file-upload` | experimental | `src/components/browser/FileUpload.vue` | none | none |
| `getting-started-card` | experimental | `src/components/dashboard/GettingStartedCard.vue` | none | `error` |
| `header-hero` | stable | `src/components/header/HeaderHero.vue` | none | none |
| `index-page` | experimental | `src/pages/IndexPage.vue` | none | none |
| `index-panel` | experimental | `src/components/maintenance/IndexPanel.vue` | none | none |
| `instance-health-row` | experimental | `src/components/dashboard/InstanceHealthRow.vue` | none | none |
| `instance-status-panel` | experimental | `src/components/dashboard/InstanceStatusPanel.vue` | `read-only-banner`, `cluster-summary`, `cluster-instances`, `cluster-instance` | none |
| `job-progress` | experimental | `src/components/migration/JobProgress.vue` | none | none |
| `ldap-panel` | experimental | `src/components/access/LdapPanel.vue` | none | none |
| `legacy-grants-panel` | experimental | `src/components/access/LegacyGrantsPanel.vue` | none | none |
| `login-modal` | stable | `src/components/header/LoginModal.vue` | none | none |
| `maintenance-view` | experimental | `src/components/maintenance/MaintenanceView.vue` | none | none |
| `mapping-step` | experimental | `src/components/migration/MappingStep.vue` | none | none |
| `maven-options-panel` | experimental | `src/components/repositories/MavenOptionsPanel.vue` | none | none |
| `md-button` | experimental | `src/components/md/MdButton.vue` | none | none |
| `md-card` | experimental | `src/components/md/MdCard.vue` | none | none |
| `md-checkbox` | experimental | `src/components/md/MdCheckbox.vue` | none | none |
| `md-chip` | experimental | `src/components/md/MdChip.vue` | none | none |
| `md-chip-input` | experimental | `src/components/md/MdChipInput.vue` | none | none |
| `md-colour-field-renderer` | stable | `src/components/renderers/MdColourFieldRenderer.vue` | none | none |
| `md-icon-button` | experimental | `src/components/md/MdIconButton.vue` | none | none |
| `md-linear-progress` | experimental | `src/components/md/MdLinearProgress.vue` | none | none |
| `md-navigation-bar` | experimental | `src/components/md/MdNavigationBar.vue` | none | none |
| `md-navigation-rail` | experimental | `src/components/md/MdNavigationRail.vue` | none | none |
| `md-overflow-menu` | experimental | `src/components/md/MdOverflowMenu.vue` | none | none |
| `md-radio` | experimental | `src/components/md/MdRadio.vue` | none | none |
| `md-select` | experimental | `src/components/md/MdSelect.vue` | none | none |
| `md-select-renderer` (no element of its own) | stable | `src/components/renderers/MdSelectRenderer.vue` | none | none |
| `md-side-sheet` | experimental | `src/components/md/MdSideSheet.vue` | none | none |
| `md-snackbar` | experimental | `src/components/md/MdSnackbar.vue` | none | none |
| `md-switch` | experimental | `src/components/md/MdSwitch.vue` | none | none |
| `md-text-area` | experimental | `src/components/md/MdTextArea.vue` | none | none |
| `md-text-area-renderer` (no element of its own) | stable | `src/components/renderers/MdTextAreaRenderer.vue` | none | none |
| `md-text-field` | experimental | `src/components/md/MdTextField.vue` | none | none |
| `md-text-field-renderer` (no element of its own) | stable | `src/components/renderers/MdTextFieldRenderer.vue` | none | none |
| `menu-panel` | stable | `src/components/header/MenuPanel.vue` | none | none |
| `migration-dialog` | experimental | `src/components/migration/MigrationDialog.vue` | none | none |
| `migration-view` | experimental | `src/components/migration/MigrationView.vue` | none | none |
| `new-entry-dialog` | stable | `src/components/settings/NewEntryDialog.vue` | none | none |
| `new-machine-dialog` | experimental | `src/components/access/NewMachineDialog.vue` | none | none |
| `new-team-dialog` | experimental | `src/components/access/NewTeamDialog.vue` | none | none |
| `new-user-dialog` | experimental | `src/components/access/NewUserDialog.vue` | none | none |
| `node-add-field` | experimental | `src/components/access/nodes/NodeAddField.vue` | none | none |
| `node-editor` | experimental | `src/components/access/nodes/NodeEditor.vue` | none | `loading` |
| `node-inherited-section` | experimental | `src/components/access/nodes/NodeInheritedSection.vue` | none | none |
| `node-row` | experimental | `src/components/access/nodes/NodeRow.vue` | none | `error` |
| `node-template-menu` | experimental | `src/components/access/nodes/NodeTemplateMenu.vue` | none | none |
| `node-tree-view` | experimental | `src/components/access/nodes/NodeTreeView.vue` | none | none |
| `not-found-view` | experimental | `src/components/util/NotFoundView.vue` | none | none |
| `object-renderer` | stable | `src/components/renderers/ObjectRenderer.vue` | none | none |
| `one-of-renderer` | stable | `src/components/renderers/OneOfRenderer.vue` | none | none |
| `optional-renderer` | stable | `src/components/renderers/OptionalRenderer.vue` | none | none |
| `page-footer` | stable | `src/components/footer/PageFooter.vue` | none | none |
| `password-card` | experimental | `src/components/account/PasswordCard.vue` | none | none |
| `password-dialog` | stable | `src/components/settings/PasswordDialog.vue` | none | none |
| `password-field` | experimental | `src/components/migration/PasswordField.vue` | none | none |
| `permission-nodes-card` | experimental | `src/components/access/PermissionNodesCard.vue` | none | none |
| `plugin-block` | stable | `src/components/plugins/PluginBlock.vue` | none | `error`, `loading` |
| `plugin-page` | stable | `src/components/plugins/PluginPageView.vue` | `header` | none |
| `policy-dialog` | experimental | `src/components/maintenance/PolicyDialog.vue` | none | none |
| `principal-actions-menu` | experimental | `src/components/access/PrincipalActionsMenu.vue` | none | none |
| `principal-detail` | experimental | `src/components/access/PrincipalDetail.vue` | none | `loading` |
| `principals-panel` | experimental | `src/components/access/PrincipalsPanel.vue` | none | none |
| `problem-alert` | experimental | `src/components/migration/ProblemAlert.vue` | none | `error` |
| `profile-card` | experimental | `src/components/account/ProfileCard.vue` | none | none |
| `public-repositories-dialog` | experimental | `src/components/dashboard/PublicRepositoriesDialog.vue` | none | none |
| `rail-brand` | experimental | `src/components/header/RailBrand.vue` | none | none |
| `read-only-toggle` | experimental | `src/components/dashboard/ReadOnlyToggle.vue` | none | `error` |
| `rebuild-index-dialog` | experimental | `src/components/maintenance/RebuildIndexDialog.vue` | none | none |
| `repositories-table` | experimental | `src/components/repositories/RepositoriesTable.vue` | `header`, `row`, `cell-name`, `empty` | `empty` |
| `repositories-view` | experimental | `src/components/repositories/RepositoriesView.vue` | none | none |
| `repository-access-card` | stable | `src/components/settings/RepositoryAccessCard.vue` | none | `loading` |
| `repository-detail` | experimental | `src/components/repositories/RepositoryDetail.vue` | `header` | `empty`, `loading` |
| `repository-members-picker` | stable | `src/components/settings/RepositoryMembersPicker.vue` | none | none |
| `repository-overview` | experimental | `src/components/overview/RepositoryOverview.vue` | none | `loading` |
| `repository-snippet` | experimental | `src/components/card/RepositorySnippet.vue` | none | none |
| `repository-type-badge` | experimental | `src/components/tokens/RepositoryTypeBadge.vue` | none | none |
| `repository-wizard` | stable | `src/components/settings/RepositoryWizard.vue` | `header` | none |
| `reset-password-dialog` | experimental | `src/components/access/ResetPasswordDialog.vue` | none | none |
| `resolved-requests-chart` (no element of its own) | experimental | `src/components/dashboard/ResolvedRequestsChart.vue` | none | none |
| `resource-trends` | experimental | `src/components/dashboard/ResourceTrends.vue` | none | none |
| `revoke-token-dialog` | experimental | `src/components/identity/RevokeTokenDialog.vue` | none | `error` |
| `rotate-token-dialog` | experimental | `src/components/identity/RotateTokenDialog.vue` | none | none |
| `run-history` | experimental | `src/components/maintenance/RunHistory.vue` | none | none |
| `run-now-dialog` | experimental | `src/components/maintenance/RunNowDialog.vue` | none | `error` |
| `search-bar` | experimental | `src/components/search/SearchBar.vue` | none | none |
| `search-filters` | experimental | `src/components/search/SearchFilters.vue` | none | none |
| `search-result-row` | experimental | `src/components/search/SearchResultRow.vue` | none | none |
| `search-view` | experimental | `src/components/search/SearchView.vue` | none | `loading` |
| `secret-once-dialog` | experimental | `src/components/identity/SecretOnceDialog.vue` | none | none |
| `section-renderer` | stable | `src/components/renderers/SectionRenderer.vue` | none | none |
| `security-panel` | experimental | `src/components/access/SecurityPanel.vue` | none | none |
| `segmented-control` | experimental | `src/components/tokens/SegmentedControl.vue` | none | none |
| `segmented-option` | experimental | `src/components/tokens/SegmentedOption.vue` | none | none |
| `settings-attention` | experimental | `src/components/settings/SettingsAttention.vue` | none | none |
| `settings-download-menu` | stable | `src/components/settings/SettingsDownloadMenu.vue` | none | none |
| `settings-encryption-alert` | experimental | `src/components/support/SettingsEncryptionAlert.vue` | none | none |
| `settings-hub` | stable | `src/components/settings/SettingsHub.vue` | none | none |
| `settings-import-errors` | stable | `src/components/settings/SettingsImportErrors.vue` | none | none |
| `settings-review-sheet` | stable | `src/components/settings/SettingsReviewSheet.vue` | `header` | none |
| `settings-view` (no element of its own) | stable | `src/components/settings/SettingsView.vue` | none | none |
| `side-nav-layout` | stable | `src/components/layout/SideNavLayout.vue` | `nav`, `page`, `lead` | none |
| `side-nav-tree` | experimental | `src/components/layout/SideNavTree.vue` | none | none |
| `site-footer` | stable | `src/components/footer/SiteFooter.vue` | none | none |
| `site-legal` | stable | `src/components/footer/SiteLegal.vue` | none | none |
| `skip-link` | stable | `src/components/layout/SkipLink.vue` | none | none |
| `snippets-card` | experimental | `src/components/card/SnippetsCard.vue` | none | none |
| `sso-provider-dialog` | experimental | `src/components/access/SsoProviderDialog.vue` | none | none |
| `sso-providers-panel` | experimental | `src/components/access/SsoProvidersPanel.vue` | none | none |
| `stat-tile` | experimental | `src/components/dashboard/StatTile.vue` | none | none |
| `status-label` | experimental | `src/components/dashboard/StatusLabel.vue` | none | none |
| `storage-usage-panel` | experimental | `src/components/maintenance/StorageUsagePanel.vue` | none | none |
| `support-view` | experimental | `src/components/support/SupportView.vue` | none | none |
| `switch-field` | stable | `src/components/renderers/SwitchField.vue` | none | none |
| `sync-step` | experimental | `src/components/migration/SyncStep.vue` | none | none |
| `system-info-view` | experimental | `src/components/support/SystemInfoView.vue` | none | `loading` |
| `tags-field-renderer` (no element of its own) | stable | `src/components/renderers/TagsFieldRenderer.vue` | none | none |
| `team-detail` | experimental | `src/components/access/TeamDetail.vue` | none | `loading` |
| `team-legacy-grants` | experimental | `src/components/access/TeamLegacyGrants.vue` | none | none |
| `team-members-card` | experimental | `src/components/access/TeamMembersCard.vue` | none | none |
| `team-parents-card` | experimental | `src/components/access/TeamParentsCard.vue` | none | `loading` |
| `teams-panel` | experimental | `src/components/access/TeamsPanel.vue` | none | none |
| `time-series-chart` | experimental | `src/components/dashboard/TimeSeriesChart.vue` | none | none |
| `token-list` | experimental | `src/components/identity/TokenList.vue` | none | none |
| `token-tag` | experimental | `src/components/tokens/TokenTag.vue` | none | none |
| `type-badge` | experimental | `src/components/repositories/TypeBadge.vue` | none | none |
| `unsaved-changes-bar` | stable | `src/components/settings/UnsavedChangesBar.vue` | none | none |
| `unsupported-renderer` | stable | `src/components/renderers/UnsupportedRenderer.vue` | none | none |
| `verify-step` | experimental | `src/components/migration/VerifyStep.vue` | none | none |
| `view-load-error` | experimental | `src/components/util/ViewLoadError.vue` | none | `error` |
| `view-loading` | experimental | `src/components/util/ViewLoading.vue` | none | `loading` |
| `write-policy-field` | experimental | `src/components/repositories/WritePolicyField.vue` | none | none |
| `xml-tag` | experimental | `src/components/card/XmlTag.vue` | none | none |

## Aliases

No alias is in effect.

## Selecting in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The attributes stay useful for structural checks and for plugins, which cannot read a role from a utility class.


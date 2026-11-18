---
description: Reference for the data-ingot-* names on the Ingot dashboard, which components and parts carry them, the state values and the stability of each name.
---

# Dashboard anchors

<!-- Generated from reposilite-frontend/src/anchors/registry.json by `npm run anchors:doc`. Change the registry, not this page. -->

This page lists every stable name that the dashboard puts into the DOM, contract 1. A plugin author or an operator who builds a dashboard can target these names instead of the utility classes, which change with each restyle. For what a plugin can use today, see [Extend the dashboard from a plugin](../../manage/plugins/extend-the-dashboard-from-a-plugin.md).

## Attributes

| Attribute | Meaning |
| --- | --- |
| `data-ingot-component` | The component, on its root element. The value is the name in the table below. |
| `data-ingot-part` | A named inner part of a component, such as its header or its actions. Listed per component. |
| `data-ingot-state` | The state a part or component is in. One of `current`, `loading`, `error`, `empty` or `open`. Listed per component. |
| `data-ingot-slot` | A named place in a component where a later change puts its own content, such as a card that is not built yet. Listed per component. |
| `data-ingot-component-alias` | The old name of a renamed component, kept for one minor release. Present only while an alias is listed below. |
| `data-ingot-repository` | The repository name, on the repository row and on the repository header. The name is already on screen. |
| `data-ingot-block` | The type of a plugin block, one of the closed set of block types. |
| `data-ingot-plugin-page` | The id of a plugin page, when it passes the same validation as on the server. |

## Stability

A `stable` name is part of the contract. It is added in a minor release, and it is renamed or removed only through an alias that lives for one minor release. An `experimental` name may change in any release.

## Components

| Name | Stability | Source | Parts | States | Slots |
| --- | --- | --- | --- | --- | --- |
| `access-domain-card` | stable | `src/components/settings/AccessDomainCard.vue` | none | none | none |
| `access-view` | experimental | `src/components/access/AccessView.vue` | none | none | none |
| `account-identity` | experimental | `src/components/header/AccountIdentity.vue` | none | none | none |
| `account-view` | experimental | `src/components/account/AccountView.vue` | none | none | `add-multi-factor-authentication` |
| `add-member-dialog` | experimental | `src/components/access/AddMemberDialog.vue` | none | `error` | none |
| `adjustments-modal` | experimental | `src/components/browser/AdjustmentsModal.vue` | none | none | none |
| `administration-frame` | stable | `src/components/layout/AdministrationFrame.vue` | none | none | none |
| `all-of-renderer` | stable | `src/components/renderers/AllOfRenderer.vue` | none | none | none |
| `analytics-view` | experimental | `src/components/dashboard/AnalyticsView.vue` | none | none | none |
| `app-shell` | experimental | `src/App.vue` | none | none | none |
| `app-tabs` | experimental | `src/components/tabs/AppTabs.vue` | none | none | none |
| `appearance-preview` | stable | `src/components/settings/AppearancePreview.vue` | none | none | none |
| `appearance-section-renderer` | stable | `src/components/renderers/AppearanceSectionRenderer.vue` | none | none | none |
| `array-list-renderer` | stable | `src/components/renderers/ArrayListRenderer.vue` | none | none | none |
| `artifact-snippet` | experimental | `src/components/card/ArtifactSnippet.vue` | none | none | none |
| `audit-event-row` | experimental | `src/components/audit/AuditEventRow.vue` | none | none | none |
| `audit-view` | experimental | `src/components/audit/AuditView.vue` | none | `loading` | none |
| `authentication-group-form` | experimental | `src/components/access/AuthenticationGroupForm.vue` | none | none | none |
| `boolean-switch-renderer` (no element of its own) | stable | `src/components/renderers/BooleanSwitchRenderer.vue` | none | none | none |
| `breadcrumb-navigation` | experimental | `src/components/browser/BreadcrumbNavigation.vue` | none | none | none |
| `browser-icon` | experimental | `src/components/browser/BrowserIcon.vue` | none | none | none |
| `card-menu` | experimental | `src/components/card/CardMenu.vue` | none | none | none |
| `chart-card` | experimental | `src/components/dashboard/ChartCard.vue` | none | `loading` | none |
| `check-permission-view` | experimental | `src/components/access/check/CheckPermissionView.vue` | none | `loading` | none |
| `cleanup-policies-panel` | experimental | `src/components/maintenance/CleanupPoliciesPanel.vue` | none | `error` | none |
| `code-brackets` | experimental | `src/components/card/CodeBrackets.vue` | none | none | none |
| `code-string` | experimental | `src/components/card/CodeString.vue` | none | none | none |
| `collection-editor` | stable | `src/components/settings/CollectionEditor.vue` | none | none | none |
| `combobox-field` | experimental | `src/components/access/check/ComboboxField.vue` | none | none | none |
| `confirm-action-dialog` | experimental | `src/components/access/ConfirmActionDialog.vue` | none | none | none |
| `conflict-dialog` | stable | `src/components/settings/ConflictDialog.vue` | none | none | none |
| `connect-step` | experimental | `src/components/migration/ConnectStep.vue` | none | `error` | none |
| `console-view` | experimental | `src/components/console/ConsoleView.vue` | none | none | none |
| `constant-renderer` | stable | `src/components/renderers/ConstantRenderer.vue` | none | none | none |
| `create-token-dialog` | experimental | `src/components/identity/CreateTokenDialog.vue` | none | `error` | none |
| `cut-over-step` | experimental | `src/components/migration/CutOverStep.vue` | none | none | none |
| `dashboard-view` | experimental | `src/components/dashboard/DashboardView.vue` | none | none | none |
| `declare-type-dialog` | experimental | `src/components/repositories/DeclareTypeDialog.vue` | none | none | none |
| `default-header` | stable | `src/components/header/DefaultHeader.vue` | `title` | none | none |
| `delete-entry-dialog` | stable | `src/components/settings/DeleteEntryDialog.vue` | none | none | none |
| `delete-entry-modal` | experimental | `src/components/browser/DeleteEntryModal.vue` | none | none | none |
| `delete-policy-dialog` | experimental | `src/components/maintenance/DeletePolicyDialog.vue` | none | none | none |
| `detailed-list-entry` | experimental | `src/components/browser/DetailedListEntry.vue` | none | none | none |
| `dialog-shell` | experimental | `src/components/md/MdDialog.vue` | none | none | none |
| `discover-step` | experimental | `src/components/migration/DiscoverStep.vue` | none | none | none |
| `domain-settings-panel` | experimental | `src/components/settings/DomainSettingsPanel.vue` | none | none | none |
| `dry-run-result` | experimental | `src/components/maintenance/DryRunResult.vue` | none | none | none |
| `dry-run-step` | experimental | `src/components/migration/DryRunStep.vue` | none | none | none |
| `entry-detail` | stable | `src/components/settings/EntryDetail.vue` | `header` | none | none |
| `entry-list` | stable | `src/components/settings/EntryList.vue` | none | none | none |
| `factory-reset-modal` | stable | `src/components/settings/FactoryResetModal.vue` | none | none | none |
| `failures-sheet` | experimental | `src/components/dashboard/FailuresSheet.vue` | none | none | none |
| `file-browser-view` | experimental | `src/components/browser/FileBrowserView.vue` | none | none | none |
| `file-list` | experimental | `src/components/browser/FileList.vue` | none | `error` | none |
| `file-upload` | experimental | `src/components/browser/FileUpload.vue` | none | none | none |
| `getting-started-card` | experimental | `src/components/dashboard/GettingStartedCard.vue` | none | `error` | none |
| `header-hero` | stable | `src/components/header/HeaderHero.vue` | none | none | none |
| `index-page` | experimental | `src/pages/IndexPage.vue` | none | none | none |
| `index-panel` | experimental | `src/components/maintenance/IndexPanel.vue` | none | none | none |
| `instance-health-row` | experimental | `src/components/dashboard/InstanceHealthRow.vue` | none | none | none |
| `instance-status-panel` | experimental | `src/components/dashboard/InstanceStatusPanel.vue` | `read-only-banner`, `cluster-summary`, `cluster-instances`, `cluster-instance` | none | none |
| `job-progress` | experimental | `src/components/migration/JobProgress.vue` | none | none | none |
| `key-value-tree` | experimental | `src/components/support/KeyValueTree.vue` | none | none | none |
| `ldap-panel` | experimental | `src/components/access/LdapPanel.vue` | none | none | none |
| `legacy-grants-panel` | experimental | `src/components/access/LegacyGrantsPanel.vue` | none | none | none |
| `login-modal` | stable | `src/components/header/LoginModal.vue` | none | none | none |
| `maintenance-view` | experimental | `src/components/maintenance/MaintenanceView.vue` | none | none | none |
| `mapping-step` | experimental | `src/components/migration/MappingStep.vue` | none | none | none |
| `maven-options-panel` | experimental | `src/components/repositories/MavenOptionsPanel.vue` | none | none | none |
| `md-alert` | experimental | `src/components/md/MdAlert.vue` | none | none | none |
| `md-badge` | experimental | `src/components/md/MdBadge.vue` | none | none | none |
| `md-breadcrumb` | experimental | `src/components/md/MdBreadcrumb.vue` | none | none | none |
| `md-button` | experimental | `src/components/md/MdButton.vue` | none | none | none |
| `md-card` | experimental | `src/components/md/MdCard.vue` | none | none | none |
| `md-checkbox` | experimental | `src/components/md/MdCheckbox.vue` | none | none | none |
| `md-chip` | experimental | `src/components/md/MdChip.vue` | none | none | none |
| `md-chip-input` | experimental | `src/components/md/MdChipInput.vue` | none | none | none |
| `md-code` | experimental | `src/components/md/MdCode.vue` | none | none | none |
| `md-code-block` | experimental | `src/components/md/MdCodeBlock.vue` | none | none | none |
| `md-colour-field-renderer` | stable | `src/components/renderers/MdColourFieldRenderer.vue` | none | none | none |
| `md-copyable-code` | experimental | `src/components/md/MdCopyableCode.vue` | none | none | none |
| `md-data-table` | experimental | `src/components/md/MdDataTable.vue` | `header`, `row` | none | none |
| `md-dialog-actions` | experimental | `src/components/md/MdDialogActions.vue` | none | none | none |
| `md-disclosure` | experimental | `src/components/md/MdDisclosure.vue` | none | none | none |
| `md-divider` | experimental | `src/components/md/MdDivider.vue` | none | none | none |
| `md-icon` | experimental | `src/components/md/MdIcon.vue` | none | none | none |
| `md-icon-button` | experimental | `src/components/md/MdIconButton.vue` | none | none | none |
| `md-key-value-list` | experimental | `src/components/md/MdKeyValueList.vue` | none | none | none |
| `md-linear-progress` | experimental | `src/components/md/MdLinearProgress.vue` | none | none | none |
| `md-list` | experimental | `src/components/md/MdList.vue` | none | none | none |
| `md-list-detail` | experimental | `src/components/md/MdListDetail.vue` | none | none | none |
| `md-list-item` | experimental | `src/components/md/MdListItem.vue` | none | none | none |
| `md-listbox` | experimental | `src/components/md/MdListbox.vue` | none | none | none |
| `md-navigation-bar` | experimental | `src/components/md/MdNavigationBar.vue` | none | none | none |
| `md-navigation-item` | experimental | `src/components/md/MdNavigationItem.vue` | none | none | none |
| `md-navigation-rail` | experimental | `src/components/md/MdNavigationRail.vue` | none | none | none |
| `md-overflow-menu` | experimental | `src/components/md/MdOverflowMenu.vue` | none | none | none |
| `md-page` | experimental | `src/components/md/MdPage.vue` | none | none | none |
| `md-page-header` | experimental | `src/components/md/MdPageHeader.vue` | none | none | none |
| `md-radio` | experimental | `src/components/md/MdRadio.vue` | none | none | none |
| `md-section` | experimental | `src/components/md/MdSection.vue` | none | none | none |
| `md-select` | experimental | `src/components/md/MdSelect.vue` | none | none | none |
| `md-select-renderer` (no element of its own) | stable | `src/components/renderers/MdSelectRenderer.vue` | none | none | none |
| `md-side-sheet` | experimental | `src/components/md/MdSideSheet.vue` | none | none | none |
| `md-skeleton` | experimental | `src/components/md/MdSkeleton.vue` | none | none | none |
| `md-snackbar` | experimental | `src/components/md/MdSnackbar.vue` | none | none | none |
| `md-state-panel` | experimental | `src/components/md/MdStatePanel.vue` | none | `empty`, `loading`, `error` | none |
| `md-switch` | experimental | `src/components/md/MdSwitch.vue` | none | none | none |
| `md-tabs` | experimental | `src/components/md/MdTabs.vue` | none | none | none |
| `md-text-area` | experimental | `src/components/md/MdTextArea.vue` | none | none | none |
| `md-text-area-renderer` (no element of its own) | stable | `src/components/renderers/MdTextAreaRenderer.vue` | none | none | none |
| `md-text-field` | experimental | `src/components/md/MdTextField.vue` | none | none | none |
| `md-text-field-renderer` (no element of its own) | stable | `src/components/renderers/MdTextFieldRenderer.vue` | none | none | none |
| `menu-panel` | stable | `src/components/header/MenuPanel.vue` | none | none | none |
| `migration-dialog` | experimental | `src/components/migration/MigrationDialog.vue` | none | none | none |
| `migration-view` | experimental | `src/components/migration/MigrationView.vue` | none | none | none |
| `new-entry-dialog` | stable | `src/components/settings/NewEntryDialog.vue` | none | none | none |
| `new-machine-dialog` | experimental | `src/components/access/NewMachineDialog.vue` | none | none | none |
| `new-team-dialog` | experimental | `src/components/access/NewTeamDialog.vue` | none | none | none |
| `new-user-dialog` | experimental | `src/components/access/NewUserDialog.vue` | none | none | none |
| `node-add-field` | experimental | `src/components/access/nodes/NodeAddField.vue` | none | none | none |
| `node-editor` | experimental | `src/components/access/nodes/NodeEditor.vue` | none | `loading` | none |
| `node-inherited-section` | experimental | `src/components/access/nodes/NodeInheritedSection.vue` | none | none | none |
| `node-row` | experimental | `src/components/access/nodes/NodeRow.vue` | none | `error` | none |
| `node-template-menu` | experimental | `src/components/access/nodes/NodeTemplateMenu.vue` | none | none | none |
| `node-tree-view` | experimental | `src/components/access/nodes/NodeTreeView.vue` | none | none | none |
| `not-found-view` | experimental | `src/components/util/NotFoundView.vue` | none | none | none |
| `object-renderer` | stable | `src/components/renderers/ObjectRenderer.vue` | none | none | none |
| `one-of-renderer` | stable | `src/components/renderers/OneOfRenderer.vue` | none | none | none |
| `optional-renderer` | stable | `src/components/renderers/OptionalRenderer.vue` | none | none | none |
| `page-footer` | stable | `src/components/footer/PageFooter.vue` | none | none | none |
| `password-card` | experimental | `src/components/account/PasswordCard.vue` | none | none | none |
| `password-dialog` | stable | `src/components/settings/PasswordDialog.vue` | none | none | none |
| `password-field` | experimental | `src/components/migration/PasswordField.vue` | none | none | none |
| `permission-nodes-card` | experimental | `src/components/access/PermissionNodesCard.vue` | none | none | none |
| `plugin-block` | stable | `src/components/plugins/PluginBlock.vue` | none | `error`, `loading` | none |
| `plugin-page` | stable | `src/components/plugins/PluginPageView.vue` | `header` | none | none |
| `policy-dialog` | experimental | `src/components/maintenance/PolicyDialog.vue` | none | none | none |
| `principal-actions-menu` | experimental | `src/components/access/PrincipalActionsMenu.vue` | none | none | none |
| `principal-detail` | experimental | `src/components/access/PrincipalDetail.vue` | none | `loading` | none |
| `principals-panel` | experimental | `src/components/access/PrincipalsPanel.vue` | none | none | none |
| `problem-alert` | experimental | `src/components/migration/ProblemAlert.vue` | none | `error` | none |
| `profile-card` | experimental | `src/components/account/ProfileCard.vue` | none | none | none |
| `public-repositories-dialog` | experimental | `src/components/dashboard/PublicRepositoriesDialog.vue` | none | none | none |
| `rail-brand` | experimental | `src/components/header/RailBrand.vue` | none | none | none |
| `read-only-toggle` | experimental | `src/components/dashboard/ReadOnlyToggle.vue` | none | `error` | none |
| `rebuild-index-dialog` | experimental | `src/components/maintenance/RebuildIndexDialog.vue` | none | none | none |
| `repositories-table` | experimental | `src/components/repositories/RepositoriesTable.vue` | `header`, `row`, `cell-name`, `empty` | `empty` | none |
| `repositories-view` | experimental | `src/components/repositories/RepositoriesView.vue` | none | none | none |
| `repository-access-card` | stable | `src/components/settings/RepositoryAccessCard.vue` | none | `loading` | none |
| `repository-detail` | experimental | `src/components/repositories/RepositoryDetail.vue` | `header`, `tabs` | `empty`, `loading` | none |
| `repository-members-picker` | stable | `src/components/settings/RepositoryMembersPicker.vue` | none | none | none |
| `repository-overview` | experimental | `src/components/overview/RepositoryOverview.vue` | none | `loading` | none |
| `repository-snippet` | experimental | `src/components/card/RepositorySnippet.vue` | none | none | none |
| `repository-type-badge` | experimental | `src/components/tokens/RepositoryTypeBadge.vue` | none | none | none |
| `repository-wizard` | stable | `src/components/settings/RepositoryWizard.vue` | `header` | none | none |
| `reset-password-dialog` | experimental | `src/components/access/ResetPasswordDialog.vue` | none | none | none |
| `resolved-requests-chart` (no element of its own) | experimental | `src/components/dashboard/ResolvedRequestsChart.vue` | none | none | none |
| `resource-trends` | experimental | `src/components/dashboard/ResourceTrends.vue` | none | none | none |
| `revoke-token-dialog` | experimental | `src/components/identity/RevokeTokenDialog.vue` | none | `error` | none |
| `rotate-token-dialog` | experimental | `src/components/identity/RotateTokenDialog.vue` | none | none | none |
| `run-history` | experimental | `src/components/maintenance/RunHistory.vue` | none | none | none |
| `run-now-dialog` | experimental | `src/components/maintenance/RunNowDialog.vue` | none | `error` | none |
| `search-bar` | experimental | `src/components/search/SearchBar.vue` | none | none | none |
| `search-filters` | experimental | `src/components/search/SearchFilters.vue` | none | none | none |
| `search-rebuild-note` | experimental | `src/components/settings/SearchRebuildNote.vue` | none | none | none |
| `search-result-row` | experimental | `src/components/search/SearchResultRow.vue` | none | none | none |
| `search-view` | experimental | `src/components/search/SearchView.vue` | none | `loading` | none |
| `secret-once-dialog` | experimental | `src/components/identity/SecretOnceDialog.vue` | none | none | none |
| `section-renderer` | stable | `src/components/renderers/SectionRenderer.vue` | none | none | none |
| `security-panel` | experimental | `src/components/access/SecurityPanel.vue` | none | none | none |
| `segmented-control` | experimental | `src/components/md/MdSegmentedButton.vue` | none | none | none |
| `segmented-option` | experimental | `src/components/md/MdSegmentedOption.vue` | none | none | none |
| `settings-attention` | experimental | `src/components/settings/SettingsAttention.vue` | none | none | none |
| `settings-backup-panel` | experimental | `src/components/maintenance/SettingsBackupPanel.vue` | none | none | none |
| `settings-download-menu` | stable | `src/components/settings/SettingsDownloadMenu.vue` | none | none | none |
| `settings-encryption-alert` | experimental | `src/components/support/SettingsEncryptionAlert.vue` | none | none | none |
| `settings-hub` | stable | `src/components/settings/SettingsHub.vue` | none | none | none |
| `settings-import-errors` | stable | `src/components/settings/SettingsImportErrors.vue` | none | none | none |
| `settings-review-sheet` | stable | `src/components/settings/SettingsReviewSheet.vue` | `header` | none | none |
| `settings-view` (no element of its own) | stable | `src/components/settings/SettingsView.vue` | none | none | none |
| `side-nav-layout` | stable | `src/components/layout/SideNavLayout.vue` | `nav`, `page`, `lead` | none | none |
| `side-nav-tree` | experimental | `src/components/layout/SideNavTree.vue` | none | none | none |
| `sign-in-switch-notice` | experimental | `src/components/access/SignInSwitchNotice.vue` | none | none | none |
| `site-footer` | stable | `src/components/footer/SiteFooter.vue` | none | none | none |
| `site-legal` | stable | `src/components/footer/SiteLegal.vue` | none | none | none |
| `skip-link` | stable | `src/components/layout/SkipLink.vue` | none | none | none |
| `snippets-card` | experimental | `src/components/card/SnippetsCard.vue` | none | none | none |
| `sso-provider-dialog` | experimental | `src/components/access/SsoProviderDialog.vue` | none | none | none |
| `sso-providers-panel` | experimental | `src/components/access/SsoProvidersPanel.vue` | none | none | none |
| `stat-tile` | experimental | `src/components/dashboard/StatTile.vue` | none | none | none |
| `status-label` | experimental | `src/components/dashboard/StatusLabel.vue` | none | none | none |
| `storage-usage-panel` | experimental | `src/components/maintenance/StorageUsagePanel.vue` | none | none | none |
| `switch-field` | stable | `src/components/renderers/SwitchField.vue` | none | none | none |
| `sync-step` | experimental | `src/components/migration/SyncStep.vue` | none | none | none |
| `system-info-view` | experimental | `src/components/support/SystemInfoView.vue` | `plugin-tier`, `plugin-grid`, `plugin-card`, `filter`, `support-action`, `support-note` | `loading`, `error`, `empty` | none |
| `tags-field-renderer` (no element of its own) | stable | `src/components/renderers/TagsFieldRenderer.vue` | none | none | none |
| `team-detail` | experimental | `src/components/access/TeamDetail.vue` | none | `loading` | none |
| `team-legacy-grants` | experimental | `src/components/access/TeamLegacyGrants.vue` | none | none | none |
| `team-members-card` | experimental | `src/components/access/TeamMembersCard.vue` | none | none | none |
| `team-parents-card` | experimental | `src/components/access/TeamParentsCard.vue` | none | `loading` | none |
| `teams-panel` | experimental | `src/components/access/TeamsPanel.vue` | none | none | none |
| `time-series-chart` | experimental | `src/components/dashboard/TimeSeriesChart.vue` | none | none | none |
| `token-list` | experimental | `src/components/identity/TokenList.vue` | none | none | none |
| `token-tag` | experimental | `src/components/tokens/TokenTag.vue` | none | none | none |
| `tokens-segmented-control` (no element of its own) | experimental | `src/components/tokens/SegmentedControl.vue` | none | none | none |
| `tokens-segmented-option` (no element of its own) | experimental | `src/components/tokens/SegmentedOption.vue` | none | none | none |
| `type-badge` | experimental | `src/components/repositories/TypeBadge.vue` | none | none | none |
| `unsaved-changes-bar` | stable | `src/components/settings/UnsavedChangesBar.vue` | none | none | none |
| `unsupported-renderer` | stable | `src/components/renderers/UnsupportedRenderer.vue` | none | none | none |
| `verify-step` | experimental | `src/components/migration/VerifyStep.vue` | none | none | none |
| `view-load-error` | experimental | `src/components/util/ViewLoadError.vue` | none | `error` | none |
| `view-loading` | experimental | `src/components/util/ViewLoading.vue` | none | `loading` | none |
| `write-policy-field` | experimental | `src/components/repositories/WritePolicyField.vue` | none | none | none |
| `xml-tag` | experimental | `src/components/card/XmlTag.vue` | none | none | none |
| `your-access` | experimental | `src/components/account/YourAccess.vue` | none | none | none |

## Aliases

| Old name | Replaced by | Removed in |
| --- | --- | --- |
| `support-view` | `system-info-view` | 3.0.0 |

## Selecting in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The attributes stay useful for structural checks and for plugins, which cannot read a role from a utility class.


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
| `adjustments-modal` | experimental | `src/components/browser/AdjustmentsModal.vue` | none | none |
| `all-of-renderer` | stable | `src/components/renderers/AllOfRenderer.vue` | none | none |
| `appearance-preview` | stable | `src/components/settings/AppearancePreview.vue` | none | none |
| `appearance-section-renderer` | stable | `src/components/renderers/AppearanceSectionRenderer.vue` | none | none |
| `array-list-renderer` | stable | `src/components/renderers/ArrayListRenderer.vue` | none | none |
| `artifact-snippet` | experimental | `src/components/card/ArtifactSnippet.vue` | none | none |
| `boolean-switch-renderer` (no element of its own) | stable | `src/components/renderers/BooleanSwitchRenderer.vue` | none | none |
| `breadcrumb-navigation` | experimental | `src/components/browser/BreadcrumbNavigation.vue` | none | none |
| `browser-icon` | experimental | `src/components/browser/BrowserIcon.vue` | none | none |
| `card-menu` | experimental | `src/components/card/CardMenu.vue` | none | none |
| `code-brackets` | experimental | `src/components/card/CodeBrackets.vue` | none | none |
| `code-string` | experimental | `src/components/card/CodeString.vue` | none | none |
| `collection-editor` | stable | `src/components/settings/CollectionEditor.vue` | none | none |
| `conflict-dialog` | stable | `src/components/settings/ConflictDialog.vue` | none | none |
| `constant-renderer` | stable | `src/components/renderers/ConstantRenderer.vue` | none | none |
| `declare-type-dialog` | experimental | `src/components/repositories/DeclareTypeDialog.vue` | none | none |
| `default-header` | stable | `src/components/header/DefaultHeader.vue` | `title` | none |
| `delete-entry-dialog` | stable | `src/components/settings/DeleteEntryDialog.vue` | none | none |
| `delete-entry-modal` | experimental | `src/components/browser/DeleteEntryModal.vue` | none | none |
| `detailed-list-entry` | experimental | `src/components/browser/DetailedListEntry.vue` | none | none |
| `entry-detail` | stable | `src/components/settings/EntryDetail.vue` | `header` | none |
| `entry-list` | stable | `src/components/settings/EntryList.vue` | none | none |
| `factory-reset-modal` | stable | `src/components/settings/FactoryResetModal.vue` | none | none |
| `file-browser-view` | experimental | `src/components/browser/FileBrowserView.vue` | none | none |
| `file-list` | experimental | `src/components/browser/FileList.vue` | none | `error` |
| `file-upload` | experimental | `src/components/browser/FileUpload.vue` | none | none |
| `header-hero` | stable | `src/components/header/HeaderHero.vue` | none | none |
| `login-modal` | stable | `src/components/header/LoginModal.vue` | none | none |
| `md-colour-field-renderer` | stable | `src/components/renderers/MdColourFieldRenderer.vue` | none | none |
| `md-select-renderer` (no element of its own) | stable | `src/components/renderers/MdSelectRenderer.vue` | none | none |
| `md-text-area-renderer` (no element of its own) | stable | `src/components/renderers/MdTextAreaRenderer.vue` | none | none |
| `md-text-field-renderer` (no element of its own) | stable | `src/components/renderers/MdTextFieldRenderer.vue` | none | none |
| `menu-panel` | stable | `src/components/header/MenuPanel.vue` | none | none |
| `new-entry-dialog` | stable | `src/components/settings/NewEntryDialog.vue` | none | none |
| `object-renderer` | stable | `src/components/renderers/ObjectRenderer.vue` | none | none |
| `one-of-renderer` | stable | `src/components/renderers/OneOfRenderer.vue` | none | none |
| `optional-renderer` | stable | `src/components/renderers/OptionalRenderer.vue` | none | none |
| `page-footer` | stable | `src/components/footer/PageFooter.vue` | none | none |
| `plugin-block` | stable | `src/components/plugins/PluginBlock.vue` | none | `error`, `loading` |
| `plugin-page` | stable | `src/components/plugins/PluginPageView.vue` | `header` | none |
| `repositories-table` | experimental | `src/components/repositories/RepositoriesTable.vue` | `header`, `row`, `cell-name`, `empty` | `empty` |
| `repositories-view` | experimental | `src/components/repositories/RepositoriesView.vue` | none | none |
| `repository-access-card` | stable | `src/components/settings/RepositoryAccessCard.vue` | none | `loading` |
| `repository-detail` | experimental | `src/components/repositories/RepositoryDetail.vue` | `header` | `empty`, `loading` |
| `repository-members-picker` | stable | `src/components/settings/RepositoryMembersPicker.vue` | none | none |
| `repository-overview` | experimental | `src/components/overview/RepositoryOverview.vue` | none | `loading` |
| `repository-snippet` | experimental | `src/components/card/RepositorySnippet.vue` | none | none |
| `repository-wizard` | stable | `src/components/settings/RepositoryWizard.vue` | `header` | none |
| `search-bar` | experimental | `src/components/search/SearchBar.vue` | none | none |
| `search-filters` | experimental | `src/components/search/SearchFilters.vue` | none | none |
| `search-result-row` | experimental | `src/components/search/SearchResultRow.vue` | none | none |
| `search-view` | experimental | `src/components/search/SearchView.vue` | none | `loading` |
| `section-renderer` | stable | `src/components/renderers/SectionRenderer.vue` | none | none |
| `settings-download-menu` | stable | `src/components/settings/SettingsDownloadMenu.vue` | none | none |
| `settings-hub` | stable | `src/components/settings/SettingsHub.vue` | none | none |
| `settings-import-errors` | stable | `src/components/settings/SettingsImportErrors.vue` | none | none |
| `settings-review-sheet` | stable | `src/components/settings/SettingsReviewSheet.vue` | `header` | none |
| `settings-view` | stable | `src/components/settings/SettingsView.vue` | `header` | none |
| `side-nav-layout` | stable | `src/components/layout/SideNavLayout.vue` | `nav`, `page` | none |
| `site-footer` | stable | `src/components/footer/SiteFooter.vue` | none | none |
| `site-legal` | stable | `src/components/footer/SiteLegal.vue` | none | none |
| `skip-link` | stable | `src/components/layout/SkipLink.vue` | none | none |
| `snippets-card` | experimental | `src/components/card/SnippetsCard.vue` | none | none |
| `switch-field` | stable | `src/components/renderers/SwitchField.vue` | none | none |
| `tags-field-renderer` (no element of its own) | stable | `src/components/renderers/TagsFieldRenderer.vue` | none | none |
| `type-badge` | experimental | `src/components/repositories/TypeBadge.vue` | none | none |
| `unsaved-changes-bar` | stable | `src/components/settings/UnsavedChangesBar.vue` | none | none |
| `unsupported-renderer` | stable | `src/components/renderers/UnsupportedRenderer.vue` | none | none |
| `write-policy-field` | experimental | `src/components/repositories/WritePolicyField.vue` | none | none |
| `xml-tag` | experimental | `src/components/card/XmlTag.vue` | none | none |

## Aliases

No alias is in effect.

## Selecting in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The attributes stay useful for structural checks and for plugins, which cannot read a role from a utility class.


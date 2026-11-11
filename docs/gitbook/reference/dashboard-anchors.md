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
| `adjustments-modal` | experimental | `src/components/browser/AdjustmentsModal.vue` | none | none |
| `artifact-snippet` | experimental | `src/components/card/ArtifactSnippet.vue` | none | none |
| `breadcrumb-navigation` | experimental | `src/components/browser/BreadcrumbNavigation.vue` | none | none |
| `browser-icon` | experimental | `src/components/browser/BrowserIcon.vue` | none | none |
| `card-menu` | experimental | `src/components/card/CardMenu.vue` | none | none |
| `code-brackets` | experimental | `src/components/card/CodeBrackets.vue` | none | none |
| `code-string` | experimental | `src/components/card/CodeString.vue` | none | none |
| `declare-type-dialog` | experimental | `src/components/repositories/DeclareTypeDialog.vue` | none | none |
| `default-header` | stable | `src/components/header/DefaultHeader.vue` | `title` | none |
| `delete-entry-modal` | experimental | `src/components/browser/DeleteEntryModal.vue` | none | none |
| `detailed-list-entry` | experimental | `src/components/browser/DetailedListEntry.vue` | none | none |
| `file-browser-view` | experimental | `src/components/browser/FileBrowserView.vue` | none | none |
| `file-list` | experimental | `src/components/browser/FileList.vue` | none | `error` |
| `file-upload` | experimental | `src/components/browser/FileUpload.vue` | none | none |
| `header-hero` | stable | `src/components/header/HeaderHero.vue` | none | none |
| `login-modal` | stable | `src/components/header/LoginModal.vue` | none | none |
| `menu-panel` | stable | `src/components/header/MenuPanel.vue` | none | none |
| `page-footer` | stable | `src/components/footer/PageFooter.vue` | none | none |
| `repositories-table` | experimental | `src/components/repositories/RepositoriesTable.vue` | `header`, `row`, `cell-name`, `empty` | `empty` |
| `repositories-view` | experimental | `src/components/repositories/RepositoriesView.vue` | none | none |
| `repository-detail` | experimental | `src/components/repositories/RepositoryDetail.vue` | `header` | `empty`, `loading` |
| `repository-overview` | experimental | `src/components/overview/RepositoryOverview.vue` | none | `loading` |
| `repository-snippet` | experimental | `src/components/card/RepositorySnippet.vue` | none | none |
| `search-bar` | experimental | `src/components/search/SearchBar.vue` | none | none |
| `search-filters` | experimental | `src/components/search/SearchFilters.vue` | none | none |
| `search-result-row` | experimental | `src/components/search/SearchResultRow.vue` | none | none |
| `search-view` | experimental | `src/components/search/SearchView.vue` | none | `loading` |
| `side-nav-layout` | stable | `src/components/layout/SideNavLayout.vue` | `nav`, `page` | none |
| `site-footer` | stable | `src/components/footer/SiteFooter.vue` | none | none |
| `site-legal` | stable | `src/components/footer/SiteLegal.vue` | none | none |
| `skip-link` | stable | `src/components/layout/SkipLink.vue` | none | none |
| `snippets-card` | experimental | `src/components/card/SnippetsCard.vue` | none | none |
| `type-badge` | experimental | `src/components/repositories/TypeBadge.vue` | none | none |
| `write-policy-field` | experimental | `src/components/repositories/WritePolicyField.vue` | none | none |
| `xml-tag` | experimental | `src/components/card/XmlTag.vue` | none | none |

## Aliases

No alias is in effect.

## Selecting in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The attributes stay useful for structural checks and for plugins, which cannot read a role from a utility class.


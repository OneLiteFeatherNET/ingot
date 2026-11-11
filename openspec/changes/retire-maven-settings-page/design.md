# Design

## Context

The dashboard is one Vue shell. The registry (`reposilite-frontend/src/navigation/registry.js`) is the one list of pages
that the rail, the bottom bar, the Administration tree, the route meta and the guard read. Settings pages share one draft
(`reposilite-frontend/src/store/configuration.js`), one save bar and one leave guard. The server decides nothing about
navigation: `reposilite-backend/src/main/kotlin/com/reposilite/frontend/DashboardPaths.kt:41-42` serves `/settings` and
`/settings/<path>` with the dashboard document, so every address below is resolved in the browser.

Today the `maven` domain has two presentations on main:

- the Repositories page, `/settings/repositories`, from `add-repositories-admin-page`, which lists and edits the
  `repositories` list (`routes.js:83-84`, `components/repositories/RepositoriesView.vue`);
- the Maven page, `/settings/maven`, which is the generic domain page. It shows a link card to the Repositories page
  (`components/settings/SettingsView.vue:519-541`), and below it the domain-level fields as one generated form
  (`SettingsView.vue:164-175`, rendered at `:548-557`). The only domain-level field is `resolutionCacheClusterMaxAgeSeconds`
  (`reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenSettings.kt:59`).

The maintainer's decision is that the Maven page is no longer needed. The design removes it from navigation, sends its
addresses to the Repositories page, and moves the domain-level form onto that page, so the one option stays editable.

## Decisions

### D1. The Maven page address redirects to the Repositories page

`/settings/maven` is a redirect, not a page. The router already redirects two old addresses of the same domain in one
block (`reposilite-frontend/src/router/create.js:62-69`). That block gains two cases, and the code keeps the same style:

- `settings` with `domain: maven`, with or without a query, goes to `settings-repositories` with the query kept
  (`replace: true`). This subsumes the `?new=repository` case at `create.js:67-68`, which keeps working with the same result.
- `settings-entry` with `domain: maven` and `collection: repositories` already goes to `settings-repository` (`create.js:64-66`).
  Nothing changes there.
- A new route name for `/settings/maven/repositories` with no entry is needed, because today that address matches no route
  (`routes.js:85-89`: the entry route needs three segments after `settings`, and the generic route takes one). The simplest
  fix is one route `{ name: 'settings-maven-repositories', path: '/settings/maven/repositories', redirect: { name: 'settings-repositories' } }`
  placed before the generic `settings` route. It carries no `meta` and no component, like the other redirect routes
  (`routes.js`, the `tokens` and `settings-authentication` entries). Its name is listed in `EXEMPT_ROUTES` in `registry.js`,
  because `unlistedRoutes` fails on any route name without an entry (`registry.js`, `unlistedRoutes`, `EXEMPT_ROUTES` at `registry.js:247`).

Why in the client: the server serves the dashboard document for any `/settings/...` address (`DashboardPaths.kt:41-42`), so
a bookmark reaches the dashboard, and the client decides where it goes. A server redirect would need a route change and
would not know the router. The old redirect for `?new=repository` is a client redirect too, so the pattern is the same.

The `repositories-routes.test.js:91-97` case "does not redirect the maven domain page" asserts the opposite. It is replaced
by the redirect case, as the test list in tasks.md names.

### D2. The domain-level fields are a section of the Repositories page

The Repositories page gets one section after the table and the filter, headed "Maven options" (proposal open question 1
and 4). The section is a new component, `components/repositories/MavenOptionsPanel.vue`, and it renders the same generated form
the Maven page renders today:

- The form is built from the served `maven` schema with the non-collection properties only. The code that does this is
  `domainForm` in `SettingsView.vue:164-175`. It moves unchanged into a helper `components/settings/domainForm.js` with one
  export, `domainFormOf(schema, collectionKeys)`, which returns `null` when there is no field. `SettingsView.vue` keeps using
  it for every other domain that has scalar fields (`web`, `frontend` and the others), so the helper is shared, not copied.
- The renderers and the form configuration are the ones `SettingsView.vue` already uses: `useConfiguration().renderers`
  (`SettingsView.vue:75`) and `formsConfiguration` (`SettingsView.vue:410-412`). The panel reads them the same way.
- A change calls `store.setDomainData('maven', event.data)` and `store.validateDomain('maven')`, the two calls of
  `onDomainChange` (`SettingsView.vue:235-238`). `event.data` is the whole domain object, the one the form was given, so the
  `repositories` list is written back unchanged. The test in tasks.md pins this: editing the option never changes a
  repository. The form must never be given a copy without the list.
- When `domainFormOf` returns `null`, the section is not rendered. Today the schema has the one field, so it is rendered.

The panel is inside `RepositoriesView.vue` and is rendered by the same route, so it inherits the page's permission (D5), its
draft and its save bar (D6).

Alternatives rejected: a second route `/settings/repositories/options` (a new address to remember, and a new page in the
tree for one field); keeping the Maven page for the option only (the page the maintainer wants gone); a section in the
Settings hub (the hub shows saved values and links, it does not edit fields, see `settings-navigation`).

### D3. The Maven domain is removed from the tree, the hub and the side navigation

Three lists name `maven` and all three change:

- `reposilite-frontend/src/navigation/registry.js:151` `REPOSITORY_DOMAINS` lists `maven`. The constant loses `maven`.
- `registry.js:315` `SETTINGS_GROUPS` lists `maven` under Repository. It loses `maven`. `settingsGroupOf` (`registry.js:324`)
  has no caller in the components on main, so removing `maven` from the mapping changes nothing on the page; the domain is
  simply not listed anywhere.
- A new list `DOMAINS_ON_OTHER_PAGES = ['maven']` in `registry.js` names the domains that have a page of their own and are not
  a settings tab. `settingsTabDomains` (`reposilite-frontend/src/components/settings/accessDomains.js:42`) drops those domains
  as it drops the access domains, so the hub (`settingsHub.js:39-40`), the side navigation (`SettingsView.vue:125-128`) and the
  default selected domain of the store (`store/configuration.js:156`) all stop seeing `maven`.

The list is separate from `ACCESS_DOMAINS` on purpose. An access domain is edited under Access, a domain on another page is
edited under Repositories, and the two rules may change apart. `settingsTabDomains` takes both lists into one filter, so no
caller changes.

The Repository group of the tree keeps Repositories (`registry.js:164`), Cleanup policies and Javadoc. `ADMIN_TREE` already
has the Repositories entry, so the tree needs no new entry.

The hub's "one domain forwards" rule (`SettingsView.vue:139`, `SettingsView.hub.test.js:177-180`) counts the tabs. With
`maven` out of the list, a server with the default domains shows the hub with the cleanup, javadoc and other cards. A server
that exposes no other domain forwards `/settings` to the first page the visitor may open (`registry.js`, `administrationLocation`),
which for an administrator is Repositories through the Settings entry, as the existing rule does for one domain.

### D4. Server, API, schema and stored data do not change

The server keeps everything it serves:

- `GET /api/settings/domains` still lists `maven`, because the store needs the domain to save, export and import it.
  `SettingsEndpoints.kt:72` is unchanged.
- `GET /api/settings/domain/maven` and `PUT /api/settings/domain/maven` (`SettingsEndpoints.kt:120` and `:157`) are unchanged.
  A `PUT` still replaces the whole domain, and validation is the same (`MavenSettings.kt:62`, `violations`, and the checks of the types below it).
- `GET /api/settings/schema/maven` (`SettingsEndpoints.kt:98`) is unchanged, and the schema is the same. The Repositories page
  already reads the schema for its columns (`SettingsView.vue:135`).
- The stored file, `configuration.cdn` and the shared settings document keep the `maven` key and its fields.

Export, import and factory reset keep the `maven` domain as they do today, because they run over the list of domains and that
list does not change (`settingsExport.js`, `settingsImport.js`, `FactoryResetModal.vue`). Only the places that show the domain
change.

This is the drop-in contract at work: a Reposilite installation pointed at this build keeps every setting, and a script that
writes `/api/settings/domain/maven` keeps working.

### D5. Permissions do not change

The Maven page and the Repositories page both use the route meta `routeMeta('settings')`, which requires the node
`ingot.admin.settings` (`reposilite-frontend/src/store/nodeAccess.js:30`, `registry.js:208-214`, `routes.js:83-85`). The Maven
options section lives on the Repositories page, so it needs the same node. The `PUT` of the domain is checked by the server
with the same node, so a visitor who could save the domain before can save it now, and a visitor who could not still cannot.

A visitor who may not open the page sees the no access message at the address, as the Repositories page already shows
(`add-repositories-admin-page`, requirement "Page address and access"). No repository data and no schema are requested for
the section in that case, because the page does not mount it.

Nobody gains or loses access. The change tests this with a visitor who has only the audit node: the Maven options section is not
rendered, the Settings tree does not list the domain, and `/settings/maven` does not show the domain.

### D6. The draft, the save bar and the leave guard are shared, as before

The Maven options section edits the same store as the repositories. Its change appears in the save bar with the repository
changes, one save writes the whole domain, and the leave guard covers both. No new save path is added. The conflict dialog
(`ConflictDialog.vue`) applies to the domain as a whole, the same as before.

### D7. Links point to the new addresses

Three places build the old addresses. Each changes to the new one. The old addresses keep working through D1, so a link that is
copied before this change still opens the page.

| Place | Today | After |
| --- | --- | --- |
| `components/dashboard/gettingStarted.js:83` (the "new repository" checklist item) | `{ name: 'settings', params: { domain: 'maven' }, query: { new: 'repository' } }` | `{ name: 'settings-repositories', query: { new: 'repository' } }` |
| `components/dashboard/PublicRepositoriesDialog.vue:53` (`visibilityRoute`, the address of a repository) | `/settings/maven/repositories/<id>` | `/settings/repositories/<id>` |
| `components/browser/FileList.vue:50` (edit the members of a repository) | `{ name: 'settings-entry', params: { domain: 'maven', collection: 'repositories', entry } }` | `{ name: 'settings-repository', params: { id: entry } }` |

`RepositoryWizard.vue` has no link to the Maven page on main (`RepositoryWizard.vue:310` already goes to `settings-repository`).

### D8. Accessibility of the new section

The section follows the rules of `add-repositories-admin-page` for the page it sits on, and adds nothing new:

- It is a `section` with `aria-labelledby` pointing to its `h2` "Maven options", and the h2 is in document order after the table.
- Each field keeps the label and the description the generated form already gives it (`showUnfocusedDescription: true`,
  `SettingsView.vue:410-412`), so the description of `resolutionCacheClusterMaxAgeSeconds` is read with the field.
- An invalid value is marked `aria-invalid` and described by its message, the same as every other field of the draft. The
  save error of the server reaches the save bar as it does today (`saveError`, `SettingsView.vue:102`, `:570`).
- Opening the page does not move focus into the section. The focus rule of the Repositories page stays: focus goes to the page
  heading after a change of the list, and the section does not take it.

This design does not claim WCAG conformance for the section beyond what the form renders today. The manual check in tasks.md
covers the keyboard path.

### D9. Documentation

The reference pages are the place an operator looks, and they name the Maven page today:

- `docs/gitbook/reference/dashboard-pages.md`: the Repository group row of the Administration tree (line 67) drops
  **Maven settings**; the route table gets the new redirect route (near line 47-49); the redirect table (lines 76-81) lists the
  `/settings/maven` address and the no-entry address; the hub paragraph (line 91) lists the Repository group without `maven`.
- `docs/gitbook/reference/repository-settings.md`: the section "Repositories page" (lines 18-22) gets a sub-section "Maven
  options" that names the one field and links to `settings-domains.md#maven`.
- `docs/gitbook/reference/settings-domains.md`: the `maven` row of the table (line 46) says that the domain is edited on the
  Repositories page. The `maven` section (line 207) keeps its field list.
- `docs/gitbook/explanation/storage-and-mirror-caching.md:117` names `resolutionCacheClusterMaxAgeSeconds` in the `maven` domain.
  The sentence gains "on the Repositories page, under Maven options".

A grep for `Settings, then Maven` and for "Maven settings" in `docs/gitbook` is part of the verification. The how-tos on main
(`create-hosted-repositories.md`, `create-proxy-repositories.md`, `create-a-group-repository.md`, `import-from-nexus.md`) say
"the maven settings" as the name of the data, not a click path, so they stay correct and are not edited.

## Risks

- **Store default domain.** `store/configuration.js:156` picks the first tab domain when the selected domain is missing. With
  `maven` out of the tabs, the default changes. Mitigation: the store test in tasks.md covers the default with the default
  domain list.
- **The hub forwarding rule.** It counts tabs, so the forwarding of `/settings` changes for a server whose domains are only
  `maven` and the access domains. Mitigation: the rule of D3, tested.
- **A plugin that names `maven` as a card.** A plugin cannot name a domain's place today (`SECTION_SETTINGS_GROUPS` and
  `treeGroupOf` only place plugin sections). Nothing to do.
- **Bookmarks of the Maven page with a query other than `new`.** The redirect keeps the query, so the page opens with the same
  query on the Repositories page. A query the Repositories page does not read is ignored.
- **The shared form helper.** `SettingsView.vue` has one generated form for every domain. Moving `domainForm` must not change the
  forms of the other domains. Mitigation: the existing `SettingsView.domainSchemas.test.js` and `SettingsView.test.js` run
  unchanged, and their results are the check.

## Open Questions

The open questions of the proposal apply here, with the same defaults:

1. Heading of the section: "Maven options".
2. `/settings/maven/repositories` with no entry: redirects to the Repositories page.
3. No card for the Maven domain in the hub.
4. The section sits below the table.
5. The hub forwarding rule decides when `maven` is the only domain left.

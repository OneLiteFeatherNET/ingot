# Proposal

## Why

The dashboard's navigation was built one section at a time, and it shows. The section table lives in `pages/index-tabs.js`,
the route table in `router/routes.js`, the Access tabs are listed a second time in `components/access/AccessView.vue`, and
the bottom bar hard-codes which sections overflow into "More". Each list answers the same question (which pages exist,
who may open them, where they are shown), and they drift: the machine accounts tab is filtered by a node that the server
does not check for that page, which is fixed separately. A reader of the rail sees one flat list of eight or nine items
with no grouping, and "Overview" for the page that lists the repositories. Settings opens on its first domain tab, so there
is no place that says what Settings contains or what it currently holds. Access and Settings both use a horizontal tab row
that overflows on a narrow screen. A deep link to a page the visitor may not use is silently sent to Overview with a toast.

The research behind this change (a survey of how comparable products such as Portainer, Backstage, Nexus, Rancher,
Jenkins, GitLab, GitHub Primer and Discourse group their navigation, and what the Ingot code does today) recommends one
registry that every navigation surface reads, a grouped rail, a split layout with a side navigation for Access and Settings,
and a settings hub. This change carries steps 2 to 4 of that recommendation. Step 1 (the machine accounts check) ships on its
own first, because it is a bug fix and not a restructuring.

Facts checked against origin/main while writing this proposal (a35c0d77):

- `reposilite-frontend/src/pages/index-tabs.js` holds the section table (`SECTIONS`, label, node, `overflow` flag).
  `reposilite-frontend/src/helpers/navigation-rail.js` holds only the rail width and `helpers/bottom-bar.js` only the bar
  height. No file holds the rail entries except `index-tabs.js`.
- `reposilite-frontend/src/router/routes.js` carries the node per route (`ACCESS_MACHINES` already uses the users node).
  `AccessView.vue` line 59 still filters the machine accounts tab by `ADMIN_NODES.tokens`, which is the step 1 fix.
  `IndexPage.vue` keeps a third copy, the `ACCESS_TABS` map from route name to tab.
- The bottom bar rule is a static `overflow` flag per section (five sections are flagged), not a rule over the permitted
  entries. For a visitor who may only open the audit log, today's bar shows Overview, Search and More (with Audit in it).
- A denied deep link is answered by `createManagerGuard` in `router/guard.js` and the watcher in `IndexPage.vue`: both send
  a signed-in visitor to Overview with the toast "You do not have access to that page". An anonymous visitor gets the sign in
  dialog.
- `components/header/MenuPanel.vue` already has a "My account" link to `/account` and a "Logout" button. There is no
  "Sign out" label to add.
- `components/settings/SettingsView.vue` has no hub. With no domain in the address it shows the first domain tab
  (`tabDomains[0]`). `components/settings/accessDomains.js` already removes `login` and `authentication` from the tabs and
  shows a card for them. Since PR #71 (`f7a39739`), the `settings-authentication` route redirects to `access-ldap`. That
  PR is merged, not open.
- `/settings/login` is not a redirect today. It opens the Settings page on the `login` domain and shows the access card.
- `reposilite-backend/src/main/kotlin/com/reposilite/frontend/DashboardPaths.kt` and the `location` regex in
  `reposilite-frontend/docker/default.conf.template` already cover `/settings/<path>` and `/access/<path>`. The
  navigation change adds no top-level segment, so neither file changes.

## What Changes

- **One navigation registry.** A new module `reposilite-frontend/src/navigation/registry.js` lists every page that the
  navigation or a guard knows about: key, route name, label, area, permission (`node`, `anyNode` or manager only), the
  bottom bar order, and for Access and Settings the child pages of the side navigation. The rail, the bottom bar, the More
  sheet, the Settings hub, breadcrumbs, the route guard and the Access tabs read it. `routes.js` keeps paths and route names
  and takes its permissions from the registry. The first code pull request does this with no visible change.
- **Grouped navigation rail.** The rail shows four groups in this order, with a divider between them and the group name as
  the accessible label of each group:
  - Work: Repositories (formerly Overview, same route `/` and `/browse/...`, same route name `overview`), Search
  - Monitor: Dashboard, Analytics, Audit log (formerly Audit, route `/audit`)
  - Administration: Access, Settings
  - Troubleshoot: Console, Maintenance
  A group without a visible entry is left out. The rail's expanded form at 1200px is not part of this change (see design).
- **Bottom bar rule.** The bar shows the first four permitted entries in the registry's bar order (Repositories, Search,
  Dashboard, Access, Settings, Analytics, Audit log, Console, Maintenance) and a "More" item when any permitted entry is
  left over. The More sheet keeps the group names as headings. For a full administrator the bar stays as it is today
  (Repositories, Search, Dashboard, Access, More). A visitor with a single admin permission now gets that page in the bar
  without a More item.
- **My account.** Stays in the avatar menu, read from the registry as the account entry. No new entry is added there.
- **Explicit no access state.** A signed-in visitor who opens a page without its permission sees the page's place with a
  "No access" message in the page body, at the same address. The visitor is not sent to Overview. An anonymous visitor is
  still asked to sign in. The toast goes away with the redirect (decision D8 in the design).
- **Split layout for Access and Settings.** Both pages get one layout: a grouped side navigation on the left with one
  address per page and the current page marked. Below 600px the side navigation is an index list, and each page has a back
  arrow to it. The horizontal tab rows go away.
- **Settings hub at `/settings`.** With no domain in the address, Settings shows category cards for the domains the visitor
  may edit, each with its saved current value (for example the number of repositories). Domains managed under Access are
  not listed as cards; the hub has one line that points to Access for sign in and security. A hub with a single visible
  page forwards to that page. Access forwards in the same way when only one page is visible.
- **Settings login redirect.** `/settings/login` redirects to `/access/sso`, the way `/settings/authentication` already
  redirects to `/access/ldap`. Old addresses keep working. The draft model of Settings does not change: one draft across all
  domains, one save bar, one leave guard.

### Shipping

This is one change with two code pull requests, merged in this order. The project rules ask that a change which mixes
Conventional Commits types becomes two changes. The maintainer chose one change for this restructuring, so the two types
are named here and kept apart in the pull requests:

1. `refactor(ui): drive navigation from one registry`: the registry, no visible change. Ships first.
2. `feat(ui): group the navigation and add side navigation for access and settings`: grouping, labels, bottom bar rule,
   no access state, split layout, settings hub, login redirect. Ships second, on top of the merged refactor.

The proposal pull request is `docs(openspec): propose restructure-navigation`. It contains only the files under
`openspec/changes/restructure-navigation/`, and its tasks stay unchecked.

### Out of scope

- Step 1, the machine accounts check (`fix(ui): check machine accounts against the users area`). It ships first, as a
  separate change, and this change assumes it is merged.
- Step 5: repository settings under `/settings/repositories`, the "Configure" and "Browse files" links, the "New
  repository" action in the rail, and the move of the repository collection out of the Maven tab.
- Step 6: `/settings/backup` with import, export and factory reset, the diff preview for imports, and the renames "Audit
  retention" and "Search index". Until step 6 lands, the settings header keeps today's import, export and reset actions.
- Step 7: search across settings and access (filter field, keywords, field anchors).
- Step 8 (optional): an instance health chip in the top bar. It waits for the multi-instance decision.
- Expanded rail with group names at 1200px.
- Any server, API, configuration, environment variable, container or Helm change. No dashboard segment is added or removed.
- Breadcrumbs for the repository browser (`components/browser/BreadcrumbNavigation.vue`). Breadcrumbs appear on the Access
  and Settings pages only.
- Deploy and cluster pages. Their place is decided by the deploy modes and cluster bus proposals.

## Capabilities

### New Capabilities

- `dashboard-navigation`: the navigation registry, the grouped rail and bottom bar, permission filtering, the account
  entry, the no access state and the address compatibility of all of these.
- `settings-navigation`: the split layout of Access and Settings, the narrow screen index, the settings hub, the auto
  forward and the login redirect.

## Impact

- **Operators:** nothing to change. No configuration key, environment variable, volume, container path, port or manifest
  changes. A deployment that is pointed at this image sees the new grouping, the new label "Repositories" and the new bar
  for partial roles. The API is unchanged. This change is not BREAKING.
- **Bookmarks and links:** every existing address keeps working. `/settings/login` changes from a card to a redirect. Old
  labels appear in the documentation, which is updated in the second pull request (`docs/gitbook/reference/dashboard-pages.md`
  and `docs/gitbook/reference/web-routes.md`).
- **Code:** `reposilite-frontend/src/navigation/` (new), `router/routes.js`, `router/guard.js`, `pages/index-tabs.js`
  (removed after its tests move), `pages/IndexPage.vue`, `components/access/AccessView.vue`,
  `components/settings/SettingsView.vue`, new layout and hub components, and their tests. No file under
  `reposilite-backend/` changes.
- **Commits:** the first pull request uses the commit types `test` and `refactor`, the second `test`, `feat` and `docs`,
  all with the scope `ui`. The pull request titles, squash-merged onto main, are the two titles above. Neither carries `!`
  or a `BREAKING CHANGE:` footer.

## Verification

- Each group starts with a failing Vitest test. The test is run and seen to fail before the code exists.
- `npm test` in `reposilite-frontend` passes. The existing tests that name the navigation (`IndexPage.test.js`,
  `guard.test.js`, `create.test.js`, `legacy.test.js`, `AccessView.test.js`, `SettingsView*.test.js`) change only where a
  behaviour in this proposal changes on purpose, and each such change is listed in the pull request.
- `grep -rn "Overview" reposilite-frontend/src` finds no navigation label after the second pull request, except the route
  name and the page content that is not navigation.
- `openspec validate restructure-navigation --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/restructure-navigation`
  prints nothing.

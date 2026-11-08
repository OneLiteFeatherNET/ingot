# Design

This design covers the frontend navigation of the dashboard. It is written against origin/main at a35c0d77. The
decisions D1 to D12 are the ones this change takes. The open questions at the end are the ones the maintainer still has to
answer before the second code pull request is opened; the defaults chosen here apply until they are answered.

## Context

- The rail and the bottom bar draw from `SECTIONS` in `reposilite-frontend/src/pages/index-tabs.js`. Each entry has a key,
  a label, a manager flag, a node or `anyNode`, a route name and an `overflow` flag. `IndexPage.vue` turns these into
  destinations and decides what the bar shows. `helpers/navigation-rail.js` and `helpers/bottom-bar.js` hold dimensions only.
- The route table `reposilite-frontend/src/router/routes.js` repeats the permission per route (`adminRoute(section, node)`).
  `ACCESS_TABS` in the same file is the third list of access pages. `AccessView.vue` has its own `ALL_TABS` with labels and
  nodes. `IndexPage.vue` has an `ACCESS_TABS` map from route name to tab key. Four places say which access page exists.
- The guard `createManagerGuard` in `router/guard.js` and the watcher in `IndexPage.vue` both send a denied, signed-in
  visitor to `overview` with the toast "You do not have access to that page". `routeAllowed(meta, can, isManager)` is the
  permission check both use.
- `SettingsView.vue` loads all domains into one store (`store.schemas`, `configurations`, the draft and `settingsDiff`).
  The domain tabs come from `settingsTabDomains` in `accessDomains.js`, which removes `login` and `authentication`. The
  `settings` route is `/settings/:domain?`, so the hub needs no new path: it is the view with no domain.
- The server reserves the top-level segments in `DashboardPaths.APP_ROUTES`. `/settings/<path>` and `/access/<path>` are
  already listed, and the nginx location regex in `reposilite-frontend/docker/default.conf.template` already matches them.
- The machine accounts check is changed in a separate change (step 1). This change assumes that the Access tabs use the users
  node for machine accounts. The check is one of the first things a test in this change asserts, so a regression is seen.

## Goals

- One list of pages that every navigation surface, guard and hub reads, so that a page is added or renamed in one place.
- A rail that a reader can scan: four named groups, Repositories as the first entry, Audit named by what it is.
- An Access and a Settings page that look and move the same way, and that work on a phone without a horizontal tab row.
- A Settings entry point that says what is in Settings and what it currently holds.
- Visitors who lack a permission see why a page is not there, not a silent move to Overview.
- No change to the server, the API, configuration, the container or the Helm chart.

## Non-goals

- The machine accounts node fix (step 1), the repository settings (step 5), backup and reset (step 6), settings search
  (step 7) and the instance health chip (step 8).
- A new top-level path segment such as `/admin`. The segments of the dashboard stay as they are, and the server's reserved list
  does not change.
- Auto-save or per-section saving. The Settings draft stays one draft across all domains.
- Changes to how a plugin's settings domain is validated or named.

## Decisions

### D1. One navigation registry

A new module `reposilite-frontend/src/navigation/registry.js` exports the list of pages (`PAGES`) and small functions over it.
Each entry has these fields:

- `key`: the stable identifier used by the rail, the bar and the tests (`overview`, `search`, `dashboard`, `analytics`,
  `audit`, `access`, `settings`, `console`, `maintenance`, `account`).
- `route`: the route name in `routes.js`.
- `label`: the text in the rail, the bar, the More sheet, the breadcrumbs and the document title.
- `area`: `work`, `monitor`, `administration`, `troubleshoot`, or `none` for pages outside the rail (the account page).
- `permission`: `{ node }`, `{ anyNode }`, or `{ manager: false }` for open pages, and `signedIn` for the account page.
- `barOrder`: a number for the bottom bar rule (D3). Pages without a number are never in the bar.
- `children`: for Access and Settings, the side navigation items, each with a route name, a label, a group heading and its own
  permission. The Access children are users, machine accounts, teams, SSO, LDAP, security and check. The Settings children are
  the domains (filled from the schema at run time, see D9).

`routes.js` imports the registry and builds the `meta` of each route from the entry of its `key` (`section`, `manager`, `node`,
`anyNode`). Paths and route names stay in `routes.js`. The helper `routeAllowed` and the guard do not change their behaviour
in the first pull request.

Consumers, all reading the registry only: the rail, the bottom bar and More sheet (`IndexPage.vue`), the Settings hub (D9),
the breadcrumbs of the split layout (D6), the route guard, the Access tabs in `AccessView.vue` and the `ACCESS_TABS` map in
`IndexPage.vue`. `pages/index-tabs.js` is removed once its tests move to the registry tests.

A tree test guards the registry. It asserts that every named route in `routes.js` is either the route of a registry entry or a
child of one, or is on a short list of exempt names (`not-found`, the legacy redirects, `settings-entry`, `access-user`,
`access-machine`, `access-team`, `token`, `token-new`, `tokens`, `settings-authentication`, `settings-login`). A new page that
is added to the router without a registry entry fails this test.

Alternative considered: generating `routes.js` from the registry. Rejected for now: the route table has redirects and dynamic
redirects that read the parameters, and generating them would move complexity into a generator without a reader gaining from it.

### D2. Groups, labels and order

The rail groups are Work (Repositories, Search), Monitor (Dashboard, Analytics, Audit log), Administration (Access, Settings) and
Troubleshoot (Console, Maintenance). The order inside a group follows the list above. The renames are:

- Overview becomes Repositories. The route name `overview`, the paths `/` and `/browse/:qualifier*`, and the router's `overview`
  redirect targets stay. `legacy.js` keeps mapping old links to `overview`.
- Audit becomes Audit log. The path `/audit` and the route name `audit` stay.

Analytics stays a separate page with its own route `/analytics` (decided by the maintainer).

Labels that are the names of settings domains (Audit retention, Search index) are not changed in this change. They belong to
step 6, where the domains are grouped under backup and reset.

### D3. Bottom bar rule

The bar shows the first four permitted entries in the `barOrder` order: Repositories, Search, Dashboard, Access, Settings,
Analytics, Audit log, Console, Maintenance. If any permitted entry is left over, the bar adds a More item at the end. The More
sheet lists the leftovers under the group headings of D2. If nothing is left over, there is no More item.

This replaces the static `overflow` flag. For a full administrator the result is unchanged (Repositories, Search, Dashboard,
Access, More). A visitor with exactly one admin permission now sees that page in the bar. A visitor who may only open the audit
log today sees Overview, Search and More with Audit under it, and after this change sees Repositories, Search and Audit log.

The first pull request keeps the old result: it reads the `overflow` flags from the registry and produces the same bar. The rule
is switched in the second pull request, with a test for each permission set.

### D4. The rail in 80 pixels

The rail stays 80 pixels wide from 600px. Groups are drawn as a divider between them, and each group is an element with the group
name as its accessible label, so that a screen reader reads the group. The expanded rail with group names at 1200px is not part
of this change (open question Q1). With that answer the rail does not need a second width, and `RAIL_WIDTH` stays as it is.

Items are the same real links as today (`router.resolve`), so a new tab and the browser history keep working.

### D5. My account stays in the avatar menu

`MenuPanel.vue` already links to `/account` and offers Logout. The second pull request reads the link's label and route from the
registry entry `account`. There is no new entry in the menu, and "Sign out" is not introduced, because the existing label is
Logout.

### D6. Split layout for Access and Settings

One component, `reposilite-frontend/src/components/layout/SideNavLayout.vue`, takes the registry children of a page and the page
content as a slot. On widths from 600px the layout shows a grouped side navigation (a list of links in headed groups, with the
current page marked by `aria-current="page"`) and the content on the right. Each child has its own address, and a link is a real
link.

Below 600px the side navigation becomes an index: the page is a list of the groups and their links. A page below the index has a
back arrow in its header that returns to the index. The width of the switch is passed to the component as a property that the
parent computes from a media query, so the component's test sets the value without reading the window.

The breadcrumb of a page is built from the registry: Access > Users, Settings > Maven defaults. The repository browser's own
breadcrumb (`components/browser/BreadcrumbNavigation.vue`) is not changed.

The horizontal tab rows of `AccessView.vue` and `SettingsTabs.vue` go away in the second pull request. The page bodies do not change:
the users panel, the teams panel, the provider panels and the settings form render as they do today inside the layout.

### D7. Auto forward and the Access address

When a page of the layout has exactly one visible child, its address forwards to that child with `replace`. This applies to
Settings (one visible domain) and to Access (one permitted page). The forward is decided by the permissions, not by the width.

`/access` keeps its meaning of "the first permitted Access page". On widths from 600px it forwards to that page, which is today's
behaviour. On narrower widths the address shows the index of the Access pages, so a phone user can choose a page. The behaviour at
the breakpoint is an open question (Q2); the default here is the one stated.

### D8. No access state for deep links

A signed-in visitor who opens a page without its permission stays at the address. The page shows the registry entry's label and the
message "You do not have access to this page", with a link to the Repositories page. The guard no longer redirects a signed-in
denied visitor to `overview`, and the watcher in `IndexPage.vue` no longer sends them there. The info toast is removed with the
redirect (Q3 asks whether it stays).

An anonymous visitor is still asked to sign in, as today. After a successful sign in that grants the page, the page opens. After a
sign in that does not grant it, the no access state is shown at the same address.

The guard keeps the password change redirect (`createPasswordChangeGuard`) unchanged.

This is the one visible change that goes against an existing test: the tests that assert the redirect to `overview` are updated in the
second pull request, and the pull request lists each of them.

### D9. The Settings hub at /settings

With no domain in the address, `SettingsView.vue` shows the hub instead of the first domain. The hub has category groups. A domain is
placed in a group by a fixed mapping in the registry, with the domain identifiers of the server schema:

- Repository: `maven`, `javadoc`, `cleanup`
- Instance: `web`, `frontend`
- Data: `statistics`, `audit`, `search`
- Plugins: every other domain the server lists. A plugin never fails to show.

The mapping is checked against the domain list in a test with a fixture of the schema list. A domain that the mapping does not name
falls into Plugins, so a new plugin appears without a change here.

Each card shows the domain title and the saved value, which is a short text built from the domain's collections (for example
"6 repositories") or, for a domain without collections, its one-line description from the schema. The hub reads the saved
configuration, not the draft. If the draft differs from the saved value for a card, the card shows "Unsaved changes" and the hub
keeps the draft unchanged. Opening a card does not save anything.

The hub lists only the groups and cards the visitor may edit. A group without a visible card is left out. The last line of the hub
reads "Sign-in and security live in Access" and links to `/access`.

The Settings page below the hub keeps the single draft: one save bar, the change counter, the review sheet, the per-change revert, the
leave guard and `beforeunload`. The leave guard applies when a visitor leaves Settings with an unsaved draft, not when they move between
Settings pages. This is what the current model does and it is not changed.

### D10. Login redirect and access domains

`/settings/login` becomes a route with a redirect to `access-sso`, placed before the generic `settings` route, as
`settings-authentication` already is. `/settings/authentication` keeps its redirect to `access-ldap`. The access domains `login` and
`authentication` stay in the export, the import and the factory reset, as `accessDomains.js` documents. Their card in
`AccessDomainCard.vue` remains for the case of a visitor who opens a domain address directly, which after this change is only the
`/settings/:domain` route for a domain the redirects do not cover.

### D11. Compatibility of addresses and the server

Every existing path keeps its route name and its meaning. The additions are routes with redirects only (`settings-login`), and no new
top-level segment. `DashboardPaths.kt` and the nginx location regex are not changed, because `/settings/<path>` and `/access/<path>`
already cover every new address. The server needs no change, and no backend test is affected.

Old bookmarks to a page under a new label (for example the Audit page) keep their address. Only the label and the rail group change.

### D12. Two code pull requests in order

The first pull request is `refactor(ui): drive navigation from one registry`. It contains the registry (D1), the consumers moved to
it, the Access tab list taken from the registry, and the tree test. It produces the same rail, the same bar (D3, old rule), the same
labels, the same redirects and the same tests, except for the moved imports. Its commits are `test(ui)` for the failing tests and
`refactor(ui)` for the code.

The second pull request is `feat(ui): group the navigation and add side navigation for access and settings`. It contains D2 to D10.
Its commits are `test(ui)`, `feat(ui)` and `docs(ui)`, the last for the two reference pages. The second pull request is opened only
after the first is merged.

## Risks

- **A central registry is a single point of failure.** A bad entry hides a page for everyone. The tree test (D1) and the permission table
  test (D3) run on every change, and the refactor pull request keeps the output identical, so a wrong entry is seen in its diff.
- **Visible change for muscle memory.** The Overview label, the grouping and the bar result for partial roles change what people see first.
  The labels are the ones listed in D2 and nothing else is renamed in this change.
- **The no access state replaces a redirect people may rely on.** A visitor who is sent to Overview today sees a page with a message
  instead. The message links to Repositories, so the way out is the same.
- **The hub's saved values are read from the store.** A hub value that is wrong, or that is shown as saved while the draft says otherwise,
  is a trust problem. The test pins the saved-value text and the unsaved marker.
- **Plugin domains.** A plugin domain without collections or with a schema without a title shows its identifier, as the tabs do today.
  The Plugins group is never empty for a plugin, and the change does not require a plugin to set a group.

## Migration plan

No operator migration. The change is in the dashboard bundle only. Rollback is a revert of the second pull request, which restores the
tabs and the redirect, and the first pull request is a no-op for the user.

## Open questions

These are the questions for the maintainer. Each has a default that the design uses until the answer is given.

- **Q1. Expanded rail at 1200px.** The research recommends group names in a wide rail. Default: not in this change. Yes would add a
  second width and a second layout for the rail, in the second pull request.
- **Q2. Access on narrow screens.** Default: `/access` forwards on widths from 600px and shows the index below. The other option is
  an index on every width, which removes the forward and makes the Access address a page in its own right.
- **Q3. Toast with the no access state.** Default: no toast, only the message in the page. The other option keeps the toast next to it.
- **Q4. Hub values.** Default: the saved value, built from the collections, with "Unsaved changes" when the draft differs. The other
  option shows only the draft, which would make the hub a summary of what is about to be saved.
- **Q5. Plugin groups.** Default: the group mapping of D9 lives in the frontend, and plugins fall into Plugins. A `group` key in the
  plugin's schema would need a server change and is not part of this change. Say so if a plugin group is wanted now.
- **Q6. One change or two.** The project rules say a change that mixes Conventional Commits types is two changes. This proposal is
  one change with two pull requests, as the maintainer asked. If the rule should apply strictly, the second pull request becomes its
  own change, `restructure-navigation-groups`, and nothing else changes.
- **Q7. Settings domain labels.** Default: Audit retention and Search index are renamed in step 6, not here. Say so if they should
  move now, since the hub shows these titles.
- **Q8. Breadcrumbs.** Default: breadcrumbs on the Access and Settings pages only. The repository browser keeps its own breadcrumb.

# Design

## Context

The dashboard is one Vue shell. The registry (`reposilite-frontend/src/navigation/registry.js`) is the single list of pages
that the rail, bottom bar, Access side navigation, route meta and guard read. Settings pages share one draft store
(`store/configuration.js`) and one save bar. The server decides nothing about navigation: `DashboardPaths.kt` lists the
dashboard paths and `docs/gitbook/reference/web-routes.md` explains the reserved segments.

## Decisions

### D1. The page is a Settings page, not a new store

The page edits `configurations.maven.repositories` through the same store functions the collection editor uses today
(`addEntry`, `setEntry`, `removeEntry` in `store/configuration.js`, called from `SettingsView.vue:201-226`). The save bar,
the conflict dialog and the leave guard therefore work unchanged, and the save is still `PUT /api/settings/domain/maven`.
Alternatives rejected: a `/api/repositories` endpoint (a second source of truth, and the report's principle 4 forbids it for
this change); saving each repository on its own (breaks the one-draft model and the conflict detection).

`SettingsView.vue` (651 lines) is not forked. The repository table, the detail and the wizard mount move into
`components/repositories/`, and `SettingsView` renders them for the route `settings-repositories`. The generic collection
editor keeps serving every other collection (cleanup policies and plugin domains).

### D2. Addresses

| Address | Route name | Notes |
| --- | --- | --- |
| `/settings/repositories` | `settings-repositories` (new) | Placed before `settings` (`routes.js:76`), whose `:domain?` would otherwise take `repositories` as a domain id. |
| `/settings/repositories/:id` | `settings-repository` (new) | The detail. |
| `/settings/maven/repositories/:id` | `settings-entry` (kept) | Redirects to the detail when the domain is `maven` and the collection is `repositories`. Other collections do not redirect. |
| `/settings/maven?new=repository` | `settings` (kept) | Redirects to `/settings/repositories?new=repository`, which opens the wizard once and removes the query, as `SettingsView.vue:193-199` does today. |

The redirects live in `router/create.js` as a `beforeEach` step before the manager guard, because the target depends on the
params of one route. Both new routes use `routeMeta('settings')`, so the node `ingot.admin.settings` decides, exactly as
today. A repository id may contain `.`, `_` and `-` (`collections.js` `ID_PATTERN`), and is URL encoded in the path.

The page name `repositories` is reserved under `/settings` against settings domain ids. The ids the server lists today are
`maven`, `javadoc`, `cleanup`, `web`, `frontend`, `statistics`, `audit`, `search`, `login` and `authentication`; none
collides. A plugin domain that is called `repositories` would be shadowed. The registry test lists the reserved page names
and fails when a known core domain id equals one. A startup warning for a plugin domain is not added here (it would be a
backend change); the reserved names are documented instead (see open questions).

No backend file changes: `/settings/<path>` is already an `APP_ROUTES` pattern (`DashboardPaths.kt`), and the split
dashboard `location` regex covers it.

### D3. Table columns and where each value comes from

| Column | Source | Notes |
| --- | --- | --- |
| Name | `entry.id` | Row header, links to the detail. |
| Type | declared `entry.type`, else derived | See D4. Text plus an icon, never colour alone. |
| Visibility | `entry.visibility` | Public, Hidden, Private, as words. |
| Version policy | `entry.versionPolicy` | Only when the served schema has the field (`add-repository-policy-fields`). Hosted and proxy. A group shows a dash. Absent means Mixed. |
| Write policy | `redeployment`, `readOnly` | Hosted only, derived, see D5. Only when the schema has `readOnly`. Without it, the column shows Allow or Allow once from `redeployment` alone. |
| Storage | `entry.storageProvider.type` | `fs` shown as File system, `s3` as S3. A group shows a dash, as `repositoryTypes.js` `SHOWN` already hides its storage. |
| Address | the base URL plus `/<id>` | Plain text with a copy button. The base URL is the instance origin plus the base path from `store/placeholders.js`, the way `store/maven/repository.js` builds repository addresses (to be confirmed at implementation). |

Size and status are not shown: the list API does not carry them, and the draft contains unsaved entries that the server has
not seen. Columns built from the draft show the unsaved state, and a row of a not yet saved repository carries the text
"Unsaved".

The list is built from the draft, not from `GET /api/maven/repositories`, so a new or deleted repository shows at once and
the page and the save bar agree. The visibility filter and tag chips of the overview are not repeated; the existing text and
`tag:` search is.

### D4. Derived type

The server derives the type from the number of loopback and external references (`RepositoryType.kt:54`). The draft has not
been through the server, so the page derives it again from the draft with the same rule: no references is Hosted, only local
references is Group, only external references is Proxy, both is Mixed, blank references ignored. A reference that is an
`http(s)` address is external (`isRemoteUrl`, `repositoryTypes.js`), anything else names a local repository. The rule is
duplicated on purpose; a test feeds one fixture of entries to the JavaScript function and compares it with the table of
expected types that the backend test `RepositoryTypeTest` uses, so a drift in either fails a test. Whether the backend
treats every non-URL reference as loopback has to be confirmed at implementation (open question 2).

"Declare type" writes `type` with the derived value and nothing else. The only side effect of a declared type is that empty
mirrored entries are dropped (`blankEntriesRemovedBy`, `repositoryTypes.js`), so the action shows that count in a dialog
when it is above zero. A Mixed repository gets no declare action, because no declared type reproduces it. It gets the
warning "References local and remote repositories. Split it into a group and a proxy." as text next to the badge and in the
detail.

### D5. Write policy is derived, not stored

Stored keys stay `redeployment` and `readOnly`. The control is a radio group on the detail page of a hosted repository:

| Choice | Stored result | Selected when |
| --- | --- | --- |
| Allow | `redeployment: true`, `readOnly` removed or false | `readOnly` is not true and `redeployment` is true |
| Allow once | `redeployment: false`, `readOnly` removed or false | `readOnly` is not true and `redeployment` is false |
| Read only | `readOnly: true`, `redeployment` unchanged | `readOnly` is true |

Choosing Read only keeps the value of `redeployment`, so choosing Allow once or Allow again restores the earlier state with
no data loss. A key is written only when the choice changes it, so saving a repository without touching the control leaves
its stored JSON as it was. The server answers `409` for Allow once, as today (`Repository.kt` `acceptsDeploymentOf`) and the
status for Read only is defined by `add-repository-policy-fields`. The control has a help text that names the status code
each choice produces; the page does not invent the code for Read only (open question 3).

### D6. Navigation tree and the registry

The registry changes in three ways, all inside `registry.js`:

1. `PAGES` becomes the four rail entries Browse (`overview`), Search, Dashboard and Administration. Administration has the
   route name `settings` (the hub), `area: 'administration'` and permission `anyNode` of every node that opens an
   Administration page (the settings node, the access nodes, audit, console, maintenance, dashboard for Analytics).
2. A new `ADMIN_TREE` lists the Administration entries in four groups. Each entry names the route, label, permission and,
   for settings domains, the domain. It replaces the Access children list as the source of the side navigation, and the Access
   children keep their route names, details and `landing` flags, because `firstAccessRoute` and `accessTabOf` read them.
3. `groupedSettingsDomains` and `SETTINGS_GROUPS` map the domains into the tree: `maven`, `javadoc` and `cleanup` to
   Repository, every other domain, a plugin domain included, to System.

The tree:

```
Administration  (rail entry, opens /settings, the hub)
  Repository
    Repositories          /settings/repositories            new
    Cleanup policies      /settings/cleanup                 domain cleanup, existing
    Maven settings        /settings/maven                   the rest of the maven domain
    Javadoc               /settings/javadoc                 existing
  Security
    Users                 /access/users                     existing
    Machine accounts      /access/machines                  existing
    Teams                 /access/teams                     existing
    SSO                   /access/sso                       existing
    LDAP                  /access/ldap                      existing
    Security settings     /access/security                  existing
    Permission check      /access/check                     existing
    Audit log             /audit                            existing
  System
    Web, Frontend, Statistics, Audit settings, Search, plugin domains   /settings/<domain>   existing
  Support
    Console               /console                          existing
    Maintenance           /maintenance                      existing
    Analytics             /analytics                        existing
```

Compared with the report's tree (section 6.1), pages that do not exist (Storage, Routing rules, Promotion, Roles,
Selectors, Anonymous access, Tokens, Tasks, Backup, Webhooks, Plugins, Migration, Nodes, System information, Support bundle)
are not drawn. Each later change adds its entry to `ADMIN_TREE`. The labels "Cleanup policies" and "Audit settings" are the
titles the schema already has; the registry does not rename a domain, which keeps the domain routes (`/settings/audit`)
untouched. "Maven settings" is the `maven` domain page after its repositories collection has moved out.

A group with no visible entry is left out, and the Administration rail entry is left out when no group has one. The hub at
`/settings` shows the groups as card sections (the existing `SettingsHub`), now over the tree, so Access and Audit pages are
reachable from the same page. With one visible page the hub forwards to it, as it does today.

The side navigation of every Administration page renders the same tree through `SideNavLayout` (`groupedChildren` is replaced
by a function that reads `ADMIN_TREE`). Access pages and Settings pages therefore show one navigation, with the current page
marked. This replaces the separate side navigations of `restructure-navigation`; its narrow screen index behaviour
(below 600px the navigation is an index list and each page has a back arrow) is kept.

A visitor who only has the audit node sees Administration with one group and one entry; the hub forwards to the audit log.

### D7. Bottom bar and labels

The bar rule is unchanged (the first four permitted entries in bar order, `registry.js` `BAR_SIZE`), and with four entries
a full administrator sees Browse, Search, Dashboard and Administration with no More item. A visitor without the dashboard
node sees Browse, Search and, if any Administration page is permitted, Administration. The Analytics, Audit and other entries
no longer have `barOrder`, since they are inside Administration. `barLayout` keeps its shape; `more` is empty for every
visitor, and the More sheet code stays for later use, unused (a deliberate small leftover; removing it is a refactor for
another change).

The label "Repositories" for `/` becomes "Browse". `document.title` for `/` follows. Routes, route names and the `overview`
key do not change.

### D8. Accessibility design

The page is a plain HTML table, not an ARIA grid. A grid promises arrow key navigation inside the table and requires a
roving tabindex that every screen reader mode handles differently; a table with links and buttons gives screen reader users
table navigation commands and keyboard users plain Tab order, and it is cheaper to get right. Decision recorded; the
requirements are in the spec.

- Structure: `<table>` with a `<caption>` ("Repositories, N of M shown"), `<th scope="col">` headers and the name cell as
  `<th scope="row">`. The heading is an `h1` ("Repositories"), sections in the detail are `h2`.
- Sorting: each sortable header contains a `<button>`, and the header cell carries `aria-sort` (`ascending`, `descending`,
  `none`) on the sorted column only. A polite live region (`role="status"`) says "Sorted by Name, ascending".
- Filter: a labelled search field. The count of matches is a `role="status"` region updated after typing stops being
  processed by Vue (no timers; the count is computed from the model value), so it is announced once per change.
- Badges: text content plus an icon with `aria-hidden="true"`. The derived marker is the word "derived" in the cell, not a
  tooltip. The warning for Mixed is text.
- Row actions: Edit and Delete are buttons or links whose names include the repository: "Edit releases", "Delete releases".
  The Address cell has a button "Copy address of releases" that reports "Copied" in a status region and keeps focus.
- Narrow screens: below 600px the table is kept as a table inside a scroll region with `role="region"`, an accessible name
  and `tabindex="0"`, and the Name column is sticky, rather than a card layout that would drop the table semantics. At
  320px CSS width with 200% zoom the page needs no two-dimensional scroll outside that region (WCAG 1.4.10).
- Dialogs (wizard, delete, declare type): the existing `vue-final-modal` base (`components/settings/dialog.js`,
  `SCRIM_STYLE`) is used. Each dialog has `role="dialog"` or `alertdialog`, `aria-modal`, a name from its heading, moves
  focus in when opened, traps Tab, closes on Escape and returns focus to the control that opened it. The delete dialog puts
  initial focus on Cancel and its confirm button stays disabled until the typed name matches, with the reason as the
  button's description.
- After create the focus goes to the h1 of the new detail. After delete it goes to the h1 of the list, and a status message
  says "releases deleted, not saved yet". Focus is never left on a removed element.
- Errors: each invalid field has `aria-invalid` and `aria-describedby` pointing to its message. The wizard step shows an
  error summary at its top that takes focus when a step is refused.
- Target size 24 by 24 CSS pixels for every control (WCAG 2.2 criterion 2.5.8), focus ring from the existing
  `md-focus-ring` classes, contrast from the Material theme tokens (text and icon pairs are checked against 4.5:1 and 3:1 in a unit test over the theme palette), and no motion beyond
  what `prefers-reduced-motion` allows.
- The navigation tree is a `nav` with the accessible name "Administration", each group a heading followed by a list, and
  the current page `aria-current="page"`. The first focus stop after the skip link target is the h1.

What tests can prove: roles, names, `aria-sort` and `aria-current`, live region text, focus after each action, Tab order of
a table row, Escape and focus return. What they cannot: how a screen reader reads it, and contrast. The manual checklist in
the tasks covers that, with NVDA on Firefox or Orca, and the result goes into the pull request. No axe dependency is added
(the repository has none, and a dependency is a supply-chain decision for the maintainer; see open question 4).

### D9. Testing approach

Vitest with the existing component test kit. Pure functions first: derived type, write policy mapping, filter and sort,
address building, the registry tree and the redirects. Component tests mount the table with a fake store (no network, no
timers; `await nextTick()` instead of waiting). F.I.R.S.T.: each test builds its own store and router, nothing shares state,
no clock reads (the page shows no time).

## Risks

- A user with Settings open and unsaved changes follows an old link to `/settings/maven/repositories/x`. The redirect keeps
  the draft, because the store is shared, so nothing is lost.
- The label change from Repositories to Browse breaks muscle memory and screenshots. It is stated in the documentation
  and in the release notes of the change.
- Putting Access and Audit under Administration with one side navigation changes the page layout of Access for everyone.
  The tabs and URLs are the same.
- A plugin settings domain named like a reserved page is shadowed. Documented, and tested for core domains only.

## Open questions

1. Whether the maintainer wants a startup warning for a plugin settings domain that collides with a reserved page name
   (backend change, small). Without it the collision is silent.
2. The loopback rule. The page derives the type in the browser. If the backend treats a reference as local by looking the
   name up in the repository list rather than by "is not an http(s) address", an entry that names a missing repository would
   derive differently. To confirm in `RepositoryLoopbackClient.kt` and the repository factory before the code is written.
3. The response status for a deploy to a Read only repository belongs to `add-repository-policy-fields`. The help text of the
   control waits for it.
4. Whether to add an automated accessibility checker (axe) as a dev dependency for the dashboard. This change works without
   one.

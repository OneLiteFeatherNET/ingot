# Design

## Open questions

Each question has a default. The tasks implement the default; an answer that differs changes the matching task and nothing else.

1. **Who gets the status when they hold the dashboard node and not the settings node?** Default: they open Overview and see the
   status sections only (D2). Alternative: keep a read-only page at a second address for them. That keeps the rail clean for
   everyone else but keeps a page for a group that is probably a handful of people.
2. **Order of the sections.** Default, top to bottom: Needs attention, Instance status (with the getting started checklist),
   Resources, At a glance, Danger zone. Needs attention is first because it is the only block that asks the operator to act, and it is
   one line when nothing needs attention. Alternative: the maintainer's wording lists the status first and the attention block
   after the counts, which puts the meters on top and Needs attention third.
3. **Heading and description.** Default: the heading stays "Overview". The description "Shared configuration for all instances."
   becomes "Status of this instance and its shared configuration." for a visitor who sees both halves, and "Status of this
   instance." for a visitor who sees only the status. Alternative: leave the description alone.
4. **Where `/dashboard` lands.** Default: `/settings`, with the query and the hash of the old address kept, and no scroll target.
   Alternative: `/settings#instance-status`, which scrolls past Needs attention.
5. **Rail icon and label.** Default: Administration keeps its label and icon. `DashboardIcon` is removed with its last user.
   Alternative: keep the icon file for a plugin that imports it. Plugins cannot import dashboard components, so the default stands.
6. **Label wrap fix: local or in `MdButton`?** Default: local to the Danger zone button (`whitespace-nowrap` on the button, and the
   modal's wrapper does not shrink it). Alternative: `whitespace-nowrap` in `MdButton`, which makes every button label one line by
   design and risks an overflow where a long label sits in a narrow box.
7. **Title clipped under the top bar: what is the expected state?** Default: the heading is fully visible at rest, after a
   navigation, after a focus move to `#main`, and after an anchor jump. A heading that scrolls under the bar while the operator
   scrolls is normal and stays. Alternative: a sticky page header, which is the territory of #250 and not decided here.

## Facts checked on origin/main (3a8cf7bd)

| Subject | Finding |
| --- | --- |
| Route | `dashboard`, `/dashboard`, `meta` from `routeMeta('dashboard')`, node `ingot.admin.dashboard`. Rendered by `IndexPage.vue` (`DashboardView` when `section === 'dashboard'`). |
| Rail entry | `PAGES` in `src/navigation/registry.js`: `{ key: 'dashboard', area: 'rail', barOrder: 3 }`. The rail holds four entries (Browse, Search, Dashboard, Administration); Administration is `barOrder: 4`. |
| Content | `DashboardView.vue`: an `h1` "Dashboard", `GettingStartedCard`, section "Instance status" (`InstanceStatusPanel`, which draws the read-only banner, health rows, cluster summary and the failures sheet) and section "Resources" (`ResourceTrends`). The panels fetch and poll themselves and stop when unmounted. |
| Data | `GET /api/status/instance`, `/snapshots`, `/failures`, `/health` (client.js), all `managerOnly("dashboard")` except `/health`. The read-only banner reads `/api/admin/read-only`. |
| Links to the route | `SettingsHub.vue` (the link line, `{ name: 'dashboard' }`), `router/legacy.js` (`?tab=dashboard` maps to `{ name: 'dashboard' }`), the registry, `IndexPage.vue`. |
| Reserved segment | `dashboard` is in `src/router/dashboard-segments.json`, `DashboardPaths.kt` (`APP_ROUTES`) and the chart copy, which CI compares byte for byte. |
| Anchors | `dashboard-view` (`DashboardView.vue`), `instance-status-panel` with its parts, `getting-started-card`, `settings-hub` (`SettingsHub.vue`). All `experimental` except `settings-hub`, which is `stable`. |
| e2e | `dashboard.spec.js` (2 tests, `/dashboard`), `layout.spec.js` (2 wide-screen tests on `/dashboard`), `navigation-chrome.spec.js` (loop over `/dashboard` and a heading test), `accessibility.spec.js` (`/dashboard` in the page list), `sign-in.spec.js` (no `/dashboard` path, only prose). |
| Overview | `SettingsView.vue` renders `AdministrationFrame` with `:reading="!repositoriesPage"` (so Overview is a 960 pixel reading page), the import and export actions, `SettingsAttention` as `lead`, `SettingsHub` (At a glance and the link line), then the Danger zone section, then the save bar. |
| Overview permission | Tree entry `overview` needs `ingot.admin.settings`. The route `settings` has `meta.node = settings`. `create.js` sends a visitor without the settings node who may open another page to that page. |

## Related OpenSpec changes

| Change | What it decides | How this change relates |
| --- | --- | --- |
| `add-administration-overview` | Overview is the first tree entry, the page is Needs attention plus At a glance plus a Dashboard link, with the file actions. | This change replaces its Dashboard link with the status itself. Both change the requirement "Settings hub at /settings", so the one that archives second rebases its text (D7). |
| `restructure-navigation` | A grouped rail (Work, Monitor, Administration, Troubleshoot) with Dashboard under Monitor. | Not implemented as written: the code has the four entry rail. This change describes the rail as built and does not touch that proposal. |
| `rework-administration-information-architecture` | The Administration tree groups and labels. Analytics sits under Monitoring and support. | Unchanged. Overview stays the first entry and Analytics stays where it is. |
| `rework-administration-side-navigation` | One scroll container, index rows with descriptions, a Sections sheet. | Unchanged. Overview below 1240 pixels stays the page beside the index. |
| `rework-admin-header-and-repository-tabs` (#250, open) | A header that does not jump (stable rows and `scrollbar-gutter`), a header beside the side navigation from 1240 pixels, repository tabs. | Neighbour. Its group 1 and this change both touch `SettingsView.vue` and the frame (D8). |
| `use-wide-screens` | Wide pages use the full width, reading pages keep a measure; the dashboard grid takes two columns at 1920 and three at 2560. | Overview becomes a wide page. Its status block keeps those column rules. |
| `fix-top-bar-chrome`, `fix-surface-hierarchy-and-page-header` | One level one heading per page, the top bar is not a heading, the page header aligns title, description and trail. | The Dashboard `h1` goes away with the page. Overview keeps the one `h1` of the frame, and the status sections are `h2`. |
| `migrate-dashboard-to-typescript` | Slice `C06` renames `src/components/dashboard/`, slice `C10` renames `src/components/settings/`, `P1-3` renames the navigation and router. | Sequencing only (D8). This change adds no file that a slice has to know about. |

## Decisions

### D1. The Dashboard becomes a section of Overview, not a copy

`DashboardView.vue` stops being a page. It keeps its file, its name and its `data-ingot-component="dashboard-view"`, loses the
`h1` and the page container, and becomes the status block that Overview mounts: the getting started card, the section "Instance
status" and the section "Resources", each an `h2` with its `aria-labelledby`. Nothing about `InstanceStatusPanel`,
`ResourceTrends`, the stores, the endpoints or the polling changes. Keeping the file keeps the diff of pull request 3 small and keeps
the TypeScript slice `C06` a pure rename.

The block is mounted lazily and only for a visitor who holds the dashboard node. A visitor with the settings node and without the
dashboard node makes no status request, so the browser console and the server log show no new 403.

### D2. Overview opens for the settings node or the dashboard node; each section checks its own node

Today the status page and Overview have different nodes. The change keeps both nodes and lets the page decide per section:

| Section | Needs |
| --- | --- |
| Needs attention, At a glance, Danger zone, the Import and Download actions | `ingot.admin.settings` |
| Instance status, Resources, the getting started card | `ingot.admin.dashboard` |

- The tree entry `overview` takes `anyNode: [settings, dashboard]`. The Administration entry of the rail already shows for any node
  of any tree page, so a dashboard-only visitor keeps it, and `administrationLocation(can)` opens Overview first, because it is the
  first entry of the tree.
- The route `settings` takes `anyNode: [settings, dashboard]` so the section guard lets the dashboard-only visitor in. Every
  settings domain page, and every page under `/settings/...` that is not Overview, keeps its settings node check in the view: a
  visitor with only the dashboard node who opens `/settings/javadoc` sees the same no access state as today, and the page makes
  no settings request. A test pins this, because widening the route meta is the risky edit of this change.
- The forward rule in `create.js` ("a visitor who may not open the hub is sent to the one page they may open") applies when the
  visitor holds neither node.
- A visitor with both nodes (every administrator) sees everything. A visitor with only the settings node sees Overview as it is
  today plus the new layout. A visitor with neither sees the no access or sign in state of `/settings`, as today.

Rejected: a second route name for a status only Overview. It would keep a page and an address for a very small group, the exact
split this change removes, and it would need its own rail logic.

### D3. The `dashboard` route becomes a redirect with the same name

`routes.js`: `{ name: 'dashboard', path: '/dashboard', redirect: to => ({ name: 'settings', query: to.query, hash: to.hash }) }`,
placed where the entry is today, with `meta` removed. `createAppRouter` already replaces a redirect without a component, as for
`tokens`. The `dashboard` key leaves `PAGES`, so the name joins `EXEMPT_ROUTES` (the routes that have no page of their own), like
`tokens`, and `routeTree.test.js` keeps passing. `legacy.js` keeps mapping `?tab=dashboard` to `{ name: 'dashboard' }`, which now
redirects, so the oldest bookmarks take two hops and end on Overview. The redirect is client side only. The server keeps answering
`/dashboard` with the app shell because the segment stays reserved (D6), so a full page load of the old address works.

The redirect runs before the guards of the target, so an anonymous visitor reaches `/settings` and gets the sign in prompt with the
dialog, exactly the experience `/dashboard` gave.

### D4. Overview is a wide page with a fixed order

`SettingsView` passes `:reading="false"` for Overview. The content, top to bottom (question 2 has the alternative):

1. Needs attention. On widths from 1240 pixels it is the `lead` of the page; below that it is drawn above the index, as
   `add-administration-overview` fixed. It is one line, "Nothing needs attention.", when empty.
2. Instance status: the getting started card at the full width of the block, then the status panel. The meters sit in a grid of
   `repeat(auto-fit, minmax(16rem, 1fr))`, so they fill the row at every width and never leave a half empty last row wider than one
   cell.
3. Resources: two charts, one column below 1024 pixels of container width, two columns from there, three at 2560 pixels, the
   rule of `use-wide-screens`.
4. At a glance: the counts as a grid of cells (`auto-fill`, `minmax(14rem, 1fr)`), a label above its number, instead of a
   two column table inside a 960 pixel line.
5. Danger zone: last block, full width, with the heading, the text and the button on one row from 600 pixels and stacked below.

At 1440 pixels the content column is about 1020 pixels (the window, minus the 96 pixel rail, the 208 pixel side navigation, the 40
pixel gap and the margins), so the meters take three or four columns and the charts take two. At 600 pixels the side navigation is an
index and the page is one column of sections. At 320 pixels each block is one column and nothing scrolls sideways.

The Import and Download actions stay in the header actions of Overview. A visitor without the settings node gets no actions and no
header buttons. The unsaved changes bar is unchanged.

### D5. Anchors: no name is removed, renamed or moved

| Anchor | Today | After |
| --- | --- | --- |
| `dashboard-view` | root of the page | root of the status block inside Overview. Same name, same file, same stability. |
| `instance-status-panel`, `getting-started-card`, `read-only-banner`, `cluster-*` | inside the page | inside the block |
| `settings-hub` | root of At a glance and the link line | root of the settings half of Overview (At a glance). The link line is gone. |

Plugins cannot import components and reach the dashboard through anchors and the `ingot-plugins` layer (see
`fix-surface-hierarchy-and-page-header`, D3), so a plugin stylesheet that matched the old page root now matches the block. That is
a documented difference: the description of `dashboard-view` in `docs/gitbook/reference/dashboard-anchors.md` says it is the
status block of Overview. `src/anchors/registry.json` changes only if an anchor test demands a new `parts` or `states` entry, and
it is not edited otherwise (it is the file #262 edits, see D8). The anchor tests under `src/anchors/` keep guarding the registry.

### D6. The reserved segment and the backend stay

`dashboard` stays in `dashboard-segments.json`, `DashboardPaths.kt` and the chart file. Removing it would turn a repository named
`dashboard` into a plain repository again and would change what a full page load of `/dashboard` returns, which is a server and
chart change for a bookmark. The cost of keeping it is one string. No Kotlin and no chart file is touched, so no backend test runs
for this change.

### D7. Requirement text and the archive order

`add-administration-overview` is not archived. It owns the requirement "Settings hub at /settings", which says "and, when the
visitor may open the dashboard, a link to it". This change modifies that requirement in `settings-navigation`. Whichever of the two
archives second keeps the other's wording and removes the link sentence. The new behaviour is in a capability of its own
(`administration-overview`) so that it does not depend on the order.

### D8. Conflicts with open pull requests, and the order

| Pull request | Files shared with this change | Effect | Order |
| --- | --- | --- | --- |
| #250 `rework-admin-header-and-repository-tabs` (proposal) | Will change `AdministrationFrame.vue`, `SettingsView.vue` ("Loading configuration" and the page frame) and the layout e2e | Its group 1 reserves header rows, its group 2 moves the header beside the side navigation. Both change where Overview starts. | Pull request 3 starts after #250 group 1 is merged, and is rebased onto group 2 if that lands first. Pull requests 1 and 2 touch the Danger zone and the scroll rule, not the frame, and go first. |
| #219 `fix(ui): fit the administration label in the bottom navigation` | `registry.js`, `IndexPage.vue`, `MdNavigationBar.vue`, `layout.spec.js` | Edits the label of Administration in the bar and the registry. Pull request 4 removes a registry entry next to it and counts bar entries. | Pull request 4 starts after #219 is merged. |
| #262 `refactor(ui): add MdIcon and its glyph registry` | `src/anchors/registry.json`, `docs/gitbook/reference/dashboard-anchors.md` | Adds anchors in the same two files. | Pull request 5 and any registry edit start after #262 is merged. D5 expects no registry edit. |
| #214 `fix(ui): keep the accessible name of a menu button with a custom trigger` | `e2e/navigation-chrome.spec.js`, `e2e/support/session.js` | Edits a spec this change edits (the `/dashboard` loop and heading test). | Pull request 4 starts after #214 is merged. |
| #241 `fix(ui): ask for the real username before deleting a user` | `e2e/support/api.js` | Adds helpers only. | None. |
| #240 `fix(maven): keep directory listings out of shared caches` | `docs/gitbook/reference/web-routes.md` | Same page as the route table notes of pull request 5. | Pull request 5 rebases. |
| `migrate-dashboard-to-typescript` (merged proposal, slices not started) | Slices `P1-3` (navigation, router), `C03` (pages), `C06` (dashboard), `C10` (settings), `S03` (e2e) rename these files | A rename under an open edit conflicts. | Pull requests 3 and 4 merge before `P1-3`, `C03`, `C06` and `C10` start. The slice plan already says not to start a slice whose files an open pull request changes. |

Before each pull request starts, run `git diff --name-only origin/main...<pr>` for every pull request in the table, as the other
plans do.

### D9. The two bug fixes

**Factory reset label wraps.** The Danger zone is a flex row with `md:flex-row md:items-center md:justify-between`, and the button
sits in the modal's `button` slot. `class="shrink-0"` is on `MdButton`, but the label wraps in the screenshot, so something
between the row and the label still lets the button shrink or the label wrap: the modal wrapper around the slot, or the missing
`whitespace-nowrap` on the label (`MdButton` has none). The group first reproduces it in a test (a browser test that asserts the
label's box is one line high at 320, 600 and 1440 pixels, and fails today at the width of the screenshot), then applies question 6.
Nothing else moves: the text and the colour of the Danger zone stay.

**Title clipped under the top bar.** The cause is not known from the screenshot, and the group starts by finding it. Candidates, in
the order to check: the focus move to `#main` after a navigation (`create.js`, `afterEach`) scrolls the heading under a sticky top
bar that is 64 pixels high and has no `scroll-margin` or `scroll-padding`; an anchor jump or a browser scroll restoration does the
same; the sticky corner pieces of the content sheet cover the first line. The group records which one it is in the pull request,
adds a failing browser test that scrolls, navigates between two Administration pages and asserts the top of the `h1` is at or below
the bottom of the top bar, and fixes it with the smallest rule (`scroll-padding-top` equal to the top bar token on the scrolling
root, and `scroll-margin-top` on `#main` if the focus is the cause). It does not make the header sticky (question 7), and a heading
that scrolls under the bar while the operator scrolls is unchanged.

Both groups touch no file in `openspec/`, no backend file and no router rule.

### D10. Data, requests and cost

Overview now mounts the status panels, so a visitor with the dashboard node who opens Administration starts the same polling the
Dashboard started, and stops it on leaving. The set of requests Overview makes is the union of what the Dashboard and Overview made
before, and each visitor makes only the part their nodes allow. No new endpoint, no new interval and no new cache. The getting
started card already fetches the settings only when none are loaded (`configuration.domains.length === 0`), so Overview does not
fetch them twice.

## Testing and review evidence

Tests follow F.I.R.S.T.: the router and registry tests run on memory history with fake nodes, the Vue tests mount with fake
stores, and no test waits on a clock. The browser tests use the existing e2e fixtures (`adminPage`, `seed`) and the fake API.

- Unit: the registry has no `dashboard` rail page and `routeTree.test.js` still covers every route; the `dashboard` route redirects with
  query and hash kept; `legacy.js` still maps the old tab; the `settings` route and the `overview` tree entry open for either node and
  for neither; a dashboard-only visitor at `/settings/<domain>` gets the no access state and no settings request; the status block
  mounts only with the dashboard node; the hub has no link line.
- Browser: Overview shows the status for an administrator and the redirect lands on it from `/dashboard`; the two former wide
  screen dashboard tests run on Overview and keep their column assertions; the label and heading fixes.
- Screenshots of Overview at 320, 600 and 1440 pixels, before and after, are pushed to the `pr-screenshots` branch and linked in
  each pull request that changes the page (1, 2 and 3), together with a dashboard-only visitor at 1440 pixels for pull request 3.

# Design

## Context

`add-repositories-admin-page` made `ADMIN_TREE` in `reposilite-frontend/src/navigation/registry.js` the one list of
Administration pages. The tree is not yet one tree in the views. Three callers build it with different inputs:

| Caller | Call | Settings domains passed |
| --- | --- | --- |
| `components/access/AccessView.vue:58` | `adminGroups(can)` | none |
| `pages/IndexPage.vue:274` (Audit, Console, Maintenance, Analytics, Migration through `SideNavLayout` at `IndexPage.vue:420-426`) | `adminGroups(can, { domains: [] })` | none |
| `components/settings/SettingsView.vue:125-128` | `adminGroups(can, { domains: tabDomains, titleOf })` | the domains of the store |

The domains come from the configuration store, which is filled only when a settings page opens
(`SettingsView.vue:417-420`, `store.fetchConfiguration()`). The other pages have no list of domains, so they render a
shorter tree. That is bug 1.

## Decisions

### D1. The settings domains are a shared input, loaded only with the permission

A composable, `components/settings/useSettingsDomains.js` (new), returns the domain names the visitor may open. It calls
`client.settings.domains()` (the call already used by the store at `store/configuration.js:142`) once per session, and
only when `can(ADMIN_NODES.settings)` is true. A visitor without that node gets an empty list and the call is never made,
so no request that would answer 403 leaves the page. The Access, Audit, Console, Maintenance, Analytics and Migration
views pass its result to `adminGroups`; `SettingsView` keeps its own store and passes the same names. Both sources must
give the same tree for the same visitor (the test in task 1.1).

Open question 2 asks whether the domain list endpoint answers 403 for a visitor without the settings node. The guard in
D1 avoids the request either way.

### D2. Keys are unique across the whole tree

The static entry `audit` (`registry.js:177`, route `audit`, label "Audit log") and the settings domain `audit` (System, label
"Audit settings") share the key `audit`. `SideNavLayout.vue:49-51` takes the first link whose key equals `current`, and
both entries match. The breadcrumb reads the first one, which is why it says "Audit log" on `/settings/audit`.

Other collisions, checked against the code and the server domain ids the registry knows (`maven`, `javadoc`, `cleanup`,
`web`, `frontend`, `statistics`, `audit`, `search`, `login`, `authentication`):

| Static key | Settings domain id that equals it | Result |
| --- | --- | --- |
| `audit` | `audit` | Collision, two current entries on `/settings/audit` (bug 2). |
| `repositories` | none, reserved by `RESERVED_SETTINGS_PAGES` (`registry.js:154`) | Not a collision for core domains. A plugin domain named `repositories` is shadowed by the route, documented in `add-repositories-admin-page`. |
| `users`, `machines`, `teams`, `sso`, `ldap`, `security`, `check`, `migration`, `console`, `maintenance`, `analytics` | none of the core ids | Collide only with a plugin domain of the same id. |

The plugin case cannot be settled in the browser alone: the server decides which domains exist. The fix is to give every
settings domain entry a key of its own, `settings:<domain>`, and every static entry a key of its own, `page:<key>`. Then
no id from one source can equal an id from the other, and the rule holds for plugin domains as well. The `key` value is
internal: it is never a route name, a URL or a label, so no link changes. The test in task 1.2 builds the tree with a plugin
domain called `audit`, `users` and `security` and checks that the keys are unique and that exactly one entry is current.

### D3. Exactly one current entry, and the breadcrumb names it

`SideNavLayout` marks `aria-current` on every link whose key equals `current`. The caller passes the key of the page on
screen, and with D2 that key matches one entry. The breadcrumb's last crumb is the label of that entry, found by the same
key. A page that matches no entry shows no current mark and no last crumb; that is the case for `/settings` with no domain
and for the hub.

On `/settings/audit` the current key is `settings:audit`. The Security entry `page:audit` is not marked, and the breadcrumb
reads "Audit settings".

### D4. One Administration frame

The frame is the part every Administration page draws: the rail, the page heading, the container and the side navigation.
Today it is drawn three ways:

- Settings (`SettingsView.vue:431-441`) draws its own `header` with an `h1` "Settings" and the description "Shared
  configuration for all instances...", inside `container mx-auto pt-7`.
- Access (`AccessView.vue:67-75`) draws an `h2` "Access" and the description "Who can sign in...", inside
  `container mx-auto pb-12 pt-7`.
- Console, Maintenance, Analytics and Migration are drawn in `IndexPage.vue:420-447` without any heading. Analytics has an
  `h2` of its own inside its content (`AnalyticsView.vue:28`), Console and Maintenance have none.

The rail is drawn by `IndexPage.vue:525-531` on every page where `hasNavigation` is true (`IndexPage.vue:166`, which is
the same for every page of the section). The code therefore draws the rail on Settings and Access too. The reported
staging state (no rail on `/settings/*` and `/access/*`) is not reproduced by reading the code and is an open question
(question 4). The requirement below holds either way.

This change fixes only the consistency of the frame (one heading, one rail, one tree position). Its widths, its sticky
behaviour and the table layout are designed in `rework-administration-layout`, which changes this frame after it is merged.

The frame is one component, `components/layout/AdministrationFrame.vue` (new). It takes the tree, the current key and the
page description, and draws the `h1` "Administration" once, then the description of the page, then the side navigation
and the page. The default heading is "Administration" (open question 1). Each view keeps its content and its description;
the views stop drawing their own headings. The settings save bar and the Settings header buttons (Import, Download) stay in
`SettingsView` and render in the page content.

### D5. The rail label fits its rail

`MdNavigationRail.vue:131` draws each label in a `span.md-label-medium` with no width limit, inside an 80 pixel rail
(`MdNavigationRail.vue:91`). "Administration" is one word without a break opportunity, so it is wider than the rail. Its
flex item centres it, so the text spills to both sides of the rail: the left part is cut off at the viewport edge, which is
the clipped "dministration" on `/console`. The stray letter on the other pages is the same overflow reaching the viewport
edge; this is the reading the staging evidence points to, and it is not yet confirmed in a browser (open question 4).

Fix: the label span gets `max-w-full break-words` (or `overflow-wrap: anywhere`) and `text-center`, so it wraps inside the
rail. A label that still cannot fit is shortened in the rail only ("Admin"), with the full name as its accessible name
(`aria-label`). The default is wrapping, not shortening (open question 3). The rail is hidden below 600 pixels by its own
class (`IndexPage.vue:525`, `hidden ... min-[600px]:block`); the test checks that the hidden rail draws no text.

### D6. Maintenance page

`StorageUsagePanel.vue:151` draws the totals block whenever `storage` is set, and the block is not guarded by `computing`
(line 145 draws the progress above it). While the server computes, the response carries zero totals and no rows, and the
page shows "Total 0 B across 0 repositories" and "No repository has been measured yet" next to the progress. The fix
guards the totals block with `!computing` and keeps the progress as the only content until the figures arrive. The
refresh control keeps its place.

`MaintenanceView.vue:30` says "Only managers can see this page". The rest of the dashboard speaks of the permission the page
needs (the sign-in text in `IndexPage.vue:343-348` names "the right permission nodes"). The new subtitle is "Storage usage,
the search index and cleanup for this Ingot instance." The sentence about the visitor is dropped, because the frame and the
sign-in text already say who may open the page.

### D7. Permissions

Nothing in this change adds a permission node or a route. The rules that hold:

- A visitor without `ingot.admin.settings` sees no settings domain link, as today (`registry.js:278`, `can(ADMIN_NODES.settings)`).
- That visitor causes no call to the settings domain endpoint (D1), so no request answers 403.
- A visitor with the audit node only sees the audit log, and the frame and tree are the same as today for that visitor.

## Test plan

Each test is written first and seen to fail. Vitest, F.I.R.S.T.: each test builds its own `can`, store and router, no
timers, no network (the client is a fake), no clock.

- Tree identity (bug 1): for one visitor (full administrator, and one with the settings node but not the others), render
  each Administration page and read its side navigation. The trees are equal, in the same groups and order.
- Keys and current (bug 2): the tree has unique keys with a plugin domain named `audit`, `users` and `security`; on
  `/settings/audit` exactly one link has `aria-current="page"`, and it is the Audit settings link; the breadcrumb's last
  crumb reads "Audit settings".
- Frame (bug 3): every Administration page renders one `h1` with the text "Administration" and no other heading of that
  text; the page description is the one of its page.
- Rail (bug 4): the rail label of Administration is rendered with the wrapping class, carries the accessible name
  "Administration", and the hidden rail has the hidden class below 600 pixels.
- Permissions: a visitor without the settings node causes no request to the settings domain endpoint on any Administration
  page (the fake client counts calls).
- Maintenance (item 8): while the storage response says `computing`, no total and no "No repository" text is drawn; the
  subtitle does not contain "manager".

What tests cannot prove: how the rail label looks at 80 pixels and where the stray letter came from. A browser check
(Playwright or the manual checklist in the tasks) records the result in the pull request.

## Risks

- Passing settings domains to every Administration page costs one request per session for an administrator. The list is
  small and cached in the composable.
- Changing the `key` of every entry touches tests that name the old keys (`administration.test.js`, `registry.test.js`).
  Each changed expectation is listed in the pull request.

## Open questions

1. Heading default: this change uses "Administration" as the one h1 on every Administration page, with the page's own
   description under it. The alternative is the page label ("Settings", "Access", "Audit log"), which keeps the old headings
   and needs no page-specific copy in the frame. Default: "Administration".
2. The domain list endpoint: whether `GET /api/settings/domains` answers 403 for a visitor without `ingot.admin.settings`.
   D1 avoids the call for such a visitor in both cases. To confirm in the backend route before the code is written.
3. Rail label: wrap or shorten. Default: wrap inside the rail, keep "Administration" visible in full. Shortening to "Admin"
   with the full accessible name is the fallback if wrapping makes the rail look broken at 80 pixels.
4. Staging evidence: the coordinator's observation that `/settings/*` and `/access/*` show no rail, and that the stray letter
   sits at the rail's height, is not reproduced in code (the rail is drawn on every page of the section,
   `IndexPage.vue:525`). The change requires the same frame on every page, so it holds either way. The browser check in the
   tasks settles the cause of the letter.
5. The Migration page joins the tree and the frame. It is listed only while the nexus-import plugin is enabled, as today.

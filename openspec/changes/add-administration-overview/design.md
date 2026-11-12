# Design

## Context

Measured on `main` in `reposilite-frontend/`:

- `components/settings/SettingsView.vue` computes `navGroups` (around line 126) from `adminGroups` for the side tree and
  `hubCards` (around line 303) from `hubGroups` in `settingsHub.js`, which calls `adminGroups` again. `hubShown` (around line
  142) decides when the hub renders. The repeat shows from 1240 pixels; below that `SideNavLayout` shows the index instead of
  the page, so the hub is not drawn.
- `settingsHub.js`: `domainSummary(schema, saved)` uses `collectionsOf` and returns "N <noun>" per collection, or the schema
  description. That is why only Repositories ("7 repositories") and Cleanup ("0 policies") carry a line.
- `SettingsHub.vue` draws the cards, an "Unsaved changes" line per card from `changes`, and the line "Sign-in and security live in
  Access".
- `SettingsView.vue:426-449`: "Import settings" (draft only, after schema validation in `settingsImport.js`) and
  `SettingsDownloadMenu` ("Download as JSON", saved or draft) in the frame's actions slot on every settings page, the Repositories
  page included. `SettingsReviewSheet` and the conflict dialog live in the same view.
- `src/navigation/registry.js`: `currentTreeKey` returns `undefined` on the hub, so no entry is current there.
  `SETTINGS_GROUPS` and `settingsGroupOf` (around lines 347-360) are a second grouping, read only by the hub.
- `src/router/create.js` forwards `/settings` to the one permitted page when only one is permitted.
- `src/anchors/registry.json`: `settings-hub` is `stable`, with no parts and no states.
- `e2e/accessibility.spec.js:41` uses the text "Import settings" as the settle marker of `/settings/repositories`.
- `/dashboard` already shows Instance status and Resources (`docs/gitbook/reference/dashboard-pages.md`).

## Goals

- The tree is the only list of Administration pages, on every width.
- `/settings` is a page of its own with a current entry, and shows only what the tree cannot.
- An operator can type the word in their head and land on the right page.
- No new request, endpoint, route or permission.

## Non-goals

- A status dashboard on `/settings`.
- A command palette, global shortcuts, roving focus.
- New pages or merged pages.

## Decisions

### D1. Overview is a tree entry for the existing route

A static entry `{ key: 'overview', route: 'settings', label: 'Overview', permission: { node: ADMIN_NODES.settings } }` sits before
the first group, in a group without a visible heading whose accessible label is "Overview". `currentTreeKey` returns `page:overview`
only for route `settings` without a `domain` parameter; `/settings/<domain>` keeps `settings:<domain>`. `unlistedRoutes` needs no
change because `settings` is already a listed route. The single-page forward in `create.js` is untouched, so a visitor with only
one permitted page still lands on it.

### D2. Overview content

```
Needs attention          rows from `changes` and the conflict state the view already holds
At a glance              counts from `collectionsOf(schema)` over the saved settings the view already loaded
Dashboard link           only with the dashboard node
```

- A row of Needs attention is a link named "<label>, Unsaved changes" to the domain page. The status is text with an icon, never
  colour alone, and the status word is not a separate link.
- "Review changes" is a text button that opens `SettingsReviewSheet`. The filled Save stays in the save bar, so the page header has
  no filled button.
- At a glance is one definition list. It renders once, when the saved settings are loaded; while they load, the section shows the
  existing progress indicator and no number. A zero is printed as "0", without a warning colour.
- Nothing else is fetched. Users, teams or providers are not counted, because that would need reads the page does not do today.

### D3. Settings file actions on Overview only

"Import settings" and "Download as JSON" keep their labels, behaviour and outlined style, and render only when the route is
`settings` without a domain. The research splits on placement (header for page-wide scope, overflow for rare actions); their scope
is the whole configuration, and Overview is the page whose subject is the whole configuration. The e2e settle marker for
`/settings/repositories` moves to the Repositories table's accessible name; `/settings` gets "Import settings" as its marker.

### D4. Filter: pure matching, rendered in the tree

`src/navigation/adminFilter.js` exports `matchEntries(groups, query)`:

- tokens: the query split on spaces, lower case; an entry matches when every token is a prefix of a word in its label, group
  heading, description, keywords, or, for plugin pages, title, description and plugin id;
- groups with no match are dropped; the current entry is always kept;
- the result is ordered as the tree.

It has no state and no DOM, so the unit tests are plain input and output. `SideNavLayout` renders a labelled search field ("Find an
administration page", `type="search"`, inside a `search` landmark within the `nav`) above Overview, and a polite live region with
"1 page matches", "N pages match" or "No administration page matches "<text>"". Escape clears and keeps focus in the field. Enter
opens the first match. Results stay plain links in reading order; no arrow key handling and no combobox role.

Example matches with the D3 keywords of `rework-administration-information-architecture`: "nginx" finds Reverse proxy; "logo" finds
Branding; "retention" finds Cleanup policies and Audit settings; "rebuild index" finds Search index and Maintenance; "oidc" finds
Single sign-on; "web" finds Reverse proxy by its old label.

### D5. Unsaved mark

`SideNavLayout` receives a set of entry keys with drafts (from `changes`; the Repositories entry maps to the `maven` domain through
`domainOfCard`). A marked entry draws "Unsaved" with an icon after the label, and its accessible name is "<label>, unsaved
changes". No other per-entry status exists.

### D6. One grouping source

`SETTINGS_GROUPS`, `SETTINGS_FALLBACK_GROUP` and `settingsGroupOf` are removed once the hub no longer reads them.
`SECTION_SETTINGS_GROUPS` goes with them; a plugin section's tree group is its manifest group (`Repository` or `System`), as
`treeGroupOf` already does. A test with a fake manifest proves every section still appears.

### D7. Anchor

The root element of the Overview content keeps `data-ingot-component="settings-hub"`. The registry entry is unchanged (no parts,
no states). The PR description and `dashboard-pages.md` say the card children are gone.

## Wireframes

Desktop, 1440 pixels, `/settings`:

```
+----+--------------------------------------------------------------------------------+
|    | Ingot Repository                                                  (T) account  |
| B  +--------------------------------------------------------------------------------+
| S  |  Administration                                                                |
| D  |  Overview                               [ Import settings ] [ Download as JSON v]|
|(A) |  Shared configuration for all instances.                                       |
|    |  +------------------------+  Needs attention                                   |
|    |  | Find an administration |  ----------------------------------------------    |
|    |  | page [               ] |  (!) Javadoc            Unsaved changes        >    |
|    |  |> Overview      current |  (!) Sign-in protection  Unsaved changes        >    |
|    |  | ARTIFACTS              |  [ Review changes ]                                |
|    |  |  Repositories          |                                                    |
|    |  |  Cleanup policies      |  At a glance                                       |
|    |  |  Javadoc     * Unsaved |  ----------------------------------------------    |
|    |  |  Search index          |  Repositories          7                           |
|    |  | ACCESS                 |  Cleanup policies      0                           |
|    |  |  Users                 |                                                    |
|    |  |  ...                   |  Instance status and resources are on the          |
|    |  |  Sign-in prot. * Unsaved|  Dashboard >                                      |
|    |  |  ...                   |                                                    |
|    |  +------------------------+                                                    |
|    +--------------------------------------------------------------------------------+
|    | 2 unsaved changes in 2 sections. Other instances pick up      [Discard] [Save] |
|    | saved changes within 10 seconds.                                               |
+----+--------------------------------------------------------------------------------+
```

Desktop, filter in use ("nginx"):

```
|  | Find an administration |
|  | page [ nginx         x]|
|  | 1 page matches         |   (live region, polite)
|  | INSTANCE               |
|  |  Reverse proxy         |
|  |   The headers that ... |   (description shown while filtering)
|  |> Overview      current |   (current entry kept)
```

Phone, 320 pixels, `/settings` (the index is the landing below 1240 pixels):

```
+------------------------------+
| Ingot Repository        (T)  |
+------------------------------+
| Administration               |
| Overview                     |
| Shared configuration for all |
| instances.                   |
| [ Import settings ]          |
| [ Download as JSON v ]       |
|                              |
| Needs attention              |  only when not empty
| (!) Javadoc, Unsaved     >   |
|                              |
| Find an administration page  |
| [                          ] |
| ARTIFACTS                    |
|  Repositories            >   |
|  Cleanup policies        >   |
|  ...                         |
+------------------------------+
| 1 unsaved change    [Save]   |
+------------------------------+
| Browse Search Dash. Admin    |
+------------------------------+
```

## Evidence

| Finding | Sources |
| --- | --- |
| A landing page must not repeat the navigation beside it | NN/g, Duplicate links, https://www.nngroup.com/articles/duplicate-links/ ; Shopify app navigation, https://shopify.dev/docs/apps/design/navigation ; Primer NavList (sidebar stays, detail changes), https://primer.style/components/nav-list ; PatternFly navigation, https://www.patternfly.org/components/navigation/design-guidelines ; Apple HIG split views, https://developer.apple.com/design/human-interface-guidelines/split-views ; GOV.UK service navigation ("not a site map"), https://design-system.service.gov.uk/components/service-navigation/ ; Gitea admin navigation, https://github.com/go-gitea/gitea/blob/main/templates/admin/navbar.tmpl |
| Every view shows the current location | NN/g, Menu design checklist, https://www.nngroup.com/articles/menu-design/ ; Primer NavList (`aria-current`) |
| Status only where it changes the next step; all or nothing; never colour alone | NN/g, Visibility of system status, https://www.nngroup.com/articles/visibility-system-status/ ; GOV.UK tag, https://design-system.service.gov.uk/components/tag/ ; Android settings guidelines ("show the value"), https://source.android.com/docs/core/settings/settings-guidelines ; Primer UnderlineNav counters (render together), https://primer.style/components/underline-nav |
| Page-wide actions in the page header, no filled button for rare actions | Polaris Page, https://polaris.shopify.com/components/layout-and-structure/page ; Primer PageHeader, https://primer.style/components/page-header ; Atlassian page header, https://atlassian.design/components/page-header/ |
| No confirmation for a reversible action | NN/g, Confirmation dialogs, https://www.nngroup.com/articles/confirmation-dialog/ |
| Scoped, labelled search; filter as you type | NN/g, Search visible and simple, https://www.nngroup.com/articles/search-visible-and-simple/ ; GOV.UK service navigation ("Search [service]"); Microsoft NavigationView AutoSuggestBox, https://learn.microsoft.com/en-us/windows/apps/design/controls/navigationview ; VS Code settings, https://code.visualstudio.com/api/ux-guidelines/settings |
| Descriptions give scent | NN/g, Vertical navigation, https://www.nngroup.com/articles/vertical-nav/ ; GitLab settings click test, issue 23483 (URL not recorded in the research corpus) |
| Landmarks and live regions | WAI-ARIA APG landmark regions, https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/ ; WCAG 2.2 4.1.3 status messages, https://www.w3.org/TR/WCAG22/ |

Where the research disagreed: GitLab, Gitea and Atlassian open their admin area on an operational dashboard; Apple and PatternFly
open the first or last section. Ingot has its dashboard at `/dashboard`, and only `/settings` hosts the cross-domain draft, Import
and Download, so a redirect would hide them and a second dashboard would repeat `/dashboard`.

Discarded: command palette evidence (one product only), settings search size thresholds from secondary blogs, "restore the last
pane" through browser storage (it changes what a bookmark opens per person).

## Risks

- Operators used to Import on a domain page look for it there. Mitigation: release notes, `dashboard-pages.md`, and the Overview
  entry is one click away in the tree.
- A plugin stylesheet that styled hub cards loses them. The anchor had no parts, so card internals were never a contract.
- Filtering hides entries; the current one is always kept, and the live region says how many match.

## Migration plan

None for operators. A rollback is a revert of the code PR.

## Open questions

1. Common tasks on Overview: default not shipped; alternative up to six permission-gated task links.
2. Import and Download: default Overview only; alternative also on every settings domain page.
3. At a glance: default collection counts; alternative leave it out.
4. Enter in the filter: default opens the first match; alternative does nothing.

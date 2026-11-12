# Design

## Context

Measured on `main` in `reposilite-frontend/`:

- `src/navigation/registry.js:165-203`, `ADMIN_TREE`: four groups with the headings Repository, Security, System and Support.
  Security holds Users, Machine accounts, Teams, SSO, LDAP, Security settings, Permission check and Audit log.
- `registry.js:152`, `REPOSITORY_DOMAINS = ['javadoc', 'cleanup']`; `treeGroupOf` (around line 344) returns `Repository` for
  those and `System` for every other domain, so `search` and `audit` land in System.
- `registry.js:88-105`: `CONTRIBUTED_GROUPS = ['Repository', 'Security', 'System', 'Support']`, `PLUGINS_HEADING = 'Plugins'`.
  `contributedHeading` uses the group value as the heading. `SECTION_SETTINGS_GROUPS` maps plugin sections.
- Settings domain labels come from the schema `@Doc` title through `titleOf`. `settingsLabelOf` only reads plugin section labels.
- `reposilite-backend/.../frontend/api/PluginUiModel.kt:109-112`: `GROUPS` (the four values) and `SECTION_GROUPS`
  (`Repository`, `System`), documented for plugin authors in `docs/gitbook/reference/plugin-api.md:331`.
- The fields behind the vague labels, read from the backend settings classes:
  - `WebSettings.kt`: `forwardedIp` (default `X-Forwarded-For`) and `forwardedPrefix`. Title "Web", description "General web settings".
  - `FrontendSettings.kt`: `id`, `title`, `description`, `organizationWebsite`, `organizationLogo`, `privacyPolicy`,
    `icpLicense`, `appearance`. Title "Frontend", description "Frontend settings".
  - `StatisticsSettings.kt`: `enabled`, `resolvedRequestsInterval`.
  - `JavadocSettings.kt`: `enabled`, `suffixes`.
  - `SearchSettings.kt`: verify schedule and sample size, rebuild schedule, gap rebuild. "Artifact search keeps an index of the
    stored files and checks it against storage."
  - `CleanupSettings.kt`: `policies`. "Cleanup policies delete old versions from repositories."
  - `AuditSettings.kt`: `retentionDays`, `recordActorAddress`. Title "Audit", description "Audit log of administrative and
    artifact events."
  - `components/access/SecurityPanel.vue`: brute force protection only.
- `components/layout/AdministrationFrame.vue:57-62`: `h1` "Administration" on every page. `pages/IndexPage.vue:103`:
  `ADMIN_DESCRIPTIONS` for seven routes. `components/settings/SettingsView.vue:416`: the 10 second notice as the description of
  every settings page.
- `components/layout/SideNavLayout.vue:82-86`: each group is `role="group"` with `aria-label`, and its heading is a `p`.
- `components/access/check/CheckPermissionView.vue:83`: the check reads `principal`, `node`, `token`, `repository`, `path` and
  `action` from the query.
- Tests that pin today's structure: `src/navigation/administration.test.js`, `registry.test.js`, `contributions.test.js`,
  `components/layout/AdministrationFrame.test.js`, `pages/IndexPage.test.js`; e2e `navigation.spec.js`,
  `navigation-chrome.spec.js:93` (`heading 'Administration', level 1`).

## Goals

- Each group holds one kind of job, and each page has exactly one home.
- Every label says what the operator wants to do there, and no word means two things.
- Every page names itself.
- Nothing an operator, a bookmark or a plugin relies on changes.

## Non-goals

- New pages, merged pages, new routes.
- The landing page, the filter and the unsaved marks (`add-administration-overview`).
- Scroll and narrow-width behaviour of the tree (`rework-administration-side-navigation`).

## Decisions

### D1. Display headings keyed by the unchanged group ids

```js
const GROUP_HEADINGS = {
  Repository: 'Artifacts',
  Security: 'Access',
  System: 'Instance',
  Support: 'Monitoring and support',
}
```

`ADMIN_TREE` keeps `Repository`, `Security`, `System` and `Support` as group ids. `adminGroups` returns `{ id, heading, links }`.
`contributedHeading` maps a valid manifest group through `GROUP_HEADINGS` and falls back to `Plugins` as today. The backend
constants and the validator are not touched. If a later change ever accepts new manifest values, they are added and the old
strings stay valid (the `INGOT_` and `REPOSILITE_` pattern of `CLAUDE.md`).

Why "Artifacts" and not "Repositories": the first entry of the group is the Repositories page, and a heading that repeats its
first row cannot be told apart from it, visually or by a screen reader (judges' finding). Open question 1.

### D2. Built-in domains move by a domain map; plugin sections keep their own group

```js
const DOMAIN_GROUPS = { javadoc: 'Repository', cleanup: 'Repository', search: 'Repository', audit: 'Support' }
```

It replaces `REPOSITORY_DOMAINS`. The default stays `System`. A plugin section keeps the group its manifest names
(`Repository` or `System`, per `plugin-api.md:362`); `DOMAIN_GROUPS` only names built-in domain ids, and a plugin section whose
domain id collides with one of them keeps its manifest group.

### D3. Labels, descriptions and keywords per entry

The lookup order for a label is: plugin section label, then `DOMAIN_LABELS` or the entry's own label, then the schema title.

| Group | Entry (route or domain) | Label | Description | Filter keywords (used by `add-administration-overview`) |
| --- | --- | --- | --- | --- |
| Artifacts | `settings-repositories` | Repositories | Hosted, proxied and group repositories, their policies and Maven options. | maven, hosted, proxy, mirror, group |
| Artifacts | `cleanup` | Cleanup policies | Rules that delete old versions from the repositories they list. | cleanup, retention, delete, snapshots |
| Artifacts | `javadoc` | Javadoc | Whether Javadoc archives are served, and which file suffixes count as Javadoc. | docs, groovydoc |
| Artifacts | `search` | Search index | The index behind artifact search: its nightly check against storage and scheduled rebuilds. | search, index, rebuild, reindex, verify |
| Access | `access-users` | Users | People who sign in, with their permissions and tokens. | people, account, password, token |
| Access | `access-machines` | Machine accounts | Accounts for CI and other automated clients, with their tokens. | ci, bot, robot, deploy, token |
| Access | `access-teams` | Teams | Groups of users and machine accounts that share permissions. | group, members |
| Access | `access-sso` | Single sign-on | External sign-in providers such as OpenID Connect. | sso, oidc, oauth, openid, login |
| Access | `access-ldap` | LDAP directory | Sign-in with accounts from an LDAP directory. | ldap, active directory |
| Access | `access-security` | Sign-in protection | Limits on repeated failed sign-ins from one IP address. | security settings, brute force, lockout |
| Access | `access-check` | Permission check | Find out why someone can or cannot reach a path. | why, denied, 403, access |
| Instance | `frontend` | Branding | Name, description, logo, colours and links shown on this instance. | frontend, logo, colour, color, theme, title, privacy |
| Instance | `web` | Reverse proxy | The headers that carry the client address and the path prefix behind a proxy. | web, nginx, traefik, x-forwarded, header |
| Instance | `statistics` | Usage statistics | Whether resolved requests are counted, and per which period. | statistics, stats, downloads |
| Instance | `settings-migration` | Migration | Import the repositories of another instance, step by step. | nexus, import |
| Monitoring and support | `audit` (page) | Audit log | Deployments, deletions, settings changes, token changes and failed sign ins on this instance, newest first. | audit, events, history, who |
| Monitoring and support | `audit` (domain) | Audit settings | How long audit events are kept, and whether the client address is recorded. | audit, retention, client address |
| Monitoring and support | `analytics` | Analytics | Traffic and resource use of this instance over time. | traffic, charts |
| Monitoring and support | `console` | Console | The log lines of this instance, filtered by level and text. | logs |
| Monitoring and support | `maintenance` | Maintenance | Storage usage, the search index and cleanup for this Ingot instance. | storage, rebuild index, cleanup run |
| Monitoring and support | `system-info` | System information | What this instance runs on. Secrets are withheld or masked, and the page only reads. | version, java, environment |
| Monitoring and support | `support` | Support bundle | What a maintainer needs to look at this instance, as one zip. | zip, debug |

Every description is written from the fields listed in Context; none promises a field that does not exist. The old label of each
renamed entry is one of its keywords, so a returning operator who types "web" or "frontend" still finds the page. The
descriptions and keywords live in the registry, next to the entries; `ADMIN_DESCRIPTIONS` moves there from `IndexPage.vue`.
A settings domain the map does not name (a plugin domain) uses its schema `title` and `description`; an empty description
leaves no slot.

### D4. Page header and breadcrumb

`AdministrationFrame` draws, in this order, outside the two columns (the header row of `rework-administration-layout`):

1. a breadcrumb `nav` named "Breadcrumb": "Administration" (link to `/settings`), then the group heading as plain text, then, on a
   detail page only, the list page as a link;
2. the `h1`: the entry label, the manifest `title` on a plugin page, the item name on a detail page;
3. the description line (`md-body-large`, `on-surface-variant`), omitted when empty;
4. on a plugin page only, "Provided by the <plugin> plugin" (`md-body-medium`);
5. page actions on the right; on a page that can show a save bar, no filled button in the header.

`main` is labelled by the `h1` (`aria-labelledby`). The focus rule of `fix-dashboard-a11y-and-caching` (focus moves to `main`
after an in-app navigation) is unchanged, so the first thing a screen reader announces is the page's name. Below 1240 pixels the
existing back arrow stays, and the breadcrumb shows only its last link on one line with an ellipsis; its full text is the `title`
and accessible name. The back arrow keeps a 48 pixel target.

The `/settings` landing page keeps `h1` "Administration" and the description "Shared configuration for all instances." in this
change; `add-administration-overview` names it Overview.

### D5. Audit log and Audit settings are two adjacent entries

Both live under Monitoring and support, Audit log first. Each keeps its own permission node (`audit` for the log, `settings` for
the domain), so no visitor loses an entry they can open today, and no hidden entry or special current key is needed. The existing
requirement "Exactly one current entry" already marks only Audit settings on `/settings/audit`. The two pages link to each other
(D6). Merging them stays possible later (open question 3).

### D6. Shortcuts are in-page links

| On page | Link | Target |
| --- | --- | --- |
| Audit log | Audit settings | `/settings/audit`, shown with the settings node |
| Audit settings | Open the audit log | `/audit`, shown with the audit node |
| Analytics | Usage statistics | `/settings/statistics` |
| Maintenance | Search index, Cleanup policies | `/settings/search`, `/settings/cleanup` |
| Search index | Rebuild now on Maintenance | `/maintenance` |
| User or machine account detail | Check a permission | `/access/check?principal=<name>` |
| Team detail | Check a permission | `/access/check` |

A link is drawn only when the visitor may open its target. None of them is a tree entry.

### D7. Semantics of a group

Each group is a `ul` with `role="group"` (or the list itself) labelled by `aria-labelledby` pointing at the visible heading text.
The heading text is not a heading element. The page outline is then the `h1` followed by the page's own `h2`s, and a screen
reader still announces the group name on entering its list. This MODIFIES "Accessible tree", which asked for a heading per
group while the code drew a `p`. Open question 4.

### D8. Order inside a group

Configuration first, then tools, then logs and views; an entry whose settings live in a separate domain sits directly after it.
Plugin entries follow the built-in entries of their group, in manifest order, and never reorder the built-ins.

### D9. Validate the grouping before the code

A tree test on a text tree of D3, with at least five operators, before the code PR:

1. "Change how long audit events are kept." (Audit settings)
2. "Find out why the CI account cannot deploy to releases." (Permission check)
3. "Rebuild the search index." (Maintenance, or Search index then the shortcut)
4. "Put Ingot behind nginx." (Reverse proxy)
5. "Change the logo." (Branding)
6. "Add a user." (Users)

Record success and time per task in the code PR. A failing heading or label changes only `GROUP_HEADINGS` or `DOMAIN_LABELS`.

## Route mapping

No address, route name or redirect changes. The tree only regroups and relabels.

| Address | Route name | Before | After |
| --- | --- | --- | --- |
| `/settings/repositories`, `/settings/repositories/:id` | `settings-repositories`, `settings-repository` | Repository / Repositories | Artifacts / Repositories |
| `/settings/cleanup` | `settings` | Repository / Cleanup | Artifacts / Cleanup policies |
| `/settings/javadoc` | `settings` | Repository / Javadoc | Artifacts / Javadoc |
| `/settings/search` | `settings` | System / Search | Artifacts / Search index |
| `/access/users[/:name]`, `/access/machines[/:name]`, `/access/teams[/:name]` | `access-*` | Security / ... | Access / ... |
| `/access/sso` | `access-sso` | Security / SSO | Access / Single sign-on |
| `/access/ldap` | `access-ldap` | Security / LDAP | Access / LDAP directory |
| `/access/security` | `access-security` | Security / Security settings | Access / Sign-in protection |
| `/access/check` | `access-check` | Security / Permission check | Access / Permission check |
| `/audit` | `audit` | Security / Audit log | Monitoring and support / Audit log |
| `/settings/audit` | `settings` | System / Audit | Monitoring and support / Audit settings |
| `/settings/frontend` | `settings` | System / Frontend | Instance / Branding |
| `/settings/web` | `settings` | System / Web | Instance / Reverse proxy |
| `/settings/statistics` | `settings` | System / Statistics | Instance / Usage statistics |
| `/settings/migration/:step?` | `settings-migration` | System / Migration | Instance / Migration |
| `/console`, `/maintenance`, `/analytics`, `/settings/system-info`, `/settings/support` | unchanged | Support / ... | Monitoring and support / ... |
| `/settings/plugins/:plugin/:page?` | `settings-plugin` | manifest group as heading | manifest group through `GROUP_HEADINGS` |

Untouched: `/settings`, `/access`, `/tokens...`, `/settings/authentication`, `/settings/login`, `/settings/maven...`, legacy `#/`
links, `dashboard-segments.json`, `DashboardPaths.kt`, the tree keys `page:<key>` and `settings:<domain>`.

## Wireframes

Desktop, 1440 pixels, `/access/users`:

```
+----+-------------------------------------------------------------------------------+
|    | Ingot Repository                                                 (T) account  |
| B  +-------------------------------------------------------------------------------+
| S  |  Administration / Access                                                      |  breadcrumb, group as text
| D  |  Users                                                       [ New user ]     |  h1 = page, its own action
|(A) |  People who sign in, with their permissions and tokens.                       |  one description line
|    |  +----------------------+  +-------------------------------------------------+ |
|    |  | ARTIFACTS            |  | Search by name     Name     Role    Last seen   | |
|    |  |  Repositories        |  | alice              admin    2 days ago      [:] | |
|    |  |  Cleanup policies    |  | ci-bot             deploy   1 hour ago      [:] | |
|    |  |  Javadoc             |  +-------------------------------------------------+ |
|    |  |  Search index        |                                                     |
|    |  | ACCESS               |                                                     |
|    |  |> Users       current |                                                     |
|    |  |  Machine accounts    |                                                     |
|    |  |  Teams               |                                                     |
|    |  |  Single sign-on      |                                                     |
|    |  |  LDAP directory      |                                                     |
|    |  |  Sign-in protection  |                                                     |
|    |  |  Permission check    |                                                     |
|    |  | INSTANCE             |                                                     |
|    |  |  Branding            |                                                     |
|    |  |  Reverse proxy       |                                                     |
|    |  |  Usage statistics    |                                                     |
|    |  | MONITORING AND SUPP. |                                                     |
|    |  |  Audit log           |                                                     |
|    |  |  Audit settings      |                                                     |
|    |  |  Analytics           |                                                     |
|    |  |  Console             |                                                     |
|    |  |  Maintenance         |                                                     |
|    |  | PLUGINS              |                                                     |
|    |  |  (unknown groups)    |                                                     |
|    |  +----------------------+                                                     |
+----+-------------------------------------------------------------------------------+
```

Desktop, detail page `/access/users/alice`:

```
|  Administration / Access / Users                     [ Check a permission ] [:]  |
|  alice                                                                            |
|  Alice Example, last seen 2 hours ago                                             |
```

Desktop, plugin page in group `Security`:

```
|  Administration / Access                                                          |
|  Mirror credentials                                         (manifest title)      |
|  Rotate the upstream credentials of mirrored repositories.  (manifest description)|
|  Provided by the acme-mirror plugin                                               |
```

Phone, 320 pixels, `/settings/web`:

```
+------------------------------+
| Ingot Repository        (T)  |
+------------------------------+
| <  Instance                  |  back arrow (48 px target), one line
| Reverse proxy                |  h1
| The headers that carry the   |
| client address and the path  |
| prefix behind a proxy.       |
|                              |
| Forwarded IP header          |
| [ X-Forwarded-For          ] |
| Forwarded prefix header      |
| [                          ] |
+------------------------------+
| 1 unsaved change    [Save]   |
| Other instances pick up      |
| saved changes within 10 s.   |
+------------------------------+
| Browse Search Dash. Admin    |
+------------------------------+
```

## Evidence

| Finding | Sources |
| --- | --- |
| One home per destination; no second copy of the navigation | NN/g, Duplicate links, https://www.nngroup.com/articles/duplicate-links/ ; NN/g, Vertical navigation, https://www.nngroup.com/articles/vertical-nav/ ; Shopify app navigation ("avoid replicating the app nav content in the app body"), https://shopify.dev/docs/apps/design/navigation ; PatternFly navigation, https://www.patternfly.org/components/navigation/design-guidelines |
| Two levels at most | Carbon UI shell left panel ("does not support three tiers"), https://carbondesignsystem.com/components/UI-shell-left-panel/usage/ ; GitLab Pajamas navigation sidebar, https://design.gitlab.com/patterns/navigation-sidebar ; NN/g, Progressive disclosure, https://www.nngroup.com/articles/progressive-disclosure/ ; Microsoft navigation basics, https://learn.microsoft.com/en-us/windows/apps/design/basics/navigation-basics ; Apple HIG sidebars, https://developer.apple.com/design/human-interface-guidelines/sidebars |
| Group by job; logs and monitoring apart from identity and configuration | Gitea admin navigation (Identity and access, Monitor), https://github.com/go-gitea/gitea/blob/main/templates/admin/navbar.tmpl ; Forgejo admin navigation, https://codeberg.org/forgejo/forgejo/src/branch/forgejo/templates/admin/navbar.tmpl ; Sonatype Nexus Repository administration menu (group names Repository, Security, System, Support only); GitLab Admin area monitoring group; Atlassian Administration (audit log under Insights); Apple HIG settings, https://developer.apple.com/design/human-interface-guidelines/settings |
| One term per concept; short noun labels; descriptive text | NN/g, Menu design checklist, https://www.nngroup.com/articles/menu-design/ ; Android settings guidelines, https://source.android.com/docs/core/settings/settings-guidelines ; GitLab Pajamas settings management, https://design.gitlab.com/patterns/settings-management ; GitLab settings click test in issue 23483 (correct location rose from 34% to 97% and from 49% to 92% with descriptive labels; URL not recorded in the research corpus) |
| Page title names the page; breadcrumb above it; current crumb not a link | PatternFly page, https://www.patternfly.org/components/page ; Primer PageHeader, https://primer.style/components/page-header ; Atlassian page header, https://atlassian.design/components/page-header/ ; Polaris Page, https://polaris.shopify.com/components/layout-and-structure/page ; Apple HIG ("don't title windows with your app name"); NN/g, Breadcrumbs, https://www.nngroup.com/articles/breadcrumbs/ |
| Contextual shortcuts as in-page links | GitLab Pajamas settings management (in-page shortcuts instead of extra sidebar entries); NN/g, Vertical navigation |
| Extensions placed through a declared contract, with a fallback bucket | VS Code settings guidelines, https://code.visualstudio.com/api/ux-guidelines/settings ; WordPress `add_menu_page` (capability gating), https://developer.wordpress.org/reference/functions/add_menu_page/ ; Grafana plugin navigation ("More apps" default bucket) |
| Validate with a tree test | NN/g, Tree testing, https://www.nngroup.com/articles/tree-testing/ ; NN/g, Card sorting, https://www.nngroup.com/articles/card-sorting-definition/ |
| Landmarks, label in name, reflow | WAI-ARIA APG landmark regions, https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/ ; WCAG 2.2 Understanding 2.5.3, https://www.w3.org/WAI/WCAG22/Understanding/label-in-name ; Understanding 1.4.10, https://www.w3.org/WAI/WCAG22/Understanding/reflow |

Discarded as too weak: Material 3 rail and drawer limits (only page titles were retrievable), the Nexus item lists taken from a
404 page's sidebar, ProGet (the site found was a different product), settings search thresholds from secondary blogs, and the
claim that Audit and Audit log describe the same thing (the code shows a log page and a settings domain).

## Risks

- A plugin author's documentation says "under Security". Mitigation: the mapping table in `plugin-api.md` and the release notes.
- E2E and unit tests assert today's headings and labels. They are rewritten red first in the same PR; each changed expectation is
  listed in the PR.
- The `use-wide-screens` change edits the same frame. Merge order is free; the second rebases.

## Migration plan

None for operators. A rollback is a revert of the code PR.

## Open questions

1. Heading of `Repository`: default "Artifacts"; alternative "Storage" or "Repository settings".
2. Renamed labels: default as in D3; alternative keep the schema titles and rely on descriptions and keywords.
3. Audit: default two adjacent entries; alternative one entry with the settings behind an action on the log page.
4. Group semantics: default list labelled by the visible heading; alternative an `h2` per group.
5. "Monitoring and support": default this heading; alternative "Operations".

# Proposal

## Why

An operator opens Administration perhaps a few times a year: to add a user, to connect LDAP, to put the instance behind a
reverse proxy, to change the logo. Today that operator meets a tree that was grown, not designed.

- **The groups mix kinds of work.** "Security" holds people (Users, Machine accounts, Teams), sign-in providers (SSO, LDAP),
  a policy form (Security settings), a tool (Permission check) and a log (Audit log). Logs and diagnostics sit next to the
  people who sign in.
- **One word, two meanings.** "Search" in the rail finds artifacts. "Search" in the System group configures the index behind
  it. "Audit log" sits under Security, "Audit" under System, and the server describes the settings domain as "Audit log of
  administrative and artifact events", so the two look like the same page twice.
- **Labels name modules, not jobs.** "Web" holds exactly two fields, the headers that carry the client address and the path
  prefix behind a proxy. "Frontend" holds the instance name, the logo, the colours and the privacy link. "Security settings"
  sits under a group that is also called Security and holds only brute force protection. Nobody looks for the logo under
  "Frontend" or for proxy headers under "Web".
- **The page never says where you are.** Every Administration page has the same heading, "Administration". Whether the
  operator is on Users, LDAP or Javadoc, the name of the page appears only in the side tree.

The research behind this change (design systems, competitor admin areas, usability studies) agrees on the remedies: group
by the operator's job, keep logs and monitoring apart from identity and configuration, one term per concept, at most two
levels, and a page title that names the page.

## What Changes

- **Groups by job, with display headings.** The four groups keep their internal ids, which are also the plugin manifest values
  `Repository`, `Security`, `System` and `Support`. Only the heading on screen changes, through a frontend map:
  `Repository` is shown as **Artifacts**, `Security` as **Access**, `System` as **Instance**, `Support` as **Monitoring and
  support**. Pages with an unknown group keep landing under **Plugins**.
- **Each page in one place.**
  - Artifacts: Repositories, Cleanup policies, Javadoc, Search index.
  - Access: Users, Machine accounts, Teams, Single sign-on, LDAP directory, Sign-in protection, Permission check.
  - Instance: Branding, Reverse proxy, Usage statistics, Migration (only while the Nexus import plugin is enabled).
  - Monitoring and support: Audit log, Audit settings, Analytics, Console, Maintenance, System information and Support bundle
    (the last two only when the support bundle is offered).
- **Labels that name the job.** Labels come from a frontend map keyed by route or settings domain id, with the server schema
  title as the fallback. `cleanup` is "Cleanup policies", `search` is "Search index" (and moves to Artifacts), `web` is "Reverse
  proxy", `frontend` is "Branding", `statistics` is "Usage statistics", `audit` is "Audit settings" (and moves next to Audit log),
  SSO is "Single sign-on", LDAP is "LDAP directory", Security settings is "Sign-in protection". The server titles, the settings
  API and the exported JSON do not change.
- **One description per page.** Every built-in entry gets a one-line description written from its real fields. Settings
  domains without a frontend description use their schema description, plugin pages their manifest description. An empty
  description leaves no empty slot.
- **A page header that names the page.** The `h1` of the Administration frame is the page's label (the manifest title on a
  plugin page, the item name on a detail page). Above it a breadcrumb reads "Administration / <group heading>"; the group is
  plain text, Administration links to `/settings`. On a detail page the trail adds the list page as a link. The landmark of the
  tree keeps the name "Administration". A plugin page adds "Provided by the <plugin> plugin" under its description.
- **The propagation notice moves to the save step.** "Changes can take up to 10 seconds to reach every instance" is today the
  description of every settings page. It becomes supporting text of the save bar ("Other instances pick up saved changes
  within 10 seconds.") and part of the success message after a save, so each page's description line is free to describe the
  page. The 10 second figure is unchanged. The `/settings` landing page keeps the heading "Administration" and the scope line
  "Shared configuration for all instances." until `add-administration-overview` turns it into Overview.
- **Shortcuts are links inside a page, never extra tree entries.** Audit log and Audit settings link to each other, Analytics
  links to Usage statistics, Maintenance links to Search index and Cleanup policies and back, and a user or team detail page
  links to Permission check with the principal filled in through the existing `principal` query.
- **Order inside a group.** Configuration first, then tools, then logs and views. Entries that belong together sit next to each
  other. Plugin entries follow the built-in entries of their group, as today.
- **Design rules written down.** `DESIGN.md` gains an "Administration information architecture" section: one home per page,
  two levels, the label rule, the description rule, the page header anatomy, and the rule that a page with a save bar has no
  filled button in its header.

Not BREAKING. No configuration key, environment variable, deployment manifest, route, route name, permission node, settings
schema or stored data changes. The plugin manifest contract is byte for byte the same. Every bookmark opens the same page as
before; the page only sits under a different heading, with a clearer name.

### Shipping

Proposal PR `docs(openspec): propose the administration area redesign`, shared with `fix-top-bar-chrome`,
`add-administration-overview` and `rework-administration-side-navigation`. Then one code PR under the type `feat` and the scope
`ui`:

`feat(ui): group and name the administration pages by operator task`

Commits: `test(ui)` for the red tests, `feat(ui)` for the registry, the frame and the shortcuts, `docs(ui)` for `DESIGN.md`,
`docs(gitbook)` for the reference pages. No file under `openspec/` is in the code PR.

### Dependencies

- `add-repositories-admin-page`, `retire-maven-settings-page` and `fix-administration-navigation`: their code is on `main` and
  their changes are not archived. This change MODIFIES the requirements "Administration tree", "Accessible tree", "One
  Administration frame" and "Breadcrumb names the page shown" of the `administration-navigation` capability they define. Archive
  them first; then these deltas apply to their text.
- `fix-top-bar-chrome`: not required. Together they leave one `h1` per page; this change alone already makes the frame `h1`
  name the page.
- `use-wide-screens`: touches `AdministrationFrame.vue` for the content cap. No requirement overlaps; the second to merge
  rebases.
- **Gate before the code PR:** the tree test in task group 2. If a heading or label fails it, only the display maps change.

### Out of scope

- The `/settings` landing page, the card grid, the page filter and the unsaved marks. They are in `add-administration-overview`.
- The scroll behaviour of the tree, the index rows below 1240 pixels and the sections sheet. They are in
  `rework-administration-side-navigation`.
- The top bar. It is in `fix-top-bar-chrome`.
- Any server change: `@Doc` titles, `PluginUiModel.GROUPS`, `SECTION_GROUPS`, `DEFAULT_GROUP`, the validator, permission nodes,
  routes and dashboard segments (`dashboard-segments.json`, `DashboardPaths.kt`) stay as they are.
- Merging Audit log and Audit settings into one page, and new pages.

## Capabilities

### Modified Capabilities

- `administration-navigation`: the groups and their headings, the labels, the page header and breadcrumb, and the semantics of
  the tree.

## Impact

- **Operators:** nothing to change. Pages are grouped and named differently; addresses are the same.
- **Plugin authors:** nothing to change. `group: "Security"` is shown under Access, `System` under Instance, `Support` under
  Monitoring and support, `Repository` under Artifacts. A plugin page's `h1` is its manifest `title`. Plugin documentation that
  says "under Security" now reads "under Access"; `plugin-api.md` gains the mapping.
- **Code:** `reposilite-frontend/src/navigation/registry.js` (heading map, `DOMAIN_GROUPS`, `DOMAIN_LABELS`, descriptions,
  keywords for the later filter), `components/layout/AdministrationFrame.vue`, `components/layout/SideNavLayout.vue`,
  `pages/IndexPage.vue` (`ADMIN_DESCRIPTIONS` moves into the registry), `components/settings/UnsavedChangesBar.vue` (propagation notice), `components/settings/SettingsView.vue` (frame heading and
  description), the Audit log, Analytics, Maintenance and principal detail pages (shortcuts). No file under `reposilite-backend/`
  changes.
- **Documentation:** `docs/gitbook/reference/dashboard-pages.md` ("Administration tree"), `docs/gitbook/reference/plugin-api.md`
  (a table "group value, heading shown" near line 331), `docs/gitbook/how-to/review-audit-log.md` (heading),
  `reposilite-frontend/DESIGN.md`.

## Verification

- The tree test in task group 2 has a recorded result before the code PR is opened.
- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes; the Playwright suites `navigation.spec.js`, `navigation-chrome.spec.js` and
  `accessibility.spec.js` pass at 320 and 1280 pixels.
- `openspec validate rework-administration-information-architecture --strict` passes, and the dash grep prints nothing.

## Open questions

1. **Heading of the `Repository` group.** Default: "Artifacts", so the heading does not repeat its first entry "Repositories".
   Alternative: "Storage" or "Repository settings". The tree test decides between them.
2. **Renamed labels.** Default: Branding, Reverse proxy, Usage statistics, Single sign-on, LDAP directory, Sign-in protection,
   Search index, Cleanup policies, Audit settings. Alternative: keep the short schema titles and rely on descriptions and filter
   keywords only.
3. **Audit.** Default: two adjacent entries, Audit log and Audit settings, each with its own permission. Alternative: one entry,
   with the settings behind an action on the Audit log page; that needs a separate tree entry for a visitor with the settings
   node but without the audit node.
4. **Group semantics in the tree.** Default: each group is a list labelled by its visible heading (`role="group"` with
   `aria-labelledby`), and the heading text is not a heading element, so heading navigation goes from the page `h1` straight to
   the page content. Alternative: an `h2` per group, as the older "Accessible tree" text says.
5. **"Monitoring and support".** Default: this heading. Alternative: "Operations", shorter but without a source.

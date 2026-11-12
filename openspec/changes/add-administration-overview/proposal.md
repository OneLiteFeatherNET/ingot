# Proposal

## Why

When an operator clicks Administration in the rail, `/settings` opens. On a wide screen the page shows the Administration tree
on the left and, in the content area, the same entries again as a grid of cards. Some cards carry a line such as "7
repositories" or "0 policies", most carry nothing. Nothing in the tree is marked as current, so the page looks like no page
at all. The operator reads two copies of one menu and cannot tell whether a card and a tree entry lead to the same place.

The cards also fail at the one thing a landing page should do: tell the operator what needs attention. Unsaved drafts across
the settings domains are marked only on their card, among twenty others. "0 policies" next to blank cards reads like an error.

Finding the right page is the other half of the problem. With about twenty entries and labels that are still being learnt, an
operator who thinks "nginx", "logo" or "retention" has nothing to type into. The rail's Search finds artifacts, not pages.

The research is unanimous on the first point: a landing page must not repeat the navigation beside it. It is mixed on the
second: some products use a status dashboard, some open the first section, some show an index. Ingot already has a status
dashboard at `/dashboard`, and `/settings` is the only page that hosts whole-configuration import and export and the review
of drafts across domains. So the landing stays a page, and it shows only what the tree cannot.

## What Changes

- **Overview, the first tree entry.** `/settings` is listed as "Overview" at the top of the tree, above the first group, and
  is marked current when it is open. It needs the settings node, like the hub today. The single-page forward of `/settings`
  stays.
- **No card grid.** The content of `/settings` no longer lists the tree's entries. It shows:
  - **Needs attention:** one row per settings domain with an unsaved draft ("Javadoc, Unsaved changes", linking to the page),
    a "Review changes" button that opens the existing review sheet, and a save conflict when one is present. With nothing to
    show, the single sentence "Nothing needs attention."
  - **At a glance:** the count of every settings collection the visitor may edit, taken from the saved settings that the page
    already loads (for example Repositories 7, Cleanup policies 0, and a plugin section's collection). All counts render
    together; none is shown before the saved settings have loaded.
  - A link "Instance status and resources are on the Dashboard" when the visitor may open `/dashboard`. There is no second
    status dashboard.
- **Header of Overview.** `h1` "Overview", breadcrumb "Administration", description "Shared configuration for all
  instances.", and the existing outlined "Import settings" and "Download as JSON" controls on the right. These two act on the
  whole configuration, so they appear on Overview only; the settings domain pages and the Repositories page no longer carry
  them. Import still loads into the draft and asks for no confirmation; the review before Save is the checkpoint.
- **Find an administration page.** A labelled filter field at the top of the tree. It matches the label, the group heading,
  the description and the keywords of every entry (the keywords include each page's old label, such as "web" or "frontend"),
  and for plugin pages their title, description and plugin id. Matching is case insensitive on word prefixes. Entries that do not match
  are hidden while a filter is set, except the current entry, which always stays visible. A polite live region announces the
  number of matches, or "No administration page matches" with the text. Escape clears the field; Enter opens the first match.
  There is no global keyboard shortcut and no query parameter.
- **Unsaved marks in the tree.** An entry whose domain has an unsaved draft shows "Unsaved" with an icon, and its accessible
  name becomes "<label>, unsaved changes". This is the only status a tree entry carries; no counts on rows.
- **One grouping source.** The second grouping of settings domains (`SETTINGS_GROUPS` and `settingsGroupOf`: Repository,
  Instance, Data, Plugins) is removed. Plugin sections keep landing in the tree group their manifest names.
- **The `settings-hub` anchor stays** on the root of the Overview content. Its card children are gone; the anchor never had
  parts, so the contract is unchanged.

Not BREAKING. No configuration key, environment variable, deployment manifest, route, permission node, settings API or stored
data changes. Visible changes: the card grid is gone, and Import and Download sit on Overview only. Both are named in the
release notes and in `dashboard-pages.md`.

### Shipping

Proposal PR `docs(openspec): propose the administration area redesign`, shared with `fix-top-bar-chrome`,
`rework-administration-information-architecture` and `rework-administration-side-navigation`. Then one code PR under the type
`feat` and the scope `ui`:

`feat(ui): replace the settings card grid with an administration overview`

Commits: `test(ui)` for the red tests, `feat(ui)` for the registry, Overview, the filter and the marks, `docs(gitbook)` for the
reference page. No file under `openspec/` is in the code PR.

### Dependencies

- `rework-administration-information-architecture` must be merged first: the filter indexes its labels, descriptions and
  keywords, and Overview uses its page header.
- `restructure-navigation` and `retire-maven-settings-page`: this change MODIFIES their requirement "Settings hub at /settings"
  (capability `settings-navigation`). `fix-administration-navigation`: this change MODIFIES "Exactly one current entry". Archive
  those changes first.

### Out of scope

- A list of common tasks on Overview (open question 1).
- A command palette, a global shortcut, keyboard roving between results.
- New status sources: no request is added to read users, teams, providers or health for Overview.
- Query parameters for list filters on the Access pages.
- The scroll behaviour of the tree and the narrow-width sheet (`rework-administration-side-navigation`).

## Capabilities

### Modified Capabilities

- `settings-navigation`: the page at `/settings` and the place of the whole-configuration actions.
- `administration-navigation`: the Overview entry, the current entry on `/settings`, the filter and the unsaved marks.

## Impact

- **Operators:** nothing to change. Administration opens on Overview, which lists what needs attention; the tree is the only
  list of pages and can be filtered. Import and Download are on Overview.
- **Plugin authors:** nothing to change. Plugin pages and sections appear in the tree and are found by the filter by title,
  description and plugin id. A stylesheet that targets `settings-hub` keeps matching the Overview root.
- **Code:** `reposilite-frontend/src/navigation/registry.js` (Overview entry, `currentTreeKey`, removal of `SETTINGS_GROUPS`),
  `components/settings/SettingsHub.vue` and `settingsHub.js` (Overview content), `components/settings/SettingsView.vue` (header
  actions only on Overview), `components/layout/SideNavLayout.vue` (filter, marks), a new `src/navigation/adminFilter.js` (pure
  matching). The Repositories page is rendered by `SettingsView` and loses the actions with the domain pages. No file under `reposilite-backend/`
  changes.
- **Documentation:** `docs/gitbook/reference/dashboard-pages.md` ("Settings hub" becomes "Overview", where Import and Download
  live, the filter).

## Verification

- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes; the Playwright suites `navigation.spec.js`, `layout.spec.js`,
  `repositories.spec.js` and `accessibility.spec.js` pass at 320 and 1280 pixels.
- `openspec validate add-administration-overview --strict` passes, and the dash grep prints nothing.

## Open questions

1. **Common tasks on Overview.** Default: not shipped; the filter keywords carry the verbs ("add user", "nginx"). Alternative: up
   to six permission-gated links phrased as tasks (Add a user, Create a repository, Connect LDAP), each opening the page whose
   create action is visible above the fold.
2. **Import and Download.** Default: on Overview only. Alternative: also on every settings domain page, as today.
3. **At a glance.** Default: collection counts from the saved settings. Alternative: leave the section out and keep Overview to
   Needs attention only.
4. **Enter in the filter.** Default: opens the first match. Alternative: Enter does nothing, and the visitor tabs to a result.

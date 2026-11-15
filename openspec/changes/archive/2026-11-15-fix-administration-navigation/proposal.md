# Proposal

## Why

An operator who moves between the Administration pages of a staging instance sees the navigation change under them. On
the settings pages the side navigation lists Repository (Repositories, Maven, Javadoc, Cleanup), Security and a System group
with every settings domain. On the Access pages the same navigation lacks Maven, Javadoc, Cleanup and the whole System
group. On the Console, Maintenance and Analytics pages it lacks the same entries. The operator cannot tell whether a page
has disappeared or whether the menu is simply different from one page to the next.

The same operator finds more inconsistencies on the way:

- On `/settings/audit` two entries are highlighted at once. "Audit log" under Security and "Audit" under System both carry
  the current mark, because the Audit log entry and the Audit settings domain share the key `audit`. The breadcrumb says
  "Audit log" while the page shows the audit settings form.
- The page heading switches between "Settings" and "Access" while the breadcrumb says "Administration" on both. The
  Console, Maintenance and Analytics pages have no page heading at all.
- A stray letter at the left edge of the viewport sits at the height of the side navigation on the Administration pages.
  The rail label "Administration" is wider than the 80 pixel rail, so it overflows, and on the Console page it is visibly
  clipped ("dministration").
- The Maintenance page reports "Total 0 B across 0 repositories" and "No repository has been measured yet" while the
  storage figures are still being computed for an instance with seven repositories. Zero is presented as a result.

These regressions come from one tree that is built in three places with three different inputs, one key that two entries
share, and one page frame that each view assembles on its own. Each fix is small. Together they make the Administration
section read as one place.

The regressions would have been caught by browser end to end tests, which the separate change `add-dashboard-e2e-tests`
proposes. This change does not depend on it: the tests named in the tasks are Vitest component and registry tests.

## What Changes

- **One tree on every Administration page.** Access, Settings, Audit, Console, Maintenance, Analytics and Migration show
  the same Administration tree. The settings domains a visitor may open are part of that tree on every page, not only on
  the settings pages. A visitor without the settings permission sees the same tree as today and causes no request that
  would answer 403.
- **Unique keys and one current entry.** No two entries of the Administration tree share a key, including the entries a
  plugin adds through settings domains. Exactly one entry carries `aria-current="page"`. `/settings/audit` marks only the
  Audit settings domain, and the Audit log entry is not marked.
- **Breadcrumb names the page shown.** The last crumb is the label of the entry that is current, which is the page on
  screen.
- **One Administration frame.** Every Administration page uses the same frame: the navigation rail on the same terms as
  the other areas, one page heading "Administration", the same container, and the side tree at the same position. Each
  page keeps its own description. The default heading is an open question.
- **Rail label fits.** The label "Administration" of the rail wraps inside the rail and never overflows it. Its accessible
  name stays "Administration". A rail that is hidden renders nothing visible.
- **Maintenance page.** While the storage figures are computing, the page shows the progress only, not zero totals. The
  subtitle no longer uses the word "managers".

Not BREAKING. No configuration key, environment variable, deployment manifest, volume or stored data changes. Routes, route
names and permission checks do not change.

### Shipping

Proposal PR `docs(openspec): propose fix-administration-navigation`, then one code PR, `fix(ui): keep the administration
navigation the same on every page`. The code PR contains test commits of type `test(ui)` and fix commits of type
`fix(ui)`, all with the scope `ui`. It contains no file under `openspec/`.

### Dependencies

- `add-repositories-admin-page`: its code is on main (commit `dcd8294a6`) and introduced the Administration tree
  (`ADMIN_TREE` in `reposilite-frontend/src/navigation/registry.js`). Its change is not archived yet, so the requirements
  of this change are MODIFIED or ADDED against the `administration-navigation` capability as that change names it. The
  capability is not in `openspec/specs` yet, so `openspec validate` warns that archiving this change would refuse its MODIFIED
  deltas. Archive `add-repositories-admin-page` first; then the MODIFIED requirement applies to its text unchanged.
- `restructure-navigation`: the side navigation of Access and Settings that this change unifies. No requirement of it
  changes.
- `add-dashboard-e2e-tests`: proposed separately. Not a dependency, see Why.

### Out of scope

- Retiring the `/settings/maven` page. That belongs to the separate change `retire-maven-settings-page`.
- Any change to the server, the API, the permission nodes or the route paths. No dashboard segment is added.
- The layout of the Administration pages: breakpoints, the side tree width, the content width, the sticky header, table truncation, the row actions menu and column priority. These are in `rework-administration-layout`, which builds on the frame of this change.
- Making the whole dashboard WCAG conformant. This change fixes the navigation and frame that the Administration pages share.
- Adding pages of the target menu (Storage, Routing rules, Tokens and the others). Their entries are not drawn until the pages exist.
- The Dashboard, Browse and Search pages.
- Legal and commercial questions.

## Capabilities

### Modified Capabilities

- `administration-navigation`: the tree on every Administration page, the settings domains in it, and the accessibility of
  the tree, as introduced by `add-repositories-admin-page`.

### New Capabilities

- `maintenance-page`: the loading state of the storage figures and the subtitle of the Maintenance page.

## Impact

- **Operators:** nothing to change. A visitor sees the same tree on every Administration page, one heading and one frame.
  The label "Administration" no longer overflows the rail.
- **Code:** `reposilite-frontend/src/navigation/registry.js`, `components/layout/SideNavLayout.vue`,
  `components/access/AccessView.vue`, `components/settings/SettingsView.vue`, `components/settings/settingsHub.js`,
  `pages/IndexPage.vue`, `components/md/MdNavigationRail.vue`, `components/maintenance/MaintenanceView.vue`,
  `components/maintenance/StorageUsagePanel.vue`, and a shared composable for the settings domain list. No file under
  `reposilite-backend/` changes.
- **Documentation:** `docs/gitbook/reference/dashboard-pages.md` names the old headings where it describes the Access and
  Settings pages. It is updated in the code PR when a heading changes.

## Verification

- Each bug has a failing Vitest test that is run and seen to fail before its fix, as the tasks describe.
- `npm test` in `reposilite-frontend` passes.
- `openspec validate fix-administration-navigation --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]'` over the change prints nothing.

# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Every test is written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock, no network (the client is a fake that counts calls), a fresh store, `can` and router per test, assertions
with messages. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, through `heavy`.
No file under `reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-administration-navigation`: this change and nothing else. Verify:
  `openspec validate fix-administration-navigation --strict` passes and the dash grep prints nothing.

## 2. Red tests for the navigation

- [ ] 2.1 `test(ui)`: write failing tests for bug 1 (the tree differs per page). For one full administrator, render each of
  `/settings/maven`, `/access/users`, `/audit`, `/console`, `/maintenance` and `/analytics` and read the side navigation of
  each; assert the three trees are identical in groups, order and labels. Add one for a visitor with the settings node but
  without the users node. Verify: they fail on `AccessView`, `IndexPage` and the Console, Maintenance and Analytics pages.
- [ ] 2.2 `test(ui)`: write failing tests for bug 2 (keys and current). With the settings domain `audit` present, assert the
  keys of the tree are unique; with the plugin domains `users`, `security` and `audit`, assert the same. On `/settings/audit`
  assert exactly one entry carries `aria-current="page"` and it is the Audit settings entry; assert the last breadcrumb crumb
  reads "Audit settings". On `/audit` assert only the Audit log entry is marked. Verify: they fail on the duplicate `audit` key
  (`registry.js:177` and the domain of the same id).
- [ ] 2.3 `test(ui)`: write failing tests for the permission rule. A visitor with only `ingot.admin.audit` opens `/audit`: the
  tree has no settings domain and the fake client records no call to the settings domain endpoint. A visitor with the settings
  node opens `/console` then `/settings`: the endpoint is called once. Verify: they fail on the views that pass `domains: []`.

## 3. Red tests for the frame and the rail

- [ ] 3.1 `test(ui)`: write failing tests for bug 3 (heading). On `/settings`, `/access/users`, `/console`, `/maintenance` and
  `/analytics`, assert one `h1` named "Administration" and the page description of each page. Verify: they fail on the
  "Settings" and "Access" headings and on the pages without a heading.
- [ ] 3.2 `test(ui)`: write failing tests for bug 4 (rail label). The Administration label of `MdNavigationRail` carries the
  wrapping class and the accessible name "Administration"; the rail wrapper carries the hidden class below 600 pixels and no
  text is drawn when it is hidden. Verify: they fail on the unwrapped label (`MdNavigationRail.vue:131`).
- [ ] 3.3 `test(ui)`: write the failing test for the frame at the same place on every page: the side navigation is the first
  child of the same frame element on each Administration page. Verify: it fails on the views that draw their own layout.

## 4. Red tests for the Maintenance page

- [ ] 4.1 `test(ui)`: write failing tests for item 8. While the storage response reports `computing` with no repositories,
  assert no "Total" line and no "No repository has been measured yet" text, and the progress is shown. When the figures
  arrive, assert the total and the count. Assert the subtitle of `MaintenanceView` contains no "manager". Verify: they fail on
  `StorageUsagePanel.vue:151` and `MaintenanceView.vue:30`.

## 5. Fixes

- [ ] 5.1 `fix(ui)`: add `components/settings/useSettingsDomains.js`, which lists the domains once per session and only with
  the settings node, and pass its result to every Administration page. Verify: the tests of 2.1 and 2.3 pass.
- [ ] 5.2 `fix(ui)`: give every entry of the tree a key of its form (`page:<key>` for a static entry, `settings:<domain>` for a
  domain), update `SideNavLayout` callers, and make the breadcrumb read the current entry. Update the expectations of
  `administration.test.js` and `registry.test.js` that name the old keys; list each change in the pull request. Verify: the
  tests of 2.2 pass.
- [ ] 5.3 `fix(ui)`: add `components/layout/AdministrationFrame.vue` with the one `h1` "Administration", the page description slot
  and the side navigation, and use it on every Administration page. The views stop drawing their own headings. Verify: the
  tests of 3.1 and 3.3 pass, and the existing `AccessView` and `SettingsView` tests pass with only the listed changes.
- [ ] 5.4 `fix(ui)`: wrap the rail label (`max-w-full break-words text-center` on the label span in `MdNavigationRail.vue`),
  keep the accessible name, and draw nothing while the rail is hidden. Verify: the tests of 3.2 pass.
- [ ] 5.5 `fix(ui)`: guard the totals block of `StorageUsagePanel.vue` with `!computing`, and change the subtitle of
  `MaintenanceView.vue`. Verify: the tests of 4.1 pass.

## 6. Browser check

- [ ] 6.1 Run the dashboard in a browser at 390 pixels width and at one desktop width and record in the pull request: the tree on each
  Administration page, the single current entry on `/settings/audit`, the heading, the rail label on `/console`, and the
  Maintenance page while the figures compute. Confirm or correct the cause of the stray letter (open question 4 of the design)
  and record it. Verify: each line is ticked, or a follow-up issue is linked.
  The layout checks at 1280 and 1920 pixels (widths, the sticky side tree, the table) are not part of this task: they are covered by
  `add-dashboard-e2e-tests` and by `rework-administration-layout`.

## 7. Documentation

- [ ] 7.1 `docs(ui)`: update `docs/gitbook/reference/dashboard-pages.md` line 63 (the heading of every Administration page is
  Administration, the page description names the page) and `docs/gitbook/how-to/review-audit-log.md` line 28 (the page heading is
  Administration, the page is the Audit log entry). Verify: `grep -rn "heading \*\*Audit log\|Shared configuration" docs/gitbook`
  finds no stale heading, and the dash grep prints nothing.

## 8. Pull request

- [ ] 8.1 Open the code pull request under the title `fix(ui): keep the administration navigation the same on every page`,
  from the remote main branch that already contains the merged proposal, with no file under `openspec/`. Fill the
  repository's pull request template.

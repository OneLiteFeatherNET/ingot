# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no network, no fixed ports, no shared mutable state (a fresh store and router per test), and assertions with
messages. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, through `heavy`. No file
under `reposilite-backend/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-repositories-admin-page`: this change and nothing else. Verify:
  `openspec validate add-repositories-admin-page --strict` passes and the dash grep from the proposal prints nothing.

## 2. Pure rules

- [ ] 2.1 `test(ui)`: write failing tests for the derived type of a draft entry (the fixture shared with `RepositoryTypeTest`
  in the backend: no references, local only, external only, both, blank references ignored, declared type wins), the write
  policy mapping of design D5 (each choice and each stored state, the round trip Read only then Allow once, no key written
  when nothing changes), the row model (columns, address, "Unsaved" marker, a group has no storage), and filter and sort
  (id, type, visibility, storage; stable order; the `tag:` search). Verify: they fail on the missing modules.
- [ ] 2.2 `feat(ui)`: add `components/repositories/repositoryRows.js` and `writePolicy.js` and extend `repositoryTypes.js`
  with the derived type. Verify: the tests of 2.1 pass and the existing `repositoryTypes.test.js` passes unedited.

## 3. Registry and routes

- [ ] 3.1 `test(ui)`: write failing tests for the registry: the four rail entries; `ADMIN_TREE` groups and entries; a group
  without a visible entry left out; the Administration entry hidden when nothing is permitted; the hub forwarding with one
  page; settings domains placed under Repository or System (a plugin domain under System); the bar for an administrator,
  for a visitor with only the audit node and for one without the dashboard node; reserved page names against the core domain
  ids; `unlistedRoutes` covers the new routes. Add router tests: `/settings/repositories` and `/settings/repositories/:id`
  resolve before `settings`; `/settings/maven/repositories/x` redirects to `/settings/repositories/x` with the query kept;
  another collection does not redirect; `/settings/maven?new=repository` redirects with the query; every route of
  `docs/gitbook/reference/dashboard-pages.md` still resolves with its name and meta. Verify: they fail before 3.2.
- [ ] 3.2 `feat(ui)`: change the registry and `routes.js` and add the redirect step to `router/create.js`. Retarget the
  four links (`RepositoryWizard.vue`, `PublicRepositoriesDialog.vue`, `FileList.vue`, `gettingStarted.js`). Update the label
  to Browse and the document title. Verify: the tests of 3.1 pass, and the changed expectations of `registry.test.js`,
  `routeTree.test.js` and `IndexPage.test.js` are listed in the pull request.

## 4. Navigation components

- [ ] 4.1 `test(ui)`: write failing component tests: the rail shows Browse, Search, Dashboard, Administration with the
  Administration entry opening `/settings`; the bottom bar has four items and no More for an administrator; the hub and the
  side navigation of an Access page and of a Settings page render the same tree with `aria-current="page"` on the current
  entry; the navigation landmark is named "Administration", groups are headings with lists; below 600px the side navigation
  is an index with a back arrow. Verify: they fail before 4.2.
- [ ] 4.2 `feat(ui)`: update `SideNavLayout` callers, `SettingsHub`, `AccessView` and `IndexPage` to read the tree. Verify:
  the tests of 4.1 pass and the Access and Settings view tests pass with only the listed changes.

## 5. Repositories page

- [ ] 5.1 `test(ui)`: write failing component tests for the table: caption with the count; `th scope` for columns and rows;
  `aria-sort` on the sorted column only and the status message after a sort; the labelled filter and its status count; badge
  text for every type, the word "derived" for an untyped repository, the warning and no declare action for Mixed; row actions
  named with the repository; the copy button's status message and kept focus; the Version policy and Write policy columns
  present only when the schema has the fields; the page edits the shared draft (a new row shows "Unsaved", the save bar
  appears, discard removes it). Verify: they fail before 5.2.
- [ ] 5.2 `feat(ui)`: add `components/repositories/RepositoriesView.vue`, `RepositoriesTable.vue`, `TypeBadge.vue` and the
  declare type dialog, mount them from `SettingsView.vue` for `settings-repositories`, and move the repositories collection
  out of the `maven` domain page, leaving a card that links to the new page. Verify: the tests of 5.1 pass.
- [ ] 5.3 `test(ui)`: write failing tests for the detail and the policy controls: the detail opens at
  `/settings/repositories/:id` and keeps the type switch and `RepositoryAccessCard`; Version policy options and Write policy
  radio group on a hosted repository only; the help text per choice; "Declare type" writes `type` and nothing else, and asks
  first when empty mirrored entries would be dropped; an unknown id shows the not found pane with a link to the list. Verify:
  they fail before 5.4.
- [ ] 5.4 `feat(ui)`: add `RepositoryDetail.vue` and `WritePolicyField.vue` over the existing entry editor. Verify: the tests
  of 5.3 pass.
- [ ] 5.5 `test(ui)`: write failing tests for create and delete: the wizard opens from "New repository" and from
  `?new=repository` once; after creating, focus is on the h1 of the detail; delete asks for the typed name, lists the groups
  that reference the repository, puts initial focus on Cancel, and after confirming moves focus to the list h1 with the status
  message "deleted, not saved yet"; Escape closes each dialog and returns focus to its opener. Verify: they fail before 5.6.
- [ ] 5.6 `feat(ui)`: wire create, delete and the group reference warning. Verify: the tests of 5.5 pass.

## 6. Accessibility pass

- [ ] 6.1 `test(ui)`: write failing tests for the requirements of the spec that 5.x did not cover: Tab order across one row
  (name link, then actions), no `tabindex` above 0, the scroll region of a narrow table is named and focusable, every
  control has a name, each invalid field has `aria-invalid` and a described message, a refused wizard step moves focus to the
  error summary, every interactive control has at least 24 by 24 CSS pixels (a style test of the shared classes), and a
  contrast test over the theme pairs used by the badges. Verify: they fail before 6.2.
- [ ] 6.2 `feat(ui)`: fix what 6.1 finds. Verify: the tests of 6.1 pass.
- [ ] 6.3 Manual pass, recorded in the pull request description. With the keyboard alone: reach the table from the skip
  link, sort, filter, open a detail, create, edit, delete and save, without a mouse and without a focus trap. With NVDA on
  Firefox or Orca: the table is announced with its caption, row and column headers are read, sorting and filtering results
  are announced, dialogs are announced with their names, and the navigation tree reads with its groups. At 320px width and
  200% zoom: no two-dimensional scroll outside the table region. Verify: each line is ticked or has a linked follow-up issue.

## 7. Documentation

- [ ] 7.1 `docs(ui)`: update `docs/gitbook/reference/dashboard-pages.md` (new routes, the Browse label, the four rail
  entries, the Administration tree, the redirects, the reserved page names under `/settings`), add the Repositories page
  section to `docs/gitbook/reference/repository-settings.md` (columns, derived type, declare type, write policy mapping,
  delete behaviour), and update the how-tos and tutorial named in the proposal so they say "Administration, Repository,
  Repositories". Add a short accessibility statement for the dashboard pages touched, and update `docs/gitbook/SUMMARY.md` if a
  page is added. No configuration key or environment variable changes, so no page for those is touched.
- [ ] 7.2 Verify: `grep -rn "Settings, then Maven\|Settings.*Maven. tab" docs/gitbook` finds no stale path, and the dash grep
  prints nothing.

## 8. Pull request

- [ ] 8.1 Open the code pull request under the title `feat(ui): add a repositories administration page and regroup the
  navigation`, from the remote main branch that already contains the merged proposal, with no file under `openspec/`.
  Fill the repository's pull request template.

# Tasks

This change ships as two code pull requests, in this order. Pull request A is `refactor(ui): drive navigation from one registry`
(groups 2). Pull request B is `feat(ui): group the navigation and add side navigation for access and settings` (groups 3 and 4).
Pull request B is opened from a branch that starts on main after pull request A is merged.

Precondition for group 2: the machine accounts fix (step 1, a separate change) is merged. Its check is that
`reposilite-frontend/src/components/access/AccessView.vue` filters the machine accounts tab by the users node and no longer by
`ADMIN_NODES.tokens`.

Every test follows F.I.R.S.T.: no sleeps or timers, no system clock, no shared mutable state, no order dependence, and assertions
with messages. Each test is written before the code it covers and is seen to fail. Frontend runs use `npm test` in
`reposilite-frontend`, with the test file of the group. No file under `reposilite-backend/` changes; a backend run is not needed.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose restructure-navigation`: this change (proposal, design, capability specs, tasks) and nothing else.
  Verify: `openspec validate restructure-navigation --strict` passes, and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/restructure-navigation`
  prints nothing.

## 2. One registry (pull request A, `refactor(ui)`)

- [ ] 2.1 `test(ui)`: write failing tests for `reposilite-frontend/src/navigation/registry.js`. One behaviour per test: every entry
  has a key, a route name, a label, an area and a permission; every named route of `routes.js` is an entry, a child of one, or on the
  exempt list of design D1; `visibleSections` (renamed from the current helper) gives the same entries as today for five permission
  sets (none, users only, audit only, full administrator, instance administrator); the Access tabs for `AccessView` are the
  users, machine accounts and teams tabs when the users node is held, and the machine accounts tab is absent when only the tokens node
  is held; the bar order of the entries is the one of D3, with the old overflow result. Verify: the test file fails on the missing
  module `src/navigation/registry.js`, and the failure message names the module.
- [ ] 2.2 `refactor(ui)`: add `src/navigation/registry.js` with the entries of D1 and D2 (old labels, Overview still named Overview
  in this pull request), build the route `meta` of `routes.js` from it, and read the rail, the bar, the More sheet, the guard, the Access
  tabs (`AccessView.vue` `ALL_TABS`, `IndexPage.vue` `ACCESS_TABS`) from it. Remove `pages/index-tabs.js` after its tests move to
  `registry.test.js`. Verify: the test of 2.1 passes. `IndexPage.test.js`, `guard.test.js`, `create.test.js`, `legacy.test.js` and
  `AccessView.test.js` pass with no assertion changed, and `git diff` of the test files shows only import paths.
- [ ] 2.3 `test(ui)`: write failing tests for the tree test of D1 as a separate check: a route added to `routes.js` without a registry
  entry or exempt entry fails with the route name in the message. Verify: the test fails on a fixture route list that has one unlisted
  name, and passes on the real list.
- [ ] 2.4 `refactor(ui)`: make the check of 2.3 pass with the real route list by adding the exempt names of D1 and the child entries
  for Access and Settings. Verify: 2.3 passes, and `npm test` in `reposilite-frontend` is green.
- [ ] 2.5 `refactor(ui)`: open pull request A with the title `refactor(ui): drive navigation from one registry`. Read
  `.github/pull_request_template.md` and fill its sections: the user story (a maintainer adds a page once, not four times), the
  verification (the test names of 2.1 and 2.3), and a note that nothing is visible. Verify: `gh pr view --json title` shows the exact
  title, the commit-style hook accepts the title and every commit, and the body has no em or en dash.

## 3. Grouped rail, bar and labels (pull request B, `feat(ui)` and `test(ui)`)

- [ ] 3.1 `test(ui)`: write failing tests of the rail: groups in the order Work, Monitor, Administration, Troubleshoot; the entries of
  each group as in D2; the label of the route `overview` is Repositories; Audit is labelled Audit log; an empty group is absent; the
  document title of `/audit` reads Audit log. Verify: the tests fail on the old labels and the old single list.
- [ ] 3.2 `feat(ui)`: render the groups in the rail with dividers and group names as accessible labels, rename the two labels, and
  update the document titles. Verify: 3.1 passes.
- [ ] 3.3 `test(ui)`: write failing tests of the bottom bar rule (D3) as a table over the permission sets of 2.1: a full administrator gets
  Repositories, Search, Dashboard, Access and More, and the More sheet holds Analytics, Audit log, Settings, Console and Maintenance under
  their group headings; a visitor with audit only gets Repositories, Search and Audit log with no More item; an anonymous visitor gets
  Repositories and Search; a visitor who holds exactly four permitted entries gets no More item. Verify: the tests fail on the old static
  flag result for the audit-only row.
- [ ] 3.4 `feat(ui)`: switch the bar to the rule of D3 and drop the static overflow flag from the registry. Update the bar tests whose
  expectation is the old flag result, and list each changed expectation in the pull request. Verify: 3.3 passes, and the full administrator
  row is unchanged.
- [ ] 3.5 `test(ui)`: write failing tests of the no access state (D8): a signed-in visitor who opens `/console` without the console node
  stays at `/console` and sees the message "You do not have access to this page" with a link to Repositories; no navigation to `overview`
  happens; an anonymous visitor is asked to sign in and sees no message. The tests use the router with an in-memory history and an injected
  session. Verify: the tests fail on the current redirect to `overview`.
- [ ] 3.6 `feat(ui)`: implement the no access state in the page layer and let the guard pass a denied, signed-in visitor through, keeping
  the password change guard unchanged. Remove the toast and the watcher redirect in `IndexPage.vue`. Update the tests that asserted the
  redirect and list each of them in the pull request. Verify: 3.5 passes, and `guard.test.js` has only the changed redirect expectations.
- [ ] 3.7 `docs(ui)`: update `docs/gitbook/reference/dashboard-pages.md` (the navigation labels in the table and the note at line 54) and
  `docs/gitbook/reference/web-routes.md` for the new labels and the no access state. Verify: `grep -rn "Overview" docs/gitbook/reference/dashboard-pages.md`
  finds no label that names the navigation, and every route name in those pages still exists in `routes.js`.

## 4. Split layout and settings hub (pull request B, `feat(ui)` and `test(ui)`)

- [ ] 4.1 `test(ui)`: write failing component tests of `SideNavLayout.vue` (D6): the grouped links of the children render in their groups;
  the current page carries `aria-current="page"`; each link is a real link to its route; with the narrow flag set, the component shows the
  index with a back arrow on a page and returns to the index from it. The narrow flag is a property, so the test sets it. Verify: the tests
  fail on the missing component.
- [ ] 4.2 `feat(ui)`: add `src/components/layout/SideNavLayout.vue` and use it in `AccessView.vue` and `SettingsView.vue` in place of the
  horizontal tab rows, with the breadcrumb built from the registry. The page bodies are unchanged. Verify: 4.1 passes, and the existing
  `AccessView.test.js` and `SettingsView*.test.js` pass with changes only where the tab role is replaced by the link role, listed in the
  pull request.
- [ ] 4.3 `test(ui)`: write failing router tests of the forwards (D7): `/access` with one permitted page replaces the address with that page;
  `/settings` with one visible domain replaces the address with that domain's page; `/access` on wide widths forwards to the first permitted
  page, and on narrow widths shows the index (the width is injected). Verify: the tests fail on the current behaviour for narrow widths.
- [ ] 4.4 `feat(ui)`: add the forwards to the route guard or the page as D7 decides, and the narrow index of `/access`. Verify: 4.3 passes.
- [ ] 4.5 `test(ui)`: write failing tests of the settings hub (D9) with a fixture schema list and a fixture saved configuration: the groups
  Repository, Instance, Data and Plugins contain the domains named in D9, and an unnamed domain lands in Plugins; a card shows the saved value
  built from the collections ("6 repositories" for six entries); a domain with a draft change shows "Unsaved changes" and the saved value
  stays; the hub shows only the groups the visitor may edit; the last line links to `/access`; opening a card does not write the draft.
  Verify: the tests fail on the missing hub.
- [ ] 4.6 `feat(ui)`: render the hub at `/settings` (no domain in the address) with the mapping of D9 kept in the registry, and the cards.
  Verify: 4.5 passes, and the draft and save tests in `SettingsView*.test.js` pass unchanged.
- [ ] 4.7 `test(ui)`: write a failing router test that `/settings/login` replaces its address with `/access/sso`, that `/settings/authentication`
  still replaces with `/access/ldap`, and that the redirects come before the generic `settings` route. Verify: the test fails on the current
  card for `login`.
- [ ] 4.8 `feat(ui)`: add the route `settings-login` with its redirect in `routes.js`, placed before `settings`, and register it in the
  registry's exempt list. Verify: 4.7 passes, and the tree test of 2.3 still passes.
- [ ] 4.9 `docs(ui)`: describe the settings hub, the side navigation of Access and Settings and the `/settings/login` redirect in
  `docs/gitbook/reference/dashboard-pages.md` and `docs/gitbook/reference/web-routes.md`. Verify: every route name and path named on the pages
  exists in `routes.js`, and the pages name no label that the code does not show.
- [ ] 4.10 `feat(ui)`: open pull request B with the title `feat(ui): group the navigation and add side navigation for access and settings`,
  after pull request A is merged and the branch is rebased on main. Read `.github/pull_request_template.md` and fill its sections, listing the
  changed test expectations of 3.4, 3.6 and 4.2 as the story's reviewer notes. Verify: `gh pr view --json title` shows the exact title, the
  commit-style hook accepts the title and every commit, and the body has no em or en dash.

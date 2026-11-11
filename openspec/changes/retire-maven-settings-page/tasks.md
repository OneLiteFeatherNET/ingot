# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no network, no fixed ports, no shared mutable state (a fresh store, router and wrapper per test), and assertions with
messages. `npm test` runs in `reposilite-frontend`, targeted with a file pattern while working, and the full run goes through
`heavy`. No file under `reposilite-backend/` changes, and no file under `openspec/` changes in the code pull request.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose retire-maven-settings-page`: this change and nothing else. Verify:
  `openspec validate retire-maven-settings-page --strict` passes and the dash grep prints nothing.

## 2. Registry and settings lists

- [ ] 2.1 `test(ui)`: write failing tests in `navigation/administration.test.js`, `navigation/registry.test.js` and
  `components/settings/accessDomains.test.js`: the Repository group of the tree lists Repositories, Cleanup policies and Javadoc
  and no Maven entry, for a domain list that contains `maven`; `settingsTabDomains` drops `maven` and keeps the other domains in
  their order; `hubGroups` (`settingsHub.test.js`) shows no card for `maven`; `unlistedRoutes` covers the new redirect route name. Verify: they fail on the current lists.
- [ ] 2.2 `feat(ui)`: remove `maven` from `REPOSITORY_DOMAINS` and `SETTINGS_GROUPS` in `navigation/registry.js`, add
  `DOMAINS_ON_OTHER_PAGES = ['maven']` there and export it, make `settingsTabDomains` (`components/settings/accessDomains.js`)
  drop it with the access domains, and add the new route name to `EXEMPT_ROUTES`. Verify: the tests of 2.1 pass and the
  existing `registry.test.js` expectations that name the Maven card change only where listed in the pull request.

## 3. Router

- [ ] 3.1 `test(ui)`: write failing tests in `router/repositories-routes.test.js`: `/settings/maven` redirects to
  `/settings/repositories` (replacing the test "does not redirect the maven domain page"); `/settings/maven?x=1` keeps the query;
  `/settings/maven/repositories` redirects to `/settings/repositories`; the existing entry and wizard redirects still hold;
  an entry of `cleanup` is not redirected; every route name listed in `dashboard-pages.md` before this change still resolves
  (the name list of this file, "the route names of the dashboard before this change"). Verify: they fail before 3.2.
- [ ] 3.2 `feat(ui)`: add the redirect of `/settings/maven` and the redirect route `settings-maven-repositories` to
  `router/routes.js` (before the generic `settings` route), and extend the redirect block of `router/create.js`. Verify: the
  tests of 3.1 pass, and `router/create.test.js` and `router/guard.test.js` pass unedited.

## 4. Maven options section

- [ ] 4.1 `test(ui)`: write failing tests for the form helper and the section. `components/settings/domainForm.test.js`:
  `domainFormOf` returns the form of the non-collection properties and `null` when there are none. `components/repositories/MavenOptionsPanel.test.js`:
  the section is headed "Maven options" and shows "Resolution cache maximum age in cluster mode" with its description; it is not
  rendered when the schema has no such field; a change calls `setDomainData('maven', data)` with the `repositories` list equal to
  the list before the change, and calls `validateDomain('maven')`; an invalid value is `aria-invalid` and described by its
  message; the section takes no focus on mount. `components/repositories/RepositoriesView.test.js`: the section comes after
  the table, and a visitor without the settings node sees no section. Verify: they fail on the missing modules.
- [ ] 4.2 `refactor(ui)`: move `domainForm` of `components/settings/SettingsView.vue` into `components/settings/domainForm.js`
  as `domainFormOf(schema, collectionKeys)` and use it from `SettingsView.vue` for the other domains. Verify: the existing
  `SettingsView.domainSchemas.test.js` and `SettingsView.test.js` pass unedited, and `domainForm.test.js` passes.
- [ ] 4.3 `feat(ui)`: add `components/repositories/MavenOptionsPanel.vue` with the renderers and form configuration of
  `SettingsView.vue`, and mount it at the end of `components/repositories/RepositoriesView.vue`. Verify: the tests of 4.1 pass.

## 5. Links to the Repositories page

- [ ] 5.1 `test(ui)`: write failing tests that the links go to the new addresses: `components/dashboard/gettingStarted.test.js`
  (the "new repository" item targets `settings-repositories` with `new=repository`), `components/dashboard/PublicRepositoriesDialog.test.js`
  (the address of a repository is `/settings/repositories/<id>`), and `components/browser/FileList.test.js` (edit members goes to
  `settings-repository` with the id). Verify: they fail on the current targets.
- [ ] 5.2 `feat(ui)`: change the targets in `components/dashboard/gettingStarted.js`, `components/dashboard/PublicRepositoriesDialog.vue`
  and `components/browser/FileList.vue`. Verify: the tests of 5.1 pass.

## 6. Remove the Maven page

- [ ] 6.1 `test(ui)`: write failing tests in `components/settings/SettingsView.repositories.test.js`: opening `/settings/maven`
  shows the Repositories page and no Maven link card; the generated domain form is not rendered for `maven`. In
  `components/settings/SettingsView.hub.test.js`: the hub with `maven` and `login` does not forward to `maven`, and the hub shows no
  Maven card. Verify: they fail on the current page.
- [ ] 6.2 `feat(ui)`: remove the Maven link card, the `movedToRepositories` and `mavenDomain` rules and the `repositoryCount` text
  from `components/settings/SettingsView.vue`, so the `maven` domain is never the active domain. Keep the store, the export,
  the import and the reset unchanged. Verify: the tests of 6.1 pass, and `settingsExport.test.js`, `settingsImport.test.js`,
  `FactoryResetModal.test.js` pass unedited.

## 7. Documentation

- [ ] 7.1 `docs(ui)`: update `docs/gitbook/reference/dashboard-pages.md` (the Repository group row, the route table with the
  new redirect route, the redirect table with the `/settings/maven` and no-entry addresses, the hub paragraph),
  `docs/gitbook/reference/repository-settings.md` (a "Maven options" sub-section of the Repositories page section that names
  `resolutionCacheClusterMaxAgeSeconds` and links to `settings-domains.md#maven`), `docs/gitbook/reference/settings-domains.md`
  (the `maven` row says the domain is edited on the Repositories page) and `docs/gitbook/explanation/storage-and-mirror-caching.md`
  (the sentence about `resolutionCacheClusterMaxAgeSeconds` names the Repositories page). No new page, so `SUMMARY.md` stays.
  Verify: `grep -rn "Maven settings\|Settings, then Maven" docs/gitbook` finds only the sentences that describe the redirect, and the
  dash grep prints nothing.

## 8. Verification

- [ ] 8.1 Run `npm test` in `reposilite-frontend` through `heavy`, and the production build (`npm run build`) through `heavy`.
  Every test that changed is listed with its reason in the pull request. Verify: both pass.
- [ ] 8.2 Manual check in a running dashboard, with the keyboard alone: open `/settings/maven` and land on Repositories, change the
  Maven option, save, reload, and check the value and the repositories. Check the section with a screen reader (NVDA with
  Firefox, or Orca) for the heading, the label and the description. Record the result in the pull request. Verify: the result
  is written in the pull request description.

## 9. Pull request

- [ ] 9.1 Open the code pull request under the title `feat(ui): retire the maven settings page in favour of repositories`,
  from the remote main branch that already contains the merged proposal, with no file under `openspec/`. Fill the repository's
  pull request template and state that the change is not breaking. Verify: the title passes the commit style hook and the PR
  body has no session link.

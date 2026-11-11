# Proposal

## Why

An operator who wants to change a repository finds two places for it. The Repositories page under Administration
(`/settings/repositories`, from `add-repositories-admin-page`) lists every repository and edits it. The Maven page
(`/settings/maven`) still exists, and the Settings hub still shows a Maven card with the count of repositories. Both edit
the same `maven` settings domain. The second place repeats the first, and an operator cannot tell which one is current
for a given task.

The Maven page is also no longer the right place for what it still holds. Besides the repositories, the `maven` domain has
one domain-level option, the resolution cache maximum age in cluster mode. Today that option is edited only on the Maven
page, below the repository link. If the page goes away without a new home, the option becomes uneditable in the
dashboard, although the API still accepts it. The maintainer has decided that `/settings/maven` is no longer needed as a
page, because everything an operator does with it is on the Repositories page. This change does that, and gives the one
domain-level option its place there, so nothing is lost.

Facts checked against the worktree (base: `origin/main` at `db0717b2b`):

- The `maven` domain has two top-level keys: `repositories` (`reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenSettings.kt:47`)
  and `resolutionCacheClusterMaxAgeSeconds` (`MavenSettings.kt:59`). No other top-level key exists.
- The Repositories page exists: `/settings/repositories` and `/settings/repositories/:id` are routes
  (`reposilite-frontend/src/router/routes.js:83-84`), and `components/repositories/RepositoriesView.vue` and
  `RepositoryDetail.vue` render them.
- The old addresses already redirect in the router: `/settings/maven/repositories/<id>` and `/settings/maven?new=repository`
  (`reposilite-frontend/src/router/create.js:62-69`). `/settings/maven` itself does not redirect. The test
  `repositories-routes.test.js:91-97` asserts that it stays.
- The `/settings/maven/repositories` address with no entry matches no route and shows the not-found page, because the entry
  route needs three segments after `settings` (`routes.js:87`).
- The Maven page shows the domain-level fields through one generated form built from the schema
  (`components/settings/SettingsView.vue:164-175`, rendered at `SettingsView.vue:548-557`). The form writes the whole domain
  (`SettingsView.vue:235-238`). The Maven page also shows a link card to the Repositories page (`SettingsView.vue:519-541`).
- The `maven` domain is listed in the Administration tree and the Settings hub under Repository through the constant
  `REPOSITORY_DOMAINS` (`reposilite-frontend/src/navigation/registry.js:151`) and the mapping `SETTINGS_GROUPS`
  (`registry.js:315`). The tree is built by `adminGroups` (`registry.js:273`). The hub is built by `hubGroups`
  (`components/settings/settingsHub.js:39-40`), which takes its domains from `settingsTabDomains`
  (`components/settings/accessDomains.js:42`).
- Operators reach the old page from three places in the dashboard: the getting started checklist
  (`components/dashboard/gettingStarted.js:83`), the public repositories dialog (`components/dashboard/PublicRepositoriesDialog.vue:53`)
  and the file browser (`components/browser/FileList.vue:50`). The first two build the old addresses, the third pushes the
  old entry route.
- The server serves `/settings` and `/settings/<path>` with the dashboard (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/DashboardPaths.kt:41-42`),
  so a redirect in the client needs no server change.
- The settings endpoints are `GET` and `PUT /api/settings/domain/{name}` and `GET /api/settings/schema/{name}`
  (`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/infrastructure/SettingsEndpoints.kt:109`, `:143`,
  `:80`). Both check the settings node, the same node the Repositories page checks.

## What Changes

- **`/settings/maven` redirects to the Repositories page.** The address `/settings/maven`, with or without a query, opens
  `/settings/repositories` with the query kept. `/settings/maven/repositories` with no entry, which is not found today,
  opens `/settings/repositories` too. The existing redirects of the old entry addresses stay as they are. The redirect runs
  in the client, in the router, next to the existing ones.
- **The Maven options live on the Repositories page.** A section headed "Maven options" below the repository table shows
  every domain-level field of the `maven` domain that is not a collection, generated from the served schema, so today that
  is `resolutionCacheClusterMaxAgeSeconds`. It edits the same draft, saves with the save bar and needs the same permission.
  It is not shown when the schema has no such field.
- **The Maven domain leaves the navigation.** It is no longer a card in the Settings hub, a link in the Administration tree
  or a tab of the Settings side navigation. The Repository group of the tree and the hub keeps Repositories, Cleanup policies
  and Javadoc.
- **Links point to the new addresses.** The getting started checklist, the public repositories dialog and the file browser
  link to `/settings/repositories` and `/settings/repositories/<id>` instead of the old addresses. The old addresses keep
  working because of the redirect.
- **The domain stays in the settings model.** The `maven` domain is still loaded, saved, exported, imported and reset with the
  other domains. Only its presentation changes.

**Operators must change nothing.** No configuration key, environment variable, volume, container path, port, manifest or
API endpoint changes. The API and the settings schema are the same, so scripts and plugins that use
`/api/settings/domain/maven` keep working. Bookmarks to `/settings/maven` and `/settings/maven/repositories/<id>` keep
working. This change is not BREAKING and carries no `!` and no `BREAKING CHANGE:` footer.

**Type and scope:** `feat(ui)`. The pull request title is `feat(ui): retire the maven settings page in favour of repositories`.
The change ships only frontend code, tests and documentation, and no file under `reposilite-backend/` changes.

## Capabilities

### Modified Capabilities

- `repositories-admin-page`: the address of the Maven page redirects, and a Maven options section is added to the page.
- `administration-navigation`: the Repository group of the Administration tree no longer lists Maven settings.
- `settings-navigation`: the Settings hub no longer shows the Maven domain, and the domain is edited on the Repositories page.

The requirements named above exist in the pending deltas of `add-repositories-admin-page` and `restructure-navigation`. The
MODIFIED deltas apply to the archived base requirements, so this change follows those two changes (see Dependencies).

## Impact

- **Operators:** no change to the instance. The dashboard shows one place for repositories, the old addresses keep working,
  and the domain-level option is edited under Repositories, then Maven options.
- **Documentation:** `docs/gitbook/reference/dashboard-pages.md` (the Repository group, the redirects, the hub),
  `docs/gitbook/reference/repository-settings.md` (a Maven options section on the Repositories page section),
  `docs/gitbook/reference/settings-domains.md` (the `maven` row and section name where the page is), and the cache
  explanation `docs/gitbook/explanation/storage-and-mirror-caching.md:117`, which names the setting by its domain only.
- **Code:** `reposilite-frontend/src/router/create.js`, `reposilite-frontend/src/navigation/registry.js`,
  `reposilite-frontend/src/components/settings/accessDomains.js`, `settingsHub.js`, `SettingsView.vue`, the Repositories page
  under `components/repositories/`, the three linking files and their tests. A shared helper for the domain form moves out of
  `SettingsView.vue`.

## Verification

- Each group starts with failing Vitest tests that are run and seen to fail before the production code changes.
- `npm test` passes in `reposilite-frontend`. The existing tests that assert the old behaviour (the Maven page as a card,
  tab or domain page, the Maven entry of the Repository group, the old link targets) change only where this proposal changes
  behaviour on purpose, and each such change is listed in the pull request.
- A manual pass with the keyboard alone over the Maven options section, and a check that the value saves and survives a
  reload, recorded in the pull request.
- `openspec validate retire-maven-settings-page --strict` passes, and the dash grep prints nothing.

## Out of Scope

- Any server, API, schema, configuration, environment variable, container or Helm change. `/api/settings/domain/maven`,
  `/api/settings/schema/maven` and `/api/settings/domains` are unchanged. The server already serves every `/settings` path
  with the dashboard, so no server route changes.
- Removing or renaming the `maven` domain, its keys, or the `repositories` list. The domain keeps its name and its
  export and import format.
- Any other settings domain. `javadoc`, `cleanup`, `web`, `frontend` and the others keep their cards and pages.
- A new permission node or a change of who may edit. Nobody gains or loses access.
- Moving more domain-level keys onto the Repositories page than the one that exists. A future key of the `maven` domain
  gets the same generated section without a change here.
- Redirecting the server side `/settings/maven` for non-browser clients. They keep using the API.
- Legal and commercial questions.

## Dependencies

- `add-repositories-admin-page` must be archived first. It creates the `repositories-admin-page` and
  `administration-navigation` requirements that this change modifies, and it already ships the redirects of the entry and
  wizard addresses that this change extends.
- `restructure-navigation` must be archived first. It creates the `settings-navigation` requirements that this change modifies.
- No dependency on `add-repository-policy-fields`, `add-repository-format-spi` or the other repository changes.

## Open Questions

1. **Heading of the section.** Default: "Maven options", the title of the `maven` domain in the schema. Alternative:
   "Repository defaults", which would be wrong for the one key today, because it is a cache setting for all repositories.
2. **`/settings/maven/repositories` with no entry.** Default: redirect to `/settings/repositories`, which today is not found.
   Alternative: keep it not found.
3. **A card for the Maven domain in the hub.** Default: no card. The Repository group of the hub and the tree already name
   Repositories. Alternative: keep a card that links to the page and shows the count.
4. **Where the section sits on the page.** Default: below the table, so the table stays the first thing an operator sees.
   Alternative: above the table.
5. **Hub when only Repositories remains.** Default: the existing forwarding rule decides (`SettingsView.hub.test.js` covers
   the forward for one domain). If a server exposes no other settings domain, `/settings` forwards to Repositories for an
   administrator.

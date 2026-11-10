# Proposal

## Why

An operator who wants to know which repositories an Ingot instance serves, what kind each one is and who may write to it has
no page for that. Repositories are the product, yet the only administrative view of them is a list inside the `maven`
settings domain, reached through Settings, then Maven, then a collection. It shows a title and a summary line per repository.
It does not show the type at a glance, the storage, the visibility or the address a build tool should use, and it has no
place for the two policies that `add-repository-policy-fields` introduces: which kinds of version a repository accepts, and
whether it is read only. An operator who comes from Nexus looks for "Repositories" under an administration menu and does not
find it. An operator who wants to know why a deploy was refused has to open every repository one by one.

The rest of the dashboard has the same problem at a smaller scale. After `restructure-navigation` the rail has four groups
(Work, Monitor, Administration, Troubleshoot) and Administration holds only two entries, Access and Settings, each with its
own side navigation. The pages an administrator uses together are spread over three groups: the audit log sits under
Monitor, the maintenance page under Troubleshoot, and cleanup policies are a Settings domain. The maintainer has decided that
the navigation is regrouped directly into Browse, Search, Dashboard and Administration, with Administration split into
Repository, Security, System and Support, and that every existing address keeps working.

Both changes are needed together. A new Repositories page needs a place in the menu, and the menu needs a first page that
justifies the Repository group.

Facts checked against the worktree (the base is current main, after PRs #76 and #82):

- The registry `reposilite-frontend/src/navigation/registry.js` exists and drives the rail, the bottom bar, the route meta
  and the Access side navigation. `AREAS` (line 65) has four groups, `PAGES` (line 73) lists ten pages, and
  `SETTINGS_GROUPS` (line 145) places the known settings domains in Repository, Instance, Data and Plugins. This change
  builds on it and does not replace it.
- The label of the page at `/` is "Repositories" (`registry.js:74`, set by `restructure-navigation`). That collides with the
  new admin page of the same name, so the browse page is labelled "Browse" from here on. The route name `overview` and the
  paths `/` and `/browse/...` do not change.
- Repositories are created and edited in the generic collection editor of the Settings view. The wizard is opened by "New
  repository" or by `?new=repository` (`components/settings/SettingsView.vue:183-199`), and an entry lives at
  `/settings/maven/repositories/<id>` (`router/routes.js:77-80`, route `settings-entry`). Four places link to that address:
  `RepositoryWizard.vue:298`, `components/dashboard/PublicRepositoriesDialog.vue:53`, `components/browser/FileList.vue:50`
  and `components/dashboard/gettingStarted.js:83`.
- Edits are a draft. Every settings page shares one draft, one save bar, one conflict dialog and one leave guard
  (`SettingsView.vue`, `UnsavedChangesBar`, `ConflictDialog`). A repository edit is saved with the rest of the `maven`
  domain through the settings API.
- The type badge exists (`store/maven/repositoryTypes.js`: `typeBadgeOf`, `settingsTypeBadge`, `LEGACY_HINT`). The server
  derives the effective type of an untyped repository (`reposilite-backend/.../maven/RepositoryType.kt:54`
  `effectiveRepositoryType`) and returns it with `typeExplicit` in `GET /api/maven/repositories`
  (`api/RepositoryListApi.kt`). The settings view does not show a derived type for an untyped entry: `settingsTypeBadge`
  treats a missing `type` as "mixed".
- `GET /api/maven/repositories` returns id, visibility, description, tags, type and typeExplicit. It has no size, format,
  storage or status. The storage kind is in the settings entry (`storageProvider.type`).
- The server already answers `/settings/<path>` and `/access/<path>` with the dashboard (`DashboardPaths.kt`,
  `APP_ROUTES`), and `docs/gitbook/reference/web-routes.md` says dashboard segments are reserved against repository names.
  A page at `/settings/repositories` adds no top-level segment, so neither `DashboardPaths.kt`, `dashboard-segments.json`
  nor the split-dashboard `location` regex changes.
- The accessibility base exists: `components/layout/SkipLink.vue`, `SideNavLayout.vue` with a navigation landmark and
  `aria-current`, and the change `fix-dashboard-a11y-and-caching`. There is no automated accessibility checker in
  `reposilite-frontend/package.json` (no axe).

## What Changes

- **Repositories page at `/settings/repositories`.** A table of every repository in the `maven` domain with the columns
  Name, Type, Visibility, Version policy, Write policy, Storage and Address. Filter by text and tag (the existing
  `parseSearch` rules), sort by any column, and a count that is announced. Create, edit and delete are on the page. It reads
  and writes the same draft of the `maven` domain as today, so it has no second store and no new API. The page is a Settings
  page: one draft, one save bar, one conflict dialog.
- **Detail at `/settings/repositories/<id>`.** The existing entry editor, moved out of the generic collection editor, with
  the type switch, the policy controls and the access card (`RepositoryAccessCard`) it has today.
- **Derived type badge.** The badge states the effective type in words. For an untyped repository it also says "derived" and
  offers "Declare type", which writes `type` with the derived value and changes no behaviour. A derived Mixed type gets a
  warning that suggests splitting it into a group and a proxy, and no declare action. Declaring a type drops empty mirrored
  entries (existing rule, `blankEntriesRemovedBy`), so the action names that count and asks first when it is not zero.
- **Policy fields.** From `add-repository-policy-fields`: a Version policy control (Mixed, Release, Snapshot) and a Write
  policy control (Allow, Allow once, Read only) on hosted repositories. Write policy is not a stored key. Allow is
  `redeployment: true`, Allow once is `redeployment: false` (today's `409`), Read only is `readOnly: true`. The column and
  the control appear only when the schema served by the instance carries the fields; until then they are left out.
- **Delete.** Uses the existing type-the-name confirmation. It warns when the repository is a member of a group and lists
  the groups. Deleting is a draft change like any other and takes effect when saved.
- **Navigation regrouping.** The rail shows Browse, Search, Dashboard and Administration. Administration opens the hub at
  `/settings` and holds four groups: Repository (Repositories, Cleanup policies, Maven and Javadoc settings), Security
  (Users, Machine accounts, Teams, SSO, LDAP, Security settings, Check, Audit log), System (the other settings domains,
  including those a plugin adds) and Support (Console, Maintenance, Analytics). The side navigation of Access and Settings
  pages shows this one tree. The bottom bar is Browse, Search, Dashboard, Administration. The registry gains the groups and
  entries; routes and permission checks stay where they are.
- **Old addresses.** `/settings/maven/repositories/<id>` redirects to `/settings/repositories/<id>`. `/settings/maven` with
  `?new=repository` opens the new page with the wizard. The four internal links above point at the new addresses. Every
  route of today keeps its path and name, as `docs/gitbook/reference/dashboard-pages.md` lists them.
- **Accessibility as a requirement.** The page is operable by keyboard alone, names every control for a screen reader,
  announces results and changes, manages focus in dialogs and after create and delete, and reflows at 320px without a
  two-dimensional scroll. The requirements are in the spec and each has a test.
- **Documentation.** `docs/gitbook/reference/dashboard-pages.md` (routes, labels, groups), a new reference section on the
  Repositories page in `docs/gitbook/reference/repository-settings.md`, the how-tos that say "Settings, then Maven" (`create-hosted-repositories.md`,
  `create-proxy-repositories.md`, `create-a-group-repository.md`, `use-s3-storage.md`, `tutorials/first-steps-with-docker.md`),
  and an accessibility statement section for the dashboard.

### Shipping

Proposal PR `docs(openspec): propose add-repositories-admin-page`, then one code PR, `feat(ui): add a repositories
administration page and regroup the navigation`. All commits of the code PR use the scope `ui` and the types `test`,
`feat` and `docs`, the way `restructure-navigation` shipped. The proposal PR contains only the files under
`openspec/changes/add-repositories-admin-page/` and its tasks stay unchecked.

### Dependencies

- `add-repository-policy-fields` supplies the optional `versionPolicy` and per-repository `readOnly` fields, and the derived
  type. This change shows them when present and does not define them. It can merge before that change: the two columns and
  the two controls are conditional on the schema, and the rest works with today's fields.
- `restructure-navigation` is implemented on main (PRs #76 and #82). This change extends its registry and changes two of its
  decisions on purpose: the label "Repositories" for `/` becomes "Browse", and the rail groups Work, Monitor, Administration
  and Troubleshoot become the four entries above. When both are archived, the requirements of `dashboard-navigation` that
  name the old groups are superseded by `administration-navigation` in this change.
- `add-repository-format-spi` adds a `format` field. A Format column is out of scope here and is added by whoever surfaces
  that field.

### Out of scope

- Any server, API, configuration, environment variable, container or Helm change. No dashboard segment is added.
- Repository size, status and file counts. The list endpoint does not provide them, and a size needs a storage scan. The
  Storage page and its usage numbers are a later item.
- Named storages, routing rules, promotion, tasks, webhooks, backup and the other pages of the target menu. Their entries
  are not drawn until a page exists.
- The "Nexus-style starter layout" action and any change to the default repositories (`releases`, `snapshots`, `private`).
- A plugin UI extension point. Pages of plugins are not added to the tree; a plugin settings domain appears under System as
  it appears today.
- Search across settings and access, the instance health chip, the command palette and the header help menu.
- Making the whole dashboard WCAG conformant. This change sets and tests the requirements for the pages it adds or moves.
- Legal and commercial questions.

## Capabilities

### New Capabilities

- `repositories-admin-page`: the table, the detail, the derived type badge, the policy controls, create and delete, the old
  addresses and the accessibility requirements of the page.
- `administration-navigation`: the four entries of the rail and the bar, the Administration groups and tree, the placement
  of settings domains, address compatibility and the accessibility of the tree.

## Impact

- **Operators:** nothing to change. No configuration key, environment variable, volume, container path, port or manifest
  changes, and the API is unchanged. A deployment pointed at this image sees a new menu, the label "Browse" and a new page.
  Bookmarks and links keep working. This change is not BREAKING and carries no `!` or `BREAKING CHANGE:` footer.
- **Screenshots and documents** that show the old menu or the old place of the wizard become out of date. The gitbook pages
  listed above are updated in the same pull request.
- **Code:** `reposilite-frontend/src/navigation/registry.js`, `router/routes.js`, `router/create.js`, `pages/IndexPage.vue`,
  the Settings and Access views and their side navigation, a new `components/repositories/` folder, the four linking files,
  and their tests. No file under `reposilite-backend/` changes.

## Verification

- Each group starts with a failing Vitest test that is run and seen to fail first.
- `npm test` in `reposilite-frontend` passes. Existing tests that name the old groups or the old address
  (`registry.test.js`, `routeTree.test.js`, `IndexPage.test.js`, `SettingsView*.test.js`, `RepositoryWizard.test.js`)
  change only where this proposal changes behaviour on purpose, and each such change is listed in the pull request.
- A manual pass with the keyboard alone and with one screen reader (NVDA with Firefox, or Orca) over the checklist in the
  tasks, recorded in the pull request. Automated tests cover roles, names, states and focus, not the whole of WCAG.
- `openspec validate add-repositories-admin-page --strict` passes, and the dash grep prints nothing.

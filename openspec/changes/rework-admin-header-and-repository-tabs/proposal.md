# Proposal

## Why

Two requests from the maintainer concern the Administration pages and the page of one repository.

**The layout jumps between entries.** An operator clicks from one Administration entry to the next and the side navigation,
the heading and the content move, sometimes sideways and sometimes down. The cause is in the frame, not in one page
(design D1): the header row above the columns changes height from page to page. An entry with a description, a trail and
a back arrow (below 1240 pixels) is taller than the index or a detail page without a description, so the side navigation
and the content start at a different height each time. Separately, no page reserves the width of a scrollbar, so a page
that becomes taller than the window moves the centred frame sideways by half a scrollbar. The settings page also starts
with the text "Loading configuration" and grows when the configuration arrives, which changes both at once.

**The repository page is one long form.** `/settings/repositories/<name>` draws the type, the write policy, the fields of
the repository, the access card and the delete action in one column. The fields belong to six groups (General, Storage,
Access, Mirror and proxy, Policy, Advanced), but an operator cannot reach one group without scrolling past the others, and
a link to one group is impossible.

The page header is the first request: the title should sit beside the side navigation, aligned with the content column,
not above it. The tabs are the second request.

## What Changes

The change ships in three groups. Each group is one Conventional Commits type and scope, and each has its own code pull
request. The order is fixed: group 1 first, because the jump is a defect and the header of group 2 is built on its
reserved height.

### Group 1: the layout stops jumping (`fix(ui)`)

- The root of the document reserves the scrollbar gutter (`scrollbar-gutter: stable`), so a page that becomes taller than
  the window does not move the frame sideways.
- The title block of the Administration frame reserves its rows on every page: the trail row (with the back arrow below
  1240 pixels) is always drawn at its full height, the heading is one line and truncates with its full name as a title,
  and the description line is reserved whether or not the page has one. The header height is then the same on every
  Administration page at one width, so the side navigation and the content start at the same height on every page.
- The "Loading configuration" text of the settings page keeps the height of the page it becomes, so the arrival of the
  configuration moves nothing that is already on screen. Only the height is reserved; the page content is not faked.

This is the fix that the maintainer asked to see first. Its scope is the stability of positions. Nothing moves to a new
place in this group.

### Group 2: the header sits beside the side navigation (`feat(ui)`)

- From 1240 pixels the page heading, the trail, the description and the page actions form one header row in the page
  column, to the right of the 208 pixel side navigation. The side navigation starts at the same top as the header. The
  content starts below the header, aligned with the header's left edge.
- Below 1240 pixels nothing changes: the header stays above the index, as `rework-administration-layout` specifies.
- The page changes get a subtle enter transition: the page column fades in and rises 4 pixels over 200 milliseconds with the
  standard easing. The side navigation and the header do not animate. Reduced motion removes the transition.

This reverses the "Page header outside the columns" requirement of `rework-administration-layout` at 1240 pixels and wider,
which is why the change is a feature and not a fix.

### Group 3: the repository page is split into tabs (`feat(ui)`)

- `/settings/repositories/<name>` shows a WAI-ARIA tab list under the repository type section: General, Storage, Access,
  Mirror and proxy, Policy and Advanced. The grouping of the fields is in design D5, and a test checks that every field of
  the repository settings is in exactly one tab.
- The active tab is in the address as `?tab=<key>`, so a tab can be linked and reloaded. A missing or unknown key opens
  General. Changing the tab replaces the history entry instead of adding one.
- Tabs are keyboard operable as the WAI-ARIA tabs pattern describes: Left and Right move between tabs, Home and End go to
  the first and last, Tab leaves the list, and the panel is labelled by its tab.
- Unsaved changes are the one draft of the settings, as today. Switching tabs keeps every value, including a value typed in
  a field that has not been blurred. There is still one save action, the save bar of the settings page. A problem in a
  tab that is not open is shown on that tab and in the save bar, and the save bar names it.
- The type section and the delete action stay outside the tabs. The delete action is drawn below the tabs, on every tab.

### Shipping

Proposal PR `docs(openspec): propose rework-admin-header-and-repository-tabs`: only the files under this change, tasks
unchecked, branched from the remote main branch. It is merged before the first code PR is opened.

Code PRs, in this order, each under the title named in the group:

| Order | Type and scope | Pull request title |
| --- | --- | --- |
| 1 | `fix(ui)` | `fix(ui): keep the administration layout still between pages` |
| 2 | `feat(ui)` | `feat(ui): place the administration header beside the side navigation` |
| 3 | `feat(ui)` | `feat(ui): split the repository settings into tabs` |

Group 2 is a `feat` because it reverses a placement that `rework-administration-layout` specified on purpose, and users see
the new place. The motion is part of that pull request and carries no type of its own.

Group 1 is a `fix` because the jump is a visible defect that the existing specifications already expect to be absent: the
side navigation is specified to stay put, and the frame is specified to keep one header row.

### Dependencies

- `fix-surface-hierarchy-and-page-header` must be merged before group 2. It provides `MdPageHeader` and the trail
  (`MdBreadcrumb`) that the header row is built from, and its task 4.6 is the markup that group 2 moves. Group 1 touches the
  same header block, so whichever of the two merges first, the other rebases on it.
- `rework-administration-layout`, `rework-administration-side-navigation` and `add-administration-overview` are archived
  before group 2, because group 2 MODIFIES the requirement "Page header outside the columns" of the first. The same rule
  applies as in `rework-administration-side-navigation`.
- `MdTabs` from `migrate-content-to-components` is the tab primitive of group 3 (design D5 and open question 1). Group 3
  does not start before it is on `main`.
- `add-ui-style-gate` must be merged first: the code PRs pass the gate and add no baseline entry.
- `rework-administration-information-architecture` owns the tree's labels and order. This change does not change them.

### Out of scope

- The Administration tree's content, labels, order and filter (`rework-administration-information-architecture`,
  `add-administration-overview`).
- The 600 and 1240 pixel breakpoints, and any drawer or new navigation pattern.
- The domain pages of the settings (Users, Javadoc and others) and the access domain. Only the repository page gets tabs.
- Changing what a repository field means, its validation, its defaults, or the write policy choices.
- The repositories list (`RepositoriesTable.vue`).
- Legal and commercial questions.

## Capabilities

### New Capabilities

- `repository-settings`: the tab structure of the repository page, its address, its keyboard model, the one draft and save,
  and the field grouping.

### Modified Capabilities

- `administration-layout`: the header position from 1240 pixels (MODIFIED "Page header outside the columns"), and the new
  requirements "Stable layout between pages" and "Page change motion".

## Impact

- **Operators:** nothing to configure. The page heading moves beside the navigation from 1240 pixels. The repository page
  has tabs, and every existing address of a repository still opens it, on General.
- **Configuration and data:** no configuration key, environment variable, deployment manifest, route, permission node or
  stored data changes. The only new address form is the optional `tab` query on `/settings/repositories/<name>`. Nothing
  here is BREAKING.
- **Plugin authors:** the anchors of the frame and the repository page keep their names (`administration-frame`,
  `side-nav-layout`, `repository-detail` with its `header` part). No plugin API changes.
- **Code:** `reposilite-frontend/src/style.css` (scrollbar gutter, reserved rows), `components/layout/AdministrationFrame.vue`
  and `SideNavLayout.vue` (header placement, the page transition key), `styles/md3/shape-elevation-motion.css` (the enter
  animation token), `components/repositories/RepositoryDetail.vue` (tabs, tab state), `components/settings/EntryDetail.vue`
  (one section per tab, the same single entry) and `components/settings/SettingsView.vue` (the outer `role="tabpanel"` of the
  repository page, which has no tab). No file under `reposilite-backend/` changes.
- **Documentation:** `docs/gitbook/reference/repository-settings.md` gets the tab list and the `tab` address in the code PR
  of group 3; `docs/gitbook/reference/dashboard-pages.md` gets the header position in the code PR of group 2.

## Verification

- Every group starts with failing tests that are seen to fail first, on the current build.
- Unit tests: `npm test` in `reposilite-frontend`, targeted while working, run through `heavy`.
- End to end and accessibility, through Playwright (`--workers=2`, through `heavy`): `e2e/layout.spec.js` and
  `e2e/accessibility.spec.js` gain the cases of design D9. They measure the left edge of the side navigation and the top
  edge of the header and the content, and run axe on each tab.
- Screenshots before and after at 320, 600 and 1440 pixels, in light and dark, of the Overview, a group page, the
  repositories list, a repository detail and each repository tab. They go into each code PR, and the images are linked from
  the pull request through the `pr-screenshots` branch.
- `openspec validate rework-admin-header-and-repository-tabs --strict` passes, and the dash grep prints nothing.

## Open maintainer questions

Each question has a default. The tasks implement the default; an answer that differs changes the matching task and nothing
else.

1. **Tab primitive.** Default: wait for `MdTabs` from `migrate-content-to-components` and use it. Alternative: build a
   small tab list in this change and let `migrate-content-to-components` adopt it. The alternative puts a second tab
   component on `main` for a while.
2. **Where the tab key lives.** Default: a query parameter, `?tab=storage`, on the existing route. Alternative: a path
   segment, `/settings/repositories/<name>/storage`. The path segment needs a new route name and redirects for the
   existing address; the query does not.
3. **Activation.** Default: automatic, so the arrow keys open the tab that has focus. Alternative: manual, Enter or Space
   opens it. The data is already in the page, so automatic costs nothing.
4. **Narrow widths.** Default: below 600 pixels the tab list is one row that scrolls sideways inside itself, and the page
   does not scroll sideways. Alternative: a select with the same six choices below 600 pixels, which changes the keyboard
   model of that width.
5. **Empty tabs.** Default: all six tabs show at every repository type. A tab with no field for the type says so in its
   panel. Alternative: a tab with no field for the type is removed.
6. **Delete.** Default: the delete action stays below the tabs on every tab. Alternative: it moves into Advanced, which
   hides a destructive action behind a tab.
7. **Header position below 1240 pixels.** Default: unchanged, the header stays above the index. Alternative: the header
   moves beside the index at 600 pixels, which would leave no room for the back arrow.
8. **Transition.** Default: 200 milliseconds, the standard easing, a 4 pixel rise and no leave transition, so no two pages
   overlap. Alternative: no transition at all, which the maintainer may prefer.
9. **Packaging of the fix.** The team's OpenSpec rules say that a change mixing types is two changes. Default: keep
   group 1 here, because the maintainer asked for it in this change, and say so in its pull request. Alternative: move
   group 1 into its own change, `fix-administration-layout-jump`, with the same tasks.

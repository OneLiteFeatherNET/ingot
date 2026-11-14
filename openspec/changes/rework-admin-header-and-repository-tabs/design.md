# Design

The design refers to the files of `reposilite-frontend/src` on `main`. Line numbers are those of the commit this proposal
branches from. The change builds on `fix-surface-hierarchy-and-page-header` (the `MdPageHeader` and `MdBreadcrumb` markup),
`rework-administration-layout` (the four widths and the header rule it changes), `rework-administration-side-navigation`
(the tree that stays sticky only when it fits) and `unify-dialogs-menus-and-feedback` (the motion tokens it extends).

## D1. What makes the Administration layout jump

The jump has three causes. They are read from the code; the group 1 tests measure them in a browser before the fix.

1. **The header height changes from page to page.** The side navigation and the page sit below the frame's header
   (`AdministrationFrame.vue`, the `header` element). That header is a flex column that holds three things whose presence
   depends on the page:
   - the back arrow and the trail row, drawn only when `showsBack || crumbs.length > 0`. Below 1240 pixels an entry has
     a 48 pixel back arrow (`size-12`); the index has none and no trail. Clicking an index row therefore adds a row above
     the page, and the page moves down by about 48 pixels plus the row gap.
   - the description line, which is `lineDescription`. An entry shows its description, a detail page shows none, and the
     settings hub shows its own. Moving from a list to a repository detail removes one line.
   - the actions, which wrap at 600 to 1239 pixels when there are several (the Overview has three).
   Each change moves the side navigation and the content down or up by the same amount, which is what the eye reads as the
   layout jumping.
2. **No scrollbar gutter.** No rule in `src` sets `scrollbar-gutter` (checked on this branch). The frame is a
   `container mx-auto` section. When a page becomes taller than the window and the browser draws a classic scrollbar (about
   15 pixels on Windows and on Linux desktops without overlay scrollbars), the centred frame moves sideways by half of it.
   Overlay scrollbars hide the effect, which is why it is easy to miss on a Mac.
3. **Content that arrives after the page is drawn.** The settings view draws "Loading configuration" (`SettingsView.vue`,
   the paragraph that shows while there is no domain) and fetches the configuration after mount (`fetchConfiguration`).
   The page is short while it waits and tall when it arrives, so causes 1 and 2 happen once more on every first visit.
   The Overview lead (`SettingsAttention`) is also added after mount when something needs attention.

Cause 1 explains the vertical movement and cause 2 the horizontal one. Both are fixed in group 1, before the header moves in
group 2.

## D2. Where the header sits

From 1240 pixels the header row moves into the page column, beside the side navigation. Below 1240 pixels it stays above the
index, as `rework-administration-layout` specifies.

The DOM order does not change: header, side navigation, page. At 1240 pixels and wider the frame places the three with CSS grid:

- `grid-cols-[208px_minmax(0,1fr)]` (the width of the side navigation of `rework-administration-layout`, and the content
  column beside it);
- the header in column 2, row 1;
- the side navigation in column 1, rows 1 and 2, with `align-self: start`, so its sticky position (`treeFits`) works inside
  its grid area;
- the page in column 2, row 2.

Below 1240 pixels the grid is a single column and the three stack in DOM order, which is the layout of today. One DOM serves
both widths, so screen readers read the heading first at every width and no content is rendered twice.

The header is not sticky. Only the side navigation is sticky, and only while it fits (`useTreeFits`).

## D3. Reserved rows

Every Administration page at one width has the same header height, which is what group 1 asserts:

- the first row (back arrow and trail) is always drawn, with a minimum height of 48 pixels, even when it holds nothing;
- the heading is one line (`truncate`) with its full text as `title`, so a long repository name cannot add a line;
- the description line is always drawn with the line height of `md-body-large`, empty when the page has no description, so
  a description does not add or remove a line;
- the actions row is at least 48 pixels high and does not wrap at 1240 pixels and wider; at 600 to 1239 pixels it may wrap,
  and the test measures the Overview with its three actions, the page most likely to wrap.

`scrollbar-gutter: stable` is set on the root element in `src/style.css`, next to the `html, body` rule of the surface
roles. Under overlay scrollbars the gutter is zero, so nothing changes there.

The "Loading configuration" text of the settings page reserves the height of the first row of the page, so the text does
not collapse when the configuration arrives. The arriving content still changes the page height, which the gutter now
absorbs horizontally. This is accepted as the cost of loading.

## D4. Page change motion

The page column (the default slot of `SideNavLayout`, the `page` part) has an enter animation only. The keyframes and the
token go into `src/styles/md3/shape-elevation-motion.css`, next to the snackbar animations, so the name follows the existing
pattern:

- `--animate-md-page-enter: md-page-enter var(--md-sys-motion-duration-short4) var(--md-sys-motion-easing-standard) both`;
- the keyframe moves from `opacity: 0; transform: translateY(4px)` to `opacity: 1; transform: none`.

The class is applied when the page changes, through a `key` on the page column. The key is the route name plus the key of the
current tree entry (`${route.name}:${current}`), and not the route path or the query:

- a change of entry (`current`) or of route name animates the page;
- a change of the `tab` query (D7) does not remount the page, so the typed value of a field survives it;
- a rename of the repository (`router.replace` with a new id, `RepositoryDetail.vue`, `onChange`) changes the path but not the
  route name or the entry key, so the field keeps its focus while the id is typed.

No leave transition is used. A leaving page is removed at once, so two pages never overlap and no height is taken twice. The
side navigation and the header are outside the keyed element and do not animate.

Reduced motion: `prefers-reduced-motion: reduce` sets `animation: none` on the page column. The existing reduced-motion rule
in `src/style.css` already covers animations of `*`; the test checks the computed `animation-name` under the emulated media.

## D5. The repository tabs and the field grouping

The tab list sits under the type section of `RepositoryDetail.vue`. The type section (the badge, "Declare type") and the write
policy field stay where they are in the grouping below; they are not a tab of their own, except the write policy, which is
Policy.

The fields are the properties of `RepositorySettings` (`reposilite-backend/.../maven/application/MavenSettings.kt`) that the
form draws, plus the two cards the page adds. Each belongs to one tab:

| Tab (key) | Fields and cards |
| --- | --- |
| General (`general`) | `id`, `description`, `tags` |
| Storage (`storage`) | `storageProvider`, `preserveSnapshots` |
| Access (`access`) | `visibility`, the access card (`RepositoryAccessCard`) |
| Mirror and proxy (`mirror`) | `proxied`, `storagePolicy`, `metadataMaxAge`, `protectedGroupPrefixes` |
| Policy (`policy`) | the write policy field (`redeployment`, `readOnly` through its choices), `versionPolicy`, `uploadChecksumPolicy` |
| Advanced (`advanced`) | `resolutionCacheMaxEntries`, `format` |

Outside the tabs: the type section (`type`), and the delete action, which is drawn below the tab panel on every tab
(open question 6). The `id` is in General, but a rename still moves the address (`onChange`), as today.

A repository type hides some fields (`hiddenFormFields`, `EntryDetail.vue`), and the grouping does not change that. A tab
whose fields are all hidden for the type says so in its panel (open question 5).

A test (group 3, 4.2) reads the field names of the repository entry from `src/components/settings/fixtures/maven.schema.json`
and asserts that each name is in exactly one tab, so a new field added to the backend is caught by the test and must be
placed. The test also fails when the table names a field the fixture does not have.

## D6. One draft, one save, and where the state lives

Tabs are views of one entry. The draft is the settings store (`store.setEntry`, the save bar and the leave guard of
`SettingsView.vue`), which is not changed. Nothing is saved per tab.

- One `EntryDetail` instance serves every tab. It gets a `section` prop with the key of the open tab. It draws only the fields
  of that section (through its existing `omit` and `showAccess` props) and keeps the whole entry in its `latest` value. A
  value of a hidden field is kept, so switching tabs never drops it from the saved entry.
- A field that has focus commits its value when the tab changes. The tab list handles the click or key event before it
  changes the tab, so the change is written before the panel is swapped. A test types into a field, switches tab and returns,
  and reads the value.
- Validation runs on the whole entry, as it does today. A problem in a field of another tab is counted on that tab's label
  (for example "Storage, 1 problem") and listed in the save bar. The save bar's link opens the tab of the first problem.
- The one save action is the save bar of the settings page. The tabs do not add a save button.

## D7. The address

The active tab is the query parameter `tab`: `/settings/repositories/<id>?tab=storage`. The keys are the ones of D5.

- No `tab`, or an unknown key, opens General.
- A tab change calls `router.replace` with the same name and params and the new query, so the history gets no entry per tab.
- A rename (`onChange`, `router.replace` with `params`) must keep the query. The current code replaces with the params only,
  which would drop `tab`; group 3 passes the query on, and a test covers a rename on the Storage tab.
- The query is not written for General, so the plain address of a repository stays the one people already have.

## D8. Keyboard model (WAI-ARIA tabs)

The tab list follows the WAI-ARIA Authoring Practices tabs pattern (https://www.w3.org/WAI/ARIA/apg/patterns/tabs/):

- `role="tablist"` with an `aria-label` of "Repository settings sections";
- each tab is a `role="tab"` button with `aria-selected`, `aria-controls` of its panel, and `id`;
- roving tabindex: the selected tab has `tabindex="0"`, the others `-1`;
- Left and Right move to the previous and next tab and wrap at the ends; Home and End go to the first and last;
- the selection follows the focus (automatic activation, open question 3);
- Tab and Shift+Tab leave the list and enter the panel, which is `role="tabpanel"` with `aria-labelledby` of its tab.

The outer `div role="tabpanel"` of `SettingsView.vue` (around `RepositoriesView` and `RepositoryDetail`) has no tab that controls
it on the repository page. Group 3 removes that role for the repository page only; the domain pages keep it, which is out of
scope.

At narrow widths the list is one row that scrolls sideways inside its own box, the selected tab is scrolled into view, and
the page does not scroll sideways (open question 4). The arrow keys still reach every tab.

## D9. Tests and checks

Every test is written first and seen to fail on the current build. Unit tests follow F.I.R.S.T. (no sleeps, no clock, a fresh
router and store per test). Layout is measured in Playwright with `getBoundingClientRect`, with role selectors and no fixed
timeouts.

Group 1 (`fix(ui)`):
- the root element's computed `scrollbar-gutter` is `stable` (unit, reads `style.css`);
- on the Overview, a group page, a list and a detail page at 1440 and 600 pixels, the header height is the same and the side
  navigation's top edge matches the content's top edge, within 0.5 pixel (Playwright);
- the left edge of the frame at 1440 pixels is the same on a short page and on a page with a scrollbar (Playwright).

Group 2 (`feat(ui)`):
- at 1240 pixels and wider the header's left edge equals the page column's left edge, within 0.5 pixel, and its top edge is the
  side navigation's top edge (Playwright);
- below 1240 pixels the header is above the index (Playwright, 1000 pixels);
- the header is not sticky, and the side navigation is the only sticky element (unit and Playwright);
- the page column animates with `md-page-enter` on an entry change, and not on a query change (unit with the key, Playwright);
- under `prefers-reduced-motion: reduce` the page column has `animation-name: none`.

Group 3 (`feat(ui)`):
- the tab list has six tabs in the order of D5, each with the role, `aria-selected`, `aria-controls` and roving tabindex (unit);
- Left, Right, Home and End move the focus and the selection (unit, through the keyboard event);
- `?tab=storage` opens Storage; an unknown key opens General; a tab change does not add a history entry (unit with a memory
  router);
- every repository field is in exactly one tab (unit, schema fixture);
- a value typed on General survives a switch to Storage and back, and a hidden field stays in the saved entry (unit);
- a problem on Storage shows on the Storage label and in the save bar, and the save bar's link opens Storage (unit);
- a rename on Storage keeps `tab=storage` (unit);
- axe reports no violation on each tab at 320, 600 and 1440 pixels (`e2e/accessibility.spec.js`), and the keyboard path of D8 works
  without a pointer (`e2e/accessibility.spec.js`).

Screenshots before and after of each group are taken at 320, 600 and 1440 pixels in light and dark, and attached to the code
pull request.

## Risks

- **Sticky inside the grid.** The side navigation is sticky within its grid area. If a grid row is taller than the navigation,
  the navigation stops at the row's end. The Playwright test at 1280 and 1920 pixels with a tall page covers this; if it fails,
  the navigation is a grid item that spans both rows only.
- **`MdTabs` shape.** The tab list uses the primitive of `migrate-content-to-components`. If its API differs from D8, group 3
  adapts the tab list to the primitive and does not add a second one (open question 1).
- **Remounted fields.** The keyed page column (D4) remounts the page when the entry changes. The draft is in the store, so a
  remount loses only the focus; the leave guard already asks before a change is dropped.
- **Hidden validation.** A tab with a problem is not open when the save fails. The label count and the save bar link (D6)
  are the answer; the test covers it.

## Not in this change

- The Administration tree, its labels and order.
- The repositories list and its table.
- The domain pages of the settings and the access domain.
- Any new colour, breakpoint, or change to the 600 and 1240 pixel breakpoints.

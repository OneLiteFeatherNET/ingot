# Proposal

## Why

On a 27 inch monitor, a 2560 pixel window, the dashboard feels squeezed and most of the screen stays empty. The Administration
pages stop at 1600 pixels, so the side and both margins hold the space the page could use. The space is not given to the
content that needs it. A repository table on that screen has room for every column, yet the columns that matter for a
decision (the address, the storage and the visibility) are hidden, and the status cards stack in one long column while the
right half of the screen shows nothing.

The cap was a sensible first step on a laptop. It was applied to every page, though, and a long sentence in a settings form
reads just as badly at 2560 pixels as a long table does at 1280. The fix is to treat the two kinds of page differently:
lists, tables and the file browser use the width they are given, and text and forms keep a measure that is easy to read.

`rework-administration-layout` (merged in #172) set the content cap at 1280 and 1600 pixels. This change replaces that cap for
the list pages and keeps it, lower, for the reading pages.

## What Changes

- **Two wide breakpoints.** Besides the layout breakpoints of `rework-administration-layout`, the dashboard uses two more: from
  1920 pixels and from 2560 pixels. At 1920 the grids of the dashboard and the overview take two columns; at 2560 they take three.
- **Wide pages use the full width.** The repository table, the file browser listing, the Tokens, Access, Audit and Identity tables,
  and the plugin list use the full width after the side navigation at every width from 1240 pixels. They have no content cap.
- **Reading pages keep a measure.** The settings forms, the Maintenance page text, the account page, the migration steps and the
  getting started text keep a maximum width of about 72 characters, 960 pixels at most, and stay at the start of the content
  column. They do not grow with the window.
- **More columns on wide screens.** The column priority of `rework-administration-layout` hides columns by width. At 1920 pixels
  the Version policy and Visibility columns are shown (they were hidden until 1600), and at 2560 pixels the Storage column is
  shown (it was hidden until 1920). Nothing that is shown today is hidden by this change.
- **Dashboard and overview in columns.** From 1920 pixels the status tiles and the charts of the dashboard sit side by side. The
  overview shows the browse list and the snippet card side by side, the list taking two thirds of the width. Below 1920 pixels both
  pages keep a single column, as today.
- **No change to data, routes or configuration.** The page sets the widths and the grids. It does not fetch more or different data.

Not BREAKING. No configuration key, environment variable, deployment manifest, route or stored data changes. The change adds
layout behaviour, so it ships as a feature.

### Shipping

Proposal PR `docs(openspec): propose rework-default-theme and use-wide-screens`, shared with `rework-default-theme`. Then one
code PR, `feat(ui): use the full width for lists and two grids on wide screens`. Type `feat`, scope `ui`: the change adds columns
and grids that users see on wide monitors. Commits: `test(ui)` for the red component and browser tests, `feat(ui)` for the
classes, the grids and the columns, `docs(ui)` for the reference pages.

### Dependencies

- `rework-administration-layout` (merged, #172): the frame (`AdministrationFrame.vue`), the four widths, the column priority and the
  content cap this change revises. The 1600 pixel cap of that change becomes the cap of the reading pages only.
- `add-dashboard-e2e-tests`: the Playwright suite under `reposilite-frontend/e2e/` that this change extends with cases at 1920 and
  2560 pixels. The cases are added here; the suite's setup is reused.

### Out of scope

- The Settings save bar, the domain forms and the JSON download.
- A maximum width for the whole window, and a user setting for the width. The layout follows the window.
- New data on the dashboard or the overview. The grids show what the pages show today.
- Sticky columns or headers beyond those of `rework-administration-layout`.
- Visual regression screenshots. The end to end cases check the structure and the visible columns, not the pixels.
- Legal and commercial questions.

## Capabilities

### New Capabilities

- `dashboard-wide-layout`: the page types (wide and reading), the wide breakpoints at 1920 and 2560 pixels, the grids of the dashboard
  and the overview, and the column visibility on wide screens.

## Impact

- **Operators on a wide monitor:** the lists use the whole window, more columns are visible, and the dashboard shows its tiles and
  charts in two or three columns. Nothing has to be configured.
- **Operators on a laptop:** nothing changes below 1920 pixels, except that the reading pages keep the measure they have today.
- **Code:** `components/layout/AdministrationFrame.vue` (the cap per page type), `components/repositories/RepositoriesTable.vue`
  (column classes), `components/browser/FileBrowserView.vue` and `FileList.vue` (full width), `components/dashboard/DashboardView.vue`
  (grid), `components/overview/RepositoryOverview.vue` (two-column split), and their component tests. The e2e cases go into
  `reposilite-frontend/e2e/layout.spec.js`. No file under `reposilite-backend/` changes.
- **Documentation:** the layout section of `docs/gitbook/reference/dashboard-pages.md` names the wide and reading page types and the
  two widths.

## Verification

- Each group starts with failing component tests that are seen to fail first. The tests check the width classes of each page type
  (wide, reading, dashboard, overview), the grid classes at 1920 and 2560 pixels, and the column classes of the repository table.
- `npm test` in `reposilite-frontend` passes.
- The Playwright cases at 1920 and 2560 pixels pass in the existing suite. They use roles and accessible names as selectors, and
  they check that the repository table shows its address and storage columns, that the dashboard grid has the stated number of
  columns, and that a settings form stays within 960 pixels.
- `openspec validate use-wide-screens --strict` passes, and the dash grep prints nothing.

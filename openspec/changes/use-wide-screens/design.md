# Design

## Context

Measured on `main`:

- `AdministrationFrame.vue:51` caps the content with `max-w-[1280px] min-[1920px]:max-w-[1600px]`, inside the shared `container`.
  The shared container keeps 16 and 24 pixel gutters, and a window-margins test forbids extra padding on it, so the gutter stays
  where it is and only the cap changes.
- The side tree is 208 pixels from 1240 pixels, and the rail is 80 pixels. At 2560 pixels the content box is therefore about
  2270 pixels before the cap, and the cap holds it to 1600.
- The repository table (`RepositoriesTable.vue`) hides Address, Write policy, Version policy, Visibility and Storage by the column
  priority of `rework-administration-layout`. At 2560 pixels the hidden columns have room.
- `DashboardView.vue` and `RepositoryOverview.vue` draw one column. The status tiles, the charts and the snippet card are stacked.
- `FileBrowserView.vue` and `FileList.vue` draw the file listing with the same cap as the frame.

## Decisions

### D1. Two kinds of page

| Kind | Pages | Width rule |
| --- | --- | --- |
| Wide (lists and tables) | Repositories, the file browser listing, Tokens, Access, Audit, Identity, plugin list | full width after the side navigation, no cap |
| Dashboard (grid of tiles and charts) | Dashboard | full width, grid columns by breakpoint (D3) |
| Hybrid (list beside a card) | Repository overview: browse list and snippet card | full width, two columns from 1920 pixels (D3) |
| Reading (text and forms) | Settings domains, Maintenance text, Account, migration steps, Getting started | maximum 960 pixels, about 72 characters, at the start of the content column |

A page is wide when its main content is a collection that a reader scans across: rows with several columns. It is reading when its
main content is a sentence or a form that a reader fills in top to bottom. A page with a list and a form (for example a repository
with its settings) is classified by its main content, and the form stays at the reading measure.

### D2. Breakpoints

Two new breakpoints join the four widths of `rework-administration-layout`:

| Width | Wide pages | Reading pages | Dashboard and overview |
| --- | --- | --- | --- |
| below 1920 px | full width after the side navigation | 960 px maximum | one column, as today |
| 1920 to 2559 px | full width | 960 px maximum | dashboard tiles and charts in 2 columns; overview split 2 to 1 |
| 2560 px and wider | full width | 960 px maximum | dashboard in 3 columns; overview split 2 to 1 |

Why 1920: at 1920 the content after the rail, the tree and the gutters is about 1500 pixels, enough for the seven table columns that D4
shows at that width, with no mid-word break. Why 2560: at that width each dashboard column stays above 600 pixels, which keeps a chart legible in a third column.
Below 1920 a second column would make the tiles narrower than their labels, so the single column stays.

The old cap of 1600 pixels at 1920 and wider (`rework-administration-layout`) is removed for wide pages and kept only for the
reading pages, at the smaller 960 pixel measure.

### D3. Grids

- Dashboard: the status tiles and the charts use CSS grid with `grid-cols-1`, then `min-[1920px]:grid-cols-2`, then
  `min-[2560px]:grid-cols-3`. A chart takes one cell. The instance status panel spans the full row.
- Overview: a two-column split, `min-[1920px]:grid-cols-[2fr_1fr]`, with the browse list in the wider column and the snippet card in
  the narrower one. Below 1920 the snippet card stays under the list, as today.
- No grid changes the order of the content in the DOM, so the reading order and the tab order are the same at every width.

### D4. Column visibility on wide screens

The priority of `rework-administration-layout` (D5 there) is kept as the order in which columns are hidden. Its thresholds move for wide
screens only:

| Column | Today | After this change |
| --- | --- | --- |
| Name, Actions, Type | always | always |
| Address, Write policy | 1240 px | 1240 px (no change) |
| Version policy, Visibility | 1600 px | 1920 px |
| Storage | 1920 px | 2560 px |

Nothing is hidden at a width where it is shown today. The change only shows the columns earlier on wide screens, and the order of the
hiding is unchanged below 1920.

### D5. Tests

- Component tests (Vitest, structure and classes only, as in `rework-administration-layout`): the width class of each page kind (wide has
  no `max-w` class; reading has `max-w-[960px]`), the grid classes of the dashboard and the overview at each breakpoint, and the column
  classes of the repository table.
- Browser end to end cases (Playwright, `reposilite-frontend/e2e/layout.spec.js`), run at 1920 and 2560 pixels. Selectors are roles and
  accessible names. They assert:
  - the repository table has the "Address" and "Storage" columns at 2560 pixels, and the "Storage" column is absent at 1920;
  - the dashboard has two tile columns at 1920 and three at 2560, read from the bounding boxes of two tiles;
  - a settings form's main region is at most 960 pixels wide at both widths;
  - the overview's snippet card sits beside the browse list at 1920 pixels.
- The e2e cases measure boxes and roles, not pixel-perfect images. A screenshot comparison is not part of this change.

## Alternatives considered

- **Remove the cap everywhere.** Rejected: it makes settings forms and prose lines unreadable at 2560 pixels, which is the reason the cap
  exists.
- **A user setting for the width.** Rejected for now: it adds a preference and a storage key for a layout that should work by default.
  It can be added later without changing the breakpoints.
- **A maximum width for the whole window (for example 2000 pixels, centred).** Rejected: it keeps the empty space the maintainer complained
  about. The space is used by the wide pages instead.
- **Three columns in the overview at 2560.** Rejected: the snippet card and the browse list are enough, and a third column would hold a
  summary with nothing to fill it.

## Risks

- **Seven columns at 1920 pixels and eight at 2560 are dense.** Mitigated by the row height staying as it is, the truncation of names and addresses from
  `rework-administration-layout`, and the detail page that shows every value.
- **A reading page is classified wrongly.** The classification is a table (D1) and each page's class is named in its component test, so a
  change of class is a visible test change.
- **Browser cases are slow.** They run in the existing suite, at the two widths only, and reuse its setup. They do not add a new browser
  job.

## Open questions

1. Breakpoint 2560 px for the third dashboard column and the Storage column. Default: 2560. The alternative is 2200 px, which gives three
   columns to a 24 inch screen at its full width.
2. Reading measure 960 px. Default: 960 px, about 72 characters. The alternative is 880 px, which keeps prose shorter at the cost of more
   wrapping in the settings tables.
3. Overview split 2 to 1 from 1920 px. Default: yes. The alternative keeps the overview in one column until 2560 px.
4. Whether the Maintenance page is a reading page. Default: yes, because its main content is text and a switch. The alternative makes it a
   wide page once it lists the instances.

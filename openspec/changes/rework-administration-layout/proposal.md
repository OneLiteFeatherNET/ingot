# Proposal

## Why

An operator opens the Repositories page on a wide monitor and finds a layout that works for neither a phone nor a
desktop. Three columns (the navigation rail, the Administration side tree and the content) leave the content too narrow.
Repository names break in the middle of a word, and so do the addresses. The "Write policy" header wraps, and each row's
Edit, Copy address and Delete buttons wrap onto two lines. The side tree sits tight against the rail. Scrolling moves the
page heading and its buttons under the top bar, so the page looks cut off after a small scroll.

These are not separate defects. The Administration pages have no designed layout for the widths between a phone and a wide
desktop: the side tree has one fixed width, the content has one container cap, the header scrolls with the page, and the
table has no rule for what to shorten or hide first. This change designs that layout once, and the Administration pages
use it.

`fix-administration-navigation` fixes the tree, the keys, the current entry, the heading and the rail label. This change
builds on its frame and does not repeat those fixes.

## What Changes

- **One layout with breakpoints.** The Administration pages use one layout at four widths: below 600 pixels (bottom bar,
  side tree as an index), 600 to 1239 pixels (rail, side tree as an index with a back arrow), 1240 pixels and wider (rail,
  side tree beside the page at 208 pixels), and 1920 pixels and wider (content up to 1600 pixels). The defaults are listed
  as open questions in the design.
- **Page header outside the columns.** The page heading and the page actions sit in one header row above the columns. The
  header is not sticky: it scrolls away. The side tree is the only sticky column, and it stops clear of the top bar and the
  save bar.
- **Names and addresses never break mid-word.** A repository name truncates with an ellipsis and carries its full name as
  its title and accessible name. An address truncates the same way and keeps its copy action beside it.
- **One Actions menu per row.** Edit, Copy address and Delete collapse into one "Actions" menu. The menu items keep the
  accessible names "Edit releases", "Copy address of releases" and "Delete releases".
- **Column priority.** Columns hide at narrower widths in a fixed order. Name and Actions never hide. Their values stay on the
  detail page.

Not BREAKING. No configuration key, environment variable, deployment manifest, route or stored data changes. The change adds
a menu pattern and column priority, so it ships as a feature.

### Shipping

Proposal PR `docs(openspec): propose rework-administration-layout`, after `fix-administration-navigation` is merged. Then one
code PR, `feat(ui): add a responsive administration layout with an actions menu`. Type `feat`, scope `ui`: the change adds a
menu pattern and column priority that users see. The breakpoints and truncation alone would have been a `fix`, so the
feature type is the one that names the change. Commits: `test(ui)` for the red tests, `feat(ui)` for the layout and the menu,
`docs(ui)` for the reference pages.

### Dependencies

- `fix-administration-navigation`: the frame (`AdministrationFrame.vue`), the single heading and the rail label. This change
  changes the layout of that frame and must be merged after it.
- `add-repositories-admin-page`: the Repositories table and its row actions (`RepositoriesTable.vue`).

### Out of scope

- The tree, the keys, the current entry, the breadcrumb and the Maintenance page (`fix-administration-navigation`).
- A drawer or a second navigation pattern beyond the index that exists today.
- Visual checks at 1280 and 1920 pixels. They are covered by `add-dashboard-e2e-tests` (browser end to end tests), and this
  change does not depend on it.
- Changing the Settings save bar, the domain forms or the JSON download.
- Legal and commercial questions.

## Capabilities

### New Capabilities

- `administration-layout`: the breakpoints, the page header, the sticky side tree, the content width, the truncation of
  names and addresses, the row Actions menu and the column priority of wide tables.

## Impact

- **Operators:** nothing to change. A desktop operator sees a wider table, one Actions menu per row and a header that scrolls
  away. A phone operator sees the same index as today.
- **Code:** `components/layout/AdministrationFrame.vue` and `SideNavLayout.vue` (widths, sticky), `components/repositories/
  RepositoriesTable.vue` (truncation, Actions, column classes), a new `components/md/MdOverflowMenu.vue`, and their tests. No
  file under `reposilite-backend/` changes.
- **Documentation:** the layout section of `docs/gitbook/reference/dashboard-pages.md` and the Repositories section of
  `docs/gitbook/reference/repository-settings.md` name the row actions; both are updated in the code PR.

## Verification

- Each group starts with a failing Vitest test that is seen to fail first. The tests check structure and classes: the
  breakpoint classes of the frame, the sticky class of the side tree and of no other element, the truncation classes and the
  `title` of names, the Actions menu and its names, and the priority classes of the columns.
- `npm test` in `reposilite-frontend` passes.
- The layout at 1280 and 1920 pixels is checked by `add-dashboard-e2e-tests`, not by this change's unit tests.
- `openspec validate rework-administration-layout --strict` passes, and the dash grep prints nothing.

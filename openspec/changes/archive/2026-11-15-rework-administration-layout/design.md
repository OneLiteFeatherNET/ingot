# Design

## Context

Measured in the code on this worktree:

- The side tree is `SideNavLayout.vue`, rendered with `w-56` (224 pixels) and `gap-10` beside the page. Its layout is a flex
  row (`md:flex-row`) from 768 pixels, so the tree competes with the content from that width on.
- The rail is `MdNavigationRail.vue:91` (`w-20`, 80 pixels), fixed at the left (`IndexPage.vue:525`), and the page is moved
  right by `min-[600px]:pl-20` (`IndexPage.vue:297`).
- The top bar is `DefaultHeader.vue:30` (`sticky top-0 z-30`). The Settings header is not sticky. Content scrolls under the
  top bar, which is correct, but the header is then covered after a small scroll, and nothing gives it room to leave cleanly.
- The Settings save bar is sticky at the bottom (`SettingsView.vue:620`), and its clearance is measured.
- The repositories table is `RepositoriesTable.vue`: `overflow-x-auto` around a `min-w-[40rem]` table. The name link has
  `break-all` (`RepositoriesTable.vue:193`) and the address `break-all` (`:236`), which is the mid-word break. The row actions
  are three buttons with `flex-wrap` (`:238-270`), which is the two-line Actions cell.
- There is no menu component under `components/md/`. An Actions menu is new.

## Decisions

### D1. One layout, four widths

| Width | Rail | Side tree | Page header | Content |
| --- | --- | --- | --- | --- |
| below 600 px | none (bottom bar) | index, back arrow on each page | heading, then description, then actions wrapped under it | full width, 16 px gutter |
| 600 to 1239 px | 80 px rail | index, back arrow on each page | one row: heading and description left, actions right | full width after the rail, 24 px gutter |
| 1240 to 1919 px | 80 px rail | beside the page, 208 px, sticky | one row above both columns | content, 24 px gutter, up to 1280 px |
| 1920 px and wider | 80 px rail | beside the page, 208 px, sticky | one row above both columns | content, 32 px gutter, up to 1600 px |

Why 1240: at this width the content keeps about 900 pixels after the rail and the tree. That is enough for the table with
the priority columns of D5 (Name, Type, Address and Actions), with no mid-word break. Below 1240 the tree is an index, so it
does not take a column. The index is the one that exists today (`narrow`, `SideNavLayout.vue`), so no new drawer is added.

Why 1600 as the cap and not 1280: at 1920 pixels a 1280 cap leaves about 300 pixels unused on each side after the rail and the
tree, which reads as too narrow. A 1600 cap keeps line length for prose and gives the table its columns.

The breakpoints are Tailwind's `min-[600px]`, `min-[1240px]` and `min-[1920px]` variants, so the existing rail breakpoint
(600) does not change. The index behaviour below 1240 is reused, so `narrow` in `SideNavLayout` takes the value of a 1240
query instead of 600 for the side tree, and the rail keeps 600.

### D2. Page header outside the columns, not sticky

The heading and the page actions are one row above the side tree and the content, in `AdministrationFrame.vue` (from
`fix-administration-navigation`). The row is not sticky. On a scroll the header leaves with the page, so no content hides
under the top bar in a way the operator cannot undo. The top bar itself stays sticky (`DefaultHeader.vue:30`, opaque, z-30),
and focus targets get `scroll-margin-top` equal to the top bar height, so a keyboard focus is never placed under it.

Below 600 pixels the actions wrap under the description, as the Settings header does today.

### D3. Sticky side tree, no overlap

Only the side tree is sticky, from 1240 pixels. Its top is the top bar height plus 16 px (`top: calc(4rem + 1rem)`), and its
maximum height is the viewport minus that top and 16 px at the bottom (`max-height: calc(100dvh - 5rem)`), with its own
scroll when the tree is taller. Its bottom edge is never under the save bar: the save bar is sticky at the bottom
(`SettingsView.vue:620`) and the page clearance keeps the last row above it, so the tree stops above the bar when the page
is scrolled to the end. The sticky element is the `nav` only, and no other element on the page is sticky except the top
bar and the save bar.

### D4. Names and addresses

- A repository name is shown with `truncate` (single line, ellipsis) inside a column whose minimum width is 12 rem. The link
  keeps its full name as its text and in `title`, so the accessible name is the full name. `break-all` is removed.
- An address is a monospace code element with `truncate` and `title` set to the full address, with the Copy action in the
  Actions menu (D6) and the same name, "Copy address of releases". The address is not an interactive element on its own, so
  it does not need a second copy button.
- No cell breaks a word. The Write policy header keeps one line (`whitespace-nowrap`) by making its column wide enough for
  its label at the widths where it is shown.

### D5. Column priority

Columns hide in a fixed order when the table cannot hold them. Priority from highest (kept longest) to lowest (hidden first):

| Priority | Column | Shown from |
| --- | --- | --- |
| 1 | Name | always |
| 2 | Actions | always |
| 3 | Type | always |
| 4 | Address | 1240 px |
| 5 | Write policy | 1240 px (only when the schema has the field) |
| 6 | Version policy | 1600 px (only when the schema has the field) |
| 7 | Visibility | 1600 px |
| 8 | Storage | 1920 px |

A hidden column is removed from the table with a responsive class, so its value is not drawn. Hidden values stay on the
detail page, which shows every field. Open question 4 asks whether hidden values should also appear as a line under the name.

### D6. Row Actions menu

The three buttons become one `MdOverflowMenu` component (new, `components/md/MdOverflowMenu.vue`):

- A button with the name "Actions for releases" (the row's name), `aria-haspopup="menu"` and `aria-expanded`.
- The menu items are the three existing actions with their existing accessible names: "Edit releases", "Copy address of
  releases", "Delete releases" (the names of today's `aria-label`s, so screen reader users hear what they heard before).
- Keyboard: Enter or Space opens the menu and focuses the first item; arrow keys move between items; Escape closes it and
  returns focus to the Actions button; Tab leaves it. Delete keeps its confirmation dialog.
- The menu is always used, at every width, so each row has one pattern. Open question 3 asks whether wide widths should show
  Edit inline.

### D7. What is tested where

Vitest checks structure and classes only: the breakpoint classes of the frame, the sticky class on the side tree and on no
other element, the truncation classes and `title`, the Actions menu and its item names, the keyboard behaviour of the
menu, and the priority classes of the columns. The visual result at 1280 and 1920 pixels is checked by
`add-dashboard-e2e-tests`. Nothing here claims a visual result from a unit test.

## Open questions

1. Breakpoint 1240 px for the side tree, with the index below it. Default: 1240. The alternative is 1360 px, which keeps more
   room for the table but puts the tree beside the page on fewer laptops.
2. Content cap 1600 px at 1920 and wider. Default: 1600. The alternative is no cap, which makes long prose lines hard to read
   on the Settings forms.
3. Actions menu at every width. Default: yes, one pattern. The alternative keeps Edit inline from 1600 px.
4. Hidden columns. Default: their values are on the detail page only. The alternative shows them as a line under the name
   below the breakpoint, which keeps the information but takes row height.
5. Header row. Default: the page header is not sticky, and the top bar stays sticky. The alternative makes the header sticky
   with a compact title, which keeps the actions reachable at the cost of a second bar.
6. Truncation of names. Default: an ellipsis with the full name in `title` and the accessible name. The alternative is a
   horizontal scroll inside the table region, which keeps every character visible but moves the row out of view on a narrow
   screen.

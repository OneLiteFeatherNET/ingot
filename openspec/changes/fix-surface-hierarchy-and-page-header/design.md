# Design

## Context

Measured on `main` in `reposilite-frontend/`, with the references checked against the source of this branch:

- **Surfaces.** `src/style.css:82-88` sets the page to `bg-surface` on `html` and `body`, and `src/App.vue:53` uses `bg-surface`
  for the app wrapper. `src/pages/IndexPage.vue:326` gives the frame (rail and top bar) `bg-surface-container` from 600 pixels.
  The content sheet is `bg-surface` with no edge (`IndexPage.vue:359-360`). `src/components/md/MdCard.vue:31-35` maps `filled` to
  `bg-surface-container-highest`, which contradicts the card rule in `DESIGN.md` (cards use `surface-container-low`), and
  `outlined` to `bg-surface` with `border-outline-variant`, which equals the page.
- **Measured contrast** (WCAG relative luminance, computed from the token values in `src/styles/md3/color.css`):

  | Pair | Light | Dark |
  | --- | --- | --- |
  | `surface` against `surface-container` (page against frame, and content sheet against frame) | 1.11:1 | 1.13:1 |
  | `surface-container-low` against `surface` (card against page) | 1.05:1 | 1.08:1 |
  | `surface-container-low` against `surface-container` | 1.05:1 | 1.05:1 |
  | `surface-container-high` against `surface-container` | 1.06:1 | 1.15:1 |
  | `outline-variant` against `surface` | 1.62:1 | 1.97:1 |
  | `outline-variant` against `surface-container-low` | 1.54:1 | 1.83:1 |
  | `outline-variant` against `surface-container` | 1.46:1 | 1.75:1 |
  | `outline` against `surface` | 4.26:1 | 5.83:1 |
  | `outline` against `surface-container-low` | 4.05:1 | 5.39:1 |
  | `outline` against `surface-container` | 3.84:1 | 5.16:1 |
  | dark `surface-dim` against `surface` (today, equal) | n/a | 1.00:1 |

  The high contrast set (`prefers-contrast: more`) strengthens `outline` (`color.css:176` and `:213`). It is measured again in
  the tasks, with the values recorded in the pull request, because this design does not compute it.
- **Page containers.** `src/components/layout/AdministrationFrame.vue:103` has two variants. The reading variant
  (`container max-w-[960px] pb-12 pt-7`) has no `mx-auto`, and the wide one has it. `src/components/account/AccountView.vue:43` has
  the same missing `mx-auto`. Hand-written page containers also sit in `DefaultHeader.vue:37`, `DashboardView.vue:37`,
  `SearchView.vue:250` (no `max-w-none`), `FileBrowserView.vue:208`, `RepositoryOverview.vue:202` (`mb-12` where the rest use
  `pb-12`), `HeaderHero.vue:42`, `SiteFooter.vue:31` (no `mx-auto`), `NotFoundView.vue:23`, `ViewLoadError.vue:27`,
  `ViewLoading.vue:23`, `IndexPage.vue:378` and `:399`. `AnalyticsView.vue` has none.
- **Repository detail.** `src/components/settings/SettingsView.vue:473` renders the repository detail as `reading`, with the title at
  `:464`. `src/components/repositories/RepositoryDetail.vue:200-212` draws a second header (`data-ingot-part="header"`, registered
  in `src/anchors/registry.json` for `repository-detail`) and an `h2` with the repository name, which sits inside the page column of
  `src/components/layout/SideNavLayout.vue`. That column starts after the `w-52` navigation and the `gap-10` (`SideNavLayout.vue:71`
  and `:76`), about 248 pixels right of the frame's `h1` at 1240 pixels and wider. The "Back to repositories" text button
  (`RepositoryDetail.vue:190-198`) insets its label, so the label and the title do not line up.
- **Breadcrumbs.** `AdministrationFrame.vue:98` defines the separator as `after:px-2 after:content-['/']` on each `li`. The non-link
  crumb is `span.block truncate` (`:170-173`). The list is `flex-wrap` above 1240 pixels and `whitespace-nowrap` below it
  (`:148-150`). The back arrow (`:113-141`, `size-12`) shares its row with the trail (`:109-112`). The file browser trail,
  `src/components/browser/BreadcrumbNavigation.vue`, already draws a real `aria-hidden` separator (`:61-66`), but it uses `gap-y-1`
  on the `nav` while the wrapping happens in the `ol` (`:52` and `:55`), its current crumb has no `min-h-12` while its links do
  (`:69` and `:77`), and its parent button has `ml-1` (`:94`).
- **Padding and inset of the detail panes.** `src/components/access/TeamsPanel.vue:196`, `PrincipalsPanel.vue:255` and
  `src/components/settings/CollectionEditor.vue:161` take `min-[840px]:p-6` and nothing below 840 pixels. `access/TeamDetail.vue:207` and
  `settings/EntryDetail.vue:280-295` (the back button, hidden from 840 pixels) complete the picture.
- **Existing tests that touch these elements.** `e2e/layout.spec.js:35` reads the Repositories heading as level 2. The
  `data-test="entry-title"` attribute is on `RepositoryDetail.vue:208` and on `settings/EntryDetail.vue:294`, and
  `src/components/settings/SettingsView.entries.test.js:192-241` reads it for settings entries. Both carriers change with this design
  and the tests are listed in the tasks.
- **Missing primitives.** On this branch `src/components/md/` has no `MdPage`, `MdPageHeader`, `MdBreadcrumb` or `MdListDetail`. They
  are provided by `add-md3-component-catalogue`, which this change depends on.
- **Dark surface-dim.** `src/styles/md3/color.css:114` sets `--md-sys-color-surface-dim` to `#0d141c`, the value of `surface`.
  `src/theme/runtime.js:108` maps `surface-dim` to the generator's `surfaceDim` for an operator seed.

## Goals

- Every page is built from `MdPage`, `MdPageHeader` and `MdBreadcrumb`, and no page writes its own container or heading.
- Each surface has one role, documented in `DESIGN.md`, and every primitive uses that role.
- The title of a page, its description and its trail start at one left edge, with or without a back control, at every width.
- The administration trail and the file browser trail are one component with one shape.

## Non-goals

- Changing the content of the tree, its labels or its order.
- Dialog, menu, list, table or form migration.
- New breakpoints, a new colour or a tonal elevation scheme for dark mode.

## Decisions

### D1. One surface table

`DESIGN.md` gets a table with one row per surface, and the primitives and views follow it. The rows, with the role and the edge:

| Surface | Role | Edge | Elevation |
| --- | --- | --- | --- |
| Page (behind everything) | `surface` | none | none |
| Frame: top bar, navigation rail | `surface-container` | none, the content sheet is the edge | none |
| Content sheet | `surface` | 1 pixel `outline` (question 1) | none |
| Card (`MdCard` default) | `surface-container-low` | 1 pixel `outline` | none |
| Card, outlined variant | `surface` | 1 pixel `outline` | none |
| Card, elevated variant | `surface-container-low` | none | `elevation-1` |
| Block nested in a card | one role above the card: `surface-container` | none | none |
| Block nested in that block | `surface-container-high` | none | none |
| Menu, select listbox | `surface-container` | none | `elevation-2` |
| Side sheet (modal) | `surface-container-low` | none | `elevation-1` |
| Dialog | `surface-container-high` | none | `elevation-3` |
| Floating panel (More panel, snackbar) | the role of its kind | none | always, at least `elevation-2` |

Nested blocks step up exactly one role from their parent and never repeat it or step down. The deepest allowed level is
`surface-container-high`; a fourth step is rejected in review. Existing inverted nesting
(`maintenance/CleanupPoliciesPanel.vue:175` and `:228`, `maintenance/IndexPanel.vue:170` and `:319`) and the same-step nesting
(`audit/AuditEventRow.vue:113` in `audit/AuditView.vue:149`, measured at 1.00:1) are fixed in the table's terms.

Outline is the role of an edge that carries meaning (a card, a field, the content sheet). `outline-variant` stays the role of
decorative dividers inside a surface. Its measured values (1.46 to 1.97:1) are below the 3:1 that a visible boundary needs, so it
is not used for any edge.

### D2. The page shell: `MdPage`

`MdPage` renders the page container, and the gutter stays the shared `container` class (`src/style.css:164-175`, which `window-margins.test.js`
guards). Its variants:

- `wide` (default): `container mx-auto max-w-none` with the page's top and bottom padding. Lists, tables, the dashboard, the file
  browser and Overview draw it.
- `reading`: `container mx-auto max-w-[960px]`. Text, forms and detail panes draw it. The column is centred (question 2).

No view writes `container` itself, and no view writes `mx-auto` or `max-w-*` on the page. `MdPage` owns the top and bottom spacing so
that `pb-12` and `mb-12` cannot disagree. A test reads the sources of the views and fails on a `container` class outside `MdPage`
(the same pattern as the gutter test in `window-margins.test.js`).

### D3. The page header: `MdPageHeader`

The header has four regions, and the first is outside the text column:

```
header (flex column)
  back row        the back control, when the page has one; its own row above the text column
  columns row     flex column below 600 pixels, a row from 600 pixels
    text column   trail, the one h1, the description
    actions       buttons, shortcuts and the Sections button
```

The back control is alone in its row. Its 48 pixel target is pulled left by 12 pixels (`-ms-3`) so that its 24 pixel glyph starts at
the container edge, which keeps the target inside the 16 pixel gutter and the glyph on the same edge as the title. A detail
page draws its back control at every width, because it is the way to its list page; a list page draws one below 1240 pixels, as it
does today. The text column never moves when the back control appears or disappears. `settings/EntryDetail.vue:272-295` gets the same rule:
its back button (hidden from 840 pixels today, which makes the `h3` jump) moves into its own row above the `h3`, with the same
offset, so the `h3` starts at one edge at every width.

The `h1` is the only heading of level one on a page. On a page with a repository name, the name is the `h1`. The repository type
section stays as an `h2` (`RepositoryDetail.vue:218`). On the repository detail, `data-test="entry-title"` moves with the name to the
`h1`. On `EntryDetail` it stays on the `h3`, which is an entry heading under the frame's `h1`, not a page heading.

### D4. One trail: `MdBreadcrumb`

`MdBreadcrumb` renders a `nav` with an `ol`, and each crumb is an `li` with one of two children: a link or the current text. The
separator is the first child of every `li` after the first, a `span` with `aria-hidden="true"` and `shrink-0`, drawn with `px-2`. No
crumb uses `after:` or `before:` for its separator. The current crumb carries `aria-current="page"`.

The Administration trail and the file browser trail both use it:

- The administration trail is the section (a link to the index), the group heading (text, or a link on a detail page to the list
  page), and on a detail page the current entry as text. The `nav` keeps the anchor `administration-breadcrumb`.
- The file browser trail (`BreadcrumbNavigation.vue`) keeps the anchor `breadcrumb-navigation` on its `nav`. Its parent directory
  button stays beside the trail as a separate action, outside `MdBreadcrumb`.
- A page whose entry has no group heading draws the section name as the current crumb. Overview draws the section name as the
  current crumb too, so the two cases use one shape.
- A trail is a flex row, the crumbs wrap by `li`, never inside one, and a long crumb truncates with `min-w-0 truncate`. No crumb
  wraps its separator onto a line of its own.
- Below 1240 pixels the trail draws its last crumb only. The middle crumbs collapse to nothing, and the full trail is the `title`
  of that crumb, so the collapse loses no information for a pointer user. The accessible name of the `nav` stays "Breadcrumb".

### D5. The repository detail title

`RepositoryDetail.vue` drops its own `header` block with the `h2` (`:200-212`) and passes the repository name to the frame as its title,
so the frame's `h1` is the name. The `data-ingot-part="header"` attribute and `data-ingot-repository` move to the element that holds
the repository type section (`:214-244`). That element sits in the page content, not in the page header, so the anchor name stays and
its place changes, which is the fix. The "Back to repositories" text button (`:190-198`) becomes the back control of the header,
with an `aria-label` of "Back to repositories". Its label is no longer a visible text button, so the 24 pixel inset that misaligned the
title goes with it. The `heading` ref and its focus behaviour keep working on the frame's `h1`.

Because the repository detail is a `reading` page (`SettingsView.vue:473`), the name is centred with the rest of the page at widths
where the side navigation is not shown.

### D6. Detail panes share one inset

The detail panes of the list-detail layout (`TeamsPanel.vue:196`, `PrincipalsPanel.vue:255`, `CollectionEditor.vue:161`) and their
detail components (`TeamDetail.vue:207`, `PrincipalDetail.vue`) take the same padding at every width through `MdListDetail`, which gives
both panes one inset. The `min-[840px]:p-6` and `max-[839px]:hidden` classes move into the primitive. The list pane and the detail
pane start at the same left edge below 840 pixels, so the title of the detail is not flush against the window edge.

### D7. Card default

`MdCard` gets the surface table's card rows. `filled` (the default) becomes `surface-container-low` with a 1 pixel `outline` border, and
`outlined` becomes `surface` with the same border. `elevated` keeps `surface-container-low` and `shadow-elevation-1`, without a border.
`MdCard.test.js` asserts the default classes, and the old assertion that the default is `bg-surface-container-highest` is removed. The
hand-written cards that match the old default (`account/ProfileCard.vue:53`, `account/PasswordCard.vue:95`, `access/TeamDetail.vue:251`,
`access/PrincipalDetail.vue:172`, `access/LegacyGrantsPanel.vue:37`, `identity/TokenList.vue:151`, `audit/AuditView.vue:149`,
`overview/RepositoryOverview.vue:372`) take
the card rows too. The changes of this design touch them only where the surface table requires a change.

### D8. Dark surface-dim

`--md-sys-color-surface-dim` in dark mode takes the value of `surface-container-lowest` (`#080f16`), so it is the darkest surface in
dark mode. `src/theme/runtime.js` applies the same rule when a seed colour is set, so the seeded theme follows the default. A test in
`src/styles/md3/color.test.js` asserts that the dark `surface-dim` has a lower relative luminance than the dark `surface`, and that
the seeded palette does too.

### D9. Anchors

The registry gains one entry: `administration-breadcrumb`, file `src/components/layout/AdministrationFrame.vue`, stability
`experimental`, no parts, no states. The new name is added in the same commit as the markup that carries it, and `anchors.test.js` must
pass in that commit. No existing anchor is renamed, moved to another file or removed. The `header` part of `repository-detail` stays
registered, and its element is the type section of D5.

### D10. Measurement

Each visual change is measured, not judged. The pull request records:

- the contrast of the content sheet edge, the card edge and the page against the frame in light, dark, and both high contrast schemes,
- the left edge of the title and of the trail at 1280 and 1920 pixels, from `getBoundingClientRect`, which must match within 0.5 pixel,
- the position of the back control on a detail page, which must lie in the gutter and not move the title,
- the screenshots of the design's pages at 320, 600, 1440 pixels (the brief's widths) and at 390, 840, 1280 and 1920 pixels, before and
  after, in light and dark.

## Risks

- **Visual weight.** A 1 pixel `outline` edge on every card is stronger than the current pages. The screenshots are the check, and
  question 3 keeps the alternative.
- **Seeded themes.** The runtime generator is the seed path. A change to a role value in `color.css` that is not in `runtime.js` makes
  the default and the seeded theme disagree, so D8 and the outline question are tested on both paths.
- **Merge order.** `AdministrationFrame.vue` is touched by this change and by `rework-administration-information-architecture`. The
  header markup belongs to this change, the groups to the other one, and the second merge rebases on the first.
- **Plugin stylesheets.** An anchored element that moves on the page keeps its name. A plugin stylesheet that positions it absolutely
  would move with it. This is documented, not prevented.

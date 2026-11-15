# Design

## Context

Measured on `main` in `reposilite-frontend/`:

- `components/layout/SideNavLayout.vue:77`: from 1240 pixels the `nav` is
  `sticky top-[calc(4rem+1rem)] flex max-h-[calc(100dvh-6rem)] w-52 shrink-0 flex-col gap-4 overflow-y-auto`. That is the inner
  scrollbar of the staging screenshot.
- Below 1240 pixels `SideNavLayout` shows the index in place of the page (`showsIndex`), with a back arrow and a breadcrumb on
  section pages.
- Rows are `min-h-10` (40 pixels) with `gap-1`; a group heading is a `p` with `md-label-medium`.
- A rough height of the full tree after `rework-administration-information-architecture` and `add-administration-overview`: 23
  rows of 44 pixels, 4 headings of about 28 pixels, the filter of about 56 pixels and the gaps, about 1,170 pixels. A 1080 pixel
  screen leaves about 950 pixels of browser viewport, minus the 64 pixel top bar and the save bar. The tree does not fit there; it
  fits at 1440 pixels of height. Fewer permissions mean fewer rows.
- There is no sheet, dialog or drawer primitive under `components/md/`. `MdOverflowMenu` teleports and handles Escape; one-off
  dialogs such as `ConfirmActionDialog.vue` trap focus.
- jsdom provides no `ResizeObserver`.
- `rework-administration-layout` "Side navigation does not overlap bars": "scroll inside itself when it is taller than the window".
  Its "Page header outside the columns" scenario "Only one sticky column" expects the side navigation to be sticky at 1280
  pixels; D1 makes that true only while the tree fits, so this change modifies that requirement too.

## Goals

- One scroll container on every Administration page.
- A sibling page within two steps at every width from 600 pixels.
- Better scent on the narrow index.

## Non-goals

- Collapsing groups, a drawer, new breakpoints.

## Decisions

### D1. Sticky only while it fits

`src/helpers/treeFits.js` exports `treeFits({ navHeight, viewportHeight, topBarHeight, saveBarHeight, margin })`, a pure function.
A composable, `useTreeFits(navRef, { observe })`, measures the heights and calls it; `observe` defaults to a `ResizeObserver` on the
`nav` and a `resize` listener on the window, and a test passes a fake that it triggers by hand. The `nav` binds `sticky top-[...]`
only when `treeFits` is true. `max-h-[...]` and `overflow-y-auto` are removed in both states.

The decision runs on resize and when the tree's own height changes (a filter, a permission change, a plugin entry), never on scroll,
so the tree does not switch between sticky and static under the pointer. When the save bar appears, its height enters the next
measurement; the bar's appearance is a size change of the page, not a scroll.

### D2. Index rows carry the description

Below 1240 pixels each index row is two lines: the label (`md-body-large`) and the description (`md-body-medium`,
`on-surface-variant`), clamped to two lines with the full text kept in the DOM. Beside the page (1240 pixels and wider) rows show the
label only, so the tree stays short. A row without a description has one line.

### D3. Sections sheet from 600 to 1239 pixels

A new primitive, `components/md/MdSideSheet.vue`: a modal side sheet from the leading edge, 320 pixels wide or the window width minus
56 pixels, whichever is smaller, with `role="dialog"`, `aria-modal="true"`, an accessible name, a close button, a scrim, Escape to
close, focus moved to the first focusable element on open, focus trapped, and focus returned to the opener on close. It honours
`prefers-reduced-motion`. Its root carries `data-ingot-component="md-side-sheet"`, registered as `experimental`.

`AdministrationFrame` shows a "Sections" button (icon plus text, 48 pixel target) beside the back arrow from 600 to 1239 pixels. The
sheet is named "Administration pages" and renders the same `SideNavLayout` tree (filter, Overview, groups, current entry, Unsaved
marks). Activating an entry navigates and closes the sheet; the focus rule of the dashboard then moves focus to `main`.

The button sits beside the back arrow, so a page without one draws no button: `/settings` below 1240 pixels shows the index
itself (today because no entry is current there, and after `add-administration-overview` because Overview draws the page and the
index together), and a sheet would repeat the list under it.

Below 600 pixels the button is not drawn: the index already fills the screen, and the bottom bar holds the four destinations.

### D4. Breakpoints unchanged

1240 stays the switch between index and side column (`rework-administration-layout` D1): at 1239 pixels a 208 pixel tree beside the
Repositories table would squeeze the priority columns. 600 stays the rail switch.

## Wireframes

Desktop, 1920 by 1080, tree taller than the window, scrolled down:

```
+----+------------------------------------------------------------------------------------+
|    | Ingot Repository                                                      (T) account  |
|    +------------------------------------------------------------------------------------+
|    |  |  Single sign-on       |  ...page content...                                     |
|    |  |  LDAP directory       |                                                         |
|    |  |  Sign-in protection   |   The tree scrolled up with the page.                   |
|    |  |  Permission check     |   No inner scrollbar; one scrollbar for the window.     |
|    |  | INSTANCE              |                                                         |
|    |  |  Branding             |                                                         |
|    |  | MONITORING AND SUPP.  |                                                         |
|    |  |  Audit log            |                                                         |
|    |  |  ...                  |                                                         |
+----+------------------------------------------------------------------------------------+
```

Desktop, 2560 by 1440, tree fits: sticky below the top bar, as today, but without `overflow-y-auto`.

Tablet, 900 pixels, `/access/ldap` before and with the sheet open:

```
+----+-----------------------------------------------+
|    | Ingot Repository                   (T)        |
|    +-----------------------------------------------+
| B  |  [<] Access                 [= Sections]      |   back arrow and Sections, 48 px targets
| S  |  LDAP directory                               |
| D  |  Sign-in with accounts from an LDAP directory.|
|(A) |  ...form...                                   |
+----+-----------------------------------------------+

+----+------------------------+----------------------+
|    | Administration pages[x]|//////////////////////|   modal side sheet, 320 px, scrim
| B  | [Find an admin. page ] |//////////////////////|
| S  |  Overview              |//////////////////////|
| D  | ARTIFACTS              |//////////////////////|
|(A) |  Repositories          |//////////////////////|
|    |  ...                   |//////////////////////|
|    | ACCESS                 |//////////////////////|
|    |  Users                 |//////////////////////|
|    | >LDAP directory  curr. |//////////////////////|
|    |  ...                   |//////////////////////|
+----+------------------------+----------------------+
```

Tablet, 900 pixels, the index with descriptions:

```
| ARTIFACTS                                         |
|  Repositories                                  >  |
|  Hosted, proxied and group repositories, their    |
|  policies and Maven options.                      |
|  Cleanup policies                              >  |
|  Rules that delete old versions from the          |
|  repositories they list.                          |
```

Phone, 320 pixels: unchanged index model with the description lines; no Sections button.

## Evidence

| Finding | Sources |
| --- | --- |
| Avoid a scroll area inside a scrolling page | Baymard Institute on inline scroll areas ("very sparingly, if at all"; hidden scrollbars on touch and trackpads; URL not recorded in the research corpus); an accessibility essay on nested scroll containers (keyboard, low vision and switch users; URL not recorded) |
| A fixed sidebar only works when it fits | Primer NavList (sidebar always visible on wide viewports), https://primer.style/components/nav-list ; Carbon UI shell left panel, https://carbondesignsystem.com/components/UI-shell-left-panel/usage/ |
| Keep navigation visible on desktop; hidden navigation is used less | NN/g, Hamburger menus and hidden navigation (179 participants; hidden navigation used in 27% of desktop cases against 48% when visible), https://www.nngroup.com/articles/hamburger-menus/ |
| Overlay navigation on narrower widths | GitLab Pajamas navigation sidebar (overlay below 1200 pixels), https://design.gitlab.com/patterns/navigation-sidebar ; Microsoft NavigationView (LeftMinimal overlay), https://learn.microsoft.com/en-us/windows/apps/design/controls/navigationview ; Carbon left panel behind a menu button |
| List-detail with a back link on narrow screens | Android list-detail layout, https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail ; Primer (back link for one level; URL not recorded in the research corpus) |
| Groups stay open; collapsing hurts findability on short trees | NN/g, Vertical navigation, https://www.nngroup.com/articles/vertical-nav/ ; GitLab settings research (issues 23483 and 1363; URLs not recorded) |
| Dialog semantics, focus and reflow | WAI-ARIA APG dialog (modal) pattern, https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/ ; WCAG 2.2 Understanding 1.4.10 reflow, https://www.w3.org/WAI/WCAG22/Understanding/reflow ; Understanding 2.5.8 target size, https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum |

Discarded: the claim that the Material 3 navigation drawer is deprecated in favour of an expanded rail (search snippets only). The
sheet here is a dialog for one task, not a persistent drawer.

## Risks

- A tree that changes height while the visitor scrolls (a plugin manifest arriving late) switches between sticky and static once.
  The switch happens on the size change, not on scroll, and only once per change.
- A new primitive is more code to keep accessible. It follows the APG dialog pattern and gets its own component test.

## Migration plan

None. A rollback is a revert of the code PR.

## Open questions

1. Sections sheet: default shipped from 600 to 1239 pixels; alternative back to index only.
2. Tree too tall at 1080 pixels: default scrolls with the page; alternative only the current group open.
3. Sheet on phones: default none; alternative the same sheet from the bottom.

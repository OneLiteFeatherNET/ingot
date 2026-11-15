# Design

## Context

Measured on `main`:

- `reposilite-frontend/src/components/header/DefaultHeader.vue:41` renders the bar as
  `sticky top-0 z-30 bg-surface-container`. `DefaultHeader.test.js:43-48` pins that role ("like the navigation rail").
- The navigation frame of #189: from 600 pixels the page root is `min-[600px]:bg-surface-container` and the rail is
  `bg-surface-container` (`components/md/MdNavigationRail.vue:114`), so rail and top bar form one L-shaped frame. The content is a
  `surface` sheet (`pane-sheet`) with an extra large top left corner, and from 1600 pixels a top right corner and a 16 pixel gap
  to the window edge (`pages/IndexPage.vue:328-341` and `:358-365`).
- The sheet's own corners scroll under the bar, so two sticky pieces at `top-16`, the bar's bottom edge, paint the same corners
  again (`IndexPage.vue:349-356`, `.pane-corner` in `src/style.css:126-147`). They are `surface-container` outside the arc.
- `DefaultHeader.vue:49` renders the instance name as an `h1`. `components/layout/AdministrationFrame.vue:57-62` renders a
  second `h1` ("Administration"). `components/search/SearchView.vue` and `components/card/SnippetsCard.vue` (on Browse) render
  their own `h1`. `components/dashboard/DashboardView.vue:43` and `:56`, `components/account/AccountView.vue:48`,
  `components/util/NotFoundView.vue:27` and the no access message in `pages/IndexPage.vue` start at `h2`.
- `DefaultHeader.vue:62-65` draws `<div class="olf-brand-bar absolute inset-x-0 bottom-0" aria-hidden="true" />` at the bottom
  edge of the bar.
- `src/styles/md3/color.css:338-348` defines `.olf-brand-bar` (4 pixels, `var(--olf-gradient)`) and a forced colours override
  that paints it `Highlight`. `src/theme/runtime.js:166` sets `--olf-gradient` from the instance's primary and tertiary colour.
  No other component draws the class.
- `DESIGN.md` is stale in four places: "Top app bar" (line 549) says `surface` with a tone change on scroll
  (`useWindowScroll`); "MdNavigationRail" (line 538) says an 80 pixel `surface` rail; "Adaptive navigation" (line 542) says
  the page is offset by 80 pixels; "Brand gradient" says the Overview hero draws the band in `HeaderHero.vue`. None of it
  mentions the content sheet.
- Tests that pin the rule and the heading today: `src/components/header/DefaultHeader.test.js:68` (`find('h1')`) and `:86-95`,
  `e2e/navigation-chrome.spec.js:84` and `:94` (`topBar(page).locator('h1')`), `:93` (`heading 'Administration', level 1`),
  `:120-129` (the rule's edges), and `src/styles/md3/color.test.js:255-260` (the class itself).
- `src/anchors/registry.json:518-524`: `default-header`, stability `stable`, part `title`. `.olf-brand-bar` is not an anchor.

## Goals

- No decorative line in the chrome.
- The frame stays as built: bar and rail in one role, the content sheet as the edge.
- One `h1` on every page of the dashboard, without moving the anchor.
- `DESIGN.md` describes what the code does.

## Non-goals

- New controls in the top bar, a second search, a header redesign.
- A tone change on scroll.
- The page header of the Administration area (`rework-administration-information-architecture`).

## Decisions

### D1. Remove the element, keep the class

The `div` is deleted from `DefaultHeader.vue`. `.olf-brand-bar`, its forced colours rule and `--olf-gradient` stay in
`color.css`, so `color.test.js` stays green and an operator seed still drives the gradient where it is used. No source in the
research supports a decorative accent line under an app bar; every separator the design systems describe has a job (see
Evidence).

### D2. The bar keeps the frame role

The bar stays `bg-surface-container` at every scroll position. No scroll listener, no composable, no transition. The edge
between the bar and the content is the `surface` sheet with its rounded corners and the sticky corner pieces under the bar,
which are already built and are not touched. Below 600 pixels there is no rail and no sheet, and the `surface` page under the
`surface-container` bar gives the same tonal edge.

A tone change to `surface` at rest, as the stale `DESIGN.md` line describes, was rejected: the corner pieces at the bar's
bottom edge are `surface-container` outside their arcs, so a `surface` bar would show two arcs under itself and break the L of
rail and bar into two pieces.

### D3. The title becomes a link

The `h1` is replaced by a `RouterLink` to `/` inside a `span` with `md-title-large`. `data-ingot-component="default-header"` stays on
the `header`, `data-ingot-part="title"` stays on the element that holds the title text. The `banner` landmark identifies the
site; the page's `h1` names the page (WAI-ARIA landmark guidance).

### D4. Every page keeps one `h1`

Removing the bar's `h1` would leave some pages without a level one heading. Each of them gets its own:

| Page | Today | After |
| --- | --- | --- |
| `/dashboard` | `h2` Instance status, `h2` Resources | new `h1` "Dashboard" (`md-headline-small`, like the Account title) above the getting started card; the sections stay `h2` |
| `/account` | `h2` "My account" (`id="account-title"`) | the same element as `h1`, same id, text and classes |
| Page not found | `h2` "Page not found" | `h1`, same id, text and classes |
| No access message | `h2` with the section label | `h1`, same id, text and classes |
| `/`, `/browse/...` | `h1` in `SnippetsCard` | unchanged |
| `/search` | `h1` in `SearchView` | unchanged |
| Administration pages | `h1` in `AdministrationFrame` | unchanged here; `rework-administration-information-architecture` changes its text |

No anchor name or part moves; `dashboard-view`, `account-view` and `not-found-view` stay on their roots.

### D5. DESIGN.md

One `docs(ui)` commit:

- "Top app bar": 64 pixels, `sticky top-0`, `surface-container` at every scroll position like the rail; the content sheet sets
  it apart; no tone change, border, shadow or decorative rule; it holds identity and the account menu only.
- "MdNavigationRail": a 96 pixel `surface-container` rail. "Adaptive navigation": the page is offset by 96 pixels, and from 600
  pixels the content is a `surface` sheet with rounded corners inside the frame of rail and bar, finished by the sticky corner
  pieces.
- "Brand gradient": not drawn in the chrome and by no component at present. The class stays as a utility.
- "Known gaps": replaced with the real list: no tabs, list, dialog or drawer primitive under `src/components/md/`, and no
  information architecture rules for the Administration area until `rework-administration-information-architecture` adds them.

## Wireframes

Before (every page, 600 pixels and wider):

```
+------+------------------------------------------------------------------+
| logo | Ingot Repository                                    (T) account  |  surface-container
|      |==================================================================|  4 px gradient stripe
| rail |.-----------------------------------------------------------------|  surface sheet, rounded corner
```

After, at rest and after scrolling (the same):

```
+------+------------------------------------------------------------------+
| logo | Ingot Repository                                    (T) account  |  surface-container, like the rail
|      |.-----------------------------------------------------------------|  no line: the sheet's tone is the edge
| rail |(  page content on the surface sheet                              |
```

Phone, 320 pixels: the same bar, 64 pixels tall, `surface-container` over the `surface` page, title truncated on one line,
account button with a 48 pixel target.

## Evidence

| Finding | Sources |
| --- | --- |
| The top bar holds product identity and global tools only | Carbon UI shell header, https://carbondesignsystem.com/components/UI-shell-header/usage/ ; GOV.UK header, https://design-system.service.gov.uk/components/header/ ; PatternFly masthead, https://www.patternfly.org/components/masthead |
| Separators have a job; avoid decoration | Primer PageHeader (`hasBorder` optional), https://primer.style/components/page-header ; Apple HIG toolbars (scroll edge effect, avoid custom backgrounds), https://developer.apple.com/design/human-interface-guidelines/toolbars ; NN/g complex applications (remove nonessential decoration), https://www.nngroup.com/articles/complex-application-design/ |
| Separation by tone instead of lines | Ingot's navigation frame as built in #189 (`IndexPage.vue`, `style.css`); Android Compose app bars (container colour as the separator), https://developer.android.com/develop/ui/compose/components/app-bars |
| Landmarks and one page heading | WAI-ARIA APG landmark regions, https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/ ; WCAG 2.2 2.4.6 and 2.4.1, https://www.w3.org/TR/WCAG22/ |
| The stripe reads as odd | The maintainer's own words on the staging screenshot. The idea that it "looks like a progress bar" was voiced by several reviewers but has no source and is not used as evidence. |

Discarded: Material 3 top app bar and divider rules. The m3.material.io pages returned only titles during the research, so
the MD3 basis is Ingot's own `DESIGN.md` and the frame as built.

## Risks

- A plugin stylesheet that selects the title by element (`header h1`) loses its target. The documented contract is the anchor
  attribute, which stays. The release notes name the change.
- A plugin stylesheet that selects `account-view h2` or `not-found-view h2` loses its target for the same reason; the release
  notes name these elements too.
- A later change could move the bar or the rail to another role and split the frame. The e2e case that compares the bar's
  background with the rail's guards it.

## Migration plan

None. A rollback is a revert of the code PR.

## Open questions

1. **Brand band elsewhere.** Default: not drawn in the chrome at all. Alternative: the documented band at the top edge of the
   Browse hero only, as a separate change.
2. **Visible Dashboard heading.** Default: visible `h1` "Dashboard". Alternative: the same `h1` visually hidden.

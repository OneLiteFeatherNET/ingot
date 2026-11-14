# Proposal

## Open maintainer questions

Each question has a default. The tasks implement the default; an answer that differs changes the matching task and nothing else.

1. **Content sheet edge: outline or elevation.** Default: a 1 pixel `outline` edge on the content sheet. Measured against the
   frame it is 3.84:1 in light and 5.16:1 in dark, which is the 3:1 that a visible boundary needs. `outline-variant` reaches only
   1.62:1 in light and 1.97:1 in dark, so it cannot carry the edge. Alternative: `shadow-elevation-1` and no edge. A shadow is
   barely visible in dark mode, so the alternative is weaker there.
2. **Reading measure: centred or left-aligned.** Default: centred, a 960 pixel column with `mx-auto` on every reading page.
   Alternative: left-aligned to the container edge, so the text sits under the top bar's left edge at every width.
3. **Card default: may it change globally?** Default: yes. `MdCard` is used by ten dashboard components and no plugin. Plugins
   cannot import Vue components; they reach the dashboard through anchors and the `ingot-plugins` stylesheet layer, and every
   anchored element keeps its name. The default card becomes `surface-container-low` with a 1 pixel `outline` edge. Alternative:
   keep `filled` as the default and give the new card its own variant name.
4. **Dark surface-dim.** Default: dark `surface-dim` takes the value of `surface-container-lowest` (`#080f16`), so it is the
   darkest surface in dark mode. The seeded path in `src/theme/runtime.js` takes the same rule, so an operator seed does not
   bring the old equal value back. Alternative: a value of the maintainer's choosing, given to both paths.
5. **Outline token value.** Default: no change to `outline-variant`. It stays the colour of decorative dividers, and component
   boundaries use `outline`. Alternative: darken `outline-variant` until it reaches 3:1 on `surface-container-low`. That also changes
   the seeded generator's output, because the seed path generates the role, so it is a visible change for every operator with a
   seed colour.

## Why

The dashboard was built view by view, and the frame of its pages now disagrees with itself. Four reports from the operators
and maintainers describe one underlying problem: each view decides its own width, header and surface.

- **Breadcrumbs on the Administration subpages render badly.** The trail is a list whose slashes are drawn by a CSS pseudo
  element. When a crumb is long, the slash wraps onto a line of its own or ends a line. The Overview page shows a plain text
  trail, a group without a heading shows none, and the last crumb carries no `aria-current`. The file browser has a second trail
  with its own spacing, so the same kind of navigation looks different on two pages.
- **The repository detail title sits too far left, and the indent is wrong.** The reading column of the Administration frame
  has no `mx-auto`, so it hugs the left edge while the wide variant centres. The repository detail draws its name a second
  time, as an `h2`, in the page column of the side navigation, which starts about 248 pixels to the right of the frame's
  heading. The "Back to repositories" text button insets its label, so the label and the title do not share an edge. The back
  arrow of the settings detail pages appears at 839 pixels and below, so the title jumps when it appears.
- **Page containers are written nine or more ways.** Some pages centre, some do not, some carry `max-w-none`, some use `mb-12`
  where the rest use `pb-12`. The page headings are `h1` on some pages and `h2` on others, and some pages have two `h1`s.
- **Surfaces barely separate.** The page, the frame (top bar and rail) and the content sheet are within about 1.1:1 of each
  other, and the card default is a surface that equals the page. Cards built by hand use a container step with no
  edge, so their edges measure about 1.05 to 1. Nested blocks sometimes repeat the step of their parent, and some invert it.
  The menus, the side sheet and the dialog each use a different container step. The dark `surface-dim` equals `surface`.

The MD3 tokens are already there. What is missing is one rule per role and one page shell that every view uses.

## What Changes

- **One surface table.** `reposilite-frontend/DESIGN.md` gets a surface table that names the role of each surface: the page, the
  frame, the content sheet, the card, nested blocks, menus, side sheets, dialogs and floating panels. The primitives follow the
  table; no view picks its own container step.
- **A visible frame.** The top bar and the rail are `surface-container`, the content sheet is `surface` with the edge of question 1.
  Cards are `surface-container-low` with a 1 pixel `outline` edge. Nested blocks step up one role, never the same role and never
  down. Menus are `surface-container` with `shadow-elevation-2`, side sheets `surface-container-low` with `shadow-elevation-1`,
  dialogs `surface-container-high` with `shadow-elevation-3`, and every floating panel carries an elevation.
- **One page shell.** Every page of the dashboard renders `MdPage` with one of two variants: `wide` (lists, tables and the
  dashboard, the full width after the side navigation) or `reading` (a centred 960 pixel column for text and forms). Both centre
  with `mx-auto`. The nine or more hand-written container classes go.
- **One page header.** Every page heading is `MdPageHeader`: the back control in its own slot, the trail, the one `h1`, the
  description and the actions. The title, the description and the trail share one left edge with or without a back control.
- **One trail.** The Administration trail and the file browser trail both render `MdBreadcrumb`. The separator is an
  `aria-hidden` element, not a pseudo element. The last crumb is `aria-current="page"`. Below 1240 pixels only the last crumb
  is drawn, which is the collapse; the full trail stays in the `title` of that crumb. The Overview trail is the section name, drawn
  as the current crumb, so it is not a special case.
- **One h1 per page.** The Repository detail page keeps its name in the frame's heading and drops its own `h2` header. The
  list pages that drew an `h2` under the frame's `h1` draw one `h1` through `MdPageHeader`. `SnippetsCard` draws its title as a
  card title, not an `h1`.
- **Anchors stay.** The new `administration-breadcrumb` anchor is added to the registry. `breadcrumb-navigation`,
  `administration-frame`, `side-nav-layout` (`nav`, `page`, `lead`) and `repository-detail` (`header`) keep their names. The
  element that carries `data-ingot-part="header"` on the repository detail page keeps the attribute on the type section, which
  now sits in the page content, and its `data-ingot-repository` value does not change.

Not BREAKING. No configuration key, environment variable, deployment manifest, route, permission node, stored data or plugin
API changes. The visible changes are the surfaces, the header alignment, the centred reading column and the breadcrumb shape.
Plugins that style an anchored element keep the same selector; the element may sit at a different place on the page, which is
the fix. The 600, 840 and 1240 pixel breakpoints stay.

### Shipping

Proposal PR `docs(openspec): propose fix-surface-hierarchy-and-page-header`: only the files under this change, tasks unchecked,
branched from the remote main branch. Then one code PR under the type `fix` and the scope `ui`:

`fix(ui): separate surfaces and align page headers and breadcrumbs`

The change is a fix: each bug in the Why section is a visible defect that the existing specifications already expect to work.
The commits inside the code PR each carry one type: `test(ui)` for the red tests, `fix(ui)` for the behaviour, `refactor(ui)`
for the container and header swaps that must not change a pixel outside the fix, and `docs(ui)` for `DESIGN.md`. No file under
`openspec/` is in the code PR.

### Dependencies

- `refactor-md3-token-utilities` must be merged first. The surface classes of this change are the Tailwind utilities that it
  defines, so a class such as `bg-surface-container-low` must resolve through the theme.
- `add-md3-component-catalogue` must be merged first. It provides `MdPage`, `MdPageHeader`, `MdBreadcrumb` and `MdListDetail`. None
  of them exists in `reposilite-frontend/src/components/md/` today (checked on this branch). If one of them is missing when
  this change is applied, the gap goes back into the catalogue change before this change starts; this change builds none of them.
- `add-ui-style-gate` must be merged first. The code PR passes the gate and removes, in the same pull request, the baseline entries
  of every custom class, arbitrary value and raw colour it fixes, and adds no entry.
- `rework-administration-information-architecture` owns the tree's content, labels and entry order. This change owns the visual
  frame, the header and the trail. The two changes touch `AdministrationFrame.vue` in different places: this change owns the
  header markup and the trail, that change owns the `groups` the frame receives. Whichever merges second rebases on the first.
- `rework-administration-side-navigation` and `add-administration-overview` change the frame's sheet and the Overview header. The
  Sections button and the Overview header keep their content; only their markup moves into `MdPageHeader`.
- `add-md3-component-catalogue` also defines `MdCard` variants. This change depends on its card token names and changes the default
  of `MdCard` only where the catalogue leaves the default open.

### Out of scope

- Dialogs and menus moving onto shared components (`unify-dialogs-menus-and-feedback`). This change sets their surface roles only.
- Lists, tables, cards built from scratch and forms (`migrate-content-to-components`). This change sets the surface of the cards
  it touches, and no more.
- The content, labels and order of the Administration tree (`rework-administration-information-architecture`).
- New colours, new breakpoints and dark mode tonal elevation beyond the dark `surface-dim` fix. A dark tonal elevation scheme is a
  separate decision.

## Capabilities

### Modified Capabilities

- `dashboard-layout`: the surfaces, the page shell, the page header, the breadcrumb, and the anchors that carry them. This
  capability has no specification under `openspec/specs/` yet, so the specification of this change is added and not modified.

## Impact

- **Operators:** nothing to configure. The pages look different: the frame reads as a frame, the cards have an edge, the repository
  detail title lines up with its breadcrumb, and the reading pages sit in the middle.
- **Plugin authors:** nothing to change. Anchored elements keep their names. A plugin stylesheet in the `ingot-plugins` layer that
  sets a background on a plugin element is not overridden by the new defaults, because the primitives are not anchored. The
  surface table is documented for plugins that want to match the frame.
- **Code:** about 20 files under `reposilite-frontend/src/`: `components/layout/AdministrationFrame.vue`,
  `components/layout/SideNavLayout.vue`, `components/browser/BreadcrumbNavigation.vue`, `components/repositories/RepositoryDetail.vue`,
  `components/repositories/RepositoriesView.vue`, `components/settings/SettingsView.vue`, `components/settings/EntryDetail.vue`,
  `components/settings/CollectionEditor.vue`, `components/access/TeamsPanel.vue`, `components/access/PrincipalsPanel.vue`,
  `components/account/AccountView.vue`, `pages/IndexPage.vue`, `components/header/DefaultHeader.vue`,
  `components/header/HeaderHero.vue`, `components/dashboard/DashboardView.vue`, `components/dashboard/AnalyticsView.vue`,
  `components/search/SearchView.vue`, `components/util/NotFoundView.vue`, `components/util/ViewLoadError.vue`,
  `components/util/ViewLoading.vue`, `components/footer/SiteFooter.vue`, `components/md/MdCard.vue`, `styles/md3/color.css`,
  `src/theme/runtime.js` (dark `surface-dim` on the seeded path), `src/anchors/registry.json`, and `reposilite-frontend/DESIGN.md`.
  No file under `reposilite-backend/` changes.
- **Documentation:** `reposilite-frontend/DESIGN.md` gets the surface table and the page shell rules. `docs/gitbook/reference/dashboard-pages.md`
  gets the new breadcrumb and the page header, only where it describes them.

## Verification

- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes, and `src/anchors/anchors.test.js` passes with the new anchor.
- Screenshots before and after, at 320, 600 and 1440 pixels (the brief's widths) and at 390, 840, 1280 and 1920 pixels, in light and
  dark, of the administration overview, the repositories list, a repository detail, a team detail, the account page, the dashboard
  and the file browser. They go into the pull request.
- Measured values in the pull request: the edge contrast of every surface pair in the surface table, in light, dark and both high
  contrast schemes, and the left edge of the title against the left edge of the trail at 1280 and 1920 pixels.
- `openspec validate fix-surface-hierarchy-and-page-header --strict` passes, and the dash grep prints nothing.

## Impact on the contract

This change adds an anchor name and keeps every other one. It changes no `REPOSILITE_*` or `INGOT_*` variable and no configuration
file. The drop-in contract of `CLAUDE.md` holds: an unmodified upstream compose file starts this build, and no value a plugin reads
moves.

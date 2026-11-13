# Proposal

## Open maintainer questions

1. **One code PR or one PR per wave.** Default: one PR per wave, five PRs in the order of the waves, each under the type `fix`
   and the scope `ui`, with the wave in the summary:
   - wave 1: `fix(ui): build cards and sections from shared components`
   - wave 2: `fix(ui): build lists and rows from shared components`
   - wave 3: `fix(ui): build tables and code samples from shared components`
   - wave 4: `fix(ui): build forms and controls from shared components`
   - wave 5: `fix(ui): use md3 window classes only and empty the style gate`

   Alternative: one code PR under the umbrella title `fix(ui): build cards, lists, tables and forms from shared components`,
   with the five waves as commit series. The waves are independently revertible either way, so the default keeps each review small.
2. **Card shape.** Default: one card, `MdCard` with `rounded-corner-medium` (12px) and `p-4` (16px) on every width. Alternative:
   `rounded-corner-large` (16px), which is the radius of the larger containers today. The default keeps the 8 existing
   `MdCard` users unchanged and moves about 60 hand-built cards onto them.
3. **Form field variant.** Default: `outlined` for every form field, which makes `MdTextField` default to outlined. Alternative:
   `filled`, the current primitive default. Outlined fields are the variant of the newest views (Audit, Password), so the
   migration touches fewer of them.
4. **The value of the `large` window class.** `add-administration-overview` and `rework-administration-side-navigation` draw the
   tree beside the page from 1240 pixels, and `refactor-md3-token-utilities` names that value `large` (its question 2) so that it
   changes no pixel. The MD3 `large` window class starts at 1200 pixels. Default: wave 5 moves `--breakpoint-large` from 1240 to
   1200 pixels, a change of 40 pixels that is visible only between 1200 and 1239 pixels. Alternative: keep `large` at 1240,
   which leaves one window class off the MD3 value.

## Why

The dashboard has many views, and each one builds its cards, rows, tables, code blocks and forms again. Measured on the
current tree, about 60 Vue files carry the radius classes `rounded-corner-medium` or `rounded-corner-large` by hand, although
`MdCard` exists and is used by 8 files. Section headings use three different type sizes. Five selectable lists compute their
selected state four times, and four copies of the same choice card live in one wizard. Four files use native `progress`,
`select`, radio or checkbox elements that bypass the primitives. Two dozen files set `font-mono` by hand, and one file carries a
`<style>` block with an app-wide scrollbar rule. Layout uses Tailwind's default breakpoints (`sm`, `md`, `lg`, `xl`) next to
literal widths such as 480, 720, 900, 960, 1024, 1240, 1920 and 2560 pixels, so the same page breaks at different widths in
different views.

The result is what the operator sees: the breadcrumbs of the administration subpages, the indentation of repository detail
pages and the surfaces that barely separate from the page background look different on every page, because each page
assembles the same parts from different pieces. Every fix then has to be made in many places. A card that looks right on one
page is wrong on the next.

This change builds the content of the dashboard from shared components only. A card is an `MdCard`, a section is an
`MdSection`, a row is an item of an `MdList`, a table is an `MdDataTable`, a code sample is an `MdCodeBlock`, a field is a
primitive from the catalogue. Styling is Tailwind utilities that resolve to the Material Design 3 tokens and to the MD3 window
classes. Once it ends, the style gate of `add-ui-style-gate` has an empty baseline, so no view can add a custom class, a
`<style>` block, a raw colour or a non-MD3 breakpoint without the frontend checks failing.

## What Changes

Everything below is visible in the layout and carries no change to data, routes, permissions or the server.

- **Cards and sections (wave 1).** Hand-built card containers become `MdCard`, with one radius and one padding. Section
  headings become `MdSection`, with one heading size per heading level (level 2 `md-title-medium`, level 3 `md-title-small`) and
  heading levels that follow the page, with no level 3 inside a level 4 and no heading without a parent section.
- **Lists and rows (wave 2).** Selectable rows, link rows, divided lists, node rows, renderer rows and checklist items use
  `MdList`, `MdListItem`, `MdListDetail`, `MdKeyValueList`, `MdDisclosure` and `MdBadge`. One row geometry applies: a minimum
  height of 56 pixels for two-line rows, a horizontal inset of 16 pixels and dividers drawn by the list. Selected state is
  computed once per list and shown as `aria-current`. Redundant resets and `overflow-wrap: anywhere` rules are dropped in
  favour of the primitive's own rules.
- **Tables and code (wave 3).** The repositories table, the dry-run result and the migration steps use `MdDataTable`. Sortable
  headers announce `aria-sort` and are real buttons. Column visibility uses the window classes, not per-width literals. Code
  samples use `MdCodeBlock`, `MdCopyableCode` and `MdCode`. The `<style>` block of the snippets card and its app-wide scrollbar
  rule go away; the scrollbar of a code block is styled by the primitive. The `font-mono` class is set by the code primitives.
- **Forms and controls (wave 4).** Native radio, checkbox, select and progress elements and hand-built buttons, search fields
  and sort buttons become `MdRadio`, `MdCheckbox`, `MdSelect`, `MdLinearProgress`, `MdButton` and `MdIconButton`. Visible
  48-pixel controls become 40-pixel controls with an `md-touch-target`, as `DESIGN.md` asks. Every form uses one field variant
  (open question 3). Field grids, fieldsets (with `legend`, not `span`), the save bar and the repository wizard's repeated
  pieces become shared components. Renderer prop wiring is one helper, not six copies.
- **Breakpoints and leftovers (wave 5).** Every `sm:`, `md:`, `lg:` and `xl:` and every literal width becomes one of the MD3
  window classes: `medium` (600 pixels), `expanded` (840 pixels), `large` (1200 pixels) and `xlarge` (1600 pixels). Tailwind's
  default breakpoints are removed from the theme, so the window classes are the only ones that exist. Remaining arbitrary values,
  `!important` overrides, opacity fades and hand-set font weights go. The style gate baseline is emptied and its allowlist
  entries that no longer apply are removed.

### Mapping of literal widths to window classes

Each literal takes the window class at which the layout was designed, rounding to the nearest MD3 threshold. The mapping is
recorded in `design.md` and is the reference for the review of each wave.

### Anchors

No `data-ingot-component` and no `data-ingot-part` is removed or moved off its semantic element. When two components merge
into one, both names stay: the outer element keeps the name of the wrapper, and the inner element keeps the name of the
component it replaces. New names are added to `src/anchors/registry.json` only, and `npm run anchors:doc` regenerates
`docs/gitbook/reference/dashboard-anchors.md` in the same commit. The stable name `settings-view` keeps its registry entry
(it has no element of its own) and loses only the class hook `settings-view` that the style block used.

### Not BREAKING

This change is not BREAKING. No configuration key, environment variable, deployment manifest, container image, route, permission
node, settings API, stored data or plugin API changes. Operators have nothing to change. The visible changes are the normalised
cards, rows, headings, tables, breakpoints and controls described above, listed in each wave's pull request description with
before and after screenshots. Plugin stylesheets target stable anchors (documented in `dashboard-anchors.md`), not the utility
classes; the task group 1 grep confirms that no documented plugin example uses a class this change removes. If that grep finds
one, the change gains an operator-facing item and is marked BREAKING before merge.

### Shipping

Proposal PR `docs(openspec): propose migrate-content-to-components`: only the files under
`openspec/changes/migrate-content-to-components/`, tasks unchecked, from the remote main branch.

Code PRs under the type `fix` and the scope `ui`, after the proposal is merged. Default: one PR per wave (open question 1). The
exact umbrella title is `fix(ui): build cards, lists, tables and forms from shared components`. Supporting commits inside a PR
carry the types `test(ui)` (red tests), `build(ui)` (style gate baseline), `docs(gitbook)` (generated anchor reference) and
`fix(ui)` (implementation), as in `add-administration-overview`. No file under `openspec/` is in a code PR.

### Dependencies

- `add-md3-component-catalogue` must be merged first. It supplies `MdSection`, `MdList`, `MdListItem`, `MdListDetail`,
  `MdKeyValueList`, `MdDisclosure`, `MdBadge`, `MdDataTable`, `MdTabs`, `MdCodeBlock`, `MdCopyableCode`, `MdCode` and
  `MdNavigationItem`. Nothing in this change builds a replacement for them.
- `fix-surface-hierarchy-and-page-header` must be merged first. It owns the surface steps, the page header and the breadcrumb.
  This change owns the content inside those surfaces.
- `add-ui-style-gate` must be merged first. Wave 5 empties its baseline; before that the gate reports the work still to do.
- `refactor-md3-token-utilities` must be merged first. The window-class names `medium`, `expanded`, `large` and `xlarge` are
  defined there, and the literals `min-[600px]:`, `min-[840px]:`, `min-[1240px]:`, `min-[1600px]:`, `max-[599px]:`,
  `max-[600px]:` and `max-[839px]:` are already replaced by them. The `md-scrollbar` utility is added by this change in wave 3.
- `unify-dialogs-menus-and-feedback` owns dialog and menu shells, status messages and snackbars. Where a file is touched by both,
  that change merges first. This change does not re-open the shell of a dialog.
- `add-administration-overview` owns `SettingsHub.vue`, `settingsHub.js` and the header actions of `SettingsView.vue`. The
  settings hub files are excluded from wave 1 if that change has not merged.

### Out of scope

- The backend, the plugin API, the plugin manifest and the anchor contract's stable names.
- Colour token values, the surface steps and the type scale. This change uses them; it does not change them.
- The information architecture of the administration tree and the tree's filter.
- New features and new behaviour. A table keeps its sort, a list keeps its selection, a form keeps its validation.
- The style gate itself, the primitive catalogue and the dialog and menu shells (sibling changes).
- Tailwind's theme beyond the breakpoints (the `@theme` of the colour and type tokens stays as it is).

## Capabilities

### New Capabilities

- `dashboard-content-components`: the cards, sections, lists, tables, code samples, forms and window classes that every
  dashboard view is built from, and the rule that the views use no custom class, no `<style>` block and no non-MD3 breakpoint.

## Impact

- **Operators:** nothing to change. Pages look more uniform; the visible differences are listed in each wave's pull request.
- **Plugin authors:** nothing to change for stable anchors. A plugin stylesheet that targets a utility class of the dashboard, not an
  anchor, may stop matching; the grep in task group 1 lists the documented examples checked.
- **Code:** under `reposilite-frontend/src/components/` (cards, lists, tables, code, forms, renderers, settings, repositories,
  migration, identity, access, audit, browser, plugins, search, dashboard, maintenance, support, account, card and pages),
  `src/style.css` (breakpoints, removal of the app-wide scrollbar rule and of the settings style block), `src/anchors/registry.json`
  and `src/anchors/anchors.test.js`. No file under `reposilite-backend/` changes.
- **Documentation:** `docs/gitbook/reference/dashboard-anchors.md` is regenerated when a name is added. The how-to for plugin
  stylesheets is checked, not changed, unless the grep finds a removed class in an example.

## Verification

- Every red test in the tasks is seen to fail first.
- `npm test` in `reposilite-frontend` passes after each wave. Changed expectations of existing tests are listed in the pull request.
- Playwright suites `accessibility.spec.js`, `layout.spec.js`, `navigation.spec.js`, `repositories.spec.js` and
  `dashboard.spec.js` pass at 320, 600, 840, 1280 and 1920 pixels.
- Screenshots: before and after at 320, 600 and 1440 pixels in light and dark for each wave, and the wave's own screenshots at
  390, 840, 1280 and 1920 pixels in light and dark. They go in the pull request description, not into the repository.
- The style gate passes with an empty baseline at the end of wave 5.
- `openspec validate migrate-content-to-components --strict` passes, and the dash grep prints nothing.

# Proposal

## Open maintainer questions

Each question has a default. The change is written against the default; a different answer changes only the tasks named in brackets.

1. **Breakpoint names.** Default: `medium`, `expanded`, `large`, `xlarge`, the MD3 window classes from 600, 840, 1240 and 1600 pixels, next to Tailwind's `sm`, `md`, `lg` and `xl`. Alternative: compact-first names (`compact` first, so that the smallest window class has a name too). No current literal needs a compact name. [tasks 3.1, 4.1]
2. **Keep 1240 pixels or move to the MD3 value of 1200.** Default: keep 1240, as `rework-administration-side-navigation` does. Moving to 1200 changes the layout between 1200 and 1239 pixels and is visible, so it is not part of this change; `migrate-content-to-components` makes that move in its wave 5 (its question 4). [tasks 3.1]
3. **Delete `.olf-brand-bar`.** The class has no template use after pull request #203 (`src/styles/md3/color.css:338`, and its forced-colors copy at `:345`). `reposilite-frontend/DESIGN.md` still lists it as a kept utility class. Default: keep it as an `@utility` in the cascade layer, so nothing breaks. Alternative: delete it and its DESIGN.md entry. Deletion removes a class that a plugin stylesheet might use, so it would have to be marked BREAKING in the release notes. [tasks 3.6, 6.1]
4. **Monospace: a type role or an allowed extension.** Default: `--font-mono` moves from `src/style.css` into `src/styles/md3/typography.css` as a documented extension token, and `md-code-small` and `md-code-medium` are body sizes in the mono family. No MD3 role is added. Alternative: a `code` role in the type scale with its own size. [tasks 3.5]
5. **The `container` class.** Default: `container` stays the name of the page gutter and is redefined as `@utility container`, because Tailwind 4.3 derives the `container` width steps from the breakpoint tokens, so the new breakpoints would add width steps at 600, 840, 1240 and 1600 pixels (to verify, task 2.1). Alternative: keep Tailwind's built-in `container` and add a separate gutter utility to `SearchView.vue`. [tasks 3.1]
6. **The 40 percent scrim.** `src/components/migration/MigrationDialog.vue:82` uses `bg-scrim/40`, the other four overlays use 32 percent. Default: keep both values as tokens (`md-scrim` at 32 and `md-scrim-strong` at 40), so nothing changes visibly. `unify-dialogs-menus-and-feedback` later moves the migration dialog onto `MdDialog` at 32 percent as one of its listed visible changes. Alternative: move the migration dialog to 32 percent, which is a visible change and then needs its own before and after screenshots. [tasks 3.4]
7. **The `!` modifiers of `md-embedded-input`.** Default: keep them. Dropping them is only tested by the screenshot comparison, and the forms plugin's selectors need them today. [tasks 3.6]
8. **The JavaScript breakpoint.** `src/pages/IndexPage.vue:288` calls `useMediaQuery('(max-width: 1239px)')`. A CSS token cannot feed `matchMedia` without a runtime read of the custom property. Default: leave the literal and record it as the one documented exception, in the enforcement scan (task 5.1). Alternative: a shared constant in `src/helpers/` that the CSS token is checked against in a test. [tasks 4.1]

## Why

The dashboard has two styling languages. Most views use Tailwind utilities, but the helpers that make the Material Design 3 look work are plain CSS classes in `src/style.css` and `src/styles/md3/`, and several primitives and views write MD3 values as literals that bypass the tokens. The result is a set of small inconsistencies that a reader only sees by looking hard:

- The same window size is written several ways. A view that needs the medium window writes `min-[600px]:`, another writes `sm:`, a third writes `max-[599px]:`. The MD3 window classes exist only in a comment.
- The state layer is written three ways. A check box and a radio use `peer-hover:opacity-[0.08]`, the navigation rail uses `group-hover:[--md-state-opacity:0.08]`, and the select menu sets `--md-state-opacity` inline with `0.12`. Each of them repeats an MD3 value that is also in the token files.
- Layout sizes are written as magic numbers in views, and the same number appears in a second view. The top bar is 4rem in `IndexPage.vue` and in `SideNavLayout.vue`. The bottom bar height exists twice, once as a constant in `src/helpers/bottom-bar.js` and once as a literal in `IndexPage.vue`. `IndexPage.vue:360` also uses a class, `pane-sheet`, that no stylesheet defines.
- Four rules sit outside any layer and therefore beat every layer, including the plugin layer. A plugin cannot change the dialog stacking value or the skeleton animation, and the dashboard cannot change them without editing the global stylesheet.
- Several colour values are written twice in `src/styles/md3/color.css`, and the reduced-motion rule is written in two files.

The brief of this change is to make Tailwind the only styling language of the dashboard, backed by the MD3 tokens. Every helper becomes a Tailwind v4 `@theme` token or an `@utility`, so that the variants `hover:`, `md:`, `dark:`, `group-*` and `peer-*` work on all of them. The rules that remain plain CSS are the ones the library hooks need, and they are listed in the enforcement tests.

## What Changes

- **Named window breakpoints.** `@theme` in `src/style.css` gains `--breakpoint-medium: 600px`, `--breakpoint-expanded: 840px`, `--breakpoint-large: 1240px` and `--breakpoint-xlarge: 1600px`. Tailwind's `sm`, `md`, `lg` and `xl` stay. Every literal breakpoint that is equivalent to a named one is replaced with the name. The literal `max-[599px]:` becomes `max-medium:`, which is `width < 600px`; this moves the boundary by one pixel at 599 pixels, and the difference is accepted and recorded in `design.md`.
- **State and focus as utilities.** `md-state-layer`, `md-focus-ring`, `md-focus-ring-inset` and `md-touch-target` are declared with `@utility` and keep their names. The focus ring and the inset ring become one definition with an offset modifier. New utilities drive the state layer from a peer or a group, and from a focus-within state, so that the literal copies of the checkbox, radio, switch, navigation rail, select and search bar go away. The opacities (hover 8 percent, focus and pressed 12, dragged 16, disabled container 12, disabled content 38), the focus ring width (3px), its offset (2px) and the touch target size (48px) are tokens.
- **Other helpers as utilities or tokens.** `.container` becomes a utility with the window margin tokens (16px below 600 pixels, 24px from 600 pixels). `.pane-corner`, `.pane-corner-start` and `.pane-corner-end` become one utility with a mirrored modifier; they are in use at `src/pages/IndexPage.vue:350-351`. `.skeleton-bars` and its keyframes become an animation token, and `.router-view-full` becomes a utility on `src/App.vue`. `md-embedded-input` stays a utility.
- **Motion.** `duration-md-<name>` utilities exist for every `--md-sys-motion-duration-*` token, because `reposilite-frontend/DESIGN.md` states that Tailwind 4 has no theme-driven duration namespace (to verify against the installed Tailwind 4.3, task 2.1). The linear progress and snackbar animations become `--animate-*` tokens. Their `<style>` blocks are removed by `add-md3-component-catalogue`, not here.
- **New tokens.** A stacking scale (sticky, top bar, menu, snackbar, next to the existing dialog value), a scrim opacity, an icon size of 18px, the field container role that is read but never declared, and layout sizes (top bar 4rem, rail 6rem, bottom bar 5rem, reading measure 960px, side sheet 320px, snackbar 560px). The elevation shadows refer to a shadow role token instead of repeating `rgb(0 0 0 / x)`.
- **Monospace** moves into the typography layer, as decided in question 4.
- **Duplicates.** Repeated colour values in `color.css` refer to one definition through `var()`. The redundant contrast overrides go. The reduced-motion rule is written once. The split `:root` and `.dark` blocks merge, and the two `@theme inline` blocks of `color.css` merge.
- **Surface-dim.** In the dark theme `--md-sys-color-surface-dim` (`src/styles/md3/color.css:114`) has the same value as `--md-sys-color-surface` (`:109`), `#0d141c`. This is flagged in `design.md`. Fixing it is visible and belongs to `fix-surface-hierarchy-and-page-header`, so this change does not change the value.

## Cascade contract

Today `.router-view-full`, `.vfm`, `.skeleton-bars` and `.olf-brand-bar` are unlayered and beat every layer, including `ingot-plugins`. After this change every rule of the dashboard sits in a layer. The consequence is intended: a plugin stylesheet in `ingot-plugins` can now override the stacking value of the modal root, the skeleton animation, and the band class, which it could not before. This widens what plugins may do and does not take anything away. The order of the layers stays `theme, base, components, utilities, ingot-plugins`, and a test asserts that `ingot-plugins` is still the last one and that no unlayered rule remains in `src/style.css`.

Moving the state layer and focus helpers from `@layer components` into `@utility` raises their precedence against the Tailwind utilities of the same element. Elements that combine `md-state-layer` with `absolute`, `overflow-hidden` or `z-*` are listed in task 4.6 and checked in the screenshots.

## Capabilities

### New Capabilities

- `dashboard-design-tokens`: the Tailwind theme and the Material Design 3 token files are the only source of styling values of the dashboard. Helpers are utilities, breakpoints and layout sizes are named, and the library hooks are the only plain selectors.

## Impact

- **Operators:** nothing to change. No configuration key, environment variable, deployment manifest, route or stored data changes. The rendered page is the same at 320, 600, 1024, 1440 and 1920 pixels, in light and dark, and the screenshots compare identical (see Verification).
- **Plugin authors:** nothing to change. The helper class names stay. Plugin stylesheets can override the four rules that are now layered (see Cascade contract). `.olf-brand-bar` stays unless question 3 is answered for deletion.
- **Code:** `reposilite-frontend/src/style.css`, `src/styles/md3/color.css`, `typography.css`, `shape-elevation-motion.css`, `state-layer.css`, `touch-target.css`, the tests in `src/styles/md3/` and `src/style.test.js`, and the class names listed in `design.md` in `src/components/md/`, `src/components/search/SearchBar.vue`, `src/pages/IndexPage.vue`, `src/App.vue`, `src/components/layout/SideNavLayout.vue`, `src/components/layout/AdministrationFrame.vue` and `src/helpers/bottom-bar.js`. No file under `reposilite-backend/` changes. No `data-ingot-*` attribute is added, removed or renamed.
- **Documentation:** `reposilite-frontend/DESIGN.md` (the source file list, the state layer and focus section, the motion section and the window margin section). `docs/gitbook/` is not touched, because no configuration key, environment variable or deployment manifest changes.

## Out of scope

- Removing Tailwind's `sm`, `md`, `lg` and `xl` breakpoints. Visible, and belongs to `migrate-content-to-components`.
- Any component markup restructuring, and the removal of the `<style>` blocks of `MdLinearProgress.vue` and `MdSnackbar.vue`. Both belong to `add-md3-component-catalogue`.
- Colour value changes, including the surface-dim fix.
- A build-time gate that fails on custom CSS and token bypasses. That is `add-ui-style-gate`. This change adds source-scan tests that run in `npm test`.
- The redesign of the top bar, the side navigation and the administration pages.

## Shipping

- Proposal pull request, under the title `docs(openspec): propose refactor-md3-token-utilities`. It contains only the files under `openspec/changes/refactor-md3-token-utilities/`, with every task unchecked, branched from the remote main branch.
- Code pull request, under the title `refactor(ui): move md3 tokens and helpers into the tailwind theme`, branched from the remote main branch that already contains the merged proposal. Type `refactor`, scope `ui`. The commits inside it are `test(ui)` for the red tests, `refactor(ui)` for the tokens, the utilities and the literal replacements, and `docs(ui)` for `DESIGN.md`. No file under `openspec/` is in the code pull request.
- Optional follow-up pull request, only when the maintainer asks for it: `docs(openspec): archive refactor-md3-token-utilities`.

This change does not depend on another change and merges first. The merge order of the programme is: this change, `add-ui-style-gate`, `add-md3-component-catalogue`, then `fix-surface-hierarchy-and-page-header` and `unify-dialogs-menus-and-feedback`, and last `migrate-content-to-components`. Each of them uses the token and utility names defined here.

## Verification

- `openspec validate refactor-md3-token-utilities --strict` passes.
- `npm test` in `reposilite-frontend` passes, with each new test seen to fail first in the red group.
- The build CSS of `npm run build` is diffed before and after. Every rule that is not a rename or a moved helper is the same; the diff is reviewed in the pull request.
- Screenshots before and after at 320, 600 and 1440 pixels, and at 390, 1024 and 1920 pixels, of the dashboard, the administration repositories list and a repository detail page, a dialog and a menu, in light and dark, are identical pixel for pixel. Any difference blocks the merge.
- Every breakpoint literal listed in the spec is absent from `src/`, except the documented `useMediaQuery` exception.
- `data-ingot-*` attributes are unchanged, and `src/anchors/anchors.test.js` passes.
- The dash grep prints nothing in any file of this change.

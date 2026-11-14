# Design

## Context

Measured on the `main` branch in `reposilite-frontend/` (non-test sources; line counts, not token counts):

- Window literals: `min-[600px]:` on 16 lines, `min-[840px]:` on 17, `min-[1240px]:` on 5, `min-[1600px]:` on 3, `max-[599px]:` on 7, `max-[600px]:` on 2, `max-[839px]:` on 6. The one JavaScript breakpoint is `src/pages/IndexPage.vue:288`, `useMediaQuery('(max-width: 1239px)')`.
- Helper classes in `src/style.css`: `.container` (used by `src/components/search/SearchView.vue:250` and `src/components/layout/AdministrationFrame.vue:103`), `.pane-corner`, `.pane-corner-start` and `.pane-corner-end` (used at `src/pages/IndexPage.vue:350-351`, so they are live), `.skeleton-bars` (`src/components/card/SnippetsCard.vue:188` and `:254`), `.router-view-full` (`src/App.vue:56`), `.vfm`, `md-embedded-input`.
- `src/pages/IndexPage.vue:360` uses the class `pane-sheet`, and no stylesheet defines it. It has no visual effect today and is removed as a dangling class (task 4.3).
- `.olf-brand-bar` is defined at `src/styles/md3/color.css:338` and in a forced-colors block at `:344-346`. No template uses it.
- State literals: `peer-hover:opacity-[0.08] peer-focus-visible:opacity-[0.12] peer-active:opacity-[0.12]` in `src/components/md/MdCheckbox.vue:101` and `MdRadio.vue:147`; `group-hover:opacity-[0.08] group-focus-visible:opacity-[0.12] group-active:opacity-[0.12]` in `MdSwitch.vue:105`; `group-hover:[--md-state-opacity:0.08]` in `MdNavigationRail.vue:150`; inline `{ '--md-state-opacity': 0.12 }` in `MdSelect.vue:418`; `has-focus-visible:outline-*` in `MdRadio.vue:125`; `has-[input:focus-visible]:outline-*` in `src/components/search/SearchBar.vue:50`.
- Disabled literals in `src/components/md/MdButton.vue`: `bg-on-surface/12 text-on-surface/38` and the border and transparent variants (lines 56 to 76).
- Scrim: `bg-scrim/32!` (with the important modifier, inside `overlay-class`) on the four vue-final-modal overlays (`PublicRepositoriesDialog.vue:65`, `DeleteEntryModal.vue:72`, `AdjustmentsModal.vue:43`, `FailuresSheet.vue:133`) and `bg-scrim/40` in `src/components/migration/MigrationDialog.vue:82`.
- Layout literals: `top-[calc(4rem+1rem)]` in `src/components/layout/SideNavLayout.vue:71`; `min-h-[calc(100dvh_-_4rem)]` and `pt`/`bottom` literals with `5rem` in `src/pages/IndexPage.vue:512`, `:517` and `:559`; `BOTTOM_BAR_HEIGHT = 80` in `src/helpers/bottom-bar.js:25`; `max-w-[960px]` in `AdministrationFrame.vue:103` and `AccountView.vue:43`; `max-w-[560px]` in `src/components/md/MdSnackbar.vue:54`.
- `--md-field-container` is read in `src/components/md/MdSelect.vue:375`, `MdTextField.vue:122`, `MdTextArea.vue:143` and `MdChipInput.vue:188`, and set in `src/components/settings/NewEntryDialog.vue:96`. It is never declared in a token file.
- Z-index literals are used across the dashboard: `z-10`, `z-20`, `z-30`, `z-40`, `z-50` and `z-[1100]` (`MdSnackbar.vue:44`). The dialog value `--md-sys-z-dialog: 1000` is in `shape-elevation-motion.css:53` and is used by `src/style.css` and `MdSelect.vue:407` (plus one).
- Motion: the comment at `src/styles/md3/shape-elevation-motion.css:23` promises `duration-md-<name>`, which does not exist. `reposilite-frontend/DESIGN.md` (Motion) states that durations are not theme-driven in Tailwind 4 and uses `duration-(--md-sys-motion-duration-short3)`.
- Dark theme: `--md-sys-color-surface-dim` is `#0d141c` at `src/styles/md3/color.css:114`, the same value as `--md-sys-color-surface` at `:109`. Flagged only.
- Hex repeats in `color.css`: `#0062a1` (`--md-sys-color-surface-tint` at `:61`, and in the `--olf-gradient` line at `:330`), `#565a8b` (`:330`), and the dark gradient at `:334`, which repeats the dark surface-tint value `#9ccaff`.
- Versions: `tailwindcss` `^4.3.3` and `@tailwindcss/vite` `^4.3.3` in `reposilite-frontend/package.json`. There is no `node_modules` in the worktree; the Tailwind facts below are to be confirmed in task 2.1 before the code PR relies on them.

## Goals

- Every styling value of the dashboard comes from a Tailwind theme token, an `@utility`, or an MD3 token variable.
- Variants (`hover:`, `md:`, `dark:`, `group-*`, `peer-*`, `has-*`) work on every helper.
- No plain CSS class selector remains in `src/style.css` or `src/styles/md3/`, except the two documented hooks: the `.dark` colour hook and the `.vfm` library hook.
- No magic number that duplicates an MD3 value or a layout size remains in a view or a primitive.
- Nothing is visible. The rendered page is pixel identical before and after.

## Non-goals

- Removing `sm`, `md`, `lg` and `xl`.
- Any component markup restructuring, and the `<style>` blocks of `MdLinearProgress.vue` and `MdSnackbar.vue` (`add-md3-component-catalogue`).
- Colour value changes, including the dark surface-dim (`fix-surface-hierarchy-and-page-header`).
- A build-time gate (`add-ui-style-gate`).

## Decisions

### D1. Named window breakpoints

`--breakpoint-medium: 600px`, `--breakpoint-expanded: 840px`, `--breakpoint-large: 1240px` and `--breakpoint-xlarge: 1600px` are declared in the `@theme` block of `src/style.css`. Tailwind 4 derives the `medium:`, `expanded:`, `large:` and `xlarge:` variants and their `max-*` forms (`max-medium:` means `width < 600px`, `max-expanded:` means `width < 840px`) from these tokens.

Replacements, one for one:

| Literal | Named class |
|---|---|
| `min-[600px]:` | `medium:` |
| `min-[840px]:` | `expanded:` |
| `min-[1240px]:` | `large:` |
| `min-[1600px]:` | `xlarge:` |
| `max-[600px]:` | `max-medium:` |
| `max-[599px]:` | `max-medium:` (accepted one-pixel change, see below) |
| `max-[839px]:` | `max-expanded:` |

Accepted difference: Tailwind 4 writes `max-[599px]` as a width below 599px (to confirm in task 2.1), so a window of exactly 599 pixels changes from the old rule to the new one. This is recorded here and checked by the screenshot comparison at a 600 pixel window (which does not hit the difference) and by a source scan. If a view depends on 599 pixels, it has to be moved to `max-[599px]` on purpose, not silently.

The JavaScript breakpoint at `IndexPage.vue:288` stays a literal (question 8 in `proposal.md`). A CSS token cannot feed `matchMedia`.

### D2. Window margin and the container utility

The page gutter is 16px below 600 pixels and 24px from 600 pixels: `--md-sys-window-margin-compact: 16px` and `--md-sys-window-margin-medium: 24px`.

`.container` becomes `@utility container`, which sets the gutter (`padding-inline`) and the width ladder. The width ladder is needed because Tailwind 4 derives the `container` max-width steps from the breakpoint tokens: once the four named breakpoints exist, Tailwind's built-in `container` would add width steps at 600, 840, 1240 and 1600 pixels, and every centred page would change width. Task 2.1 confirms this against the installed Tailwind. If it does not happen, the built-in utility is kept and the gutter moves to `md-window-margin`, and question 5 in `proposal.md` is answered accordingly.

The gutter stays inside a layer (`@utility` is in the utilities layer), so a view's own `px-*` still overrides it, and the existing window-margin test (`src/styles/md3/window-margins.test.js`) keeps its meaning.

### D3. State layer, focus and touch target as utilities

- `@utility md-state-layer` sets `position: relative` and `isolation: isolate`, and draws the `::before` overlay with `opacity: var(--md-state-opacity)`. The `::before` rule stays a component of the helper (it is part of the utility body).
- State values are tokens in `state-layer.css`: `--md-sys-state-hover-opacity: 0.08`, `--md-sys-state-focus-opacity: 0.12`, `--md-sys-state-pressed-opacity: 0.12`, `--md-sys-state-dragged-opacity: 0.16`, `--md-sys-state-disabled-container-opacity: 0.12`, `--md-sys-state-disabled-content-opacity: 0.38`.
- Variant-driven utilities set `--md-state-opacity` and are used with a variant: `md-state-hover`, `md-state-focus`, `md-state-pressed`, `md-state-dragged`. Usage: `peer-hover:md-state-hover peer-focus-visible:md-state-focus peer-active:md-state-pressed` (MdCheckbox, MdRadio), `group-hover:md-state-hover` (MdNavigationRail), `md-state-focus` for the select's active item in place of the inline style (MdSelect).
- Opacity modifiers that refer to the disabled values use `--opacity-*` theme keys (`--opacity-md-disabled-container`, `--opacity-md-disabled-content`), so `bg-on-surface/md-disabled-container text-on-surface/md-disabled-content` replaces `/12` and `/38`.

Risk, recorded for review: the current `MdCheckbox`, `MdRadio` and `MdSwitch` rules set `opacity` on the state element itself, while the new utilities set `--md-state-opacity` for the `::before` overlay. The two are equal only when the element carries `md-state-layer`. Task 4.2 gives those elements `md-state-layer` in the same change, and the screenshot comparison of the checkbox, radio and switch is a merge gate. If the comparison shows a difference, the element keeps its own opacity rule as a utility, `md-state-opacity-hover` and the like, and the decision is recorded in the pull request.

- The focus ring and the inset ring are one definition: `--md-sys-focus-ring-width: 3px` and `--md-sys-focus-ring-offset: 2px`. `@utility md-focus-ring` draws `outline: var(--md-sys-focus-ring-width) solid var(--md-sys-color-secondary)` with `outline-offset: var(--md-sys-focus-ring-offset)` on `:focus-visible`. `@utility md-focus-ring-inset` uses the same declarations with the offset negated. Both class names stay, so the DESIGN.md contract holds.
- The focus-within ring replaces `has-focus-visible:outline-*` (`MdRadio.vue:125`) and `has-[input:focus-visible]:outline-*` (`SearchBar.vue:50`): `@utility md-focus-ring-within` and `has-focus-visible:md-focus-ring-within` (MdRadio), and `has-focus-visible:md-focus-ring-within` (SearchBar). The SearchBar selector changes from "the input" to "any focus-visible child"; the search field has one focusable child, so the behaviour is the same, and task 4.2 checks it.
- `@utility md-touch-target` keeps its name; the 48px size is `--md-sys-touch-target-size: 48px`.

### D4. Motion

- `duration-md-<name>` is an `@utility` family that sets `transition-duration` and `animation-duration` from `--md-sys-motion-duration-<name>`. It is needed because Tailwind 4 has no theme namespace for durations (to be confirmed in task 2.1; `DESIGN.md` already states this). Until then the code keeps `duration-(--md-sys-motion-duration-*)` working.
- Animations are `--animate-*` tokens that use motion tokens:
  - `--animate-md-linear-slide` runs the keyframes `md-linear-slide` for 1.6s, as in `MdLinearProgress.vue:68-87`. The slower 4 second variant, used for reduced motion, is `--animate-md-linear-slide-reduced`, applied with `motion-reduce:`.
  - `--animate-md-snackbar-enter` and `--animate-md-snackbar-leave` for `MdSnackbar.vue:87-107`.
  - `--animate-skeleton-fade-in: skeleton-fade-in var(--md-sys-motion-duration-extra-long2) ease-out var(--md-sys-motion-duration-short4) both`. The timing function stays `ease-out`. Replacing it with a motion easing token would change the curve, which is visible.
- The keyframes live in the same `@theme` block, so a token and its keyframes travel together.
- `duration-md-*` and `animate-*` replace the literals `0.8s`, `0.2s`, `1.6s` and `4s`.

### D5. Stacking, scrim and icon size

- Z scale, in `shape-elevation-motion.css`, with values that equal the literals in use: `--z-index-md-sticky: 20`, `--z-index-md-top-bar: 30`, `--z-index-md-menu: 50`, `--z-index-md-dialog: 1000` (the existing `--md-sys-z-dialog` is kept as the source value), `--z-index-md-snackbar: 1100`. The tokens are exposed as `z-md-*` utilities. The component literals (`z-50` on dialogs in `src/components/migration/MigrationDialog.vue:82` and `src/components/settings/PasswordDialog.vue:92`) stay until `unify-dialogs-menus-and-feedback` moves those dialogs onto `MdDialog`; the change is listed in that proposal, not here. `z-[1100]` in `MdSnackbar.vue:44` becomes `z-md-snackbar` in `add-md3-component-catalogue`, which rewrites that component.
- Scrim: `--opacity-md-scrim: 32%` (used as `bg-scrim/md-scrim!` on the four overlays, keeping the important modifier) and `--opacity-md-scrim-strong: 40%` (`MigrationDialog.vue:82`, question 6).
- Icon size: `--spacing-md-icon: 1.125rem` (18px), so `size-md-icon` replaces `size-[18px]`, `h-[18px]` and `w-[18px]` in `MdButton`, `MdCheckbox` (two), `MdChip` (two), `MdChipInput`, `SegmentedOption`, `TeamParentsCard` and `RepositoryOverview` (the nine literal occurrences, rechecked in task 4.4).

### D6. Layout tokens

Declared in `@theme` in `shape-elevation-motion.css` next to the other foundation tokens, so `DESIGN.md` can name them:

| Token | Value | Replaces |
|---|---|---|
| `--spacing-md-top-bar` | 4rem | `top-[calc(4rem+1rem)]` (SideNavLayout:71), `min-h-[calc(100dvh_-_4rem)]` (IndexPage:360) |
| `--spacing-md-rail` | 6rem | `w-24` in `MdNavigationRail.vue:114`, the rail width in `IndexPage.vue` |
| `--spacing-md-bottom-bar` | 5rem | `5rem` in `IndexPage.vue:512`, `:517`, `:559`; `BOTTOM_BAR_HEIGHT` |
| `--container-md-reading` | 60rem (960px) | `max-w-[960px]` (AdministrationFrame:103, AccountView:43) |
| `--container-md-sheet` | 20rem (320px) | the side sheet width literal in `MdSideSheet.vue` |
| `--container-md-snackbar` | 35rem (560px) | `max-w-[560px]` (MdSnackbar:54) |

The dialog width (`min-w-70`, `max-w-140`) is already on the spacing scale (280px and 560px) and needs no token. The `1rem` gap in `top-[calc(4rem+1rem)]` becomes `--spacing(4)` in the arbitrary value.

`src/helpers/bottom-bar.js` keeps `BOTTOM_BAR_HEIGHT = 80` because the script writes the value to the document root. A test (task 5.5) asserts that the constant equals the CSS token in pixels, so the two cannot drift.

### D7. Field container

`--md-field-container: var(--md-sys-color-surface)` is declared in both the light `:root` block and the `.dark` block of `color.css`. The read sites drop the fallback argument and read `var(--md-field-container)`. The declaration sits in the same element rules as the surface token, so a `.dark` element computes the same value as the surface does. `NewEntryDialog.vue:96` keeps setting it on its own element.

### D8. Shadows

`--md-sys-elevation-level<n>` refer to a shadow role: `--md-sys-shadow-key: rgb(0 0 0 / 0.3)` and `--md-sys-shadow-ambient: rgb(0 0 0 / 0.15)`. The values are unchanged; only the literal copies in the five levels go.

### D9. Typography and monospace

- `--font-mono` moves from `src/style.css` into `typography.css` as an extension token (question 4).
- `md-code-small` and `md-code-medium` are utilities with the body sizes and the mono family.
- Tailwind 4 does not generate one `@utility` from a loop, so the seventeen type utilities stay as seventeen blocks (`typography.css:115-249`). Each block shrinks to the declarations that come from one token group, and the prominent labels (`md-label-large-prominent`, `md-label-medium-prominent`) use `@apply md-label-large` and `@apply md-label-medium` with the weight changed. This removes the copies, not the names.

### D10. Colour duplicates

- The hex values that repeat (`#0062a1` at the surface tint and the gradient, `#565a8b` in the gradient, and the dark gradient) are declared once and referenced with `var()`. The gradient in `--olf-gradient` refers to the surface-tint role instead of repeating its hex value. The computed values do not change.
- The redundant contrast overrides at `color.css:160` and `:174` (audit lines) are removed if the computed values are the same in both themes; the test in group 5 checks the computed values.
- The `:root` and `.dark` blocks that are split (`color.css:26`, `:282`, `:301`, `:329`) merge into one `:root` and one `.dark` block at the top of the file. The two `@theme inline` blocks (`:223` and `:350`) merge into one.
- `.olf-brand-bar` becomes `@utility olf-brand-bar`, its forced-colors rule kept beside it. It is kept by default (question 3).

### D11. Reduced motion, once

The reduced-motion rule is written once, in `src/style.css` `@layer base`. The copy in `state-layer.css` (the `::before` transition set to `none`) is removed. The global rule already shortens every transition to 0.01ms, so the only difference is a 0.01ms fade instead of none for users who ask for less motion, which is not visible. The source scan in group 5 checks that the rule appears once.

### D12. Cascade layers

- `@layer theme, base, components, utilities, ingot-plugins;` stays the order.
- `.vfm` moves into `@layer components` as a documented library hook, `z-index: var(--md-sys-z-dialog)`.
- `.router-view-full` is removed from `src/style.css`. `@utility md-router-view` sets `min-height: calc(100vh - --spacing(8))`, which is the old `calc(100vh - 2rem)`, and `src/App.vue:56` uses it. The literal `2rem` becomes the spacing scale.
- `.skeleton-bars` becomes `@utility skeleton-bars` that uses `animate-skeleton-fade-in`.
- `.pane-corner`, `.pane-corner-start` and `.pane-corner-end` become one `@utility pane-corner` with a mirrored modifier: `pane-corner-start` puts the corner on the left and `pane-corner-end` on the right, and both keep their class names, so the markup at `IndexPage.vue:350-351` keeps them. The position literal `min-[1600px]:right-4` on line 351 becomes `xlarge:right-4`.
- `.olf-brand-bar` as in D10.
- `md-embedded-input` stays a utility, with its `!` modifiers (question 7).

Precedence effects, reviewed in task 4.6: a helper that was in `@layer components` is now a utility, and utilities of the same element sort after components. Combinations to check are `md-state-layer` with `absolute`, `fixed`, `sticky`, `overflow-hidden` and `z-*`; `md-touch-target` with `position` utilities; and `md-focus-ring` with `outline-*` utilities.

## Risks

| Risk | Mitigation |
|---|---|
| `max-[599px]` boundary moves by one pixel | Accepted and recorded (D1); screenshot at 600 pixels |
| Tailwind `container` picks up the new breakpoints | Custom `@utility container` (D2); confirmed in task 2.1 |
| Checkbox, radio and switch opacity moves from the element to `::before` | Element gets `md-state-layer`; screenshot merge gate; fallback utilities described in D3 |
| SearchBar focus selector changes from input to any focus-visible child | One focusable child; checked in task 4.2 |
| Helpers gain precedence over other utilities on the same element | Review in task 4.6 |
| Plugin rules can now override `.vfm`, the skeleton, the router view and the band | Intended (proposal, cascade contract); a test asserts the order |
| `.olf-brand-bar` deletion breaks a plugin that uses the class | Kept by default (question 3) |
| `md-field-container` default changes inheritance | Declared in the same element rules as the surface token (D7) |

## Tailwind 4.3 facts to confirm (task 2.1)

These statements are assumptions in this design until task 2.1 checks them against the installed `tailwindcss` 4.3 package and records the result in `DESIGN.md` (task 6.1):

1. There is no theme namespace for durations, so `duration-md-*` is a utility family.
2. `--z-index-*`, `--opacity-*`, `--container-*`, `--spacing-*` and `--animate-*` produce the `z-*`, `bg-*/<key>`, `max-w-*` and `w-*`, `size-*`, `animate-*` utilities as described here.
3. Tailwind's built-in `container` derives its width steps from the breakpoint tokens.
4. A custom `@utility container` replaces the built-in one, and `@utility` with a variant (`peer-hover:md-state-hover`) works.

If one of these is wrong, the affected decision changes and the change is updated before the code PR is opened.

## Test plan (F.I.R.S.T.)

Every test reads a source file from disk (`readFileSync` from the frontend directory, as in `src/style.test.js`). No test reads the system time, sleeps, touches the network, or shares state. Each assertion has a message that names the rule. Tests use plain string and block helpers already present in `style.test.js` (`blockAt`).

- `src/style.test.js`: the layer order and the absence of unlayered rules; the reduced-motion rule appears once; the cascade order of the `ingot-plugins` layer.
- `src/styles/md3/color.test.js`: hex values appear once outside the root and dark blocks; the merged blocks; the field container declared in both themes; the olf brand utility declared with `@utility`.
- `src/styles/md3/state-layer.test.js`: the helper names are `@utility`; the state tokens and their values; the focus ring declared once with an offset modifier; no `peer-hover:opacity-[` or `group-hover:[--md-state` remains in `src/components/md/`.
- `src/styles/md3/touch-target.test.js`: `md-touch-target` is `@utility` and sizes from the token.
- `src/styles/md3/typography.test.js`: the mono token and its utilities; the prominent labels use `@apply`.
- `src/styles/md3/window-margins.test.js`: the window margin tokens; the container gutter values 16px and 24px; the existing rule that a `container` element carries no `px-*`.
- New in `src/styles/md3/shape-elevation-motion.test.js`: the z scale values; the layout tokens; the duration and animation tokens; the shadow role.
- New in `src/styles/md3/scan.test.js` (task 5.1): a source scan of `src/**/*.vue` for the literal breakpoints, the state literals, the scrim literals, the icon literal and `pane-sheet`, with the single documented `useMediaQuery` exception.
- `src/helpers/bottom-bar.test.js` (task 5.5): `BOTTOM_BAR_HEIGHT` equals the CSS token in pixels.

No test checks a computed style in a browser. The visual behaviour is checked by the screenshot comparison in task 7.3, which is not a unit test.

## Verification of visual parity

Before and after screenshots, captured with the same Playwright script on the same seeded data:

- At 320, 600 and 1440 pixels (the widths the review standard for visible changes requires) and at 390, 1024 and 1920 pixels (the widths the maintainer asked for).
- Pages: the dashboard, the administration repositories list, a repository detail page, one dialog (the delete confirmation) and one menu (the overflow menu), in light and dark.
- Pixel comparison. Any difference blocks the merge until it is explained in the pull request, and then either fixed or recorded as an accepted change.
- The build CSS of `npm run build` is diffed before and after, and the diff is reviewed in the pull request. Every rule that changes is a rename, a moved helper or a removed duplicate.

## Surface-dim (flag only)

`--md-sys-color-surface-dim` in the dark theme (`src/styles/md3/color.css:114`) is `#0d141c`, the value of `--md-sys-color-surface` (`:109`). A surface that is meant to sit below the default surface cannot be told apart from the page. This is visible and is listed in `fix-surface-hierarchy-and-page-header`. This change does not change it.

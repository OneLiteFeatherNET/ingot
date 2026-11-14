# Ingot design system

The dashboard is styled with Tailwind CSS 4 on top of a Material Design 3 (MD3) token set.
The names in this document are the contract that views and components build on. Keep them
stable: renaming a token breaks every view that uses it.

Source files:

- `src/styles/md3/color.css`: colour roles, light and dark, plus two status colours
- `src/styles/md3/typography.css`: type scale and the `md-*` text classes
- `src/styles/md3/shape-elevation-motion.css`: corner radii, elevation, easing, durations,
  the animations, the stacking, scrim, icon and layout sizes
- `src/styles/md3/state-layer.css`: state layers, state opacities and the focus ring
- `src/styles/md3/touch-target.css`: the touch target
- `src/components/md/`: primitives
- `src/style.css`: imports the files above, the window size classes, the `dark` variant, the
  page gutter, the router view height, the skeleton, the pane corners and the global rules
  (the reduced motion rule and the base styles)

## Rules for contributors

- **Use roles, never raw hex.** Write `bg-surface-container` and `text-on-surface`, not a
  hex value or a Tailwind palette shade. Role colours switch between light and dark on
  their own. (This file is scanned for class names too, so palette examples here would
  generate utilities.)
- **Do not write `dark:` variants for role colours.** `text-on-surface` already is the right
  colour in both themes. `dark:` is for the few cases that are not role colours.
- **Surfaces use `surface-container-*`.** Pick the step by emphasis, from least to most:
  `surface-container-lowest`, `surface-container-low` (cards and panels),
  `surface-container` (the default), `surface-container-high` and
  `surface-container-highest` (raised or hovered elements). The same step is a different
  shade in each theme, so never reason about it as light or dark.
- **One filled button per view**, as the primary action. Everything else is `tonal`,
  `outlined` or `text`.
- **Every icon-only control has an `aria-label`.** `MdIconButton` warns without one.
- **Visual size and touch target are separate.** MD3 sizes a control by what it shows
  (a 40px button, a 40px icon button, a 32px chip) and gives it a touch target of at least
  48 by 48px, which may be larger than the control. A custom control that needs a touch
  target adds `md-touch-target` (see Touch target below). Do not make a visible control
  48px tall just to reach the minimum.
- **Focusable custom controls use `md-focus-ring`** (or the `:focus-visible` outline that
  it sets). Do not remove outlines without replacing them.
- **Interactive elements add `md-state-layer`** to get hover, focus and pressed overlays
  with the MD3 opacities.
- **Text uses the `md-*` classes**, not hand-set font sizes, so the type scale stays
  consistent.
- **Do not add new `--color-*` or `--md-*` tokens inside a view.** A new role goes into
  `color.css` (and this file) so that every view can use it.
- **Every dialog is a vue-final-modal with `teleport-to="body"`.** Page content can carry
  its own z-index, so a dialog left in place can paint under it. The `.vfm` rule in
  `src/style.css` gives every modal root the `--md-sys-z-dialog` stacking value. The dialog
  itself keeps a 24px side inset (set on the modal's `content-class`), caps at 560px and
  never goes below 280px.
- **The style gate enforces these rules in `npm test` and `npm run eslint`.** It lives in
  `src/style-gate/` and rejects a `<style>` block, an arbitrary value, a raw colour, a palette
  colour, a Tailwind default, a literal inline style, an unknown class and an inline icon
  outside the icon folders. Existing violations sit in its baselines, which may only shrink:
  a fix removes its entry in the same pull request.

## Colour

Default seed `#3A5F85`, a steel blue. The scheme is MD3 `SchemeVibrant` at contrast 0, generated
with `@material/material-color-utilities` (pinned in `package.json`), the same generator and
variant the operator seed path uses. The default therefore looks exactly like setting that seed
(see Operator branding). The previous default was the OneLiteFeather pink (`#ac1c67` light,
`#ff88b7` dark), which read as too pink for a dashboard of tables and status. Values live in
`src/styles/md3/color.css`:

- Light values on `:root`, dark values on `.dark`.
- Each role is a CSS custom property `--md-sys-color-<role>`.
- `@theme inline` exposes each role as `--color-<role>`, which makes these utilities
  available: `bg-<role>`, `text-<role>`, `border-<role>`, `ring-<role>`, `outline-<role>`,
  `fill-<role>`, `stroke-<role>`, `divide-<role>`, `placeholder-<role>`, and the
  variant forms such as `hover:bg-<role>/90` (the opacity modifier works).

The dashboard applies the `dark` class to the document root and to the app wrapper. The
mode comes from `src/store/theme.js` (`auto`, `light` or `dark`).

Roles:

| Group | Roles |
| --- | --- |
| Primary | `primary`, `on-primary`, `primary-container`, `on-primary-container`, `primary-fixed`, `primary-fixed-dim`, `on-primary-fixed`, `on-primary-fixed-variant` |
| Secondary | `secondary`, `on-secondary`, `secondary-container`, `on-secondary-container`, `secondary-fixed`, `secondary-fixed-dim`, `on-secondary-fixed`, `on-secondary-fixed-variant` |
| Tertiary | `tertiary`, `on-tertiary`, `tertiary-container`, `on-tertiary-container`, `tertiary-fixed`, `tertiary-fixed-dim`, `on-tertiary-fixed`, `on-tertiary-fixed-variant` |
| Error | `error`, `on-error`, `error-container`, `on-error-container` |
| Surface | `background`, `on-background`, `surface`, `on-surface`, `surface-variant`, `on-surface-variant`, `surface-tint`, `surface-dim`, `surface-bright` |
| Surface containers | `surface-container-lowest`, `surface-container-low`, `surface-container`, `surface-container-high`, `surface-container-highest` |
| Inverse | `inverse-surface`, `inverse-on-surface`, `inverse-primary` |
| Outline and overlay | `outline`, `outline-variant`, `scrim`, `shadow` |

The fixed roles are not in the official tables. They come from the same generator at
contrast 0 and do not switch with the theme, which is what a fixed role is for.

### Default values

The role values of the default scheme, generated from the steel seed. They are the same values an
operator gets from `seedColor: "#3A5F85"`, and they are the source of truth in `color.css`.

| Role | Light | Dark |
| --- | --- | --- |
| `primary` | `#0062a1` | `#9ccaff` |
| `on-primary` | `#ffffff` | `#003257` |
| `primary-container` | `#d0e4ff` | `#00497b` |
| `on-primary-container` | `#00497b` | `#d0e4ff` |
| `primary-fixed` | `#d0e4ff` | `#d0e4ff` |
| `primary-fixed-dim` | `#9ccaff` | `#9ccaff` |
| `on-primary-fixed` | `#001d35` | `#001d35` |
| `on-primary-fixed-variant` | `#00497b` | `#00497b` |
| `secondary` | `#515e7d` | `#b9c6ea` |
| `on-secondary` | `#ffffff` | `#23304d` |
| `secondary-container` | `#d8e2ff` | `#394664` |
| `on-secondary-container` | `#394664` | `#d8e2ff` |
| `secondary-fixed` | `#d8e2ff` | `#d8e2ff` |
| `secondary-fixed-dim` | `#b9c6ea` | `#b9c6ea` |
| `on-secondary-fixed` | `#0c1b36` | `#0c1b36` |
| `on-secondary-fixed-variant` | `#394664` | `#394664` |
| `tertiary` | `#565a8b` | `#bfc2fa` |
| `on-tertiary` | `#ffffff` | `#282c5a` |
| `tertiary-container` | `#e0e0ff` | `#3f4372` |
| `on-tertiary-container` | `#3f4372` | `#e0e0ff` |
| `tertiary-fixed` | `#e0e0ff` | `#e0e0ff` |
| `tertiary-fixed-dim` | `#bfc2fa` | `#bfc2fa` |
| `on-tertiary-fixed` | `#121644` | `#121644` |
| `on-tertiary-fixed-variant` | `#3f4372` | `#3f4372` |
| `error` | `#ba1a1a` | `#ffb4ab` |
| `on-error` | `#ffffff` | `#690005` |
| `error-container` | `#ffdad6` | `#93000a` |
| `on-error-container` | `#93000a` | `#ffdad6` |
| `background` | `#f8f9ff` | `#0d141c` |
| `on-background` | `#161c24` | `#dde3ee` |
| `surface` | `#f8f9ff` | `#0d141c` |
| `on-surface` | `#161c24` | `#dde3ee` |
| `surface-variant` | `#dae3f1` | `#3f4753` |
| `on-surface-variant` | `#3f4753` | `#bec7d5` |
| `surface-tint` | `#0062a1` | `#9ccaff` |
| `surface-dim` | `#d4dae5` | `#0d141c` |
| `surface-bright` | `#f8f9ff` | `#333a42` |
| `surface-container-lowest` | `#ffffff` | `#080f16` |
| `surface-container-low` | `#eef4ff` | `#161c24` |
| `surface-container` | `#e8eefa` | `#1a2028` |
| `surface-container-high` | `#e2e8f4` | `#242b33` |
| `surface-container-highest` | `#dde3ee` | `#2f353e` |
| `inverse-surface` | `#2a3139` | `#dde3ee` |
| `inverse-on-surface` | `#ebf1fc` | `#2a3139` |
| `inverse-primary` | `#9ccaff` | `#0062a1` |
| `outline` | `#6f7884` | `#89919e` |
| `outline-variant` | `#bec7d5` | `#3f4753` |
| `scrim` | `#000000` | `#000000` |
| `shadow` | `#000000` | `#000000` |

The neutrals come from the seed's own palette, so they carry a faint steel tint (chroma about 3 on the
light `surface`, about 6 on the dark one). The earlier near-neutral override of the pink scheme is
gone. The test in `src/styles/md3/color.test.js` allows that tint and rejects a stronger cast.

### High contrast

`@media (prefers-contrast: more)` swaps in the contrast 1.0 values of the same SchemeVibrant
generator for the accent roles, the fixed roles, `on-surface`, `on-background`,
`on-surface-variant` and `outline`. The surfaces keep their values, so the page keeps its
colour and only the content gets stronger. The light set is scoped to `:root:not(.dark)` and
the dark set to `.dark`, so a root that carries both classes takes the dark set. With the
query on, every text pair in the table above reaches 7:1 in both themes; without it, 4.5:1.
`src/styles/md3/color.test.js` checks both.

Medium contrast (0.5) was generated with the same method and is not emitted. CSS has no
middle step for `prefers-contrast` (only `no-preference`, `more` and `less`). If the
dashboard ever gets a manual contrast setting, regenerate the medium set at 0.5 and attach
it to a data attribute at that point.

Status colours that MD3 does not define. Use them for text only:

| Token | Utility | Light | Dark |
| --- | --- | --- | --- |
| `--md-ext-color-success` | `text-success` | `#166534` | `#4ade80` |
| `--md-ext-color-warning` | `text-warning` | `#854d0e` | `#eab308` |

Use the `error` roles for failures. `success` and `warning` are for confirmations and
non-fatal notices only.

### Brand gradient

The OLF gradient is a brand element, not a role, so it has no Tailwind colour utility. The
`--olf-gradient` custom property in `color.css` runs at 135 degrees from the primary to the
tertiary of the default scheme, in both modes: `#0062a1` to `#565a8b` in light, `#9ccaff` to
`#bfc2fa` in dark. The OLF pink and the four-stop brand gradient are no longer used by the
default. Nothing pink is drawn by the default theme. The gradient is not drawn in the chrome
and no component draws it at present: the top bar keeps `surface-container` with no rule, and
the Overview hero draws no band. Use it for brand areas only:

- `.olf-brand-bar` is a 4px band at the top edge of a section. It is kept as a utility class
  and is not used by any component.
- Components stay on the MD3 roles. A button on a gradient area is still a `primary` filled
  button.
- Under `forced-colors: active` the bar takes the `Highlight` system colour, because forced
  colours drop background images.
- The feather logo is never recoloured, shadowed or tilted. Nothing in this file touches it.

### Operator branding

An operator can set a seed colour in the server settings (`appearance.seedColor`). Empty
means the static theme above, unchanged. A seed generates the complete MD3 scheme at runtime
with `@material/material-color-utilities` (pinned, see `package.json`) and writes it into one
`<style id="ingot-theme">`, with the same selectors as `color.css`: `:root` for light, `.dark`
for dark, and the `prefers-contrast: more` block for both high contrast schemes.

- `schemeVariant` selects the scheme (`TONAL_SPOT`, `VIBRANT` (default), `EXPRESSIVE`,
  `NEUTRAL`, `FIDELITY`, `CONTENT`, `MONOCHROME`). `primaryColor`, `secondaryColor`,
  `tertiaryColor` and `errorColor` replace the palette of their role; they need a seed.
- Invalid hex values and unknown variants are ignored with a `console.warn`.
- Series 1 to 3 of the chart palette follow primary, tertiary and secondary. The brand
  gradient runs from primary to tertiary. Series 4 to 8 keep their static values. The
  default theme is generated from the steel seed with the same method, so the neutrals of the
  default and of a seed-based theme come from the same palette.
- `logoDark` replaces the logo in the dark theme, `favicon` replaces the icon, and
  `footerText` is shown as plain text in the footer. `theme-color` is updated when present.
- The generator is a separate chunk, loaded only when a seed is set (about 20 KB gzipped), so
  the default theme does not pay for it. Validation lives in `src/theme/settings.js`, and the
  generator in `src/theme/runtime.js`.
- Development: `?theme-seed=%23006a6a` previews a seed without changing the settings. It is
  read only when `import.meta.env.DEV` is true.
- The settings are fetched from `/api/frontend/settings` in both deployments, bundled or
  detached, before the first render. The request goes below the mount point of the build, never
  below the current address. The wait is capped at two seconds; a failure keeps the defaults
  silently.
- Saving the `frontend` settings domain applies the appearance again at once, without a reload.
  Clearing the seed takes the generated theme and the favicon back out.
- In the settings editor the five colour fields (`seedColor`, `primaryColor`, `secondaryColor`,
  `tertiaryColor`, `errorColor`) are colour fields: an outlined text field with a swatch, a
  picker behind an icon button, and a Clear action. A value must be `#RRGGBB` or empty, and the
  domain cannot be saved otherwise. Under the Appearance section a preview strip shows the
  primary, secondary, tertiary, error and surface roles of the scheme the form would give.

## Charts

Series colours are a dedicated palette, not MD3 roles. The seed's roles sit too close together
to tell apart as series, so the chart tokens are eight hues spaced 30 degrees apart on the
colour wheel, from 29 to 239 degrees. Series 1 is the steel blue and series 8 is the rose-red,
so no magenta opens a chart and the pink end sits last.

Each token is a CSS custom property, `--md-ext-color-chart-1` to `-8`, in `color.css`:
light values on `:root`, dark values on `.dark`. They have no Tailwind utility, because a
chart reads them through `src/store/palette.js` (`categorical()`), not through a class.

Generation: each hue was harmonised toward the steel seed `#3A5F85` with `Blend.harmonize` from
`@material/material-color-utilities`, chroma 52. Light takes tone 40 and dark tone 80 of the
resulting hue. The hue search covered start points and spacings in a pool of hue markers, and
kept the set with the largest smallest colour difference that also clears 3:1 on both surfaces.
The generator ran as a throwaway script outside the project, and the library is not a project
dependency.

| Series | Token | Hue (CIE LCh, dark) | Light (tone 40) | Dark (tone 80) | Light contrast on `surface` | Dark contrast on `surface` |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `chart-1` | steel blue, 258 | `#186393` | `#92cdff` | 6.16:1 | 10.93:1 |
| 2 | `chart-2` | blue-cyan, 232 | `#13677d` | `#51d6ff` | 6.12:1 | 10.95:1 |
| 3 | `chart-3` | teal, 194 | `#006a67` | `#2ddcd8` | 6.14:1 | 10.88:1 |
| 4 | `chart-4` | green, 160 | `#006c48` | `#63dca5` | 6.17:1 | 10.86:1 |
| 5 | `chart-5` | olive-green, 127 | `#42681d` | `#a1d66f` | 6.19:1 | 10.90:1 |
| 6 | `chart-6` | olive, 98 | `#695f08` | `#d8c843` | 6.17:1 | 10.81:1 |
| 7 | `chart-7` | gold-brown, 51 | `#96481f` | `#ffb593` | 6.16:1 | 10.83:1 |
| 8 | `chart-8` | rose-red, 17 | `#a03c4a` | `#fdb3b8` | 6.17:1 | 10.86:1 |

Light `surface` is `#f8f9ff`, dark `surface` is `#0d141c`. Every series clears 3:1 on both, and on
`surface-container-highest` as well (5.0:1 light, 7.2:1 dark), since the test checks it. Between
the series, the smallest CIE76 difference is 17.1 in light (series 2 and 3) and 17.3 in dark
(series 1 and 2). The test requires 15. Series 8 is a rose-red that reads pale pink in dark mode;
it is the last series on purpose, so a chart with fewer series never shows it first.

Series are assigned in the order above, and every pair is checked, adjacent or not. A legend
still needs its labels, because colour alone does not identify a series for everyone.

The colour tests in `src/styles/md3/color.test.js` hold the rules above: 3:1 on the surfaces,
15 CIE76 units between any two series, and a steel blue first series. `src/store/palette.test.js`
checks that the palette reads the eight tokens that `color.css` defines.

Charts use these colours for marks only. Text on a chart uses `chrome()` roles.

## Typography

Font stack: `"Roboto Flex", Roboto, system-ui, sans-serif`. It is exposed as
`--md-sys-typescale-font-family` and as Tailwind's `font-sans`, which Preflight applies to
the whole page. No webfont is loaded.

Each role has four tokens, `--md-sys-typescale-<role>-{size,line-height,weight,tracking}`,
and one utility class:

| Class | Size / line height (px) | Weight | Tracking (px) |
| --- | --- | --- | --- |
| `md-display-large` | 57 / 64 | 400 | -0.25 |
| `md-display-medium` | 45 / 52 | 400 | 0 |
| `md-display-small` | 36 / 44 | 400 | 0 |
| `md-headline-large` | 32 / 40 | 400 | 0 |
| `md-headline-medium` | 28 / 36 | 400 | 0 |
| `md-headline-small` | 24 / 32 | 400 | 0 |
| `md-title-large` | 22 / 28 | 400 | 0 |
| `md-title-medium` | 16 / 24 | 500 | 0.15 |
| `md-title-small` | 14 / 20 | 500 | 0.1 |
| `md-body-large` | 16 / 24 | 400 | 0.5 |
| `md-body-medium` | 14 / 20 | 400 | 0.25 |
| `md-body-small` | 12 / 16 | 400 | 0.4 |
| `md-label-large` | 14 / 20 | 500 | 0.1 |
| `md-label-medium` | 12 / 16 | 500 | 0.5 |
| `md-label-small` | 11 / 16 | 500 | 0.5 |
| `md-label-large-prominent` | 14 / 20 | 700 | 0.1 |
| `md-label-medium-prominent` | 12 / 16 | 700 | 0.5 |

The two prominent labels are the label roles in the bold weight, for emphasis within a
line of label text, such as a name in a sentence. The type scale has no other sizes:
`text-xxs`, `text-xm` and `text-ssm` were removed. Use the `md-*` role closest to the size
you need.

The classes are Tailwind utilities, so variants work: `md:md-title-large`,
`peer-focus:md-label-medium`.

## Shape

| Token | Utility | Radius |
| --- | --- | --- |
| `--md-sys-shape-corner-none` | `rounded-corner-none` | 0 |
| `--md-sys-shape-corner-extra-small` | `rounded-corner-extra-small` | 4px |
| `--md-sys-shape-corner-small` | `rounded-corner-small` | 8px |
| `--md-sys-shape-corner-medium` | `rounded-corner-medium` | 12px |
| `--md-sys-shape-corner-large` | `rounded-corner-large` | 16px |
| `--md-sys-shape-corner-extra-large` | `rounded-corner-extra-large` | 28px |
| `--md-sys-shape-corner-full` | `rounded-corner-full` | 9999px |

The `rounded-*` names that Tailwind ships (`rounded-md`, `rounded-xl`, and so on) still
exist. Prefer the `rounded-corner-*` names in new code.

## Elevation

`shadow-elevation-0` to `shadow-elevation-5` map to `--md-sys-elevation-level0` to
`level5`. Each level is a key shadow plus an ambient shadow. Level 0 is `none`.

## State layers and focus

- `md-state-layer` is a utility. It adds a translucent overlay in the element's own text
  colour (`currentColor`). Hover is 8%, focus-visible and pressed are 12%, and the dragged
  state is 16%. The values are tokens: `--md-sys-state-hover-opacity`,
  `--md-sys-state-focus-opacity`, `--md-sys-state-pressed-opacity` and
  `--md-sys-state-dragged-opacity`. The element needs a border radius, which it can inherit.
- The dragged state is the `is-dragged` class (plugin markup may set it). The class is
  written as an attribute selector in the stylesheet, because the style gate counts a class
  selector outside the documented hooks.
- The overlay is a `::before` pseudo-element. Do not give the element its own `::before`.
- Hover only applies on devices that can hover. Disabled elements and
  `aria-disabled="true"` get no overlay.
- A variant can drive the state of another element. The state utilities set the overlay
  opacity: `peer-hover:md-state-hover`, `group-hover:md-state-hover`,
  `peer-focus-visible:md-state-focus`, `peer-active:md-state-pressed`, and `md-state-dragged`.
  A control that keeps its own opacity (a checkbox, a radio, a switch) uses the opacity keys
  `opacity-md-state-hover`, `opacity-md-state-focus` and `opacity-md-state-pressed` instead.
- `md-focus-ring` draws the MD3 focus ring on `:focus-visible`: a 3px outline in the
  `secondary` role, drawn 2px outside the element's edge. The width and the offset are the
  tokens `--md-sys-focus-ring-width` (3px) and `--md-sys-focus-ring-offset` (2px).
- `md-focus-ring-inset` is the same ring drawn inside the edge, with the offset equal to the
  negated width. Use it for an item that sits flush against the window edge, such as the
  navigation rail, where the outset ring would be clipped.
- `md-focus-ring-within` draws the ring of a container while a child has keyboard focus:
  `has-focus-visible:md-focus-ring-within`. A radio card and the search field use it.
- The focus ring is for keyboard focus only. A pointer click on a navigation rail item
  blurs that item, so no ring is left behind; a keyboard activation keeps its focus.
- The disabled colours of a control use `bg-on-surface/md-disabled-container` and
  `text-on-surface/md-disabled-content`, which are the MD3 disabled opacities (12% and 38%).

## Touch target

Visual size and hit area are separate, as in MD3 and material-web:

- A button is a 40px container, a standard or filled icon button is 40 by 40 px with a
  24px icon, and a chip is 32px. The visual size is what a control shows.
- `md-touch-target` is a utility. It gives the element a hit area of at least 48 by 48 px,
  the token `--md-sys-touch-target-size`. It is an invisible box centred on the element,
  drawn with `::after`. It takes no space and paints nothing.
- The element needs `position: relative`, which the utility sets. `::before` stays
  reserved for the state layer, so the two never clash.

## Motion

Easing (`ease-md-*`): `standard`, `standard-decelerate`, `standard-accelerate`,
`emphasized`, `emphasized-decelerate`, `emphasized-accelerate`.

Durations: Tailwind 4.3 has no theme namespace for durations (a `--duration-*` token
generates no utility; checked by compiling a scratch stylesheet). Each motion duration token
therefore has a `duration-md-<name>` utility: `short1` to `short4` (50 to 200ms),
`medium1` to `medium4` (250 to 400ms), `long1` to `long4` (450 to 600ms) and `extra-long1` to
`extra-long4` (700 to 1000ms, for the largest transitions). The utility sets the transition
duration and the `--tw-duration` value that the transition utilities read. The older form
`duration-(--md-sys-motion-duration-short3)` still works and is not rewritten.

Animations are `--animate-*` tokens built from the motion tokens, with their keyframes next
to them, in `shape-elevation-motion.css`:

- `animate-md-linear-slide` (1.6s) and `animate-md-linear-slide-reduced` (4s), for the linear
  progress indicator. Under `prefers-reduced-motion` the indicator uses the slower token.
- `animate-md-snackbar-enter` and `animate-md-snackbar-leave`. The snackbar still runs its own
  transitions until the component catalogue change rewrites it, so these tokens are not used
  yet.
- `animate-skeleton-fade-in` (800ms, with a 200ms delay), for the skeleton bars. Its timing
  function is `ease-out`; a motion easing token would change the curve.

Reduced motion is one rule, in the `base` layer of `src/style.css`. It shortens every
animation and transition of the dashboard to 0.01ms, so `md-state-layer` and the linear
progress animation respect `prefers-reduced-motion` without a rule of their own.

## Primitives

All primitives live in `src/components/md/` and are imported directly. Each one is a single
file with a unit test next to it. None of them has runtime dependencies beyond Vue.

### MdButton

Props:

- `variant`: `'filled'` (default), `'tonal'`, `'outlined'`, `'text'`, `'elevated'`. A
  filled button is the primary action, and there is one per view
- `error`: boolean. The one way to make a destructive action (delete, revoke, discard).
  It recolours the chosen variant with the error roles: filled uses `bg-error` and
  `text-on-error`, tonal `error-container`, outlined `border-error` and `text-error`, text
  and elevated `text-error`. Disabled buttons keep the disabled colours. Pair it with
  `variant="text"` for a low-emphasis destructive action, or with the default filled variant
  for the confirming step, which counts as the view's filled button
- `variant="error"`: compatibility alias for a filled button with `error`. New code uses
  `error`
- `href`: string. When set, the root is an `<a>`, otherwise a `<button>`
- `disabled`: boolean. A button gets the `disabled` attribute; an anchor gets
  `aria-disabled="true"` and loses its `href`
- `type`: `'button'` (default). Only applies to the `<button>` form

Slots: `default` (the label), `icon` (decorative, hidden from assistive technology).

The container is 40px tall with 24px horizontal padding, or 16px on the leading edge when
an icon is present. The touch target is 48px (see Touch target).

Listeners such as `@click` pass through to the root element.

### MdIconButton

Props:

- `variant`: `'standard'` (default), `'filled'`, `'tonal'`, `'outlined'`, `'error'`. The
  `error` variant is a standard button in the `error` role (`text-error`) for destructive
  row actions such as delete or revoke
- `href`, `disabled`, `type` as in MdButton
- `toggle`: boolean. Turns the button into a toggle that reports `aria-pressed`
- `selected`: boolean. The toggle state, used only with `toggle`

Requires `aria-label`. Without it the component logs a `console.warn` on mount.

Slot: `default` (the icon). The container and state layer are 40 by 40 px, the icon is
24px, and the touch target is 48 by 48 px.

### MdCard

Props:

- `variant`: `'filled'` (default), `'elevated'`, `'outlined'`

Slot: `default`. The card adds the 12px corner radius and 16px padding.

### MdTextField

Props:

- `modelValue`: string, for `v-model`
- `label`: string, required. Rendered as `<label for>` tied to the input
- `variant`: `'filled'` (default) or `'outlined'`
- `supportingText`: string, shown under the field
- `error`: boolean. Sets `aria-invalid="true"`, switches the field to the error colours,
  and shows `errorText` in place of `supportingText`
- `errorText`: string
- `id`: optional. Generated when not given
- `type`, `name`, `autocomplete`, `disabled`, `required`: passed to the `<input>`

Emits `update:modelValue`. Slots: `leading-icon`, `trailing-icon` (both decorative).

- Focus: the active indicator (filled) and the outline (outlined) are 2px in the `primary`
  role, the MD3 reference value.
- Icons sit 12px from the edge. The input text keeps its 48px inset when a leading icon is
  present.
- Disabled, per MD3: the filled container is `on-surface` at 4%, the filled active
  indicator is `on-surface` at 38%, the outlined outline is `on-surface` at 12%, and the
  input text, label, supporting text and icons are `on-surface` at 38%.

The label floats through the peer pattern, and that pattern needs the input's placeholder
to be a space. A real `placeholder` is therefore not supported.

Attributes that are not props are forwarded to the `<input>`, where the browser enforces
them. That covers `min`, `max`, `step`, `inputmode`, `pattern`, `maxlength` and `spellcheck`,
for example `<MdTextField type="date" :min="today" />`. `class` and `style` stay on the
wrapper that holds the field, so layout classes do not end up on the input.

### MdSelect

An outlined select for a fixed set of values. It is a native `<select>`, so the browser
supplies the keyboard and the mobile picker.

Props:

- `modelValue`: string, for `v-model`. Matches the `value` of one option
- `label`: string, required. The label always floats on the top edge of the 56px field
- `options`: array of `{ value, label }`, required
- `supportingText`: string, shown under the field
- `error`: boolean. Sets `aria-invalid="true"`, switches the field to the error colours and
  shows `errorText` in place of `supportingText`
- `errorText`: string
- `id`: optional. Generated when not given
- `disabled`, `required`: passed to the `<select>`

Emits `update:modelValue` with the value of the chosen option as text. The settings form
maps that text back to the enum value it stands for. Attributes that are not props go to the
`<select>`; `class` and `style` stay on the wrapper.

### MdChip

Props:

- `variant`: `'assist'` (default) or `'filter'`
- `selected`: boolean. For `filter` only: the chip has `role="checkbox"` and
  `aria-checked`, and shows a check mark while selected
- `href`: string. For `assist` only: renders an `<a>`
- `disabled`: boolean

Slots: `default` (the label), `icon`. The chip container is 32px tall, and its touch target
is 48px (see Touch target).

### MdSwitch

Props:

- `modelValue`: boolean, for `v-model`
- `disabled`: boolean
- `id`: optional. Generated when not given

Emits `update:modelValue`. The default slot is the label, tied to the switch through
`<label for>`. Attributes that are not props, such as `aria-labelledby`, go to the switch
element. `class` and `style` stay on the root.

The switch is a `<button role="switch">` with `aria-checked`. The track is 52 by 32px with a
2px outline when off. The handle is 16px when off, 24px when on and 28px while pressed. The
40px state layer sits around the handle: hover 8%, focus and pressed 12%. A disabled switch
uses on-surface at 12% for the track and at 38% for the handle, as the MD3 token reference
sets it.

### MdCheckbox

Props:

- `modelValue`: boolean, for `v-model`
- `indeterminate`: boolean. Shows a dash in place of the check mark
- `error`: boolean. Draws the box in the error role and sets `aria-invalid`
- `disabled`: boolean
- `id`: optional. Generated when not given

Emits `update:modelValue`. The default slot is the label. The control is a real
`<input type="checkbox">`, transparent and covering a 48px area over the 40px state layer.
The visible box is 18px with a 2px outline, and it fills with `primary` when checked. A
disabled checkbox uses on-surface at 38%. Attributes that are not props go to the input.

### MdNavigationBar

A bottom or top bar for three to five destinations. It fits the dashboard's five tabs.

Props:

- `items`: array of `{ key, label, href? }`, required. With `href` the item is a link;
  without it, a button
- `modelValue`: string. The key of the active item. It gets `aria-current="page"`
- `label`: string, the landmark name. Defaults to `'Primary'`

Emits `update:modelValue` with the key of the clicked item. Slot `icon` receives
`{ item, active }`.

80px `surface-container` bar with a 64 by 32 `secondary-container` pill and a 24px icon. Labels are always shown. A plain click on a link emits `update:modelValue` and keeps the page; a modified click is left to the browser.

### MdNavigationRail

Same props and events as `MdNavigationBar`. 96px `surface-container` rail, 56 by 32 pill, 56px items with the label under the icon, first item 44px from the top. Used from 600px.

### Adaptive navigation

Below 600px the bar is fixed at the bottom and respects `env(safe-area-inset-bottom)`; the page reserves its height. From 600px the rail is fixed at the left and the page is offset by 96px, header included. From 600px the content is a `surface` sheet with rounded corners inside the frame of rail and top bar, and the sticky corner pieces under the top bar finish the sheet's corners. Switch with the window classes `max-medium:` / `medium:` (see Window margins), never with JavaScript. A user with one section gets no navigation.

The page publishes the bar height as `--app-bottom-bar-height` on the document root (80px with a
bar, 0px without). The snackbar adds it to its bottom margin below 600px, so it sits above the bar.

### Administration tree

From 1240px the tree is a column beside the page, 208px wide. It is sticky only while its full height fits between the top bar and the save bar; otherwise it scrolls with the page. It never has a scroll box of its own, in either state. The decision is `treeFits` in `src/helpers/treeFits.js` (a pure function), taken by `useTreeFits` when the tree or the window changes size and when the save bar appears. It is never taken on scroll, so the tree does not switch between sticky and static under the pointer.

Below 1240px the tree is the index of a page. Each row shows the entry's description under its label, clamped to two lines with the full text in the DOM; a row without a description shows its label only. Beside the page the rows show labels only.

From 600px to 1239px the page header has a Sections button beside the back arrow. It opens the same tree in an `MdSideSheet` titled "Administration pages". Below 600px no button is drawn, and the index is the page. A page without a back arrow, the index at `/settings`, draws no button.

### Side sheet (`MdSideSheet`)

A modal panel from the leading edge, 320px wide or the window less 56px, whichever is smaller, over a scrim. It has `role="dialog"` and `aria-modal="true"`, and is named by its visible title. On open, focus moves to the first focusable element, and Tab and Shift+Tab stay inside the sheet. Escape, the close button and the scrim close it and return focus to the element that opened it. A close that the page starts, such as a choice of an entry, leaves the focus where the page puts it. The fade is off under `prefers-reduced-motion`. The sheet is registered as `md-side-sheet` and the tree inside it as `side-nav-tree`, both experimental.

### Top app bar

64px, `sticky top-0`, `surface-container` at every scroll position, the same role as the rail, so the two form one frame. It has no tone change on scroll, no border, no shadow and no decorative rule: the `surface` content sheet is the edge. It holds the instance name, as a link to the start page, and the account menu, and nothing else. The instance name is not a heading; each page has its own level one heading.

### MdLinearProgress

Props:

- `value`: number from 0 to 1. Clamped into that range
- `indeterminate`: boolean. Slides the bar and omits `aria-valuenow`
- `label`: string, the accessible name. Defaults to `'Progress'`

Renders `role="progressbar"` with `aria-valuenow` as a percentage.

### MdSnackbar

The notification of the dashboard. It shows one notice at a time from a shared queue
(`src/store/snackbar.js`), and it is mounted once, in `src/App.vue`. Do not mount a second
one.

Callers do not use the component directly. They use the helpers in `src/helpers/toast.js`:

- `createSuccessToast(message)`, `createInfoToast(message)`: leave by themselves after 4
  seconds
- `createWarningToast(message)`: leaves after 6 seconds
- `createErrorToast(message)`: leaves after 10 seconds
- `errorMessage(error)`: the readable text of a failed request

Every notice leaves by itself, so a persistent failure cannot hold up the notices behind it.
A notice with an action stays until it is dismissed or the action is taken.

Each helper also takes `{ action: { label, onClick }, duration }` as its second argument.
The action runs its callback and then dismisses the notice. `duration` overrides the default
for the tone, and `null` keeps the notice until it is dismissed.

Queue rules:

- One notice is visible at a time. The waiting line holds at most three notices. When it is
  full, the oldest waiting non-error notice is dropped. Errors are never dropped, so the line
  can grow past three when it holds only errors.
- A notice identical to the visible one (same text and tone) restarts its timer and is not
  added again. A notice identical to the last waiting one is not queued twice.

Visuals: `bg-inverse-surface` with `text-inverse-on-surface`, `md-body-medium`, the
`rounded-corner-extra-small` corner and `shadow-elevation-3`. The action label is
`text-inverse-primary`. The snackbar sits at the bottom centre with a 16px inset, and at the
bottom left from the `md` breakpoint. It is at most 560px wide. It stacks above dialogs
(`z-[1100]`, while dialogs use `--md-sys-z-dialog`).

Accessibility: errors are `role="alert"`, everything else is `role="status"` with
`aria-live="polite"`. The close button is named "Dismiss notification".

Do not show a success notice for something the header already confirms, such as a sign-in.

### MdRadio

Props:

- `value`: required. The value of this radio
- `name`: required. Radios that share a `name` form one group. The name is required so that
  no radio is left out of its group by accident
- `modelValue`: the value of the group, for `v-model`. The radio is checked when it equals
  `value`
- `disabled`: boolean
- `card`: boolean. Makes the whole option one clickable card (see below)
- `id`: optional. Generated when not given

Emits `update:modelValue` with its own `value` when it is chosen. Attributes that are not props,
such as `aria-describedby`, go to the input. `class` and `style` stay on the root, so a
consumer can give the card its border and colours.

Usage, as a group inside a `<fieldset>` with a `<legend>`:

```vue
<fieldset>
  <legend class="md-title-medium">Who can read it</legend>
  <MdRadio
    v-for="option in options"
    :key="option.value"
    v-model="visibility"
    name="visibility"
    :value="option.value"
  >
    {{ option.label }}
  </MdRadio>
</fieldset>
```

Keyboard: the group gets one tab stop. Down and Right move to the next option, Up and Left to
the previous one, both wrap, and disabled options are skipped. The focus follows the selection.
The component handles the arrow keys itself, so every browser behaves the same and the default
is prevented.

Focus: the focus ring is shown only for keyboard focus, as a 3px outline in the secondary role
around the circle. In the card variant it goes around the whole card instead. A pointer click
leaves no ring behind.

### MdRadio card variant

`card` makes the whole option one clickable target. The root becomes a `<label>` around the
input, so a click on the card, on its text or on its empty area selects the option. Use it for
option cards with an explanation, such as visibility or access choices. Without `card`, only the
label text and the circle select the option.

The card gives its own look through classes on the root. The chosen card uses the
`secondary-container` background and `on-secondary-container` text, with a 1px `primary`
border. An unchosen card has an `outline-variant` border and `on-surface` text:

```vue
<MdRadio
  v-for="option in options"
  :key="option.value"
  v-model="visibility"
  name="visibility"
  :value="option.value"
  card
  class="rounded-corner-medium border p-3"
  :class="visibility === option.value ? 'border-primary bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface'"
>
  <span class="flex min-w-0 flex-col gap-0.5">
    <span class="md-title-small">{{ option.label }}</span>
    <span class="md-body-medium">{{ option.who }}</span>
  </span>
</MdRadio>
```

The circle stays at the top left of the card. The slot content sits to its right. A disabled
card has a `not-allowed` cursor and dims its text; it cannot be chosen. The keyboard focus ring
is drawn around the whole card.

## Administration information architecture

The Administration tree is the one navigation of the operator area. These rules hold for every page in it:

- **One home per page.** A page is listed under one heading and with one label. A link from another page is a shortcut inside
  that page, never a second entry in the tree.
- **Two levels.** The tree is a list of groups, each a heading over a list of pages. The headings are Artifacts, Access,
  Instance and Monitoring and support. A group keeps its id, the value a plugin manifest names (Repository, Security, System,
  Support), and only the heading on screen changes (`GROUP_HEADINGS` in `src/navigation/registry.js`).
- **Group headings.** A heading is set in the primary colour at title-small, and every group after the first starts with a
  1 pixel `outline-variant` rule above it, so a group reads as a block rather than as one more entry.
- **Order.** Entries that belong together are adjacent: Audit settings follows Audit log. The pages that are not settings
  domains keep their order relative to each other, because the rail's Administration entry opens the first page the visitor
  may open.
- **Labels name the job.** A label is the task the operator comes for ("Reverse proxy", "Branding", "Cleanup policies"). One
  term names one concept, and a renamed label keeps its old word as a filter keyword.
- **Descriptions.** Each entry has one line, written from the fields the page really has. It sits under the heading. An entry
  without a description draws no empty line.
- **Page header.** Above the columns: the trail (Administration, then the group as plain text, and on a detail page the list
  page as a link), the heading that names the page, the description, and the page actions with the shortcuts on the right.
  Below 1240 pixels the trail is its last crumb, beside a 48 pixel back arrow.
- **Save bar.** The notice that saved changes reach every instance within 10 seconds is the supporting text of the save bar and
  part of the save confirmation. It is not the description of a page.
- **No filled button in the header of a page that can show a save bar.** The save bar carries the primary action.

## Known gaps

Gaps that are deliberately left for the views that own them are listed here:

- No tabs, list, dialog or drawer primitive under `src/components/md/`. Views that need one build it in their own folder until a primitive is added here.
- The information architecture of the Administration area (its page header, its breadcrumb and the order of its entries) has no rules in this document until `rework-administration-information-architecture` adds them.
- The surface-dim of the dark theme is `#0d141c`, the same value as the surface role, so a
  surface that is meant to sit below the default cannot be told apart from the page. The
  value is kept by the token change; `fix-surface-hierarchy-and-page-header` changes it.

## Window margins

The MD3 window size classes are custom variants, not theme breakpoints, on purpose: Tailwind
derives the width steps of its built-in `container` from the theme breakpoints, and the
window classes would have added steps to every centred page.

| Class | Window |
| --- | --- |
| `max-medium:` | below 600px (compact) |
| `medium:` | from 600px (medium) |
| `max-expanded:` | below 840px |
| `expanded:` | from 840px |
| `large:` | from 1240px |
| `xlarge:` | from 1600px |

A pixel literal such as `min-[600px]:` is not used in the components; a test scans the sources.
`max-medium:` is below 600px. Tailwind writes `max-[599px]:` as below 599px, so a window of
exactly 599 pixels follows the medium class and not the old literal. The JavaScript breakpoint
in `IndexPage.vue` stays a literal, because a CSS token cannot feed `matchMedia`.

The `container` utility is the page gutter. Its side padding follows the MD3 window margins:
16px below 600px (compact), 24px from 600px (medium and expanded). It is an `@utility`, so a
view's own `px-*` utility would still win and override the gutter. Do not add `px-*` to a
`container` element unless the gutter should differ from the page. Every view and the header
use the shared gutter; a test reads their sources and fails if a `container` element carries
a `px-*` class. The built-in width steps of `container` (40rem to 96rem) are kept: the custom
utility adds only the gutter.

## Layout, stacking and scrim

Named sizes, each a theme key (see `shape-elevation-motion.css` and `color.css`):

| Key | Value | Use |
| --- | --- | --- |
| `z-md-sticky` | 20 | sticky elements (no view uses it yet) |
| `z-md-top-bar` | 30 | the bottom bar of a narrow window |
| `z-md-menu` | 50 | menus |
| `z-md-dialog` | 1000 (`--md-sys-z-dialog`) | dialogs; the vue-final-modal root takes the same value |
| `z-md-snackbar` | 1100 | the snackbar |
| `bg-scrim/md-scrim` | 32% | the overlay of a dialog or a sheet |
| `bg-scrim/md-scrim-strong` | 40% | the migration dialog |
| `size-md-icon` | 18px (`1.125rem`) | icons of the primitives |
| `top-md-top-bar`, `min-h-md-top-bar` | 4rem | the top bar |
| `w-md-rail` | 6rem | the navigation rail |
| `--spacing-md-bottom-bar` | 5rem | the bottom bar, read as `var(--spacing-md-bottom-bar)`; `BOTTOM_BAR_HEIGHT` in `src/helpers/bottom-bar.js` must equal it in pixels (80px) |
| `max-w-md-reading` | 60rem (960px) | the reading measure of a page |
| `--container-md-sheet` | 20rem (320px) | the side sheet, read as `var(--container-md-sheet)` in its width |
| `max-w-md-snackbar` | 35rem (560px) | the snackbar |

The layout keys are spacing or container keys, so `calc()` takes them as
`var(--spacing-md-top-bar)`, `var(--container-md-sheet)` and so on.

The pane corners that finish the content sheet are `pane-corner`, with `pane-corner-start`
on the left edge and `pane-corner-end` on the right. `md-router-view` is the minimum height
of a page below the top bar. `skeleton-bars` fades in its bars. `md-embedded-input` removes
the field box of an input inside a composite field. `olf-brand-bar` is a 4px band at the top
of a section and is kept for plugin markup.

## Tailwind 4.3 facts

Checked against the installed `tailwindcss` 4.3.3 by compiling a scratch stylesheet:

- No `--duration-*` namespace: `duration-md-short2` is not generated from a theme token, so
  the duration utilities are written out (see Motion).
- `--z-index-*`, `--opacity-*`, `--container-*`, `--spacing-*` and `--animate-*` produce the
  `z-*`, `bg-*/<key>` and `opacity-*`, `max-w-*`, `w-*`, `top-*`, `size-*` and `animate-*`
  utilities.
- The built-in `container` derives its width steps from the theme breakpoints, which are the
  default `sm` to `2xl` steps here. The window classes are custom variants, so the steps do not
  change; `@utility container` adds the gutter beside them.
- `@utility` works with a variant, for example `peer-hover:md-state-hover`.
- `max-[599px]:` compiles to `width < 599px`, and `max-medium:` to `width < 600px`. This is the
  one-pixel difference of the window classes.

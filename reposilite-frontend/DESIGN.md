# Ingot design system

The dashboard is styled with Tailwind CSS 4 on top of a Material Design 3 (MD3) token set.
The names in this document are the contract that views and components build on. Keep them
stable: renaming a token breaks every view that uses it.

Source files:

- `src/styles/md3/color.css`: colour roles, light and dark, plus two status colours
- `src/styles/md3/typography.css`: type scale and the `md-*` text classes
- `src/styles/md3/shape-elevation-motion.css`: corner radii, elevation, easing and durations
- `src/styles/md3/state-layer.css`: state layers and the focus ring
- `src/components/md/`: primitives
- `src/style.css`: imports the files above, sets the `dark` variant, and holds the few
  global rules

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

## Colour

Seed `#EC008B`, the OneLiteFeather Pink that the brand uses as its Material 3 source colour.
The scheme is MD3 `SchemeVibrant` at contrast 0, and the accent roles are the official OLF
tables from the brand skill (`references/material3.md`). The same generator, the Python
`materialyoucolor` package, reproduces those tables exactly, so the values can be checked.
The `@material/material-color-utilities` package for JavaScript does not match them and was
not used for the accents. Values live in `src/styles/md3/color.css`:

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

### Neutral surfaces (override)

The official table is strongly pink: its light surface is `#fff4f7` and its surface-variant
`#ffcfeb`. The project does not want tinted surfaces, so the neutral roles are not taken
from the table. They come from a near-neutral palette at the source hue (357) with chroma 3,
and the variant roles (`surface-variant`, `on-surface-variant`, `outline`, `outline-variant`)
from the same hue at chroma 6. Chroma 3 is low enough to read as grey and high enough that a
surface does not look dead next to the pink accents. Each role takes its tone from the
standard MD3 mapping:

| Theme | Surfaces | Text and outline |
| --- | --- | --- |
| Light | `surface`, `background`, `surface-bright` T98; `surface-container-lowest` T100, `-low` T96, `-container` T94, `-high` T92, `-highest` T90; `surface-dim` T87 | `on-surface` T10; `on-surface-variant` T30; `outline` T50; `outline-variant` T80; `surface-variant` T90 |
| Dark | `surface`, `background`, `surface-dim` T6; `surface-container-lowest` T4, `-low` T10, `-container` T12, `-high` T17, `-highest` T22; `surface-bright` T24 | `on-surface` T90; `on-surface-variant` T80; `outline` T60; `outline-variant` T30; `surface-variant` T30 |

The inverse roles follow the same pattern: `inverse-surface` is T20 in light and T90 in
dark, and `inverse-on-surface` is T95 and T20.

The brand charcoal `#1f1f23` is at tone 12. The dark `surface-container` is also at tone 12,
so the charcoal of the brand matches that step, and the dark `surface` (tone 6) sits one step
darker than it. Light `surface` is `#fff8f8`, near white.

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
`--olf-gradient` custom property in `color.css` is the four-stop standard from the brand guide
at 135 degrees: `#f7931d`, `#ec008b`, `#91268f`, `#2a388f`. Use it for brand areas only:

- `.olf-brand-bar` is a 4px band at the top edge of a section. The Overview hero draws one,
  in `HeaderHero.vue`.
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
  neutrals of a generated theme come from the seed's hue, not from the near-neutral override
  described above.
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
to tell apart as series, so the chart tokens are eight hues spaced about 45 degrees apart on
the colour wheel. The first series is the OLF pink itself, and the others are the orange,
gold, green, teal, cyan, blue and violet families that sit beside it.

Each token is a CSS custom property, `--md-ext-color-chart-1` to `-8`, in `color.css`:
light values on `:root`, dark values on `.dark`. They have no Tailwind utility, because a
chart reads them through `src/store/palette.js` (`categorical()`), not through a class.

Generation: each hue was harmonised toward `#ec008b` with `Blend.harmonize` from
`@material/material-color-utilities`, then tone 40 (light) and tone 80 (dark) were taken from
its `TonalPalette`. The first series is the seed itself. Harmonising pulls each hue a few
degrees toward the pink, so the hue markers were placed before harmonising to land on their
names. The generator ran as a throwaway script outside the project, and the library is not a
project dependency. The hue search was run over a pool of hue markers, and the eight that
maximised the smallest colour difference were kept.

| Series | Token | Hue (harmonised, HCT) | Light (tone 40) | Dark (tone 80) | Light contrast on `surface` | Dark contrast on `surface` |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `chart-1` | pink, 357 | `#b8006b` | `#ffb0cc` | 6.16:1 | 10.91:1 |
| 2 | `chart-2` | cyan, 240 | `#006590` | `#88ceff` | 6.14:1 | 10.90:1 |
| 3 | `chart-3` | gold, 75 | `#815500` | `#feba50` | 6.20:1 | 10.93:1 |
| 4 | `chart-4` | green, 120 | `#566500` | `#bad14d` | 6.15:1 | 10.90:1 |
| 5 | `chart-5` | teal, 195 | `#006a68` | `#69d8d5` | 6.14:1 | 10.96:1 |
| 6 | `chart-6` | orange, 47 | `#9c4504` | `#ffb690` | 6.13:1 | 10.93:1 |
| 7 | `chart-7` | blue, 280 | `#4253c3` | `#bbc3ff` | 6.15:1 | 10.94:1 |
| 8 | `chart-8` | violet, 315 | `#8040ad` | `#e3b5ff` | 6.16:1 | 10.90:1 |

Light `surface` is `#fff8f8`, dark `surface` is `#161213`. Every series clears 3:1 on both,
and on `surface-container-highest` as well, since the test checks it. Between the series,
the smallest CIE76 difference is 21 in light (orange, series 6, and gold, series 3) and 19 in
dark (cyan, series 2, and blue, series 7). The previous copper palette managed 28 and 17.
Series are assigned in the order above, and every pair is checked, adjacent or not. Orange is
series 6 rather than 2, because in dark mode a pale pink and a pale peach read as one series.
Cyan takes series 2, so the first three series (pink, cyan and gold) stay apart in both themes. A legend still needs its labels, because colour
alone does not identify a series for everyone.

The colour tests in `src/styles/md3/color.test.js` hold the rules above: 3:1 on the surfaces,
15 CIE76 units between any two series, and a pink first series. `src/store/palette.test.js`
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

- `md-state-layer` adds a translucent overlay in the element's own text colour
  (`currentColor`). Hover is 8%, focus-visible and pressed are 12%, and the dragged
  state (`is-dragged`) is 16%. These are the MD3 state opacities (`md-sys-state`). The
  element needs a border radius, which it can inherit.
- The overlay is a `::before` pseudo-element. Do not give the element its own `::before`.
- Hover only applies on devices that can hover. Disabled elements and
  `aria-disabled="true"` get no overlay.
- `md-focus-ring` draws the MD3 focus ring on `:focus-visible`: a 3px outline in the
  `secondary` role, drawn 2px outside the element's edge (`outline-offset: 2px`).

## Touch target

Visual size and hit area are separate, as in MD3 and material-web:

- A button is a 40px container, a standard or filled icon button is 40 by 40 px with a
  24px icon, and a chip is 32px. The visual size is what a control shows.
- `md-touch-target` gives the element a hit area of at least 48 by 48 px. It is an
  invisible box centred on the element, drawn with `::after` (`min-width` and
  `min-height` 48px). It takes no space and paints nothing.
- The element needs `position: relative`, which the primitives set. `::before` stays
  reserved for the state layer, so the two never clash.

## Motion

Easing (`ease-md-*`): `standard`, `standard-decelerate`, `standard-accelerate`,
`emphasized`, `emphasized-decelerate`, `emphasized-accelerate`.

Durations are not theme-driven in Tailwind 4, so reference the variable directly:
`duration-(--md-sys-motion-duration-short3)`. Available steps are `short1` to `short4`
(50 to 200ms), `medium1` to `medium4` (250 to 400ms), `long1` to `long4` (450 to 600ms) and
`extra-long1` to `extra-long4` (700 to 1000ms, for the largest transitions).

`md-state-layer` and the linear progress animation respect `prefers-reduced-motion`.

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

Same props and events as `MdNavigationBar`. 80px `surface` rail, 56 by 32 pill, 56px items with the label under the icon, first item 44px from the top. Used from 600px.

### Adaptive navigation

Below 600px the bar is fixed at the bottom and respects `env(safe-area-inset-bottom)`; the page reserves its height. From 600px the rail is fixed at the left and the page is offset by 80px, header included. Switch with the CSS breakpoints `max-[600px]:` / `min-[600px]:`, never with JavaScript. A user with one section gets no navigation.

The page publishes the bar height as `--app-bottom-bar-height` on the document root (80px with a
bar, 0px without). The snackbar adds it to its bottom margin below 600px, so it sits above the bar.

### Top app bar

64px, `sticky top-0`, `surface`. Takes `surface-container` once the window has scrolled (`useWindowScroll`), with a `short4` colour transition. No shadow change.

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

## Known gaps

None at present. Gaps that are deliberately left for the views that own them are listed here.

## Window margins

The `container` class is the page gutter. Its side padding follows the MD3 window size
classes: 16px below 600px (compact), 24px from 600px (medium and expanded). The rule is
inside `@layer components`, so a view's own `px-*` utility would still win and override
the gutter. Do not add `px-*` to a `container` element unless the gutter should differ from
the page. Every view and the header use the shared gutter; a test reads their sources and
fails if a `container` element carries a `px-*` class.

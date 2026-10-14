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
- **Touch targets are at least 48px.** The primitives already do this; a custom control
  needs `min-h-12` (48px) or larger.
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

Generated from the seed `#B5651D` (copper) with the MD3 `SchemeTonalSpot` variant. Values
live in `src/styles/md3/color.css`:

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

Status colours that MD3 does not define. Use them for text only:

| Token | Utility | Light | Dark |
| --- | --- | --- | --- |
| `--md-ext-color-success` | `text-success` | `#166534` | `#4ade80` |
| `--md-ext-color-warning` | `text-warning` | `#854d0e` | `#eab308` |

Use the `error` roles for failures. `success` and `warning` are for confirmations and
non-fatal notices only.

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
  (`currentColor`). Hover is 8%, focus-visible and pressed are 10%. Add `is-dragged` for
  the 16% dragged state. The element needs a border radius, which it can inherit.
- The overlay is a `::before` pseudo-element. Do not give the element its own `::before`.
- Hover only applies on devices that can hover. Disabled elements and
  `aria-disabled="true"` get no overlay.
- `md-focus-ring` draws a 2px `primary` outline on `:focus-visible`, offset by 2px.

## Motion

Easing (`ease-md-*`): `standard`, `standard-decelerate`, `standard-accelerate`,
`emphasized`, `emphasized-decelerate`, `emphasized-accelerate`.

Durations are not theme-driven in Tailwind 4, so reference the variable directly:
`duration-(--md-sys-motion-duration-short3)`. Available steps are `short1` to `short4`
(50 to 200ms), `medium1` to `medium4` (250 to 400ms) and `long1` to `long4` (450 to 600ms).

`md-state-layer` and the linear progress animation respect `prefers-reduced-motion`.

## Primitives

All primitives live in `src/components/md/` and are imported directly. Each one is a single
file with a unit test next to it. None of them has runtime dependencies beyond Vue.

### MdButton

Props:

- `variant`: `'filled'` (default), `'tonal'`, `'outlined'`, `'text'`, `'elevated'`, `'error'`.
  `error` is a filled destructive action (`bg-error`, `text-on-error`), for the confirming
  step of a delete or revoke. It counts as a filled button for the one-per-view rule
- `href`: string. When set, the root is an `<a>`, otherwise a `<button>`
- `disabled`: boolean. A button gets the `disabled` attribute; an anchor gets
  `aria-disabled="true"` and loses its `href`
- `type`: `'button'` (default). Only applies to the `<button>` form

Slots: `default` (the label), `icon` (decorative, hidden from assistive technology).

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

Slot: `default` (the icon). The root is 48 by 48 px.

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

The label floats through the peer pattern, and that pattern needs the input's placeholder
to be a space. A real `placeholder` is therefore not supported.

Attributes that are not props are forwarded to the `<input>`, where the browser enforces
them. That covers `min`, `max`, `step`, `inputmode`, `pattern`, `maxlength` and `spellcheck`,
for example `<MdTextField type="date" :min="today" />`. `class` and `style` stay on the
wrapper that holds the field, so layout classes do not end up on the input.

### MdChip

Props:

- `variant`: `'assist'` (default) or `'filter'`
- `selected`: boolean. For `filter` only: the chip has `role="checkbox"` and
  `aria-checked`, and shows a check mark while selected
- `href`: string. For `assist` only: renders an `<a>`
- `disabled`: boolean

Slots: `default` (the label), `icon`.

### MdNavigationBar

A bottom or top bar for three to five destinations. It fits the dashboard's five tabs.

Props:

- `items`: array of `{ key, label, href? }`, required. With `href` the item is a link;
  without it, a button
- `modelValue`: string. The key of the active item. It gets `aria-current="page"`
- `label`: string, the landmark name. Defaults to `'Primary'`

Emits `update:modelValue` with the key of the clicked item. Slot `icon` receives
`{ item, active }`.

### MdLinearProgress

Props:

- `value`: number from 0 to 1. Clamped into that range
- `indeterminate`: boolean. Slides the bar and omits `aria-valuenow`
- `label`: string, the accessible name. Defaults to `'Progress'`

Renders `role="progressbar"` with `aria-valuenow` as a percentage.

## Known gaps

These are deliberately left for the views that own them:

- The `container` class keeps the shared page gutter (`px-10`, `px-2` below 640px). It is
  layout, not colour, and the layout work decides its final form.
- The section tabs on the index page are primary tabs built in `src/pages/IndexPage.vue`
  on the `md-state-layer` and `md-focus-ring` classes, not on `MdNavigationBar`. That
  primitive is a bottom bar with icons and a pill indicator, which does not suit a top row
  of text tabs.

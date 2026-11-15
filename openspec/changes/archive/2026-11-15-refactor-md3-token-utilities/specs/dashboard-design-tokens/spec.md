# Spec Delta

## Purpose

The dashboard's styling values come from one place: the Tailwind theme and the Material Design 3 token files. Helpers are Tailwind utilities, window sizes and layout sizes have names, and the only plain CSS selectors left are the library hooks. Nothing the user sees changes.

## ADDED Requirements

### Requirement: Named window breakpoints

The Tailwind theme of the dashboard SHALL declare the MD3 window classes as named breakpoints: `medium` at 600px, `expanded` at 840px, `large` at 1240px and `xlarge` at 1600px. Tailwind's `sm`, `md`, `lg` and `xl` breakpoints SHALL remain. A view or primitive SHALL use a named breakpoint instead of an arbitrary `min-[...]` or `max-[...]` variant with one of these values.

#### Scenario: Named breakpoint replaces a literal

- **WHEN** a component needs the medium window and above
- **THEN** it uses the `medium:` variant, and no `min-[600px]:` literal remains in the dashboard source

#### Scenario: Compact upper bound

- **WHEN** a component needs the window below 600px
- **THEN** it uses `max-medium:`, which applies below 600px, and no `max-[600px]:` or `max-[599px]:` literal remains in the dashboard source

#### Scenario: The one-pixel difference is recorded

- **WHEN** the change replaces `max-[599px]:` with `max-medium:`
- **THEN** the design record names the one-pixel difference at a window of exactly 599 pixels, and no other visible difference is accepted

#### Scenario: Defaults remain

- **WHEN** the theme is built
- **THEN** the `sm`, `md`, `lg` and `xl` breakpoints still exist with their Tailwind values

### Requirement: Window margin and container tokens

The page gutter SHALL be 16px below 600px and 24px from 600px, taken from the MD3 window margin tokens. The `container` utility SHALL set that gutter and SHALL keep the centred width ladder of the page at its current steps, so that the new breakpoints do not change the width of any page.

#### Scenario: Gutter at compact and medium windows

- **WHEN** a `container` element is drawn below 600px and from 600px
- **THEN** its horizontal padding is 16px below 600px and 24px from 600px

#### Scenario: Width ladder unchanged

- **WHEN** a centred page is drawn at 1240px and at 1600px
- **THEN** its maximum width is the same as before the change, because the new breakpoints do not add width steps

#### Scenario: View padding still wins

- **WHEN** a view adds a `px-*` utility to a `container` element
- **THEN** the view's padding applies, because the gutter is a utility and not an unlayered rule

### Requirement: State layers and focus rings are utilities

The state layer, the focus ring, the inset focus ring and the touch target SHALL be declared with `@utility`, under the names `md-state-layer`, `md-focus-ring`, `md-focus-ring-inset` and `md-touch-target`. The focus ring and the inset focus ring SHALL share one definition, which takes an offset modifier. The state opacities, the focus ring width and offset, and the touch target size SHALL be theme or MD3 tokens.

#### Scenario: Helpers declared as utilities

- **WHEN** the stylesheet is read
- **THEN** each of the four helper names is declared with `@utility`, and no `.md-` class selector appears inside a `@layer` block

#### Scenario: Focus ring from one definition

- **WHEN** an element uses `md-focus-ring` and another uses `md-focus-ring-inset`
- **THEN** both draw a 3px ring in the secondary role on keyboard focus, offset 2px outside and 3px inside respectively, from the same declarations

#### Scenario: Touch target from a token

- **WHEN** an element uses `md-touch-target`
- **THEN** its hit area is at least the touch target token, 48px, centred on the element

### Requirement: State variants use utilities and tokens

Peer-driven, group-driven and focus-within states SHALL be expressed as a variant with a state utility, for example `peer-hover:md-state-hover`, `group-hover:md-state-hover` or `has-focus-visible:md-focus-ring-within`. The state opacities SHALL be tokens, and disabled colours SHALL use the disabled opacity tokens. No literal opacity, inline state opacity or arbitrary `has-[...]` or `peer-*` state selector SHALL remain in the primitives and the views.

#### Scenario: Checkbox and radio hover

- **WHEN** a pointer hovers over a checkbox or a radio
- **THEN** the state layer shows the hover opacity from the hover token, and the same result as before

#### Scenario: Navigation rail hover

- **WHEN** a pointer hovers over a navigation rail item
- **THEN** the item's state layer shows the hover opacity from the hover token

#### Scenario: Disabled button

- **WHEN** a button is disabled
- **THEN** its container uses the disabled container opacity token and its content uses the disabled content opacity token

#### Scenario: Focus ring from a focus-within state

- **WHEN** a keyboard user moves focus into the search field or the radio card
- **THEN** the container shows the focus ring, and a pointer click does not leave one behind

### Requirement: Motion durations and animations are tokens

Each MD3 motion duration token SHALL have a matching `duration-md-<name>` utility. The linear progress animation, the snackbar enter and leave animations and the skeleton fade-in SHALL be `--animate-*` tokens built from the motion duration and easing tokens, except for the skeleton timing function, which stays `ease-out`. The keyframes SHALL be declared next to their animation tokens.

#### Scenario: Duration utility

- **WHEN** a component sets a transition from the `short3` duration token
- **THEN** it uses the `duration-md-short3` utility

#### Scenario: Animation token

- **WHEN** the linear progress indicator animates
- **THEN** it uses the `animate-md-linear-slide` token, and under reduced motion the slower token applies instead

#### Scenario: Reduced motion

- **WHEN** a user asks for less motion
- **THEN** every animation and transition of the dashboard finishes at once, from one global rule that is written once in the stylesheet

### Requirement: Stacking, scrim, icon and layout sizes are named

The stacking order SHALL be a named scale: sticky, top bar, menu, dialog and snackbar, exposed as `z-md-*` utilities with the values in use. The scrim opacity SHALL be a token. The icon size SHALL be a spacing token of 18px. The top bar height, the navigation rail width, the bottom bar height, the reading measure, the side sheet width and the snackbar maximum width SHALL be theme tokens. `BOTTOM_BAR_HEIGHT` SHALL equal the bottom bar height token in pixels.

#### Scenario: Stacking names

- **WHEN** a menu or a dialog needs a stacking value
- **THEN** it uses a `z-md-*` utility, and the dialog value stays above the menu value

#### Scenario: Layout token

- **WHEN** a view sets the top bar height or the rail width
- **THEN** it uses the layout token and not a literal `4rem` or `6rem`

#### Scenario: Bottom bar constant matches the token

- **WHEN** the bottom bar constant is read
- **THEN** its value in pixels equals the bottom bar height token

### Requirement: Field container role is declared

The field container role SHALL be declared in the colour tokens for both themes, so that the fields that read it do not depend on a value set elsewhere, and so that the fallback argument is no longer needed.

#### Scenario: Field container in both themes

- **WHEN** the colour tokens are read in the light and the dark theme
- **THEN** `--md-field-container` is declared in both, and each reads the surface role of its theme

### Requirement: Plain selectors are documented hooks only

The stylesheet `src/style.css` and the token files under `src/styles/md3/` SHALL contain no plain class selector, except the `.dark` colour hook and the `.vfm` library hook. Every other helper SHALL be an `@utility`, or a rule of a documented hook.

#### Scenario: Only documented hooks

- **WHEN** the stylesheet and the token files are scanned for class selectors
- **THEN** the only class selectors found are `.dark` and `.vfm`

#### Scenario: Helpers in the utility layer

- **WHEN** a helper such as `pane-corner`, `skeleton-bars` or `olf-brand-bar` is declared
- **THEN** it is declared with `@utility`, and its name is the class name used in the markup

### Requirement: Every rule is in a layer and the plugin layer is last

Every rule of `src/style.css` and the token files SHALL be inside a cascade layer, except for `@import`, `@plugin`, `@custom-variant`, `@theme` and `@utility` blocks. The layer order SHALL be `theme, base, components, utilities, ingot-plugins`, and `ingot-plugins` SHALL be the last layer. The stylesheet SHALL declare this order in one statement.

#### Scenario: Plugin layer last

- **WHEN** the layer statement of `src/style.css` is read
- **THEN** `ingot-plugins` is the last name of the statement

#### Scenario: No unlayered rule

- **WHEN** the top level of `src/style.css` and the token files is read
- **THEN** no rule outside a layer, `@theme`, `@utility` or the listed at-rules is found

#### Scenario: Plugin may override a moved helper

- **WHEN** a plugin stylesheet in `ingot-plugins` sets the stacking value of the modal root
- **THEN** the plugin value applies, because the modal root's rule is in a layer that comes before `ingot-plugins`

### Requirement: Colour and motion values are written once

Each colour value that repeats in `src/styles/md3/color.css` SHALL be declared once and referred to by `var()`. The `:root` and `.dark` blocks SHALL each be a single block, and the `@theme inline` blocks of `color.css` SHALL be a single block. The reduced-motion rule SHALL appear once in the dashboard stylesheet.

#### Scenario: Repeated hex value

- **WHEN** the colour file is scanned for hex values
- **THEN** each hex value that is a role value appears once outside the `:root` and `.dark` blocks

#### Scenario: One reduced-motion rule

- **WHEN** the dashboard stylesheet and the token files are scanned for `prefers-reduced-motion`
- **THEN** one rule matches, in the base layer of `src/style.css`

### Requirement: No visible change

The rendered dashboard SHALL be identical before and after this change. Every replaced literal SHALL produce the same computed value, and any difference found by the screenshot comparison SHALL block the merge until it is explained and recorded.

#### Scenario: Screenshot comparison

- **WHEN** the dashboard, the administration repositories list, a repository detail page, a dialog and a menu are captured at 320, 600, 1440, 390, 1024 and 1920 pixels in light and dark, before and after the change
- **THEN** every pair of screenshots is identical

#### Scenario: Surface-dim is not changed

- **WHEN** the dark theme colour tokens are compared before and after the change
- **THEN** `--md-sys-color-surface-dim` has the same value as before, and the equality with the surface value is recorded as a finding for `fix-surface-hierarchy-and-page-header`

### Requirement: Anchors are unchanged

No `data-ingot-component` or `data-ingot-part` attribute SHALL be added, removed or renamed by this change. The anchor registry test SHALL pass unchanged.

#### Scenario: Anchor attributes

- **WHEN** the components touched by this change are compared before and after
- **THEN** every `data-ingot-*` attribute has the same name and the same value

#### Scenario: Registry test

- **WHEN** `src/anchors/anchors.test.js` runs
- **THEN** it passes without edits to the registry

# Spec Delta

## Purpose

The dashboard moves between its states with a small set of Material 3 transitions. This capability names the patterns, the
tokens they use, the rules that keep the layout, the focus and the reduced-motion behaviour, and the surfaces that move.

## ADDED Requirements

### Requirement: Each state change uses one named pattern

A state change of the dashboard that has a transition SHALL use one of the patterns `fade-through`, `shared-x`, `shared-y`,
`menu`, `dialog`, `sheet`, `snackbar`, `expand` and `fade`. A component SHALL name its pattern through `MdTransition` and SHALL
NOT write a transition of its own with literal values.

#### Scenario: Top-level page

- **WHEN** a visitor opens a top-level destination of the rail or the bottom bar
- **THEN** the page content uses the `fade-through` pattern

#### Scenario: Sibling page

- **WHEN** a visitor changes between two Administration entries of the same frame
- **THEN** the page column uses the `shared-x` pattern with a direction from the order of the two entries

#### Scenario: No literal value

- **WHEN** a changed component of the dashboard declares a transition
- **THEN** its duration and easing are tokens, and no `<style>` block of the change contains a literal duration

### Requirement: Durations and easings are tokens

Every duration of a pattern SHALL be one of the motion duration tokens, and every easing SHALL be one of the motion easing
tokens. A page transition SHALL take at most 300 milliseconds from the start of its leave to the end of its enter.

#### Scenario: Page transition length

- **WHEN** the `fade-through` pattern runs for a page change
- **THEN** its leave takes the `short2` token (100 milliseconds) and its enter takes the `short4` token (200 milliseconds), so the
  total is 300 milliseconds

#### Scenario: Dialog panel

- **WHEN** a dialog opens
- **THEN** its panel takes the `medium1` token (250 milliseconds) with the `emphasized-decelerate` easing

### Requirement: Transitions animate transform and opacity only

A transition SHALL animate `transform` and `opacity`. The height of an expand or a collapse SHALL be animated through
`grid-template-rows` from `0fr` to `1fr` on a wrapper, and no other property SHALL be animated.

#### Scenario: Page slide

- **WHEN** a sibling page slides in
- **THEN** the only animated properties are `transform` and `opacity`

#### Scenario: Expand

- **WHEN** a disclosure opens
- **THEN** its height is animated through `grid-template-rows` on the wrapper of its content

### Requirement: No layout shift during a transition

A transition SHALL NOT change the box of any element outside the element it animates. The side navigation, the header and the
top bar keep their positions while a page transitions.

#### Scenario: Side navigation stays still

- **WHEN** an Administration entry changes while the page column transitions
- **THEN** the side navigation has the same top and left edges before, during and after the transition

#### Scenario: Page height

- **WHEN** a page with a height different from the previous page is inserted
- **THEN** the height of the page column changes only when the new page is inserted, and no transition animates it

### Requirement: No motion on first paint

The first page a visitor opens SHALL be drawn without a transition. A transition SHALL NOT run for the initial content of a
page, a disclosure that is closed on load, or a dialog that is not open.

#### Scenario: First load

- **WHEN** the dashboard loads on a deep link
- **THEN** the page content is drawn without a transition

### Requirement: Focus moves to the page heading after the navigation

After an in-app navigation that changes the page, focus SHALL move to the first heading of the new page, or to `main` when the
page has no heading. Focus SHALL move when the new page is inserted, and SHALL NOT wait for the transition to end.

#### Scenario: Heading receives focus

- **WHEN** the visitor navigates from one top-level page to another
- **THEN** the heading of the new page has focus when the new page is inserted

#### Scenario: Focus does not wait

- **WHEN** a transition is running for a navigation
- **THEN** focus is set at the insertion of the new page and not in a hook that runs after the enter

### Requirement: An interrupted navigation cancels cleanly

When a navigation starts while another transition is running, the dashboard SHALL insert only the latest page. The interrupted page
SHALL NOT be inserted, and no transition class SHALL remain on an element after the transition has ended.

#### Scenario: Two navigations in quick succession

- **WHEN** the visitor opens a second page while the first page is leaving
- **THEN** only the second page is inserted, and it is the only page in the content area after the transition

### Requirement: Reduced motion is an instant swap

While `prefers-reduced-motion: reduce` matches, every transition of the dashboard SHALL be instant. The single reduced-motion rule
of the base layer SHALL cover every transition class, and no pattern SHALL add a transform or a duration of its own under that
media query.

#### Scenario: Page change under reduced motion

- **WHEN** a visitor prefers reduced motion and changes the page
- **THEN** the new page is drawn at once, without a transform, and focus still moves to its heading

#### Scenario: One rule

- **WHEN** the stylesheets are scanned for `prefers-reduced-motion`
- **THEN** the transition classes are covered by the rule of `src/style.css` and no second block sets a transition duration for them

### Requirement: Plugin pages take the route transition and may opt out

A plugin page SHALL take the `fade-through` pattern like any top-level page. A plugin page descriptor MAY carry the field `motion`
with the value `route` or `none`. A missing field SHALL mean `route`. With `none`, the transition into that page SHALL be an
instant swap, and focus SHALL still move to the heading of the page.

#### Scenario: Plugin page without the field

- **WHEN** a visitor opens a plugin page whose descriptor has no `motion` field
- **THEN** the page takes the `fade-through` pattern

#### Scenario: Plugin page that opts out

- **WHEN** a visitor opens a plugin page whose descriptor has `motion` set to `none`
- **THEN** the page is drawn at once and its heading has focus

#### Scenario: Unknown value

- **WHEN** a descriptor has a `motion` value other than `route` or `none`
- **THEN** the page takes the `fade-through` pattern, and the dashboard ignores the value

### Requirement: Plugin anchors keep their elements

The transitions SHALL be applied to the existing elements of the page, through the transition of Vue, and SHALL NOT add a wrapper
element. No `data-ingot-*` attribute SHALL move to another element, and no selector that names a registered component or part SHALL
match a different element after the change.

#### Scenario: Anchors after a page change

- **WHEN** a visitor opens a page with a registered anchor
- **THEN** the anchor is on the same element as before the change, and the number of elements with that anchor is unchanged

### Requirement: Menus, dialogs and sheets open from their anchor

A menu SHALL scale from `0.92` to `1` and fade in from the edge of its anchor, with its `transform-origin` at that edge. A dialog
SHALL fade its scrim and fade and scale its panel from `0.95`. A side sheet SHALL slide in from its edge. Each SHALL be off under
reduced motion, as the single rule states.

#### Scenario: Menu opens from its trigger

- **WHEN** a visitor opens a menu below its trigger
- **THEN** the menu's origin is its top edge, and it scales from `0.92`

#### Scenario: Dialog opens

- **WHEN** a visitor opens a dialog
- **THEN** the scrim fades and the panel fades and scales from `0.95`, over the `medium1` token

### Requirement: Snackbars slide up

A snackbar SHALL enter by moving up 16 pixels and fading in over the `short4` token, and leave over the `short3` token. When a new
message replaces the current one, the current message SHALL leave before the new one enters, so that two messages never overlap.

#### Scenario: Replacement

- **WHEN** a second message arrives while the first is shown
- **THEN** the first leaves, and then the second enters, and both are never drawn at once

### Requirement: Tab indicators and panels move by order

When a visitor selects another tab, the indicator SHALL slide to the selected tab, and the tab panel SHALL use the `shared-x`
pattern with the direction given by the index of the two tabs.

#### Scenario: Next tab

- **WHEN** the visitor selects a tab to the right of the current one
- **THEN** the indicator slides to the right, and the new panel enters from the right

### Requirement: Expand and collapse animate their height

A disclosure, a key tree row and a system information section SHALL animate their height on open and close through
`grid-template-rows` from `0fr` to `1fr`, over the `medium2` token. The chevron SHALL rotate over the `short3` token. A closing
disclosure SHALL keep its content until the transition has ended.

#### Scenario: Open

- **WHEN** a visitor opens a disclosure
- **THEN** its content grows from zero height to its full height over the `medium2` token

#### Scenario: Close

- **WHEN** a visitor closes a disclosure
- **THEN** its content stays until the transition has ended, and then the disclosure is closed

### Requirement: Loaded content fades in over the skeleton

When the content of a loading state arrives, the skeleton SHALL leave over the `short2` token and the content SHALL enter over the
`short4` token. The change SHALL NOT wait on a delay of its own, and the existing 200 millisecond delay of the skeleton bars SHALL
stay.

#### Scenario: Content arrives

- **WHEN** a list or a page leaves its loading state
- **THEN** the content fades in and the skeleton leaves, and no visitor waits on a delay of the transition

### Requirement: Sign-in second step steps on the shared Y axis

The second step of a password sign-in SHALL enter with the `shared-y` pattern inside its dialog, and its heading SHALL have focus as
it does today.

#### Scenario: Second step

- **WHEN** a password sign-in asks for a second factor
- **THEN** the step enters from below by 8 pixels, and its heading has focus

### Requirement: Motion is documented for plugin authors

The dashboard design document and the plugin documentation SHALL describe the patterns, the `motion` field of a plugin page
descriptor and the reduced-motion behaviour, and SHALL state that a plugin page takes the route transition unless it opts out.

#### Scenario: Plugin author reads the docs

- **WHEN** a plugin author looks up how a page transitions
- **THEN** the documentation names the `fade-through` pattern, the `motion` field and its values, and the reduced-motion behaviour

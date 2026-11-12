# Spec Delta

## Purpose

The top bar of the dashboard carries the instance name and the account menu. It separates itself from the content by tone,
draws no decorative line, and leaves the page's level one heading to the page.

## ADDED Requirements

### Requirement: Top bar draws no decorative rule

The top bar SHALL NOT draw a gradient rule, a border or any other line along any of its edges, on any page and in any theme,
forced colours included. The `--olf-gradient` token and the `.olf-brand-bar` class SHALL remain defined, and an operator seed
colour SHALL keep driving the gradient.

#### Scenario: Administration page

- **WHEN** a full administrator opens `/settings` at 1280 pixels wide
- **THEN** the top bar contains no element with the class `olf-brand-bar`, and no line is drawn under the bar

#### Scenario: Forced colours

- **WHEN** a visitor opens any page with forced colours active
- **THEN** no stripe in the system highlight colour is drawn under the top bar

#### Scenario: Gradient token kept

- **WHEN** an operator sets a seed colour and opens the dashboard
- **THEN** `--olf-gradient` is set from that seed, as before this change

### Requirement: Top bar separates from the content by tone

The top bar SHALL use the `surface` colour role while the window is scrolled to the top, and the `surface-container` role once
the window has scrolled down. The change SHALL use the `short4` duration of the motion tokens, and SHALL be instant when the
visitor prefers reduced motion. The bar SHALL have no border and no shadow in either state.

#### Scenario: At rest

- **WHEN** a visitor opens a page and has not scrolled
- **THEN** the top bar has the `surface` background and no border or shadow

#### Scenario: After scrolling

- **WHEN** the visitor scrolls the window down
- **THEN** the top bar has the `surface-container` background, and returns to `surface` when scrolled back to the top

#### Scenario: Reduced motion

- **WHEN** a visitor who prefers reduced motion scrolls the window
- **THEN** the background changes without a transition

### Requirement: The instance name is not a heading

The instance name in the top bar SHALL be a link to `/` inside the `banner` landmark and SHALL NOT be a heading. The element that
holds the name SHALL keep `data-ingot-part="title"`, and the top bar SHALL keep `data-ingot-component="default-header"`.

#### Scenario: One level one heading

- **WHEN** a full administrator opens `/access/users`
- **THEN** the page has exactly one level one heading, and it is not the instance name

#### Scenario: Anchor kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="default-header"] [data-ingot-part="title"]`
- **THEN** it matches the element that holds the instance name

#### Scenario: Narrow screen

- **WHEN** a visitor opens any page at 320 pixels wide
- **THEN** the instance name is drawn on one line with an ellipsis when it is too long, and the page does not scroll horizontally

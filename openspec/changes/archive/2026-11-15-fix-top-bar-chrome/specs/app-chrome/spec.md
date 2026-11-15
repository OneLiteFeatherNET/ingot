# Spec Delta

## Purpose

The top bar of the dashboard carries the instance name and the account menu. It is part of the navigation frame with the rail,
draws no decorative line, and leaves the level one heading to the page, which has one on every page.

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

### Requirement: Top bar stays part of the navigation frame

The top bar SHALL use the `surface-container` colour role at every scroll position, the same role as the navigation rail. From 600
pixels the page content SHALL stay a `surface` sheet with rounded corners where it meets the rail and the bar, and those corners
SHALL stay drawn at the bar's bottom edge while the page scrolls. The bar SHALL have no border and no shadow, and its background
SHALL NOT change on scroll.

#### Scenario: At rest

- **WHEN** a visitor opens `/` at 1280 pixels wide and has not scrolled
- **THEN** the top bar's background equals the rail's background and the `surface-container` role, the content sheet's
  background is the `surface` role, and the bar has no border or shadow

#### Scenario: After scrolling

- **WHEN** the visitor scrolls the window down by 400 pixels
- **THEN** the top bar's background is still the `surface-container` role, and the rounded corners of the sheet are drawn at
  the bar's bottom edge

#### Scenario: Narrow screen frame

- **WHEN** a visitor opens `/` at 320 pixels wide
- **THEN** the top bar's background is the `surface-container` role over the `surface` page, with no line under the bar

### Requirement: The instance name is not a heading

The instance name in the top bar SHALL be a link to `/` inside the `banner` landmark and SHALL NOT be a heading. The element that
holds the name SHALL keep `data-ingot-part="title"`, and the top bar SHALL keep `data-ingot-component="default-header"`.

#### Scenario: Not a heading

- **WHEN** a full administrator opens `/access/users`
- **THEN** the top bar contains no heading element, and the instance name is a link to `/`

#### Scenario: Anchor kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="default-header"] [data-ingot-part="title"]`
- **THEN** it matches the element that holds the instance name

#### Scenario: Narrow screen

- **WHEN** a visitor opens any page at 320 pixels wide
- **THEN** the instance name is drawn on one line with an ellipsis when it is too long, and the page does not scroll horizontally

### Requirement: Every page has one level one heading

Every page of the dashboard SHALL have exactly one level one heading, and it SHALL NOT be the instance name. The Dashboard SHALL
have the level one heading "Dashboard". The title "My account" of the Account page, the title "Page not found" and the title of the
no access message SHALL be level one headings with their current text. The anchor names of these pages SHALL NOT change.

#### Scenario: Pages of the rail

- **WHEN** a full administrator opens `/`, `/search`, `/dashboard` and `/access/users` in turn
- **THEN** each page has exactly one level one heading, none of them is the instance name, and the one on `/dashboard` reads
  Dashboard

#### Scenario: Account page

- **WHEN** a signed in visitor opens `/account`
- **THEN** the page has exactly one level one heading, and it reads My account

#### Scenario: Page not found

- **WHEN** a visitor opens an address that no page answers
- **THEN** the page has exactly one level one heading, and it reads Page not found

#### Scenario: No access

- **WHEN** a signed in visitor without the dashboard node opens `/dashboard`
- **THEN** the no access message is shown with exactly one level one heading

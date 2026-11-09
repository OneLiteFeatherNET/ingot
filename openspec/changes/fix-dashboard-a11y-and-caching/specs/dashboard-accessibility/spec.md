# Spec Delta

## Purpose

A keyboard or screen reader user can skip the navigation, hears a page change, and is not moved by animation they have
asked to avoid. The rules apply to every page of the dashboard.

## ADDED Requirements

### Requirement: The page content is a main landmark

The dashboard page SHALL contain exactly one `main` element, with the id `main` and `tabindex="-1"`. The `main` element
SHALL contain the page content and SHALL NOT contain the top bar or the navigation.

#### Scenario: Landmark present

- **WHEN** the dashboard page is rendered
- **THEN** exactly one element matches `main#main[tabindex="-1"]`, and the page header is not a descendant of it

### Requirement: A skip link is the first focusable element

The dashboard SHALL render a skip link with the text "Skip to main content" as its first focusable element. The link SHALL
target `#main`, SHALL be visually hidden until it has focus, and SHALL move focus to `main` when activated without changing
the address.

#### Scenario: First Tab stop

- **WHEN** the page loads and the user presses Tab once
- **THEN** the focused element is the skip link and it is visible

#### Scenario: Activating the skip link

- **WHEN** the user activates the skip link
- **THEN** `document.activeElement` is the `main` element and the address does not change

### Requirement: Focus moves to the page on in-app navigation

When the router changes the path inside the dashboard, the dashboard SHALL move focus to `main` after the new page has
rendered. A first navigation of the page load, a change of the query alone and a change of the hash alone SHALL NOT move
focus.

#### Scenario: Page change

- **WHEN** the user navigates from one dashboard page to another path
- **THEN** after the new page has rendered, `document.activeElement` is the `main` element

#### Scenario: Filter change keeps focus

- **WHEN** the user changes a filter that only updates the query string of the current path
- **THEN** `document.activeElement` is not changed by the navigation

#### Scenario: First page load

- **WHEN** the dashboard loads for the first time
- **THEN** focus does not move to `main` because of the navigation

### Requirement: Reduced motion is honoured on every page

While `prefers-reduced-motion: reduce` matches, the global stylesheet SHALL set the animation and transition durations of
every element to 0.01ms, and SHALL set the animation iteration count to 1. The rule SHALL NOT remove an animation's end
state and SHALL NOT replace the component rules that already exist.

#### Scenario: Global rule present

- **WHEN** the global stylesheet is read
- **THEN** it contains a `prefers-reduced-motion: reduce` block that applies to all elements and pseudo-elements and sets
  `animation-duration`, `transition-duration` and `animation-iteration-count`

### Requirement: Charts do not animate

Each ECharts chart on the dashboard SHALL be created with `animation: false`, whatever the user's motion preference.

#### Scenario: Time series chart

- **WHEN** the time series chart builds its ECharts option
- **THEN** the option contains `animation: false`

# Spec Delta

## MODIFIED Requirements

### Requirement: Focus moves to the page on in-app navigation

When the router changes the path inside the dashboard, the dashboard SHALL move focus to the first heading of the new page after
the new page has been inserted, or to `main` when the page has no heading. Focus SHALL NOT wait for a transition to end. A first
navigation of the page load, a change of the query alone and a change of the hash alone SHALL NOT move focus.

#### Scenario: Page change

- **WHEN** the user navigates from one dashboard page to another path
- **THEN** after the new page has been inserted, `document.activeElement` is the first heading of the page, or `main` when the
  page has no heading

#### Scenario: Page change with a transition

- **WHEN** the user navigates to another path while the page transition runs
- **THEN** focus moves at the insertion of the new page and not after the transition has ended

#### Scenario: Filter change keeps focus

- **WHEN** the user changes a filter that only updates the query string of the current path
- **THEN** `document.activeElement` is not changed by the navigation

#### Scenario: First page load

- **WHEN** the dashboard loads for the first time
- **THEN** focus does not move to the heading or to `main` because of the navigation

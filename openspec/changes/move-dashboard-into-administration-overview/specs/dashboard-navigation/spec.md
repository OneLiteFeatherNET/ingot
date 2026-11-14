# Spec Delta

## Purpose

The rail and the bottom bar have no Dashboard entry, and the old Dashboard address leads to Overview.

## ADDED Requirements

### Requirement: The rail and the bar have no Dashboard entry

The rail and the bottom bar SHALL NOT contain a Dashboard entry. The rail SHALL show Browse, Search and Administration, each only
to a visitor who may open it, and the bottom bar SHALL show the same entries and no More item. Administration SHALL be shown to a
visitor who holds the dashboard node, whether or not they hold the settings node, so a visitor who could open the Dashboard can still
reach its content from the rail.

#### Scenario: The rail of an administrator

- **WHEN** a full administrator opens `/`
- **THEN** the rail shows Browse, Search and Administration and no Dashboard

#### Scenario: The bar of an administrator on a phone

- **WHEN** a full administrator opens the dashboard in a window 320 pixels wide
- **THEN** the bottom bar shows Browse, Search and Administration, and no More item

#### Scenario: A visitor with only the dashboard node

- **WHEN** a visitor who holds `ingot.admin.dashboard` and no other admin node opens `/`
- **THEN** the rail shows Browse, Search and Administration, and Administration opens Overview with the status

#### Scenario: An anonymous visitor

- **WHEN** an anonymous visitor opens `/`
- **THEN** the rail shows Browse and Search only

### Requirement: The Dashboard address redirects to Overview

The route named `dashboard`, at `/dashboard`, SHALL redirect to Overview (`/settings`), replace the history entry, and keep the query
and the hash of the address. The route name SHALL stay, so a link in the old `#/...?tab=dashboard` form ends on Overview. The server
SHALL keep answering `/dashboard` with the dashboard document, and the segment `dashboard` SHALL stay in `dashboard-segments.json`,
`DashboardPaths.kt` and the chart copy.

#### Scenario: A bookmark of the Dashboard

- **WHEN** an administrator opens a bookmark of `/dashboard`
- **THEN** the address becomes `/settings`, Overview shows the status, and the back button does not return to `/dashboard`

#### Scenario: A full page load of the old address

- **WHEN** a browser requests `/dashboard` from the server
- **THEN** the server answers with the dashboard document, and the router then redirects to `/settings`

#### Scenario: An old tab link

- **WHEN** a visitor opens `#/?tab=dashboard`
- **THEN** the page ends on Overview

#### Scenario: The query survives

- **WHEN** a visitor opens `/dashboard?from=email`
- **THEN** the address becomes `/settings?from=email`

#### Scenario: An anonymous visitor follows the old address

- **WHEN** an anonymous visitor opens `/dashboard`
- **THEN** the address becomes `/settings` and the page shows the sign in prompt

#### Scenario: A signed in user without the node follows the old address

- **WHEN** a signed in user who holds no admin node opens `/dashboard`
- **THEN** the address becomes `/settings` and the page shows "You do not have access to this page"

#### Scenario: The reserved segment is unchanged

- **WHEN** `dashboard-segments.json` is compared with the copy under `charts/ingot/files`
- **THEN** both are identical and both list `dashboard`

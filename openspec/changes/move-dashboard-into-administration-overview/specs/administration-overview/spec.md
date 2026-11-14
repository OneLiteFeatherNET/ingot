# Spec Delta

## Purpose

Overview, the first entry of Administration, holds the instance status as well as the shared configuration, and uses the width of
the window. Each section is shown to the visitor who holds the permission that section needs.

## ADDED Requirements

### Requirement: Overview shows the instance status

Overview (`/settings` without a domain) SHALL show, to a visitor who holds `ingot.admin.dashboard`, the getting started card, a
section "Instance status" and a section "Resources", with the content, the endpoints and the polling of the former Dashboard page.
The sections SHALL be `h2` headings under the single `h1` "Overview", and the status block SHALL carry
`data-ingot-component="dashboard-view"`. Overview SHALL make no request that the Dashboard and the previous Overview did not make.

#### Scenario: An administrator sees the status on Overview

- **WHEN** a full administrator opens `/settings`
- **THEN** the page shows the sections Instance status and Resources, the meters of the status, and exactly one level one heading, "Overview"

#### Scenario: The status polls only while Overview is open

- **WHEN** an administrator leaves Overview for another page
- **THEN** the status and resource requests stop, as they did when the Dashboard page was left

#### Scenario: The anchor of the status block is kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="dashboard-view"]`
- **THEN** it matches the root of the status block on Overview

### Requirement: Each section of Overview follows its own permission

Needs attention, At a glance, the Danger zone and the file actions SHALL be shown only to a visitor who holds `ingot.admin.settings`.
The instance status, the resources and the getting started card SHALL be shown only to a visitor who holds `ingot.admin.dashboard`.
A section the visitor may not see SHALL NOT be drawn or requested. Overview SHALL open for either node, and every other page below
`/settings` SHALL still need the settings node.

#### Scenario: A visitor with the dashboard node and without the settings node

- **WHEN** a visitor who holds `ingot.admin.dashboard` and not `ingot.admin.settings` opens `/settings`
- **THEN** Overview shows the status sections and no Needs attention, At a glance, Danger zone or file actions, and the page makes no settings request

#### Scenario: A visitor with the settings node and without the dashboard node

- **WHEN** a visitor who holds `ingot.admin.settings` and not `ingot.admin.dashboard` opens `/settings`
- **THEN** Overview shows Needs attention, At a glance and the Danger zone, no status section, and the browser sends no request to `/api/status/instance`

#### Scenario: A dashboard-only visitor cannot open a settings page

- **WHEN** a visitor who holds `ingot.admin.dashboard` and not `ingot.admin.settings` opens `/settings/javadoc`
- **THEN** the page shows the message "You do not have access to this page" and makes no settings request

#### Scenario: A visitor with neither node

- **WHEN** a signed in visitor who holds neither node opens `/settings`
- **THEN** the page shows "You do not have access to this page", and an anonymous visitor sees the sign in prompt at the same address

### Requirement: Overview is a wide page

Overview SHALL NOT keep the reading measure, and its sections SHALL use the full width of the content column. The status meters
SHALL fill each row, the resource charts SHALL be one column below 1024 pixels of container width, two from 1024 pixels and three from
2560 pixels, and At a glance SHALL be a grid of counts. The Danger zone SHALL be the last block of the page content, and nothing
SHALL scroll sideways at 320 pixels.

#### Scenario: Order at 1440 pixels

- **WHEN** a full administrator opens `/settings` in a window 1440 pixels wide
- **THEN** from top to bottom the page shows Needs attention, Instance status, Resources, At a glance and the Danger zone, and the Danger zone is below At a glance

#### Scenario: The charts share a row on a wide window

- **WHEN** a full administrator opens `/settings` in a window 1920 pixels wide
- **THEN** the Memory and Threads charts are in one row, and the status checklist reaches past the middle of the window

#### Scenario: One column at 320 pixels

- **WHEN** a full administrator opens `/settings` in a window 320 pixels wide
- **THEN** every section is one column and the page has no horizontal scrollbar

#### Scenario: Overview is not a 960 pixel column

- **WHEN** a full administrator opens `/settings` in a window 1440 pixels wide
- **THEN** the content column of Overview is wider than 960 pixels

### Requirement: The Danger zone button keeps its label on one line

The button of the Danger zone SHALL show its label "Factory reset" on one line at every width from 320 pixels. The Danger zone's
text, colour and place in the page SHALL NOT change.

#### Scenario: Label height at three widths

- **WHEN** a full administrator opens `/settings` at 320, 600 and 1440 pixels
- **THEN** the label of the Factory reset button is one line high at each width

### Requirement: The page title is not hidden by the top bar

At rest, after a navigation, after the focus moves to the page, and after a jump to an anchor, the top edge of the page's level one
heading SHALL be at or below the bottom edge of the top bar. A heading that scrolls under the bar while the visitor scrolls is not
affected.

#### Scenario: Title after a navigation

- **WHEN** an administrator scrolls Overview and follows the Users entry of the side navigation
- **THEN** the heading "Users" is fully visible below the top bar, at 320, 600 and 1440 pixels

#### Scenario: Title at rest

- **WHEN** an administrator opens `/settings` at scroll position 0
- **THEN** the heading "Overview" is fully visible below the top bar

### Requirement: The overview description names both halves

The description under the heading "Overview" SHALL read "Status of this instance and its shared configuration." for a visitor who
sees both halves, and "Status of this instance." for a visitor who sees the status only. A visitor who sees the settings half only SHALL
see "Shared configuration for all instances.".

#### Scenario: Description for an administrator

- **WHEN** a full administrator opens `/settings`
- **THEN** the description is "Status of this instance and its shared configuration."

# Spec Delta

## Purpose

The dashboard is checked against the real server and the real browser. A regression that only shows when both run together is caught
by a test that drives the page the way an operator does. The suite changes no product behaviour.

## ADDED Requirements

### Requirement: Suite runs against the shaded jar with no external services

The suite SHALL start the shaded server jar built in the same run, with a fresh temporary data directory, a free loopback port and one
temporary admin token. It SHALL NOT need a database server, an external service or a network beyond loopback.

#### Scenario: Fresh instance per run

- **WHEN** the suite starts
- **THEN** it starts one new server on a directory created for this run, and removes the directory when the run ends

#### Scenario: No external service

- **WHEN** the suite runs on a host without network access beyond loopback
- **THEN** every test passes or fails on the server's own behaviour, not on a connection error to an outside host

### Requirement: Tests are independent and repeatable

Each test SHALL create the data it needs under names derived from its own title and worker index. No test SHALL depend on another test's
result or order, no test SHALL retry to pass, and the suite SHALL NOT use fixed sleeps or wall-clock waits.

#### Scenario: Random order and parallel workers

- **WHEN** the suite runs with fully parallel execution and every spec repeated twice
- **THEN** every test reaches the same result as in the sequential run

#### Scenario: No retries hide failures

- **WHEN** a test fails once
- **THEN** the run reports it as failed, with retries set to zero

### Requirement: Test data is seeded through the public REST API only

A test SHALL create repositories and upload files through the public REST API with the bootstrap token. A test SHALL NOT write to or read
from the data directory or the database.

#### Scenario: Repository created through the API

- **WHEN** a test needs a repository
- **THEN** it creates the repository through the settings endpoint and removes it at the end of the test

### Requirement: Sign in and sign out with a token

The dashboard SHALL accept the bootstrap token at sign-in and SHALL end the session on sign-out.

#### Scenario: Sign in with a token

- **WHEN** a visitor enters a valid token on the sign-in dialog
- **THEN** the dashboard shows the signed-in state with the token's name

#### Scenario: Sign out

- **WHEN** a signed-in visitor signs out
- **THEN** the dashboard shows the signed-out state and no Administration entry

### Requirement: Browse a repository and download a file

A visitor SHALL be able to open a repository in the browser and download a file from it.

#### Scenario: Download

- **WHEN** a file was uploaded to a repository through the API and the visitor opens that repository in the browser
- **THEN** the file is listed, and downloading it returns the uploaded bytes

#### Scenario: Upload then browse

- **WHEN** a test uploads a file through the repository protocol
- **THEN** the browser lists the file under the same path, and the test does not reload the page to make it appear

### Requirement: Administration navigation is identical on every Administration page

On every Administration page, the side navigation SHALL show the same entries in the same order for an instance admin, and exactly one
entry SHALL be marked as current. The test is an expected failure until `fix-administration-navigation` lands.

#### Scenario: Same entries on every page

- **WHEN** an instance admin opens each Administration page in turn
- **THEN** the side navigation lists the same entries, in the same order, on each of them

#### Scenario: Exactly one current entry

- **WHEN** an instance admin opens `/settings/audit`
- **THEN** exactly one side navigation link carries `aria-current="page"`

#### Scenario: Expected failure until the fix

- **WHEN** the navigation regression is still present
- **THEN** the test is reported as an expected failure, and it is reported as an unexpected pass once the fix lands

### Requirement: Repositories can be created, edited and deleted on the Repositories page

An instance admin SHALL create, edit and delete a repository on the Repositories page, and the page SHALL show each change.

#### Scenario: Create, edit and delete

- **WHEN** an instance admin creates a repository, edits its description and deletes it on the Repositories page
- **THEN** each step is visible on the page, and the repository is gone from the API at the end

### Requirement: Repositories table keeps names whole and actions on one line

At 1280 px and at 1920 px the Repositories table SHALL NOT break a repository name or address mid-word, and each row's actions SHALL stay on one line.
The test is an expected failure until `fix-administration-navigation` lands.

#### Scenario: Long name at 1280 px

- **WHEN** an instance admin opens the Repositories page at 1280 px with a repository whose name is long
- **THEN** the name is shown on one line and the row's actions are on the same line as the row

#### Scenario: Same at 1920 px

- **WHEN** an instance admin opens the Repositories page at 1920 px
- **THEN** no name or address is broken mid-word and no row's actions wrap

### Requirement: Page heading and actions stay visible while scrolling

While an Administration page scrolls, the page heading and the page actions SHALL remain visible and SHALL NOT be covered by the app bar.
The test is an expected failure until its fixing change is named and lands.

#### Scenario: Scroll a long page

- **WHEN** an instance admin scrolls an Administration page with a long list
- **THEN** the page heading and the page actions are visible and not covered by the app bar

### Requirement: Browse overview snippet shows the full URL and a labelled filter

The build snippet on the Browse overview SHALL show the full repository URL without horizontal scrolling of its container, and the filter
field SHALL have an accessible name that says it filters the list. The test is an expected failure until `fix-browse-overview` lands.

#### Scenario: Full URL in the snippet

- **WHEN** a visitor opens the Browse overview of a repository
- **THEN** the snippet shows the full repository URL and its container does not scroll sideways

#### Scenario: Filter labelled as a filter of the list

- **WHEN** a visitor reads the accessible name of the filter field
- **THEN** the name says that the field filters the list

### Requirement: Dashboard shows read-only as status and no used value above its limit

The Dashboard SHALL show the read-only state as a status only, with no switch control, and no card SHALL show a used value above its
limit. The test is an expected failure until `fix-dashboard-status` lands.

#### Scenario: Read-only as status

- **WHEN** an instance admin opens the Dashboard
- **THEN** the read-only state is shown as a status and no switch control for it is present

#### Scenario: Used value within limit

- **WHEN** a card shows a used value and a limit
- **THEN** the used value is not greater than the limit shown

### Requirement: Visitors without admin rights see no Administration entry

A signed-out visitor, and a signed-in principal without an admin area, SHALL NOT see an Administration entry in the navigation.

#### Scenario: Anonymous visitor

- **WHEN** a signed-out visitor opens the dashboard
- **THEN** the navigation lists Browse and Search, and no Administration entry

### Requirement: Theme choice persists across a reload

The theme toggle SHALL switch between light and dark, and the chosen theme SHALL still be applied after a page reload in the same browser context.

#### Scenario: Toggle and reload

- **WHEN** a visitor switches the theme and reloads the page
- **THEN** the page shows the theme chosen before the reload

### Requirement: Side navigation is reachable at 320 px

At a viewport width of 320 px the side navigation SHALL be reachable by keyboard and by touch, and no page SHALL scroll horizontally.

#### Scenario: Narrow viewport

- **WHEN** a visitor opens an Administration page at 320 px wide
- **THEN** the navigation can be opened and each of its links can be reached, and the page has no horizontal scroll

### Requirement: Covered pages have no serious or critical accessibility violations

Each covered page SHALL be checked with axe-core in its final state. The test SHALL fail on serious and critical violations and SHALL report
moderate and minor ones without failing.

#### Scenario: Serious violation fails the test

- **WHEN** a covered page has a serious or critical violation
- **THEN** the accessibility test for that page fails and names the rule

### Requirement: No breaking change to the product

The change SHALL leave the server, the jar, the container images, the Helm chart, the configuration, the workflows and the HTTP API unchanged.
The suite SHALL be a development dependency of the dashboard only.

#### Scenario: Production dependencies unchanged

- **WHEN** `npm run license-check` runs
- **THEN** it reads the same production dependency set as before this change

# Spec Delta

## Purpose

Access and Settings share one layout: a grouped side navigation with one address per page, an index list on a narrow screen, and a
Settings hub that says what Settings contains and what it currently holds. The Settings draft model does not change.

## ADDED Requirements

### Requirement: Shared split layout for Access and Settings

The Access page and the Settings page SHALL show a side navigation on the left, grouped under headings, with a link for each page
the visitor may open. Each page SHALL have its own address. The current page SHALL be marked with `aria-current="page"`. The horizontal
tab rows of both pages SHALL NOT be shown.

#### Scenario: The current Access page is marked

- **WHEN** a visitor with the users and teams permissions opens `/access/teams`
- **THEN** the side navigation shows the Users and Teams links under the People heading, the Teams link is marked as the current page, and no tab row is shown

#### Scenario: A side link is a real link

- **WHEN** a visitor selects a page in the side navigation
- **THEN** the browser's address changes to the page's own address, and the link can be opened in a new tab

### Requirement: Index list on a narrow screen

Below 600px the side navigation SHALL be shown as an index of the groups and their links. A page opened from the index SHALL show a back
arrow that returns to the index. The width at which the layout switches SHALL be a value the component receives, so that its behaviour can
be tested without the window.

#### Scenario: A phone visitor reaches a page through the index

- **WHEN** a visitor on a narrow screen opens the Settings index and selects Maven defaults
- **THEN** the Maven defaults page opens with a back arrow, and selecting the arrow returns to the index

### Requirement: Settings hub at /settings

With no domain in the address, the Settings page SHALL show a hub of category cards, grouped as Repository, Instance, Data and Plugins.
Each card SHALL show the domain's title and its saved value. The hub SHALL show only the groups and cards the visitor may edit, and
SHALL end with a line that links sign-in and security to Access.

#### Scenario: The hub lists the saved values

- **WHEN** a visitor who may edit Settings opens `/settings` and the saved Maven configuration has six repositories
- **THEN** the Repository group shows a Maven card with the text "6 repositories"

#### Scenario: Domains are placed by the mapping

- **WHEN** the hub is shown to a full administrator
- **THEN** Repository holds Maven, Javadoc and Cleanup, Instance holds Web and Frontend, Data holds Statistics, Audit and Search, and Plugins holds every other domain

#### Scenario: A plugin domain is never hidden

- **WHEN** the server lists a plugin domain that the mapping does not name
- **THEN** the hub shows it under Plugins, and the change does not require the plugin to name a group

#### Scenario: Unsaved changes are marked, not saved

- **WHEN** a visitor has changed the draft of a domain and opens the hub
- **THEN** that domain's card shows "Unsaved changes", the card's value is the saved value, and the draft is unchanged

#### Scenario: A visitor who may not edit Settings sees no hub cards

- **WHEN** a visitor without the settings permission opens `/settings`
- **THEN** no card is shown and the visitor is shown the no access message of the dashboard navigation

### Requirement: Auto forward when one page is visible

When exactly one page of the side navigation is visible to the visitor, the address of its layout SHALL forward to that page with a replace,
so that no back step is added. This applies to Access, which forwards to the one permitted page, and to Settings, which forwards to the one
visible domain. The forward SHALL depend on the permissions and not on the width.

#### Scenario: Settings with one visible domain forwards

- **WHEN** a visitor whose permissions allow only the Maven domain opens `/settings`
- **THEN** the address becomes `/settings/maven`, and the browser's back button does not return to `/settings`

#### Scenario: Access on a wide screen forwards to the first permitted page

- **WHEN** a visitor on a wide screen with the teams permission only opens `/access`
- **THEN** the address becomes `/access/teams`

### Requirement: Login settings redirect to Access

`/settings/login` SHALL redirect to `/access/sso`. `/settings/authentication` SHALL keep redirecting to `/access/ldap`. The Settings page
SHALL NOT show a card for the login domain at `/settings/login`.

#### Scenario: An old login settings link opens single sign on

- **WHEN** a visitor opens `/settings/login`
- **THEN** the address becomes `/access/sso` and the single sign on page is shown

#### Scenario: The authentication link still opens LDAP

- **WHEN** a visitor opens `/settings/authentication`
- **THEN** the address becomes `/access/ldap`

### Requirement: The Settings draft model is unchanged

The Settings page SHALL keep one draft across all domains, one save bar with a change counter, the review of the draft before saving, the
revert of each change, and the leave guard with the browser's unload warning. The leave guard SHALL apply when a visitor leaves Settings with
unsaved changes, and SHALL NOT apply when a visitor moves between Settings pages.

#### Scenario: Moving between Settings pages keeps the draft

- **WHEN** a visitor changes a field in Maven defaults and then opens the Cleanup page
- **THEN** the change is still in the draft, the save bar counts it, and no leave prompt is shown

#### Scenario: Leaving Settings with a draft asks first

- **WHEN** a visitor changes a field and then opens the dashboard's Repositories page
- **THEN** the leave guard asks whether to leave, and the draft is kept if the visitor stays

### Requirement: Access-managed domains stay out of the tabs

The domains `login` and `authentication` SHALL NOT be shown as tabs or cards of the Settings page. They SHALL still be part of the export, the
import and the factory reset, as they are today.

#### Scenario: The authentication domain is not a Settings card

- **WHEN** a visitor opens the Settings hub
- **THEN** no card for the authentication domain is shown, and the sign-in line of the hub links to Access

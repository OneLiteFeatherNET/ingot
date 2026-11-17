# settings-navigation Specification

## Purpose
Access and Settings share one layout: a grouped side navigation with one address per page, an index list on a narrow screen, and a
Settings hub that says what Settings contains and what it currently holds. The Settings draft model does not change.

## Requirements

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

With no domain in the address, the Settings page SHALL show Overview: the heading Overview, a description (see the requirement "The overview description names both halves" of `administration-overview`), a section Needs attention and a section At a glance for a visitor who holds the settings node, and the instance status for a visitor who holds the dashboard node. It SHALL NOT contain a link to a Dashboard page and SHALL NOT list the pages of the Administration tree as cards or links.

#### Scenario: No link to a Dashboard page

- **WHEN** a full administrator opens `/settings`
- **THEN** the page contains no link whose text is "Dashboard" and no line that sends the visitor to a page for the instance status

#### Scenario: No copy of the tree

- **WHEN** a full administrator opens `/settings` at 1440 pixels wide
- **THEN** the content area contains no link to an Administration page that the tree also lists, other than the rows of Needs attention

#### Scenario: A visitor who may not edit Settings

- **WHEN** a visitor who holds neither `ingot.admin.settings` nor `ingot.admin.dashboard` opens `/settings`
- **THEN** no Overview content is shown, and the visitor is forwarded or shown the no access message as before this change, and an anonymous visitor sees the sign in prompt

### Requirement: Needs attention on the settings hub

Needs attention SHALL list one row per settings domain with an unsaved draft, linking to that domain's page, a control that opens the review of all drafts, and a save conflict when one is present; with none of these it SHALL read "Nothing needs attention.".

#### Scenario: Drafts need attention

- **WHEN** a visitor who may edit Settings has changed the draft of Javadoc and opens `/settings`
- **THEN** Needs attention lists Javadoc with the text Unsaved changes, linking to `/settings/javadoc`, and the saved value of Javadoc is unchanged

#### Scenario: Nothing to do

- **WHEN** a visitor who may edit Settings has no draft and no conflict and opens `/settings`
- **THEN** Needs attention reads "Nothing needs attention."

### Requirement: Settings hub at a glance and anchor

At a glance SHALL list the count of every settings collection the visitor may edit, taken from the saved settings, and SHALL render all counts at once after the saved settings are loaded. The page SHALL make no request beyond those the Dashboard and the Settings page made before this change. Its settings half SHALL carry `data-ingot-component="settings-hub"`.

#### Scenario: Counts render together

- **WHEN** a full administrator with seven repositories and no cleanup policy opens `/settings`
- **THEN** At a glance lists Repositories 7 and Cleanup policies 0 in the same render, and shows no number before the saved settings have loaded

#### Scenario: Plugin collection counted

- **WHEN** a plugin section's domain has a collection
- **THEN** At a glance lists its count under the section label

#### Scenario: Anchor kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="settings-hub"]`
- **THEN** it matches the root of the settings half of Overview
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

### Requirement: The Maven domain is edited on the Repositories page

The `maven` domain SHALL NOT be a tab, a card or a side navigation link of the Settings page. It SHALL still be loaded, saved,
exported, imported and reset with the other settings domains, and the Repositories page SHALL edit it. The settings API and the
settings schema SHALL NOT change.

#### Scenario: The domain is not a tab

- **WHEN** an administrator opens any Settings page
- **THEN** the side navigation does not list a Maven entry

#### Scenario: Export keeps the domain

- **WHEN** an administrator downloads the settings from the Settings page
- **THEN** the file contains the `maven` domain, as it did before this change

#### Scenario: Factory reset keeps the domain

- **WHEN** an administrator resets the settings to their defaults
- **THEN** the `maven` domain is reset with the other domains

### Requirement: Settings file actions live on Overview

"Import settings" and "Download as JSON" SHALL be shown in the page header of Overview only, as outlined controls with the same
labels and behaviour as before. Settings domain pages and the Repositories page SHALL NOT show them. Importing SHALL load the file
into the draft without a confirmation, and nothing SHALL be stored before Save.

#### Scenario: Overview header

- **WHEN** a full administrator opens `/settings`
- **THEN** the header shows Import settings and Download as JSON, and no filled button

#### Scenario: Domain page header

- **WHEN** a full administrator opens `/settings/javadoc` or `/settings/repositories`
- **THEN** neither Import settings nor Download as JSON is shown

#### Scenario: Import stays a draft

- **WHEN** the administrator imports a valid settings file on Overview
- **THEN** the changed domains appear under Needs attention as unsaved, and the server settings are unchanged until Save

### Requirement: Overview below 1240 pixels

Below 1240 pixels `/settings` SHALL show, in this order: the page header of Overview, Needs attention when it has a row or a
conflict, the Administration index with its filter, At a glance, and the dashboard link.

#### Scenario: Phone

- **WHEN** a full administrator with a draft in Javadoc opens `/settings` at 320 pixels wide
- **THEN** the level one heading reads Overview, Needs attention lists Javadoc above the index, and the page does not scroll
  horizontally

#### Scenario: Nothing needs attention on a tablet

- **WHEN** a full administrator without a draft or a conflict opens `/settings` at 900 pixels wide
- **THEN** the Needs attention section is not drawn, the index follows the page header, and At a glance follows the index

### Requirement: Overview is the narrow index

Below 1240 pixels the index on `/settings` SHALL list Overview first and mark it `aria-current="page"`, and `/settings` SHALL draw no
back arrow. Every other Administration page below 1240 pixels SHALL keep its back arrow and its target as before this change.

#### Scenario: Current entry in the index

- **WHEN** a full administrator opens `/settings` at 320 pixels wide
- **THEN** the index lists Overview first, marked current, and no back arrow is drawn

#### Scenario: Sibling page keeps its back arrow

- **WHEN** the administrator opens `/settings/javadoc` at 900 pixels wide
- **THEN** the back arrow is drawn and leads to `/settings`

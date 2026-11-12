# Spec Delta

## Purpose

Overview is the first entry of the Administration tree, the tree can be filtered by the words an operator has in mind, and an entry
with an unsaved draft says so.

## MODIFIED Requirements

### Requirement: Exactly one current entry

A page of the Administration tree SHALL mark exactly one entry of the tree as current. `/settings` without a domain SHALL mark the
Overview entry. A settings domain page SHALL mark its own entry. A detail page SHALL mark the entry of its list page. A page that is
not in the tree SHALL mark none.

#### Scenario: Overview

- **WHEN** a full administrator opens `/settings` without a domain
- **THEN** only the Overview entry is marked `aria-current="page"`

#### Scenario: Audit settings page

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** only the Audit settings entry is marked, and neither Overview nor Audit log is

#### Scenario: Audit log page

- **WHEN** a full administrator opens `/audit`
- **THEN** only the Audit log entry is marked

#### Scenario: Detail page

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** only the Users entry is marked

## ADDED Requirements

### Requirement: Overview is the first entry

The tree SHALL list an entry Overview, linking to `/settings`, before the first group and without a group heading, for every visitor
with the settings node. The route, its name and the forward of `/settings` to the one permitted page SHALL NOT change.

#### Scenario: Full administrator

- **WHEN** a full administrator opens any Administration page
- **THEN** the first entry of the tree is Overview, linking to `/settings`

#### Scenario: No settings node

- **WHEN** a visitor without the settings node opens `/console`
- **THEN** the tree lists no Overview entry

### Requirement: Find an administration page

The tree SHALL begin with a search field labelled "Find an administration page". Typing SHALL keep the entries whose label, group
heading, description or keywords contain a word that starts with each typed word, ignoring case, and for a plugin page also its
manifest title, description and plugin id. Groups without a kept entry SHALL be hidden while text is entered. The current entry SHALL
always stay visible. The filter SHALL NOT add a query parameter, a global keyboard shortcut or a request.

#### Scenario: Old label still found

- **WHEN** a full administrator types "web"
- **THEN** Reverse proxy is listed and the live region announces "1 page matches"

#### Scenario: Synonym

- **WHEN** the administrator types "nginx"
- **THEN** Reverse proxy is listed

#### Scenario: Two homes of one concept

- **WHEN** the administrator types "rebuild index"
- **THEN** Search index and Maintenance are listed

#### Scenario: Plugin page found by plugin id

- **WHEN** a plugin `acme-mirror` contributes a page in an unknown group and the administrator types "acme"
- **THEN** the page is listed under Plugins

#### Scenario: No match

- **WHEN** the administrator types "xyz"
- **THEN** only the current entry stays visible, and the live region announces that no administration page matches "xyz"

#### Scenario: Escape

- **WHEN** the field holds text and the administrator presses Escape
- **THEN** the field is empty, every entry is listed again, and focus stays in the field

### Requirement: Filter feedback and keys

A polite live region SHALL announce the number of matching entries, not counting a current entry that is kept only because it is
current, or that no administration page matches the text. Escape SHALL clear the field and keep focus in it. Enter SHALL open the
first matching entry. Results SHALL stay links in reading order, reached with Tab.

#### Scenario: Count announced

- **WHEN** a full administrator types "retention"
- **THEN** Cleanup policies and Audit settings are listed and the live region announces "2 pages match"

#### Scenario: Enter

- **WHEN** the administrator types "logo" and presses Enter
- **THEN** `/settings/frontend` opens, labelled Branding

### Requirement: Unsaved changes are marked in the tree

An entry whose settings domain has an unsaved draft SHALL show the text Unsaved with an icon, and its accessible name SHALL end with
", unsaved changes". No other status and no count SHALL be shown on a tree entry.

#### Scenario: Draft in Javadoc

- **WHEN** a full administrator has changed the draft of Javadoc
- **THEN** the Javadoc entry shows Unsaved, and its accessible name is "Javadoc, unsaved changes"

#### Scenario: Draft in the Maven domain

- **WHEN** the administrator has changed a repository on the Repositories page without saving
- **THEN** the Repositories entry shows Unsaved

#### Scenario: Saved

- **WHEN** the administrator saves every draft
- **THEN** no entry shows Unsaved

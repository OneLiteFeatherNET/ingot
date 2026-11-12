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

### Requirement: One Administration frame

Every Administration page SHALL draw the same frame: the navigation rail when the visitor sees it, the same page container, the
side navigation at the same place beside the page, and one level one heading that names the page shown. The heading SHALL be the
label of the current tree entry, the manifest `title` on a plugin page, or the item name on a detail page. The `main` landmark SHALL
be labelled by that heading. The tree landmark SHALL keep the name Administration.

#### Scenario: Heading names the page

- **WHEN** a full administrator opens `/access/users`, `/settings/web`, `/console` and `/settings/plugins/acme/mirrors` in turn
- **THEN** the level one heading reads Users, Reverse proxy, Console and the manifest title of the plugin page, and each page has
  exactly one level one heading

#### Scenario: Detail page

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** the level one heading reads alice

#### Scenario: Landmark name kept

- **WHEN** a screen reader lists the landmarks of `/access/users`
- **THEN** the tree is a navigation landmark named Administration, and the main landmark is named Users

#### Scenario: Landing page

- **WHEN** a full administrator opens `/settings`
- **THEN** the level one heading reads Overview, the label of the current tree entry, and the description reads "Shared
  configuration for all instances."

### Requirement: Breadcrumb shows where the page sits

The breadcrumb SHALL sit above the level one heading. Its first crumb SHALL be Administration, linked to `/settings`, except on
Overview, where the breadcrumb SHALL hold only the crumb Administration, as plain text and not a link. On every other page its
second crumb SHALL be the heading of the current entry's group, as plain text and not a link. On a detail page the trail SHALL add
the current tree entry as a link. The page itself SHALL be named by the level one heading and SHALL NOT be repeated as a crumb.
Below 1240 pixels the trail SHALL show its last crumb on one line after the back arrow, truncated with an ellipsis, with the full
trail as its title and accessible name; a page without a back arrow SHALL show its trail without one.

#### Scenario: Overview

- **WHEN** a full administrator opens `/settings`
- **THEN** the breadcrumb reads Administration only, that crumb is not a link, and the level one heading reads Overview

#### Scenario: Audit settings

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** the breadcrumb reads Administration, then Monitoring and support, Administration links to `/settings`, and the level
  one heading reads Audit settings

#### Scenario: Detail page trail

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** the breadcrumb reads Administration, Access, Users, where Users links to `/access/users`, and Access is not a link

#### Scenario: Narrow screen

- **WHEN** a visitor opens `/settings/web` at 320 pixels wide
- **THEN** the back arrow and the last crumb are drawn on one line, the back arrow has a target of at least 48 by 48 pixels, and
  the page does not scroll horizontally

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

- **WHEN** a plugin `acme-mirror` contributes a page with `group: "Security"` whose title and description do not contain "acme",
  and the administrator types "acme"
- **THEN** the page is listed under Access

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

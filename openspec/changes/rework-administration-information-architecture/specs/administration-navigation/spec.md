# Spec Delta

## Purpose

The Administration tree groups its pages by the operator's job under display headings, names every page by what is done
there, and every Administration page names itself in its heading.

## MODIFIED Requirements

### Requirement: Administration tree

Administration SHALL group its pages under four group ids, shown with these headings and entries, in this order:

- `Repository`, shown as Artifacts: Repositories, Cleanup policies, Javadoc, Search index.
- `Security`, shown as Access: Users, Machine accounts, Teams, Single sign-on, LDAP directory, Sign-in protection, Permission
  check.
- `System`, shown as Instance: Branding, Reverse proxy, Usage statistics, Migration, and every other settings domain, a plugin
  domain included.
- `Support`, shown as Monitoring and support: Audit log, Audit settings, Console, Maintenance, Analytics, System information,
  Support bundle.

Plugin entries SHALL follow the built-in entries of their group. A plugin page whose group is none of the four ids SHALL be listed
under Plugins, after every built-in group. Every page that is not a settings domain SHALL keep the order it had relative to the
other such pages before this change, so the first permitted page of the tree stays the same for every visitor. A page SHALL be listed only when it exists and the visitor may
open it. A group without a listed page SHALL be left out. The `maven` domain SHALL NOT be listed. Every route, route name and
tree key SHALL stay as before; only the group and the label of an entry change.

#### Scenario: Full administrator

- **WHEN** a full administrator opens any Administration page, with Migration, System information and Support bundle offered
- **THEN** the tree lists Artifacts, Access, Instance and Monitoring and support with exactly the entries above, in that order

#### Scenario: Search index moved

- **WHEN** a full administrator opens the tree
- **THEN** Search index is listed under Artifacts and links to `/settings/search`, and no entry under Instance is labelled Search

#### Scenario: Audit entries are adjacent

- **WHEN** a full administrator opens the tree
- **THEN** Audit log and Audit settings are the first two entries of Monitoring and support, and no entry under Access is
  labelled Audit log

#### Scenario: Settings node without the audit node

- **WHEN** a visitor with the settings node but without the audit node opens the tree
- **THEN** Audit settings is listed under Monitoring and support and Audit log is not

#### Scenario: Plugin domain

- **WHEN** a plugin adds a settings domain without a section group
- **THEN** it is listed under Instance at `/settings/<domain>` with its schema title

#### Scenario: Limited role

- **WHEN** a visitor may open only Console and Maintenance
- **THEN** only the group Monitoring and support is listed

#### Scenario: Single page

- **WHEN** exactly one Administration page is permitted
- **THEN** `/settings` forwards to that page

#### Scenario: Old bookmarks

- **WHEN** a visitor opens `/settings/web`, `/settings/frontend`, `/settings/search`, `/settings/audit` or `/access/security`
- **THEN** the same page opens as before this change, under its new label

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
- **THEN** the level one heading reads Administration and the description reads "Shared configuration for all instances."

### Requirement: Breadcrumb shows where the page sits

The breadcrumb SHALL sit above the level one heading. Its first crumb SHALL be Administration, linked to `/settings`. Its second
crumb SHALL be the heading of the current entry's group, as plain text and not a link. On a detail page the trail SHALL add the
current tree entry as a link. The page itself SHALL be named by the level one heading and SHALL NOT be repeated as a crumb. Below
1240 pixels the trail SHALL show its last crumb on one line after the back arrow, truncated with an ellipsis, with the full trail as
its title and accessible name.

#### Scenario: Audit settings

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** the breadcrumb reads Administration, then Monitoring and support, and the level one heading reads Audit settings

#### Scenario: Detail page trail

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** the breadcrumb reads Administration, Access, Users, where Users links to `/access/users`, and Access is not a link

#### Scenario: Narrow screen

- **WHEN** a visitor opens `/settings/web` at 320 pixels wide
- **THEN** the back arrow and the last crumb are drawn on one line, the back arrow has a target of at least 48 by 48 pixels, and
  the page does not scroll horizontally

### Requirement: Accessible tree

The tree SHALL be a `nav` landmark named Administration. Each group SHALL be a list of links labelled by the group's visible
heading, so that a screen reader announces the group name when focus enters it. The visible heading SHALL NOT be a heading element.
The rail SHALL be a `nav` landmark with its own name. Focus SHALL be reachable in reading order, and route changes SHALL keep the
focus behaviour of the dashboard.

#### Scenario: Landmarks

- **WHEN** a screen reader lists the landmarks of an Administration page
- **THEN** the main navigation, the Administration navigation and the main content are listed with distinct names

#### Scenario: Group names

- **WHEN** focus moves into the first link of the Access group
- **THEN** the group is announced as Access

#### Scenario: Heading outline

- **WHEN** a screen reader lists the headings of `/access/users`
- **THEN** the first heading is the level one heading Users, and no group of the tree is listed as a heading

### Requirement: One tree on every Administration page

The side navigation of Access, Settings, Audit, Console, Maintenance, Analytics and Migration pages SHALL show the same
Administration tree, including every settings domain the visitor may open, with the current page marked by
`aria-current="page"`. Below 600 CSS pixels the tree SHALL be an index with a back arrow on each page.

#### Scenario: Console page lists the settings domains

- **WHEN** a full administrator opens `/console`
- **THEN** the tree lists Artifacts with Repositories, Cleanup policies, Javadoc and Search index, Instance with Branding,
  Reverse proxy, Usage statistics and the other settings domains, and Monitoring and support with Audit settings, and marks
  Console as current

#### Scenario: Access page lists the same tree

- **WHEN** a full administrator opens `/access/teams`
- **THEN** the tree lists the same groups and entries as on `/console`, and marks Teams as current

#### Scenario: Settings and Access show equal trees

- **WHEN** the same visitor opens each Administration page in turn
- **THEN** every page shows an identical tree, apart from the marked entry

#### Scenario: Narrow screen

- **WHEN** a visitor opens any Administration page below 600 pixels
- **THEN** the tree is shown as an index list with a back arrow on each page

### Requirement: Exactly one current entry

A page of the Administration tree SHALL mark exactly one entry of the tree as current. A page that is not in the tree SHALL
mark none.

#### Scenario: Audit settings page

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** only the Audit settings entry in Monitoring and support is marked `aria-current="page"`, and the Audit log entry is
  not

#### Scenario: Audit log page

- **WHEN** a full administrator opens `/audit`
- **THEN** only the Audit log entry in Monitoring and support is marked, and no settings domain entry is marked

#### Scenario: Settings hub

- **WHEN** a full administrator opens `/settings` without a domain
- **THEN** no entry of the tree is marked

### Requirement: Addresses unchanged

Every route path and route name of the dashboard before this change SHALL still resolve, with its permission check
unchanged. No top level path segment SHALL be added. New pages SHALL live under `/settings`, `/access` or `/browse`.
The page names under `/settings` SHALL be reserved against settings domain ids.

#### Scenario: Old audit address

- **WHEN** a visitor with the audit node opens `/audit`
- **THEN** the audit log opens at the same address, listed under Monitoring and support

#### Scenario: Old maintenance address

- **WHEN** a visitor opens `/maintenance` and `/console` and `/analytics`
- **THEN** each page opens at its address, listed under Monitoring and support

#### Scenario: No new top level segment

- **WHEN** the dashboard segment list is compared with the one before this change
- **THEN** the lists are equal

## RENAMED Requirements

- FROM: `### Requirement: Breadcrumb names the page shown`
- TO: `### Requirement: Breadcrumb shows where the page sits`

## ADDED Requirements

### Requirement: Display headings keep the plugin group values

The group ids `Repository`, `Security`, `System` and `Support` SHALL remain the values a plugin manifest names. The frontend SHALL map
each id to its display heading. The server's accepted group values, their default and the validator SHALL NOT change, so the server
keeps refusing a page with any other group value. The frontend SHALL still list a page with a group value it does not know under
Plugins, after every built-in group, as a fallback for an unexpected value.

#### Scenario: Plugin page in Security

- **WHEN** a plugin manifest declares a page with `group: "Security"`
- **THEN** the page is listed under Access, after Permission check

#### Scenario: Unexpected group value in the frontend

- **WHEN** the frontend is given a plugin page whose group is none of the four ids, as a unit test does with a fake manifest
- **THEN** the page is listed under Plugins after every built-in group and is not dropped

#### Scenario: Server still refuses an unknown group

- **WHEN** a plugin manifest declares a page with `group: "Billing"`
- **THEN** the server refuses the page with the same validation message as before this change

#### Scenario: Plugin section in Repository

- **WHEN** a plugin declares a settings section with `group: "Repository"`
- **THEN** its domain is listed under Artifacts with the section label

### Requirement: Labels and descriptions per entry

Every built-in entry SHALL have a label and a one-line description defined in the frontend registry. A settings domain the registry
does not name SHALL use its schema title as the label and its schema description as the description. A plugin page SHALL use its
manifest title and description. An entry without a description SHALL draw no empty description line. The settings schema, the
settings API and the exported settings JSON SHALL NOT change.

#### Scenario: Renamed domain

- **WHEN** a full administrator opens `/settings/web`
- **THEN** the tree entry and the level one heading read Reverse proxy, the description names the forwarded headers, and
  the schema of the `web` domain still carries the title Web

#### Scenario: Plugin domain without a description

- **WHEN** a plugin domain's schema has an empty description
- **THEN** its page shows the heading and no description line

### Requirement: Propagation notice at the save step

The statement that saved settings reach every instance within 10 seconds SHALL be shown as supporting text of the save bar and in
the message that confirms a save. It SHALL NOT be the description of a settings page.

#### Scenario: Settings page description

- **WHEN** a full administrator opens `/settings/javadoc`
- **THEN** the description under the heading describes Javadoc and does not mention 10 seconds

#### Scenario: Saving

- **WHEN** the administrator changes a field and the save bar appears
- **THEN** the save bar says that other instances pick up saved changes within 10 seconds, and after saving the confirmation says
  the same

### Requirement: Plugin pages name their plugin

A plugin page SHALL show "Provided by the <plugin> plugin" under its description, where <plugin> is the plugin id from the manifest.

#### Scenario: Plugin page header

- **WHEN** a full administrator opens a page that the plugin `acme-mirror` contributes
- **THEN** the header shows "Provided by the acme-mirror plugin"

### Requirement: Shortcuts are links inside a page

Related pages SHALL link to each other inside the page, and no shortcut SHALL be a tree entry. A shortcut SHALL be drawn only when the
visitor may open its target. The links SHALL be: Audit log to Audit settings and back; Analytics to Usage statistics; Maintenance to
Search index and Cleanup policies; Search index to Maintenance; a user or machine account detail page to Permission check with the
principal filled in through the `principal` query; a team detail page to Permission check.

#### Scenario: From a user to the permission check

- **WHEN** a full administrator opens `/access/users/alice` and activates "Check a permission"
- **THEN** `/access/check?principal=alice` opens with alice filled in

#### Scenario: Shortcut hidden without permission

- **WHEN** a visitor with the audit node but without the settings node opens `/audit`
- **THEN** no link to Audit settings is drawn

### Requirement: Administration entry opens the same page

The Administration entry of the rail and the bottom bar SHALL open the same page as before this change for every set of
permissions: `/settings` for a visitor with the settings node, otherwise the first page of the tree the visitor may open, taken
in the order the pages had before this change.

#### Scenario: Console and Analytics only

- **WHEN** a visitor with the console and dashboard nodes, and without the settings node, activates Administration
- **THEN** `/console` opens, as before this change

#### Scenario: Audit and Console only

- **WHEN** a visitor with the audit and console nodes, and without the settings node, activates Administration
- **THEN** `/audit` opens, as before this change

#### Scenario: Settings node

- **WHEN** a visitor with the settings node activates Administration
- **THEN** `/settings` opens

# administration-navigation Specification

## Purpose
The dashboard navigation is grouped by what an operator does: Browse, Search, Dashboard and Administration. Administration
holds Artifacts, Access, Instance and Monitoring and support. The group ids that plugin manifests name stay Repository,
Security, System and Support; the frontend maps each id to its heading. Every address that works today keeps working. Where
this spec and the `dashboard-navigation` spec of `restructure-navigation` differ on groups and labels, this spec governs.

## Requirements

### Requirement: Rail and bottom bar entries

The rail and the bottom bar SHALL show Browse, Search, Dashboard and Administration, each only when the visitor may open it.
Browse SHALL keep the path `/` and the route name `overview`. Administration SHALL open the page named by the requirement
"Administration entry opens the same page" and SHALL be shown when the visitor may open any Administration page. The bar SHALL
need no More item.

#### Scenario: Full administrator

- **WHEN** an instance administrator opens the dashboard on a narrow screen
- **THEN** the bar shows Browse, Search, Dashboard and Administration

#### Scenario: Audit node only

- **WHEN** a visitor has only the audit node
- **THEN** the navigation shows Browse, Search and Administration, and Administration opens the audit log

#### Scenario: Anonymous visitor

- **WHEN** a visitor is not signed in
- **THEN** the navigation shows Browse and Search only

### Requirement: Overview is the first entry

The tree SHALL list an entry Overview, linking to `/settings`, before the first group and without a group heading, for every visitor
with the settings node. The route, its name and the forward of `/settings` to the one permitted page SHALL NOT change.

#### Scenario: Full administrator

- **WHEN** a full administrator opens any Administration page
- **THEN** the first entry of the tree is Overview, linking to `/settings`

#### Scenario: No settings node

- **WHEN** a visitor without the settings node opens `/console`
- **THEN** the tree lists no Overview entry

### Requirement: Administration tree

Administration SHALL group its pages under four group ids, shown with these headings and entries, in this order. The first two groups are:

- `Repository`, shown as Artifacts: Repositories, Cleanup policies, Javadoc, Search index.
- `Security`, shown as Access: Users, Machine accounts, Teams, Single sign-on, LDAP directory, Sign-in protection, Permission check.

#### Scenario: Full administrator

- **WHEN** a full administrator opens any Administration page, with Migration, System information and Support bundle offered
- **THEN** the tree lists Artifacts, Access, Instance and Monitoring and support with exactly the entries above, in that order, after the Overview entry

#### Scenario: Search index moved

- **WHEN** a full administrator opens the tree
- **THEN** Search index is listed under Artifacts and links to `/settings/search`, and no entry under Instance is labelled Search

### Requirement: Administration tree groups for System and Support

The last two groups SHALL follow, in the same order:

- `System`, shown as Instance: Branding, Reverse proxy, Usage statistics, Migration, and every other settings domain, a plugin domain included.
- `Support`, shown as Monitoring and support: Audit log, Audit settings, Console, Maintenance, Analytics, System information, Support bundle.

#### Scenario: Audit entries are adjacent

- **WHEN** a full administrator opens the tree
- **THEN** Audit log and Audit settings are the first two entries of Monitoring and support, and no entry under Access is labelled Audit log

#### Scenario: Settings node without the audit node

- **WHEN** a visitor with the settings node but without the audit node opens the tree
- **THEN** Audit settings is listed under Monitoring and support and Audit log is not

#### Scenario: Plugin domain

- **WHEN** a plugin adds a settings domain without a section group
- **THEN** it is listed under Instance at `/settings/<domain>`, labelled with its plugin section label or, when it has none, its schema title

### Requirement: Conditional Administration tree entries

The tree SHALL list its conditional entries under these rules. Migration is listed only while the nexus-import plugin is enabled. System information and Support bundle are listed only while the instance offers the support bundle. Overview is listed before the first group.

#### Scenario: Migration hidden without the plugin

- **WHEN** a full administrator opens the tree on an instance where the nexus-import plugin is disabled
- **THEN** Migration is not listed under Instance

### Requirement: Plugin entries in the Administration tree

Plugin entries SHALL follow the built-in entries of their group. A plugin page whose group is none of the four ids SHALL be listed under Plugins, after every built-in group. A page SHALL be listed only when it exists and the visitor may open it. A group without a listed page SHALL be left out. The `maven` domain SHALL NOT be listed. Every route and route name SHALL keep its address; only the group, the label and the description of an entry are set by the tree.

#### Scenario: Limited role

- **WHEN** a visitor may open only Console and Maintenance
- **THEN** only the group Monitoring and support is listed

#### Scenario: Single page

- **WHEN** exactly one Administration page is permitted
- **THEN** `/settings` forwards to that page

#### Scenario: Old bookmarks

- **WHEN** a visitor opens `/settings/web`, `/settings/frontend`, `/settings/search`, `/settings/audit` or `/access/security`
- **THEN** the page opens at that address, under its entry label in the tree
### Requirement: One tree on every Administration page

The side navigation of Access, Settings, Audit, Console, Maintenance, Analytics and Migration pages, of the support pages (System information and Support bundle) and of the plugin pages SHALL show the same Administration tree, including every settings domain the visitor may open, with the current page marked by `aria-current="page"`.

#### Scenario: Console page lists the settings domains

- **WHEN** a full administrator opens `/console`
- **THEN** the tree lists Artifacts with Repositories, Cleanup policies, Javadoc and Search index, Instance with Branding, Reverse proxy, Usage statistics and the other settings domains, and Monitoring and support with Audit settings, and marks Console as current

#### Scenario: Access page lists the same tree

- **WHEN** a full administrator opens `/access/teams`
- **THEN** the tree lists the same groups and entries as on `/console`, and marks Teams as current

#### Scenario: Settings and Access show equal trees

- **WHEN** the same visitor opens each Administration page in turn
- **THEN** every page shows an identical tree, apart from the marked entry

### Requirement: Narrow screen tree index

Below 1240 CSS pixels the tree SHALL be an index, and a page of a section SHALL show a back arrow to that index. From 600 CSS pixels a Sections button opens the same tree in a side sheet.

#### Scenario: Narrow screen

- **WHEN** a visitor opens `/settings/web` below 1240 pixels
- **THEN** the tree is not drawn beside the page, the page shows a back arrow to the index of the tree, and the index lists the same groups and entries
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

- **WHEN** a plugin `acme-mirror` contributes a page with `group: "Security"` whose title and description do not contain "acme", and the administrator types "acme"
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
", unsaved changes". The marks are drawn on the settings pages: Overview, the settings domain pages and the Repositories page. No
other status and no count SHALL be shown on a tree entry.

#### Scenario: Draft in Javadoc

- **WHEN** a full administrator has changed the draft of Javadoc and opens a settings page
- **THEN** the Javadoc entry shows Unsaved, and its accessible name is "Javadoc, unsaved changes"

#### Scenario: Draft in the Maven domain

- **WHEN** the administrator has changed a repository on the Repositories page without saving
- **THEN** the Repositories entry shows Unsaved

#### Scenario: Saved

- **WHEN** the administrator saves every draft
- **THEN** no entry shows Unsaved

### Requirement: Exactly one current entry

A page of the Administration tree SHALL mark exactly one entry of the tree as current. `/settings` without a domain SHALL mark the
Overview entry. A settings domain page SHALL mark its own entry. A detail page SHALL mark the entry of its list page. A plugin page
SHALL mark its own plugin entry. A page that is not in the tree SHALL mark none.

#### Scenario: Overview

- **WHEN** a full administrator opens `/settings` without a domain
- **THEN** only the Overview entry is marked `aria-current="page"`

#### Scenario: Audit settings page

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** only the Audit settings entry in Monitoring and support is marked `aria-current="page"`, and the Audit log entry is not

#### Scenario: Audit log page

- **WHEN** a full administrator opens `/audit`
- **THEN** only the Audit log entry in Monitoring and support is marked, and no settings domain entry is marked

#### Scenario: Detail page

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** only the Users entry is marked

### Requirement: One Administration frame

Every Administration page SHALL draw the same frame: the navigation rail when the visitor sees it, the same page container, the side
navigation at the same place beside the page, and one level one heading that names the page shown. The heading SHALL be the label of
the current tree entry, the manifest `title` on a plugin page, or the item name on a detail page. The `main` landmark SHALL be labelled
by that heading. The tree landmark SHALL keep the name Administration.

#### Scenario: Heading names the page

- **WHEN** a full administrator opens `/access/users`, `/settings/web`, `/console` and `/settings/plugins/acme/mirrors` in turn
- **THEN** the level one heading reads Users, Reverse proxy, Console and the manifest title of the plugin page, and each page has exactly one level one heading

#### Scenario: Detail page

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** the level one heading reads alice

#### Scenario: Landmark name kept

- **WHEN** a screen reader lists the landmarks of `/access/users`
- **THEN** the tree is a navigation landmark named Administration, and the main landmark is named Users

#### Scenario: Landing page

- **WHEN** a full administrator opens `/settings`
- **THEN** the level one heading reads Overview, the label of the current tree entry, and the description reads "Shared configuration for all instances."

### Requirement: Breadcrumb shows where the page sits

The breadcrumb SHALL sit above the level one heading. Its first crumb SHALL be Administration. On Overview the first crumb is plain text and the breadcrumb holds no other crumb. On every other page the first crumb SHALL link to the index of the page's section: `/settings` on the settings and plugin pages, and `/access` on the Access pages.

#### Scenario: Overview

- **WHEN** a full administrator opens `/settings`
- **THEN** the breadcrumb reads Administration only, that crumb is not a link, and the level one heading reads Overview

### Requirement: Breadcrumb second crumb and detail trail

On every other page the second crumb SHALL be the heading of the current entry's group, as plain text and not a link. On a detail page the trail SHALL add the list page of the current entry as a link. The page itself SHALL be named by the level one heading and SHALL NOT be repeated as a crumb.

#### Scenario: Audit settings

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** the breadcrumb reads Administration, then Monitoring and support, Administration links to `/settings`, and the level one heading reads Audit settings

#### Scenario: Detail page trail

- **WHEN** a full administrator opens `/access/users/alice`
- **THEN** the breadcrumb reads Administration, Access, Users, where Users links to `/access/users`, and Access is not a link

### Requirement: Breadcrumb on a narrow screen

Below 1240 CSS pixels the trail SHALL show its last crumb on one line after the back arrow, truncated with an ellipsis, with the full trail as its title and accessible name; a page without a back arrow SHALL show its trail without one.

#### Scenario: Narrow screen

- **WHEN** a visitor opens `/settings/web` at 320 pixels wide
- **THEN** the back arrow and the last crumb are drawn on one line, the back arrow has a target of at least 48 by 48 pixels, and the page does not scroll horizontally
### Requirement: Unique entry keys

Every entry of the Administration tree SHALL have a key that no other entry of the tree has, including the entries that a settings
domain or a plugin page adds. A static page is keyed `page:<key>`, a settings domain `settings:<domain>` and a plugin page
`plugin:<plugin>/<id>`, so a static page and a settings domain with the same id SHALL NOT share a key. A key is internal: it is never
a route name, an address or a label.

#### Scenario: Audit log and Audit settings

- **WHEN** the tree is built for a full administrator with the settings domain `audit` present
- **THEN** the Audit log entry and the Audit settings entry have different keys

#### Scenario: Plugin domain with a page id

- **WHEN** a plugin settings domain is called `users` or `security`
- **THEN** its entry has its own key, and the entries of `/access/users` and `/access/security` keep theirs

### Requirement: Settings domains need the settings permission

The settings domains in the tree SHALL be shown only to a visitor with the settings node. The domain list SHALL be requested from the
server once per client, which is once per session, and only by a page that draws the tree to such a visitor. A visitor without the node
SHALL make no request to the settings domain endpoint from any Administration page. A failed request is not kept, so the next page asks
again.

#### Scenario: Visitor without the settings node

- **WHEN** a visitor with the audit node only opens `/audit`
- **THEN** the tree shows Audit log and no settings domain, and the settings domain endpoint is not called

#### Scenario: Visitor with the settings node

- **WHEN** a visitor with the settings node opens `/console` and then `/settings`
- **THEN** the settings domain endpoint is called once for the session

### Requirement: Rail label fits the rail

The label of each rail entry SHALL NOT overflow the rail or the viewport edge. A label wider than the rail SHALL be cut with an ellipsis,
and the full label SHALL then be the title of the entry. The accessible name of the Administration entry SHALL stay Administration. Below
600 CSS pixels the rail SHALL draw no text.

#### Scenario: Long label on the Console page

- **WHEN** a full administrator opens `/console` at 600 pixels or wider
- **THEN** the Administration label is drawn inside the rail, cut with an ellipsis if it is wider than the rail, and its full text is the title of the entry when it is cut

#### Scenario: Hidden rail

- **WHEN** a visitor opens an Administration page below 600 pixels
- **THEN** no rail text is visible in the viewport

### Requirement: Display headings keep the plugin group values

The group ids `Repository`, `Security`, `System` and `Support` SHALL remain the values a plugin manifest names. The frontend SHALL map each id to its display heading. The server's accepted group values, their default and the validator SHALL NOT change, so the server keeps refusing a page with any other group value. The frontend SHALL still list a page with a group value it does not know under Plugins, after every built-in group, as a fallback for an unexpected value.

#### Scenario: Plugin page in Security

- **WHEN** a plugin manifest declares a page with `group: "Security"`
- **THEN** the page is listed under Access, after Permission check

#### Scenario: Unexpected group value in the frontend

- **WHEN** the frontend is given a plugin page whose group is none of the four ids, as a unit test does with a fake manifest
- **THEN** the page is listed under Plugins after every built-in group and is not dropped

#### Scenario: Server still refuses an unknown group

- **WHEN** a plugin manifest declares a page with `group: "Billing"`
- **THEN** the server refuses the page with a validation message that names the valid groups

#### Scenario: Plugin section in Repository

- **WHEN** a plugin declares a settings section with `group: "Repository"`
- **THEN** its domain is listed under Artifacts with the section label

### Requirement: Labels and descriptions per entry

Every built-in entry SHALL have a label and a one-line description defined in the frontend registry. A settings domain the registry does not name SHALL use its plugin section label when one is registered, and otherwise its schema title as the label, and its schema description as the description. A plugin page SHALL use its manifest title and description. The settings schema, the settings API and the exported settings JSON SHALL NOT change.

#### Scenario: Renamed domain

- **WHEN** a full administrator opens `/settings/web`
- **THEN** the tree entry and the level one heading read Reverse proxy, the description says that it covers the headers that carry the client address and the path prefix behind a proxy, and the schema of the `web` domain still carries the title Web

### Requirement: Entry without a description

An entry without a description SHALL draw no empty description line.

#### Scenario: Plugin domain without a description

- **WHEN** a plugin domain's schema has an empty description
- **THEN** its page shows the heading and no description line
### Requirement: Propagation notice at the save step

The statement that saved settings reach every instance within 10 seconds SHALL be shown as supporting text of the save bar and in the message that confirms a save. It SHALL NOT be the description of a settings page.

#### Scenario: Settings page description

- **WHEN** a full administrator opens `/settings/javadoc`
- **THEN** the description under the heading describes Javadoc and does not mention 10 seconds

#### Scenario: Saving

- **WHEN** the administrator changes a field and the save bar appears
- **THEN** the save bar says that other instances pick up saved changes within 10 seconds, and after saving the confirmation says the same

### Requirement: Plugin pages name their plugin

A plugin page SHALL show "Provided by the <plugin> plugin" under its description, where <plugin> is the plugin id from the manifest.

#### Scenario: Plugin page header

- **WHEN** a full administrator opens a page that the plugin `acme-mirror` contributes
- **THEN** the header shows "Provided by the acme-mirror plugin"

### Requirement: Shortcuts are links inside a page

Related pages SHALL link to each other inside the page, and no shortcut SHALL be a tree entry. A shortcut SHALL be drawn only when the visitor may open its target. The links SHALL be: Audit log to Audit settings and back; Analytics to Usage statistics; Maintenance to Search index and Cleanup policies; Search index to Maintenance; a user or machine account detail page to Permission check with the principal filled in through the `principal` query; a team detail page to Permission check.

#### Scenario: From a user to the permission check

- **WHEN** a full administrator opens `/access/users/alice` and activates "Check a permission"
- **THEN** `/access/check?principal=alice` opens with alice filled in

#### Scenario: Shortcut hidden without permission

- **WHEN** a visitor with the audit node but without the settings node opens `/audit`
- **THEN** no link to Audit settings is drawn

### Requirement: Administration entry opens the same page

The Administration entry of the rail and the bottom bar SHALL open `/settings` for a visitor with the settings node. Otherwise it SHALL open the first page of the tree that the visitor may open, taken in the order of the tree.

#### Scenario: Console and Analytics only

- **WHEN** a visitor with the console and dashboard nodes, and without the settings node, activates Administration
- **THEN** `/console` opens

#### Scenario: Audit and Console only

- **WHEN** a visitor with the audit and console nodes, and without the settings node, activates Administration
- **THEN** `/audit` opens

#### Scenario: Settings node

- **WHEN** a visitor with the settings node activates Administration
- **THEN** `/settings` opens

### Requirement: Addresses unchanged

Every route path and route name of the dashboard SHALL still resolve, with its permission check unchanged. No top level path segment SHALL be added. New pages SHALL live under `/settings`, `/access` or `/browse`. The page names under `/settings` SHALL be reserved against settings domain ids.

#### Scenario: Old audit address

- **WHEN** a visitor with the audit node opens `/audit`
- **THEN** the audit log opens at the same address, listed under Monitoring and support

#### Scenario: Old maintenance address

- **WHEN** a visitor opens `/maintenance` and `/console` and `/analytics`
- **THEN** each page opens at its address, listed under Monitoring and support

#### Scenario: No new top level segment

- **WHEN** the dashboard segment list is compared with the list of top level segments in the router
- **THEN** the lists are equal

### Requirement: Accessible tree

The tree SHALL be a `nav` landmark named Administration. Each group SHALL be a list of links labelled by the group's visible heading, so that a screen reader announces the group name when focus enters it. The visible heading SHALL NOT be a heading element. The rail SHALL be a `nav` landmark with its own name. Focus SHALL be reachable in reading order, and route changes SHALL keep the focus behaviour of the dashboard.

#### Scenario: Landmarks

- **WHEN** a screen reader lists the landmarks of an Administration page
- **THEN** the main navigation, the Administration navigation and the main content are listed with distinct names

#### Scenario: Group names

- **WHEN** focus moves into the first link of the Access group
- **THEN** the group is announced as Access

#### Scenario: Heading outline

- **WHEN** a screen reader lists the headings of `/access/users`
- **THEN** the first heading is the level one heading Users, and no group of the tree is listed as a heading

# Spec Delta

## Purpose

The dashboard's navigation is one registry of pages. The rail, the bottom bar, the More sheet, the Settings hub, the breadcrumbs,
the route guard and the Access tabs all read it. The rail groups the pages by what a reader does with them, and a visitor who lacks a
permission sees why a page is not open to them.

## ADDED Requirements

### Requirement: One registry of navigable pages

The dashboard SHALL define every page that the navigation or a route guard knows about in one registry. The rail, the bottom bar,
the More sheet, the Settings hub, the breadcrumbs, the route guard and the Access tabs SHALL read their entries from that registry and
SHALL NOT keep a list of their own. A named route that is neither a registry entry, nor a child of one, nor on the list of exempt
routes SHALL fail the test suite.

#### Scenario: A new page is added once

- **WHEN** a page is added to the route table with a registry entry
- **THEN** the rail, the bottom bar and the guard show and guard the page from that entry alone, with no second list to edit

#### Scenario: A route without an entry fails the suite

- **WHEN** a named route is added to the route table without a registry entry, a parent entry or an exempt listing
- **THEN** the navigation test fails and names the route

### Requirement: Groups and labels of the rail

The rail SHALL show the pages in the groups Work, Monitor, Administration and Troubleshoot, in that order, and a group with no
visible page SHALL NOT be shown. The page labelled Overview SHALL be labelled Repositories, and the page labelled Audit SHALL be
labelled Audit log. The route names and paths of these pages SHALL NOT change.

#### Scenario: The rail lists Work, Monitor, Administration and Troubleshoot in order

- **WHEN** a full administrator opens `/`
- **THEN** the rail shows the Work group (Repositories, Search), then Monitor (Dashboard, Analytics, Audit log), then Administration (Access, Settings), then Troubleshoot (Console, Maintenance), each group with its name as accessible label

#### Scenario: The repository page is labelled Repositories

- **WHEN** a visitor opens `/`
- **THEN** the first entry of the Work group is labelled Repositories and is marked as the current page, and the route name stays `overview`

#### Scenario: A visitor without admin rights sees only the open groups

- **WHEN** an anonymous visitor opens the dashboard
- **THEN** the rail shows the Work group with Repositories and Search, and no Monitor, Administration or Troubleshoot group is shown

#### Scenario: The audit page keeps its address

- **WHEN** a visitor follows a bookmark to `/audit`
- **THEN** the audit log opens at `/audit` and its entry in the rail is labelled Audit log

### Requirement: Bottom bar rule

On widths below 600px the bottom bar SHALL show the first four permitted entries in the registry's bar order. When at least one
permitted entry is left over, the bar SHALL add a More item after them, and the More sheet SHALL list the leftover entries under the
group headings of the rail. When no permitted entry is left over, the bar SHALL NOT show a More item.

#### Scenario: A full administrator sees a More item

- **WHEN** a full administrator opens the dashboard on a phone
- **THEN** the bar shows Repositories, Search, Dashboard, Access and More, and the More sheet lists Analytics, Audit log, Settings, Console and Maintenance under the Monitor, Administration and Troubleshoot headings

#### Scenario: A visitor with one admin permission sees its page in the bar

- **WHEN** a visitor holds only the audit permission and opens the dashboard on a phone
- **THEN** the bar shows Repositories, Search and Audit log, and no More item

#### Scenario: Four permitted entries need no More item

- **WHEN** a visitor holds the dashboard and access permissions and no others, besides the open pages
- **THEN** the bar shows Repositories, Search, Dashboard and Access, and no More item, because no permitted entry is left over

### Requirement: Entries follow the permissions of their page

An entry SHALL be shown only to a visitor who holds the permission the entry names: its node, one of its alternative nodes, or manager
access when it names none. The rail, the bar and the More sheet SHALL hide an entry that the visitor lacks, and SHALL NOT show it greyed out.

#### Scenario: The Access entry needs one of the access nodes

- **WHEN** a visitor holds only the teams permission
- **THEN** the Access entry is shown, because one of its alternative nodes is held, and its Machine accounts child is hidden for the same reason

#### Scenario: A hidden entry is not reachable from the bar

- **WHEN** a visitor lacks the console permission
- **THEN** the Console entry is not in the rail, the bar or the More sheet

### Requirement: No access state for a deep link

A signed-in visitor who opens a page without the permission for it SHALL stay at the same address and SHALL see the page's label with the
message "You do not have access to this page" and a link to Repositories. The dashboard SHALL NOT redirect such a visitor to another page.
An anonymous visitor SHALL be asked to sign in, and SHALL NOT see the message before signing in.

#### Scenario: A signed-in visitor opens the console without the permission

- **WHEN** a signed-in visitor without the console permission opens `/console`
- **THEN** the address stays `/console`, the no access message is shown, and the link to Repositories is present

#### Scenario: An anonymous visitor opens an admin page

- **WHEN** an anonymous visitor opens `/dashboard`
- **THEN** the sign in dialog is offered and no no access message is shown

#### Scenario: A sign in that grants the page opens it

- **WHEN** an anonymous visitor signs in with a principal that holds the dashboard permission after opening `/dashboard`
- **THEN** the dashboard opens at `/dashboard`

### Requirement: My account stays in the avatar menu

The avatar menu SHALL link to the My account page at `/account` for a signed-in visitor, and SHALL keep the Logout action. The menu SHALL
read the link from the registry entry of the account page.

#### Scenario: A signed-in visitor opens My account from the menu

- **WHEN** a signed-in visitor selects My account in the avatar menu
- **THEN** the account page opens at `/account` and the rail does not show My account as a group entry

### Requirement: Addresses and server routing do not change

The navigation change SHALL NOT add a top-level path segment, SHALL NOT change a path or route name of an existing page, and SHALL NOT
change the dashboard segments that the server reserves. Every address that worked before the change SHALL still work, and the only new
route name SHALL be `settings-login`, which is a redirect.

#### Scenario: An old address still opens its page

- **WHEN** a visitor opens an address that was valid before the change, for example `/access/machines/ci` or `/settings/maven`
- **THEN** the same page opens, or the page the address redirected to before the change opens

#### Scenario: The server needs no change

- **WHEN** the change is deployed
- **THEN** the server answers the same segments with the dashboard document, and the reserved segment list in the backend is unchanged

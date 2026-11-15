# Spec Delta

## Purpose

An administrator can see, create, edit and delete the repositories of an instance on one page, with the type, visibility,
storage, version policy and write policy of each in view, using a keyboard alone or a screen reader. The page reads and
writes the existing `maven` settings domain and changes no stored data by itself.

## ADDED Requirements

### Requirement: Page address and access

The dashboard SHALL serve the Repositories page at `/settings/repositories` and one repository at
`/settings/repositories/<id>`. A visitor SHALL need the same permission as the Settings page. The pages SHALL use the one
settings draft, save bar, conflict dialog and leave guard, and SHALL add no API and no server route.

#### Scenario: Permitted administrator

- **WHEN** a signed in visitor with the settings node opens `/settings/repositories`
- **THEN** the table of the repositories of the `maven` domain is shown

#### Scenario: Visitor without the node

- **WHEN** a signed in visitor without the settings node opens `/settings/repositories`
- **THEN** the page shows the no access message at the same address and no repository data is requested

#### Scenario: Shared draft

- **WHEN** an administrator edits a repository on this page and then opens another Settings page
- **THEN** the unsaved change is still counted by the one save bar

### Requirement: Table columns

The table SHALL list every repository of the draft with the columns Name, Type, Visibility, Version policy, Write policy,
Storage and Address. Version policy and Write policy SHALL be shown only when the settings schema served by the instance
declares the fields. A repository that is not saved yet SHALL be marked "Unsaved" in text.

#### Scenario: Default repositories

- **WHEN** the instance has only the default repositories
- **THEN** the table shows `releases`, `snapshots` and `private` with their type, visibility, storage and address

#### Scenario: Schema without policy fields

- **WHEN** the served schema has no `versionPolicy` or `readOnly` field
- **THEN** the Version policy column is absent and the Write policy column shows Allow or Allow once from `redeployment`

#### Scenario: Unsaved repository

- **WHEN** an administrator creates a repository and has not saved
- **THEN** its row appears at once with the text "Unsaved"

### Requirement: Filter and sort

The page SHALL filter the rows by text over id, description and tags and by `tag:<name>` tokens, using the rules of the
repository search. It SHALL sort by Name, Type, Visibility and Storage, ascending or descending, in a stable order.

#### Scenario: Tag filter

- **WHEN** the filter contains `tag:team-a`
- **THEN** only repositories with that tag are listed and the count reflects it

#### Scenario: Sort twice

- **WHEN** an administrator activates the Name sort twice
- **THEN** the rows are in descending name order and that header reports descending

### Requirement: Derived type badge

The Type column and the detail SHALL name the effective type in words. For a repository without a declared type the badge
SHALL also say "derived". "Declare type" SHALL write only the `type` key with the derived value. A derived Mixed type SHALL
show a warning in text and no declare action.

#### Scenario: Declare a derived type

- **WHEN** an administrator declares the derived Proxy type of an untyped repository
- **THEN** the draft entry gains `type: PROXY` and no other key changes

#### Scenario: Empty mirrored entries

- **WHEN** declaring a type would drop empty mirrored entries
- **THEN** a dialog states how many entries are removed and nothing changes until it is confirmed

#### Scenario: Mixed type

- **WHEN** a repository references both local and remote repositories
- **THEN** the badge reads Mixed with the warning text and offers no declare action

### Requirement: Policy controls

The detail of a hosted repository SHALL offer a Write policy choice of Allow, Allow once and Read only, stored as
`redeployment` and `readOnly`, and SHALL offer a Version policy choice when the schema has the field. No new key SHALL be
stored for the write policy. A key SHALL be written only when the choice changes it.

#### Scenario: Allow once

- **WHEN** an administrator selects Allow once on a repository whose `redeployment` is true
- **THEN** the draft has `redeployment: false` and no `readOnly` key is added

#### Scenario: Read only keeps redeployment

- **WHEN** an administrator selects Read only and later Allow once
- **THEN** `redeployment` has the value it had before and `readOnly` is no longer true

#### Scenario: Untouched repository

- **WHEN** an administrator opens a repository and saves without changing the control
- **THEN** its stored JSON is identical to before

### Requirement: Create

The page SHALL offer "New repository", which opens the existing guided wizard, and SHALL open the wizard once for the
query `new=repository`. After creation the focus SHALL move to the heading of the new repository's detail.

#### Scenario: Link from the getting started card

- **WHEN** a visitor opens `/settings/repositories?new=repository`
- **THEN** the wizard opens once and the query is removed from the address

### Requirement: Delete

Deleting SHALL ask for the repository name to be typed, SHALL list the groups of the draft that name the repository as a
member, and SHALL be a draft change that takes effect on save. Initial focus SHALL be on Cancel.

#### Scenario: Member of a group

- **WHEN** an administrator deletes a repository that a group lists as a member
- **THEN** the dialog names that group before the confirm button can be used

#### Scenario: Confirm

- **WHEN** the typed name matches and the administrator confirms
- **THEN** the row is removed from the draft, the save bar shows the change and focus moves to the page heading

### Requirement: Old addresses keep working

The address `/settings/maven/repositories/<id>` SHALL redirect to `/settings/repositories/<id>`, and `/settings/maven` with
`new=repository` SHALL redirect to the new page with the query kept. Other collections of other domains SHALL NOT redirect.

#### Scenario: Bookmarked entry

- **WHEN** a visitor opens `/settings/maven/repositories/releases`
- **THEN** the address becomes `/settings/repositories/releases` and the detail of `releases` is shown

#### Scenario: Cleanup policy entry

- **WHEN** a visitor opens an entry of the `cleanup` domain
- **THEN** the address is unchanged

### Requirement: Keyboard operation

Every function of the page SHALL be operable with the keyboard alone, in a logical Tab order, with a visible focus
indicator, without a keyboard trap and without a positive `tabindex`. A row SHALL be reached in the order name link, then
its actions. The page SHALL use a plain table, not an ARIA grid.

#### Scenario: Tab through a row

- **WHEN** focus is on the name link of a row and the visitor presses Tab
- **THEN** focus moves to the first action of that same row

#### Scenario: Escape closes a dialog

- **WHEN** a dialog is open and the visitor presses Escape
- **THEN** the dialog closes and focus returns to the control that opened it

### Requirement: Screen reader semantics

The table SHALL have a caption with the shown and total count, column headers with `scope="col"` and the name cell as a row
header. A sorted header SHALL carry `aria-sort`. Every control SHALL have an accessible name that includes the repository
where it acts on one, such as "Delete releases". Type and state SHALL be conveyed in text, not by colour or icon alone.

#### Scenario: Sort announcement

- **WHEN** a visitor sorts by Type
- **THEN** only that header has `aria-sort` and a status region reports the sort

#### Scenario: Filter announcement

- **WHEN** the filter text changes the number of rows
- **THEN** a status region reports the new count once

#### Scenario: Action names

- **WHEN** the actions of the row of `releases` are read
- **THEN** their names are "Edit releases" and "Delete releases"

### Requirement: Focus and messages after changes

After create, the focus SHALL be on the new detail heading. After delete, the focus SHALL be on the list heading and a
status message SHALL say the repository is deleted and not saved yet. A refused wizard step SHALL move focus to an error
summary, and each invalid field SHALL be marked `aria-invalid` and described by its message. Focus SHALL never rest on a
removed element.

#### Scenario: After delete

- **WHEN** a repository is deleted from the draft
- **THEN** focus is on the page heading and the status region says it is deleted and not saved yet

#### Scenario: Invalid id

- **WHEN** an id that is already in use is typed in the wizard
- **THEN** the field is `aria-invalid` and `aria-describedby` points to the message

### Requirement: Reflow and target size

At a viewport of 320 CSS pixels and 200 percent zoom the page SHALL need no two-dimensional scroll except inside the table
region, which SHALL be named and focusable. Interactive controls SHALL be at least 24 by 24 CSS pixels. Text and icon
colours of badges SHALL meet 4.5 to 1 and 3 to 1, and animation SHALL respect `prefers-reduced-motion`.

#### Scenario: Narrow viewport

- **WHEN** the page is shown at 320 pixels
- **THEN** the table scrolls inside a focusable region with an accessible name and the page itself does not scroll sideways

# Spec Delta

## Purpose

The Administration tree never has a scroll box of its own, the narrow index gives each row a description, and from 600 to 1239
pixels the tree opens as a sheet without leaving the page.

## MODIFIED Requirements

### Requirement: Side navigation does not overlap bars

From 1240 pixels the side navigation SHALL stop below the top bar and above the save bar. It SHALL be sticky only while its full
height fits between the top bar and the save bar; otherwise it SHALL scroll with the page. It SHALL NOT have a scroll container of its
own in either state. Whether it is sticky SHALL be decided when the window or the navigation changes size, and SHALL NOT change while
the page scrolls.

#### Scenario: Tree fits

- **WHEN** a full administrator opens an Administration page at 2560 by 1440 pixels
- **THEN** the side navigation is sticky below the top bar, and no element inside it scrolls on its own

#### Scenario: Tree taller than the window

- **WHEN** a full administrator opens an Administration page at 1920 by 1080 or 1366 by 768 pixels and the tree is taller than the
  space between the bars
- **THEN** the side navigation is not sticky, scrolls with the page, and its last entry is reached by scrolling the window

#### Scenario: No jump while scrolling

- **WHEN** the administrator scrolls such a page from top to bottom
- **THEN** the side navigation does not switch between sticky and static during the scroll

#### Scenario: Save bar appears

- **WHEN** the tree fits, and a draft makes the save bar appear
- **THEN** the decision is taken again with the save bar's height, and no tree entry is drawn under the save bar

## ADDED Requirements

### Requirement: Index rows show the description

Below 1240 pixels each row of the Administration index SHALL show the entry's label and, under it, its one-line description. A row
without a description SHALL show its label only. From 1240 pixels the rows of the side navigation SHALL show the label only.

#### Scenario: Index at 900 pixels

- **WHEN** a full administrator opens `/settings` at 900 pixels wide
- **THEN** the Reverse proxy row shows its label and the description that names the forwarded headers

#### Scenario: Side column

- **WHEN** the same visitor opens `/settings` at 1440 pixels wide
- **THEN** the rows of the side navigation show labels only

### Requirement: Sections sheet from 600 to 1239 pixels

From 600 to 1239 pixels each Administration page SHALL show a Sections button beside the back arrow, with a target of at least 48 by
48 pixels. It SHALL open a modal side sheet named "Administration pages" that holds the Administration tree with the current entry
marked. Choosing an entry SHALL open it and close the sheet. Below 600 and from 1240 pixels the button SHALL NOT be drawn.

#### Scenario: Move to a sibling page

- **WHEN** a full administrator on `/access/ldap` at 900 pixels activates Sections and then Users
- **THEN** `/access/users` opens and the sheet is closed

#### Scenario: Not on phones

- **WHEN** a visitor opens an Administration page at 320 pixels wide
- **THEN** no Sections button is drawn, and the page does not scroll horizontally

### Requirement: The sections sheet keeps focus

While the sections sheet is open, focus SHALL stay inside it. Escape and the close button SHALL close the sheet and return focus to
the Sections button.

#### Scenario: Close with Escape

- **WHEN** the sheet is open and the administrator presses Escape
- **THEN** the sheet closes and focus is on the Sections button

#### Scenario: Focus stays in the sheet

- **WHEN** the sheet is open and the administrator presses Tab past its last element
- **THEN** focus moves to the first element of the sheet

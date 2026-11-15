# administration-layout Specification

## Purpose
The layout of the Administration pages between a phone and a wide desktop. It keeps the side tree beside the content from 1240 pixels, turns it into an index below that width, keeps the page header above the columns, and decides which table columns are shortened or hidden first.

## Requirements

### Requirement: Layout at four widths

The Administration pages SHALL use one layout at four widths: below 600 pixels with no rail, and the Administration tree as an index page that a back arrow leads back to; from 600 to 1239 pixels with the rail, the index page, and the tree in a Sections sheet on every other page; and from 1240 pixels with the side navigation beside the page. The wide pages take the full width after the side navigation at every width.

#### Scenario: Index below 1240 pixels

- **WHEN** a visitor opens an Administration page at 1000 pixels wide
- **THEN** the page is drawn at full width after the rail, with a back arrow beside its heading that leads to the index, and the side navigation is not drawn beside the page

#### Scenario: Index page below 1240 pixels

- **WHEN** a visitor opens `/settings` at 1000 pixels wide
- **THEN** the side navigation is the index, drawn in place of the page content and at full width after the rail

#### Scenario: Side navigation beside the page

- **WHEN** a visitor opens an Administration page at 1280 pixels wide
- **THEN** the side navigation is drawn beside the page at 208 pixels wide

### Requirement: Page header outside the columns

The page heading and the page actions SHALL sit in one header row above the side navigation and the content. The header row SHALL NOT be sticky. Only the side navigation at 1240 pixels and wider while it fits between the bars, and the top bar and the save bar, SHALL be sticky. The decorative corner pieces that the page shell draws behind the columns are not part of the header or the page content and are not counted.

#### Scenario: Header scrolls away

- **WHEN** a visitor scrolls an Administration page down by 200 pixels
- **THEN** the page heading is no longer drawn under the top bar, and the top bar stays at the top of the window

#### Scenario: Only one sticky column

- **WHEN** an Administration page is drawn at 1280 by 1440 pixels and the tree fits between the bars
- **THEN** the side navigation is the only element of the page content with a sticky position

#### Scenario: No sticky column when the tree does not fit

- **WHEN** an Administration page is drawn at 1366 by 768 pixels and the tree is taller than the space between the bars
- **THEN** no element of the page content has a sticky position

### Requirement: Side navigation does not overlap bars

From 1240 pixels the side navigation SHALL stop below the top bar and above the save bar. It SHALL be sticky only while its full height fits between the top bar and the save bar; otherwise it SHALL scroll with the page. It SHALL NOT have a scroll container of its own in either state. Whether it is sticky SHALL be decided when the window or the navigation changes size, and SHALL NOT change while the page scrolls.

#### Scenario: Tree fits

- **WHEN** a full administrator opens an Administration page at 2560 by 1440 pixels
- **THEN** the side navigation is sticky below the top bar, and no element inside it scrolls on its own

#### Scenario: Tree taller than the window

- **WHEN** a full administrator opens an Administration page at 1920 by 1080 or 1366 by 768 pixels and the tree is taller than the space between the bars
- **THEN** the side navigation is not sticky, scrolls with the page, and its last entry is reached by scrolling the window

#### Scenario: No jump while scrolling

- **WHEN** the administrator scrolls such a page from top to bottom
- **THEN** the side navigation does not switch between sticky and static during the scroll

#### Scenario: Save bar appears

- **WHEN** the tree fits, and a draft makes the save bar appear
- **THEN** the decision is taken again with the save bar's height, and no tree entry is drawn under the save bar

### Requirement: Index rows show the description

Below 1240 pixels each row of the Administration index SHALL show the entry's label and, under it, its one-line description. A row without a description SHALL show its label only. From 1240 pixels the rows of the side navigation SHALL show the label only.

#### Scenario: Index at 900 pixels

- **WHEN** a full administrator opens `/settings` at 900 pixels wide
- **THEN** the Reverse proxy row shows its label and the description "The headers that carry the client address and the path prefix behind a proxy."

#### Scenario: Side column

- **WHEN** the same visitor opens `/settings` at 1440 pixels wide
- **THEN** the rows of the side navigation show labels only

### Requirement: Sections sheet from 600 to 1239 pixels

From 600 to 1239 pixels each Administration page that draws a back arrow SHALL show a Sections button beside it, with a target of at least 48 by 48 pixels. It SHALL open a modal side sheet named "Administration pages" that holds the Administration tree with the current entry marked. Choosing an entry SHALL open it and close the sheet. A page that shows the index itself (`/settings`) SHALL NOT draw the button. Below 600 and from 1240 pixels the button SHALL NOT be drawn.

#### Scenario: Move to a sibling page

- **WHEN** a full administrator on `/access/ldap` at 900 pixels activates Sections and then Users
- **THEN** `/access/users` opens and the sheet is closed

#### Scenario: Not on the index

- **WHEN** a full administrator opens `/settings` at 900 pixels wide
- **THEN** no Sections button is drawn, and the index is on the page

#### Scenario: Not on phones

- **WHEN** a visitor opens an Administration page at 320 pixels wide
- **THEN** no Sections button is drawn, and the page does not scroll horizontally

### Requirement: The sections sheet keeps focus

While the sections sheet is open, focus SHALL stay inside it. Escape and the close button SHALL close the sheet and return focus to the Sections button.

#### Scenario: Close with Escape

- **WHEN** the sheet is open and the administrator presses Escape
- **THEN** the sheet closes and focus is on the Sections button

#### Scenario: Focus stays in the sheet

- **WHEN** the sheet is open and the administrator presses Tab past its last element
- **THEN** focus moves to the first element of the sheet

### Requirement: Names and addresses do not break mid-word

A repository name and an address SHALL be drawn on one line and truncated with an ellipsis. The full value SHALL be the
title of the cell and the accessible name of the name link.

#### Scenario: Long repository name

- **WHEN** the table lists a repository named onelitefeather-snapshots at 1280 pixels wide
- **THEN** the name is drawn on one line, is not split inside the word, and its title and accessible name are the full name

#### Scenario: Long address

- **WHEN** the table lists an address longer than its column
- **THEN** the address is drawn on one line with an ellipsis, and its title is the full address

### Requirement: One Actions menu per row

The Repositories table SHALL show Edit, Copy address and Delete for each row in one menu button named "Actions for <name>".
Its items SHALL keep the accessible names "Edit <name>", "Copy address of <name>" and "Delete <name>".

#### Scenario: Opening the menu

- **WHEN** a keyboard user activates the Actions button of the row releases
- **THEN** a menu opens with the items "Edit releases", "Copy address of releases" and "Delete releases", and the first item has focus

#### Scenario: Closing the menu

- **WHEN** the menu is open and the user presses Escape
- **THEN** the menu closes and focus returns to the Actions button

### Requirement: Column priority

The Repositories table SHALL hide its low-priority columns at narrower widths: Storage first, then Version policy and
Visibility, then Write policy and Address. Name, Type and Actions SHALL always be shown.

#### Scenario: Narrow desktop

- **WHEN** the table is drawn at 1280 pixels wide
- **THEN** Name, Type, Address, Write policy and Actions are shown, and Version policy, Visibility and Storage are not

#### Scenario: Name and Actions stay

- **WHEN** the table is drawn at any width
- **THEN** the Name and Actions columns are shown

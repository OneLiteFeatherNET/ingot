# Spec Delta

## ADDED Requirements

### Requirement: Layout at four widths

The Administration pages SHALL use one layout at four widths: below 600 pixels with no rail and the side navigation as an
index; from 600 to 1239 pixels with the rail and the side navigation as an index; from 1240 pixels with the side navigation
beside the page; and from 1920 pixels with the content at most 1600 pixels wide.

#### Scenario: Index below 1240 pixels

- **WHEN** a visitor opens an Administration page at 1000 pixels wide
- **THEN** the side navigation is an index with a back arrow, and the page is drawn at full width after the rail

#### Scenario: Side navigation beside the page

- **WHEN** a visitor opens an Administration page at 1280 pixels wide
- **THEN** the side navigation is drawn beside the page at 208 pixels wide

#### Scenario: Content cap

- **WHEN** a visitor opens an Administration page at 1920 pixels wide
- **THEN** the content is at most 1600 pixels wide and centred in the space after the side navigation

### Requirement: Page header outside the columns

The page heading and the page actions SHALL sit in one header row above the side navigation and the content. The header row
SHALL NOT be sticky. Only the side navigation at 1240 pixels and wider, and the top bar and the save bar, SHALL be sticky.

#### Scenario: Header scrolls away

- **WHEN** a visitor scrolls an Administration page down by 200 pixels
- **THEN** the page heading is no longer drawn under the top bar, and the top bar stays at the top of the window

#### Scenario: Only one sticky column

- **WHEN** an Administration page is drawn at 1280 pixels wide
- **THEN** the side navigation is the only element of the page content with a sticky position

### Requirement: Side navigation does not overlap bars

The side navigation at 1240 pixels and wider SHALL stop below the top bar and above the save bar, and SHALL scroll inside itself
when it is taller than the window.

#### Scenario: Tall navigation

- **WHEN** the side navigation is taller than the window at 1280 pixels
- **THEN** it scrolls inside its own box, and its top edge stays below the top bar

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

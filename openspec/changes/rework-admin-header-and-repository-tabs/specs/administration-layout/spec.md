# Spec Delta

## MODIFIED Requirements

### Requirement: Page header outside the columns

The page heading and the page actions SHALL form one header row. From 1240 pixels the header row SHALL sit in the page
column, beside the side navigation, and the side navigation SHALL start at the same top edge as the header. Below 1240
pixels the header row SHALL sit above the side navigation and the page, as the index layout. The header row SHALL NOT be
sticky. Only the side navigation at 1240 pixels and wider, and the top bar and the save bar, SHALL be sticky.

#### Scenario: Header beside the side navigation

- **WHEN** a visitor opens an Administration page at 1280 pixels wide
- **THEN** the page heading starts at the left edge of the page column, to the right of the side navigation, and its top
  edge is the top edge of the side navigation

#### Scenario: Header above the index

- **WHEN** a visitor opens the Administration index at 1000 pixels wide
- **THEN** the page heading is drawn above the index and the index starts below it

#### Scenario: Header scrolls away

- **WHEN** a visitor scrolls an Administration page down by 200 pixels at 1280 pixels wide
- **THEN** the page heading is no longer drawn under the top bar, and the top bar stays at the top of the window

#### Scenario: Only one sticky column

- **WHEN** an Administration page is drawn at 1280 pixels wide
- **THEN** the side navigation is the only element of the page content with a sticky position

## ADDED Requirements

### Requirement: Stable width under a scrollbar

The root of the document SHALL reserve the width of a vertical scrollbar, so that a page which becomes taller than the window
does not move the Administration frame sideways.

#### Scenario: Scrollbar does not move the frame

- **WHEN** a page becomes taller than the window at 1440 pixels wide and a classic scrollbar is drawn
- **THEN** the left edge of the frame is the same as on a page that has no scrollbar

### Requirement: Reserved header rows

The title block of the Administration frame SHALL draw its first row (back arrow and trail) at its full height, one line for
the heading and one line for the description on every page, whether or not the page has them. The heading SHALL NOT wrap to
a second line. The header SHALL have the same height on every Administration page at one width.

#### Scenario: Same header height on every page

- **WHEN** a visitor moves from the Overview to a group page, and from a list to a detail page, at 1440 pixels wide
- **THEN** the header has the same height on each page, and the side navigation and the content start at the same top edge

#### Scenario: Index entry to page at narrow width

- **WHEN** a visitor opens an index entry at 600 pixels wide
- **THEN** the page content does not move down when the back arrow appears, because the first row is already drawn

### Requirement: Content that arrives after the page is drawn

Content that is loaded after the page is drawn SHALL NOT move the side navigation or the header. The loading text of the settings
page SHALL reserve the height of the first row of the page.

#### Scenario: Loading settings

- **WHEN** the settings configuration is still loading and then arrives
- **THEN** the header and the side navigation do not move

### Requirement: Page change animation

When a visitor opens another Administration entry, the page column SHALL fade in and rise 4 pixels over 200 milliseconds with
the standard easing of the motion tokens. The page column SHALL have no leave transition, and the side navigation and the
header SHALL NOT animate.

#### Scenario: Entry change animates

- **WHEN** a visitor opens another Administration entry
- **THEN** the page column plays the enter animation, and the side navigation and the header do not

### Requirement: No replay for query changes and reduced motion

A change of the query of a page, or a rename of an entry, SHALL NOT replay the page animation or remount the page. When the
visitor prefers reduced motion, the page column SHALL NOT animate.

#### Scenario: Tab change does not remount

- **WHEN** a visitor changes the tab of a repository page
- **THEN** the page column does not play the enter animation and the value of a field that has focus is kept

#### Scenario: Reduced motion

- **WHEN** the visitor prefers reduced motion and opens another Administration entry
- **THEN** the page column has no animation and its content appears at once

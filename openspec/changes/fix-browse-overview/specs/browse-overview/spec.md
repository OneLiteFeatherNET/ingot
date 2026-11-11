# Spec Delta

## Purpose

The Browse page at `/` lists the repositories and shows the build snippet of one of them. This delta makes the snippet
readable at every width, names the repository it is for, and makes the repository filter a filter of the list. It also
explains the derived type Mixed next to the declared types. No address, API or stored value changes.

## ADDED Requirements

### Requirement: Snippet URLs wrap instead of scrolling

The repository URL in the build snippet SHALL wrap inside its own characters when the card is narrower than the URL. The
rest of the snippet SHALL keep its indentation and line structure. The copied text SHALL contain no line break or character
that is not in the snippet.

#### Scenario: A long URL on a wide screen

- **WHEN** a visitor opens the Browse page at 1920px and selects a repository with a long URL
- **THEN** the whole URL is visible inside the snippet card without a horizontal scrollbar, and it breaks only inside the URL

#### Scenario: A long URL on a phone

- **WHEN** a visitor opens the Browse page at 320px and selects Gradle Kotlin
- **THEN** the URL wraps inside the card, and the code block shows no horizontal scrollbar for the URL

#### Scenario: The copied text is unchanged by the wrap

- **WHEN** a visitor copies the snippet of a wrapped URL
- **THEN** the clipboard holds the same text as the unwrapped snippet, with no added line break

### Requirement: Snippet card placement by width

The snippet card SHALL sit in its own column on the right from 1024px, at its current width. Below 1024px the card SHALL
follow the repository list, so the list is the first content a visitor reaches on a narrow screen.

#### Scenario: Wide screen keeps the column

- **WHEN** a visitor opens the Browse page at 1280px
- **THEN** the repository list and the snippet card are side by side, the card on the right

#### Scenario: Narrow screen shows the list first

- **WHEN** a visitor opens the Browse page at 390px
- **THEN** the repository list comes before the snippet card in the reading order

### Requirement: Snippet heading names the repository

On a repository path the snippet card SHALL be headed with the repository it is for, in the form "Repository details: <id>".
On the root page, where no repository is selected, the card SHALL be headed "Use a repository" and SHALL say in one line which
placeholder to replace. The artifact card keeps its own heading.

#### Scenario: A repository is selected

- **WHEN** a visitor opens `/browse/releases`
- **THEN** the card heading reads "Repository details: releases"

#### Scenario: The root page has no repository

- **WHEN** a visitor opens `/`
- **THEN** the card heading reads "Use a repository", and the caption tells the visitor to replace `<repository>` with a repository id from the list

### Requirement: Copy feedback is announced

The copy action SHALL keep its name and its place above the code, and it SHALL NOT sit over a line of the snippet. The outcome of
a copy (copied, or the text selected for a manual copy) SHALL be reported in one status region that is present before the
outcome is shown, so that assistive technology announces it.

#### Scenario: A successful copy is announced

- **WHEN** a visitor activates the copy action and the clipboard accepts the text
- **THEN** the status region reads "Copied" and the region was present before the click

#### Scenario: A refused copy selects the text

- **WHEN** a visitor activates the copy action and the clipboard refuses the text
- **THEN** the snippet is selected, and the status region tells the visitor to press Ctrl+C or Cmd+C

### Requirement: Repository filter is a filter of the list

The field above the repository list SHALL be a filter of that list. It SHALL be labelled "Filter repositories", it SHALL use the
outlined field of the Administration Repositories page with a filter icon, and it SHALL NOT be a search landmark. It SHALL
read `tag:name` words as tags, as the Administration page does. The number of shown repositories SHALL be announced in a
status region that is present while the list loads.

#### Scenario: The field is named as a filter

- **WHEN** a visitor opens the Browse page
- **THEN** the field has the accessible name "Filter repositories" and the supporting text "Search by id, description or tag. Use tag:name for one tag."

#### Scenario: A tag word filters by tag

- **WHEN** a visitor types `tag:ci` into the filter
- **THEN** only the repositories that carry the tag `ci` are shown, and the count reads "N of M repositories"

#### Scenario: The count is announced

- **WHEN** a visitor types text that leaves three repositories
- **THEN** the status region reads "3 of 7 repositories"

### Requirement: No match offers the artifact search

When the filter text matches no repository, the list SHALL offer a link to the artifact search page with the text already
entered, next to the "Clear filters" action. When the text is blank, no such link SHALL be offered.

#### Scenario: An artifact name typed into the filter

- **WHEN** a visitor types `guava` into the filter and no repository matches
- **THEN** the page offers a link "Search artifacts for guava" that opens the Search page with `q=guava`

#### Scenario: Only tags selected

- **WHEN** a visitor selects a tag chip that no repository carries
- **THEN** the page offers "Clear filters" and no artifact search link

### Requirement: Type chips separate derived Mixed from declared types

The type chips SHALL show Hosted, Proxy and Group as declared types in one group named "Repository type". Mixed SHALL be shown in
a separate group named "Derived type", with a sentence that says no type is declared and the repository proxies both local and
remote repositories. The address value of each chip SHALL stay the same.

#### Scenario: Mixed is explained next to its chip

- **WHEN** a visitor opens the Browse page
- **THEN** the chip Mixed sits in the group "Derived type", and the sentence of that group names local and remote repositories

#### Scenario: Selecting Mixed keeps the address value

- **WHEN** a visitor selects the Mixed chip
- **THEN** the address contains `type=mixed` and the list shows the repositories whose type is mixed

### Requirement: Row marks stay inside the row at compact widths

Below 600px the type badge and the visibility label of a repository row SHALL move to a line below the repository id, and the
avatar, the id and the chevron SHALL stay on the first line. No mark SHALL be clipped at 320px. From 600px the row SHALL keep
its single line.

#### Scenario: A legacy repository at 320px

- **WHEN** a visitor opens the Browse page at 320px and a repository has a legacy type badge and is private
- **THEN** the type badge and the Private label are visible below the id, and the chevron is visible at the end of the first line

#### Scenario: A row at 1280px

- **WHEN** a visitor opens the Browse page at 1280px
- **THEN** the type badge, the visibility label and the chevron are on one line with the id

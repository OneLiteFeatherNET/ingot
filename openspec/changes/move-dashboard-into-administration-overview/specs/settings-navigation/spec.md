# Spec Delta

## Purpose

The Overview page no longer sends the visitor to a Dashboard page, because the status is on the page.

## MODIFIED Requirements

### Requirement: Settings hub at /settings

With no domain in the address, the Settings page SHALL show Overview: the heading Overview, a description (see the requirement "The
overview description names both halves" of `administration-overview`), a section Needs attention and a section At a glance for a
visitor who holds the settings node, and the instance status for a visitor who holds the dashboard node. It SHALL NOT contain a link
to a Dashboard page and SHALL NOT list the pages of the Administration tree as cards or links. Needs attention SHALL list one row per
settings domain with an unsaved draft, linking to that domain's page, a control that opens the review of all drafts, and a save
conflict when one is present; with none of these it SHALL read "Nothing needs attention.". At a glance SHALL list the count of every
settings collection the visitor may edit, taken from the saved settings, and SHALL render all counts at once after the saved
settings are loaded. Its settings half SHALL carry `data-ingot-component="settings-hub"`.

#### Scenario: Drafts need attention

- **WHEN** a visitor who may edit Settings has changed the draft of Javadoc and opens `/settings`
- **THEN** Needs attention lists Javadoc with the text Unsaved changes, linking to `/settings/javadoc`, and the saved value of Javadoc is unchanged

#### Scenario: Nothing to do

- **WHEN** a visitor who may edit Settings has no draft and no conflict and opens `/settings`
- **THEN** Needs attention reads "Nothing needs attention."

#### Scenario: Counts render together

- **WHEN** a full administrator with seven repositories and no cleanup policy opens `/settings`
- **THEN** At a glance lists Repositories 7 and Cleanup policies 0 in the same render, and shows no number before the saved settings have loaded

#### Scenario: No link to a Dashboard page

- **WHEN** a full administrator opens `/settings`
- **THEN** the page contains no link whose text is "Dashboard" and no line that sends the visitor to a page for the instance status

#### Scenario: No copy of the tree

- **WHEN** a full administrator opens `/settings` at 1440 pixels wide
- **THEN** the content area contains no link to an Administration page that the tree also lists, other than the rows of Needs attention

#### Scenario: Anchor kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="settings-hub"]`
- **THEN** it matches the root of the settings half of Overview

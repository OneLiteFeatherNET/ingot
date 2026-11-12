# Spec Delta

## Purpose

The page at `/settings` becomes Overview: it shows what needs attention and the whole-configuration actions, and it no longer
repeats the Administration tree as cards.

## MODIFIED Requirements

### Requirement: Settings hub at /settings

With no domain in the address, the Settings page SHALL show Overview: the heading Overview, the description "Shared configuration
for all instances.", a section Needs attention, a section At a glance and, when the visitor may open the dashboard, a link to it.
It SHALL NOT list the pages of the Administration tree as cards or links. Needs attention SHALL list one row per settings domain
with an unsaved draft, linking to that domain's page, a control that opens the review of all drafts, and a save conflict when one
is present; with none of these it SHALL read "Nothing needs attention.". At a glance SHALL list the count of every settings collection
the visitor may edit, taken from the saved settings, and SHALL render all counts at once after the saved settings are loaded. The
page SHALL make no request beyond those the Settings page makes today. Its root element SHALL carry
`data-ingot-component="settings-hub"`.

#### Scenario: Drafts need attention

- **WHEN** a visitor who may edit Settings has changed the draft of Javadoc and opens `/settings`
- **THEN** Needs attention lists Javadoc with the text Unsaved changes, linking to `/settings/javadoc`, and the saved value of
  Javadoc is unchanged

#### Scenario: Nothing to do

- **WHEN** a visitor who may edit Settings has no draft and no conflict and opens `/settings`
- **THEN** Needs attention reads "Nothing needs attention."

#### Scenario: Counts render together

- **WHEN** a full administrator with seven repositories and no cleanup policy opens `/settings`
- **THEN** At a glance lists Repositories 7 and Cleanup policies 0 in the same render, and shows no number before the saved
  settings have loaded

#### Scenario: Plugin collection counted

- **WHEN** a plugin section's domain has a collection
- **THEN** At a glance lists its count under the section label

#### Scenario: No copy of the tree

- **WHEN** a full administrator opens `/settings` at 1440 pixels wide
- **THEN** the content area contains no link to an Administration page that the tree also lists, other than the rows of Needs
  attention

#### Scenario: Anchor kept

- **WHEN** a plugin stylesheet selects `[data-ingot-component="settings-hub"]`
- **THEN** it matches the root of the Overview content

#### Scenario: A visitor who may not edit Settings

- **WHEN** a visitor without the settings permission opens `/settings`
- **THEN** no Overview content is shown, and the visitor is forwarded or shown the no access message as before this change

## ADDED Requirements

### Requirement: Settings file actions live on Overview

"Import settings" and "Download as JSON" SHALL be shown in the page header of Overview only, as outlined controls with the same
labels and behaviour as before. Settings domain pages and the Repositories page SHALL NOT show them. Importing SHALL load the file
into the draft without a confirmation, and nothing SHALL be stored before Save.

#### Scenario: Overview header

- **WHEN** a full administrator opens `/settings`
- **THEN** the header shows Import settings and Download as JSON, and no filled button

#### Scenario: Domain page header

- **WHEN** a full administrator opens `/settings/javadoc` or `/settings/repositories`
- **THEN** neither Import settings nor Download as JSON is shown

#### Scenario: Import stays a draft

- **WHEN** the administrator imports a valid settings file on Overview
- **THEN** the changed domains appear under Needs attention as unsaved, and the server settings are unchanged until Save

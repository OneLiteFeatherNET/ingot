# Spec Delta

## Purpose

The Settings hub and the Settings side navigation no longer show the Maven domain, because its repositories and its
domain-level options are edited on the Repositories page. The domain itself is unchanged in the saved settings, the export,
the import and the factory reset.

## MODIFIED Requirements

### Requirement: Settings hub at /settings

With no domain in the address, the Settings page SHALL show a hub of category cards, grouped as Repository, Instance, Data and Plugins.
Each card SHALL show the domain's title and its saved value. The hub SHALL show only the groups and cards the visitor may edit, SHALL
NOT show a card for the `maven` domain, and SHALL end with a line that links sign-in and security to Access.

#### Scenario: The hub lists the saved values

- **WHEN** a visitor who may edit Settings opens `/settings`
- **THEN** each card that is shown carries the saved value of its domain, and no card of the `maven` domain is shown

#### Scenario: Domains are placed by the mapping

- **WHEN** the hub is shown to a full administrator
- **THEN** Repository holds Javadoc and Cleanup, Instance holds Web and Frontend, Data holds Statistics, Audit and Search, and Plugins holds every other domain

#### Scenario: A plugin domain is never hidden

- **WHEN** the server lists a plugin domain that the mapping does not name
- **THEN** the hub shows it under Plugins, and the change does not require the plugin to name a group

#### Scenario: Unsaved changes are marked, not saved

- **WHEN** a visitor has changed the draft of a domain and opens the hub
- **THEN** that domain's card shows "Unsaved changes", the card's value is the saved value, and the draft is unchanged

#### Scenario: A visitor who may not edit Settings sees no hub cards

- **WHEN** a visitor without the settings permission opens `/settings`
- **THEN** no card is shown and the visitor is shown the no access message of the dashboard navigation

## ADDED Requirements

### Requirement: The Maven domain is edited on the Repositories page

The `maven` domain SHALL NOT be a tab, a card or a side navigation link of the Settings page. It SHALL still be loaded, saved,
exported, imported and reset with the other settings domains, and the Repositories page SHALL edit it. The settings API and the
settings schema SHALL NOT change.

#### Scenario: The domain is not a tab

- **WHEN** an administrator opens any Settings page
- **THEN** the side navigation does not list a Maven entry

#### Scenario: Export keeps the domain

- **WHEN** an administrator downloads the settings from the Settings page
- **THEN** the file contains the `maven` domain, as it did before this change

#### Scenario: Factory reset keeps the domain

- **WHEN** an administrator resets the settings to their defaults
- **THEN** the `maven` domain is reset with the other domains

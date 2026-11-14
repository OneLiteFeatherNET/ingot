# Spec Delta

## Purpose

Overview stops carrying the whole-configuration actions, which move to the page Backup and recovery.

## ADDED Requirements

### Requirement: Overview without backup actions

Overview SHALL NOT show "Import settings", "Download as JSON" or the factory reset. For a visitor with the settings area it SHALL
show one link to Backup and recovery. The page SHALL make no request beyond those it makes today.

#### Scenario: Header

- **WHEN** a visitor with the settings area opens `/settings`
- **THEN** the header has no Import settings and no Download as JSON, and Overview links to `/settings/backup`

### Requirement: Backup and recovery in the filter

The Administration filter SHALL find Backup and recovery by the words export, import, restore, recovery, reset, backup,
passphrase and download.

#### Scenario: Filter

- **WHEN** the visitor types "restore" into the filter
- **THEN** Backup and recovery is listed

# Spec Delta

## Purpose

Overview stops carrying the whole-configuration actions. They are on the Settings backup tab of the Maintenance page, which is
where the settings file is exported and restored. The factory reset stays on System and support.

## ADDED Requirements

### Requirement: Settings backup tab in Maintenance navigation

The Maintenance page SHALL list the Settings backup tab for a visitor with `ingot.admin.settings` and SHALL NOT list it for a
visitor without that area. Overview SHALL link to nothing for backup and recovery.

#### Scenario: Visitor with the settings area

- **WHEN** a visitor with `ingot.admin.settings` opens `/maintenance`
- **THEN** the tab "Settings backup" is listed beside Storage usage, Search index and Cleanup policies

#### Scenario: Visitor without the settings area

- **WHEN** a visitor without `ingot.admin.settings` opens `/maintenance`
- **THEN** the tab "Settings backup" is not listed

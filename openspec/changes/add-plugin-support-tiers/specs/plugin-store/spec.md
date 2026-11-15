# Spec Delta

## Purpose

The catalogue of the plugin store says which tier each entry has, so that the dashboard and the operator see it before a plugin is
selected. The tier stays information: an entry is loaded or refused by the rules of this capability and never by its tier.

## ADDED Requirements

### Requirement: Optional tier of a catalogue entry

Each entry of `ingot-plugin-catalogue.json` MAY carry a `tier` text with the value `core`, `official`, `community` or
`experimental`. A missing `tier` SHALL read as `community`. The field is additive, so the schema number stays `1`. A `tier` with any
other value SHALL refuse that entry only, and the other entries of the same catalogue SHALL stay usable.

#### Scenario: Entry without tier

- **WHEN** a schema 1 catalogue lists an entry with no `tier` field
- **THEN** the entry is usable and its tier is shown as Community

#### Scenario: Unknown tier value

- **WHEN** an entry declares `"tier": "gold"`
- **THEN** that entry is refused with a reason that names the field, and the other entries of the catalogue stay usable

### Requirement: Mismatch of catalogue tier and jar tier

When a store plugin loads, the store SHALL compare the tier of its catalogue entry with the effective tier of the loaded plugin.
A mismatch SHALL be logged as a warning that names the plugin and both tiers. The plugin SHALL still load, and the effective tier of the
loaded jar SHALL be the tier shown.

#### Scenario: Catalogue says Official, jar says Community

- **WHEN** the catalogue entry declares `official` and the jar declares Community
- **THEN** the plugin loads, the warning names both tiers, and the plugin is shown as Community

### Requirement: Unchanged defaults with the tier field

The catalogue reader of the tier field SHALL NOT run when no store variable is set. The existing requirement `Defaults are unchanged`
SHALL keep its meaning: with no store variable set, no store request is made.

#### Scenario: Fresh installation

- **WHEN** the server starts with no store variable set
- **THEN** the catalogue is not read and the tier of the store is not evaluated

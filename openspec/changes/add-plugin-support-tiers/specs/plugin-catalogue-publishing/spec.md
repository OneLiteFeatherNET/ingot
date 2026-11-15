# Spec Delta

## Purpose

The release that publishes plugins also publishes the tier of each one, and the tier of a published version never changes. A
release cannot list a plugin as Official or Experimental without the evidence the tier check requires.

## ADDED Requirements

### Requirement: Tier written from the tier check

The catalogue generator SHALL take the tier of each plugin from `build/plugin-tiers.json`, the file the tier check writes, and SHALL
write it as the `tier` field of the entry in lower case. The generator SHALL make no network call and SHALL stay deterministic.

#### Scenario: Two runs with the same tier file

- **WHEN** the generator runs twice with the same fixtures and the same tier file
- **THEN** both outputs are identical

#### Scenario: Tier file missing a plugin

- **WHEN** a plugin of the release has no entry in the tier file
- **THEN** the release fails before any upload and names the plugin

### Requirement: Tier check before generation

The release job SHALL run the tier check before the generator. A failed check SHALL stop the job before any upload.

#### Scenario: Declaration without evidence at release

- **WHEN** a plugin declares Official without a contract test on the release commit
- **THEN** the job fails before any upload and names the module

### Requirement: Tier of a released version is fixed

A listed version SHALL keep the tier it was published with. The generator SHALL stop with an error when a listed version has a different
tier in the new catalogue, in the same way it stops for a changed digest. A promotion or demotion SHALL be published as a new version.

#### Scenario: Tier changed for a listed version

- **WHEN** the tier in the tier file of a listed version differs from the tier in the catalogue
- **THEN** the generator stops, uploads nothing, and names the version and both tiers

#### Scenario: Yanked version

- **WHEN** a version is yanked
- **THEN** only its `yanked` flag changes, and its tier stays as it was

# support-policy Specification

## Purpose
A user knows where to ask for help and which release lines receive fixes, in the repository itself.

## Requirements

### Requirement: Support file exists

`SUPPORT.md` at the repository root SHALL say where to ask questions, where to report a vulnerability by linking `.github/SECURITY.md`, and which release lines receive fixes and for how long.

#### Scenario: User needs help

- **WHEN** a user opens `SUPPORT.md`
- **THEN** the file names the channel for questions, links the security policy, and states the support window

### Requirement: Support never gates a feature

`SUPPORT.md` SHALL state that support terms do not restrict any feature of Ingot.

#### Scenario: Reader asks about paid tiers

- **WHEN** a reader looks for a commercial tier
- **THEN** the file describes community support only and says no feature depends on a support arrangement

### Requirement: Supported versions agree across files

The `Supported Versions` section of `.github/SECURITY.md` SHALL list the same release lines as `SUPPORT.md` and SHALL NOT say that no release has been published.

#### Scenario: Reporter checks if a version is supported

- **WHEN** a reporter reads the table in `.github/SECURITY.md`
- **THEN** the table lists the release lines named in `SUPPORT.md`, and the text links `SUPPORT.md`

### Requirement: Tiers described in SUPPORT.md

`SUPPORT.md` SHALL name the four plugin tiers (Core, Official, Community and Experimental) and the support each one receives. It
SHALL link the definitions on `docs/gitbook/references/plugins/plugin-tiers.md`.

#### Scenario: Reader asks what Official means

- **WHEN** a reader looks for the meaning of the Official tier in `SUPPORT.md`
- **THEN** the file defines Official as a plugin maintained and tested by the OneLiteFeather maintainers, fixed on the supported release lines, on a best effort community basis, and links the definition

### Requirement: Existing support terms stay unchanged

`SUPPORT.md` SHALL keep its existing sentences that say the support is community support, that there is no paid tier, that there is no
service level agreement, and that no fix date is promised. The tier section SHALL only add text below them. Official SHALL NOT be
described as paid or as a service level.

#### Scenario: Existing sentences

- **WHEN** the tier section is added to `SUPPORT.md`
- **THEN** a diff of the file shows additions only, and the sentences about community support, no paid tier and no SLA are byte for byte unchanged

### Requirement: Support refers to the listed release lines

Core and Official support SHALL be stated for the release lines that the table in `SUPPORT.md` lists as supported, on a best effort basis.
The text SHALL NOT limit a tier to the latest release. A change of that table SHALL change the fixed lines of Core and Official plugins
with it, and no other list SHALL be written for the same promise.

#### Scenario: Previous line listed as supported

- **WHEN** the table lists the previous minor line with fixes
- **THEN** the Core and Official tiers are fixed on that line as well, on a best effort basis

### Requirement: Tier descriptions never gate a feature

The tier descriptions SHALL state that no feature of Ingot depends on a tier or on a support arrangement. Community plugins SHALL be
described as having no maintainer support beyond the public discussions.

#### Scenario: Community plugin

- **WHEN** a reader looks up the support of a Community plugin
- **THEN** the file says it receives community support only and that no feature depends on the tier

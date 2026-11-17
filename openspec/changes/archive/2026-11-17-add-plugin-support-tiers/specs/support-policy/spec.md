# Spec Delta

## Purpose

A reader of `SUPPORT.md` knows which level of support each plugin tier receives, and that the levels refer to the release lines
the file lists. No tier is described in terms of the latest release alone.

## ADDED Requirements

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

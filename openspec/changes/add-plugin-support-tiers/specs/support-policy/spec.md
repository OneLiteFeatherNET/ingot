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
- **THEN** the file says that Official plugins receive fixes for the release lines the file lists as supported, and links the definition

### Requirement: Support refers to the listed release lines

Core and Official support SHALL be stated for the release lines that the table in `SUPPORT.md` lists as supported. The text SHALL NOT
limit a tier to the latest release. A change of that table SHALL change the support of Core and Official plugins with it, and no
other list SHALL be written for the same promise.

#### Scenario: Previous line listed as supported

- **WHEN** the table lists the previous minor line with fixes
- **THEN** the Core and Official tiers receive fixes on that line as well

### Requirement: Tier descriptions never gate a feature

The tier descriptions SHALL state that no feature of Ingot depends on a tier or on a support arrangement. Community plugins SHALL be
described as having no maintainer support beyond the public discussions.

#### Scenario: Community plugin

- **WHEN** a reader looks up the support of a Community plugin
- **THEN** the file says it receives community support only and that no feature depends on the tier

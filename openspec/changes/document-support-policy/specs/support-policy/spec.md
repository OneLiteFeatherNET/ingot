# Spec Delta

## Purpose

A user knows where to ask for help and which release lines receive fixes, in the repository itself.

## ADDED Requirements

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

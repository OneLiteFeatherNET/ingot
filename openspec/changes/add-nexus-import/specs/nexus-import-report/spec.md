# Spec Delta

## Purpose

Before anything is written, the operator sees what will happen to each object in Nexus, what will not carry over, and why. Only
then are repository definitions created, and never in a way that touches an existing repository or widens access.

## ADDED Requirements

### Requirement: Dry run writes nothing

A job of kind `DRY_RUN` SHALL read Nexus and Ingot and SHALL NOT create a repository, write a file, or change a setting. It SHALL
produce a loss report, the estimated transfer size, and the list of blockers.

#### Scenario: No side effects

- **WHEN** a dry run completes
- **THEN** the `maven` settings domain, the storage and the search index are unchanged, and only job and report rows exist

### Requirement: Loss report rows

Every discovered object SHALL have one row with the object type, the Nexus id, the Ingot target, a status of `MAPPED`, `PARTIAL`,
`MANUAL`, `UNSUPPORTED` or `EXCLUDED`, the reason, the consequence and the operator action. The report SHALL be available as JSON and CSV.

#### Scenario: Unsupported format

- **WHEN** Nexus holds an npm repository
- **THEN** its row is `UNSUPPORTED` with its component count and size

#### Scenario: Deselected repository

- **WHEN** the operator excludes a repository
- **THEN** its row is `EXCLUDED` and nothing is done for it

#### Scenario: CSV download

- **WHEN** the report is requested with `format=csv`
- **THEN** the body has a header row and one line per report row, with fields quoted

### Requirement: Unreadable objects are reported once

When the credential cannot read an area, the report SHALL contain one row for the area that says so and SHALL NOT fail the job.

#### Scenario: Settings forbidden

- **WHEN** Nexus answers `403` for repository settings
- **THEN** each affected definition is `MANUAL`, and content copy stays possible

### Requirement: Maven definitions

For a selected maven2 repository the plugin SHALL create a definition in the `maven` settings domain: `HOSTED` with `redeployment`
true only for write policy `ALLOW`, `PROXY` with its remote URL and no credentials, or `GROUP` with local references in member
order. Visibility SHALL be `PRIVATE`.

#### Scenario: Group with a foreign member

- **WHEN** a Nexus group has a Maven member and an npm member
- **THEN** the Ingot group lists only the Maven member and the row is `PARTIAL` naming the dropped one

#### Scenario: Proxy credentials

- **WHEN** a Nexus proxy uses a remote password
- **THEN** the definition has no credentials and the row says they must be entered again

### Requirement: Existing repositories are never touched

When the target name exists in Ingot, the plugin SHALL NOT change, rename or retype that repository. It SHALL report a collision and
propose `<name>-nexus`. A job SHALL NOT start for a target whose name is invalid or reserved.

#### Scenario: Name taken

- **WHEN** Nexus has `releases` and Ingot already has `releases`
- **THEN** the existing repository is unchanged and the proposal is `releases-nexus`

### Requirement: Definitions are written once, with a check

Definitions SHALL be written in one update of the `maven` domain using the entity tag read at the start. On a precondition failure
the plugin SHALL re-read and retry once. When the shared settings file owns the domain, every definition SHALL be `MANUAL` with the
settings the operator can apply.

#### Scenario: Managed settings

- **WHEN** the domain is externally managed and the update answers `409`
- **THEN** no definition is created and each row carries the JSON to add by hand

### Requirement: Access is only narrowed

The plugin SHALL NOT create a deny rule, a token or a permission, and SHALL NOT make a repository more visible than `PRIVATE`.

#### Scenario: Anonymous Nexus

- **WHEN** Nexus allows anonymous read on a repository
- **THEN** the Ingot repository is `PRIVATE` and the row names the difference

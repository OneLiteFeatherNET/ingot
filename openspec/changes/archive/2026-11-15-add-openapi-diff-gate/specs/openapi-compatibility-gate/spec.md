# Spec Delta

## Purpose

A pull request that removes or incompatibly changes part of the documented REST API fails a check unless its title declares the
break with `!`. The API document is committed, kept fresh by a test, and attached to every release.

## ADDED Requirements

### Requirement: Committed snapshot stays equal to the served document

The repository SHALL contain a snapshot of the OpenAPI document, and a test SHALL fail when the served document differs from it,
ignoring the version field.

#### Scenario: Annotation change without refresh

- **WHEN** an annotation changes and the snapshot is not refreshed
- **THEN** the snapshot test fails and names the refresh command

### Requirement: Breaking change without marker fails

On a pull request, the check SHALL compare the snapshot on the base branch with the one on the pull request and SHALL fail when
error-level breaking changes exist and the title has no `!` after the type or scope.

#### Scenario: Endpoint removed, plain title

- **WHEN** a pull request removes an endpoint and its title is `fix(api): drop route`
- **THEN** the check fails and lists the removal

#### Scenario: Endpoint removed, marked title

- **WHEN** the title is `fix(api)!: drop route`
- **THEN** the check passes and reports the same changes

#### Scenario: Title edited

- **WHEN** the title is edited to add `!`
- **THEN** the check runs again and turns green without a new push

### Requirement: Additions are not breaking

Adding an endpoint, an optional parameter or an optional response field SHALL NOT fail the check.

#### Scenario: New endpoint

- **WHEN** a pull request only adds a documented endpoint
- **THEN** the check passes

### Requirement: Pinned tool, no data leaves CI

The workflow SHALL pin the diff action by commit SHA with the tag in a comment, SHALL run with `contents: read`, and SHALL NOT
upload the specs to a third party.

#### Scenario: Workflow lint

- **WHEN** the workflow file is inspected
- **THEN** every `uses:` line ends in a 40 character SHA, `review` is `false` and no write permission is requested

### Requirement: Release carries the document

A release SHALL attach the document as `ingot-<version>-openapi.json` with the release version in `info.version`.

#### Scenario: Release upload

- **WHEN** a release is published
- **THEN** the release page lists the document next to the jar

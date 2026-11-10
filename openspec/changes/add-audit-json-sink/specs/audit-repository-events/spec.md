# Spec Delta

## Purpose

A reviewer can filter the audit log for the creation and deletion of a whole repository, and sees which repository it was.

## ADDED Requirements

### Requirement: Repository created

When a save of the `maven` settings domain adds a repository id that was not present before, the server SHALL record an audit
event `REPOSITORY_CREATED` with `repository` set to the id, the actor of the request, and `details` holding `type` and
`visibility`.

#### Scenario: New repository

- **WHEN** an admin saves the maven domain with a new repository `internal`
- **THEN** an event `REPOSITORY_CREATED` with repository `internal` is recorded, and the `SETTINGS_CHANGED` event is recorded as before

### Requirement: Repository deleted

When a save of the `maven` settings domain removes a repository id that was present before, the server SHALL record
`REPOSITORY_DELETED` with `repository` set to the removed id and the details of the removed entry.

#### Scenario: Removed repository

- **WHEN** an admin saves the maven domain without the repository `snapshots`
- **THEN** an event `REPOSITORY_DELETED` with repository `snapshots` is recorded

#### Scenario: Rename

- **WHEN** an admin changes the id of repository `a` to `b`
- **THEN** one `REPOSITORY_DELETED` for `a` and one `REPOSITORY_CREATED` for `b` are recorded

### Requirement: Other edits are unchanged

A save that does not add or remove an id SHALL NOT record either event. `SETTINGS_CHANGED` SHALL be recorded exactly as before,
and the stored action names SHALL NOT be renamed.

#### Scenario: Visibility change

- **WHEN** an admin changes only the visibility of an existing repository
- **THEN** only `SETTINGS_CHANGED` is recorded

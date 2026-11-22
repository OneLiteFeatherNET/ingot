# Spec Delta

## Purpose

An operator chooses path-style S3 addressing per repository, and existing deployments that use the JVM property keep working.

## ADDED Requirements

### Requirement: Per-repository path style

The S3 storage settings SHALL accept `pathStyleAccess`. When it is set, the repository SHALL use path-style addressing exactly when it is
true. When it is unset, the repository SHALL use path-style addressing exactly when the JVM property
`reposilite.s3.pathStyleAccessEnabled` is `true`.

#### Scenario: Setting overrides property

- **WHEN** the property is `true` and a repository sets `pathStyleAccess` to false
- **THEN** that repository uses virtual-host addressing and the others keep path style

#### Scenario: Property as fallback

- **WHEN** a repository leaves `pathStyleAccess` unset and the property is `true`
- **THEN** the repository uses path-style addressing

#### Scenario: Setting without property

- **WHEN** the property is absent and a repository sets `pathStyleAccess` to true
- **THEN** the repository uses path-style addressing

### Requirement: Existing configuration is unchanged

A settings document without `pathStyleAccess` SHALL load as unset, and saving it SHALL NOT write a value the operator did not choose.

#### Scenario: Old file

- **WHEN** a configuration written before this change is loaded and saved again
- **THEN** it contains no `pathStyleAccess` value and the repository behaves as before

# Spec Delta

## Purpose

Every repository has a format. Existing repositories are Maven without any change to their settings, and the server has one
place where formats are defined.

## ADDED Requirements

### Requirement: Format defaults to maven2

A repository in the `maven` settings domain SHALL accept an optional `format` key. When the key is absent or null, the
repository SHALL be of format `maven2`. The registry and `Repository` SHALL expose the resolved format id.

#### Scenario: Existing configuration

- **WHEN** the server starts with a `configuration.shared.json` written by Reposilite 3.5.28 that has no `format` key
- **THEN** every repository starts, is reported with format `maven2`, and serves requests as before

#### Scenario: Explicit maven2

- **WHEN** a repository is configured with `"format": "maven2"`
- **THEN** it behaves exactly like a repository without the key

### Requirement: Absent format is not written back

When the settings of the `maven` domain are saved, a repository whose `format` was absent SHALL be stored without a
`format` key. A repository whose `format` was set SHALL keep the value it was given.

#### Scenario: Editing another field

- **WHEN** an operator changes the description of `releases`, which has no `format` key, through the settings API
- **THEN** the stored settings of `releases` contain the new description and no `format` key

#### Scenario: Explicit value kept

- **WHEN** an operator saves the domain and `snapshots` has `"format": "maven2"`
- **THEN** the stored settings of `snapshots` still contain `"format": "maven2"`

### Requirement: Unknown formats are refused

A settings update SHALL be refused when a repository names a format id that no installed format provides; the message SHALL
name the repository and the known ids. At start, a stored repository with an unknown format SHALL NOT be served, the failure
SHALL be recorded with the repository name, and the other repositories SHALL start.

#### Scenario: Update with an unknown format

- **WHEN** an operator saves a repository `files` with `"format": "raw"` on a server that only knows `maven2`
- **THEN** the update is refused with a message naming `files` and `maven2`, and the stored settings are unchanged

#### Scenario: Rollback from a later version

- **WHEN** the server starts with stored settings in which `files` has `"format": "raw"`
- **THEN** `files` answers like a repository that does not exist, a failure names `files`, and `releases` serves as before

### Requirement: Groups stay within one format

A repository of type `GROUP`, and an untyped repository that proxies local repositories, SHALL only reference local
repositories of its own format. A reference to a repository of another format SHALL be a settings violation naming both.

#### Scenario: Mixed group refused

- **WHEN** settings are validated with a test format `fake` installed, `fake-hosted` of format `fake` and a group `public` of
  format `maven2` that proxies `fake-hosted`
- **THEN** validation reports that `public` references `fake-hosted` of another format

### Requirement: Maven is the only format and keeps its rules

The server SHALL provide exactly one format, `maven2`, discovered through the format service interface. Its path rules
SHALL give the same answers as the code they replace for metadata files, checksum sidecars, resolution metadata, download
counting and index paths. Duplicate format ids SHALL fail the start with both implementing classes named.

#### Scenario: Rule equivalence

- **WHEN** the characterisation test runs the previous functions and the `maven2` path rules over the same table of paths,
  including `maven-metadata.xml.sha1`, a timestamped snapshot jar, `ivy-1.0.xml` and a file without extension
- **THEN** every answer is equal

#### Scenario: Metadata may be redeployed

- **WHEN** a client deploys `maven-metadata.xml` to a repository with redeployment disabled where the file already exists
- **THEN** the deploy is accepted, as before

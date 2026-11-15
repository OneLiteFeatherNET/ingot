# Spec Delta

## Purpose

Maven hosted content moves from Nexus into Ingot through the Nexus REST API, every file checked against what Nexus reports, with
nothing overwritten unless the operator asked for it.

## ADDED Requirements

### Requirement: Copy through the Maven facade

The plugin SHALL download each asset from its `downloadUrl` and write it through the Maven facade deploy, so storage, search and
events behave as for any deploy. It SHALL keep the Maven path. It SHALL copy `.md5`, `.sha1` and `maven-metadata.xml` assets
byte for byte and SHALL NOT generate checksums or regenerate metadata.

#### Scenario: Plain copy

- **WHEN** a hosted repository holds a jar, its pom and their sidecars
- **THEN** every file exists in the target with the same bytes and appears in search

#### Scenario: Metadata last

- **WHEN** a directory holds artifacts and `maven-metadata.xml`
- **THEN** the metadata file is written after the other files of that directory

### Requirement: Checksum verification while copying

The plugin SHALL compute md5, sha1 and sha256 of each download and compare md5 and sha1, and any further checksum Nexus reports, and
the size. On a mismatch the write SHALL fail, nothing SHALL remain at the path, and the result SHALL be `CHECKSUM_FAILED`. The
sha256 SHALL be stored in the result row.

#### Scenario: Corrupted download

- **WHEN** the body differs from the checksum in the listing
- **THEN** the target has no file at that path and the row is `CHECKSUM_FAILED`

#### Scenario: Truncated download

- **WHEN** the connection ends before the listed size
- **THEN** the file is retried, and after the last attempt recorded as failed with nothing stored

### Requirement: Conflict policy

A path that exists with a different checksum SHALL be a conflict. The job parameter `conflictPolicy` SHALL be `refuse` (default),
`abort` or `overwrite`. With `refuse` the existing file SHALL stay and the repository SHALL end `CONFLICTED`. With `abort` the first
conflict SHALL stop that repository. With `overwrite` the file SHALL be replaced and both steps audited.

#### Scenario: Default refuses

- **WHEN** no policy is given and a path differs
- **THEN** the file is unchanged, the conflict lists both checksums, and the job continues with other files

#### Scenario: Abort

- **WHEN** the policy is `abort` and the first conflict is found
- **THEN** the repository stops with `CONFLICTED` and the next repository starts

#### Scenario: Overwrite is explicit

- **WHEN** the policy is `overwrite`
- **THEN** the dry run shows the conflict count, the file is replaced, and delete and deploy events are recorded

### Requirement: Same content is skipped

A path that exists with the same checksum SHALL be skipped, recorded `SKIPPED_SAME`, and not written again.

#### Scenario: Second job

- **WHEN** a job runs over a repository that a finished job copied
- **THEN** no file is written and every row is `SKIPPED_SAME`

### Requirement: Vanished files do not fail the job

A path that Nexus lists and then answers `404` for SHALL be recorded `GONE` and SHALL NOT fail the job.

#### Scenario: Deleted while running

- **WHEN** an asset is removed in Nexus after it was listed
- **THEN** its row is `GONE` and the job continues

### Requirement: Verify pass

A job of kind `VERIFY` SHALL write nothing and SHALL compare, per repository, the asset count, the path sets, and the size and sha1
of each path against Nexus. The result SHALL be `VERIFIED` or `MISMATCH` with the first differing paths. A repository with a
conflict or a failed file SHALL NOT be `VERIFIED`.

#### Scenario: Missing file

- **WHEN** one path is absent from the target
- **THEN** the repository is `MISMATCH` and the path is named

#### Scenario: Clean

- **WHEN** all paths, sizes and sha1 values agree
- **THEN** the repository is `VERIFIED`

### Requirement: Audit events

With `INGOT_NEXUS_IMPORT_AUDIT` unset or `per-file`, each copied file SHALL produce an `ARTIFACT_DEPLOYED` event with actor
`migration`. With `summary`, no per-file event SHALL be recorded, and no token name SHALL be able to cause that. Every job SHALL end
with one `NEXUS_IMPORT_RUN` event with the counts.

#### Scenario: Per file

- **WHEN** three files are copied in the default mode
- **THEN** the audit log has three deploy events with actor `migration` and one summary event

#### Scenario: Summary only

- **WHEN** the mode is `summary`
- **THEN** the audit log has one summary event and no deploy event for the job

#### Scenario: Look-alike token

- **WHEN** a token named `migration-bulk` deploys a file during a summary-only job
- **THEN** its deploy event is recorded

### Requirement: Nothing is written to Nexus

The plugin SHALL send only `GET` and `HEAD` requests to Nexus.

#### Scenario: Request log of the fake

- **WHEN** any job kind runs against the fake Nexus
- **THEN** the fake recorded no request with another method

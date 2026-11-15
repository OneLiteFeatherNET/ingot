# Spec Delta

## Purpose

A repository can check that an uploaded checksum file matches the artifact it describes, and the default keeps today's behaviour.

## ADDED Requirements

### Requirement: Policy per repository and per instance

A repository SHALL accept `uploadChecksumPolicy` with the values `ignore`, `warn` and `reject`. When the key is absent the
repository SHALL use the instance default from `INGOT_UPLOAD_CHECKSUM_POLICY`, then `REPOSILITE_UPLOAD_CHECKSUM_POLICY`, then
the system property `ingot.uploadChecksumPolicy`, and when none is set the policy SHALL be `ignore`. An unknown value SHALL be
refused at startup or at settings validation with a message that names the key.

#### Scenario: Default keeps today's behaviour

- **WHEN** neither the key nor the variable is set and a mismatching `.sha1` file is deployed after its artifact
- **THEN** the file is stored, no refusal is given and no audit action is written

#### Scenario: Repository value wins over the instance default

- **WHEN** the variable is `reject` and a repository sets `uploadChecksumPolicy` to `ignore`
- **THEN** a mismatching checksum file deployed to that repository is stored

#### Scenario: Fallback spelling is read

- **WHEN** only `REPOSILITE_UPLOAD_CHECKSUM_POLICY=reject` is set
- **THEN** a repository without the key behaves as `reject`, and `INGOT_UPLOAD_CHECKSUM_POLICY` wins when both are set

#### Scenario: Unknown value is refused

- **WHEN** the key is set to `strict`
- **THEN** validation fails with a message that names `uploadChecksumPolicy`

### Requirement: Checksum files are checked against the stored artifact

When the policy is not `ignore`, a deployed file whose name ends in `.md5`, `.sha1`, `.sha256` or `.sha512` SHALL be compared
with the digest of the stored file of the same path without that suffix, computed with the algorithm of the suffix. The upload
SHALL be accepted as the bare digest or as `digest  name`, in any letter case.

#### Scenario: Matching checksum is stored

- **WHEN** an artifact is deployed and then its `.sha256` file with the correct digest
- **THEN** the checksum file is stored and served, under `warn` and under `reject`

#### Scenario: Both accepted forms match

- **WHEN** the checksum file holds the digest in upper case followed by two spaces and the file name
- **THEN** it is treated as matching

#### Scenario: Other files are not checked

- **WHEN** a `.jar`, a `.pom` or a `.asc` file is deployed
- **THEN** no digest is computed

### Requirement: Reject refuses a mismatch and stores nothing

Under `reject`, a checksum file whose digest differs from the stored artifact, or whose body is not a hex digest of the length
of the algorithm, or is longer than 1024 bytes, SHALL be answered with `400` and a message that names the algorithm. Nothing
SHALL be written for the checksum file and the stored artifact SHALL be unchanged. The check SHALL run after the redeployment
check, so that a refused redeployment is still answered with `409`.

#### Scenario: Mismatch answers 400 and leaves no file

- **WHEN** an artifact is deployed and then a `.sha1` file with another digest
- **THEN** the answer is `400`, the `.sha1` path does not exist, no temporary file remains, and the artifact is served unchanged

#### Scenario: Malformed body is refused

- **WHEN** the checksum file holds `not-a-digest`
- **THEN** the answer is `400` and nothing is stored

#### Scenario: Redeployment conflict still wins

- **WHEN** the checksum file already exists, redeployment is off and a mismatching file is deployed
- **THEN** the answer is `409`, as without the policy

### Requirement: Warn stores and records

Under `warn`, a mismatch SHALL be logged and audited with `outcome` `accepted`, and the checksum file SHALL be stored and the
request answered as today.

#### Scenario: Mismatch is accepted with a record

- **WHEN** the policy is `warn` and a mismatching checksum file is deployed
- **THEN** the file is stored, the answer is the usual success, and one `UPLOAD_CHECKSUM_MISMATCH` event with outcome `accepted` exists

### Requirement: Order of upload

Only the order artifact first, checksum second SHALL be checked. A checksum file whose artifact is not stored SHALL be
accepted without a check, under every policy.

#### Scenario: Checksum before artifact is accepted

- **WHEN** a `.sha1` file is deployed and its artifact does not exist yet
- **THEN** the file is stored, nothing is audited, and a later artifact upload is not checked against it

### Requirement: Audit event for a mismatch

A mismatch under `warn` or `reject` SHALL write one audit event with the action `UPLOAD_CHECKSUM_MISMATCH`, the repository,
the path of the checksum file, the actor of the request, and the details `algorithm` and `outcome` (`rejected` or `accepted`).
A matching upload SHALL write no such event.

#### Scenario: Rejection is audited

- **WHEN** a mismatching checksum file is refused under `reject`
- **THEN** one event with outcome `rejected` and the algorithm is recorded, and no `ARTIFACT_DEPLOYED` event exists for the checksum file

### Requirement: Storage independence

The check SHALL behave the same on filesystem and S3 storage, SHALL read the stored artifact as a stream without holding it in
memory, and SHALL NOT change the layout or content of any stored file.

#### Scenario: S3 repository behaves the same

- **WHEN** the repository uses S3 storage and a mismatching checksum file is deployed under `reject`
- **THEN** the answer is `400` and no object is written for the checksum file

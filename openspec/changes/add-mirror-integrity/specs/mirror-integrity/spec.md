# Spec Delta

## Purpose

A mirror with `store` enabled can check the bytes it keeps against the checksum that its upstream publishes, and the default
keeps today's behaviour.

## ADDED Requirements

### Requirement: Checksum policy per mirror

A mirror SHALL accept `checksumPolicy` with the values `ignore`, `warn` and `fail`. When the key is absent the policy SHALL be
`ignore`, so that settings written before this change load and behave as before. The policy SHALL apply only to artifact files
that a mirror with `store` enabled fetches. Metadata files and checksum files SHALL NOT be checked. A mirror with `store`
disabled SHALL refuse `warn` and `fail` at validation, with a message that names `checksumPolicy`.

#### Scenario: Default keeps today's behaviour

- **WHEN** a mirror has no `checksumPolicy` and an artifact is fetched through it
- **THEN** the artifact is stored and served as before, and no sibling checksum file is requested

#### Scenario: Warn logs a mismatch and keeps the copy

- **WHEN** the policy is `warn` and the fetched bytes do not match the upstream `.sha256` file
- **THEN** a warning is logged that names the artifact and the host, the copy is stored, and it is served

#### Scenario: Fail refuses a mismatch and tries the next host

- **WHEN** the policy is `fail`, the first host serves bytes that do not match its `.sha256` file, and the second host serves matching bytes
- **THEN** the copy from the second host is stored and served, and the copy from the first host is never stored

#### Scenario: Fail refuses when no host matches

- **WHEN** the policy is `fail` and every eligible host serves bytes that do not match
- **THEN** the response is `502 Bad Gateway` with a generic message, and the path is not present in storage

#### Scenario: Store disabled with a verifying policy

- **WHEN** a mirror has `store` disabled and `checksumPolicy` set to `fail`
- **THEN** the settings are refused at validation with a message that names `checksumPolicy`

### Requirement: Digest source of a verified artifact

Under `fail` the digest SHALL be read from the sibling `.sha256` file of the same host. When that file is missing, the sibling
`.sha1` file of the same host SHALL be used. When neither file exists, the host SHALL be refused.

#### Scenario: SHA-1 fallback

- **WHEN** the policy is `fail`, the host has no `.sha256` file and its `.sha1` file matches the bytes
- **THEN** the artifact is stored and served

#### Scenario: No checksum file

- **WHEN** the policy is `fail` and the host has neither a `.sha256` nor a `.sha1` file
- **THEN** the host is refused and the next host is tried

### Requirement: Verified commit under fail

Under `fail` a copy SHALL become visible in storage only after its digest matches. A copy that does not match SHALL NOT be
listed, served or left behind in the repository tree.

#### Scenario: Mismatch leaves nothing behind

- **WHEN** the policy is `fail`, the only host serves bytes that do not match, and the artifact is requested again
- **THEN** the second request is also answered from upstream, and the directory listing does not contain the artifact

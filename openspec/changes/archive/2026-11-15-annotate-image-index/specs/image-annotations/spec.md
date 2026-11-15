# Spec Delta

## Purpose

An operator who inspects a tag of either image can read what it is and what it is built on from the index and the platform
manifests, without changing how either image runs.

## ADDED Requirements

### Requirement: Index and manifest carry the image annotations

The release job SHALL set the OCI annotations `org.opencontainers.image.title`, `org.opencontainers.image.description`,
`org.opencontainers.image.vendor`, `org.opencontainers.image.licenses` and `org.opencontainers.image.version` on the image index and
on every platform manifest of both the server image and the dashboard image, with the same values as their labels.

#### Scenario: Server index inspected

- **WHEN** an operator inspects the raw index of a released server tag
- **THEN** the index and each platform manifest carry the five annotations with the values of the image labels

#### Scenario: Nightly index inspected

- **WHEN** an operator inspects the raw index of the nightly server tag
- **THEN** the `version` annotation reads `nightly`, the same value as the label

### Requirement: Base image is named by annotation

The release job MUST set `org.opencontainers.image.base.name` and `org.opencontainers.image.base.digest` on the image index and on
every platform manifest of each image. Both values MUST be read from the run stage `FROM` line of that image's Dockerfile at build
time. The base name MUST be a fully qualified reference without a digest, and the base digest MUST be the digest pinned on that line.

#### Scenario: Server base recorded

- **WHEN** an operator inspects the raw index of a released server tag
- **THEN** `base.name` names the image of the run stage of the server Dockerfile and `base.digest` equals the digest pinned on that
  `FROM` line

#### Scenario: Base digest updated in the Dockerfile

- **WHEN** the digest pinned on the run stage `FROM` line changes in a Dockerfile
- **THEN** the next build of that image carries the new digest in `base.digest` without any change to the workflow

### Requirement: Annotations do not change how the images run

Adding the annotations MUST NOT change any label value, tag, entrypoint, path, port, user, volume or environment variable of either
image, and MUST NOT change any file that an operator configures.

#### Scenario: Upstream compose file

- **WHEN** an unmodified upstream compose file is started with only the image line swapped to the new tag
- **THEN** the container starts with the same entrypoint, port and user as before the change

### Requirement: Release job checks the annotations

The release job SHALL run the check script on each pushed image reference after the push and MUST fail when an expected annotation
is missing or empty on the index.

#### Scenario: Annotation missing

- **WHEN** a pushed index lacks `base.digest`
- **THEN** the check script exits non-zero and names the missing key

#### Scenario: All annotations present

- **WHEN** a pushed index carries all seven annotations with non-empty values
- **THEN** the check script exits zero

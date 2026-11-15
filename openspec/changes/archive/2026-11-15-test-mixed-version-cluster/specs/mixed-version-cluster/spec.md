# Spec Delta

## Purpose

A release and the release after it serve side by side against one database during a rolling upgrade, and a pull request
fails when a change breaks that pairing.

## ADDED Requirements

### Requirement: A deploy on one release is readable on the other

With the previous release and the current build on one database and one repository volume, an artifact deployed through
either replica SHALL be returned unchanged by the other.

#### Scenario: Deploy on the old replica, read on the new one

- **WHEN** an artifact is deployed through the previous release
- **THEN** the current build answers 200 with the same bytes

#### Scenario: Deploy on the new replica, read on the old one

- **WHEN** an artifact is deployed through the current build
- **THEN** the previous release answers 200 with the same bytes

#### Scenario: Different artifacts at the same time

- **WHEN** two different artifacts are deployed at the same moment, one through each replica
- **THEN** both deploys succeed and both artifacts are readable through both replicas

### Requirement: The old replica restarts after the schema changed

A replica of the previous release that is restarted after the current build changed the schema SHALL start and serve requests.

#### Scenario: Restart of the old replica

- **WHEN** the previous release is stopped and started again while the current build runs
- **THEN** it answers an authenticated request on the shared database

### Requirement: Added columns are writable by the previous release

Every column that the current build adds to the database SHALL be nullable, have a default, or be generated. No column that
the previous release knows SHALL be removed or change its type.

#### Scenario: A new column without a default

- **WHEN** the columns of the database are listed after the previous release started and again after the current build started
- **THEN** a column that exists only in the second list as `NOT NULL` without a default fails the test, naming the table and
  the column

#### Scenario: A removed or retyped column

- **WHEN** a column of the first list is missing from the second or has another type
- **THEN** the test fails, naming the table and the column

### Requirement: Unknown bus messages are ignored

A replica SHALL ignore a bus message of an unknown envelope version and a message of an unknown event type, SHALL count it as
ignored, SHALL record no failure, and SHALL keep applying known events.

#### Scenario: Newer envelope version

- **WHEN** a message with `"v":2` reaches a current replica
- **THEN** it is counted as ignored, no failure is recorded, and a known event sent afterwards is applied

#### Scenario: Unknown event type in version 1

- **WHEN** a message of version 1 with an unknown type reaches a current replica
- **THEN** it is counted as ignored and the replica keeps serving

### Requirement: The wire format of envelope version 1 stays fixed

The envelope codec SHALL decode a fixed version 1 message of every event type, and SHALL decode a payload with an extra field.

#### Scenario: Golden message

- **WHEN** a stored version 1 string of an event type is decoded
- **THEN** the type, the origin and the payload fields equal the stored values

#### Scenario: Extra payload field

- **WHEN** a version 1 message carries a payload field the codec does not list
- **THEN** it is accepted

### Requirement: The previous image follows the latest release

The container job SHALL start the highest released version as the previous image, unless a developer overrides the image.

#### Scenario: A new release

- **WHEN** a release tag newer than the current one exists in the repository
- **THEN** the next container job runs that release as the previous image

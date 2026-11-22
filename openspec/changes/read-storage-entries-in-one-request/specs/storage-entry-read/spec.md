# Spec Delta

## Purpose

A storage read that returns content, size and modification time together, so a download needs one request to the object store.

## ADDED Requirements

### Requirement: Combined read with a default

The storage provider API SHALL offer `readEntry(location)` that returns the content, the size and the modification time of a
file. A provider that does not override it SHALL behave as the sequence of `getFileDetails` and `getFile`. A plugin compiled
against the older interface SHALL load and SHALL serve reads through the default.

#### Scenario: A provider without an override

- **WHEN** `readEntry` is called on a provider that only implements the older methods
- **THEN** it returns the same content, size and modification time that `getFileDetails` and `getFile` return

#### Scenario: A missing file

- **WHEN** `readEntry` is called for a location that does not exist
- **THEN** it returns the same error as `getFile` for that location

### Requirement: One request on S3

The S3 provider SHALL answer `readEntry` with exactly one request to the object store.

#### Scenario: A file is read

- **WHEN** a Maven client downloads a file from an S3 repository
- **THEN** the object store receives one GET and no HEAD for that file

### Requirement: Responses unchanged

The Maven file endpoint SHALL return the same status code, content, length, last modified and entity tag headers as before.

#### Scenario: A download on S3

- **WHEN** a file is downloaded
- **THEN** the response is equal in body and headers to the response before the change

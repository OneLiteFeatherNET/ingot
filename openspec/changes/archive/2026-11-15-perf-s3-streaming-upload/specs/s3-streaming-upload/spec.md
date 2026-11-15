# Spec Delta

## Purpose

Uploads to an S3 bucket reach it as they arrive, without a copy in the temporary directory, and a failed upload leaves nothing
behind that Ingot can abort.

## ADDED Requirements

### Requirement: Streamed upload without a temporary file

The S3 storage provider SHALL send an uploaded stream without writing it to a local temporary file. A stream that ends within
the first 8 MiB SHALL be sent in one put request with its exact length. A longer stream SHALL be sent as a multipart upload
with parts of 8 MiB, except that the last part MAY be smaller.

#### Scenario: Small upload

- **WHEN** a stream of 1 KiB is uploaded to an S3 repository
- **THEN** one put request carries the 1 KiB and its exact length, and no local temporary file is created

#### Scenario: Large upload

- **WHEN** a stream of 20 MiB of unknown length is uploaded to an S3 repository
- **THEN** a multipart upload is created, parts of 8 MiB and a final part of 4 MiB are sent in order, and the upload is completed

#### Scenario: Stream of exactly one part

- **WHEN** a stream of exactly 8 MiB is uploaded
- **THEN** the upload ends with one put request of 8 MiB, not a multipart upload with a single part

### Requirement: Content type and key are unchanged

The S3 storage provider MUST store a streamed upload under the same key and with the same content type as a single put of the
same location.

#### Scenario: Multipart upload keeps the content type

- **WHEN** a 20 MiB file named `example-1.0.jar` is uploaded
- **THEN** the multipart upload is created with the content type `application/java-archive`

### Requirement: Failed upload is aborted

The S3 storage provider MUST abort the multipart upload when a part or its completion fails, and it MUST NOT leave an
incomplete multipart upload created by its own request. The client MUST receive the status and the generic message of a
failed write, with no part of the exception text.

#### Scenario: Part fails

- **WHEN** the third part of a multipart upload is rejected by S3
- **THEN** the multipart upload is aborted, and the response carries the status of a failed write and a generic message

#### Scenario: Abort fails

- **WHEN** a part fails and the abort request also fails
- **THEN** the failure of the abort is logged with its exception, and the response is still the generic failed write

### Requirement: Cached metadata is invalidated on completion

The S3 client wrapper that caches HEAD answers MUST invalidate the cached answer for a key when a multipart upload to that key
completes, and it MUST apply the same invalidation of the `maven-metadata.xml` entry as it does after a single put.

#### Scenario: Cached answer replaced by a multipart upload

- **WHEN** a HEAD answer for `com/example/a/1.0/a-1.0.jar` is cached, and a multipart upload to that key completes
- **THEN** the next HEAD request for that key goes to S3 and does not return the cached answer

### Requirement: Escape hatch restores the temporary file path

The server SHALL read `INGOT_S3_BUFFER_TO_DISK` (with `REPOSILITE_S3_BUFFER_TO_DISK` as the fallback) when the S3 storage
provider is created. When the value is `true`, every upload SHALL be written to a local temporary file before it is sent, as
before this change. The default SHALL be `false`. A value that is neither `true` nor `false` SHALL stop the start with the name
of the variable.

#### Scenario: Setting enabled

- **WHEN** `INGOT_S3_BUFFER_TO_DISK=true` is set and a 20 MiB file is uploaded
- **THEN** the upload is sent from a local temporary file in one put request, as before

#### Scenario: Fallback spelling

- **WHEN** only `REPOSILITE_S3_BUFFER_TO_DISK=true` is set
- **THEN** the setting takes effect

#### Scenario: Invalid value

- **WHEN** `INGOT_S3_BUFFER_TO_DISK=yes` is set
- **THEN** the start stops with an error that names `INGOT_S3_BUFFER_TO_DISK`

# Spec Delta

## Purpose

Artifact downloads can be resumed and revalidated. Clients that send none of the related headers see the same answer as before,
plus two headers.

## ADDED Requirements

### Requirement: Validators and range advertisement

An artifact `GET` or `HEAD` answer SHALL carry `Accept-Ranges: bytes` and a strong `ETag`. The `ETag` SHALL come from the stored
checksum of the artifact when one exists, and from its size and last-modified time otherwise. `Last-Modified` and `Cache-Control`
SHALL keep their present values, and the caching rules for snapshots and `maven-metadata.xml` SHALL NOT change.

#### Scenario: Plain download

- **WHEN** a client requests an artifact with no conditional or range header
- **THEN** the answer is `200` with the full body, the same `Content-Length`, `Last-Modified` and `Cache-Control` as before, `Accept-Ranges: bytes` and an `ETag`

#### Scenario: Tag follows the checksum

- **WHEN** an artifact has a stored `.sha256` file and the artifact content changes so that the checksum changes
- **THEN** the `ETag` changes

### Requirement: Single range requests

The server SHALL answer a single satisfiable `Range` of unit `bytes` with `206`, a `Content-Range` header and only the requested
bytes. It SHALL answer an unsatisfiable range with `416` and `Content-Range: bytes */<size>`. It SHALL ignore a header with several
ranges, an unknown unit or a malformed value, and answer the full `200`.

#### Scenario: Resume a download

- **WHEN** a client requests `Range: bytes=100-` of a 1000 byte artifact
- **THEN** the answer is `206`, `Content-Range: bytes 100-999/1000`, `Content-Length` `900` and the last 900 bytes

#### Scenario: Suffix range

- **WHEN** a client requests `Range: bytes=-10` of a 1000 byte artifact
- **THEN** the answer is `206` with `Content-Range: bytes 990-999/1000` and the last 10 bytes

#### Scenario: Range beyond the end

- **WHEN** a client requests `Range: bytes=2000-` of a 1000 byte artifact
- **THEN** the answer is `416` with `Content-Range: bytes */1000`

#### Scenario: Several ranges

- **WHEN** a client requests `Range: bytes=0-9,20-29`
- **THEN** the answer is the full `200` body

#### Scenario: HEAD with a range

- **WHEN** a client sends `HEAD` with `Range: bytes=0-9`
- **THEN** the answer has the status and headers of the matching `GET` and no body

### Requirement: Conditional requests

The server SHALL answer `304` with no body, and with `ETag`, `Last-Modified` and `Cache-Control`, when `If-None-Match` matches the
current `ETag`, or when `If-Modified-Since` is not earlier than `Last-Modified` and no `If-None-Match` is sent. The server SHALL honour
`If-Range`: a matching `ETag` or date keeps the range, any other value serves the full `200`.

#### Scenario: Revalidation by tag

- **WHEN** a client sends `If-None-Match` with the artifact's current `ETag`
- **THEN** the answer is `304` with no body

#### Scenario: Revalidation by date

- **WHEN** a client sends `If-Modified-Since` with the artifact's `Last-Modified`
- **THEN** the answer is `304` with no body

#### Scenario: Changed artifact

- **WHEN** a client sends an `If-None-Match` that does not match
- **THEN** the answer is `200` with the full body

#### Scenario: If-Range does not match

- **WHEN** a client sends `Range: bytes=100-` and an `If-Range` tag that no longer matches
- **THEN** the answer is `200` with the full body

### Requirement: Both storage back ends

Range and conditional requests SHALL work for filesystem and S3 storage. A ranged request SHALL NOT read the bytes before the range
from the back end where the back end can skip them.

#### Scenario: S3 ranged read

- **WHEN** a client requests a byte range of an artifact stored in S3
- **THEN** the answer is `206` with the requested bytes, and the storage request carries a `Range` header

#### Scenario: Filesystem ranged read

- **WHEN** a client requests a byte range of an artifact stored on the filesystem
- **THEN** the answer is `206` with the requested bytes

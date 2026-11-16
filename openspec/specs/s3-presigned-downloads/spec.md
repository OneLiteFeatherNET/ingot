# s3-presigned-downloads Specification

## Purpose
An operator can let the bucket serve the bytes of cacheable release files, so that the instance does not stream them. The
switch is off by default, and every other answer stays with the instance.

## Requirements

### Requirement: The redirect switch is read at start

The server SHALL read `INGOT_S3_PRESIGNED_REDIRECTS` once at start, and SHALL read `REPOSILITE_S3_PRESIGNED_REDIRECTS` when the
first name is not set. The values `true` and `false`, in any case, are accepted, and an empty or unset value means `false`. Any
other value SHALL stop the start with a message that names the variable.

#### Scenario: Default

- **WHEN** the server starts with neither switch variable set
- **THEN** no download is redirected

#### Scenario: Unknown switch value

- **WHEN** the server starts with `INGOT_S3_PRESIGNED_REDIRECTS=yes`
- **THEN** the start stops and the message names `INGOT_S3_PRESIGNED_REDIRECTS`

### Requirement: The presigned URL lifetime is bounded

The server SHALL read `INGOT_S3_PRESIGN_TTL_SECONDS` (fallback `REPOSILITE_S3_PRESIGN_TTL_SECONDS`) with the default 300. A value
that is not a whole number from 60 to 3600 SHALL stop the start with a message that names the variable.

#### Scenario: TTL out of range

- **WHEN** the server starts with `INGOT_S3_PRESIGN_TTL_SECONDS=30`
- **THEN** the start stops and the message names `INGOT_S3_PRESIGN_TTL_SECONDS`

### Requirement: A cacheable public download is redirected to a presigned URL

When the switch is on, the server SHALL answer a `GET` of a cacheable file with public scope that is stored in an S3 repository,
and that carries no `Range`, `If-None-Match`, `If-Modified-Since` or `If-Range` header, with `302 Found` and a `Location` that is
a presigned `GET` URL for the object, valid for the configured TTL.

#### Scenario: Download of a public cacheable release

- **WHEN** a client requests a cacheable release file of a PUBLIC S3 repository with the switch on
- **THEN** the response is `302` with a `Location` that names the bucket and the key

### Requirement: The redirect is issued after the read check

The server SHALL produce a redirect only after the read check of the request has passed, so that a presigned URL is given only
to a caller who could read the file.

#### Scenario: Anonymous request for a private file

- **WHEN** an anonymous client requests a file from a PRIVATE S3 repository while the switch is on
- **THEN** the response is `401` and carries no `Location` header

### Requirement: Private files are never redirected

The server SHALL NOT redirect a file whose scope is private, even for an authorized caller. A file of a private repository, and a
file that a repository serves through a mirror with upstream credentials, SHALL be served by the instance, whatever the switch
and `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` say. A presigned URL is a bearer link for its lifetime, so it is never given for such a
file.

#### Scenario: Authorized request for a private file

- **WHEN** an authorized client requests a cacheable file from a PRIVATE S3 repository while the switch is on
- **THEN** the response is `200` served by the instance, and carries no `Location` header

#### Scenario: Credentialed mirror with the escape hatch

- **WHEN** the switch is on, `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true`, and a client requests a cacheable file through a mirror
  that has upstream credentials
- **THEN** the response is `200` served by the instance, and carries no `Location` header

### Requirement: A redirect counts as a served copy

A redirect SHALL move the usage record of the proxy cache copy that it points to, as a streamed answer does, so that a copy that is
served only through redirects is not evicted as unused. A `HEAD` request SHALL NOT move that record.

#### Scenario: Redirect keeps the mirror copy in use

- **WHEN** a public cacheable file is requested through a mirror and answered with a redirect
- **THEN** the usage record of that mirror copy is moved forward

#### Scenario: HEAD leaves the usage record alone

- **WHEN** a client sends `HEAD` for a public cacheable file of a mirror
- **THEN** the usage record of that mirror copy is not moved

### Requirement: The redirect expires before its URL does

The `Cache-Control` of a redirect SHALL carry `public`, the only scope that is redirected, and its `max-age` SHALL be the smaller
of `maxAge` and half the TTL, so that a shared cache drops the redirect before the presigned URL expires.

#### Scenario: Redirect lifetime

- **WHEN** the switch is on, the TTL is 300 seconds and `maxAge` is 3600 seconds
- **THEN** the redirect carries `Cache-Control` with `max-age=150`

### Requirement: The presign endpoint can differ from the storage endpoint

When `INGOT_S3_PRESIGN_ENDPOINT` (fallback `REPOSILITE_S3_PRESIGN_ENDPOINT`) is set, the presigned URL SHALL use that endpoint.
When it is not set, the configured S3 endpoint SHALL be used.

#### Scenario: Presigned endpoint

- **WHEN** the switch is on and `INGOT_S3_PRESIGN_ENDPOINT` names a public address
- **THEN** the `Location` uses that address as its host

### Requirement: Every other answer stays with the instance

The server SHALL serve through the instance, as before, any `HEAD` request, any `GET` with a range or conditional header, any
file that is not cacheable, any file with private scope, any metadata file, and any file of a repository that is not stored in S3,
whatever the switch says.

#### Scenario: Range request

- **WHEN** a client requests a range of a cacheable release file while the switch is on
- **THEN** the response is `206` with the bytes served by the instance, and no `Location` header

#### Scenario: Metadata

- **WHEN** a client requests `maven-metadata.xml` from an S3 repository while the switch is on
- **THEN** the response is `200` served by the instance, and `Cache-Control` is `no-cache, no-store, max-age=0`

#### Scenario: Anonymous request for a private file

- **WHEN** an anonymous client requests a file from a PRIVATE S3 repository while the switch is on
- **THEN** the response is `401` and carries no `Location` header

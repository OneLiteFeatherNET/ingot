# Spec Delta

## Purpose

A filesystem upload is either complete or absent for every reader, on one replica or on several sharing a volume. A crash
leaves no partial file behind for long, and a client that never uploads partial names sees no change.

## ADDED Requirements

### Requirement: Uploads are written beside their target

The filesystem storage provider SHALL write the body of an upload to a temporary file in the same directory as the target
file, named `.ingot-<random>.part`. The provider SHALL NOT write upload bodies to the system temporary directory.

#### Scenario: Upload in progress

- **WHEN** a client has sent part of the body of `PUT /releases/com/example/a/1.0/a-1.0.jar` and the body is not complete
- **THEN** a file named `.ingot-*.part` exists in `releases/com/example/a/1.0/` and no file exists at `a-1.0.jar` yet

#### Scenario: System temporary directory is not used

- **WHEN** an upload is stored
- **THEN** no temporary file of the upload is created under the system temporary directory

### Requirement: Commit by atomic move

The provider SHALL commit an upload by one atomic move of the temporary file onto the target, replacing the previous file.
The provider MAY fall back to a move that replaces the target without the atomic option only when the filesystem reports
that atomic moves are not supported, and SHALL log a warning when it does.

#### Scenario: Overwrite keeps the previous content until the commit

- **WHEN** a client overwrites `a-1.0.jar` and the new body is still being received
- **THEN** a reader of `a-1.0.jar` receives the previous bytes unchanged

#### Scenario: Failed upload leaves the previous file

- **WHEN** receiving the body of an upload fails part way, or the body exceeds the available quota
- **THEN** the target keeps its previous content or stays absent, and no `.ingot-*.part` file remains in its directory

#### Scenario: Atomic move unsupported

- **WHEN** the filesystem rejects an atomic move during a commit
- **THEN** the provider replaces the target with a plain move, logs one warning, and the upload succeeds

### Requirement: In-progress files are hidden

The provider SHALL leave files named `.ingot-*.part` out of directory listings, directory information, search entries,
storage usage figures and direct reads. A request for such a path SHALL answer `404`. An upload to such a name SHALL
answer `400`.

#### Scenario: Directory listing

- **WHEN** a client requests `GET /releases/com/example/a/1.0/` while an upload of `a-1.0.jar` is in progress
- **THEN** the response lists `a-1.0.jar` only when it is complete, and never lists the `.ingot-*.part` file

#### Scenario: Direct read of a temporary name

- **WHEN** a client requests `GET /releases/com/example/a/1.0/.ingot-x.part`
- **THEN** the response is `404`

#### Scenario: Reserved name on upload

- **WHEN** a client sends `PUT /releases/com/example/a/1.0/.ingot-x.part`
- **THEN** the response is `400` and no file is written

#### Scenario: Usage excludes the in-progress bytes

- **WHEN** the storage usage is measured while an upload is in progress
- **THEN** the bytes of the `.ingot-*.part` file are not counted

### Requirement: Stale partial files are removed at start

When the provider starts, it SHALL delete each `.ingot-*.part` file whose last modification time is more than 24 hours
before the current time of the injected clock. It SHALL keep newer partial files and SHALL NOT delete any other file.

#### Scenario: Crash leftover

- **WHEN** the provider starts and a `.ingot-*.part` file was last modified 25 hours earlier
- **THEN** the file is deleted

#### Scenario: Upload by another replica

- **WHEN** the provider starts and a `.ingot-*.part` file was last modified one hour earlier
- **THEN** the file is kept

#### Scenario: Other files

- **WHEN** the provider starts and a file named `a-1.0.jar.part` or `.ingot-x.jar` older than 24 hours exists
- **THEN** the file is kept

### Requirement: Client-visible behaviour of uploads and downloads is unchanged

The status codes and response bodies of uploads and downloads SHALL NOT change, except for the `400` of the reserved name.
A stored artifact SHALL be byte-identical to the uploaded body.

#### Scenario: Maven deploy

- **WHEN** a Maven client deploys a jar and its checksums to a release repository
- **THEN** each response has the same status and body as before, and the stored jar has the same SHA-1 as the uploaded jar

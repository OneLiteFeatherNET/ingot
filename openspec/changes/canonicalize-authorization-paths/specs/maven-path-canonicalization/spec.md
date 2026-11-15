# Spec Delta

## Purpose

One canonical form of a repository path for every authorization decision on writes, always enforced. The path that is judged is the path that is stored.

## ADDED Requirements

### Requirement: Writes are judged by the canonical path

Ingot SHALL judge uploads and deletes by the canonical form of the request path, and SHALL hand the same repository and location to the storage operation. There SHALL be no setting that turns this off.

#### Scenario: An escaped spelling is judged as the location it is stored at

- **WHEN** an upload to `/releases/com/ex%61mple/file.jar` arrives
- **THEN** the decision is the one for `/releases/com/example/file.jar`, and an accepted file is stored at `com/example/file.jar`

#### Scenario: A plain path is judged canonically

- **WHEN** an upload to `/releases/com//example/file.jar` arrives
- **THEN** the decision is the one for `/releases/com/example/file.jar`

#### Scenario: A path without escapes is unchanged

- **WHEN** an upload to a path without escapes, dot segments or doubled slashes arrives
- **THEN** the decision and the stored location are the ones Ingot used before this change

#### Scenario: A plus sign stays a plus sign

- **WHEN** an upload to `/releases/com/example/lib/1.0+build/lib-1.0+build.jar` arrives
- **THEN** the file is judged and stored with the `+` kept

### Requirement: Malformed paths are refused before authorization

Ingot SHALL refuse a write path with a NUL or control character, a malformed escape, a `.` or `..` segment, a backslash, or an encoded slash with 400, before any authorization runs.

#### Scenario: A traversal sequence is refused before authorization

- **WHEN** an upload to `/releases/%2e%2e/other/file.jar` arrives
- **THEN** the answer is 400 and the token's rights are not consulted

#### Scenario: An encoded slash is refused

- **WHEN** an upload to `/releases/com%2Fexample/file.jar` arrives
- **THEN** the answer is 400

### Requirement: Legacy route rules see the canonical string

The legacy route rule (case-insensitive prefix, no segment boundary) SHALL receive the canonical path of a write.

#### Scenario: A legacy route judges the canonical string

- **WHEN** a legacy route `/releases/com/example` exists and an upload to `/releases/com/ex%61mple/file.jar` arrives
- **THEN** the route is matched against `/releases/com/example/file.jar`, never against the raw path

### Requirement: Reads keep the normalized location

Reads and metadata reads SHALL keep judging the decoded, normalized location, as they do today.

#### Scenario: A read is unchanged

- **WHEN** a read of a normal artifact arrives
- **THEN** the decision is the one Ingot made before this change

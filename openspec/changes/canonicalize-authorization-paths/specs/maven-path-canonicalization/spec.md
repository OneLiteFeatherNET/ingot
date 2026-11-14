# Spec Delta

## Purpose

One canonical form of a repository path for every authorization decision on writes, in three modes. The default keeps today's decisions and reports the differences, so existing installations see no change.

## ADDED Requirements

### Requirement: The mode selects the path that authorization judges

Ingot SHALL read the mode from `INGOT_AUTHORIZATION_PATHS` with the values `off`, `warn` and `enforce`, falling back to `REPOSILITE_AUTHORIZATION_PATHS` when the first is unset. The default SHALL be `warn`. Upload, delete and metadata writes SHALL be judged by the raw path in `off` and `warn`, and by the canonical path in `enforce`.

#### Scenario: The default keeps the raw decision

- **WHEN** no switch is set and an upload to a path with an encoded character arrives
- **THEN** the decision is the one Ingot made before this change

#### Scenario: The INGOT name wins over the legacy name

- **WHEN** `INGOT_AUTHORIZATION_PATHS=enforce` and `REPOSILITE_AUTHORIZATION_PATHS=off` are both set
- **THEN** the mode is `enforce`

### Requirement: Warn mode reports differences and changes no response

In `warn`, Ingot SHALL compute the canonical decision alongside the raw one. When the two differ, Ingot SHALL log the request at WARN level and increment a counter. The response SHALL be the raw decision.

#### Scenario: A differing upload is logged and answered as before

- **WHEN** an upload to `/releases/com%2Fexample/...` is allowed on the raw path and refused on the canonical path
- **THEN** the upload is accepted, one WARN line names both decisions, and the counter increases by one

#### Scenario: A matching request is not logged

- **WHEN** the raw and canonical decisions agree
- **THEN** nothing is logged and the counter does not change

### Requirement: Enforce mode judges the canonical path and refuses malformed paths first

In `enforce`, Ingot SHALL refuse a path with a NUL or control character, a malformed escape, a `.` or `..` segment, or an encoded slash inside a segment with 400, before any authorization runs. Any other path SHALL be judged by its canonical form.

#### Scenario: A traversal sequence is refused before authorization

- **WHEN** an upload to `/releases/%2e%2e/other/file.jar` arrives in `enforce`
- **THEN** the answer is 400 and the token's rights are not consulted

#### Scenario: A plain path is judged canonically

- **WHEN** an upload to `/releases/com//example/file.jar` arrives in `enforce`
- **THEN** the decision is the one for `/releases/com/example/file.jar`

### Requirement: Legacy route rules see the string the decision uses

The legacy route rule (case-insensitive prefix, no segment boundary) SHALL receive the same string as the decision: the raw path in `off` and `warn`, and the canonical path in `enforce`.

#### Scenario: A legacy route judges the canonical string in enforce

- **WHEN** a legacy route `/rel` exists and `enforce` is set
- **THEN** the route is matched against the canonical path, never against the raw one

### Requirement: Reads keep the normalized location in every mode

Reads and metadata reads SHALL keep judging the normalized location in every mode, as they do today.

#### Scenario: A read is unchanged by the switch

- **WHEN** the mode is `off`, `warn` or `enforce` and a read of a normal artifact arrives
- **THEN** the decision is the same in all three modes

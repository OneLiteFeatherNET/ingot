# Spec Delta

## Purpose

The phrase statistics endpoint returns as many entries as its `{limit}` asks for, refuses limits it cannot serve, and shows
a token only the entries under paths it can read.

## ADDED Requirements

### Requirement: The phrase lookup honours its limit

The endpoint `GET /api/statistics/resolved/phrase/{limit}/{repository}/{gav}` SHALL return at most `{limit}` entries, ordered
by resolved count from highest to lowest. The `sum` SHALL equal the sum of the entries returned.

#### Scenario: Several entries requested

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/2/releases/com/example` and five entries match the phrase
- **THEN** the response holds the two entries with the highest counts and `sum` is the sum of those two

#### Scenario: One entry requested

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/1/releases/com/example`
- **THEN** the response holds the single entry with the highest count, as before this change

### Requirement: Invalid limits are refused

The endpoint MUST answer `400` with an `ErrorResponse` when `{limit}` is not an integer or is outside `1` to `100`, and MUST
NOT run the lookup in that case.

#### Scenario: Zero

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/0/releases/com/example`
- **THEN** the response status is `400` and the body names the expected range

#### Scenario: Above the maximum

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/101/releases/com/example`
- **THEN** the response status is `400`

#### Scenario: Not a number

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/many/releases/com/example`
- **THEN** the response status is `400`

#### Scenario: Maximum accepted

- **WHEN** a client requests `GET /api/statistics/resolved/phrase/100/releases/com/example` with a token that may read the path
- **THEN** the response status is `200`

### Requirement: Results are limited to what the caller can read

For a token without the manager area, the lookup SHALL return only entries whose path the token may read, and the limit SHALL
apply after that filter. A manager SHALL see every matching entry. An unauthenticated request SHALL keep answering `401`, and a
token without read access to the phrase path SHALL keep answering `403`.

#### Scenario: Partial read access

- **WHEN** a token may read `/releases/com/example` only, and the entries `com/example/a` (count 5) and `x/com/example/b`
  (count 9) exist in `releases`, and the client requests `GET /api/statistics/resolved/phrase/10/releases/com/example`
- **THEN** the response holds only `com/example/a` and `sum` is 5

#### Scenario: Limit applies after the filter

- **WHEN** a token may read `/releases/com/example` only, the entry `x/com/example/b` (count 9) is the highest, and the client
  requests `GET /api/statistics/resolved/phrase/1/releases/com/example`
- **THEN** the response holds the readable entry `com/example/a`, not an empty list

#### Scenario: Manager sees everything

- **WHEN** a token with the manager area requests `GET /api/statistics/resolved/phrase/10/releases/com/example`
- **THEN** the response holds both entries of the first scenario

#### Scenario: No read access to the phrase path

- **WHEN** a token may not read `/releases/com/example` and requests `GET /api/statistics/resolved/phrase/10/releases/com/example`
- **THEN** the response status is `403`

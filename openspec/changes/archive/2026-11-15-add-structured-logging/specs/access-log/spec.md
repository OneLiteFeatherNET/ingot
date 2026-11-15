# Spec Delta

## Purpose

An operator who wants one line per request gets it from the server itself, with the client address that the trusted proxy
list resolves, and without credentials in it.

## ADDED Requirements

### Requirement: Access record is opt-in and independent of the cache bypass

The server SHALL write one INFO record per request when `INGOT_ACCESS_LOG=true` (or `REPOSILITE_ACCESS_LOG=true` when unset).
The record SHALL be written whether or not the external cache bypass is enabled. When the variable is unset or any other
value, the server SHALL NOT write the record, and the existing debug line SHALL keep its current behaviour.

#### Scenario: Default

- **WHEN** the server handles a request with no access log variable set
- **THEN** no access record is written

#### Scenario: Enabled without cache bypass

- **WHEN** the server starts with `INGOT_ACCESS_LOG=true` and the cache bypass is off, and a client requests `GET /api/status/health`
- **THEN** exactly one INFO access record is written for that request

### Requirement: Access record fields

The access record SHALL contain the method, the request path without the query string, the response status, the duration in
milliseconds, the response body size in bytes, the client address resolved by the trusted proxy resolver, and the user name or
`-` when the request is anonymous.

#### Scenario: Forwarded client

- **WHEN** `INGOT_TRUSTED_PROXIES` lists the peer and the request arrives with `X-Forwarded-For: 198.51.100.7`
- **THEN** the record names `198.51.100.7` as the client address

#### Scenario: Anonymous request

- **WHEN** a request without credentials gets a `401`
- **THEN** the record carries status `401` and the user `-`

### Requirement: Access record holds no credentials

The access record SHALL NOT contain the query string, the `Authorization` header, cookie values, tokens or passwords.

#### Scenario: Query string with a token

- **WHEN** a client requests `GET /api/some-path?token=secret-value`
- **THEN** the record contains the path without the query and does not contain `secret-value`

#### Scenario: Bearer token

- **WHEN** a client sends `Authorization: Bearer ingot_example_secret`
- **THEN** the record does not contain `ingot_example_secret`


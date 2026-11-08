# Spec Delta

## Purpose

Small JSON endpoints do not read large bodies, and the session cookie is marked `Secure` whenever the client used HTTPS.

## ADDED Requirements

### Requirement: Body cap on small JSON endpoints

The login, setup, refresh, token and principal JSON endpoints SHALL refuse a request body above 64 KiB with `413` in the
`ErrorResponse` shape, before the body is read. A declared `Content-Length` above the cap SHALL be refused without reading,
and a chunked body SHALL be read through a stream bounded at the cap. Artifact uploads and the global request limit SHALL
NOT change.

#### Scenario: Declared length too large

- **WHEN** a login request declares a `Content-Length` of 65537
- **THEN** the response is `413` with a `message` and a `status` field, and the body was not read

#### Scenario: Chunked body too large

- **WHEN** a login request sends a chunked body of more than 64 KiB
- **THEN** the response is `413`

#### Scenario: Normal login

- **WHEN** a login request has a body of 200 bytes
- **THEN** it is handled as before

#### Scenario: Artifact upload

- **WHEN** an artifact of 10 MiB is deployed with `PUT`
- **THEN** the upload succeeds as before

### Requirement: Secure cookie behind a TLS proxy

The session cookie SHALL carry `Secure` when the request arrived over HTTPS or carries `X-Forwarded-Proto: https`. When a
trusted proxy list is set, the forwarded scheme SHALL count only from a trusted peer.

#### Scenario: Forwarded HTTPS

- **WHEN** a login succeeds on a request with `X-Forwarded-Proto: https`
- **THEN** the session cookie carries `Secure`

#### Scenario: Forwarded HTTPS from an untrusted peer

- **WHEN** a trusted proxy list is set and a login arrives from an untrusted peer with `X-Forwarded-Proto: https` over plain HTTP
- **THEN** the session cookie does not carry `Secure`

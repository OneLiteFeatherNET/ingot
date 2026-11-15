# Spec Delta

## Purpose

Refusals name how to authenticate and which methods are allowed, as RFC 9110 requires, without popups in browsers.

## ADDED Requirements

### Requirement: Challenge on 401

A `401` under `/api` SHALL carry `WWW-Authenticate: Bearer realm="Ingot"`. A `401` on a repository path SHALL keep
`WWW-Authenticate: Basic realm="Ingot", charset="UTF-8"`.

#### Scenario: Unauthenticated API call

- **WHEN** a client calls a protected endpoint under `/api` without credentials
- **THEN** the response is `401` with `WWW-Authenticate: Bearer realm="Ingot"` and no `Basic` challenge

#### Scenario: Unauthenticated repository call

- **WHEN** a client requests a private artifact without credentials
- **THEN** the response is `401` with the `Basic` challenge as before

### Requirement: Allow on 405

A `405` for a deploy or delete on a proxy or group repository SHALL carry `Allow: GET, HEAD`.

#### Scenario: Upload to a proxy repository

- **WHEN** a client sends `PUT` to a file path of a proxy repository
- **THEN** the response is `405` with `Allow: GET, HEAD`

### Requirement: Exact CSRF exemption

The CSRF check SHALL exempt only the login and setup paths under the configured base path, compared as whole paths.

#### Scenario: Look-alike path

- **WHEN** a request with a session cookie and without a CSRF token uses `POST /other/api/auth/login`
- **THEN** it is refused with `403`

#### Scenario: Real login path

- **WHEN** a request without a CSRF token uses `POST /api/auth/login`
- **THEN** the CSRF check does not refuse it

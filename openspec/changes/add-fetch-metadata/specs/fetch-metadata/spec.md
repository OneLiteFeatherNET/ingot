# Spec Delta

## Purpose

A browser session cookie authenticates a person on the dashboard. A request that a page on another site starts must not
use that cookie to read or change `/api`, while top-level navigation, token clients and same-origin dashboard calls keep
working.

## ADDED Requirements

### Requirement: Cross-site cookie requests to the API are refused

The server SHALL answer `403` to a request under `/api` that has no `Authorization` header, carries the `ingot_session`
or the `ingot_refresh` cookie, and has a `Sec-Fetch-Site` header of `same-site` or `cross-site`, unless the request is a
top-level navigation. A missing `Sec-Fetch-Site` header, or one with any other value, SHALL be allowed.

#### Scenario: Cross-site read with a session cookie

- **WHEN** a client sends `GET /api/auth/me` with the cookie `ingot_session` and `Sec-Fetch-Site: cross-site`
- **THEN** the response status is `403` and the body is the error response of the CSRF refusal

#### Scenario: Sibling subdomain

- **WHEN** a client sends `POST /api/identity/me/tokens` with the cookie `ingot_session`, a matching `X-CSRF-Token`
  and `Sec-Fetch-Site: same-site`
- **THEN** the response status is `403` and the handler does not run

#### Scenario: Same origin

- **WHEN** a client sends `GET /api/auth/me` with the cookie `ingot_session` and `Sec-Fetch-Site: same-origin`
- **THEN** the response is the same as without the header

#### Scenario: Missing header

- **WHEN** a client sends `GET /api/auth/me` with the cookie `ingot_session` and no `Sec-Fetch-Site` header
- **THEN** the response is the same as without the header

### Requirement: Top-level navigation is not refused

The server SHALL NOT refuse a request whose `Sec-Fetch-Mode` is `navigate` and whose `Sec-Fetch-Dest` is `document`, even
when its `Sec-Fetch-Site` is `cross-site`.

#### Scenario: Link from another site

- **WHEN** a browser opens `GET /api/auth/oidc/{id}/callback` by a top-level navigation from another site, with the cookie
  `ingot_session`, `Sec-Fetch-Mode: navigate`, `Sec-Fetch-Dest: document` and `Sec-Fetch-Site: cross-site`
- **THEN** the request is handled as it is without the header

### Requirement: Token requests are not refused

The server SHALL NOT apply the cross-site refusal to a request that carries an `Authorization` header, even when a session
cookie is also present.

#### Scenario: Bearer token from another site

- **WHEN** a client sends `GET /api/auth/me` with `Authorization: Bearer <token>` and `Sec-Fetch-Site: cross-site`
- **THEN** the request is authenticated by the token and is not refused by this rule

### Requirement: Scope is the API only

The server SHALL apply the cross-site refusal only to paths under `/api`, below the configured base path or at the root.
A path such as `/apix` and a repository path SHALL NOT be refused by this rule.

#### Scenario: Repository path

- **WHEN** a client sends `GET /releases/com/example/a/1.0/a-1.0.jar` with a session cookie and `Sec-Fetch-Site: cross-site`
- **THEN** the request is not refused by this rule

#### Scenario: Look-alike path

- **WHEN** a client sends `GET /apix/status` with a session cookie and `Sec-Fetch-Site: cross-site`
- **THEN** the request is not refused by this rule

### Requirement: Switch to turn the check off

The server SHALL read `INGOT_FETCH_METADATA`, or the system property `ingot.fetchMetadata` when the variable is unset,
and SHALL turn the refusal off only when the value is `off`, matched without regard to case. Any other value SHALL keep the
refusal on. The server SHALL NOT read a `REPOSILITE_` spelling of this switch.

#### Scenario: Switched off

- **WHEN** the server starts with `INGOT_FETCH_METADATA=off` and a cookie request with `Sec-Fetch-Site: cross-site` arrives
- **THEN** the request is not refused by this rule

#### Scenario: Unrecognised value

- **WHEN** the server starts with `INGOT_FETCH_METADATA=of` and a cookie request with `Sec-Fetch-Site: cross-site` arrives
- **THEN** the response status is `403`

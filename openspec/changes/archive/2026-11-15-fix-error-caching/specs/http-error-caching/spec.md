# Spec Delta

## Purpose

An error answer depends on the request's `Accept` header and must not be stored by a shared cache as the answer for every
client. A miss on a repository path must not stay cached as a miss.

## ADDED Requirements

### Requirement: Vary on error answers

The server MUST include `Accept` in the `Vary` header of every error answer whose body is an `ErrorResponse`, because its
content type depends on the request's `Accept` header. A `Vary` value that the response already has MUST be kept, `Accept`
MUST NOT be listed twice, and a `Vary` value of `*` MUST stay `*`.

#### Scenario: A client asks for problem details

- **WHEN** a client sends `Accept: application/problem+json` and an `/api` request fails with `404`
- **THEN** the answer has `Content-Type: application/problem+json` and a `Vary` header that includes `Accept`

#### Scenario: A legacy client sends no Accept header

- **WHEN** a client sends no `Accept` header and a repository request is refused with `401`
- **THEN** the answer has `Content-Type: application/json` and a `Vary` header that includes `Accept`

#### Scenario: An existing Vary value is kept

- **WHEN** an error answer already has `Vary: Accept-Encoding`
- **THEN** the `Vary` header is `Accept-Encoding, Accept`

### Requirement: No-cache on error answers outside /api

The server MUST answer `Cache-Control: no-cache` on an error answer outside `/api` that has no `Cache-Control` value of its
own, so that a shared cache revalidates a miss instead of keeping it. An error answer under `/api` MUST keep the
`Cache-Control` value that it has today.

#### Scenario: A missing file in a repository

- **WHEN** an anonymous client requests a file that a repository does not contain
- **THEN** the answer is `404` with the HTML page as before, and `Cache-Control: no-cache`

#### Scenario: A refused repository request

- **WHEN** a client is refused on a repository path with an `ErrorResponse` and `401`
- **THEN** the answer has `Cache-Control: no-cache` and keeps its `WWW-Authenticate` challenge

#### Scenario: An API error keeps its cache header

- **WHEN** an `/api` request fails and `bypassExternalCache` is disabled
- **THEN** the answer has no `Cache-Control` header that this change added

### Requirement: Success answers are unchanged

This change MUST NOT alter the status code, body, `Content-Type` or any header of a successful answer, and MUST NOT alter the
status code, body or `Content-Type` of an error answer.

#### Scenario: A successful download

- **WHEN** a client downloads an artifact that exists
- **THEN** the answer has the same `Cache-Control`, `Content-Type` and `Content-Length` as before this change, and no `Vary` value
  that this change added

#### Scenario: An error body keeps its message

- **WHEN** a client requests a missing repository file with `Accept: application/json`
- **THEN** the status and the message of the error are the same as before this change

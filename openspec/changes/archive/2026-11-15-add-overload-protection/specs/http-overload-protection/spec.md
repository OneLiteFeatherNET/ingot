# Spec Delta

## Purpose

An operator can cap the requests that Ingot works on at once. Above the cap a client is told to retry, and the health probe is never refused, so a burst does not cascade into restarts.

## ADDED Requirements

### Requirement: The limit is opt-in

The server SHALL NOT limit concurrent requests unless `INGOT_HTTP_MAXCONCURRENT` (or the system property
`ingot.http.maxConcurrent`) is set to a positive whole number. A server without the setting SHALL answer every request as it does today.

#### Scenario: Setting unset

- **WHEN** the server starts without `INGOT_HTTP_MAXCONCURRENT`, `REPOSILITE_HTTP_MAXCONCURRENT` or the system property
- **THEN** no request is refused for concurrency and the responses are the same as before this change

### Requirement: Requests above the limit are refused at once

When the number of requests in progress equals the limit, the server SHALL answer a new request that is not exempt with
`503 Service Unavailable`, a `Retry-After` header of `1`, and the `ErrorResponse` body with `status` `503` and a generic
`message`. The server SHALL NOT queue the request, wait for a free slot, read its body or touch the database for it.

#### Scenario: Over the limit

- **WHEN** the server runs with `INGOT_HTTP_MAXCONCURRENT=1` and one artifact request is still in progress, and a second artifact request arrives
- **THEN** the second request is answered `503` with `Retry-After: 1` without waiting for the first to finish

#### Scenario: Refusal body without a problem preference

- **WHEN** a refused request does not name `application/problem+json` in its `Accept` header
- **THEN** the body is JSON with `status` and `message` and the content type `application/json`

#### Scenario: Refusal body with a problem preference

- **WHEN** a refused request names `application/problem+json` with a higher quality than `application/json`
- **THEN** the body is answered with the content type `application/problem+json`

### Requirement: A slot is held until the response is complete

The server SHALL release a request's slot when its response is complete or has failed, not when the handler returns. A
streamed artifact or an upload SHALL hold its slot until its last byte is written or read. A slot SHALL be released at most once per request.

#### Scenario: Streamed download holds its slot

- **WHEN** a download with `INGOT_HTTP_MAXCONCURRENT=1` has returned its handler but is still streaming its body
- **THEN** a second request is answered `503`, and it is answered normally after the first body has been written completely

#### Scenario: Failed request releases its slot

- **WHEN** a request in progress fails with an error or the client disconnects
- **THEN** its slot is released and the next request is served

### Requirement: Health probes are never refused

The server SHALL answer `GET /api/status/health` (and `GET /api/status/ready` once `add-readiness-endpoint` is merged)
regardless of the number of requests in progress, and such a request SHALL NOT count against the limit.

#### Scenario: Liveness at the limit

- **WHEN** the number of requests in progress equals `INGOT_HTTP_MAXCONCURRENT` and a client requests `GET /api/status/health`
- **THEN** the response is `200` and the number of requests in progress does not change

### Requirement: Invalid values stop the start

The server SHALL refuse to start when `INGOT_HTTP_MAXCONCURRENT` is set to a value that is not a positive whole number, and
the message SHALL name the variable and the value's rejection reason.

#### Scenario: Zero is refused

- **WHEN** the server starts with `INGOT_HTTP_MAXCONCURRENT=0`
- **THEN** the start fails with a message that names `INGOT_HTTP_MAXCONCURRENT`

#### Scenario: Non-numeric value is refused

- **WHEN** the server starts with `INGOT_HTTP_MAXCONCURRENT=many`
- **THEN** the start fails with a message that names `INGOT_HTTP_MAXCONCURRENT`

### Requirement: The legacy spelling is a fallback

The server SHALL read `REPOSILITE_HTTP_MAXCONCURRENT` when `INGOT_HTTP_MAXCONCURRENT` is not set, and the `INGOT_` variable SHALL win when both are set.

#### Scenario: Only the legacy variable is set

- **WHEN** the server starts with `REPOSILITE_HTTP_MAXCONCURRENT=4` and no `INGOT_HTTP_MAXCONCURRENT`
- **THEN** the limit is 4

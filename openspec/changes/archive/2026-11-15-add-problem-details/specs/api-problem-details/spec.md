# Spec Delta

## Purpose

Every error answered under `/api` has one shape. Clients that ask for `application/problem+json` get RFC 9457 Problem Details;
every other client gets the same body it has always got, with extra fields.

## ADDED Requirements

### Requirement: Common error shape

An error answer under `/api` SHALL be a JSON object with the members `status` (number), `message` (string), `type` (URI, default
`about:blank`), `title` (the reason phrase of `status`), `detail` (equal to `message`) and `instance` (the request path). `problems`
SHALL be present only where it is today. The members `status`, `message` and `problems` SHALL keep their present names, types and values.

#### Scenario: A refused request carries the legacy and the new members

- **WHEN** an anonymous client calls an `/api` route that requires authentication
- **THEN** the answer is `401` and the body contains `status` `401`, the same `message` as before, `title` `Unauthorized`, `detail` equal to `message`, `type` `about:blank` and `instance` equal to the request path

#### Scenario: A validation failure keeps its problems list

- **WHEN** a request is refused with a list of problems
- **THEN** the body still has `problems` with one entry per problem, and the new members are added next to it

### Requirement: Content negotiation

The server SHALL answer an error with `Content-Type: application/problem+json` when the request's `Accept` header prefers it, and
with `Content-Type: application/json` in every other case, including an absent `Accept` header and `*/*`.

#### Scenario: A client asks for problem details

- **WHEN** a client sends `Accept: application/problem+json` and the request fails with `404`
- **THEN** the answer has `Content-Type: application/problem+json` and the common error shape

#### Scenario: A legacy client sends nothing

- **WHEN** a client sends no `Accept` header and the request fails with `404`
- **THEN** the answer has `Content-Type: application/json` and contains the same `status` and `message` as before this change

#### Scenario: Quality values are respected

- **WHEN** a client sends `Accept: application/problem+json;q=0.1, application/json;q=0.9`
- **THEN** the answer has `Content-Type: application/json`

### Requirement: Every error path under /api uses the shape

Errors raised as a Javalin `HttpResponseException`, a request to an unmatched `/api/*` path, an over-large request body and an
unhandled failure SHALL answer in the common error shape. An unhandled failure SHALL answer `500` with a generic message and SHALL
NOT include the exception message or class name.

#### Scenario: Unmatched API path

- **WHEN** a client requests `GET /api/does-not-exist`
- **THEN** the answer is `404` with the common error shape and not the dashboard HTML page

#### Scenario: Unmatched dashboard path

- **WHEN** a browser requests `GET /unknown-dashboard-page` outside `/api`
- **THEN** the answer is the dashboard HTML page as before this change

#### Scenario: Unhandled failure

- **WHEN** a route throws an unexpected exception
- **THEN** the answer is `500` with the common error shape and a generic message, and the exception is recorded through the failure facade

#### Scenario: Javalin rejects the request itself

- **WHEN** a request is rejected by Javalin with an `HttpResponseException` such as `413`
- **THEN** the body is the common error shape and not the `{title, type, status, details}` shape

### Requirement: Plugin API compatibility

`ErrorResponse` SHALL keep its existing constructors, properties and helper functions with unchanged signatures. New members SHALL
have default values, so code compiled against the previous version keeps working without recompilation.

#### Scenario: A plugin builds an error the old way

- **WHEN** a plugin constructs `ErrorResponse(status, message)` or `ErrorResponse(HttpStatus, String)` and returns it from a route
- **THEN** the answer carries the common error shape, with `title` and `instance` filled in by the server

#### Scenario: The prometheus plugin still compiles

- **WHEN** the prometheus plugin is compiled against the changed `ErrorResponse`
- **THEN** it compiles with no source change

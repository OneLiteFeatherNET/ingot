# Spec Delta

## Purpose

The OpenAPI document that Ingot serves lists every REST endpoint, says how to authenticate, and the documentation states who can
read it. A new endpoint cannot be added without its entry or a written reason.

## ADDED Requirements

### Requirement: Every REST route is documented

Every REST route registered by the backend SHALL have an entry in the served OpenAPI document, except routes on a written
exclusion list that gives a reason for each.

#### Scenario: Previously missing route is listed

- **WHEN** a client requests the OpenAPI document
- **THEN** it contains `GET /api/maven/details/{repository}` next to the list and file-path forms

#### Scenario: Unannotated route fails the build

- **WHEN** a route is registered with no annotation and no exclusion entry
- **THEN** the completeness test fails and its message names the route

### Requirement: Annotations do not change behaviour

Completing the document SHALL NOT change any route, status code, header or response body of the API.

#### Scenario: Responses are unchanged

- **WHEN** the existing endpoint tests run against the annotated classes
- **THEN** they pass without modification

### Requirement: Security schemes are declared

The document SHALL declare HTTP Basic with a token and the session bearer as security schemes, and routes that need a login SHALL
reference them.

#### Scenario: Scheme is present

- **WHEN** a client reads the document
- **THEN** both schemes are declared and a route that needs a login references one of them

### Requirement: Access to the document is verified and documented

The behaviour of `GET /openapi` without credentials SHALL be covered by an integration test, and the reference pages SHALL state
that behaviour consistently.

#### Scenario: Pages agree

- **WHEN** a reader compares `web-routes.md` and `http-api.md`
- **THEN** both give the same access statement and neither carries a "not verified" hint about it

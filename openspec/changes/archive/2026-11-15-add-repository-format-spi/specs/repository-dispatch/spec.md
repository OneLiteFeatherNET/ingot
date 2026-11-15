# Spec Delta

## Purpose

Requests under `/{repository}/**` reach the handler of the repository's format, and every response stays byte for byte what
it is today.

## ADDED Requirements

### Requirement: Requests are dispatched by repository format

The server SHALL register one route set on `/{repository}/<path>` for HEAD, GET, POST, PUT and DELETE, at the priority and
registration point of the Maven routes it replaces. For each request it SHALL resolve the repository through the registry
and call the request handler of that repository's format. A request for a repository that does not exist SHALL be handled
by the default format `maven2`.

#### Scenario: Maven artifact

- **WHEN** a Maven client requests `GET /releases/com/example/a/1.0/a-1.0.jar`
- **THEN** the `maven2` handler answers with the same status, headers and body as before

#### Scenario: Unknown repository

- **WHEN** a client requests `GET /missing/com/example/a/1.0/a-1.0.jar`
- **THEN** the response is the same `404` page as before

#### Scenario: Upload to a proxy repository

- **WHEN** a client sends `PUT /central/com/example/a/1.0/a-1.0.jar` to a typed proxy repository
- **THEN** the response is `405` with the same `Allow` header and body as before

### Requirement: Responses unchanged

For HEAD, GET, POST, PUT and DELETE on hosted, proxy and group repositories, on directories, on `/api/` paths that no route
claims, on a dashboard path shared with a repository, and on paths served by the redirect plugin, the status, headers and
body SHALL equal those recorded before the change.

#### Scenario: Characterisation

- **WHEN** the characterisation integration test written before the change runs after it
- **THEN** it passes without edits

#### Scenario: Shared dashboard path

- **WHEN** a repository named `settings` exists and a Gradle client requests `GET /settings/com/example/a/1.0/a-1.0.pom`
- **THEN** the dispatcher serves the artifact, and a top-level browser navigation to `/settings` still gets the dashboard

#### Scenario: Redirect plugin

- **WHEN** the server runs with `reposilite.redirect.default-repository=releases` and a client requests a redirected path
- **THEN** the answer is the same as before, produced through the dispatcher

### Requirement: Surrounding routing unchanged

The dashboard routes SHALL keep `Priorities.HIGH`, the `WWW-Authenticate` after filter SHALL keep its path, the reserved
segment warnings SHALL keep their text, and the generated OpenAPI document SHALL keep its repository paths and tags.

#### Scenario: OpenAPI document

- **WHEN** the OpenAPI document is generated after the change
- **THEN** the paths `/{repository}/{gav}` with the tag `Maven` and their operations equal those of main

#### Scenario: Challenge on a repository path

- **WHEN** an anonymous client requests a file of a private repository
- **THEN** the `401` carries `WWW-Authenticate: Basic realm="Ingot", charset="UTF-8"`, as before

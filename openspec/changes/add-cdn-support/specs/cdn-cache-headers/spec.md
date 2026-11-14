# Spec Delta

## Purpose

A shared cache stores a file answer only when no upstream credential stood behind it, and a compressed answer is stored
under a key that names its encoding.

## ADDED Requirements

### Requirement: A credentialed mirror makes the answer private

The server SHALL answer a file that a repository serves through a mirror with `authorization` set (method BASIC or
CUSTOM_HEADER) with `Cache-Control: private, max-age=<maxAge>`, whatever the visibility of the repository. The rule SHALL apply
to a group that reaches such a mirror, directly or through another group. A mirror without `authorization` keeps the rule of
the repository cache-control capability. When `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` is `true`, the server SHALL answer `public`.

#### Scenario: Public proxy with upstream credentials, anonymous download

- **WHEN** an anonymous client requests a file through a PUBLIC proxy whose mirror has basic credentials, and the file may be cached
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Public proxy without upstream credentials

- **WHEN** an anonymous client requests a file through a PUBLIC proxy whose mirror has no `authorization`, and the file may be cached
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

#### Scenario: Public group that reaches a credentialed proxy

- **WHEN** an anonymous client requests a file through a PUBLIC group that references a PUBLIC proxy with credentials
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Escape hatch restores the public value

- **WHEN** the server starts with `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` and an anonymous client requests a file through a PUBLIC proxy with credentials
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

### Requirement: Compressed file answers vary on the encoding

When compression is enabled, the server SHALL send `Vary` with `Accept-Encoding` on file answers, so that a shared cache keys a
compressed answer by its encoding. The server SHALL keep the other headers of the answer as they are.

#### Scenario: Compressed answer

- **WHEN** a client that accepts gzip requests a file while compression is enabled
- **THEN** the response carries a `Vary` header that names `Accept-Encoding`

# Spec Delta

## Purpose

Browsers are told how to treat the dashboard, the API and repository content, without changing what Maven and Gradle clients see.

## ADDED Requirements

### Requirement: Headers on every response

The server SHALL send `X-Content-Type-Options: nosniff` and `Referrer-Policy: strict-origin-when-cross-origin` on every response.

#### Scenario: Artifact download

- **WHEN** a client requests `GET /releases/com/example/a/1.0/a-1.0.jar`
- **THEN** the response carries both headers and the same body and status as before

### Requirement: Framing protection on the dashboard and the API

The server SHALL send `X-Frame-Options: DENY` and a `Content-Security-Policy` with `frame-ancestors 'none'` on dashboard
documents and on `/api` responses.

#### Scenario: API response

- **WHEN** a client requests `GET /api/status/health`
- **THEN** the response carries `X-Frame-Options: DENY` and a policy containing `frame-ancestors 'none'`

### Requirement: Dashboard policy that the built application satisfies

The dashboard document SHALL carry a `Content-Security-Policy` that the built application and the `{{REPOSILITE.*}}`
placeholder injection satisfy, and a `Permissions-Policy` denying camera, microphone, geolocation, payment and usb.

#### Scenario: Built index is served

- **WHEN** the built `index.html` is served by the backend
- **THEN** every inline script and style in it is allowed by the policy through a hash or nonce, and the policy lists no other inline source

### Requirement: Repository content stays sandboxed

Repository files, the directory index pages and the Javadoc pages SHALL carry `Content-Security-Policy` with `sandbox`.
Javadoc pages SHALL keep `allow-scripts`. Existing values SHALL NOT be loosened. The setting `INGOT_REPOSITORY_SANDBOX`
(default `true`) SHALL remove the header when `false`.

#### Scenario: Directory index

- **WHEN** a client requests a directory such as `GET /releases/com/example/`
- **THEN** the response carries a `Content-Security-Policy` containing `sandbox`

#### Scenario: Javadoc keeps scripts

- **WHEN** a Javadoc page is requested through the Javadoc endpoint
- **THEN** its policy contains `sandbox` and `allow-scripts` and does not contain `allow-same-origin`

#### Scenario: Sandbox switched off

- **WHEN** the server starts with `INGOT_REPOSITORY_SANDBOX=false` and a repository file is requested
- **THEN** the response carries no `sandbox` policy

### Requirement: HSTS is opt-in and HTTPS only

The server SHALL send `Strict-Transport-Security: max-age=<n>` only when `INGOT_HSTS_MAX_AGE` is set and the request arrived
over HTTPS, directly or through `X-Forwarded-Proto: https`. It SHALL NOT add `includeSubDomains` or `preload`.

#### Scenario: Not configured

- **WHEN** `INGOT_HSTS_MAX_AGE` is unset and a request arrives over HTTPS
- **THEN** the response carries no `Strict-Transport-Security` header

#### Scenario: Plain HTTP

- **WHEN** `INGOT_HSTS_MAX_AGE=31536000` is set and a request arrives over plain HTTP
- **THEN** the response carries no `Strict-Transport-Security` header

#### Scenario: Behind a TLS proxy

- **WHEN** `INGOT_HSTS_MAX_AGE=31536000` is set and a request carries `X-Forwarded-Proto: https`
- **THEN** the response carries `Strict-Transport-Security: max-age=31536000`

# Spec Delta

## Purpose

The embedded dashboard and the Nitro dashboard server tell the browser how long to keep the bundle, when to ask again
for the document, and which browsing context may share the page. Both servers answer the same way.

## ADDED Requirements

### Requirement: Hashed dashboard assets are cached as immutable

The backend SHALL send `Cache-Control: public, max-age=31536000, immutable` on every successful response for a file under
`/assets/`. The Nitro dashboard server SHALL send the same value for its assets.

#### Scenario: Existing bundle

- **WHEN** a client requests `GET /assets/index-<hash>.js` and the file exists in the bundle
- **THEN** the response is `200` and carries `Cache-Control: public, max-age=31536000, immutable`

#### Scenario: Missing asset

- **WHEN** a client requests `GET /assets/does-not-exist.js`
- **THEN** the response is `404` and carries no `immutable` directive

### Requirement: The dashboard document is revalidated on every load

The backend SHALL send `Cache-Control: no-cache` on the dashboard document, which is the response to `/`, `/index.html`
and to each application path that the backend answers with the dashboard. The Nitro dashboard server SHALL send the same
value on its document response.

#### Scenario: Index page

- **WHEN** a client requests `GET /` on a server without a repository named `/`
- **THEN** the response carries `Cache-Control: no-cache`

#### Scenario: Application path

- **WHEN** a client requests `GET /settings`, and no repository is named `settings`
- **THEN** the response carries `Cache-Control: no-cache`

#### Scenario: Maven answer on a shared path

- **WHEN** a client requests a Maven path under a repository whose name is also a dashboard path
- **THEN** the response is the Maven answer and carries the `Cache-Control` that Maven sets, not `no-cache`

### Requirement: The dashboard document isolates its browsing context group

The backend SHALL send `Cross-Origin-Opener-Policy: same-origin` on the dashboard document. The Nitro dashboard server
SHALL send the same header on its document response. Assets, `/api` responses and repository content SHALL NOT carry it.

#### Scenario: Dashboard document

- **WHEN** a client requests `GET /index.html`
- **THEN** the response carries `Cross-Origin-Opener-Policy: same-origin`

#### Scenario: Asset does not carry the opener policy

- **WHEN** a client requests `GET /assets/index-<hash>.js`
- **THEN** the response does not carry `Cross-Origin-Opener-Policy`

#### Scenario: API response does not carry the opener policy

- **WHEN** a client requests `GET /api/status/health`
- **THEN** the response does not carry `Cross-Origin-Opener-Policy`

### Requirement: Existing responses keep their headers

The change SHALL NOT alter the status code, the body or any header of a response outside the dashboard document and the
dashboard assets. An operator-supplied frontend directory SHALL keep the headers it has today.

#### Scenario: Artifact download

- **WHEN** a client requests a released artifact through its Maven path
- **THEN** the status, the body and every header are the same as before this change

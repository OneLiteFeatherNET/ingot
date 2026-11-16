# Spec Delta

## Purpose

A shared cache in front of Ingot learns which URLs changed when an artifact is deployed or deleted. The plugin is not part of the
image and does nothing until the operator sets its URL.

## ADDED Requirements

### Requirement: The plugin is off without a URL

The plugin SHALL send no request when `INGOT_CDN_PURGE_URL` (fallback `REPOSILITE_CDN_PURGE_URL`) is empty or unset. When the URL
is set and `INGOT_CDN_PURGE_BASE_URL` (fallback `REPOSILITE_CDN_PURGE_BASE_URL`) is not, the plugin SHALL refuse to start, and the
message SHALL name `INGOT_CDN_PURGE_BASE_URL`.

#### Scenario: No URL configured

- **WHEN** a file is deployed and the purge URL is not set
- **THEN** no request is sent

#### Scenario: Base URL missing

- **WHEN** the purge URL is set and the base URL is not
- **THEN** the plugin does not start, and the message names `INGOT_CDN_PURGE_BASE_URL`

### Requirement: A deploy or a delete sends one purge request

For each `DeployEvent` and each `DeleteEvent`, the plugin SHALL send one `POST` to the purge URL with the JSON body
`{"urls": ["<public url>"]}`, where the public URL is the base URL joined with the repository id and the path, with each path
segment percent-encoded. When `INGOT_CDN_PURGE_TOKEN` (fallback `REPOSILITE_CDN_PURGE_TOKEN`) is set, the request SHALL carry
`Authorization: Bearer` with that token, and the token SHALL NOT be written to any log.

#### Scenario: Deploy

- **WHEN** a file `com/example/app/1.0/app-1.0.jar` is deployed to repository `releases` and the base URL is `https://repo.example.com`
- **THEN** one request is sent with the body `{"urls": ["https://repo.example.com/releases/com/example/app/1.0/app-1.0.jar"]}`

#### Scenario: Delete

- **WHEN** a file is deleted from a repository
- **THEN** one request is sent for its public URL

#### Scenario: Token

- **WHEN** the token is set
- **THEN** the request carries `Authorization: Bearer` with the token, and the token does not appear in the log

### Requirement: A failed purge does not change the answer of the request

The plugin SHALL catch every failure of a purge request, including a timeout, a refused connection and a non-2xx status, log it, and
return. The deployment or the deletion SHALL answer as it would without the plugin. A purge that fails is not retried. The
request timeout SHALL be 5 seconds.

#### Scenario: Purge endpoint fails

- **WHEN** the purge endpoint answers `500` to a deploy
- **THEN** the deployment still answers its own status, and the failure is logged once

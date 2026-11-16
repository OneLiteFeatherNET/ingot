# Spec Delta

## Purpose

An operator who puts a CDN in front of Ingot finds one page that states which answers the CDN may store, how it treats
credentials, and which optional switches help it, and the page agrees with the reference pages.

## ADDED Requirements

### Requirement: The guide is listed and linked

The guide SHALL exist at `docs/gitbook/how-to/put-a-cdn-in-front.md`, SHALL be listed in `docs/gitbook/SUMMARY.md`, and SHALL be linked
from `docs/gitbook/how-to/run-behind-reverse-proxy.md`.

#### Scenario: Navigation

- **WHEN** the documentation is read from its summary
- **THEN** the guide is reachable from the summary and from the reverse proxy guide

### Requirement: The guide states the cacheability of each answer

The guide SHALL state, in a table, the `Cache-Control` values that a shared cache may see, and the table SHALL match the value
description in `web-routes.md`. The guide SHALL state that a cache must not store a `private` answer, and that an answer
produced with upstream credentials is `private`.

#### Scenario: Table matches the reference

- **WHEN** the value table of the guide is compared with the `Cache-Control` row of `web-routes.md`
- **THEN** the values and their conditions are the same

### Requirement: The guide states how a CDN treats credentials

For a private repository, the guide SHALL state that the CDN forwards `Authorization` to Ingot and does not answer a request that
carries it from its store, or that it bypasses the cache for such requests. The guide SHALL state that Ingot sets no `s-maxage`
and that the TTL is set on the CDN.

#### Scenario: Private repository behind a CDN

- **WHEN** a reader looks for the rule on credentials
- **THEN** the guide names the forwarding of `Authorization` and the bypass or the no-store rule for requests that carry it

### Requirement: The guide names every variable of this change

The guide SHALL name each of `INGOT_S3_PRESIGNED_REDIRECTS`, `INGOT_S3_PRESIGN_TTL_SECONDS`, `INGOT_S3_PRESIGN_ENDPOINT`,
`INGOT_CDN_PURGE_URL`, `INGOT_CDN_PURGE_BASE_URL` and `INGOT_CDN_PURGE_TOKEN`, and each name SHALL appear in
`docs/gitbook/reference/environment-variables.md` with the same default.

#### Scenario: Variables agree

- **WHEN** the variable names of the guide are compared with the environment variables reference
- **THEN** every name of the guide is listed there with the same default

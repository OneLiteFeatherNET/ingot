# Spec Delta

## Purpose

Concurrent requests for one mirrored artifact share one upstream fetch when the mirror stores its copy, so a burst of builds does not multiply the upstream load. This behaviour already exists and is pinned here so that the request limit cannot break it.

## ADDED Requirements

### Requirement: Stored fetches of one artifact are shared

When every mirror host of a repository stores its copy, concurrent requests for the same artifact by the same kind of caller (authenticated or anonymous) SHALL share one upstream fetch while it is in progress. Each waiting request SHALL receive the same resolution as the fetch that it waited for.

#### Scenario: Two requests during one fetch

- **WHEN** two requests for the same artifact arrive while the first upstream fetch has not completed
- **THEN** the upstream host receives one request for that artifact and both callers receive the stored file

### Requirement: Streamed fetches are not shared

When any mirror host of the repository streams without storing, the server SHALL fetch for each caller and SHALL NOT share the stream between callers.

#### Scenario: Non-storing host

- **WHEN** two requests for the same artifact arrive and the only mirror host has `store` set to false
- **THEN** the upstream host receives one request for each caller

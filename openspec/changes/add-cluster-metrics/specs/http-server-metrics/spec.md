# Spec Delta

## Purpose

Operators can scrape request timings and operational health from several replicas and aggregate them, without changing the series
that dashboards already read.

## ADDED Requirements

### Requirement: Request duration histogram

The metrics endpoint SHALL expose `ingot_http_server_request_duration_seconds` as a histogram with the labels `method`, `route`
and `code`, and it SHALL observe each request that is not served by the metrics path itself.

#### Scenario: Matched route

- **WHEN** a client requests `GET /releases/com/example/a/1.0/a-1.0.jar` and the route answers `200`
- **THEN** one observation is recorded with `route` set to the route template of that request, `method="GET"` and `code="200"`

#### Scenario: Unmatched path

- **WHEN** a client requests a path that matches no route
- **THEN** the observation carries `route="unmatched"` and the raw path appears in no label

#### Scenario: Duration uses the injected ticker

- **WHEN** the injected ticker advances by 250 ms between the start and the end of a request in a test
- **THEN** the observation equals 0.25 seconds, and the wall clock is not read

### Requirement: Request counter

The metrics endpoint SHALL expose `ingot_http_server_requests_total` as a counter with the same labels as the histogram, and it
SHALL increase it once for each observed request.

#### Scenario: Two requests

- **WHEN** two requests with the same method, route and status are served
- **THEN** the counter for that label set increases by two

### Requirement: Existing series keep their names and types

The metrics endpoint SHALL keep every existing `jetty_*` and `reposilite_*` series with its current name, type and labels. The new
`ingot_*` series SHALL be added alongside them and SHALL NOT replace any of them.

#### Scenario: Existing response time summary

- **WHEN** a scrape is taken after the change
- **THEN** `jetty_response_time_seconds` is still present as a summary with the `code` label

### Requirement: Operational series for replicas

The metrics endpoint SHALL expose the following series, each read at scrape time from the facade that owns the value:
`ingot_deploy_results_total` with the label `result` (`accepted` or `rejected`), `ingot_upstream_requests_total` with the label
`outcome` (`success`, `not_found`, `failure` or `timeout`), and `ingot_mirror_cache_entries`.

#### Scenario: Rejected deploy

- **WHEN** a deploy is refused by the repository's rules
- **THEN** `ingot_deploy_results_total{result="rejected"}` increases by one and no other `result` value changes

#### Scenario: Upstream timeout

- **WHEN** a proxy repository's upstream fetch times out
- **THEN** `ingot_upstream_requests_total{outcome="timeout"}` increases by one

### Requirement: Cluster and job health series

When the cluster bus is configured, the metrics endpoint SHALL expose `ingot_cluster_connected` (0 or 1) and
`ingot_cluster_dropped_events_total`. The metrics endpoint SHALL expose `ingot_batch_job_last_success_timestamp_seconds` with the
label `job` (the cleanup policy run and the audit retention purge), `ingot_search_outbox_pending` and
`ingot_search_outbox_oldest_age_seconds`. Without a configured cluster bus the cluster series SHALL be absent.

#### Scenario: No cluster bus

- **WHEN** no cluster URL is configured and a scrape is taken
- **THEN** no `ingot_cluster_*` series is present

#### Scenario: Outbox backlog

- **WHEN** the search outbox holds three pending entries and the oldest was written 40 seconds ago
- **THEN** `ingot_search_outbox_pending` reads 3 and `ingot_search_outbox_oldest_age_seconds` reads 40

### Requirement: Bounded label values

No series SHALL carry a repository name, artifact path, user name, token name or client address as a label value.

#### Scenario: Many repositories

- **WHEN** deploys happen to ten different repositories
- **THEN** the number of series of `ingot_deploy_results_total` does not change with the number of repositories

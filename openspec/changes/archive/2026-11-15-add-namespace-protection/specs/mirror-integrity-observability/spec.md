# Spec Delta

## Purpose

An operator can see checksum results of mirrors as audit events and as a metric, and so alert on a poisoned upstream. Nothing is
recorded for a mirror whose `checksumPolicy` is `ignore`.

## ADDED Requirements

### Requirement: Mismatch event

When a mirror with `checksumPolicy` `warn` or `fail` fetches an artifact whose digest differs from the upstream checksum file,
the service SHALL write an audit event `MIRROR_CHECKSUM_MISMATCH` with `success` false. The details SHALL hold `host`, `policy`,
`algorithm` and `outcome`, where `outcome` is `stored` under `warn` and `refused` under `fail`.

#### Scenario: Warn stores and records

- **WHEN** the policy is `warn` and the copy does not match
- **THEN** the copy is stored and an event with outcome `stored` is written

#### Scenario: Fail refuses and records

- **WHEN** the policy is `fail` and the copy does not match
- **THEN** the copy is not stored and an event with outcome `refused` is written

### Requirement: Missing checksum event

When a host publishes no usable checksum file for a fetched artifact under `warn` or `fail`, the service SHALL write an audit
event `MIRROR_CHECKSUM_MISSING` with the same details keys.

#### Scenario: Neither file exists

- **WHEN** the host has no `.sha256` and no `.sha1` file for the artifact under `fail`
- **THEN** the host is refused and a `MIRROR_CHECKSUM_MISSING` event with outcome `refused` is written

### Requirement: Policy ignore records nothing

A mirror with `checksumPolicy` `ignore`, and any metadata or checksum file, SHALL write no integrity event and move no integrity
counter.

#### Scenario: Ignore is silent

- **WHEN** a mirror with policy `ignore` stores an artifact
- **THEN** no audit event is written and the counter is unchanged

### Requirement: Checksum result metric

The metrics endpoint SHALL expose the counter `ingot_mirror_checksum_results_total` with the labels `policy` (`warn`, `fail`) and
`result` (`verified`, `mismatch`, `missing`). Every label combination SHALL be present from the start of the instance, and `warn`
SHALL be counted like `fail`.

#### Scenario: Series exist at zero

- **WHEN** the metrics are scraped before any fetch
- **THEN** all six series are present with the value 0

#### Scenario: Warn mismatch counted

- **WHEN** a `warn` mirror stores a mismatching copy
- **THEN** the series `policy="warn",result="mismatch"` is 1

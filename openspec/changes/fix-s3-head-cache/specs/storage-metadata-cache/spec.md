# Spec Delta

## Purpose

An S3 object metadata answer is reused only where reusing it cannot show a stale file to a client, and in a cluster it is
reused for a bounded time only.

## ADDED Requirements

### Requirement: Mutable keys are never cached

The S3 metadata cache SHALL NOT store a HEAD answer for a key whose file name starts with `maven-metadata`, or for a key
that has a directory segment ending in `-SNAPSHOT`. Each such request SHALL be sent to S3.

#### Scenario: Maven metadata and its checksum

- **WHEN** `parent/maven-metadata.xml` and `parent/maven-metadata.xml.sha1` are each requested twice
- **THEN** S3 receives four HEAD requests

#### Scenario: Snapshot artifact

- **WHEN** `group/artifact/1.0-SNAPSHOT/artifact-1.0-20260101.120000-1.jar` is requested twice
- **THEN** S3 receives two HEAD requests

#### Scenario: Release artifact stays cached

- **WHEN** `group/artifact/1.0/artifact-1.0.jar` is requested twice
- **THEN** S3 receives one HEAD request

### Requirement: Cluster bounds the age of a cached answer

While a cluster bus is configured, a cached HEAD answer SHALL NOT be used more than 300 seconds after it was stored. Reads
SHALL NOT renew this bound. The configured creation and access expiry SHALL still apply when they are shorter.

#### Scenario: Entry expires with a bus

- **WHEN** the bus is open, an answer is stored at time 0, and the key is requested again at 301 seconds
- **THEN** S3 receives a second HEAD request for the key

#### Scenario: Reads do not renew the bound

- **WHEN** the bus is open, an answer is stored at time 0, read at 200 seconds, and the key is requested again at 301 seconds
- **THEN** S3 receives a second HEAD request for the key

### Requirement: Standalone keeps the configured lifetime

Without a cluster bus, the cache SHALL keep the creation and access expiry from its settings and SHALL NOT apply the 300 second bound.

#### Scenario: No bus

- **WHEN** no cluster bus is configured, an answer is stored at time 0, and the key is requested again at 301 seconds
- **THEN** the cached answer is used and S3 receives no second HEAD request for the key

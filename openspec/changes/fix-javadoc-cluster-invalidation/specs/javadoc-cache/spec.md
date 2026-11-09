# Spec Delta

## Purpose

A replica that serves an unpacked javadoc copy drops it when another replica deploys a new build of the same artifact, and a received eviction can never reach outside the javadoc cache of its repository.

## ADDED Requirements

### Requirement: Deploy drops the javadoc cache on every replica

When a javadoc jar is deployed and a cluster bus is configured, the instance that accepted the deploy SHALL publish a `javadoc.invalidated` event that names the repository and the artifact path. Every other instance of the cluster SHALL remove the unpacked javadoc cache of that artifact.

#### Scenario: Replica with an old copy

- **WHEN** a javadoc jar of `com/example/lib/1.0-SNAPSHOT` is deployed on replica A and replica B holds an unpacked copy of that artifact
- **THEN** replica B removes the unpacked copy, and its next request unpacks the new jar

#### Scenario: Other artifacts are kept

- **WHEN** an event names the artifact `com/example/lib`
- **THEN** the cached javadoc of `com/example/other` on every replica is left untouched

### Requirement: Eviction stays inside the javadoc cache of its repository

An instance SHALL refuse a received `javadoc.invalidated` event whose path is not a valid repository location or whose resolved directory is not strictly inside the javadoc cache of the named repository. A refused event SHALL delete nothing and SHALL be logged.

#### Scenario: Path leaves the cache root

- **WHEN** an event carries the path `../../etc`
- **THEN** no directory is removed and the refusal is logged

#### Scenario: Unknown repository

- **WHEN** an event names a repository that the instance does not have
- **THEN** no directory is removed

### Requirement: Applied events are not published again

An instance SHALL NOT publish a `javadoc.invalidated` event when it applies an event that it received.

#### Scenario: Echo suppressed

- **WHEN** replica B applies an event sent by replica A
- **THEN** replica B publishes nothing

### Requirement: Without a bus nothing changes

Without a cluster bus, a javadoc deploy SHALL remove the cache of the accepting instance only, as it does today, and SHALL publish no event.

#### Scenario: Single instance

- **WHEN** Ingot runs without a cluster bus URL and a javadoc jar is deployed
- **THEN** the local cache is removed and no event is published

### Requirement: The event carries no secret material and unknown types are ignored

The event type `javadoc.invalidated` SHALL carry the payload fields `repository` and `path` only. An instance that does not know the type SHALL ignore the message and keep running.

#### Scenario: Older replica receives the event

- **WHEN** a replica without this event type receives it
- **THEN** the replica ignores the message and keeps serving requests

### Requirement: A lost event is not repaired by polling

A replica that does not receive the event SHALL keep its unpacked javadoc copy until that copy is removed. The change SHALL NOT add a poll or a time bound for javadoc caches, and the operator guide SHALL state this.

#### Scenario: Bus unavailable during a deploy

- **WHEN** the bus is unavailable when a javadoc jar is deployed
- **THEN** the other replicas keep serving their previous unpacked copy, and the operator guide describes this

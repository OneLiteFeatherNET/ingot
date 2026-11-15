# Spec Delta

## Purpose

Two Ingot replicas on one database, one bucket and one bus behave as one server during rolling restarts, pod kills,
concurrent deploys and sessions that move between pods. A pull request fails when they do not.

## ADDED Requirements

### Requirement: The smoke test runs on every pull request

The continuous integration SHALL run the HA smoke test on kind against two server replicas, one MariaDB server, one MinIO
bucket and one NATS server, installed from `charts/ingot/ci/ha-values.yaml`. A failing check SHALL fail the job.

#### Scenario: Pull request

- **WHEN** a pull request is opened or updated
- **THEN** the `ha-smoke` job starts the dependencies, installs the two replicas and runs every check, and the pull request
  fails if any check fails

### Requirement: No failed reads during a rolling restart

While the server Deployment is restarted, reads of an existing artifact through the Service SHALL all succeed and return the
artifact's bytes.

#### Scenario: Rollout restart under read traffic

- **WHEN** the server Deployment is restarted while the driver sends reads through the Service
- **THEN** every read returns status 200 with the artifact's bytes, and the count of failed reads is zero

### Requirement: A lease is taken over when its holder dies

When the replica that holds a cleanup lease is force-deleted during its run, another replica SHALL complete the run within the
lease time-to-live plus the poll interval, and the run SHALL be recorded once.

#### Scenario: Holder killed mid-run

- **WHEN** the run is observed as running on replica A and A is force-deleted
- **THEN** replica B completes the run within the lease time-to-live plus the poll interval, and the run history holds exactly one
  completed run

#### Scenario: Run not observed

- **WHEN** the run finishes before the driver observes it as running
- **THEN** the check fails with a message that names the missed observation, and does not pass

### Requirement: No lost metadata version on concurrent deploys

When two different versions of one artifact are deployed at the same time through two replicas, the group's
`maven-metadata.xml` SHALL list both versions afterwards.

#### Scenario: Two deploys at once

- **WHEN** the driver deploys versions 1.0.0 and 1.0.1 of one artifact through replicas A and B at the same time
- **THEN** both deploys succeed and `maven-metadata.xml` lists both versions

### Requirement: Sessions move between replicas

A refresh token issued by one replica SHALL be accepted by another replica. After a logout through one replica, the refresh
token SHALL be refused by every replica at once.

#### Scenario: Refresh on another replica

- **WHEN** a client logs in on replica A and refreshes on replica B
- **THEN** the refresh returns status 200 and a new token pair

#### Scenario: Revocation seen by the other replica

- **WHEN** the client logs out on replica B and then refreshes on replica A with the old refresh token
- **THEN** replica A answers 401 for that refresh

### Requirement: Both replicas show the cluster

The status of either replica SHALL list every running instance in its cluster section while the bus is configured.

#### Scenario: Status from one replica

- **WHEN** the driver reads the status from replica A after both replicas are ready
- **THEN** the cluster section lists two instances

### Requirement: The driver is deterministic and tested without a cluster

The driver SHALL NOT wait by a fixed sleep. Each deadline SHALL be computed from an injected clock. The decision logic SHALL be
covered by unit tests that need no cluster, no network and no system clock.

#### Scenario: Unit suite without a cluster

- **WHEN** `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v` runs on a machine without kind
- **THEN** the driver's decision tests run and pass without network access

#### Scenario: Deadline from the clock

- **WHEN** the injected clock passes the deadline of a wait
- **THEN** the wait returns a timeout failure with the name of the condition it was waiting for

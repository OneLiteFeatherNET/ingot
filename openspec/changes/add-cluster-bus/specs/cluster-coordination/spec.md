# Spec Delta

## Purpose

Optional, best-effort coordination between the instances of one Ingot database. Instances share invalidations, log lines and
revocations over a configured message bus, while the database stays the only source of truth for shared state.

## ADDED Requirements

### Requirement: Inert without a bus URL

When no cluster bus URL is configured, the instance SHALL behave exactly as it does without this capability. It SHALL open no
cluster connection, start no cluster thread and publish no cluster event.

#### Scenario: Default deployment is unchanged

- **WHEN** an instance starts with no cluster bus URL in any of its sources
- **THEN** no connection to a message bus is opened, and every existing endpoint, setting and response is unchanged

### Requirement: Bus URL sources and precedence

The instance SHALL read the bus URL from the system property `ingot.cluster.url`, then from `INGOT_CLUSTER_URL`, then from
`REPOSILITE_CLUSTER_URL`. The first source that defines a value SHALL be used.

#### Scenario: Legacy variable alone enables the bus

- **WHEN** only `REPOSILITE_CLUSTER_URL` is defined with a valid bus URL
- **THEN** the instance uses that URL and connects to the bus

#### Scenario: New variable takes precedence over the legacy one

- **WHEN** both `INGOT_CLUSTER_URL` and `REPOSILITE_CLUSTER_URL` are defined with different valid URLs
- **THEN** the instance uses the value of `INGOT_CLUSTER_URL`

### Requirement: Accepted bus schemes

The instance SHALL accept bus URLs with the schemes `nats`, `tls` (NATS over TLS), `redis`, `rediss` (TLS), `valkey` and
`valkeys` (TLS). Any other scheme SHALL stop the startup with a configuration error that names the accepted schemes and does
not print the URL.

#### Scenario: Unknown scheme stops startup

- **WHEN** the bus URL uses a scheme outside the accepted list
- **THEN** the instance refuses to start, and the error names the accepted schemes without echoing the URL

#### Scenario: Valkey and Redis schemes are equivalent transports

- **WHEN** the bus URL uses `valkey` or `redis`, with the same host and port
- **THEN** the instance uses the same plain publish and subscribe transport for both

### Requirement: Redis and Valkey carry events only

When the bus is Redis or Valkey, the instance SHALL use it only to publish and subscribe to cluster events. It SHALL NOT use it
to hold locks, counters, sessions, revisions or any other authoritative state, and SHALL NOT require server modules.

#### Scenario: Locks stay in the database

- **WHEN** two instances on one database and one Valkey bus perform a conditional write to the same token row
- **THEN** exactly one write succeeds, decided by the database, and the bus plays no part in the outcome

### Requirement: Database remains authoritative

Sessions, refresh families, token rows, configuration revisions and counters SHALL be held only in the database. A cluster event
SHALL only tell an instance to drop derived state or to re-read data from the database.

#### Scenario: Lost event does not change the outcome

- **WHEN** an instance never receives a cluster event for a token change
- **THEN** every request still sees the token as stored in the database, within the time bound of the affected cache

### Requirement: Event envelope

Every cluster event SHALL carry an envelope version, an event type, the identifier of the instance that published it, the cluster
identifier and a timestamp. An event SHALL NOT carry a password, a token secret, a refresh token, a session token or a setup code.

#### Scenario: Secrets are never published

- **WHEN** a token is created, rotated or deleted on an instance
- **THEN** the published event names the token but contains none of its secret material

### Requirement: Own and foreign events are ignored

An instance SHALL ignore events that it published itself and events that carry a different cluster identifier. Applying a
received event SHALL NOT publish that event again.

#### Scenario: Instance ignores its own echo

- **WHEN** an instance receives an event whose originating instance identifier is its own
- **THEN** the instance applies nothing and publishes nothing

#### Scenario: Two databases on one bus do not mix

- **WHEN** two Ingot installations with different databases use the same bus
- **THEN** each installation ignores the events of the other

### Requirement: Events are idempotent and order-independent

Applying the same event twice, or applying events out of order, SHALL leave an instance in the same state as applying them once in
order.

#### Scenario: Duplicate token change

- **WHEN** an instance receives the same token change event twice
- **THEN** the cached credentials of that token are dropped once, and the second application changes nothing

### Requirement: Invalidation propagates to other instances

When an instance changes a token, deploys or deletes a resolution-metadata file, or changes a repository's stored state, every other
instance connected to the same bus SHALL drop the derived state for that change without a restart.

#### Scenario: Disabled token stops working on every instance

- **WHEN** an administrator disables a token on instance A
- **THEN** instance B refuses the token's Basic or Bearer credentials on the next request after the event arrives

#### Scenario: Deployment clears a stale resolution entry elsewhere

- **WHEN** instance A deploys an artifact whose metadata was previously recorded as missing on instance B
- **THEN** instance B drops that entry and resolves the metadata from storage on the next request

### Requirement: Cached credentials have a hard lifetime

Cached authentication results SHALL be dropped no later than sixty seconds after they were stored, regardless of how often they are
used and regardless of whether any cluster event was delivered.

#### Scenario: Busy credential is re-checked after a minute

- **WHEN** a credential is used continuously on an instance that never received an invalidation for its token
- **THEN** the instance authenticates the credential again against the database no later than sixty seconds after the previous check

### Requirement: Resolution entries have a lifetime in cluster mode

When the bus is configured, cached resolution entries SHALL expire after a maximum age, whether or not an invalidation was delivered.
Without the bus, resolution entries keep their current lifetime.

#### Scenario: Lost invalidation is bounded

- **WHEN** an instance misses the invalidation of a missing-metadata entry and the bus is configured
- **THEN** the entry is dropped when its maximum age is reached, and the metadata resolves from storage afterwards

### Requirement: Configuration change reaches other instances

When shared configuration is saved on one instance, every other instance SHALL reload it on receipt of the change event. Without that
event, every instance SHALL still reload it within its polling interval.

#### Scenario: Saved setting applies without waiting for the poll

- **WHEN** an administrator saves shared configuration on instance A and the event reaches instance B
- **THEN** instance B reloads the configuration before its next scheduled poll

#### Scenario: Lost change event falls back to polling

- **WHEN** the change event does not reach instance B
- **THEN** instance B reloads the configuration at its next scheduled poll

### Requirement: Principal revocation takes effect on the next request

A disabled or deleted principal SHALL be refused for session tokens on the next request of every instance that shares the database,
with or without the bus.

#### Scenario: Disabled user loses an active browser session

- **WHEN** a user's principal is disabled on instance A while the user holds a session on instance B
- **THEN** instance B refuses that session on the next request

### Requirement: Session family revocation propagates

When a session family is revoked, instances SHALL refuse its session tokens as soon as the revocation event arrives. Without the event,
the session tokens SHALL be refused no later than their expiry, which is bounded by the session lifetime.

#### Scenario: Logout ends the session on every instance

- **WHEN** a user logs out on instance A and the event reaches instance B
- **THEN** instance B refuses the session token of that family on the next request

#### Scenario: Lost revocation falls back to expiry

- **WHEN** the revocation event does not reach instance B
- **THEN** instance B refuses the session token once it expires, and not later

### Requirement: Console log fan-out

When the bus is configured and console fan-out is enabled, the live log lines of every instance SHALL be shown in every console session,
each labelled with the instance that produced it. Lines SHALL be forwarded only at the visibility threshold of the producing instance.

#### Scenario: Administrator sees another replica's log

- **WHEN** an administrator is connected to a console session on instance A and instance B logs a line at its visible threshold
- **THEN** the line appears in the session on instance A, labelled with instance B

#### Scenario: Lines are not duplicated

- **WHEN** a line produced on instance A reaches instance A through the bus
- **THEN** the session on instance A shows the line once

### Requirement: Console fan-out is switchable

An operator SHALL be able to disable console log fan-out with a setting of its own, while the rest of the bus keeps working.

#### Scenario: Fan-out disabled, invalidation still works

- **WHEN** console fan-out is disabled and a bus is configured
- **THEN** no log line is published, and token invalidation still propagates

### Requirement: Storage usage figures propagate

When one instance recomputes repository storage usage, the other instances SHALL report the new figures once the event arrives, and
until then they SHALL keep reporting their last figures.

#### Scenario: Usage refresh is visible everywhere

- **WHEN** instance A finishes a storage usage refresh
- **THEN** instance B reports the same figures after receiving the event

### Requirement: Failure aggregation is optional

The failure list MAY be aggregated across instances. Aggregation SHALL be disabled by default and SHALL be enabled only by an explicit
setting.

#### Scenario: Default keeps failures per instance

- **WHEN** the bus is configured and failure aggregation is not enabled
- **THEN** each instance lists only the failures it recorded itself

### Requirement: Single runner for scheduled jobs

A scheduled job that must run once per occurrence, namely a cleanup policy run and the audit retention purge, SHALL run on exactly one
instance per occurrence. The database SHALL decide which instance runs it. The bus SHALL NOT be used to decide this.

#### Scenario: Two replicas reach the same scheduled minute

- **WHEN** two instances on one database reach the scheduled time of the same cleanup policy
- **THEN** exactly one instance starts the run, and the other records nothing for that occurrence

#### Scenario: Crash after claim skips the occurrence

- **WHEN** the instance that claimed an occurrence stops before finishing it
- **THEN** no other instance runs that occurrence again, which matches the existing behaviour of a missed scheduled run

### Requirement: Transport failure falls back to database behaviour

If the bus is unreachable, each instance SHALL keep serving requests with the database-backed behaviour, SHALL log the state change once, and
SHALL reconnect with growing, bounded delays. Requests SHALL NOT wait for the bus.

#### Scenario: Bus down at startup

- **WHEN** an instance starts while the configured bus is unreachable
- **THEN** the instance starts, serves requests, logs one warning, and connects when the bus becomes reachable

#### Scenario: Bus down during operation

- **WHEN** the bus connection drops while requests are being served
- **THEN** no request is delayed by the outage, and the instance reconnects in the background

### Requirement: Credentials stay out of logs and status

The bus URL credentials SHALL NOT appear in logs, status output or the administrator console. When credentials are sent over an
unencrypted scheme, the instance SHALL log a warning.

#### Scenario: Credentials redacted

- **WHEN** the instance logs its bus configuration
- **THEN** the username and password of the bus URL are replaced by a placeholder

#### Scenario: Plain connection with credentials

- **WHEN** the bus URL has credentials and an unencrypted scheme
- **THEN** the instance logs a warning that names the scheme and not the credentials

### Requirement: Bus state is observable

The instance SHALL report whether it is connected to the bus, when it last received an event, and how many events it published, applied
and ignored, in its status output.

#### Scenario: Status shows a disconnected bus

- **WHEN** the bus is unreachable
- **THEN** the status output reports the bus as disconnected with the time of the last received event

### Requirement: Mixed versions are tolerated

An instance SHALL ignore an event whose envelope version or event type it does not know, and SHALL count that event as ignored. An
unknown event SHALL NOT cause an error response or a restart.

#### Scenario: Newer instance publishes an unknown event

- **WHEN** an instance receives an event of a type it does not know
- **THEN** the instance ignores it, counts it as ignored, and continues without error

#### Scenario: Instance without the capability

- **WHEN** a cluster contains an instance that does not implement this capability
- **THEN** that instance receives no events and converges through its existing polling and time bounds

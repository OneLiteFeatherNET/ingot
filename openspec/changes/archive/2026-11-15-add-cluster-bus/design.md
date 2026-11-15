# Design

## Context

Ingot keeps most shared state in the database: access tokens, identity principals, refresh families, login attempts, statistics
and shared configuration. Some state lives in process memory, and with several replicas that memory diverges. See
proposal.md for the motivation and cluster-coordination/spec.md for the required behaviour.

The inventory below was re-checked against origin/main. File paths are relative to `reposilite-backend/src/main/kotlin/`.
Line numbers are left out because they drift; the class and file names are the reference.

| State | Where | Today with N replicas | Impact | Desired |
|---|---|---|---|---|
| Basic and Bearer credential cache, one minute from storage | `com/reposilite/auth/AuthenticationFacade.kt` (`CREDENTIAL_CACHE_MAX_AGE`, `expireAfterWrite`) | Drop on change happens only on the instance that changed the token. The one-minute bound is already on main | A disabled token keeps working on other pods for up to one minute | Event on change. The bound stays as the fallback |
| Session JWT and refresh families | `net/onelitefeather/ingot/identity/session/SessionService.kt`, `SessionSigner.kt`; `identity/login/LoginFacade.kt` | Refresh and logout are database writes and are immediate. The JWT is not looked up, so a logout is honoured by the other pods until `exp`. The principal is checked on every request | Logout window of up to `sessionLifetimeMinutes` (default 15) on other pods | Revocation event adds a denylist of family ids until expiry. Database stays authoritative |
| Token change listeners | `com/reposilite/token/AccessTokenFacade.kt` (`onTokenChanged`, `tokenStateChanged`); `net/onelitefeather/ingot/identity/login/OwnTokens.kt`; `identity/service/IdentityFacade.kt` | Listeners are local (`CopyOnWriteArrayList`) | Same as the credential cache | Emitted as an event, and applied on receipt through the same local path |
| Resolution cache (maven pins), off by default (`resolutionCacheMaxEntries` is 0) | `com/reposilite/maven/ResolutionCache.kt` (`internal`), `maven/Repository.kt`, `maven/application/MavenSettings.kt`; invalidation in `maven/ResolutionProvider.kt` and `maven/MetadataService.kt`; purge in `maven/infrastructure/CacheCommand.kt` | Each pod keeps its own pins. Invalidation on deploy reaches only the pod that handled the deploy. There is no time bound at all | A `MissingMetadata` pin on pod B can hide a deployed artifact from pod B indefinitely, until eviction or purge | Invalidation event. Maximum age in cluster mode only |
| Shared configuration, 10 s poll | `com/reposilite/configuration/shared/application/SharedConfigurationPlugin.kt` (`scheduleWithFixedDelay`); `configuration/shared/infrastructure/RemoteSharedConfigurationProvider.kt` | Correct. Each pod compares the database update time with its own last load | Up to ten seconds of lag, no correctness issue | Change event triggers an immediate poll. The poll stays as fallback |
| Statistics buffer, flushed every 10 s | `com/reposilite/statistics/StatisticsFacade.kt`; `statistics/application/StatisticsPlugin.kt` (no dispose handler) | Upserts add up correctly across pods | Loss of up to ten seconds per pod on shutdown (HA stage 1.3) | No bus. Handled by HA stage 1.3 |
| Live console log | `com/reposilite/ReposiliteJournalist.kt` (`subscribe`); `console/infrastructure/ConsoleSseHandler.kt` with `ConsoleSseRegistry.kt`; `console/infrastructure/ConsoleWebSocketHandler.kt` | Subscribers see only their own pod. The backlog on connect is the pod's `CachedLogger` | Operators see one replica's logs | Log lines fan out through the bus, labelled by origin |
| Storage usage snapshot | `com/reposilite/maven/StorageUsageService.kt`; `maven/infrastructure/MavenStorageEndpoints.kt` | Each pod keeps its own figures and walks storage on its first request | Dashboard figures differ by pod, and every pod walks storage | Snapshot event, applied on receipt |
| Failure records | `com/reposilite/status/FailureFacade.kt`; `status/application/FailureComponents.kt` | Per pod in memory | A failure is visible only on the pod that recorded it | Optional aggregation, off by default |
| Scheduled cleanup policies, 30 s tick | `com/reposilite/cleanup/CleanupScheduler.kt`; `cleanup/application/CleanupPlugin.kt`; `cleanup/CleanupService.kt` | Claimed per policy and UTC day through `ScheduledJobClaims` on main | None on main. One run per day across instances | No change. Not a bus concern |
| Audit retention purge, every 24 h | `com/reposilite/audit/application/AuditPlugin.kt`; `audit/AuditFacade.kt` (claim `audit-purge:<UTC date>`) | Claimed per UTC day on main | None on main | No change |
| Failed-login folding | `com/reposilite/audit/AuditFacade.kt` | One open row per pod per minute and key | Brute-force counts split by pod, log size slightly higher | No change, documented |
| Mirror single-flight | `com/reposilite/maven/MirrorService.kt` | Per pod | A cold start fetches once per pod. HA design: deliberately not done | No change |
| Mirror ledger throttle | `com/reposilite/maven/MirrorCache.kt`; production store `maven/infrastructure/SqlMirrorCacheLedger.kt` | Throttle per pod, writes conditional in the database. `InMemoryMirrorCacheLedger` is test-only | Last-access written up to once per pod per interval | No change |
| Token usage throttle | `net/onelitefeather/ingot/identity/storage/TokenUsageTracker.kt` | Database conditional update bounds writes | None | No change |
| Password login limiter | `net/onelitefeather/ingot/identity/login/LoginAttemptLimiter.kt` | Database, versioned writes | None | No change |
| Basic and token brute-force counter, off by default | `com/reposilite/auth/AuthenticationFacade.kt` | Allowance multiplied by the number of pods | Weaker protection when enabled | Out of scope (HA stage 3.3) |
| Metadata and file locks | `com/reposilite/storage/filesystem/FileSystemStorageProvider.kt`; S3 has none | Locks end at the process boundary | Lost metadata updates (HA stage 1.2) | Out of scope, database row lock |
| S3 HEAD metadata cache, opt-in (Caffeine) | `com/reposilite/storage/s3/S3StorageProviderFactory.kt` | Time-bounded per pod | Acceptable, already bounded by configuration | No change |
| Status snapshots, in memory | `com/reposilite/status/StatusFacade.kt` | Graphs show the pod that is asked | Dashboard status and graphs show only the pod that answered, so operators see one replica | Status snapshot event; the dashboard shows a cluster summary plus one entry per instance |
| Console connection registries | `ConsoleSseRegistry.kt` (concurrent, on main); `ConsoleWebSocketHandler.kt` (synchronised `WeakHashMap`) | Guarded on main | None | No change |

## Goals / Non-Goals

**Goals:**

- Make invalidations, revocations, configuration hints and log lines reach every instance quickly when the operator has configured a bus.
- Keep the database the only authority for everything that must be correct.
- Make a bus outage, a lost event and a mixed-version cluster degrade to behaviour that is already correct, only slower.
- Keep single-runner jobs single, through the existing claims, and keep them independent of the bus.
- Change nothing for a deployment without a bus URL.

**Non-Goals:**

- Using the bus for locks, counters, revisions, sessions or any decision that needs a result.
- Delivery guarantees, replay, or ordering across instances. Replay would need JetStream, which the HA design reserves for statistics.
- Service discovery. Kubernetes is not special: the bus is a URL.
- Search over the bus. Search stays in the database.
- Fixing the metadata locks, schema initialisation or statistics flush on shutdown. Those are HA stage 1 work.

## Decisions

### 1. The bus notifies; the database decides

Every event tells an instance to drop derived state or to re-read data from the database. No event carries authoritative data
that another instance must trust. Losing an event costs convergence time, never correctness.

Alternatives considered:

- **Redis-style locks or NATS JetStream KV for leases.** The HA design rejects Redis locks because an expired lock cannot fence a
  paused process. JetStream KV would add a consensus component to the write path for a guarantee the database already gives.
- **Polling only.** Correct, but console fan-out and immediate revocation cannot be met with polling.

### 2. Remote events never go through the emitting path

Core code emits an event after a database write has committed. The cluster plugin publishes only events that originate locally.
When the plugin applies a received event, it calls the internal invalidation functions directly (for example the credential
cache drop, `ResolutionCache.invalidate`, the shared configuration reload). It does not call the facade methods that emit the
event. Echo loops are therefore impossible by construction, and no thread-local flag is needed. Tests inject the bus and assert
that a received event publishes nothing.

Events are published after commit. A change that is announced before its transaction commits would make other instances read old
data, and only the polling fallback would repair it.

### 3. Event contract

New event classes live in the plugin API package, marked experimental in their documentation until one release has shipped:
`ResolutionInvalidatedEvent`, `TokenStateChangedEvent`, `SessionFamilyRevokedEvent`, `SharedConfigurationChangedEvent` and
`StorageUsageRefreshedEvent`. Dispatch in `Extensions.emitEvent` is by exact class (`events[event.javaClass]`), so each event is
a concrete class.

Console fan-out needs no core event. The cluster plugin uses the existing `ReposiliteJournalist.subscribe`, and it gets one new
entry point to deliver a remote line to subscribers without writing it to the local log, so that no line is written twice or
published back.

Wire format, one JSON object per message:

```json
{"v":1,"type":"token.changed","cluster":"<uuid>","origin":"<instance>","at":"<ISO-8601 instant>","payload":{"name":"ci-bot"}}
```

- `v` is the envelope version. `type` is the event type. `cluster` is the cluster identifier. `origin` is the instance identifier.
  `at` comes from the injected clock. `payload` is specific to the type.
- A receiver ignores a message whose `v` or `type` it does not know, and counts it. It ignores a message whose `cluster` differs
  from its own, and a message whose `origin` is its own.
- Payloads never carry secrets: token names but not secrets, family identifiers but no refresh tokens or session tokens. A test
  scans every serialised payload for secret-shaped fields.

Event types in version 1: `resolution.invalidated` (repository and path), `token.changed` (token name), `session.revoked`
(family identifier), `config.changed` (revision, used as a hint only), `storage.usage` (per-repository figures), `console.line`
(channel, message, timestamp), and `failure.recorded` when aggregation is enabled.

### 4. Cluster identity from the database

A random cluster identifier is stored once per database, in the configuration table, through
`ConfigurationFacade.findOrCreateConfiguration`, the same way `jwt-signing-key` and `entity-tag-key` are created. Subjects and
channels include it (`ingot.<cluster>.events` for NATS, `ingot:<cluster>:events` for Redis and Valkey), so that several Ingot
installations can share one broker without mixing events. The check in the receiver is the real guard. Subject scoping only makes
broker access rules practical.

### 5. Transports behind one interface, selected by URL scheme

The cluster package defines a transport interface with three operations: publish a serialised envelope without blocking, start
receiving with a listener, and close. A factory per scheme creates the transport. A transport class is loaded only when its
scheme is configured, so an instance without a URL never touches a client library.

- **NATS** (`nats`, `tls`): the `jnats` client, Apache 2.0. Its dependency set is checked at implementation time.
- **Redis and Valkey** (`redis`, `rediss`, `valkey`, `valkeys`): the Jedis client, MIT. Jedis runs a blocking subscription on
  one dedicated thread, which matches the publisher and listener model here. Lettuce was not chosen because its asynchronous
  model and larger dependency set add more than one subscription needs. Valkey-GLIDE was not chosen because it ships a native
  library per platform, which would enlarge every image.
- **Licensing:** Valkey is BSD-3-Clause. Redis 8 is distributed under RSALv2, SSPLv1 or AGPLv3. The change uses only the plain
  publish and subscribe commands, so either server works with the same code. The documentation names Valkey first. The server
  terms are re-checked before the release.
- **Database** (`database:`, `database://`): no client library. It uses the connection pool the instance already has, and its
  table and polling are described in decision 15.
- Client versions are pinned at implementation time, at least two weeks old, as the repository's Renovate policy requires.

Redis and Valkey are accepted as transports only. This supersedes the HA design's statement that Redis has no remaining niche
(`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`). The HA design's reason for rejecting Redis locks still holds, and
it is the reason the bus never holds locks.

### 6. Single runner through the existing claims

Scheduled jobs that must run once per occurrence already claim a key before they start, through `ScheduledJobClaims` and
`SqlScheduledJobClaims` on origin/main:

- cleanup policies: `cleanup:<policy>:<UTC date>`, claimed in `CleanupScheduler`;
- the audit retention purge: `audit-purge:<UTC date>`, claimed in `AuditFacade`.

Both keys come from `utcDayKey`. The claim is an insert that ignores an existing row in the `scheduled_job_claim` table. Rows older
than seven days (`CLAIM_RETENTION`) are deleted by the instance that makes the next claim. A policy's scheduled minute is read in
the clock's zone, and its claim is per UTC day, so one policy still runs once per day across instances.

This change keeps that mechanism unchanged and does not use the bus for it. Its tests are already on main (`CleanupSchedulerClaimTest`,
`AuditPurgeClaimTest`, `SqlScheduledJobClaimsTest`).

A daily claim does not need a lease with expiry. An instance that crashes after claiming skips that day's run, which is how a
missed scheduled run already behaves. Advisory locks were not chosen because they are not portable to SQLite or H2. A job that runs
for a long time would need a lease with a fencing value. No such job is in scope here.

### 7. Hard lifetime for cached credentials (already on main)

`AuthenticationFacade` uses `expireAfterWrite` with `CREDENTIAL_CACHE_MAX_AGE` of one minute, and `AuthenticationCacheTest`
covers a busy credential that is checked again after the bound. The cluster change depends on this bound, because the credential
cache has no polling fallback. No change is needed here.

### 8. Resolution entries get a maximum age in cluster mode

A new maven setting, `resolutionCacheClusterMaxAgeSeconds`, bounds the lifetime of every resolution entry. It lives in `MavenSettings`
beside `resolutionCacheMaxEntries`, in the shared configuration domain `maven`. Its stored default is `0`, which means "use the
cluster default": 300 seconds when a bus is configured, and no maximum age without one, so today's lifetime is unchanged. A
positive value overrides the default in either mode.

The name carries `Cluster` on purpose. Upstream Reposilite adds a per-repository `resolutionCacheMissTtl` (GH-2772) that bounds
only cached misses. The two settings are independent: when both apply, an entry expires at the earlier of the two bounds. Porting
GH-2772 stays a separate upstream pick and does not change this setting.

### 9. Revocation keeps the session bound as its fallback

`SessionService` keeps an in-memory set of revoked family identifiers, each kept until the session expiry it would have had. It is
filled by the local logout and revoke-all paths and by received `session.revoked` events, and it is consulted in `authenticate`
after the signature and expiry checks have passed. Its size is bounded by the number of live sessions. The database check on the
principal is unchanged.

An instance restarted during a session loses its denylist. A revoked session then works on that instance until its expiry. That is
the bound the identity model already gives, so the restart cannot widen it.

### 10. Console fan-out never blocks logging

Each instance publishes `console.line` messages from a bounded outbound queue that a separate thread drains. When the queue is full
the oldest entry is dropped and a counter is incremented. Logging never waits for the bus. Received lines are delivered to local
subscribers only, labelled with the origin instance, and are not written to the local log.

The backlog a session receives on connection stays local to its instance. Fan-out covers live lines only. That is recorded as an open
question.

### 11. Aggregated instance status

Each instance publishes a `status.snapshot` event each time `StatusFacade` records one of its in-memory snapshots, so the event follows the same schedule as the recording. The event carries the instance identifier, the version, the uptime start, used and maximum memory, used and maximum threads, the failure count, the latest snapshot sample and the interval at which the instance records snapshots. The interval is carried so that receivers can judge staleness. The event carries no secrets and no hostname beyond the instance identifier.

Each receiver keeps the last snapshot per instance. An instance whose last snapshot is older than one interval is shown as stale. An instance whose last snapshot is older than three intervals is dropped. A stale entry stays visible until it is dropped, so an operator sees a replica that stopped reporting rather than a replica that silently vanished.

The cluster summary follows fixed rules. Memory used and maximum are summed, and so are threads used and maximum. The failure count is summed as a count only. The failure list stays per instance unless failure aggregation is switched on. Uptime shows the shortest value, which belongs to the most recent restart. Version shows the single version when all instances agree, and otherwise shows "mixed" together with the list of versions. Health is the health of the worst instance.

The memory and thread graphs show one series per instance rather than a sum. Each pod takes its samples at a different moment, so a sum would draw a curve that no instance ever had.

Without a bus nothing changes. The existing status API response keeps its fields and their meaning. The cluster view is an additional field or endpoint, never a change to an existing field, so a client that reads only the existing fields gets the same response as before.

### 12. Connection management and status

The transport reconnects with exponential backoff from one second to sixty seconds, with jitter. Each delay comes from a pure policy
function that takes the clock and the attempt number, so tests cover it without sleeping. The state change is logged once per
transition. The outbound queue is bounded, so an outage cannot grow memory.

Status output reports the transport (`nats`, `redis`, `valkey` or `database`), the state (connected, reconnecting, disabled), the
last received event time, and the counts of published, applied and ignored events. The URL is shown with its credentials replaced.

### 13. Configuration and credentials

Bus settings are instance settings, so they belong to the local configuration rather than to the shared configuration:

| Setting | Source |
|---|---|
| Bus URL | `ingot.cluster.url`, then `INGOT_CLUSTER_URL`, then `REPOSILITE_CLUSTER_URL` |
| Database transport URL | `INGOT_CLUSTER_URL=database:` (or `database://`), with no host and no credentials. Uses the instance's own database. Refused on SQLite and H2 (decision 15) |
| Poll interval, database transport only | `INGOT_CLUSTER_POLL_INTERVAL_MS`, default 1000, raised to 200 when lower |
| Password, when the URL has none | `INGOT_CLUSTER_PASSWORD`, then `REPOSILITE_CLUSTER_PASSWORD`, so that the URL can live in a ConfigMap and the password in a Secret |
| NATS credentials file, NATS schemes only | `ingot.cluster.credentials`, then `INGOT_CLUSTER_CREDENTIALS`: path to a `.creds` file (user JWT plus NKEY seed) for brokers in operator (decentralised JWT) mode, which accept no user and password. Mounted from a Secret. Ignored with a warning for Redis and Valkey |
| Instance identifier | `INGOT_CLUSTER_INSTANCE_ID`, default the host name, which is the pod name under Kubernetes |
| Console fan-out | `INGOT_CLUSTER_CONSOLE`, default on when a bus is configured, and off on the database transport (decision 15) |
| Failure aggregation | `INGOT_CLUSTER_FAILURES`, default off |

These variables are read by the cluster plugin's own factory, not by the local configuration factory, so the `INGOT_LOCAL_` namespace
stays unchanged. The URL is parsed once. Credentials are removed from it before anything is logged. A warning is logged when
credentials are sent over an unencrypted scheme.

### 14. Plugin layout

A new `cluster` plugin lives in package `net.onelitefeather.ingot.cluster` in `reposilite-backend`. It must be in the same module as
`ResolutionCache`, which is `internal`. It declares dependencies on the plugins whose facades it observes, using the names in their
`@Plugin` annotations: `access-token`, `authentication`, `configuration`, `shared-configuration`, `maven`, `console`, `cleanup`,
`audit` and `local-configuration`. It is registered in `META-INF/services/com.reposilite.plugin.api.ReposilitePlugin`, the file that
lists the other plugins, including `net.onelitefeather.ingot.identity.login.application.LoginPlugin`.

The plugin registers its event listeners and its subscriptions during initialisation. Without a URL, it registers nothing that
does work at runtime.

### 15. Database transport

The `database:` scheme (also `database://`) uses the database the instance already has. It needs no host, port or credentials,
and it uses the Exposed connection pool that every repository uses. It serves deployments that run several instances on MariaDB,
MySQL or PostgreSQL and do not want to operate a message broker. A file database cannot be shared by instances, so on SQLite or
H2 the scheme is refused with one error, the bus stays off and the instance keeps running. A `database:` URL with a host or with
credentials is refused the same way, and the error does not echo the URL.

Storage: one table, `cluster_event`, is created with `SchemaUtils.create` in the transport's initialisation, as the repositories
create `scheduled_job_claim` and the other tables. Its columns are an auto-increment `id` (primary key), `cluster` (the cluster
identifier), `origin` (the publishing instance), `created_at` (epoch milliseconds from the publisher's injected clock) and
`payload` (text, the serialised envelope). Publishing is an insert, made on the bus's outbound thread of decision 10 and 12, so
neither logging nor a request waits for it.

Polling: each instance reads `cluster_event` every poll interval, `INGOT_CLUSTER_POLL_INTERVAL_MS` (default 1000, raised to 200 with
one warning when lower). It reads rows with an id above its watermark and skips rows from its own origin. The watermark starts at
the largest id present at connect, so stored history is never replayed.

Ordering: auto-increment values are assigned at insert, not at commit. A row with a lower id can therefore become visible after a
row with a higher id, and a watermark alone would skip it. Each poll also re-reads the last 100 ids below the watermark and delivers
only the ids not yet delivered, which are kept in a bounded set for that window. A transaction that stays open while 100 newer rows
commit is missed, and the time bounds of the cluster-coordination spec cover that case. Ordering
relies only on the id, never on time, so clock skew between instances changes when a row is removed, not the order in which rows
are read.

Payload limit: the envelope stored in `payload` is limited to 16 KiB. This change sets that limit, because no limit was defined
before. A larger envelope is not inserted, is counted as dropped and is logged once.

Retention: rows older than five minutes are deleted. The window is a fixed value in this change, not a setting. At most one instance
per window runs the deletion. Each window is a `ScheduledJobClaims` claim with the key `cluster-event-prune:<window index>`, where the
window index is the epoch milliseconds of the claiming instance divided by the window length, and the instance whose claim succeeds
deletes the rows older than the window. The claim was chosen over an idempotent delete by every instance because concurrent range
deletes on one index take overlapping gap locks in InnoDB and can deadlock. Reusing the claim adds no mechanism. A window whose claimant
stops early is caught up by the next window, since the delete removes everything older than the cut-off. Instances with skewed clocks
can compute different window indexes near a boundary and both delete; the delete is idempotent, so the result is the same.

Console: fan-out is off by default on this transport, because every log line would become a row. `INGOT_CLUSTER_CONSOLE=true`
enables it explicitly. All other events stay on.

Expectations: an event reaches the other instances within one poll interval after its insert commits. Database load per instance is
one indexed range query per poll, one insert per published event and one claim per window. The table size is bounded by the event rate
multiplied by the retention window.

Failure: a failing poll or insert counts as a broker disconnect. It uses the same reconnect backoff, the same bounded outbound queue
and the same fallback to database-authoritative behaviour. The status output shows the transport as `database`.

Alternatives considered:

- **PostgreSQL LISTEN/NOTIFY.** It wakes readers without a poll, but it exists only on PostgreSQL and needs a dedicated connection
  outside the pool. It stays a possible later optimisation for PostgreSQL. Polling remains the portable path for MariaDB, MySQL and
  PostgreSQL alike.
- **Peer-to-peer HTTP, or JGroups over a Kubernetes headless service.** Both need discovery and open ports between pods. The bus is
  configured by a URL only, and discovery is a non-goal of this change.
- **Database polling (chosen).** Every multi-instance deployment already shares the database. The transport needs no extra
  infrastructure and no client library, and one code path serves all supported databases.

## Risks / Trade-offs

- **Events lost during an outage.** Mitigation: each event has a database fallback with a bound: the poll for configuration, the
  maximum age for resolution entries, the credential lifetime of one minute, and the session expiry for revocation. A test covers each
  bound with the event dropped.
- **Duplicate or reordered events.** Mitigation: every apply is idempotent. Dropping a cache entry twice or late is harmless.
- **Console volume.** A busy instance can overrun a slow bus. Mitigation: bounded queue, drop-oldest, a counter, and the switch.
- **Log disclosure to bus readers.** Console lines contain user names and addresses. Mitigation: TLS schemes, credentials, the cluster
  subject, a documented recommendation for broker access rules, and the separate switch.
- **Shared broker between installations.** The receiver filters by cluster identifier. Broker access rules are still needed if the
  installations do not trust each other.
- **Denylist lost on restart.** Mitigation: the bound stays at the session expiry, which is today's bound.
- **Image size.** Two client libraries add to every image, even when inert. Mitigation: classes load only when selected. The size
  change is measured in the image CI job and reported in the release notes.
- **Public event types become a compatibility promise.** Mitigation: experimental marking until one release has shipped, and
  additive changes only.
- **Blocking thread per subscription.** Mitigation: one subscription per instance, a dedicated thread, and the reconnect loop above.
- **Database load and table growth (database transport).** Each poll is one range query on the primary key, and every event is a
  row. Mitigation: the poll interval setting, console fan-out off by default on this transport, the payload limit and the retention
  window of decision 15.

## Migration Plan

1. Land this change. The plugin is inert without a URL, so the default deployment does not change. The container image CI job runs
   the drop-in checks from CLAUDE.md, with no URL set.
2. Operators opt in by setting `INGOT_CLUSTER_URL` (and the password variable if needed) on a staging cluster. The status output
   confirms the connection. Then the operator runs one token disable and one console session across two pods to see the effect.
3. Rollback is removing the URL and restarting. The cluster identifier row is additive and harmless to an older version, which
   ignores it. The resolution maximum age returns to the unbounded behaviour of today.

## Testing

Every test follows F.I.R.S.T. Time comes from an injected clock. Random identifiers are injected. Waits use latches with a bounded
timeout, and no test sleeps.

- **Unit tests** (no network, no database, no sleeps): envelope round trip, unknown version and unknown type ignored and counted,
  secret-shaped fields absent from every payload, URL parsing and redaction, scheme refusal, backoff sequence from the policy
  function, the bounded outbound queue with its drop counter, echo suppression, cluster identifier filtering, and the revocation
  denylist bound. An in-memory transport connects two in-process instances. The claim logic is not retested here; it is covered on main.
- **Integration tests against NATS and Valkey**: publish and receive round trip, reconnection after the broker restarts, and no
  request delayed by a broker that is down. Both use the existing per-JVM singleton `SharedContainers`
  (`reposilite-backend/src/integration/kotlin/com/reposilite/SharedContainers.kt`), to which this change adds lazy `nats` and
  `valkey` containers beside `mariaDb`, `postgres` and `floci`.
- **Two-instance tests** against one MariaDB or PostgreSQL database and one bus: a token disabled on one instance is refused on the
  other, a logout ends the session on the other, a saved configuration applies before the next poll, and console lines from one
  instance appear in a session on the other, each once.
- **Database transport**: integration tests against the MariaDB and PostgreSQL containers in `SharedContainers`, with two instances
  on their own database names. They cover a publish and receive round trip within a bounded latch, no replay of stored rows on
  connect, removal after the retention window, and the refusal on SQLite and H2 with the instance still serving requests. Unit
  tests cover the poll interval clamp, the re-read of late ids, the retention cut-off and the console default.
- **Bound tests**: each fallback bound is tested with its event dropped, using the injected clock and the scheduler the plugin
  receives, not wall time.

## Open Questions

- Should a console session on connection receive the backlog of the other instances as well? It is deferrable because it changes
  only what a new session sees, and the event contract already allows it.
- Where should failure aggregation be stored if it is enabled? The default is off, so the storage choice can wait for the first
  request for it.

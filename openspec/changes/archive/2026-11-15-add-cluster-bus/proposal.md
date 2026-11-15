# Proposal

## Why

Ingot runs as one instance by default. The Kubernetes HA design (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`)
plans several replicas that share one database and one bucket. The database already gives correct results for shared data:
sessions, refresh families, token rows, revisions and counters. What several replicas lack is prompt agreement on state that
each process holds in memory. A token disabled on one pod keeps working on another until that pod's credential cache entry
expires, which is at most one minute after it was stored. A resolution pin written by one pod is never dropped by the others,
and it has no time bound. The administrator console shows only the pod the connection landed on.

This change adds a best-effort fan-out for those invalidations and log lines. The fan-out is an optional bus with a
pluggable transport. Without a configured URL it does nothing.

## What Changes

- Add an optional cluster bus, enabled only by a URL: `INGOT_CLUSTER_URL`, with the system property `ingot.cluster.url`
  and the legacy `REPOSILITE_CLUSTER_URL` as fallback. Without the URL the new `cluster` plugin starts no thread, opens no
  connection and emits no event. Behaviour is identical to today.
- Accepted schemes: `nats://` and `tls://` for NATS; `redis://`, `rediss://`, `valkey://` and `valkeys://` for Redis and
  Valkey; `database:` (also `database://`) for the database transport. Redis and Valkey are transports only, over plain
  publish and subscribe. They never hold locks, counters or revisions, and the change needs no server module.
- A database transport for clusters without a message broker. Every multi-instance deployment already shares one MariaDB,
  MySQL or PostgreSQL database, and the transport uses that connection. Instances insert their events as rows of one new table,
  `cluster_event`, and poll it every `INGOT_CLUSTER_POLL_INTERVAL_MS` (default 1000 ms). It needs no broker and no new
  dependency. On SQLite and H2 the scheme is refused and the bus stays off, because a file database cannot be shared by instances.
- Best-effort events for: resolution cache invalidation, shared configuration change hints, token state changes that drop
  cached credentials, session family revocation, console log fan-out and storage usage snapshots. Failure aggregation is
  optional and off by default.
- The database stays authoritative. A lost event delays convergence until the existing polling (shared configuration,
  ten seconds) or a time bound takes over. The resolution cache gains a maximum age, which applies only when the bus is
  configured.
- Aggregated instance status: with a bus configured, the dashboard status cards and the memory and thread graphs show a
  cluster summary and one entry or series per instance, instead of only the instance that answered. The status output gains
  a cluster view, and its existing fields keep their meaning. Without a bus nothing changes.
- Single-runner jobs (cleanup policy runs and the audit retention purge) keep using the existing `ScheduledJobClaims` on
  origin/main. The bus does not take part in those claims, and this change adds no claim table.
- Add new core events on the existing extension mechanism for the state the cluster plugin must observe. Existing
  `AccessTokenFacade.onTokenChanged` listeners keep working.
- Supersede the HA design's statement that Redis has no remaining niche. Valkey or Redis is accepted as a pub/sub transport
  only. Locks stay in the database, as the HA design requires.
- Add an operator guide `docs/gitbook/how-to/configure-cluster-bus.md`, list it in `docs/gitbook/SUMMARY.md`, and link it
  from `docs/gitbook/how-to/run-on-kubernetes.md` and `docs/gitbook/explanation/multi-replica-operation.md`. The new
  variables are added to `docs/gitbook/reference/environment-variables.md`, and the new maven setting to
  `docs/gitbook/reference/settings-domains.md`.
- **BREAKING**: none. No existing setting, environment variable, volume, path, port or manifest changes meaning.

## Already on origin/main

The first draft of this proposal listed the following as work. They are already on origin/main and are not repeated as tasks:

- The credential cache bound. `AuthenticationFacade` uses `expireAfterWrite` with `CREDENTIAL_CACHE_MAX_AGE` of one minute,
  and `AuthenticationCacheTest` covers a busy credential. This change depends on that bound.
- Single-runner claims for cleanup policies and the audit retention purge, through `ScheduledJobClaims`,
  `SqlScheduledJobClaims` and the `scheduled_job_claim` table, with their tests.
- Thread-safe console connection registries. The SSE connections use `ConsoleSseRegistry`, and the WebSocket map is
  synchronised.

The local branch `fix/replica-safety` carries commits for the same three items. They are not ancestors of origin/main, and
its code is not needed, so it must not be re-applied.

## Capabilities

### New Capabilities

- `cluster-coordination`: optional best-effort event fan-out between the instances of one Ingot database. It covers the
  transports, the inert default, the event contract, the fallbacks and mixed-version behaviour. Single-runner job claims are
  already specified by the existing code and are referenced, not redefined.

### Modified Capabilities

- None. `openspec/specs/` holds no capability yet, so no existing requirement changes.

## Out of scope

- Search. The search index stays in the database, as described in `docs/superpowers/specs/2026-10-16-artifact-search.md`,
  which refines ADR-INGT-0002 (kept in Outline, not in this repository). An external search engine can later sit behind the
  same search interface as a plugin. It is not part of this change.
- Locks, counters and revisions over the bus. Those stay in the database.
- HA stage 1 (schema initialisation lock, metadata row locks, statistics flush on shutdown) and HA stage 2 (readiness,
  graceful shutdown, chart). These are prerequisites in the HA plan, not part of this change.
- The brute-force counter for Basic and token authentication (HA stage 3.3). The password-login limiter is already
  database-backed.
- Distributed single-flight for mirror fetches, a shared artifact byte cache, and splitting authentication into a service.
  The HA design rejects each of these.
- Kubernetes discovery. The bus is configured by a URL only.
- Archiving this change. The archive is a follow-up pull request, `docs(openspec): archive add-cluster-bus`, opened only when
  the maintainer asks for it.

## Commit and pull request

- Type and scope the change ships under: `feat(cluster)`.
- Pull request title: `feat(cluster): share cache and revocation events across instances`.
- Implementation commits follow one type per commit (`test`, `feat`, `refactor`, `docs`, `ci`). The pull request is
  squash-merged, so the title is the commit that lands on main.

## Impact

- Code: a new `net.onelitefeather.ingot.cluster` package in `reposilite-backend`, which must sit in the same module because
  `ResolutionCache` is `internal`. Small hooks in `auth`, `maven`, `token`, `console`, `configuration`, `cleanup` and `audit`
  under `com.reposilite.*`, and in `identity` under `net.onelitefeather.ingot.identity`. Additive event types in
  `com.reposilite.plugin.api`.
- Dashboard: `InstanceStatusPanel.vue` and `ResourceTrends.vue` in `reposilite-frontend` for the cluster summary, the per-instance
  list and the per-instance graph series. The status snapshot code in `com.reposilite.status` gets a publish hook and a cluster view.
- Dependencies: the NATS client (`io.nats:jnats`, Apache 2.0) and Jedis (MIT) in `reposilite-backend`. A transport class loads
  only when its scheme is configured. The database transport adds no dependency.
- Image: additional jars for the two clients and their pool. Paths, port, volume, entrypoint and environment variables do not change.
- Database: one row in the existing configuration table holds the cluster identifier, created through
  `ConfigurationFacade.findOrCreateConfiguration`, as `jwt-signing-key` and `entity-tag-key` are. One table, `cluster_event`,
  is added for the database transport only. It is created when that transport is selected, and its rows are pruned through the
  existing `ScheduledJobClaims`, which gets no new table.
- Docs: one new how-to guide, and the pages named under What Changes.
- Tests: unit tests with an in-memory transport, and integration tests against NATS and Valkey containers added to the
  existing per-JVM singleton `SharedContainers` (`reposilite-backend/src/integration/kotlin/com/reposilite/SharedContainers.kt`),
  beside its MariaDB, PostgreSQL and Floci containers. The database transport adds integration tests against the MariaDB and
  PostgreSQL containers of the same object, with no broker container.

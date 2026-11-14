---
description: States which Ingot topology is supported when several replicas share one database, what is not supported, how a rolling upgrade is ordered, how each failure looks to a client, and which scale figures exist. Every claim names the test or CI job that backs it, or says that it is not tested.
---

# High availability support

An operator who runs more than one replica asks three questions: which setup is supported, what is not, and how big can it get. This page answers them for the 1.x line. Each statement names the test that backs it, so a reviewer who removes a test can see which statement loses its evidence.

"Supported" means three things on this page:

* the topology runs in the project's CI on every pull request,
* a failure in that topology is treated as a bug,
* the default configuration needs no workaround.

The page makes no promise about response times, support channels or commercial terms. Those are outside it.

## How to read the evidence

Each row has one state:

| State | Meaning |
| --- | --- |
| tested on every pull request | A test named in the row runs in a CI job that runs on every pull request. |
| partly tested | Some tests named in the row run in a CI job on every pull request. The rest of the statement has no test, and the row says which part it is. |
| not tested | No test or job asserts the statement. It may still be true, but this page does not promise it. |
| not supported | The project makes no promise for this setup. No test is named, because a missing promise has no test. |

The jobs that carry the evidence, all of which run on pull requests:

* **HA smoke test** (`ha-smoke` job of `.github/workflows/ci.yml`, driver `charts/ingot/tests/ha_smoke.py`): two replicas on kind, with MariaDB, an S3 endpoint (Floci) and NATS.
* **Container images** (`./gradlew :reposilite-backend:containerTest`): the built image, the bus between two replicas and the comparison with the previous release.
* **Coverage** (`./gradlew :reposilite-backend:testCoverage`): the unit suite and the integration suite. The integration suite runs on SQLite and MariaDB, and the tests that name PostgreSQL also run on PostgreSQL.
* **Helm chart** (unit tests in `charts/ingot/tests`).

## Supported topology

| Part of the topology | Evidence | State |
| --- | --- | --- |
| N replicas of one release line behind one Service | `ha_smoke.py` checks `check_rolling_restart`, `check_lease_takeover`, `check_concurrent_deploys`, `check_sessions` and `check_cluster_view`, run on two replicas of the same image | tested on every pull request |
| MariaDB as the shared database | the HA smoke test uses MariaDB; `MetadataLeaseIntegrationTest` and `LeaseSchemaLockTakeoverIntegrationTest` run two instances on one MariaDB database | tested on every pull request |
| PostgreSQL as the shared database, one instance | a set of integration tests runs on PostgreSQL through `PostgresSpecificationJunitExtension` (#121) | tested on every pull request |
| PostgreSQL with several replicas | `ConcurrentSchemaInitialisationIntegrationTest` (PostgreSQL case) starts replicas together on one schema, `TwoInstanceClusterIntegrationTest` (`startPair(postgres = true)`) runs two instances on one PostgreSQL database, and `DatabaseClusterTransportIntegrationTest` (PostgreSQL case) covers the transport. `PostgresAdvisorySchemaLock` has no test of its own. The two-replica smoke test and the container suite have no PostgreSQL leg yet | partly tested |
| S3 storage shared by all replicas | the HA smoke test stores its repositories on the S3 endpoint Floci, not on AWS | tested on every pull request |
| A filesystem volume that several replicas mount (`ReadWriteMany`) | no test mounts one volume on two replicas | not tested |
| Optional NATS cluster bus | `ClusterBusImageTest` "two replicas see each other through the bus"; the HA smoke test runs NATS | tested on every pull request |
| Replicas of two adjacent releases on one database | `MixedVersionClusterTest`, see [the rolling upgrade rule](#rolling-upgrade-rule) | tested on every pull request, once `test-mixed-version-cluster` has merged |

## Not supported

These setups have no promise. No test covers them, and the page does not claim that they work.

* SQLite or H2 with more than one replica. SQLite is one file on one machine.
* MySQL. It is experimental, see [Multi-replica operation](multi-replica-operation.md).
* Several regions, or several database clusters that replicate between each other.
* A read and write split between replicas.
* Active-active replication between two sites.
* Galera as a tested target.
* A filesystem volume that the replicas do not share.
* Replicas of release lines that are not adjacent. The mixed-version test covers the previous release and the current build only.
* Starting replicas of both releases at the same moment. See the order below.

## Rolling upgrade rule

In this section N is the previous release and N+1 is the release that carries the change. The rule says what N can still work with while N+1 runs on the same database. It is the rule of `test-mixed-version-cluster`, with the limits that its run found.

The tests named in this section are in the open pull request for `test-mixed-version-cluster`. They count as evidence only once that pull request has merged.

| Layer | Allowed inside N and N+1 | Not allowed | Evidence |
| --- | --- | --- | --- |
| Schema | A new table, a new nullable column, a new column with a default | Dropping, renaming or changing the type of a column, and a new `NOT NULL` column without a default in a table that exists | `columns the current build adds stay writable by the previous release` |
| Data | A migration that copies rows and leaves the source in place | A migration that deletes what N still reads | not tested |
| Bus | A new event type, a new payload field | A new envelope version, or removing a payload field that N reads | `unknown bus messages are ignored and the next known event is applied`; `a message with envelope version 2 is ignored` and `a version 1 message of an unknown event type is ignored` (`ClusterEnvelopeCodecGoldenTest`) |
| Settings | A new domain, a new field with a default | Renaming a domain or a field | `a settings change of the current build is served by the previous release` |
| Jobs | Leases and claims as new nullable columns | Relying on N to honour a lease | not tested |

### Order of a rolling upgrade

1. Start one replica of N+1 on the database. Wait until it answers its status endpoint.
2. Replace the replicas of N one at a time. A replica of N that restarts after N+1 has changed the schema must start and serve: `the previous release restarts and serves once the current build has changed the schema`.
3. Do not start replicas of both releases at the same moment. The restart case starts N first, then N+1, and restarts N last. No test starts two releases at the same moment, so that case is not tested.

### Limits found by the test

* **Settings arrive late on N.** The previous release applies a shared settings change on a 10 second poll, so a setting saved on N+1 reaches an N replica within that lag. The test waits up to 60 seconds, which is six polls, in `a settings change of the current build is served by the previous release`. The other direction, a setting saved on N and read on N+1, is observed by that test and not asserted, so it is not tested.
* **New columns are only allowed in new tables.** N writes into existing tables without knowing the columns that N+1 adds. A column that N+1 adds to an existing table must therefore be nullable or have a default. The test checks this on the real database schema, not only on the mapping.
* **Unknown bus messages are ignored and counted.** A replica ignores an envelope with an unknown version and a message with an unknown event type, and counts both. The unit tests are `ClusterReceiverTest`: "an envelope with an unknown version is ignored and counted" and "an envelope with an unknown event type is ignored and counted". The two-replica case is `unknown bus messages are ignored and the next known event is applied`. An envelope version newer than N+1 is not covered.
* **Concurrent deploys of one artifact are not guarded across the pair.** The metadata lock exists only in N+1. The test deploys different artifacts at the same moment, not the same artifact, so the case of one artifact is not tested.
* **No scheduled cleanup until every replica runs N+1.** N ignores the leases of the cleanup jobs, see [Upgrade changes](../reference/upgrade-changes.md). Not tested.
* **Token changes on N during the window.** Creating, rotating or revoking tokens on N during the window is not tested. [Upgrade from Ingot 1.1.x](../how-to/upgrade-ingot.md) asks to freeze them until every replica runs the new release.
* **Deletes in a data migration.** The permission node migration deletes rows that a replica of the previous release reads. That pair does not exist yet, because the 1.1.x line has no permission nodes. A later pair with such a step needs the step deferred, or the rule narrowed.

## Failure behaviour

| Failure | What a client sees | What recovers it | Evidence | State |
| --- | --- | --- | --- | --- |
| A replica stops while it holds a batch of search outbox rows | Nothing. The index catches up later | The batch lease runs out after 60 seconds, and another replica takes the rows | `an entry of an expired lease is taken over by another instance` (`OutboxClaimExclusivityTest`) | tested on every pull request |
| A replica stops during a scheduled cleanup run | Nothing | A later check on the same UTC day takes the run over after the 60 second lease, and runs it once | `takes over a run whose instance stopped before it finished, and runs it once` (`CleanupSchedulerClaimTest`); `check_lease_takeover` in the HA smoke test | tested on every pull request |
| A replica was not started at the scheduled minute | Nothing | Nothing. The run of that day is not caught up later | `does not start a run at a later tick when no instance started it at the scheduled minute` (`CleanupSchedulerClaimTest`) | tested on every pull request |
| The holder of the schema lock on MariaDB stops | A replica that starts meanwhile waits for the lock | The lease expires after 60 seconds and the next replica takes it | `takes over a lease whose holder stopped renewing once it has expired` (`LeaseSchemaLockTakeoverIntegrationTest`) | tested on every pull request |
| The holder of a metadata write lock stops | A write of another replica for the same artifact is refused while the lease is held | The lease is taken over once it has expired | `takes over the lease of an instance that stopped after the lease expired` (`MetadataLeaseIntegrationTest`) | tested on every pull request |
| The lease of a metadata write is taken over before the commit | `503`, and nothing is written | The client retries | `refuses the commit with 503 and writes nothing when the lease is taken over before it` (`MetadataLocksTest`) | tested on every pull request |
| A search rebuild or verify run is still live on another replica | A second run is refused | The run ends, or its lease expires and the abandoned run is failed | `a second rebuild is refused while the lease of the first is live` and `a rebuild can start once the lease of an abandoned run has expired and the abandoned run is failed` (`RebuildServiceTest`) | tested on every pull request |
| The database does not answer | Readiness answers `503` while a check fails. Requests that need the database fail | The database returns | `should name a check that answers false` (`ReadinessProbeTest`) shows the mechanism. The database check itself is not tested | not tested for a database outage |
| The bus is reconnecting | Readiness answers `503` | The bus reconnects | `should fail the cluster check when the bus is reconnecting` (`ReadinessClusterCheckTest`) | tested on every pull request |
| The bus is down | Replicas do not see each other's events at once | Shared settings catch up through the database on the next poll | `reloadIfChanged loads the stored document when the provider reports a newer revision` (`SharedConfigurationPollTest`). The bus outage itself is not tested | not tested for a bus outage |

## Scale and sizing

No figure in the table below is measured. A value enters the table only when a run was recorded with its date, the version, the hardware, the workload and the query that produced it, as [Size the deployment](../how-to/size-the-deployment.md) describes. The HA smoke test checks function, not capacity, so it produces no figure.

| Dimension | Measured value | Conditions | Status |
| --- | --- | --- | --- |
| Replicas | not measured | none recorded | not measured |
| Repositories | not measured | none recorded | not measured |
| Artifacts per repository | not measured | none recorded | not measured |
| Total artifacts | not measured | none recorded | not measured |
| Search index size | not measured | none recorded | not measured |
| Concurrent clients | not measured | none recorded | not measured |
| Largest upload | not measured | none recorded | not measured |
| Database size | not measured | none recorded | not measured |

Lease and poll times are constants of the code, not capacity. They are listed in the next section.

## Known limits of the code

These values are fixed in the code. They are limits, not capacity.

| Limit | Value | Evidence | State |
| --- | --- | --- | --- |
| Multipart part size of an S3 upload | 8 MiB per part, the last part may be smaller | `should send a stream of 20 MiB as multipart upload with 8 MiB parts and a smaller last part` (`S3StreamingUploadTest`) | tested on every pull request |
| Lease of a cleanup run | 60 seconds, extended at most once every 20 seconds | `extends the active lease at most once every 20 seconds of the clock` (`CleanupServiceLeaseTest`) | tested on every pull request |
| Lease of a search outbox batch | 60 seconds, extended every 20 seconds | `an entry of an expired lease is taken over by another instance` (`OutboxClaimExclusivityTest`) | tested on every pull request |
| Lease of the schema lock | 60 seconds, renewed every 10 seconds | `keeps the lease for a holder that renews it before the expiry` (`LeaseSchemaLockTest`) | tested on every pull request |
| Poll of shared settings | every 10 seconds | the cadence is not asserted. The settings lag of N in the rolling upgrade rule is bounded at 60 seconds by `a settings change of the current build is served by the previous release` | not tested for the cadence |
| Poll of the database cluster transport | 1 second by default | `the poll interval defaults to one second on the database transport` (`ClusterDatabaseSettingsTest`) | tested on every pull request |
| Retention of a daily job claim | 7 days | `grants a key again once its claim is older than seven days` (`SqlScheduledJobClaimsTest`) | tested on every pull request |

## Related pages

* [Multi-replica operation](multi-replica-operation.md) explains what each replica shares and what it keeps for itself.
* [Run on Kubernetes](../how-to/run-on-kubernetes.md) sets up the chart for more than one replica.
* [Upgrade from Ingot 1.1.x](../how-to/upgrade-ingot.md) gives the steps of the 1.2.0 upgrade.
* [Upgrade changes and supported paths](../reference/upgrade-changes.md) lists the changes per release.
* [Configure the cluster bus](../how-to/configure-cluster-bus.md) describes the optional bus.
* [Size the deployment](../how-to/size-the-deployment.md) describes how a measurement is made.

# Kubernetes-ready Ingot: high availability, configuration distribution and cloud-native structure

Status: design, approved for staged implementation
Date: 2026-08-08

## Why this document exists

Ingot runs well as a single instance. Running two of them against the same database and the
same object store is not safe today, for reasons that are specific and fixable rather than
architectural. This document records what those reasons are, what has to change, and in
which order.

It also answers three questions that came up alongside high availability: how configuration
reaches a pod, how the dashboard and the server scale independently, and where a message bus
belongs. The answers turned out to be connected, so they live in one document.

The scope covers four stages. Only the first two are required for high availability. Stages
three and four are recorded here because they change decisions made earlier, and because
leaving them out would make the earlier stages look arbitrary. Each stage gets its own
implementation plan; this document is the shared context, not a single work item.

## Architecture decisions

Three decisions constrain everything below.

### Locks belong in the database

Schema migration and metadata writes are correctness problems. A lost lock means a lost
update, not a slow request.

MariaDB Galera is already part of the deployment. `GET_LOCK()` and `SELECT ... FOR UPDATE`
are transactional, consensus-backed, and release when the connection dies, which covers a
pod that is killed rather than stopped.

Redis locks were considered and rejected. Without a fencing token, an expired lock does not
protect against a paused process that resumes and writes anyway, which is the exact failure
this needs to prevent. NATS JetStream KV offers revision-based compare-and-swap and would
work, but it puts a new consensus component in the write path for a guarantee the database
already provides.

### NATS covers fan-out, and nothing else

Four places need the same thing: tell every other instance that something changed. Cache
invalidation, configuration propagation, token revocation and console log streaming are all
best-effort. Losing an event means one instance holds a cache entry slightly longer than it
should.

Core NATS publish and subscribe is enough for all of them. JetStream is needed only where an
event must survive, which is statistics ingestion once it crosses a process boundary.

Redis has no remaining niche. It could take the brute-force counter, but so can the database,
and introducing a component for one counter is not a trade worth making. If Redis is already
operated for other reasons, that counter is a reasonable thing to move there later.

### The split runs through deployment targets, not repositories

Ingot is already a modular monolith. All fourteen core building blocks are plugins with
declared dependencies, sorted topologically by `PluginLoader.sortPlugins()`. What is missing
is a switch deciding which plugins a given process loads.

The model is the one Grafana Loki and Mimir use: one artifact, one image, one `--target`
flag. `--target=all` stays the default and reproduces today's behaviour exactly, which is
what keeps the drop-in contract intact.

Separate images per domain were considered and rejected. The hot path is streaming bytes
from object storage to a client, and every additional hop costs latency without buying
anything. The domain has four aggregates, not forty. Harbor took the other road and is
widely considered hard to operate.

## What already works

Worth stating, because it shapes how much is left:

- **Database backends.** `DatabaseConnectionFactory` supports MariaDB, MySQL and PostgreSQL
  through HikariCP. Tokens, statistics and shared configuration already live there. SQLite is
  only the default.
- **Configuration propagation.** `RemoteSharedConfigurationProvider` stores in the database,
  and `SharedConfigurationPlugin` polls for changes every ten seconds. The log message reads
  "Propagation ... updating current instance", so this was written with more than one
  instance in mind.
- **Statistics aggregation.** `SqlStatisticsRepository.incrementResolvedRequests` is an
  upsert with `count + count`. Several instances add up correctly.
- **Storage abstraction.** `StorageProvider` is configurable per repository, and the S3
  implementation exists. Shared storage without a ReadWriteMany volume is already possible.
- **Stateless authentication.** Basic auth against tokens in the database, no server-side
  session store. Artifact traffic needs no sticky sessions.
- **Environment configuration.** `INGOT_LOCAL_*` and the legacy `REPOSILITE_LOCAL_*` prefixes
  are read by `LocalConfigurationFactory`, so a ConfigMap can replace the configuration file.
- **Dashboard split.** A second image and `INGOT_LOCAL_DEFAULTFRONTEND=false` already allow
  separate deployments.

## Stage 1: correctness

Without these three changes, a second replica is unsafe. None of them require a new
component.

### 1.1 Serialise schema initialisation

`SqlAccessTokenRepository`, `SqlStatisticsRepository` and `SqlConfigurationRepository` each
call `SchemaUtils.create(...)` or `createMissingTablesAndColumns(...)` during
initialisation. With several pods starting at once, which is what a rolling update and an
initial `replicas: 3` both produce, they issue DDL against the same tables concurrently.

On Galera, DDL runs under total order isolation. Concurrent `CREATE TABLE IF NOT EXISTS` and
`ALTER TABLE` statements stall the cluster at best and abort a node at worst.

The fix is an advisory lock around the whole initialisation sequence: `GET_LOCK()` on
MariaDB and MySQL, `pg_advisory_lock` on PostgreSQL, a no-op on SQLite and H2 where a single
writer is the only supported mode anyway. The lock is acquired once at startup and released
before the server begins serving.

### 1.2 Serialise metadata writes

`MetadataService.generatePom` reads `maven-metadata.xml`, modifies it and writes it back.
`FileSystemStorageProvider` holds a `ReentrantReadWriteLock` per location, but that lock ends
at the process boundary, and the code says so: `TO-FIX: FS locks are not truly respected`.
The S3 provider has no lock at all. Two deployments of the same group and artifact from two
pods lose one version.

`PreservedBuildsListener` has the same shape: it deletes snapshot files based on a timestamp
read from metadata another pod may be rewriting.

The fix is a lock row keyed by repository and path, taken with `SELECT ... FOR UPDATE` for
the duration of the read-modify-write. This covers both storage providers, because the
serialisation happens in the database rather than in the storage layer.

### 1.3 Flush statistics on shutdown

`StatisticsFacade` buffers increments in memory and flushes every ten seconds from a
scheduled task. `Reposilite.shutdown()` stops the scheduler before anything else, and
`StatisticsPlugin` registers no dispose handler, so every rolling update discards up to ten
seconds of statistics per pod.

The fix is a `ReposiliteDisposeEvent` listener calling `saveRecordsBulk()` once more.

## Stage 2: Kubernetes

After this stage, running several replicas is correct and behaves properly under rolling
updates, node drains and probe failures.

### 2.1 Readiness separate from liveness

`/api/status/health` reports `webServer.isAlive()`, which means Jetty is running. A pod whose
connection pool is exhausted or whose object store is unreachable still reports UP and keeps
receiving traffic.

Add `/api/status/ready`, unauthenticated like the existing endpoint, reporting:

- database reachable, via a pooled `SELECT 1`
- storage provider reachable, via a cheap existence check per configured provider
- shared configuration loaded
- not draining

The last item is a flag set at the very start of the shutdown sequence, before anything is
torn down. It is what lets Kubernetes remove the pod from the service endpoints while it is
still serving in-flight requests.

`/api/status/health` keeps its current meaning and becomes the liveness probe.

### 2.2 Graceful shutdown

Two gaps. `stopTimeout` is not set anywhere on the Jetty server, so a stop does not wait for
in-flight requests, and a large upload dies mid-stream. And the draining flag from 2.1 has to
be set before `webServer.stop()` is reached.

The sequence becomes: set draining, wait out the endpoint removal delay, stop accepting new
connections, drain in-flight requests up to `stopTimeout`, then continue with today's teardown.

On the manifest side this pairs with a `preStop` hook and a `terminationGracePeriodSeconds`
larger than `stopTimeout`.

### 2.3 Local disk that Kubernetes has to know about

Two paths need writable local storage that is not the data volume:

- `S3StorageProvider.putFile` writes the entire upload to a temporary file before sending it,
  because the S3 API requires a content length. A 500 MB artifact needs 500 MB of ephemeral
  storage. Without an `emptyDir` for `/tmp` and an `ephemeral-storage` limit, the kubelet
  evicts the pod during the upload.
- `JavadocContainerService` unpacks javadoc jars under the working directory, per pod, with
  no eviction. This belongs in an `emptyDir` with a size limit, not on the data volume.

### 2.4 Read path and write path as separate deployments

This is the largest scaling gain in the whole document, and it needs no new code once 1.2 is
in place.

Reads are the overwhelming majority of repository traffic, need no coordination and scale
linearly. Writes need the metadata locks. Routing `GET` and `HEAD` to one deployment and
`PUT`, `POST` and `DELETE` to another, from the same image, gives each an independent replica
count and autoscaler. The split lives entirely in the ingress rules.

### 2.5 Chart and manifests

There is no Ingot chart; `kubernetes.md` currently points at the Reposilite chart and says so.
What is needed:

- two deployments, dashboard and server, with the probes from 2.1
- ingress routing the four dashboard paths directly, see the configuration section below
- `emptyDir` volumes and `ephemeral-storage` limits from 2.3
- a PodDisruptionBudget and topology spread constraints
- the read and write split from 2.4 as a documented option rather than a default

### 2.6 Documentation corrections

Three things the current guides get wrong for a clustered deployment:

- `--local-configuration-mode=none` is required, see below
- S3 credentials belong in a Secret, not in the database, see below
- the first access token should come from `--token`, not from `kubectl attach`, which reaches
  a random pod when there is more than one

## Stage 3: distribution over NATS

Optional. Nothing here is required for several replicas to be correct; it removes polling,
closes propagation delays and fixes the console.

### 3.1 Two new events in the core

The clustering logic belongs in a plugin, but two things it needs to observe have no event
today:

- cache invalidation, currently a direct call into `ResolutionCache`, which is `internal`
- token revocation, currently a repository write with no notification

Both become events on the existing extension mechanism. This is the only core change stage 3
requires.

### 3.2 The clustering plugin

A plugin that does nothing when no NATS URL is configured, so the default deployment is
unchanged. With a URL, it mirrors four things across instances:

| Concern | Today | With NATS |
|---|---|---|
| `ResolutionCache` invalidation | per instance, `cache-purge` reaches one pod | published, every instance invalidates |
| Shared configuration | ten second database poll | published on save, poll stays as fallback |
| Token revocation | up to sixty seconds of staleness from `authenticationCache` | published on revoke |
| Console log streaming | one pod's logs | every pod publishes, every session sees all |

The console case is the most valuable. `ReposiliteJournalist.subscribe()` already implements
fan-out; it simply ends at the process boundary. Mirroring it over NATS means an
administrator sees the logs of every replica regardless of which pod the connection landed
on, which makes the console better in a cluster than it is standalone.

### 3.3 The brute-force counter

`AuthenticationFacade` holds failed login attempts in a process-local cache, so with three
replicas an attacker gets three times the allowance. Login frequency is orders of magnitude
below artifact reads, so a database table with an upsert is sufficient and avoids adding a
dependency. This is listed under stage 3 because it is a clustering concern, not because it
needs NATS.

## Stage 4: deployment targets

This is the cloud-native structure and the frame an enterprise edition would build on.

### 4.1 The mechanism

- a `targets` field on the `@Plugin` annotation
- a filter in `PluginLoader.sortPlugins()`
- a `--target` parameter defaulting to `all`

Small in code. The weight is in deciding which targets exist and documenting them.

### 4.2 Which cuts are worth making

In order of value:

**Statistics.** The cleanest boundary in the codebase.
`StatisticsFacade.incrementResolvedRequest` is called from exactly one place,
`RepositoryService`, with no return value and no error path. Over JetStream, the read path
publishes and a statistics process consumes and writes. The stream also replaces the
in-memory buffer that stage 1.3 patches. The `statistics` to `console` plugin dependency
exists only to register a command and is not a runtime coupling.

**Javadoc.** Unpacking jars is CPU and disk bound, a completely different resource profile
from streaming bytes, with its own endpoints and its own scratch directory.

**Authentication: deliberately not cut.** `access-token` is the only plugin with no
dependencies and is technically the best isolated, but `AccessTokenFacade` is consulted on
every request through `ContextDsl` and `ReposiliteRouting`. A network hop per artifact
download to check a token is the wrong trade. It stays embedded, with revocation propagated
per 3.1.

### 4.3 The abstraction this rests on

`RemoteClient` is already the interface for "fetch a file from somewhere else", with two
implementations: `HttpRemoteClient` for real upstream mirrors, and `RepositoryLoopbackClient`
for a local repository addressed as if it were remote. The second one proves the core already
tolerates a repository that is not local. A third implementation talking to another Ingot
process fits at the same seam without `RepositoryService` knowing.

### 4.4 Relationship to an enterprise edition

The plugin system is already the open-core mechanism.
`PluginLoader.loadPluginsByServiceFiles()` loads jars from the plugins directory through a
`URLClassLoader` and `ServiceLoader`. An enterprise plugin is a jar in that directory, using
the same API third-party plugins use.

The design rule that follows: **every enterprise feature must be expressible as a plugin.**
If it cannot be, it belongs in the core and is free software. This keeps the boundary honest
and protects the Reposilite plugin compatibility promise.

The licensing question is open and legal rather than technical. Apache 2.0 imposes no
copyleft, so proprietary plugins against the `com.reposilite.*` API are possible, and the
dzikoysk copyright headers are unaffected as long as enterprise code lives in new files. That
assessment is not a substitute for advice from someone qualified to give it.

## Configuration distribution

Three levels, each with a different path into a pod.

| Level | Contains | Mechanism | Kubernetes |
|---|---|---|---|
| Parameters | working directory, token, migrations, configuration mode | argv | `args:` |
| Local configuration | port, thread pools, database URL, SSL, default frontend | file, overridable by environment | ConfigMap and Secret as environment variables |
| Shared configuration | repositories, mirrors, frontend settings, LDAP, statistics | database or file | database |

### Three corrections

**`--local-configuration-mode=none` is required.** `LocalConfigurationProvider.render()`
writes `configuration.cdn` back to the working directory whenever the mode is `AUTO`, which
is the default. Against a read-only ConfigMap mount this fails. The error is swallowed and
the server starts anyway, which is worse than failing: the deployment ends up with a
configuration source that tries to overwrite itself.

**Database configuration and GitOps configuration are mutually exclusive.** Passing
`--shared-configuration-path` switches to `LocalSharedConfigurationProvider`, which reports
`isMutable() = false` and `isUpdateRequired() = false`. The dashboard can then display
settings but not save them, and changes require a rollout. Storing in the database keeps the
dashboard working and propagates within ten seconds, at the cost of configuration that does
not live in git. For a clustered deployment the database is the right choice, because the
propagation is already built.

**S3 credentials do not have to live in the database.**
`S3StorageProviderFactory` installs a static credentials provider only when both the access
key and the secret key are non-empty. Leaving them blank falls through to the AWS default
credential chain: environment variables, web identity and IRSA, instance metadata.
`AWS_REGION` is already read. Clearing those two fields in the shared configuration moves the
credentials into a Kubernetes Secret, and rotation becomes a secret update rather than a
database write. No code change required.

## Scaling the dashboard separately from the server

The split already exists and is well documented. One thing does not carry over to Kubernetes.

The dashboard container proxies everything that is not its own static content to the server,
including every artifact download and every CI upload. The reasoning in the guide is correct
for Compose: repository names are arbitrary top-level paths and cannot be distinguished from
dashboard routes by prefix, and a second origin would mean CORS on every repository read.

On Kubernetes this inverts the stated purpose. Scaling the dashboard without touching the
traffic CI depends on does not work when all of that traffic passes through the dashboard.

The exception list in the nginx template is finite, which is what makes the alternative
possible. It contains exactly four entries:

```
/assets/       prefix
/favicon.png   exact
/index.html    exact
/              exact
```

An ingress can express that directly, because ingress-nginx evaluates exact matches before
prefix matches: the four paths above route to the dashboard, and a catch-all prefix routes to
the server. One origin is preserved, so no CORS. Dashboard routing is hash based, so
navigation never reaches the server and the exact match on the document root is sufficient.
The proxy hop disappears entirely.

The `INGOT_BACKEND` proxy stays in the image for Compose and for anyone without an ingress
that can express exact path rules.

## Compatibility

Ingot is a drop-in replacement for Reposilite, and that outranks everything in this document.
Every stage must still satisfy the checks in `CLAUDE.md`:

1. An unmodified upstream Compose file starts with only the image reference changed.
2. An existing `/app/data` is adopted, whoever owns it.
3. `PUID`, `PGID` and `REPOSILITE_OPTS` still take effect.

The "Container images" CI job answers all three by running the image, including an upgrade
rehearsal against the real upstream image.

Specific commitments per stage:

- **Stage 1** changes behaviour for everyone, since the advisory lock is taken on every
  start. This is acceptable because an uncontended lock costs nothing measurable, and it is
  the only stage without an opt-in. SQLite and H2 skip it entirely.
- **Stage 2** adds an endpoint and a shutdown delay. Existing probes keep working, because
  `/api/status/health` keeps its meaning.
- **Stage 3** is inert without a NATS URL.
- **Stage 4** defaults to `--target=all`, which is today's process.

New environment variables take the `INGOT_` prefix, with any `REPOSILITE_` spelling remaining
valid as a fallback.

## Testing

- **Stage 1** needs concurrency tests that the existing suite has no shape for: several
  connections racing on schema initialisation, and concurrent deployments of the same group
  and artifact asserting that no version is lost. These run against a real database, not
  SQLite, since the behaviour under test is database-specific.
- **Stage 2** extends the container CI job: start two replicas against one database and one
  bucket, confirm both become ready, roll one and confirm no request fails.
- **Stage 3** requires a NATS container in the integration environment and a test asserting
  that an invalidation on one instance is observed by another.
- **Stage 4** asserts that each target starts, exposes the endpoints it should and does not
  expose the ones it should not.

## Deliberately not done

Recording these so they read as decisions rather than oversights.

**Distributed single-flight for mirror fetches.** `MirrorService.inFlightFetches` is per
process, so a cold start fetches an artifact once per pod instead of once. The effect is
brief and `putFile` is idempotent. The coordination is not worth its complexity.

**Redis.** Every need it would cover is covered better by the database or by NATS.

**A shared artifact byte cache.** With S3 as the backing store this would be a cache in front
of a cache.

**Splitting authentication into its own service.** Explained in 4.2.

**Microservices as separate images.** Explained in the architecture decisions.

## Order of work

Stages 1 and 2 deliver high availability and are required. Stage 3 is optional and additive.
Stage 4 is the structural work, and it comes last on purpose: every cut made before the
correctness problems are solved would be built on them.

One caveat worth repeating. The largest throughput gain in this document is the read and
write split in 2.4, and it needs no target mechanism and no message bus. If the pressure is
performance rather than organisation, the work stops paying off after stage 2. Stages 3 and 4
are worth doing for operability, for the console, and as the frame for an enterprise edition,
not for throughput.

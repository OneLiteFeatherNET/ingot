# Design

This design adds the deploy target `worker`, a switch that lets `server` replicas leave the background jobs to a worker, and the
chart mode `scalable`. It records the investigation first (findings F1 to F5, with file and line), then the decisions D1 to D9
with their alternatives, then the open questions with proposed defaults, then the conflicts with open pull requests. All paths
are relative to `reposilite-backend/src/main/kotlin` unless they start with `charts/` or `docs/`.

## Context

- `add-deploy-modes` introduced `DeployTarget` with `ALL` and `SERVER` (`net/onelitefeather/ingot/deploy/DeployTarget.kt:37`),
  the pure `DeployTargetResolver.resolve` (`:65`) with the sources option, property and environment variable (`:67-69`), and the
  refusal of unknown values as `UnknownDeployTargetException` (`:24`) mapped to a one line exit code 1
  (`net/onelitefeather/ingot/deploy/StartupRefusal.kt:30`, `com/reposilite/ReposiliteLauncher.kt:36-39`).
- Its design listed `worker` as a non-goal and named the Loki and Mimir `-target` naming. The resolver is called before
  anything binds a port (`com/reposilite/ReposiliteFactory.kt:69-78`).
- Today the target changes exactly one thing at runtime: `FrontendPlugin` registers the dashboard routes only for `ALL`
  (`com/reposilite/frontend/application/FrontendPlugin.kt:101`). There is no plugin filter by target yet. The HA design proposed a
  `targets` field on `@Plugin` (`docs/superpowers/specs/2026-08-08-kubernetes-ha-design.md`, 4.1), which this change does not
  need (D2).
- The chart has the modes `allInOne` and `split` (`charts/ingot/values.yaml:9`, `values.schema.json:7`).

## Investigation

### F1. Do background jobs run on every replica, and is that coordinated?

They are scheduled on every replica, because each process owns one single thread scheduler
(`com/reposilite/ReposiliteFactory.kt:116`, `com/reposilite/shared/extensions/NamedThreadPools.kt:54`) and there is no leader
election and no "this instance is the scheduler" flag. The coordination is per job, through one shared mechanism:
`ScheduledJobClaims` (`com/reposilite/scheduling/ScheduledJobClaims.kt:31`), implemented in the database by
`SqlScheduledJobClaims` (`com/reposilite/scheduling/infrastructure/SqlScheduledJobClaims.kt:67`). `claim(key)` is granted to one
caller, `lease(key, ttl)` to one caller while the lease is live, `takeover` to one caller after an expired lease. Without a shared
database every call is granted (`ScheduledJobClaims.Unshared`, `:62`). The NATS cluster bus plays no part in job
coordination. It is fan-out only (`net/onelitefeather/ingot/cluster/ClusterEventType.kt:23-37`: resolution, token, session,
config, javadoc, storage usage, console, failure, status and read-only events), which matches the HA design decision "NATS
covers fan-out, and nothing else".

Conclusion: jobs that must run once do run on every replica's scheduler, and exactly one replica wins the claim per key. The
standby replicas pay a database round trip per tick. No double work was found for them, with one exception (F3).

### F2. The job inventory

Singleton jobs (move to the worker, and stop on a `server` with `INGOT_BACKGROUND_JOBS=false`):

| Job | Scheduled at | Coordination |
| --- | --- | --- |
| Cleanup policies, scheduled run | `com/reposilite/cleanup/application/CleanupPlugin.kt:93` (tick every 30 s) | day key lease, `CleanupScheduler.kt:73`; whole run under an active lease, `CleanupService.kt:110` |
| Audit retention purge | `com/reposilite/audit/application/AuditPlugin.kt:105` (every 24 h) | day key lease, `com/reposilite/audit/AuditFacade.kt:218` |
| Search rebuild | `net/onelitefeather/ingot/search/application/SearchPlugin.kt:309` (tick every 30 s) | `claims.claim(utcDayKey("search-rebuild"))`, `search/rebuild/RebuildScheduler.kt:95` |
| Search verify | same tick | `claims.claim(utcDayKey("search-verify"))`, `search/verify/VerifyScheduler.kt:71` |
| Search token sweep | same tick | none, see F3 (`search/TokenSweep.kt:34`) |
| Search outbox drain | `SearchPlugin.kt:327` (poll 2 s or 10 s) | per entry claim with owner and lease, `search/outbox/OutboxWorker.kt:63`, `OutboxStore.kt:76-77` |

Per-process duties (stay on every target, including `worker`, and are not touched by the switch):

| Duty | Scheduled at | Why it stays |
| --- | --- | --- |
| Statistics flush every 10 s | `com/reposilite/statistics/application/StatisticsPlugin.kt:54` | flushes this process's in-memory buffer (`statistics/StatisticsFacade.kt:61`); the write is an additive upsert, so replicas add up |
| Settings reload every 10 s | `com/reposilite/configuration/shared/application/SharedConfigurationPlugin.kt:79` | each process must see shared settings; the worker needs them for policies |
| Status snapshots | `com/reposilite/status/StatusSnapshotScheduler.kt:34`, started at `status/application/StatusPlugin.kt:105` | local figures of this process |
| Search backend availability tick | `SearchPlugin.kt:174` | availability of the backend for this process |
| Cluster event prune | `net/onelitefeather/ingot/cluster/DatabaseClusterTransport.kt:159` and the poll scheduler `cluster/PollScheduler.kt:34` | bus plumbing, claimed per window |
| Schema lock renewal | `net/onelitefeather/ingot/schema/LeaseSchemaLock.kt:81` | start-up only |
| Console SSE keepalive | `com/reposilite/console/infrastructure/ConsoleSseHandler.kt:77` | per connection |

Not jobs in this codebase, and therefore not moved: mirror "sync" and metadata regeneration. Mirroring is on demand while a
request is served (`com/reposilite/maven/MirrorService.kt:183`, ledger `MirrorCache.kt:46`), so the read path writes cache
entries to storage and to the ledger. Metadata is rewritten by writers (deploy, delete, cleanup) under `MetadataLocks`
(`com/reposilite/maven/MetadataLocks.kt:26`), which adds a shared database lease to the process lock, so every writer is
serialised across instances. The maintainer's list names both; D4 explains how the proposal handles that.

### F3. Where jobs would be duplicated or racy

One finding, small: `TokenSweep` has no claim. It runs on the first tick after every start and then every `interval` on each
replica (`search/TokenSweep.kt:37-60`, kept state in `cursor` and `lastRunAt` per process). Each replica keys the same unkeyed
rows. The writes are idempotent, so the result is correct, but with N replicas the backfill is done N times in the worst case.
This is a candidate for a separate `fix(search): claim the token sweep before it runs`, not part of this change. Red-test plan:
two `TokenSweep` instances share a database and an injected clock; assert that `keyRows` is called by only one of them per
interval, using a fake claim store that grants the key once; then wrap the sweep in a `claim(utcDayKey(...))` or an interval
window key, in the style of `RebuildScheduler`. No sleeps, the clock is advanced explicitly. The maintainer decides whether it
goes first. With `INGOT_BACKGROUND_JOBS=false` on all server replicas the finding disappears in practice.

Two related items are already being fixed and are only noted here: the scheduled cleanup lease fencing (#225) and making up
missed scheduled runs (#239), both in `CleanupScheduler.kt`. A worker that is down at the minute a cleanup is due depends on
the `takeover` path that #239 extends, so the worker rollout should wait for #225 and #239.

### F4. State that is local to a process

| State | Where | Effect of a read, write, worker split |
| --- | --- | --- |
| Resolution cache | `com/reposilite/maven/ResolutionCache.kt`, invalidated by `RESOLUTION_INVALIDATED` over the bus (`net/onelitefeather/ingot/repository/RepositoryCacheCoordinator.kt:13`) | a write or a cleanup on another pod invalidates read pods only if the bus is up; without it the entries live until their bounded lifetime. This is why `worker` requires a bus |
| Token credential cache | `com/reposilite/auth/AuthenticationFacade.kt:80`, dropped on token change (`:105`, `TOKEN_CHANGED`) | a token revoked on the write pod must reach read pods; same bus requirement |
| Session denial list | `net/onelitefeather/ingot/identity/session/SessionService.kt:67`, `SESSION_REVOKED` event | session tokens are not looked up in the database (`SessionService.kt:53`), so a login on a write pod is valid on a read pod; revocation is bus driven |
| Statistics buffer | `statistics/StatisticsFacade.kt:61` | each pod flushes its own counts; read pods count the downloads, the worker counts nothing; totals add up |
| Console log buffer and SSE sessions | `console/infrastructure/ConsoleSseRegistry.kt:30` | a `GET` stream is routed to a read pod, so it shows that pod's lines plus the forwarded `CONSOLE_LINE` events of the others |
| Metadata and schema locks | `maven/MetadataLocks.kt:26`, `schema/LeaseSchemaLock.kt` | already database leases, so writers on any pod are serialised |
| Local file system storage | `FileSystemStorageProvider` | not shared between pods; see D7 |
| Read-only flag, settings | `READ_ONLY_CHANGED`, `CONFIG_CHANGED` events and the 10 s settings poll | reach every pod through bus and database |

### F5. The existing cluster mode

There is no single "cluster mode" switch. A deployment is clustered when it combines three optional pieces: a shared database
(`DatabaseConnectionFactory` supports `mariadb`, `mysql`, `postgresql` and the default embedded `sqlite`,
`com/reposilite/configuration/local/infrastructure/DatabaseConnection.kt:45-58`), repositories on object storage (the S3
provider, selected per repository, `com/reposilite/storage/StorageProviderSerdes.kt:67`), and a bus URL read by
`ClusterSettings` (`net/onelitefeather/ingot/cluster/ClusterSettings.kt:57`, `INGOT_CLUSTER_URL` with the schemes NATS, Redis
family and `database`). Without a bus URL the cluster plugin does nothing (`ClusterPlugin.kt:100`). The chart's typed values
for them are `database.existingSecret`, `objectStorage.existingSecret` and `clusterBus.url` (`charts/ingot/values.yaml:20-35`).
The chart does not refuse `deployment.replicas > 1` without them today.

### F6. What the chart's routing supports today

`charts/ingot/templates/ingress.yaml` in `split` mode emits path rules only: `Exact /`, `Prefix /assets`, `Exact /favicon.png`,
one `Prefix` per dashboard segment (from `files/dashboard-segments.json`) to the dashboard Service, and a final `Prefix /` to the
server Service. `httproute.yaml` emits the same as `PathPrefix` and `Exact` matches and no `method` field. Both reject being
enabled together (`ingress.yaml:1-3`). A Kubernetes `Ingress` has no method matching in its API. ingress-nginx can only do it
through annotation snippets, which many clusters disable for security reasons, and Traefik needs its own CRD. Gateway API
`HTTPRoute` has `matches[].method`, and its precedence rules rank an exact path over a longer prefix, then a method match, then
headers. A rule with `PathPrefix /` and `method: GET` therefore beats a rule with `PathPrefix /` and no method.

## Decisions

### D1. Add `worker` to the closed list, same sources, same refusal

`DeployTarget` gets `WORKER("worker")`. `VALID_VALUES` is derived from the entries (`DeployTarget.kt:41`), so the unknown-value
refusal lists `all, server, worker` with no new code. The legacy `defaultFrontend` mapping is unchanged and never yields
`worker`. Alternatives: a separate boolean `INGOT_WORKER=true` (rejected: two switches that can contradict each other, and
the Loki and Mimir model is one target).

### D2. The worker is the same plugin set with a restricted route list, not a plugin filter

All plugins still load on `worker`: the schedulers live inside the plugins whose facades the jobs call (cleanup needs the maven
facade, search needs the repository registry). A `targets` field on `@Plugin` would force a decision per plugin and a
dependency graph check in `PluginLoader` for little gain, and it has no value until a process needs fewer plugins. What the
worker changes is what it serves: the routing setup registers only the status routes and `/metrics` (the
`StatusEndpoints` routes at `com/reposilite/status/infrastructure/StatusEndpoints.kt:49-136`) and everything else is `404`. The
Maven handler and the dashboard are not reachable, so a misrouted request cannot run a deploy on the worker. The dashboard
routes are already skipped for non-`ALL` (`FrontendPlugin.kt:101`).

### D3. No `read` and no `write` target

Read and write need no different code: both serve the same routes, every writer takes the shared metadata lease
(`MetadataLocks.kt`), and caches are kept right by the bus. A `read` target would have to refuse writes in code, which a method
router already does without a code path. It would also be wrong in the one place where reads write: a proxied artifact is
fetched, stored and recorded in the mirror ledger while a `GET` is served (`MirrorService.kt:183`), and a `read` target that
refuses storage writes would break every proxy repository. A `write` target would have to serve the admin API's `GET`s, or the
dashboard breaks. Splitting by target also adds three more values to document, test and keep compatible. The deciding rule:
a target changes what the process does, routing changes where traffic goes, and read versus write is traffic. Revisit if a
real read-only mode is wanted for security (the existing read-only flag, `READ_ONLY_CHANGED`, already covers an instance
that refuses writes).

### D4. Which jobs move, and what the maintainer's list maps to

The worker owns the singleton jobs of F2. "Mirror sync" and "metadata regeneration" are not scheduled jobs here (F2), so they
stay on whichever pod serves the request or the cleanup, and nothing is claimed for them. "Scheduled tasks" is the cleanup
and audit purge schedule. "Later search indexing" is the outbox drain, the rebuild and the verify, which are already claim based
and can move with the same gate now. Statistics flush is not moved: it drains the buffer of the pod that counted.

### D5. `INGOT_BACKGROUND_JOBS`, opt-in, `INGOT_` only

Boolean, default `true`, sources as `INGOT_TARGET`: option `--background-jobs`, property `ingot.background-jobs`, variable
`INGOT_BACKGROUND_JOBS`. Only the singleton jobs of F2 consult it, through one small `BackgroundJobs` gate that the plugins
receive, so a test can drive it with a fake. `server` with `false` is the only combination that turns jobs off. `all` with
`false` is refused, because `all` promises that the pod does everything and nothing else would run the jobs; `worker` with
`false` is refused as contradictory. A value that is not `true` or `false` stops the start with the valid values, as an unknown
target does. The default leaves every existing deployment, including an unmodified upstream compose file, unchanged.
Alternative: make `server` stop jobs whenever it sees a worker. Rejected: instances cannot see each other reliably, and the
behaviour of an existing target must not change under a deployment.

### D6. The worker serves only probes and metrics

`/api/status/health` and `/api/status/ready` are what the chart probes. `/metrics` comes from the optional Prometheus plugin and
is allowed through when present, so the existing `ServiceMonitor` works for all three workloads. `/api/status/instance` is kept
for the cluster status view. The worker listens on the same port `8080`, so the chart reuses its probe and security helpers.

### D7. Start refusal when shared state is missing

Checked for `worker`, and for `server` with `INGOT_BACKGROUND_JOBS=false`, after the shared configuration is loaded (so the
repository list is known) and before the web server binds. The refusal reuses `StartupRefusal` (one line, exit code 1) and
names every missing requirement, for example:

`Ingot did not start: target worker needs shared state, but the database is embedded sqlite (set INGOT_LOCAL_DATABASE to mariadb, mysql or postgresql), no cluster bus is configured (set INGOT_CLUSTER_URL), and repository 'releases' is stored on the local file system (use object storage).`

Rules: embedded SQLite is refused (a worker in another pod could not reach the file); an empty bus URL is refused (the worker's
changes must reach the read pods' caches, F4); a repository on the local file system is refused unless the operator sets
`INGOT_ALLOW_SHARED_FILESYSTEM=true` (a ReadWriteMany volume mounted into every pod is a valid setup that the process cannot
detect, so there is an escape hatch, `INGOT_` only). `all` and `server` with defaults never run the check, so existing installs
start as before. The text is final in the spec.

### D8. Chart `scalable`

Workloads: `<name>-read` (`INGOT_TARGET=server`, `INGOT_BACKGROUND_JOBS=false`, default 2 replicas, optional HPA and PDB),
`<name>-write` (same env, default 2 replicas), `<name>-worker` (`INGOT_TARGET=worker`, 1 replica, no HPA, no route), and the
dashboard workload of `split` unchanged. Services: `<name>-read`, `<name>-write`, and `<name>` selecting read and write pods
together, so the existing `service`, `serviceMonitor` and NetworkPolicy values keep working. Values: `scalable.read`,
`scalable.write`, `scalable.worker` with `replicas`, `resources`, `autoscaling` (read only) and `podDisruptionBudget`, falling
back to the top-level keys for everything else (image, env, probes, jvm), so a values file stays small. Persistence: the data
volume is not mounted in `scalable` (a PVC cannot be shared), and `persistence.enabled: true` is refused, because the
requirements of D7 imply object storage; the temporary directory uses `emptyDir`. Render refusals, one line each:

- `scalable mode needs database.existingSecret: pods share one database`
- `scalable mode needs objectStorage.existingSecret: pods cannot share a local volume`
- `scalable mode needs clusterBus.url: a write or a cleanup on one pod has to reach the caches of the others`
- `scalable mode does not use persistence: unset persistence.enabled`

Routing with `httpRoute.enabled`: (1) dashboard paths to the dashboard, as in `split`; (2) `PathPrefix /` with `method: GET`
and with `method: HEAD` to `<name>-read`; (3) `PathPrefix /` without a method to `<name>-write`, which catches `PUT`, `POST`,
`DELETE`, `PATCH`, `OPTIONS` and anything else. The admin API `GET`s (settings, tokens, status pages) and the console SSE stream
therefore land on read pods, which is correct because they only read and the caches are bus fed. Write pods handle login,
deploys and every change.

Routing with `ingress.enabled`: a plain Ingress cannot match a method, so non-dashboard paths go to `<name>`, which selects
both pods. Read and write pods then share all traffic evenly and the split gives independent sizing but no isolation. The
limitation is printed in `NOTES.txt` and in the how-to, with the two escape routes: Gateway API, or an ingress controller's
own method routing on the `<name>-read` and `<name>-write` Services that the chart renders anyway. The chart does not
render controller specific annotations. Alternatives: refuse `ingress` in `scalable` (rejected: too hostile to the many
clusters without a Gateway), or default `GET` to read with a configuration snippet annotation (rejected: disabled by default
in current ingress-nginx and a security risk).

### D9. Defaults and drop-in

Nothing changes unless the operator sets `INGOT_TARGET=worker`, `INGOT_BACKGROUND_JOBS=false`, or `mode: scalable`. The new
variables have no `REPOSILITE_` spelling. The image, entrypoint, paths, compose files and the `allInOne` and `split` renders
stay byte for byte equal, which the golden fixtures check.

## Risks and trade-offs

- A worker that is down means no scheduled cleanup, audit purge or index drain. The chart runs one replica with a liveness
  probe; an alert rule is out of scope. #239 makes missed cleanup runs recoverable.
- The `/metrics` allow-list depends on the plugin registering that path; a different plugin path is not allowed through. This
  is accepted and documented.
- Two workers are safe (all claims), so a rollout with `maxSurge` never double-runs a job.
- Read pods run the proxy cache fill, so they need write access to object storage. The chart gives all pods the same secret.

## Open questions

1. Should `INGOT_ALLOW_SHARED_FILESYSTEM` exist? Proposed default: yes, off, because an RWX volume is a real setup.
2. Should `server` with `INGOT_BACKGROUND_JOBS=false` also require the shared state of D7? Proposed default: yes, same check,
   otherwise a pod could silently run with no job owner at all.
3. Is the `/metrics` pass-through for the worker enough, or should the worker serve the Prometheus plugin only when it is
   installed? Proposed default: pass through when present.
4. Default replicas of `scalable.read` and `scalable.write`. Proposed default: 2 and 2, worker 1.
5. Should the token sweep claim (F3) ship before this change? Proposed default: yes, as a small `fix(search)` pull request, but
   it is not a blocker.
6. Should read pods in `scalable` be allowed to set `autoscaling.enabled`, and on what metric? Proposed default: HPA on CPU
   only, as the existing `autoscaling` values, no custom metrics.
7. Name of the combined Service. Proposed default: `<name>` (so existing `service` values and the ServiceMonitor apply).

## Conflicts with open pull requests

Checked with `gh pr list -R OneLiteFeatherNET/ingot` at the time of writing.

- #269 `fix(chart): keep the liveness probe green while a replica drains`: `templates/_helpers.tpl`, `values.yaml`,
  golden fixtures and `tests/test_chart.py`. The `scalable` workloads reuse the probe helpers, so the chart pull request
  (3.2) must come after #269, #271 and #272.
- #271 `fix(chart): stop false warnings in the install notes`: `NOTES.txt`, `values.yaml`, `tests/test_chart.py`. The Ingress
  limitation note of 2.5 edits the same file.
- #272 `fix(chart): probe the port the container listens on`: `_helpers.tpl`, `deployment.yaml`, `values.yaml`,
  `tests/test_chart.py`. `scalable` renders three Deployments from the helpers it changes.
- #267 `docs(gitbook): add a helm values reference and chart feature guides`: `docs/gitbook/reference/helm-values.md`, which
  task 2.8 extends.
- #225 `fix(scheduling): fence job leases with an epoch` and #239 `feat(cleanup): make up missed scheduled runs when enabled`:
  `CleanupScheduler.kt` and its tests. Task 1.4 wires the gate into the cleanup plugin, not the scheduler, but both change
  the claim semantics the worker relies on (F3).
- #234 and #220 (search backends) touch `SearchPlugin` wiring and may add a scheduler. Task 1.4 gates the three call sites of
  F2 and must be rebased on them.
- #251, #249, #273 and others are proposals under `openspec/changes/` with other names, no file overlap.
- `add-deploy-modes` itself: this change extends `DeployTarget.kt` and `charts/ingot` from it. Both are on main.

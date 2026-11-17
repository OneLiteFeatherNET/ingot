# Design

The design refers to files on `main` at commit `d440721db`. Paths are relative to the repository root. Line numbers are those
of that commit. The change builds on the Maintenance page (`openspec/specs/maintenance-page`), the Administration tree
(`openspec/specs/administration-navigation`), the cleanup scheduling (`openspec/specs/cleanup-scheduling`) and the nexus import
jobs (`openspec/specs/nexus-import-jobs`).

## Research: what keeps a record of its runs today

### Cleanup runs

- `GET /api/cleanup/runs` is `reposilite-backend/src/main/kotlin/com/reposilite/cleanup/infrastructure/CleanupEndpoints.kt`
  lines 89-107. It is guarded by `managerOnly("maintenance")` (`ingot.admin.maintenance`) and takes an optional `policy`
  query parameter (line 103). Its response is `List<CleanupRunResponse>` (line 101). The response has no page, no cursor and
  no limit parameter.
- **Bound:** the list is bounded per policy, not in total. `CLEANUP_RUN_HISTORY_LIMIT = 20` (`.../cleanup/CleanupModel.kt:25`)
  is applied by `runs.keepNewest(policyId, CLEANUP_RUN_HISTORY_LIMIT)` after each run (`.../cleanup/CleanupService.kt:157`),
  and `SqlCleanupRunRepository.keepNewest` deletes the rows beyond the newest 20 of that policy
  (`.../cleanup/infrastructure/SqlCleanupRunRepository.kt:90-99`). So the endpoint returns at most 20 times the number of
  policies. The fields of a run are `id, policyId, trigger, startedAt, finishedAt, status, deletedCount, deletedBytes,
  failures` (`CleanupEndpoints.kt:122-132`). `failures` names each deletion that did not work.
- A run is synchronous. `CleanupService.run` (`CleanupService.kt:105`) returns the finished run; no cancel route exists.
- Triggers are `MANUAL` and `SCHEDULED` (`CleanupModel.kt`, `enum class CleanupTrigger`). Statuses are `SUCCEEDED`, `PARTIAL`
  and `FAILED` (`CleanupModel.kt`, `enum class CleanupRunStatus`).

### Search index runs

- The runs are rows of `search_rebuild_run` (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/search/database/SearchTables.kt:150`),
  written by `net/onelitefeather/ingot/search/runs/RunStore.kt`. Rebuild and verify share the table (`runs/RunModel.kt:24`).
- Kinds are `REBUILD` and `VERIFY` (`runs/RunModel.kt:25`). Triggers are `MANUAL, SCHEDULED, STARTUP, GAP, BACKEND`
  (`runs/RunModel.kt:29`). Statuses are `RUNNING, COMPLETED, FAILED, CANCELLED` (`runs/RunModel.kt:31`). The counters are
  `processed, upserted, deleted, unparsed, skippedTooLong, errors` (`runs/RunModel.kt:38-45`).
- **How the runs reach the Maintenance page:** `GET /api/maven/search/index` (`net/onelitefeather/ingot/search/api/SearchApiEndpoints.kt:204-220`,
  `managerOnly("maintenance")`) calls `indexResponse()` (line 376). That reads `search.indexRuns()` (line 377), which is
  `SearchFacade.indexRuns()` (`net/onelitefeather/ingot/search/application/SearchFacade.kt:51`) and returns an `IndexRunStatus`
  with the active run, the runs and the last full rebuild (`runs/RunModel.kt:84-92`). The response maps the runs at line 395
  (`runs = runs.runs.map(::runResponse)`). The frontend reads `index.runs` in `reposilite-frontend/src/components/maintenance/IndexPanel.vue:77`
  and draws them in the list at lines 404-440.
- **Bound:** `RunPolicy.HISTORY = 20` (`runs/RunModel.kt:112`) feeds `RunStore.recent(limit)` (`runs/RunStore.kt:210-212`), which
  orders by id, newest first. So the index status shows 20 runs of both kinds.
- **Retention:** nothing deletes from `search_rebuild_run`. A search of `net/onelitefeather/ingot/search/runs/` for `deleteWhere`
  finds no delete on the run table; the matches that write the run row are counter writes (`RunStore.kt`, lines 93, 153, 180). Every
  run stays in the table forever, and the endpoint shows the newest 20. A scheduled verify (`search/verify/VerifyScheduler.kt:72`,
  `RunTrigger.SCHEDULED`) and a scheduled rebuild (`search/rebuild/RebuildScheduler.kt:96`) both add rows.
- **Cancel:** `POST /api/maven/search/index/runs/{id}/cancel` (`SearchApiEndpoints.kt:290-321`, the route at line 303) answers
  `204` when the run is cancelled, `409` when it has finished, and `404` when the run does not exist. `RunStore.cancel` (line
  191) is the conditional write. The cancel stops the run after the batch in flight (`SearchApiEndpoints.kt:293`).
- **Lock:** a single run is allowed at a time. A second start answers `409` (`SearchApiEndpoints.kt:249`).

### Migration (nexus import)

- The nexus import is a plugin, `reposilite-plugins/nexus-import-plugin`. Its routes are `NexusImportEndpoints.kt`:
  `GET /api/migration/nexus/jobs` (line 93), `GET .../jobs/{id}` (line 99), `DELETE .../jobs/{id}` (line 105), pause, resume
  and cancel (lines 111-125), the report and the conflicts (lines 136, 155). Each route is `managerOnly { }` with no area, so the
  guard is `requireArea(null)`, which means an instance admin (`ContextDsl.kt:162-168`; the node is `ingot.admin.*`, see
  `reposilite-frontend/src/store/nodeAccess.ts:37`).
- Jobs are rows of the job store (`.../nexusimport/jobs/JobStore.kt`). `JobStore.jobs()` (line 342) returns every row; there is no
  limit. A finished job can be deleted (`JobStore.deleteJob`, line 372).
- Statuses are `QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED` (`.../nexusimport/jobs/JobModel.kt:18`), and a pause has a
  reason `READ_ONLY, CREDENTIALS_NEEDED, OPERATOR` (`JobModel.kt:24`). A job has no trigger field; every job is created by an
  operator.

### Other background work

| Work | Keeps a record of runs? | Where | Decision |
| --- | --- | --- | --- |
| Storage usage computation | no. It keeps the last figures and an in-flight flag | `reposilite-backend/src/main/kotlin/com/reposilite/maven/StorageUsageService.kt:127-138` | not listed |
| Javadoc extraction | no. It extracts a jar when it is asked, and logs a failure | `reposilite-backend/src/main/kotlin/com/reposilite/javadocs/JavadocContainerService.kt:204-211` | not listed (feature moves to a plugin in #598) |
| Settings sync (shared configuration reload) | no run record. It is a reload on a cluster event | `openspec/specs/shared-configuration-reload` | not listed |
| Status snapshot | no. It writes the latest snapshot | `reposilite-backend/src/main/kotlin/com/reposilite/status/StatusSnapshotScheduler.kt` | not listed |
| Cleanup scheduler | yes, through the cleanup runs | `reposilite-backend/src/main/kotlin/com/reposilite/cleanup/CleanupScheduler.kt` | listed as `cleanup` |
| Search verify and rebuild schedulers | yes, through `search_rebuild_run` | `search/verify/VerifyScheduler.kt`, `search/rebuild/RebuildScheduler.kt` | listed as `index` |

### The plugin API today

- A plugin extends `ReposilitePlugin` (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/api/ReposilitePlugin.java`).
  `initialize()` returns a `Facade` (line 50). `extensions()` gives the core services (line 55).
- `Facade` is a marker interface (`plugin/api/Facade.kt:19`). Plugins call another plugin's facade with `facade<F>()`
  (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/ReposilitePluginExtensions.kt:29`).
- Lifecycle events are `ReposiliteInitializeEvent`, `ReposilitePostInitializeEvent` (line 23), `ReposiliteStartedEvent` (line 25)
  and `ReposiliteDisposeEvent` (`plugin/api/LifecycleEvents.kt`). Events implement `Event` (`plugin/api/Event.kt:19`) and a
  listener is registered with `event<E>(listener)` (`ReposilitePluginExtensions.kt:26`).
- No extension point lists background runs. `PluginComponents` (`plugin/api/PluginComponents.kt:20`) is empty.
- Open plugin proposals: #598 `extract-javadoc-plugin` and #599 `extract-cleanup-plugin` (both open). The cleanup feature would
  therefore leave the core, and its runs with it.

## Decisions

### D1. Which sources are listed

List the sources that keep a record of their runs and whose records a visitor can read: the search index (`index`), the
cleanup policies (`cleanup`) and the nexus import (`migration`). The other work in the table above keeps no run record, so a
Jobs page could only show a live state with no history. That is a different feature (an active-work indicator), and it is out
of scope. Storage usage and the status snapshot are also not runs: they replace their figures on each computation.

The Jobs page does not list a source by its code. It lists the sources that register (D4), so a plugin source that is disabled
simply has no rows.

### D2. Aggregation on the server, with keyset paging

**Decision (2026-11-17): one paged endpoint on the server, `GET /api/jobs`, merging the sources in the server.** Not a client-side
merge of the existing endpoints.

Reasons:

1. **The existing endpoints cannot be paged.** `GET /api/maven/search/index` returns a fixed 20 and `GET /api/cleanup/runs`
   returns an unbounded list (see the research above). Changing their shape breaks the drop-in contract, so a paged endpoint is
   needed anyway. Once it exists, merging belongs with it.
2. **Bounded pages need one cursor across sources.** A client that calls three endpoints must merge three lists of unknown length,
   and it cannot know when the history of a source has run out. The server can ask each source for its next `limit` runs and
   return the first `limit` of the merge, with one cursor.
3. **Permissions are checked where the data is.** A nexus job names source URLs and a pause reason. A cleanup failure names a
   path. The client must never receive a run of a source the visitor may not read, so the check happens before the rows leave the
   server (D6). A client merge would ship every source to every admin, and the check would live in the frontend.
4. **Plugins need no frontend knowledge.** A plugin source registers on the server (D4). The client asks one endpoint and
   gets its rows, with no knowledge of the plugin's route names.
5. **One cursor survives new runs.** Runs start at the top of the list while an operator pages down. Keyset paging on
   `(startedAt, source, id)` returns the next older run and no row twice. An offset would skip or repeat rows.

Contract:

- `GET /api/jobs?kind=&state=&trigger=&size=&cursor=`. `kind` takes a source id (`index`, `cleanup`, `migration`, a plugin source)
  or a kind (`index.verify`). `kind` and `state` and `trigger` may repeat. `size` is 1 to 100, default 50.
- The response is `{ items, nextCursor, sourceErrors, kinds }`. `nextCursor` is null when no source has more rows. `sourceErrors`
  names a source that failed on this request (D4). `kinds` lists the kinds the caller may read, so the filter offers only those.
- The cursor is opaque: base64 of the `startedAt` in epoch milliseconds, the source id, and the local id of the last item.
- **Merge:** each visible source returns up to `size` runs sorted by its own `(startedAt desc, localId desc)`. The server merges
  the lists by `startedAt`, takes `size` items, and sets `nextCursor` from the last item when any source returned a full page.
  The cost is O(sources times size) per request, which is small for the handful of sources the instance has.
- **Filters go down to the sources.** A source maps the normalised `state` and `trigger` to its own values (D3). If the server
  filtered after the merge, a page could come back short and the client would have to ask again. Pushing the filter down keeps
  each page full.
- A source must return its runs in start order and must not return a run twice across pages. The cleanup table is ordered by
  `startedAt` (`SqlCleanupRunRepository.kt`, `runs` at line 84). The search table is ordered by id, and an id is assigned when
  the run starts, so id order is start order for that source. The source contract (D4) states the rule.

Rejected alternative: **client-side aggregation of the existing endpoints.** It fails the first two reasons, and it would make
the Jobs page depend on the shape of each endpoint that a plugin adds.

### D3. The shared run shape

Each run of any source is one `JobRun`:

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Globally unique and opaque: `<source>:<local id>`, for example `index:412` or `migration:7`. The path segment is URL-encoded. |
| `source` | string | The source id: `index`, `cleanup`, `migration`, or a plugin source id. |
| `kind` | string | `index.rebuild`, `index.verify`, `cleanup.policy`, `migration.nexus`, or `<source>.<name>` for a plugin. |
| `label` | string | The text of the row: "Verify", "Cleanup: nightly", "Nexus import, 12 repositories". |
| `trigger` | `MANUAL` \| `SCHEDULED` \| `SYSTEM` | `SYSTEM` groups the startup, gap and backend triggers of the index. The raw trigger goes in `triggerDetail`. A nexus job is `MANUAL`. |
| `state` | `QUEUED` \| `RUNNING` \| `PAUSED` \| `SUCCEEDED` \| `PARTIAL` \| `FAILED` \| `CANCELLED` | The normalised state (below). |
| `startedAt` | instant | When the run started. |
| `finishedAt` | instant, nullable | When it finished. Null while it runs or waits. |
| `durationMs` | long, nullable | The duration of a finished run. |
| `counts` | list of `{key, label, value}` | The counters, in the order the source chooses. The labels are the source's. |
| `failures` | list of `{message}` | The failures, at most 50 in a detail, with `failuresTruncated` when more. |

Normalised states:

| State | Index (`RunStatus`) | Cleanup (`CleanupRunStatus`) | Migration (`JobStatus`) |
| --- | --- | --- | --- |
| `QUEUED` | | | `QUEUED` |
| `RUNNING` | `RUNNING` | | `RUNNING` |
| `PAUSED` | | | `PAUSED` |
| `SUCCEEDED` | `COMPLETED` | `SUCCEEDED` | `COMPLETED` |
| `PARTIAL` | | `PARTIAL` | |
| `FAILED` | `FAILED` | `FAILED` | `FAILED` |
| `CANCELLED` | `CANCELLED` | | `CANCELLED` |

The index `COMPLETED` status becomes `SUCCEEDED` even when a verify fixed rows. The counts show the fixes; the state says the run
finished. A migration `COMPLETED` job with mismatched repositories is `SUCCEEDED` with the report in the detail.

The shape is additive: a source adds counts and failures and never removes a field.

### D4. How a plugin registers a job source

**Decision (2026-11-17): a new registration event, not a facade.**

- New API in `com.reposilite.plugin.api.jobs` (the package name stays `com.reposilite.*`, as the Ingot contract requires):
  `JobSource` (an interface with `id`, `requiredNode`, `page(query, before, limit)`, `detail(localId)`), the data classes `JobRun`, `JobQuery`, `JobCursor`, `JobPage`, and
  `JobRegistrationEvent`, an `Event` that carries a `JobRegistry`. The registry has one method, `register(source: JobSource)`.
- Core fires `JobRegistrationEvent` after the plugins have initialised, at `ReposilitePostInitializeEvent` (`LifecycleEvents.kt:23`).
  A plugin listens for it in its `initialize()`. A plugin that registers later (after the event) is an error that the core logs,
  and the source is not listed.
- The registration is held by the plugin's lifetime. When a plugin is disabled, its sources leave the registry. Its rows then
  disappear from the Jobs page and nothing else changes. A disabled plugin with a history of runs shows no runs, and the page says
  nothing about it; the history stays in the plugin's tables.
- **Why an event and not a facade:** a facade is a call that a plugin makes to another plugin (`facade<F>()`, `ReposilitePluginExtensions.kt:29`).
  A registration is the reverse: the core asks for sources. An event is the existing way for the core to hand a plugin a
  service at a lifecycle point (`plugin/api/LifecycleEvents.kt`). The core's own sources use the same path, so they go through the
  same contract as a plugin.
- **Binary compatibility:** the change adds types and an event; it changes no signature of `ReposilitePlugin`, `Facade`,
  `PluginComponents` or `Event`. `plugin-abi-gate` (`openspec/specs/plugin-abi-gate/spec.md`) therefore passes unchanged for
  every plugin built against 3.5.28. The core sources are registered by `net.onelitefeather.ingot.jobs` on the same event.
- **Source contract:**
  - `page` returns runs sorted by `(startedAt desc, localId desc)`, at most `limit` (the server passes at most 100).
  - It honours `state` and `trigger` by mapping them to its own values, so a page is full when rows exist.
  - It never returns a run twice across pages for one cursor.
  - It throws for a store error. The aggregator catches the error, leaves the source out of that page, and names it in
    `sourceErrors`. One broken source does not turn the whole page into an error.
- **Frontend:** a plugin needs no frontend code. The Jobs page renders every `JobRun` the same way. A plugin that wants a richer
  detail adds `counts` and `failures`, not a page.
- **Plugins in the pipeline:** #599 (cleanup) moves its runs to its own plugin. Its source registers as `cleanup` with the same
  contract, so the Jobs page keeps its rows. The migration source already lives in `reposilite-plugins/nexus-import-plugin` and
  registers from there.

### D5. Retention: who prunes, and the defaults

**The Jobs page never deletes a run.** It reads what each source keeps. Each source owns its retention:

| Source | Kept today | Who prunes | Default in this change |
| --- | --- | --- | --- |
| `cleanup` | 20 runs per policy (`CleanupModel.kt:25`, `CleanupService.kt:157`) | the cleanup service, after each run | unchanged: 20 per policy |
| `index` | every run (`RunStore.kt` has no delete); the endpoint shows 20 | nobody | unchanged in this change; see below |
| `migration` | every job | the operator, with `DELETE` on a finished job (`NexusImportEndpoints.kt:105`) | unchanged |

**The retention of the search run table is not changed here.** Every scheduled verify adds a row, and the table
is never pruned. The Jobs page shows the rows that exist, so it is correct with or without a prune. The planned follow-up is a
keep-newest of 200 runs per kind, run by the verify scheduler after each run, in its own `feat` change (`prune-search-run-history`).
That keeps about six months of a daily verify and makes the table bounded. It is a separate change because it is a different
scope (search, not jobs) and a different type (a data change), and the proposal ships as one type.

The Jobs page size is 50 and the maximum is 100 (D2). These numbers bound a request, not the history.

### D6. Permissions: who may see which kind

The Jobs endpoints use the node of each source, and a source the visitor may not read is absent from every answer:

| Source | Node that lists its runs | Where the same node is checked today |
| --- | --- | --- |
| `index` | `ingot.admin.maintenance` | `managerOnly("maintenance")` at `SearchApiEndpoints.kt:216` (index), `:242` (rebuild), `:273` (verify), `:305` (cancel) |
| `cleanup` | `ingot.admin.maintenance` | `managerOnly("maintenance")` at `CleanupEndpoints.kt:62`, `:83`, `:102` |
| `migration` | `ingot.admin.*` (instance admin) | `managerOnly {}` with no area, `NexusImportEndpoints.kt:76`, `:82`, `:88` and the other job routes |
| a plugin source | the `requiredNode` it declares; the default is `ingot.admin.*` | none; the default is the narrowest node |

Rules:

1. `GET /api/jobs` answers `403` when the visitor may read none of the sources, with the same message the maintenance endpoints
   give. A visitor who reads some sources gets a list of those.
2. `kinds` in the answer lists only the kinds the visitor may read. The kind filter offers no other kind.
3. `GET /api/jobs/{id}` for a run of a source the visitor may not read answers `404`, the same as a run that does not exist. The
   visitor learns nothing about it.
4. The Jobs page does not cancel a run, and no Jobs endpoint does. Cancel stays on the source pages. The existing cancel
   routes (`SearchApiEndpoints.kt:290`, `NexusImportEndpoints.kt:123`) keep their paths and their nodes.
5. Failures and counts can name paths and URLs of the instance. They are shown only to the nodes above. A failure never carries a
   credential: the nexus routes already never echo one (`openspec/specs/nexus-import-jobs`, "Credentials after a replica loss").
6. The frontend entry for the Jobs page uses `anyNode: [ingot.admin.maintenance, ingot.admin.*]`
   (`registry.ts`, the `permission` field of a page entry). The server remains the authority; the frontend only hides an entry.

No new permission node is added.

### D7. Frontend: the page and the maintenance tabs

- **Entry:** `reposilite-frontend/src/navigation/registry.ts`. A new entry in the `Support` group (lines 356-364) after Maintenance:
  `{ key: 'jobs', route: 'jobs', label: 'Jobs', permission: { anyNode: [...] }, description: '...', keywords: 'runs, background, index, cleanup, migration' }`,
  and a page entry in `PAGES` (near line 425) with `area: 'none'`. The route name `jobs` and the detail route are added to
  `src/router/routes.ts`. The address `/settings/jobs` is new; no existing address changes.
- **The list:** filters for kind, state and trigger, held in the address (`?kind=index&state=FAILED`). A "Load more" button continues
  from `nextCursor`, appends the next page and announces the count in a live region. There is no infinite scroll and no live job list: the page loads on open and on Refresh. A "Refresh"
  action reloads the first page.
- **The detail:** `/settings/jobs/:id`, with the id URL-encoded. It shows the fields of D3, the counts, the failures (with
  `failuresTruncated`), and no Cancel action. A live run is cancelled on its source
  page, as today.
- **Maintenance, Search index tab:** `IndexPanel.vue` stops drawing the list at lines 404-440. It shows the active run (unchanged,
  lines 331-355), the latest rebuild and the latest verify, each from `GET /api/jobs?kind=index.rebuild&size=1` and
  `?kind=index.verify&size=1`, and an "All runs" link to `{ name: 'jobs', query: { kind: 'index' } }`. The drift report of the last
  verify stays where it is.
- **Maintenance, Cleanup tab:** `RunHistory.vue` stops drawing every run. It shows the latest run of each policy and an "All runs"
  link to `{ name: 'jobs', query: { kind: 'cleanup' } }`. The policy select on the tab is removed, since the tab shows one run per policy (question 3).
- The index endpoint keeps its `runs` array (D8). The frontend stops reading it; it can be removed in a later major version.

### D8. Drop-in impact

- **Additive:** `GET /api/jobs`, `GET /api/jobs/{id}`, the types in `com.reposilite.plugin.api.jobs`,
  and the Jobs address in the frontend.
- **Unchanged:** every existing path, method and response shape. `GET /api/maven/search/index` keeps its 20 runs.
  `GET /api/cleanup/runs` keeps its list. The nexus job routes keep their behaviour, and `/maintenance?tab=index` keeps opening
  the Search index tab.
- **No configuration key, environment variable, volume or manifest changes.** The only storage change is none: the Jobs
  endpoints read the existing tables.
- **Nothing is BREAKING.**

## Open questions (settled 2026-11-17)

Every question is settled with its recommended default, except question 9. Question 9 follows the maintainer's constraint that cancel
happens only on the source pages, so its answer is the alternative of that question. The tasks and the specs implement these answers.

1. **Page size and paging style.** Resolved: 50 per page with "Load more" and a cursor (D2), with no total count, as the maintainer asks.
2. **The filter value for an index run.** Resolved: `kind=index` for the source and `kind=index.verify` for one kind (D2, D3).
3. **A policy filter on the cleanup source.** Resolved: no policy filter in this change. `kind=cleanup` lists all policies, and the
   Cleanup tab drops its policy select.
4. **Search run retention.** Resolved: unchanged in this change (D5). The keep-newest of 200 runs per kind is the planned follow-up
   `prune-search-run-history`, in its own change.
5. **Storage usage, javadoc, settings sync and the status snapshot.** Resolved: not listed (D1). A live "running now" row is a separate
   feature and is not part of this change.
6. **Nexus jobs and instance admins.** Resolved: the migration source is visible to instance admins only (`ingot.admin.*`), as the
   maintainer asks.
7. **Plugin source registration.** Resolved: a registration event at `ReposilitePostInitializeEvent` (D4).
8. **Detail address.** Resolved: `/settings/jobs/:id` with the id URL-encoded (D7).
9. **Cancel from the Jobs page.** Resolved: no cancel on the Jobs page. Cancel stays on the source pages, as the maintainer requires.
   This is the alternative of the original question, not its default. The `cancellable` field, the cancel endpoint and the Cancel
   action are removed from the change (D3, D6, D7).
10. **Trigger of a migration job.** Resolved: `MANUAL` (D3).
11. **The latest run on the maintenance tabs.** Resolved: one request per kind (`size=1`) from the Jobs endpoint (D7).

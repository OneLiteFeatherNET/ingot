# Proposal

## Why

Administration → Maintenance has three tabs: Storage usage, Search index and Cleanup policies. The last two each keep their
own list of runs. The Search index tab lists every index run (rebuild and verify) as a card with the start, the finish and
the counts. Cleanup has its own run history below the policy list. Both lists were built for a handful of runs, and both
grow with the work the instance does.

The server bounds the lists today, but the bound is per source and it is not a page. The index status returns the newest 20
index runs of either kind. The cleanup endpoint returns the newest 20 runs of each policy, so a second policy doubles the
list. A scheduled verify adds a run every day, so the index window of 20 holds three weeks of history and the card list
on the tab grows with each verify. The rows behind the index window are never removed. The page therefore swells again each
time the instance adds a scheduled job, and an operator who wants the last failed verify has to scroll past every successful
one.

The maintainer asks for one place for background runs: a Jobs page that lists the runs of every source the instance has, in
one list with paging, filters and a detail view. The maintenance tabs then keep only the latest run of their kind and link to
the Jobs page for the rest.

## What Changes

- **A Jobs page under Monitoring and support.** The page is in the Support group of the Administration tree, after
  Maintenance, at the address `/settings/jobs`. It is one list of background runs from every source the visitor may see.
- **One list, one shape.** Each row shows the kind of run, its trigger (manual, scheduled or system), its state, the start,
  the duration, the counts and the failures. The shape is the same for every source (design D3).
- **Filters.** The list filters by kind (`index`, `cleanup`, `migration`, or a plugin source), by state and by trigger. The
  filters are in the address, so `/settings/jobs?kind=index` opens the index runs.
- **Paging, never an endless list.** The list loads 50 runs at a time with a "Load more" button that continues from a cursor.
  The server returns at most 100 runs per request.
- **A run detail.** The detail shows the counts and the failures with their reasons. It has no cancel: a live run is
  cancelled on its source page, as today.
- **Maintenance tabs keep the latest run.** The Search index tab shows the latest rebuild and the latest verify, plus the live
  run. The Cleanup policies tab shows the latest run of each policy. Both have an "All runs" link to the Jobs page,
  pre-filtered by kind (`?kind=index` and `?kind=cleanup`).
- **Plugins contribute runs.** A plugin registers a job source through a new registration event and a new API type, so a
  plugin that owns a background job (for example the cleanup or javadoc plugins proposed in #598 and #599) lists its runs on
  the Jobs page without any change to the core list. Design D4 gives the contract.

### Sources in this change

The first sources are the three that keep a record of their runs today. The table is the scope; nothing outside it is listed.

| Source (`kind` filter) | Records | Trigger | Cancel on its source page | Who may see it |
| --- | --- | --- | --- | --- |
| Search index, `index` (kinds `index.rebuild`, `index.verify`) | `search_rebuild_run` | manual, scheduled, startup, gap, backend | yes, while running, on the Search index tab | `ingot.admin.maintenance` |
| Cleanup policies, `cleanup` (kind `cleanup.policy`) | `cleanup_run` | manual, scheduled | no, a run is synchronous | `ingot.admin.maintenance` |
| Migration, `migration` (kind `migration.nexus`) | the nexus import job store | manual (created by an operator) | yes, while queued, running or paused, on the migration page | instance admins (`ingot.admin.*`) |

Storage usage, javadoc extraction, settings sync and the status snapshot do not keep a record of their runs. They are not
listed. Design D1 gives the reasons.

### Shipping

The change ships under the Conventional Commits type `feat` and the scope `jobs`:

- Proposal PR `docs(openspec): propose add-jobs-page`: only the files under `openspec/changes/add-jobs-page/`, tasks
  unchecked, branched from the remote main branch. It is merged before the code PR is opened.
- Code PR `feat(jobs): add a jobs page for background runs`: the implementation, branched from the remote main branch that
  already contains the merged proposal. Its commits follow the task groups: `test(jobs)` before `feat(jobs)` in each group,
  and `docs(jobs)` for the reference pages. The squash merge makes the PR title the commit on main.
- A follow-up PR that ticks the tasks or archives the change is opened only when the maintainer asks for it.

The change is one type. Retention of the search run table is a separate concern and is not part of this change (design D5):
it would ship as its own `feat` change under the search scope.

### Out of scope

- Pruning the search run table. The Jobs page reads what the sources keep; it does not decide how long they keep it.
- Listing storage usage, javadoc extraction, settings sync and the status snapshot as runs. None of them keeps a record today.
- Starting, pausing, resuming or cancelling a run from the Jobs page. The existing actions stay on their source pages.
- Live updates over a stream. The page reloads on open and on the "Refresh" action, and the live run card on the maintenance
  tab keeps its polling.
- A policy filter on the cleanup source. It is not part of this change (design, question 3).
- The move of the cleanup and javadoc features into plugins (#598, #599). This change only makes that move possible: a moved
  source registers the same way as the nexus import does.
- Notifications, retention settings in the configuration, and a new permission node.

## Capabilities

### New Capabilities

- `background-jobs`: the Jobs list across sources, its paging and filters, the run shape, the run detail, the
  permission per source, the plugin source contract, and the rule that the Jobs page never deletes a run.

### Modified Capabilities

- `administration-navigation`: the Support group lists Jobs after Maintenance (MODIFIED "Administration tree").
- `maintenance-page`: the tabs show the latest run of each kind and link to the Jobs page (ADDED "Latest run per kind with a
  link to all runs"). No existing requirement of `maintenance-page` covers the run lists, so the change adds a requirement
  rather than modifying one.

## Impact

- **Operators:** nothing to configure. The Jobs entry appears under Monitoring and support for the operators who may see one of
  its sources. The Maintenance tabs show fewer rows and link to the full list. Existing addresses keep working, including
  `/maintenance?tab=index`.
- **Configuration and data:** no configuration key, environment variable, deployment manifest, permission node or stored
  data changes, and no migration step runs. The Jobs endpoints only read the run tables. Nothing here is BREAKING.
- **API (drop-in):** additive only. New endpoints `GET /api/jobs` and `GET /api/jobs/{id}`. Every
  existing endpoint keeps its path, its method, its response shape and its bound: `GET /api/maven/search/index` keeps the
  `runs` array, `GET /api/cleanup/runs` keeps its list, and the nexus job routes keep their behaviour.
- **Plugin authors:** no change is required. A plugin that wants its runs listed implements the new `JobSource` type and
  registers it in the new registration event (design D4). The existing plugin API types keep their signatures, so the
  plugin ABI gate passes unchanged.
- **Code:** a new package `net.onelitefeather.ingot.jobs` in `reposilite-backend` for the list, the registry and the three
  core sources and the plugin contract types (`JobSource`, `JobQuery`, `JobPage`, `JobRegistrationEvent`); `reposilite-frontend/src/navigation/registry.ts`
  for the entry; a new Jobs view under `reposilite-frontend/src/components/jobs/`; and the maintenance tab components
  (`IndexPanel.vue`, `RunHistory.vue`) which stop drawing their full lists. The nexus import plugin registers its source in its
  own module.
- **Documentation:** `docs/gitbook/reference/` gets the Jobs page and the plugin contract, and the maintenance reference gets the
  links. Both are in the `docs(jobs)` task group.

## Verification

- Every group starts with failing tests that are seen to fail on the current build. The backend tests are unit tests with
  in-memory sources and a fixed clock; one integration test reads the SQLite fixture of the search and cleanup tables.
- `./gradlew :reposilite-backend:integration --tests` for the targeted integration test, run through `heavy`, and the
  OpenAPI compatibility gate, which must show the two new endpoints as additions only.
- `npm test` in `reposilite-frontend`, targeted with a file pattern while working.
- Playwright (`--workers=2`, through `heavy`) for the navigation entry, the `?kind=index` address, Load more, the detail and
  the links from both maintenance tabs.
- `openspec validate add-jobs-page --strict` passes, and the dash grep prints nothing.

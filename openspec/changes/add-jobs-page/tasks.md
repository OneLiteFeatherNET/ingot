# Tasks

The code pull request is one PR under the title `feat(jobs): add a jobs page for background runs`. Its commits follow the groups
below: each group is one Conventional Commits type, and inside a group the `test(jobs)` commit comes before the `feat(jobs)` commit
that makes it pass. The squash merge makes the PR title the commit on main. No file under `openspec/` is part of the code PR.

Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (a fixed `java.time.Clock` or a fixed `Instant` is
injected), no network, a fresh source, store and registry per test, a temporary directory per test, and assertions with messages.
Backend unit tests use in-memory fake sources and run in milliseconds. Each backend integration test runs through `heavy` with
`--max-workers=2`, through `./gradlew :reposilite-backend:integration --tests`, never the full integration suite. Frontend tests run
with `npm test` in `reposilite-frontend`, targeted with a file pattern. Playwright runs with `--workers=2`, through `heavy`.

Screenshots: the Jobs page and both maintenance tabs are attached to the code pull request at 320, 600 and 1440 pixels, in light and
dark, before and after the change.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-jobs-page`: this change and nothing else, tasks unchecked, branched from the remote main branch.
  Verify: `openspec validate add-jobs-page --strict` passes, and `grep -nP '[\x{2013}\x{2014}]'` over the change prints nothing.

## 2. Backend: the list, the source contract and the endpoints

### 2a. The shared shape and the paging

- [ ] 2.1 `test(jobs)`: in `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/jobs/JobStateMappingTest.kt`, assert that index
  statuses `RUNNING, COMPLETED, FAILED, CANCELLED` map to `RUNNING, SUCCEEDED, FAILED, CANCELLED`, that cleanup statuses `SUCCEEDED,
  PARTIAL, FAILED` map to themselves, that every migration `JobStatus` maps to its state, and that the index triggers `STARTUP, GAP,
  BACKEND` map to `SYSTEM` with the raw trigger kept in `triggerDetail`. Verify: it fails, because the mapping does not exist.
- [ ] 2.2 `test(jobs)`: in `JobPagingTest.kt`, with fake sources and a fixed clock, assert that the first page merges two sources by
  start time, that the cursor of the last item yields the next older runs with no repeat, that a size of 500 is answered with 100
  items, and that a source which throws is named in `sourceErrors` while the other runs are listed. Verify: it fails.
- [ ] 2.3 `test(jobs)`: in `JobVisibilityTest.kt`, assert that a source the visitor may not read is absent from items, from `kinds` and
  from the id lookup (which answers not found), that a visitor who reads no source gets the forbidden answer with the area in its
  message, and that a filter on an unknown kind answers an empty page and no error. Verify: it fails.
- [ ] 2.4 `test(jobs)`: in `JobRegistrationTest.kt`, assert that a source registered on `JobRegistrationEvent` is listed, that a
  registration after the event is logged and not listed, and that the sources of a disabled plugin leave the registry. Verify: it fails.
- [ ] 2.5 `feat(jobs)`: add `net.onelitefeather.ingot.jobs` (the `JobRun` model, the state and trigger enums, the cursor codec, the merge
  and the registry) and the types of `com.reposilite.plugin.api.jobs` (`JobSource`, `JobQuery`, `JobPage`, `JobRegistrationEvent`).
  No existing type of `com.reposilite.plugin.api` changes its signature. Verify: 2.1 to 2.4 pass.

### 2b. The endpoints and the core sources

- [ ] 2.6 `test(jobs)`: in `JobsEndpointsIntegrationTest.kt`, against the SQLite fixture with search and cleanup rows, assert the
  paging of `GET /api/jobs`, the filters `kind=index` and `state=FAILED`, `GET /api/jobs/{id}`, and the cancel answers `204` for a
  running verify, `409` for a finished one and `404` for an unknown one. Verify: it fails. Run through `heavy`.
- [ ] 2.7 `feat(jobs)`: add `JobsEndpoints.kt` with the OpenAPI annotations of `GET /api/jobs`, `GET /api/jobs/{id}` and
  `POST /api/jobs/{id}/cancel`, and the core sources `index` (over `RunStore`, keyset on id) and `cleanup` (over the cleanup run
  repository, keyset on start time). The cancel delegates to `SearchIndexAdmin.cancel`. Verify: 2.6 passes, and the OpenAPI
  compatibility gate reports the three endpoints as additions only.
- [ ] 2.8 `test(jobs)`: in `reposilite-plugins/nexus-import-plugin`, add `JobSourceTest.kt`, asserting that the migration source lists
  the job rows with the states of 2.1, that a visitor without `ingot.admin.*` gets no migration run, and that the cancel delegates to
  the existing job cancel. Verify: it fails.
- [ ] 2.9 `feat(jobs)`: register the migration source from `reposilite-plugins/nexus-import-plugin` on `JobRegistrationEvent`, with
  `requiredNode` `ingot.admin.*`. Verify: 2.8 passes, and the existing nexus import tests still pass.
- [ ] 2.10 Check, no commit of its own: run the plugin ABI check (`plugin-abi-gate`) and the existing cleanup and search tests through
  `heavy` before the code pull request is opened. Verify: they pass with no change to their expectations.

## 3. Frontend: the Jobs page and the maintenance tabs

- [ ] 3.1 `test(jobs)`: in `reposilite-frontend/src/navigation/registry.test.ts`, assert that the Support group lists Jobs after
  Maintenance, that its route is `jobs`, and that its permission is `anyNode` of `ingot.admin.maintenance` and `ingot.admin.*`.
  Verify: it fails.
- [ ] 3.2 `test(jobs)`: in `reposilite-frontend/src/components/jobs/JobsView.test.ts`, assert that `?kind=index&state=FAILED` is read from
  the address and sent to the server, that "Load more" appends the next page without refetching the first, that the detail lists the
  failures with the truncation note, and that Cancel is drawn only when `cancellable` is true. Verify: it fails.
- [ ] 3.3 `test(jobs)`: in `IndexPanel.test.ts` and `RunHistory.test.ts`, assert that the tabs draw the latest run of each kind and one
  run per policy, that "All runs" links to `{ name: 'jobs', query: { kind: 'index' } }` and to `{ name: 'jobs', query: { kind: 'cleanup' } }`,
  and that no run list is drawn. The existing assertions on the full lists are replaced by these. Verify: it fails.
- [ ] 3.4 `test(jobs)`: in `e2e/jobs.spec.js`, add the cases: the Support entry opens the Jobs page, `/settings/jobs?kind=index` lists
  index runs only, "Load more" adds rows, a detail opens and shows its failures, and both maintenance tabs link to the Jobs page. Locate
  by role and name, no fixed timeouts. Verify: it fails.
- [ ] 3.5 `feat(jobs)`: add the Jobs view under `reposilite-frontend/src/components/jobs/`, the client functions for the three endpoints,
  the routes `jobs` and `job` in `src/router/routes.ts`, and the registry entry with its page entry. Verify: 3.1 and 3.2 pass.
- [ ] 3.6 `feat(jobs)`: change `IndexPanel.vue` and `RunHistory.vue` to draw the latest run of each kind and each policy with the
  "All runs" links, and stop drawing the full lists. Verify: 3.3 passes and 3.4 passes.

## 4. Documentation

- [ ] 4.1 `docs(jobs)`: add `docs/gitbook/reference/jobs.md` with the page, its filters, the sources and their nodes, and the retention
  of each source, and link it from the maintenance reference page.
- [ ] 4.2 `docs(jobs)`: add to the plugin development pages under `docs/gitbook/` how a plugin registers a job source on
  `JobRegistrationEvent`, with the source contract of design D4. Verify: the page names no new configuration key and no environment variable.

## 5. Open the code pull request

- [ ] 5.1 Open the code pull request `feat(jobs): add a jobs page for background runs` against `main`, with the body of the pull request
  template (`.github/PULL_REQUEST_TEMPLATE.md`), the screenshots of 320, 600 and 1440 pixels, and the list of the commits per type. Its
  body names no session link and no co-author. Verify: `gh pr view` shows the exact title, and the body has no em or en dash.

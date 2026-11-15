# Design

## Context

- The dashboard is Vue 3 with vue-router and Vitest (`reposilite-frontend/package.json`). Routes are listed in
  `reposilite-frontend/src/router/routes.js`. The Settings route is `/settings/:domain?` (`routes.js:76`) and a specific route
  must come before it, as `settings-login` does (`routes.js:74-75`). The server already reserves `/settings/<path>`
  (`openspec/changes/restructure-navigation/design.md`, Context), so no new top-level segment is needed.
- Pages are registered in one navigation registry (`reposilite-frontend/src/navigation/registry.js`, header comment).
  `restructure-navigation` is on main. `add-repositories-admin-page` defines the Administration groups Repository, Security, System
  and Support and the routes below `/settings`.
- The plugin UI extension point (`add-plugin-ui-extension-point`) lets a plugin declare pages from six fixed blocks, with no plugin
  script. A seven step wizard with its own state, a mapping table and a poller cannot be built from those blocks, so this page
  is not contributed through it. The extension point only supplies the visibility signal (D1).
- The dashboard already holds a long-lived server stream: the console uses `EventSource` through `extended-eventsource`
  (`src/store/console/connection.js:20`). Apart from that there is no polling code in the frontend, so the wizard introduces a
  small poller (D5).
- The API is `add-nexus-import`'s, under `/api/migration/nexus`, instance admin only. It is stateless between calls except for
  jobs: connection test, discovery and every job carry the Nexus URL and credentials in their request body, and a job runs on one
  replica under a lease while its progress is readable from any replica (D3).
- `fix-dashboard-a11y-and-caching` adds a `main` landmark and moves focus to `main` on in-app navigation (A1 to A3). Not on main yet.
- The legacy 2.x `migration` plugin has no frontend page (`grep -ri migration reposilite-frontend/src` finds one unrelated comment
  in `nodeTemplates.js:68`), so nothing collides with the path.

## Goals

- A guided, ordered path through the import for an instance admin, from Connect to the cut-over checklist.
- The loss report is seen and accepted before any byte is written.
- A long job shows progress, and a reload, a sleeping laptop or a replica change does not lose the running job.
- Usable with keyboard and screen reader.
- No backend change and no new configuration.

## Non-goals

See the Out of scope section of the proposal.

## Decisions

### D1. A first-party page, shown when the plugin is enabled

The wizard is shipped inside `reposilite-frontend`, as a static entry of the navigation registry at
`/settings/migration/<step>`, listed under Administration > System. It is not contributed through the extension point, because the
extension point is declarative by design and the wizard needs components, a store and a poller.

Visibility is decided by one signal, the manifest of the extension point: `GET /api/frontend/plugin-ui` returns `enabledPlugins`,
the names of the enabled server plugins (a requirement that `add-plugin-ui-extension-point` carries). The registry entry has a
`requires` predicate, `enabledPlugins` contains `nexus-import`, in addition to the instance admin permission (D2). The manifest is
read once after the session is known, the way the extension point reads it. The wizard never reads the plugin's switch from a
configuration endpoint. When the plugin is not listed, the entry is absent and a visitor who types `/settings/migration` lands on
the not-found page of the dashboard, like any unknown settings domain. The signal does not depend on `INGOT_LOCAL_PLUGINUI`.

The step components live under one directory, so the way the page is mounted can change without touching them.

### D2. Route, navigation and permission

- One route name `settings-migration` with the path `/settings/migration/:step?`, placed before `settings`. `step` is one of
  `connect`, `discover`, `mapping`, `dry-run`, `sync`, `verify`, `cut-over`. No step means "the first step that is open".
- An unknown or closed `step` redirects to the first open step.
- Navigation: the registry entry is Migration under Administration, System, the group name and route style of
  `add-repositories-admin-page`. This change builds on that change if it lands first. Otherwise the entry is listed in the existing
  Settings navigation (the Instance group of `SETTINGS_GROUPS` in `registry.js`), with the same address, and moves into System when
  that change lands.
- The page name `migration` is reserved against settings domain ids in the registry, as `add-repositories-admin-page` reserves its
  page names, and a test over the bundled domain ids asserts that none is called `migration`.
- Permission: the instance administrator, the same check that `add-nexus-import` applies to every route (`managerOnly()` with no
  area), evaluated in the dashboard with the matcher for `*` (`src/store/nodeAccess.js`). The server stays the authority: every
  call can answer `403`, and the wizard then shows the access-denied state and no data.

### D3. API the wizard calls (defined by add-nexus-import)

`<base>` is `/api/migration/nexus`. Errors are RFC 9457 problem bodies (`add-problem-details`). The wizard uses exactly these
routes, and adds none.

| Route | Used by step | Notes |
|---|---|---|
| `POST <base>/connection/test` | Connect | Reachability, version, edition, which REST areas the credential may read, warnings (plain `http`, trust problems). Body: URL, user name, password literal or secret reference. |
| `POST <base>/discover` | Discover | Repositories with name, format, type, component and asset counts and size, and the format histogram. Same connection body. |
| `POST <base>/jobs` | Dry run, Sync, Verify | Body: kind (`DRY_RUN`, `IMPORT`, `VERIFY`), connection, selection, target names, conflict policy. Answers `202` with the job id. |
| `GET <base>/jobs`, `GET <base>/jobs/{id}` | all job steps | State, per repository progress and counters, pause reason, lease holder. Readable from any replica. |
| `POST <base>/jobs/{id}/pause`, `/resume`, `/cancel` | Sync | Operator control. |
| `PUT <base>/jobs/{id}/credentials` | Sync | Supplies the password again when a job is `PAUSED` with the reason `CREDENTIALS_NEEDED`. |
| `GET <base>/jobs/{id}/report?format=json\|csv` | Dry run | The loss report: one row per object with status `MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED` or `EXCLUDED`, reason, consequence and operator action. |
| `GET <base>/jobs/{id}/conflicts` | Sync, Verify | Paths whose checksum differs, with both checksums. |
| `DELETE <base>/jobs/{id}` | Cut-over | Removes a finished job and its rows, offered after the checklist, with confirmation. |

Facts of that API that the wizard builds on:

- A dry run, an import and a verify are jobs of one resource, so any replica can answer for any of them.
- A job is `PAUSED` with a pause reason `CREDENTIALS_NEEDED` (a literal password and a lost replica, or a `401` or `403` from
  Nexus) or `READ_ONLY` (the instance is read-only, the job resumes by itself when it is turned off). The wizard shows both in
  words. For `CREDENTIALS_NEEDED` it shows a small form that sends `PUT .../credentials`.
- The conflict policy is `refuse` (default: nothing overwritten, the repository ends `CONFLICTED` and cannot be `VERIFIED`),
  `abort` (the first conflict stops that repository) or `overwrite` (files are replaced, both steps audited). Repository results
  are `VERIFIED`, `MISMATCH`, `CONFLICTED` or `FAILED`.
- The import job creates the repository definitions and copies the content. A name that is taken is never touched: the report
  proposes `<name>-nexus`. A target name that is invalid or reserved is refused with `400`.
- The credential is held by the server only in memory for a literal password, or as reference text. There is no connection
  resource: connection test, discovery and each job send the connection again.
- The only values of a job state that the wizard relies on by name are `PAUSED` and its pause reasons. A job is treated as finished
  when it reports a finished instant (Q1).
- No call writes to Nexus. There is no server-side mapping, acceptance, checklist or capability resource, no delta pass and no
  alias, so none of them is in this wizard.

### D4. State: jobs on the server, the rest in memory

What survives a reload is what the API stores: the jobs. The address carries the step.

- **Server (jobs):** the dry run with its report, the import with its progress, the verify with its result, and the pause state. After
  a reload the wizard calls `GET jobs`, finds the newest job of each kind and derives the step to open: an unfinished import opens
  Sync, a finished import without a finished verify opens Verify, a finished verify opens Cut-over.
- **Browser memory only (not persisted):** the connection form, the discovery result, the mapping, the conflict policy and the
  acceptance of the loss report. They are lost by a reload. After a reload with no job the wizard opens Connect, which is cheap
  to redo. Nothing is written to `localStorage`, `sessionStorage`, a cookie or the address, because the connection form holds a
  password.
- **The password.** A secret reference (`${env:NAME}`, `${file:PATH}`) is kept as text, because it is not a secret. A literal
  password is held in a non-reactive module variable, never in a component's reactive state, a store that devtools can show, the
  address or the DOM after the request. It is needed again for Discover, each job start and a re-entry, so it is kept while the
  wizard page is open, and it is dropped when the visitor leaves the wizard route, signs out, gets a `401`, or presses "Forget the
  password". After a reload the wizard asks again where a call needs it (Sync, Verify) and, for a running job, sends it only with
  `PUT .../credentials`.
- View state (table filters, open row, sort order) stays in the query string with the `url-state` helper
  (`src/router/url-state.js`).

The acceptance of the loss report is a gate in the page, bound to the dry run job id and to a fingerprint of the selection,
the target names and the conflict policy. The server does not enforce it. It protects the operator from starting a sync that
differs from what they read, not the server from a different client.

### D5. Progress: polling, with a bounded schedule

Discovery is one request. Dry run, sync and verify are jobs and show progress by reading `GET jobs/{id}`, not by holding a stream.

- A small poller calls `GET` on the resource, then waits the interval and repeats. The first interval is 2 seconds, and it grows
  by 1.5 times up to 15 seconds while the answer does not change, and returns to 2 seconds when it does. It stops when the job is
  finished.
- The timer, the clock and `fetch` are injected, so tests drive it without waiting (F.I.R.S.T.).
- The poller pauses while the tab is hidden (`visibilitychange`) and polls once at once when it is shown again.
- A failed poll (network error or `5xx`) shows an inline, polite "Progress could not be read, trying again" note and keeps
  retrying on the longest interval. `401` goes to the existing sign-in flow, `403` and `404` stop the poller.
- Why polling and not server-sent events: `add-nexus-import` defines no stream, the job runs on one replica and progress must read
  the same on every replica, which a stream from a load-balanced replica does not give without the cluster bus, and a stream is
  buffered or cut by some reverse proxies (`docs/gitbook/how-to/run-behind-reverse-proxy.md`). The cost is a request every 2 to 15
  seconds for one admin. The poller is the only consumer of the transport, so a stream could replace it later.
- Survives reload: on load the Sync step calls `GET jobs`. If an unfinished import exists the page attaches to it, otherwise it
  shows the newest finished one. A job that is running while its counters do not move for 120 seconds (a constant of the page, not a
  configuration key) is shown as "No progress reported for N minutes", with no automatic action.

### D6. Gating

A step is open when its input exists. Connect is always open. Discover needs a successful connection test in memory. Mapping needs a
discovery result. Dry run needs a mapping with at least one included repository, no duplicate target name and a conflict policy.
Sync needs a finished `DRY_RUN` job whose report the operator accepted for the current fingerprint and which has no blocker. Verify
needs an import job that has started. Cut-over needs a finished verify job. After a reload the jobs open Sync, Verify and Cut-over
as D4 says, and the steps before them need the password and a new discovery, which the step list says in text.

The step list shows closed steps as unavailable (`aria-disabled`, with the reason in text), and a direct address to a closed step
redirects to the first open step with a message. The Sync start button is disabled, with the reason beside it, when the dry run has
blockers, when the acceptance is stale, or when no repository is included.

### D7. Discover and Mapping

- Discover lists repositories with name, format, type, component and asset counts and size, plus the format histogram. Security
  objects, cleanup policies and routing rules are not discovered as lists: they appear as `MANUAL` or `UNSUPPORTED` rows of the loss
  report, which is where the operator sees them.
- Mapping is a table of the repositories with columns Nexus name, format, type, size, target name (text input), include (checkbox)
  and warnings. A conflict policy control (`refuse`, `abort`, `overwrite`) sits above it, `refuse` preselected. Choosing
  `overwrite` needs a confirmation dialog that says files will be replaced.
- Validation in the browser is advisory and covers an empty name and a name used twice in the plan. Taken and reserved names are
  decided by the server: the dry run report names the collision and proposes `<name>-nexus`, the wizard offers to take the proposal
  (which changes the mapping and makes the acceptance stale), and `POST jobs` shows the server's `400` problem body when a name is
  invalid or reserved.
- Formats the plugin cannot import are shown, excluded and not selectable, with the component count and size, so the format decision
  has data.
- A change of the mapping, the selection or the policy changes the fingerprint, which makes the dry run and its acceptance stale,
  and the page says so next to the Dry run step.

### D8. Dry run and loss report

- Starting a dry run is `POST jobs` with kind `DRY_RUN`, and a poll (D5). While it runs the page shows the same progress element
  as Sync.
- The report table has the columns object type, Nexus id, Ingot target, status, reason, consequence and operator action. Status is
  a text label with an icon and a shape, never colour alone. Filters: status, kind, free text, applied in the page over the rows of
  the report. JSON and CSV are plain links to `GET jobs/{id}/report?format=`, so the browser downloads them with the session.
- Above the table: counts per status, the estimated transfer size and the blockers. A blocker is a list with a link to the row it
  comes from.
- Accept: a checkbox "I have read the loss report for this mapping" and a button. It records the acceptance in the page (D4). Sync
  is disabled until it is recorded.

### D9. Sync, verify and cut-over views

- Sync: `POST jobs` with kind `IMPORT`. One overall bar and one row per repository (state, assets done of total, bytes, errors,
  and its result `CONFLICTED`, `FAILED` or `VERIFIED`). Pause, Resume and Cancel buttons follow the job state. Cancel asks for
  confirmation in a dialog that traps focus and returns it to the button. A `PAUSED` job shows its pause reason in words:
  `READ_ONLY` says the instance is read-only and the job resumes by itself, `CREDENTIALS_NEEDED` shows the re-entry form. Conflicts
  are listed from `GET jobs/{id}/conflicts` with both checksums.
- Verify: `POST jobs` with kind `VERIFY`, a status row per repository with `VERIFIED` or `MISMATCH` in text, the counts, and the
  mismatching paths. A repository that is not `VERIFIED` is listed as blocking in the checklist.
- Cut-over: the checklist items are Freeze Nexus writes (manual), Verify all repositories (derived from the newest `VERIFY` job, not
  tickable by the operator) and Switch clients (manual). Ticks of the manual items are held in the page only and are not stored,
  and the page says so. The page never writes to Nexus, so the freeze is instructions only: it names Nexus's read-only setting for
  each repository, as text. A finished job can be deleted with `DELETE jobs/{id}` after a confirmation.
- Client snippets: a Maven `settings.xml` mirror and server fragment and a Gradle `repositories` block, generated in the
  browser from the Ingot base URL and the target repository names, with a copy button. No secret is ever put into a snippet,
  the credential id is a placeholder.

### D10. Accessibility (WCAG 2.2 AA)

- Structure: each step renders one `h2` (the page `h1` is "Migration"), inside the `main` landmark that
  `fix-dashboard-a11y-and-caching` adds. The step list is an `ol` of links with `aria-current="step"` on the current one
  (2.4.6, 1.3.1). Moving to another step moves focus to its `h2`, which has `tabindex="-1"` (2.4.3).
- Forms: every input has a visible label and its error text linked with `aria-describedby`, a failed submit moves focus to an
  error summary that links to the fields (3.3.1, 3.3.3). The password field has the correct `autocomplete` value and a show
  and hide button with a state (`aria-pressed`). Required fields say so in text.
- Tables: a `caption`, `th scope`, a real `button` in a sortable header with `aria-sort`, and a visible filter label. The mapping
  inputs are labelled by the row's Nexus name (1.3.1, 4.1.2).
- Status: text plus icon plus shape for `MAPPED`, `PARTIAL`, `MANUAL`, `UNSUPPORTED`, `EXCLUDED`, `VERIFIED`, `MISMATCH` and the job
  states, so colour is never the only means (1.4.1). Text and non-text contrast follow the existing theme tokens (1.4.3, 1.4.11).
- Progress: the overall bar is a `progress` element or `role="progressbar"` with `aria-valuenow`, `aria-valuemin`, `aria-valuemax`
  and a label. A separate `role="status"` live region announces, at most once every 10 seconds and only when the whole percent
  changed by 10 or more or the state changed, so a screen reader is not flooded. Indeterminate when the total is unknown.
- Dialogs: confirm dialogs use the existing dialog component, trap focus, close on Escape and return focus.
- Time: no timeout is imposed by the page. A long job never ends a session by itself (2.2.1). Polling does not steal focus.
- Motion: bars and spinners stop animating under `prefers-reduced-motion: reduce` (the global rule of
  `fix-dashboard-a11y-and-caching` A4 covers CSS, and the bar uses no JavaScript animation).
- Target size and keyboard: all controls are reachable by Tab in reading order, with a visible focus indicator, and none is
  smaller than 24 by 24 CSS pixels (2.5.8). The layout works at 320 px width without a horizontal page scroll (1.4.10).

### D11. Error states

Every call that fails shows the problem `title` and `detail` of the RFC 9457 body in an alert region next to the action that
failed, with the `type` link when there is one, and keeps the form input. `401` leaves through the existing sign-in flow and
returns to the same step. `403` shows an access-denied state. A body that is not a problem document shows the status text.
No secret is ever shown back or logged to the console.

## Risks and trade-offs

- **Two changes the wizard depends on are unmerged.** The page cannot be built before `add-nexus-import` lands, and it needs
  `enabledPlugins` from `add-plugin-ui-extension-point`. The step components talk to one API module (`migrationApi.js`), so a
  changed route name or body is one file. Mitigation: the tests use a fake of that module, and the contract is checked once against
  the real change when it is merged.
- **Polling cost and latency.** A change shows up after at most 15 seconds on a quiet job. This is acceptable for a job that
  runs for hours. The interval is a constant of the poller.
- **The password lives in browser memory while the wizard is open.** The API has no connection resource, so every job start sends
  the credential again. A secret reference avoids a literal password in the browser altogether, and the page offers it first. A
  reload clears the password, and the operator re-enters it where a call needs it.
- **The mapping, acceptance and checklist ticks are not stored.** A reload before the first job loses them. This is the cost of
  adding no server state here, and it is cheap on a first step. Storing a plan on the server would be a change to
  `add-nexus-import`.
- **Acceptance is a page gate, not a server rule.** Another client of the API can start a sync without it.
- **Single page of results for big reports.** The report is read as one document by the API. The table renders one page of it at
  a time and never more than a few hundred rows in the DOM.

## Migration plan

No operator migration. The page appears only when the `nexus-import` plugin is enabled. Rollback is a revert of the pull
request. The server state of an import (jobs) belongs to `add-nexus-import` and is not touched by this change.

## Open questions

- **Q1. Job response vocabulary.** `add-nexus-import` names `PAUSED`, the pause reasons and the repository results, but does not
  list the other job states or the response field names. Default: the wizard treats a job as finished when it reports a finished
  instant, and the API module is the one adapter. Task 2.1 compares this with the merged API and adjusts the module. This blocks
  the API module task until confirmed.

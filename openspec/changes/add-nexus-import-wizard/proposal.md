# Proposal

## Why

An operator who wants to leave Sonatype Nexus Repository 3 for Ingot has a long, nervous job in front of them: connect to a
running Nexus, find out what is in it, decide what goes where, learn what will be lost, copy hundreds of gigabytes while builds
keep running, prove that the copy is complete, and then switch every client. The engine for that is the change
`add-nexus-import`, and it is an API. Without a guided page, the operator drives it with `curl`, keeps the order of the steps in
their head, and finds out about a lost privilege or a missing format after the sync, not before.

What the operator needs is one place in the dashboard that walks them through the order, refuses a step whose input is not
ready, shows the loss report before anything is written, shows a long copy making progress (and still shows it after a reload,
a laptop sleep or a failover to another replica), and ends in a checklist for the day the clients are switched. It must be
usable with a keyboard and a screen reader, because an instance administrator may be either.

The wizard is a client of that API. It adds no server behaviour of its own. It is visible only when the `nexus-import` plugin is
enabled, so an installation that never turns the plugin on sees no change.

## What Changes

- **Page.** A first-party page shipped inside `reposilite-frontend`, listed in the navigation registry under Administration > System
  as Migration, with one URL per step (`/settings/migration/<step>`). It is not contributed through
  `add-plugin-ui-extension-point`, whose pages are declarative and built from six fixed blocks and cannot hold a seven step wizard.
  It is shown only to an instance administrator and only when the manifest of the extension point (`GET /api/frontend/plugin-ui`)
  lists `nexus-import` in its `enabledPlugins` field. That field is the one small requirement this change adds to the extension
  point. It builds on `add-repositories-admin-page` for the group names and routes if that lands first, and otherwise sits in the
  existing Settings navigation.
- **Seven steps.** Connect, Discover, Mapping, Dry run, Sync, Verify and Cut-over. A step opens only when its input exists. A step
  already done can be reopened. Changing the mapping, the selection or the conflict policy makes the earlier dry run stale and
  blocks Sync until a new dry run is accepted.
- **Connect.** Nexus URL, user name and a password (a literal or a secret reference) go to the server in the request body of the
  connection test. The browser never talks to Nexus and never writes the credentials to browser storage or the address. The API
  has no connection resource, so the page holds a literal password in memory while the wizard is open and sends it with discovery
  and each job. TLS trust is the JVM trust store: there is no trust choice in the page, and a trust failure points at
  `INGOT_EXTRA_CA_CERTS`.
- **Discover and Mapping.** A table of repositories (format, type, size, counts) and the format histogram. Per repository the
  operator sets the target name and includes or excludes it, and picks the conflict policy (`refuse` by default, `abort`,
  `overwrite` after a confirmation). Taken and reserved names come back from the dry run and the job start, with the proposal
  `<name>-nexus`.
- **Dry run and loss report.** The dry run is a server job of kind `DRY_RUN`. Its loss report is shown as a filterable table with
  one row per object and the five statuses, with downloads in JSON and CSV. The operator must accept the report for the current
  mapping before Sync can start. Nothing is written to Ingot or to Nexus by this step.
- **Sync with progress.** An `IMPORT` job. Per repository counts and bytes, an overall bar, pause, resume and cancel, the conflicts
  with both checksums, and the paused reasons `READ_ONLY` and `CREDENTIALS_NEEDED` in words, the latter with a form that sends the
  password again. Progress is polled from the job resource, so it works through any proxy and from any replica, and it resumes by
  itself after a reload because the page looks up the unfinished job on load. Server-sent events are not required.
- **Verify.** A `VERIFY` job. Per repository `VERIFIED` or `MISMATCH` with the counts, and the list of mismatching paths. A
  repository that is not `VERIFIED` is marked as blocking in the cut-over checklist.
- **Cut-over checklist.** Freeze Nexus writes (done by the operator in Nexus), verify (derived from the verify job), switch
  clients. Client snippets for Maven `settings.xml` and Gradle are generated for the Ingot base URL. The ticks are kept in the page
  and are not stored, because the API has no checklist resource.
- **Accessibility.** One heading and one landmark per step, a step list with `aria-current="step"`, labelled form controls,
  tables with captions, status that never relies on colour, a progress bar with a throttled live region, focus moved to the step
  heading, reduced motion honoured. Section 3 of the design lists the criteria.
- **Documentation.** A how-to page for the wizard steps, linked from `docs/gitbook/how-to/import-from-other-repository-managers.md`
  and `docs/gitbook/SUMMARY.md`. This change adds no configuration key or environment variable: the switch that enables the
  plugin belongs to `add-nexus-import`, and the CA setting belongs to `add-image-extra-ca-bundle`.

### Out of scope

- The REST API, the engine, the job tables, the leases, the verification logic and the switch that enables the plugin. All of
  that is `add-nexus-import`. This change calls the routes that change defines and tests against a fake of them.
- The plugin UI extension point itself (`add-plugin-ui-extension-point`). This change reads its `enabledPlugins` field and does
  not register a page through it.
- A plan, an acceptance record or a checklist stored on the server, a capabilities resource, a delta pass and the
  `/repository/<name>` alias. `add-nexus-import` defines none of them, so the page has none.
- Mapping panels for users, roles, privileges, content selectors, LDAP and OIDC settings and the token re-issue list. They are
  v2 of the plugin (report section 7.3). Today they are `MANUAL` or `UNSUPPORTED` rows of the loss report, and this change builds
  and tests the repository sections only.
- Per-job rollback, the downloadable final report beyond the loss report, audit log import and Raw or other formats.
- An automated axe or Playwright run (roadmap N15). This change adds component tests and a manual keyboard and screen reader
  checklist in the tasks.
- Navigation regrouping, which is `add-repositories-admin-page`. This change only adds one entry in its System group.
- Legal and business model questions, including the Nexus image licence in CI.

## Capabilities

### New Capabilities

- `nexus-import-wizard`: the page, the seven steps and their gating, the credential handling, the loss report view, progress that
  survives a reload, the verify and cut-over views, and the accessibility behaviour.

## Impact

- **Type and scope.** The change ships under `feat(ui)`, the scope the dashboard commits use (`feat(ui): group the navigation ...`).
  The pull request title, squash-merged onto main, is:

  `feat(ui): add the nexus import wizard`

- **Breaking marker.** No breaking change. Nothing has to change in a configuration file, volume, manifest or plugin. The page
  exists only while the `nexus-import` plugin is enabled, and that plugin is bundled but disabled by default. A Reposilite
  installation pointed at Ingot sees no new page. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None.
- **Opt-in.** The plugin switch of `add-nexus-import`. This change adds no switch of its own.
- **Dependencies on other changes (neither is on main yet).**
  - `add-plugin-ui-extension-point`: supplies `enabledPlugins` in the manifest, the only thing this change takes from it. The
    wizard cannot be shown before that field exists.
  - `add-nexus-import`: owns the REST API under `/api/migration/nexus/*` and the semantics of connect, discover, job states, the
    credential re-send (`PUT /jobs/{id}/credentials`, `CREDENTIALS_NEEDED`), the conflict policy and verify. The calls used are in
    design D3. Where the two differ, that change wins and the calls here are adjusted.
  - `add-image-extra-ca-bundle`: the way an operator gets a private CA into the JVM trust store that the import uses. It is not
    called by the page, and the page only names `INGOT_EXTRA_CA_CERTS` in its trust failure text.
  - `add-repositories-admin-page`: defines the Administration groups (Repository, Security, System, Support) and the routes below
    `/settings`. This change builds on it if it lands first, and otherwise lists the entry in the existing Settings navigation.
  - `fix-dashboard-a11y-and-caching`: the main landmark and the focus move on route change that the wizard builds on (A1 to A3).
  - `restructure-navigation` (on main): the navigation registry and the `/settings/<path>` route pattern with its reserved segment.
- **Code.** `reposilite-frontend` (pages, components, one store, tests) and `docs/gitbook/`. No backend change.

## Verification

- `npx vitest run` in `reposilite-frontend`, targeted per commit.
- `openspec validate add-nexus-import-wizard --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-nexus-import-wizard` prints nothing.
- The manual keyboard and screen reader pass of task 7.2, recorded in the pull request.

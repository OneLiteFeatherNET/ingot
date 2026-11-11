# Proposal

## Why

An instance administrator opens the dashboard to answer one question: is Ingot healthy, and can I trust what I am looking at?
Today the first control under "Instance status" is a switch labelled "Read-only mode". One click stops every upload on every
replica of the cluster. It asks for no confirmation, it does not say what it does, and it does not say who may use it. The page
is where people come to look, so the switch reads as one more figure, and it is the one control that changes data.

The figures next to it do not add up for a reader. "Used threads 54, of 25 in the pool" shows more threads in use than the pool
allows, because the two numbers come from different things. "91.6 MB of 256 MB available" reads as free memory, but 256 MB is the
largest heap the JVM may use. "Healthy" appears twice under the cluster, "Failures 0, None recorded" says the same thing twice,
and a nightly image reports "Up to date" against the latest release without showing which build is running. An operator who
reads these cards either stops trusting the page or acts on a wrong number.

The fix keeps every REST response and the read-only API as they are. The dashboard shows read-only mode only as a status. The
switch moves to the Maintenance page under Administration, behind a confirmation, and only to visitors with the maintenance
area that the API already checks. Every card names what it measures, and a cluster is summarised once, with the instances below.

## What Changes

- **Switch placement.** The "Read-only mode" switch leaves the dashboard. It is shown on the Maintenance page only, to visitors
  with the maintenance area (`ingot.admin.maintenance`). Visitors without it see no switch, as today.
- **Confirmation.** Turning read-only on opens a dialog that states the consequence: writes are refused with `503` on every
  instance until it is turned off, reads continue, and scheduled cleanup and index runs wait. Turning it off needs no dialog.
- **Read-only status.** The dashboard shows a banner while read-only is on. The banner says whether the cluster or this instance
  is affected, and links to the Maintenance page for a visitor who may change it. No actor or time is shown, because the read-only
  API does not return them; the audit log holds them.
- **Figures.** Threads are shown as live threads with no limit, because no limit in the API counts the same threads. Memory says
  "of 256 MB maximum heap". Progress bars stay within 0 to 100 and carry a value that a screen reader can read.
- **Failures.** The subline says what the count covers ("since the last restart"). It does not repeat the value.
- **Version.** The dashboard says "Same as latest release" when the running version equals the newest release. It never says
  "Up to date", because that claims the running build is current, which the version comparison cannot show.
- **Health.** One health statement per view: the cluster summary when a cluster is present, the health row otherwise.
- **Cluster layout.** With a cluster, the cluster summary comes first and each instance is one labelled row below it. The
  per-instance tiles are not repeated above the summary. An installation without a cluster looks as it does today, apart from the
  wording above.
- **Charts.** The memory and threads charts keep a legend for several instances, pinned by a test, and their subtitles name the
  unit and the meaning.
- **Documentation.** `docs/gitbook/reference/dashboard-pages.md` (the dashboard and Maintenance rows),
  `docs/gitbook/how-to/freeze-writes-for-maintenance.md` (the dashboard steps now point at the Maintenance page) and
  `docs/gitbook/how-to/monitor-status-and-failures.md` (what each status field counts).

### Out of scope

- Navigation bugs in the Administration tree. They belong to `fix-administration-navigation`.
- New metrics, Prometheus output, or a busy-thread count of the web server pool. A real pool utilisation needs a new metric.
- Any change to a REST response: no field is added, renamed or removed, and no value changes. The read-only API keeps its shape.
- Who changed read-only mode and when. Adding those fields to `GET /api/admin/read-only` is a separate additive change.
- The build identity of a nightly image (commit). The status response carries no commit today.
- The value of `latestVersion` while the remote version check is off. It is pinned by `RemoteVersionCheckRequestTest`; see the open
  questions in the design.
- Legal and business questions.

## Capabilities

### Modified Capabilities

- `read-only-mode` (from `add-read-only-mode`): the visibility requirement moves the switch from the dashboard to the Maintenance
  page and adds the confirmation and the accessible description of the switch.

### New Capabilities

- `dashboard-instance-status`: what each card of the instance status measures, its unit and its source, the cluster layout, the
  single health statement and the readable rows and meters.

## Impact

- **Type and scope.** The change ships under `fix(ui)`. The changes are in `reposilite-frontend/src` and `docs/gitbook/`, and no
  backend code changes. The commits use `test(ui)`, `fix(ui)` and `docs(ui)`. The pull request title, squash-merged onto main, is:

  `fix(ui): move the read-only switch and correct the dashboard figures`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, plugin or REST response
  changes. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** The switch is no longer on the dashboard. Visitors who used it there find it on the
  Maintenance page. Turning read-only on needs one more click. Nothing else changes for an existing installation.
- **Opt-in.** Nothing is opt-in.
- **Dependencies on other changes.**
  - `add-read-only-mode`: the `read-only-mode` capability and the endpoints. The endpoints already exist on main
    (`ReadOnlyEndpoints.kt`), so this change can be built now. Its MODIFIED requirement needs the `read-only-mode` spec to exist,
    so this change is archived after `add-read-only-mode`, and `openspec validate` reports that ordering as an INFO line.
  - `add-repositories-admin-page` and `restructure-navigation`: the Maintenance page sits in the Support group of the
    Administration tree. The group already exists on main (`navigation/registry.js`); the route `/maintenance` does not move.
  - `fix-administration-navigation` (separate): may rename groups. It does not change the route, so this change is not blocked.
  - `fix-dashboard-a11y-and-caching`: both touch the dashboard. Its landmark and focus rules are separate requirements.
- **Code.** `reposilite-frontend/src/components/dashboard`, `reposilite-frontend/src/components/maintenance`,
  `reposilite-frontend/src/components/md` (none expected), `docs/gitbook/reference/dashboard-pages.md`,
  `docs/gitbook/how-to/freeze-writes-for-maintenance.md` and `docs/gitbook/how-to/monitor-status-and-failures.md`.

## Verification

- Vitest tests per commit, run as `npx vitest run <files>` in `reposilite-frontend`, each seen to fail before its fix.
- `npm run eslint` on the changed files reports nothing new.
- `openspec validate fix-dashboard-status --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/fix-dashboard-status` prints nothing.

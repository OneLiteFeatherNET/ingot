# Design

## What was checked against the code

Each claim below was read from the code on `origin/main`. Paths are relative to the repository root. The last column says
whether the report is a real defect.

| # | Reported on the dashboard | Cause in the code | Verdict |
|---|---|---|---|
| 1 | "Read-only mode" switch first under "Instance status" | `reposilite-frontend/src/components/dashboard/InstanceStatusPanel.vue:252` renders `<ReadOnlyToggle>` above the cards. `ReadOnlyToggle.vue:53` calls `enable()` or `disable()` straight from the change, with no confirmation, and `ReadOnlyToggle.vue:73-79` gives the `MdSwitch` a label and no description. | Real. Placement and missing confirmation. |
| 1a | Who may use it | The API checks `managerOnly("maintenance")` on `GET`, `POST` and `DELETE` (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/readonly/ReadOnlyEndpoints.kt:60, 78, 96`), which is the node `ingot.admin.maintenance` (`reposilite-frontend/src/store/nodeAccess.js:28`). A visitor without it gets `403` on the probe, and `ReadOnlyToggle.vue:42-44` hides the control. | Not a defect. Visitors without the node see no switch. `ReadOnlyToggle.test.js` covers the 403 case. |
| 1b | Banner | The banner exists (`InstanceStatusPanel.vue:236-250`) and says the state and its source. It shows no actor, no time and no link. `GET /api/admin/read-only` answers only `{enabled, source}` (`ReadOnlyEndpoints.kt:40`). | Partly real. The banner is the status the maintainer asks for. It lacks the link and says nothing about who changed the state. |
| 2 | "Used threads 54, of 25 in the pool" | The number is `Thread.activeCount()` (`reposilite-backend/src/main/kotlin/com/reposilite/status/StatusFacade.kt:177-178`), the live threads of the JVM. The denominator is `maxThreads`, the sum of the web, IO and database pool sizes (`status/application/StatusPlugin.kt:97-100`). Those default to 16, 8 and 1 (`configuration/local/LocalConfiguration.kt:116, 120, 124`), so 25. | Real, in the pairing. Both backend fields keep their documented meaning. `docs/gitbook/how-to/monitor-status-and-failures.md:59-60` names them as different things. The dashboard puts them side by side. |
| 2a | Cluster "107 of 50" | `InstanceStatusPanel.vue:149-153` sums `usedThreads` (54 + 53 = 107) and `maxThreads` (25 + 25 = 50). The same mismatch, summed. | Real, same cause. |
| 2b | Instance rows "54 of 25 threads" | `InstanceStatusPanel.vue:165`. | Real, same cause. |
| 2c | Full bar | `StatTile.vue:105-106` sets `aria-valuenow` from `ratio * 100` without a limit. With 54 of 25 the value is 216 on a meter whose `aria-valuemax` is 100. | Real. A meter cannot show a value above its own maximum. |
| 3 | "Used memory 91.6 MB of 256 MB available" | `maxMemory` is `Runtime.maxMemory()` in megabytes (`StatusFacade.kt:148`), the largest heap the JVM may use. `usedMemory` is `totalMemory - freeMemory` (`StatusFacade.kt:180-181`), the heap in use, including garbage not yet collected. The bar divides the two correctly. The word "available" is wrong. | Real, wording only. No number is wrong. |
| 3a | Instance rows "91.6 of 256 MB" | `InstanceStatusPanel.vue:164`. The unit appears once. | Real. |
| 4 | "Cluster health: Healthy, Worst instance" and a lone "Healthy" | The cluster tile takes the worst instance's `health` (`InstanceStatusPanel.vue:159`, `clusterStatus.js:80-85`). The second "Healthy" is `InstanceHealthRow.vue`, which reads `/api/status/health` for this instance (`instanceHealth.js`, `describeHealth`). Two statements, two sources, one page. | Real. |
| 5 | "Failures 0, None recorded" | The value is `failuresCount` and the subline repeats it (`InstanceStatusPanel.vue:127-129, 269-275`). The count is `FailureFacade.getFailures().size` (`StatusFacade.kt:151`), the distinct local failure records since the last restart (`FailureFacade.kt:120-121`). `docs/gitbook/how-to/monitor-status-and-failures.md:95` says the list is in memory. | Real, the subline. The count is correct. |
| 5a | Cluster failures adds up | `summariseCluster` sums `failuresCount` per instance (`clusterStatus.js:109`). The same error on two replicas is counted twice. | Real, not stated anywhere. The card will say "counted per instance". |
| 6 | Version "1.1.1, Up to date" on a nightly image | `VERSION` is the constant in `build.gradle.kts:43`, rewritten by Release Please. The nightly image is built from `main` (`.github/workflows/release-please.yml`, where a nightly sets only the image tag), so it reports the same `1.1.1` as the latest release, and the comparison says "Up to date". The status response carries no commit. | Real. The comparison is right and the claim is too strong. "Up to date" says the running build is current, which the comparison cannot show. |
| 6a | Remote check off | `StatusFacade.kt:105-106` returns `VERSION` as the latest version when the check is off, so the page would say "Up to date" without any check having run. `reposilite-backend/src/test/kotlin/com/reposilite/status/RemoteVersionCheckRequestTest.kt:92-96` pins this behaviour on purpose. | Real, backend. Not changed here; see open question 2. |
| 7 | Memory and threads charts without a legend | `TimeSeriesChart.vue:59` and `:84` show a legend when there are two or more series. The cluster gives one series per instance (`ResourceTrends.vue`, the `seriesPerInstance` helper). The code is right and no test pins it. | Not reproduced from the code. The change pins the legend with a test. If staging still shows none, the cause is in the data (open question 4). |
| 8 | Instance cards and cluster cards repeat each other | `InstanceStatusPanel.vue:254-293` (instance tiles) and `:310-342` (cluster tiles) show the same five figures. | Real. |
| 9 | Chart subtitles | `ResourceTrends.vue:112` "Megabytes in use" and `:126` "Threads in use". "in use" is the word the threads tile misuses. | Real, wording. |

Nothing in the list needs a new metric or a change to a REST response.

## Decision: no backend change in this change

No backend number is computed wrong. `usedThreads`, `maxThreads`, `usedMemory`, `maxMemory` and `failuresCount` each do what
their name and `docs/gitbook/how-to/monitor-status-and-failures.md` say. The defects are the pairing and the wording on the page.
A backend change would change REST responses, which this change must not do, so the fix is in the dashboard. No backend unit
test is written for the same reason.

The one backend defect found (6a, `latestVersion` while the check is off) is a real inconsistency with the documented meaning of
`<unknown>`. It is pinned by a test as a deliberate choice, and changing it changes a REST value for installations that switch the
check off. That is a maintainer decision. It is recorded as open question 2 and is not split into a `fix(status)` change, so this
change ships under one type and one scope.

## Decision: the switch moves to the Maintenance page

The switch goes to `/maintenance`, the page labelled Maintenance in the Support group of the Administration tree
(`reposilite-frontend/src/navigation/registry.js:192`). Reasons:

- It is gated by the node the API checks, `ingot.admin.maintenance`. The page's permission and the API's permission are one
  thing, so a visitor who can open the page can use the switch, and nobody else sees a control that would fail.
- The page already holds the actions that change data or pause work: storage usage, the search index rebuild and the cleanup
  runs (`reposilite-frontend/src/components/maintenance/MaintenanceView.vue`). A write freeze belongs with them.
- The dashboard is for reading. It keeps the status.

The alternative, the System group, holds configuration domains. A runtime switch that changes the write path of every replica
does not belong in a list of settings pages.

On the Maintenance page the switch sits in its own section, above the other panels, labelled "Writes", with the status line and
the description that the accessibility rules need.

## Decision: the dialog states the consequence, and only turning on asks

Turning read-only on opens a `vue-final-modal` dialog, the same kind as `components/maintenance/RebuildIndexDialog.vue`. The dialog
names the effect: every upload, deploy and delete is refused with `503` on every instance until read-only is turned off, reads keep
working, and cleanup and index runs wait. Its confirm button says what it does ("Turn on read-only mode"). Turning it off is one
click, because it only restores writes.

While `INGOT_READ_ONLY` forces the state, the switch is disabled and its text names the variable. This is the case the API answers
with `409` (`ReadOnlyEndpoints.kt:105`), so the reason is shown before the click.

## Decision: the wording of the figures

| Card | Value | Detail | Meter | Why |
|---|---|---|---|---|
| Used memory | heap in use, MB | "of 256 MB maximum heap" | yes, heap in use divided by the maximum heap | "available" read as free memory. The bar already divides by the maximum. |
| Live threads | JVM thread count | "Threads in this JVM" | no | No limit in the API counts the same threads. The pool sizes are a different thing. |

A bar that cannot show what it divides by is removed. The meter stays where the reading has a true limit. Its `aria-valuenow` is
clamped to 0 to 100, and its `aria-valuetext` gives the value and the limit in words.

## Decision: the cluster layout

With a cluster, the page reads top to bottom as the cluster summary, then one row per instance, then the charts. The per-instance
tiles above the summary are removed, because the summary and the rows carry the same figures.

| Block | Shown | Content |
|---|---|---|
| Read-only banner | only while read-only is on | state, scope (cluster or this instance), and a link to Maintenance for a visitor with the node |
| Cluster summary | with a cluster | Cluster memory (sum of heap in use, of the sum of maximum heaps), Cluster threads (sum, no limit), Cluster failures (sum of distinct records per instance), Shortest uptime, Cluster version, Cluster health (worst instance, with "N of M instances up") |
| Instance rows | with a cluster | one row per instance with labelled fields: Version, Uptime, Heap, Threads, Failures, Health. The first row carries "This instance", because `StatusApi.kt` documents the local instance as first. |
| Single-instance tiles | without a cluster | Used memory, Live threads, Failures, Uptime, Version, and the health row |

An installation without a bus keeps the single-instance tiles, with the new wording. Nothing else changes for it.

## Decision: one health statement per view

With a cluster, the cluster summary's health tile is the statement. The health row keeps only its detail lines and the integration
links, without its own "Healthy" label. Without a cluster, the health row's label is the statement, as today.

## Card table

Every card on the page: what it measures, where its number comes from, and its unit. The last column is the wording this change
gives it.

| Card | Measures | Source (field and code) | Unit | Shows | After this change |
|---|---|---|---|---|---|
| Used memory | heap in use of this JVM | `usedMemory`, `StatusFacade.kt:180-181` | MB, one decimal | always | "of N MB maximum heap", with meter |
| Live threads | live threads of this JVM | `usedThreads`, `StatusFacade.kt:177-178` | count | always | "Threads in this JVM", no meter |
| Failures | distinct failure records since the last restart | `failuresCount`, `StatusFacade.kt:151`, `FailureFacade.kt:120-121` | count | always; opens the list when above zero | subline "None since the last restart", or "N distinct since the last restart" |
| Uptime | time since the last restart | `uptime`, `StatusFacade.kt:146` | duration | always | unchanged |
| Version | the running build's version, and the newest release | `version`, `build.gradle.kts:43`; `latestVersion`, `StatusFacade.kt:105-106, 145` | text | always | "Same as latest release", or "Release X is out"; never "Up to date" |
| Read-only banner | whether writes are refused, and by what | `readOnly`, `readOnlySource`, `StatusFacade.kt:152-153` | state | only when on | scope and link; no actor or time |
| Health row | this instance's health and its detail lines | `GET /api/status/health`, `instanceHealth.js` | Healthy, Unhealthy, Health unavailable | always | the single label without a cluster; details only with one |
| Cluster memory | heap in use, summed over instances | `cluster.instances[].usedMemory` | MB | with a cluster | "of N MB maximum heap in total" |
| Cluster threads | live threads, summed over instances | `cluster.instances[].usedThreads` | count | with a cluster | "Live threads on all instances", no limit |
| Cluster failures | distinct failure records, summed over instances | `cluster.instances[].failuresCount` | count | with a cluster | "counted per instance" |
| Shortest uptime | time since the most recent restart in the cluster | `cluster.instances[].uptimeStart` | duration | with a cluster | unchanged |
| Cluster version | the one version, or "mixed" | `cluster.instances[].version` | text | with a cluster | unchanged |
| Cluster health | the worst instance's health | `cluster.instances[].health` | Healthy, Unhealthy, Health unavailable | with a cluster | "N of M instances up" as detail; the only health statement with a cluster |
| Memory chart | heap in use over time, one line per instance | `samples[].memory`, or `/api/status/snapshots` without a cluster | MB | always | subtitle "Heap in use, MB, recent history" |
| Threads chart | live threads over time, one line per instance | `samples[].threads`, or `/api/status/snapshots` without a cluster | count | always | subtitle "Live threads, recent history" |
| Switch (Maintenance page) | the stored read-only state | `GET`, `POST`, `DELETE /api/admin/read-only` | on or off | visitors with the maintenance node | dialog on turn-on; disabled with its reason when the environment forces it |

## Accessibility

- The switch is an `MdSwitch`. It forwards `aria-describedby` to the `button role="switch"` (`reposilite-frontend/src/components/md/MdSwitch.vue`,
  which sets `inheritAttrs: false` and passes the attributes to the switch). The description is a paragraph with the consequence,
  rendered under the label. Its text matches the first sentence of the dialog, so a screen reader user hears it on focus.
- The dialog is a modal with a focus trap, and Escape cancels it. Focus returns to the switch when it closes.
- Meters use `role="meter"` with `aria-valuenow` in 0 to 100, `aria-valuemin`, `aria-valuemax` and an `aria-valuetext` such as
  "91.6 of 256 MB maximum heap in use, 36 percent". The words carry the value; the percentage alone does not.
- The cluster instances form a list whose items have labelled fields, read in order, with no meaning carried by colour alone.
- The status banner keeps `role="status"`. The failures tile opens its dialog only when the count is above zero, as it does today.

## Tests

Frontend tests follow the project's F.I.R.S.T. rules: no real timers for polling, no network, a mocked session and client, and a
fixed clock wherever uptime is computed. Each test is written and seen to fail before the code it covers.

- `InstanceStatusPanel.test.js`: no switch in the panel; the banner without a switch; the banner link only with the node; the memory
  detail wording; threads without "of" and without a meter; the failures subline; the version label never "Up to date"; a cluster
  shows the summary and the rows and no per-instance tiles; one health statement.
- `ReadOnlyToggle.test.js`: the dialog is required before any request; Cancel sends nothing; the switch is linked to its description
  with `aria-describedby`; a forced state disables the switch and names the variable.
- `StatTile.test.js` (new): a ratio above 1 gives `aria-valuenow` 100 and an `aria-valuetext`.
- `TimeSeriesChart.test.js` (new): the option has a legend for two series and none for one.
- `MaintenanceView.test.js`: the switch is on the page; a 403 probe hides it.

## Open questions

Each question has a default, which the tasks implement. A maintainer can change a default without changing the rest of the design.

1. **Who and when on the banner.** Default: not shown. The read-only API returns `{enabled, source}` only. The audit log holds the
   actor and the time (`READ_ONLY_CHANGED`, `docs/gitbook/reference/audit-events.md:103`), and the banner links to the Maintenance
   page, not to the log. Adding `changedBy` and `changedAt` to `GET /api/admin/read-only` is an additive change of its own.
2. **`latestVersion` while the remote check is off.** Default: unchanged. The dashboard cannot tell "checked and current" from "not
   checked", so with the check off the page says "Same as latest release" as well. The fix is to answer `<unknown>` when the check is
   off, which matches the documented meaning of `<unknown>` (`docs/gitbook/how-to/monitor-status-and-failures.md:55`). It changes a
   REST value for installations that switch the check off, and `RemoteVersionCheckRequestTest.kt:96` pins the current value. It needs
   the maintainer's decision.
3. **Build identity of a nightly.** Default: the page shows the version only. A commit and a "nightly" marker need an additive field
   on `GET /api/status/instance` and a build property. That is a separate `feat(status)` change.
4. **Legend not seen on staging.** Default: the test pins the legend for two series. If staging still shows no legend, the next step
   is to check whether the cluster sends two series at that moment (the instances list of `/api/status/instance`), not to change the
   rule.
5. **"This instance" row.** Default: the first instance row carries the label, because `StatusApi.kt` documents the local instance
   first. If the bus stopped doing that, the label would be wrong. A field that names the local instance would fix it, as an additive
   change.
6. **Failures with a cluster.** Default: the cluster failures tile shows the sum, and the list opens from the first (this instance)
   row only, because the list is local (`FailureFacade.kt:120-121`). The list does not show other instances' failures.

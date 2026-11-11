# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ui`. Tests are written first and
seen to fail before the production code changes. Every test follows F.I.R.S.T.: no sleeps, no real timers for polling, no system
clock (a fixed clock where uptime is computed), no shared mutable state, no network, and assertions with messages. Frontend tests run
as `npx vitest run <files>` in `reposilite-frontend`. No backend test is written, because no backend number changes (see the design).

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose fix-dashboard-status`: this change and nothing else. Verify:
  `openspec validate fix-dashboard-status --strict` passes and the dash grep from the proposal prints nothing.

## 2. Read-only switch moves to the Maintenance page

- [ ] 2.1 `test(ui)`: write failing tests:
  - `InstanceStatusPanel.test.js`: the panel renders no `MdSwitch` and no `ReadOnlyToggle`;
  - the banner shows no switch, shows the link to Maintenance only for a principal with the maintenance node, and shows the text that a
    maintenance administrator can turn it off for everyone else.
  - `ReadOnlyToggle.test.js`: activating the switch while off opens the dialog and sends no request; the confirm button sends one `POST`;
    Cancel and Escape send none and leave the switch off; turning off sends `DELETE` without a dialog; the switch carries
    `aria-describedby` pointing at the consequence text; a forced state disables the switch and names `INGOT_READ_ONLY`.
  - `MaintenanceView.test.js`: the Maintenance page shows the switch in a section labelled "Writes"; a `403` on the probe hides it.
  Verify: they fail before 2.2.
- [ ] 2.2 `fix(ui)`: remove `ReadOnlyToggle` from `InstanceStatusPanel.vue`; add the confirmation dialog (`vue-final-modal`, as in
  `components/maintenance/RebuildIndexDialog.vue`) and the description to `ReadOnlyToggle.vue`; render the switch as a section of
  `components/maintenance/MaintenanceView.vue`; add the link to `/maintenance` to the banner for the principal with the node. Verify: the
  tests of 2.1 pass and the existing `ReadOnlyToggle.test.js` cases for the 403 probe still pass.

## 3. Figures, rows, health and legends

- [ ] 3.1 `test(ui)`: write failing tests:
  - `StatTile.test.js` (new): a ratio above 1 gives `aria-valuenow="100"`; `aria-valuetext` gives the value and the limit in words;
  - `InstanceStatusPanel.test.js`: the memory detail reads "of N MB maximum heap"; the threads card reads "Live threads" with no "of"
    and no meter; the failures subline reads "None since the last restart" at zero and never repeats the count; the version label is
    "Same as latest release" for equal versions and never "Up to date"; a cluster shows the summary and the instance rows, and no
    per-instance tile above it; an instance row names the unit on each figure; the cluster health card is the only "Healthy" statement
    with a cluster, and reads "2 of 2 instances up" for two up instances;
  - `TimeSeriesChart.test.js` (new): the option has a legend for two series and none for one, with the instance names as series names;
  - `ResourceTrends.test.js`: the subtitles name "heap in use, MB" and "live threads".
  Verify: they fail before 3.2.
- [ ] 3.2 `fix(ui)`: change `InstanceStatusPanel.vue` (wording, the cluster layout, the single health statement, the failures subline,
  the version label, the rows), `StatTile.vue` (clamp and value text of the meter), `clusterStatus.js` (the health detail), `InstanceHealthRow.vue`
  (no label with a cluster), `ResourceTrends.vue` (subtitles) and, only if the legend test fails, `TimeSeriesChart.vue`. Verify: the tests of
  3.1 pass, and the existing `InstanceStatusPanel.test.js`, `clusterStatus.test.js` and `InstanceHealthRow.test.js` cases that describe the
  old wording are updated in the same commit with the new wording and nothing else.
- [ ] 3.3 `test(ui)`: run `npx vitest run` on `src/components/dashboard` and `src/components/maintenance`, and `npm run eslint` on the changed
  files. Verify: nothing fails that did not fail on main, and the commit has no change outside the changed files.

## 4. Operator documentation

- [ ] 4.1 `docs(ui)`: update `docs/gitbook/reference/dashboard-pages.md`: the `/dashboard` row names the banner and says the switch is on the
  Maintenance page; the `/maintenance` row names the read-only switch and who may use it.
- [ ] 4.2 `docs(ui)`: update `docs/gitbook/how-to/freeze-writes-for-maintenance.md`: the dashboard steps open Maintenance (Administration,
  Support) instead of the status page, and describe the confirmation.
- [ ] 4.3 `docs(ui)`: update `docs/gitbook/how-to/monitor-status-and-failures.md`: `usedMemory` and `maxMemory` are described as heap in use
  and maximum heap; `usedThreads` as live threads, and `maxThreads` as the pool sizes, stated as not comparable on the dashboard; the
  failures count as distinct records per instance since the last restart; `latestVersion` as the newest release, with the dashboard wording
  "Same as latest release". Verify: each sentence matches the code cited in the design.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `fix(ui): move the read-only switch and correct the dashboard figures`. Read
  `.github/pull_request_template.md` (or the template directory, whichever exists on main) and fill its sections. The description tells the
  story of the administrator who opens the dashboard and sees a switch that stops every upload, and of the figures that did not add up.
  It carries no session link and no attribution line, and it has no em or en dash. Verify: `gh pr view --json title` shows the exact title,
  and the commit-style hook accepts the title and every commit.

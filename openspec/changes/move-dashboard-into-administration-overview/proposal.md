# Proposal

## Why

The maintainer looked at Administration > Overview on staging and asked for one thing: move the Dashboard into the Overview.

Today an operator has two places that both answer "how is this instance". The rail has a Dashboard entry with the instance status,
the resources and the getting started checklist. Administration > Overview has Needs attention, At a glance (the repository and
cleanup policy counts), a one line link that says "Instance status and resources are on the Dashboard", and the Danger zone with
Factory reset. The link is the giveaway: Overview points the operator at another page for the half of the answer it does not hold.
The rail also spends one of its four entries, and one of the four slots of the bottom bar, on a page that only an operator opens.

Overview is also narrow for what it holds. It is a reading page with a 960 pixel measure, so on a 1440 pixel window the counts sit
in a thin column with a blank half beside them, while the status meters, which want width, live on a different page.

The maintainer's screenshot of Overview shows two defects that are not part of the move and ship first on their own: the label
"Factory reset" wraps onto two lines inside its button, and the page title "Overview" is clipped under the top bar when the page is
scrolled.

### Who can see the Dashboard today (checked against origin/main 3a8cf7bd)

- **Anonymous visitor:** no rail entry. `/dashboard` stays at its address and shows the sign in prompt. Every status call
  answers 401.
- **Signed in user without the dashboard permission** (every ordinary user): no rail entry. `/dashboard` shows the "You do not have
  access to this page" state. The calls answer 403.
- **A principal holding `ingot.admin.dashboard`** (an instance administrator with `ingot.admin.*`, or a principal that was given the
  dashboard node alone): rail entry, the page, and its data.

The server enforces the same rule: `GET /api/status/instance`, `/snapshots` and `/failures` (and the failures `DELETE`) are
`managerOnly("dashboard")` in `StatusEndpoints.kt`. Only `/api/status/health` and `/api/status/ready` are open, and those are probes,
not what the page draws. The page route has `meta.node = ingot.admin.dashboard` (`ADMIN_NODES.dashboard`), the same node the
Analytics page uses.

So no ordinary user and no anonymous visitor loses anything when the rail entry goes. The group that is affected is the one that
holds the dashboard node but not the settings node (for example a monitoring operator). The Overview page needs the settings node
today, so moving the content as it is would lock that group out of the status page. That is the one real decision of this change
(design D2).

## What Changes

- **The status moves into Overview.** Overview shows the instance status (with the getting started checklist and the read-only
  banner), the resources and the cluster summary, in sections of their own. The data, the endpoints, the polling and the components
  are the ones the Dashboard uses today.
- **Overview uses the width it is given.** It stops being a reading page. At a glance becomes a grid of counts, the status meters sit
  in a grid that fills the width, the two resource charts sit side by side from 1024 pixels, and the Danger zone is the last block
  of the page.
- **The Dashboard entry leaves the rail and the bottom bar.** The rail holds Browse, Search and Administration. The bottom bar holds
  the same three.
- **`/dashboard` redirects to Overview.** The route keeps its name and its address, so a bookmark, an old `#/...?tab=dashboard` link
  and the server's reserved segment keep working. It replaces the history entry and keeps a query and a hash.
- **A visitor with the dashboard permission and without the settings permission still gets the status.** For them Overview shows the
  status sections only. They see the same content at the same address, and the page names nothing they cannot use.
- **The link line goes.** "Instance status and resources are on the Dashboard" is removed, with its `dashboard` prop.
- **Docs, anchors and e2e follow.** The route table, the rail description and the layout notes of the gitbook are updated. No anchor
  name is removed or renamed (design D5). The e2e specs that open `/dashboard` open Overview, and one spec keeps testing the redirect.
- **Two bug fixes ship first, as their own group** (`fix(ui)`): the Factory reset label stays on one line, and the page title is
  never hidden by the top bar at rest or after a navigation.

## Shipping and commit types

The change is four code pull requests and one docs pull request, in this order. Each is one Conventional Commits type and scope.

1. `fix(ui): keep the factory reset label on one line`
2. `fix(ui): keep the page title clear of the top bar`
3. `feat(ui): show the instance status on the administration overview`
4. `feat(ui): retire the dashboard page and redirect it to the overview`
5. `docs(gitbook): describe the status on the administration overview`

The proposal pull request is `docs(openspec): propose move-dashboard-into-administration-overview`. Pull requests 1 and 2 do not
depend on the rest and may merge first or alone. Pull request 3 is additive (Overview gains the status while the Dashboard still
exists), so it can be reviewed and reverted on its own. Pull request 4 is the visible removal.

## Impact

- **No data change and no API change.** No endpoint, schema, setting, permission node, environment variable, deployment manifest
  or stored value changes. `docs/openapi/ingot-openapi.json` is untouched. The status endpoints keep `managerOnly("dashboard")`.
- **No BREAKING item.** An operator changes nothing. `/dashboard` still opens something useful, plugin selectors that name
  `data-ingot-component="dashboard-view"` or `"settings-hub"` still match, and the reserved path segment `dashboard` stays in
  `dashboard-segments.json`, `DashboardPaths.kt` and the chart copy, so a repository with that name behaves as before. This is a
  visible change for people: the rail loses an entry.
- **Drop-in replacement for Reposilite is unaffected.** Nothing in the image, entrypoint, paths, configuration or plugin API moves.
- **Code touched.** `reposilite-frontend/src/navigation/registry.js`, `src/router/routes.js`, `src/router/create.js`,
  `src/pages/IndexPage.vue`, `src/components/settings/SettingsView.vue`, `SettingsHub.vue`, `src/components/dashboard/DashboardView.vue`,
  the Danger zone markup or `src/components/md/MdButton.vue`, the e2e specs `dashboard`, `layout`, `navigation-chrome` and
  `accessibility`, and the pages under `docs/gitbook/reference/` and `docs/gitbook/how-to/`.

## Out of scope

- The Analytics page. It stays at `/analytics`, in Administration > Monitoring and support.
- Any new chart, meter, status check or change to what a meter means.
- A separate permission for the Overview, or a change to who holds `ingot.admin.dashboard` or `ingot.admin.settings`.
- Removing `/dashboard` from the reserved segments, or any backend or chart change.
- The page header rework of #250 (`rework-admin-header-and-repository-tabs`), the header of the Administration frame, and the
  TypeScript migration (`migrate-dashboard-to-typescript`). This change is sequenced around them (design D8).
- Making the page title sticky. The fix only guarantees that the title is not hidden at rest.

# Proposal

## Why

The page Settings > System information (`/settings/system-info`) and the page Settings > Support bundle
(`/settings/support`) answer one question for an operator: what does this instance run on, and what does a maintainer
need when something goes wrong. Today they are two entries in the navigation, and the first is one long column. It stacks
ten sections one below the other: Ingot, Java with its input arguments, operating system, database, storage, cluster,
plugins with their tier labels, environment variables, system properties and the configuration with its local and shared
parts. An operator who looks for one value scrolls past the others, and a link to one part of the page is only an anchor
in a long document.

The support bundle is a second page for the same information. Its download button sits on a page of its own, one click
away from the page that shows what the bundle contains. An operator who reads the system information and then wants the
zip has to find the second entry.

The maintainer chose this layout for the system information page:

- four tabs: Overview, Plugins, Environment and Configuration;
- an Overview of short cards in a responsive grid, each with its few key values;
- a filter on the long tabs, which narrow rows by key or value;
- the active tab in the address, so a link and a reload keep it, and the existing section anchors keep working.

This change applies the same layout to both pages, and merges them. The system information and the support bundle become
one page with one navigation entry, and the download action is available from every tab.

## What Changes

One change, shipped as one code pull request under `feat(ui)`. The scope is the frontend only: no backend, API or data
change.

- **One page.** `/settings/system-info` becomes the one page. It shows four tabs, Overview, Plugins, Environment and
  Configuration, as described in `design.md`. The Overview holds six cards (Ingot, Java, Operating system, Database,
  Storage, Cluster); the Java card keeps the input arguments in an expandable part. Plugins keeps the tier labels.
  Environment holds the environment variables and the system properties, Configuration the local and the shared
  configuration.
- **Filter.** The Environment and Configuration tabs get a filter field that narrows rows by key or by value. The filter
  works on the values the server already sent and redacted. It cannot show a value that is not on the page.
- **Tab in the address.** The active tab is the query parameter `tab` (`overview`, `plugins`, `environment`,
  `configuration`). An unknown or missing key opens Overview. The section anchors `#support-ingot`, `#support-java`,
  `#support-os`, `#support-database`, `#support-storage`, `#support-cluster`, `#support-plugins`,
  `#support-environment`, `#support-properties` and `#support-configuration` keep working: the page opens the tab that
  holds the section and scrolls to it.
- **Download action in the header.** The download of the support bundle moves into the page header. It is available on
  every tab. The short note on what is removed or masked moves with it, in the same words as today, behind a disclosure
  so the header stays short. The "cannot be checked right now" and "turned off" states of today's support page are kept
  as they are.
- **One navigation entry.** The two entries of the Support group become one entry, `system-info`, labelled
  "System and support". The entry `support` is removed from the navigation tree; its address and its route name keep
  working, see the next point.
- **Old address redirects.** `/settings/support` redirects to `/settings/system-info`. The route name `support` stays
  resolvable, so a bookmark, a link in a plugin and a route name in code keep working. No address answers 404.
- **Anchor contract.** The root of the page stays `data-ingot-component="system-info-view"`. The name `support-view`,
  which the support page registered, is kept as a declared alias of the merged root
  (`data-ingot-component-alias="support-view"`, `replacedBy: system-info-view`), and the reference page lists it with the
  release in which it is removed. Nothing is dropped silently. New parts are registered in
  `reposilite-frontend/src/anchors/registry.json`; `plugin-tier` stays.
- **Documentation.** The reference page of the anchors is regenerated with `npm run anchors:doc`, and the pages of
  `docs/gitbook` that name `/settings/support` as a separate page are updated in the code pull request.

No file under `reposilite-backend/` changes. No file under `openspec/` is part of the code pull request.

## Impact

- **Operators:** one navigation entry, "System and support", instead of two. Its address is `/settings/system-info`.
  Every link and bookmark to `/settings/support` still opens the page, which has the download in its header. The values
  are the same; only their arrangement changes. Nothing is BREAKING.
- **Configuration and data:** no configuration key, environment variable, deployment manifest, permission node, endpoint
  or stored data changes. The new address form is the optional `tab` query on `/settings/system-info`.
- **Plugin authors:** `system-info-view` and `plugin-tier` keep their names. `support-view` keeps working as an alias
  until the release named in the registry. The reference page lists it.
- **Code:** `reposilite-frontend/src/components/support/SystemInfoView.vue` (tabs, cards, filter, anchors),
  `SupportView.vue` (its content moves into the page header and is then removed), the router entry in
  `src/router/routes.ts`, the navigation registry in `src/navigation/registry.ts` and its tests, and
  `src/anchors/registry.json`.
- **Documentation:** `docs/gitbook/references/plugins/dashboard-anchors.md` (regenerated) and every page under
  `docs/gitbook` that describes `/settings/support` as a page of its own.

## Verification

- Every test is written first and seen to fail on the current build (see `tasks.md`).
- Unit tests with vitest: `npm test` in `reposilite-frontend`, targeted while working, through `heavy`.
- End to end with Playwright (`--workers=2`, through `heavy`): the redirect, the tab address, the section anchors, the
  keyboard model and axe on every tab at 320, 600 and 1440 pixels.
- Screenshots of the Overview, each tab and the redirected address, in light and dark, at 320, 600 and 1440 pixels,
  attached to the code pull request.
- `npx -y @fission-ai/openspec validate structure-system-and-support-page --strict` passes.

## Dependencies and open questions

The tab list is the one from `rework-admin-header-and-repository-tabs`, which is not yet merged. This change reuses it
and does not build a second one. If that change has not merged when the code pull request starts, the code pull request
waits for it. See `design.md` D3 and the open questions there, each with a default that the tasks implement.

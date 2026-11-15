# Proposal

## Why

An operator who installs a server plugin gets its routes, its events and a settings form, and nothing else. A plugin that
needs a status page, a button that starts a job or a place in the Administration menu has to ship its own frontend, which the
operator must then serve from the `static` directory (`docs/gitbook/how-to/serve-custom-frontend.md`) or rebuild the dashboard
for. Both break the sentence the product stands on: pick up a plugin, restart, and it works. The Nexus import (N11) is the first
case that cannot wait: a connect, discover and dry run wizard has no honest home today, and a plugin author has no sanctioned
way to give it one.

The obvious fix, letting a plugin ship JavaScript that the dashboard loads, is also the dangerous one. The dashboard origin
holds the administrator's session and a readable CSRF token. A script loaded into it can do whatever that administrator can, and
the dashboard policy (`script-src 'self'`) would allow it, because the script comes from the same host. The split dashboard
image exists so that the piece facing the browser stays small and cannot be talked into much. A plugin script runs against both.

So this change adds the extension point without running any plugin code in the browser. A plugin declares a page as data:
a title, the place in the menu, the permission it needs, and a list of blocks the dashboard already knows how to draw (text,
key and value list, table, form, action button, polled status). The dashboard draws them with its own components. The data
comes from the plugin's own API routes, which the server protects like any other route.

An existing Reposilite plugin does not call the new API, so it shows nothing new and nothing changes for it.

Facts checked against this worktree (main at `86a2089d`):

- `FrontendFacade.registerPlaceholder` (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/FrontendFacade.kt:50`) is the only
  way a plugin reaches the dashboard today. It substitutes text into the bundle. It cannot add a page, and `{{REPOSILITE.*}}`
  stays exactly as it is.
- A plugin's settings domain already appears in Settings: `settingsGroupOf` in
  `reposilite-frontend/src/navigation/registry.js` puts every domain it does not know into a "Plugins" group, and the form is drawn
  from the domain's JSON schema. What is missing is a label and a place chosen by the plugin
  (`openspec/changes/restructure-navigation/design.md`, open question Q5, left it to a server change).
- Plugin routes are protected by `managerOnly(area)` (`reposilite-backend/src/main/kotlin/com/reposilite/shared/ContextDsl.kt:97`).
  The areas are fixed: `NodePattern.ADMIN_AREAS` in `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/model/NodePattern.kt:173`.
- The dashboard policy is `DASHBOARD_POLICY` (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/SecurityHeaders.kt:72`) and the
  split image repeats it (`reposilite-frontend/server/lib/security-headers.js`). `'unsafe-eval'` is already there for Ajv.
- The split image answers `/settings/<anything>` with the document and proxies `/api/*`
  (`reposilite-frontend/server/lib/route-classifier.js`), so a new API endpoint and a new page below `/settings` need no change in it.
- The router matches `/settings/:domain/:collection/:entry` (`reposilite-frontend/src/router/routes.js:77`), so a plugin page route must be placed before it.

## What Changes

- **Plugin API.** A new facade `PluginUiFacade` (plugin name `plugin-ui`, listed in `dependencies` by a plugin that uses it).
  `registerPage(page)` adds a page. `registerSettingsSection(section)` gives an existing settings domain a label and a menu group.
  Both take plain data classes. A plugin that never calls them is unaffected.
- **Page model.** A page has an id, a title, a menu group (`Repository`, `Security`, `System` or `Support`, default `System`), the
  admin area it needs, and up to 30 blocks. Block types in this version: `text`, `keyValue`, `table`, `form`, `action`, `status`.
  There is no block that carries HTML, a script or a style. See `design.md` for the full model and the limits.
- **Permission per page.** Every page names the admin area that opens it (`settings`, `users`, `teams`, `tokens`,
  `maintenance`, `audit`, `console`, `dashboard`, or `*` for the full instance administrator). The server filters the manifest by the
  caller's nodes, and the dashboard checks the same node before it draws the page.
- **Manifest endpoint.** `GET /api/frontend/plugin-ui` returns the pages and settings sections the signed-in caller may use, and
  `enabledPlugins`, the names of the enabled server plugins, so that a first-party page such as the Nexus import wizard can appear
  only while its plugin runs. It answers `401` to an anonymous caller. The bundled dashboard and the split image both read it, because the split image proxies `/api`.
- **Data routes stay the plugin's.** A block reads and writes only below `/api/plugins/<plugin>/`. For a plugin that registered a
  page, the server requires the page's area for every request below that prefix, so a plugin cannot forget the check.
- **Dashboard.** One route `/settings/plugins/:plugin/:page?` (name `settings-plugin`). The navigation registry gains
  contributed entries, shown in the menu group the page names. The group names are those of
  `add-repositories-admin-page`. Until that change lands they sit in the existing "Plugins" category of Settings.
- **No change to the browser policy.** `DASHBOARD_POLICY` and the split image's policy stay byte for byte. Plugin text is rendered
  as text.
- **Switch.** `INGOT_LOCAL_PLUGINUI` (`REPOSILITE_LOCAL_PLUGINUI` as fallback), default `true`. `false` makes the manifest empty and
  hides every contributed page. It exists for an operator who wants the dashboard to show only what Ingot ships.
- **Documentation.** `docs/gitbook/reference/plugin-api.md` (the facade, the block reference, the rules),
  `docs/gitbook/how-to/write-a-plugin.md` (a link and a short example), `docs/gitbook/explanation/security-headers.md` (why the
  policy is unchanged and why plugin script is not loaded), `docs/gitbook/reference/dashboard-pages.md` (the route),
  `docs/gitbook/reference/http-api.md` (the endpoint) and `docs/gitbook/reference/configuration-and-startup.md` (the switch).

### Out of scope

- Loading plugin JavaScript or CSS into the dashboard, in any form (script tag, module import, iframe, web component). The reasons
  and what a later opt-in would have to bring (a separate origin, a signed bundle, a stated policy change) are in `design.md`. This
  change does not decide it.
- A dashboard card (the research listed one). Page, menu entry and settings section are the scope.
- New permission nodes. A page uses one of the existing admin areas. A node per plugin (`ingot.admin.plugin.<id>`) would extend the node grammar of
  `add-permission-nodes` and is a separate change.
- The regrouped navigation itself (Browse, Search, Dashboard, Administration with Repository, Security, System, Support). That is
  `add-repositories-admin-page`. This change only uses its group names and routes.
- The Nexus import page. It is a first-party page in `reposilite-frontend` (`add-nexus-import-wizard`), not a contributed page, and
  needs no block beyond these six. This change only tells it, through `enabledPlugins`, whether the plugin is enabled.
- Signing or verifying plugin jars. A plugin jar is trusted server code today and stays so.
- Any change to the `{{REPOSILITE.*}}` placeholders.

## Capabilities

### New Capabilities

- `plugin-ui-extension`: the facade, the page and block model and its limits, the permission per page, the manifest endpoint, the
  data route guard, the dashboard route and menu entries, and the unchanged browser policy.

## Impact

- **Type and scope.** The change ships under `feat(frontend)`. Commits use `test`, `feat` and `docs` with the scope `frontend`. The
  pull request title, squash-merged onto main, is:

  `feat(frontend): let plugins contribute dashboard pages without loading plugin script`

- **Breaking marker.** No breaking change. Nothing an operator has to change: no configuration key, variable, volume, manifest or
  plugin. The new switch defaults to on, and no existing plugin registers a page, so nothing appears until a plugin author opts in.
  The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None. A Reposilite plugin compiled against 3.5.28 loads and behaves as before. The plugin
  ABI is only added to (`gate-plugin-abi` reports it).
- **Both deploy modes.** The bundled dashboard and the split dashboard image behave the same. The manifest is an API call, which
  the split image already proxies.
- **Dependencies on other changes.**
  - `restructure-navigation` (merged): the registry this change extends. Open question Q5 there is answered here.
  - `add-repositories-admin-page`: defines the Administration groups Repository, Security, System and Support and their routes. This
    change builds on it if it lands first and falls back to the Plugins category of Settings otherwise. Either can land first.
  - `add-permission-nodes` (merged): areas and `*` as the permission of a page. No node is added.
  - `gate-plugin-abi` (not merged): the new facade is additive. Landing after it makes its report the first to show the addition.
  - `add-deploy-modes` (merged): the split image; this change adds a test, not a feature, there.
  - `add-nexus-import-wizard`: reads `enabledPlugins`; it needs this change for that field only.
- **Code.** `reposilite-backend` (frontend plugin, web filter, local configuration), `reposilite-frontend` (registry, router, page
  and block components, api client), `docs/gitbook/`.

## Verification

- Targeted tests per commit, run through `heavy` with `--max-workers=2`. Frontend tests with `npm test` in `reposilite-frontend`.
- `openspec validate add-plugin-ui-extension-point --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-ui-extension-point` prints nothing.

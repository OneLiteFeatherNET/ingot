# Proposal

## Why

A dashboard page that a plugin contributes is for administrators today. The manifest endpoint answers `401` to an anonymous caller,
and every page names an admin area (`openspec/specs/plugin-ui-extension/spec.md`, "Manifest endpoint" and "Permission per page").
The Gradle and Minecraft portal plugins (`add-gradle-plugin-portal-plugin`, `add-minecraft-plugin-portal-plugin`) exist for
readers: whoever may read a repository should find its plugins, versions and snippets without an administrator account. The
browse views already answer everyone. `/browse` and `/search` need no sign-in (`reposilite-frontend/src/router/routes.ts`), so
the dashboard has a place for readers, and a plugin has no way to put anything into it.

Facts checked against `main` at `3614657d0`:

- `PluginUiEndpoint` answers `401` to an anonymous caller (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/infrastructure/PluginUiEndpoint.kt`).
- `PluginUiDataGuard` requires an admin area for every request below `/api/plugins/<plugin>/` once the plugin has a page
  (`PluginUiDataGuard.kt`). A data route that an anonymous reader must call would be refused by that guard.
- `PluginUiPage` is a data class with a required `area` (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/PluginUiModel.kt`).
  Adding an optional field to it changes the constructor, which breaks a plugin compiled against the current facade. Reader pages
  therefore get their own class.
- The route `settings-plugin` carries `manager: true` (`routes.ts:90`), so it cannot serve a reader.
- A new top-level dashboard segment is claimed by the split dashboard image unconditionally
  (`reposilite-frontend/server/lib/route-classifier.js`, `classify`). A repository with that id would then stop answering Maven
  clients in split deployments. The address needs the hand-over that `browse` already gets (`DashboardRouteDecision`), see design D3.

## What Changes

- **Plugin API.** `PluginUiFacade.forPlugin(this).registerReaderPage(PluginUiReaderPage(...))` adds a page for readers. It has an id,
  a title, an optional description and blocks. It has no area and no menu group. Its blocks are read-only: `text`, `keyValue` and
  `table` in this change, and the browse blocks of `add-plugin-ui-browse-blocks` once that change lands. `form`, `action` and
  `status` are refused at registration.
- **Manifest for everyone.** `GET /api/frontend/plugin-ui/readers` returns the reader pages to an anonymous caller and to a signed in
  caller alike. It carries no stylesheet and no list of enabled plugins. `GET /api/frontend/plugin-ui` keeps its contract: it still
  answers `401` to an anonymous caller.
- **Data routes.** A GET or HEAD request to a path that a reader page declares as a source is reachable without sign-in. Every other
  request below `/api/plugins/<plugin>/` keeps the rule of `plugin-ui-extension`.
- **Dashboard.** A route `/plugins/<plugin>/<page>` (name `plugin-reader`) draws a reader page. The Browse section lists the reader pages
  under the heading "Plugins". Without a reader page, nothing is listed and the address is not claimed.
- **Switch.** `INGOT_LOCAL_PLUGINREADERS`, read with the fallback `REPOSILITE_LOCAL_PLUGINREADERS`, default `true`. `false` empties the
  reader manifest, answers `404` on the declared reader sources and leaves the address unclaimed.
- **Documentation.** `docs/gitbook/references/plugins/plugin-api.md` (reader pages and the rules of their sources),
  `docs/gitbook/references/http-api/http-api.md` (the endpoint), `docs/gitbook/references/http-api/dashboard-pages.md` (the route),
  `docs/gitbook/references/configuration/configuration-and-startup.md` (the switch) and
  `docs/gitbook/references/architecture/security-headers.md` (unchanged policy, reader pages draw data only).

### Out of scope

- The browse blocks `list`, `code`, `filter` and `link`. They belong to `add-plugin-ui-browse-blocks`.
- The viewer-aware access check that a reader source needs to filter rows. It belongs to `expose-plugin-access-checks`. This change
  documents the contract and does not implement the check.
- Search attributes, facets and the enrichment hook (`add-search-document-attributes`, `add-search-attribute-facets`,
  `add-artifact-enrichment-hook`).
- Writes for anonymous callers. A reader page is read-only, and nothing in this change lets an anonymous request change state.
- Plugin JavaScript, CSS, frames or HTML in the browser. The reasons are in `add-plugin-ui-extension-point/design.md`, and they hold.
- Icons and static assets for readers (`add-plugin-asset-routes`, optional and not written).
- Any change to the signed-in manifest, to the browser policy, to `{{REPOSILITE.*}}` placeholders, or to an existing permission node.

## Capabilities

### New Capabilities

- `plugin-reader-pages`: reader pages as data, their validation, the anonymous manifest, the anonymous data sources and their
  guard, the Browse entry, the reserved address and the switch.

### Modified Capabilities

- `plugin-ui-extension`: the data route guard gets one exception, the declared reader sources.

## Impact

- **Type and scope.** The change ships under `feat(frontend)`. Test commits use `test(frontend)`, docs commits `docs(frontend)`. The
  pull request title, squash-merged onto main, is:

  `feat(frontend): let plugins contribute pages for readers`

- **Breaking marker.** No breaking change. No operator has to change a configuration key, a variable, a volume, a manifest, an image
  or a plugin. The new switch is optional and defaults to `true`, and nothing appears until a plugin registers a reader page. The
  title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None for an installation without a reader page. The data guard keeps its behaviour for
  every plugin that registers no reader page. The address `/plugins/...` is claimed only while a reader page is registered. For a
  repository named `plugins`, Maven and Gradle clients keep their answers, and a browser that navigates to `/plugins` gets the
  dashboard while such a page exists. The warning in design D3 names that repository.
- **Both deploy modes.** The bundled dashboard and the split dashboard image receive the same manifest, because the split image
  already proxies `/api/*`. The split image answers `/plugins/...` only for browser navigation (design D3). A test covers that.
- **Dependencies.**
  - `add-plugin-ui-extension-point` (merged, archived): the facade, the registrar and the guard that this change extends.
  - `restructure-navigation` (merged, archived): the registry that the Browse heading is added to.
  - `expose-plugin-access-checks` (other agent, Minecraft numbering G7): every reader source has to filter its rows with a viewer-aware
    check. The change can land without it, because no shipped plugin registers a reader page. No plugin should register one before it
    lands.
  - `add-plugin-ui-browse-blocks` (G6, soft): it adds the blocks that reader pages need. Until then reader pages use `text`, `keyValue`
    and `table`. The two changes can land in either order.
  - `extend-plugin-abi-gate` (G8, soft): it makes the new facade types visible in the report. It does not block this change.
  - Consumers: `add-gradle-plugin-portal-plugin` and `add-minecraft-plugin-portal-plugin`.
- **Code.** `reposilite-backend` (frontend plugin, `PluginUiPlugin`, the facade, the endpoint and the guard), `reposilite-frontend`
  (router, registry, a reader view, store, the split server classifier), `docs/gitbook/`.

## Verification

- Each code pull request runs its targeted tests through `heavy` with `--max-workers=2`: the facade and validator tests as unit tests,
  the endpoint and guard as integration tests with `:reposilite-backend:integration --tests '<pattern>'`. Frontend tests run with
  `npm test` in `reposilite-frontend`, and the split server tests in `reposilite-frontend/server`.
- `openspec validate add-plugin-reader-pages --strict --no-interactive` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-reader-pages` prints nothing.

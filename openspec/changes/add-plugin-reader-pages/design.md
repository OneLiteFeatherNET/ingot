# Design

Paths are relative to the repository root. Backend paths start at `reposilite-backend/src/main/kotlin/com/reposilite/`, frontend
paths at `reposilite-frontend/`.

## Context: what the plugin UI does today

- **The facade.** `PluginUiFacade.forPlugin(this)` returns a registrar. `registerPage` takes a `PluginUiPage` with a required
  `area` (`frontend/api/PluginUiModel.kt`, `frontend/api/PluginUiFacade.kt`). Every page is admin only.
- **The manifest.** `GET /api/frontend/plugin-ui` runs inside `authenticated { }` (`frontend/infrastructure/PluginUiEndpoint.kt`).
  It filters pages by `requireArea` and reports `enabledPlugins` for signed-in callers.
- **The data guard.** `PluginUiDataGuard` is a `BEFORE` route on `/api/plugins/*`. For a plugin with pages it demands one of the page
  areas after authentication, and it answers `401` or `403` (`frontend/infrastructure/PluginUiDataGuard.kt`). The plugin name is the
  first segment after `/api/plugins/`.
- **The dashboard route.** `settings-plugin` (`/settings/plugins/:plugin/:page?`) carries `manager: true`
  (`reposilite-frontend/src/router/routes.ts:90`). `IndexPage.vue` checks the page node before it draws the page, and it loads the
  manifest only after sign in (`src/store/pluginUi.ts`, `src/pages/IndexPage.vue:149`).
- **Anonymous browse.** `browse` and `search` answer everyone (`routes.ts`, `docs/gitbook/references/http-api/dashboard-pages.md`).
  `DashboardPaths.APP_ROUTES` (`frontend/DashboardPaths.kt`) lists the server's dashboard segments, and
  `dashboard-segments.json` lists the same segments for the frontend.
- **Shared paths.** `FrontendHandler` registers each app route with a hand-over: when a repository with the same id exists, a
  Maven request still goes to the repository and only a browser navigation gets the dashboard (`DashboardRouteDecision`, comment
  at `frontend/infrastructure/FrontendHandler.kt:122`). `MavenPlugin` warns about such ids (`DashboardPaths.reservedRepositoryWarnings`).
- **The split image.** `reposilite-frontend/server/lib/route-classifier.js` (`classify`) answers every listed segment with the
  document and proxies the rest. It does not look at `Sec-Fetch-Mode`. A segment added to the list therefore shadows a repository
  of that name in split deployments, for every request.
- **Stylesheets.** `PluginStylesheetEndpoint` serves CSS without sign in. The manifest lists the sheets only for signed-in callers
  (`openspec/specs/plugin-stylesheet-channel/spec.md`, "The manifest lists stylesheets for signed-in callers").
- **Placeholders.** `FrontendFacade.registerPlaceholder` (`frontend/FrontendFacade.kt:50`) splices plugin values into served
  dashboard files. Reader pages do not use it. See "Known channel" below.

## The decision that shapes this change: data for readers too

The archived change (`add-plugin-ui-extension-point/design.md`) decided that a plugin contributes data, never script. That decision
holds for readers, because an anonymous visitor is the least trusted viewer of all. Nothing in this design loads plugin code in the
browser, adds a source to the policy, or lets a plugin choose an address. The change only decides who may read which declared data
route, and how the dashboard places the page.

## Security model

### Trust boundaries

| Thing | Where it runs | What it may decide |
| --- | --- | --- |
| Plugin code | The server JVM | Everything the server does for its own routes. Unchanged by this change. |
| Reader declaration | Built in the server, validated, sent as JSON | Words, order, which of its own routes the blocks call, and that a route is readable without sign in. |
| Reader source route | The plugin, on the server | The rows an anonymous or signed-in viewer gets. It must filter them with the viewer's rights (`expose-plugin-access-checks`). |
| Block components | The dashboard, from its own build | Drawing the declaration. They send only the calls the declaration allows. |
| Browser | The visitor | Nothing from the plugin, except the text and the rows it was sent. |

The new trust statement is this: an anonymous visitor may read what a declared reader source returns, and the plugin decides what that
is. The core decides only which requests reach the source. The core cannot check that the plugin filters rows, so the filter is a
requirement on the plugin, documented, reviewed, and covered by the tests of `expose-plugin-access-checks`.

### Threats and the control for each

| # | Threat | Control |
| --- | --- | --- |
| T1 | Plugin script runs in another plugin's context, or in the administrator's origin | No script exists in the model. Each block path must start with `/api/plugins/<plugin>/`, where `<plugin>` is taken from the registering plugin, so a plugin cannot name another plugin's routes (`PluginUiValidator.pathProblem`). The dashboard checks the same prefix before any call (`pluginBlocks.ts`, `pathAllowed`). |
| T2 | Plugin A makes its reader source open a route of plugin B | The exemption set is keyed by the plugin segment of the request path and holds only paths that plugin declared. Another plugin's path is never in it. |
| T3 | An anonymous request reaches an admin route of the same plugin | The exemption is exact: method `GET` or `HEAD` and a path equal to a declared source. Any other method, any other path, and any path that contains `..`, `//`, an empty segment or a percent-encoded character fails closed, so the admin floor applies and the answer is `401`. |
| T4 | A reader page writes state | Registration refuses `form`, `action` and `status` in a reader page. The dashboard refuses to draw them and sends no request. Sources must be `GET` paths. |
| T5 | Existence leak: a reader learns that a repository or item exists | Reader pages list only the page, never data. The source decides: an item the viewer may not read answers `404` (`expose-plugin-access-checks`, D4). A reader manifest is public by declaration; the risk is accepted under Q5. |
| T6 | A cached answer for one viewer reaches another | The guard adds `Vary: Cookie, Authorization` to answers of reader sources and `Cache-Control: private, no-cache` unless the plugin set its own header. |
| T7 | Plugin CSS changes the look of pages for anonymous visitors, or hides content | The reader manifest carries no stylesheet (D8). A signed-in visitor gets the sheet as before. |
| T8 | An anonymous request exhausts the server | Reader sources run under the server's existing request limits and overload protection (`openspec/specs/http-overload-protection`). This change adds no bypass. A source should bound its own work, which is a documented requirement. |
| T9 | A reader page carries a link to script or to another origin | The model has no link block here. The browse-blocks change adds one with a same-origin relative path only, and it refuses `//`, schemes and `/api/` (`add-plugin-ui-browse-blocks`). |
| T10 | A repository named `plugins` stops answering Maven clients | The address is claimed only while a reader page exists. The split image answers `/plugins/...` only for browser navigation. Maven clients always reach the repository (D3). |
| T11 | Plugin text reaches served HTML through a placeholder | Reader pages do not register placeholders, and their text is drawn as data. The existing placeholder channel is outside this change (see Known channel). |

### Known channel: `registerPlaceholder`

`FrontendFacade.registerPlaceholder` (`frontend/FrontendFacade.kt:50`) lets any plugin splice a value into the dashboard files that
contain the placeholder. Only the JavaScript path escapes the value (`escapeForJsString`, line 63). That is an existing channel that
this change neither opens nor closes. The reader security model does not rely on it. Restricting it to asset files is not part of this change (Q6, settled
2026-11-17).

## Decisions

### D1: a separate class for reader pages

`PluginUiReaderPage(id, title, description, blocks)` is a new data class. It has no `area`, no `group` and no menu field. Changing
`PluginUiPage` would change a constructor that compiled plugins call, and the binary report of `extend-plugin-abi-gate` would flag
it. The blocks are the existing sealed `PluginUiBlock` types. Reader pages accept a subset of them, and the browse blocks of
`add-plugin-ui-browse-blocks` join that subset when they land.

Limits match an admin page: id `[a-z0-9-]{1,32}`, title 1 to 60 characters, description at most 200, 1 to 30 blocks, at most 20 reader
pages per plugin, and the serialised reader pages of one plugin within 64 KiB. Admin pages and reader pages have separate counts.

### D2: registration rules

`PluginUiValidator` gets a function `readerPageProblems(plugin, page)`. It refuses:

- `Form`, `Action` and `Status`, each with the block index and the page id in the warning;
- a `source` that is not a path under `/api/plugins/<plugin>/` (the existing `pathProblem`).

A refused reader page is dropped with a warning, as an admin page is. The server starts and the other pages are served.

### D3: the address and the reserved segment

Reader pages live at `/plugins/<plugin>/<page>`. The segment `plugins` is new. It is handled as follows.

1. **Claimed only while needed.** The backend registers the route `/plugins/<path>` at `RoutingSetupEvent` only when at least one reader
   page is registered by then. Plugins initialise before routing, so the check is final at that point. Without a reader page the path
   is not claimed, and an instance without such a plugin is unchanged.
2. **Same hand-over as `browse`.** The route reuses `DashboardRouteDecision`. A repository named `plugins` keeps answering Maven
   and Gradle clients, and only a browser navigation to `/plugins/...` gets the dashboard.
3. **Split image.** `route-classifier.js` gets a second list, `navigationSegments`, that holds `plugins`. `classify` answers those
   segments with the document only when the request is a browser navigation (`Sec-Fetch-Mode: navigate`). For any other request the
   split image proxies, as it does today. The list `dashboard-segments.json` keeps its first list unchanged, so the router test that
   compares the frontend and the backend list still passes.
4. **Warning.** When reader pages are registered and a repository has the id `plugins`, the startup log names the repository and says
   that browser navigation to `/plugins` shows the dashboard. `DashboardPaths.RESERVED_SEGMENTS` stays unchanged, so an instance
   without reader pages gets no new warning.

The known trade-off is the one that `browse` already has. A browser that opens a repository named `plugins` is shown the dashboard
while a reader page exists. Q2 is settled: the address stays and the trade-off is accepted.

### D4: the anonymous manifest

`GET /api/frontend/plugin-ui/readers` returns `{ "version": 1, "pages": [...] }` with the reader pages of every enabled plugin. It
answers anonymous and signed-in callers alike, sends `Cache-Control: private, no-cache`, and is served through `/api/*` by the split
image without a change. It returns no stylesheet and no `enabledPlugins` list (D8, and the existing names-only rule for signed-in
callers).

`GET /api/frontend/plugin-ui` keeps its contract. It answers `401` to an anonymous caller and it does not repeat the reader pages. The
dashboard reads the reader manifest for every visitor, and the signed-in manifest for the admin pages.

With `INGOT_LOCAL_PLUGINREADERS=false`, the reader manifest is `{ "version": 1, "pages": [] }`.

### D5: the data guard's exception

`PluginUiDataGuard` gets a second lookup next to `areasGuarding(plugin)`: `readerSources(plugin, method, path)`. The rules, in this
order:

1. If the request is not `GET` or `HEAD`, the existing rule applies.
2. If the path contains `..`, `//`, an empty segment or `%`, the existing rule applies. A path the guard cannot read exactly is not
   exempt.
3. If the path, after the prefix `/api/plugins/<plugin>/`, equals a source of a reader page of this plugin, the guard lets the request
   through without authentication, and it adds the `Vary` and `Cache-Control` headers of T6. The admin floor is not checked for it.
4. If the reader switch is off, the same request answers `404` with the usual problem body, and the plugin handler does not run.
5. Otherwise the existing rule applies. For a plugin with no admin page that rule does nothing, as before.

The exemption set is computed at registration from the reader pages, so a request costs one set lookup.

### D6: the viewer context

The core passes no viewer object to a reader source. The handler reads the caller from the request, as every plugin route does. The
contract that the handler must follow is documented with the reader pages: a source returns only rows the viewer may read, it answers
`404` for an item that is not readable, and it bounds the size of its answer. The reader page documentation links to the access check
of `expose-plugin-access-checks`.

### D7: the Browse entry and the route

The router gets one route `{ name: 'plugin-reader', path: '/plugins/:plugin/:page?' }` without `manager` meta. The page is drawn by
`PluginPageView.vue`, reused with a prop for the reader audience, so the blocks are drawn by the same `PluginBlock.vue`. The page
shows the description and the plugin name, as admin pages do.

In `registry.ts` a new function `readerPages()` returns the reader pages of the manifest. The Browse rail (`railGroups`) gets a heading
"Plugins" after the browse entries, listing the reader pages in manifest order. When the list is empty, the heading is not drawn. A reader
page is never in the bottom bar. `unlistedRoutes` accepts `plugin-reader` as an exempt route, as it does `settings-plugin`.

Reader pages load for everyone. `pluginUi.ts` reads the reader manifest at start and reads the signed-in manifest after sign in. A
visitor who is not signed in gets no sign-in prompt on a reader page.

Unsupported blocks show the same note as today: a block type that the dashboard does not know, or a form, action or status in a reader
page, is drawn as "Unsupported block".

### D8: no stylesheet for readers

The reader manifest carries no stylesheet. An anonymous visitor sees the dashboard's own styles on a reader page, and a signed-in visitor
sees the sheet as before. This keeps the sheet decision of `plugin-stylesheet-channel` for the signed-in dashboard, and it means that a
plugin sheet is never the only thing an anonymous visitor sees. Q3 is settled: no sheet is listed for readers.

### D9: the switch

`INGOT_LOCAL_PLUGINREADERS`, read as `REPOSILITE_LOCAL_PLUGINREADERS`, default `true`, read through `LocalConfiguration` the same way
as `pluginUi` (`frontend/application/PluginUiPlugin.kt`). `false` empties the reader manifest, answers `404` for reader sources (D5),
and leaves the address unclaimed (D3). `INGOT_LOCAL_PLUGINUI=false` already switches every contribution off, and it also covers readers.

The default is `true` because installing a plugin is the opt-in, and the switch exists for an operator who does not want readers at all.
Q1 is settled: the default stays `true`.

### D10: the browser policy

`DASHBOARD_POLICY` and the split image's copy stay byte for byte. A reader page loads nothing new: no script, no frame, no image, no font.

## Alternatives considered

- **Optional `area` on `PluginUiPage`.** Rejected: it changes a constructor that compiled plugins call (D1), and it mixes two audiences in
  one type that has an area everywhere else.
- **A new top-level segment without a claim rule** (for example `/reader`). Rejected: the split image would shadow a repository of that name
  for every request, which breaks a drop-in installation (D3).
- **A separate origin for reader pages.** Rejected for this change. It needs a host, a policy and a protocol, and the reader cases here are
  data, not code.
- **Reader pages in the signed-in manifest only.** Rejected: an anonymous reader would get nothing, which is the purpose of the change.
- **Letting a reader source skip the guard entirely.** Rejected: the exact-path exemption (D5) keeps every admin route under the floor.

## Risks

- **A plugin filters its rows wrongly.** The core cannot tell. Mitigation: the documented contract (D6), the access check of
  `expose-plugin-access-checks`, and a test in each plugin repository that asks for a private repository as an anonymous visitor.
- **Reader sources add load.** Mitigation: existing request limits (T8), and the guidance to bound answers.
- **A plugin changes its reader page after a release.** The reader manifest is rebuilt on each start, as the admin manifest is, so
  nothing is cached across restarts.
- **The reserved segment surprises a browser user** on an instance with a repository named `plugins` and a reader page. Mitigation: the
  warning (D3) and Q2.

## Open questions, settled 2026-11-17

The maintainer settled every question with the default recommended here. The answers are binding for the code pull requests.

- **Q1. Switch default.** Settled 2026-11-17: `true`. Readers are an explicit opt-in by installing a plugin that registers a reader
  page, and `false` remains available to the operator.
- **Q2. Address.** Settled 2026-11-17: `/plugins/<plugin>/<page>`, with the trade-off of D3. A query on `/browse` is not used.
- **Q3. Stylesheet for readers.** Settled 2026-11-17: none (D8).
- **Q4. Where readers sit.** Settled 2026-11-17: the Browse section, under "Plugins". No top-level tab is added.
- **Q5. Plugin names for anonymous visitors.** Settled 2026-11-17: reader pages name their plugin, so the names of installed reader
  plugins are public. `enabledPlugins` stays signed-in only.
- **Q6. The placeholder channel.** Settled 2026-11-17: left as it is. Restricting `registerPlaceholder` to asset files is not part of
  this change.

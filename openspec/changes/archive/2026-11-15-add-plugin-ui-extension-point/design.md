# Design

## The decision that shapes everything: data, not code

A plugin contributes a page as a declaration. The dashboard draws it from a closed set of blocks it already contains. No plugin
code runs in the browser.

Why not load plugin JavaScript, which is what most extension points do:

1. **The dashboard origin is the administrator.** The session cookies are HttpOnly, but the CSRF cookie is readable on purpose
   (`ingot_csrf`, `SessionCookies.kt`) and the page's own scripts call every admin endpoint with it. A script that runs in that
   origin can create a token, change a team, read the settings and turn them into an exfiltration, with the administrator's rights.
   A plugin jar is already trusted on the server, but its author is not necessarily trusted with the browser session of every
   administrator who ever opens the dashboard, and a compromised plugin update would then reach people, not just a process.
2. **The policy cannot help.** `DASHBOARD_POLICY` says `script-src 'self' 'unsafe-eval'`
   (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/SecurityHeaders.kt:72`). A script the server serves for a plugin is
   `'self'`. Allowing plugin script costs no policy change and gains no protection, which is the worst combination. A hash or
   nonce scheme would need the server to know the bytes in advance and the split image to learn them, and `'unsafe-eval'` (kept
   for Ajv) already weakens what a nonce buys.
3. **The split image would lose its point.** The Nitro image is small so that the piece facing the browser has little to abuse
   (`reposilite-frontend/Dockerfile` header). It serves only the build and proxies the rest. Serving plugin bundles from it means
   either proxying plugin script through it, or giving it plugin files, and then it is as trusted as the plugin.
4. **Upgrades.** A plugin script depends on dashboard internals (Vue, the stores, the router). The plugin ABI gate
   (`gate-plugin-abi`) covers JVM bytes, not a JavaScript surface, so every dashboard release could break a plugin silently.
   A declaration is a small versioned schema that the server validates.
5. **Declarative is enough for the stated cases.** The wizard-like and status-like pages of N11 and of the supply-chain plugins are
   forms, tables and buttons over the plugin's own API. The dashboard already draws forms from JSON schema (JSON Forms and Ajv
   in `SettingsView.vue`), so the largest block is not new.

What a later opt-in for plugin script would have to bring, so it is not mistaken for this change: a separate origin (a sandboxed
`iframe` on its own host, no cookies, talking through `postMessage` with a narrow API), a stated policy for that host, an operator
switch that is off by default, and a signature or pinned hash for the bundle. It is not designed here and not ruled out.

## Trust model

| Thing | Where it runs | Trusted with |
| --- | --- | --- |
| Plugin jar | The server JVM | Everything the server can do. Unchanged. |
| Plugin page declaration | Built in the server, validated, sent as JSON | Choosing words, order and which of its own routes a block calls. Nothing else. |
| Block components | The dashboard, shipped in the dashboard build | Drawing the declaration and calling the allowed routes. |
| Plugin API routes | The server JVM | Their own data and effects, behind the page's area. |

Consequences, each of which the specification turns into a requirement:

- **Text is text.** Titles, labels and cell values are drawn with Vue interpolation. No block renders HTML, and the model has no
  field that could carry it. The model has no link or image block, so no address from a plugin is ever opened or loaded by the browser.
- **Calls stay in their lane.** A block names a path. The server rejects at registration any path that does not start with
  `/api/plugins/<plugin>/`, and the dashboard refuses to call anything else even if the manifest were altered in transit. The method
  of an `action` is `POST`, `PUT` or `DELETE`. Requests carry the same CSRF header as every other dashboard call, because the
  blocks use the dashboard's own API client.
- **Permission is checked three times.** The server leaves out of the manifest a page whose area the caller lacks, so an
  unauthorised caller does not learn the page exists. The dashboard checks the node again before drawing, with the same matcher
  as every other page (`reposilite-frontend/src/store/nodeAccess.js`). And a request filter, described next, checks the area on
  the data routes, because the manifest is only advice to the browser.
- **Failure is local.** A manifest entry that fails validation is dropped with a warning that names the plugin and the problem.
  The server still starts and the other pages still show.

## The data route filter

`managerOnly(area)` is the way a route checks an admin area (`reposilite-backend/src/main/kotlin/com/reposilite/shared/ContextDsl.kt:97`).
A plugin author can forget it, and then the page's area is decoration. For a plugin that registered at least one page, the
frontend plugin installs a filter for `/api/plugins/<plugin>/*` that requires one of the areas that the plugin's pages name
(the caller needs any one of them, and a route may require more). The filter runs after
authentication, answers `401` or `403` with the usual problem body, and does nothing for a plugin that registered no page, so no
existing plugin route changes behaviour. The filter is a floor, not a replacement: a route that needs more still checks it.

The implementation confirms in its first red test that Javalin's before handler for that prefix runs before the plugin's route
and after the session is known. If it does not, the same rule moves into a `pluginRoute` helper that plugins use, and the
requirement stays.

## The page model

All fields are plain data. The Kotlin classes live in `com.reposilite.frontend.api` (the plugin package stays `com.reposilite.*`).

```
PluginUiPage
  plugin        string, set by the facade from the registering plugin, not by the plugin
  id            [a-z0-9-]{1,32}, unique within the plugin
  title         1..60 characters
  description   optional, 0..200 characters
  group         Repository | Security | System | Support, default System
  area          settings | users | teams | tokens | maintenance | audit | console | dashboard | *
  blocks        1..30 blocks, in order

Block (field `type` selects the shape)
  text        text 1..2000 characters, plain
  keyValue    source: a path, GET, returns { "key": "value", ... }
  table       source: a path, GET, returns an array of objects; columns [{ key, label }] 1..12
  form        source: GET path returning the object; target: PUT or POST path; schema: JSON schema; uiSchema: optional
  action      label, method POST|PUT|DELETE, path, confirm: optional text, danger: optional boolean
  status      source: GET path returning { "state": string, "message": string, "progress": 0..1 }; refreshSeconds 5..300

PluginSettingsSection
  domain        the id of a settings domain of the same plugin
  label         1..60 characters
  group         Repository | System, default System
```

Limits: 20 pages per plugin, a serialised manifest of at most 64 KiB per plugin. A `status` block polls with a minimum interval
of five seconds and only while the page is visible. A `table` shows at most 500 rows and says when it cut them. Values are
stringified for display; objects and arrays are shown as compact JSON text.

`form` reuses the JSON Forms renderer the dashboard already has for settings, with the same Ajv validation, so no new library and
no policy change. Schema `$ref` to an address outside the document is refused.

`status` and `action` together carry the job pattern the Nexus import needs (start, watch progress). Whether N11 needs a
`steps` block is for that change to show.

## Where the page lives in the address space

The page is at `/settings/plugins/<plugin>/<page>` (route `settings-plugin`, `<page>` optional and defaulting to the plugin's first
page). It is a child of `/settings`, which is a reserved dashboard segment, so it cannot collide with a repository name and the
backend list `DashboardPaths.APP_ROUTES` (`/settings/<path>`) and the split image's segment list already answer it with the document.
The router entry is placed before `settings-entry` (`/settings/:domain/:collection/:entry`, `routes.js:77`), which would otherwise take
`/settings/plugins/x/y` as a collection entry.

The id `plugins` is now reserved against settings domain ids. A plugin whose settings domain is called `plugins` is reported at
startup, as the regrouping report names the other reserved page names. No bundled domain is called that.

The menu group (`group`) decides where the entry shows, not the address. Entries in System sit under Administration, System;
the four group names are exactly those that `add-repositories-admin-page` defines for Administration (Repository, Security, System,
Support), and a plugin's settings domain is listed at `/settings/<domain>` under System, as that change specifies. This change builds
on `add-repositories-admin-page` if it lands first. Otherwise every contributed entry appears in the existing Plugins category of the
Settings side navigation (`settingsGroupOf` falls back to it), so the change can land first.

## The navigation registry

`reposilite-frontend/src/navigation/registry.js` gains `contributedPages(can)` and `setContributions(manifest)`. The static `PAGES`
list stays static. The contributions come from the manifest after the session is known, and the registry answers the same
questions (`railGroups`, `barLayout`, `groupedSettingsDomains`, the breadcrumb and title helpers) over both. A contributed page is never
in the bottom bar. A contribution never replaces or reorders a built-in page. `unlistedRoutes` accepts `settings-plugin` as the one
route that has no static entry, which keeps the rule that a route without a place fails the suite.

`registerSettingsSection` fills the gap that `restructure-navigation` left as Q5: the group and label of a plugin's settings domain come
from the plugin, not from a table in the frontend, and a domain without a section behaves as today.

## Reporting enabled plugins

The manifest also carries `enabledPlugins`, a list of plugin names. A first-party page that belongs to a plugin but is not
contributed by it needs a visibility signal, and the dashboard must not guess it from a configuration endpoint. The nexus import
wizard (`add-nexus-import-wizard`) is the first reader: it is shipped in `reposilite-frontend` as a fixed page, because a wizard of
seven steps with its own state cannot be built from the six blocks, and it appears only when `enabledPlugins` contains
`nexus-import`. The server fills the field from its plugin loader, so a plugin does not call anything to be listed. It is sent to
every signed-in caller, contains names only, and is not governed by `INGOT_LOCAL_PLUGINUI`, which controls what plugins contribute,
not what Ingot ships. A capability flag registered by the plugin was considered and rejected: it needs the plugin to depend on
`plugin-ui` just to say it is on.

## Both deploy modes

The manifest is `GET /api/frontend/plugin-ui`, an API call. The split image proxies `/api/*` to the server
(`reposilite-frontend/server/lib/route-classifier.js`), including the session cookie and the CSRF header, so it needs no code
change. A test in the dashboard server suite asserts that the policy string it sends equals the backend's after this change, which
holds the "unchanged policy" requirement in both modes. A dashboard served from somewhere else and pointed at the server by hand
gets the same manifest.

## Alternatives considered

- **Plugin script in an iframe on the same origin.** Rejected: same origin means the same session and the same reach.
- **Plugin script on a separate origin.** Not rejected forever, deferred: it needs hosting, a message protocol and a policy
  statement, and none of the stated cases needs it.
- **A new node per plugin now.** Deferred: the node grammar names a fixed area list (`NodePattern.ADMIN_AREAS`) and extending it is
  an identity change with its own migration questions. Using the eight existing areas, or `*`, gives an operator a real control today.
- **Letting the plugin choose any path for its data.** Rejected: the prefix rule is what makes the server-side filter possible.
- **Always on, no switch.** Rejected: an operator who runs only what Ingot ships should be able to say so. The default stays on
  because it changes nothing without a plugin that opts in.
- **Reading the manifest from a file in the plugin jar.** Rejected: code is easier to test, to validate at registration with a
  clear error and to keep in step with the plugin's routes. A file can be added later as another way to build the same data.

## Risks

- **The block set will feel small.** That is intended for a first version. Adding a block type is additive and versioned by the
  manifest's `version` field (1 in this change), and the dashboard ignores a block type it does not know, with a visible
  "unsupported block" note, so a newer plugin on an older dashboard degrades and does not break.
- **A plugin that registers a page and an unguarded route below another plugin's prefix.** The filter keys on the prefix of the
  registering plugin only. A plugin cannot register a page for another plugin's prefix: the prefix is derived from the plugin name,
  not given.
- **Polling load.** The five second floor and the visibility rule bound it. A cluster with many open dashboards sees one read
  per page per interval, the same order as the dashboard's existing status refresh.

## Open questions

- **Q1. Node per plugin.** Default: existing areas and `*`. A node per plugin (`ingot.admin.plugin.<id>`) is a follow-up on the
  identity grammar. Does not block this change.
- **Q3. The filter placement.** The first red test decides between the before handler and the helper, as described. Both satisfy
  the requirement; neither needs a maintainer decision.

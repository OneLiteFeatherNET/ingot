# Design

## Starting point, verified on main

- **No stylesheet reaches the dashboard from a plugin.** The only plugin channels are the page declarations of `plugin-ui`
  (`reposilite-backend/src/main/kotlin/com/reposilite/frontend/api/PluginUiFacade.kt:32`, `PluginUiRegistrar` at `:123`), the
  placeholders (`FrontendFacade.kt:50`, merged into the bundled assets at `:136`, processed files only `.html` and `.js`,
  `FrontendHandler.kt:64`), and the appearance settings (`AppearanceSettings.kt:35-75`). The dashboard stylesheet is imported by
  `reposilite-frontend/src/main.js` (`import './style.css'`).
- **Cascade of the dashboard.** `style.css:23` imports Tailwind v4, which declares its layers `theme, base, components, utilities`.
  The dashboard adds `@layer base` (`style.css:55`) and `@layer components` (`style.css:118`). Three files are not in a layer:
  `styles/md3/color.css` (6 top-level rules), `typography.css` (1) and `shape-elevation-motion.css` (1). Seven `.vue` files carry
  `<style>` blocks, which are also unlayered (for example `components/settings/SettingsView.vue`).
- **Split image.** Paths that are not documents are proxied (`reposilite-frontend/server/lib/route-classifier.js:55-61`), so
  `/api/frontend/plugin-styles/...` reaches the server in the split deployment with no change.
- **Policy.** `DASHBOARD_POLICY` (`reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/web/SecurityHeaders.kt:72`) says
  `style-src 'self' 'unsafe-inline'` and `img-src 'self' data: https: http:`. The split image repeats it
  (`reposilite-frontend/server/lib/security-headers.js:27`).
- **Manifest.** `GET /api/frontend/plugin-ui` (`PluginUiEndpoint.kt:41`) answers `401` to anonymous callers. `PluginUiManifest`
  is at `PluginUiModel.kt:123`. The manifest is built by `PluginUiFacade.manifest` (`PluginUiFacade.kt:97`).
- **Data guard.** `PluginUiDataGuard` guards only `/api/plugins/<plugin>/` (`PluginUiDataGuard.kt:35`). A stylesheet path under
  `/api/frontend/` is not in that prefix.
- **Switch.** `LocalConfiguration.kt:151` declares `pluginUi`, which is read as `INGOT_LOCAL_PLUGINUI` (fallback
  `REPOSILITE_LOCAL_PLUGINUI`). A new property of the same kind is read the same way.
- **Immutable serving.** `FrontendHandler.kt:183` already caches hashed bundles for a year with `public, max-age=31536000,
  immutable`, and names the document `no-cache`. This change uses the same policy for a content-addressed sheet.
- **Dashboard loading.** `reposilite-frontend/src/store/pluginUi.js:30` (`loadPluginUi`) fetches the manifest once the visitor
  is signed in and passes the result to the navigation registry. The stylesheet module is called from the same place.

## Decision 1: declaration, a resource in the plugin jar

Default: `registerStylesheet(resource: String)` on `PluginUiRegistrar`, where `resource` names a file in the plugin's own class
loader, for example `dashboard.css`. The registrar reads it at registration, so a missing resource is a warning at startup, not an
error on the first request.

```kotlin
@Plugin(name = "example", dependencies = ["plugin-ui"])
class ExamplePlugin : ReposilitePlugin() {
    override fun initialize(): Facade? {
        facade<PluginUiFacade>().forPlugin(this).registerStylesheet("dashboard.css")
        return null
    }
}
```

Alternatives:

- **A `stylesheet` field in the manifest file of the jar.** Rejected for the same reason as in `add-plugin-ui-extension-point`: code is
  easier to validate at registration with a clear message, and the call sits next to the page declarations.
- **A CSS string in code.** Rejected as the default: a 64 KiB string literal is hard to review. The method can take a string later as
  a second overload.
- **A file in the `static/` directory of the operator.** Rejected: it would be an operator action per plugin, and the operator is not
  the author.

One stylesheet per plugin. A second call is dropped with a warning, as a duplicate settings section is today.

## Decision 2: validation, and why the default is to reject network constructs

The validator is a pure function (`PluginStylesheetValidator`), so its tests are fast and need no server. It runs once, when the
sheet is registered. A sheet that fails is dropped with a warning that names the plugin and the first problem.

The rules:

1. Size: at most 64 KiB (`PluginUiModel.MAX_MANIFEST_BYTES`, the same bound as the manifest).
2. Encoding: UTF-8 without a byte order mark. An `@charset` rule is rejected, because the server writes the charset.
3. Braces: balanced, and never closed below depth zero before the end. An unbalanced sheet could close the layer early.
4. Escapes: a backslash outside a string is rejected. CSS escapes can spell a forbidden keyword (`\75rl(` is `url(`, and
   `@\69mport` is `@import`), so a scan of the raw text is not enough. Plugin authors do not need escapes for the anchor selectors.
5. Forbidden at-rules: `@import`, `@font-face`, `@namespace` (not needed, rejected to keep the grammar small).
6. Network functions: `url(` is allowed only with a `data:image/png`, `data:image/gif`, `data:image/jpeg`, `data:image/webp` or
   `data:image/svg+xml` argument. `src(`, `image-set(` and `-webkit-image-set(` are rejected.
7. Legacy hooks: `expression(`, `behavior:` and `-moz-binding` are rejected.

Why rule 6 is the default, and not a CSP narrowing per sheet: CSP is per document. A `img-src` that excludes `https:` for plugin
sheets is not possible without also narrowing the dashboard, and the dashboard needs `https:` for the logos the operator configures
(`AppearanceSettings.kt:65`, `organizationLogo` in `FrontendSettings.kt`). The alternative, a hash or nonce for each sheet, would
not stop a rule that loads a URL. So the restriction is done where the sheet is read: without a network function, a stylesheet cannot
make a request, and CSS has no other way to send a value out of the page. Rule 6 also keeps `font-src` and the other network
fetches of CSS out of the channel.

What remains possible is visual only: a rule can show or hide an element, change its colour or position, and cover another element.
That is the security note in Decision 6.

## Decision 3: the layer, and what it does and does not beat

Default: the server wraps the accepted rules in `@layer ingot-plugins { ... }`. The dashboard declares the layer order once, after
its imports in `style.css`, as `@layer theme, base, components, utilities, ingot-plugins;`. Layers are ordered by their first
declaration, so the order is fixed by the dashboard and not by the sheet that arrives later.

Cascade facts this relies on, from the CSS cascade specification:

- Among layered rules, a later layer wins over an earlier one, whatever the specificity. So a plugin rule beats the Tailwind
  utilities that style a component, which is the restyling a plugin needs.
- An unlayered rule beats every layered rule. So a plugin rule does **not** beat the unlayered rules of the dashboard: the seven
  `<style>` blocks, the Material 3 token files and the `:root` custom properties in `styles/md3/color.css`.

So the defined position of a plugin rule is: above every layer of the dashboard, below its unlayered rules. The plugin cannot
override the colour tokens (`--md-sys-color-*`), and this is the reason the theming tokens API is out of scope. Converting the
unlayered CSS to layers would change the dashboard's own cascade, so it is a separate change with a visual review.

The wrap also makes the sheet's own `@layer` statements harmless: a sheet may declare sub-layers inside `ingot-plugins`, but it
cannot reorder the layers of the dashboard.

## Decision 4: load order

Default: the dashboard appends one `<link rel="stylesheet" data-ingot-plugin-style="<plugin>" href="...">` to the end of `<head>`,
which is after the dashboard's own stylesheet (Vite emits it as a link in production and as style elements in development, and both
are in the head before the manifest arrives).

The layer declaration already fixes the order of the layers, so the position of the link does not change the cascade for layered
rules. The position is still defined and tested, so that a later change that moves the dashboard CSS does not make plugin rules
depend on load timing. If a sheet fails to load, the module removes its link, and the page is drawn with the dashboard's own styles.

The module refuses an `href` that does not match `^/api/frontend/plugin-styles/[a-z0-9-]{1,32}/[0-9a-f]{16}\.css$`, the same way the
dashboard refuses a block path outside its prefix (`pluginBlocks.js:30`). A tampered manifest then changes nothing.

On a cluster in the middle of a rolling upgrade, one node may serve a sheet under a hash that another node does not have yet. The
link then answers `404`, the module removes it, and the next manifest fetch adds the new one. No request is retried in a loop.

## Decision 5: serving

- Path: `GET /api/frontend/plugin-styles/<plugin>/<hash>.css`. The hash is the first 16 hexadecimal characters of the SHA-256 of
  the served bytes, after the wrap. Content addressing means the URL changes with the content, so the year-long cache is safe.
- Headers: `Content-Type: text/css; charset=utf-8`, `Cache-Control: public, max-age=31536000, immutable`, `ETag: "<hash>"`.
- Authentication: none. The sheet is a static file that the plugin ships in its jar, so it contains nothing secret, and the link is
  loaded by the browser as an ordinary stylesheet. The manifest is still signed-in only, so the path becomes known only to a signed-in
  caller.
- `404` for an unknown plugin, an unknown hash, or while the switch is off.
- The path sits under `/api/`, so the split image proxies it. A test of the route classifier covers this (see tasks).

Alternatives: serving the sheet inline in the manifest (rejected: the manifest is JSON, the sheet would be escaped text and cached
with the manifest's `no-cache` policy), and serving it from `/settings/...` (rejected: `/settings/` is a document route, so the split
image would answer it with the app shell).

## Decision 6: the manifest entry, who receives it, and the security note

The manifest gains `stylesheets`:

```json
{ "version": 1, "pages": [], "sections": [], "enabledPlugins": ["example"],
  "stylesheets": [ { "plugin": "example", "path": "/api/frontend/plugin-styles/example/3f9c1a2b7d4e6f80.css", "sha256": "3f9c1a2b7d4e6f80..." } ] }
```

`version` stays 1, because the field is additive: a dashboard that does not know it ignores it.

Who receives it: every signed-in caller, whatever the caller's areas. The stylesheet is not a page of an area. It restyles what
the caller sees, and a restyle that depended on the caller's rights would look different to each admin. The same reasoning applies to
`enabledPlugins`.

The security note, to be written in the documentation (`docs/gitbook/explanation/security-headers.md`) and in the plugin page:

- **Only instance administrators install plugins.** A plugin jar runs in the server with full rights, so its author is already the
  instance administrator's trusted code. The stylesheet adds no new party.
- **A stylesheet can hide or restyle security relevant UI.** A rule can hide a confirmation text, make a destructive button look
  like a harmless one, hide the read-only banner, or cover a control. The dashboard keeps its confirmations and checks in the DOM and
  on the server, so a hidden control is still refused by the server, but what the administrator sees can differ from what they click.
  This is accepted for the same reason as the first point: the author is trusted. An operator who does not trust a plugin's
  appearance turns the switch off, or does not install the plugin.
- **A stylesheet cannot read the session.** It has no script, it cannot read cookies or the CSRF header, and it cannot send a value
  out (Decision 2, rule 6).

## Decision 7: the switch

`INGOT_LOCAL_PLUGINSTYLES` (fallback `REPOSILITE_LOCAL_PLUGINSTYLES`), a property of `LocalConfiguration` next to `pluginUi`, default
`true`. With `false` the manifest's `stylesheets` is empty and the path answers `404`. The switch does not affect pages, sections or
`enabledPlugins`, which `INGOT_LOCAL_PLUGINUI` controls. When the switch is on, a plugin that does not declare a stylesheet changes
nothing: its manifest entry is absent, and no request is made for it.

Naming: the sibling switch is `INGOT_LOCAL_PLUGINUI`, and the mechanism of `LocalConfiguration` produces `INGOT_LOCAL_*` names. The
name `INGOT_PLUGIN_STYLES` suggested in the proposal would need an explicit mapping that the other switches do not have (Q1).

## Decision 8: CSP

No change. `style-src 'self' 'unsafe-inline'` allows the same-origin `<link>`. The split image policy and `DASHBOARD_POLICY` stay
byte for byte. A test asserts both, as `add-plugin-ui-extension-point` does for its own policy.

## Decision 9: the scope convention

Default: the convention is that a plugin rule starts with `[data-ingot-component=...]` (or `[data-ingot-part=...]` inside one).
The example in the documentation follows it. The server does not enforce it: a sheet that styles `body` is accepted, because the
server cannot know what is intended, and the rule is an authoring rule. The convention keeps a plugin's rules from changing elements
it does not own, and it is what makes the anchors useful, but it is not a security boundary.

## Decision 10: testing order

Red first, as in the other changes of this series:

- Validator unit tests (`:reposilite-backend:test --tests 'com.reposilite.frontend.api.PluginStylesheetValidatorTest'`): each rule of
  Decision 2 with a passing and a failing sheet, including the escape bypass `\75rl(` and an unbalanced sheet that closes the layer.
- Integration tests (`:reposilite-backend:integration --tests 'com.reposilite.frontend.PluginStylesheet*'`): registration keeps a valid
  sheet and the manifest lists it for a signed-in caller; the path answers with the wrapped bytes, the headers and the hash; a wrong
  hash and an unknown plugin answer `404`; the switch off empties the list and answers `404`; a plugin without a sheet has no entry.
- Route classifier test (`reposilite-frontend/server/lib/route-classifier.test.js`): `/api/frontend/plugin-styles/...` is proxied.
- Policy test: `DASHBOARD_POLICY` and the split policy equal their recorded values.
- Vitest (`reposilite-frontend/src/store/pluginStyles.test.js`): a link is appended after the existing stylesheet and style elements;
  a link is removed when the manifest no longer lists the plugin; a second manifest with a new hash replaces the link; a path that does
  not match the pattern adds no link; an empty `stylesheets` list adds none; `style.css` declares `ingot-plugins` after the Tailwind
  layers.

All tests are F.I.R.S.T.: no sleeps, a temporary directory per integration test, the clock injected where a time is read, and
each test builds its own fixture.

## Decision 11: documentation

- The page "Extending the dashboard from a plugin" (created by `add-ui-component-anchors`) gains the working example: a `dashboard.css`
  that styles the `status` block of a plugin page and the plugin's table header, using only anchors from the registry, the Kotlin
  registration line, and the rules a reader must know (the layer, the network ban, the escape ban).
- The example is shown in full and built in a test: the example sheet must pass the validator. A test in the backend reads the
  documentation example and checks it, so the example cannot drift from the rules.
- `docs/gitbook/reference/plugin-api.md` (Dashboard pages) gains `registerStylesheet`, and `configuration-and-startup.md` and
  `environment-variables.md` gain `INGOT_LOCAL_PLUGINSTYLES`. `http-api.md` gains the path and the `stylesheets` field.
  `security-headers.md` gains the note of Decision 6 and the statement that the policy is unchanged.

## Alternatives not taken

- **Plugin JavaScript, in an iframe or a separate origin.** Not decided here. `add-plugin-ui-extension-point` keeps it out of the
  dashboard, and this change does not reopen that.
- **A CSP per sheet.** Not possible: CSP is per document (see Decision 2).
- **Allowing network constructs and trusting the author.** Rejected for the default: the rule costs a plugin author nothing they need,
  and it makes a whole class of exfiltration impossible instead of relying on review.
- **Scoping by rewriting selectors on the server.** Rejected: a rewrite of selectors is a CSS parser, and the layer already gives the
  cascade position that the rewrite would aim for.

## Open questions (each has a default; none blocks implementation)

- **Q1. Switch name.** Default: `INGOT_LOCAL_PLUGINSTYLES`, which the existing mechanism produces. The name `INGOT_PLUGIN_STYLES`
  suggested in the proposal is possible with an explicit mapping. The maintainer chooses.
- **Q2. Default of the switch.** Default: on, effective only for plugins that declare a stylesheet. An operator who wants no plugin
  styles at all sets the switch to `false`.
- **Q3. Maximum size.** Default: 64 KiB per sheet.
- **Q4. Allowed data URIs.** Default: raster images and SVG as `data:image/...`. SVG is accepted because a CSS image cannot run script.
  The maintainer may drop SVG.
- **Q5. Anonymous fetch of the sheet.** Default: no sign-in needed, because the sheet is a static file in the jar. The path is known
  only from the signed-in manifest in practice.
- **Q6. Scope enforcement.** Default: convention only (Decision 9). A later change could warn for selectors that do not mention
  `data-ingot-`.
- **Q7. Unlayered dashboard CSS.** Default: unchanged. Converting it to layers is a separate change with a visual review, and until
  then plugin rules cannot override the colour tokens.
- **Q8. Login page.** Default: no plugin styles on the sign-in page, because the manifest is signed-in only.
- **Q9. Rolling upgrade.** Default: a `404` removes the link, and the next manifest fetch adds the new one (Decision 4).

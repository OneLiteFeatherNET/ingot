# Proposal

## Why

An operator who installs a plugin with a dashboard page can read its status and press its buttons. A plugin author who wants
the page to fit the dashboard, or wants a warning on a page to stand out, cannot do it: the plugin ships no CSS into the
dashboard, and the dashboard has no name to attach a rule to. `add-ui-component-anchors` gives the names. This change gives
the other half, a way for a plugin to deliver a stylesheet, so that a name can be used by a rule the plugin ships.

The channel is deliberately a stylesheet and not a script. `add-plugin-ui-extension-point` decided that no plugin code runs in
the dashboard, because the dashboard origin holds the administrator's session and a script there can do anything the
administrator can. This change keeps that decision. CSS cannot read cookies, cannot call the API, and cannot run code. Its
remaining risks are cosmetic and are documented as such: a stylesheet can hide or restyle controls the administrator relies
on. That risk is accepted because the only party who can install a plugin is already the instance administrator, who runs
server code with full rights (see `design.md`, Security).

The rules that make the channel safe are server side. The server reads the sheet at registration, rejects every construct that
can reach the network, places the rules in a named cascade layer, and serves the result from a path that is derived from its
content. An operator who wants no plugin styles at all switches them off, and an installation without a plugin that declares a
stylesheet does not change.

Facts checked against this worktree (main at `db0717b2b`, with `add-ui-component-anchors` on the same branch) are in
`design.md`, with `path:line` citations.

## What Changes

- **Declaration.** A plugin that already uses `plugin-ui` calls `registerStylesheet("<resource>")` on its registrar. The resource is
  a CSS file in the plugin's jar. One stylesheet per plugin.
- **Validation.** At registration the server reads the file as UTF-8 and rejects it, with a warning that names the plugin and
  the reason, when it is larger than 64 KiB, has unbalanced braces, uses an `@import`, `@font-face`, a CSS escape outside a string,
  or any function that loads a resource (`url()` except `data:image/...`, `src()`, `image-set()`), or uses `expression(`,
  `behavior:` or `-moz-binding`.
- **Layer.** The server wraps the accepted rules in `@layer ingot-plugins { ... }`, and the dashboard declares that layer after
  the layers of Tailwind (`reposilite-frontend/src/style.css`). A plugin cannot choose its own layer order.
- **Serving.** `GET /api/frontend/plugin-styles/<plugin>/<hash>.css`, where `<hash>` is the first 16 hexadecimal characters of the
  SHA-256 of the served bytes. It answers `text/css; charset=utf-8`, with `Cache-Control: public, max-age=31536000, immutable` and
  an `ETag`. It answers `404` for an unknown plugin, an unknown hash or while the switch is off. It needs no sign-in.
- **Manifest.** `GET /api/frontend/plugin-ui` (`add-plugin-ui-extension-point`) gains a `stylesheets` list with one entry per plugin
  that declared a valid sheet: `{ plugin, path, sha256 }`. `version` stays 1, because the field is additive.
- **Dashboard.** A small module adds one `<link rel="stylesheet">` per entry to the end of `<head>`, after the dashboard's own
  stylesheet, and removes the links that are no longer in the manifest. A path that does not match the served pattern is refused.
- **Switch.** `INGOT_LOCAL_PLUGINSTYLES` (default `true`, see open question Q1). `false` leaves the `stylesheets` list empty and
  answers `404` on the path. Plugin pages and settings sections are not affected by it.
- **Documentation.** The page "Extending the dashboard from a plugin", created by `add-ui-component-anchors`, gains a worked
  example plugin stylesheet that uses the anchors. The Plugin API reference, the configuration reference, the environment
  variables, the security headers explanation and the HTTP API reference gain the new items.

### Out of scope

- JavaScript in the dashboard, in any form. This change does not change `add-plugin-ui-extension-point`.
- Theming tokens: a documented list of CSS custom properties that plugins may override. Plugin rules in a layer do not beat
  unlayered dashboard rules (`design.md`), so the colour tokens of `styles/md3/color.css` are out of reach. A token API is a
  separate change.
- Login page styling. The stylesheet is loaded only for a signed-in caller, after the manifest.
- Converting the unlayered dashboard CSS (the Vue `<style>` blocks and the Material 3 token files) into layers. That changes the
  cascade of the dashboard and needs its own visual review.
- Enforcing that selectors use `[data-ingot-component]`. The scope is an authoring convention, documented, not a security boundary.
- Signing plugin stylesheets, or checking them against a hash supplied by the plugin.
- Any change to `DASHBOARD_POLICY` or the split image policy. A same-origin stylesheet is allowed by the existing `style-src 'self'`.

## Capabilities

### New Capabilities

- `plugin-stylesheet-channel`: the declaration, the validation rules, the layer, the served path and its caching, the manifest
  entry, the dashboard loading rule and the switch.

### Modified Capabilities

- None. `ui-component-anchors` is referenced, not changed by this proposal.

## Impact

- **Type and scope.** The change ships under `feat(frontend)`. Commits use `test(frontend)` for the tests, `feat(frontend)` for the
  code and `docs(gitbook)` for the documentation pages. The pull request title, squash-merged onto main, is:

  `feat(frontend): let plugins ship a stylesheet for the dashboard`

- **Breaking marker.** No breaking change. The new switch defaults to on and changes nothing unless a plugin declares a stylesheet.
  No existing configuration key, variable, volume, manifest field, route or plugin is changed or removed. The title carries no
  `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None for an installation without a plugin that declares a stylesheet. The `stylesheets`
  list is present and empty for every other installation.
- **Both deploy modes.** The path is under `/api/`, which the split image proxies (`reposilite-frontend/server/lib/route-classifier.js`,
  the fall-through `proxy` branch). The dashboard loads the link from the same origin in both modes.
- **Dependencies on other changes.**
  - `add-ui-component-anchors`: **required**. The example stylesheet selects on the anchors, and the scope convention is defined
    in terms of them. Both changes are in the same proposal PR and the code PRs are ordered: anchors first, then this change.
  - `add-plugin-ui-extension-point`: merged in code (PR #139). This change extends its registrar, its manifest and its local
    configuration. Its change folder is still on main and unarchived.
  - `gate-plugin-abi` (not merged): the new registrar method is additive.
- **Code.** `reposilite-backend` (the registrar, the validator, the stylesheet store, the endpoint, the local configuration),
  `reposilite-frontend` (the style module, the `style.css` layer order, the loading rule), `docs/gitbook/`.

## Verification

- Targeted backend unit tests through `:reposilite-backend:test --tests '<pattern>'` and integration tests through
  `:reposilite-backend:integration --tests '<pattern>'`, both run through `heavy` with `--max-workers=2`.
- Frontend tests with `npm test` in `reposilite-frontend`, and `npm run eslint`.
- `openspec validate add-plugin-stylesheet-channel --strict` and `openspec validate add-ui-component-anchors --strict` pass.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-plugin-stylesheet-channel openspec/changes/add-ui-component-anchors` prints nothing.

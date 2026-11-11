# Tasks

Every group is one or more commits of one Conventional Commits type each, with the scope `frontend` for code and `gitbook` for
documentation. Tests are written first and seen to fail. Every test follows F.I.R.S.T.: no sleeps, no system clock (a
`java.time.Clock` is injected where a time is read), no shared mutable state, a temporary directory per integration test, and
assertions with messages. Backend tests run through `heavy` with `--max-workers=2`: unit tests with
`:reposilite-backend:test --tests '<pattern>'`, integration tests with `:reposilite-backend:integration --tests '<pattern>'`, never
the full suite. Frontend tests with `npm test` in `reposilite-frontend`.

This change depends on `add-ui-component-anchors`. Its groups come after that change's code pull request is merged.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-plugin-stylesheet-channel`: this change, and the matching sentence in
  `add-ui-component-anchors`, in the same commit. Verify: both changes pass `openspec validate --strict` and the dash grep prints nothing.

## 2. Validator (backend)

- [ ] 2.1 `test(frontend)`: write failing unit tests for `PluginStylesheetValidator` as a pure function. A valid sheet passes. Each
  of these fails with a message naming the rule: 64 KiB plus one byte, a BOM, `@charset`, unbalanced braces, a brace that closes the
  layer early, `@import`, `@font-face`, `@namespace`, `url(https://...)`, `url(/api/tokens)`, `url(data:text/css,...)`, `src(`,
  `image-set(`, a backslash outside a string, the escape `\75rl(`, `expression(`, `behavior:`, `-moz-binding`. A `url(data:image/png...)`
  passes. Verify: they fail because the class does not exist.
- [ ] 2.2 `feat(frontend)`: add `PluginStylesheetValidator` and the wrap `@layer ingot-plugins { ... }` in
  `com.reposilite.frontend.api`. Verify: the tests of 2.1 pass.

## 3. Registration, store and endpoint (backend)

- [ ] 3.1 `test(frontend)`: write failing integration tests: a valid sheet registered by a plugin with `dependencies = ["plugin-ui"]`
  appears in the manifest for a signed-in caller with its path and hash; the anonymous manifest is `401`; a second `registerStylesheet`
  call for the same plugin is dropped with a warning captured through a log appender; a missing resource is a warning and no entry; a
  plugin without a sheet has no entry. Verify: they fail before 3.2.
- [ ] 3.2 `feat(frontend)`: add `registerStylesheet(resource)` to `PluginUiRegistrar`, read the resource through the plugin's class
  loader, store the wrapped bytes and their hash, and add the `stylesheets` list to `PluginUiManifest` (additive, `version` stays 1).
  Verify: the tests of 3.1 pass and `FrontendFacadeTest` and the existing plugin UI tests pass unedited.
- [ ] 3.3 `test(frontend)`: write failing integration tests for `GET /api/frontend/plugin-styles/<plugin>/<hash>.css`: `200` with
  `text/css; charset=utf-8`, the `immutable` cache policy and the `ETag`; `404` for a wrong hash, an unknown plugin and a path with an
  extra segment; anonymous access allowed; the body equals the wrapped bytes. Verify: they fail before 3.4.
- [ ] 3.4 `feat(frontend)`: add the endpoint `PluginStylesheetEndpoint` and its OpenAPI description. Verify: the tests of 3.3 pass.
- [ ] 3.5 `test(frontend)`: write failing integration tests for the switch: `INGOT_LOCAL_PLUGINSTYLES=false` gives an empty
  `stylesheets` list and `404` on the path, and leaves the pages of the same plugin in the manifest; the fallback
  `REPOSILITE_LOCAL_PLUGINSTYLES=false` has the same effect; the default is on. Verify: they fail before 3.6.
- [ ] 3.6 `feat(frontend)`: add `pluginStyles` to `LocalConfiguration` next to `pluginUi`, with its description, and wire it into the
  facade and the endpoint. Verify: the tests of 3.5 pass.

## 4. Policy and route (backend and split image)

- [ ] 4.1 `test(frontend)`: add a backend test that `DASHBOARD_POLICY` equals its recorded value, and a split image test
  (`reposilite-frontend/server/lib/security-headers.test.js`) that its policy equals the same string plus `form-action 'self'`.
  Add a route classifier case that `/api/frontend/plugin-styles/example/3f9c1a2b7d4e6f80.css` is proxied. Verify: they pass without a
  production change, and fail if either policy gains a source.

## 5. Dashboard (frontend)

- [ ] 5.1 `test(frontend)`: write failing vitest tests in `src/store/pluginStyles.test.js`: a link is appended after the existing
  stylesheet link and style element in a jsdom head; the link has `rel="stylesheet"`, the `href` from the manifest and
  `data-ingot-plugin-style="<plugin>"`; a second manifest without the plugin removes the link; a manifest with a new hash replaces
  the link and leaves one; a path outside the pattern adds nothing; an empty list adds nothing; `style.css` declares `ingot-plugins`
  after the Tailwind imports and the `utilities` layer. Verify: they fail before 5.2.
- [ ] 5.2 `feat(frontend)`: add `src/store/pluginStyles.js` with `applyPluginStyles(stylesheets)`, call it from `loadPluginUi` after
  the manifest is set, and add `@layer theme, base, components, utilities, ingot-plugins;` after the imports in `src/style.css`.
  Verify: the tests of 5.1 pass, and `src/store/pluginUi.test.js` passes unedited.
- [ ] 5.3 `test(frontend)` and `feat(frontend)`: a link whose stylesheet fails to load (`error` event) is removed, and the page keeps
  its own styles. Verify with a dispatched `error` event, no timers.

## 6. Browser check (frontend)

- [ ] 6.1 Run the example plugin stylesheet of group 7 against the fake API (`npm run fake-api`, `npm run dev`) and check in a browser
  that its rule applies to a `status` block and not to a dashboard element outside the anchor, and that a dashboard utility is overridden
  by it. Check that the dashboard's colour tokens are not changed. Stop the dev server afterwards.

## 7. Documentation (gitbook)

- [ ] 7.1 `docs(gitbook)`: extend "Extending the dashboard from a plugin" with the example plugin: the Kotlin registration line, the
  `dashboard.css` file (anchors only, the convention of Decision 9), the rules a plugin author must know (the layer, the network ban,
  the escape ban, the size limit) and what the plugin cannot change (the colour tokens, the sign-in page). The example text is the one
  that the test of 7.2 validates.
- [ ] 7.2 `test(frontend)`: write a backend test that reads the example stylesheet from the documentation page and passes it through
  `PluginStylesheetValidator`, so the example cannot drift from the rules. Verify: it fails until 7.1 is written, then passes.
- [ ] 7.3 `docs(gitbook)`: add `registerStylesheet` to `docs/gitbook/reference/plugin-api.md` ("Dashboard pages"), the switch to
  `configuration-and-startup.md` and `environment-variables.md`, the path and the `stylesheets` field to `http-api.md`, and the
  security note of Decision 6 to `explanation/security-headers.md`, stating that the policy is unchanged.
- [ ] 7.4 `docs(gitbook)`: update the `serve-custom-frontend.md` sentence that says plugin placeholders are the only plugin hook, so it
  names the stylesheet channel. Verify by grep that no page says a plugin cannot ship CSS.

## 8. Pull request

- [ ] 8.1 Open the code pull request, with no file under `openspec/`, titled
  `feat(frontend): let plugins ship a stylesheet for the dashboard`. It opens only after
  `add-ui-component-anchors` is merged. Fill the repository's pull request template, and state in it the security note (only
  instance administrators install plugins, a stylesheet can hide or restyle controls, a stylesheet cannot run code or reach the
  network), the switch name, and that no plugin script is added. Do not tick tasks in this pull request.

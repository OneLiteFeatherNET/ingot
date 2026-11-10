# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `frontend`. Tests are written first
and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (a `status` block
is driven by an injected timer and a fake clock), no system clock (any time source is a `java.time.Clock`), no fixed ports, no
shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `heavy`, with
`--max-workers=2`, targeted and never the full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`,
integration tests as `:reposilite-backend:integration --tests '<pattern>'`, frontend tests with `npm test` in `reposilite-frontend`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-plugin-ui-extension-point`: this change and nothing else. Verify:
  `openspec validate add-plugin-ui-extension-point --strict` passes and the dash grep from the proposal prints nothing.

## 2. Model and validation (backend)

- [ ] 2.1 `test(frontend)`: write failing tests for the validator as a pure function: a valid page passes; an id with capitals, a
  title of 61 characters, 31 blocks, an unknown area, an unknown group, a path outside `/api/plugins/<plugin>/`, an `action` with
  `GET`, a `status` interval of 4 seconds, a `form` schema with an external `$ref`, and a manifest over 64 KiB each fail with a
  message naming the problem. The plugin prefix comes from the plugin name, not the page. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(frontend)`: add the page, block and section data classes and the validator in `com.reposilite.frontend.api`. Verify:
  the tests of 2.1 pass.
- [ ] 2.3 `test(frontend)`: write failing tests for `PluginUiFacade`: registration keeps valid pages and drops invalid ones with a
  warning through a captured appender; the plugin name is taken from the registering plugin; a settings section for a domain the plugin
  does not own is dropped; a domain named `plugins` is reported. Verify: they fail before 2.4.
- [ ] 2.4 `feat(frontend)`: add `PluginUiFacade`, wire it into `FrontendPlugin` under the plugin name `plugin-ui`, and read
  `INGOT_LOCAL_PLUGINUI` (fallback `REPOSILITE_LOCAL_PLUGINUI`) in the local configuration. Verify: the tests of 2.3 pass and
  `FrontendFacadeTest` passes unedited.

## 3. Manifest and data route filter (backend)

- [ ] 3.1 `test(frontend)`: write failing integration tests: the manifest is `401` anonymous; a caller with the area gets the page
  and `version` 1; a caller without it gets neither the page nor its name; the switch off gives a manifest without pages; `enabledPlugins` lists an enabled plugin, omits a bundled one that is off, and survives the switch off. Verify: they fail
  before 3.2.
- [ ] 3.2 `feat(frontend)`: add `GET /api/frontend/plugin-ui` with its OpenAPI description, filtered by the caller's nodes, with `enabledPlugins` taken from the plugin loader. Verify:
  the tests of 3.1 pass.
- [ ] 3.3 `test(frontend)`: write failing integration tests for the filter: an unguarded plugin route below the prefix answers `403`
  without the area and the handler does not run; with the area it runs; a plugin without a page is untouched; a look-alike prefix
  (`/api/plugins/example-other/`) is not guarded by `example`. Verify: they fail before 3.4.
- [ ] 3.4 `feat(frontend)`: install the filter for plugins that registered a page, or the `pluginRoute` helper if the first test
  shows the before handler cannot see the session (design, Q3). Verify: the tests of 3.3 pass and the existing web route tests pass unedited.

## 4. Dashboard

- [ ] 4.1 `test(frontend)`: write failing vitest tests: the registry places a contributed page in its group and in Plugins when the
  group is absent, never in the bottom bar, never above a built-in page; `unlistedRoutes` accepts `settings-plugin`; the router matches
  `/settings/plugins/x/y` before `settings-entry`; a page without the node shows the no access state and makes no request. Verify:
  they fail before 4.2.
- [ ] 4.2 `feat(frontend)`: add `contributedPages` and `setContributions` to the registry, the `settings-plugin` route, the manifest
  load after the session, and the section placement. Verify: the tests of 4.1 pass and the existing registry and router tests pass unedited.
- [ ] 4.3 `test(frontend)`: write failing component tests per block: markup in `text` and in table cells is shown as text (a
  rendered DOM assertion); `table` cuts at 500 rows and says so; `action` asks for `confirm` text first and sends the CSRF header; a path
  outside the prefix makes no request; `status` polls at the injected interval and stops when the page is hidden; an unknown block shows
  the unsupported note. Verify: they fail before 4.4.
- [ ] 4.4 `feat(frontend)`: add the block components, reusing the JSON Forms renderer for `form`. Verify: the tests of 4.3 pass and no
  new runtime dependency is added to `package.json`.

## 5. Split image and policy

- [ ] 5.1 `test(frontend)`: add a test in `reposilite-frontend/server/lib/security-headers.test.js` that the image's policy equals the
  backend's `DASHBOARD_POLICY` plus `form-action 'self'`, and a backend test that `DASHBOARD_POLICY` equals its recorded value. Add a
  route classifier case that `/settings/plugins/example/status` answers with the document and `/api/frontend/plugin-ui` is proxied.
  Verify: they pass without a production change, and fail if either policy gains a source.

## 6. Documentation

- [ ] 6.1 `docs(frontend)`: add the "Dashboard pages" section to `docs/gitbook/reference/plugin-api.md` (the facade, the block
  reference, the limits, the data prefix and the filter, a Kotlin and a Java example) and link it from
  `docs/gitbook/how-to/write-a-plugin.md`.
- [ ] 6.2 `docs(frontend)`: add to `docs/gitbook/explanation/security-headers.md` why the policy is unchanged and why plugin script is
  not loaded, and name `INGOT_LOCAL_PLUGINUI` in `docs/gitbook/reference/configuration-and-startup.md` and
  `docs/gitbook/reference/environment-variables.md`.
- [ ] 6.3 `docs(frontend)`: add the route to `docs/gitbook/reference/dashboard-pages.md` and the endpoint to
  `docs/gitbook/reference/http-api.md`. Update `docs/gitbook/SUMMARY.md` only if a page is added.

## 7. Pull request

- [ ] 7.1 Open the code pull request, with no file under `openspec/`, titled
  `feat(frontend): let plugins contribute dashboard pages without loading plugin script`. Fill the repository's pull request
  template. Do not tick tasks in this pull request.

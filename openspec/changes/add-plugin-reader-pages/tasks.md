# Tasks

Each group is one or more commits of one Conventional Commits type each, with the scope `frontend`. Tests are written first and seen to
fail before the code they cover exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (inject a
`java.time.Clock` where a time is read), no fixed ports, no shared mutable state, a `@TempDir` where a file is needed, a fresh server
per integration test, and assertions with messages. Gradle runs go through `heavy` with `--max-workers=2`, targeted. Frontend tests run
with `npm test` in `reposilite-frontend`.

Depends on: `add-plugin-ui-extension-point` (merged), `expose-plugin-access-checks` (must land before any plugin registers a reader
page), `add-plugin-ui-browse-blocks` (soft, extends the allowed block set), `extend-plugin-abi-gate` (soft, reports the new types).

Each code pull request stays under about 400 changed lines, excluding tests and fixtures, and builds and passes on its own.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose add-plugin-reader-pages. Verify: `openspec validate add-plugin-reader-pages --strict --no-interactive`
  passes and the dash grep from the proposal prints nothing.

## 2. Code pull request 1: model and registration (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [x] 2.1 `test(frontend)`: `PluginUiReaderPageValidatorTest`, one test per refusal (form, action and status blocks, a source outside the
  prefix, 21 pages, 31 blocks, an id that does not match) and one for a valid page. Fails before 2.2.
- [x] 2.2 `feat(frontend)`: add `PluginUiReaderPage`, `PluginUiValidator.readerPageProblems`, `PluginUiRegistrar.registerReaderPage`
  and the per-plugin limits. Dropped pages log a warning that names the plugin and the problem.
- [x] 2.3 `test(frontend)` and `feat(frontend)`: the switch `INGOT_LOCAL_PLUGINREADERS` with the fallback `REPOSILITE_LOCAL_PLUGINREADERS`,
  default `true`, read through `LocalConfiguration` the way `pluginUi` is read. A unit test covers the default and the fallback.
- [x] 2.4 `docs(frontend)`: the switch in `docs/gitbook/references/configuration/configuration-and-startup.md` and the registration rules
  in `docs/gitbook/references/plugins/plugin-api.md`.

## 3. Code pull request 2: serving readers (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [ ] 3.1 `test(frontend)`: `PluginUiReaderManifestIntegrationTest`: anonymous and signed-in callers get the same reader pages; `GET
  /api/frontend/plugin-ui` still answers `401` to an anonymous caller; the manifest has no stylesheet and no `enabledPlugins`. Fails before 3.3.
- [ ] 3.2 `test(frontend)`: `PluginUiReaderGuardIntegrationTest`: a declared source answers an anonymous `GET` and `HEAD`; `POST` to the same
  path answers `401`; `..`, `//`, an empty segment and `%2e` do not match; another plugin's source does not match; the admin floor still
  answers `403` for a non-source route; the switch answers `404` without running the handler; the answer carries `Vary: Cookie,
  Authorization` and `Cache-Control: private, no-cache` unless set by the plugin.
- [ ] 3.3 `feat(frontend)`: `PluginUiReaderEndpoint` for `GET /api/frontend/plugin-ui/readers`, and the exemption lookup in
  `PluginUiDataGuard` (design D5, in the order given there) with the response headers.
- [ ] 3.4 `docs(frontend)`: the endpoint in `docs/gitbook/references/http-api/http-api.md` and the rules of reader sources in
  `docs/gitbook/references/plugins/plugin-api.md`, including the viewer contract of design D6.

## 4. Code pull request 3, the user-visible change (`feat(frontend)`, `test(frontend)`, `docs(frontend)`)

- [ ] 4.1 `test(frontend)`: `reposilite-frontend/src/navigation/registry.test.ts` (new cases): `readerPages()` lists the manifest pages
  in order, the "Plugins" heading is absent with no page, a reader page is not in the bottom bar, and `unlistedRoutes` accepts
  `plugin-reader`. `pluginUi.test.ts`: the reader manifest loads for an anonymous visitor.
- [ ] 4.2 `test(frontend)`: `reposilite-frontend/src/components/plugins/PluginBlock.test.ts` (new case): a `form`, `action` or `status`
  block in a reader page shows the unsupported note and sends no request.
- [ ] 4.3 `test(frontend)`: `reposilite-frontend/server/lib/route-classifier.test.js`: `/plugins/x` is a document for a navigation, a
  proxy otherwise, and `/api/...` is unchanged.
- [ ] 4.4 `feat(frontend)`: the route `plugin-reader` in `routes.ts`, the reader view (`PluginPageView.vue` with a reader prop), the
  registry functions, the Browse heading, the store change in `pluginUi.ts`, and the `navigationSegments` list in the split classifier.
- [ ] 4.5 `test(frontend)` and `feat(frontend)`: the startup warning for a repository named `plugins` while a reader page exists, with a
  test that asserts the message through a captured logger.
- [ ] 4.6 `docs(frontend)`: the route in `docs/gitbook/references/http-api/dashboard-pages.md` and the "Who can open it" row for the reader
  route, the sign-in rule and the Browse entry in the same page.
- [ ] 4.7 Verify in a browser at 320, 600 and 1440 px that a reader page has no horizontal scroll, and that the Browse heading appears
  only with a reader page. Screenshots go to the `pr-screenshots` branch and are linked in the pull request.

## 5. Pull request

- [ ] 5.1 Open code pull requests 1 and 2 under the titles `feat(frontend): accept reader pages in the plugin ui facade` and
  `feat(frontend): serve reader pages to anonymous visitors`, each from a branch of the remote main branch, each with no file under
  `openspec/`.
- [ ] 5.2 Open code pull request 3, last, under the title `feat(frontend): let plugins contribute pages for readers`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `feat`). The description tells the story of a reader who has no
  administrator account and still finds the plugins of the repositories they may read. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body has no
  em or en dash.

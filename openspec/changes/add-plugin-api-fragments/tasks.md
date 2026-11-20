# Tasks

Each group is one pull request, or one step of one. A pull request has the title named in its heading, which is the single commit that
lands on main. Inside a pull request the commits have one type each: the failing test first (`test(plugin)`), then the Gradle file
(`build(plugin)`), then the change (`feat(plugin)`), then the plugin author page (`docs(plugin)`). Tests are written first and seen to
fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, no
shared mutable static state, a `@TempDir` per test for every fixture jar, and assertions with messages that name the missing or
unexpected entry. WARN lines are asserted through a captured logger, never read from the console. Gradle runs go through `heavy` with
`--max-workers=2` and `--tests`, never as the full suite (integration tests run with `:reposilite-backend:integration --tests`). Each
code pull request stays near 400 changed lines. No code pull request contains a file under `openspec/`. Checkboxes are ticked in a
follow-up pull request, never in a code pull request.

## 0. Preconditions (results recorded in `design.md`, before the first code pull request)

- [x] 0.1 Record the baseline: on `origin/main`, run `OpenApiSnapshotIntegrationTest` with
  `heavy ./gradlew :reposilite-backend:integration --tests 'com.reposilite.web.OpenApiSnapshotIntegrationTest' --max-workers=2`, and the
  settings tests with `:reposilite-backend:test --tests 'com.reposilite.configuration.shared.*'`. Save the bytes served at `/openapi` by
  the snapshot test's server to a file outside the repository. Verify: both runs are green, and the saved bytes are the reference of task
  1.4.
- [x] 0.2 Confirm where `openapi-annotation-processor` writes `openapi-plugin/openapi-default.json`, `openapi-plugin/.index`,
  `json-schemes/index` and `json-schemes/<class name>` for a module (kapt class output or a resource directory), by building a throwaway
  module with one `@OpenApi` route and one `@JsonSchema` class in a throwaway checkout. Verify: the paths are written into design D4.
- [x] 0.3 Confirm that Javalin builds the document once per process and that the definition processor runs after every plugin has
  loaded, by reading `OpenApiPlugin` of the version `javalinOpenApi` that the backend build declares. Verify: the answer is written into design D2.

## 1. Fragment merge (pull request 1)

PR title: `feat(plugin): merge the api document fragments of loaded plugins`

- [x] 1.1 `test(plugin)`: failing tests `PluginOpenApiFragmentsTest` in
  `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/plugin/openapi/`, each over in-memory JSON with a recording warning sink:
  no fragment returns the same node object; a fragment adds its paths and schemas; a server path and a server schema are kept and each
  logs one line naming the plugin and the key; two plugins with one schema keep the lower id; the same input in two registration orders
  gives equal documents; a fragment that is not JSON, and one whose `paths` is an array, add nothing and log one line naming the plugin;
  a fragment without `components` adds its paths and creates nothing else; a server document without `components` gains
  `components.schemas` only when a fragment adds a schema. Verify: they fail because the class does not exist.
- [x] 1.2 `test(plugin)`: failing test `PluginOpenApiFragmentSourceTest` in the same package, with fixture jars in a `@TempDir` opened in
  their own `URLClassLoader`: for a plugin whose class comes from a jar with `META-INF/ingot/openapi/fixture.json`, the source returns
  `fixture` with those bytes; a jar with `other.json` only returns nothing for `fixture`; two plugins of one shared loader each get only
  their own file; the list is sorted by plugin id. Verify: it fails because the class does not exist.
- [x] 1.3 `feat(plugin)`: add the internal `PluginOpenApiFragments` (merge, D2) and its source (lookup by id through the plugin class's
  loader, D1) in `net.onelitefeather.ingot.plugin.openapi`, and call the merge first in the definition processor of
  `JavalinConfiguration.configureOpenApi`, with WARN lines through the server logger. Verify: the tests of 1.1 and 1.2 pass, and the plugin
  ABI gate reports no change.
- [x] 1.4 Verify the drop-in: `OpenApiSnapshotIntegrationTest` passes without `-PrefreshOpenApiSnapshot`, `docs/openapi/ingot-openapi.json`
  is not in the diff, and the bytes served at `/openapi` equal the reference saved in 0.1.

## 2. Schema lookup in the plugin jar (pull request 2)

PR title: `feat(plugin): read the settings schema of a plugin from its own jar`

- [x] 2.1 `test(plugin)`: failing tests `PluginSettingsSchemasTest` in
  `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/plugin/schema/`, with a fixture jar in a `@TempDir` that holds a settings
  class and `json-schemes/<its name>`, opened in its own `URLClassLoader`: the lookup returns the file's bytes; a jar without the file
  returns null; a type on the server's class loader is not looked up. Verify: they fail because the class does not exist.
- [x] 2.2 `test(plugin)`: failing test in `reposilite-backend/src/test/kotlin/com/reposilite/configuration/shared/` that
  `SharedConfigurationFacade`, given a domain whose type comes from the fixture jar with its schema, serves that schema and logs no
  `Cannot find scheme` line, and given the fixture jar without the file, logs one such line and serves the runtime schema. Verify: the
  first case fails, the second passes on main.
- [x] 2.3 `feat(plugin)`: add the internal `PluginSettingsSchemas` in `net.onelitefeather.ingot.plugin.schema` and call it in
  `SharedConfigurationFacade` before the warning and the runtime generator, only for types not on the facade's own loader (D3). Verify:
  the tests of 2.1 and 2.2 pass, `:reposilite-backend:test --tests 'com.reposilite.configuration.shared.*'` passes unedited against the
  baseline of 0.1, and the plugin ABI gate reports no change.

## 3. Build step for a plugin module (pull request 3, its own pull request)

PR title: `build(plugin): generate the api document fragment and settings schema of a plugin module`

- [x] 3.1 `test(plugin)`: failing tests `PluginApiFragmentsTest` in `buildSrc/src/test/kotlin/`, each with a temporary jar: a jar with
  `META-INF/ingot/openapi/cleanup.json` and `json-schemes/a.B` passes for id `cleanup`; a jar with `openapi-plugin/.index` fails and the
  message names the entry; a jar with `json-schemes/index` fails and names the entry; a jar with `META-INF/ingot/openapi/other.json`
  fails for id `cleanup` and names the entry and the expected id; a jar with no fragment passes (a module without routes). Verify: they
  fail because `PluginApiFragments.kt` does not exist.
- [x] 3.2 `build(plugin)`: add `PluginApiFragments.kt` to `buildSrc/src/main/kotlin/ingot/plugin/` with the `apply(project, pluginId)`
  step of D4 (processor of the backend's version, rename, exclusions in the processed output) and the task `verifyPluginApiFragments`
  wired into `check`. Verify: `:buildSrc:test` passes. In a throwaway checkout, `reposilite-plugins/nexus-import-plugin` with the step
  applied builds a jar that holds no `openapi-plugin/.index` and no `json-schemes/index`, and the result is not committed.
- [x] 3.3 `docs(plugin)`: a section "Document routes and settings" in `docs/gitbook/manage/plugins/write-a-plugin.md` with the file layout,
  the call of the step for modules of this repository, the plain Gradle lines for a third-party build, and the merge rules (server wins,
  lower id wins, a broken fragment is skipped). Mark the file layout as experimental in `docs/gitbook/references/plugins/plugin-api.md`.
  Verify: the pages name `META-INF/ingot/openapi/<plugin id>.json` and `json-schemes/<class name>` exactly as the code does.

## 4. Open the code pull requests

- [x] 4.1 Open the code pull requests in the order of groups 1 to 3, each branched from the remote main branch that holds the previous
  one and the merged proposal, under `feat(plugin): merge the api document fragments of loaded plugins`,
  `feat(plugin): read the settings schema of a plugin from its own jar` and
  `build(plugin): generate the api document fragment and settings schema of a plugin module`. Each one is opened only after its tests
  pass and with no file under `openspec/`. Verify: each title is a valid Conventional Commit, and `gh pr view` shows the expected title
  and files.

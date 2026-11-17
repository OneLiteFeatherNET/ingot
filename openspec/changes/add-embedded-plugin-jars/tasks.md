# Tasks

Each group is one pull request, or one step of one. A pull request has the title named in its heading, which is the single commit
that lands on main. Inside a pull request the commits have one type each: the failing test first (`test(plugin)`), then the Gradle file
(`build(plugin)`), then the change (`feat(plugin)`), then the operator page (`docs(plugin)`). Tests are written first and seen to fail
before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network, no shared
mutable state, a `@TempDir` per test, the fallback directory of the extractor injected as a temporary directory of the test, and
assertions with messages. Loader tests read log lines from a captured logger, never from the console. Gradle runs go through `heavy`
with `--max-workers=2` and `--tests`, never as the full suite. Each code pull request stays near 400 changed lines. No code pull
request contains a file under `openspec/`. Checkboxes are ticked in a follow-up pull request, never in a code pull request.

## 0. Preconditions (results recorded in `design.md`, before the first code pull request)

- [ ] 0.1 Record the baseline: run `./gradlew :reposilite-backend:test --tests 'com.reposilite.plugin.*' --tests 'net.onelitefeather.ingot.plugin.*'`
  on `origin/main` through `heavy`, and note the count of passing tests. Verify: the baseline is green, so a later failure is caused by
  this change.
- [ ] 0.2 Confirm that a `from` of a module's shadow jar copies the file as it is. Build the shadow jar of
  `:reposilite-plugins:nexus-import-plugin` in a throwaway checkout and compare its SHA-256 with the entry of a server jar that copies
  it in. Record the result in design D1. If the copy is merged instead, D1 changes before pull request 2.
- [ ] 0.3 Confirm that no Gradle cycle appears when a plugin module compiles against the backend and the backend's shadow jar takes the
  plugin's shadow jar. Run `./gradlew :reposilite-backend:shadowJar --dry-run` with a fixture module named, in a throwaway checkout.
  Record the result in design D1.
- [ ] 0.4 Read `add-plugin-support-tiers` on `main`, the rule "Core requires a bundled jar", and record in open question 5 whether the tier
  check would fail an embedded module that declares Core. Verify: the answer is written in `proposal.md`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-embedded-plugin-jars`: this change and nothing else, branched from the remote main branch.
  Verify: `npx -y @fission-ai/openspec@latest validate add-embedded-plugin-jars --strict` passes and the dash grep from the proposal
  prints nothing.

## 2. Version rule and origin (pull request 1)

PR title: `feat(plugin): add the embedded origin and the plugin version rule`

- [ ] 2.1 `test(plugin)`: failing tests `PluginVersionTest` in `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/plugin/version/`:
  `1.2.10` is higher than `1.2.9`; `1.2` equals `1.2.0`; `1.0.0-SNAPSHOT` is lower than `1.0.0`; two pre-releases of the same numbers are
  equal; `+build` is ignored; `1.2.3.4`, `latest`, a blank string and a missing value are invalid and count as lowest. Verify: they fail
  because `PluginVersion` does not exist.
- [ ] 2.2 `test(plugin)`: failing rows in `PluginTierResolverTest` for `PluginOrigin.EMBEDDED`: a declared Community or no tier resolves
  to Core, a declared Experimental stays Experimental, a declared Core resolves to Core. Verify: they fail because the value does not exist.
- [ ] 2.3 `feat(plugin)`: add `PluginVersion` (parse and compare) in `net.onelitefeather.ingot.plugin.version`, add `EMBEDDED` to
  `PluginOrigin` and the mapping in `PluginTierResolver`. Verify: the tests of 2.1 and 2.2 pass, the existing tests of the plugin
  package pass unedited, and the plugin ABI gate reports no change.

## 3. Build hook (pull request 2)

PR title: `feat(plugin): embed a named plugin jar into the server jar`

- [ ] 3.1 `test(plugin)`: failing tests `EmbeddedPluginJarsTest` in `buildSrc/src/test/kotlin/`, each with a temporary jar: a server jar
  without embedded entries passes; a named module with no entry fails and the message names the module; an entry without
  `Implementation-Version` fails; an `embedded-plugins/` entry that no module names fails. Verify: they fail because
  `EmbeddedPluginJars.kt` does not exist. The test dependencies are declared in `buildSrc/build.gradle.kts`.
- [ ] 3.2 `build(plugin)`: add `EmbeddedPluginJars.kt` to `buildSrc/src/main/kotlin/`, the list `embeddedPluginProjects` (empty), the
  configuration `embeddedPlugin` and its dependencies, the `from` of the shadow jar into `embedded-plugins`, and the task
  `verifyEmbeddedPlugins` wired into `check` of `reposilite-backend`. Verify: `:buildSrc:test` passes, and
  `heavy ./gradlew :reposilite-backend:shadowJar :reposilite-backend:verifyEmbeddedPlugins --max-workers=2` produces a jar with no
  `embedded-plugins/` entry. Then, in a throwaway checkout, a named fixture module produces exactly one entry, and the result is not committed.

## 4. Loader for embedded jars (pull request 3)

PR title: `feat(plugin): load plugin jars embedded in the server`

- [ ] 4.1 `test(plugin)`: failing tests `EmbeddedPluginSourceTest` and `EmbeddedPluginExtractorTest` in
  `reposilite-backend/src/test/kotlin/net/onelitefeather/ingot/plugin/embedded/`:
  the source lists `.jar` entries under `embedded-plugins/` from a jar fixture and from a classes directory fixture; a server without
  entries creates no directory; a rewrite removes a stale jar and a `.partial` file; a jar is written in full; when the extraction
  directory cannot be written (a regular file occupies its path) the fallback root of the test receives the jar and one WARN line names
  both directories; when neither can be written one ERROR line names the jar and the start does not throw. Verify: they fail because the
  classes do not exist.
- [ ] 4.2 `test(plugin)`: failing tests `EmbeddedPluginLoadingTest` in `reposilite-backend/src/test/kotlin/com/reposilite/plugin/`, with an
  embedded fixture jar made by `PluginJarFixture`: the fixture plugin is registered with origin `EMBEDDED` and effective tier Core; its
  class loader is not the server loader and its jar is in the loaders of the storage discovery; a store plugin with the same id is
  shadowed; a bundled jar with the same id is skipped; an embedded id that a built-in plugin holds stops the start with the existing
  duplicate message; a plugins-directory jar with the same id stops the start with the duplicate message, until pull request 4 replaces
  that behaviour; with no embedded jar the registered plugins and the start log equal the baseline of 0.1. Verify: they fail.
- [ ] 4.3 `feat(plugin)`: add the package `net.onelitefeather.ingot.plugin.embedded` with `EmbeddedPluginSource` and
  `EmbeddedPluginExtractor` (D4, D5). In `PluginLoader` add the constructor parameters `embeddedPluginSource` and
  `embeddedPluginsDirectory` with defaults that read the code source and `<workingDirectory>/embedded-plugins`, load each extracted jar in
  its own loader (D6), register the candidates without a plugins-directory copy after the scan, and extract the declared-names helper of
  the store path so both use it. `ReposiliteFactory` passes the working directory. Verify: the tests of 4.1 and 4.2 pass and the existing
  plugin tests pass unedited. The commit body lists the existing tests that were run.

## 5. Replacement by a newer plugins-directory jar (pull request 4)

PR title: `feat(plugin): let a newer plugins directory jar replace the embedded copy`

- [ ] 5.1 `test(plugin)`: failing tests `PluginVersionPrecedenceTest` in `reposilite-backend/src/test/kotlin/com/reposilite/plugin/`, with a
  captured logger and an embedded fixture: a higher plugins-directory version is registered with origin `PLUGINS_DIRECTORY` and the embedded
  copy is not registered; an equal version and a lower version keep the embedded copy; a missing and an invalid version keep the embedded
  copy and the start goes on; exactly one INFO line in the form of the spec is logged for each decision and none without a directory copy;
  two plugins-directory jars with one id stop the start with `Plugin <id> is already registered`; a replacing copy that declares Core
  reads as Community and one that declares Official reads as Official; another id loads as before. Verify: they fail.
- [ ] 5.2 `feat(plugin)`: change `loadPluginsByServiceFiles` to decide each held id (D7), and make `declaredByJarsIn` return the map of class
  name to jar. The decision follows D3 and D7 exactly. Verify: the tests of 5.1 pass, the test of 4.2 that pinned the duplicate message is
  replaced by the new behaviour and the commit body says so, and the other existing plugin tests pass unedited.
- [ ] 5.3 `docs(plugin)`: update `docs/gitbook/manage/plugins/install-a-plugin.md` with the update path (a newer jar in the plugins
  directory replaces the embedded copy, and the INFO line to look for), `docs/gitbook/references/configuration/configuration-and-startup.md`
  with the `embedded-plugins/` directory, the rewrite at start and the temporary-directory fallback, with a note that a tmpfs on the
  temporary directory is an option for read-only containers, and `docs/gitbook/references/plugins/plugin-api.md` with the rule that an
  embedded plugin sets `@Plugin.version` to the manifest version and that a constructor runs even for a copy that loses. Verify: the
  links resolve, and no file outside `docs/gitbook/` changes.

## 6. Open the code pull requests

- [ ] 6.1 Open pull request 1 under `feat(plugin): add the embedded origin and the plugin version rule`, after the proposal has merged.
  Verify: the title is the one of group 2 and the description follows the repository template.
- [ ] 6.2 Open pull request 2 under `feat(plugin): embed a named plugin jar into the server jar`, after pull request 1 has merged.
  Verify: the title is the one of group 3, and the result of 0.2 and 0.3 is linked in the description.
- [ ] 6.3 Open pull request 3 under `feat(plugin): load plugin jars embedded in the server`, after pull request 2 has merged.
  Verify: the title is the one of group 4.
- [ ] 6.4 Open pull request 4 under `feat(plugin): let a newer plugins directory jar replace the embedded copy`, after pull request 3 has
  merged. This is the last task of the change. Verify: the title is the one of group 5, and the pull request states that
  `extract-javadoc-plugin` may start its code pull requests after it merges.

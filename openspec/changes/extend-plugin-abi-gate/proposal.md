# Proposal

## Why

The plugin ABI gate (`openspec/specs/plugin-abi-gate`, archived as `gate-plugin-abi`) compares the jar of the server with Reposilite
3.5.28, and only for the packages `com.reposilite.*` that the documented Reposilite surface uses. That answers one promise: a plugin
written for Reposilite keeps running on Ingot. It does not answer the second promise that Ingot makes to its own plugins, which is
the surface under `net.onelitefeather.ingot.*`. The documentation says so: `docs/gitbook/references/plugins/plugin-api.md`, section
"Compatibility checks", lists Ingot's own packages under "Not compared".

That surface is now what plugins are built against. The repository format SPI (`net.onelitefeather.ingot.format`), the search backend
SPI (`net.onelitefeather.ingot.search.backend`), the storage capabilities (`net.onelitefeather.ingot.storage.capability`) and the
repository registry (`net.onelitefeather.ingot.repository`) are documented for plugin authors. The portal plugins of this repository
(`add-gradle-plugin-portal-plugin`, `add-minecraft-plugin-portal-plugin`) add more: an enrichment hook, search attributes, facets and an
access check. A rename or a changed signature in any of them compiles, passes every test in the monorepo, and fails on an operator's
server the first time a plugin calls it.

Facts checked against `main` at `3614657d0`:

- `reposilite-backend/build.gradle.kts`, `pluginApiPackages` (about line 503) lists fifteen `com.reposilite.*` packages. No
  `net.onelitefeather.ingot` package is in it, and `pluginApiReport` (about line 523) uses that list only.
- The Ingot bundle is published as `net.onelitefeather.ingot:ingot` at each release (`reposilite-backend/build.gradle.kts`, publication
  `bundle`, `artifactId = "ingot"`). The last release is `v1.1.1` (tag, 2026-10-16), so a baseline exists. Whether that release is
  visible in the release repository is an open question (Q1).
- Experimental surface is marked with three annotations: `@ExperimentalIngotFormatApi` (`format/RepositoryFormat.kt`, level ERROR),
  `@ExperimentalIngotSearchApi` (`search/backend/SearchBackendApi.kt`, WARNING) and `@ExperimentalIngotStorageApi`
  (`storage/IngotStorageProviderFactory.kt`, WARNING). The storage capability interfaces carry the storage marker.
- `RepositoryRegistry` (`repository/RepositoryRegistry.kt:28`) is documented as "not yet stable for plugins", yet it has no marker.
  The gate therefore cannot tell its status from the code alone (Q3).
- The CI job "Plugin API" (`.github/workflows/ci.yml`, about line 564) runs `pluginApiReport` and the canaries, and writes the Markdown
  report to the job summary. It is report-only (`plugin-abi-gate`, "Report-only CI job").
- Kotlin `internal` declarations are public in bytecode. The archived design (`gate-plugin-abi/design.md`, D1) names this as the reason
  for a fixed package list. It applies here too, and it needs a stronger filter for Ingot's packages, which contain internal types such
  as the constructor of `SearchFacade`, which is internal to `net.onelitefeather.ingot.search.application`, a package that is not in the list.

## What Changes

- **Ingot package list.** A second list in `reposilite-backend/build.gradle.kts`, `ingotPluginApiPackages`, with the packages that
  plugin-api.md documents (`net.onelitefeather.ingot.format`, `.repository`, `.search.backend`, `.storage`, `.storage.capability`), the
  plugin API that Ingot added under the `com.reposilite.*` name (`com.reposilite.frontend.api`, home of the dashboard facade `PluginUiFacade`,
  which neither existing comparison sees), and the packages of the prerequisites that land first (`add-plugin-api-artifact`,
  `add-artifact-enrichment-hook`,
  `add-search-document-attributes`, `add-search-attribute-facets`, `expose-plugin-access-checks`). A unit test (in `.github/scripts/tests`,
  Python `unittest`, reading the build script and the documentation as text) fails when a documented Ingot package is missing from the list.
- **Baseline and task.** A new task `ingotPluginApiReport` compares `net.onelitefeather.ingot:ingot:<ingotBaseline>` with the jar of the
  current build over `ingotPluginApiPackages`, writes HTML and Markdown under `build/reports/plugin-api/`, and never fails. The baseline is a
  single property, `ingotBaseline`, in the build script, set to the last release and changed only by the release change.
- **Classification.** Every finding is marked either `experimental` or `promised`. A type or member counts as experimental when it carries an
  annotation whose name starts with `ExperimentalIngot`, or when its package is listed in `ingotExperimentalPackages` in the build script.
  The report shows the two groups in separate sections. Only the promised group is a candidate for blocking later.
- **Internal filter.** Before the comparison, a task reads the Kotlin metadata of the classes in the Ingot packages and writes the names of
  the `internal` classes into a file. The comparison excludes them. Members of public classes whose names carry the mangling that Kotlin
  gives an `internal` member (a `$` in the name) are excluded as well. The metadata reader is a pinned dependency of the build.
- **CI job.** The job "Plugin API" gets one more step, `ingotPluginApiReport`, and a section "Ingot plugin API" in the job summary, with the
  counts per group. The job stays report-only, and the existing HTML artifact `plugin-api-report` carries the new report too.
- **Documentation.** `docs/gitbook/references/plugins/plugin-api.md`, "Compatibility checks": the table gets the Ingot baseline and the Ingot
  packages, and "Not compared" loses the Ingot row. The sentence that the gate is report-only stays, for both reports.

### Out of scope

- Making either report blocking. That needs an accepted-differences file and a decision per finding, and it is a later change
  (`ci(plugin): enforce the plugin api gate`).
- Packages of Ingot that no plugin is documented to use (for example `net.onelitefeather.ingot.identity`, `.deploy` and `.web`). They
  can be added to the list by a one-line change when a plugin needs them.
- An API dump with a Kotlin binary-compatibility tool for Ingot's packages. A dump answers a different question (did a change break the
  build's own declaration), and it needs a committed baseline file for every package. The comparison with the last release answers the
  question that plugins ask.
- The compatibility of the portal plugins themselves. Each plugin repository builds against the published API artifact in its own CI.
- `com.reposilite.journalist`, which stays out of every list.
- Changing the 3.5.28 comparison, its package list or its canaries.

## Capabilities

### Modified Capabilities

- `plugin-abi-gate`: the gate also covers the Ingot plugin API, against the last Ingot release, with experimental and promised findings
  kept apart. The 3.5.28 requirements stay as they are.

## Impact

- **Type and scope.** The change ships under `ci(plugin)`. Commits use `test(plugin)`, `build(plugin)` and `docs(plugin)`. The gate is the
  purpose, and the build and test commits are its parts. The pull request title, squash-merged onto main, is:

  `ci(plugin): cover the ingot plugin api packages in the abi gate`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, image or plugin changes, and the
  server jar is byte for byte the same. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes by default.** None for an operator. The CI job gets a longer summary and one more report. A pull request that
  changes an Ingot package sees a finding in the summary and nothing fails.
- **Dependencies.**
  - `gate-plugin-abi` (merged): the task, the job and the 3.5.28 comparison that this change extends.
  - The last Ingot release in the release repository (Q1). If it is not there, the baseline is set to the first release that is, and the
    report says so.
  - The prerequisites `add-plugin-api-artifact`, `add-artifact-enrichment-hook`, `add-search-document-attributes`,
    `add-search-attribute-facets` and `expose-plugin-access-checks` (other agents, soft). Each adds its package to the list in its own
    pull request, in the same reviewed line. Until they land, the report simply does not show them.
  - Consumers that the report helps: `add-plugin-reader-pages` (its design D1, a new reader page class) and
    `add-plugin-ui-browse-blocks` (its design D8, four subclasses of the sealed block model). The report must show them as additions.
- **Order.** Default: this change lands after the prerequisites it lists. Recommended: it lands earlier, so that the other prerequisites are
  reported as they arrive. The default order of the portal proposals puts it last; Q2 asks the maintainers to choose.
- **Code.** `reposilite-backend/build.gradle.kts`, `.github/workflows/ci.yml` (one step and one summary section), `.github/scripts/tests`
  (a unit test), `docs/gitbook/references/plugins/plugin-api.md`.

## Verification

- The list test runs with `python3 -m unittest` for the new test module. It needs no network and no Gradle.
- `./gradlew :reposilite-backend:ingotPluginApiReport` runs through `heavy` with `--max-workers=2`, writes both report files, and lists at
  least one finding of each group after a scratch change in an experimental type (not committed).
- The job summary of a pull request shows the Ingot section with the counts per group.
- `openspec validate extend-plugin-abi-gate --strict --no-interactive` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/extend-plugin-abi-gate`
  prints nothing.

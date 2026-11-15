# Proposal

## Why

Ingot promises that a plugin written for Reposilite keeps working when the server underneath it is swapped for Ingot. The
promise is in `CLAUDE.md` (packages stay `com.reposilite.*`), in `VERSIONING.md` (a breaking plugin API change is a major
release) and in `docs/gitbook/reference/plugin-api.md`. Nothing in the build checks it. A refactor that renames a method on
`MavenFacade` or changes a constructor compiles, passes every test, and only fails on an operator's server, as a
`NoSuchMethodError` the first time a third-party plugin calls the old signature.

The promise is already bent, unnoticed. Compared with Reposilite 3.5.28, the release Ingot forked from, `RepositorySettings`
gained `type`, `description` and `tags` in the middle of its constructor, and `MavenSettings` gained a second parameter. The
constructors a 3.5.28 plugin links against no longer exist. No reviewer saw it, because no tool compares the two.

The next steps of the multi-format work (`extract-repository-registry`, `add-repository-format-spi`) touch exactly the types
plugins use most: `MavenFacade`, `Repository`, the storage API. They need a gate before they start, not after.

## What Changes

- **Binary report against Reposilite 3.5.28.** A Gradle task in `reposilite-backend` compares the published upstream jar
  `com.reposilite:reposilite:3.5.28` with the jar of the current build using japicmp, restricted to the documented plugin
  surface (a fixed package list, see design D2). It writes an HTML and a Markdown report and never fails the build.
- **Binary canaries.** An integration test loads the jars that upstream published for `checksum-plugin` 3.5.28 and
  `migration-plugin` 3.5.28, unchanged, into a fresh Ingot instance and checks that each one starts and does its job. These
  are real third-party bytecode, compiled against Reposilite, not against Ingot.
- **Compile canaries.** The bundled sources of `reposilite-plugins/checksum-plugin` and `reposilite-plugins/migration-plugin`
  are identical to upstream 3.5.28 today. The gate compiles and tests them against the current backend and reports when a
  pull request edits their `src/main`, because an edit there can hide an API break.
- **CI job "Plugin API".** Runs on every pull request, writes the Markdown report and the canary results to the job summary,
  and uploads the HTML report. In this change the job is report-only: no finding fails it.
- **Documentation.** A section in `docs/gitbook/reference/plugin-api.md` says what is checked, against which release, and
  where the report is.

### Out of scope

- Making the gate blocking. That is a follow-up (`ci(plugin): enforce the plugin api gate`) once the first report has been
  triaged into an accepted-differences file.
- Fixing the drift the first report shows. Each finding is either restored or accepted in its own change.
- API dumps for Ingot's own packages (`net.onelitefeather.ingot.*`). The format SPI gets one when it is declared stable.
- Plugins built against Reposilite 3.6.x. Ingot's promise is the fork point, 3.5.28 (`VERSIONING.md`).

## Capabilities

### New Capabilities

- `plugin-abi-gate`: the binary comparison against Reposilite 3.5.28, the binary and compile canaries, and the CI report.

## Impact

- **Type and scope.** The change ships under `ci(plugin)`. Commits use `test(plugin)`, `build(plugin)`, `ci(plugin)` and
  `docs(plugin)`; the CI gate is the purpose, the build and test commits are its parts. The pull request title is:

  `ci(plugin): report binary compatibility of the plugin api against reposilite 3.5.28`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest, image or plugin
  changes, and the server jar is byte for byte what it would be without this change. The title carries no `!`.
- **Build.** One Gradle plugin (`me.champeau.gradle.japicmp`, pinned), one test-scope configuration for the canary jars, and
  the content filter of the upstream repository widened by exactly the group `com.reposilite.plugin`.
- **Order.** This change lands first. `add-repository-format-spi` depends on it; `extract-repository-registry` should land
  after it so its report is the first one reviewed against the gate.

## Verification

- The report task runs locally through `heavy` with `--max-workers=2` and produces both report files.
- The canary integration test passes through `:reposilite-backend:integration --tests '*UpstreamPluginCanary*'`.
- `openspec validate gate-plugin-abi --strict` passes and `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/gate-plugin-abi`
  prints nothing.

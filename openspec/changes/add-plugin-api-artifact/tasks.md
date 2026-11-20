# Tasks

Each group is one or more commits of one Conventional Commits type each. Tests are written first and seen to fail before the
production code exists. Every test follows F.I.R.S.T.: no sleeps, no system clock, no shared mutable state, a temporary directory
per test, and assertions with messages. Gradle runs go through `heavy` with `--max-workers=2` and are targeted.

Prerequisite: none. This is the first change of the order in `add-gradle-plugin-portal-plugin` and `add-minecraft-plugin-portal-plugin`.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose `add-plugin-api-artifact`. This change and nothing else. Verify:
  `openspec validate add-plugin-api-artifact --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Closure check and allowlist (red, then green)

- [x] 2.1 `test(plugin)`: failing unit tests of the closure check, with class files built into a temporary directory: an allowlisted
  reference passes; a public signature referring to a class that is neither in the list nor in `plugin-api-provided.txt` fails and
  names the referring class and the target; a public signature referring to a class named in the provided list passes; a reference
  in a private member passes; a JDK or Kotlin standard library type passes; an allowlist line outside `net.onelitefeather.ingot.`
  fails. Verify: they fail on the missing check.
- [x] 2.2 `feat(plugin)`: add `reposilite-backend/plugin-api-classes.txt` with the first list of new Ingot classes only (reviewed in
  this pull request), `reposilite-backend/plugin-api-provided.txt` with the legacy exceptions of D2 (`com.reposilite.plugin.api.*`
  base classes and `com.reposilite.journalist`, reviewed in this pull request), the `pluginApiJar` task and the closure check as a
  Gradle task that fails the build with one line per violation. Verify: the tests of 2.1 pass, and
  `heavy ./gradlew :reposilite-backend:pluginApiJar :reposilite-backend:pluginApiClosure --max-workers=2` passes.
- [x] 2.3 `feat(plugin)`: add `@ExperimentalIngotPluginApi` in `net.onelitefeather.ingot.plugin` with the opt-in level `WARNING`,
  and list it. Verify: a test compiles a sample with and without the opt-in and asserts the warning text.

## 3. Consumer fixture (red, then green)

- [x] 3.1 `test(plugin)`: add `reposilite-test/plugin-api-consumer` with one sample class that uses `@ExperimentalIngotPluginApi` with
  an explicit opt-in, and a test that the sample class carries that annotation and that it is readable at runtime. The module depends
  on the slim jar as a file only and on no legacy base class. Verify: the test fails before the artifact exists.
- [x] 3.2 `build(plugin)`: add the second publication `net.onelitefeather.ingot:ingot-plugin-api` next to `bundle`
  (`reposilite-backend/build.gradle.kts`, around lines 291-296), leaving `bundle` unchanged. Verify: the consumer test of 3.1 passes
  and the bundle jar hash matches the hash of `origin/main` apart from the marker class of 2.3.

## 4. Continuous integration and release (pull requests of their own)

- [x] 4.1 `ci(plugin)`: build the slim jar, run the closure check and the consumer test on every pull request, in the job next to
  `plugin-api` (`.github/workflows/ci.yml`). Verify: the job is green on the pull request and fails when an allowlisted class is
  removed from the list in a local experiment.
- [x] 4.2 `build(plugin)`: publish the slim artifact in the release job with the bundle, and not in the nightly job. Verify: the
  release workflow dry run lists both coordinates and the nightly workflow lists only the bundle.

## 5. Documentation (pull request of its own)

- [x] 5.1 `docs(plugin)`: update `docs/gitbook/references/plugins/plugin-api.md` with both coordinates, the rule that a plugin uses only
  allowlisted classes or the legacy classes of the bundle named in `plugin-api-provided.txt`, and the statement that existing plugins
  need no change. Verify: the page links the allowlist file, the provided list and the consumer fixture.

## 6. Pull requests

- [x] 6.1 Open the code pull requests in the order of groups 2 to 5, each from a fresh `origin/main` that contains the merged
  proposal, each small and with no file under `openspec/`. The first one carries the title
  `feat(plugin): publish a slim plugin api artifact`. The CI pull request is titled
  `ci(plugin): build and check the slim plugin api artifact`, and the documentation pull request is titled
  `docs(plugin): document the plugin api coordinates`. Each pull request body fills `.github/PULL_REQUEST_TEMPLATE.md` and contains no
  session link.

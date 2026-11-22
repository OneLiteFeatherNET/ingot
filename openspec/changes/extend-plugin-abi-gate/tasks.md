# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `plugin`. Tests are written first and seen to
fail before the code they cover exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no network access from
a test, no shared mutable state, a temporary directory per test where a file is needed, and assertions with messages. Gradle runs go through
`heavy` with `--max-workers=2`, targeted and never the full suite. Python tests run with `python3 -m unittest` from `.github/scripts`.
No configuration key, environment variable or deployment manifest changes.

Depends on: `gate-plugin-abi` (merged). Soft: the prerequisites that add packages to the list (each adds its own package in its own pull
request, see design D7). Lands before them (proposal, "Order"; design Q2 and D9, settled 2026-11-17).

Each code pull request stays under about 400 changed lines, excluding tests and fixtures, and builds and passes on its own.

## 1. Proposal

- [x] 1.1 `docs(openspec)`: propose extend-plugin-abi-gate. Verify: `openspec validate extend-plugin-abi-gate --strict --no-interactive`
  passes and the dash grep from the proposal prints nothing.

## 2. Code pull request 1: package lists (`test(plugin)`, `build(plugin)`)

- [x] 2.1 `test(plugin)`: `ingot_package_lists_test.py` in `.github/scripts/tests`. It reads `reposilite-backend/build.gradle.kts` and
  `docs/gitbook/references/plugins/plugin-api.md` as text and asserts that every documented `net.onelitefeather.ingot.*` package and
  `com.reposilite.frontend.api` is in `ingotPluginApiPackages`, and that `com.reposilite.journalist` is in neither list. Fails before 2.2,
  with a message that names the missing package.
- [x] 2.2 `build(plugin)`: add `ingotPluginApiPackages` with the packages of the design, and `ingotExperimentalPackages` with the experimental
  packages named in the documentation. No task is registered yet.
- [ ] 2.3 Verify the Ingot baseline (design Q1): run `./gradlew --build-cache :reposilite-backend:dependencies --configuration` on a detached
  configuration that declares `net.onelitefeather.ingot:ingot:1.1.1`, and record in the pull request whether it resolves from
  `repo.onelitefeather.dev`. If it does not, choose the first release that does, and set `ingotBaseline` to it in task 3.1.

## 3. Code pull request 2: the Ingot report (`build(plugin)`, `test(plugin)`)

- [x] 3.1 `build(plugin)`: add the property `ingotBaseline` (the release of 2.3), the detached configurations for the baseline archive and its
  classpath, and the task `ingotPluginApiReport` with `failOnModification = false`, `onlyBinaryIncompatibleModified = true`, and the
  reports `build/reports/plugin-api/ingot-report.html` and `ingot-report.md`. Verify: the task succeeds and writes both files.
- [x] 3.2 `test(plugin)`: a unit test of the classification rule of design D3 over a small table: an annotation whose simple name starts with
  `ExperimentalIngot` makes a finding experimental, a package in `ingotExperimentalPackages` does too, and everything else is promised. The
  test is a plain Kotlin test in `reposilite-backend/src/test`, with no class files read from disk.
- [x] 3.3 `build(plugin)`: the classification step of D3, which reads the annotations of the new jar with the class-file reader already on the
  build classpath, and the two sections of the Markdown report with their counts.
- [x] 3.4 `build(plugin)`: the internal filter of design D4. The task `ingotInternalClasses` writes the internal class names with
  `kotlinx-metadata-jvm` (pinned), the comparison excludes them and the members with `$` in their names, and a class whose metadata cannot be
  read is logged and kept. Test first: `InternalClassFilterTest` over metadata of one internal and one public class, from fixtures that are
  compiled in the test, with a temporary directory.
- [ ] 3.5 Verify with a scratch change that is not committed: remove a public method of `com.reposilite.frontend.api.PluginUiRegistrar` and
  add a method to an `ExperimentalIngotSearchApi` interface. The report must list the first under promised and the second under experimental.
  Record the result in the pull request.

## 4. Code pull request 3, the complete gate (`ci(plugin)`, `test(plugin)`, `docs(plugin)`)

- [x] 4.1 `test(plugin)`: `plugin_api_summary_test.py` for the summary script that counts the two groups from the Markdown report. It uses a
  fixture report written into a temporary directory. Fails before 4.2.
- [x] 4.2 `ci(plugin)`: add the step `./gradlew --build-cache :reposilite-backend:ingotPluginApiReport` to the job "Plugin API" in
  `.github/workflows/ci.yml`, after the 3.5.28 report, and the section "Ingot plugin API" in `$GITHUB_STEP_SUMMARY` with the counts of each
  group. The step is report-only, and `permissions: contents: read` stays.
- [x] 4.3 `docs(plugin)`: the "Compatibility checks" section of `docs/gitbook/references/plugins/plugin-api.md`. The baseline row gets the Ingot
  release, the packages row gets the Ingot packages with their experimental status, the "Not compared" row loses the Ingot packages, and the
  text says where the Ingot report is found.

## 5. Pull request

- [x] 5.1 Open code pull requests 1 and 2 under the titles `test(plugin): check the ingot package lists against the plugin api docs` and
  `build(plugin): report the ingot plugin api against the last release`, each from a branch of the remote main branch, each with no file under
  `openspec/`.
- [ ] 5.2 Open code pull request 3, last, under the title `ci(plugin): cover the ingot plugin api packages in the abi gate`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections (type `ci`). The description tells the story of a plugin author who builds against an
  Ingot interface that changed, and of a reviewer who sees the change in the job summary before the release. It carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the
  body has no em or en dash.

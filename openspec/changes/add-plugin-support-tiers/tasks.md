# Tasks

Each group is one or more commits of one Conventional Commits type each. The pull request title of a group is the one written in its
heading. Tests are written first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits
by time, no system clock (a `Clock` is injected where time matters), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. The loader tests use a captured appender, not console output. No code pull request contains a file
under `openspec/`. Every group that changes what an operator reads (the startup log, the system information page, the support bundle or
`SUPPORT.md`) also updates the matching page under `docs/gitbook/`.

## 0. Preconditions (checked before the code pull requests)

- [ ] 0.1 Verify that the upstream contract test of `plugin-abi-gate` runs in CI for `checksum-plugin` and `migration-plugin`. Verify: a
  CI job names the test, or the declaration of those two plugins in group 5 waits until it does. Task 5.2 cites this result.
- [ ] 0.2 Verify the state of `add-release-branches-and-backports` on `main`. It is parallel to this change and not a blocker. Verify:
  its status is recorded in the pull request of group 10.1, and `SUPPORT.md` lists the release lines at merge time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-plugin-support-tiers`: this change and nothing else, branched from the remote main branch. Verify:
  `openspec validate add-plugin-support-tiers --strict --no-interactive` passes and the dash grep from the proposal prints nothing.

## 2. Tier type and resolution (red, then green)

PR title: `feat(plugin): add the optional tier to the plugin annotation`

- [ ] 2.1 `test(plugin)`: failing tests for the enum values, the default `COMMUNITY` of the annotation element, a class compiled without the
  element reading Community, and a table test of the origin rule (D2) over origin, declared tier and expected effective tier. Verify: they
  fail on the missing classes.
- [ ] 2.2 `feat(plugin)`: add `PluginTier` in `com.reposilite.plugin.api`, the `tier` element on `@Plugin`, and the pure resolver in
  `net.onelitefeather.ingot.plugin.tier`. Verify: the tests of 2.1 pass, and the existing plugin tests pass unedited.

## 3. Startup log and experimental warning (red, then green)

PR title: `feat(plugin): log the tier of each loaded plugin and warn for experimental ones`

- [ ] 3.1 `test(plugin)`: failing tests with a captured appender: the list line shows `name (tier)` and keeps its chunks; one WARN per
  Experimental plugin; no tier warning for the other tiers; a Core claim from outside the image logs one warning and resolves to
  Community. Verify: they fail.
- [ ] 3.2 `feat(plugin)`: change the list line of `PluginLoader.initialize` and log the Experimental warning after it. Verify: the tests
  of 3.1 pass and the start test of the bundled plugins is unchanged. Note in the commit body that the list line text changes.

## 4. Support information (red, then green)

PR title: `feat(plugin): report the tier in the support information`

- [ ] 4.1 `test(plugin)`: failing tests that `PluginInformation` and `PluginFact` carry `tier`, and that `plugins.json` of the support bundle
  lists it per plugin with the existing fields unchanged. Verify: they fail.
- [ ] 4.2 `feat(plugin)`: add `tier` to `PluginFact`, `PluginInformation` and the collector. Verify: the tests of 4.1 pass and the redaction
  test of `plugins` passes unedited.

## 5. Declarations (red, then green)

PR title: `feat(plugin): declare the tier of the plugins in the repository`

- [ ] 5.1 `test(plugin)`: failing tests, one per plugin module, that the declared tier equals the value of design D8 (prometheus and
  nexus-import Core, opensearch, elasticsearch and meilisearch Experimental, checksum and migration Official, swagger, groovy and example
  Community). Verify: they fail.
- [ ] 5.2 `feat(plugin)`: add the `tier` element to the `@Plugin` of each plugin module. Checksum and migration are declared Official only
  when task 0.1 is verified, and the commit body cites the CI job. Verify: the tests of 5.1 pass, and the bundled compile canaries of
  `checksum-plugin` and `migration-plugin` pass.

## 6. Catalogue reader (red, then green)

PR title: `feat(plugin): read the optional tier of store catalogue entries`

- [ ] 6.1 `test(plugin)`: failing tests that a missing `tier` reads as Community, that `official` reads as Official, that an unknown value
  refuses only its entry, and that a mismatch with the loaded jar logs one warning and shows the jar's tier. Verify: they fail.
- [ ] 6.2 `feat(plugin)`: add `tier` to `PluginCatalogueEntry`, the parser and the comparison in the store loader. Verify: the tests of
  6.1 pass and the existing store tests pass unedited. The schema stays `1`.

## 7. Tier check in CI and in the release (red, then green)

Each group is one pull request, in this order.

PR title: `ci(plugin): check experimental opt-ins against the declared tier`

- [ ] 7.1 `test(plugin)`: failing Gradle tests of `checkPluginTiers` over fixture modules in a temporary directory: an opt-in without
  declaration fails; a declaration without opt-in fails; a module with neither passes. Verify: they fail.
- [ ] 7.2 `ci(plugin)`: add the task `checkPluginTiers` in `buildSrc`, its write of `build/plugin-tiers.json`, and the job `Plugin tiers` in
  `ci.yml` on pull requests to `main`. Verify: the tests of 7.1 pass and the job runs on the pull request.

PR title: `ci(plugin): require contract tests and a CI job for official plugins`

- [ ] 7.3 `test(plugin)` then `ci(plugin)`: failing fixture tests that an Official module without a `contractTest` source set fails, and that
  a module not named by any workflow fails. Then the rule in `checkPluginTiers`. Verify: the tests pass and `checksum-plugin` and
  `migration-plugin` pass the rule.

PR title: `ci(plugin): match the core tier with the bundled jars of the image`

- [ ] 7.4 `test(plugin)` then `ci(plugin)`: failing fixture tests that a Core declaration without a bundled jar fails, and that a bundled
  jar without a Core or Experimental declaration fails, with the `Dockerfile` lines as input. Then the rule. Verify: the tests pass and the
  check passes on `main`.

## 8. Catalogue generator (red, then green)

PR title: `feat(plugin): write the tier of each jar into the plugin catalogue`

- [ ] 8.1 `test(plugin)`: failing Python tests in `.github/scripts/tests` that the generator writes `tier` in lower case, that a missing
  plugin in the tier file stops the run, that a changed tier of a listed version stops the run and uploads nothing, that a yanked version
  keeps its tier, and that two runs are identical. Verify: they fail.
- [ ] 8.2 `feat(plugin)`: read `build/plugin-tiers.json` in the generator and write the `tier` field; run the tier check before the
  generator in the release job. Verify: the tests of 8.1 pass and the release workflow test in `.github/scripts/tests` passes unedited.

## 9. System information page (red, then green)

PR title: `feat(ui): show the tier next to each plugin in system information`

- [ ] 9.1 `test(ui)`: failing component tests that the plugin list of `SystemInfoView` shows the tier label next to the version, and that a
  plugin without a tier shows Community. Verify: they fail.
- [ ] 9.2 `feat(ui)`: add the label to the existing plugin list. No new page. Verify: the tests of 9.1 pass, and before and after screenshots
  at 320, 600 and 1440 pixels are pushed to the `pr-screenshots` branch and linked in the pull request.

## 10. Documentation

PR title: `docs(support): describe the plugin tiers in SUPPORT.md`

- [ ] 10.1 `docs(support)`: add the tier section to `SUPPORT.md`, with the release lines it lists as supported and the link to the
  definitions. Core and Official support follows those lines, not the latest release. Note in the pull request the state of
  `add-release-branches-and-backports` from task 0.2. Verify: `openspec/specs/support-policy` scenarios are met by the text.

PR title: `docs(gitbook): describe the four plugin tiers and show a badge on plugin pages`

- [ ] 10.2 `docs(gitbook)`: add `references/plugins/plugin-tiers.md`, link it in `SUMMARY.md`, add a `Tier: <name>` line to each plugin page,
  and describe the new log line and the warning in the upgrade notes under `docs/gitbook/release-notes/`. No emoji. Verify: no page links
  to a missing file, and the page lists the four tiers with the support of each.

## 11. Open the code pull requests

- [ ] 11.1 Open the code pull requests in the order of groups 2 to 10, each on the remote main branch that holds the previous one, with the
  titles named in the groups above. Each one is opened only after its tests pass and no file under `openspec/` is in it. Verify: each pull
  request title is a valid Conventional Commit, and `gh pr view -R OneLiteFeatherNET/ingot` shows the expected title and files.

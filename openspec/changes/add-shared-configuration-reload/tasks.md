# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `configuration`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (the reload never reads the time, the poll schedule is not waited for and `reloadIfChanged` is called directly), no fixed
ports, no shared mutable state, a temporary directory per test, and assertions with messages. Gradle runs go through `heavy`,
with `--max-workers=2`, targeted and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-shared-configuration-reload`: this change and nothing else. Verify:
  `openspec validate add-shared-configuration-reload --strict` passes and the dash grep from the proposal prints nothing.

## 2. Switch

- [ ] 2.1 `test(configuration)`: write failing tests for `SharedConfigurationReload.read(environment)`: unset is off; `true` and
  `FALSE` in any case; `INGOT_SHARED_CONFIGURATION_RELOAD` wins over `REPOSILITE_SHARED_CONFIGURATION_RELOAD`; the legacy
  spelling alone turns it on; an unknown value is refused with a message that names the variable. Verify: they fail on the
  missing class.
- [ ] 2.2 `feat(configuration)`: add `SharedConfigurationReload` in `com.reposilite.configuration.shared` and read it once in
  `SharedConfigurationComponents`, passing it to the local provider. Verify: the tests of 2.1 pass.

## 3. Watching the file

- [ ] 3.1 `test(configuration)`: write failing tests against a `LocalSharedConfigurationProvider` over a temporary directory:
  with the switch off a changed file is not reported and `isMutable` stays false; with it on, a changed file is reported and
  applied by `reloadIfChanged`; an unchanged file is not reloaded; a rewrite that restores the old modification time is still
  applied; a change made through a `..data` symlink swap is applied; a document that does not parse keeps the settings in force,
  logs exactly one ERROR across two polls (captured appender), and the corrected document is applied afterwards; a domain that
  fails validation keeps its old value while the others load. Also write the integration test
  `SharedConfigurationReloadIntegrationTest`: start Ingot with `--shared-configuration` and the switch, rewrite the file, and
  wait for the new value with a bounded poll that fails after a fixed number of attempts. Verify: the unit tests fail before
  3.2, and the integration test fails before 3.2.
- [ ] 3.2 `feat(configuration)`: keep the SHA-256 of the last loaded content in the local provider, make `isUpdateRequired`
  re-read and compare it, schedule the existing poll for providers that are mutable or externally managed in
  `SharedConfigurationPlugin`, and log one ERROR in `reloadIfChanged` for a document that does not parse. Verify: the tests of
  3.1 pass, and the existing `SharedConfigurationPollTest` passes unedited.

## 4. Read-only file-managed settings

- [ ] 4.1 `test(configuration)`: write failing tests: with the switch on, `updateDomain` and `updateSharedSettings` for a known
  domain return a conflict and leave the file bytes and the settings unchanged, and no `SETTINGS_CHANGED` event is emitted; an
  unknown domain still answers not found; with the switch off the write is stored as today; the `PUT` endpoint answers `409`
  in the `ErrorResponse` shape with the file unchanged. Verify: they fail before 4.2.
- [ ] 4.2 `feat(configuration)`: add the externally-managed flag to `SharedConfigurationProvider` with the default `false`,
  set it for the local provider when the switch is on, and check it in `SharedConfigurationFacade.updateDomain` and
  `updateSharedSettings` after the domain lookup. Verify: the tests of 4.1 pass.

## 5. Documentation

- [ ] 5.1 `docs(configuration)`: in `docs/gitbook/reference/configuration-and-startup.md` replace the "not watched" row of the
  shared settings source table with the switch; in `docs/gitbook/reference/environment-variables.md` add
  `INGOT_SHARED_CONFIGURATION_RELOAD` with its legacy fallback and default; in `docs/gitbook/reference/http-api.md` document the
  `409` of the settings domain PUT under the switch. Verify: the three pages name the same default and the same fallback.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `feat(configuration): reload shared settings from a mounted file`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (a commit to the
  Git file that does not reach the running instance until a restart) without class names. It carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, and the body has no em or en dash.

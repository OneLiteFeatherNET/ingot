# Tasks

This change ships as one code pull request titled `fix(configuration): log unreadable shared and local settings`.
Each group is one or more commits of one Conventional Commits type each, all with the scope `configuration`. The test commit of
each group comes before its fix commit.

Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no shared mutable state, a temporary directory per
filesystem test, and assertions with messages. Log lines are asserted through a captured appender (a real logger writing into
memory, as in `DeployTargetWarningTest`), never through the console. Each test is written before the code it covers and is seen
to fail. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. No task changes a
configuration key, an environment variable or a deployment manifest, so no page under `docs/gitbook/` changes.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose report-configuration-errors: this change (proposal, design, capability spec, tasks) and
  nothing else. Verify: `openspec validate report-configuration-errors --strict` passes and the dash grep from the proposal
  prints nothing.

## 2. Shared settings file (`test(configuration)` then `fix(configuration)`)

- [ ] 2.1 `test(configuration)`: write failing tests on `SharedConfigurationFacade`: a document that is not valid JSON logs one
  ERROR line that names `configuration.shared.json`, the line and the column, and contains no value from the document; the call
  still succeeds with defaults; an empty document logs no ERROR. Verify: the tests fail on the current silent fallback.
- [ ] 2.2 `fix(configuration)`: log the parse error in the `readTree` failure branch of `loadSharedSettingsFromString`, using the
  exception class and the location only (design D2), and give `LocalSharedConfigurationProvider.name()` the resolved file path
  (design D3). Verify: the tests of 2.1 pass, and the existing shared configuration tests pass with no assertion changed.

## 3. Local settings (`test(configuration)` then `fix(configuration)`)

- [ ] 3.1 `test(configuration)`: write failing tests on `LocalConfigurationFactory.applyCustomProperties`: `PORT=abc` logs one ERROR
  containing `PORT` and `int` and still throws `NumberFormatException`; `IDLETIMEOUT=abc` does the same with `long`; `DEBUGENABLED=yes`
  logs one WARN containing `DEBUGENABLED`, without the value, and leaves the setting `false`; `TRUE` logs nothing; a valid
  `8181` logs nothing. Include a case with a `null` journalist that checks the ERROR goes to standard error through an injected
  stream, not through a real `System.err` (design D1). Verify: the tests fail on the missing log lines.
- [ ] 3.2 `fix(configuration)`: add the ERROR for a failed conversion of `Int` and `Long` values and the WARN for a boolean that is not
  `true` or `false`, ignoring case, with the stderr fallback when no journalist exists (design D1). Keep the conversion calls and the
  thrown exception as they are. Verify: the tests of 3.1 pass and the existing `LocalConfigurationFactoryTest` and
  `SearchConfigurationTest` pass with no assertion changed.

## 4. Pull request (`fix(configuration)`)

- [ ] 4.1 `fix(configuration)`: open the code pull request with the title `fix(configuration): log unreadable shared and local settings`,
  from a branch that holds groups 2 and 3, based on the remote main branch that already contains the merged proposal, with no file
  under `openspec/`. Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. Verify: `gh pr view --json title` shows the exact
  title, the commit-style hook accepts the title and every commit, and the body has no em or en dash and no session link.

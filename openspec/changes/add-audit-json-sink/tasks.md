# Tasks

Each group is one or more commits of one Conventional Commits type, all with the scope `audit`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(time is an injected `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, bounded waits
through latches, and assertions with messages. The sink is tested through an injected output stream and a file in a temporary
directory, never the process environment or console. Gradle runs go through `heavy` with `--max-workers=2`, targeted and never
the full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`, integration tests as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-audit-json-sink`: this change and nothing else. Verify:
  `openspec validate add-audit-json-sink --strict` passes and the dash grep from the proposal prints nothing.

## 2. Red tests

- [ ] 2.1 `test(audit)`: write failing tests for the settings resolver as a pure function of an environment map: `INGOT_`
  beats `REPOSILITE_`; stdout only for `true`; a blank file path means off; both unset means off. Verify: they fail on the
  missing class.
- [ ] 2.2 `test(audit)`: write failing tests for the line encoder: the exact field list and order, `null` for absent values,
  `details` as an object, escaping of quotes, backslashes, line feeds and control characters, `time` from an injected `Clock`,
  `occurrences` 1. Verify: they fail before 3.2.
- [ ] 2.3 `test(audit)`: write failing tests for the sink with a stub writer: a full queue drops without blocking and counts;
  a failing writer counts, never throws to the caller and logs at most one warning per injected-clock minute; a file renamed
  between events is reopened; the table insert in `AuditFacade.record` still happens when the sink throws or the writer fails.
  Use a latch to wait for the writer thread. Verify: they fail before 3.3.
- [ ] 2.4 `test(audit)`: write failing tests for `AuditFacade.record` with the sink on: the line is written before the insert and
  survives a failing repository; `actorAddress` is `null` when `recordActorAddress` is `false`; three failed logins give three
  lines and one folded row. Verify: they fail before 3.4.
- [ ] 2.5 `test(audit)`: write failing tests for the repository events with a fake before and after tree: a new id, a removed
  id, a rename, a visibility-only change (no repository event), and that `SETTINGS_CHANGED` is still emitted. Verify: they fail
  before 4.1.

## 3. JSON sink

- [ ] 3.1 `feat(audit)`: add the `AuditSinkSettings` resolver with the `INGOT_AUDIT_SINK_*` names and `REPOSILITE_` fallback.
  Verify: the tests of 2.1 pass.
- [ ] 3.2 `feat(audit)`: add the line encoder and the `AuditSinkLine` schema type with `schema` and `version`. Verify: the
  tests of 2.2 pass.
- [ ] 3.3 `feat(audit)`: add the bounded queue, the single writer thread, the file writer with reopen and mode `0640`, the
  standard output writer, the counters and the rate-limited warning. Verify: the tests of 2.3 pass.
- [ ] 3.4 `feat(audit)`: call the sink from `AuditFacade.record` before the insert, wired in `AuditComponents` with the node id
  from `ClusterSources`, and start and stop it with the plugin. Verify: the tests of 2.4 pass, and the existing audit tests
  pass unedited with the sink off.

## 4. Repository events

- [ ] 4.1 `feat(audit)`: append `REPOSITORY_CREATED` and `REPOSITORY_DELETED` to `AuditAction` and emit them from
  `SharedConfigurationFacade.recordSettingsChange` for the `maven` domain. Verify: the tests of 2.5 pass, and the existing
  settings audit tests pass unedited.
- [ ] 4.2 `test(audit)`: add an integration test that saves the maven domain through the settings API, then reads
  `GET /api/audit?action=REPOSITORY_DELETED` and checks the repository and actor. Run it on SQLite. Verify: it passes.

## 5. Documentation and drop-in checks

- [ ] 5.1 `docs(audit)`: add the two actions to `docs/gitbook/reference/audit-events.md`, a section "JSON lines sink" with the
  field table, the compatibility rule and the failure behaviour, and a "Personal data" section from `design.md`. Add the two
  variables with their fallbacks to `docs/gitbook/reference/environment-variables.md`. Write
  `docs/gitbook/how-to/forward-audit-events-to-a-siem.md` (stdout versus file, `logrotate` and sidecar examples, collector
  filters on `schema`, the per-replica rule, retention and erasure in the collector). Link it from `review-audit-log.md` and
  the gitbook summary. Verify: every new variable in the code appears in the reference page (`git grep` over source and page).
- [ ] 5.2 Drop-in check, no commit: the Container images job runs with no `INGOT_AUDIT_SINK_*` variable set and stays green,
  including the upstream compose file with only the image line swapped and the upgrade rehearsal against the real upstream
  image. If it fails, the change is wrong, not the test.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `feat(audit): add a json lines audit sink and repository events`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the operator's story (evidence the security
  team cannot see, a repository that vanished with no name in the log) without class names. It carries no session link and no
  attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every
  commit, and the body has no em or en dash.

# Tasks

Each group is one or more commits of one Conventional Commits type, all with the scope `logging`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock
(time is an injected `java.time.Clock` or a monotonic source), no fixed ports, no shared mutable state, a temporary directory
per test, and assertions with messages. Log lines are checked through a captured appender or a captured stream, never by
reading console output. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted and never the
full suite. Unit tests run as `:reposilite-backend:test --tests '<pattern>'`, integration tests as
`:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-structured-logging`: this change and nothing else. Verify:
  `openspec validate add-structured-logging --strict` passes and the dash grep from the proposal prints nothing.

## 2. Spike and red tests

- [ ] 2.1 `test(logging)`: spike the file writer. Write a failing test that starts the logging with `INGOT_LOG_FILE=false` in
  a fresh JVM, with the log directory in a temporary directory, and asserts that no rolling file appears while a console
  line still does. It fails on today's code, which proves the problem. Record the mechanism that makes it pass in
  `design.md` (decision 2) before task 3.2. Verify: the test fails for the stated reason before 3.2.
- [ ] 2.2 `test(logging)`: write failing tests for the settings resolver as a pure function of an environment map: `INGOT_`
  wins over `REPOSILITE_`; each variable falls back to its legacy name; unset gives the default; an unknown format or level
  value is refused with the variable name in the message. Verify: they fail on the missing class.
- [ ] 2.3 `test(logging)`: write failing tests for the JSON console line: the fields `time`, `level`, `message`; escaping of
  quotes, backslashes, newlines and control characters; no ANSI sequence; `time` from an injected `Clock`; `trace_id` and
  `span_id` present only when the injected trace source returns ids. Verify: they fail before 4.1.
- [ ] 2.4 `test(logging)`: write failing tests for the levels: `INGOT_LOG_LEVEL` sets the console threshold; the `--level`
  flag wins over it; neither gives INFO; `INGOT_LOG_LEVELS` sets a package override that the captured appender shows; an
  unknown level stops the start. Verify: they fail before 5.1.
- [ ] 2.5 `test(logging)`: write failing tests for the access record, with the in-process web test setup and an ephemeral
  port: one INFO record per request when `INGOT_ACCESS_LOG=true`, with the cache bypass off; none when unset; the record
  holds method, path without query, status, duration, bytes and user; the query string and the `Authorization` value are
  absent; the forwarded client address follows `INGOT_TRUSTED_PROXIES`. The duration comes from an injected monotonic
  source. Verify: they fail before 5.2.

## 3. File writer opt-out

- [ ] 3.1 `feat(logging)`: add the settings resolver for `INGOT_LOG_*` with the `REPOSILITE_LOG_*` fallback, with no
  behaviour attached yet. Verify: the tests of 2.2 pass.
- [ ] 3.2 `feat(logging)`: stop the rolling file writer when `INGOT_LOG_FILE=false`, by the mechanism recorded in
  `design.md`. Verify: the test of 2.1 passes, and the existing logging tests pass unedited with the file writer on.

## 4. Console format and trace fields

- [ ] 4.1 `feat(logging)`: add the Ingot-owned JSON journalist and select it in the single-argument factory overload when
  `INGOT_LOG_FORMAT=json`; the text path stays the library's `PrintStreamLogger`. Verify: the tests of 2.3 pass without the
  trace fields.
- [ ] 4.2 `feat(logging)`: add the `TraceContext` seam and its reflection probe for the OpenTelemetry API, and fill
  `trace_id` and `span_id` from it. Verify: the trace tests of 2.3 pass; a unit test with no API on the test classpath
  asserts that both fields are absent. Check in the spike whether the agent exposes its API to the application class loader,
  and record the result in `design.md` (decision 3).

## 5. Levels and access record

- [ ] 5.1 `feat(logging)`: apply `INGOT_LOG_LEVEL` as the console threshold, with `--level` taking precedence, and
  `INGOT_LOG_LEVELS` as package overrides at start. Verify: the tests of 2.4 pass.
- [ ] 5.2 `feat(logging)`: register the access record handler pair when `INGOT_ACCESS_LOG=true`, independent of
  `bypassExternalCache`, with the trusted proxy resolver for the client address and no query string or credentials.
  Verify: the tests of 2.5 pass, and the default route table in the existing web tests is unchanged.

## 6. Documentation and drop-in checks

- [ ] 6.1 `docs(logging)`: list every `INGOT_LOG_*` and `INGOT_ACCESS_LOG` variable with its `REPOSILITE_` fallback in
  `docs/gitbook/reference/environment-variables.md`, update the log files row there, and describe the JSON lines, trace fields
  and access record in `docs/gitbook/how-to/monitor-status-and-failures.md`. Verify: every new variable in the code appears in
  the reference page (`git grep` over the source and the page).
- [ ] 6.2 Drop-in check, no commit: the Container images job runs with no `INGOT_LOG_*` or `INGOT_ACCESS_LOG` variable set and
  stays green, including the upstream compose file with only the image line swapped and the upgrade rehearsal against the
  real upstream image. If it fails, the change is wrong, not the test.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `feat(logging): add json logs, env log levels and an access log`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the operator's story (a log that cannot be
  joined to its trace, a file that must be mounted just to start, a request nobody can see) without class names. It carries
  no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook
  accepts the title and every commit, and the body has no em or en dash.

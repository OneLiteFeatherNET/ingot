# Tasks

Each group is one or more commits of one Conventional Commits type each. This change ships under `ci(security)`, the
scope of the supply chain changes that merged before it. No group changes a configuration key, environment variable,
image or deployment manifest, so no page under `docs/gitbook/` changes.

The workflow shape test is plain Python with PyYAML, reads `.github/workflows/codeql.yml` fresh in every test, needs no
network and no clock, and lives in `.github/scripts/tests/`, which the `images` job of `ci.yml` already runs with
`python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v`. It therefore runs on every pull
request without a new CI step. No task runs the Gradle test suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-codeql-analysis`: this change and nothing else. Verify:
  `openspec validate add-codeql-analysis --strict` passes and the dash grep from the proposal prints nothing.

## 2. Workflow shape test

- [ ] 2.1 `ci(security)`: add `.github/scripts/tests/test_codeql_workflow.py` with failing tests, one behaviour each:
  the workflow triggers on `pull_request` into `main`, on `push` to `main` and on `schedule`; the matrix contains
  `java-kotlin` and `javascript-typescript`; the `java-kotlin` job selects a manual build and no step uses build mode
  `none` for it; the top-level permissions are exactly `contents: read`; only the `analyze` job declares
  `security-events: write`; no job declares `packages`, `id-token` or `attestations` write; every `uses:` line names a
  40-character SHA with a version comment; every job declares `timeout-minutes`. Verify: the test module fails on main
  with a message naming the missing file, and each test name states one behaviour.

## 3. Workflow and configuration

- [ ] 3.1 `ci(security)`: add `.github/workflows/codeql.yml` with the triggers, the matrix, the pinned references of
  `design.md` (checkout, setup-java, setup-gradle and `github/codeql-action` `init` and `analyze` at the SHAs that
  `ci.yml` and `scorecard.yml` use), the manual build step `./gradlew classes --no-daemon --max-workers=2` for
  `java-kotlin`, `concurrency` cancelling older pull request runs, and the header comment that states the report-only
  behaviour. Add `.github/codeql/codeql-config.yml` with the `paths-ignore` list of `design.md`. Verify: the tests of 2.1
  pass, `python3 -m unittest discover -s .github/scripts/tests -t .github/scripts/tests -v` is green locally, and the
  YAML parses.

## 4. First runs and baseline (no commit)

- [ ] 4.1 Verify on the pull request: both language jobs succeed, the Kotlin extraction log lists backend sources, and the
  runtime is recorded. Verify on main after merge: the first alerts appear under the Security tab, and the runtime of the
  first five Kotlin runs is noted in the pull request of the follow-up change. A maintainer records the baseline date
  in `design.md` as a follow-up, not in this change.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `ci(security): run codeql on java-kotlin and javascript-typescript`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the reviewer what the analysis
  looks for and what a maintainer sees when a finding appears, in domain terms, without workflow internals beyond what a
  reviewer needs. It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact
  title, the commit-style hook accepts the title and every commit, and the body has no em or en dash.

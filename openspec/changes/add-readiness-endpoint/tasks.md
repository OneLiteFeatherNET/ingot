# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `status`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time (the
checks are released through latches, and the bound is a constructor parameter), no system clock (any time source is injected
as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, and assertions with
messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full suite. Integration tests run
as `:reposilite-backend:integration --tests '<pattern>'`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-readiness-endpoint`: this change and nothing else. Verify:
  `openspec validate add-readiness-endpoint --strict` passes and the dash grep from the proposal prints nothing.

## 2. R1 and R2 Readiness checks and response

- [ ] 2.1 `test(status)`: write failing unit tests for the pure evaluator, with the checks passed in as functions: all
  checks pass gives `UP` and an empty `failing`; each check that returns `false` is named; a check that throws is named;
  a check that does not answer within the bound is named (a blocking check released by a latch in the test); the cluster
  check is not evaluated when no bus is attached; `reconnecting` and `disabled` fail the cluster check; the body holds no
  message text of a thrown exception and no repository name. Verify: they fail on the missing class.
- [ ] 2.2 `test(status)`: write failing integration tests for `GET /api/status/ready` on a running instance: `200` with an
  empty `failing` without credentials; `503` with `database` listed when the database check is replaced by a failing one;
  `GET /api/status/health` still answers `200` in that state, and the health body and the manager paths are unchanged. Verify: they fail with `404` before 2.3.
- [ ] 2.3 `feat(status)`: add the evaluator and the probe with the bounded checks (two-second bound, one thread per check),
  the started flag set on `ReposiliteStartedEvent` and cleared on `ReposiliteDisposeEvent`, the database check through the
  `HikariDataSource` of the database connection, the storage check through `getFiles` on each repository's root, and the
  cluster check through the attached `ClusterStatusSource`. Add `maven` to the dependencies of the status plugin
  (`StatusPlugin.kt:48`) and check that no plugin cycle results. Add the route `/api/status/ready` in `StatusEndpoints` with
  an `@OpenApi` annotation listing `200` and `503`. Log each failing check at `WARN` with its detail. Verify: the tests of
  2.1 and 2.2 pass, and the existing `StatusFacadeTest` and `StatusIntegrationTest` pass unedited.

## 3. R3 Unchanged endpoints and docs

- [ ] 3.1 `docs(status)`: update `docs/gitbook/reference/web-routes.md`: add the row for `GET /api/status/ready` to the
  table, replace the sentence "There is no `/api/status/ready` route", and list the four checks. Verify: the table renders
  and no line says that the route is missing.
- [ ] 3.2 `docs(status)`: update `docs/gitbook/how-to/monitor-status-and-failures.md`: replace "There is no separate
  readiness endpoint" and the item "A readiness probe" under "What Ingot does not offer", and describe the readiness check
  with its `503` body and the two-second bound.
- [ ] 3.3 `docs(status)`: update `docs/gitbook/explanation/multi-replica-operation.md` (the bullet "No readiness endpoint",
  line 76): state that `/api/status/ready` answers whether the replica can serve, and that the start-up steps are covered
  by the `plugins` check. Verify: no page in `docs/gitbook/` still says that Ingot has no readiness endpoint:
  `git grep -n -i 'readiness endpoint' -- docs/gitbook` shows only the new text.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `feat(status): add readiness endpoint at /api/status/ready`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of the operator (a replica that
  kept receiving traffic while its database was gone) without class names. It carries no session link and no attribution
  line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit,
  and the body has no em or en dash.

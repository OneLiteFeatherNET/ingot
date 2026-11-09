# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `admin`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock (any time source is injected as a `java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per
test, and assertions with messages. Gradle runs go through `heavy`, with `--max-workers=2`, targeted and never the full
suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`, and unit tests as
`:reposilite-backend:test --tests '<pattern>'`. Container and chart jobs run one at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-read-only-mode`: this change and nothing else. Verify:
  `openspec validate add-read-only-mode --strict` passes and the dash grep from the proposal prints nothing.

## 2. Switch and stored state

- [ ] 2.1 `test(admin)`: write failing tests for the state resolver as a pure function of the environment value, the system
  property and the stored state: forced on by `true`; stored state decides when unset or `false`; default off; `REPOSILITE_`
  fallback; and a stored state re-read after five seconds on a fake clock but not before. Add a test that two compare-and-save
  calls against one revision let exactly one win. Verify: they fail on the missing classes.
- [ ] 2.2 `feat(admin)`: add the resolver, the cache with its five-second maximum age, the stored entry through the existing
  configuration store, and the settings `INGOT_READ_ONLY`, `REPOSILITE_READ_ONLY` and `-Dingot.readOnly`. Verify: the tests of
  2.1 pass.
- [ ] 2.3 `docs(admin)`: document the setting, its precedence and the default in `docs/gitbook/reference/environment-variables.md`.

## 3. Refused writes

- [ ] 3.1 `test(admin)`: write failing tests: `PUT`, `POST`, `PATCH` and `DELETE` on an API route and on a Maven deploy and
  delete answer `503`, `Retry-After` and `application/problem+json` while read-only, and nothing is written; `GET` and `HEAD`
  serve as before; login and refresh are served; a look-alike path is not exempt; a request without credentials gets `401`, not
  `503`; a token without permission gets `403`; `GET /api/status/health` answers `200` `UP`. Verify: they fail before 3.2.
- [ ] 3.2 `feat(admin)`: add the check in the request dispatch after authentication and authorization, with the exact exempt
  route list. Verify: the tests of 3.1 pass and the existing web and Maven route tests pass unedited.
- [ ] 3.3 `docs(admin)`: add the `503` case to `docs/gitbook/reference/api-errors.md` and the refused methods to
  `docs/gitbook/reference/http-api.md`.

## 4. Toggle endpoint and bus event

- [ ] 4.1 `test(admin)`: write failing tests for `GET`, `POST` and `DELETE /api/admin/read-only`: `403` without the maintenance
  area and no change; `200` with the new state and source; `409` naming `INGOT_READ_ONLY` while the environment forces the state;
  one audit event with the actor per stored change. Verify: they fail before 4.2.
- [ ] 4.2 `feat(admin)`: add the endpoint, the facade method that writes the stored state and the audit event, and the OpenAPI
  description. Verify: the tests of 4.1 pass.
- [ ] 4.3 `test(admin)`: write failing tests for the `read-only.changed` envelope: a publish after a stored change, a receiver
  that re-reads at once, an unknown type from an older instance ignored without an error, and no thread or connection when no bus
  is configured (captured appender for the ignored type). Verify: they fail before 4.4.
- [ ] 4.4 `feat(admin)`: add `READ_ONLY_CHANGED("read-only.changed", setOf("enabled"))` to the cluster event types and wire the
  publish and the receive. Verify: the tests of 4.3 pass.
- [ ] 4.5 `docs(admin)`: document the endpoint in `docs/gitbook/reference/http-api.md`, the audit event in
  `docs/gitbook/reference/audit-events.md`, and the maintenance area in `docs/gitbook/reference/permission-nodes.md`, replacing the
  note that no endpoint checks it.

## 5. Scheduled runs wait

- [ ] 5.1 `test(admin)`: write failing tests with a fake claims store and a fake clock: a due cleanup tick while read-only writes
  no claim and deletes nothing; the search verify tick and the rebuild tick do the same; after read-only is turned off the next
  due tick runs once as scheduled; a run due during read-only is not started later. Verify: they fail before 5.2.
- [ ] 5.2 `feat(admin)`: add the guard before the claim in the cleanup, verify and rebuild schedulers. Verify: the tests of 5.1 pass
  and the existing scheduler tests pass unedited.

## 6. Gauge, status and dashboard

- [ ] 6.1 `test(admin)`: write failing tests: the gauge `ingot_read_only` is `1` on and `0` off; the instance status carries
  `readOnly` and the source; the dashboard component shows the banner when on and the toggle only with the maintenance area
  (`npx vitest run` in `reposilite-frontend`). Verify: they fail before 6.2.
- [ ] 6.2 `feat(admin)`: add the gauge, the status fields, the banner and the toggle to the status page. Verify: the tests of 6.1 pass.

## 7. Operator documentation

- [ ] 7.1 `docs(admin)`: add `docs/gitbook/how-to/freeze-writes-for-maintenance.md`, list it in `docs/gitbook/SUMMARY.md`, and add
  the read-only variant to `docs/gitbook/how-to/back-up-and-restore.md` next to the stop-the-container steps. The page covers the
  mixed-cluster caveat of a forced replica and the fact that proxy cache fills continue.

## 8. Pull request

- [ ] 8.1 Open the pull request with the title `feat(admin): add cluster-wide read-only mode`. The description follows
  `.github/pull_request_template.md`, carries no session link, and states that the change is opt-in with no breaking change.

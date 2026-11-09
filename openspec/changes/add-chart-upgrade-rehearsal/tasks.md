# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `chart`. Tests are written
first and seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system
clock, no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages. The rehearsal
creates its own kind cluster and is the only job that uses the network. Local runs go through `HEAVY_SLOTS=1 heavy` with
Gradle `--no-daemon --max-workers=2` where Gradle is involved, and kind or Testcontainers runs one at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-chart-upgrade-rehearsal`: this change and nothing else. Verify:
  `openspec validate add-chart-upgrade-rehearsal --strict` passes and the dash grep from the proposal prints nothing.

## 2. Spike: does the upgrade keep the data without the overrides

- [ ] 2.1 `test(chart)`: write failing unit tests for the comparison helper as a pure function of the claim UID before and
  after, the artifact sha1 before and after, and the token status, for the cases equal, renamed claim, changed sha1 and
  revoked token. Verify: they fail on the missing helper module.
- [ ] 2.2 `test(chart)`: write the rehearsal scenario as a failing test without the name overrides: upstream 1.4.5 with
  persistence, artifact, token, image-only `helm upgrade`, assertions from 2.1, then rollback. Verify: it fails before 2.3
  for the reason of the open verification, or passes when the rename does not happen.
- [ ] 2.3 Run the spike once on a kind cluster and record in the pull request whether the claim UID changed. If it changed,
  stop before 3.1: open the change `fix-chart-upstream-names` (type `fix(chart)`), and merge that change before this one. If it did
  not change, continue with 3.1 and make 2.2 the regression test.

## 3. Rehearsal and test hook

- [ ] 3.1 `ci(chart)`: add the helper module that 2.1 describes, and the rehearsal that 2.2 describes, under
  `charts/ingot/tests/rehearsal/`. The upstream chart is fetched by version 1.4.5 and checked by its digest, and the
  upstream image is pinned by tag and digest. Verify: the unit tests of 2.1 pass without a cluster, and the rehearsal passes
  on kind.
- [ ] 3.2 `ci(chart)`: add the rehearsal to the `chart` job of `.github/workflows/ci.yml` after `ct install`, and set
  `upgrade: true` in `ct.yaml`. Verify: the workflow lints, and the job runs green on the pull request.
- [ ] 3.3 `test(chart)`: write failing tests for the render of the test hook, one for the default render (no test pod)
  and one for `tests.enabled=true` (one pod with `helm.sh/hook: test`), in `charts/ingot/tests/test_chart.py`. Verify: they
  fail before 3.4.
- [ ] 3.4 `ci(chart)`: add `charts/ingot/templates/tests/` with the test pod that calls the API with a token, and the value
  `tests.enabled` (default `false`) in `charts/ingot/values.yaml` and `charts/ingot/values.schema.json`. Verify: the tests of
  3.3 pass, `charts/ingot/tests/test_equivalence.py` stays green and `helm lint charts/ingot --strict` passes.
- [ ] 3.5 `docs(chart)`: document `tests.enabled` and `helm test` in `docs/gitbook/how-to/verify-an-upgrade.md`, and the result
  of the rehearsal in `docs/gitbook/how-to/migrate-from-upstream-chart.md`, including whether the name overrides are still
  required. Verify: the docs match the rendered chart.

## 4. Pull request

- [ ] 4.1 Open the code pull request with the title `ci(chart): rehearse the upgrade from the upstream chart`, with the
  result of 2.3 in its description. Verify: the title passes the commit style hook, and the CI `chart` job is green.

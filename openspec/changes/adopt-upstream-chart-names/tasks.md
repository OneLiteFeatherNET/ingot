# Tasks

Each group is one or more commits of one Conventional Commits type, all with the scope `chart`. The test commits and the docs
commit support the `fix(chart)` pull request, and the pull request title carries the type `fix`. Tests are written first and
seen to fail before the production code exists. Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no
fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages. The kind rehearsal creates
its own cluster and runs one job at a time through `HEAVY_SLOTS=1 heavy`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose adopt-upstream-chart-names`: this change and nothing else. Verify:
  `openspec validate adopt-upstream-chart-names --strict` passes and the dash grep from the proposal prints nothing.

## 2. Tests first

- [ ] 2.1 `test(chart)`: add `charts/ingot/tests/test_name_adoption.py` with the offline cases that pin the fallback: release
  `reposilite` without overrides renders `reposilite-ingot` and selector name `ingot`, release `mirror` renders `mirror-ingot`,
  the override pair renders `reposilite` with selector name `reposilite`, and NOTES.txt prints no adoption line. Verify: the
  tests pass on main before any production change, and they guard the fallback from here on.
- [ ] 2.2 `test(chart)`: add the kind rehearsal case in `charts/ingot/tests/rehearsal/` (from `add-chart-upgrade-rehearsal`):
  upstream 1.4.5 with persistence, an artifact and a token, then an image-only `helm upgrade` without any override. Assert the
  claim UID, the Deployment and Service name `reposilite`, the artifact sha1 and the token, then repeat them after
  `helm rollback`. This is `test_image_only_render_keeps_the_upstream_claim_name`, moved to kind because `lookup` returns
  nothing under `helm template`. Verify: the case fails on main, with the claim renamed to `reposilite-ingot`.
- [ ] 2.3 `test(chart)`: add the kind cases for open questions 3 and 4 of `design.md`: a claim owned by another release does not
  change the names, and a Deployment with a foreign selector fails the render with both selectors named. Verify: both cases fail
  on main.

## 3. Fix

- [ ] 3.1 `fix(chart)`: add the named template that performs the lookup and the owner check, and route the fullname helper and
  `ingot.selectorLabels` through it, with the explicit overrides skipping the lookup. Only the server selector changes. The
  dashboard keeps `ingot.dashboardSelectorLabels`. Verify: 2.1 passes, 2.2 and 2.3 pass on kind, `helm lint charts/ingot --strict`
  passes, and `charts/ingot/tests/test_equivalence.py` and `test_golden.py` stay green.
- [ ] 3.2 `fix(chart)`: print the adoption line in `charts/ingot/templates/NOTES.txt` when adoption is active. Verify: the chart
  test that renders NOTES with cluster access shows the line, and the fallback render shows none.

## 4. Docs

- [ ] 4.1 `docs(chart)`: update `docs/gitbook/how-to/migrate-from-upstream-chart.md`. The overrides are not needed for
  `helm install` and `helm upgrade` with cluster access. They stay required for `helm template` and for GitOps tools that render
  without cluster access, and the page names Argo CD as the example. Correct the data section, and state the `helm diff`
  expectation. Verify: the page matches the rendered output of 2.1 and 2.2, and the dash grep prints nothing.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `fix(chart): keep the upstream resource names when upgrading from the reposilite chart`,
  with the result of the kind rehearsal in its description. Verify: the title passes the commit style hook, the CI `chart` job
  is green, and pull request #101 is merged or rebased under it.

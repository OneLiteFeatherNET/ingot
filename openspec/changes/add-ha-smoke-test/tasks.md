# Tasks

Every commit of this change has the type `test` and the scope `chart`. Tests are written first and seen to fail before the
code they check exists. Every test follows F.I.R.S.T.: no sleeps, no system clock (an injected clock stands in for
`java.time.Clock`), no fixed ports, no shared mutable state, a temporary directory per test, and assertions with messages.
The kind run goes through `HEAVY_SLOTS=1 heavy`, one kind cluster at a time, and never shares a cluster with another job.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose add-ha-smoke-test`: this change only, branched from the remote main branch. Verify:
  `openspec validate add-ha-smoke-test --strict` passes and the dash grep from the proposal prints nothing.

## 2. Driver decision logic

- [ ] 2.1 `test(chart)`: write failing unit tests in `charts/ingot/tests/test_ha_smoke.py` for the pure helpers, with a fake
  clock: a wait returns a timeout failure naming its condition once the deadline passes; the failed-read count is zero only
  when every read is a 200 with the expected bytes; the metadata check fails when one of two versions is missing; the
  takeover window is kill time plus time-to-live plus poll interval; the missed-observation case fails with its own message;
  the status parser lists two instances from a two-instance response and fails on a missing cluster section. Verify: the
  module is missing, so every test fails before 2.2.
- [ ] 2.2 `test(chart)`: add the helpers in `charts/ingot/tests/ha_smoke.py`. Verify: the tests of 2.1 pass with
  `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v`, and no test opens a socket.

## 3. Dependencies in CI

- [ ] 3.1 `test(chart)`: write a failing unit test that parses `charts/ingot/tests/ha/dependencies.yaml` and asserts one
  Service each for MariaDB, MinIO and NATS, the bucket creation job, and that the host names and ports in
  `charts/ingot/ci/ha-values.yaml` match those Services. Verify: it fails before 3.2 because the manifest is missing.
- [ ] 3.2 `test(chart)`: add `charts/ingot/tests/ha/dependencies.yaml` and a step in the `chart` job of `.github/workflows/ci.yml`
  that applies it and waits for the three workloads before `ct install`. Verify: the test of 3.1 passes and the `chart` job
  still installs both deploy modes on kind.

## 4. The smoke run

- [ ] 4.1 `test(chart)`: extend `charts/ingot/tests/ha_smoke.py` with the driver: install `ci/ha-values.yaml` with two replicas,
  wait for both to be ready, then run the five checks (rolling restart, lease takeover, concurrent deploys, sessions, cluster
  view), each addressed as the proposal names it. Verify: on a kind cluster from `main` (before the Wave A to C changes) the
  driver fails at the first check that depends on a missing fix, which proves the test is red. The driver is then run with
  `HEAVY_SLOTS=1 heavy` on the branch with those changes merged, and every check passes.
- [ ] 4.2 `test(chart)`: add the `ha-smoke` job to `.github/workflows/ci.yml`: build the server and dashboard images with the
  `ci-server` and `ci-dashboard` cache scopes, create the kind cluster, load the images, run the dependency step of 3.2, then run
  the driver. Verify: the job is green on the pull request, and a deliberate break of the `ha-values.yaml` replica count makes
  it fail (checked locally with the driver, not committed).

## 5. Documentation

No task. This change changes no configuration key, environment variable, deployment manifest or data, so no page under
`docs/gitbook/` changes.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `test(chart): add a two-replica smoke test on kind`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells what a reviewer would see go wrong with two
  replicas (a read that fails during a rollout, a lease that stays held after its pod died, a version that disappears from the
  index) and names the dependency on the Wave A to C changes. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.

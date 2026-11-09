# Proposal

## Why

An operator who runs Reposilite on Kubernetes with the upstream chart expects one changed line to move the release to
Ingot: the image. The migration guide says otherwise. `docs/gitbook/how-to/migrate-from-upstream-chart.md:43` requires the
two name overrides `nameOverride` and `fullnameOverride`, because without them the Ingot chart names the release
`<release>-ingot`. The helper that builds the name (`charts/ingot/templates/_helpers.tpl:20`) is the same one the claim
template uses (`charts/ingot/templates/pvc.yaml:5`). An upgrade that skips the overrides therefore creates a new,
empty claim `reposilite-ingot` and a new Deployment, while the old claim `reposilite` stays behind under its
`helm.sh/resource-policy: keep`, no longer mounted. The pod starts on an empty `/app/data`, and the artifacts and tokens
look gone.

Nothing in CI would notice. The chart tests only render templates (`charts/ingot/tests/test_equivalence.py`), and
`ct.yaml` has no `upgrade` setting, so `ct install` never installs a previous release. No test installs the upstream chart
and upgrades it in place.

## What Changes

- **Upgrade rehearsal on kind.** A CI step installs the upstream `reposilite` chart 1.4.5 (appVersion 3.6.6) on a kind
  cluster with a claim, using the upstream image pinned by tag and digest. It writes an artifact, creates a token, and runs
  `helm upgrade` to the Ingot chart changing only `image.name` and `image.tag`, without the name overrides. It then asserts
  the claim UID, the artifact sha1 and the token. It then runs `helm rollback` and asserts the same three again.
- **Helper unit tests.** The comparison of claim UIDs, checksums and token status is a pure function. It is covered by unit
  tests that need no cluster, so the logic is checked on every chart change.
- **Chart test hook.** A `helm test` pod in `charts/ingot/templates/tests/`, rendered only when the new value
  `tests.enabled` is `true` (default `false`). It checks the server API with a token. It lets CI and operators run
  `helm test` after an install or upgrade.
- **Chart-testing upgrade mode.** `ct.yaml` gets `upgrade: true`, so `ct install` also upgrades the chart in each install
  test.

### Conditional fix

Whether the rename really happens is an open verification (roadmap section "Open verifications", item 3). The spike task
in this change runs the rehearsal first, without the overrides, and records the result in the pull request. The fix is
conditional on that result:

- If the claim keeps its UID and the data survives, this change adds no compatibility code.
- If the rename is confirmed, the fix is a separate change `fix-chart-upstream-names` (type `fix(chart)`). It adds a
  compatibility default that resolves the fullname to `<release>-reposilite` when the release was created by the upstream
  chart, detected through the existing claim or an explicit `compat.upstreamNames` key. It ships with a release note. This
  change then merges after that fix, and its rehearsal asserts the drop-in behaviour without the overrides.

### Out of scope

- Any change to the rendered output of the chart by default. The rehearsal observes the chart, it does not change it,
  unless the conditional fix above is needed, and that fix is a separate change.
- Rollback of Ingot to Reposilite 2.x or the database migration. Only the chart upgrade and the rollback to the upstream
  chart are covered.
- The HA values and the split deployment, which the chart changes of wave C and the `add-deploy-modes` change cover.
- Running the rehearsal against the real staging cluster. The rehearsal runs on kind only.
- The release pipeline and the image signing of the release workflow.

## Capabilities

### New Capabilities

- `chart-upgrade-rehearsal`: the upgrade and rollback of a release from the upstream chart to the Ingot chart, which keeps
  the data volume, the artifacts and the tokens, and the opt-in chart test hook.

## Impact

- **Type and scope.** The change ships under `ci(chart)`. The pull request title, squash-merged onto main, is:

  `ci(chart): rehearse the upgrade from the upstream chart`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change for an operator. The new value `tests.enabled` defaults to `false` and renders nothing. The title carries no `!`
  and no commit carries a `BREAKING CHANGE:` footer. If the conditional fix is needed, it is marked in its own proposal.
- **Behaviour that changes by default.** None. The rendered manifests of the chart do not change when `tests.enabled` is
  unset, which `charts/ingot/tests/test_equivalence.py` keeps checking.
- **Code.** `.github/workflows/ci.yml` (a step in the `chart` job, or a job of its own), `ct.yaml`, `charts/ingot/templates/tests/`,
  `charts/ingot/values.yaml` and `charts/ingot/values.schema.json` for `tests.enabled`, and the chart tests under
  `charts/ingot/tests/`.
- **Docs.** `docs/gitbook/how-to/verify-an-upgrade.md` for `tests.enabled` and `helm test`, and
  `docs/gitbook/how-to/migrate-from-upstream-chart.md` for the rehearsal result, which will show whether the overrides stay
  required.
- **Dependencies.** None on another open change. `fix-chart-upstream-names` is a conditional follow-up that this change
  depends on only when the spike confirms the rename. `add-chart-ha-values` (wave C) depends on this change, because it
  is verified by the same rehearsal.

## Verification

- The spike and the helper tests run first, then the rehearsal runs on kind through `HEAVY_SLOTS=1 heavy`, one kind job at
  a time.
- `helm lint charts/ingot --strict` and `python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v` pass.
- `openspec validate add-chart-upgrade-rehearsal --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-chart-upgrade-rehearsal` prints nothing.

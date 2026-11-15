# Proposal

## Why

An operator runs Reposilite on Kubernetes with the upstream chart 1.4.5. The migration guide promises one changed line:
the image. Today the guide also requires two name overrides, `nameOverride` and `fullnameOverride`
(`docs/gitbook/how-to/migrate-from-upstream-chart.md:43`). An operator who skips them gets a different installation
without any error:

- The Ingot chart names the release `<release>-ingot`, so release `reposilite` renders `reposilite-ingot`
  (`charts/ingot/templates/_helpers.tpl:12-24`). The upstream chart names the same release `reposilite`.
- The claim, the Deployment and the Service are created under the new name. The new claim is empty, and the Deployment
  starts on an empty `/app/data`. The artifacts and the tokens look gone.
- The old claim stays in the cluster under `helm.sh/resource-policy: keep`, no longer mounted. The old Service is removed
  with the old Deployment.
- The Ingot selector is `app.kubernetes.io/name: ingot`, the upstream selector is `app.kubernetes.io/name: reposilite`. A
  Deployment selector is immutable, so a fix that keeps the upstream name must also keep the upstream selector.

This breaks the drop-in contract of `CLAUDE.md`: moving from Reposilite to Ingot must cost one changed line. The data
section of the same guide says the names match "with the name overrides", which is true only when the operator sets them,
and nothing in the chart tests installs the upstream chart and upgrades it.

The chart can recognise its own predecessor. When the cluster already holds a claim or a Deployment with the upstream name,
and the Deployment carries the upstream selector, the release is the upstream release, and the chart can keep the upstream
names without an operator decision.

## What Changes

- **Name adoption by lookup.** The chart looks up, in the release namespace, the PersistentVolumeClaim and the Deployment
  named with the upstream fullname. The upstream fullname is the release name when it contains `reposilite`, and
  `<release>-reposilite` otherwise. When either object exists and is owned by this release, the chart renders the upstream
  fullname for every object that uses the fullname, and it renders the upstream selector labels
  (`app.kubernetes.io/name: reposilite`, `app.kubernetes.io/instance: <release>`) for the server workload.
- **Unchanged otherwise.** When no such object exists, the chart renders `<release>-ingot` as it does today. Fresh installs
  and existing Ingot releases such as `mirror` (rendered as `mirror-ingot`) see no change.
- **Explicit overrides keep working.** `nameOverride` and `fullnameOverride` still win over the lookup. The documented
  override path stays valid for every tool that renders the chart without cluster access.
- **One notice in NOTES.txt** when adoption is active, naming the upstream fullname that is kept. Nothing is rendered when
  adoption is off.
- **Upgrade rehearsal case.** The kind rehearsal of `add-chart-upgrade-rehearsal` gets an image-only upgrade from upstream
  1.4.5 without any name override. It asserts the claim UID, the Deployment and Service names, the artifact sha1 and the
  token, and it repeats the assertions after `helm rollback`.
- **Migration guide.** The page states that the overrides are not needed for `helm install` and `helm upgrade` with cluster
  access, and that they are required for `helm template` and for GitOps tools that render without cluster access. The data
  section is corrected.

### Out of scope

- Any change to the default names of fresh installs or of existing Ingot releases.
- A new configuration key that forces or disables adoption. The explicit overrides are the way out (see the open questions
  in `design.md`).
- Renaming the dashboard workload or any Ingot-only object. Split mode is new, so it has no upstream names to keep.
- Argo CD or Flux specific settings. The change documents the render limit and does not configure any GitOps tool.
- Image, entrypoint, data format and Reposilite 2.x migration.
- Running the rehearsal on the staging cluster. The rehearsal runs on kind only.

## Capabilities

### New Capabilities

- `chart-name-adoption`: an upgrade from the upstream chart keeps the upstream resource names and selector when the
  cluster holds the upstream release, and renders the Ingot names otherwise.

## Impact

- **Type and scope.** The change ships under `fix(chart)`. The pull request title, squash-merged onto main, is:

  `fix(chart): keep the upstream resource names when upgrading from the reposilite chart`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change for an operator who follows the migration guide. Operators who set the overrides see the same render as before.
- **Behaviour that changes by default.** Only for a release that the lookup recognises as the upstream release, which
  previously could not be upgraded without the overrides. Its pods get the upstream selector and the `reposilite` name
  labels, and the OpenTelemetry service name defaults to the upstream fullname. Nothing changes for any other release.
- **Code.** `charts/ingot/templates/_helpers.tpl` (fullname and selector helpers), `charts/ingot/templates/deployment.yaml`,
  `service.yaml`, `serviceaccount.yaml`, `pvc.yaml` and the other objects that use the fullname or the selector helper,
  `charts/ingot/templates/NOTES.txt`, and the chart tests under `charts/ingot/tests/`.
- **Docs.** `docs/gitbook/how-to/migrate-from-upstream-chart.md`. No new value key, so no `values.yaml` page changes.
- **Dependencies.** The kind case depends on `add-chart-upgrade-rehearsal` (pull request #101), which provides the rehearsal
  harness. The selector change lands in the shared helper, so `add-deploy-modes` and `add-chart-network-and-trust`
  inherit it. `add-deploy-modes` still documents the overrides as required in its migration tasks, and that text needs the
  same correction. The conditional follow-up `fix-chart-upstream-names` named in `add-chart-upgrade-rehearsal` is this
  change under a new name, with a lookup in place of the explicit key that proposal mentions. Whether that proposal is
  edited to point here is a maintainer decision.

## Verification

- Offline: `helm lint charts/ingot --strict`, the chart tests under `charts/ingot/tests/` (including `test_equivalence.py` and
  `test_golden.py`, which keep rendering the override path unchanged) and `openspec validate adopt-upstream-chart-names --strict`.
- Kind: the upgrade rehearsal from `add-chart-upgrade-rehearsal` runs with one kind job at a time, through
  `HEAVY_SLOTS=1 heavy`. The render test `test_image_only_render_keeps_the_upstream_claim_name` moves here, because `lookup`
  returns nothing under `helm template`.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/adopt-upstream-chart-names` prints nothing.

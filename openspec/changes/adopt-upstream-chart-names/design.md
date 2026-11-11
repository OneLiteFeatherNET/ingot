# Design

## Context

The Ingot chart derives every object name from `ingot.fullname` (`charts/ingot/templates/_helpers.tpl:12-24`). The helper
returns `fullnameOverride` when it is set. Otherwise it returns the release name when the release name contains the chart
name `ingot`, and `<release>-ingot` in every other case. The upstream chart 1.4.5 uses the same shape with the chart name
`reposilite`, so for a release `reposilite` it returns `reposilite`. The Ingot helper only matches the upstream result when
the operator sets `nameOverride: reposilite` and `fullnameOverride: reposilite`.

The objects that carry the fullname are the claim (`pvc.yaml:5`), the Deployment (`deployment.yaml:4`), the Service
(`service.yaml:4`), the ServiceAccount (`serviceaccount.yaml:5`), and the HPA, PodDisruptionBudget, NetworkPolicy,
ServiceMonitor, PrometheusRule, Ingress and HTTPRoute. The upstream chart 1.4.5 renders the Deployment, the Service, the
claim, the ServiceAccount, the HPA and the Ingress under its fullname, so an image-only switch changes all of them. Helm
deletes what the new render no longer contains, unless the object has `helm.sh/resource-policy: keep`. The claim has it, so
the data survives under the old name, while the Deployment and the Service are replaced.

The selector is `ingot.selectorLabels` (`_helpers.tpl:43-46`), which uses `ingot.name`. `ingot.name` is the chart name
unless `nameOverride` is set. The Deployment selector and the Service selector both depend on it, and the Deployment
selector is immutable.

Existing Ingot releases name their objects `<release>-ingot`. Helm releases carry `meta.helm.sh/release-name` on every object
they install, so the owning release of any object can be read back from the cluster.

## Decisions

### The chart looks up the upstream claim and the upstream Deployment

The lookup runs in the release namespace. The name is the upstream fullname: the release name when it contains `reposilite`,
and `<release>-reposilite` otherwise. Adoption happens when either object exists and is owned by this release
(`meta.helm.sh/release-name` equal to `.Release.Name`, and `meta.helm.sh/release-namespace` equal to the release namespace).

The claim is the object that holds the data, so it is the primary signal. The Deployment is the signal for a release that
runs without a claim, because an upstream release with `persistence.enabled: false` still has a Deployment with the upstream
name. Together they cover every upstream release that the chart can upgrade.

Adoption is skipped when `nameOverride` or `fullnameOverride` is set, because the operator has already decided the names.

### Adopted names keep the upstream selector for the server

When adoption is active, `ingot.selectorLabels` renders `app.kubernetes.io/name: reposilite` and
`app.kubernetes.io/instance: <release>`. The Deployment, the Service, the PodDisruptionBudget, the NetworkPolicy and the
ServiceMonitor use that helper, so they agree. The dashboard workload keeps `ingot.dashboardSelectorLabels`, because split
mode is new and has no upstream selector to keep.

The helper reads the decision once, so every object sees the same result in one render. The decision is a named template
that returns the upstream fullname or the empty string, and both the name helpers and the selector helpers call it.

### The fallback is the current render

When the lookup returns nothing, which is the case for `helm template`, for `helm install --dry-run=client` and for GitOps
tools that render without cluster access, the chart renders `<release>-ingot` and the selector `ingot`. This is the render
the chart produces today, so existing installs and the offline tests see no change. The documented answer for those tools is
the explicit override pair.

### NOTES.txt names the adopted fullname

When adoption is active, NOTES.txt prints one line with the adopted fullname and the instruction that the release keeps
the upstream names. NOTES.txt is not rendered by `helm template`, so the notice only appears where the decision was taken
with cluster access, which is where it is useful.

### The OpenTelemetry service name follows the fullname

`OTEL_SERVICE_NAME` defaults to the fullname (`_helpers.tpl`, the `opentelemetry` env block). An adopted release therefore
reports `reposilite`, the name it reported as upstream, and a fresh install reports `<release>-ingot` as before. The value is
still set by `opentelemetry.serviceName`.

## Open questions

Each question has a default. The maintainer can change a default before the code pull request is opened.

1. **Which objects are looked up?** Default: the claim and the Deployment. The Service and the ServiceAccount are not looked
   up. They follow the decision, which is safe because the Deployment lookup covers every upstream release that has one.
2. **Only the claim exists, no Deployment.** Default: adopt. The claim is what holds the data, and no Deployment with the
   upstream name exists to conflict with the upstream selector. The Deployment is then rendered with the upstream selector.
3. **A Deployment with the upstream name exists, but its selector is not the upstream selector.** Default: fail the render,
   and name both the object and the two selectors. Rendering `<release>-ingot` beside it would create a second workload
   against the same claim, and rendering the upstream name would fail on the immutable selector.
4. **A claim or Deployment with the upstream name exists, but another release owns it.** Default: do not adopt. The owner
   check compares `meta.helm.sh/release-name` and `meta.helm.sh/release-namespace`, so a foreign object never changes the
   names of this release.
5. **Reinstall after an uninstall that kept the claim.** The claim still carries this release name, so the owner check
   passes. Whether `helm install` then fails on the existing claim, or adopts it, is decided by the rehearsal. Default: the
   rehearsal records the result, and the migration guide documents it. No code path is added for it.
6. **A NOTES.txt warning instead of a notice.** Default: a notice only. The adoption is the intended behaviour, and the notice
   tells the operator which names are kept. A warning would appear on every upgrade of an upstream release, which is noise.
7. **An explicit opt-out key, for example `compat.upstreamNames: false`.** Default: no key. The explicit override pair is the
   documented way to choose the Ingot names, and a new key would add a value that the operator must learn for a case that
   the overrides already cover.
8. **GitOps tools that render with `helm template`.** Argo CD renders charts this way unless it is configured to run Helm.
   Such a tool sees the fallback names, so an upgrade would replace the Deployment and create a new claim, and the old claim
   would stay behind. Default: the migration guide names the override pair as required for those tools, and the change adds
   no failure. Argo CD users need to set the overrides. This is the largest residual risk, and it is documented rather than
   detected.

## Risks

- The lookup changes the render of a live release, so the rendered manifests depend on the cluster. A `helm diff` run
  before and after the chart upgrade shows the change, and the migration guide asks for that diff.
- Adoption keeps a name that the operator might not expect. The NOTES.txt line and the migration guide state it.
- The selector helper is shared with `add-deploy-modes` and `add-chart-network-and-trust`. Their tasks that assert the
  selector must read the adopted value, or they fail when adoption is active.

## Verification

- Offline tests render the fallback and the override path, so they pass without a cluster and guard the default render.
- The kind rehearsal covers the adopted path: an image-only upgrade from upstream 1.4.5 without overrides, the owner check
  with a foreign claim, and the selector failure of open question 3. The rehearsal creates its own cluster per run.

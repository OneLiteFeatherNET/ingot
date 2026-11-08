---
description: Move a Reposilite release from the upstream Helm chart 1.4.5 to the Ingot Helm chart with a values diff, keeping the names, the selector and the data volume, with a table that maps every key of the staging values and the rollback to the upstream chart.
---

# Migrate from the upstream Helm chart

A release installed with the upstream `reposilite` chart can move to the Ingot chart without new resources. The migration is a values diff: the Ingot chart keeps the value keys and defaults of the upstream chart 1.4.5, and two name overrides make the rendered names and the selector equal to those of the upstream release. The Deployment selector cannot change in place, so those overrides are the part that must not be skipped.

The example throughout is the staging release of Ingot: a Flux `HelmRelease` named `reposilite` in the namespace `reposilite-staging`, which uses the upstream chart and a post renderer that sets the image digest.

## Before you begin

* The release name and namespace of the existing release, from `helm list -n <namespace>`.
* A copy of the data, if the release has a volume. See [Back up before upgrading](back-up-before-upgrading.md).
* The `helm diff` plugin, which shows the change before it is applied:

  ```bash
  helm plugin install https://github.com/databus23/helm-diff
  ```

## 1. Save the current values

Save the values that the release uses now:

```bash
helm get values reposilite -n reposilite-staging -o yaml > reposilite-values.yaml
```

For a Flux release, the values are the `spec.values` of the `HelmRelease`. The post renderer stays as it is, because the Deployment name and the image line that it patches do not change.

## 2. Write the migrated values

Start from the saved values and add the two name overrides and the image of the chart:

```yaml
nameOverride: reposilite
fullnameOverride: reposilite
image:
  name: ghcr.io/onelitefeathernet/ingot
  tag: "nightly"
```

Keep the `image.tag` the release already uses. Staging follows the `nightly` tag, and its post renderer pins the digest. If `image.tag` is not set, the chart uses its own version. The name overrides are required. Without them the chart is named `ingot`, and a release called `reposilite` would be rendered as `reposilite-ingot`, with a different selector.

Save the result as `ingot-values.yaml`.

## 3. Compare the values keys

Every key of the staging values has the same name and the same meaning in the Ingot chart:

| Key | In the Ingot chart |
|-----|--------------------|
| `image.name` | Same key. The default is the Ingot image. |
| `image.tag` | Same key. |
| `deployment.replicas` | Same key. |
| `deployment.podSecurityContext.enabled` | Same key, `false` by default. |
| `deployment.podSecurityContext.runAsNonRoot` | Same key. |
| `deployment.podSecurityContext.runAsUser` | Same key. |
| `deployment.podSecurityContext.runAsGroup` | Same key. |
| `deployment.podSecurityContext.fsGroup` | Same key. |
| `deployment.podSecurityContext.seccompProfile.type` | Same key. |
| `deployment.containerSecurityContext.enabled` | Same key, `false` by default. |
| `deployment.containerSecurityContext.runAsUser` | Same key. |
| `deployment.containerSecurityContext.runAsGroup` | Same key. |
| `deployment.containerSecurityContext.allowPrivilegeEscalation` | Same key. |
| `deployment.containerSecurityContext.readOnlyRootFilesystem` | Same key. |
| `deployment.containerSecurityContext.capabilities.drop` | Same key. |
| `deployment.podLabels.logs.onelitefeather.net/env` | Same key. |
| `deployment.podLabels.logs.onelitefeather.net/format` | Same key. |
| `deployment.initContainers` | Same key. |
| `deployment.additionalVolumes` | Same key. |
| `deployment.additionalVolumeMounts` | Same key. |
| `probes.port` | Same key. |
| `probes.scheme` | Same key. |
| `probes.readiness.initialDelaySeconds` | Same key. |
| `probes.readiness.periodSeconds` | Same key. |
| `probes.readiness.timeoutSeconds` | Same key. |
| `probes.readiness.successThreshold` | Same key. |
| `probes.readiness.failureThreshold` | Same key. |
| `probes.liveness.initialDelaySeconds` | Same key. |
| `probes.liveness.periodSeconds` | Same key. |
| `probes.liveness.timeoutSeconds` | Same key. |
| `probes.liveness.successThreshold` | Same key. |
| `probes.liveness.failureThreshold` | Same key. |
| `persistence.enabled` | Same key. |
| `rollingUpdate.maxUnavailable` | Same key. |
| `rollingUpdate.maxSurge` | Same key. |
| `resources.requests.cpu` | Same key. |
| `resources.requests.memory` | Same key. |
| `resources.limits.cpu` | Same key. |
| `resources.limits.memory` | Same key. |
| `affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution` | Same key. |
| `affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution` | Same key. |
| `autoscaling.enabled` | Same key. |
| `autoscaling.minReplicas` | Same key. |
| `autoscaling.maxReplicas` | Same key. |
| `autoscaling.metrics` | Same key. |
| `autoscaling.behavior.scaleDown.stabilizationWindowSeconds` | Same key. |
| `autoscaling.behavior.scaleDown.policies` | Same key. |
| `env` | Same list. `INGOT_LOCAL_DEFAULTFRONTEND=false` keeps working. `INGOT_TARGET=server` is the newer form of the same setting. |
| `ingress.enabled` | Same key. |

The keys added by the Ingot chart are `mode`, which defaults to `allInOne` and stays that way for a migration, the `dashboard` block, which is rendered only in split mode, `serviceMonitor`, `podDisruptionBudget` and `httpRoute`. All of them default to the behaviour of the upstream chart.

## 4. Diff before the change

Run the diff against the release, and read it before you apply it:

```bash
helm diff upgrade reposilite oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace reposilite-staging \
  -f ingot-values.yaml
```

The expected changes are the chart label and the version label on the pods, which restart the pods once, and the image of the release. The names of the Deployment, the Service and the claim must not change, and neither may the selector of the Deployment. If the diff shows a changed selector, stop and check the name overrides.

## 5. Apply the change

```bash
helm upgrade reposilite oci://ghcr.io/onelitefeathernet/charts/ingot \
  --version 1.1.1 \
  --namespace reposilite-staging \
  -f ingot-values.yaml

kubectl -n reposilite-staging rollout status deployment/reposilite
```

For a release that Flux manages, change `spec.chart` of the `HelmRelease` from the upstream chart to the Ingot chart in the OCI registry, with the same version and the values of step 2. The post renderer keeps matching the Deployment by its name, and it keeps patching the image by its digest, because the image name is the same.

## The data volume

Staging keeps `persistence.enabled: false` and an `emptyDir` at `/app/data`, so nothing has to move.

A release with a claim from the upstream chart keeps that claim. The Ingot chart names its claim after the release, as the upstream chart does, so with the name overrides it is the same `reposilite` claim, mounted at the same path. No new claim is created. Both charts set `helm.sh/resource-policy: keep` on the claim, so an uninstall does not delete the data either.

## Roll back to the upstream chart

If the migrated release does not behave, install the upstream chart again with the values you saved in step 1:

```bash
helm repo add reposilite https://helm.reposilite.com/
helm upgrade reposilite reposilite/reposilite \
  --version 1.4.5 \
  --namespace reposilite-staging \
  -f reposilite-values.yaml
```

The upstream chart renders the same names, so the data volume and the Service stay as they are. Helm can also return to the revision before the migration with `helm rollback reposilite <revision>`. For a Flux release, change `spec.chart` back first, so that Flux does not apply the Ingot chart again.

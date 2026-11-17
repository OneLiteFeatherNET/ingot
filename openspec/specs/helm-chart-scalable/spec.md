# helm-chart-scalable Specification

## Purpose
A third chart mode, `scalable`, next to `allInOne` and `split`: read, write and worker workloads from one image, and routing by
HTTP method. It extends the requirement on the `mode` value of the `helm-chart` capability of the change `add-deploy-modes`.

## Requirements

### Requirement: Mode scalable and unchanged defaults

The chart SHALL accept `mode: scalable` in addition to `allInOne` and `split`. The default mode SHALL stay `allInOne`, and the
rendered manifests of the default values and of the `split` mode SHALL NOT change.

#### Scenario: Default render is unchanged

- **WHEN** the chart is rendered with its default values
- **THEN** the output equals the golden fixture of the previous release

### Requirement: Scalable workloads

In `scalable` mode the chart SHALL render a read Deployment and a write Deployment with `INGOT_TARGET=server` and
`INGOT_BACKGROUND_JOBS=false`, a worker Deployment with `INGOT_TARGET=worker` and one replica, and the dashboard workload of
`split` mode. It SHALL render the Services `<name>-read` and `<name>-write`, and a Service `<name>` that selects the read and
write pods together. The worker SHALL have no HPA and no routing rule.

#### Scenario: Pods are separated

- **WHEN** the chart is rendered with `mode: scalable`
- **THEN** the pod selectors of the read, write, worker and dashboard Deployments are pairwise disjoint, and `<name>` selects exactly the read and write pods

### Requirement: Method routing with Gateway API

With `httpRoute.enabled` in `scalable` mode the chart SHALL render the dashboard rules of `split`, a rule that matches
`PathPrefix /` with `method: GET` and a rule with `method: HEAD` to `<name>-read`, and a last rule that matches `PathPrefix /`
without a method to `<name>-write`.

#### Scenario: Writes reach the write pods

- **WHEN** the rendered HTTPRoute is evaluated for `PUT`, `POST`, `DELETE` and `PATCH` on `/releases/a/b/1.0/b-1.0.jar`
- **THEN** the only rule that matches is the one without a method, which names `<name>-write`

### Requirement: Ingress limitation is stated

With `ingress.enabled` in `scalable` mode the chart SHALL send every non-dashboard path to the Service `<name>` and SHALL print
in the install notes that a plain Ingress cannot route by HTTP method and that Gateway API is needed for it. The chart SHALL
NOT render controller specific annotations for method routing.

#### Scenario: Notes name the limitation

- **WHEN** the chart is installed with `mode: scalable` and `ingress.enabled: true`
- **THEN** the notes contain a line that says read and write traffic share the pods and that `httpRoute` routes by method

### Requirement: Render refusals

The chart SHALL fail to render `scalable` mode, with one line that names the value to set, when `database.existingSecret`,
`objectStorage.existingSecret` or `clusterBus.url` is empty, when `persistence.enabled` is true, or when `ingress.enabled`
and `httpRoute.enabled` are both true.

#### Scenario: Missing cluster bus

- **WHEN** the chart is rendered with `mode: scalable` and `clusterBus.url` empty
- **THEN** rendering fails with `scalable mode needs clusterBus.url: a write or a cleanup on one pod has to reach the caches of the others`

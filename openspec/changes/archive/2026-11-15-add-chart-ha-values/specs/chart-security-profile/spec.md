# Spec Delta

## Purpose

A cluster that enforces the Pod Security Standards `restricted` level admits the Ingot server when the operator selects the
restricted profile. The default render stays as it is, so the default pod keeps `PUID`, `PGID` and the ownership fix.

## ADDED Requirements

### Requirement: Default profile is unchanged

When `deployment.securityProfile` is empty or unset, the server SHALL keep the security defaults of the `helm-chart`
capability: no pod security context, no security context on the container, and no `runAsUser`.

#### Scenario: No profile renders no security context

- **WHEN** the chart is rendered with no security values
- **THEN** the server pod and container render no security context

### Requirement: Restricted profile

When `deployment.securityProfile` is `restricted`, the server pod SHALL set `runAsNonRoot: true`, `runAsUser: 977`,
`runAsGroup: 977`, `fsGroup: 977` and `seccompProfile.type: RuntimeDefault`. The server container SHALL set
`allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `runAsNonRoot: true`, `runAsUser: 977`,
`runAsGroup: 977`, `capabilities.drop: [ALL]` and `seccompProfile.type: RuntimeDefault`.

#### Scenario: The restricted pod is rendered

- **WHEN** the chart is rendered with `deployment.securityProfile: restricted`
- **THEN** the pod and container security contexts carry every field listed above

### Requirement: Writable paths under the restricted profile

Under `restricted`, the chart SHALL mount an `emptyDir` volume named `ingot-tmp` at `/tmp` and an `emptyDir` volume named
`ingot-logs` at `/var/log/reposilite`. The data volume SHALL remain as it is.

#### Scenario: The server writes only to mounted paths

- **WHEN** the restricted render is started with a read-only root filesystem
- **THEN** `/tmp`, `/var/log/reposilite` and `/app/data` are the only writable paths, and each one is a volume

### Requirement: Restricted OpenShift variant

When `deployment.securityProfile` is `restricted-openshift`, the chart SHALL set the same fields as `restricted` except
`runAsUser`, `runAsGroup` and `fsGroup`, which it SHALL NOT set.

#### Scenario: No fixed id is set

- **WHEN** the chart is rendered with `deployment.securityProfile: restricted-openshift`
- **THEN** no `runAsUser`, `runAsGroup` or `fsGroup` is rendered, and `runAsNonRoot: true` is rendered

### Requirement: Conflicting security settings fail

The chart MUST fail rendering when a profile is set and `deployment.podSecurityContext.enabled` or
`deployment.containerSecurityContext.enabled` is true. The message MUST name both keys. The schema MUST reject any profile
other than `""`, `restricted` and `restricted-openshift`.

#### Scenario: A profile and a security block together fail

- **WHEN** the chart is rendered with `deployment.securityProfile: restricted` and `deployment.podSecurityContext.enabled: true`
- **THEN** rendering fails with a message that names `deployment.securityProfile` and `deployment.podSecurityContext.enabled`

#### Scenario: An unknown profile is rejected

- **WHEN** the chart is installed with `deployment.securityProfile: privileged`
- **THEN** schema validation fails before anything is rendered

### Requirement: Restricted namespace admits the pod in CI

CI SHALL install the restricted render into a namespace labelled `pod-security.kubernetes.io/enforce=restricted`, and the
server pod SHALL become ready there. CI SHALL run the image with `--read-only --user 977:977 --cap-drop ALL`, with `/tmp` and
`/var/log/reposilite` as writable mounts, and the health endpoint SHALL answer `200`.

#### Scenario: The pod is admitted and ready

- **WHEN** the restricted render is installed into the labelled namespace
- **THEN** the Kubernetes API accepts the pod and the Deployment reports the pod ready

#### Scenario: The read-only container starts

- **WHEN** the image runs with a read-only root filesystem, uid 977, all capabilities dropped and the two writable mounts
- **THEN** `GET /api/status/health` answers `200`

# Spec Delta

## Purpose

An operator of several replicas on Kubernetes sets the database, the object storage credentials, the cluster bus and the
spread of pods in typed values. A release that sets none of them renders exactly what it rendered before.

## ADDED Requirements

### Requirement: Unset values render nothing

The chart SHALL render the same manifests as before this change whenever none of the values of this capability is set.

#### Scenario: Defaults are unchanged

- **WHEN** the chart is rendered in `allInOne` mode, in `split` mode and with the staging fixture, with no value of this
  capability set
- **THEN** the parsed manifests equal the stored golden renders of those three inputs

### Requirement: Database from an existing Secret

When `database.existingSecret` is set, the server container SHALL read `INGOT_LOCAL_DATABASE` from the key
`database.secretKey` (default `database`) of that Secret. The chart SHALL NOT render the database value.

#### Scenario: The database comes from the Secret

- **WHEN** the chart is rendered with `database.existingSecret: ingot-database`
- **THEN** the server container has the variable `INGOT_LOCAL_DATABASE` with a `secretKeyRef` to `ingot-database` and key `database`, and no literal value

#### Scenario: A variable set twice fails

- **WHEN** the chart is rendered with `database.existingSecret` set and `INGOT_LOCAL_DATABASE` also in `env`
- **THEN** rendering fails with a message that names `database.existingSecret` and `INGOT_LOCAL_DATABASE`

### Requirement: Object storage credentials from an existing Secret

When `objectStorage.existingSecret` is set, the server container SHALL read `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
from the keys of the same names, and SHALL read `AWS_REGION` from the same Secret as an optional key.

#### Scenario: Credentials are referenced

- **WHEN** the chart is rendered with `objectStorage.existingSecret: s3-keys`
- **THEN** the server container has `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` as `secretKeyRef` entries to `s3-keys`, and `AWS_REGION` with `optional: true`

### Requirement: Cluster bus settings

The chart SHALL render `INGOT_CLUSTER_URL` from `clusterBus.url`. When `clusterBus.passwordSecret` is set, it SHALL render
`INGOT_CLUSTER_PASSWORD` from the key `password` of that Secret. When `clusterBus.credentialsSecret` is set, it SHALL mount
that Secret read-only and SHALL set `INGOT_CLUSTER_CREDENTIALS` to the path of the key `nats.creds` in the mount.

#### Scenario: NATS credentials are mounted

- **WHEN** the chart is rendered with `clusterBus.url: nats://nats:4222` and `clusterBus.credentialsSecret: nats-creds`
- **THEN** the server container sets `INGOT_CLUSTER_URL`, mounts `nats-creds` read-only, and sets `INGOT_CLUSTER_CREDENTIALS` to the mounted `nats.creds` file

#### Scenario: Bus unset adds nothing

- **WHEN** the chart is rendered with no `clusterBus` value
- **THEN** no bus variable and no bus volume is rendered

### Requirement: Topology spread constraints

The chart SHALL render the values `deployment.topologySpreadConstraints` on the server Deployment and
`dashboard.topologySpreadConstraints` on the dashboard Deployment, unchanged. An empty list SHALL render no field.

#### Scenario: Constraints are passed through

- **WHEN** the chart is rendered with one constraint on `topology.kubernetes.io/zone` for the server
- **THEN** the server pod template carries that constraint and the dashboard pod template carries none

### Requirement: Image digest

When `image.digest` is set, the server container image SHALL be `name@digest`, and the tag SHALL NOT be rendered. When it is
unset, the image SHALL be `name:tag` as before.

#### Scenario: Digest replaces the tag

- **WHEN** the chart is rendered with `image.digest: sha256:abc` and `image.tag: 1.2.0`
- **THEN** the server container image is `ghcr.io/onelitefeathernet/ingot@sha256:abc`

### Requirement: Schema types the new values

`values.schema.json` MUST type every value of this capability. It MUST reject a value of the wrong type. It MUST keep the
root and `deployment` open to keys it does not know.

#### Scenario: A wrong type is rejected

- **WHEN** the chart is installed with `image.digest: 12`
- **THEN** schema validation fails before anything is rendered

#### Scenario: An unknown key is still accepted

- **WHEN** the chart is rendered with an unknown key `deployment.somethingNew`
- **THEN** rendering succeeds

### Requirement: Secret values are never rendered

The chart MUST NOT render the value of any Secret it references. The rendered manifests MUST contain only the names and keys
of Secrets.

#### Scenario: Only references are rendered

- **WHEN** the chart is rendered with all the Secret references of this capability
- **THEN** the output contains no literal password, access key or URL with credentials

### Requirement: HA fixture renders and lints

The fixture `charts/ingot/tests/fixtures/ha-values.yaml` SHALL set two server replicas, the three Secret references, the bus
URL, a topology spread constraint and `image.digest`. It SHALL render and pass `helm lint --strict`. It SHALL NOT be placed
in `charts/ingot/ci/`.

#### Scenario: The fixture renders two spread replicas

- **WHEN** the fixture is rendered
- **THEN** the server Deployment has `replicas: 2` and the topology spread constraint, and helm lint reports no error

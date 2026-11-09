# Spec Delta

## Purpose

Operator guides for running Ingot on Kubernetes. The guides describe the behaviour that the server and the chart have
today, state the limits that remain, and point to the change that closes each limit.

## ADDED Requirements

### Requirement: Keyless S3 credentials are documented

The S3 guide MUST state that Ingot passes credentials to the S3 client only when both `accessKey` and `secretKey` are set,
and that otherwise the AWS SDK default credential chain applies. The guide MUST show the chart value that annotates the service
account for IRSA and MUST state that Pod Identity needs no annotation.

#### Scenario: Operator uses IRSA without static keys

- **WHEN** an operator reads the S3 guide to run Ingot with an IAM role for the service account
- **THEN** the guide shows the `serviceAccount.annotations` value with the role ARN and states that `accessKey` and `secretKey` stay empty

#### Scenario: Only one key is set

- **WHEN** an operator sets `accessKey` but leaves `secretKey` empty
- **THEN** the guide states that Ingot passes no static credentials and the SDK default chain applies

### Requirement: Gateway TLS is documented without in-app TLS

The TLS guide MUST describe terminating TLS at a Gateway or an ingress with cert-manager. The guide MUST NOT require the in-app
TLS connector. The guide MUST state that the in-app TLS key path accepts `.pem` and `.jks` files only, until the change
`fix-tls-key-file-names` merges.

#### Scenario: Gateway listener serves the certificate

- **WHEN** an operator follows the TLS guide with a Gateway API implementation
- **THEN** the guide creates a cert-manager Certificate whose Secret is referenced by the Gateway listener, and attaches the chart's
  HTTPRoute with `httpRoute.parentRefs`

#### Scenario: cert-manager file names inside Ingot

- **WHEN** an operator reads the in-app TLS section and the key path names `tls.crt` and `tls.key`
- **THEN** the guide states that Ingot does not accept those names yet and that the Gateway or ingress is the supported way to serve TLS

### Requirement: Edge limits for large uploads are documented

The edge guide MUST name the request body size and the read and send timeouts that the edge has to allow for artifact uploads.
The guide MUST state that the chart renders no timeout field on the HTTPRoute and MUST name where the implementation-specific
settings go.

#### Scenario: Upload fails with 413 at the ingress

- **WHEN** an upload larger than the ingress default fails with status `413`
- **THEN** the guide names the nginx ingress annotation `nginx.ingress.kubernetes.io/proxy-body-size` and states that the value must be at least the largest artifact

#### Scenario: Upload stops after the read timeout

- **WHEN** an upload stops with a gateway timeout while the transfer is still running
- **THEN** the guide names the timeout settings of the ingress controller or Gateway implementation that control it

### Requirement: Secrets never sit in the values file

The GitOps guide MUST show that Ingot reads secret values from the environment only through `env` or `envFrom`, and that the Secret
holding them is created outside the values file. The guide MUST cover SOPS, External Secrets Operator and Sealed Secrets. The guide
MUST list the values that Ingot reads from the environment today and state that repository settings such as S3 keys are set
through the API or the dashboard.

#### Scenario: External Secrets Operator provides the database password

- **WHEN** an operator reads the GitOps guide with External Secrets Operator
- **THEN** the guide shows an ExternalSecret that creates the Secret, and the chart value `envFrom` that reads it

#### Scenario: Repository setting with a secret

- **WHEN** an operator asks where the S3 secret key belongs
- **THEN** the guide states that it is stored in the repository settings and must not be placed in the values file

### Requirement: Sizing numbers come from a recorded measurement

The sizing guide MUST describe the method for choosing heap size and memory limits. The guide MUST contain resource numbers only
when they cite a recorded measurement, with the date, the workload and the measurement source. Without such a measurement, the
guide MUST show the chart defaults and state that they are not measured.

#### Scenario: No staging measurement is recorded

- **WHEN** the sizing guide is published before a staging measurement is recorded
- **THEN** it contains the method and the chart defaults and no recommended heap or memory value

#### Scenario: Measurement is recorded

- **WHEN** a staging measurement is recorded for the sizing guide
- **THEN** the guide cites the date, the workload and the source of the numbers next to each value

### Requirement: Deployment shapes are compared without throughput claims

The comparison page MUST describe the JAR, the container image, the Helm all-in-one chart and the Helm split chart. For each shape
the page MUST state how it is installed, upgraded and rolled back, and MUST link to the how-to for that shape. The page MUST NOT state
throughput or latency figures.

#### Scenario: Reader compares upgrade paths

- **WHEN** a reader compares the shapes for upgrades
- **THEN** each shape lists its upgrade command or step and its rollback step, and links to the matching how-to

### Requirement: New guides are listed and checked

Every new guide MUST be listed in `docs/gitbook/SUMMARY.md`. The consistency test in `charts/ingot/tests` MUST fail when a listed guide
is missing, and when a guide cites a chart value key that `charts/ingot/values.yaml` does not define.

#### Scenario: Guide missing from the summary

- **WHEN** a new guide is not listed in `docs/gitbook/SUMMARY.md`
- **THEN** the test fails and its message names the guide file

#### Scenario: Guide cites an unknown chart value

- **WHEN** a guide sets a key in a YAML values block, such as `httpRoute.timeouts`, that the chart does not define
- **THEN** the test fails and its message names the key and the guide file

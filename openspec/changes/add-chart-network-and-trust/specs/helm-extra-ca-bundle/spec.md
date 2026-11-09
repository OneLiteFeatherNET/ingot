# Spec Delta

## Purpose

An operator with an internal certificate authority can hand its PEM files to the server through the chart, without replacing
the trust store that the public certificates depend on.

## ADDED Requirements

### Requirement: Extra CA bundle is off by default

The chart SHALL add no volume, mount and environment variable for an extra CA bundle unless `extraCaCerts.enabled` is `true`.
With the value unset, the rendered manifests SHALL be the same as before this change.

#### Scenario: Default render

- **WHEN** the chart is rendered with the default values
- **THEN** the server container has no `INGOT_EXTRA_CA_CERTS` variable and the pod has no volume named for the extra CA bundle

### Requirement: Exactly one source for the bundle

When enabled, the chart MUST take the PEM files from exactly one of `extraCaCerts.configMapName` or `extraCaCerts.secretName`.
The render MUST fail with a message that names `extraCaCerts` when both or neither are set.

#### Scenario: Two sources

- **WHEN** `extraCaCerts.enabled=true` with both `configMapName` and `secretName` set
- **THEN** the render fails and the message names `extraCaCerts`

#### Scenario: No source

- **WHEN** `extraCaCerts.enabled=true` with neither source set
- **THEN** the render fails and the message names `extraCaCerts`

### Requirement: Read-only mount and a writable trust path

When enabled, the server container SHALL mount the ConfigMap or Secret read-only at `/etc/ingot/extra-ca`, SHALL set
`INGOT_EXTRA_CA_CERTS` to `/etc/ingot/extra-ca`, and SHALL mount a writable `emptyDir` at `/tmp`, so that the image can write a
merged trust store with a read-only root filesystem.

#### Scenario: ConfigMap source

- **WHEN** `extraCaCerts.enabled=true` and `configMapName` is `internal-ca`
- **THEN** the pod has a volume backed by the ConfigMap `internal-ca`, mounted read-only at `/etc/ingot/extra-ca`, and the server container sets `INGOT_EXTRA_CA_CERTS=/etc/ingot/extra-ca`

#### Scenario: Secret source

- **WHEN** `extraCaCerts.enabled=true` and `secretName` is `internal-ca`
- **THEN** the pod has a volume backed by the Secret `internal-ca`, mounted read-only at `/etc/ingot/extra-ca`

#### Scenario: Writable trust path

- **WHEN** `extraCaCerts.enabled=true`
- **THEN** the server container mounts an `emptyDir` volume at `/tmp`

### Requirement: Only the server reads the bundle

The dashboard workload SHALL NOT receive the extra CA mount, the environment variable or the `/tmp` emptyDir.

#### Scenario: Split mode

- **WHEN** `mode=split` and `extraCaCerts.enabled=true`
- **THEN** the dashboard Deployment has no `INGOT_EXTRA_CA_CERTS` variable and no extra CA volume

### Requirement: The public trust store stays in use

The chart MUST NOT set a Java trust store flag or any other setting that replaces the public CAs. The bundle SHALL only add to
the trust store that the image builds at start.

#### Scenario: Public certificate after the change

- **WHEN** the server starts with a bundle that holds only a private CA
- **THEN** the server still trusts the public certificates it trusted before, which the image's merged trust store keeps

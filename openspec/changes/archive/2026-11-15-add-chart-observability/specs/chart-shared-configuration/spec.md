# Spec Delta

## Purpose

An operator can keep the shared settings of Ingot in Git and mount them through the chart, and a change to the file reaches
the running server without a restart.

## ADDED Requirements

### Requirement: Chart-owned or existing ConfigMap

The chart SHALL take the shared settings from `sharedConfiguration.content` (rendered as a ConfigMap owned by the release)
or from `sharedConfiguration.existingConfigMap` (an existing ConfigMap), and SHALL NOT render a ConfigMap when the second
is set. The file SHALL have the key `configuration.shared.json` in both cases.

#### Scenario: Content from values

- **WHEN** the chart is rendered with `sharedConfiguration.enabled: true` and `content` set
- **THEN** one ConfigMap holding `configuration.shared.json` with that content is rendered

#### Scenario: Existing ConfigMap

- **WHEN** the chart is rendered with `sharedConfiguration.enabled: true` and `existingConfigMap: shared-settings`
- **THEN** no ConfigMap is rendered and the server volume names `shared-settings`

#### Scenario: Nothing to mount

- **WHEN** the chart is rendered with `sharedConfiguration.enabled: true` and neither `content` nor `existingConfigMap`
- **THEN** `helm template` fails with a message that names both keys

### Requirement: Read-only directory mount

The file SHALL be mounted as a directory at `/app/shared-config`, read-only, and the mount MUST NOT use `subPath`. The
data volume at `/app/data` SHALL keep its mount.

#### Scenario: Mount path

- **WHEN** the chart is rendered with `sharedConfiguration.enabled: true`
- **THEN** the server container mounts the shared volume read-only at `/app/shared-config` without `subPath`, and the
  `/app/data` mount is unchanged

### Requirement: Reload switch and file variable

When `sharedConfiguration.enabled` is `true`, the server SHALL receive `INGOT_SHARED_CONFIGURATION_RELOAD=true` and
`INGOT_SHARED_CONFIGURATION_FILE=/app/shared-config/configuration.shared.json`. No other shared-configuration variable
SHALL be rendered.

#### Scenario: Enabled render

- **WHEN** the chart is rendered with `sharedConfiguration.enabled: true` and content
- **THEN** the server environment contains both variables with the values above

### Requirement: Checksum for chart-owned content

For chart-owned content the pod template SHALL carry a checksum annotation of the rendered content, so that a change to
the content restarts the pods. For an existing ConfigMap the chart SHALL NOT set this annotation, because the reload
applies the change without a restart.

#### Scenario: Content changes

- **WHEN** `sharedConfiguration.content` differs between two renders
- **THEN** the checksum annotation of the pod template differs

#### Scenario: Existing ConfigMap

- **WHEN** the chart is rendered with `existingConfigMap` set
- **THEN** the pod template carries no shared-configuration checksum annotation

### Requirement: Server only and default unchanged

The shared file SHALL be mounted on the server workload only. When `sharedConfiguration.enabled` is not `true`, the render
SHALL contain no ConfigMap, volume, mount, annotation or variable of this feature.

#### Scenario: Default render

- **WHEN** the chart is rendered with no `sharedConfiguration` value
- **THEN** the render has no shared-configuration object and the server environment is the same as before this change

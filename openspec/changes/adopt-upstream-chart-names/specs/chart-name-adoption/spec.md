# Spec Delta

## Purpose

An operator who moves a release from the upstream chart to the Ingot chart by changing the image keeps the release's data,
its Service and its Deployment, with no name override.

## ADDED Requirements

### Requirement: Upstream release keeps its names

The Ingot chart SHALL, when the release namespace holds a claim or a Deployment named with the upstream fullname and owned by
this release, render the upstream fullname for the claim, the Deployment, the Service and the ServiceAccount, unless
`nameOverride` or `fullnameOverride` is set.

#### Scenario: Image-only upgrade from the upstream chart

- **WHEN** the upstream chart 1.4.5 is installed as release `reposilite` with persistence, and `helm upgrade` changes only
  `image.name` and `image.tag` without any name override
- **THEN** the claim `reposilite` has the same UID as before the upgrade, no second claim is created, and the Service is
  `reposilite`

### Requirement: Adopted release keeps the upstream selector

The Ingot chart SHALL render the selector labels `app.kubernetes.io/name: reposilite` and
`app.kubernetes.io/instance: <release>` for the server workload when it adopts the upstream names.

#### Scenario: Deployment selector is unchanged

- **WHEN** the upstream release is upgraded to the Ingot chart with adoption active
- **THEN** the Deployment selector equals the selector of the upstream Deployment, and the Deployment update succeeds

### Requirement: New and Ingot releases keep their names

The Ingot chart SHALL render `<release>-ingot` and the selector `app.kubernetes.io/name: ingot` when no owned claim or
Deployment with the upstream fullname exists.

#### Scenario: Fresh install and existing Ingot release

- **WHEN** the chart is rendered without cluster access, or for a release such as `mirror` with no upstream object
- **THEN** the rendered claim is `<release>-ingot` and the selector name is `ingot`, as before this change

### Requirement: Foreign or mismatched objects never change the names

The Ingot chart SHALL NOT adopt a claim or a Deployment that another release owns, and SHALL fail the render when a Deployment
with the upstream fullname has a selector other than the upstream selector.

#### Scenario: Object owned by another release

- **WHEN** a claim named with the upstream fullname carries the owner annotations of a different release
- **THEN** the render uses `<release>-ingot` and the claim of the other release is not referenced

#### Scenario: Deployment with a different selector

- **WHEN** a Deployment named with the upstream fullname has a selector other than the upstream selector
- **THEN** the render fails with a message that names the Deployment and both selectors

### Requirement: Explicit overrides win

The Ingot chart SHALL render the names of `nameOverride` and `fullnameOverride` when either is set, and SHALL NOT perform
the lookup in that case.

#### Scenario: Override pair set

- **WHEN** `nameOverride` and `fullnameOverride` are both `reposilite`
- **THEN** the names and the selector are those of the upstream release, whatever the cluster holds

### Requirement: Adoption is announced

The Ingot chart SHALL print one line in NOTES.txt that names the adopted fullname when it adopts the upstream names, and SHALL
print nothing about adoption otherwise.

#### Scenario: Notice after an adopted upgrade

- **WHEN** the release is upgraded with adoption active
- **THEN** NOTES.txt names the kept fullname and states that the release keeps the upstream names

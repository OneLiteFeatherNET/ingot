# Spec Delta

## Purpose

A release installed with the upstream chart moves to the Ingot chart by changing the image line, and keeps its data.

## ADDED Requirements

### Requirement: Upgrade keeps the data volume

The Ingot chart SHALL, when installed over a release of the upstream chart 1.4.5 with only the image changed, keep the
existing persistent volume claim of that release (same name and same UID) and mount it at `/app/data`.

#### Scenario: Image-only upgrade from the upstream chart

- **WHEN** the rehearsal installs the upstream chart 1.4.5 as release `reposilite` with persistence enabled, and runs
  `helm upgrade` to the Ingot chart that changes only `image.name` and `image.tag`
- **THEN** the claim `reposilite` has the same UID as before the upgrade, and no second claim is created

### Requirement: Artifacts and tokens survive the upgrade

The upgraded release SHALL serve the artifacts and accept the tokens that existed before the upgrade, without any
configuration change.

#### Scenario: Artifact and token after the upgrade

- **WHEN** an artifact was uploaded and a token was created before the upgrade
- **THEN** the artifact has the same sha1 after the upgrade, and the token authenticates a request for the same path

### Requirement: Rollback to the upstream chart keeps the data

The rehearsal SHALL roll the release back to the upstream chart revision and assert the claim UID, the artifact sha1 and the
token again.

#### Scenario: Rollback

- **WHEN** `helm rollback` returns the release to the upstream revision
- **THEN** the claim UID, the artifact sha1 and the token are the same as before the upgrade

### Requirement: Rehearsal is reproducible

The rehearsal SHALL pin the upstream chart version 1.4.5 and the upstream image by tag and digest, and SHALL NOT depend on
the state of a cluster other than the one it creates.

#### Scenario: Re-run on a clean kind cluster

- **WHEN** the rehearsal runs twice on a fresh kind cluster with the same images
- **THEN** both runs reach the same assertions with the same results

### Requirement: Chart test hook is opt-in

The chart SHALL render a `helm test` pod only when `tests.enabled` is `true`, and SHALL render no test pod when the value is
unset or `false`.

#### Scenario: Default render

- **WHEN** the chart is rendered with the default values
- **THEN** no resource of kind Pod with the test annotation is rendered

#### Scenario: Test hook enabled

- **WHEN** the chart is rendered with `tests.enabled=true`
- **THEN** one test pod annotated `helm.sh/hook: test` is rendered

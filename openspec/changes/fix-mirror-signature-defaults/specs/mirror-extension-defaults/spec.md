# Spec Delta

## Purpose

A mirrored repository proxies Sigstore bundles beside their artifacts by default, and a stored extension list is never changed by a new default.

## ADDED Requirements

### Requirement: Default extensions include Sigstore bundles

The default `allowedExtensions` of a mirrored repository SHALL include `.sigstore.json` in addition to the artifact, checksum and `.asc` extensions it has today.

#### Scenario: Default list

- **WHEN** a mirrored repository is created without an `allowedExtensions` value
- **THEN** its `allowedExtensions` contains `.sigstore.json` and still contains `.jar`, `.pom`, `.sha512` and `.asc`

#### Scenario: Bundle requested through the default

- **WHEN** a client requests `GET /releases/com/example/a/1.0/a-1.0.jar.sigstore.json` from a mirror that uses the default list and the upstream serves the file
- **THEN** the mirror answers with the file and the same status as for `a-1.0.jar.asc`

### Requirement: Stored lists are kept

A stored `allowedExtensions` value SHALL NOT be changed by the new default. A mirror whose stored list lacks `.sigstore.json` SHALL keep refusing that extension.

#### Scenario: Stored list without the bundle

- **WHEN** a mirror stores the list `.jar`, `.pom` and `.asc` and a client requests `a-1.0.jar.sigstore.json`
- **THEN** the mirror answers as it does for a missing file and does not contact the upstream for that file

### Requirement: Hosted repositories are not filtered by extension

A hosted repository SHALL accept a deployment according to its acceptance rules and SHALL NOT consult the mirror `allowedExtensions` list.

#### Scenario: Deploy of a bundle

- **WHEN** a client deploys `a-1.0.jar.sigstore.json` to a hosted repository
- **THEN** the deployment is decided as it is for any other file of that repository, with no reference to the mirror list

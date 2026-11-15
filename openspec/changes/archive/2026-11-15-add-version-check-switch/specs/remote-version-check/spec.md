# Spec Delta

## Purpose

An operator turns the hourly check for a newer Ingot version off with an environment variable, and an unchanged deployment keeps checking exactly as before.

## ADDED Requirements

### Requirement: Check stays on by default

The remote version check SHALL stay enabled when no property and no environment variable names it.

#### Scenario: Nothing configured

- **WHEN** neither a system property nor an environment variable for the check is set
- **THEN** the setting resolves to enabled

### Requirement: Environment variable turns the check off

The setting SHALL read `INGOT_STATUS_REMOTE_VERSION_CHECK`, with `REPOSILITE_STATUS_REMOTE_VERSION_CHECK` as fallback, and the value `false` without regard to case SHALL disable the check. An empty value SHALL count as not set.

#### Scenario: Variable set to false

- **WHEN** `INGOT_STATUS_REMOTE_VERSION_CHECK` is `false` and no system property is set
- **THEN** the instance status request opens no connection to the version endpoint and reports the running version as latest

#### Scenario: Legacy variable

- **WHEN** only `REPOSILITE_STATUS_REMOTE_VERSION_CHECK=false` is set
- **THEN** the check is disabled

#### Scenario: Empty variable

- **WHEN** `INGOT_STATUS_REMOTE_VERSION_CHECK` is an empty string
- **THEN** the setting falls through to the next source

### Requirement: Precedence is fixed

The setting SHALL use the first present value of: system property `ingot.status.remote-version-check`, variable `INGOT_STATUS_REMOTE_VERSION_CHECK`, system property `reposilite.status.remote-version-check`, variable `REPOSILITE_STATUS_REMOTE_VERSION_CHECK`.

#### Scenario: Property outranks variable

- **WHEN** the variable is `false` and the `ingot.` property is `true`
- **THEN** the check is enabled

#### Scenario: INGOT variable outranks legacy property

- **WHEN** the `INGOT_` variable is `true` and the `reposilite.` property is `false`
- **THEN** the check is enabled

### Requirement: Existing property semantics are kept

A system property SHALL keep its current meaning: the value `true` enables the check and every other value disables it.

#### Scenario: Property with an unusual value

- **WHEN** `ingot.status.remote-version-check` is `no`
- **THEN** the check is disabled, as before this change

### Requirement: Unknown variable value is ignored with a warning

A variable value other than `true`, `false` or empty SHALL be ignored, SHALL leave the next source in charge, and SHALL log one warning that names the variable.

#### Scenario: Typo in the variable

- **WHEN** `INGOT_STATUS_REMOTE_VERSION_CHECK` is `flase`
- **THEN** the setting falls through to the next source, and one warning naming the variable is logged

### Requirement: Version check sends no Ingot identifier

The version request SHALL carry no request body, no instance identifier and no Ingot version number.

#### Scenario: Request captured by a local server

- **WHEN** the check runs against a local server that records the request
- **THEN** the recorded request has no body and no header or query value that names the instance or its version

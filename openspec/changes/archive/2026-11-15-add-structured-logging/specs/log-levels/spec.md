# Spec Delta

## Purpose

An operator sets the console threshold and per-package levels at start without a command-line flag or an edited properties
file.

## ADDED Requirements

### Requirement: Console threshold from the environment

The server SHALL read `INGOT_LOG_LEVEL` (or `REPOSILITE_LOG_LEVEL` when unset) and use it as the console threshold, with the
same values as `--level`. The `--level` flag SHALL win over the variable when both are given. When neither is set the
threshold SHALL be INFO, as before.

#### Scenario: Variable sets the threshold

- **WHEN** the server starts with `INGOT_LOG_LEVEL=DEBUG` and no `--level` flag
- **THEN** debug lines reach stdout

#### Scenario: Flag wins

- **WHEN** the server starts with `INGOT_LOG_LEVEL=DEBUG` and `--level=WARN`
- **THEN** debug and info lines do not reach stdout

#### Scenario: Default

- **WHEN** the server starts with neither a flag nor a variable
- **THEN** the threshold is INFO

### Requirement: Per-package levels from the environment

The server SHALL read `INGOT_LOG_LEVELS` as a comma-separated list of `package=level` pairs and apply each pair as a level
for that package and its sub-packages when the server starts. Packages not listed SHALL keep the levels in
`tinylog.properties`.

#### Scenario: One package overridden

- **WHEN** the server starts with `INGOT_LOG_LEVELS=com.reposilite.web=warn`
- **THEN** info lines from `com.reposilite.web` are not written, and info lines from other packages are still written

#### Scenario: Entry with an unknown level

- **WHEN** the server starts with `INGOT_LOG_LEVELS=com.reposilite.web=loud`
- **THEN** the start stops and the message names `INGOT_LOG_LEVELS` and the entry

### Requirement: Invalid level values stop the start

The server SHALL refuse a `INGOT_LOG_LEVEL` value that is not a known level, and SHALL name the variable in the message. It
SHALL NOT fall back silently to INFO for this variable.

#### Scenario: Unknown threshold

- **WHEN** the server starts with `INGOT_LOG_LEVEL=verbose`
- **THEN** the start stops and the message names `INGOT_LOG_LEVEL`


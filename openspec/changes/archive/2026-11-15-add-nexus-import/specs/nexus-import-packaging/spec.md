# Spec Delta

## Purpose

The importer ships with Ingot and costs nothing until an operator turns it on. Switching Ingot in for Reposilite stays a one-line
change.

## ADDED Requirements

### Requirement: Own module and names

The plugin SHALL be the module `reposilite-plugins/nexus-import-plugin`, with plugin name `nexus-import`, code in the package
`net.onelitefeather.ingot.nexusimport`, and a jar named `nexus-import-plugin.jar`. It SHALL NOT change the legacy `migration` plugin.

#### Scenario: Both present

- **WHEN** both plugins are on the plugin path
- **THEN** both start, with different names and routes

### Requirement: Bundled and off by default

The image SHALL contain `/app/bundled-plugins/nexus-import-plugin.jar`. The loader SHALL load it only when `INGOT_NEXUS_IMPORT_ENABLED`
is `true`, or when that variable is unset and `REPOSILITE_NEXUS_IMPORT_ENABLED` is `true`. A set `INGOT_` value SHALL win.

#### Scenario: Default

- **WHEN** neither variable is set
- **THEN** the plugin is not loaded, no route exists and no table is created

#### Scenario: Fallback spelling

- **WHEN** only `REPOSILITE_NEXUS_IMPORT_ENABLED=true` is set
- **THEN** the plugin is loaded

#### Scenario: INGOT wins

- **WHEN** `INGOT_NEXUS_IMPORT_ENABLED=false` and the legacy variable is `true`
- **THEN** the plugin is not loaded

### Requirement: One switch per bundled jar

The metrics switch SHALL NOT load the importer, and the importer switch SHALL NOT load the metrics plugin. A jar that the loader does
not know SHALL follow the metrics switch as before.

#### Scenario: Metrics only

- **WHEN** `INGOT_METRICS_ENABLED=true` and the importer switch is unset
- **THEN** only the metrics plugin loads

#### Scenario: Unknown jar

- **WHEN** an operator's own jar is in the bundled directory and the metrics switch is on
- **THEN** it loads as it did before this change

### Requirement: Drop-in contract

Adding the jar SHALL NOT change the entrypoint, the paths, the port, the volume, the healthcheck or any existing variable, and an
unmodified Reposilite compose file with the image line swapped SHALL start and adopt an existing data directory.

#### Scenario: Upstream compose

- **WHEN** the container job starts the image from an unmodified upstream compose file
- **THEN** it starts, adopts `/app/data`, and honours `PUID`, `PGID` and `REPOSILITE_OPTS`

### Requirement: Documented switches

Every `INGOT_NEXUS_IMPORT_*` variable SHALL be listed with its fallback, default and range in `docs/gitbook/reference/environment-variables.md`,
and the how-to SHALL name the three decisions an operator takes: the conflict policy, the audit mode and the credential form.

#### Scenario: Reference complete

- **WHEN** the plugin reads a variable
- **THEN** the reference page has a row for it

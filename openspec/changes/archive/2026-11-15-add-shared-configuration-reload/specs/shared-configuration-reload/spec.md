# Spec Delta

## Purpose

An instance that loads its shared settings from a mounted file picks up a changed file without a restart, and the file it loads
is never overwritten by the API. The switch is off by default, so an existing installation keeps today's behaviour.

## ADDED Requirements

### Requirement: The reload is opt-in

The server SHALL read `INGOT_SHARED_CONFIGURATION_RELOAD` and fall back to `REPOSILITE_SHARED_CONFIGURATION_RELOAD` when the
first is unset. The value `INGOT_` takes precedence when both are set. The setting SHALL be off when neither is set. An
unknown value SHALL stop the start with a message that names the variable.

#### Scenario: Not configured

- **WHEN** the server starts from a file with `--shared-configuration` and neither variable is set, and the file changes
- **THEN** the server does not load the change and does not write the file on a settings save

#### Scenario: Legacy spelling

- **WHEN** only `REPOSILITE_SHARED_CONFIGURATION_RELOAD=true` is set
- **THEN** the switch is on

#### Scenario: Unknown value

- **WHEN** `INGOT_SHARED_CONFIGURATION_RELOAD=yes` is set
- **THEN** the server stops the start and the message names `INGOT_SHARED_CONFIGURATION_RELOAD`

### Requirement: A changed file is applied without a restart

With the switch on and a `--shared-configuration` file as the source, the server MUST check the file on the existing ten second
poll and MUST apply a changed document. A change is detected by the content of the file, not by its modification time.

#### Scenario: Edit of the mounted file

- **WHEN** the file is rewritten with a new value for a setting
- **THEN** the running instance serves the new value after the next poll, without a restart

#### Scenario: Rewrite that keeps the modification time

- **WHEN** the file content changes and its modification time is set back to the old value
- **THEN** the server still applies the new content

#### Scenario: Symlink swap

- **WHEN** the file is replaced by changing the target of a `..data` symlink, as a mounted ConfigMap is updated
- **THEN** the server applies the content of the new target

#### Scenario: Unchanged file

- **WHEN** the poll runs and the content of the file is the content loaded last
- **THEN** the server does not reload the settings

### Requirement: A broken document keeps the settings in force

When the document of the file does not parse, the server MUST keep the settings in force and MUST log exactly one ERROR that
names the source. The server MUST NOT parse the same content again on every poll. A domain that fails validation keeps its
previous value, and the other domains of the same document are still applied.

#### Scenario: Document that does not parse

- **WHEN** the file is replaced by content that is not JSON and two polls run
- **THEN** the settings keep their previous values and exactly one ERROR is logged

#### Scenario: Corrected document

- **WHEN** the broken file is replaced by a valid document
- **THEN** the next poll applies the valid document

#### Scenario: Invalid value in one domain

- **WHEN** a document has a valid value for one domain and an invalid value for another
- **THEN** the valid domain is applied and the invalid domain keeps its previous value

### Requirement: File-managed settings are read-only through the API

With the switch on and the file as the source, the server SHALL answer a write to a known settings domain with `409`, through
`PUT /api/settings/domain/{name}` and through the facade method that plugins use. The answer SHALL name the settings file as
the owner of the domain. A rejected write MUST NOT change the file or the settings, and MUST NOT record a `SETTINGS_CHANGED`
audit event. A request for an unknown domain SHALL keep answering `404`.

#### Scenario: Save from the dashboard or the API

- **WHEN** a client sends `PUT /api/settings/domain/maven` with a new body
- **THEN** the response is `409`, the file bytes are unchanged and the stored settings are unchanged

#### Scenario: Plugin write

- **WHEN** a plugin calls `updateSharedSettings` for a known domain
- **THEN** the call returns an error, the file is not written and no audit event is recorded

#### Scenario: Unknown domain

- **WHEN** a client sends `PUT /api/settings/domain/unknown` with a body
- **THEN** the response is `404`

#### Scenario: Switch off

- **WHEN** the switch is off and a client saves a domain
- **THEN** the change is stored in the file as before

### Requirement: The database source is not affected

When the switch is on and no `--shared-configuration` file is given, the server MUST use the database source as before, MUST
keep accepting writes, and MUST log one WARN at start that says the switch has no effect.

#### Scenario: Switch on without a file

- **WHEN** `INGOT_SHARED_CONFIGURATION_RELOAD=true` is set and the server starts without `--shared-configuration`
- **THEN** a save of a settings domain is stored in the database and one WARN names the switch as having no effect

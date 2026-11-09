# Spec Delta

## Purpose

An operator who mistypes a configuration value sees the mistake in the log, and the value in effect is the one Ingot has always used.

## ADDED Requirements

### Requirement: Unreadable shared settings are logged as an error

When the stored shared configuration is not valid JSON, the loader SHALL log one ERROR line. The line SHALL name the provider, which for the local file is the file `configuration.shared.json`, and SHALL give the line and column reported by the parser. The line SHALL NOT contain the parser message or any part of the document. The loader SHALL continue with defaults for every domain, as it does today.

#### Scenario: Malformed shared file

- **WHEN** `configuration.shared.json` holds a document that is not valid JSON
- **THEN** the captured log holds exactly one ERROR line that contains `configuration.shared.json` and the line and column, and no value from the document, and the loading call still succeeds

#### Scenario: Empty shared file

- **WHEN** `configuration.shared.json` is empty or missing
- **THEN** no ERROR line is logged for it

### Requirement: Values that cannot be converted name their setting

When an environment variable or system property cannot be converted to the type of its setting, the factory SHALL log one ERROR line that names the setting and the expected type, before it throws the exception it throws today. The exception type and message SHALL NOT change.

#### Scenario: Int setting with a non-numeric value

- **WHEN** the external property `PORT` is set to `abc`
- **THEN** the captured log holds one ERROR line that contains `PORT` and `int`, and applying the property still throws `NumberFormatException`

#### Scenario: Long setting with a non-numeric value

- **WHEN** the external property `IDLETIMEOUT` is set to `abc`
- **THEN** the captured log holds one ERROR line that contains `IDLETIMEOUT` and `long`, and applying the property still throws `NumberFormatException`

### Requirement: Boolean values other than true or false are reported

When a boolean setting receives a value that is neither `true` nor `false`, ignoring case, the factory SHALL log one WARN line that names the setting and states that `true` or `false` is expected. The WARN line SHALL NOT contain the value. The setting SHALL keep the value it gets today.

#### Scenario: Boolean setting with yes

- **WHEN** the external property `DEBUGENABLED` is set to `yes`
- **THEN** the captured log holds one WARN line that contains `DEBUGENABLED`, and the setting is `false`

#### Scenario: Boolean setting with an upper case true

- **WHEN** the external property `DEBUGENABLED` is set to `TRUE`
- **THEN** no WARN line is logged for it, and the setting is `true`

### Requirement: Setting values do not change

The change SHALL NOT alter any configuration value. Every value that loads today SHALL load to the same result, and every failure that stops startup today SHALL still stop startup.

#### Scenario: Unchanged boolean result

- **WHEN** the external property `DEBUGENABLED` is set to `yes`
- **THEN** the setting is `false`, the same as before this change

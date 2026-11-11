## ADDED Requirements

### Requirement: Sealed secret values

When a settings key is active, the server SHALL store every non-empty literal value of a `@Sensitive` property as an envelope. Other values, empty values and settings secret references SHALL be stored as written.

#### Scenario: Secret sealed in the stored document

- **WHEN** a key is active and the S3 secret key is set to a literal value
- **THEN** the stored settings contain an envelope for that value and not the literal value

#### Scenario: Reference stays a reference

- **WHEN** a key is active and a `@Sensitive` value is `${env:S3_SECRET}` with the reference switch on
- **THEN** the stored value is the reference and no envelope is written for it

### Requirement: Envelope format

The server SHALL write a sealed value as `enc:v1:<kid>:<base64>`, where the body is a random 12 byte nonce, the AES-256-GCM ciphertext and a 16 byte tag. The additional data SHALL name the domain and the property path, with list entries named by their key, and SHALL NOT use the list index.

#### Scenario: Envelope moved to another field

- **WHEN** an envelope of one property is copied into another property of the stored document
- **THEN** reading the document fails for the property that holds it, naming the domain and the property and not the value

#### Scenario: Unknown envelope version

- **WHEN** a stored value starts with `enc:v2:`
- **THEN** the server refuses to read it and names its path, and it does not treat the text as a plain value

### Requirement: Key sources

The server SHALL read the settings key from `INGOT_SETTINGS_KEY` or from the file named by `INGOT_SETTINGS_KEY_FILE`, accepting the `REPOSILITE_` spelling as fallback. The key SHALL be base64 of exactly 32 bytes. Setting both sources SHALL stop the start.

#### Scenario: Key file with a trailing line break

- **WHEN** `INGOT_SETTINGS_KEY_FILE` names a file whose content is a valid key followed by one line break
- **THEN** the server uses that key

#### Scenario: Key of the wrong length

- **WHEN** `INGOT_SETTINGS_KEY` decodes to 16 bytes
- **THEN** the server stops at start with a message that names the variable and the length, and the message holds no part of the key

#### Scenario: Both sources set

- **WHEN** `INGOT_SETTINGS_KEY` and `INGOT_SETTINGS_KEY_FILE` are both set
- **THEN** the server stops at start and names both variables

### Requirement: Key from the environment only

The settings key SHALL be read from the process environment or from a file it names. A shared settings document and the local configuration file SHALL NOT set or override it.

#### Scenario: Key written into the shared document

- **WHEN** the shared settings document contains a field named like the key variable
- **THEN** the server ignores it and the active key does not change

### Requirement: Key identity at start

The server SHALL store the key identifier and a check value of the active key. At start, an instance whose configured key does not match the stored check SHALL stop, unless the stored check matches `INGOT_SETTINGS_KEY_PREVIOUS`. The message SHALL name the key identifier and not the key.

#### Scenario: Replica started with another key

- **WHEN** an instance starts with a key whose check differs from the stored check and from the previous key
- **THEN** the instance stops at start and names the stored key identifier

#### Scenario: First start with a key

- **WHEN** a key is configured and no check is stored and no envelope exists
- **THEN** the server stores the check of that key and starts

### Requirement: No key with sealed values

An instance without a key SHALL stop at start when the stored document contains a sealed value, and the message SHALL name the domain and the property.

#### Scenario: Key removed after encryption

- **WHEN** the stored document holds envelopes and no key source is set
- **THEN** the server stops at start and names the first affected domain and property

### Requirement: Optional mode is the default

`INGOT_SETTINGS_ENCRYPTION` SHALL default to `optional`. In `optional` mode, without a key, the stored bytes, the start, the settings API and the download SHALL behave as they did before this change.

#### Scenario: Upstream data directory without a key

- **WHEN** an unmodified Reposilite data directory with plain text secrets is started without any key variable
- **THEN** the server starts, the stored document is not rewritten and the settings API returns the same responses as before

#### Scenario: Key set on first start in database mode

- **WHEN** a key is configured in `optional` mode and the database holds plain text secrets
- **THEN** the server stores the document again with sealed values in one compare-and-set write and records one `SETTINGS_SECRETS_REENCRYPTED` event without values

### Requirement: Required mode

With `INGOT_SETTINGS_ENCRYPTION=required`, the server SHALL stop at start without a valid key, and SHALL refuse with `409` every write of a `@Sensitive` value while no key is active.

#### Scenario: Required without a key

- **WHEN** `INGOT_SETTINGS_ENCRYPTION=required` and no key source is set
- **THEN** the server stops at start and names the missing key variables

#### Scenario: Write without a key in required mode

- **WHEN** the mode is `required` and a key is active
- **THEN** a write of a `@Sensitive` value is stored sealed

### Requirement: Decryption failure on load

A value that cannot be decrypted SHALL fail the load of its domain. At start, the instance SHALL stop. On a reload, the previous settings SHALL stay in place and an error SHALL be logged that names the domain and the property.

#### Scenario: Tampered envelope on reload

- **WHEN** a sealed value is changed in the shared file while the instance runs with reload on
- **THEN** the instance keeps the previous settings, logs an error that names the domain and property, and logs no value

### Requirement: Rotation with a previous key

`INGOT_SETTINGS_KEY_PREVIOUS` (with its `_FILE` spelling) SHALL be used for decryption only. The console command `settings-reencrypt` SHALL seal every envelope with the active key, store the check of the active key and record one `SETTINGS_SECRETS_REENCRYPTED` event with the key identifiers.

#### Scenario: Value sealed with the previous key

- **WHEN** an instance holds the new key as active and the old key as previous, and a stored value was sealed with the old key
- **THEN** the instance reads the value and seals the next write with the new key

#### Scenario: Re-encrypt run

- **WHEN** `settings-reencrypt` runs with the new key active
- **THEN** every stored envelope names the new key identifier and the stored check is the new key's check

### Requirement: Reads hide every secret for the dashboard

A `GET /api/settings/domain/{name}?secrets=hidden` SHALL replace every non-empty `@Sensitive` value by `********`, whatever `hideSettingsSecrets` says. A sealed value SHALL NOT be returned in any form. A reference SHALL be returned as written.

#### Scenario: Dashboard read with the flag off

- **WHEN** `hideSettingsSecrets` is false and the dashboard reads a domain with `?secrets=hidden`
- **THEN** every non-empty secret is `********`, and the response holds no envelope

#### Scenario: Plain read unchanged

- **WHEN** a script reads the domain without the parameter and `hideSettingsSecrets` is false
- **THEN** the response is the same as before this change

### Requirement: Placeholder on write

A write that sends `********` for a `@Sensitive` value SHALL keep the stored value, on every instance and whatever `hideSettingsSecrets` says. A `********` without a stored counterpart SHALL be refused with `400`, and the message SHALL name the property.

#### Scenario: Unchanged secret written back

- **WHEN** a dashboard saves a domain in which a secret still reads `********`
- **THEN** the stored value is kept and the audit event names only the changed fields

#### Scenario: Placeholder without a stored value

- **WHEN** a new mirror entry is written with `********` as its password
- **THEN** the server answers `400` and stores nothing

### Requirement: No value in logs, audit or the support bundle

Log lines, audit events and the support bundle SHALL NOT contain a key, a password, a plain secret value or an envelope. The key identifier MAY appear in logs and audit events.

#### Scenario: Failure log of a sealed document

- **WHEN** a stored document with envelopes fails to load
- **THEN** the captured log holds the masked document and no envelope text

### Requirement: Warning of plain text secrets

The settings state response SHALL report `secretsEncrypted` and the number of stored secret values that are neither sealed nor references. The dashboard SHALL show an alert while secrets are not sealed and that number is greater than zero, naming the count and the key setting only.

#### Scenario: Warning without a key

- **WHEN** no key is active and two stored secret values are plain text
- **THEN** the state reports `secretsEncrypted=false` and `plaintextSecrets=2`, and the dashboard shows the alert

### Requirement: Plugin secrets

A plugin SHALL mark its secret properties with `@Sensitive`. The server SHALL seal, mask and flag those properties in the schema as `x-ingot-secret` with no further plugin code.

#### Scenario: Plugin property marked sensitive

- **WHEN** a plugin settings class marks a property with `@Sensitive` and a key is active
- **THEN** the stored value of that property is sealed and the schema of that property carries `x-ingot-secret: true`

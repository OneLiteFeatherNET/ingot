## ADDED Requirements

### Requirement: Password protected export

`POST /api/settings/export` SHALL return the saved state of every settings domain as a file encrypted with a key derived from the password, using Argon2id and AES-256-GCM. The password SHALL be at least 12 characters. The export SHALL work without a settings key.

#### Scenario: Export without a settings key

- **WHEN** no settings key is configured and an administrator exports with a password of 12 characters
- **THEN** the response is a file with the header `format: ingot-settings-export` and version 1, and the plain text does not appear in it

#### Scenario: Short password

- **WHEN** the password has 11 characters
- **THEN** the server answers `400` and stores no audit event

### Requirement: Export content

The encrypted content SHALL hold the saved state, not the draft, with every secret in clear and every reference as written. The header SHALL hold the key derivation parameters and the salt, and it SHALL be authenticated as additional data.

#### Scenario: Draft is not exported

- **WHEN** a domain has unsaved changes in the dashboard and an export is requested
- **THEN** the export holds the stored value of that domain

### Requirement: Import opens a file for review

`POST /api/settings/import/open` SHALL decrypt an export with the password and return the plain document without storing it. A wrong password, a changed header, a changed ciphertext, an unknown version and KDF parameters above the bounds SHALL all return `400` with the same text.

#### Scenario: Wrong password

- **WHEN** the password does not match the export
- **THEN** the server answers `400` with the generic message, stores nothing, and records `SETTINGS_IMPORT_REFUSED` with reason `password`

#### Scenario: Changed header

- **WHEN** the `createdAt` field of an export is edited after the export was written
- **THEN** the server answers `400` with the generic message

#### Scenario: Cost above the bounds

- **WHEN** an export declares a memory cost above 262144 KiB
- **THEN** the server answers `400` without running the key derivation

### Requirement: Import through the review

The dashboard SHALL apply an imported document to the draft and store it only through the ordinary save, so every value passes the existing review, conflict check and `SETTINGS_CHANGED` event. A file without the `format` header SHALL be imported as before.

#### Scenario: Legacy file

- **WHEN** an administrator imports a plain JSON file without a `format` header
- **THEN** the dashboard applies it to the draft as before, and placeholders in it keep the stored values

### Requirement: Password dialogs

The dashboard SHALL ask for the password before a download, with a confirmation field and a strength hint, and before an import of an export. It SHALL NOT keep the password after the request.

#### Scenario: Download with mismatched confirmation

- **WHEN** the confirmation does not equal the password
- **THEN** the dialog does not send the request

#### Scenario: Import of an export

- **WHEN** an administrator selects an export file
- **THEN** the dashboard asks for the password before it sends the file

### Requirement: Audit of export and import

Four audit actions SHALL be appended to `AuditAction` after `UPLOAD_CHECKSUM_MISMATCH`: `SETTINGS_EXPORTED`, `SETTINGS_IMPORT_DECRYPTED`, `SETTINGS_IMPORT_REFUSED` and `SETTINGS_SECRETS_REENCRYPTED`. None SHALL carry a value, a password or a key.

#### Scenario: Export event

- **WHEN** an export succeeds
- **THEN** one `SETTINGS_EXPORTED` event records the actor, the number of domains and the time, and no field value

#### Scenario: Decrypt event

- **WHEN** an import decrypts successfully
- **THEN** one `SETTINGS_IMPORT_DECRYPTED` event records the actor and the number of domains

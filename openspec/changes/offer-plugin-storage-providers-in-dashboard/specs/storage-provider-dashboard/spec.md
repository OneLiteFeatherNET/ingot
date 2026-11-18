# Spec Delta

## Purpose

The dashboard and the repository wizard offer a storage provider that a plugin has loaded, next to the built-in `fs` and `s3`
providers. A provider whose plugin is not loaded is not offered. The saved configuration format and the built-in providers do not
change.

## ADDED Requirements

### Requirement: Plugin storage variants are offered only while the plugin is loaded

The served settings schema of a repository SHALL list, in the `oneOf` of `storageProvider`, one variant for each storage provider
factory that a loaded plugin registered. The built-in variants `fs` and `s3` SHALL always be listed first and SHALL NOT change.

#### Scenario: Plugin loaded

- **WHEN** a storage plugin with the type `gcs` is loaded
- **THEN** the served `storageProvider` schema contains a variant whose `type` is the constant `gcs`, after the built-in variants

#### Scenario: Plugin absent

- **WHEN** no storage plugin is loaded
- **THEN** the served `storageProvider` schema contains exactly the built-in variants and is byte-identical to the schema without this change

#### Scenario: Several plugins

- **WHEN** two storage plugins with the types `gcs` and `azure-blob` are loaded, in either jar order
- **THEN** the plugin variants are listed sorted by type id, `azure-blob` before `gcs`

### Requirement: A plugin variant takes its fields from the plugin's settings schema

A plugin variant SHALL carry the schema of the plugin's settings class, with the `@Doc` titles and descriptions and the `@Min`, `@Max`
and `@Sensitive` rules of that class.

#### Scenario: Fields of a plugin variant

- **WHEN** the served schema is read for a loaded plugin whose settings class declares a `bucketName` field
- **THEN** the variant of that plugin lists `bucketName` with the title given by its `@Doc`

#### Scenario: A plugin whose schema cannot be generated

- **WHEN** the settings schema of one loaded plugin cannot be generated
- **THEN** that plugin's variant is left out, a warning names the plugin and its type id, and every other variant is still listed

### Requirement: Secrets of a plugin variant are handled like built-in secrets

The `@Sensitive` fields of every listed variant SHALL be part of the secret handling of `storageProvider`. A placeholder that the
dashboard sends back for a secret SHALL keep the stored value.

#### Scenario: Plugin secret kept on update

- **WHEN** a repository uses a plugin variant with a sensitive field and the dashboard sends the placeholder for that field
- **THEN** the stored value of the field is kept and is not replaced by the placeholder

#### Scenario: Built-in secret unchanged

- **WHEN** no plugin is loaded
- **THEN** the secret handling of `storageProvider` for `fs` and `s3` is the same as before this change

### Requirement: A saved provider that is not listed is kept

A saved `storageProvider` whose type is not listed in the served schema SHALL be shown with its type name and a note that the provider
is not installed, and SHALL NOT be rewritten to `fs` or to any other type when the settings are saved.

#### Scenario: Plugin removed after use

- **WHEN** a repository is saved with the type `azure-blob` and the Azure plugin is no longer loaded
- **THEN** the Storage tab shows `azure-blob` with the not installed note and the saved block is written back unchanged

### Requirement: The wizard offers the listed providers

The repository wizard SHALL offer exactly the variants of the served schema as storage kinds. A plugin kind SHALL validate the
`required` fields of its schema and SHALL write a block with `type` and the filled fields, leaving blank optional fields out.

#### Scenario: Wizard with a plugin loaded

- **WHEN** the served schema lists the variants `fs`, `s3` and `gcs`
- **THEN** the wizard offers the three kinds in that order and writes a `gcs` block when `gcs` is chosen

#### Scenario: Wizard without a plugin

- **WHEN** the served schema lists `fs` and `s3` only
- **THEN** the wizard offers the two kinds and writes the blocks it writes today

### Requirement: Saved configuration format and plugin API are unchanged

The change SHALL NOT add or rename a configuration key, an environment variable, a route or a database field, and SHALL NOT change
the signatures of `IngotStorageProviderFactory`, `StorageProvider` or `StorageFacade`.

#### Scenario: Existing repository with the built-in providers

- **WHEN** an existing repository with a `fs` or an `s3` block is loaded and saved again
- **THEN** the saved block has the same keys and values as before this change

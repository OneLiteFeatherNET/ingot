# Spec Delta

## Purpose

A storage provider can be supplied by a plugin jar. The server finds it before any repository is built, keeps running when the
provider a repository names is not installed, and never touches the data of a repository whose provider is missing. The built-in
`fs` and `s3` providers keep their ids, settings and behaviour.

## ADDED Requirements

### Requirement: Public experimental storage interfaces

The provider interfaces SHALL live in `net.onelitefeather.ingot.storage` and carry the opt-in marker `ExperimentalIngotStorageApi`.
A factory SHALL declare the API version it was built for, and the registry SHALL accept only the version of this server. The existing
`StorageProviderFactory`, `StorageProvider` and `StorageFacade` SHALL NOT change their signatures.

#### Scenario: Matching API version

- **WHEN** a plugin jar contains a factory of the current API version
- **THEN** the factory is registered and its type can be used in a storage block

#### Scenario: Other API version

- **WHEN** a plugin jar contains a factory whose API version differs from the server
- **THEN** the factory is refused, and the warning names the class and both versions

#### Scenario: Plain factory in a plugin jar

- **WHEN** a plugin jar contains a `StorageProviderFactory` that does not implement `IngotStorageProviderFactory`
- **THEN** the factory is refused, and the warning names the jar

### Requirement: Discovery from plugin class loaders before initialisation

The server SHALL discover storage factories from the application class loader and from the class loader of every plugin jar. The
discovery SHALL run after every plugin has loaded and before the first plugin initialises, so that repositories built during plugin
initialisation see every registered provider.

#### Scenario: Plugin provider available to a repository

- **WHEN** a plugin jar registers the type `fixture` and a repository storage block names `fixture`
- **THEN** the repository is built with the plugin's provider and serves files

#### Scenario: Discovery order

- **WHEN** the server starts with plugins that depend on one another
- **THEN** every factory is registered before the first `initialize()` call, whatever the plugin order

### Requirement: Built-in providers keep their ids and cannot be replaced

The built-in providers `fs` and `s3` SHALL be registered first, SHALL keep their type ids, settings classes, JSON shape and behaviour, and
SHALL NOT be replaced by any plugin. A factory whose id is already registered SHALL be refused with a warning that names both sources.

#### Scenario: Plugin claims a built-in id

- **WHEN** a plugin registers a factory with the type `s3`
- **THEN** the plugin factory is refused, the warning names both sources, and repositories with `s3` keep the built-in provider

#### Scenario: Duplicate id between plugins

- **WHEN** two plugin jars register a factory with the same type
- **THEN** the first registered factory is kept, and the warning names both classes

#### Scenario: Invalid id

- **WHEN** a factory declares a type that is not lower-case kebab-case
- **THEN** the factory is refused, and the warning names the class

### Requirement: Plugin settings are part of the shared configuration

A storage block whose `type` is registered SHALL deserialise to the settings class of its factory. A storage block whose `type` is not
registered SHALL deserialise to `UnresolvedStorageProviderSettings`, which keeps the raw JSON and writes back the identical document. A block
without `type` SHALL keep the existing guess: `s3` when `bucketName` is present, `fs` otherwise.

#### Scenario: Unknown type round trip

- **WHEN** a storage block with the type `azure-blob` is read and written back without a registered factory
- **THEN** the written document equals the read document, field by field and in the same order

#### Scenario: Missing type guess

- **WHEN** a storage block has no `type` and contains `bucketName`
- **THEN** the block is read as an `s3` block, as before this change

#### Scenario: Unknown type does not fail the parse

- **WHEN** the shared configuration contains a storage block of a type that is not installed
- **THEN** the shared configuration is parsed and the other blocks keep their values

### Requirement: Missing provider keeps the server running

When a repository names a storage type that no factory has registered, the server SHALL start, SHALL create the repository in state
`UNAVAILABLE` with the reason `storage provider '<type>' is not installed`, and SHALL answer requests to that repository with `503`,
`Retry-After` and a problem body that names the reason. The server SHALL NOT read, write or remove any file of that repository, and SHALL
NOT call any operation of a storage provider for it.

#### Scenario: Startup with a missing type

- **WHEN** the server starts with a repository whose storage type is not installed
- **THEN** the server is running, the repository is listed with state `UNAVAILABLE` and its reason, and the other repositories serve files

#### Scenario: Request to an unavailable repository

- **WHEN** a client downloads or deploys a file of the unavailable repository
- **THEN** the response is `503` with the reason in the problem detail and `Retry-After: 30`, and no storage location is accessed

#### Scenario: Plugin installed later

- **WHEN** the missing plugin is installed and the server is restarted or the configuration is reloaded
- **THEN** the repository is built from the same raw block and becomes available, with no data moved or re-created

#### Scenario: Saving settings keeps the unknown block

- **WHEN** the repository settings are saved from the dashboard while the plugin is missing
- **THEN** the storage block is written back unchanged and the form shows it read-only with the reason

#### Scenario: Decorator over a missing type

- **WHEN** a plugin provider calls `createProvider` with a settings block whose type is not installed
- **THEN** the repository is `UNAVAILABLE` with the reason that names the missing inner type, as for a missing outer type

#### Scenario: Other failure of a factory

- **WHEN** a factory's `create` throws for any reason other than a missing type
- **THEN** the failure is reported and the repository is not loaded, as before this change, for built-in and plugin factories alike

#### Scenario: Readiness

- **WHEN** a repository is `UNAVAILABLE` and the readiness endpoint is called
- **THEN** the readiness answer does not fail because of that repository, and it lists the count of unavailable repositories

### Requirement: Dashboard schema lists registered providers

The repository settings schema served to the dashboard SHALL list every registered storage type, and each plugin type SHALL show its own
fields from its settings class, including the masking of fields marked `@Sensitive`. The static OpenAPI annotation of the built-in pair SHALL
NOT change.

#### Scenario: Plugin type in the schema

- **WHEN** the dashboard requests the repository settings schema with a plugin type registered
- **THEN** the schema lists that type as a choice and contains its settings fields

#### Scenario: Sensitive field of a plugin type

- **WHEN** a plugin settings class declares a field marked `@Sensitive`
- **THEN** the schema marks the field the same way as the built-in secrets, and the settings view masks its value

### Requirement: Provider context for plugins

A plugin factory SHALL receive a `StorageProviderContext` that offers the journalist, the failure facade, the working directory and the
repository name, `environment(name)`, `secret(name)` and `createProvider(settings)`. `environment` SHALL read the `INGOT_` name first and the
`REPOSILITE_` name second. `secret` SHALL read the `_FILE` variant when the variable is set and SHALL redact every value it returns from logs.

#### Scenario: Environment fallback

- **WHEN** a plugin reads `environment("FOO")` and only `REPOSILITE_FOO` is set
- **THEN** the value of `REPOSILITE_FOO` is returned

#### Scenario: Secret from file

- **WHEN** a plugin reads `secret("FOO")` and `FOO_FILE` names a readable file
- **THEN** the content of the file is returned without trailing line breaks, and the value does not appear in a log line

#### Scenario: Cycle in composition

- **WHEN** a provider calls `createProvider` with a settings value whose type is already being built in the same chain
- **THEN** the call fails with an error that lists the chain, and no provider is created

### Requirement: Contract test kit for storage providers

The project SHALL provide an abstract JUnit 5 class `StorageProviderContract` in the test fixtures of `reposilite-backend`, covering the
behaviour that every provider must keep. The `fs` provider SHALL run the class as a unit test with a temporary directory, and the `s3` provider
SHALL run it in the integration source set with its container.

#### Scenario: Ranged read outside the file

- **WHEN** a provider is asked for a range that starts past the end of a file, or a zero-length range
- **THEN** the result is the documented empty answer, and no exception escapes

#### Scenario: No partial object during write

- **WHEN** a `putFile` is still reading its input stream
- **THEN** a concurrent read of the same location does not return the partial content

#### Scenario: Root delete refused

- **WHEN** a provider is asked to remove the root location
- **THEN** the removal is refused and nothing is removed

#### Scenario: Missing key only

- **WHEN** a provider reads a location that is absent
- **THEN** the result is `NOT_FOUND`, and an I/O failure is never reported as `NOT_FOUND`

#### Scenario: Last writer wins

- **WHEN** several threads put different content to the same location
- **THEN** the stored content equals the content of one of the writes, never a mix of them

### Requirement: Contract kit leaves the built-in providers unchanged

The contract kit SHALL NOT change any behaviour, setting, key, JSON shape or on-disk layout of `fs` or `s3`. A case that a built-in
provider fails today SHALL be switched off for that provider with a reason that names the gap.

#### Scenario: Built-in gap is recorded, not changed

- **WHEN** a contract case fails for `fs` or `s3` on the code before this change
- **THEN** the case is skipped for that provider with a message that names the gap, and the provider's behaviour is unchanged

## MODIFIED Requirements

None.

## REMOVED Requirements

None.

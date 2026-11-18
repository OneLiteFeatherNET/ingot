# Design

This design depends on `add-storage-provider-registration` (the `IngotStorageProviderFactory` type, plugin discovery and the
registry `StorageProviderRegistry`) and on `add-storage-capability-hooks` (the capability interfaces). Both are archived. It
reads the settings shape of the Google Cloud Storage and Azure Blob plugins, and it does not change either plugin.

## Context

- The repository settings class `MavenSettings` declares the storage choice as `storageProvider: StorageProviderSettings`
  with `@get:OneOf(FileSystemStorageProviderSettings::class, S3StorageProviderSettings::class)`
  (`reposilite-backend/src/main/kotlin/com/reposilite/maven/application/MavenSettings.kt`).
- The served schema of a settings type comes from `SharedConfigurationFacade`. It first looks in `JsonSchemaLoader`'s generated
  schemes, which the Javalin OpenAPI annotation processor writes at build time, and generates the schema at runtime only when no
  generated scheme exists (`configuration/shared/SharedConfigurationFacade.kt`, the `init` block). A build-time scheme does not
  see plugins. This reading is taken from the code and is confirmed by task 1.1.
- `StorageProviderSerdes` (`storage/StorageProviderSerdes.kt`) builds `STORAGE_PROVIDERS` in a companion object from
  `ServiceLoader.load(StorageProviderFactory::class.java)`, which is the application class path only. Its `EnumResolver` and
  `SubtypeResolver` read that map, so the `type` enum and the `oneOf` of a generated `StorageProviderSettings` cannot contain a
  plugin type, even when the plugin is loaded.
- `SettingsSecrets.kt` derives the secret shape of a property from its `@OneOf` as well (`objectShape`, the branch that unions
  the shapes of the listed classes). A plugin variant's `@Sensitive` fields are therefore unknown to the secret handling today.
- The frontend renders a `oneOf` through `OneOfRenderer.vue` and `configuration.ts`, and it chooses the variant from the
  `type` value. The Storage tab is therefore expected to follow the served schema without a change. Task 4.1 verifies it.
- The wizard (`reposilite-frontend/src/components/settings/repositoryWizard.ts`) keeps `storage.kind` as `fs` or `s3`, and
  `storageProviderOf` writes `{ type: 'fs' }` or the S3 block. Any other kind is written as `fs`, which is why the wizard must
  not receive a plugin kind before this change.
- The display-only layout registry of the form ordering work (`reposilite-frontend/src/components/settings/nestedLayouts.ts`,
  PR #791) is not on `main` at the time of writing. The frontend group uses it only when it lands (open question 5 of the
  proposal).

## Decisions

### D1: a variant is a loaded storage factory, with no new plugin API

A variant is offered for every factory in the live registry whose source is a plugin class loader. The built-in factories
keep the static `@OneOf` list. A plugin therefore needs no new member and no flag. The variant's schema is the settings class
of the factory (`factory.settingsType`), with `type` fixed to `factory.type` as a `const`. Its title and description come from
the `@Doc` of the settings class, as they do for the built-in variants.

Alternative (open question 1): a `dashboardVisible` member on `IngotStorageProviderFactory`. It is not chosen by default
because it adds API surface for a need that no plugin has stated.

### D2: one live registry for the schema and the serdes

The registry that holds the built-in and the plugin factories is created once per server with the plugin system, and it is
handed to the schema generator and to the serdes. `StorageProviderSerdes` keeps its companion map only as the built-in
fallback for code that runs without a server, and its `EnumResolver` and `SubtypeResolver` read the registry when one is
present. The parse path (`parseStorageProvider`, with `UnresolvedStorageProviderSettings` for a type it does not know) is not
changed, so a saved file is read the same way as before.

The exact owner of the registry is confirmed by task 2.1, which reads where `StorageFacade` is constructed. Open question 2
covers the alternative.

### D3: the merge happens at serve time, for the storage property only

`SharedConfigurationFacade` serves the generated scheme of the repository settings unchanged, except for `storageProvider`.
For that one property, a transform reads the registry when the schema is served and appends one entry to the `oneOf` for
each plugin variant, in the order of D6. The transform:

- is an identity when the registry holds no plugin factory, so the served bytes equal the bytes of `main` (task 1.2 asserts
  this);
- generates the variant's schema with the runtime `SchemaGenerator` that already exists in the facade, which applies the
  `@Doc`, `@Sensitive` and `@Min` rules of the settings class;
- sets the `type` property of the variant to `const: <type id>`.

It is not a regeneration of every settings schema (open question 3). Only the one property that the registry can change is
touched, and only the settings type that declares `storageProvider` is affected.

### D4: the secret shape includes the offered variants

`SettingsSecrets.kt` computes the shape of `storageProvider` from the `@OneOf` list. The same computation also reads the
plugin variants of the registry when the shape is computed, so the `@Sensitive` fields of a plugin variant are included in
the secret shape. The consequences are the ones that the S3 keys already have: the dashboard receives a placeholder, the
stored value is sealed when `encrypt-settings-at-rest` is active, and a submitted placeholder keeps the stored secret.

This is required before any plugin variant is offered. Task 2.4 fails without it.

### D5: the wizard offers the served variants

`repositoryWizard.ts` replaces the fixed kinds with the variants of the served schema. `fs` and `s3` keep their current fields and
their current written shape. A plugin variant shows the fields that its schema lists, with the `required` list enforced as the
`validateStorage` function does for S3 today. The written block is `{ type: <type id>, ...fields }` and nothing else. The first
save of a plugin variant writes the fields that the user filled, and the optional fields are written only when they are not
blank, as `storageProviderOf` does now.

Alternative (open question 4): hand the plugin variant to the Storage tab. It is smaller but leaves the wizard unable to finish
a plugin repository.

### D6: ordering is deterministic and display-only

The variants of the served schema keep the built-in order (`fs`, `s3`). Plugin variants follow, sorted by type id, so the order
does not depend on the order of the plugin jars or on the class path. The fields inside a variant keep the order of the settings
class. When PR #791 lands, its display-only layout registry may reorder fields of a variant by name. It never changes the order
of the `oneOf` and never changes the saved file.

### D7: a saved type that is not offered is kept

A stored `storageProvider` whose type is not in the served list (for example `azure-blob` after the plugin was removed) is shown
in the Storage tab with its type name and a note that the provider is not installed. Nothing rewrites the block. The repository
is `UNAVAILABLE` in the registry with the existing reason, as `add-storage-provider-registration` defines it, so this change adds
no new state. The Storage tab must not fall back to `fs` when the `type` is unknown. Task 4.3 tests this.

### D8: a failing plugin schema is skipped with a warning

When the runtime generator fails for one plugin variant, the variant is left out of the served list, the warning names the plugin
and the type id, and every other variant is still served. The repository settings schema is never refused for one plugin. This
matches the rule of `add-storage-provider-registration` that a broken plugin does not stop the others.

### D9: drop-in

- With no storage plugin loaded, the served schema, the wizard options and the secret shape are the ones of `main`. Task 1.2 and
  task 2.5 compare them.
- The saved configuration format does not change. No key, no environment variable, no manifest and no volume changes.
- The plugin API signatures do not change, so an existing plugin compiles against this server unchanged.

## Risks

- The build-time scheme of a settings type can differ from what the runtime generator produces. D3 keeps the built-in part of the
  schema as it is, so the risk is limited to the plugin part, which only exists when a plugin is loaded.
- A plugin's `@Sensitive` rules apply only when its settings class is annotated. A plugin that forgets the annotation leaks the
  value to the dashboard as today's unannotated plugins already do. The documentation of the plugin API says so; no runtime check is
  added here.
- The wizard and the Storage tab could drift. Both read the same served list, and task 4.2 tests that the wizard offers exactly that list.

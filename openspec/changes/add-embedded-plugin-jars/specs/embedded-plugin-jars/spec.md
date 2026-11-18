## ADDED Requirements

### Requirement: Build embeds only the named plugin jars

The backend shadow jar SHALL contain, for each module path in `embeddedPluginProjects` of `reposilite-backend/build.gradle.kts`,
exactly one entry `embedded-plugins/<jar name>` with the module's shadow jar as it is. With the list empty the shadow jar SHALL
contain no entry under `embedded-plugins/`.

#### Scenario: List empty

- **WHEN** the backend shadow jar is built with `embeddedPluginProjects` empty
- **THEN** the jar contains no entry whose name starts with `embedded-plugins/`

#### Scenario: Named module

- **WHEN** a fixture module is named in `embeddedPluginProjects`
- **THEN** the jar contains exactly one entry `embedded-plugins/<fixture jar name>` whose bytes equal the module's shadow jar

### Requirement: Verification fails a broken embedding

The task `verifyEmbeddedPlugins` SHALL fail the build when a named module has no entry, when the entry has no manifest attribute
`Implementation-Version`, or when the jar holds an `embedded-plugins/` entry that no named module declares. It SHALL run as part of
`check` of the backend.

#### Scenario: Missing entry

- **WHEN** a module is named but its jar is not in the server jar
- **THEN** `verifyEmbeddedPlugins` fails and names the module

#### Scenario: Undeclared entry

- **WHEN** the server jar contains `embedded-plugins/other.jar` and no module names it
- **THEN** `verifyEmbeddedPlugins` fails and names the entry

### Requirement: Loader reads the nested jars of the running server

At start the loader SHALL list the `embedded-plugins/` entries that end in `.jar` in the code source of the server, which is the
server jar or, when run from classes, the classes directory. A server with no such entry SHALL NOT create the extraction directory.

#### Scenario: No nested jar

- **WHEN** the server starts from a jar without any `embedded-plugins/` entry
- **THEN** the directory `<workingDirectory>/embedded-plugins/` does not exist after the start and the loader logs no line about embedded plugins

#### Scenario: Two nested jars

- **WHEN** the server starts from a jar with two `embedded-plugins/` entries
- **THEN** both are extracted and loaded, and each one is listed once

### Requirement: Extraction directory is rewritten at each start

Before any embedded jar is loaded, the loader SHALL remove the directory `<workingDirectory>/embedded-plugins/` recursively when it exists.
Each jar SHALL then be written to a `.partial` file and moved to its final name by an atomic move. A jar that the server no longer
embeds SHALL NOT remain in the directory after the start.

#### Scenario: Stale copy removed

- **WHEN** the directory holds `old.jar` and the server embeds only `javadoc-plugin.jar`
- **THEN** after the start the directory contains `javadoc-plugin.jar` and no `old.jar`

#### Scenario: Partial file left by a crash

- **WHEN** the directory holds `javadoc-plugin.jar.partial` from an earlier crash
- **THEN** the start removes it and writes `javadoc-plugin.jar` in full

### Requirement: Unwritable extraction directory falls back to the temporary directory

When the extraction directory cannot be written, the loader SHALL extract the jars into `<java.io.tmpdir>/ingot-embedded-plugins-<hash>`,
where `<hash>` is the first twelve hexadecimal digits of the SHA-256 of the absolute working directory, and log one WARN line that
names both directories and the reason. When the fallback cannot be written either, the loader SHALL log one ERROR line that names
the jar and both reasons, SHALL NOT load that jar, and SHALL let the start go on.

#### Scenario: Working directory not writable

- **WHEN** the extraction directory cannot be created and the temporary directory can be written
- **THEN** the jar is extracted into the fallback directory, the plugin loads, and one WARN line names both directories

#### Scenario: Neither directory writable

- **WHEN** neither the extraction directory nor the fallback directory can be written
- **THEN** one ERROR line names the jar, the server starts without that plugin, and the start does not fail

### Requirement: Each embedded jar has its own class loader

The loader SHALL load each extracted jar in its own `URLClassLoader` whose parent is the class loader of the server. It SHALL
instantiate only the plugin classes that the jar names in its own service file, and it SHALL add that loader to the loaders whose
storage providers are discovered.

#### Scenario: Plugin from an embedded jar

- **WHEN** an embedded jar declares the plugin `fixture`
- **THEN** the plugin object's class loader is not the class loader of the server, and the storage providers of the jar are discovered

#### Scenario: Two embedded jars

- **WHEN** two embedded jars are loaded
- **THEN** each one has its own class loader

### Requirement: Embedded plugins have the origin EMBEDDED and the tier Core

A plugin registered from an embedded jar SHALL have the origin `EMBEDDED`. The tier resolver SHALL map `EMBEDDED` to Core, the same as
`BUILT_IN` and `BUNDLED_IMAGE`, except that a declared Experimental tier SHALL stay Experimental.

#### Scenario: Embedded plugin without declaration

- **WHEN** an embedded plugin declares no tier
- **THEN** its effective tier is Core, and the start line shows `(core)` after its name

#### Scenario: Embedded plugin declaring Experimental

- **WHEN** an embedded plugin declares `EXPERIMENTAL`
- **THEN** its effective tier is Experimental

### Requirement: Embedded plugins follow the store and bundled rules for their id

A store plugin whose id an embedded plugin holds SHALL NOT be loaded, and a bundled plugin whose id an embedded plugin holds SHALL be
skipped with the existing debug line. An embedded plugin whose id is already registered by a built-in plugin SHALL stop the start
with the existing duplicate-id message.

#### Scenario: Store plugin with an embedded id

- **WHEN** the plugin store selects a plugin whose id an embedded plugin holds
- **THEN** the store plugin is not loaded and its entry reads as shadowed

#### Scenario: Bundled jar with an embedded id

- **WHEN** a bundled jar declares the id of an embedded plugin
- **THEN** the bundled jar is skipped and the embedded plugin is the one registered

### Requirement: Drop-in guarantee for installations without embedded jars

With no embedded jar in the server, the loader SHALL register the same plugins in the same order as before this change, SHALL write no
new file and SHALL log no new line. The plugin API SHALL stay binary compatible: `PluginTier`, the annotation `@Plugin` and the public
signatures of `Extensions` SHALL NOT change.

#### Scenario: Existing installation

- **WHEN** an installation with the existing plugins directory and no embedded jar starts
- **THEN** the registered plugins, their origins and the startup log are the same as before this change

#### Scenario: Plugin API dump

- **WHEN** the plugin ABI gate runs on this change
- **THEN** the dump of the plugin API is identical to the dump on `main`

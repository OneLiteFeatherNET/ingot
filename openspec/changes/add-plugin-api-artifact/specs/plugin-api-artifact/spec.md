# Spec Delta

## Purpose

A plugin author can compile a plugin against a published artifact that contains the plugin API and nothing else of the server. The
artifact is a second publication. The server bundle and every existing plugin stay as they are.

## ADDED Requirements

### Requirement: Separate plugin API artifact

The build SHALL publish a Maven artifact `net.onelitefeather.ingot:ingot-plugin-api` whose classes are exactly the classes named in
`reposilite-backend/plugin-api-classes.txt`. The artifact SHALL NOT contain any other class of the server.

#### Scenario: Contents match the allowlist

- **WHEN** the artifact is built from a clean checkout
- **THEN** every class in it is named in the allowlist, and every class named in the allowlist that exists in the server is in it

#### Scenario: Bundle unchanged

- **WHEN** the artifact is published
- **THEN** the coordinates and the contents of `net.onelitefeather.ingot:ingot` are the same as before the change, apart from the
  marker annotation `@ExperimentalIngotPluginApi` that this change adds to both

### Requirement: Closure of the artifact

The build SHALL fail when a public signature of a class in the artifact refers to a class that is neither in the artifact nor a JDK or
Kotlin standard library class. The failure SHALL name the referring class and the missing target.

#### Scenario: Missing reference

- **WHEN** an allowlisted class has a public method whose parameter type is a server class outside the allowlist
- **THEN** the closure check fails and names the method's class and the parameter type

#### Scenario: Reference in a private member

- **WHEN** an allowlisted class refers to a server class only in a private member
- **THEN** the closure check passes

### Requirement: Compilation without the server

A plugin SHALL compile against the artifact alone. The consumer fixture SHALL depend on the artifact as a file and on no project of the
monorepo.

#### Scenario: Consumer fixture

- **WHEN** the consumer fixture is built with only the artifact on its compile classpath
- **THEN** the build succeeds and the sample plugin class carries the `@Plugin` annotation at runtime

### Requirement: Experimental marker

The artifact SHALL contain the annotation `@ExperimentalIngotPluginApi` in `net.onelitefeather.ingot.plugin`, with opt-in level
`WARNING`, so that a plugin type marked with it can be changed without a major version.

#### Scenario: Use without opt-in

- **WHEN** a plugin uses a type marked with `@ExperimentalIngotPluginApi` without opting in
- **THEN** the compiler emits a warning that names the marker

### Requirement: Release only

The artifact SHALL be published with the release that carries the bundle and with the same version. It SHALL NOT be published with
nightly builds.

#### Scenario: Nightly build

- **WHEN** a nightly build is published
- **THEN** only the bundle is published

### Requirement: Drop-in for existing installations and plugins

Adding the artifact SHALL NOT change the configuration, the environment, the data directory or the container image of an
installation. Existing plugins that compile against the bundle SHALL keep compiling without any change.

#### Scenario: Existing plugin

- **WHEN** the example plugin is compiled against the bundle after the change
- **THEN** it compiles without any change to its build file

# Spec Delta

## Purpose

A reader can find Gradle plugins held in the repositories they may read, see their versions and description, and copy the snippets
that resolve them. The capability is delivered by an external plugin and changes nothing for an installation without it.

## ADDED Requirements

### Requirement: Opt-in by installation

The plugin SHALL register its pages and routes only when its jar is installed. An installation without the jar SHALL behave exactly
as before.

#### Scenario: No jar

- **WHEN** the plugin is not installed
- **THEN** no portal page, route or index attribute exists

### Requirement: Detection of Gradle plugins

The plugin SHALL recognise a Gradle plugin from a marker POM whose artifactId equals its groupId followed by `.gradle.plugin`, and
from a jar that contains `META-INF/gradle-plugins/<id>.properties`. The plugin id SHALL be the groupId of the marker, or the file
name without extension of the properties entry.

#### Scenario: Marker artifact

- **WHEN** `com.example.demo:com.example.demo.gradle.plugin:1.0.0` is stored as a POM
- **THEN** the plugin `com.example.demo` at version `1.0.0` appears in the listing

#### Scenario: Descriptor only

- **WHEN** a jar with `META-INF/gradle-plugins/org.acme.tool.properties` is stored and no marker exists
- **THEN** the plugin `org.acme.tool` appears, marked as detected from a descriptor

#### Scenario: Not a plugin

- **WHEN** an ordinary library jar without a descriptor is stored
- **THEN** it does not appear in the listing

### Requirement: Detection never blocks a write

A failure while reading a POM or a jar SHALL NOT fail the deployment or the indexing of the file. The plugin SHALL log a warning with
repository and path and SHALL skip the file.

#### Scenario: Damaged jar

- **WHEN** a deployed jar has a damaged central directory
- **THEN** the deployment succeeds and the plugin logs one warning

### Requirement: Rights-aware listing

The listing, the detail page and every route of the plugin SHALL show only plugins in repositories the viewer may read, evaluated for
the viewer's token or for the anonymous viewer.

#### Scenario: Private repository, anonymous viewer

- **WHEN** a plugin exists only in a private repository and an anonymous viewer opens the listing
- **THEN** the plugin is absent and its detail URL answers `404`

#### Scenario: Same plugin in two repositories

- **WHEN** the viewer may read one of two repositories that hold the plugin
- **THEN** only the readable repository and its versions are shown

### Requirement: Snippets

The detail page SHALL show the `plugins` block in Kotlin and Groovy DSL and a `pluginManagement` repositories block for the
repository the version lives in. The block SHALL contain credential placeholders if and only if the repository is private, and SHALL
never contain a real secret or the viewer's token.

#### Scenario: Public repository

- **WHEN** the plugin is in a public repository
- **THEN** the `pluginManagement` block has no `credentials` section

#### Scenario: Private repository

- **WHEN** the plugin is in a private repository
- **THEN** the block has a `credentials` section that reads property names, not values

#### Scenario: Hostile description

- **WHEN** a description contains markup or script
- **THEN** it is shown as text and never inside a snippet

### Requirement: Built only on the published plugin API

The plugin build SHALL depend only on published artifacts, with no project dependency on the Ingot monorepo, and its CI SHALL fail
if the jar uses a class outside the gated plugin API packages without a recorded exception.

#### Scenario: Clean checkout

- **WHEN** CI builds the plugin from a clean checkout
- **THEN** the build resolves the plugin API artifact from a repository and succeeds

### Requirement: Listed in the plugin catalogue

Each release SHALL be signed and published so that the plugin catalogue lists it with a `compatibility` block.

#### Scenario: Release

- **WHEN** a release of the plugin is created
- **THEN** the catalogue contains an entry whose digest matches the published jar

# Spec Delta

## Purpose

A reader can browse the Minecraft plugins and mods held in the repositories they may read, filter them by loader and game version,
see their versions and dependencies, and download them. The data comes from the descriptors inside the jars. The capability is
delivered by an external plugin and changes nothing for an installation without it.

## ADDED Requirements

### Requirement: Opt-in by installation

The plugin SHALL register its enricher, pages and routes only when its jar is installed. An installation without the jar SHALL behave
exactly as before.

#### Scenario: No jar

- **WHEN** the plugin is not installed
- **THEN** no portal page, route, attribute or extra work in the index pipeline exists

### Requirement: Supported descriptors

The plugin SHALL read `plugin.yml`, `paper-plugin.yml`, `bungee.yml`, `velocity-plugin.json`, `fabric.mod.json`, `quilt.mod.json`,
`META-INF/mods.toml`, `META-INF/neoforge.mods.toml` and `META-INF/sponge_plugins.json` from the root of a jar, and SHALL extract
name, version, authors, description, supported API or game versions, dependencies and loader.

#### Scenario: Paper plugin

- **WHEN** a jar with `paper-plugin.yml` naming `api-version: 1.21` is indexed
- **THEN** the project shows loader `paper` and game version `1.21`

#### Scenario: Multi loader mod

- **WHEN** a jar holds `fabric.mod.json` and `META-INF/neoforge.mods.toml`
- **THEN** both loaders are recorded and the project appears under either filter

#### Scenario: No descriptor

- **WHEN** a jar holds none of the descriptors
- **THEN** it is not part of the portal and no warning is logged

### Requirement: Extraction runs off the deploy path and never blocks it

Extraction SHALL run in the index pipeline, not in the deploy request, and for existing artifacts on reindex. A malformed
descriptor, a limit breach or a timeout SHALL NOT fail the deployment, the indexing of the file or the rebuild.

#### Scenario: Malformed YAML

- **WHEN** a deployed jar holds a `plugin.yml` that is not valid YAML
- **THEN** the deployment succeeds, the file is indexed without portal attributes and one warning names repository and path

#### Scenario: Reindex

- **WHEN** an index rebuild runs over jars stored before the plugin was installed
- **THEN** their portal attributes appear

### Requirement: Bounded reading of untrusted jars

The plugin SHALL enforce a maximum jar size, a maximum entry count, a maximum uncompressed size per descriptor, a maximum compression
ratio, a maximum total inflated size per jar and a per-jar deadline, and SHALL skip the file when any limit is exceeded. It SHALL NOT
write any jar entry to disk and SHALL NOT load any class from a jar.

#### Scenario: Zip bomb

- **WHEN** a descriptor entry inflates beyond its limit or the ratio limit
- **THEN** reading stops at the limit, the file is skipped and memory use stays bounded

#### Scenario: Too many entries

- **WHEN** a jar holds more entries than the limit
- **THEN** the file is skipped with a warning

#### Scenario: Deadline

- **WHEN** reading a jar exceeds its deadline
- **THEN** the work is aborted and the file is skipped

### Requirement: Safe parsing

YAML SHALL be parsed without constructing arbitrary types (no global tags), with limits on aliases, nesting and size. JSON and TOML
SHALL be parsed with depth, length and node limits. Extracted values SHALL be length bounded and stripped of control and
bidirectional override characters before storage.

#### Scenario: Type construction tag

- **WHEN** a `plugin.yml` contains a `!!` tag that names a class
- **THEN** parsing fails, nothing is constructed and the file is skipped

#### Scenario: Alias expansion

- **WHEN** a YAML document expands through many aliases
- **THEN** parsing fails at the alias limit

### Requirement: Rights-aware portal

The listing, facet counts, totals, detail pages and every route of the plugin SHALL include only artifacts in repositories the viewer
may read.

#### Scenario: Facet counts

- **WHEN** an anonymous viewer opens the portal and a private repository holds ten Fabric mods
- **THEN** the Fabric count does not include them

#### Scenario: Guessed URL

- **WHEN** a viewer opens the detail URL of an artifact in a repository they cannot read
- **THEN** the answer is `404`, the same as for an unknown artifact

### Requirement: Browsing

The portal SHALL show cards with name, description, loaders, game versions and authors, SHALL filter by loader, game version and
free text, and SHALL show on the detail page all readable versions with the Maven download path. Extracted text SHALL be shown as
text, never as markup, and the card SHALL show the Maven coordinates beside the descriptor name.

#### Scenario: Filter

- **WHEN** the viewer selects loader `paper` and game version `1.21`
- **THEN** only projects that declare both are listed, with counts

#### Scenario: Hostile description

- **WHEN** a descriptor description contains markup
- **THEN** it is shown as plain text

### Requirement: Built only on the published plugin API

The build SHALL depend only on published artifacts and CI SHALL fail if the jar uses a class outside the gated plugin API packages
without a recorded exception.

#### Scenario: Clean checkout

- **WHEN** CI builds the plugin from a clean checkout
- **THEN** the build resolves the plugin API artifact from a repository and succeeds

### Requirement: Listed in the plugin catalogue

Each release SHALL be signed and published so that the plugin catalogue lists it with a `compatibility` block.

#### Scenario: Release

- **WHEN** a release of the plugin is created
- **THEN** the catalogue contains an entry whose digest matches the published jar

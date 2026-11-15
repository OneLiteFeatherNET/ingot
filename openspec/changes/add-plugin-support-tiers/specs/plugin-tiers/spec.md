# Spec Delta

## Purpose

An operator can see, for every loaded plugin, which level of support it has and what that level is based on. The tier is
information and a warning. It never decides whether a plugin loads, and a plugin built for Reposilite keeps loading unchanged.

## ADDED Requirements

### Requirement: Optional tier on the plugin annotation

The `@Plugin` annotation SHALL have an element `tier` of the enum `com.reposilite.plugin.api.PluginTier` with the values `CORE`,
`OFFICIAL`, `COMMUNITY` and `EXPERIMENTAL`. Its default SHALL be `COMMUNITY`. A plugin compiled against a Reposilite release
without the element SHALL load unchanged and SHALL read as Community.

#### Scenario: Plugin compiled without the element

- **WHEN** a jar built against `com.reposilite:reposilite:3.5.28` is placed in the plugins directory
- **THEN** the plugin loads as it did before, and its effective tier is Community

#### Scenario: Element declared

- **WHEN** a plugin declares `tier = PluginTier.OFFICIAL` and is loaded from the plugins directory
- **THEN** its effective tier is Official

### Requirement: Effective tier follows the origin of the plugin

The loader SHALL compute one effective tier per plugin. A plugin built into the backend, or loaded from the bundled jars of the
image, SHALL be Core, except that a plugin that declares Experimental SHALL be Experimental. A plugin from any other origin SHALL
keep its declared tier. A plugin from any other origin that declares Core SHALL be Community and the start SHALL log a warning
that names the plugin.

#### Scenario: Bundled jar with a declared Official tier

- **WHEN** a jar of `/app/bundled-plugins` declares `OFFICIAL`
- **THEN** its effective tier is Core

#### Scenario: Bundled jar with a declared Experimental tier

- **WHEN** the `opensearch-plugin` jar of `/app/bundled-plugins` is loaded and declares `EXPERIMENTAL`
- **THEN** its effective tier is Experimental

#### Scenario: Core claimed from outside the image

- **WHEN** a jar of the plugins directory declares `CORE`
- **THEN** its effective tier is Community and the start logs a warning that names the plugin and says the plugin is not shipped in the image

### Requirement: A tier never blocks a plugin

No tier SHALL cause a plugin to be refused, skipped, unloaded, delayed or started in another order. No environment variable or
configuration key SHALL select plugins by tier. A warning about a tier SHALL be logged and the start SHALL go on.

#### Scenario: Experimental plugin selected

- **WHEN** an Experimental plugin is in the plugins directory and its switch is on
- **THEN** the plugin loads, the start logs one warning for it, and the server starts

#### Scenario: Community plugin in the store

- **WHEN** a Community plugin of the plugin store is selected
- **THEN** the plugin loads by the rules of `plugin-store`, unchanged by its tier

### Requirement: Experimental plugins warn once at start

For each plugin whose effective tier is Experimental the start SHALL log one WARN line after the list of loaded plugins. The line
SHALL name the plugin and SHALL say that its API may change or be removed without a major release. The line SHALL NOT be logged
per request.

#### Scenario: One warning per plugin

- **WHEN** two Experimental plugins load
- **THEN** the log contains exactly one warning line for each of them, and no other warning about tiers

#### Scenario: Stable plugins

- **WHEN** only Core, Official and Community plugins load
- **THEN** the log contains no tier warning

### Requirement: Loaded plugins list shows the tier

The startup log line that lists the loaded plugins SHALL show each name followed by its effective tier in lower case in brackets,
for example `checksum (official)`. The order and the chunks of the list SHALL NOT change.

#### Scenario: Mixed plugins

- **WHEN** the checksum plugin and the Elasticsearch plugin load
- **THEN** the list shows `checksum (official)` and `elasticsearch-search (experimental)`

### Requirement: Tier shown in the system information page and the support bundle

The system information page SHALL show the effective tier of each plugin next to its version. The support bundle file
`plugins.json` SHALL carry the same tier per plugin. Both are additive fields, and no existing field changes.

#### Scenario: Support bundle

- **WHEN** an administrator downloads the support bundle
- **THEN** `plugins.json` lists each plugin with `name`, `version`, `dependencies` and `tier`

### Requirement: Experimental requires an experimental opt-in

A plugin module SHALL declare `EXPERIMENTAL` if and only if it opts in to at least one `@ExperimentalIngot*` API. The tier check
SHALL fail a module that breaks this rule.

#### Scenario: Experimental opt-in without declaration

- **WHEN** a module opts in to `ExperimentalIngotStorageApi` and declares `COMMUNITY`
- **THEN** the tier check fails and names the module and the missing declaration

### Requirement: Official requires contract tests and a CI job

A plugin module SHALL declare `OFFICIAL` only if it has a `contractTest` source set or task, and a workflow of `.github/workflows`
runs the module by its Gradle path. The tier check SHALL fail a module that declares Official without both.

#### Scenario: Official without contract tests

- **WHEN** a module declares `OFFICIAL` and has no `contractTest` source set
- **THEN** the tier check fails and names the module and the missing contract tests

### Requirement: Core requires a bundled jar

A plugin module SHALL declare `CORE` only if its jar is one of the jars that the `Dockerfile` copies into `/app/bundled-plugins`. The
tier check SHALL fail a Core declaration without such a jar, and a bundled jar that declares neither Core nor Experimental.

#### Scenario: Core declaration without a bundled jar

- **WHEN** a module declares `CORE` and the `Dockerfile` does not copy its jar
- **THEN** the tier check fails and names the module

### Requirement: Community needs no evidence

A plugin module that declares `COMMUNITY` and opts in to no experimental API SHALL pass the tier check with no further evidence,
beyond the plugin ABI check that already runs.

#### Scenario: Community without evidence

- **WHEN** a module declares `COMMUNITY` and opts in to no experimental API
- **THEN** the tier check passes

### Requirement: Tier check runs in CI and in the release

A CI job named `Plugin tiers` SHALL run the tier check on every pull request to `main`, and it SHALL be blocking. The check SHALL write
`build/plugin-tiers.json` with the declared and effective tier of each module. The release job SHALL run the check before the
catalogue generator reads that file.

#### Scenario: Check fails on a pull request

- **WHEN** a pull request declares a tier without its evidence
- **THEN** the job `Plugin tiers` fails and the pull request cannot merge

### Requirement: Declarations of the repository

The plugin modules of `reposilite-plugins/` SHALL declare the tiers of the table in `design.md` (D8). The built-in plugins of the
backend SHALL need no declaration, because their origin makes them Core.

#### Scenario: Pending plugin changes

- **WHEN** an open plugin change opts in to an experimental API
- **THEN** the change declares `EXPERIMENTAL` in the commit that adds its registration, before its pull request is merged

### Requirement: Tier documentation

The GitBook SHALL define the four tiers and the support each one receives on the page `references/plugins/plugin-tiers.md`, and every
plugin page SHALL show its tier with a link to that page. The tiers SHALL NOT be shown with an emoji.

#### Scenario: Reader opens a plugin page

- **WHEN** a reader opens the page of a plugin with a tier
- **THEN** the page shows `Tier: <name>` and links to the tier definitions

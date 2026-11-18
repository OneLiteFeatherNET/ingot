## ADDED Requirements

### Requirement: Jar version comes from the manifest

The version of a plugin jar SHALL be the value of the main manifest attribute `Implementation-Version` of the jar that declares the
plugin. The version element of `@Plugin` SHALL NOT decide which copy is used.

#### Scenario: Manifest version read

- **WHEN** a jar of the plugins directory has `Implementation-Version: 1.1.0` in its manifest
- **THEN** the loader reads the version of that jar as `1.1.0`

#### Scenario: Annotation ignored for the rule

- **WHEN** a jar's `@Plugin` version is `3.5.28` and its manifest version is `1.0.0`
- **THEN** the rule compares `1.0.0`

### Requirement: Version grammar and order

A version SHALL be one to three decimal numbers separated by dots, followed by an optional pre-release suffix that starts with a
hyphen, and an optional build suffix that starts with a plus sign and is ignored. Numbers SHALL compare numerically, a missing number
SHALL count as zero, and a release SHALL be higher than a pre-release of the same numbers. Two pre-releases of the same numbers SHALL
be equal.

#### Scenario: Numeric order

- **WHEN** the versions `1.2.10` and `1.2.9` are compared
- **THEN** `1.2.10` is higher

#### Scenario: Missing number

- **WHEN** the versions `1.2` and `1.2.0` are compared
- **THEN** they are equal

#### Scenario: Pre-release

- **WHEN** the versions `1.0.0-SNAPSHOT` and `1.0.0` are compared
- **THEN** `1.0.0` is higher

### Requirement: Missing or invalid version counts as lowest

A missing, blank or unparseable version SHALL count as lower than every valid version, and it SHALL NOT stop the start.

#### Scenario: Plugins directory jar without version

- **WHEN** a plugins-directory jar with the id of an embedded plugin has no `Implementation-Version`
- **THEN** the embedded copy is used

#### Scenario: Unparseable version

- **WHEN** a plugins-directory jar declares the version `latest`
- **THEN** the embedded copy is used and the start goes on

### Requirement: A strictly higher plugins directory jar replaces the embedded copy

For a plugin id held by an embedded jar, the loader SHALL use the plugins-directory copy only when its version is strictly higher than the
embedded version. Otherwise it SHALL use the embedded copy and SHALL NOT register the plugins-directory copy. The rule SHALL apply
only to ids that an embedded jar holds.

#### Scenario: Higher version replaces

- **WHEN** the embedded `javadoc` is `1.0.0` and the plugins directory holds `javadoc` at `1.1.0`
- **THEN** the plugins-directory copy is registered with origin `PLUGINS_DIRECTORY` and the embedded copy is not registered

#### Scenario: Equal version keeps the embedded copy

- **WHEN** the embedded `javadoc` is `1.0.0` and the plugins directory holds `javadoc` at `1.0.0`
- **THEN** the embedded copy is registered with origin `EMBEDDED`

#### Scenario: Lower version keeps the embedded copy

- **WHEN** the embedded `javadoc` is `1.0.0` and the plugins directory holds `javadoc` at `0.9.0`
- **THEN** the embedded copy is registered and the plugins-directory copy is not registered

#### Scenario: Other ids untouched

- **WHEN** the plugins directory holds a plugin whose id no embedded jar holds
- **THEN** the plugin loads as it did before this change

### Requirement: One INFO line for each replacement decision

When a plugins-directory jar declares an id that an embedded jar holds, the loader SHALL log exactly one INFO line in the form
`Plugin <id>: embedded version <v>, plugins directory version <v>, the <plugins directory|embedded> copy is used`. A missing version
SHALL show `none` and an invalid version SHALL show its raw string in quotes. No other line SHALL be logged for that decision.

#### Scenario: Higher version line

- **WHEN** the plugins directory holds `javadoc` at `1.1.0` and the embedded version is `1.0.0`
- **THEN** exactly one INFO line reads `Plugin javadoc: embedded version 1.0.0, plugins directory version 1.1.0, the plugins directory copy is used`

#### Scenario: Equal version line

- **WHEN** the plugins directory holds `javadoc` at `1.0.0` and the embedded version is `1.0.0`
- **THEN** exactly one INFO line names both versions and says the embedded copy is used

#### Scenario: No plugins directory copy

- **WHEN** no plugins-directory jar declares the id of an embedded plugin
- **THEN** the loader logs no line about the replacement decision

### Requirement: Two plugins directory jars with one id still stop the start

When two jars of the plugins directory declare the same plugin id, the start SHALL stop with the message `Plugin <id> is already
registered`, whether or not an embedded jar holds that id.

#### Scenario: Two directory copies with an embedded copy

- **WHEN** the embedded `javadoc` is `1.0.0` and two plugins-directory jars declare `javadoc`
- **THEN** the start stops with the message `Plugin javadoc is already registered`

### Requirement: Replacing copy reports its own declared tier

A plugins-directory copy that replaces an embedded copy SHALL keep the origin `PLUGINS_DIRECTORY`, so its effective tier is its
declared tier, with a declared Core shown as Community.

#### Scenario: Replacing copy declares Official

- **WHEN** a replacing plugins-directory copy declares `OFFICIAL`
- **THEN** its effective tier is Official

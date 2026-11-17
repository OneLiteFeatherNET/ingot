## ADDED Requirements

### Requirement: The viewer is a module with its own version and jar

The Javadoc viewer SHALL be built from the Gradle module `reposilite-plugins/javadoc-plugin` with the packages `com.reposilite.javadocs` and the plugin id `javadoc`. The module SHALL have its own version, independent of the server version, and SHALL build the jar `javadoc-plugin.jar` with its version in the manifest attribute `Implementation-Version`.

#### Scenario: Own version

- **WHEN** the server version changes and the viewer does not
- **THEN** the jar of the viewer keeps its own version, and the server version does not change it

#### Scenario: Manifest version

- **WHEN** `javadoc-plugin.jar` is built
- **THEN** its manifest names the module version in `Implementation-Version`

### Requirement: The server jar embeds a copy of the viewer

The server jar SHALL carry the jar of the viewer as the nested resource `embedded-plugins/javadoc-plugin.jar`, and the loader SHALL load that copy as a plugin in its own class loader. The classes of the viewer SHALL NOT be on the server classpath.

#### Scenario: Server started outside the image

- **WHEN** the server jar is started with no plugins directory and no image directories
- **THEN** the viewer is active and its routes answer as before

#### Scenario: No class on the server classpath

- **WHEN** the classes of the server jar are listed
- **THEN** no class of `com.reposilite.javadocs` is among them

### Requirement: A newer viewer jar in the plugins directory replaces the embedded copy

A jar of the plugins directory that holds the plugin id `javadoc` SHALL replace the embedded copy only when its manifest version is strictly higher than the embedded version. Otherwise the embedded copy SHALL be used. The start SHALL log one INFO line that names both versions and the one in use, and SHALL NOT register two plugins with the id `javadoc`.

#### Scenario: Newer jar

- **WHEN** the plugins directory holds `javadoc-plugin.jar` with version `1.1.0` and the embedded version is `1.0.0`
- **THEN** the plugins-directory jar is used, and the log names both versions

#### Scenario: Same or older jar

- **WHEN** the plugins directory holds a jar with version `1.0.0`, or a jar with no version, and the embedded version is `1.0.0`
- **THEN** the embedded copy is used, and the log names both versions

### Requirement: The default does not change

The viewer SHALL be active by default. The settings domain `javadoc` SHALL keep its key, its fields `enabled` and `suffixes`, and its defaults, and the routes `/javadoc/{repository}/<gav>` and `/javadoc/{repository}/<gav>/raw/<resource>` SHALL keep their paths and responses. The image SHALL keep its file list, and no released jar of the viewer SHALL be copied into `/app/bundled-plugins`.

#### Scenario: Existing installation

- **WHEN** an existing installation starts the new server with its unchanged configuration and data directory
- **THEN** the javadoc routes answer as before, the stored `javadoc` settings are read unchanged, and the cache directory `javadocs` is used as before

#### Scenario: Disabled by the setting

- **WHEN** `javadoc.enabled` is `false`
- **THEN** both javadoc routes answer `404`, the placeholder of the suffixes is empty and the file browser shows no Javadoc action

### Requirement: The core does not depend on the viewer

No class of the core packages SHALL depend on a class of `com.reposilite.javadocs`. The core SHALL reach the viewer only through the public interface `JavadocCacheReceiver` of the cluster package, which the viewer implements. An architecture test SHALL enforce this rule.

#### Scenario: Import added to the core

- **WHEN** a core class imports a class of `com.reposilite.javadocs`
- **THEN** the architecture test fails and names the importing class

### Requirement: Without the viewer the Javadoc links are hidden

When the server sends no value for the placeholder `{{REPOSILITE.JAVADOC_SUFFIXES}}`, the dashboard SHALL treat the suffixes as empty and SHALL NOT show a Javadoc action in the file browser. The dashboard SHALL NOT fall back to a default list of suffixes.

#### Scenario: Built without the viewer

- **WHEN** a server answers without the placeholder and a browser lists an artifact `demo-1.0-javadoc.jar`
- **THEN** the row shows no Javadoc action and no link to `/javadoc/` is rendered

#### Scenario: Placeholder sent

- **WHEN** the server sends the placeholder with `-javadoc.jar`
- **THEN** the row of `demo-1.0-javadoc.jar` shows the Javadoc action as before

### Requirement: The settings page keeps its place and its address

The `javadoc` settings domain SHALL stay in the Administration tree under Artifacts, with its label and description as before. Its address `/settings/javadoc` SHALL stay the same. When the domain is not served, the address SHALL redirect to `/settings`.

#### Scenario: Deep link with the viewer

- **WHEN** a full administrator opens `/settings/javadoc` on a server with the viewer
- **THEN** the page of the Javadoc domain opens under the heading Artifacts, with the same fields as before

#### Scenario: Deep link without the viewer

- **WHEN** a full administrator opens `/settings/javadoc` on a server without the viewer
- **THEN** the browser lands on `/settings` and the Javadoc entry is not listed

### Requirement: Cluster events keep their names and payload

The viewer SHALL emit `JavadocInvalidatedEvent` after its local eviction, and the cluster plugin SHALL publish it as `javadoc.invalidated` with the fields `repository` and `path`. A received event SHALL reach the viewer through `JavadocCacheReceiver`. Without the viewer, a received event SHALL be ignored and logged at debug level.

#### Scenario: Deploy on one replica

- **WHEN** a javadoc jar is deployed and a cluster bus is configured
- **THEN** the event `javadoc.invalidated` carries the repository and the artifact path, as before

#### Scenario: Event without the viewer

- **WHEN** a replica without the viewer receives `javadoc.invalidated`
- **THEN** the replica deletes nothing, logs the event at debug level and keeps running

### Requirement: The drop-in contract holds for every image and deployment

Moving the viewer SHALL NOT require an operator to change an environment variable, a configuration file, a volume, a manifest, a plugin or the image reference. The container images job SHALL pass on the pull request that changes the build ignore file, including the upgrade rehearsal against the upstream image.

#### Scenario: Image line swapped only

- **WHEN** an unmodified upstream compose file is started with the Ingot image as its only change
- **THEN** the server starts, the viewer is active by default, and the existing data directory is adopted

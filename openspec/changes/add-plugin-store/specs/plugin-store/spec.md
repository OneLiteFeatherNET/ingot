# Spec Delta

## Purpose

An instance can install plugins from a catalogue in a Maven repository. Nothing is downloaded unless a source or a selection is
configured, a jar from the store loads only when its signature and digest verify, and a failure of the store never stops the server
from starting.

## ADDED Requirements

### Requirement: Defaults are unchanged

With no store variable set and no shared `plugins` selection, the server SHALL NOT open a network connection for the store, SHALL NOT
create the plugin cache directory, and SHALL load the plugins directory and the bundled jars exactly as before.

#### Scenario: Fresh installation

- **WHEN** the server starts with none of `INGOT_PLUGINS`, `INGOT_PLUGIN_SOURCES`, `INGOT_PLUGIN_TRUSTED_KEYS` and `INGOT_PLUGIN_ALLOW_UNSIGNED` set, and the shared settings have no `plugins` selection
- **THEN** no store request is made, no `plugin-cache` directory is created, and the set of loaded plugins equals the set without this change

### Requirement: Local selection

The server SHALL read the local selection from `INGOT_PLUGINS` (fallback `REPOSILITE_PLUGINS`) and from the local key `plugins`, both
as a comma-separated list of `groupId:artifactId:version`. An entry that does not match that form SHALL refuse the whole local list at
start, and the message SHALL name the variable and the entry.

#### Scenario: Malformed entry

- **WHEN** `INGOT_PLUGINS` contains `opensearch-plugin` without a group and a version
- **THEN** the start reports the variable and the entry, and no store plugin is loaded from the list

#### Scenario: Legacy spelling

- **WHEN** only `REPOSILITE_PLUGINS` is set to a valid list
- **THEN** the list is used as if `INGOT_PLUGINS` had the same value

### Requirement: Shared selection in the dashboard

The shared settings domain `plugins` SHALL hold the selection of an instance administrator as a list of coordinates. The effective set
SHALL be the union of the local and the shared selection. A coordinate that is set locally SHALL NOT be removable in the dashboard.

#### Scenario: Local entry protected

- **WHEN** the local list contains `net.onelitefeather.ingot:opensearch-plugin:1.0.0` and an administrator tries to remove it in the dashboard
- **THEN** the entry stays in the effective set and the dashboard shows it as set in configuration

### Requirement: Restart applies a change

A change of the effective set SHALL take effect only at the next start of a replica. The store SHALL NOT load or unload a plugin while the
server runs. Each replica SHALL report a pending restart while its loaded set differs from the effective set.

#### Scenario: Selection added while running

- **WHEN** an administrator adds a plugin to the shared selection of a running replica
- **THEN** the plugin is not loaded, the replica reports a pending restart, and the plugin loads after the next start

### Requirement: Catalogue in a Maven repository

A source SHALL be a base URL of a Maven repository served over `https`. The catalogue SHALL be the file `ingot-plugin-catalogue.json`
at the source root with its detached signature `ingot-plugin-catalogue.json.asc`. Its `schema` SHALL be `1`. A catalogue with another
schema, or an entry whose `id` differs from the loaded plugin, SHALL be refused.

#### Scenario: Unknown schema

- **WHEN** a source serves a catalogue with `schema` 2
- **THEN** the catalogue is refused with a reason that names both numbers, and no entry of that source is selectable

#### Scenario: Plain HTTP source

- **WHEN** `INGOT_PLUGIN_SOURCES` contains an `http://` URL
- **THEN** the source is refused at start with a message that names the variable

### Requirement: Mandatory signatures

A jar from the store SHALL load only when the catalogue and the jar both verify against a key listed in `INGOT_PLUGIN_TRUSTED_KEYS`,
and when the SHA-256 of the jar equals the `sha256` of its catalogue entry. A source with no trusted key SHALL NOT be used.

#### Scenario: Digest mismatch

- **WHEN** the downloaded jar has a SHA-256 different from the catalogue entry
- **THEN** the jar is not loaded, the file is deleted, and the status names the digest step

#### Scenario: Signature from an untrusted key

- **WHEN** the jar's signature verifies only against a key that is not in the trusted keys
- **THEN** the jar is not loaded and the status names the signature step

#### Scenario: No trusted key

- **WHEN** a source is configured and `INGOT_PLUGIN_TRUSTED_KEYS` is unset
- **THEN** the source is not used and the status says that no trusted key is configured

### Requirement: Unsigned content only by explicit opt-in

Unsigned catalogues and jars SHALL load only when `INGOT_PLUGIN_ALLOW_UNSIGNED` is `true`. While the switch is on, the server SHALL log a
warning at every start and the status SHALL mark each such plugin as `UNVERIFIED`.

#### Scenario: Switch off

- **WHEN** a jar has no signature and the switch is unset or `false`
- **THEN** the jar is not loaded

#### Scenario: Switch on

- **WHEN** a jar has no signature and the switch is `true`
- **THEN** the jar loads, the status shows `UNVERIFIED`, and a warning is logged at start

### Requirement: Cache outside the plugins directory

Verified jars SHALL be stored under `<data>/plugin-cache` with their signature and a record of the digest, the key fingerprint and the
verification time. The store SHALL NOT write into the plugins directory. Writes SHALL be atomic.

#### Scenario: Downloaded jar location

- **WHEN** a store plugin is verified
- **THEN** its files are under the plugin cache and the plugins directory contains no new file

### Requirement: Offline start with a verified cache

When a source cannot be reached, a cached jar SHALL load if its local SHA-256 equals its record, its signature verifies against a
currently trusted key, and its compatibility holds. The store SHALL NOT require network access for that check.

#### Scenario: Source down

- **WHEN** the source is unreachable and the cache holds a verified jar for a selected coordinate
- **THEN** the plugin loads from the cache and the status shows the source as unreachable

#### Scenario: Cached jar altered

- **WHEN** the cached jar no longer matches its record
- **THEN** the cached jar is not loaded and the entry is reported as `REFUSED`

### Requirement: Startup never stops for the store

A store entry that cannot be obtained, verified, loaded or initialised SHALL be skipped or marked `FAILED`, with its reason and the step
name in the status, and the server SHALL start. The store SHALL NOT retry in a loop at startup, and the startup budget for the whole store
SHALL be 60 seconds.

#### Scenario: Download hangs

- **WHEN** a source does not answer within the startup budget
- **THEN** the entries that were not finished are `UNAVAILABLE` and the server starts

#### Scenario: Plugin initialise throws

- **WHEN** a store plugin throws in `initialize`
- **THEN** the plugin is `FAILED` in the status, the server starts, and the status notes that routes registered before the exception remain until the next restart

#### Scenario: Recovery switch

- **WHEN** `INGOT_PLUGIN_STORE_DISABLED` is `true`
- **THEN** the store loads nothing and makes no request for that start

### Requirement: Precedence for one plugin id

For one plugin id, a jar in the plugins directory SHALL win over a store plugin, and a store plugin SHALL win over a bundled jar. A
losing entry SHALL be reported as `SHADOWED` with the id of the winning source.

#### Scenario: Manual jar and store plugin share an id

- **WHEN** a jar in the plugins directory and a selected store plugin declare the same id
- **THEN** the store plugin is not loaded and the status shows it as `SHADOWED` by the manual jar

#### Scenario: Store plugin replaces the bundled jar

- **WHEN** the store selects a plugin whose id matches a bundled jar that is switched on
- **THEN** the store plugin loads and the bundled jar is `SHADOWED`

### Requirement: Compatibility

A catalogue entry SHALL declare `compatibility.ingot` with `min` inclusive and `below` exclusive, and `compatibility.pluginApi`. An entry
outside the range or with another `pluginApi` SHALL NOT be selectable and SHALL NOT load. An entry without a `compatibility` block SHALL be
refused.

#### Scenario: Running version outside the range

- **WHEN** the running Ingot version is not below `below`
- **THEN** the dashboard disables the selection of that entry and the status names the range

#### Scenario: Entry without compatibility

- **WHEN** a catalogue entry has no `compatibility` block
- **THEN** the entry is refused and the reason says that compatibility is missing

### Requirement: Yanked entries

An entry with `yanked: true` SHALL NOT be selectable. A yanked jar already in the cache SHALL still load, and the status SHALL show it as
`YANKED`.

#### Scenario: Yank after selection

- **WHEN** a selected plugin is yanked in the catalogue and its jar is cached
- **THEN** the plugin still loads and the status shows `YANKED`

### Requirement: Source order

When two sources list the same plugin id, the first source in `INGOT_PLUGIN_SOURCES` SHALL provide the entry. The other entry SHALL be
reported as shadowed by the name of the first source.

#### Scenario: Duplicate id across sources

- **WHEN** two sources list `opensearch-plugin`
- **THEN** the entry of the first source is used and the second is reported as shadowed

### Requirement: Permissions, audit and read-only mode

The store routes and the `plugins` domain SHALL require the full instance administrator. A changed selection, a refused download, a failed
verification, an unverified load, a skipped plugin and a refresh SHALL each write an audit entry. In read-only mode the selection SHALL NOT
change and the refresh SHALL be refused, while startup loading SHALL continue.

#### Scenario: Non-administrator

- **WHEN** a user without the full administrator node calls `GET /api/plugin-store/status`
- **THEN** the request is refused with the access problem body

#### Scenario: Read-only mode

- **WHEN** the instance is read-only and an administrator calls `POST /api/plugin-store/refresh`
- **THEN** the request is refused with the read-only problem body and the cache is unchanged

### Requirement: Replica reporting

Each replica SHALL record the effective set it loaded. The status SHALL name every replica whose recorded set differs from another recent
replica, and SHALL mark a replica whose record is older than three minutes as stale. A difference SHALL NOT block a selection change.

#### Scenario: Replicas differ

- **WHEN** two replicas have loaded different sets
- **THEN** the status names both replicas and the dashboard shows a warning, and the selection can still be changed

### Requirement: Routes and OpenAPI

The store SHALL expose `GET /api/plugin-store/status`, `GET /api/plugin-store/catalogue` and `POST /api/plugin-store/refresh`, all documented
in the OpenAPI description. The selection SHALL be read and written through `GET` and `PUT /api/settings/domain/plugins`. The change SHALL
add paths and schemas only and SHALL NOT change an existing route.

#### Scenario: Existing routes

- **WHEN** the OpenAPI diff of this change is computed against `main`
- **THEN** it reports additions only

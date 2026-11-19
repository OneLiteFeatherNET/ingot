## ADDED Requirements

### Requirement: Served document merges the fragments of loaded plugins

When the OpenAPI document is built, the server SHALL read `META-INF/ingot/openapi/<plugin id>.json` through the class loader of each
registered plugin's class, and SHALL add the entries of the fragment's `paths` and `components.schemas` that the document does not hold
yet. Plugins SHALL be merged in ascending order of their id. The merge SHALL run before the existing definition processors.

#### Scenario: Plugin routes reach the document

- **WHEN** a loaded plugin with id `fixture` ships a fragment with the path `/api/fixture/items` and the schema `FixtureItem`
- **THEN** the served document holds `paths./api/fixture/items` and `components.schemas.FixtureItem` with the fragment's content

#### Scenario: Plugin without a fragment

- **WHEN** a loaded plugin ships no file under `META-INF/ingot/openapi/`
- **THEN** the served document holds no entry from that plugin and no line about it is logged

#### Scenario: Order does not depend on load order

- **WHEN** plugins `alpha` and `beta` ship fragments and are registered in either order
- **THEN** the served document is the same in both cases

### Requirement: Server entries win over plugin entries

A key of `paths` or `components.schemas` that the document already holds SHALL NOT be replaced by a fragment. The server's entries SHALL
win over every plugin, and a plugin with a lower id SHALL win over one with a higher id. Each kept key SHALL log exactly one WARN line
that names the plugin and the key.

#### Scenario: Plugin repeats a server path

- **WHEN** a plugin fragment holds the path `/api/status/instance`, which the server documents
- **THEN** the served entry is the server's and one WARN line names the plugin and `/api/status/instance`

#### Scenario: Two plugins document one schema

- **WHEN** plugins `alpha` and `beta` both document the schema `Shared`
- **THEN** the served schema is the one of `alpha` and one WARN line names `beta` and `Shared`

### Requirement: Broken fragment never fails the server

A fragment that does not parse as a JSON object, or whose `paths` or `components.schemas` is not an object, SHALL be skipped whole with
one WARN line that names the plugin. Neither the start nor the request to `/openapi` SHALL fail because of a fragment.

#### Scenario: Fragment is not JSON

- **WHEN** a plugin ships a fragment with the content `not json`
- **THEN** `/openapi` answers 200 with the document of the other sources, and one WARN line names the plugin

#### Scenario: Paths is an array

- **WHEN** a plugin ships a fragment whose `paths` is a JSON array
- **THEN** no entry of that fragment is added, including its schemas, and one WARN line names the plugin

### Requirement: Document is unchanged without plugin fragments

With no loaded plugin shipping a fragment, the served document SHALL be byte-identical to the document served before this change, and
the committed snapshot `docs/openapi/ingot-openapi.json` SHALL stay the full merged document.

#### Scenario: Snapshot holds

- **WHEN** `OpenApiSnapshotIntegrationTest` runs without `-PrefreshOpenApiSnapshot`
- **THEN** it passes against the unchanged snapshot

#### Scenario: Merge step is a no-op

- **WHEN** the merge receives the server document and no fragment
- **THEN** it returns the same document object, with no copy and no change

### Requirement: Settings schema is read from the plugin's own jar

For a settings domain whose type is not listed in the server's `json-schemes/index` and whose class loader is not the server's, the
server SHALL read `json-schemes/<type name>` through the type's class loader before it falls back to the runtime generator. Only when
that file is absent SHALL it log the warning `Cannot find scheme for ...` and generate the schema at runtime. A type on the server's
class loader SHALL keep the existing lookup unchanged.

#### Scenario: Plugin ships its schema

- **WHEN** a plugin jar holds the domain type `FixtureSettings` and the file `json-schemes/<fully qualified name of FixtureSettings>`
- **THEN** the domain is served that file's schema and no `Cannot find scheme` line is logged

#### Scenario: Plugin ships no schema

- **WHEN** a plugin jar holds a domain type and no matching file under `json-schemes/`
- **THEN** one `Cannot find scheme` WARN line is logged and the schema comes from the runtime generator, as before

#### Scenario: Server type keeps the runtime path in tests

- **WHEN** a domain type on the server's class loader is missing from the server's index
- **THEN** the plugin lookup is not tried and the runtime generator is used, as before

### Requirement: Plugin module build ships the fragment and no index

A plugin module that applies the build step SHALL produce a jar that holds `META-INF/ingot/openapi/<plugin id>.json` when the module
documents a route, the files `json-schemes/<class name>` of its `@JsonSchema` classes, and neither `openapi-plugin/.index` nor
`json-schemes/index`. The check task SHALL fail the build when the jar holds either index file or a fragment under another id.

#### Scenario: Index files are left out

- **WHEN** a plugin module with an `@OpenApi` route and a `@JsonSchema` settings class is built with the step
- **THEN** its jar holds the fragment and the schema file, and holds no `openapi-plugin/.index` and no `json-schemes/index`

#### Scenario: Index file present

- **WHEN** the check task reads a jar that holds `json-schemes/index`
- **THEN** the task fails and its message names the entry

#### Scenario: Fragment under the wrong id

- **WHEN** the check task for plugin id `cleanup` reads a jar that holds `META-INF/ingot/openapi/other.json`
- **THEN** the task fails and its message names the entry and the expected id

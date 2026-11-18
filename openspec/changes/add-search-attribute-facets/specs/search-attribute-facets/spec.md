# Spec Delta

## Purpose

A client can narrow the file results of the search by plugin attributes, sort them by one declared attribute, and receive counts per
value of declared attributes. The counts are correct under multi-select and under the access rights of the viewer. Requests that do not
use the parameters are answered as before.

## ADDED Requirements

### Requirement: Declared attributes only

An enricher SHALL declare every attribute key that a query may filter, facet or sort on, with the flags filterable, facetable and
sortable. A key that is not declared with the matching flag SHALL be refused, and the refusal SHALL NOT be an empty result.

#### Scenario: Undeclared key

- **WHEN** a request filters on `gradle-portal.plugin-id` and no enricher declares that key
- **THEN** the response is `400` with the problem type `undeclared-attribute` naming the key

#### Scenario: Declared for facets only

- **WHEN** a request filters on a key declared with `facetable` but not `filterable`
- **THEN** the response is `400` with the problem type `undeclared-attribute`

### Requirement: Query semantics

Values of one attribute key SHALL be combined with OR and different keys with AND. A request SHALL carry at most four distinct keys, 16
values per key, and eight facet keys.

#### Scenario: Two values of one key

- **WHEN** a request filters `minecraft-portal.loader` to `paper` and `folia`
- **THEN** files with either loader are returned

#### Scenario: Too many keys

- **WHEN** a request filters on five distinct keys
- **THEN** the response is `400` and no backend is queried

### Requirement: Sort on a declared key

A request MAY sort by one key declared as sortable. Files without the value SHALL sort last. Ties SHALL be broken by the existing order of
groups and versions, so that paging is stable.

#### Scenario: Missing values

- **WHEN** a sort on `minecraft-portal.name` is requested and one file has no name
- **THEN** that file is the last of the result

### Requirement: Counting rule

The count of each value of a facet key SHALL be taken over the rows that match all attribute filters except the filters on that key,
and over the rows the viewer may read. A selected value SHALL keep showing its alternatives.

#### Scenario: Multi-select

- **WHEN** a request selects loader `paper` and asks for facets on loader and on game version
- **THEN** the loader facet shows the counts of every loader among rows matching the game version filter, and the game version facet shows
  the counts among rows matching `paper`

#### Scenario: Unreadable rows

- **WHEN** an anonymous viewer requests a facet and a private repository holds matching rows
- **THEN** the counts do not include those rows

### Requirement: Capability refusals

A backend that does not declare the capability needed by a feature of a request SHALL cause the request to be refused before the backend is
queried. The refusal SHALL be `400` with the problem type `unsupported-search-capability` and the name of the capability.

#### Scenario: Backend without facets

- **WHEN** a request asks for attribute facets and the configured backend does not declare `ATTRIBUTE_FACETS`
- **THEN** the response is `400` with the problem type `unsupported-search-capability` naming `ATTRIBUTE_FACETS`

### Requirement: Pending settings

A backend that must apply a settings change before it can answer a filter SHALL answer `503` with `Retry-After` while the change is
pending, and SHALL NOT answer with a partial result.

#### Scenario: Meilisearch settings pending

- **WHEN** a declared filterable key has been added and its settings update is not finished
- **THEN** a filter on that key answers `503` with `Retry-After`

### Requirement: Compatibility

A request without the attribute parameters SHALL be answered as before, and the response SHALL NOT gain a field unless the request asks for
attribute facets.

#### Scenario: Plain search

- **WHEN** a search runs without attribute parameters
- **THEN** the response has the same fields as before the change

### Requirement: Drop-in for existing installations

The change SHALL NOT add a configuration key, an environment variable, a volume, a manifest or an image requirement. Its database
behaviour SHALL use the table and index created by `add-search-document-attributes`.

#### Scenario: Existing installation

- **WHEN** an existing installation is upgraded with the change and no enricher is installed
- **THEN** no attribute filter is accepted for undeclared keys and every existing search answers as before

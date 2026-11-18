# Spec Delta

## Purpose

An index row can carry a bounded, namespaced set of plugin attributes. The attributes are written by enrichers, stored by the backends
that declare the capability, kept consistent when files change, and returned only on request. An installation without enrichers has no
attributes and behaves as before.

## ADDED Requirements

### Requirement: Namespaced, bounded attributes

An index row SHALL carry attributes whose keys are `<enricher-id>.<name>`, whose values are strings, and whose size is bounded to at most
32 keys, 16 values per key, 2000 characters per value and 8 KiB of key and value text in total. The core SHALL refuse an attribute set
that exceeds a bound and SHALL NOT truncate it.

#### Scenario: Set over the limit

- **WHEN** an enricher writes 33 keys for one file
- **THEN** no attribute of that set is stored, the enrichment counts as failed with the rule `too-many-keys`, and the row is written
  without attributes from that enricher

#### Scenario: Value over the limit

- **WHEN** an enricher writes a value of 2001 characters
- **THEN** the set is refused with the rule `value-too-long`, and the message does not contain the value

### Requirement: Rejected control characters and spoofing characters

Values SHALL NOT contain a control character or a bidirectional override or isolate character. The core SHALL refuse such a value and SHALL
NOT clean it.

#### Scenario: Bidirectional override

- **WHEN** a value contains U+202E
- **THEN** the set is refused with the rule `control-character`

### Requirement: Namespace ownership

An enricher SHALL write only keys of its own namespace. The namespace `core` SHALL be reserved for keys the core writes.

#### Scenario: Foreign key

- **WHEN** an enricher with id `gradle-portal` writes the key `minecraft-portal.loader`
- **THEN** the set is refused with the rule `foreign-namespace`

#### Scenario: Version key

- **WHEN** an enricher runs for a file
- **THEN** the row carries `core.enricher.<id>` with the version of that enricher, and no enricher can write a `core` key

### Requirement: Write rules

A changed file SHALL replace the attributes of the enrichers that ran with the results of that call. A failed or skipped enricher SHALL
remove its own attributes and its version key from the row. A touch, a reorigin, or a rebuild that keeps an unchanged row SHALL keep the
stored attributes. A removal of a row SHALL remove its attributes with it.

#### Scenario: Failed enricher after a new version

- **WHEN** a jar is replaced and the enricher fails on the new jar
- **THEN** the attributes of the earlier version are not in the row afterwards

#### Scenario: Touch

- **WHEN** a pending row is touched during a rebuild
- **THEN** its attributes are unchanged

#### Scenario: Removal

- **WHEN** a file is deleted
- **THEN** the row and its attributes are gone from the index

### Requirement: Rebuild by enricher version

A rebuild SHALL enrich a row whose file did not change when a matching enricher has no stored version for it or a different one, and
SHALL NOT enrich it when every matching enricher has the stored version.

#### Scenario: Upgraded enricher

- **WHEN** an enricher's version changes from `1` to `2` and a rebuild runs over unchanged files
- **THEN** each matching row is enriched once with trigger `REBUILD`

#### Scenario: Unchanged enricher

- **WHEN** no enricher changed and a rebuild runs over unchanged files
- **THEN** no row is enriched

### Requirement: Backend capability

A backend SHALL declare the capability `ATTRIBUTES` to receive attributes. A backend that does not declare it SHALL receive no attributes,
and the core SHALL count the enrichment as skipped with the reason `unsupported-backend`. The database backend of the core SHALL declare
the capability.

#### Scenario: Backend without the capability

- **WHEN** an enricher writes attributes and the configured backend does not declare `ATTRIBUTES`
- **THEN** the row is written without attributes, the status counts a skip with reason `unsupported-backend`, and no request fails

### Requirement: Storage per backend

The database backend SHALL store attributes in a child table written in the same transaction as the row and removed with it. The
Elasticsearch and OpenSearch backends SHALL store them under `attributes.*` with keyword fields. The Meilisearch backend SHALL store them
as a document object. A backend that stores attributes SHALL keep them consistent with the rules of the write requirement.

#### Scenario: Database row removed

- **WHEN** a row with three attributes is removed
- **THEN** no attribute row of that file remains in the database

### Requirement: Attributes in the response only on request

The file results of the search API SHALL include an `attributes` object only when the request carries `attributes=true`. The attributes
SHALL be returned only for rows that the caller may read.

#### Scenario: Default request

- **WHEN** a search runs without the parameter
- **THEN** the response has the same fields as before the change

#### Scenario: Request with the parameter for a private repository

- **WHEN** an anonymous viewer requests attributes and a matching row is in a private repository
- **THEN** the row is absent from the result, and its attributes are absent with it

### Requirement: Drop-in for existing installations

The database schema change SHALL be additive and created at start. No configuration key, environment variable, volume, manifest or image
SHALL change. Rows that exist before the change SHALL have no attributes until they are enriched.

#### Scenario: Existing data directory

- **WHEN** an existing data directory is started with the change
- **THEN** the server starts, the existing rows are unchanged and the new table is empty

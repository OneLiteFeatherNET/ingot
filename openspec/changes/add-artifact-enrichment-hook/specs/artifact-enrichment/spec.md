# Spec Delta

## Purpose

A plugin can see the files of a repository as they enter the search index, on deployment, on proxied download and on rebuild, and can
read a bounded part of their bytes. An installation without such a plugin behaves as before.

## ADDED Requirements

### Requirement: Opt-in by registration

The search plugin SHALL call an enricher only after the enricher has been registered through the registration event. With no enricher
registered, the index pipeline SHALL NOT read file content on behalf of enrichers and SHALL write the same rows as before the change.

#### Scenario: No enricher

- **WHEN** no enricher is registered and a jar is deployed
- **THEN** the row is written as before and no storage range read is made for enrichment

#### Scenario: Refused registration

- **WHEN** an enricher declares an `apiVersion` other than the current one
- **THEN** it is never called and a warning names the version and the class

### Requirement: Invocation on every entry of a file into the index

The search plugin SHALL call each registered enricher whose declared extensions match a file on a deployment, on a proxied file stored
from an upstream, and on a rebuild of a changed row. The call SHALL happen before the row is written, and the request SHALL carry the
trigger and the provenance.

#### Scenario: Deployment

- **WHEN** a `.jar` is deployed and an enricher declares `jar`
- **THEN** the enricher is called once with trigger `CHANGED` and provenance `HOSTED`

#### Scenario: Proxied file

- **WHEN** a `.pom` is fetched from an upstream and stored by a mirror repository, and an enricher declares `pom`
- **THEN** the enricher is called once with trigger `CHANGED` and provenance `MIRROR`

#### Scenario: Rebuild of a changed row

- **WHEN** a rebuild indexes a file whose size or modification time changed
- **THEN** the enricher is called once with trigger `REBUILD`

#### Scenario: Rebuild of an unchanged row

- **WHEN** a rebuild indexes a file whose size and modification time are unchanged
- **THEN** the enricher is not called

### Requirement: Bounded content access

The content handle SHALL read only the file of the call, SHALL serve ranges through the storage, and SHALL refuse a read that would
exceed the byte budget of the call. The budget and the deadline SHALL be enforced by the core, not by the enricher.

#### Scenario: Budget exceeded

- **WHEN** an enricher reads more bytes than the budget of the call allows
- **THEN** the read fails, the call is recorded as failed and the row is still written

#### Scenario: Read of another path

- **WHEN** an enricher is handed a request for one path
- **THEN** the content handle cannot name any other path

### Requirement: Failures never change what a client sees

An exception, a deadline expiry, a full executor or a refusal of a call SHALL NOT fail the deployment, the proxied download, the rebuild
page or the indexing of the file. The row SHALL be written without enrichment output, and each failure SHALL be counted and logged once
with the enricher id, the repository and the path, but without any content.

#### Scenario: Enricher throws

- **WHEN** an enricher throws an exception with a message that contains file content
- **THEN** the row is written, the counter `failed` for that enricher increases, and the warning does not contain the message

#### Scenario: Deadline

- **WHEN** an enricher does not return before the deadline
- **THEN** the call is cancelled, the result is `Failed` with reason `timeout`, and the outbox continues

#### Scenario: Executor full

- **WHEN** the executor queue is full
- **THEN** the enricher is not called, the result is `Skipped` with reason `busy`, and the row is written

### Requirement: Status of enrichers

The index status response SHALL carry an additive block with the counters `calls`, `completed`, `skipped`, `failed` and `busy` for each
registered enricher, and the number of failures per reason. No existing field of the response SHALL change.

#### Scenario: Status after failures

- **WHEN** an enricher failed twice and completed once
- **THEN** the status shows `calls` 3, `completed` 1, `failed` 2 for that enricher id

### Requirement: Trust of enricher output

Enricher output SHALL be treated as data. The core SHALL NOT render it, and it SHALL reach a viewer only through the access checks of the
search layer.

#### Scenario: Enricher reads a private repository

- **WHEN** an enricher reads a file of a private repository
- **THEN** the bytes are read as the system and the output is not visible to a viewer without the search access of that repository

### Requirement: Experimental API

Every public type of the hook SHALL be marked `@ExperimentalIngotSearchApi`. The types SHALL live in `net.onelitefeather.ingot.search.enrichment`
and SHALL NOT add a member to any type of `com.reposilite.*`.

#### Scenario: Use without opt-in

- **WHEN** a plugin uses an enricher type without opting in
- **THEN** the compiler emits a warning that names the marker

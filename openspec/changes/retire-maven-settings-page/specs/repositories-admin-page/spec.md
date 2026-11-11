# Spec Delta

## Purpose

The Repositories page is the one place to edit the `maven` settings domain in the dashboard. Its old Maven address
redirects to it, and the domain-level options of the domain are edited in a section of the page, so the page no longer
needs a Maven page of its own.

## MODIFIED Requirements

### Requirement: Old addresses keep working

The address `/settings/maven`, with or without a query, SHALL redirect to `/settings/repositories` with the query kept. The address
`/settings/maven/repositories/<id>` SHALL redirect to `/settings/repositories/<id>`, and `/settings/maven/repositories` with no entry
SHALL redirect to `/settings/repositories`. Other collections of other domains SHALL NOT redirect.

#### Scenario: Bookmarked entry

- **WHEN** a visitor opens `/settings/maven/repositories/releases`
- **THEN** the address becomes `/settings/repositories/releases` and the detail of `releases` is shown

#### Scenario: Bookmarked Maven page

- **WHEN** a visitor opens `/settings/maven` with `?new=repository`
- **THEN** the address becomes `/settings/repositories?new=repository` and the wizard opens once

#### Scenario: Maven page with no entry

- **WHEN** a visitor opens `/settings/maven` or `/settings/maven/repositories`
- **THEN** the address becomes `/settings/repositories` and the table of repositories is shown

#### Scenario: Cleanup policy entry

- **WHEN** a visitor opens an entry of the `cleanup` domain
- **THEN** the address is unchanged

## ADDED Requirements

### Requirement: Maven options section

The Repositories page SHALL show the domain-level fields of the `maven` domain, the ones that are not collections, in a section
headed "Maven options" below the table. The fields SHALL be generated from the settings schema the instance serves. The section
SHALL edit the same draft as the table, SHALL be saved with it, and SHALL NOT change any repository of the domain.

#### Scenario: Option edited and saved

- **WHEN** an administrator changes "Resolution cache maximum age in cluster mode" in the Maven options section and selects Save
- **THEN** the save writes the `maven` domain with the new value and the same repositories

#### Scenario: Option edit keeps the repositories

- **WHEN** an administrator changes a domain-level field and has not saved
- **THEN** every repository of the table keeps its values, and the save bar counts one change

#### Scenario: Schema without domain-level fields

- **WHEN** the served schema of the `maven` domain has no property that is not a collection
- **THEN** the Maven options section is not shown and the table is the whole page

#### Scenario: Visitor without the node

- **WHEN** a signed in visitor without the settings node opens `/settings/repositories`
- **THEN** the page shows the no access message and the Maven options section is not rendered

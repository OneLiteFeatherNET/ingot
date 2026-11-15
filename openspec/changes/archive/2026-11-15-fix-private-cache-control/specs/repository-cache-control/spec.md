# Spec Delta

## Purpose

A shared cache stores a file answer only when the repository the file belongs to is open to everyone, so that an
access-controlled artifact is not handed out to a caller who was never allowed to read it.

## ADDED Requirements

### Requirement: Cache-Control follows the visibility of the repository

The server SHALL answer a repository file with `Cache-Control: public, max-age=<maxAge>` only when the repository is PUBLIC
and every local repository it reaches is PUBLIC. Otherwise the server SHALL answer `Cache-Control: private, max-age=<maxAge>`,
whether the request is anonymous or authorized.

#### Scenario: Public repository, anonymous download

- **WHEN** an anonymous client requests a file from a PUBLIC repository that may be cached
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

#### Scenario: Private repository, authorized download

- **WHEN** a client with read access requests a file from a PRIVATE repository that may be cached
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>` and no `public` directive

#### Scenario: Hidden repository, download by exact path

- **WHEN** an anonymous client requests a file from a HIDDEN repository by its exact path
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Private repository, anonymous request

- **WHEN** an anonymous client requests a file from a PRIVATE repository
- **THEN** the response is `401` with `Cache-Control: no-cache, no-store, max-age=0`

#### Scenario: Range and conditional answers

- **WHEN** a client requests a range of a file from a PRIVATE repository, or revalidates it with `If-None-Match`
- **THEN** the `206` or `304` response carries the same `Cache-Control` value as the `200` response for that file

### Requirement: Groups answer with the scope of their members

A group or mixed repository SHALL take the scope of every local repository that it reaches through a loopback reference,
directly or through another group, and SHALL ignore remote references. A group whose local members are all PUBLIC SHALL
answer `public`. A group that reaches a non-PUBLIC local repository SHALL answer `private`.

#### Scenario: Public group with a private member

- **WHEN** an anonymous client requests a file through a PUBLIC group whose member is a PRIVATE repository and the group's mirror identity can read the file
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Public group with public members

- **WHEN** an anonymous client requests a file through a PUBLIC group whose members are all PUBLIC repositories
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

#### Scenario: Nested group

- **WHEN** a PUBLIC group references a PUBLIC group that references a HIDDEN repository, and a file is requested through the outer group
- **THEN** the response carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Remote references only

- **WHEN** a PUBLIC proxy repository references only remote hosts and a file is requested through it
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

### Requirement: Answers that are not cacheable keep their value

The server SHALL keep `Cache-Control: no-cache, no-store, max-age=0` for every answer that is not cacheable, whatever the
visibility of the repository. Repositories that allow redeployment and `maven-metadata.xml` are such answers.

#### Scenario: Metadata in a public repository

- **WHEN** an anonymous client requests `maven-metadata.xml` from a PUBLIC repository
- **THEN** the response carries `Cache-Control: no-cache, no-store, max-age=0`

#### Scenario: Redeployable private repository

- **WHEN** an authorized client requests a file from a PRIVATE repository that allows redeployment
- **THEN** the response carries `Cache-Control: no-cache, no-store, max-age=0`

### Requirement: Escape hatch restores the public value

The server SHALL read `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC` from the environment, and SHALL read `REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC`
when the first one is not set. The value `true` SHALL make the server answer `public` for the answers that the rules above make
`private`. The value `false`, or no value, SHALL keep the rules above. Any other value SHALL stop the start-up with a message
that names the variable.

#### Scenario: Switch enabled

- **WHEN** the server starts with `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` and an authorized client requests a file from a PRIVATE repository
- **THEN** the response carries `Cache-Control: public, max-age=<maxAge>`

#### Scenario: Legacy spelling

- **WHEN** the server starts with `REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true` and no `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`
- **THEN** the switch is on

#### Scenario: Default

- **WHEN** the server starts with neither variable set
- **THEN** an authorized request for a file from a PRIVATE repository carries `Cache-Control: private, max-age=<maxAge>`

#### Scenario: Unknown value

- **WHEN** the server starts with `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=yes`
- **THEN** the start-up stops and the message names `INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC`

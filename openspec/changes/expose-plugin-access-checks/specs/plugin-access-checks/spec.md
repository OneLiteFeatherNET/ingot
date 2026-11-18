# Spec Delta

## Purpose

A plugin can ask the core whether a viewer may read or list a repository or a path, and obtain the search access value of a viewer, without
copying any rule. The answers are the answers of the routes. The public surface reveals no token, no authorization type and no existence
of a repository the viewer may not read.

## ADDED Requirements

### Requirement: Answers equal the routes

Every answer of the viewer facade SHALL equal the answer of the route function for the same viewer and input. The facade SHALL NOT contain a
rule of its own.

#### Scenario: Hidden repository, anonymous viewer

- **WHEN** an anonymous viewer asks whether it can list a `HIDDEN` repository
- **THEN** the answer is `false`, the same as the listing route

#### Scenario: Private repository, token with the read right

- **WHEN** a token with the read right on a `PRIVATE` repository asks whether it can read a path in it
- **THEN** the answer is `true`, the same as the read route

### Requirement: Canonical paths only

A path SHALL be judged only in its canonical form. A path that is not canonical SHALL be answered with `false`, and a canonical path SHALL be
judged as the route judges it.

#### Scenario: Encoded slash

- **WHEN** a plugin asks about a path that contains an encoded slash
- **THEN** the answer is `false`

#### Scenario: Dot segment

- **WHEN** a plugin asks about a path with a `..` segment
- **THEN** the answer is `false`

### Requirement: No existence leak

An unknown repository, a repository the viewer may not read and a repository that does not exist SHALL give the same answer, `false`.

#### Scenario: Unknown and unreadable

- **WHEN** a viewer asks about an unknown repository and about a private repository it may not read
- **THEN** both answers are `false`

### Requirement: Search access value

The viewer SHALL provide the search access value that the core computes for the same subject and visibilities, so that a query made with it
returns only rows the viewer may read.

#### Scenario: Search access

- **WHEN** a plugin passes the search access value of a viewer to a search
- **THEN** no row of a repository the viewer may not read is returned and no count includes such a row

### Requirement: Public surface

The public types of `net.onelitefeather.ingot.access` SHALL NOT expose the authorization chain, its authorizers or decisions, the token
identifier as a constructible value, token secrets or hashes, or a constructor of the viewer. The viewer SHALL NOT reveal the token in its
string form.

#### Scenario: Surface test

- **WHEN** the public members of the package are listed by the surface test
- **THEN** none of them has a type of the authorization package or a token secret

### Requirement: Experimental and drop-in

The types SHALL be marked `@ExperimentalIngotPluginApi`. The change SHALL NOT alter any route, status code, rule, configuration key, environment
variable or deployment manifest.

#### Scenario: Existing installation

- **WHEN** an installation without the plugin is upgraded with the change
- **THEN** every route answers as before and no new configuration is required

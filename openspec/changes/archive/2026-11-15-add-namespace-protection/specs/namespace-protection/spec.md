# Spec Delta

## Purpose

An operator can declare group id prefixes that a group repository serves from hosted members only, so that a public proxy can
never answer for coordinates the operator owns. The setting is empty by default and then changes nothing.

## ADDED Requirements

### Requirement: Protected prefixes setting

A repository SHALL accept an optional list `protectedGroupPrefixes` of dotted group ids, defaulting to an empty list. The
key SHALL be accepted only when the effective type of the repository is `GROUP`, and each prefix SHALL consist of segments of
letters, digits, `_` and `-` joined by single dots, without duplicates.

#### Scenario: Default is empty

- **WHEN** a repository settings file has no `protectedGroupPrefixes`
- **THEN** the repository loads with an empty list and resolves exactly as before

#### Scenario: Key refused on another type

- **WHEN** a `PROXY`, `HOSTED` or `MIXED` repository sets a non-empty `protectedGroupPrefixes`
- **THEN** validation fails and the message names `protectedGroupPrefixes` and the repository

#### Scenario: Malformed prefix refused

- **WHEN** a prefix is blank, starts or ends with a dot, contains a slash or repeats another prefix
- **THEN** validation fails and the message names the prefix

### Requirement: Segment matching

A coordinate SHALL be protected when the leading path segments of its location equal the segments of a prefix. Matching SHALL be
by whole segments and case sensitive, and a location shorter than the prefix SHALL NOT be protected.

#### Scenario: Prefix covers its subgroups

- **WHEN** the prefix is `net.onelitefeather` and the request is `net/onelitefeather/ingot/1.0/ingot-1.0.jar`
- **THEN** the coordinate is protected

#### Scenario: Look-alike group is not covered

- **WHEN** the prefix is `net.onelitefeather` and the request is `net/onelitefeatherx/lib/1.0/lib-1.0.jar`
- **THEN** the coordinate is not protected and resolves as before

### Requirement: Hosted members only

For a protected coordinate the group SHALL ask hosted members only and SHALL NOT contact any proxy member. A member is hosted when
its effective type is `HOSTED`, or when it is a group whose members are all hosted. A member of type `PROXY` or `MIXED`, or a
group with a proxy member at any depth, SHALL be skipped.

#### Scenario: Hosted member answers

- **WHEN** a protected coordinate is held by a hosted member and a proxy member also exists
- **THEN** the hosted copy is served and the proxy is not contacted

#### Scenario: Proxy is never asked

- **WHEN** no hosted member holds a protected coordinate and a proxy member could have it
- **THEN** the proxy receives no request and the answer is 404

#### Scenario: Nested group with a proxy is skipped

- **WHEN** a member group contains a proxy member
- **THEN** that member group is skipped for protected coordinates

### Requirement: Refusal answers like a miss

When a proxy member was skipped and no hosted member holds a protected coordinate, the answer SHALL be the ordinary 404 of an
unresolved coordinate, with the same body as any other miss. The request SHALL write one audit event `NAMESPACE_PROXY_REFUSED`
and SHALL increase `ingot_namespace_refusals_total` for the repository.

#### Scenario: Same answer as a plain miss

- **WHEN** the refused 404 is compared with the 404 of an unprotected missing coordinate
- **THEN** status and body are equal

#### Scenario: Event written once

- **WHEN** one refused request is made
- **THEN** one `NAMESPACE_PROXY_REFUSED` event exists with `success` false, the repository, the path and the skipped member names

#### Scenario: Served request writes nothing

- **WHEN** a hosted member serves a protected coordinate
- **THEN** no event is written and the counter does not move

### Requirement: Off means unchanged

With an empty list, the group SHALL resolve exactly as it did before this change, for hosted and proxy members alike.

#### Scenario: Empty list keeps proxy answers

- **WHEN** `protectedGroupPrefixes` is empty and only a proxy member holds the coordinate
- **THEN** the proxy copy is served

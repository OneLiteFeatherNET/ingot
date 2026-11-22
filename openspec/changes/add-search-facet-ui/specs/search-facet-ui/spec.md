# Spec Delta

## Purpose

A visitor narrows file search results by declared plugin attributes from the dashboard and sees the counts the API computes.

## ADDED Requirements

### Requirement: Attribute filter groups

The search view SHALL show one filter group for each key in `facets.attributes`, with the count beside each value. Instances without
declared attributes SHALL see the view unchanged.

#### Scenario: Two groups

- **WHEN** the response carries buckets for `minecraft-portal.loader` and `minecraft-portal.game-version`
- **THEN** the view shows two groups, each value with its count

#### Scenario: No declarations

- **WHEN** the response has no `facets.attributes`
- **THEN** the view shows the same groups as before this change

### Requirement: Choice is part of the address

A chosen attribute value SHALL be written to the page address, and opening that address SHALL show the same choice.

#### Scenario: Shared link

- **WHEN** a visitor opens an address with `attribute=minecraft-portal.loader:paper`
- **THEN** the value `paper` is chosen and only matching files are listed

### Requirement: A chosen value stays visible

A chosen value SHALL stay in its group, with count zero when the response no longer lists it.

#### Scenario: Zero count

- **WHEN** `paper` is chosen and the buckets omit it
- **THEN** the group still shows `paper`, chosen, with count `0`

### Requirement: Refusal falls back to plain search

When the API refuses the attribute parameters, the view SHALL drop them and show the plain search without an error banner.

#### Scenario: Missing capability

- **WHEN** the API answers `unsupported-search-capability`
- **THEN** the view repeats the request without attribute parameters and shows no attribute group

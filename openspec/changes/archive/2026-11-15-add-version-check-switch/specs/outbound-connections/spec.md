# Spec Delta

## Purpose

An operator or auditor reads one page that lists every connection Ingot opens and how to turn each one off.

## ADDED Requirements

### Requirement: Page lists every outbound connection

`docs/gitbook/reference/outbound-connections.md` SHALL list the version check, mirror and proxy repositories, OIDC and GitHub login, LDAP, S3, the database and the cluster bus, each with its trigger, its default and how to turn it off.

#### Scenario: Auditor looks for the version check

- **WHEN** a reader opens the page
- **THEN** the version check row names the endpoint, the hourly limit, that it is on by default, and both the variable and the property that turn it off

### Requirement: Page states what does not exist

The page SHALL state that Ingot has no telemetry, usage reporting, analytics or licence call, and that a fresh install makes exactly one outbound call.

#### Scenario: Fresh install

- **WHEN** an instance starts with the default repositories, filesystem storage and SQLite, and no provider, LDAP or bus
- **THEN** the only outbound call it can make is the version check

### Requirement: Page names hosts for an egress allow list

The page SHALL name the host of the default version check endpoint and say that every other host is one the operator configured.

#### Scenario: Network team builds an allow list

- **WHEN** a network engineer reads the page
- **THEN** the engineer finds `api.github.com` for the version check and no other fixed host

### Requirement: Page is reachable from the documentation

The page SHALL be listed in `docs/gitbook/SUMMARY.md` and linked from the environment variable reference and from the status how-to.

#### Scenario: Reader follows the status how-to

- **WHEN** a reader of the status how-to wants every outbound call
- **THEN** a link leads to the page

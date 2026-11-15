# Spec Delta

## Purpose

Error bodies tell the caller what happened in general terms and never reveal the internals of the server.

## ADDED Requirements

### Requirement: Generic messages for internal failures

Responses for failures in storage, S3, the filesystem, settings parsing and the remote client SHALL carry a fixed message for
the status. They SHALL NOT contain the exception message or the exception class name. The status code SHALL NOT change.

#### Scenario: Filesystem failure

- **WHEN** reading a stored entry fails with an exception whose message contains an absolute path
- **THEN** the body carries the generic message and does not contain the path or the class name, and the status is the one returned before

#### Scenario: Malformed settings body

- **WHEN** a settings update is sent with a body Jackson cannot parse
- **THEN** the `400` body does not contain the parser message or any field name of the settings classes

#### Scenario: Remote failure

- **WHEN** a proxy fetch fails with a connection exception
- **THEN** the body does not contain the exception class name

### Requirement: Detail stays available to the operator

The exception behind a generic message SHALL be logged, and SHALL be recorded through the existing failure facade.

#### Scenario: Logged detail

- **WHEN** a storage failure produces a generic body
- **THEN** a captured log appender holds the original exception message

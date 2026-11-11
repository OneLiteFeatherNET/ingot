# Spec Delta

## Purpose

A reporter, an operator and an auditor find the response targets, the disclosure window, the CVE process and a machine
readable contact in the repository, without any runtime change.

## ADDED Requirements

### Requirement: Response targets are stated

The security policy SHALL state that a report is acknowledged within 14 days and SHALL state a coordinated disclosure window
of 90 days, extended only by agreement with the reporter.

#### Scenario: Reporter checks the response time

- **WHEN** a reporter reads the reporting and disclosure sections of `.github/SECURITY.md`
- **THEN** the policy names the 14-day acknowledgement and the 90-day window

### Requirement: Supported versions state only the current fact

The `Supported Versions` section SHALL state that security fixes are made on the latest minor release line and on `main`.
It SHALL NOT promise support for any older line and SHALL NOT describe an Ingot release as unreleased.

#### Scenario: Operator asks about an older line

- **WHEN** an operator looks for support of a release line older than the latest
- **THEN** the section says that no published statement covers that line yet

### Requirement: CVE handling is stated

The security policy SHALL state that a CVE is requested through the GitHub Security Advisory opened for the report.

#### Scenario: Reporter asks for a CVE

- **WHEN** a reporter asks how a vulnerability receives a CVE identifier
- **THEN** the policy names the GitHub Security Advisory as the path

### Requirement: security.txt follows RFC 9116

The repository SHALL contain `.well-known/security.txt` with exactly one Contact and exactly one Expires field, SHALL set
Policy to the URL of the security policy, and SHALL set Expires to a date no more than twelve months after the file last
changed.

#### Scenario: Scanner parses the file

- **WHEN** a tool parses `.well-known/security.txt`
- **THEN** Contact and Expires are present and Expires is a date in the future

### Requirement: Ingot does not serve security.txt

This change SHALL NOT add a route to the Ingot server that answers `/.well-known/security.txt`, so an operator installation
behaves as before.

#### Scenario: Operator installation after the change

- **WHEN** an operator runs Ingot from this release
- **THEN** the server answers `/.well-known/security.txt` exactly as it did before

### Requirement: Security Insights agrees with the policy

Once `SECURITY-INSIGHTS.yml` exists, it SHALL name the same advisory channel and the same response and disclosure values as
`.github/SECURITY.md`.

#### Scenario: Two files name the reporting channel

- **WHEN** a reader compares `SECURITY-INSIGHTS.yml` with `.github/SECURITY.md`
- **THEN** both name the advisory form and the same 14-day and 90-day values

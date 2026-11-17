# Spec Delta

## Purpose

A reporter, a contributor, an adopter or an auditor finds a working contact, the people who decide, and the relation to upstream Reposilite in the repository itself, without any runtime change.

## ADDED Requirements

### Requirement: Security policy names a private contact

The security policy SHALL name one private contact address in addition to the GitHub private advisory form, and SHALL NOT contain a `TODO(onelitefeather)` marker.

#### Scenario: Reporter reads the policy

- **WHEN** a reporter opens `.github/SECURITY.md`
- **THEN** the policy shows a private contact address and the advisory form link, and no unfinished placeholder

### Requirement: Code of conduct names a reporting address

The code of conduct SHALL name an address for confidential reports and SHALL NOT contain a `TODO(onelitefeather)` marker.

#### Scenario: Report of a violation

- **WHEN** a person reads the reporting section of `.github/CODE_OF_CONDUCT.md`
- **THEN** the section shows one address for confidential reports

### Requirement: Contact addresses agree across files

The security contact address SHALL be the same string in `.github/SECURITY.md`, in the security contacts of `SECURITY-INSIGHTS.yml`, and in the code of conduct when the code of conduct uses the same mailbox.

#### Scenario: Address changed in one file only

- **WHEN** the security contact address is changed in `.github/SECURITY.md`
- **THEN** the same change is made in `SECURITY-INSIGHTS.yml` in the same pull request

### Requirement: Maintainers are listed with their role

`MAINTAINERS.md` SHALL list every current maintainer with a GitHub handle and a role, SHALL say how to reach them, and SHALL link `.github/MAINTAINING.md` for the fork workflow instead of repeating it.

#### Scenario: New maintainer

- **WHEN** a person becomes a maintainer
- **THEN** a pull request adds their entry to `MAINTAINERS.md`

### Requirement: Governance states decisions and the upstream relation

`GOVERNANCE.md` SHALL describe how decisions are taken, how maintainers are added and removed, and that Ingot is an independent product not affiliated with or endorsed by upstream Reposilite. It SHALL refer to `VERSIONING.md` for the version line and to `.github/MAINTAINING.md` for the upstream sync.

#### Scenario: Upstream release question

- **WHEN** a reader asks whether an upstream Reposilite release is part of Ingot
- **THEN** `GOVERNANCE.md` answers that Ingot has its own version line and that upstream changes arrive through the cherry-pick policy

### Requirement: Adopters are listed by pull request

`ADOPTERS.md` SHALL list OneLiteFeather as the first adopter and SHALL accept further organisations through a pull request that adds one entry.

#### Scenario: Organisation adds itself

- **WHEN** an organisation runs Ingot in production and wants to be listed
- **THEN** a pull request adds one entry to `ADOPTERS.md` with the organisation name and an optional link

### Requirement: Security Insights file is valid and current

`SECURITY-INSIGHTS.yml` at the repository root SHALL be valid YAML, SHALL carry the project header, the license, the contribution policy, the security contacts and the vulnerability reporting channel, and SHALL state the date of its last review.

#### Scenario: Machine reads the file

- **WHEN** a tool parses `SECURITY-INSIGHTS.yml` with a YAML parser
- **THEN** the parse succeeds and the security contact and the reporting channel are present

### Requirement: Trust files change no runtime behaviour

The trust files SHALL be documentation only. The change SHALL NOT alter code, configuration keys, environment variables, the container image or the Helm chart.

#### Scenario: Drop-in check

- **WHEN** the change is reviewed against the drop-in contract
- **THEN** the changed paths are limited to `.github/` and the new files at the repository root, and an existing installation needs no change

# Spec Delta

## Purpose

The Administration tree no longer lists the Maven settings domain. Its repositories are one entry, Repositories, in the Repository
group, and the old Maven address opens that page.

## MODIFIED Requirements

### Requirement: Administration tree

Administration SHALL group its pages as Repository (Repositories, Cleanup policies, Javadoc), Security (Users, Machine accounts,
Teams, SSO, LDAP, Security settings, Permission check, Audit log), System (every other settings domain, a plugin domain included)
and Support (Console, Maintenance, Analytics). The `maven` domain SHALL NOT be listed, because it is edited on Repositories. A page
SHALL be listed only when it exists and the visitor may open it. A group without a listed page SHALL be left out.

#### Scenario: Plugin domain

- **WHEN** a plugin adds a settings domain
- **THEN** it is listed under System at `/settings/<domain>`

#### Scenario: Maven domain

- **WHEN** a full administrator opens the Administration tree
- **THEN** the Repository group lists Repositories, Cleanup policies and Javadoc, and no entry names the Maven settings

#### Scenario: Limited role

- **WHEN** a visitor may open only Console and Maintenance
- **THEN** only the group Support is listed

#### Scenario: Single page

- **WHEN** exactly one Administration page is permitted
- **THEN** `/settings` forwards to that page

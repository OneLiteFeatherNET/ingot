# Spec Delta

## Purpose

The dashboard navigation is grouped by what an operator does: Browse, Search, Dashboard and Administration. Administration
holds Repository, Security, System and Support. Every address that works today keeps working. Where this spec and the
`dashboard-navigation` spec of `restructure-navigation` differ on groups and labels, this spec governs.

## ADDED Requirements

### Requirement: Rail and bottom bar entries

The rail and the bottom bar SHALL show Browse, Search, Dashboard and Administration, each only when the visitor may open it.
Browse SHALL keep the path `/` and the route name `overview`. Administration SHALL open `/settings` and SHALL be shown when
the visitor may open any Administration page. The bar SHALL need no More item.

#### Scenario: Full administrator

- **WHEN** an instance administrator opens the dashboard on a narrow screen
- **THEN** the bar shows Browse, Search, Dashboard and Administration

#### Scenario: Audit node only

- **WHEN** a visitor has only the audit node
- **THEN** the navigation shows Browse, Search and Administration, and Administration opens the audit log

#### Scenario: Anonymous visitor

- **WHEN** a visitor is not signed in
- **THEN** the navigation shows Browse and Search only

### Requirement: Administration tree

Administration SHALL group its pages as Repository (Repositories, Cleanup policies, Maven settings, Javadoc), Security
(Users, Machine accounts, Teams, SSO, LDAP, Security settings, Permission check, Audit log), System (every other settings
domain, a plugin domain included) and Support (Console, Maintenance, Analytics). A page SHALL be listed only when it
exists and the visitor may open it. A group without a listed page SHALL be left out.

#### Scenario: Plugin domain

- **WHEN** a plugin adds a settings domain
- **THEN** it is listed under System at `/settings/<domain>`

#### Scenario: Limited role

- **WHEN** a visitor may open only Console and Maintenance
- **THEN** only the group Support is listed

#### Scenario: Single page

- **WHEN** exactly one Administration page is permitted
- **THEN** `/settings` forwards to that page

### Requirement: One tree on every Administration page

The side navigation of Access, Settings, Audit, Console, Maintenance and Analytics pages SHALL show the same tree with the
current page marked by `aria-current="page"`. Below 600 CSS pixels the tree SHALL be an index with a back arrow on each page.

#### Scenario: Access page

- **WHEN** a visitor opens `/access/teams`
- **THEN** the tree lists all four groups and marks Teams as current

#### Scenario: Narrow screen

- **WHEN** a visitor opens `/settings` below 600 pixels
- **THEN** the tree is shown as an index list

### Requirement: Addresses unchanged

Every route path and route name of the dashboard before this change SHALL still resolve, with its permission check
unchanged. No top level path segment SHALL be added. New pages SHALL live under `/settings`, `/access` or `/browse`.
The page names under `/settings` SHALL be reserved against settings domain ids.

#### Scenario: Old audit address

- **WHEN** a visitor with the audit node opens `/audit`
- **THEN** the audit log opens at the same address, listed under Security

#### Scenario: Old maintenance address

- **WHEN** a visitor opens `/maintenance` and `/console` and `/analytics`
- **THEN** each page opens at its address, listed under Support

#### Scenario: No new top level segment

- **WHEN** the dashboard segment list is compared with the one before this change
- **THEN** the lists are equal

### Requirement: Accessible tree

The tree SHALL be a `nav` landmark named "Administration". Each group SHALL be a heading followed by a list of links.
Browse, Search, Dashboard and Administration in the rail SHALL be a `nav` landmark with its own name. Focus SHALL be
reachable in reading order, and route changes SHALL keep the focus behaviour of the dashboard.

#### Scenario: Landmarks

- **WHEN** a screen reader lists the landmarks of an Administration page
- **THEN** the main navigation, the Administration navigation and the main content are listed with distinct names

#### Scenario: Group headings

- **WHEN** the tree is read in browse mode
- **THEN** each of Repository, Security, System and Support is a heading above its list

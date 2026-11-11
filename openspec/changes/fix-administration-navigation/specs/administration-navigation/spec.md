# Spec Delta

## MODIFIED Requirements

### Requirement: One tree on every Administration page

The side navigation of Access, Settings, Audit, Console, Maintenance, Analytics and Migration pages SHALL show the same
Administration tree, including every settings domain the visitor may open, with the current page marked by
`aria-current="page"`. Below 600 CSS pixels the tree SHALL be an index with a back arrow on each page.

#### Scenario: Console page lists the settings domains

- **WHEN** a full administrator opens `/console`
- **THEN** the tree lists Repository with Repositories, Maven settings, Javadoc and Cleanup policies, and System with the
  other settings domains, and marks Console as current

#### Scenario: Access page lists the same tree

- **WHEN** a full administrator opens `/access/teams`
- **THEN** the tree lists the same groups and entries as on `/console`, and marks Teams as current

#### Scenario: Settings and Access show equal trees

- **WHEN** the same visitor opens each Administration page in turn
- **THEN** every page shows an identical tree, apart from the marked entry

#### Scenario: Narrow screen

- **WHEN** a visitor opens any Administration page below 600 pixels
- **THEN** the tree is shown as an index list with a back arrow on each page

## ADDED Requirements

### Requirement: Unique entry keys

Every entry of the Administration tree SHALL have a key that no other entry of the tree has, including entries that a
settings domain or a plugin adds. A static page and a settings domain with the same id SHALL NOT share a key.

#### Scenario: Audit log and Audit settings

- **WHEN** the tree is built for a full administrator with the settings domain `audit` present
- **THEN** the Audit log entry and the Audit settings entry have different keys

#### Scenario: Plugin domain with a page id

- **WHEN** a plugin settings domain is called `users` or `security`
- **THEN** its entry has its own key, and the entries of `/access/users` and `/access/security` keep theirs

### Requirement: Exactly one current entry

A page of the Administration tree SHALL mark exactly one entry of the tree as current. A page that is not in the tree SHALL
mark none.

#### Scenario: Audit settings page

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** only the Audit settings entry in System is marked `aria-current="page"`, and the Audit log entry in Security is not

#### Scenario: Audit log page

- **WHEN** a full administrator opens `/audit`
- **THEN** only the Audit log entry in Security is marked, and no settings domain entry is marked

#### Scenario: Settings hub

- **WHEN** a full administrator opens `/settings` without a domain
- **THEN** no entry of the tree is marked

### Requirement: Breadcrumb names the page shown

The last crumb of an Administration page SHALL be the label of the entry that is current. The first crumb SHALL be
Administration.

#### Scenario: Audit settings breadcrumb

- **WHEN** a full administrator opens `/settings/audit`
- **THEN** the breadcrumb reads Administration, then Audit settings

### Requirement: Settings domains need the settings permission

The settings domains in the tree SHALL be shown only to a visitor with the settings node, and that visitor's domain list SHALL
be requested once per session. A visitor without the node SHALL make no request to the settings domain endpoint from any
Administration page.

#### Scenario: Visitor without the settings node

- **WHEN** a visitor with the audit node only opens `/audit`
- **THEN** the tree shows Audit log and no settings domain, and the settings domain endpoint is not called

#### Scenario: Visitor with the settings node

- **WHEN** a visitor with the settings node opens `/console` and then `/settings`
- **THEN** the settings domain endpoint is called once for the session

### Requirement: One Administration frame

Every Administration page SHALL draw the same frame: the navigation rail when the visitor sees it, one page heading named
Administration, the same page container, and the side navigation at the same place beside the page.

#### Scenario: Heading on every page

- **WHEN** a full administrator opens `/settings`, `/access/users`, `/console`, `/maintenance` and `/analytics` in turn
- **THEN** each page has one heading named Administration, and the page description names that page

#### Scenario: Rail on every page

- **WHEN** a full administrator opens any Administration page at 600 pixels or wider
- **THEN** the navigation rail is drawn at the left edge, with the Administration entry marked current

### Requirement: Rail label fits the rail

The label of each rail entry SHALL wrap inside the rail and SHALL NOT overflow its width or the viewport edge. The
accessible name of the Administration entry SHALL stay Administration.

#### Scenario: Long label on the Console page

- **WHEN** a full administrator opens `/console` at 600 pixels or wider
- **THEN** the Administration label is drawn in full inside the rail, with no clipped letter at the left edge

#### Scenario: Hidden rail

- **WHEN** a visitor opens an Administration page below 600 pixels
- **THEN** no rail text is visible in the viewport

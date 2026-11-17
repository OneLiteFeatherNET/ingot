## MODIFIED Requirements

### Requirement: Administration tree

Administration SHALL group its pages under four group ids, shown with these headings and entries, in this order:

- `Repository`, shown as Artifacts: Repositories, Cleanup policies, Javadoc, Search index.
- `Security`, shown as Access: Users, Machine accounts, Teams, Single sign-on, LDAP directory, Sign-in protection, Permission check.
- `System`, shown as Instance: Branding, Reverse proxy, Usage statistics, Migration, and every other settings domain, a plugin domain included.
- `Support`, shown as Monitoring and support: Audit log, Audit settings, Console, Maintenance, Jobs, Analytics, System information, Support bundle.

Migration is listed only while the nexus-import plugin is enabled. System information and Support bundle are listed only while the instance offers the support bundle. Overview is listed before the first group.

Plugin entries SHALL follow the built-in entries of their group. A plugin page whose group is none of the four ids SHALL be listed under Plugins, after every built-in group. A page SHALL be listed only when it exists and the visitor may open it. A group without a listed page SHALL be left out. The `maven` domain SHALL NOT be listed. Every route and route name SHALL keep its address; only the group, the label and the description of an entry are set by the tree.

#### Scenario: Full administrator

- **WHEN** a full administrator opens any Administration page, with Migration, System information and Support bundle offered
- **THEN** the tree lists Artifacts, Access, Instance and Monitoring and support with exactly the entries above, in that order, after the Overview entry

#### Scenario: Search index moved

- **WHEN** a full administrator opens the tree
- **THEN** Search index is listed under Artifacts and links to `/settings/search`, and no entry under Instance is labelled Search

#### Scenario: Audit entries are adjacent

- **WHEN** a full administrator opens the tree
- **THEN** Audit log and Audit settings are the first two entries of Monitoring and support, and no entry under Access is labelled Audit log

#### Scenario: Settings node without the audit node

- **WHEN** a visitor with the settings node but without the audit node opens the tree
- **THEN** Audit settings is listed under Monitoring and support and Audit log is not

#### Scenario: Plugin domain

- **WHEN** a plugin adds a settings domain without a section group
- **THEN** it is listed under Instance at `/settings/<domain>`, labelled with its plugin section label or, when it has none, its schema title

#### Scenario: Limited role

- **WHEN** a visitor may open only Console and Maintenance
- **THEN** only the group Monitoring and support is listed, with Maintenance and Jobs in it and no other entry

#### Scenario: Jobs listed for a maintenance visitor

- **WHEN** a visitor holds the maintenance area but not the instance admin node, and Migration is offered
- **THEN** Jobs is listed under Monitoring and support after Maintenance, and Migration is not listed

#### Scenario: Single page

- **WHEN** exactly one Administration page is permitted
- **THEN** `/settings` forwards to that page

#### Scenario: Old bookmarks

- **WHEN** a visitor opens `/settings/web`, `/settings/frontend`, `/settings/search`, `/settings/audit` or `/access/security`
- **THEN** the page opens at that address, under its entry label in the tree


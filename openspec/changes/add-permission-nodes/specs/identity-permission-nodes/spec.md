# Spec Delta

## Purpose

Permission nodes for principals and teams, with inheritance, negation and token narrowing. Nodes replace the repository
roles and the instance role as the source of decisions, while path grants and legacy route grants keep their meaning.

## ADDED Requirements

### Requirement: Node grammar

A permission node SHALL be one of `ingot.repository.<repository>.<action>`, `ingot.admin.<area>` or `*`. The action SHALL be
one of `read`, `deploy`, `delete` or `manage`, and the area SHALL be one of `settings`, `users`, `teams`, `tokens`,
`maintenance`, `audit`, `console` or `dashboard`. A wildcard SHALL be allowed only as a whole segment. The repository part
SHALL be everything between the `ingot.repository.` prefix and the final segment. Any other node SHALL be refused.

#### Scenario: Valid nodes are accepted

- **WHEN** a node list contains `ingot.repository.*.read`, `ingot.repository.releases.*`, `ingot.admin.users` and `*`
- **THEN** every node is accepted

#### Scenario: Malformed nodes are refused with every problem

- **WHEN** a node list contains `ingot.repository.releases` (no action), `ingot.admin.billing` (unknown area), `ingot.*`
  and `ingot.repository.rel*.read`
- **THEN** the request is refused with 400 and the answer lists each of the four problems

#### Scenario: A negated node is stored as denied

- **WHEN** a rule is written with `allowed` false for `ingot.repository.releases.delete`
- **THEN** the node is stored as denied and is displayed as `-ingot.repository.releases.delete`

### Requirement: Evaluation by closest source

The system SHALL decide a requested node by consulting the sources in order: the principal's own nodes, then the nodes of
its teams, then the nodes of inherited teams nearest first. Within the first source that has a matching node, the most
specific matching node SHALL decide. When two matching nodes of that source tie in specificity, denial SHALL win. When no
source has a matching node, the request SHALL be denied.

#### Scenario: A direct node beats a team node

- **WHEN** a principal has `ingot.repository.releases.read` denied and its team has `ingot.repository.releases.read` allowed
- **THEN** reading `releases` is denied and the decision names the principal as its source

#### Scenario: The more specific node wins within a source

- **WHEN** a team has `ingot.repository.*.read` allowed and `ingot.repository.releases.read` denied
- **THEN** reading `releases` is denied, and reading `snapshots` is allowed

#### Scenario: Denial wins a tie

- **WHEN** two teams of the same level grant and deny `ingot.repository.releases.deploy` with equal specificity
- **THEN** deploying to `releases` is denied

#### Scenario: No matching node denies

- **WHEN** no node of any source matches `ingot.repository.releases.read`
- **THEN** the request is denied and the decision source is the default

### Requirement: Inheritance between teams

A team SHALL be able to inherit from parent teams. The nodes of a parent SHALL apply to its children at the distance of the
inheritance, nearest parent first. A change that creates a cycle, including a team as its own parent, SHALL be refused with
409. A chain of inheritance deeper than eight levels SHALL be refused with 409. Private teams SHALL NOT be parents or children.

#### Scenario: The nearest parent decides

- **WHEN** a principal is a member of team `child`, whose parent `middle` allows `ingot.repository.releases.read` and whose
  grandparent `root` denies it
- **THEN** reading `releases` is allowed, and the decision names `middle` with depth 1

#### Scenario: A cycle is refused

- **WHEN** a team `a` with parent `b` is changed so that `b` gets `a` as parent
- **THEN** the change is refused with 409 and nothing is stored

#### Scenario: A chain of nine levels is refused

- **WHEN** a parent change would make a chain of nine inheritance levels
- **THEN** the change is refused with 409 and the answer names the depth rule

### Requirement: Tokens narrow and never widen

A token with a node list SHALL be allowed a request only when the owner is allowed it and the token's list allows it. A token
list with no matching node SHALL deny. A token without a node list SHALL narrow nothing. A token SHALL NOT receive rights
beyond those of its owner through its node list.

#### Scenario: A token list removes a right

- **WHEN** a principal may deploy to `releases` and its token has the node list `ingot.repository.*.read`
- **THEN** the token may read `releases` and may not deploy to it

#### Scenario: A token list cannot add a right

- **WHEN** a principal may only read `releases` and its token has the node list `ingot.repository.*.deploy`
- **THEN** the token may not deploy to `releases`, because the owner may not

#### Scenario: A token without a list keeps the owner's rights

- **WHEN** a token has no node list
- **THEN** its decisions are those of its owner

### Requirement: Instance admin is the node ingot.admin.*

The system SHALL treat a subject as an instance administrator when its effective decision for `ingot.admin.*` is allowed,
token narrowing included. A disabled subject SHALL be denied everything. The former instance role SHALL be represented by the
node `ingot.admin.*`.

#### Scenario: The star node makes an admin

- **WHEN** a team holds `*` allowed and a principal is a member of that team
- **THEN** the principal is an instance administrator and may open every manager-only endpoint

#### Scenario: A disabled admin is denied

- **WHEN** an administrator is disabled
- **THEN** every decision for that principal is denied

### Requirement: Path requests map actions to repository nodes

A path request SHALL be decided against the repository node of its action: `READ` to `read`, `WRITE` to `deploy`, `DELETE` to
`delete` and `MANAGE_REPOSITORY` to `manage`. Path grants and legacy route grants SHALL take part at the team level with
their existing matching rules. Legacy grants SHALL keep the matching of the previous implementation exactly.

#### Scenario: A legacy route keeps its prefix semantics

- **WHEN** a migrated token has the route `/releases/com/example` with the legacy read permission
- **THEN** it may read `/releases/com/examples/x` as before (no segment boundary), and it may not deploy to `/releases/com/example/x.jar`

#### Scenario: A repository node covers the repository

- **WHEN** a team has `ingot.repository.releases.deploy` allowed
- **THEN** a PUT to `/releases/com/example/x.jar` is allowed, and a DELETE to the same path is denied

### Requirement: Migration converts repository roles and instance roles

On start, the system SHALL run migration `002-permission-nodes` once, in one transaction, guarded by its marker. The migration
SHALL convert repository-wide team grants into nodes (`READ` to `read`; `DEPLOY` to `read` and `deploy`; `MAINTAIN` to `read`,
`deploy` and `delete`; `ADMIN` to all four actions) and SHALL convert each instance role into the node `ingot.admin.*`.

#### Scenario: A repository role becomes nodes

- **WHEN** a team holds a repository-wide `DEPLOY` grant on `releases` before the migration
- **THEN** after the migration the team holds `ingot.repository.releases.read` and `ingot.repository.releases.deploy` allowed,
  and no repository-wide grant remains for it

### Requirement: Migration converts token scopes

The migration SHALL convert the scopes of each token into its node list: `READ` to `ingot.repository.*.read`; `WRITE` to
`ingot.repository.*.deploy` and `ingot.repository.*.delete`; `DELETE` to `ingot.repository.*.delete`; `MANAGE_REPOSITORY` to
`ingot.repository.*.manage`; `INSTANCE_ADMIN` to `ingot.admin.*`. A token without scopes SHALL keep no node list.

#### Scenario: A write scope does not delete

- **WHEN** a token with the scope `WRITE` is migrated
- **THEN** its node list allows `ingot.repository.*.deploy` and does not allow `ingot.repository.*.delete`

### Requirement: Migration is guarded and verified

The migration SHALL leave path-scoped and legacy grants unchanged. A failed verification SHALL roll back the whole migration,
marker included, so that the next start retries from a clean state.

#### Scenario: The migration runs once

- **WHEN** the instance starts a second time
- **THEN** the migration does nothing and creates no nodes

### Requirement: Node administration API

The system SHALL expose `GET` and `PUT` on `/api/identity/principals/{name}/nodes` and `/api/identity/teams/{name}/nodes` with
the body `{ nodes: [{ node, allowed }] }`. `PUT` SHALL replace the whole list, SHALL honour `If-Match` and SHALL answer 412 on a
mismatch. A change SHALL emit one `NODES_CHANGED` audit event with the names of the subject and the changed nodes. A refused or
unchanged change SHALL emit no event. The endpoints SHALL be restricted to instance administrators.

#### Scenario: A stale write is refused

- **WHEN** a team's node list is replaced with an `If-Match` value that no longer matches
- **THEN** the answer is 412 and the stored list is unchanged

#### Scenario: A change is audited once

- **WHEN** a principal's node list changes from one node to two
- **THEN** exactly one `NODES_CHANGED` event is recorded, and it names the two added nodes

### Requirement: Node catalog

The system SHALL serve the node catalog with the kinds `STATIC`, `REPOSITORY` and `WILDCARD`, covering the admin nodes, `*`,
`ingot.admin.*`, each existing repository with its four actions and `ingot.repository.<repository>.*`, and
`ingot.repository.*.<action>`.

#### Scenario: The catalog filters by prefix

- **WHEN** the catalog is requested with the prefix `ingot.admin.`
- **THEN** only the admin nodes are returned

### Requirement: Permission check with derivation

The system SHALL serve a check that takes a principal, a concrete node and an optional token. It SHALL answer with the decision
and list every candidate it considered in the trace. The check and the templates are instance admin only, except that a
principal may check its own nodes.

#### Scenario: The check explains a denial

- **WHEN** a check is made for a node that a closer source denies
- **THEN** the decision names that source and the trace lists the candidate of the farther source with `matched` true and its
  reason

### Requirement: Templates are data

The system SHALL serve the templates `read`, `deploy`, `maintain`, `admin` and `instance-admin`, each with its node list. The
templates SHALL NOT change any stored rights when they are read.

#### Scenario: Templates list their nodes

- **WHEN** the templates are requested
- **THEN** the `admin` template contains the four repository nodes on `{repository}`, and `instance-admin` contains `ingot.admin.*`

### Requirement: Token node lists are created through the API

Self-service and admin token creation SHALL accept `nodes`. An empty list SHALL be refused with 400. The legacy `scopes` input
SHALL still be accepted and SHALL be translated to nodes by the mapping of the migration.

#### Scenario: A narrowed token is created

- **WHEN** a user creates a token with the nodes `ingot.repository.*.read`
- **THEN** the token is created with that node list and it cannot deploy anywhere, although the user can

### Requirement: Legacy surfaces keep their behaviour

The legacy token endpoint, its console commands and the output of `LegacyAuthorizationParityTest` SHALL keep their behaviour.
The principal and team list and detail responses SHALL keep their former fields for one release, additionally to the new `nodes`
summary.

#### Scenario: The parity suite is unchanged

- **WHEN** `LegacyAuthorizationParityTest` runs against the new evaluator
- **THEN** every case passes without any edit to the test

# Spec Delta

## Purpose

A signed-in principal can read its own access summary in one call: its teams with their direct parents, its instance administrator flag, its
effective nodes and denials, and its grants. The route answers only for the caller and reuses the evaluator that decides requests.

## ADDED Requirements

### Requirement: Caller's own summary only

The route `GET /api/identity/me/access` SHALL answer with the summary of the principal of the presented credential, cookie session or token. It
SHALL NOT accept a principal name, a token name or any other selector that names another principal. A request that carries a `principal`
query parameter SHALL get the caller's own summary and the parameter SHALL be ignored.

#### Scenario: Own summary

- **WHEN** the principal "alice" calls the route with her own session
- **THEN** the answer names "alice", and its teams and nodes are alice's

#### Scenario: Other principal named

- **WHEN** "alice" calls the route with `?principal=bob`
- **THEN** the answer is alice's summary, and it contains nothing of bob

### Requirement: No administrator role needed

The route SHALL be available to any authenticated principal, without the `ingot.admin.*` node or any area node. A principal without any
administrative right SHALL get its own summary.

#### Scenario: Plain member

- **WHEN** a principal that holds only `ingot.repository.releases.read` calls the route
- **THEN** the answer is 200 with its own summary, and the answer lists that node

### Requirement: Teams with direct parents

The answer SHALL list the teams the principal is in, each with its direct parent teams. It SHALL list only direct parents, not the whole chain,
and SHALL NOT list the private team of a machine account.

#### Scenario: Team with parent

- **WHEN** the principal is in "releases-maintainers", whose parent is "maintainers"
- **THEN** the answer lists "releases-maintainers" with parents ["maintainers"]

#### Scenario: Private team hidden

- **WHEN** the principal is a machine account with a private team named `machine:ci-deploy`
- **THEN** the answer does not list `machine:ci-deploy` as a team, and the nodes of that team appear in `nodes` and `grants`

### Requirement: Effective nodes and denials

The answer SHALL list the effective nodes of the principal, token narrowing included, as `PermissionEvaluator.effectiveNodes` gives them, and
SHALL list the denied nodes in a separate list. A denial SHALL come from the principal, its teams or its token.

#### Scenario: Token narrows

- **WHEN** the caller presents a token whose node list allows only `ingot.repository.releases.read` while its owner may deploy
- **THEN** `nodes` lists only the narrowed node and `tokenNarrowed` is true

#### Scenario: Denial listed

- **WHEN** the principal holds a denial of `ingot.repository.releases.delete`
- **THEN** `denials` lists that node, and `nodes` does not list it as allowed

### Requirement: Instance administrator flag

The answer SHALL set `instanceAdmin` to true when the subject holds the instance role, directly or through a team, and to false otherwise.

#### Scenario: Administrator

- **WHEN** a principal holds the instance role through the team "administrators"
- **THEN** `instanceAdmin` is true

### Requirement: Providers of the principal

The answer SHALL list the login providers the principal is linked to, as the principal detail endpoint lists them, under `principal.providers`.

#### Scenario: LDAP linked

- **WHEN** the principal signed in through the LDAP provider `ldap`
- **THEN** `principal.providers` contains `ldap`

### Requirement: Grants as stored

The answer SHALL list each grant of the principal and its teams as team, repository, path prefix and role, the same values the session answer gives.

#### Scenario: Repository grant

- **WHEN** the team "releases-maintainers" has a DEPLOY grant on `releases` with path prefix `net/example`
- **THEN** `grants` contains team "releases-maintainers", repository "releases", pathPrefix "net/example" and role "DEPLOY"

### Requirement: No secrets and no other data

The answer SHALL NOT contain a token secret, the list of the principal's tokens, the password or any hash, the list of other principals or teams,
or a value of another principal.

#### Scenario: Secrets absent

- **WHEN** the summary is built for a principal with two tokens
- **THEN** the answer contains neither token's secret nor the name of the second token

### Requirement: Temporary token

A token without a stored principal SHALL get an answer with `principal` null and `teams` empty. The answer SHALL still carry the nodes of the token
and its name, and SHALL have status 200.

#### Scenario: Temporary token

- **WHEN** a temporary token calls the route
- **THEN** the answer is 200, `principal` is null, and `nodes` lists the token's nodes

### Requirement: Authentication and errors

A request without a credential SHALL get 401 through the existing authentication. A token that no longer exists SHALL get 404 with the existing error
response, as `/api/auth/me` does.

#### Scenario: Anonymous

- **WHEN** a request without a credential calls the route
- **THEN** the answer is 401

### Requirement: Consistent with the check

For the same caller, every node in `nodes` SHALL be allowed by `GET /api/identity/check`, and every node in `denials` SHALL be denied by it.

#### Scenario: Agreement with the check

- **WHEN** a test builds a principal with a team, a token and a denial, and compares the summary with the check for each node
- **THEN** every allowed node is allowed by the check and every denial is denied by the check

### Requirement: Documented in OpenAPI

The route SHALL carry an `@OpenApi` annotation with its path, method, tags, security schemes and the response schema of the summary, and the
generated OpenAPI document SHALL list it under the Identity tag.

#### Scenario: OpenAPI lists the route

- **WHEN** the OpenAPI document is generated
- **THEN** it contains `GET /api/identity/me/access` under the Identity tag with a 200 response and a 401 response

### Requirement: Additive change

The route SHALL be an addition. No existing route, field, configuration key, environment variable or stored data SHALL change.

#### Scenario: Existing session answer unchanged

- **WHEN** a client calls `GET /api/auth/me` after this change
- **THEN** the answer is the same as before this change

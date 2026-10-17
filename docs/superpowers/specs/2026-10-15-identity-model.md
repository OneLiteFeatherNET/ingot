# Identity model (phase 1)

Status: proposed for implementation. Decision record: ADR-INGT-0001 (accepted).

This document specifies phase 1 of the identity model for Ingot: the data model, the
migration from access tokens, the permission evaluation, the compatibility layer and the
phase-1 administration API. Section 13 (phase 2a) adds the local password provider, browser
sessions, the setup code for the first administrator and users' own tokens with Bearer access.
OIDC, LDAP and the dashboard stay out of scope and are listed under "Open points".

## 1. Goals and constraints

- **Drop-in compatibility outranks everything.** An installation that upgrades from
  Reposilite or from an earlier Ingot must need no configuration change, no volume change
  and no plugin change. Every existing access token keeps its name, secret, expiry,
  description, identifier and exact rights. Migrated accounts behave exactly as before,
  including the quirks of the legacy route matching (see section 5).
- **Additive schema.** New tables are created on start, the way the other tables are. The
  old tables (`access_token`, `permission_access_token`, `permission_route`) are read
  during the migration and are never dropped or rewritten by the new code.
- **Portable SQL.** Everything runs on SQLite, MariaDB, MySQL, PostgreSQL and H2.
- **Packages.** Plugin API and existing classes stay in `com.reposilite.*`. New code lives in
  `net.onelitefeather.ingot.identity` (see section 9). Existing code only changes where
  the authorization decision is made.

## 2. Concepts

- **Principal**: an identity that can own tokens and inherit rights. `USER` is a human,
  `MACHINE` is a CI job or bot. Principals have no direct grants; they only inherit the
  rights of their teams and their own instance role.
- **Team** (named "Team" in the UI): a named group of principals with grants. A team with a
  name starting with `machine:` is a *private team*: it is owned by exactly one machine
  principal, is created by the migration or by the first legacy token creation, and is
  managed through the principal and token operations only (section 8.3).
- **Grant**: a role that a team holds on a repository, optionally limited to a path prefix
  inside that repository.
- **Role**: `READ`, `DEPLOY`, `MAINTAIN`, `ADMIN` (new model), `LEGACY_READ`,
  `LEGACY_WRITE` (old route semantics only).
- **Instance role**: `INSTANCE_ADMIN` assigned to a principal or to a team. It replaces the
  legacy `MANAGER` permission.
- **Token**: a secret owned by one principal. A token's **scopes** can only narrow the
  rights of its owner; they never add rights.

### 2.1 Roles and actions

Actions are what a request asks for: `READ`, `WRITE`, `DELETE`, `MANAGE_REPOSITORY`,
`INSTANCE_ADMIN`. Roles are cumulative.

| Role           | Actions granted                                             | Scope of the grant                                         |
|----------------|-------------------------------------------------------------|------------------------------------------------------------|
| `READ`         | READ                                                        | repository or path prefix                                  |
| `DEPLOY`       | READ, WRITE                                                 | repository or path prefix                                  |
| `MAINTAIN`     | READ, WRITE, DELETE                                         | repository or path prefix                                  |
| `ADMIN`        | READ, WRITE, DELETE, MANAGE_REPOSITORY                      | repository (repository settings and team grants on it)     |
| `LEGACY_READ`  | READ                                                        | legacy route prefix (section 5), no repository field       |
| `LEGACY_WRITE` | WRITE, DELETE                                               | legacy route prefix (section 5), no repository field       |
| instance role `INSTANCE_ADMIN` | every action on every repository, including INSTANCE_ADMIN | instance-wide                              |

Deliberate clarification: `LEGACY_WRITE` also grants `DELETE`. The old code deleted files
under the `WRITE` route permission, so a legacy write token must keep deleting. Nothing else
is implied: `LEGACY_READ` does not imply WRITE or DELETE, and `LEGACY_WRITE` does not imply READ.

`ADMIN` covers team grants on its repository, so managing those grants is a
`MANAGE_REPOSITORY` action. Repository-scoped cleanup endpoints stay manager-only in phase 1
(open point O-4).

## 3. Data model

All tables use Exposed `IntIdTable` (`id` integer primary key, auto-increment) unless
noted. Names are lower case, no prefix, which matches the existing `access_token` style.
Foreign keys use `ON DELETE CASCADE`. Because SQLite only enforces foreign keys when the
connection enables them, every delete path also removes its children explicitly (migration
004 of the legacy tables shows the orphan leak this prevents).

```
principal
  id             int pk
  kind           varchar(16)        USER | MACHINE
  name           varchar(255)       as typed
  name_key       varchar(255)       lower-case name, UNIQUE (case-insensitive uniqueness)
  display_name   varchar(255)
  disabled       boolean            default false
  created_at     timestamp
  last_seen_at   timestamp null

team
  id             int pk
  name           varchar(320)       as typed
  name_key       varchar(320)       lower-case name, UNIQUE
  description    text
  external_ref   varchar(512) null  reserved for sync (phase 2), null for manual teams

team_member
  team_id        fk team            PRIMARY KEY (team_id, principal_id)
  principal_id   fk principal
  source         varchar(16)        MANUAL | SYNC   (SYNC rows are written by phase-2 sync only)

team_grant
  id             int pk
  team_id        fk team            index
  repository     varchar(255) null  null = every repository
  path_prefix    varchar(1024) null null = whole repository (legacy: the full route path)
  role           varchar(32)        READ | DEPLOY | MAINTAIN | ADMIN | LEGACY_READ | LEGACY_WRITE

instance_role
  id             int pk
  principal_id   fk principal null   exactly one of principal_id / team_id is set
  team_id        fk team null
  role           varchar(32)        INSTANCE_ADMIN

principal_token
  id             int pk             preserved from access_token.id on migration
  principal_id   fk principal       index
  name           varchar(255)       UNIQUE (exact, as access_token.name today)
  secret_hash    varchar(512)       BCrypt, same encoding as access_token.secret
  scopes         text null          JSON array of actions, null = no narrowing (legacy tokens)
  expires_at     timestamp null
  last_used_at   timestamp null     written at most once per five minutes per token
  last_used_ip   varchar(64) null
  created_at     date
  description    text

identity_migration                  marker rows of versioned migrations
  name           varchar(64)        UNIQUE, e.g. "001-access-tokens"
  applied_at     timestamp
```

Integrity rules enforced in code (and covered by tests):

- A principal or team name is unique case-insensitively within its own kind of object, via
  `name_key`. Principals and teams are separate namespaces.
- A principal name is also reserved against token names: a legacy token whose name equals a
  `USER` principal is refused with a conflict (section 6.4).
- A team name starting with `machine:` is reserved for private teams.
- `team_grant` rows of role `LEGACY_*` exist only on private teams.
- `instance_role` rows are unique per (principal or team, role); enforced before insert
  because a nullable unique index does not behave the same on every database.
- An empty `scopes` array is rejected by the API (400). A token without scopes has `null`.

## 4. Migration

### 4.1 Versioning and the marker

`IdentityMigrations.MIGRATION_001 = "001-access-tokens"`. The migration is registered in
`identity_migration` with insert-ignore semantics, the same way the ETag key of the
`settings` table is created. Concurrent starts of several instances are guarded as follows.

### 4.2 Algorithm (one transaction)

```
migrateAccessTokens(database):
  transaction(database):
    marker = insertIgnore identity_migration(name = "001-access-tokens")
    if marker.insertedCount == 0:
        return ALREADY_APPLIED            # another instance or an earlier start did it

    for token in access_token order by id:
        name = token.name
        if name_key(name) already used by another principal (case-insensitive collision):
            name = "${name}-${token.id}"   # log a warning; the token keeps its exact name
        principal = insert principal(kind = MACHINE, name, name_key, display_name = name,
                                     disabled = false, created_at = token.createdAt at 00:00 UTC)
        insert principal_token(id = token.id, principal, name = token.name,
                               secret_hash = token.secret, scopes = null,
                               expires_at = token.expiresAt, created_at = token.createdAt,
                               description = token.description)
        team = insert team("machine:" + principal.name, description = "private team of " + name)
        insert team_member(team, principal, MANUAL)
        for perm in permission_access_token where access_token_id = token.id:
            if perm.permission == "access-token:manager":
                insert instance_role(principal_id = principal, role = INSTANCE_ADMIN)
            else: log warning and skip (unknown legacy permission)
        for route in permission_route where access_token_id = token.id, deduplicated:
            role = route.permission == "route:read" ? LEGACY_READ : LEGACY_WRITE
            insert team_grant(team, repository = null, path_prefix = route.route, role)

    reset identity sequence of principal_token (PostgreSQL, H2 only, section 4.4)
    verify counts: principal(MACHINE) == access_token rows migrated,
                   principal_token == access_token, legacy grants == permission_route rows
                   (after dedup); a mismatch throws and rolls back, including the marker
    return MIGRATED(count)
```

The old tables are only read. The marker is part of the same transaction, so a failed run
leaves no marker and the next start retries from a clean state.

### 4.3 Concurrency guard

The marker is the first statement of the transaction. `insertIgnore` maps to the vendor's
conflict-tolerant insert (`INSERT OR IGNORE` on SQLite, `INSERT IGNORE` on MariaDB and MySQL,
`ON CONFLICT DO NOTHING` on PostgreSQL, `MERGE` on H2) and reports `insertedCount`. Two instances
that start at the same time serialize on the unique index (PostgreSQL, MySQL) or on the
database write lock (SQLite, H2). The second one inserts zero rows once the first commits and
returns `ALREADY_APPLIED`. No exception is used for control flow, so no transaction is
aborted on PostgreSQL.

### 4.4 Identity sequence on PostgreSQL and H2

Migrated rows keep their `access_token.id`, so identifiers stay stable. PostgreSQL and H2 do
not advance an identity sequence on explicit inserts, so the migration then sets it past
`MAX(id)`: `setval(pg_get_serial_sequence(...), MAX(id))` on PostgreSQL and
`ALTER TABLE principal_token ALTER COLUMN id RESTART WITH MAX(id)+1` on H2. SQLite, MariaDB and
MySQL advance their counters on explicit inserts.

### 4.5 Temporary tokens

Tokens from `--token name:secret` are not migrated. They stay in memory
(`InMemoryAccessTokenRepository`) and behave as in-memory machine principals with a
private in-memory team that holds their legacy grants. They are not persisted.

### 4.6 Downgrade

The old tables hold the state as it was before the first start of the new version. A
downgrade therefore works, but changes made after the migration (new tokens, changed rights)
are not in the old tables and are lost for the old version. This is documented for operators
in the upgrade notes. The migration never writes the old tables.

## 5. Permission evaluation

### 5.1 The pure evaluator

`PermissionEvaluator` has no database access and no clock. Its input is an `AccessSubject`:

```
AccessSubject(
  principalId: Int?              // null for temporary tokens
  kind: MACHINE | USER
  disabled: Boolean
  instanceAdmin: Boolean         // INSTANCE_ADMIN directly or through a team; legacy MANAGER
  grants: List<Grant>            // grants of all teams of the principal, with their team name
  scopes: Set<Action>?           // null = no narrowing
)
Grant(team: String, role: Role, repository: String?, pathPrefix: String?)
Request(fullPath: String, action: Action)   // fullPath is "/repository/path/in/repo", like today
```

`repository` and `pathInRepo` are derived from `fullPath`: the first segment after the leading
slash, and the rest (empty for a repository root).

### 5.2 Allowed decision

```
allowed(subject, request):
  if subject.disabled                                  -> deny
  if subject.scopes != null and action !in scopes     -> deny
  if subject.instanceAdmin                              -> allow        # legacy MANAGER bypass
  for g in subject.grants:
      if action in actionsOf(g.role) and matches(g, request)  -> allow
  deny

matches(g, request):
  LEGACY_READ, LEGACY_WRITE:
      request.fullPath.startsWith(g.pathPrefix, ignoreCase = true)     # no segment boundary
  otherwise:
      (g.repository == null or g.repository == repository)            # exact, case-sensitive
      and (g.pathPrefix is blank
           or pathInRepo == p or pathInRepo.startsWith(p + "/"))      # p = prefix without slashes
                                                                        # segment boundary, case-sensitive
```

The new-role path matching is deliberately stricter than the legacy one (segment boundary and
case-sensitive). The legacy path matching is kept as it is, because migrated accounts must keep
it.

### 5.3 Visibility (`canSee`) and listing

`canSee(subject, fragment)` answers for a repository fragment `"/name"` and reproduces the
old `AccessTokenFacade.canSee` for legacy grants exactly:

```
canSee(subject, fragment):
  if disabled -> false
  if scopes != null and READ !in scopes -> false
  if instanceAdmin -> true
  legacy: any LEGACY grant g with
          g.pathPrefix.startsWith(fragment, ignoreCase = true) or fragment.startsWith(g.pathPrefix)
  new: any non-legacy grant g with g.repository == null or g.repository == name(fragment)
```

Note the second comparison of the legacy expression is case-sensitive, as it is today.

Repository listing (`RepositorySecurityProvider.canListRepository`):

| Visibility | Who lists it                                                                                    |
|------------|-------------------------------------------------------------------------------------------------|
| PUBLIC     | everyone                                                                                        |
| PRIVATE    | `canSee`                                                                                        |
| HIDDEN     | instance admins, and principals with a **new-model** grant of role READ or higher on the repository (repository-wide or path-prefixed). LEGACY grants never list a HIDDEN repository. |

Decision recorded from the brief: HIDDEN repositories are listed for principals whose teams
have at least READ on them, and for instance admins. Everyone may still read HIDDEN content by
its known path, as today (`canAccessResource` returns OK for HIDDEN).

### 5.4 Legacy parity rules (pinned by tests)

- `hasPermissionTo(id, path, READ|WRITE)` is `MANAGER or any route r with r.permission == perm and path startsWith r.path ignoreCase`. The evaluator reproduces this with LEGACY grants. READ does not imply WRITE and vice versa.
- A path prefix has no segment boundary: `/releases/com/example` also matches `/releases/com/examples/...`.
- A legacy route `/` matches every path.
- Matching is case-insensitive for `hasPermissionTo`; the second `canSee` comparison is case-sensitive.
- `MANAGER` bypasses every route check, including READ and WRITE on routes that were never granted.
- A token with no rights authenticates but can do nothing.

### 5.5 Mapping from HTTP to actions

| Caller                                        | Action                                             |
|-----------------------------------------------|----------------------------------------------------|
| `ContextDsl.isAuthorized`, GET / HEAD         | READ                                               |
| `ContextDsl.isAuthorized`, PUT / POST         | WRITE                                              |
| `ContextDsl.isAuthorized`, DELETE             | DELETE (LEGACY_WRITE implies it, so legacy is unchanged) |
| `RepositorySecurityProvider` read paths       | READ (unchanged)                                   |
| `RepositorySecurityProvider` deploy / metadata| WRITE (unchanged)                                  |
| `RepositoryService` delete decision           | DELETE (was WRITE; the same result for legacy tokens) |
| `ContextDsl.isManager`, `managerOnly`         | INSTANCE_ADMIN (MANAGER bypass)                    |

## 6. Compatibility layer

### 6.1 Facade

`AccessTokenFacade` keeps its public API. Its persistent storage is replaced by an adapter,
`IdentityAccessTokenRepository`, that implements the existing `AccessTokenRepository` on the
new tables:

| `AccessTokenRepository` call                    | Stored as                                                  |
|-------------------------------------------------|------------------------------------------------------------|
| `saveAccessToken` (create)                      | principal (MACHINE, reused if it exists), `principal_token`, private team `machine:<name>` with membership |
| `saveAccessToken` (update)                      | `principal_token` row                                      |
| `addPermission(MANAGER)`                        | `instance_role` INSTANCE_ADMIN on the principal            |
| `deletePermission(MANAGER)`                     | delete that `instance_role` row                            |
| `addRoute(route)`                               | `team_grant` on the private team, LEGACY_READ or LEGACY_WRITE, path_prefix = route |
| `deleteRoute`, `deleteRoutesByPath`             | delete matching LEGACY grants of the private team          |
| `findAccessTokenRoutesById`                     | LEGACY grants of the private team as `Route`               |
| `findAccessTokenPermissionsById`                | `MANAGER` if the principal is an instance admin            |
| `deleteAccessToken`                             | delete the `principal_token` row; if it was the last token of a machine principal, also delete that principal's legacy grants and its direct instance role |

Token ids are the `principal_token.id` values, which are the old `access_token.id` values.

Authorization reads go through a new facade method `subjectOf(identifier)`:

- persistent token: the `AccessSubject` loaded from the identity tables (principal, memberships,
  grants, instance roles, scopes, disabled);
- temporary token, or persistent without a database: an `AccessSubject` built from the in-memory
  legacy view (`MANAGER` as instance admin, each route as a LEGACY grant).

`hasPermission(MANAGER)` becomes `subjectOf(id).instanceAdmin`. `hasPermissionTo` and `canSee`
call the evaluator. `isDisabled(id)` is new; `BasicAuthenticator` rejects disabled principals.

### 6.2 Authentication

HTTP Basic and `xBasic` (`name:secret`) match `principal_token.name` exactly, as today, and then
BCrypt-compare the secret. Expiry is checked as today. The authentication cache keeps
its invalidation hooks: every change made through the facade or through the identity admin API
calls `tokenStateChanged(name)`, which drops the cached credentials. Audit actor names do not
change. `DeployEvent.by` stays `name@ip`.

### 6.3 Temporary tokens (`--token name:secret`)

Today's behaviour is pinned as it is (test `TemporaryTokenNameCollisionTest`):

- at startup, `--token name:secret` is created as a temporary token with `MANAGER`;
- if a persistent token with the same name exists, **it is deleted** (today's
  `storeAccessToken` deletes the token of the opposite type), together with its legacy grants and
  its direct instance role when it was the last token of its machine principal;
- for the rest of the process the in-memory token wins (`findRawAccessTokenByName` looks at the
  temporary repository first);
- when the process restarts without `--token`, the persistent token is gone, as it is today.

### 6.4 Name collisions during migration and creation

- A legacy token whose name is taken case-insensitively by another principal is migrated with the
  principal name `name-<tokenId>` and a warning. Its token name is kept, so authentication is
  unchanged. This can only happen on databases with case-sensitive collations.
- Creating a legacy token reuses the machine principal of exactly the same name (a token that was
  revoked and created again). A principal whose name differs only in case, or that is a `USER`
  principal, refuses the creation with 409 and a clear message. Attaching the token to the
  other principal would hand it that principal's rights. No such principal exists before phase 1.

### 6.5 Unchanged surfaces

- `/api/tokens` (list, get, upsert, patch, regenerate secret, delete), with their DTOs
  (`AccessTokenDto`, `AccessTokenDetailsDto`, `CreateAccessTokenResponse`), ETags and If-Match.
- Console commands `token-generate`, `token-modify`, `token-rename`, `token-revoke`,
  `token-regenerate`, `route-add`, `route-remove`, `tokens`, `token-export`, `token-import`. They
  go through the facade and their output is unchanged.
- `/api/auth/me` keeps `accessToken`, `permissions[].identifier` and `routes[]`, and adds:

```
{
  "accessToken": { ... unchanged ... },
  "permissions": [ { "identifier": "access-token:manager" } ],
  "routes":      [ { "path": "/releases", "permission": { "identifier": "route:read" } } ],
  "principal":   { "id": 7, "kind": "MACHINE", "name": "ci-bot" },     // id null for temporary tokens
  "teams":       [ "machine:ci-bot" ],
  "grants":      [ { "team": "machine:ci-bot", "repository": null, "pathPrefix": "/releases", "role": "LEGACY_READ" } ]
}
```

## 7. Phase-1 administration API

All endpoints require an instance admin (`managerOnly`). They are annotated with
`@OpenApi` like their neighbours (tag `Identity`). Mutating endpoints accept `If-Match`. A
mismatch returns 412, as in the token API. Responses to reads carry an `ETag`. Errors use
`ErrorResponse`: 400 invalid input, 404 unknown name, 409 name taken or reserved, 412 precondition
failed.

### 7.1 Principals

| Method | Path                                             | Body / result                                                                 |
|--------|--------------------------------------------------|-------------------------------------------------------------------------------|
| GET    | `/api/identity/principals?kind=USER\|MACHINE`    | `[PrincipalSummaryDto]`: the list row, flat, with the summaries a list shows    |
| POST   | `/api/identity/principals`                       | body `{kind, name, displayName?, disabled?}` -> `PrincipalDetailDto`, 201      |
| GET    | `/api/identity/principals/{name}`                | `PrincipalDetailDto` (teams, instanceRole, tokens without secrets)            |
| PATCH  | `/api/identity/principals/{name}`                | body `{displayName?, disabled?}` -> `PrincipalDetailDto`                     |
| DELETE | `/api/identity/principals/{name}`                | deletes the principal, its tokens, memberships, instance role and private team |

```
PrincipalDto        { id, kind, name, displayName, disabled, createdAt, lastSeenAt }
PrincipalSummaryDto { ...PrincipalDto, instanceAdmin: Boolean, instanceRole: Boolean, tokenCount: Int,
                      providers: [String] }
PrincipalDetailDto  { principal: PrincipalDto, teams: [name], instanceRole: Boolean,
                      tokens: [TokenDto] }
TokenDto            { name, description, createdAt, expiresAt, scopes: [Action] | null, lastUsedAt, lastUsedIp }
```

A list row is flat and answers everything a list shows, so that a client reads the list once and never the detail of
each row. `instanceAdmin` is true when the principal holds the instance role directly or is a member of a team that holds
it (the rule the access evaluation uses). `instanceRole` is the direct role only, the same value as in the detail; the
actions on a principal (section 7.4) can grant and revoke only that one. `tokenCount` is the
number of persistent tokens of the principal. `providers` names the login providers the principal can sign in with, by
the provider ids of `/api/auth/providers`: a USER with a stored password has `["local"]`, a USER without one has `[]`, and
a MACHINE principal always has `[]`, because machines authenticate with tokens only. The server reads the tokens,
memberships, instance roles and credentials of all principals in one query each, not once per row.

The detail nests the `PrincipalDto` fields in `principal`, next to the lists of the detail. This is the shape on
the wire; an earlier draft of this section wrote the detail flat. Clients read both.

### 7.2 Tokens of a principal

| Method | Path                                                   | Body / result                                                      |
|--------|--------------------------------------------------------|--------------------------------------------------------------------|
| GET    | `/api/identity/principals/{name}/tokens`               | `[TokenDto]`                                                       |
| POST   | `/api/identity/principals/{name}/tokens`               | body `{name, description?, expiresAt?, scopes?, secret?}` -> `{token: TokenDto, secret}` (secret returned once; generated when absent) |
| DELETE | `/api/identity/principals/{name}/tokens/{tokenName}`   | removes the token; 204                                            |

A token created here has no legacy grants. Its rights are those of the principal's teams, and
its scopes narrow them. The `/api/tokens` list shows every token, so these tokens are visible
there too (with an empty legacy rights list).

### 7.3 Teams

| Method | Path                                      | Body / result                                                                    |
|--------|-------------------------------------------|----------------------------------------------------------------------------------|
| GET    | `/api/identity/teams`                     | `[TeamDto]` with `memberCount`, `grantCount`, `instanceAdmin`; private teams are excluded |
| POST   | `/api/identity/teams`                     | body `{name, description?, externalRef?}` -> `TeamDetailDto`, 201                |
| GET    | `/api/identity/teams/{name}`              | `TeamDetailDto` with members and grants                                           |
| PATCH  | `/api/identity/teams/{name}`              | body `{description?, externalRef?}` -> `TeamDetailDto`                           |
| DELETE | `/api/identity/teams/{name}`              | deletes the team, its memberships, grants and instance role                      |
| PUT    | `/api/identity/teams/{name}/members`      | body `{members: [principalName]}`: replaces the MANUAL memberships; SYNC rows are kept |
| PUT    | `/api/identity/teams/{name}/grants`       | body `{grants: [{repository?, pathPrefix?, role}]}`: replaces all grants of the team |

```
TeamDto       { name, description, externalRef, memberCount, grantCount, instanceAdmin: Boolean }
TeamDetailDto { name, description, externalRef, members: [{principal, kind, source}],
                grants: [{repository, pathPrefix, role}], instanceRole: Boolean }
```

A list row carries the counts and the instance role, so a client reads the list once. `instanceAdmin` of a row is the
`instanceRole` of the detail of the same team. The detail lists its members and grants and does not repeat the counts.
The server counts members and grants of all teams in one query each, not once per row.

Rules: `role` in a grant is `READ`, `DEPLOY`, `MAINTAIN` or `ADMIN` (400 for legacy roles);
`repository` is null for "every repository"; `pathPrefix` is normalized (leading and trailing
slashes removed); a team named `machine:*` is 409 for create and 404 for the team endpoints;
members must be existing principals; a disabled principal can be a member.

### 7.4 Instance role

| Method | Path                                                | Body / result                                                  |
|--------|-----------------------------------------------------|----------------------------------------------------------------|
| PUT    | `/api/identity/principals/{name}/instance-admin`    | no body -> `PrincipalDetailDto` with `ETag`; granting twice changes nothing |
| DELETE | `/api/identity/principals/{name}/instance-admin`    | no body -> `PrincipalDetailDto` with `ETag`                    |
| PUT    | `/api/identity/teams/{name}/instance-admin`         | no body -> `TeamDetailDto` with `ETag`                         |
| DELETE | `/api/identity/teams/{name}/instance-admin`         | no body -> `TeamDetailDto` with `ETag`                         |

All four accept `If-Match`. The instance role of a principal reported in `PrincipalDetailDto.instanceRole` is
the direct role; an admin that holds the role through a team shows in `teams` instead.

**Last usable instance admin.** A removal that leaves no usable instance admin answers 409 with a message that
names the rule. A usable admin is an enabled principal with an unexpired persistent token that holds the role,
directly or through a team it is a member of. Temporary `--token` admins are not persistent and never count. The
same rule guards every change that would remove an admin as a side effect: deleting or disabling the principal,
deleting the team, deleting the token, and replacing team members so that a team admin loses its membership. A
change is refused only when a usable admin exists before it.

### 7.5 Effective access on a repository

`GET /api/identity/repositories/{repository}/access`

```
{
  "repository": "releases",
  "visibility": "HIDDEN",
  "entries": [
    {
      "principal": "ci-bot", "kind": "MACHINE", "disabled": false,
      "instanceAdmin": false,
      "effective": ["READ", "WRITE"],                     // union over the applying grants; token scopes are not applied here
      "via": [
        { "team": "machine:ci-bot", "role": "LEGACY_READ", "pathPrefix": "/releases/com/example" },
        { "team": "deployers", "role": "DEPLOY", "pathPrefix": null }
      ]
    }
  ]
}
```

Only principals with at least one grant that applies to the repository, or with the instance
role, appear. Team grants apply when `repository` is null or equal. `instanceAdmin` entries
have `via` entries of source `INSTANCE_ADMIN`. The `effective` list is the union of the
actions of the applying grants. The endpoint explains; it does not enforce anything.

### 7.6 Permission nodes (phase 2b)

Decision record: ADR-INGT-0003 (accepted). Teams and principals carry lists of permission nodes, each allowed or denied.
Teams inherit from parent teams. A token narrows its owner through its own node list and never widens it. The repository roles
and the instance role remain as predefined bundles for the user interface, and as the input of the migration. The change is
described in `openspec/changes/add-permission-nodes`. This section is the contract as implemented; where it differs from the
draft of the change, it wins.

**Nodes.** A node is `ingot.repository.<repository>.<action>` with the action `read`, `deploy`, `delete` or `manage`;
`ingot.admin.<area>` with the area `settings`, `users`, `teams`, `tokens`, `maintenance`, `audit`, `console` or `dashboard`;
or `*`, which allows everything. `ingot.admin.*` is the former instance admin. A wildcard is allowed only as a whole segment
(`ingot.repository.*.read`, `ingot.repository.releases.*`). The repository part is everything between the prefix and the final
action segment, so repository names may contain dots. A denied node is stored with `allowed: false` and displayed as
`-node`. A node that does not follow this grammar is refused with 400, and the answer lists every problem (below).

**Evaluation.** A requested node is decided by the sources in order: the principal's own nodes; the nodes and the path or
legacy grants of its teams; the nodes of inherited teams, nearest parent first, up to eight levels. Within the first source
that holds a matching node, the most specific match decides, counted by its literal parts (`ingot.repository.releases.read`
beats `ingot.repository.*.read`), and a denial wins a tie. No matching node denies. A token with its own node list is then
applied as an intersection: the owner's answer and the token's answer must both allow. Disabled principals are denied
everything. Instance admin means `ingot.admin.*` is allowed, token narrowing included.

**Path requests.** A file request maps its action to a repository node: READ to `read`, WRITE to `deploy`, DELETE to `delete`,
and manage operations to `manage`. The `manage` action is reserved: no endpoint performs a manage operation yet, so the node is stored and listed, but no request is checked against it and granting it changes no access. Path-scoped grants and legacy route grants take part at their team's level, with their
existing matching rules. Legacy routes keep the exact old prefix matching, so migrated tokens keep their rights. An instance
admin passes every path check that its token allows.

**Storage.** `permission_node(id, subject_kind USER|MACHINE|TEAM, subject_id, node, allowed, created_at)`, unique on subject and
node. `team_parent(team_id, parent_team_id)`. `principal_token.nodes`, a JSON array of `{node, allowed}`, null when the token
does not narrow. `team_grant` keeps path-scoped and legacy grants only.

**Migration `002-permission-nodes`.** Runs once, in one transaction, after `001-access-tokens`, guarded by its marker row.
Repository-wide team grants become nodes: READ gives `read`; DEPLOY gives `read` and `deploy`; MAINTAIN gives `read`, `deploy`
and `delete`; ADMIN gives all four, on the repository or on `*`. Instance roles become the node `ingot.admin.*` of their subject.
Token scopes become the token's node list: READ gives `ingot.repository.*.read`, WRITE gives `ingot.repository.*.deploy`,
DELETE gives `ingot.repository.*.delete`, MANAGE_REPOSITORY gives `ingot.repository.*.manage`, and INSTANCE_ADMIN gives
`ingot.admin.*`. The scopes stay in their column. A repository name that cannot be written as a node keeps its grant. A failed
check rolls back the whole migration, marker included.

**Endpoints.** All answers are JSON. Mutations take `If-Match` with the entity tag of the subject, and answer 412 on a
mismatch. Manager areas: `users` for principal endpoints, `teams` for team endpoints, `tokens` for principal token
endpoints, and the full admin `ingot.admin.*` for the others.

| Method | Path | Body and result |
|--------|------|-----------------|
| GET, PUT | `/api/identity/principals/{name}/nodes` | `{ nodes: [{ node, allowed }] }`; PUT replaces the whole list |
| GET, PUT | `/api/identity/teams/{name}/nodes` | `{ nodes: [{ node, allowed }] }`; PUT replaces the whole list |
| GET, PUT | `/api/identity/teams/{name}/parents` | `{ parents: [teamName] }`; a cycle or a depth above 8 is 409, an unknown or private team is 400 |
| GET | `/api/identity/nodes/catalog?prefix=` | `{ nodes: [{ node, description, kind }] }`, kind `STATIC`, `REPOSITORY` or `WILDCARD` |
| GET | `/api/identity/nodes/templates` | `{ templates: [{ id, label, nodes }] }`, ids `read`, `deploy`, `maintain`, `admin`, `instance-admin` |
| GET | `/api/identity/check?principal=&node=&token=` | `{ principal, node, allowed, decision: { source, subject, node, allowed, depth }, trace: [{ source, subject, node, allowed, matched, reason, depth }] }` |
| PUT | `/api/identity/teams/{name}/grants` | path-scoped grants only; a repository-wide grant is 400 with a message that names the node endpoint |

The check needs a concrete node: a wildcard is 400. Instance admins may check any principal. Any other caller may check its own
principal, and gets 403 for another one. `source` is one of `USER`, `TEAM`, `INHERITED_TEAM`, `LEGACY`, `TOKEN` or `DEFAULT`.
The trace lists every candidate of every level consulted, with `matched` and the reason.

Node lists are validated as a whole. Each problem is listed in the 400 message, and a node listed twice is a problem too.
The 400 of a refused list is `{ "status": 400, "message": "<the joined text>", "problems": [ { "node": "<the node as sent>",
"message": "<why>" } ] }`, with one entry per invalid row (a node listed twice gives one entry for that node). `problems` is
additive and is left out of every other error. The same shape answers a refused parent list (7.6, parents), where `node` is the
parent team's name, and a refused token node list. Clients that read only `message` keep working. An unchanged list writes
nothing and records no audit event. A change emits `NODES_CHANGED` with `added` and `removed` as
`node=allowed` or `node=denied` pairs. A parent change emits `TEAM_UPDATED` with `changed: ["parents"]`.

**Token narrowing.** `POST /api/identity/principals/{name}/tokens` and `POST /api/identity/me/tokens` accept `nodes`. An empty
list is 400. `scopes` is still accepted and becomes the node list of the token. Token responses (`TokenDto`) carry the list, or null. Token node lists are set at creation only; changing them later is open point O-22.

**Last usable instance admin.** A usable admin is an enabled principal with an unexpired token whose node list is null or
allows `ingot.admin.*`, and which is an admin through its own nodes or its teams' nodes. A change that would leave no usable
admin is refused with 409, as in section 7.4.

**Changed responses (additive).** Principal and team list rows carry `nodeCount` and `isAdmin`. Principal and team details carry
`nodes`, `nodeCount` and `isAdmin`; team details carry `parents`. Token responses carry `nodes`. `/api/auth/me` carries `nodes`:
the node patterns the owner may use, token narrowing included, for gating views. The patterns are for the interface; the
exact answer for one node is the check endpoint. `instanceAdmin`, `instanceRole` and `grants` keep their values for one
release and are deprecated. `instanceAdmin` is the direct or team-held `ingot.admin.*` node.

**Deviations and open points.**

- O-22: token node lists are set at creation only. An endpoint to change them is not part of this phase.
- Path-scoped and legacy grants take part at their team's level, as candidates with the specificity of the requested
  repository node. A denial from a closer source, such as the principal's own nodes, overrides them. At the same team level an
  exact denial wins the tie.
- The check and the list of node patterns describe nodes, not files. File access stays a path check.
- A downgrade to a build before this phase does not see node rights, since the nodes and the token node lists are not read by
  it. Path grants and legacy grants remain readable. The upgrade notes must say so; the page is not written yet.
- The login providers must switch their admin mapping from the instance role to the node `ingot.admin.*` on the team, and must
  not write nodes, parents or grants through team synchronisation (see the change's design document).

## 8. Behaviour details

### 8.1 Disabled principals

A disabled principal authenticates with no error at the storage level, but every
authorization decision denies, and Basic authentication fails with 401 (`Invalid authorization
credentials`, as for a wrong secret, so the response does not reveal the state).

### 8.2 Last-used tracking

`principal_token.last_used_at` and `last_used_ip` and `principal.last_seen_at` are written on
successful authentication, including an authentication answered from the credential cache. A
failed authentication writes nothing, and a failure to write never fails the authentication.
Writes happen at most once per five minutes for each token and for each principal. An in-memory
guard keeps the hot path free of writes; the database has the final word through a conditional
UPDATE that writes only when the stored time is at least five minutes old, so instances sharing
one database cannot write more often, and cannot move a stored time backwards. The clock is
injected. The values are exposed as `lastUsedAt` and `lastUsedIp` on token DTOs (identity API
and `/api/tokens` details) and as `lastSeenAt` on principal DTOs. They are informational: no
authorization decision reads them. Reads never change an entity tag.

### 8.3 Private teams

A private team is created with its machine principal, holds exactly that principal as
MANUAL member, and is hidden from the team list. Its LEGACY grants are maintained only by the
legacy token facade (`addRoute`, `deleteRoute`, `deleteRoutesByPath`). The team endpoints refuse
it. Deleting the principal deletes the private team.

### 8.4 Audit

Changes made through the identity admin API emit audit events through the existing audit module.
Token creation and deletion keep their existing actions (`TOKEN_CREATED`, `TOKEN_DELETED`). The
new actions are appended to `AuditAction`, so stored names keep their meaning:

| Change                                              | Action                                  | Details (names and changed fields, no secrets)        |
|-----------------------------------------------------|-----------------------------------------|-------------------------------------------------------|
| principal created                                   | `PRINCIPAL_CREATED`                     | `principal`, `kind`                                   |
| principal updated (display name, or re-enabled)     | `PRINCIPAL_UPDATED`                     | `principal`, `changed` (field names)                  |
| principal disabled                                  | `PRINCIPAL_DISABLED`                    | `principal`                                           |
| principal deleted                                   | `PRINCIPAL_DELETED`                     | `principal`, `kind`, `tokens` (token names)           |
| team created / updated / deleted                    | `TEAM_CREATED` / `TEAM_UPDATED` / `TEAM_DELETED` | `team`, and `changed` for updates           |
| team members replaced                               | `TEAM_MEMBERSHIP_CHANGED`               | `team`, `added`, `removed` (principal names)          |
| team grants replaced                                | `TEAM_GRANTS_CHANGED`                   | `team`, `added`, `removed` (`repository=... path=... role=...`) |
| instance role granted / revoked                     | `INSTANCE_ADMIN_GRANTED` / `INSTANCE_ADMIN_REVOKED` | `principal` or `team`                     |
| principal token created / deleted                   | `TOKEN_CREATED` / `TOKEN_DELETED`       | `token`, `principal`                                  |

A refused change and a change that alters nothing record no event (open point O-7 is closed).

A login provider that removes the last usable instance admin is audited with `LAST_ADMIN_REVOKED_BY_PROVIDER` (additive action,
see 14.5): `provider` and `principal`, with the actor `system:<provider>`. Unlike the rows above, nothing is refused here.

## 9. Module and package layout

No new Gradle module. All code stays in `reposilite-backend`.

```
net/onelitefeather/ingot/identity/
  IdentityMigrations.kt         versions and marker names
  model/                        Principal, PrincipalKind, Team, Role, Action, Grant, AccessSubject
  evaluation/PermissionEvaluator.kt   pure decision functions (section 5), no DB, no clock
  storage/IdentityTables.kt     Exposed tables (section 3)
  storage/IdentityStore.kt      queries and writes; loads AccessSubject; throttled last-used writes
  storage/LegacyAccessTokenMigration.kt   section 4
  storage/IdentityAccessTokenRepository.kt  AccessTokenRepository adapter (section 6.1)
  service/IdentityFacade.kt     admin operations, validation, ETags, audit hooks
  api/IdentityApiEndpoints.kt   REST routes (section 7), DTOs in api/IdentityDtos.kt
```

Changed existing code:

- `com.reposilite.token.AccessTokenFacade`: `subjectOf`, `hasPermission`, `hasPermissionTo`,
  `canSee`, `isDisabled`, `tokenStateChanged`; the persistent repository is the adapter.
- `com.reposilite.token.application.AccessTokenComponents`: creates the identity tables, runs
  the migration after the legacy tables exist, wires the adapter. Without a database the
  in-memory repositories are used as today.
- `com.reposilite.token.application.AccessTokenPlugin`: registers the identity API routes.
- `com.reposilite.auth.BasicAuthenticator`: rejects disabled principals.
- `com.reposilite.auth.AuthenticationFacade.getSessionDetails`: adds `principal`, `teams`, `grants`.
- `com.reposilite.shared.ContextDsl`: method to action mapping (5.5).
- `com.reposilite.maven.RepositorySecurityProvider`: HIDDEN listing rule (5.3).
- `com.reposilite.maven.RepositoryService`: delete decision uses DELETE.

The `com.reposilite.token.AccessTokenRepository` interface and `Route` / `RoutePermission` stay as
they are. `Route.hasPermissionTo` remains the oracle for the parity tests.

## 10. Tests

Every test follows F.I.R.S.T.: a fresh database per test (`DatabaseConnectionFactory` with
`sqlite --temporary` or `h2 --temporary`, or a file in a `@TempDir`), an injected `Clock`, no
sleeps, assertions with messages, one behaviour per test.

Required:

- migration idempotency (second run is a no-op and creates no rows);
- migration of a realistic legacy database (several tokens, routes, a manager, a token without
  routes, a route with both permissions), followed by authentication and authorization parity
  for each token over a matrix of paths and HTTP methods, with `Route.hasPermissionTo` as oracle;
- old tables remain intact after migration (row counts and contents unchanged);
- concurrent-start migration guard: two connections to the same file database start at once,
  exactly one migrates, and no row is duplicated;
- HIDDEN listing rule (new-model READ lists, LEGACY does not, instance admin lists);
- `--token` same-name behaviour (5.3 and 6.3) pinned.

## 11. Open points

- O-1 Login providers (local password, OIDC, LDAP group sync into `team_member` SYNC rows): phase 2. OIDC, GitHub and LDAP
  are closed in phase 3 (section 14).
- O-2 JWT browser sessions and their revocation: implemented in phase 2a, see section 13.
- O-3 Setup code for the first instance admin: implemented in phase 2a, see section 13. Phase 1 kept `--token` and the
  existing manager tokens as the only bootstrap.
- O-4 Repository-scoped cleanup endpoints stay manager-only; `MAINTAIN` does not yet unlock them.
- O-5 Dashboard UI for principals, teams and grants: separate phase, after the MD3 work.
- O-6 Downgrade: after migration the old version reads the frozen pre-migration state (4.6).
- O-7 Audit actions for principal and team changes: closed in phase 1b (section 8.4).
- O-8 Scope names are the action names. A UI for scopes is not part of phase 1.
- O-9 `expiresAt` and `disabled` for USER principals are stored but not used before login
  providers exist.

## 12. Implementation status and deviations (phase 1)

Recorded after the implementation, so that the spec and the code agree. Phase 1b (branch
`feat/identity-phase1b`) closed the gaps the phase-1 record listed: instance role assignment (section 7.4),
last-used tracking (section 8.2), identity audit events (section 8.4) and HTTP tests of the admin API.

- Sections 3 to 7 are implemented as written, with these exceptions:
  - Create and delete responses use HTTP 200 with a body, like the token API. The spec's 201 and 204 are not used.
  - The instance role is assigned and removed through the endpoints of section 7.4. A principal's
    `instanceRole` reports its direct role only. Phase 1b keeps the legacy manager token permission
    working: it still grants the same role, and the last-admin rule of section 7.4 applies to the identity
    endpoints only. Removing the last admin through the legacy `/api/tokens` permissions or the console is
    not guarded in phase 1b.
  - `last_used_at`, `last_used_ip` and `principal.last_seen_at` are written on successful authentication,
    throttled to five minutes (section 8.2), and exposed as additive fields.
  - Identity admin changes emit audit events (section 8.4).
- The migration lives in `LegacyAccessTokenMigration`, and its version constant is
  `MIGRATION_ACCESS_TOKENS = "001-access-tokens"`. The separate `IdentityMigrations.kt` of section 9 was not created.
- The migration lives in `LegacyAccessTokenMigration`, and its version constant is
  `MIGRATION_ACCESS_TOKENS = "001-access-tokens"`. The separate `IdentityMigrations.kt` of section 9 was not created.
- A machine principal created through the identity API gets its private team at once, as a machine principal
  created by a legacy token does (section 8.3).
- Section 6.4 is amended in this document: a legacy token whose name clashes case-insensitively with a
  machine principal, or with a user, is refused rather than attached.
- Tests cover the store, the migration, the evaluator, the legacy parity matrix, the `--token` pin, the
  identity service, the instance role rules, the last-use throttle and the audit events (unit tests), and the
  identity admin API over HTTP (`IdentityAdminIntegrationTest`, local variant).
- O-10 Instance role assignment through the identity API: closed in phase 1b (section 7.4). Assigning the role
  to a USER principal is still only meaningful once the login providers of phase 2 exist.
- Phase 2a (section 13.9) closes O-11 to O-16. O-19 was already closed here: last use and last seen are written by
  the token usage tracker (section 8.2).

## 13. Browser login and sessions (phase 2a)

Decision record: ADR-INGT-0001 (accepted). Humans log in through pluggable login providers. This phase implements the
provider interface and the LOCAL provider (username and password). OIDC and LDAP follow as further providers without
changing the session layer. Browser sessions are short-lived signed tokens with a refresh token, so that logout, a
disabled user and changed rights take effect on every instance at the latest when the session token expires.

### 13.1 Packages and wiring

```
net/onelitefeather/ingot/identity/login/
  LoginFacade.kt, LoginService.kt       the facade the rest of Ingot uses; password login, setup, password changes
  LoginProvider.kt, LocalLoginProvider.kt, LoginOutcome.kt   provider interface and outcomes
  PasswordHasher.kt                     argon2id, PHC strings
  LoginAttemptPolicy.kt, LoginAttemptLimiter.kt   failed password logins per user name and address, and per address,
                                        with backoff, counted in the database
  SetupCode.kt                          the one-time setup code
  BearerSecret.kt, OwnTokens.kt         secrets of users' own tokens, creation, rotation, deletion
  LoginSettings.kt                      session, refresh and failure limits (domain "login")
  storage/LoginTables.kt, storage/LoginStore.kt   local_credential, refresh_token, login_attempt, login_setup_marker
  api/AuthLoginEndpoints.kt             /api/auth/providers, login, setup, refresh, logout
  api/SelfServiceEndpoints.kt           /api/identity/me/* and the administrator password reset
  api/LoginDtos.kt                      request and response bodies
  application/LoginPlugin.kt            the plugin "login" (depends on access-token, authentication, configuration)
net/onelitefeather/ingot/identity/session/
  SessionSigner.kt                      JWT issue and verification
  SessionService.kt                     sessions, refresh rotation, reuse detection, logout, revoke-all
  SessionCookies.kt                     cookie names, Set-Cookie values, TLS detection
  CsrfPolicy.kt                         the double-submit rule as a pure function
```

Changed existing code, kept to the decision points:

- `com.reposilite.token.AccessTokenType` gains `PRINCIPAL`. A principal identity is a user acting directly, such as a
  browser session. Its value is the principal id. It has no token rows: `NoTokenRepository` answers every read with
  nothing and refuses every write, so a principal id can never be read as a temporary token with the same number.
  `AccessTokenFacade.subjectOf` resolves it from the identity tables, and a missing or disabled principal gets an empty
  subject that denies everything. `getPermissions` reports `MANAGER` for an instance admin. `createAccessToken` refuses
  `PRINCIPAL`.
- `AccessSubjectSource` gains `subjectOfPrincipal` and `principalOf`, implemented by `IdentityAccessTokenRepository`.
- `com.reposilite.web.infrastructure.ReposiliteRouting` resolves the caller in this order: an `Authorization: Bearer`
  header, then the existing Basic and xBasic handling, then an `ingot_session` cookie when no Authorization header is
  present. Before every route handler it applies the CSRF rule of 13.4.
- `JavalinConfiguration` passes the login facade to the DSL. `META-INF/services` registers `LoginPlugin`.

### 13.2 Data

```
local_credential
  principal_id        pk, fk principal, cascade
  password_hash       varchar(512)   argon2id, PHC string: $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>
  must_change_password boolean       default false
  updated_at          timestamp

refresh_token
  id                  int pk
  family_id           varchar(64), indexed     one login and all tokens derived from it
  principal_id        fk principal, cascade
  token_hash          varchar(128), unique     SHA-256 hex of the opaque token; the token itself is never stored
  created_at, expires_at  timestamp
  used_at             timestamp null           set when the token is exchanged; makes it single use
  revoked_at          timestamp null
  user_agent          varchar(512) null
  ip                  varchar(64) null
```

```
login_attempt
  attempt_key         varchar(64) pk   SHA-256 of "user|<name lower-cased>|<address>" or "address|<address>"; no name is stored
  failures            int              failures since the last lockout
  lockouts            int              lockouts so far, which set the length of the next lock
  last_failure_at     timestamp, indexed
  locked_until        timestamp null
  version             int              changes with every write; an update applies only to the version it read

login_setup_marker
  name                varchar(64) pk   "first-administrator", claimed once per database (13.7)
  claimed_at          timestamp
```

These tables are part of the identity schema (`IDENTITY_TABLES`), so they exist wherever the identity store does. They
are created on start with `createMissingTablesAndColumns`, like the other identity tables. No marker is needed for the
creation, because nothing is migrated. The legacy tables are never touched.

Principal names are case-insensitive, so the user name of a login is too. A login names a user; a machine principal has
no password and cannot log in.

### 13.3 Algorithms and libraries

- **Password hashing:** argon2id, version 19, OWASP minimum parameters (19 MiB, two passes, one lane), 16-byte salt,
  32-byte hash, implemented with `org.bouncycastle:bcprov-jdk18on:1.84`. The same version was already on the classpath
  through `at.favre.lib:bcrypt` (via bcpkix); it is now declared directly, because the login code calls it. Verification
  uses the cost stored in the hash, so raising the cost later does not invalidate existing passwords. Verification is
  constant time. A wrong password, an unknown user and a user without a password all run one verification, so the
  reply and its timing do not show which one happened.
- **Session tokens:** JWT, `com.nimbusds:nimbus-jose-jwt:10.9.1` (no further dependencies). HS256 only; verification
  rejects any other algorithm, including `none`, and checks the expiry against the injected clock. Claims: `sub` (principal
  id), `name`, `kind`, `iat`, `exp`, `sid` (refresh family id).
- **Signing key:** 32 random bytes, stored in the shared settings table under `jwt-signing-key`, created with the
  insert-ignore pattern of `entity-tag-key` (`ConfigurationFacade.findOrCreateConfiguration`). Every instance on the same
  database accepts the sessions of every other instance.
- **Refresh tokens:** 32 random bytes, base64url. Only the SHA-256 hash is stored. The lookup goes by that hash, which is
  an indexed unique column. A hash needs no salt, because the input has 256 bits of entropy.
- **Own token secrets:** `ingot_<tokenId>_<random>`, see 13.6.

### 13.4 Cookies, sessions and CSRF

Lifetimes come from the settings domain `login`: `sessionLifetimeMinutes` (default 15), `refreshLifetimeHours`
(default 168, seven days) and `refreshAbsoluteDays` (default 30). The refresh lifetime is an idle bound: every exchange
issues a token that lives that long again, but never beyond the absolute lifetime. That lifetime counts from the login,
which is the first token of the refresh family, so a renewal cannot extend it. A renewal after it revokes the family
and is refused; logging in again starts a new family (O-14, closed).

| Cookie | Value | Attributes | Path |
|---|---|---|---|
| `ingot_session` | session JWT | HttpOnly, SameSite=Lax, Max-Age = session lifetime | base path |
| `ingot_refresh` | opaque refresh token | HttpOnly, SameSite=Lax, Max-Age = refresh lifetime | `<base>/api/auth` |
| `ingot_csrf` | random, new with every session | SameSite=Lax, not HttpOnly | base path |

`Secure` is set when the request is HTTPS, or when the first value of `X-Forwarded-Proto` is `https`. The base path is
`LocalConfiguration.basePath`, `/` by default.

The CSRF rule (double-submit): a request that changes state (POST, PUT, PATCH, DELETE) and is authenticated by a session
or refresh cookie, with no Authorization header, must repeat the value of `ingot_csrf` in the `X-CSRF-Token` header.
Otherwise it gets 403. Basic and Bearer requests are exempt. Login and setup are exempt, because they create the cookies.
Reads are exempt.

A session is checked against its principal on every request. A disabled or deleted principal is refused on the next
request. Logout and password changes revoke the refresh token family, or all families of the user. The session token
itself is not looked up, so it keeps working until its `exp`, which is at most the session lifetime after the revocation.

Refresh reuse detection: presenting a spent token (or a revoked one that was spent) revokes the whole family. The claim of
the next token is a conditional update (`used_at` and `revoked_at` must still be null), so two concurrent exchanges of one
token on any instance produce exactly one new token.

### 13.5 API contract

All paths are relative to the base path. Error bodies are `{"status": <int>, "message": "<text>"}`, plus `problems` for a refused node or parent list (7.6). Responses that set
cookies set all three cookies of 13.4. The status codes are 200 for creation, like the token API; phase 1 already
recorded that deviation.

`GET /api/auth/providers` (public)

```
200 { "providers": [ { "id": "local", "type": "LOCAL", "label": "Username and password" } ],
      "tokenLogin": true,
      "setupRequired": false }
```

`setupRequired` is true while no user exists, and the local provider is listed only once a user exists (14.3). Later providers (OIDC, LDAP) add entries with a `loginUrl`.

`POST /api/auth/login` (public), body `{"provider": "local", "username": "...", "password": "..."}`

```
200 { "principal": { "id": 7, "kind": "USER", "name": "alice", "displayName": "Alice" },
      "mustChangePassword": false,
      "expiresAt": "2026-10-15T10:15:00Z" }                      + session, refresh and csrf cookies
400  missing fields, or an unknown provider
401  wrong user name or password (the same answer for an unknown user)
403  the user is disabled (only after the right password)
429  locked: too many failed attempts for this user name from this address, or from this address at all. The
     password is not checked. `Retry-After` gives the wait in whole seconds
503  no database: browser sessions need one
```

`POST /api/auth/refresh` (refresh cookie, and `X-CSRF-Token` when the cookie is sent)

```
200 { "principal": { ... }, "expiresAt": "..." }               + new cookies
401  unknown, expired, revoked or spent refresh token, or the user is gone; the three cookies are cleared
403  CSRF header missing or wrong
```

`POST /api/auth/logout` (session or refresh cookie)

```
200 (empty)    the refresh family is revoked and the three cookies are cleared
403  CSRF header missing or wrong
```

`GET /api/auth/setup` (public): `200 { "required": true }`. True while no user exists.

`POST /api/auth/setup` (public), body `{"setupCode": "...", "username": "...", "password": "...", "displayName": "..."}`

```
200 { "principal": { ... }, "mustChangePassword": false, "expiresAt": "..." }   creates the user as instance admin, starts a session
400  missing fields, a user name with colons, spaces or control characters, or a password outside the rules
403  setup is not required (a user exists), or the setup code is wrong
409  the name is held by a principal already (machine principals included), or another instance of the database
     completed the setup (13.7)
429  too many wrong setup codes from this address, with `Retry-After` as for login
```

`GET /api/auth/me` keeps its shape. It works with a session cookie, Basic and Bearer. For a session, `accessToken.name`
is the user name, `accessToken.description` is the display name, and `permissions` contains `access-token:manager` for an
instance admin.

`POST /api/identity/me/password` (session, Basic or Bearer as a user), body `{"currentPassword", "newPassword"}`

```
200 (empty)    the password changed; every session of the user ended; a forced change ended
403  the current password is wrong, or the caller is not a user principal (machine, legacy token)
400  the new password breaks the rules
```

`GET /api/identity/me/tokens` (as a user): `200 [ TokenDto ]` with `name`, `description`, `createdAt`, `expiresAt`,
`scopes`, `lastUsedAt`. No secret.

`POST /api/identity/me/tokens` (as a user), body `{"name", "description"?, "expiresAt"?, "scopes"?}`. There is no
`secret` field: the server generates it.

```
200 { "token": TokenDto, "secret": "ingot_<id>_<random>" }     the secret is returned once
400  name missing, contains a colon, or scopes empty or unknown
409  a token with this name exists (token names are unique across users, as before)
```

`POST /api/identity/me/tokens/{name}/rotate` (as a user): `200 { "token", "secret" }`, the new secret once; the old one
stops working at once. `404` for a name that is not one of the user's own tokens.

`DELETE /api/identity/me/tokens/{name}` (as a user): `200`, the token stops working at once. `404` as above.

`POST /api/identity/principals/{name}/password` (instance admin), body `{"newPassword": "..."}`: `200`. The user must
choose a new password at the next login (`mustChangePassword` is true), and every session of the user ends. `400`
for a machine principal or a password outside the rules, `404` for an unknown principal, `403` for a non-admin.

Password rules: 12 to 256 characters. Setup user names: 1 to 255 characters, no colon, no whitespace, no control
characters.

### 13.6 Bearer tokens

`Authorization: Bearer <secret>` authenticates the token named by the secret. The secret of a token created through
`/api/identity/me/tokens` (and of every rotation) has the form `ingot_<tokenId>_<random>`. The token id is part of the
secret, so no name is needed, and the id is no secret: the random part protects the token. The whole secret stays below
72 bytes, because bcrypt, which hashes it, ignores anything after that; a test pins this.

The Bearer check runs through the Basic path with the token name the id leads to. That keeps brute force protection,
the expiry check, the disabled-principal check and the authentication cache as they are. A secret that names no token
is checked with an empty name, fails, and counts like any wrong secret.

Tokens created through the identity admin API (`POST /api/identity/principals/{name}/tokens`) get this form when the
caller gives no secret; a secret the caller supplies is stored as given. Tokens created before this change, and tokens
created through the legacy `/api/tokens` endpoint, keep their unprefixed secrets and work over Basic only. The legacy
endpoint keeps its format, because the drop-in contract forbids changing what it returns (O-12, closed).

### 13.7 Setup code

While no user exists, the instance generates a setup code: 12 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, which
leaves out 0, O, 1, I and L. The code is kept in memory and logged once per start with the line
`--- First administrator`. It is accepted in any letter case and with surrounding spaces, and compared in constant time.
A successful setup creates the user with the instance admin role, consumes the code and starts a session. After that no
code is issued. A restart while still no user exists logs a new code.

Setups on one instance are serialized, so the check for "no user yet" and the creation cannot interleave. Wrong codes
count towards the address limit of 13.8, and are audited as `LOGIN_FAILED` with the actor `setup`.

Several instances on one database each log a code, and the setup is exclusive across them. The creation first claims
the marker row `first-administrator` of the database, with an insert that ignores an existing row. Only the instance
that wins the claim creates the administrator. Another instance answers `409` when its own code still matches, and
creates nothing. A creation that fails after the claim (for example because the name is taken) releases the claim
again. The marker stays after a successful setup, so the setup is once per database. A database whose users were all
removed by hand needs its marker row removed too before a new setup code can be used (O-13, closed).

### 13.8 Failure limit and audit

Password logins are limited by default, in the login domain: `login.maxFailedAttempts` (default 5) and
`login.lockoutMinutes` (default 15). The limit is independent of `authentication.bruteForceProtection`, which keeps
guarding Basic and token authentication and stays off by default (O-11, closed).

Every failed login counts twice: for its user name with its address, and for its address alone. A key that reaches the
maximum is locked for the lockout time. Every further lockout without a quiet period doubles that time, up to 24 hours.
Failures older than the lockout time no longer count, and a quiet period of one lockout time after a lock has ended
forgets the backoff. A successful login forgets only the key of its user name. The address key is kept, so an account of
one's own cannot reset the failures of the address. Wrong setup codes count towards the address key.

The counters live in `login_attempt`, and every change is a conditional update on the version it read, so a lock set on
one instance holds on every instance sharing the database. A blocked attempt is not checked and is audited as
`LOGIN_FAILED`. It answers `429` with `Retry-After`. Rows whose last failure is older than a day are removed, so the table
does not grow with user names an attacker invents.

Audited: `LOGIN_FAILED` for a rejected, disabled or blocked login and a wrong setup code (the attempted name and the
address, never the password or the code). Also `LOGIN_SUCCEEDED`, `LOGOUT`, `SESSION_REFRESH_REUSE_DETECTED` (a spent
refresh token was presented, which revokes its family), `SETUP_COMPLETED`, `PASSWORD_CHANGED` and `PASSWORD_RESET`
(O-15, closed). The last six are appended to `AuditAction`, so stored names keep their meaning. Renewals of a session are
not audited. No event carries a password, a token or a session identifier: the user name and the request address are
recorded. Also `TOKEN_CREATED`, `TOKEN_SECRET_REGENERATED` and `TOKEN_DELETED` for the users' own tokens (never the
secret), as before.

### 13.9 Deviations and open points

Closed in phase 2a:

- O-11 Password logins are limited by default, per user name with address and per address, shared through the
  database (13.8). The brute force settings of Basic and token authentication are unchanged.
- O-12 Tokens created through the identity admin API get the `ingot_` form and work as Bearer tokens (13.6). The legacy
  `/api/tokens` endpoint keeps its unprefixed secrets, because the drop-in contract forbids changing what it returns.
- O-13 Exactly one instance completes the first setup of a database, through the marker row (13.7). The other answers 409.
- O-14 The refresh lifetime has an absolute limit, `login.refreshAbsoluteDays`, counted from the login (13.4).
- O-15 Logins, logouts, reuse of a spent refresh token, setup, password changes and resets are audited (13.8). Renewals are
  not audited.
- O-16 Deleting a principal removes its password and its refresh tokens in the same transaction as its tokens, so the
  removal does not depend on foreign keys being enforced. Its sessions end at once, because the session token is refused
  when its principal is gone and no refresh token is left (`IdentityStore.deletePrincipal`).
- O-19 Closed by phase 1b: last use and last seen are written (section 12).

Still open:

- O-17 `X-Forwarded-Proto` is read only for the Secure flag. Ingot had no forwarded-proto handling before; the first
  value of the header is taken as the scheme. Trusting the header matters for the Secure flag only, never for
  authorization.
- O-18 The session key is not rotated. A rotation needs a second key that verifies the old tokens until they expire.
- O-20 No API to list or revoke a user's sessions, and no UI for any of this.
- O-21 The forwarded path prefix (`X-Forwarded-Prefix`) is not part of the cookie path. Cookies follow the configured
  base path only.
- O-10 is closed: phase 1b added the instance admin endpoints for principals and teams (section 7.4), and the
  first-admin setup assigns the role to the first user.

## 14. External login providers (phase 3)

Decision record: ADR-INGT-0001 (accepted). This section is the contract the frontend is built against. It implements O-1:
OpenID Connect, GitHub (OAuth 2, built in) and LDAP as browser login providers behind the provider interface of section 13,
with the same session layer. The design decisions are in `openspec/changes/add-external-login-providers/design.md`; the
behaviour is pinned by `openspec/changes/add-external-login-providers/specs/external-login-providers/spec.md`.

### 14.1 Packages

```
net/onelitefeather/ingot/identity/login/oidc/     settings, discovery and JWKS caches, PKCE, state cookie, ID token validation,
                                                  the OIDC and GitHub flows, the OIDC connection test
net/onelitefeather/ingot/identity/login/ldap/     the LDAP login provider (JNDI), RFC 4515 escaping, the LDAP connection test
net/onelitefeather/ingot/identity/login/external/ just-in-time users, user_identity, group synchronisation, admin groups
```

Wiring: `LoginService` builds its provider list from the settings on every call, `LoginPlugin` creates the services,
`AuthLoginEndpoints` and the new endpoint classes register the routes, and `IDENTITY_TABLES` lists `user_identity`.

### 14.2 Settings (domain `login`)

Added to `LoginSettings`. Every new key is optional and defaults to disabled, so an existing installation is unchanged.

```
oidcProviders: [                       empty by default
  {
    id: "corp"                          slug [a-z0-9-]{1,64}, unique; @SettingsKey
    enabled: false
    label: "Corporate SSO"
    icon: GENERIC                       GENERIC | GITHUB | GITLAB | GOOGLE | MICROSOFT | KEYCLOAK | AUTHENTIK | OKTA | AUTH0 | GITEA
    issuerUrl: ""                       OIDC discovery base; GITHUB with "" or "https://github.com" uses GitHub's endpoints
    clientId: ""
    clientSecret: ""                    @Sensitive
    scopes: "openid profile email"      GITHUB: blank or this default becomes "read:user user:email read:org"
    usernameClaim: "preferred_username"
    displayNameClaim: "name"
    emailClaim: "email"
    groupsClaim: "groups"
    allowedEmailDomains: []             when not empty, the email domain must be listed (case-insensitive), checked at every login
    autoCreateUsers: true
    adminGroups: []                     group names whose members become instance administrators (14.5)
  }
]
ldap: {
  enabled: false
  label: "LDAP"
  url: ""                               ldap:// or ldaps://host:port
  startTls: false                       StartTLS before the first bind (ldap:// only)
  bindDn: ""                            blank: anonymous search
  bindPassword: ""                      @Sensitive
  userBaseDn: ""
  userFilter: "(uid={username})"        {username} is replaced by the escaped user name
  usernameAttribute: "uid"
  displayNameAttribute: "cn"
  emailAttribute: "mail"
  groupBaseDn: ""                       blank: no group synchronisation
  groupFilter: "(member={dn})"          {dn} and {username} are replaced by their escaped values
  groupNameAttribute: "cn"
  autoCreateUsers: true
  adminGroups: []
}
publicUrl: ""                           blank: redirect URIs are built from the request. Otherwise an absolute https address, or
                                        http for localhost, without query, fragment or user name, for example https://repo.example.org/
```

Entries of `oidcProviders` with an empty or duplicate id, or an id that is not a slug, are skipped and logged once per start.
An enabled provider with a missing required field (issuer, client id, url) is not announced, and its routes answer `503`
with the name of the missing field, never a secret.

### 14.3 API

`GET /api/auth/providers` (public) lists the enabled providers only:

```
200 { "providers": [
        { "id": "local", "type": "LOCAL", "label": "Username and password", "icon": "LOCAL" },
        { "id": "corp", "type": "OIDC", "label": "Corporate SSO", "icon": "GENERIC", "loginUrl": "/api/auth/oidc/corp/login" },
        { "id": "ldap", "type": "LDAP", "label": "LDAP", "icon": "LDAP" }
      ],
      "tokenLogin": true,
      "setupRequired": false }
```

`type` is `LOCAL`, `OIDC` or `LDAP`. `loginUrl` is present for `OIDC` only and is relative to the base path, like every
path of this API. `icon` is the enum of 14.2 for `OIDC`, `LOCAL` for the local provider and `LDAP` for the LDAP provider. The local
provider is listed first while a local user exists or the first administrator was set up. `setupRequired` is unchanged.

`GET /api/auth/oidc/{id}/login?redirect=<path>` (public)

```
302 Location: <authorization endpoint>?response_type=code&client_id=...&redirect_uri=...&scope=...
               &state=...&nonce=...&code_challenge=...&code_challenge_method=S256        + ingot_oidc cookie
404  unknown or disabled provider
503  the provider is not usable (missing field), with the field named
```

`ingot_oidc` is a signed JWT (HMAC-SHA256, key derived from the session signing key) with the state, nonce, PKCE verifier,
provider id and redirect path. It lives ten minutes, is HttpOnly, SameSite=Lax, path `<base>/api/auth/oidc`, and `Secure` under
the rule of 13.4. The redirect path is kept only when it passes the redirect policy of 14.4; otherwise it becomes `/`.

`GET /api/auth/oidc/{id}/callback?code=...&state=...` (public). Every outcome clears `ingot_oidc` and then either sets the three
session cookies of 13.4 and redirects to the base path with the redirect path, or redirects to `<base>/?loginError=<code>`:

```
302 Location: <base>/<redirect path>            + session, refresh and csrf cookies
302 Location: <base>/?loginError=<code>         codes of 14.4
```

The redirect uri registered with the provider is `<publicUrl>/api/auth/oidc/<id>/callback` when `publicUrl` is set (the trailing
slash of the public URL is dropped). Without a public URL it is `<scheme>://<Host>/<base path>/api/auth/oidc/<id>/callback`. The
scheme is `https` when the request is secure (13.4), so a proxy that terminates TLS must send `X-Forwarded-Proto: https`. The path
of `publicUrl` must be the base path the server runs under, because the state cookie is scoped to it.

`POST /api/auth/login` (public) with `{"provider": "ldap", "username": "...", "password": "..."}` returns the response of 13.5.
The status codes are those of 13.5, plus `403` with the reason of 14.4 when the directory account is not provisioned, and `503`
when the directory cannot be reached. The `429` lock of 13.8 applies to LDAP attempts as it does to local ones.

`POST /api/auth/providers/oidc/{id}/test` (instance admin)

```
200 { "ok": true,  "issuer": "https://idp.example", "endpoints": { "authorization": "...", "token": "...", "jwks": "...", "userinfo": "..." } }
200 { "ok": false, "issuer": null, "endpoints": null, "error": "discovery document not reachable" }
403  not an instance admin
404  unknown provider
```

`POST /api/auth/providers/ldap/test` (instance admin), body `{"username"?: "...", "password"?: "..."}`

```
200 { "ok": true, "userDn": "uid=alice,ou=people,dc=example,dc=org", "groups": ["devs", "ops"] }
200 { "ok": true }                                     service bind only, no username given
200 { "ok": false, "error": "service bind failed" }
403  not an instance admin
```

`userDn` and `groups` appear only when a username was given and the user was found. A password is used for a user bind and is
never echoed. `error` texts never contain a password, a secret or a bind password.

### 14.4 Login codes

A failed callback redirects with one of these codes. A code reveals nothing beyond itself; the detail is logged at debug level.

| Code | Meaning |
|---|---|
| `invalid_state` | cookie missing, expired, for another provider, or state differs |
| `access_denied` | the provider denied the login |
| `provider_error` | the provider returned another error |
| `token_exchange_failed` | the token endpoint refused the code |
| `invalid_id_token` | signature, algorithm, issuer, audience, `azp`, expiry, skew or nonce failed |
| `email_domain_not_allowed` | `allowedEmailDomains` is set and the email is missing or not listed |
| `user_disabled` | the linked or matching principal is disabled |
| `user_not_provisioned` | no link exists and `autoCreateUsers` is false |
| `username_taken` | the user name belongs to another principal; nothing is linked |
| `invalid_username` | the user-name claim is not a valid user name (the setup rules of 13.5) |
| `setup_required` | no user exists yet; the first administrator must be set up with the setup code |
| `provider_unavailable` | discovery, the JWKS or the token endpoint could not be reached |
| `no_database` | browser sessions need a database |

LDAP answers the same reasons as `403` with `{"status": 403, "message": "<reason>"}`, and wrong credentials as `401`.

The redirect policy for `redirect` (14.2 and 14.3): a value is kept only when it starts with a single `/`, contains no backslash,
no colon before the query string, no control character, and is at most 1024 characters long. Anything else becomes `/`.

### 14.5 Data and group synchronisation

```
user_identity
  provider       varchar(64)     "oidc:<id>" or "ldap:<url-host>"
  subject        varchar(512)    the sub claim, the numeric GitHub id, or the DN
  principal_id   fk principal, cascade
  unique (provider, subject)
```

The table is part of `IDENTITY_TABLES`, created on start, and removed with its principal by `IdentityStore.deletePrincipal`.

Teams take part in synchronisation by `external_ref`: `oidc:<id>:<group>` for an OIDC or GitHub provider (a GitHub team is
`oidc:<id>:<org>/<team-slug>`), and `ldap:<group>` for LDAP. At each successful external login:

- every existing team whose ref equals one of the principal's groups gets a `SYNC` membership, if it has none;
- every `SYNC` membership of the principal in a team of the same provider prefix that is no longer matched is removed;
- `MANUAL` memberships and memberships of other providers are never changed;
- no team is created for an ordinary group. The operator creates the team with the ref.

Last usable admin: the provider stays authoritative. When a sync revokes the admin right and no usable instance admin (7.4, with the
rule of `AdminReachability`) is left, the revocation is still performed, and each principal that lost its admin right is reported
with a WARN that names the way back (`start with --token name:secret to regain access`) and audited as
`LAST_ADMIN_REVOKED_BY_PROVIDER` with `provider` and `principal`. A sync that leaves no usable admin when none existed before writes
nothing. Ingot never refuses a directory change for this reason.

Admin groups: for each name in `adminGroups`, the team with ref `<prefix><group>` is created on demand. Admin rights are never written
to a principal. They are granted to the synchronised team through one function of the external package (`AdminRights`), which
today sets the instance role of the team and which switches to the permission node `ingot.admin.*` on the team under ADR-INGT-0003.
The admin mapping therefore only ever changes group membership and the rights of the team.
A team with the same name but another ref is not taken over; the group is logged as skipped. For a team of the provider prefix, the
admin right of the team is granted while its group is listed and revoked when it is not. Members of such a team are instance administrators
through section 7.4, so the last-admin rule of section 7.4 applies. A manually granted instance role sits on a manual team and is
never touched by synchronisation.

Groups are read at an external login only. A refresh does not contact the identity provider, so a membership change in the
directory takes effect at the user's next external login (14.6, O-22).

Just-in-time creation: a user principal named after the user-name claim (the setup name rules apply), created only while a user
exists (`setup_required` otherwise), never linked to an existing principal of that name (`username_taken`). Creation is audited as
`PRINCIPAL_CREATED` with the actor `oidc:<id>` or `ldap`. Failed external logins are audited as `LOGIN_FAILED`, and successful
ones as `LOGIN_SUCCEEDED`, both with the address of the request and never a token or a password.

### 14.6 Algorithms, libraries and open points

- Discovery, JWKS and token requests use `java.net.http` with a five second connect and read timeout and no redirects. Discovery
  and the JWKS are cached per provider for one hour on the injected clock. A key id that is not cached triggers one refetch per minute.
  Issuers must be `https`, except loopback addresses.
- ID tokens are verified with nimbus-jose-jwt 10.9.1 (already declared) for RS256, RS384, RS512, PS256, ES256, ES384 and ES512.
  `none` and the HMAC algorithms are refused. Clock skew is 60 seconds.
- PKCE with S256 for every OIDC and GitHub login. Nonce is checked for OIDC only, because GitHub issues no ID token.
- LDAP uses JNDI (`com.sun.jndi.ldap.LdapCtxFactory`), as the legacy authenticator does. No dependency is added, and the
  UnboundID server stays a test dependency. Filter values are escaped per RFC 4515, an empty password is refused before any
  bind, and the search must return exactly one entry.
- No new dependency. `build.gradle.kts` is unchanged, so the licence report is unchanged.
- Legacy LDAP Basic authentication (`authentication.ldap`, `LdapAuthenticator`) is not changed and does not read `login.ldap`. The
  two settings are independent, and the guide says how to point both at one directory.

Open points:

- O-22 Group changes in the directory take effect at the next external login. An open session keeps its memberships until its
  refresh family ends (at most `login.refreshAbsoluteDays`). Ending the sessions of a user at once needs an administrative
  revocation endpoint, which is O-20.
- O-23 Resolved: `login.publicUrl` sets the base of the redirect URIs and of the callback URL shown in the dashboard (14.2, 14.3).
  Without it, deployments behind a proxy must send `Host` and `X-Forwarded-Proto` correctly.
- O-24 Groups come from the ID token (OIDC) or the GitHub API only, not from the userinfo endpoint. A provider that puts groups
  only there needs an ID token claim configured on the provider side.
- O-25 SAML, SCIM and directory-driven de-provisioning are not implemented. A removed directory account keeps its principal until an
  administrator disables it, and its memberships last until its next external login.
- O-26 Account linking of an existing local user to an external subject is not implemented. A name conflict is refused.
- O-27 StartTLS for `login.ldap` (`startTls: true` on `ldap://`) is implemented with JNDI's extended operation, and the bind after
  the upgrade relies on JNDI's reconnect. No automated test covers it, because the in-memory directory has no TLS listener. Verify it
  against a real directory before enabling it, or use `ldaps://`.
- O-28 Audit ownership: the login service audits the outcome of a password login (LDAP) and the OpenID Connect callback audits
  its own outcome, with the address of the request. The account service audits only the creation of a user.
- O-29 A blank password is a missing field (`400`) for every provider, as the existing login route already answers. The directory
  is not asked in that case. A wrong password is `401`.

# Identity model (phase 1)

Status: proposed for implementation. Decision record: ADR-INGT-0001 (accepted).

This document specifies phase 1 of the identity model for Ingot: the data model, the
migration from access tokens, the permission evaluation, the compatibility layer and the
phase-1 administration API. Login providers (local password, OIDC, LDAP), JWT browser
sessions, the setup code and the dashboard are out of scope and listed under
"Open points".

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
  last_used_at   timestamp null     written at most once per minute per token
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
| GET    | `/api/identity/principals?kind=USER\|MACHINE`    | `[PrincipalDto]`                                                              |
| POST   | `/api/identity/principals`                       | body `{kind, name, displayName?, disabled?}` -> `PrincipalDetailDto`, 201      |
| GET    | `/api/identity/principals/{name}`                | `PrincipalDetailDto` (teams, instanceRole, tokens without secrets)            |
| PATCH  | `/api/identity/principals/{name}`                | body `{displayName?, disabled?}` -> `PrincipalDetailDto`                     |
| DELETE | `/api/identity/principals/{name}`                | deletes the principal, its tokens, memberships, instance role and private team |

```
PrincipalDto       { id, kind, name, displayName, disabled, createdAt, lastSeenAt }
PrincipalDetailDto { ...PrincipalDto, teams: [name], instanceRole: Boolean,
                     tokens: [TokenDto] }
TokenDto           { name, description, createdAt, expiresAt, scopes: [Action] | null, lastUsedAt }
```

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
| GET    | `/api/identity/teams`                     | `[TeamDto]` with `memberCount`, `grantCount`; private teams are excluded          |
| POST   | `/api/identity/teams`                     | body `{name, description?, externalRef?}` -> `TeamDetailDto`, 201                |
| GET    | `/api/identity/teams/{name}`              | `TeamDetailDto` with members and grants                                           |
| PATCH  | `/api/identity/teams/{name}`              | body `{description?, externalRef?}` -> `TeamDetailDto`                           |
| DELETE | `/api/identity/teams/{name}`              | deletes the team, its memberships, grants and instance role                      |
| PUT    | `/api/identity/teams/{name}/members`      | body `{members: [principalName]}`: replaces the MANUAL memberships; SYNC rows are kept |
| PUT    | `/api/identity/teams/{name}/grants`       | body `{grants: [{repository?, pathPrefix?, role}]}`: replaces all grants of the team |

```
TeamDto       { name, description, externalRef, memberCount, grantCount }
TeamDetailDto { ...TeamDto, members: [{principal, kind, source}],
                grants: [{repository, pathPrefix, role}], instanceRole: Boolean }
```

Rules: `role` in a grant is `READ`, `DEPLOY`, `MAINTAIN` or `ADMIN` (400 for legacy roles);
`repository` is null for "every repository"; `pathPrefix` is normalized (leading and trailing
slashes removed); a team named `machine:*` is 409 for create and 404 for the team endpoints;
members must be existing principals; a disabled principal can be a member.

### 7.4 Effective access on a repository

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

## 8. Behaviour details

### 8.1 Disabled principals

A disabled principal authenticates with no error at the storage level, but every
authorization decision denies, and Basic authentication fails with 401 (`Invalid authorization
credentials`, as for a wrong secret, so the response does not reveal the state).

### 8.2 Last-used tracking

`principal_token.last_used_at` and `last_used_ip` and `principal.last_seen_at` are written on
successful authentication, at most once per minute per token (the throttle compares with the
injected clock). They are informational in phase 1.

### 8.3 Private teams

A private team is created with its machine principal, holds exactly that principal as
MANUAL member, and is hidden from the team list. Its LEGACY grants are maintained only by the
legacy token facade (`addRoute`, `deleteRoute`, `deleteRoutesByPath`). The team endpoints refuse
it. Deleting the principal deletes the private team.

### 8.4 Audit

Changes made through the identity admin API emit audit events. Phase 1 uses the existing
token audit actions for token changes (`TOKEN_CREATED`, `TOKEN_DELETED`,
`TOKEN_ROUTES_CHANGED`) and adds no new audit action for principal and team changes (open point O-7).

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

- O-1 Login providers (local password, OIDC, LDAP group sync into `team_member` SYNC rows): phase 2.
- O-2 JWT browser sessions and their revocation: phase 2.
- O-3 Setup code for the first instance admin: phase 2. Phase 1 keeps `--token` and the
  existing manager tokens as the only bootstrap.
- O-4 Repository-scoped cleanup endpoints stay manager-only; `MAINTAIN` does not yet unlock them.
- O-5 Dashboard UI for principals, teams and grants: separate phase, after the MD3 work.
- O-6 Downgrade: after migration the old version reads the frozen pre-migration state (4.6).
- O-7 Audit actions for principal and team changes are not added in phase 1.
- O-8 Scope names are the action names. A UI for scopes is not part of phase 1.
- O-9 `expiresAt` and `disabled` for USER principals are stored but not used before login
  providers exist.

## 12. Implementation status and deviations (phase 1)

Recorded after the implementation, so that the spec and the code agree.

- Sections 3 to 7 are implemented as written, with these exceptions:
  - Create and delete responses use HTTP 200 with a body, like the token API. The spec's 201 and 204 are not used.
  - No endpoint assigns the instance role to a team or to a principal. The role is read in the
    DTOs (`instanceRole`) and reaches principals through the legacy manager token permission. Assignment
    through the API is open point O-10.
  - `last_used_at`, `last_used_ip` and `principal.last_seen_at` are not written yet (section 8.2). The
    columns exist. The entity tags leave them out, so a read never changes a tag.
  - Identity admin changes do not emit audit events, except token creation and deletion, which reuse the
    token audit actions. Open point O-7 stands.
- The migration lives in `LegacyAccessTokenMigration`, and its version constant is
  `MIGRATION_ACCESS_TOKENS = "001-access-tokens"`. The separate `IdentityMigrations.kt` of section 9 was not created.
- A machine principal created through the identity API gets its private team at once, as a machine principal
  created by a legacy token does (section 8.3).
- Section 6.4 is amended in this document: a legacy token whose name clashes case-insensitively with a
  machine principal, or with a user, is refused rather than attached.
- Tests cover the store, the migration, the evaluator, the legacy parity matrix, the `--token` pin and the
  identity service. The HTTP layer has no direct test in phase 1. It is covered by the service tests and by
  compilation.
- O-10 Instance role assignment for principals and teams through the identity API: phase 2, together with the
  login providers that make USER principals usable.

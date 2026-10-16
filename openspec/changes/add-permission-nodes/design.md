# Design

This design implements ADR-INGT-0003 on top of the identity model of phase 1 and phase 2a. Where the ADR and this document
differ in wording, the decisions below are the ones the implementation follows. The identity model specification gets a
section 7.6 with the HTTP contract as implemented (see the last task).

## D1. Node grammar

A node is a dotted string. Two shapes exist, plus the all-node:

| Shape                                   | Meaning                                                                 |
|-----------------------------------------|-------------------------------------------------------------------------|
| `ingot.repository.<repo>.<action>`      | an action on one repository, action one of `read`, `deploy`, `delete`, `manage` |
| `ingot.admin.<area>`                    | an administrative area, one of `settings`, `users`, `teams`, `tokens`, `maintenance`, `audit`, `console`, `dashboard` |
| `*`                                     | everything                                                               |

Wildcards:

- `*` is valid only as a whole segment: `ingot.repository.*.read`, `ingot.repository.releases.*`, `ingot.admin.*`.
- In the repository shape the `<repo>` part is everything between the `ingot.repository.` prefix and the final action
  segment. Repository names may therefore contain dots. The only wildcard form in that part is the whole part `*`.
- A `*` segment inside a node matches exactly one segment, except the repository part, which matches any repository name.
- `*` alone matches every node.
- A negated node is stored as `allowed = false`. The string `-node` is only the display form.
- Anything else is refused with 400 and a problem list: a missing action, an unknown action or area, a `*` that is not a
  whole segment, empty segments, whitespace, control characters, a leading `-`, or a length above 255.

Specificity, used by evaluation: the number of literal parts of a pattern. The parts are `ingot`, `repository` or `admin`,
the repository part (or area), and the action. `*` alone has no literal part. So `ingot.repository.releases.read` (4) beats
`ingot.repository.*.read` (3), which beats `ingot.repository.*.*` (2).

## D2. Storage

- `permission_node(id, subject_kind USER|MACHINE|TEAM, subject_id, node, allowed, created_at)`, unique on
  `(subject_kind, subject_id, node)`. Principal nodes of both kinds are the direct source, reported as `USER`.
- `team_parent(team_id, parent_team_id)`, the inheritance edges. Unique pair, both ends cascade.
- `principal_token.nodes` (text, JSON array of `{node, allowed}`, null = no narrowing). It is new and added with migration
  002. The existing `scopes` column stays readable; new tokens write the node list of their scopes into both.
- Tables are created on start like the others, and the schema change is additive.

## D3. Evaluation

The evaluator stays pure: it takes an `AccessSubject` and a request and reads no database and no clock. The subject lists
its sources in precedence order:

1. the principal's direct nodes (`USER`, depth 0),
2. the nodes and legacy grants of its teams (`TEAM`, depth 0; the private team of a machine is one of them),
3. the nodes of inherited teams, one level per distance, nearest first (`INHERITED_TEAM`, depth 1 to 8).

For a requested concrete node `N`:

```
decide(N):
  for level in sources in order:
      candidates = all rules of the level whose pattern matches N
      if candidates is empty: continue
      best = candidates with the highest specificity
      return allowed = no best candidate is denied        # deny wins a tie
  return DEFAULT, denied
```

A candidate that is denied at the best specificity decides the level. Within one level the candidates of all its teams are
pooled, so a team's deny can beat another team's allow of the same specificity.

Token narrowing: when the token has a node list, the token list is decided with the same rule (one level, source `TOKEN`).
The final answer is `owner.allowed AND token.allowed`. A token list with no matching node denies. A token list is never
consulted for widening, and a missing token list (null) narrows nothing.

Disabled principals are denied everything. A disabled subject is never an admin.

Instance admin: `isAdmin(subject)` is `decide(ingot.admin.*).allowed`, including token narrowing. Its meaning is the former
`instanceAdmin` flag, so `*` and `ingot.admin.*` both make a principal an admin.

## D4. Path requests

A request for a path is decided by mapping its action to a repository node: `READ` to `read`, `WRITE` to `deploy`, `DELETE`
to `delete`, `MANAGE_REPOSITORY` to `manage`. `INSTANCE_ADMIN` maps to `ingot.admin.*`. The decision for a path request is:

```
allowsPath(subject, path, action):
  if disabled: deny
  N = ingot.repository.<repo of path>.<mapped action>
  if token list exists and token denies N: deny
  if isAdmin(owner): allow                       # former MANAGER bypass, still behind token narrowing for N
  owner levels as in D3, where the candidates also include the grants:
     - path grants (repository-wide or path-scoped, role actions) reach N when the role has the action,
       the repository matches and the path lies within the grant prefix (segment boundary, case-sensitive)
     - legacy grants (LEGACY_READ, LEGACY_WRITE) match by the old rule: full path starts with the route
       prefix, case-insensitive, no segment boundary; they contribute the actions of their role
  candidate grants have the specificity of N itself (4), so an exact node of the same level decides a tie
```

Legacy grants and path grants therefore take part in the closest-source rule at the team level. A denial at the user level
beats a team grant, as it beats a team node. The legacy parity rules of section 5.4 of the identity model hold unchanged:
a subject with only legacy grants and no nodes gets exactly the old answers, because its node levels are empty.

## D5. Legacy grants in node checks

A node check (the `check` endpoint, the effective nodes of `/api/auth/me`) has no path. A legacy grant counts for a
repository node only when its route is the whole repository, that is `/<repo>` or `/`, case-insensitive. A partial legacy
route does not answer a node question; the decision reports it in the trace with `matched: false` and reason
`path-scoped legacy grant, use a path check`. The source of such a decision is `LEGACY`.

## D6. Visibility and listing

- `canSee(subject, "/<repo>")` is true when the subject is an admin, when the owner decides `read` on the repository and
  the token allows it, when a legacy grant reaches the fragment by the old rule (section 5.3 of the identity model), or when a
  path grant reaches it. The legacy expression stays exactly as it is.
- A HIDDEN repository is listed for an admin, and for a subject that decides `read` on it, and for a path grant with the
  READ action on it. Legacy grants never list a HIDDEN repository, as before.

## D7. Migration `002-permission-nodes`

One transaction, claimed with the insert-ignore marker `002-permission-nodes` in `identity_migration`, after
`001-access-tokens`. It runs only when the marker is new, and rolls back as a whole, marker included, when verification
fails.

1. Repository-wide team grants (`repository` null or set, `path_prefix` null, role `READ`, `DEPLOY`, `MAINTAIN` or
   `ADMIN`) become nodes of their team:
   - `READ` to `ingot.repository.<repo>.read`
   - `DEPLOY` to `read`, `deploy`
   - `MAINTAIN` to `read`, `deploy`, `delete`
   - `ADMIN` to the four explicit nodes `read`, `deploy`, `delete`, `manage`
   `<repo>` is `*` when the grant names no repository. The converted rows are removed from `team_grant`.
2. Path-scoped non-legacy grants stay in `team_grant` unchanged. They keep the phase 1 semantics (D4).
3. Legacy grants (`LEGACY_READ`, `LEGACY_WRITE`) on private teams stay in `team_grant` unchanged.
4. Each `instance_role` row becomes the node `ingot.admin.*` allowed on the same principal or team, and the row is removed.
5. Token scopes become the token node list, one entry per scope, and `nodes` is written for every token whose `scopes` is not
   null:
   - `READ` to `ingot.repository.*.read`
   - `WRITE` to `ingot.repository.*.deploy` only. A write scope never covered DELETE, which has its own scope, so the
     migration keeps the rights of the scopes exactly. Legacy route grants keep deleting, as LEGACY_WRITE includes DELETE.
   - `DELETE` to `ingot.repository.*.delete`
   - `MANAGE_REPOSITORY` to `ingot.repository.*.manage`
   - `INSTANCE_ADMIN` to `ingot.admin.*`
   Tokens without scopes keep a null list, which narrows nothing.
6. Verification counts the converted rows against the rows read, and throws on a mismatch, which rolls everything back.

Access tokens of the legacy tables are not touched. Migration 001 is not changed.

Downgrade: the phase 1 and 2a builds do not read `permission_node` or the token `nodes` column. After migration 002 they see
the path grants and the legacy grants only. The upgrade notes state this. A downgrade is supported only before the first start
of this version, as the identity model already states for migration 001.

## D8. Repository-wide grants through the old endpoint

`PUT /api/identity/teams/{name}/grants` keeps its shape but accepts only path-scoped grants (`pathPrefix` set, role `READ`,
`DEPLOY` or `MAINTAIN`). A repository-wide grant is answered with 400 and a message that names the node endpoint. `GET` lists
the path-scoped grants only. Legacy roles stay refused with 400.

## D9. Node API

Contract details are in the identity model, section 7.6. In short:

- `GET` and `PUT` on `/api/identity/principals/{name}/nodes` and `/api/identity/teams/{name}/nodes`, body and response
  `{ nodes: [{ node, allowed }] }`. `PUT` replaces the whole list. An empty list is allowed for nodes (it means no nodes), and a
  duplicate node is 400. The response carries an `ETag`. `PUT` takes `If-Match` and answers 412 on a mismatch, like the other
  identity writes.
- A refused change (400, 409, 412) writes no audit event, and an unchanged list writes none either, as section 8.4 requires.
- `GET` and `PUT` on `/api/identity/teams/{name}/parents`, body and response `{ parents: [teamName] }`. Parents must be
  existing, non-private teams. A cycle, including a team as its own parent, and a chain deeper than 8 are answered with 409 and a
  message that names the rule. The check covers every team whose ancestry the change touches.
- Principal and team list and detail DTOs gain `nodes` as a summary: `nodeCount` and `isAdmin`. `instanceAdmin`,
  `instanceRole` and `grants` stay additive for one release. Their values are derived from the nodes (`instanceAdmin` is
  `isAdmin` of the principal or of the team's own nodes). Documented as deprecated.
- `/api/auth/me` gains `nodes`: the distinct patterns that decide allowed for the subject, token narrowing included. It
  answers the UI's gating questions. The exact decision for one node is the check endpoint.

## D10. Catalog, templates and check

- `GET /api/identity/nodes/catalog?prefix=`: the admin nodes (`STATIC`), `*` and `ingot.admin.*` (`WILDCARD`), per existing
  repository the four actions and `ingot.repository.<repo>.*` (`REPOSITORY`), and `ingot.repository.*.<action>` (`WILDCARD`).
  `prefix` filters by string prefix. Repositories come from the repository provider at request time, so new repositories
  appear without a restart.
- `GET /api/identity/nodes/templates`: `read`, `deploy`, `maintain`, `admin` (the bundles of D7 on `{repository}`) and
  `instance-admin` (`ingot.admin.*`). The templates are data only; applying one is the UI's job, which writes the node list.
- `GET /api/identity/check?principal=&node=[&token=]`: instance admin only, or the caller's own principal. `node` must be
  concrete (a wildcard is 400). It returns `allowed`, the `decision` (`source`, `subject`, `node`, `allowed`, `depth`) and a
  `trace` with one entry per candidate of every level, token included: `source`, `subject`, `node` (the pattern), `allowed`,
  `matched` and `reason`. The trace is the derivation: it shows every level consulted, including the ones that did not match.

## D11. Token narrowing

- Self-service tokens (`POST /api/identity/me/tokens`) and admin-created tokens (`POST /api/identity/principals/{name}/tokens`)
  accept `nodes`. An empty array is 400, like empty scopes. A node that fails D1 is 400 with the problem list.
- `scopes` stays accepted. It is translated to nodes by the D7 mapping at the boundary, and the token stores the nodes. The
  `scopes` in responses are the stored scopes, as before. A token created with nodes has no scopes.
- The legacy `/api/tokens` endpoint keeps its output and has no node input. Its tokens narrow nothing.

## D12. Last usable instance admin

A usable admin is an enabled principal with an unexpired persistent token whose node list is null or allows `ingot.admin.*`,
and which is admin through its own or its teams' nodes. The rule of section 7.4 of the identity model applies to every change
that would remove an admin: the node changes, the team membership changes, the deletions and the disabling. A change is refused
only when a usable admin existed before it. Temporary `--token` admins never count.

## D13. Manager checks

Manager-only endpoints map to `ingot.admin.<area>`. The identity endpoints map as follows: principal endpoints to `users`,
team endpoints to `teams`, token endpoints (`/api/identity/principals/{name}/tokens`, `/api/tokens`) to `tokens`. Other manager
checks keep `ingot.admin.*`, which is the former behaviour, so no area is opened by accident. `ContextDsl.managerOnly` and
`isManager` take an optional area and default to the node `ingot.admin.*`.

## D14. Audit

One new action, `NODES_CHANGED`, appended to `AuditAction`. Details: the subject kind and name, and `added` and `removed` as
`node=allowed`/`node=denied` pairs, no secrets. Parent changes are recorded as `TEAM_UPDATED` with `changed: parents`, so no
second new action is needed for them. Migration 002 writes no audit events, because it runs at start-up before any actor exists;
it logs one line with the counts, like migration 001.

## Consumers the other branches must follow

- **Login providers (OIDC, LDAP).** The `adminGroups` mapping must write the node `ingot.admin.*` allowed on the mapped team
  (`permission_node` with subject kind TEAM), not an `instance_role` row. Team synchronisation keeps writing `team_member` rows
  with source SYNC, and must not write nodes, parents or grants. Removing a synced admin goes through the last-admin rule (D12).
  The login code reads admin status through the evaluator (`isAdmin`), never through `instance_role`.
- **Frontend.** Builds against section 7.6 of the identity model: nodes, parents, catalog, templates, check and the summaries.
- **Authorization call sites.** `ContextDsl`, `RepositorySecurityProvider`, `AccessTokenFacade` and `RepositoryService` map
  their actions as in D4 and D6. `RepositoryService` deletes are `delete`, not `write`.

## Risks

- Path grants and node denials interact through the team level only. An operator who wants a path to be unreachable must
  remove the grant, not add a node. The trace shows this, and the docs say it.
- Wildcard repository matching uses the repository part of the node. Repositories are not otherwise validated, so a name with
  a dot is addressable, but only as an exact literal or through the whole-part wildcard.
- A machine principal's private team has legacy grants. An operator who adds nodes to a machine principal changes its effective
  rights only at the USER level, which is closer than the team level. The trace makes that visible.

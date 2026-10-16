# Proposal

## Why

The identity model (phases 1 and 2a, `docs/superpowers/specs/2026-10-15-identity-model.md`) gives every repository four
fixed roles (READ, DEPLOY, MAINTAIN, ADMIN) and one instance-wide admin flag. Operators cannot express "this CI bot may
deploy to snapshots but never delete", "this team inherits everything from the release managers except repository
settings", or "this account may open the users page but not the token page". Minecraft permission plugins (LuckPerms
style) solved the same problem with dotted permission nodes. ADR-INGT-0003 (accepted, kept in Outline) adopts that model
for Ingot.

## What Changes

- Teams and principals (users and machine accounts) carry **permission node lists**. Each entry is a node with the value
  allowed or denied. A denied node is shown as `-node`.
- Teams can **inherit** from parent teams. Cycles are refused on save, and the inheritance depth is limited to 8.
- The node schema is `ingot.repository.<repository>.<read|deploy|delete|manage>`, `ingot.admin.<area>` with the areas
  `settings`, `users`, `teams`, `tokens`, `maintenance`, `audit`, `console` and `dashboard`, `ingot.admin.*` (equal to the
  former instance admin) and `*` (everything). A wildcard is allowed only as a whole segment.
- Evaluation: the closest source wins (the principal's own nodes, then its teams, then inherited teams nearest first). Within
  a source the most specific node wins, and on a tie denial wins. No matching node means denied.
- Token scopes become **token node lists**. A token can only narrow its owner's rights. It is applied as an intersection and
  can never widen them.
- The former roles become **predefined node bundles** (templates) for the user interface. The former instance role becomes
  the node `ingot.admin.*`.
- Migration `002-permission-nodes` converts repository-wide team grants, instance roles and token scopes into nodes, once, in
  one transaction. Path-scoped grants and the legacy route grants keep their semantics.
- New endpoints: node lists of principals and teams, team parents, the node catalog (for autocomplete), the templates and a
  permission check that explains its decision. `/api/auth/me` gains the effective `nodes`.
- Changes are audited with the additive action `NODES_CHANGED`.
- Legacy routes of migrated tokens keep their exact old prefix matching. `LegacyAuthorizationParityTest` stays green without
  any edit.

### Out of scope

- The login providers (OIDC, LDAP group sync). They must switch from instance roles and grants to nodes, and the design
  lists what they must change. Their code lives on another branch and is not part of this change.
- The dashboard. The HTTP contract is documented in section 7.6 of the identity model so that the frontend can build against
  it.
- Path-level nodes. Nodes address repositories and admin areas only. File-level rights stay path grants.
- Repository-scoped cleanup endpoints (open point O-4 of the identity model).
- Removing the old tables, the old DTO fields or the legacy `/api/tokens` endpoint. Old fields stay for one release.
- Negation in the legacy `/api/tokens` endpoint, which has no node concept and keeps its output.

## Capabilities

### New Capabilities

- `identity-permission-nodes`: the node grammar, the storage of node lists and team parents, the evaluation with inheritance,
  negation and token narrowing, the migration from roles, the node catalog, templates, the permission check and the
  administration API.

## Impact

- **Operators:** no configuration, environment variable, volume, manifest or plugin change. Existing rights are converted
  in place on the first start of the new version. A downgrade to a build without this change does not see node rights, and
  the upgrade notes say so. **No operator action is required before or after the upgrade.** Nothing here is BREAKING.
- **Code:** `reposilite-backend` only, in `net.onelitefeather.ingot.identity` and in the authorization call sites
  (`ContextDsl`, `RepositorySecurityProvider`, `AccessTokenFacade`, `RepositoryService` manager checks).
- **Login providers (other branch):** the admin group mapping must set the node `ingot.admin.*` on the team, not an instance
  role. Team sync keeps writing `team_member` rows with source SYNC and must not write nodes or parents. See `design.md`.
- **Commits:** the change ships as several commits of one type each, all `feat(identity)` except the proposal and the
  documentation. Pull request title, squash-merged onto main:

  `feat(identity): add permission nodes`

## Verification

- Targeted tests only, run with `--max-workers=2`, never the full suites.
- `openspec validate add-permission-nodes --strict` passes.

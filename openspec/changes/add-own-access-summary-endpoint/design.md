# Design

## Context

- `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/api/IdentityApiEndpoints.kt` holds the identity routes. Each route
  declares an `@OpenApi` annotation and then a `ReposiliteRoute`. `permissionCheck` (around line 532) is `authenticated { ... }` and reads the
  caller through `identifier`. `repositoryAccess` (around line 384) is `managerOnly("users")`. `getTeam` (around line 220) is `managerOnly("teams")`.
- `com/reposilite/auth/AuthenticationFacade.kt:230-247` builds `SessionDetails` for `/api/auth/me` from `accessTokenFacade.subjectOf(identifier)`:
  `teams = subject.teams`, `nodes = PermissionEvaluator.effectiveNodes(subject)`, and `grants = subject.levels.flatMap { it.grants }`.
- `SessionDetails` has no parents, no instance admin flag, no display name, and no provider list. `PrincipalDetailDto` (`IdentityDtos.kt:56`) has the
  provider list through `PrincipalSummaryDto.providers`; the list comes from the login store.
- `docs/gitbook/explanation/identity-model.md` says the evaluator is one function of the principal, its teams and its tokens, and that every part of
  Ingot asks the same evaluator. The summary keeps that: it reads the subject, it does not decide.
- Private teams (`machine:` prefix) are not listed by the team endpoints. Their rights belong to one machine account.

## Goals

- A signed-in principal reads its own teams with their direct parents, its instance administrator flag, its effective nodes and denials, and its
  grants, without an administrator role.
- The answer is the caller's own data, and it cannot be pointed at another principal.
- The route reuses the evaluator and the subject of the request path.

## Non-goals

- A new permission model, a new role, a new evaluator, or a write path.
- Changing `/api/auth/me`.

## Decisions

### D1. Route and method

`GET /api/identity/me/access`, under the identity routes so it lives with the other identity documentation. It is `authenticated { }`, not
`managerOnly`. It takes no path or query parameter, so there is no way to name another principal. A request with a `principal` query parameter
ignores it; the answer is the caller's.

### D2. The answer

```
OwnAccessSummaryDto(
  principal: { name, displayName, kind, providers: [String] } | null,   // null for a temporary token with no stored principal
  instanceAdmin: Boolean,
  token: String?,                 // the name of the token that made the request; null for a cookie session
  tokenNarrowed: Boolean,
  teams: [{ name, parents: [String] }],   // direct parents only, private teams excluded
  nodes: [String],                // effective allowed nodes, token narrowing included
  denials: [String],              // denied nodes, from the principal, its teams and its token
  grants: [{ team, repository?, pathPrefix?, role }]
)
```

- `nodes` is `PermissionEvaluator.effectiveNodes(subject)`, the same list `/api/auth/me` gives.
- `denials` are read from the same subject, the rule entries with `allowed = false`. They are not computed here.
- `instanceAdmin` is `true` when the subject holds the instance role, directly or through a team, which is the rule the evaluator applies to an
  instance administrator. It is read from the subject, not computed from node text, so it matches the decisions of the check.
- `providers` come from the login store for the principal's id, as `GET /api/identity/principals/{name}` returns them.
- The shape is additive. Fields are never removed; new fields are added to the DTO.

### D3. Private teams

A machine account's private team is not in `teams`, consistent with the team list. Its nodes and grants appear in `nodes`, `denials` and `grants`
under the team name, so the rights stay visible. The alternative is open question 3.

### D4. Temporary tokens

A token without a stored principal (`SessionPrincipal.id == null`) gets `principal = null`, `teams = []`, `instanceAdmin` from its own node list, and the
token name. The answer is still 200; the page explains that the token has no owner record.

### D5. Errors

- No credential: `401`, through the existing authentication path.
- A token that does not exist any more: `404` with the existing `ErrorResponse`, as `/api/auth/me` does.
- Any other request: `200`.

## Risks

- **Drift from the check.** The summary and the check are two views of one subject. A test asserts that every node in `nodes` makes
  `GET /api/identity/check` answer allowed for the same caller, and that every denial answers denied, so the two cannot drift silently.
- **Size.** A large team tree gives a long answer. Parents are direct only, so the answer grows with the number of teams, not with the depth.

## Migration

None. The route is new. No stored data, no configuration and no client changes.

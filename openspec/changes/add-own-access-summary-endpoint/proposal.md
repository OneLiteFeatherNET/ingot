# Proposal

## Open maintainer questions

Each question has a default. The change is written for the default; a different answer changes the named requirement only.

1. **Route or extended session.** Default: a new read route, `GET /api/identity/me/access`, that returns the caller's own summary. Alternative:
   add optional fields (`parents`, `instanceAdmin`, `displayName`, `providers`) to the answer of `GET /api/auth/me`. The alternative touches
   every client that reads the session, so the default leaves that answer alone.
2. **Denials.** Default: the summary lists the denials the caller holds, directly or through a team or a token, in a separate list. Alternative:
   allowed nodes only, which hides the reason some requests are refused.
3. **Private teams.** Default: a machine account's own private team is not listed as a team, as the team list does not list it; its nodes and
   grants appear under the permissions and the repository access. Alternative: a single entry "Own access" that names the private team.

## Why

The account page has to answer "what may I do?" for the signed-in principal. The data is already there, but no single call returns it in a shape a
person can read:

- `GET /api/auth/me` returns team names, grants and effective nodes, token narrowing included. It does not return the parents of a team, the
  instance administrator flag or the display name after a reload.
- `GET /api/identity/teams/{name}` returns the parents, but it needs `ingot.admin.teams`. A team member who is not an administrator cannot read
  the team they are in.
- `GET /api/identity/principals/{name}` returns the providers and the display name, but it needs `ingot.admin.users`.
- `GET /api/identity/check` decides one node or one path at a time and cannot list what is held.

A person looking at their own account should not need an administrator to read their own teams. The information is theirs, so the answer is
limited to the caller and goes through the same evaluator that decides requests.

## What Changes

- **A read route for the caller's own summary.** `GET /api/identity/me/access` answers for the principal of the presented credential, cookie session
  or token. It takes no principal parameter. It is not an admin route.
- **The answer carries only the caller's own data:** the principal's name, display name, kind and linked providers; whether it is an instance
  administrator; its teams with their direct parents; its effective permission nodes, token narrowing included; its denials; its grants (team,
  repository, path prefix, role); and the name of the token that made the request, when there is one. It never carries a secret, a token list, or
  another principal.
- **One evaluator.** The route reuses `PermissionEvaluator.effectiveNodes` and the subject the request path reads. It adds no second rule.
- **Documented in OpenAPI** with the route's annotation, and in `docs/gitbook/how-to/check-access.md` next to the existing check.

Not BREAKING. The route is new and additive. No configuration key, environment variable, deployment manifest, stored data or existing route
changes. A Reposilite plugin, a client of `/api/auth/me` and the existing check see no change. The route answers 404 on a server built before it
exists, which the account page reads as "not available".

## Capabilities

### New Capabilities

- `own-access-summary`: the caller's own access summary as a read route.

## Impact

- **Operators:** nothing to change. A signed-in principal can read its own access; no one can read another principal's access through this route.
- **Plugin authors:** nothing to change. The route is part of the identity API, not a plugin extension point.
- **Code (backend):** `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/identity/api/IdentityApiEndpoints.kt` (one route), `IdentityDtos.kt` (one summary DTO),
  the identity facade (one method that builds the summary from the same subject as the check), and the test sources of the identity package.
- **Code (frontend):** none in this change. The account page's change consumes it.
- **Documentation:** `docs/gitbook/how-to/check-access.md` (one paragraph and the call).

## Verification

- Every red test in the tasks is seen to fail first.
- Unit tests of the identity facade pass with fixed subjects, with no database shared between tests.
- `./gradlew :reposilite-backend:test --tests '*OwnAccess*' --max-workers=2` runs through `heavy`, and the identity integration tests run with
  `:reposilite-backend:integration --tests` (not `integrationTest`).
- `openspec validate add-own-access-summary-endpoint --strict` passes, and the dash grep prints nothing.

## Out of scope

- Changing `/api/auth/me` (alternative 1 in the open questions).
- Showing another principal's access. That stays on the admin routes.
- Changing a team, a node, a grant, a token or a password through any route.
- Scope types beyond repositories and path prefixes (groupId namespaces, conditions, signing keys). The answer lists grants as they are today; a later
  change adds the new fields to the same route.

## Shipping

Proposal PR `docs(openspec): propose the account menu and profile rework`, shared with `rework-account-menu-and-profile`. Then one code PR under type
`feat` and scope `identity`:

`feat(identity): add a read endpoint for the caller's own access summary`

Commits: `test(identity)` for the red tests, `feat(identity)` for the route and the summary, `docs(gitbook)` for `check-access.md`. No file under
`openspec/` is in the code PR. This PR is merged before the account page's code PR is opened.

## Dependencies

- `rework-account-menu-and-profile` consumes this route. Its Your access section waits for this change to merge.
- `add-openapi-diff-gate`, if merged, must show this route as an addition only.

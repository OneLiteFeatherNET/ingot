# Tasks

Each group is one Conventional Commits type and is one commit or a short series of them. Tests land in the same commit as
the code they test and are written first: each test is seen to fail before the production code exists. Every test follows
F.I.R.S.T. (fast, independent, repeatable, self-validating, timely): a fresh database per test, an injected `Clock`, no sleeps,
no shared mutable state, assertions with messages. Test runs are targeted and use `--max-workers=2`.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose permission nodes`: this change (proposal, design, capability spec, tasks) and
  `openspec/config.yaml`. Verify: `openspec validate add-permission-nodes --strict` passes.

## 2. Storage and API for node lists

- [x] 2.1 `test(identity)`: write failing tests for the node grammar (every valid shape, every refusal with its problem list),
  for the store (unique `(subject, node)`, node lists replaced atomically, team parents with cascade), and for the node
  endpoints (`GET`/`PUT` principal and team nodes, `If-Match` 412, 400 problem list, `NODES_CHANGED` once, no event for an
  unchanged list). Verify: the tests fail on the missing classes.
- [x] 2.2 `feat(identity): store permission nodes for users and teams`: add `permission_node` and `team_parent` to
  `IdentityTables`, the node grammar parser (`NodePattern`), the store methods, the facade methods with ETags and audit, and the
  `/nodes` and `/parents` endpoints with their cycle and depth checks (D1, D2, D9, D14). Verify: the tests of 2.1 pass and
  `IdentityStoreTest` and `IdentityFacadeTest` still pass.

## 3. Evaluation

- [x] 3.1 `test(identity)`: write the evaluator matrix as failing tests, one behaviour per test: wildcards in each position,
  `*` alone, negation, specificity (literal parts), source precedence (direct over team over inherited), inheritance distance
  with nearest parent first, cycles, depth 8 accepted and 9 refused, token intersection (narrowing, no widening), default deny,
  disabled subject, instance admin through `ingot.admin.*` and `*`, repository names with dots. Verify: the tests fail before 3.2.
- [x] 3.2 `feat(identity): evaluate permission nodes with inheritance and negation`: extend `AccessSubject` with the ordered
  sources, implement the node decision with the trace (`PermissionEvaluator.decide`), the path decision with path and legacy
  candidates (D4), `canSee` and the listing rule (D6), and map the actions (D4). Verify: the evaluator matrix passes;
  `LegacyAuthorizationParityTest`, `PermissionEvaluatorTest` (updated to the new signature only where the old parameter
  `instanceAdmin` moved) and `IdentityAuthorizationTest` pass. The parity test must not be edited.
- [x] 3.3 Guard: `LegacyAuthorizationParityTest.kt` is not edited by any commit of this change. Verify: `git diff a0c424d3 -- '**/LegacyAuthorizationParityTest.kt'` is empty when the branch is complete.

## 4. Migration from roles, instance roles and scopes

- [x] 4.1 `test(identity)`: write failing migration tests on a realistic database: repository-wide grants of every role,
  an `ADMIN` on `*`, an instance role on a principal and on a team, a token with scopes `READ`, `WRITE`, `DELETE`,
  `MANAGE_REPOSITORY` and `INSTANCE_ADMIN`, a path-scoped grant, legacy grants; a second start creates no rows; a failing
  verification rolls back the marker; the path grants and legacy grants are unchanged. Verify: the tests fail before 4.2.
- [x] 4.2 `feat(identity): migrate repository roles to permission nodes`: `IdentityMigrations` marker `002-permission-nodes`,
  the conversion (D7), the removal of converted `team_grant` and `instance_role` rows, and the grants endpoint refusing
  repository-wide grants (D8). Verify: 4.1 passes, `LegacyAccessTokenMigrationTest` and `IdentityAdminIntegrationTest` (local
  variant) still pass.

## 5. Catalog, templates and permission check

- [x] 5.1 `test(identity)`: write failing tests for the catalog content (admin nodes, `*`, per repository the four actions plus
  `<repo>.*`, `*.<action>`, the kinds, the prefix filter, a repository created after start appears), the templates (five ids with
  their nodes), and the check endpoint (instance admin only, own principal allowed, wildcard 400, trace lists every consulted
  candidate with `matched` and `reason`). Verify: the tests fail before 5.2.
- [x] 5.2 `feat(identity): serve node catalog, templates and permission checks`: the catalog, the templates, the check endpoint
  (D10), the node summary in principal and team DTOs (`nodeCount`, `isAdmin`, the old fields kept), and `nodes` in
  `/api/auth/me` (D9). Verify: 5.1 passes, and the DTO tests (`IdentityListSummaryTest`) still pass.

## 6. Tokens narrowed by nodes

- [x] 6.1 `test(identity)`: write failing tests that a token created with `nodes` is narrowed (its owner may deploy, the token
  may not), that an empty `nodes` array is 400, that `scopes` is translated, that the last-admin rule counts only tokens whose
  nodes allow `ingot.admin.*` (D12), and that the manager endpoints map to their areas (D13) so that `ingot.admin.users` opens
  the principal endpoints only. Verify: the tests fail before 6.2.
- [x] 6.2 `feat(identity): narrow tokens with permission nodes`: the `nodes` column on `principal_token`, token creation and
  update with `nodes`, the scope translation, the last-admin rule, the area mapping in `ContextDsl`, and the `NODES_CHANGED`
  audit for token node changes. Verify: 6.1 passes, and `TokenUsageTrackingTest`, `OwnTokensTest` and
  `TemporaryTokenNameCollisionTest` still pass.

## 7. Documentation

- [x] 7.1 `docs(identity)`: add section 7.6 to `docs/superpowers/specs/2026-10-15-identity-model.md` with the contract as
  implemented (nodes, parents, catalog, templates, check, DTO summaries, `/api/auth/me`, token `nodes`), the migration 002
  behaviour and the downgrade note, and mark O-4 and O-8 as changed where they are. Verify: every path and field in the
  section matches a route in `IdentityApiEndpoints.kt`.
- [ ] 7.2 `docs(identity)`: note in the upgrade notes that a downgrade to a build without this change does not see node rights.
  Verify: the note is in the same place as the migration 001 downgrade note. Operator-facing text only, no internal details.

## 8. Pull request

- [ ] 8.1 Open the pull request with the title `feat(identity): add permission nodes` and a body that names the spec section
  7.6 and the login provider changes listed in `design.md`. Verify: the title passes the commit-style hook and contains no
  attribution line.

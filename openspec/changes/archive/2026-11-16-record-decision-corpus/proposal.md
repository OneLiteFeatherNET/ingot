# Proposal

## Open maintainer questions

1. **Where does the golden corpus live?** Default: as Kotlin fixtures in the test sources under `net.onelitefeather.ingot.identity.corpus`, not as a regenerable file. A regeneration step would let a changed decision look like an updated fixture.
2. **Are today's surprises recorded as they are?** Default: yes. Rows such as "a personal deny of `*.read` beats a team allow" and "an instance admin ignores repository denials" record current behaviour, each with a note. Later changes that alter a row name it in a `changesIn` note, so the diff is visible in their own commit.
3. **Are upload paths recorded in raw form as well?** Default: yes. Each path row carries the raw string an upload passes today and the normalized string a read passes, so change `canonicalize-authorization-paths` has a before-and-after table.

## Why

Phase A changes the decision path itself: an authorizer chain, a policy revision and a decision log. Several decision functions answer the same question in slightly different ways (`PermissionEvaluator.allows`, `decide`, `decidePath`, `canSee`, `canListHidden`, the artifact listing predicates, `isInstanceAdmin` and `allowsAdminArea`). Existing tests cover parts of this (`PermissionEvaluatorTest`, `LegacyAuthorizationParityTest`, `SearchAccessParityTest`, `ArtifactReadVisibilityTest`), but nothing records one matrix of decisions across nodes, path grants, legacy routes, legacy MANAGER tokens, admins, token narrowing and path variants. Without that matrix, a refactor that is meant to change nothing has no proof that it changed nothing.

## What Changes

- Adds a golden decision corpus: a fixed matrix of subjects, requests and expected outcomes, recorded from the code as it is on main.
- The matrix covers node rules, path grants, legacy routes, legacy MANAGER tokens, instance admins, token narrowing, disabled principals, the instance-admin shortcut, and path variants (encoded characters, dot segments, double slashes and mixed case).
- Each row names the decision function it exercises. A harness test fails when a row is missing, so a row cannot be dropped silently.
- The corpus is pure unit tests: no server, no database, no clock, no shared state.
- No production code changes. Later changes may alter a recorded row only with a `changesIn` note and a matching change in their proposal.

### Out of scope

- Any production change, including fixes to the surprises the corpus records.
- HTTP-level behaviour. It needs a running server and is covered by the existing integration tests. The corpus stops at `AccessTokenFacade` and the evaluator functions it calls.
- A corpus for the search compiler beyond the READ check already covered by `SearchAccessParityTest`.

## Capabilities

### New Capabilities

- `identity-authorization-corpus`: the golden matrix of authorization decisions, its coverage rules and its equality check.

## Impact

- **Operators:** no configuration, environment variable, volume, manifest, data or plugin change. Nothing here is BREAKING.
- **Code:** test sources of `reposilite-backend` only.
- **Commit:** a single `test(identity)` commit. Pull request title, squash-merged onto main:

  `test(identity): record a golden authorization decision corpus`

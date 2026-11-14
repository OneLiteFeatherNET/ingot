# Proposal

## Open maintainer questions

1. **Do listing predicates move into the chain in phase A?** Default: no. `canSee`, `canListHidden`, `readableArtifactSubtrees` and the search compiler keep calling the built-in evaluator directly. Listing needs filter semantics (`toQuery`), which belong to phase B or C. The pull request lists them as the next step. Alternative: wrap them now with a post-filter adapter, which widens this change.
2. **Is the chain internal in phase A?** Default: yes, `internal` in `net.onelitefeather.ingot.identity.authorization`. The public SPI in `com.reposilite.plugin.api.authorization` is change `add-authorization-spi`, which stabilizes against real use.
3. **Does the explain output change?** Default: no. `/api/identity/check` keeps its JSON shape and its trace entries.

## Why

Check decisions come from several functions with partly duplicated logic. `callerHolds` re-implements the action mapping outside the evaluator (`IdentityFacade.kt:670-701`), and the admin checks, the path checks and the holds checks each call the evaluator in their own way. Later phases need one place where an authorizer can answer Allow, Deny or NoOpinion, and where restrictions, bindings and plugin authorizers can sit. Phase A puts the existing evaluator into that place without changing a single decision.

## What Changes

- An internal `Authorizer` interface returns `Allow`, `Deny` or `NoOpinion`, with the trace the explain output needs.
- An `AuthorizationChain` evaluates, in order: disabled principal, restrictions (none in phase A), the built-in authorizer, bindings (none), plugin authorizers (none), default deny.
- The built-in authorizer wraps `PermissionEvaluator` without changing its logic. "No matching rule" becomes NoOpinion, and an explicit deny stays Deny.
- Check decisions are routed through the chain: `AccessTokenFacade.hasAccessTo`, `allowsAdminArea` and the admin checks, the `/check` endpoint's `decide` and `decidePath` calls, the holds checks of the guarded writes, and `ArtifactListingFilter`'s single allow check.
- The token narrowing stays inside the built-in authorizer in phase A. The instance-admin shortcut depends on it (`PermissionEvaluator.kt`, `decideRequest`), and moving it out would change decisions.

### Out of scope

- Restrictions, bindings, the credential ceiling as a separate step, and plugin authorizers. Their slots are empty in this change.
- Listing predicates and the search compiler (question 1).
- Explain additions for new policy types, and any operator-facing configuration. The chain order is fixed in phase A.

## Capabilities

### New Capabilities

- `identity-authorizer-chain`: the internal authorizer contract, the fixed order of the chain and the rule that no decision changes.

## Impact

- **Operators:** no change. No configuration key, environment variable, volume, manifest or plugin change. Nothing here is BREAKING.
- **Code:** `reposilite-backend`, in `net.onelitefeather.ingot.identity.authorization` (new) and at the call sites listed above. `PermissionEvaluator`'s public signatures stay as they are.
- **Tests:** no edits to `PermissionEvaluatorTest`, `IdentityAuthorizationTest`, `LegacyAuthorizationParityTest`, `SearchAccessParityTest`, `ExternalGroupSyncTest` or the corpus of `record-decision-corpus`.
- **Commits:** `test(identity)` for the chain contract, then `refactor(identity)` for the chain and the call sites. Pull request title, squash-merged onto main:

  `refactor(identity): route every decision through an authorizer chain`

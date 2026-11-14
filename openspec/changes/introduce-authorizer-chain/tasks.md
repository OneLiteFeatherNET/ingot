# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: fake authorizers built per test, no sleeps, no shared mutable state, assertions that name the stage that decided. Test runs go through the slot runner with `--max-workers=2`.

## 1. Contract

- [ ] 1.1 `test(identity)`: write failing tests first. Cases: each stage's Allow and Deny stops the chain; NoOpinion from every stage ends in Deny; the disabled principal is refused before any stage; the empty restrictions, bindings and plugin slots contribute NoOpinion; the order of the stages is the one in `design.md`; the trace of a decision is passed through unchanged. Verify: the tests fail because `AuthorizationChain` does not exist.
- [ ] 1.2 `test(identity)`: write failing tests for the built-in authorizer's mapping: "no matching rule" gives NoOpinion, an explicit deny gives Deny, and the instance-admin shortcut is preserved for a token that does not deny. Verify: the tests fail before 2.1.

## 2. Refactor

- [ ] 2.1 `refactor(identity)`: add `Authorizer`, `AuthorizerDecision`, `AuthorizationChain` and `BuiltInAuthorizer` in `net.onelitefeather.ingot.identity.authorization`. `BuiltInAuthorizer` calls `PermissionEvaluator` and changes no logic. Verify: 1.1 and 1.2 pass.
- [ ] 2.2 `refactor(identity)`: route `AccessTokenFacade.hasAccessTo`, `allowsAdminArea`, the admin checks, `IdentityFacade`'s `decide` and `decidePath` calls, the holds checks of `callerHolds`, and `ArtifactListingFilter`'s allow check through the chain. Verify: no public signature of `PermissionEvaluator` changes, and the corpus passes.
- [ ] 2.3 `refactor(identity)`: list the listing predicates and the search compiler in the pull request as the next step. Verify: each remaining direct call to `PermissionEvaluator` is named in the pull request.

## 3. Equality checks

- [ ] 3.1 Verify that the corpus of `record-decision-corpus` is unchanged: `git diff origin/main -- '**/corpus/**'` is empty in this change.
- [ ] 3.2 Verify that `PermissionEvaluatorTest`, `IdentityAuthorizationTest`, `LegacyAuthorizationParityTest`, `SearchAccessParityTest` and `ExternalGroupSyncTest` have no edits in this change.
- [ ] 3.3 Verify the trace: a `/check` fixture produces the same JSON before and after, compared by a test that names the field that differs.

## 4. Verification

- [ ] 4.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*' --max-workers=2`, plus `SearchAccessParityTest` and `ArtifactReadVisibilityTest`.

## 5. Pull request

- [ ] 5.1 After the proposal pull request is merged, open the code pull request with the title `refactor(identity): route every decision through an authorizer chain`.

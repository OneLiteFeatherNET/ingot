# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: fixtures built per test, an injected `Clock`, no sleeps, no shared mutable state, assertions that name the team and the caller. Test runs go through the slot runner with `--max-workers=2`. Configuration changes update the matching page under `docs/gitbook/`.

## 1. Reproduce the gap

- [ ] 1.1 Write a reproduction test first, reusing the fixture of `ExternalGroupSyncTest`, that shows the gap on main. The details are kept in the security advisory. Verify: the test is red on main, which proves the gap before any fix.
- [ ] 1.2 Verify, in the same test file, what `updateTeam` stores for a blank `externalRef` (`IdentityFacade.kt`, the `updateExternalRef` flag), and whether clearing a reference is possible through the API. Verify: the answer is written into the pull request description.

## 2. Guard tests

- [ ] 2.1 `test(identity)`: write failing tests first. Cases: a caller with `ingot.admin.teams` only, in `warn`, creates and updates a link with 201 and 200, and one `EXTERNAL_REF_UNGUARDED` event is recorded; the same caller with no switch set (the default `enforce`) gets 403; a caller with `ingot.admin.idp` passes in `enforce`; a caller with `ingot.admin.*` passes; clearing a reference counts as a change; an existing reference is untouched by a later unrelated update; a node `ingot.admin.idp` is accepted and `ingot.admin.idp` is covered by `ingot.admin.*`. Verify: the tests fail before 2.2.

## 3. Guard and switch

- [ ] 3.1 `fix(identity)`: add the admin area `ingot.admin.idp` to the admin areas of the node grammar, and the `EXTERNAL_REF_UNGUARDED` audit action. Verify: the grammar tests and `IdentityApiEndpoints` tests still pass.
- [ ] 3.2 `fix(identity)!`: add the guard to `createTeam` and `updateTeam`, with the mode read from `INGOT_IDENTITY_IDP_GUARD` and the `REPOSILITE_IDENTITY_IDP_GUARD` fallback, default `enforce`. The caller is passed from the route the way `replaceGrants` receives it. The commit carries the `!` and a `BREAKING CHANGE:` footer that names the IdP right and the `INGOT_IDENTITY_IDP_GUARD=warn` fallback. Verify: 2.1 passes.

## 4. Documentation

- [ ] 4.1 `docs(identity)`: document the area `ingot.admin.idp`, the switch and the three modes in `docs/gitbook/explanation/identity-model.md` and in the identity provider guide that describes external references. Verify: the pages state the default `enforce`, the `warn` fallback, the migration step for automation that links teams, and that existing references are kept.

## 5. Verification

- [ ] 5.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*' --max-workers=2`, including `ExternalGroupSyncTest` unedited.
- [ ] 5.2 Run the "Container images" job's upgrade rehearsal and check the three drop-in questions. Verify: the job is green.

## 6. Pull request

- [ ] 6.1 After the proposal pull request is merged, open the code pull request with the title `fix(identity)!: require idp rights to link teams to provider groups`.

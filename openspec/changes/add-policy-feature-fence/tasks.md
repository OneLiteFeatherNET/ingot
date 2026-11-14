# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: a temporary database file per test (`@TempDir`), an injected `Clock`, no sleeps, no shared mutable state, assertions with messages. Test runs go through the slot runner with `--max-workers=2`.

## 1. Known features and the startup check

- [ ] 1.1 `test(identity)`: write failing tests first. Cases: a fresh database has an empty `policy_feature` table; a table with only known names passes; an unknown name stops startup and the message names the feature and the running version; the check runs before both identity migrations (a recording fake shows the order); the table exists on SQLite and H2. Verify: the tests fail because `PolicyFeatureFence` does not exist.
- [ ] 1.2 `feat(identity)`: add the `policy_feature` table to `IDENTITY_TABLES`, the `PolicyFeatureFence` check with an empty `KnownPolicyFeatures` set, and the call in the persistent repository factory before the legacy migration. Verify: 1.1 passes, and `IdentityStoreTest` still passes.
- [ ] 1.3 `docs(identity)`: add the downgrade rule to `docs/gitbook/explanation/identity-model.md`: a rollback below this release is unsupported once a newer build has written a feature. Verify: the page renders and states the rule in one paragraph.

## 2. Verification

- [ ] 2.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*Fence*' --max-workers=2`, plus `IdentityStoreTest`.
- [ ] 2.2 Run the upgrade rehearsal of the "Container images" job against an existing `/app/data` from the upstream image. Verify: the server starts, adopts the data and creates the empty table.

## 3. Pull request

- [ ] 3.1 After the proposal pull request is merged, open the code pull request with the title `feat(identity): refuse to start on unknown policy features`.

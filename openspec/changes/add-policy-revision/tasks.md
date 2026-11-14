# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: two stores on a temporary SQLite file per test (`@TempDir`), an injected `Clock`, a fake `Ticker` for the cache bound, `refreshOnce()` instead of a timer, no sleeps, no shared mutable state. Test runs go through the slot runner with `--max-workers=2`. Configuration changes update the matching page under `docs/gitbook/`.

## 1. Failing tests

- [ ] 1.1 `test(identity)`: write failing tests first. Cases: each identity write increments the revision once, in the same transaction; a rolled-back write leaves it unchanged; a revocation through store A is refused by store B after `refreshOnce()`; a cached credential of an older revision is dropped on its next use; the one-minute bound still applies to a fresh entry (fake ticker); a write reads the revision before deciding; a single store behaves as before. Verify: the tests fail because `policy_revision` does not exist.

## 2. Revision and refresh

- [ ] 2.1 `feat(identity)`: add the `policy_revision` table to `IDENTITY_TABLES`, the increment in every identity write, and the `AuthorizationRevision` with `refreshOnce()` and the loop. Verify: 1.1 passes for the storage cases.
- [ ] 2.2 `feat(identity)`: make the authentication cache revision-aware in `AuthenticationFacade`, and read `INGOT_AUTHORIZATION_REFRESH` with the `REPOSILITE_AUTHORIZATION_REFRESH` fallback. Verify: 1.1 passes for the cache and the revocation cases, and `AccessTokenFacadeTest` and `TokenStateChangedEventTest` pass unedited.

## 3. Documentation

- [ ] 3.1 `docs(identity)`: update the staleness statement in `docs/gitbook/explanation/permission-evaluation.md` (the cache is the credential cache, and revocations reach other replicas within the refresh interval). Update `docs/gitbook/explanation/multi-replica-operation.md`. Verify: both pages state the default of 2 seconds and the one-minute upper bound.

## 4. Verification

- [ ] 4.1 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*' --max-workers=2`, plus `AccessTokenFacadeTest`.
- [ ] 4.2 Run the multi-replica smoke test that the cluster changes provide, if it is on main when this change is built (verify: search for the smoke test before running it).
- [ ] 4.3 Run the "Container images" job's upgrade rehearsal and check the three drop-in questions. Verify: the job is green.

## 5. Pull request

- [ ] 5.1 After the proposal pull request is merged, open the code pull request with the title `feat(identity): invalidate authorization caches across replicas`.

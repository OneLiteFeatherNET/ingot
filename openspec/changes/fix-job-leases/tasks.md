# Tasks

This change ships as one code pull request titled `fix(scheduling): expire and take over cleanup and audit job leases`. Each
group is one or more commits, one Conventional Commits type each. The scope is `scheduling` unless the group names another.

Every test follows F.I.R.S.T.: no sleeps or waits by time, no system clock (the clock is injected as a `java.time.Clock`), no
shared mutable state, a fresh in-memory database per test, and assertions with messages. Each test is written first and seen to
fail before the production code exists. Gradle runs go through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2`, targeted
and never the full suite. Integration tests run as `:reposilite-backend:integration --tests '<pattern>'`. Only one Testcontainers
or MariaDB job runs at a time.

## 1. Proposal

- [ ] 1.1 `docs(openspec)`: propose fix-job-leases: this change (proposal, design, capability specs, tasks) and nothing else.
  Verify: `openspec validate fix-job-leases --strict` passes and the dash grep from the proposal prints nothing.

## 2. Verify the problem first

The design depends on two open verifications from the roadmap. Both come before any production code. Task 3.3 depends on the
result of 2.2.

- [ ] 2.1 `test(scheduling)`: write failing tests that show the current gaps, against the in-memory database: (a) two cleanup
  services that share one claim store both start a manual run of the same policy, and the second is not refused; (b) a daily key
  claimed by a replica that then stops cannot be run by the other replica after any time has passed. Verify: both tests fail for the
  stated reason, with a message that names the gap.
- [ ] 2.2 Spike, no commit: run two connections against one MariaDB Galera node (Testcontainers, through `heavy`, alone) that race a
  conditional `UPDATE` of one lease row 200 times. Record how many calls win and whether any returns error 1213 or SQLSTATE 40001.
  Verify: the counts are written into the pull request description. If there is no conflict error, task 3.4 is dropped and the
  retry is not added. Also check that `GET_LOCK` is rejected on the same node, as the roadmap states.

## 3. Lease API (`test(scheduling)` then `fix(scheduling)`)

- [ ] 3.1 `test(scheduling)`: write failing tests for `SqlScheduledJobClaims` with an injected clock, one behaviour per test: grant
  when the key is absent; refusal while the lease is live; grant after the expiry, with the clock moved and no sleep; a done key is
  refused forever; `extend` by the owner only; `complete` by another owner changes nothing; `complete` with the done flag refuses later
  grants; a released key is granted again; retention deletes a released row older than seven days and keeps a row with a live lease;
  `Unshared` grants everything. Verify: the tests fail on the missing methods.
- [ ] 3.2 `fix(scheduling)`: add the three nullable columns through `createMissingTablesAndColumns`, implement `lease`, `extend` and
  `complete` as conditional single-row updates after an `insertIgnore` of the key, change the retention delete to keep live leases, and
  override the three methods in `Unshared`. Verify: 3.1 passes and the existing `SqlScheduledJobClaimsTest` passes unedited.
- [ ] 3.3 `test(scheduling)`: write the retry tests only if 2.2 showed a conflict error. A fake operation throws error 1213 twice and then
  succeeds; another throws on every attempt. The delay comes from an injected function that records the calls. Verify: the tests fail
  on the missing retry.
- [ ] 3.4 `fix(scheduling)`: add the bounded retry of 3.3 around the lease operations, with the delay injected. Skip this task when 2.2
  found no conflict error. Verify: 3.3 passes.

## 4. Cleanup runs (`test(cleanup)` then `fix(cleanup)`)

- [ ] 4.1 `test(cleanup)`: write failing tests for `CleanupService`, with the claim store injected: a second run of a live policy
  answers `Cleanup policy <id> is already running` on another instance and deletes nothing; the active lease is released after a
  run that throws; a lease taken over during the deletion loop stops the next deletion and records `lease lost`; the lease is extended
  at most once every 20 seconds of the injected clock. Verify: the tests fail on the missing behaviour.
- [ ] 4.2 `fix(cleanup)`: give `CleanupService` the claim store, take `cleanup:<policy>:active` in `run`, renew it from the deletion
  loop, release it in `finally`. Keep the in-JVM set as the first check. Verify: 4.1 passes and `CleanupServiceTest` passes unedited.
- [ ] 4.3 `test(cleanup)`: write failing scheduler tests in `CleanupSchedulerTest` and `CleanupSchedulerClaimTest`: a run starts at
  the first tick after the scheduled time; a run whose window of six hours has passed does not start; a second replica starts no run
  after the first completed the day; a replica whose predecessor stopped mid-run takes over after the expiry and runs once; a
  scheduled run that finds the manual lease held logs a skip and does not retry that day. Replace the test
  `should not start a policy outside its scheduled minute` by `should not start a policy after its catch-up window`, because catch-up
  changes that rule. Verify: the new tests fail, and only the replaced test changes its name and assertion.
- [ ] 4.4 `fix(cleanup)`: in `CleanupScheduler`, drop the exact-minute check and take the day lease with the done flag before each
  scheduled run, with the window constant of six hours. Hold the day lease during the run through the renewal of 4.2. Verify: 4.3 passes.

## 5. Audit purge (`test(audit)` then `fix(audit)`)

- [ ] 5.1 `test(audit)`: write a failing test in `AuditPurgeClaimTest`: a purge holder that stops before it deletes lets another
  replica run the purge once for the same day after the expiry, and no third run happens that day. Verify: the test fails on the
  insert-once claim.
- [ ] 5.2 `fix(audit)`: take the purge's daily key through `lease` with the done flag in `AuditFacade.purgeExpired`. Verify: 5.1 passes
  and the existing `AuditPurgeClaimTest` cases pass.

## 6. Documentation (`docs(cleanup)`)

- [ ] 6.1 `docs(cleanup)`: update `docs/gitbook/reference/cleanup-policies.md` (the Schedule section: catch-up within six hours,
  the cluster-wide run lock and the `409` answer), `docs/gitbook/how-to/set-cleanup-policies.md` (the schedule note and the
  `already running` row of the table) and `docs/gitbook/reference/upgrade-changes.md` (the change of schedule behaviour, and the mixed
  version note from design decision 6). No environment variable or manifest changes. Verify: the pages no longer say "Ingot does not
  catch up later", and the dash grep prints nothing.

## 7. Pull request

- [ ] 7.1 Open the pull request titled `fix(scheduling): expire and take over cleanup and audit job leases` with `gh pr create`, using
  the repository template, the result of the 2.2 spike, and the catch-up decision from design decision 4 in the description. Verify:
  the title is exactly the one above and the description has no session link or attribution line.

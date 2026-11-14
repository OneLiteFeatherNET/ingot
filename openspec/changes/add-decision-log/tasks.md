# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: fake sinks per test, injected `Clock` and identifier supplier, a latch with a bounded wait instead of a sleep, a captured appender for log assertions, no shared mutable state. Test runs go through the slot runner with `--max-workers=2`. Configuration changes update the matching page under `docs/gitbook/`.

## 1. Failing tests

- [ ] 1.1 `test(identity)`: write failing tests first. Cases: a denied check writes exactly one record with the listed fields; an allowed read writes none; an allowed write writes one; a refused admin operation writes one; a record holds the raw resource only when it differs; `off` writes nothing; in `async`, a full queue drops the record, increments the counter and does not delay the answer; in `sync`, a failing sink refuses the request with 503 and applies no decision; the drop warning is asserted through a captured appender. Verify: the tests fail because the decision record does not exist.

## 2. Record and modes

- [ ] 2.1 `feat(identity)`: add the record type, the two audit actions, the bounded queue and single writer for `async`, the synchronous path for `sync`, and the reader of `INGOT_DECISION_LOG` with the `REPOSILITE_DECISION_LOG` fallback. Verify: 1.1 passes.
- [ ] 2.2 `feat(identity)`: call the recorder from the authorization chain for the decisions of D2, with the revision from `add-policy-revision` when it is present and null otherwise. Verify: the corpus of `record-decision-corpus` passes unchanged, which shows that recording does not change a decision.

## 3. Documentation

- [ ] 3.1 `docs(identity)`: document the decision records, the three modes and the counter in `docs/gitbook/explanation/permission-evaluation.md`, and the effect on the audit table in `docs/gitbook/explanation/drop-in-compatibility.md`. Verify: both pages name the default `async` and the way to turn the log off.

## 4. Verification

- [ ] 4.1 Verify that the audit retention (`CLEANUP_RUN`) covers the new rows, or record in the pull request that it does not. Verify: the answer is written into the pull request description.
- [ ] 4.2 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.*' --max-workers=2`, plus the audit tests in `reposilite-backend/src/test` that exist on main (verify the file names before running).
- [ ] 4.3 Run the "Container images" job's upgrade rehearsal and check the three drop-in questions. Verify: the job is green.

## 5. Pull request

- [ ] 5.1 After the proposal pull request is merged, open the code pull request with the title `feat(identity): log authorization decisions`.

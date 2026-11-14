# Tasks

One Conventional Commits type per commit. Every test follows F.I.R.S.T.: fixtures built per test, no sleeps, no system time, no shared mutable state, assertions with messages that name the row. Test runs are targeted and go through the slot runner with `--max-workers=2`.

## 1. Corpus

- [ ] 1.1 Write the harness test first. It fails while the corpus is empty and reports each missing row by its identifier. Verify: the test is red on the commit before 1.2.
- [ ] 1.2 Build subject fixtures without a database: principals, teams, parents, nodes, grants, token node lists and legacy levels. Each test builds its own fixture.
- [ ] 1.3 Record node rows: repository nodes, wildcards in each position, artifact nodes, admin areas, `*`, negation, specificity ties and source precedence. Verify: each row cites the evaluator function it calls.
- [ ] 1.4 Record path grants and legacy routes, including the segment-bounded grant against the unbounded, case-insensitive legacy prefix.
- [ ] 1.5 Record instance admins, token narrowing, the disabled principal and the instance-admin shortcut on path requests (repository denials ignored).
- [ ] 1.6 Record path variants: `%2e%2e`, `%2F`, `..`, doubled slashes and mixed case. Each row has its raw form (what uploads pass, `ContextDsl.kt`) and its normalized form (what reads pass, `Location`). Mark rows that change later with `changesIn`.
- [ ] 1.7 Record the listing predicates: `canSee`, `canListHidden`, `readableArtifactSubtrees`, `hasArtifactReadRule`, and `allows` for READ as the search compiler uses it.
- [ ] 1.8 Add the equality test: every recorded row is evaluated and compared, and a failure names the row. Verify: removing one row turns the harness red.
- [ ] 1.9 Check that no production file changed. Verify: `git diff --stat origin/main -- reposilite-backend/src/main` is empty.
- [ ] 1.10 Run the targeted tests: `heavy ./gradlew :reposilite-backend:test --tests 'net.onelitefeather.ingot.identity.corpus.*' --max-workers=2`, plus `PermissionEvaluatorTest`, `LegacyAuthorizationParityTest` and `SearchAccessParityTest`. Verify: all pass without edits to those three files.
- [ ] 1.11 Commit `test(identity): record a golden authorization decision corpus` with `git commit -s`.

## 2. Pull request

- [ ] 2.1 After the proposal pull request is merged, open the code pull request with the title `test(identity): record a golden authorization decision corpus`.

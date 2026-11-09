# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `frontend`. This change has
no new unit tests, so the test-first step is the throwaway pull request in group 5, which must show the gate red before the
gate is trusted. Every test that exists or is added follows F.I.R.S.T.: no sleeps or waits by time, no system clock, no
shared mutable state, a temporary directory per test where files are involved, and assertions with messages. Test runs
go through `heavy`. Do not run Gradle for this change.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose run-frontend-checks-in-ci`: this change and nothing else. Verify:
  `openspec validate run-frontend-checks-in-ci --strict` passes and the dash grep from the proposal prints nothing.

## 2. Baseline on main

- [ ] 2.1 No commit. In a worktree from the up-to-date `origin/main`, run `npm ci`, `npm test` and `npm run eslint` in
  `reposilite-frontend` through `heavy`. Record the pass and fail counts, the test file count and the ESLint error and
  warning counts in the pull request description. Verify: the numbers are recorded. If a test fails on `main`, stop and
  report it to the maintainer; it is fixed in its own change first, and this change does not merge before that.

## 3. ESLint warn phase

- [ ] 3.1 `ci(frontend)`: in `reposilite-frontend/eslint.config.js`, add a helper that maps every rule severity of the
  configuration to `warn`, including the array form `['error', options]`, and apply it to `js.configs.recommended`, the
  Vue preset and the local rules. Keep the rule set unchanged. Verify: `npm run eslint` exits 0 on `origin/main` and
  prints the warning count; a scratch file with an unused variable and a semicolon, run through the script and deleted
  afterwards, prints warnings and exits 0.

## 4. Frontend job

- [ ] 4.1 `ci(frontend)`: add a `frontend` job to `.github/workflows/ci.yml`, after `licenses`, with the same triggers as the
  file. Steps: checkout (`actions/checkout@v6`), setup Node 24 with the npm cache keyed on
  `reposilite-frontend/package-lock.json` (as the `licenses` job does), `npm ci`, `npm run eslint`, `npm test`, all in
  `reposilite-frontend`. Keep `permissions: contents: read` at the top level. Verify: the YAML parses, and the job appears
  in the pull request checks with the step names above.

## 5. Gate proof (no commit)

- [ ] 5.1 Open a throwaway draft pull request from a branch with one failing `*.test.js` assertion and one deliberate lint
  violation. Verify: the `Frontend` check is red because of the test, the log shows the lint warning, and the `Licenses`
  check is unaffected. Then fix the test and confirm the check turns green. Close the pull request and delete the branch
  without merging.
- [ ] 5.2 No commit. Verify: `git diff --name-only origin/main` on the final branch lists only
  `.github/workflows/ci.yml` and `reposilite-frontend/eslint.config.js`, and no file under `docs/gitbook/` changes, because no
  setting, environment variable or manifest changes.

## 6. Pull request

- [ ] 6.1 Open the code pull request with the title `ci(frontend): run vitest and eslint in the frontend pipeline`.
  Read `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of a contributor whose
  broken component test passed the pipeline, and of the warn phase that lets the linter report without blocking. It
  carries the baseline numbers from 2.1 and the result of 5.1. It carries no session link and no attribution line.
  Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and
  the body has no em or en dash.

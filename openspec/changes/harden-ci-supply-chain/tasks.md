# Tasks

Each group is one or more commits of one Conventional Commits type each, all with the scope `ci(security)`. This change
touches CI configuration only. No group changes a configuration key, environment variable, image or deployment manifest,
so no page under `docs/gitbook/` is updated.

The tests for workflow files are checks that run before the change and fail, then pass after it. Each group names its
red check. A check uses `grep` or a YAML parse, not a sleep, not a clock and not a network call that the result depends on.
Gradle is not involved; no task runs the full suite.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose harden-ci-supply-chain`: this change and nothing else. Verify:
  `openspec validate harden-ci-supply-chain --strict` passes and the dash grep from the proposal prints nothing.

## 2. C1 Pin actions by commit SHA

- [ ] 2.1 `ci(security)`: red check first. `git grep -nE 'uses: [^ ]+@' -- .github/workflows | grep -vE '@[0-9a-f]{40}'`
  prints the 18 tag-pinned lines (16 actions and 2 reusable workflow calls). Keep the output as the baseline.
- [ ] 2.2 `ci(security)`: replace each tag with the full SHA of the commit the tag resolves to (dereference annotated tags),
  and add `# vX.Y.Z` with the exact release tag. Keep every major version. Verify: the red check of 2.1 prints nothing, and
  `grep -nE 'uses: .+@[0-9a-f]{40}' .github/workflows/*.yml` shows a `# v` comment on every line. The code pull request's
  CI run stays green with the same action majors.

## 3. C2 Renovate keeps the pins

- [ ] 3.1 `ci(security)`: red check first. `renovate.json` extends `OneLiteFeatherNET/renovate:default` and no digest pinning
  helper, so `grep -c 'pinGitHubActionDigests' renovate.json` prints `0`.
- [ ] 3.2 `ci(security)`: add the GitHub Actions digest pinning preset to `extends` in `renovate.json`. Verify: the check of
  3.1 finds the preset, and a Renovate dry run on the branch shows the grouped update for `actions/checkout` changing the SHA
  and the comment together. The result for `OneLiteFeatherNET/workflows` follows the rule recorded in `design.md`.

## 4. C3 Verified npm install

- [ ] 4.1 `ci(security)`: red check first. `git grep -n 'npm audit signatures' -- .github/workflows` prints nothing.
- [ ] 4.2 `ci(security)`: add a step `npm audit signatures` in `reposilite-frontend` directly after `npm ci` in the
  `licenses` job of `ci.yml`. Verify: the check of 4.1 finds the step, and the `licenses` job passes on the pull request.
  Node 24 ships npm 11, which has the command.

## 5. C4 Explicit token scopes

- [ ] 5.1 `ci(security)`: red check first. A YAML parse that lists every job of `ci.yml` and `release-please.yml` without a
  `permissions` key. On origin/main the list is `licenses`, `vulnerabilities`, `images`, `chart`, `coverage` (ci.yml) and
  `changes`, `maven` (release-please.yml), plus any job the apply stage finds with the same gap.
- [ ] 5.2 `ci(security)`: add a `permissions` block to each job in the list above. Each block grants `contents: read` only.
  Keep the existing write blocks of `release-please`, `jar`, `chart` and `container` unchanged. Verify: the parse of 5.1 finds
  no job without `permissions`, and the top-level `permissions: contents: read` stays in both files.

## 6. C5 Scorecard

- [ ] 6.1 `ci(security)`: red check first. `test -f .github/workflows/scorecard.yml` fails on origin/main.
- [ ] 6.2 `ci(security)`: add `.github/workflows/scorecard.yml` with triggers `push` to `main` and `schedule` (weekly), a top
  level `permissions: read-all`, a job with `security-events: write`, `id-token: write`, `contents: read` and `actions: read`,
  `persist-credentials: false` on the checkout, and `ossf/scorecard-action` pinned by SHA with a `# vX.Y.Z` comment. The action
  runs with `publish_results: true` and writes its SARIF file for code scanning. No `pull_request` trigger. Verify: the YAML
  parses, the red check of 6.1 passes, and the first run on main after the merge publishes a result. No test beyond that is
  needed for a workflow that only reports.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `ci(security): pin actions by commit sha and add scorecard`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the reviewer what a maintainer sees in a
  failed build (which code ran, and why a moved tag no longer changes it) without workflow internals. It carries no session
  link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style hook accepts the
  title and every commit, and the body has no em or en dash.

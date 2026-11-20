# Tasks

Each group is one or more commits, and each commit has one Conventional Commits type. The mechanism ships under `ci(release)`,
the documentation under `docs(support)` and `docs(release)`. Every helper script is written with its unit tests first and the
tests fail until the script exists. The tests follow F.I.R.S.T.: no sleep, the date injected as a parameter, no network, a
temporary directory per test and no shared state. Groups 3 to 12 are one pull request each, unless noted, and each pull request
builds and passes on its own.

## 1. Proposal

- [x] 1.1 `docs(openspec): propose add-release-branches-and-backports`: this change and nothing else, branched from the remote
  main. Verify: `openspec validate add-release-branches-and-backports --strict --no-interactive` passes and the dash grep from
  the proposal prints nothing. The proposal pull request is merged before the first code pull request is opened.

## 2. Spike and maintainer decisions

- [ ] 2.1 Spike on a throwaway repository (a fork, not `main`): with release-please-action at the pinned SHA `45996ed`, check that
  `target-branch` reads the manifest and config of the branch, that `versioning-strategy: always-bump-patch` overrides a
  `versioning` key in the config file or not, and the release pull request title pattern for a branch. Record the answers in the
  first code pull request description. Verify: each claim in the design, section "Facts checked", is either confirmed or
  corrected in the design before group 6 starts.
- [x] 2.2 The maintainer confirmed the open questions of the design (1 to 9) on 2026-11-17, in particular the window, cutting on a
  major, the meaning of tag `X`, the label name and the archive method. Verify: the answers are recorded in design.md, section
  "Open questions, settled 2026-11-17".
- [ ] 2.3 Sequencing: the maintainer decided on 2026-11-17 that pull request #22 (release 2.0.0) waits until groups 3 to 9 and
  13 to 14 are merged. Verify: the decision is written on PR #22 before it is merged.

## 3. Support lines

- [x] 3.1 `ci(release)`: add `.github/scripts/render_support_tables.py` and its tests. The script reads `support-lines.json` and
  renders the support table and the label set; the tests cover a regular line, an LTS line with a later end of life date, and a
  regular line whose end of life date is wrong. Add `support-lines.json` with the lines as they are today. Verify: the tests pass
  and the script output is deterministic.
- [x] 3.2 `docs(support)`: render the support table of `SUPPORT.md` and the `Supported Versions` table of `.github/SECURITY.md`
  from the script, between marker comments, and state the window in `SUPPORT.md` as confirmed in 2.2. Do not touch the contact
  section of `.github/SECURITY.md`. Verify: running the script changes nothing in the two files.
- [x] 3.3 `ci(release)`: add a `support-lines` job to `ci.yml` that runs the renderer in check mode, verifies the `backport:<line>`
  labels and the existence of `release/<line>.x` for each open non-latest line. Verify: the job fails on a deliberately wrong
  date in a test fixture and passes on the repository.

## 4. Release tags

- [x] 4.1 `ci(release)`: add `.github/scripts/release_tags.py` and its tests. Pure functions: the newest tag across lines, the
  highest version with a given major, the tag set for a release (`X.Y.Z`, `X.Y`, `X` and `latest` as decided in the design), and
  the refusal of a version outside the branch's line. Verify: the tests cover `1.1.2` on `release/1.1.x` with `2.0.0` present,
  and `2.0.0` on `main`, and pass without a network.

## 5. Workflow triggers on release branches

- [x] 5.1 `ci(release)`: add `release/**` to the base or push branches of `ci.yml`, `heavy.yml`, `dco.yml`, `openapi-diff.yml`
  and `codeql.yml`. Verify: the workflows parse, and a pull request into a test release branch runs the checks of `main`.

## 6. Release Please on release branches

- [ ] 6.1 `ci(release)`: `release-please.yml` runs on `push` to `release/**` with `target-branch: ${{ github.ref_name }}` and the
  versioning strategy from 2.1. It refuses a release whose version is outside the branch's line, using `release_tags.py`. The
  `concurrency` group stays per ref. Verify: a dry run on the throwaway repository from 2.1 produces a patch version and a
  release pull request against the branch only.

## 7. Container images and jars

- [x] 7.1 `ci(release)`: the image job takes its tags from `release_tags.py`, builds `nightly` only on `main`, and keeps SBOM and
  provenance for every release. Verify: the tags for a `1.1.2` release on a branch are `1.1.2`, `1.1` and `1` while no higher
  major exists, and no `latest`.
- [x] 7.2 `ci(release)`: the `jar` job verifies its attestations with `--source-ref` set to the ref that built the jar, and
  `heavy.yml` resolves the previous release of the same line on a branch. `.github/RELEASE.md` is updated in group 13, not here.
  Verify: the workflow parses and the heavy previous-release rule has a test in `release_tags.py`.

## 8. GitHub Latest

- [ ] 8.1 `ci(release)`: a step after every release runs `gh release edit <newest tag> --latest`, with the newest tag chosen by
  `release_tags.py`. Verify: on the throwaway repository a patch on the old line keeps the Latest flag on the newest line.

## 9. Cutting a release branch

- [ ] 9.1 `ci(release)`: add `.github/scripts/release_branch.py` and its tests for the cut decision (patch 0 on `main`, base tag
  of the previous line, idempotence, refusal when the branch points elsewhere), and the `cut-release-branch` job that creates the
  branch, the `backport:<line>` label and the tracking issue. The job opens the pull request that adds the line to
  `support-lines.json`. Verify: the tests pass and the throwaway repository gets its branch from `v1.1.1` once and not twice.

## 10. Backport decisions

- [x] 10.1 `ci(release)`: add `.github/scripts/backport_decision.py` and its tests: label parsing, the security-only rule (`fix(security)`
  accepted, every other type refused, the breaking marker), and the refusal of a label for a line without a branch. Verify: each rule in the backports spec has a
  failing test first.

## 11. Backport workflow

- [ ] 11.1 `ci(release)`: add `.github/workflows/backport.yml` on `pull_request_target` (`closed`, `labeled`), restricted to merged
  pull requests into `main`. It never checks out the pull request head. It cherry-picks the merge commit with `-x -s`, opens the
  pull request as the release bot, and on conflict aborts, removes its branch and comments on the source pull request with the
  manual steps. Verify: on the throwaway repository a labelled fix opens one pull request, and a conflicting one opens none and
  leaves one comment.

## 12. End of a window

- [ ] 12.1 `ci(release)`: a `schedule` job in `release-please.yml` that reads the end of life dates from `support-lines.json`, with the
  date passed in as a parameter, opens the archiving pull request, and renames the branch to `archive/<line>.x` once that pull
  request is merged. Verify: the tests for the date decision pass, and the job changes nothing before the end of life date.

## 13. Maintainer runbook

- [x] 13.1 `docs(release)`: add a section "Release branches and backports" to `.github/RELEASE.md`, covering the cut, the manual cut
  from the last tag, the backport label and the conflict steps, the window end, the branch protection and the rule that no release
  branch merges into `main`. Replace `--source-ref refs/heads/main` in `.github/RELEASE.md` with the ref that built the jar. Link the
  section from `.github/MAINTAINING.md` under "Versions". Verify: every command in the section runs on the throwaway repository.

## 14. Public documentation

- [x] 14.1 `docs(support)`: update `docs/gitbook/release-notes/upgrade-changes.md` (supported upgrade paths for the lines),
  `docs/gitbook/release-notes/release-notes.md` (how a release line and a backport appear in the changelog) and `VERSIONING.md`
  (the lines and the tags). The table in `SUPPORT.md` already comes from group 3. Verify: every link resolves and the pages agree
  with `support-lines.json`.

## 15. Pull requests

- [ ] 15.1 Open the code pull requests in the order of groups 3 to 14, each under its own title, starting with `ci(release): add
  the support lines file and its renderer`. Groups 3 to 9 and 13 to 14 go before pull request #22 is merged, if the maintainer
  decided so in 2.3. Each description follows `.github/PULL_REQUEST_TEMPLATE.md`, carries no session link and states that
  no release branch exists until the cut. Verify: `gh pr list -R OneLiteFeatherNET/ingot --search 'ci(release)'` lists the series in order.

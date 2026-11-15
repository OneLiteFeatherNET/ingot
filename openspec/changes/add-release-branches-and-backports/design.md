# Design

## Facts checked

Repository (origin/main at the time of writing):

- `release-please.yml` triggers on `push` to `main` only. Its `container` job tags `latest`, `X.Y` and `X` only when a release
  was created, and it builds a `nightly` tag for every other relevant push. `maven`, `jar`, `plugins` and `chart` run on
  `release_created`. The `jar` job verifies its attestations with `--source-ref refs/heads/main` (also in `.github/RELEASE.md`),
  so a jar built on a branch would fail verification.
- `heavy.yml` "Resolve the latest release" takes the highest `v*` tag as the previous release for the upgrade rehearsal. It
  does not know about lines.
- `ci.yml`, `heavy.yml`, `dco.yml`, `openapi-diff.yml` and `codeql.yml` name `main` as the only base or push branch.
  `ci.yml` also runs a nightly full pass on `main`.

Release Please, `googleapis/release-please-action` at `v5.0.0` (commit `45996ed1f6d02564a971a2fa1b5860e934307cf7`, the SHA
pinned in `release-please.yml`), read in its `action.yml` at that commit:

- `target-branch` is an input (default empty). The release-please manifest documentation says a manifest "must exist at the tip of
  the `--target-branch`", so each branch carries its own `.release-please-manifest.json`.
- `versioning-strategy` is an input (default `default`). The release-please core accepts `always-bump-patch`, documented as
  "useful for backporting bugfixes to previous major/minor release branches". Whether the input overrides a `versioning`
  key in the config file is not verified yet, and the spike in the tasks checks it.
- The action has no input that sets the GitHub "Latest" flag, and nothing in its inputs creates a branch. The branch cut is
  therefore a job of this repository.

These two facts drive the design. Anything that depends on how the action behaves is tested in a spike before the workflow
change lands (tasks group 2).

## Decision 1: the support lines live in one file

`support-lines.json` at the repository root lists the lines in the order they are supported:

```json
{
  "lines": [
    { "line": "2.0", "kind": "regular", "status": "latest" },
    { "line": "1.1", "kind": "regular", "status": "previous", "end_of_life": "2027-02-13" }
  ]
}
```

The dates above are illustrative. `end_of_life` is the date the line stops receiving fixes.

- For `kind: regular`, the check requires `end_of_life` to equal the ship date of the successor line plus 90 days.
- `kind: lts` is accepted with any `end_of_life` that is later than that. Nothing else changes (see "LTS later").
- `.github/scripts/render_support_tables.py` writes the support table of `SUPPORT.md` and the
  `Supported Versions` table of `.github/SECURITY.md` between marker comments. CI renders the tables and fails when the files
  differ, so the text cannot be edited by hand and drift away.
- The same check reads the set of lines and requires a `backport:<line>` label per non-latest line, and a release branch
  `release/<line>.x` for every non-latest line that is still open. It fails when a label or a branch is missing or when a
  closed line still has a branch.

Consequence: changing the window means changing one file and running one command. The tables and the labels follow.

## Decision 2: a release branch is cut when the next line ships, from the last tag of the old line

- Name: `release/<major>.<minor>.x`, for example `release/1.1.x`. The `x` stays literal, so the branch name matches the
  line and not a version.
- Trigger: a release created on `main` whose version has patch 0 (`X.Y.0`). A major release counts as a new line too, so the
  release that ships 2.0.0 cuts `release/1.1.x`. The coordinator's brief said "next minor"; this design extends it to majors
  and marks it as a maintainer decision in the open questions.
- Base: the tag of the last release of the old line (`v1.1.1` for `release/1.1.x`), not the tip of `main`. `main` already
  holds unreleased changes for the new line, and the old line must not receive them.
- Who: a job in `release-please.yml` (`cut-release-branch`), running after a release on `main` that has patch 0. It runs with
  the release bot token, so the branch can be pushed past a protection rule that allows only that actor. It is idempotent:
  when the branch exists and points at the old tag, it does nothing; when it exists and points elsewhere, the job fails
  and names the branch for the maintainer.
- It also creates the label `backport:<line>` (if missing) and opens a tracking issue "Window for <line> ends on <date>" with
  the end of life date. `support-lines.json` is not edited by the job. The job opens a pull request that adds the new line to
  `support-lines.json` and re-renders the tables, and a maintainer reviews and merges it, so `main` only changes through review.
- Never earlier: no branch exists before the successor ships, so nothing needs to be maintained on a line that is not yet
  superseded. Main is the only place that receives fixes until then.

## Decision 3: Release Please on a release branch

- The `release-please` job runs on `push` to `release/**` as well, with `target-branch: ${{ github.ref_name }}` and
  `versioning-strategy: always-bump-patch`, and the existing `concurrency` group per ref.
- Each branch keeps its own `.release-please-manifest.json` (value `1.1.1` at the cut, then `1.1.2` after the first patch) and
  its own `CHANGELOG.md`, which starts as a copy of the old line's changelog.
- Versions on a branch always have the branch's major and minor. `always-bump-patch` makes a backported `feat:` a patch
  release, so a backport never moves a line to a new minor.
- Tags: `v1.1.2` on the branch commit. The tag namespace is shared with `main`, which is safe because `main` never produces a
  version below its own manifest value and a branch never produces a version outside its line. The changelog of `main`
  never lists a patch of an older line, and no branch is merged back into `main` (the runbook says so, and a ruleset
  refuses merges from `release/**`).
- The release pull request title carries the line, for example `chore(release-1.1.x): release 1.1.2`, so a reviewer sees which
  line it belongs to. The exact pattern is set in the branch configuration and checked in the spike.
- The `maven`, `plugins` and `chart` jobs run for branch releases as they do for `main`. The plugin catalogue is only
  regenerated on `main`, because the catalogue lists the newest versions (see open questions).

## Decision 4: the GitHub "Latest" flag follows the newest line

- The action creates releases without a Latest option (checked above), and GitHub marks the most recently created
  release as Latest. A `1.1.2` created after `2.0.0` would therefore take the badge.
- A step after every release, on any branch, runs `gh release edit <newest tag> --latest`, where `<newest tag>` is the highest
  `v*` tag across all lines. A patch on an old line therefore never takes the badge, and the newest line keeps it. The command
  is the one `gh release edit --latest` documents ("Explicitly mark the release as Latest").
- The step is one shell script with a unit-testable core: the function that picks the newest tag from a list of tags.

## Decision 5: container tags

The image job keeps `X.Y.Z` on every release. Whether `X.Y` and `X` move is decided by comparing versions, not by the branch:

| Tag | Moves when | Built from `release/1.1.x` |
|---|---|---|
| `X.Y.Z` | every release | `1.1.2` |
| `X.Y` | every release of that line, because the line is `X.Y` | `1.1`, moved to `1.1.2` |
| `X` | the release is the highest version with major `X` | `1`, moved to `1.1.2` while no `1.2` or higher exists |
| `latest` | the release is the highest version across all lines | not moved |

- Comparison uses the existing version tags (`git ls-remote --tags`, sorted with `sort -V`), the same method
  `heavy.yml` already uses. The decision is a small pure function with unit tests.
- SBOM and provenance stay on for every release (`sbom: true`, `provenance: mode=max`), as on `main`.
- Nightlies: the `nightly` tag is pushed only for `main`. The `changes` job and the `nightly` branch of the image job are
  guarded with `github.ref == 'refs/heads/main'`.
- The image labels (`org.opencontainers.image.version`) carry the patch version, and the drop-in contract checks (the
  upstream compose file, the adoption of an existing `/app/data`, `PUID`/`PGID`/`REPOSILITE_OPTS`) run for every image
  build, because `heavy.yml` builds from whatever branch triggers it.
- The `jar` job replaces `--source-ref refs/heads/main` with the ref that built the jar (`github.ref`), in the workflow and in
  `.github/RELEASE.md`. Signer identity stays the release workflow path.

## Decision 6: CI on release branches

- `ci.yml`, `heavy.yml`, `dco.yml`, `openapi-diff.yml` and `codeql.yml` get `release/**` as a base or push branch. The
  `plugin-api` job of `ci.yml` is what keeps a backport from breaking the plugin contract, so it must run on release
  branches.
- `heavy.yml`'s "Resolve the latest release" must find the previous release of the line under test. Rehearsing an upgrade
  of a `1.1.x` build from a `2.0.x` image would be wrong, so the rule becomes: on a branch, the previous release is the
  highest tag of the same line; on `main`, the highest tag overall, as today.
- The nightly full run in `ci.yml` covers `main` and each open release branch, so a branch gets the full pass at least
  once a day.

## Decision 7: backports

- The trigger is `pull_request_target` with `types: [closed, labeled]`, restricted to merged pull requests into `main` with the
  `backport:<line>` label. The workflow never checks out the pull request head. It works on the squashed commit on `main`
  (`merge_commit_sha`), so no code from a fork runs with write access. The helper script is loaded from `main`.
- Allowed types: `fix`, `perf` and `revert`. A `feat` or any change with `!` or a `BREAKING CHANGE:` footer is refused with a
  comment, because a backport must not change the API (the `plugin-api` job checks that again on the backport pull request).
  `docs`, `test`, `build`, `ci`, `refactor` and `chore` need a second label, `backport:force`, so an accidental label does
  not spread a change that was never meant for the line.
- The cherry-pick: `git cherry-pick -x -s <sha>` on a branch `backport/<pr-number>-<line>` created from `origin/release/<line>.x`.
  `-x` adds the `(cherry picked from commit ...)` trailer and `-s` a sign-off. The sign-off names the label actor, because the
  Developer Certificate of Origin certification has to come from a person (open question on the exact identity).
- Pull request: title equal to the title of the source pull request, which is already a valid Conventional Commit. The body
  states "Backport of #<n> to `release/<line>.x`" and links the source pull request. Base `release/<line>.x`. The pull request
  is opened by the release bot, which the sign-off check accepts as a bot pull request (`add-dco-check`, C2). The cherry-pick
  trailer is not a DCO exemption on its own, because the source commit is not an ancestor of `upstream/main`.
- Conflict: the cherry-pick fails, the job aborts and removes its branch, and it comments on the source pull request with the
  commands to finish the backport by hand: fetch, branch from `origin/release/<line>.x`, cherry-pick with `-x`, resolve,
  push, and open the pull request with the same title. The job status of the source pull request stays green, because the
  merge to `main` succeeded; the comment is the report.
- Who merges a backport: a maintainer, with the same CI as `main`, after the branch protection rule (required review and
  checks) passes.

## Decision 8: window end and archiving

- At `end_of_life`, the scheduled job `end-of-life` (a `schedule` in `release-please.yml`, runs daily, reads the date from
  `support-lines.json`, injected as a parameter for tests) does three things through one pull request: it sets the line to
  `status: archived` in `support-lines.json`, re-renders the tables and removes the `backport:<line>` label from open
  pull requests with a comment. The maintainer merges that pull request.
- Archiving the branch: the job renames `release/<line>.x` to `archive/<line>.x`. The commits and the tags stay, the release
  workflow stops matching the branch, and a rename is reversible. Deleting the branch would also be possible, but a rename
  keeps the history visible without the workflow.
- Tags are never deleted or moved.

## Decision 9: plugin support tiers

- `add-plugin-support-tiers` defines the tiers Core and Official. Its text says those tiers are supported "for the supported
  release lines" as defined in `SUPPORT.md`. This change provides that definition through `support-lines.json` and the rendered
  table, so the tier change refers to `SUPPORT.md` and does not repeat the window.
- Plugin jars for a branch release are published from that branch's release run, so a Core or Official plugin that changed
  in a backport gets a patch on the line. The plugin catalogue (`generate_plugin_catalogue.py`) lists the newest versions and
  is only regenerated on `main`.
- No duplicated tier text in this change. The one sentence in `SUPPORT.md` that connects both is written by this change and
  names the tiers only by reference.

## LTS later

LTS is a non-goal. The design leaves room for it, so that an LTS line is a data change:

- `kind: lts` in `support-lines.json`, with an `end_of_life` later than the regular rule. No workflow change, no change to
  the branch cut, the backport labels, the tags or the archive job.
- The check already accepts `lts`. The rendered tables show the kind.
- Example shape for a later decision (not decided here): the first minor of each major becomes `lts` with 12 months of
  support, which means `1.0` would become `kind: lts` with `end_of_life` 12 months after `2.0.0`. Whether `1.0` is kept as
  an LTS line depends on whether a branch exists for it, which the cut rule already allows.
- LTS adds a policy decision the maintainer must make (which minors, how long, whether security-only). The file format is
  designed so that decision costs one reviewed pull request.

## Sequencing with pull request #22

- Release 2.0.0 (PR #22) is the first release that cuts `release/1.1.x`. If it merges before the cut job exists, the branch has
  to be cut by hand from `v1.1.1` with the steps in the runbook, and the window clock starts on the ship date either way.
- Recommended order: merge the code pull requests and the docs before PR #22 is merged. The release pull request itself is
  not changed.

## Risks

- The spike may show that `versioning-strategy` does not override a `versioning` key in the config file. The fallback is a
  one-line `versioning` key in each branch's `release-please-config.json`, committed by the cut job as its first commit.
- A release run on a branch that fails after the tag is pushed leaves a tag without assets. The existing `--clobber` upload
  and the `container_version` rebuild input cover that, as they do on `main`.
- Shared tag namespace: a mistake that tags a branch commit with a `main` version would block that version. The version
  check in the release step refuses any version outside the branch's line.
- The workflow file grows. The release decisions are moved into scripts with tests, so the YAML only wires them.

## Open questions with defaults

1. **Cut on a new major as well as a new minor?** Default: yes, because 2.0.0 is the case that matters now.
2. **Maintainer creates `release/1.1.x` before PR #22 or the job does it on release?** Default: the job, with the sequencing
   above. A manual cut is the fallback.
3. **Tag `X` on patch releases of an older line.** Default: `1` moves to `1.1.2` while `1.1` is the highest 1.x. Alternative: `X`
   tracks only the newest line, which is simpler and stops users who pin `1` from receiving fixes.
4. **Label naming.** Default: `backport:<major>.<minor>`, for example `backport:1.1`, mapped to `release/1.1.x`.
5. **Sign-off identity of a backport.** Default: the label actor's sign-off. If that person has no public email, the job
   comments and the maintainer adds the sign-off before merge.
6. **Plugin catalogue for branch releases.** Default: regenerated on `main` only.
7. **Renovate on release branches.** Default: not covered; security dependency updates for an old line are a manual backport.
8. **Archive by rename or by deletion.** Default: rename to `archive/<line>.x`.
9. **Confirmation of the window.** The window in `SUPPORT.md` stays as currently written (latest line every fix, previous line
   security only for 90 days after the next line ships, older lines nothing). The maintainer confirms it in the proposal review.

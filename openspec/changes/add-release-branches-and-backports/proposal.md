# Proposal

## Why

`SUPPORT.md` and `.github/SECURITY.md` promise that the previous minor line receives security fixes for 90 days after the
next line ships. The repository cannot keep that promise. Release Please releases only from `main`
(`release-please-config.json`, `.release-please-manifest.json`, `.github/workflows/release-please.yml`), and there is no
release branch to build a fix on. When release 2.0.0 ships (pending as PR #22), 1.1.x becomes the previous line, and a
security fix for it has nowhere to be built or published. `openspec/changes/archive/2026-11-15-document-support-policy/design.md`
records the same gap.

The maintainer decided to make the existing support window technically real now. Long-term support (LTS) is not part of
this change.

## What Changes

- **Release branches.** A branch `release/<major>.<minor>.x` is created from the last tag of a line when the next line ships
  on `main`. It is never created earlier. A workflow job creates it, and the branch is archived when its window ends.
- **Release Please on release branches.** Each release branch runs Release Please with `target-branch` set to the branch,
  its own manifest and changelog, and `always-bump-patch` versioning. Such a branch only produces patch releases, and its
  tags never collide with `main`.
- **Backports.** A label `backport:<major>.<minor>` on a merged pull request into `main` makes a bot cherry-pick the squashed
  commit onto the release branch and open a pull request. A conflict is reported on the source pull request with the steps
  to resolve it by hand.
- **CI, images and releases on release branches.** The checks that run for `main` run for release branches too, including
  the image builds for both architectures and the drop-in contract checks. Release images carry SBOM and provenance as
  `main` releases do. Tags `X.Y.Z` and `X.Y` are pushed for every release, `latest` stays on the newest line, and nightlies
  stay on `main`.
- **One source for the support lines.** A file `support-lines.json` lists every line with its kind and end of life date.
  The support tables in `SUPPORT.md` and `.github/SECURITY.md` and the set of `backport:` labels are generated from it or
  checked against it, so the promise and the mechanism cannot drift apart.
- **Documentation.** `SUPPORT.md`, the security table, the GitBook upgrade and release-notes pages and `VERSIONING.md`
  describe the same window. `.github/RELEASE.md` gets a maintainer runbook for cutting and backporting.
- **Plugins.** The support tiers of `add-plugin-support-tiers` follow the lines defined here. That change is referenced, not
  duplicated (see the design, section "Plugin support tiers").

### Out of scope

- **LTS.** No line is long-term supported by this change. The file format and the checks are designed so that LTS is a
  policy and configuration change later (see the design, section "LTS later"). The example of "first minor of each major,
  12 months" is an example only.
- Renovate updates for release branches. Dependency updates keep targeting `main`.
- Changes to the upstream Reposilite line. It is not covered by this policy.
- Changing the `main` release flow, the version scheme or the Conventional Commits rules.
- Branch protection and rulesets in the GitHub settings. They are described in the runbook and set by a maintainer.
- Creating `release/1.1.x` by hand before pull request #22 ships. The cut job creates it when 2.0.0 ships (open question 2,
  settled 2026-11-17).

## Capabilities

### New Capabilities

- `release-lines`: the support lines, how a release branch is cut, what Release Please, the container tags and the GitHub
  Latest flag do on a branch, when a line ends, and how the support tables follow the lines file.
- `backports`: how a change reaches a release branch, which change types may go there, how a conflict is reported, and what
  a backport pull request must carry.

## Impact

- **Type and scope.** The mechanism ships under `ci(release)`. Documentation commits inside the same pull requests keep their
  own types (`docs(support)`, `docs(release)`), one type per commit, as `add-dco-check` does. The squash commit on `main` carries
  the title of the pull request. The title of the code pull request series is:

  `ci(release): add release branches and backport automation`

  The proposal pull request is titled `docs(openspec): propose add-release-branches-and-backports`.
- **Breaking marker.** No breaking change for an operator. No configuration key, environment variable, volume, image name or
  manifest changes. Tags `X.Y.Z` and `X.Y` are added to images that already exist under the same names. The tag `X` keeps its
  current meaning (see the design, section "Container tags"). `latest` keeps pointing at the newest release.
- **Behaviour that changes.** A release line that is not the newest one can now receive a patch release from its own branch.
  The GitHub "Latest" badge follows the newest line, not the most recent release.
- **Dependencies on other changes.**
  - `add-plugin-support-tiers`: its tier text refers to the supported release lines defined in `SUPPORT.md`. This change
    defines those lines. Neither blocks the other. No open pull request for that change was found at the time of writing
    (`gh pr list -R OneLiteFeatherNET/ingot --search add-plugin-support-tiers` returned nothing), so the reference is by
    change name only.
  - `add-dco-check`: the backport pull requests must pass the sign-off check, and the check must also run for release branches.
  - `add-repository-trust-files` and `document-support-policy`: both touch `.github/SECURITY.md` or `SUPPORT.md`. This change
    replaces the window text those files carry, so it lands after them or rebases onto them.
- **Code.** `.github/workflows/release-please.yml`, `ci.yml`, `heavy.yml`, `dco.yml`, `codeql.yml`, `openapi-diff.yml`,
  `.github/scripts/` (new helper scripts and their tests), `support-lines.json`, `SUPPORT.md`, `.github/SECURITY.md`,
  `.github/RELEASE.md`, `VERSIONING.md` and `docs/gitbook/release-notes/`.

## Verification

- `openspec validate add-release-branches-and-backports --strict --no-interactive` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-release-branches-and-backports` prints nothing.
- Every helper script and its unit tests are written first and fail until the script exists. The tests create their own
  temporary directories and inject the date, so no test reads the clock or the network.
- The first code pull request that touches the release workflow is checked on a fork or on a throwaway branch before it
  reaches `main`, because the action behaviour in the design is verified by a spike and not by reading alone.

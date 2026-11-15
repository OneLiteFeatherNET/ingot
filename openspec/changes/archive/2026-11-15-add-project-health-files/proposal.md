# Proposal

## Why

The public README of Ingot says that no release has been cut, which stopped being true with `v1.0.0`. An operator who reads
the first screen of the repository concludes that the project is not yet usable, and a reviewer who reads it concludes that
the documentation is out of date. The same screen shows no sign that the security workflow runs, although the Scorecard
workflow has completed on `main` for some time.

A reader who wants to know where the project is going finds no roadmap. A pull request has no default reviewer, so review
falls to whoever notices it. An automated health report, which many adopters and funders read, finds no roadmap, no
owners and no `.clomonitor.yml` that says which checks do not apply yet.

## What Changes

- **Status line.** The sentence in `.github/README.md` that says no Ingot release has been cut is replaced by a pointer to the
  releases page. The quickstart still builds from source, and the sentence says so.
- **Scorecard badge.** The README header shows the OpenSSF Scorecard badge next to the licence badge. It links to the
  Scorecard viewer for the repository.
- **`ROADMAP.md`.** A public list of the proposals that are not yet on main. Each entry links its proposal and says in one line
  what it changes. It says that planning detail lives outside the repository.
- **`.github/CODEOWNERS`.** The repository default owner is the `ingot-maintainers` team of the organisation.
- **`.clomonitor.yml`.** Exemptions with a reason for the checks that do not apply yet: the summary table, a community
  meeting, a Slack presence, website analytics, a CLA and an Artifact Hub badge.

### Out of scope

- The OpenSSF Best Practices badge. It needs a registered project ID, so it is a follow-up after the maintainer registers at
  bestpractices.dev.
- An Artifact Hub badge and `artifacthub-repo.yml`. The Helm chart in `charts/ingot` is not published to Artifact Hub, so the
  badge would point at nothing. It is a follow-up once the chart is published.
- `MAINTAINERS.md`, `GOVERNANCE.md`, `ADOPTERS.md` and `SECURITY-INSIGHTS.yml`. They belong to `add-repository-trust-files`.
  `CODEOWNERS` names the team, and the maintainers check of CLOMonitor reads that file as well.
- The community link and documentation link placeholders. They belong to `define-governance-and-community`.
- Repository settings. They are listed below and changed by the maintainer, never by a commit.

## Capabilities

### New Capabilities

- `project-health-files`: the status statement in the README, the Scorecard badge, the public roadmap, the code owners and the
  health report configuration.

## Impact

- **Type and scope.** The change ships under `docs(project)`. The pull request title, squash-merged onto main, is:

  `docs(project): add roadmap, codeowners and the scorecard badge`

- **Breaking marker.** None. Documentation and repository metadata only. Nothing an operator configures changes.
- **Behaviour that changes by default.** None.
- **Dependencies on other changes.**
  - `add-repository-trust-files`: the CLOMonitor `maintainers` check passes through `CODEOWNERS` as well as through its
    `MAINTAINERS.md`, so the two changes do not block each other. `ROADMAP.md` is not in that change's scope.
  - `complete-security-disclosure` and `define-governance-and-community` edit other hunks of `.github/README.md`.
  - `harden-ci-supply-chain` leaves the README badge to this change.
- **Code.** None. `.github/README.md`, `.github/CODEOWNERS`, `.clomonitor.yml`, `ROADMAP.md`.

## Repository settings (maintainer, not part of any pull request)

Read with `gh api` on the day this proposal was written. The maintainer changes them by hand. No commit does.

| Setting | Current state | Target |
|:--|:--|:--|
| Branch protection or ruleset on `main` | Not protected (`branches/main/protection` answers 404, no ruleset exists) | Pull request required, one approval, the `ci.yml` checks required, no force push, no deletion |
| GitHub Discussions | Disabled | Enabled. The community link in `define-governance-and-community` points at it, and CLOMonitor `github_discussions` reads it |
| Repository homepage | `https://reposilite.com`, the upstream project | The Ingot documentation site once it exists. Until then the `docs/gitbook` folder on GitHub |
| Private vulnerability reporting | Enabled (`{"enabled":true}`) | No change. Confirm it stays enabled |
| Access of team `ingot-maintainers` | The team has one member. Its repository grant could not be read with the available token | Write or maintain access, so that `CODEOWNERS` can request reviews |

## Verification

- `openspec validate add-project-health-files --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/add-project-health-files` prints nothing. The new files must not contain an
  em or en dash either.
- After merge: CLOMonitor passes `readme`, `roadmap`, `maintainers`, `changelog`, `contributing`, `security_policy`,
  `code_of_conduct` and `openssf_scorecard_badge`, and the exempted checks show their reasons.

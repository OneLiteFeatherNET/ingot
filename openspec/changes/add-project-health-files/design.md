# Design

## Context

Facts checked on the worktree and on origin/main:

- `.github/README.md:41` says "No Ingot release has been cut yet, so the quickstart below builds from source." The releases
  `v1.0.0`, `v1.1.0` and `v1.1.1` exist (`gh release list`, `v1.1.1` marked Latest).
- The README header carries one badge, for the licence, at the top of the file. There is no Scorecard badge.
- `.github/workflows/scorecard.yml` is on main. It publishes results with `publish_results: true`, and its runs on 2026-11-11
  completed successfully (`gh run list --workflow scorecard.yml`). The badge therefore has a result to show.
- No `ROADMAP.md`, no `.github/CODEOWNERS` and no `.clomonitor.yml` exist. The root has `CHANGELOG.md`, `VERSIONING.md` and
  `CLAUDE.md` as Markdown files.
- The team `ingot-maintainers` exists in the organisation with one member. The `repos` listing of that team returned no
  entries with the available token, so its access to the repository is unverified.
- The Helm chart `charts/ingot/Chart.yaml` has no Artifact Hub annotations, and no `artifacthub-repo.yml` exists.

## Decision 1: status line

The sentence is replaced by a link to the releases page and no version number. A version number in a README drifts with
every release, and the sentence that caused this change was exactly such a fact. The quickstart text stays unchanged.

## Decision 2: Scorecard badge

The badge uses the public Scorecard API, which the workflow publishes to:

- image: `https://api.scorecard.dev/projects/github.com/OneLiteFeatherNET/ingot/badge`
- link: `https://scorecard.dev/viewer/?uri=github.com/OneLiteFeatherNET/ingot`

The OpenSSF Best Practices badge is not added here. Its URL contains a project ID that exists only after registration, and a
badge with a placeholder ID would be a broken image. Artifact Hub is not added because the chart is not published there.

## Decision 3: ROADMAP.md content

A change appears on the roadmap when its proposal is on main and its implementation has not merged. The list was made by
matching each proposal's pull request title against the commit subjects on origin/main, and then reading the proposals whose
titles did not match. Entries that landed under a different title are not listed. The maintainer confirms each row before the
code pull request (task 2.3).

Product changes:

| Change | Line for the reader |
|:--|:--|
| `add-chart-upgrade-rehearsal` | Moving a release from the upstream Helm chart to the Ingot chart with one changed line, rehearsed in CI |
| `add-openapi-diff-gate` | Pull requests that break the REST API fail unless they carry a marker |
| `add-repository-format-spi` | Repository formats beyond Maven, with Maven 2 as the default |
| `add-repository-policy-fields` | Version policy, a read-only flag and a write policy view per repository |
| `add-structured-logging` | JSON log output, log levels from the environment and an access log |
| `add-support-bundle` | A system information page and a support bundle for maintainers |
| `fix-entrypoint-signal-handling` | SIGTERM reaches the server process, so shutdown completes its flushes and releases |
| `gate-plugin-abi` | Plugin API compatibility enforced in CI, where today it is reported |

Documentation and governance changes:

| Change | Line for the reader |
|:--|:--|
| `document-support-policy` | Where help is found and which release lines receive fixes |
| `add-repository-trust-files` | Maintainers, governance, adopters and the security contacts |

Each link has the form `openspec/changes/<name>/proposal.md`. The archive follow-up of a change moves that path, so the archive
pull request updates the roadmap entry in the same pull request. This is a rule of this change, not a suggestion.

The ROADMAP says that the dates of the work are not promised and that planning detail lives in the project's planning tool, as
`CLAUDE.md` states.

## Decision 4: CODEOWNERS

The file contains one line, `* @OneLiteFeatherNET/ingot-maintainers`. The team is the organisation's own and exists. It is
used because a single named person is a guess, and the team can gain members without a file change. A team without write
access to the repository cannot be requested as reviewer, so the maintainer confirms the grant (settings table, task 2.1).
Requiring code-owner review in branch protection is not switched on until the team has at least two members.

## Decision 5: .clomonitor.yml

CLOMonitor reads an `exemptions` list whose entries name a check and give a reason. The reason is shown in the report. The
format was taken from the CLOMonitor source (`clomonitor-core/src/linter/metadata.rs`, `exemptions` field) and its
`docs/checks.md`, "Exemptions" section. The file exempts only the checks that do not apply yet:

| Check | Reason to show |
|:--|:--|
| `summary_table` | The project is not listed in the CNCF landscape |
| `community_meeting` | No recurring community meeting exists |
| `slack_presence` | No Slack or Kubernetes Slack channel is used |
| `analytics` | The project website, when it exists, uses no analytics |
| `cla` | The project uses the Developer Certificate of Origin, not a CLA |
| `artifacthub_badge` | The Helm chart is not yet published on Artifact Hub |

`github_discussions` is not exempt. It passes once the setting in the table above is switched on. Exemptions are reviewed when
the underlying fact changes, and the maintainer confirms each reason (task 2.2).

## Decision 6: which README hunks this change owns

This change edits the status sentence and the header of `.github/README.md`. The community link near the end of the file is
owned by `define-governance-and-community`, so the two changes touch different hunks.

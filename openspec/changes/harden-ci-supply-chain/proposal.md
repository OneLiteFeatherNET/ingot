# Proposal

## Why

Ingot's workflows run third-party code with a token that can write to the repository and to GHCR. Sixteen action
references are pinned by tag (`actions/checkout@v6`, `actions/setup-java@v5`, `actions/upload-artifact@v4`, and the
rest), and two jobs call reusable workflows from `OneLiteFeatherNET/workflows` at `@v2.4.0`. A tag can be moved to a
different commit by whoever controls the action repository. A release that a maintainer reviewed can then change on the
next run without any change in this repository, and nothing in the repository says which commit actually ran.

A maintainer who reads a failed build wants to know which code was executed. Today the answer is "whatever the tag points
to this morning".

Three smaller gaps sit next to it. The frontend is installed with `npm ci`, which checks the lockfile hashes but not the
registry signatures and provenance attestations that npm publishes for many packages. No OpenSSF Scorecard run measures
the workflows, so the pinning and token checks that Ingot could be held to are invisible. And the token scopes are only
implicit: the top level of both workflows is `contents: read`, but most jobs say nothing about their own scope, so a
reviewer has to work out what each job may write.

Facts checked against origin/main (`c0f01846`):

- The roadmap named four tag-pinned references (`ci.yml:60,66,78,212`). The real count is 16 action references across
  `ci.yml` (lines 60, 66, 78, 101, 107, 150, 199, 212, 285, 366, 376) and `release-please.yml` (lines 85, 162, 168, 204,
  269), plus the reusable workflow calls at `ci.yml:42` and `release-please.yml:141`. The four named lines are correct.
- `renovate.json` extends `OneLiteFeatherNET/renovate:default`. That shared preset and the repository file contain no
  digest pinning helper, so Renovate leaves a tag as a tag and proposes the next tag. The existing SHA pins with a
  `# vX.Y.Z` comment show the desired form, but nothing keeps new references in that form.
- `licenses` runs `npm ci` in `reposilite-frontend` at `ci.yml:86`. No step verifies signatures.
- No Scorecard workflow exists. `.github/workflows` on origin/main holds `ci.yml` and `release-please.yml` only.
- Top-level `permissions: contents: read` is set in both workflows (`ci.yml:10-11`, `release-please.yml:19-20`). Jobs
  without their own block are `ci.yml` `licenses`, `vulnerabilities`, `images`, `chart`, `coverage`, and
  `release-please.yml` `changes` and `maven`.

## What Changes

- **C1 Pin by commit SHA.** Every `uses:` in `.github/workflows/` names a full commit SHA followed by a `# vX.Y.Z`
  comment, reusable workflow calls included. The SHA is the commit the current tag resolves to, and the major version
  stays where it is (`actions/checkout@v4` at `release-please.yml:269` stays on v4). Behaviour of every run stays the same.
- **C2 Renovate keeps the pins.** `renovate.json` extends the GitHub Actions digest pinning preset, so a new action release
  arrives as a pull request that moves the SHA and the version comment together. How this interacts with the existing
  grouping is recorded in `design.md`.
- **C3 Verified npm install.** The `licenses` job runs `npm audit signatures` in `reposilite-frontend` directly after
  `npm ci`. A signature or attestation that does not verify fails the job.
- **C4 Explicit token scopes.** Both workflows keep `contents: read` at the top level. Every job declares its own
  `permissions:` block. Write scopes stay only where a job writes today: release-please pull requests and releases,
  package publishing, and the Scorecard job.
- **C5 Scorecard.** A new `.github/workflows/scorecard.yml` runs `ossf/scorecard-action`, pinned by SHA, on pushes to
  `main` and once a week. It uploads SARIF to code scanning and publishes the result to the OpenSSF API. It does not run on
  pull requests, so no fork token reaches it.

### Out of scope

- Branch protection or rulesets. The Scorecard Branch-Protection check depends on repository settings that a maintainer
  has to change.
- A blocking workflow linter (zizmor or actionlint) in CI. Scorecard reports findings but does not block a merge. Gating
  is a follow-up.
- `persist-credentials: false` on checkouts. Each job needs a review of whether it pushes, which this change does not do.
- `npm audit signatures` in `reposilite-frontend/Dockerfile`. The image build sits behind the drop-in image contract and
  belongs in its own change.
- Major version upgrades of any action.
- Signing of release artifacts. That is `sign-release-artifacts` (N6).
- `SECURITY.md`, the README badge and the contact address.

## Impact

- **Type and scope.** The change ships under `ci(security)`. Every commit of the code pull request uses the same type and
  scope. The pull request title, squash-merged onto main, is:

  `ci(security): pin actions by commit sha and add scorecard`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, image, deployment manifest
  or plugin changes, so there is no `docs/gitbook/` page to update and no operator action. A Reposilite installation
  pointed at Ingot behaves as before. The title carries no `!` and no commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes.** None for users or clients. Maven, Gradle and sbt clients, the dashboard and plugins see the
  same server. A CI run may fail where it did not before: `npm audit signatures` can stop a build on a broken signature,
  and a job with a missing permission block is a visible change to the job, not to the artifacts.
- **Code.** `.github/workflows/ci.yml`, `.github/workflows/release-please.yml`, the new
  `.github/workflows/scorecard.yml` and `renovate.json`. No Kotlin or frontend source changes.

## Verification

- Before each group, the check named in `tasks.md` fails on origin/main. After the group it passes.
- `openspec validate harden-ci-supply-chain --strict` passes.
- `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/harden-ci-supply-chain` prints nothing.
- The code pull request runs the `licenses` job with `npm audit signatures`, and the Scorecard workflow publishes its
  first result after the merge to main.

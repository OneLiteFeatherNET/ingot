# Proposal

## Why

A release of Ingot is three artifacts: the server image, the dashboard image and the Helm chart in `ghcr.io/onelitefeathernet`,
and the standalone jar on the GitHub release. None of them is signed. The release workflow already attaches an SBOM and a
provenance record to the two images, but nothing in them is verifiable by a third party, and nothing ties the chart or the
jar to the workflow that built them. A tag in a registry can be moved by anyone who holds push rights to the package, and an
operator who pulls `ingot:<version>` has no way to check that the bytes came from this repository's release workflow.

An operator who wants to admit only signed images into a cluster (Flux `verify.provider: cosign`, Kyverno, the Sigstore
policy controller) has nothing to check against today.

Facts checked against origin/main:

- `release-please.yml:350-351`, `378-379`, `415-416` and `437-438` set `sbom` and `provenance` on the server image, its retry,
  the dashboard image and its retry. All four are gated on `released == 'true'`, so nightlies carry neither. Confirmed.
- No `id-token` or `attestations` permission exists anywhere under `.github/`. The `container` job has `contents: read` and
  `packages: write` (lines 265-267), the `chart` job the same (lines 199-201), and the `jar` job `contents: write` (lines
  158-159). Confirmed.
- The chart is pushed as OCI to `oci://ghcr.io/onelitefeathernet/charts` at `release-please.yml:227`. The jar is uploaded to
  the release at line 186. Both run only when a release was created. Confirmed.
- Correction to the roadmap wording "releases only": the `released` output is also `true` for a `workflow_dispatch` rebuild
  with `container_version` (line 281). A dispatch rebuild of a released version therefore counts as a release for signing.
  The signing condition follows `released`, and `design.md` records what that means for the signing identity.

## What Changes

- **R1 Keyless signatures on the images.** On a release, after the server image and the dashboard image are pushed (including
  their retries), the workflow signs each image by digest with cosign in keyless mode, using the job's OIDC identity. No key
  is stored in the repository. The job then verifies both signatures against the release workflow identity before it can
  succeed.
- **R2 Signature on the chart.** On a release, the OCI chart is signed by its digest after `helm push`, and verified the same
  way.
- **R3 Attestation of the jar.** On a release, `actions/attest-build-provenance` records provenance for
  `ingot-<version>.jar` before the jar is uploaded, and the job verifies it with `gh attestation verify`.
- **R4 Narrow permissions.** `id-token: write` and `attestations: write` are granted only to the three jobs that sign or attest
  (`container`, `chart`, `jar`). The top-level `contents: read` stays. The Maven, changes and release-please jobs get no new
  scope.
- **R5 Verification documented.** The verify commands for the images, the chart and the jar go to the install guides and to
  `.github/RELEASE.md`, together with the note that nightlies are not signed.

Nothing else changes. Image names, tags, digests of the image content, the SBOM and provenance that BuildKit already writes,
the chart version, the jar name and every runtime behaviour stay as they are.

### Out of scope

- Signing nightly images. The `release-please.yml` comment at lines 68-76 and 346-349 records why a nightly does not get
  extra manifests pushed to GHCR. Signatures add one more manifest per digest, so they follow the same rule.
- The Maven artifacts published to `repo.onelitefeather.dev` by the reusable `gradle-publish.yml@v2.4.0` workflow. That
  workflow belongs to `OneLiteFeatherNET/workflows`.
- Admission policies in the chart, and any runtime check inside Ingot. Verification stays with the operator.
- A CycloneDX SBOM for the jar, VEX statements and reproducible builds (roadmap S11, which depends on this change).
- Pinning the existing action references by commit SHA and the Scorecard workflow. Both belong to `harden-ci-supply-chain`.
- Signing with a stored key, or with a key that a maintainer holds.

## Capabilities

### New Capabilities

- `release-artifact-signing`: keyless signatures on the release images and the chart, the attestation of the standalone jar,
  the identity they are verified against, and the documented verify commands.

## Impact

- **Type and scope.** The change ships under `ci(release)`. The pull request title, squash-merged onto main, is:

  `ci(release): sign release images, chart and jar`

  The documentation commits inside the pull request use `docs(release)` and `docs(deploy)`, as the house pattern does for
  `harden-http-security`. The squashed commit on main carries only the title.
- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin has to
  change. An installation pointed at Ingot pulls the same image and installs the same chart. The title carries no `!` and no
  commit carries a `BREAKING CHANGE:` footer.
- **Behaviour that changes.** Releases take a little longer, because the signing and verification steps run after the
  pushes. The registry gets one signature per release digest for each image and for the chart. Nothing changes for
  nightlies, for Maven clients or for the dashboard.
- **Dependencies.**
  - `harden-ci-supply-chain` (wave 1, proposal PR #85, not merged). The new action references in this change are pinned by
    commit SHA under its rule. Its C4 lists the write scopes that a job may hold. This change adds `id-token: write` and
    `attestations: write` to the three release jobs, so C4 is amended in the same review, not after it.
  - No other change in wave B touches `release-please.yml`.
  - `sign-release-artifacts` is a prerequisite for roadmap S11 (`build(release)`, CycloneDX and VEX).
- **Code.** `.github/workflows/release-please.yml`, a policy test under `.github/tests/`, `.github/RELEASE.md`, and
  `docs/gitbook/how-to/install-with-docker.md` and `docs/gitbook/how-to/run-on-kubernetes.md`.

## Verification

- The workflow policy test is written first and seen to fail on main (`test(release)`). It asserts the permissions, the
  release gating and the order of sign, verify and upload steps. It reads the workflow file from the repository, so it needs
  no network and no clock.
- A real signature cannot be produced outside a release. The first release after merge is the proof: its own verify steps
  must pass, and `cosign verify` and `gh attestation verify` run by hand on that release must succeed with the identity in
  `design.md`. A release that fails verification is a failed release, not a silent unsigned one.
- `openspec validate sign-release-artifacts --strict` passes, and the dash grep from the other proposals prints nothing.

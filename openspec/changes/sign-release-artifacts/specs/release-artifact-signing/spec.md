# Spec Delta

## Purpose

An operator can check that a release image, the Helm chart and the standalone jar were built by the release workflow of this
repository, without trusting a tag and without any key distributed by the maintainers. Nightlies and every runtime behaviour
stay as they are.

## ADDED Requirements

### Requirement: Release images are signed by digest

The release workflow SHALL sign the server image and the dashboard image of a release by their digest, and SHALL NOT sign
a tag. A signature SHALL be produced in keyless mode from the workflow's OIDC identity.

#### Scenario: Server image of a release

- **WHEN** a release is created and the server image is pushed
- **THEN** a signature exists for the pushed digest, and `cosign verify` with the release workflow identity succeeds for that digest

#### Scenario: Push retried

- **WHEN** the first push of an image fails and the retry succeeds
- **THEN** the signature is made for the digest of the retried push

### Requirement: Nightly images stay unsigned

The release workflow MUST NOT sign, attest or push any signature for a nightly image, and the signing steps MUST be skipped
whenever the `released` output is not `true`.

#### Scenario: Push to main without a release

- **WHEN** a push to main changes the image and no release is created
- **THEN** the nightly image is pushed without a signature, and no signing or attestation step runs

### Requirement: The Helm chart is signed by digest

The release workflow SHALL sign the OCI chart of a release by the digest that `helm push` reports, and SHALL verify that
signature before the chart job succeeds.

#### Scenario: Chart of a release

- **WHEN** the chart job pushes `ingot-<version>.tgz` to `oci://ghcr.io/onelitefeathernet/charts`
- **THEN** `cosign verify` succeeds for the chart digest with the release workflow identity

#### Scenario: Digest not reported

- **WHEN** `helm push` does not report a digest
- **THEN** the chart job fails and no signature is made for a guessed reference

### Requirement: The standalone jar carries a provenance attestation

The release workflow SHALL attest the standalone jar with build provenance before the jar is uploaded to the GitHub release,
and SHALL verify the attestation before the jar job succeeds.

#### Scenario: Jar of a release

- **WHEN** a release is created
- **THEN** `gh attestation verify` succeeds for `ingot-<version>.jar` against the repository

#### Scenario: Attestation fails

- **WHEN** the attestation step fails
- **THEN** the jar is not uploaded to the release

### Requirement: The signing identity is the release workflow on main

Signatures and attestations MUST be verified against the identity of `release-please.yml` running on `refs/heads/main` in
`OneLiteFeatherNET/ingot`. A run whose identity differs MUST fail.

#### Scenario: Rebuild dispatched from another branch

- **WHEN** a `workflow_dispatch` rebuild with `container_version` runs on a branch other than main
- **THEN** the signature verification fails, the job is reported as failed, and the release is not reported as successful

### Requirement: Only the release jobs can request an identity token

The `id-token` and `attestations` write permissions MUST be declared only by the `container`, `chart` and `jar` jobs. The
top-level permission of the workflow MUST remain `contents: read`.

#### Scenario: Other jobs

- **WHEN** the workflow is read
- **THEN** the `changes`, `maven` and `release-please` jobs declare no `id-token` and no `attestations` permission, and the top-level permissions are `contents: read`

### Requirement: Signing leaves the pulled artifacts unchanged

Signing and attestation MUST NOT change any image tag, the content digest of an image, the chart version, the jar name or
the SBOM and provenance that BuildKit already attaches to the images.

#### Scenario: Operator pulls a release

- **WHEN** an operator runs `docker pull ghcr.io/onelitefeathernet/ingot:<version>` after this change
- **THEN** the image digest equals the digest of the image released before this change, and the run command, port and volume are unchanged

### Requirement: Verification is documented

The install guides and `.github/RELEASE.md` SHALL give the verify commands for the server image, the dashboard image, the
chart and the jar, and SHALL state that nightly images are not signed.

#### Scenario: Reading the Docker install guide

- **WHEN** a reader opens `docs/gitbook/how-to/install-with-docker.md`
- **THEN** the page contains a `cosign verify` command for the image with the release workflow identity, and the note that nightly tags are not signed

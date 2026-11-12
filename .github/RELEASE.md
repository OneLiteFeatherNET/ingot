# Release guide

Releases are automated. There is no command a maintainer runs by hand, and there is no
release branch: the version lives in the repository and
[Release Please](https://github.com/googleapis/release-please) owns it.

## How a release happens

1. Commits land on `main` following [Conventional Commits](https://www.conventionalcommits.org/).
   `feat` bumps the minor version, `fix` the patch, and a `!` or a `BREAKING CHANGE:`
   footer the major.
2. Release Please opens or updates a release pull request. It carries the changelog and
   the version bump across `build.gradle.kts`, the frontend manifests and the Compose
   example. Do not edit those version lines by hand.
3. Merging that pull request creates the tag and the GitHub release. The same workflow run
   then publishes the Maven artifacts to `repo.onelitefeather.dev`, attaches the standalone
   `ingot-<version>.jar` to the release, and pushes the container image to
   `ghcr.io/onelitefeathernet/ingot`.

A push to `main` that produces no release still refreshes the `nightly` container image.

Everything above lives in [`release-please.yml`](workflows/release-please.yml) and
[`release-please-config.json`](../release-please-config.json).

## The release pull request needs its CI approved

This repository is still a GitHub fork of dzikoysk/reposilite, so workflow runs on pull
requests land in `action_required` and wait for a maintainer to press "Approve and run".
That includes the release pull request Release Please opens, which is why its checks look
stuck rather than failing.

Two ways out, both in Settings → Actions → General: relax "Fork pull request workflows", or
leave the fork network so the repository stands on its own. The second is irreversible, and
the attribution does not depend on it: that lives in NOTICE, the README and the git history.

## What a release needs

- `ONELITEFEATHER_MAVEN_USERNAME` and `ONELITEFEATHER_MAVEN_PASSWORD` repository secrets
  for the Maven publish. The container push authenticates with the built-in `GITHUB_TOKEN`
  and needs no secret.

## What a release attaches

The release page carries the standalone jar and two CycloneDX 1.6 SBOMs, each attested to the jar:

- `ingot-<version>.jar`, the standalone server.
- `ingot-<version>.cdx.json`, the libraries of the server jar.
- `ingot-frontend-<version>.cdx.json`, the npm packages of the dashboard that the jar contains.

How to verify and read the SBOMs is in [Verify the SBOMs of a release](../docs/gitbook/how-to/verify-release-sboms.md).

## Verify a release

Every release is signed. The server image, the dashboard image and the Helm chart are signed
keylessly with the identity of the release workflow on `main`, and the standalone jar carries
a build provenance attestation from the same workflow. No key is involved, so there is no
public key to fetch or trust: the check is that the signer is this workflow on `main`.

Replace `<version>` with the release, for example `3.5.29`. The signer identity is the same
for every command:

```sh
# Server image
cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/ingot:<version>

# Dashboard image
cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/ingot-dashboard:<version>

# Helm chart
cosign verify \
  --certificate-identity https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/onelitefeathernet/charts/ingot:<version>

# Standalone jar, downloaded from the release page
gh attestation verify ingot-<version>.jar \
  --repo OneLiteFeatherNET/ingot \
  --signer-workflow OneLiteFeatherNET/ingot/.github/workflows/release-please.yml \
  --source-ref refs/heads/main
```

Each command exits with an error when the signature or attestation is missing or was made by
another workflow, branch or repository.

Two things to know:

- Nightly images are not signed. The `nightly` tag carries no signature, so these commands
  fail for it by design. Only a release version is verifiable.
- A rebuild of a release (see below) is signed as well, but only when it is dispatched from
  `main`. A rebuild dispatched from another branch is signed by an identity that the check
  refuses, and the job fails after the push.

## Rebuilding a container image

The `Release Please` workflow takes a `workflow_dispatch` with a `container_version` input.
It runs only the container job, for the version you name, and leaves the release itself
alone. Dispatch it from `main`: the signature of a rebuild is accepted only from that branch
(see [Verify a release](#verify-a-release)).

## Adding a file whose version has to follow

Add it to `extra-files` in `release-please-config.json` and put an
`x-release-please-version` marker on the line holding the version, or use the `json`
updater with a JSON path. Keep markers out of snippets that readers copy: a stale example
version is a smaller problem than a confusing one.

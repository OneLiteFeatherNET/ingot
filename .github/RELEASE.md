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

## Plugins

Each release publishes the official plugins and a signed catalogue of them. The `plugins` job of
[`release-please.yml`](workflows/release-please.yml) does this after the container job of the same release, and the logic is in
[`publish_plugins.sh`](scripts/publish_plugins.sh) and [`generate_plugin_catalogue.py`](scripts/generate_plugin_catalogue.py).
Nightlies and pull requests never run it.

The repository gets, in this order:

1. `net/onelitefeather/ingot/<plugin>/<version>/<plugin>-<version>.jar` and its `.asc`, one pair per plugin.
2. `ingot-plugin-keys.asc`, every public key that has signed a plugin. A key is only ever added.
3. `ingot-plugin-catalogue.json` and its `.asc`, last. The catalogue lists every released version with its SHA-256 and its
   compatibility range. It is append-only: a listed version is never changed or removed.

Before anything is uploaded, the job checks that the jar bundled in the released image has the same SHA-256 as the jar it
publishes, so the store and the image never carry different bytes under one version. After the upload, the job downloads the
published files, anonymously, and checks every digest and signature against the public key. A mismatch fails the release and
names the coordinate.

### Environment and names

The job runs in the `plugin-release` environment, which needs required reviewers. Its secrets and variables are:

| Name | Kind | Holds |
|---|---|---|
| `PLUGIN_SIGNING_KEY` | secret | the armoured private signing key |
| `PLUGIN_SIGNING_PASSPHRASE` | secret | its passphrase, empty when the key has none |
| `PLUGIN_REPOSITORY_TOKEN` | secret | the deploy credential for the official repository path, sent as a bearer token |
| `PLUGIN_REPOSITORY_URL` | variable | the `https` base URL of the official repository |
| `PLUGIN_SIGNING_KEY_ID` | variable | the 40 character fingerprint of the signing key |

No value of these belongs in the repository, in a log or in a pull request. While `PLUGIN_REPOSITORY_URL` is empty, the job
writes a warning to its summary and uploads nothing. The release itself still succeeds, because the image and the jars do not
depend on the repository. A missing key or key id fails the job before anything is uploaded.

The key is created outside this pipeline. The job prints its fingerprint in the job summary of each release, so a maintainer can
compare it with `PLUGIN_SIGNING_KEY_ID`.

### Verify a published plugin

Import the key file into an empty keyring, then check the catalogue, a jar and its build provenance:

```sh
gpg --homedir "$(mktemp -d)" --import ingot-plugin-keys.asc
gpg --verify ingot-plugin-catalogue.json.asc ingot-plugin-catalogue.json
gpg --verify opensearch-plugin-<version>.jar.asc opensearch-plugin-<version>.jar
gh attestation verify opensearch-plugin-<version>.jar \
  --repo OneLiteFeatherNET/ingot \
  --signer-workflow OneLiteFeatherNET/ingot/.github/workflows/release-please.yml \
  --source-ref refs/heads/main
```

### Key rotation

1. Create the new key outside this pipeline. Put its private part in `PLUGIN_SIGNING_KEY` and its fingerprint in
   `PLUGIN_SIGNING_KEY_ID`.
2. The next release adds the new public key to `ingot-plugin-keys.asc`. Old keys stay in that file for as long as a version
   they signed is published. Never remove a key from the file while its versions exist.
3. The release notes name the new fingerprint. Operators add the new public key to `INGOT_PLUGIN_TRUSTED_KEYS`.
4. The old private key is destroyed or kept offline, as the maintainers decide. The environment holds only the current one.

### Repair

- **The run failed before the upload.** Nothing was published. Re-run the failed jobs of the run that created the release. A
  jar that is already listed must produce the same digest, so a re-run changes nothing that was already published.
- **The verification failed after the upload.** Do not delete any file from the repository. The repair is a yank, which marks
  one listed version with `"yanked": true` and changes nothing else. The generator provides it as
  `generate_plugin_catalogue.py yank --repository DIR --plugin ID --version VERSION`. No workflow run performs a yank yet, so
  until a follow-up change wires it in, a yank is made by a maintainer through that change and not by hand. A yanked version
  stays in the catalogue, is no longer selectable, and keeps loading where it is already cached.

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

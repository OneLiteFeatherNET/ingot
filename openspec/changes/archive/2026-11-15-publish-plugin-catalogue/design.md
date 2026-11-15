# Design

This design implements the publishing side of `add-plugin-store`. The catalogue format, the signature rules and the verification of
the operator are defined there and are not repeated here. Question numbers refer to `proposal.md`.

## Context

- `.github/workflows/release-please.yml` creates the release, exposes `tag_name`, and runs the Maven, standalone jar and image jobs
  after it. Nightly images are filtered by the `changes` job, and releases are never filtered.
- The image sets the bundled plugin jars by copying them into `/app/bundled-plugins` in the `Dockerfile`, next to
  `prometheus-plugin.jar` and `nexus-import-plugin.jar`. The `opensearch-plugin.jar` line is already there.
- `sign-release-artifacts` signs the images with keyless cosign and `gh attestation` covers provenance. Plugins use OpenPGP, because
  the store trusts keys an operator pins (`add-plugin-store`, D2 and D4).
- `.github/scripts` already holds Python scripts with tests under `.github/scripts/tests`, so the generator follows that layout.

## Decisions

### D1: releases only

The plugin job runs when a release is created, and only then. The `changes` filter is not used for it, because a plugin is published
with a version number and a version number exists only for a release. A nightly build does not upload a jar, does not rewrite the
catalogue and does not touch the environment. This keeps every catalogue entry tied to a release.

### D2: one environment, one key, no secret in the workflow

The job uses the environment `plugin-release` (question 1). The environment holds:

| Name | Kind | Use |
|---|---|---|
| `PLUGIN_SIGNING_KEY` | secret | the armoured private key |
| `PLUGIN_SIGNING_PASSPHRASE` | secret | its passphrase, empty when the key has none |
| `PLUGIN_REPOSITORY_URL` | variable | the official repository, a placeholder until question 2 is answered |
| `PLUGIN_REPOSITORY_TOKEN` | secret | the deploy credential (question 4) |
| `PLUGIN_SIGNING_KEY_ID` | variable | the fingerprint the verification step expects |

Handling in the job:

- The key is imported into a `GNUPGHOME` under `${RUNNER_TEMP}` with `gpg --batch --import` from an environment variable, never from a
  file in the checkout, and the directory is deleted in an `always()` step.
- The passphrase is passed through `--pinentry-mode loopback --passphrase-fd 0` from a pipe, so it never appears in an argument list.
- The job runs without `set -x`, masks every secret with `::add-mask::` where it is derived, and writes only public material (the public
  key, the fingerprint, the digests) to the job summary.
- A missing secret or a malformed key fails the job before any upload. A missing `PLUGIN_REPOSITORY_URL` does not fail the release: the
  job writes a warning to the summary and stops before the upload (question 2).

### D3: the upload order

1. The jars are built by the existing Gradle task that produces the standalone jar, from the release tag.
2. Each jar is signed: `gpg --detach-sign --armor` produces `<jar>.asc`. The job checks the signature with the public key it just
   exported, and fails on a mismatch.
3. The jars and their `.asc` files are uploaded to the Maven layout under the group `net.onelitefeather.ingot`.
4. The generator runs (D4), the catalogue is signed, and `ingot-plugin-catalogue.json` and its `.asc` are uploaded last.

The catalogue is the last file, so a reader that sees a new version in the catalogue always finds its jar. An upload that fails in step 3
leaves the old catalogue in place, and the job fails.

### D4: the generator and the history

`.github/scripts/generate_plugin_catalogue.py` takes:

- the directory of the published repository (a local mirror of the Maven layout, downloaded before the run),
- the release version and the generation time as arguments (`--generated`), so that the output is reproducible in a test,
- the list of plugin ids to publish with their compatibility values.

It reads the existing catalogue, adds the new version, computes the SHA-256 of each jar from the bytes, and writes the file with sorted
keys and a fixed indentation. Rules:

- The catalogue is append-only. An existing version is never removed or changed, so its digest stays what it was.
- A version that is already listed with the same digest is a no-op. The same version with a different digest is an error, and the job
  stops.
- A yank (`yanked: true`) is a separate, reviewed change to the file, made by a maintainer through the same job with `--yank <id> <version>`.
  It changes only that flag.
- The compatibility values (`min`, `below`, `pluginApi`) are set from the release, as `add-plugin-store` requires.

The generator has no network access. The job provides the mirror.

### D5: verification after upload

A final step downloads the published catalogue, its signature and each published jar, using only the public key of the repository
(`PLUGIN_SIGNING_KEY_ID` is the expected fingerprint, not a secret). It checks the catalogue signature, every digest against the catalogue,
and every jar signature. The step writes the result to the job summary and fails the release on any mismatch.

The step uses no secret beyond what an anonymous reader of the repository has, so a verifier outside the release job can repeat it.

### D6: the bundled jar is the published jar

The image build fetches nothing new. The `Dockerfile` already copies the jar from the Gradle output. The job compares the SHA-256 of
that jar with the digest the catalogue lists for the same version, and the release fails when they differ. A plugin built twice with
different bytes cannot reach the store and the image under one version.

### D7: provenance next to the PGP signature (question 5)

The jars also receive a build provenance attestation through `actions/attest-build-provenance`, the mechanism the image and the
standalone jar already use. This needs `id-token: write` and `attestations: write` on the job only. The attestation does not replace
the PGP signature. The store verifies PGP; a security reviewer can check the attestation with `gh attestation verify`.

### D8: key rotation (question 3)

A rotation is a release-note event:

1. A maintainer creates the new key outside this pipeline and adds its public part to the environment as the new key id.
2. The repository root gains `ingot-plugin-keys.asc` with every public key ever used. Old keys stay in the file while any version they
   signed is published.
3. The release notes name the new fingerprint. Operators copy the new public key into `INGOT_PLUGIN_TRUSTED_KEYS`.
4. The old private key is destroyed or kept offline, as the maintainers decide, and the environment holds only the new one.

Store clients trust every key in the file they are configured with, so an operator who has not yet added the new key sees the new
versions refused with a clear signature reason, and nothing else breaks.

### D9: failure and repair

A failed upload before step 4 of D3 leaves the catalogue unchanged, so a re-run of the job is safe: the generator recognises identical
digests and does nothing. A failed verification after an upload is repaired by a maintainer with the yank path of D4, never by deleting
files from the repository. The repair procedure is written in `.github/RELEASE.md`.

## Testing

The generator is tested with committed fixtures under `.github/scripts/tests`. Each test uses its own temporary directory and the
`--generated` argument, so the output does not depend on the clock, and it makes no network call. A test asserts the append-only rule,
the refusal of a changed digest, the yank change and the byte-identical output of two runs. The workflow is linted with `actionlint`.
The job itself is exercised once as a dry run against a fixture repository, configured through a non-release input, before the first
real release.

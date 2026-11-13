# Spec Delta

## Purpose

Each Ingot release publishes its official plugins and a signed catalogue to a Maven repository. The catalogue is append-only, every
published jar is signed and verified after upload, and the jar in the image is the jar that is published.

## ADDED Requirements

### Requirement: Releases only

The plugin publishing job SHALL run only when a release is created. A nightly build or a pull request SHALL NOT upload a plugin, change
the catalogue or read the signing environment.

#### Scenario: Nightly build

- **WHEN** the nightly workflow runs without a release
- **THEN** the plugin job is not started and no file is uploaded to the repository

### Requirement: Signing key handling

The signing key and its passphrase SHALL be read only from the `plugin-release` environment and SHALL NOT be printed, echoed, written
to the checkout or uploaded as an artifact. The job SHALL run with the minimum permissions it needs and SHALL remove its temporary key
directory when it ends.

#### Scenario: Key never printed

- **WHEN** the job runs with a valid key
- **THEN** the job log and the job summary contain the public key and its fingerprint, and neither the private key nor the passphrase

#### Scenario: Missing key

- **WHEN** the signing key secret is empty
- **THEN** the job fails before any upload and says that the key is missing, without printing any other value

### Requirement: Missing repository configuration

When the repository URL variable is not set, the job SHALL write a warning to the job summary and SHALL stop before any upload. The release
SHALL NOT fail for that reason.

#### Scenario: Repository not configured

- **WHEN** `PLUGIN_REPOSITORY_URL` is empty
- **THEN** no file is uploaded, the summary carries the warning, and the image and the jars of the release are still published

### Requirement: Signed jars with provenance

Every published plugin jar SHALL carry an OpenPGP detached signature, checked by the job against the exported public key before upload, and
a build provenance attestation from the same workflow run.

#### Scenario: Signature check fails

- **WHEN** a jar signature does not verify against the exported public key
- **THEN** the job fails and nothing is uploaded for that release

### Requirement: Append-only catalogue

The generator SHALL keep every released version in `ingot-plugin-catalogue.json`. An existing version SHALL NOT be removed or changed, and a
version whose digest differs from the listed one SHALL stop the job. A withdrawn version SHALL be marked `yanked` by a separate change of
the flag only.

#### Scenario: Re-run of a release

- **WHEN** the job runs again for a version with the same digest
- **THEN** the catalogue is unchanged and the job succeeds

#### Scenario: Digest changed for a listed version

- **WHEN** the rebuilt jar of a listed version has another SHA-256
- **THEN** the job stops and uploads nothing

#### Scenario: Yank

- **WHEN** a maintainer yanks a listed version
- **THEN** only its `yanked` flag changes and no other entry or file changes

### Requirement: Generation is deterministic

The generator SHALL take the generation time as an argument. Two runs with the same inputs SHALL produce byte-identical output, and the
generator SHALL make no network call.

#### Scenario: Two runs

- **WHEN** the generator runs twice with the same fixtures and the same `--generated` value
- **THEN** both outputs are identical

### Requirement: Upload order

Jars and their signatures SHALL be uploaded before the catalogue, and the catalogue SHALL be uploaded last. A failed jar upload SHALL leave
the previous catalogue in place.

#### Scenario: Jar upload fails

- **WHEN** the upload of a jar fails
- **THEN** the catalogue is not uploaded and the job fails

### Requirement: Verification after upload

After the upload the job SHALL download the catalogue, its signature and every published jar with its signature, using only the public key,
and SHALL check every digest and signature. A mismatch SHALL fail the release.

#### Scenario: Published jar differs

- **WHEN** a downloaded jar does not match its catalogue digest
- **THEN** the release job fails and the summary names the coordinate

### Requirement: The image bundles the published jar

The jar that the image bundles for `opensearch-plugin` SHALL have the same SHA-256 as the published jar of the same version. The release SHALL
fail when they differ. `INGOT_OPENSEARCH_ENABLED` and the bundled switch SHALL keep their meaning.

#### Scenario: Same bytes

- **WHEN** the release builds the image and publishes the plugin
- **THEN** the digest of `/app/bundled-plugins/opensearch-plugin.jar` equals the digest in the catalogue

### Requirement: Maintainer documentation

`.github/RELEASE.md` SHALL describe the plugin job, the environment and the names of its secrets and variables, the key rotation and the
repair of a failed upload. It SHALL NOT contain a secret, a token or a private key.

#### Scenario: Repair after failed verification

- **WHEN** a verification fails after an upload
- **THEN** the documented repair uses the yank path, and no file is deleted from the repository

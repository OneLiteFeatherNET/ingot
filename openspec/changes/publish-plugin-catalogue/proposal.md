# Proposal

## Open maintainer questions

Each question carries the default this proposal uses. None of them blocks the proposal PR; the code PR waits for answers 1, 2
and 4.

1. **Where the private signing key lives.** Default: a GitHub environment named `plugin-release` with required reviewers. The
   armoured private key and its passphrase are secrets of that environment, readable only by the release job of a published
   release. Reason: the key never leaves the release job, and a reviewer approves each use. Alternatives: a cloud KMS or a
   hardware token held by a maintainer (stronger, but a manual step per release), or Sigstore keyless signing if the maintainers
   pick Sigstore in `add-plugin-store` (question 1). Question: confirm the environment, and who the reviewers are.
2. **Target repository URL.** Placeholder: `<official-repository-url>`, read from an environment variable of the same environment,
   never written into the workflow. Default: until the variable is set, the publish job writes a visible warning to the job summary
   and the release still succeeds, because the image and the jars do not depend on the repository. Question: which host serves the
   official repository, and whether a missing repository should fail the release instead.
3. **Key rotation and revocation.** Default: a rotation adds the new public key to the repository root as
   `ingot-plugin-keys.asc`, the release notes name the new fingerprint, and operators replace the file in
   `INGOT_PLUGIN_TRUSTED_KEYS`. Signatures of older versions stay valid for the old key, which remains in the key file for
   as long as those versions are published. Question: confirm that old keys are never removed while their versions exist.
4. **Who may write to the repository.** Default: a deploy credential scoped to the official repository path, stored in the
   `plugin-release` environment as a secret, rotated yearly. Question: confirm the scope and the owner of the credential.
5. **Provenance attestation next to the PGP signature.** Default: the jars also get a build provenance attestation from the same
   workflow run (the same mechanism the image and the standalone jar use), so that a verifier can check either the key or the
   workflow. Question: keep both, or PGP only.
6. **Plugin version.** Default: a plugin is released with the Ingot version of the same release, so the compatibility range of
   `add-plugin-store` is one version number per release. Question: confirm.

## Why

`add-plugin-store` lets an operator pick plugins from a catalogue in a Maven repository, and it refuses any jar whose signature or
digest does not verify. Such a catalogue needs a publisher. Today the only plugin that Ingot publishes is the OpenSearch plugin, and
it reaches users inside the image, where its jar is copied in by the Dockerfile and nothing records its digest or its signature. An
operator who wants the same plugin from the store has no official source to point at, and a plugin author has nowhere to publish.

The release is the right moment for this. The release pipeline already builds the jars, signs the images and attaches the SBOMs, and
a release is the artefact that anyone audits. Publishing from the same pipeline, with the same run identity, means the jar an operator
installs from the store is the jar the image bundles, byte for byte, and the catalogue cannot list a version that was never released.

The signing key is the sensitive part. The store trusts only the keys an operator configured, so a leaked or misused key would make
a malicious jar loadable on every instance that trusts the official key. The design keeps the key inside one protected environment,
never prints it, and never writes it to a file that outlives the job.

## What Changes

- **A release job that publishes the official plugins.** When a release is created, a job in `.github/workflows/release-please.yml`
  builds `opensearch-plugin`, signs each jar with the release signing key (OpenPGP detached `.asc`), attests its provenance, and
  uploads the jars and their signatures to the official Maven repository. The job runs in the `plugin-release` environment.
- **The catalogue.** A generator, `.github/scripts/generate_plugin_catalogue.py`, writes `ingot-plugin-catalogue.json` from the
  published versions and their SHA-256 digests. The job signs the catalogue with the same key and uploads it with its `.asc`.
  The generator is deterministic for a given input and a given generation time, and the time is an argument.
- **Append-only history.** The catalogue keeps every released version. A version that must be withdrawn is marked `yanked`, and it
  is never deleted from the repository, because a cached copy of it still has to verify.
- **Order and verification.** Jars are uploaded before the catalogue, so the catalogue never names a missing file. After the upload a
  verification step downloads the published files with the public key only, checks every digest and signature, and fails the
  release if one does not match.
- **Bundled jar identical to the published jar.** The image copies the same `opensearch-plugin.jar` that the job publishes, and a
  check compares their SHA-256. `INGOT_OPENSEARCH_ENABLED` keeps its meaning, and the bundled jar keeps its place in
  `PluginLoader.BUNDLED_SWITCHES`.
- **Releases only.** Nightlies never publish a plugin, so that the catalogue changes only with a release and every published version
  has a release behind it.
- **Maintainer documentation.** `.github/RELEASE.md` gets a section on the plugin job, the environment, the key rotation and the
  repair of a failed upload. No secret is written there, only the names of the environment and its variables.

### Out of scope

- The release signing of the container images. They keep the keyless cosign signature of `sign-release-artifacts`. The plugin
  catalogue uses OpenPGP because the operator trusts keys, not identities (see `add-plugin-store`, design D4).
- Choosing or hosting the official repository. The URL is a placeholder and the hosting decision belongs to the maintainers.
- Publishing plugins other than `opensearch-plugin`. The job takes a list, so a later plugin is one line, but no other plugin is
  published by this change.
- Any change to the store client, its verification or its cache. Those are `add-plugin-store`.
- A key ceremony or a key generation. The key is created by a maintainer outside this change and stored as described in question 1.
- Publishing from pull requests, forks or nightlies.

## Capabilities

### New Capabilities

- `plugin-catalogue-publishing`: the release job, the signing rules, the catalogue generation and history, the upload order, the
  verification after upload, the identity of the bundled jar and the maintainer documentation.

## Impact

- **Type and scope.** The change ships under `ci(release)`. Its commits are `test(release)` for the generator and `ci(release)` for
  the workflow, the environment notes and the documentation. The pull request title is:

  `ci(release): publish plugins and their catalogue to a maven repository`

- **Breaking marker.** No breaking change for operators. No configuration key, environment variable, volume, manifest, route or plugin
  changes. The image still contains `opensearch-plugin.jar` at the same path with the same bytes. Operators who never open the store
  see no difference. A maintainer must create the `plugin-release` environment and its secrets before the first release publishes;
  until then the job warns and the release succeeds (question 2).
- **Secrets.** Three secrets of the environment: the armoured private key, its passphrase and the repository deploy credential. None is
  printed, echoed, written to the workspace or uploaded as an artifact. The job runs with `permissions` set to the minimum it needs
  (`contents: read`, `id-token: write` and `attestations: write` for the provenance step), no `set -x`, and the key is imported into a
  temporary `GNUPGHOME` that is removed when the job ends.
- **Dependencies.** Python for the generator, which the repository already uses in `.github/scripts`, and the GnuPG binary of the
  runner. No new Gradle dependency.
- **Order.** Depends on `add-plugin-store` for the catalogue format and the compatibility fields, so the generator follows that
  change's catalogue schema version 1. It does not block `add-plugin-store`: the store can be built and tested against a local
  fixture catalogue first.

## Verification

- Unit tests of the generator with committed fixtures, a generation time passed as an argument, and a temporary directory per test.
  They cover the append-only rule, yanked entries, the digest of the jar and the refusal of a duplicate version.
- The workflow lints with `actionlint` in the existing CI. A dry run of the job with a fixture repository, in a non-release
  configuration, checks the upload order and the verification step without touching the official repository.
- The bundled jar check compares the SHA-256 of the image jar with the published jar, and the container job's upgrade rehearsal keeps
  passing.
- `openspec validate publish-plugin-catalogue --strict` passes and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/publish-plugin-catalogue` prints nothing.

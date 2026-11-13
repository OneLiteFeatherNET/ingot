# Tasks

Each group is one or more commits of one Conventional Commits type each, all under the scope `release`. Generator tests are written
first and seen to fail before the script exists. Every test follows F.I.R.S.T.: the generation time is an argument, no network, a
temporary directory per test, no shared state, and assertions with messages. No task prints, commits or stores a secret.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose publish-plugin-catalogue`: this change and nothing else, tasks unchecked, branched from the remote
  main branch. Verify: `openspec validate publish-plugin-catalogue --strict` passes and the dash grep from the proposal prints
  nothing.

## 2. Generator (red, then green)

- [ ] 2.1 `test(release)`: failing tests in `.github/scripts/tests` with committed fixtures: a new version is appended; a listed
  version with the same digest is a no-op; a listed version with another digest stops the run; a yank changes only the flag; two runs
  with the same `--generated` are byte-identical; no network is used. Verify: they fail on the missing script.
- [ ] 2.2 `ci(release)`: add `.github/scripts/generate_plugin_catalogue.py` with the rules of design D4. Verify: the tests of 2.1 pass.

## 3. Release job (red, then green)

- [ ] 3.1 `ci(release)`: add a job to `.github/workflows/release-please.yml` that runs only after a release is created, in the
  environment `plugin-release`, with `permissions` limited to `contents: read`, `id-token: write` and `attestations: write`. It builds
  the jars, imports the key into a temporary `GNUPGHOME` from the environment, signs each jar, checks each signature, and removes the
  directory in an `always()` step. Verify: `actionlint` passes and a dry run against a fixture repository succeeds with the secrets
  unset (it fails before any upload with the message of the missing key).
- [ ] 3.2 `ci(release)`: add the empty repository case of the design (a warning in the job summary, no upload, the release not
  failed) and the upload order of D3. Verify: the dry run with `PLUGIN_REPOSITORY_URL` unset writes the warning and uploads nothing.
- [ ] 3.3 `ci(release)`: add the provenance attestation with `actions/attest-build-provenance` for each jar, pinned by commit SHA as
  the other jobs pin their actions. Verify: `actionlint` passes and the pins match the existing workflow convention.

## 4. Verification and bundled jar identity (red, then green)

- [ ] 4.1 `ci(release)`: add the post-upload verification of D5 with the public key only, and the summary of each coordinate. Verify:
  a fixture run with a corrupted jar fails and names the coordinate.
- [ ] 4.2 `ci(release)`: add the comparison of the SHA-256 of `/app/bundled-plugins/opensearch-plugin.jar` in the image with the
  published digest, failing the release on a difference. Verify: the container job's upgrade rehearsal keeps passing with the plugin
  switched off.

## 5. Documentation

- [ ] 5.1 `docs(release)`: add the plugin section to `.github/RELEASE.md`: the environment and the names of its secrets and variables,
  the key rotation of D8, the yank repair of D4 and D9, and no secret value. Verify: a search of the file for the words "BEGIN PGP" and
  "token=" finds nothing.

## 6. Code pull request

- [ ] 6.1 Open the code pull request under the title `ci(release): publish plugins and their catalogue to a maven repository`, from a
  branch of the remote main branch that already contains the merged proposal and the merged `add-plugin-store` catalogue format, with
  no file under `openspec/`.

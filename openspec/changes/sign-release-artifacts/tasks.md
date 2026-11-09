# Tasks

Each group is one or more commits of one Conventional Commits type each. The workflow change is scoped `release`. Tests are
written first and seen to fail before the workflow changes. Every test follows F.I.R.S.T.: no sleeps or waits by time, no
system clock, no shared mutable state, the workflow file read fresh in each test, and assertions with messages. Heavy
verification runs through `HEAVY_SLOTS=1 heavy` with `--no-daemon --max-workers=2` if a Gradle run is needed. The policy test
is plain Python and runs without Gradle.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose sign-release-artifacts`: this change and nothing else. Verify:
  `openspec validate sign-release-artifacts --strict` passes and the dash grep from the proposal prints nothing.

## 2. Release workflow policy test

- [ ] 2.1 `test(release)`: add `.github/tests/test_release_workflow.py`, reading `.github/workflows/release-please.yml` with
  PyYAML. Write failing tests, one behaviour each: the top-level permissions are `contents: read`; the `container`, `chart`
  and `jar` jobs declare `id-token: write`; `container` and `jar` declare `attestations: write`; `changes`, `maven` and
  `release-please` declare neither; every `cosign` and attestation step has an `if` that mentions `released` or
  `release_created`; the server and dashboard retry steps have ids; the jar attest step comes before the upload step.
  Verify: `python3 -m pytest .github/tests -q` fails on main, and each failure names the missing property.

## 3. Keyless signatures and attestation

- [ ] 3.1 `ci(release)`: give the server and dashboard retry steps the ids `server-retry` and `dashboard-retry`. Add
  `id-token: write` and `attestations: write` to the `container` job. Add a signing step for both images, gated on `released`,
  that signs each image by digest, taking the digest from the first push or the retry, and that retries once after the same
  wait the image push uses. Install cosign with `sigstore/cosign-installer`, pinned by commit SHA with a version comment. Then
  verify both signatures against `https://github.com/OneLiteFeatherNET/ingot/.github/workflows/release-please.yml@refs/heads/main`
  with the issuer `https://token.actions.githubusercontent.com`. Verify: the tests of 2.1 about the container job pass, and
  the signing step is skipped on a nightly run in the workflow's own logic (the `if` names `released`).
- [ ] 3.2 `ci(release)`: in the `chart` job, add `id-token: write`. Capture the `Digest:` line that `helm push` prints, fail
  when it is missing, sign `oci://ghcr.io/onelitefeathernet/charts/ingot@<digest>` with cosign, and verify it with the same
  identity. Gate every step on `release_created`. Verify: the tests of 2.1 about the chart job pass, and the job fails when the
  digest line is absent (a shell check in the step, covered by the reasoning in `design.md`).
- [ ] 3.3 `ci(release)`: in the `jar` job, add `id-token: write` and `attestations: write`. Run
  `actions/attest-build-provenance`, pinned by commit SHA, on `reposilite-backend/build/libs/ingot-${VERSION}.jar` before the
  `gh release upload` step, then verify with `gh attestation verify`. Verify: the ordering test of 2.1 passes, and the upload
  step still runs only when `release_created` is `true`.
- [ ] 3.4 `ci(release)`: confirm that no step outside the release-gated jobs requests an identity token, and that the top-level
  permissions are unchanged. Verify: the whole of `.github/tests` passes.

## 4. Documentation

- [ ] 4.1 `docs(release)`: add a section "Verify a release" to `.github/RELEASE.md` with the `cosign verify` command for each
  image, the `cosign verify` command for the chart, and `gh attestation verify` for the jar, all with the identity in
  `design.md`. State that nightly images are not signed and that a rebuild of a release is dispatched from main. Verify: each
  command in the section matches the identity used by the workflow, checked by eye against task 3.1.
- [ ] 4.2 `docs(deploy)`: add a short verification step before the first install to
  `docs/gitbook/how-to/install-with-docker.md` and `docs/gitbook/how-to/run-on-kubernetes.md`, with the `cosign verify` command
  for the image and the chart, and a note on Flux `verify.provider: cosign` for `OCIRepository`. Mention that the commands are
  optional and that the default install is unchanged. Verify: both pages link to `.github/RELEASE.md` for the identity and
  nothing else in them changes.

## 5. Pull request

- [ ] 5.1 Open the code pull request with the title `ci(release): sign release images, chart and jar`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections, ticking `build` / `ci` / `chore`. The description tells the story
  of an operator who cannot tell a release from a look-alike push, without workflow internals beyond what a reviewer needs.
  It carries no session link and no attribution line. Verify: `gh pr view --json title` shows the exact title, the commit-style
  hook accepts the title and every commit, and the body has no em or en dash.

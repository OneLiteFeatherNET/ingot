# Tasks

Each group is one or more commits of one Conventional Commits type each. The pull request title is `build(release)`,
because the main artifact is build output. Commits inside the pull request use `test(release)` for the failing tests,
`ci(release)` for the validation tooling and the workflows, `build(release)` for the Gradle build, and `docs(release)`
for the documentation. No group changes an operator-facing configuration key, environment variable, image or
manifest.

Every test follows F.I.R.S.T.: no sleeps, no clock reads, no network, one behaviour per test, each test reads its own
fixture copy from a temporary directory, and the workflow files are read fresh in each test. The validation tests and
the workflow shape tests live in `.github/scripts/tests/`, which the `images` job of `ci.yml` runs, so they run on every
pull request. The Gradle task is run through `heavy` with `--no-daemon --max-workers=2`.

## 1. Proposal

- [ ] 1.1 `docs(openspec): propose attach-release-sboms`: this change and nothing else. Verify:
  `openspec validate attach-release-sboms --strict` passes and the dash grep from the proposal prints nothing.

## 2. Failing tests first

- [ ] 2.1 `test(release)`: add `.github/scripts/tests/test_verify_sbom.py` with small hand-written CycloneDX fixtures
  under `.github/scripts/tests/fixtures/sbom/`, one behaviour each: a valid document passes; `specVersion` other than
  `1.6` fails; a missing `metadata.timestamp` fails; a component without `purl` fails; a duplicate `bom-ref` fails; a
  dangling dependency reference fails and names the reference; a licence share below the threshold fails; the hash and
  supplier shares are printed and do not fail; a schema violation fails. Verify: the module fails on main with an import
  error naming `verify_sbom`.
- [ ] 2.2 `test(release)`: add `.github/scripts/tests/test_release_sbom_workflow.py`, reading `release-please.yml` and
  `ci.yml` with PyYAML, with failing tests: the `jar` job generates both SBOMs only after `shadowJar`; the validation step
  comes before the attestation steps; both SBOM attestation steps come before the upload step; the upload step names both
  SBOM files; every SBOM step carries a `release_created` condition; the `sbom` job of `ci.yml` declares
  `contents: read` only and does not request `id-token`; no SBOM step appears outside the `jar` job and the `sbom` job.
  Verify: the module fails on main, and each failure names the missing property.

## 3. Validation tooling

- [ ] 3.1 `ci(release)`: add `.github/scripts/verify_sbom.py` (standard library plus `jsonschema`) and the vendored CycloneDX
  1.6 schema files under `.github/scripts/schemas/` with their licence notice, and add the pinned `jsonschema` install to
  the `images` job of `ci.yml` before the discover command. Make the tests of 2.1 pass. Verify: the discover command is
  green locally, and the script prints the licence and hash shares for each fixture.

## 4. Generators

- [ ] 4.1 `build(release)`: apply the CycloneDX Gradle plugin to `:reposilite-backend` at the exact version current at
  apply time, with the configuration of `design.md` decision 3. Verify:
  `HEAVY_SLOTS=1 heavy ./gradlew :reposilite-backend:cyclonedxBom --no-daemon --max-workers=2` produces
  `ingot-<version>.cdx.json`, the file passes `verify_sbom.py`, and its components match
  `./gradlew :reposilite-backend:dependencies --configuration runtimeClasspath` in count, with the difference recorded in the
  pull request.
- [ ] 4.2 `build(release)`: confirm the npm generator on `reposilite-frontend/package-lock.json` with the exact pinned
  version: the production-only flag, the spec version and the output path. If the pinned tool fails, use `npm sbom` and
  update `design.md` decision 4 in the same commit. Verify: the output passes `verify_sbom.py` and contains no `devDependencies`
  package, checked by name against the lockfile.

## 5. Workflows

- [ ] 5.1 `ci(release)`: add the `sbom` job to `.github/workflows/ci.yml` on pull requests into `main`, with
  `contents: read`, Java 21, the Gradle set-up action, the Gradle SBOM task, the npm generator and `verify_sbom.py` for
  both files. Verify: the tests of 2.2 about `ci.yml` pass.
- [ ] 5.2 `ci(release)`: extend the `jar` job of `.github/workflows/release-please.yml` with the Node set-up at the version
  `ci.yml` uses, the frontend SBOM step, the validation step, the two `actions/attest-sbom` steps pinned by SHA with the
  attestation step of the jar kept as it is, the `gh attestation verify` steps with the CycloneDX predicate type, and the
  upload of both SBOM files after the verification. Every step carries the `release_created` condition. Verify: the
  tests of 2.2 about `release-please.yml` pass, `python3 -m unittest discover -s .github/scripts/tests` is green, and the
  `jar` job still declares only `contents: write`, `id-token: write` and `attestations: write`.

## 6. Documentation

- [ ] 6.1 `docs(release)`: list the release assets, including both SBOMs, in `.github/RELEASE.md`. Verify: the list matches
  the upload step names of 5.2.
- [ ] 6.2 `docs(release)`: add `docs/gitbook/how-to/verify-release-sboms.md` and its entry in `docs/gitbook/SUMMARY.md`.
  The page downloads the SBOMs next to the jar, runs `gh attestation verify --predicate-type https://cyclonedx.org/bom`
  for the jar, reads the component list, and names the TR-03183-2 mapping of `design.md` with its gaps. Verify: the
  predicate type and file names on the page are the ones the first release produced, and the page links to
  `.github/RELEASE.md` for the list of assets.

## 7. Pull request

- [ ] 7.1 Open the code pull request with the title `build(release): attach cyclonedx sboms to releases`. Read
  `.github/PULL_REQUEST_TEMPLATE.md` and fill its sections. The description tells the story of an operator who needs to
  know what is inside a release before running it, and of a release that refuses to publish an SBOM it cannot check,
  without workflow internals beyond what a reviewer needs. It carries no session link and no attribution line. Verify:
  `gh pr view --json title` shows the exact title, the commit-style hook accepts the title and every commit, and the body
  has no em or en dash.

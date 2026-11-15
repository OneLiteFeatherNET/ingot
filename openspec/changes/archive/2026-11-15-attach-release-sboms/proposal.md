# Proposal

## Why

A release of Ingot now carries signatures and a build provenance for the jar, the images and the chart
(`sign-release-artifacts`). What an operator cannot yet get is a list of what is inside the artifact. The standalone jar
is a fat jar of Kotlin, Java and Maven dependencies, and the dashboard is compiled into it as JavaScript, so a reader who
asks "which libraries, at which versions, under which licences, and does this one have a CVE" gets no machine-readable
answer for the jar. The container images carry a BuildKit SBOM, but it is written in BuildKit's own format for the image
filesystem, and it is attached only to the image manifests, not to the release page where the jar lives.

Public-sector buyers in Germany are asked for an SBOM that follows BSI TR-03183-2, and the EU Cyber Resilience Act asks
manufacturers to supply one with each product. An SBOM the operator can download next to the jar, check against its
signature and feed into a scanner answers both questions with the same file.

Facts checked against origin/main (`db0717b2b`, with `sign-release-artifacts` merged as `c08f812d4`):

- `reposilite-backend/build.gradle.kts:45` depends on `project(":reposilite-frontend")`, so the dashboard is inside the
  jar. A Gradle SBOM of the backend does not see the npm packages whose code is bundled, so the frontend needs its own
  SBOM.
- The `jar` job of `release-please.yml` builds `:reposilite-backend:shadowJar`, attests it with
  `actions/attest-build-provenance`, verifies the attestation and uploads `ingot-<version>.jar` to the GitHub release.
  It runs only when `release_created` is `true`, so nightlies never reach it.
- The `container` job attaches a BuildKit SBOM to each release image (`release-please.yml`, the `sbom:` inputs). That is
  unchanged by this change.
- No Gradle plugin for CycloneDX, no `cyclonedx-npm` and no SBOM file is produced anywhere in the repository. A search of
  the build files, the workflows and the frontend manifest finds none.
- `release-please-config.json` has no `changelog-sections` key, so release-please uses its default sections: `feat`, `fix`,
  `perf`, `revert` and `docs` appear in CHANGELOG.md, and `build`, `ci`, `chore`, `refactor`, `style` and `test` do not.
- The release policy test in `.github/tests/test_release_workflow.py` is not run by any job in `ci.yml`. The only
  discovery command in CI is `python3 -m unittest discover -s .github/scripts/tests`. This change puts its new tests where
  CI runs them.

## What Changes

- **C1 CycloneDX SBOM of the server jar.** The backend applies the CycloneDX Gradle plugin at a pinned version and
  produces `ingot-<version>.cdx.json` in CycloneDX 1.6 JSON, covering the runtime classpath that the shadow jar contains.
- **C2 CycloneDX SBOM of the dashboard.** A pinned `@cyclonedx/cyclonedx-npm` produces `ingot-frontend-<version>.cdx.json`
  from `reposilite-frontend/package-lock.json`, production dependencies only.
- **C3 Attestation of both SBOMs.** `actions/attest-sbom`, pinned by commit SHA, attaches both SBOMs to the jar as subject
  in the release job, before the upload. `gh attestation verify` checks them with the CycloneDX predicate type.
- **C4 Release assets.** Both SBOM files are uploaded to the GitHub release next to the jar, after their attestations
  verify.
- **C5 Verification in CI.** A script validates each SBOM against the CycloneDX 1.6 JSON schema vendored in the
  repository and checks the fields listed in `design.md`. It runs in a new `sbom` job on every pull request, so that a
  broken SBOM fails the pull request rather than a release.
- **C6 Documentation.** A how-to page in `docs/gitbook/how-to/` explains how to download, verify and read the SBOMs, and
  `.github/RELEASE.md` lists the new assets.

### Out of scope

- VEX statements. They are the follow-up `add-vex-documents`, which depends on this change.
- SBOMs for the container images as separate release assets. The BuildKit attestations stay as they are.
- SPDX output. CycloneDX 1.6 is the single format of this change; a second format is a later decision.
- Reproducible builds and the rebuild check that the roadmap names as S11 follow-up work.
- Signing a stored key, a detached signature file or an SBOM for nightly images. Nightlies stay unsigned and without SBOM
  assets, as `CLAUDE.md` requires.
- The Maven artifacts published to `repo.onelitefeather.dev`, which belong to the reusable workflow of
  `OneLiteFeatherNET/workflows`.
- Filling BSI TR-03183-2 fields that cannot be filled from the build. `design.md` lists them.

## Capabilities

### New Capabilities

- `release-sbom`: the CycloneDX SBOMs of the server jar and the dashboard, their attestation, their assets on the release
  page, the CI validation and the documentation that tells an operator how to use them.

## Impact

- **Type and scope.** The change ships under `build(release)`. `build` is the type for build output and build tooling, and
  the Gradle plugin and the packaging step belong to it. `ci` would describe the workflow alone, and this change's main
  artifact is the build output. `build` does not appear in CHANGELOG.md under the default sections, so the release notes
  do not gain a line for every SBOM. The operator-visible part is the release page and the how-to page, which the
  documentation commit covers. The pull request title, squash-merged onto main, is:

  `build(release): attach cyclonedx sboms to releases`

  Commits inside the pull request keep one type each: the Gradle and npm commits use `build(release)`, the workflow and
  validation commits use `ci(release)`, and the documentation commit uses `docs(release)`. The squash commit on main
  carries only the title.
- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, image, manifest or plugin
  changes. The jar is the same bytes as before, and the image is not touched.
- **Behaviour that changes.** A release page gets two more assets and two more attestations. A release takes a few
  minutes longer. A pull request gets a new `sbom` job that runs the two generators and the validation.
- **Dependencies.** `sign-release-artifacts` (merged) provides the attestation pattern and the `id-token` and
  `attestations` permissions that the jar job already declares. `harden-ci-supply-chain` (merged) defines the SHA pinning.
  This change does not depend on `add-codeql-analysis` or `add-dco-check`.
- **Code.** `reposilite-backend/build.gradle.kts`, the version catalogue if the plugin is listed there, a frontend npm
  invocation in the workflow (no dependency added to the frontend manifest), `.github/workflows/release-please.yml`,
  `.github/workflows/ci.yml`, `.github/scripts/verify_sbom.py`, the vendored schema under `.github/scripts/schemas/`,
  `.github/scripts/tests/test_verify_sbom.py`, `.github/RELEASE.md` and a new page under `docs/gitbook/how-to/`, with its
  entry in `docs/gitbook/SUMMARY.md`.

## Verification

- The validation tests are written first and fail on main, because the script and its fixtures do not exist yet. The
  fixtures are small hand-written SBOM documents, so no test needs a Gradle build or the network.
- The `sbom` job runs on the pull request and validates the SBOMs of the backend and the frontend built in that job.
- The first release after the merge is the proof of the attestation step. Its SBOM assets are downloaded and checked with
  the verify command of the how-to page, and a failed attestation fails the release.
- `openspec validate attach-release-sboms --strict` passes, and `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/attach-release-sboms`
  prints nothing.

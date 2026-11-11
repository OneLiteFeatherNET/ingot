# Design

## Decisions

1. **Two SBOMs, because the dashboard is not in the Gradle graph.** `reposilite-backend/build.gradle.kts:45` makes the
   dashboard a project dependency, and its npm packages reach the jar only as compiled JavaScript. A backend SBOM would
   therefore omit the packages that are actually bundled. The Gradle SBOM covers the Java and Kotlin dependencies of the
   runtime classpath; the npm SBOM covers the production packages in `reposilite-frontend/package-lock.json`. Each file
   is named for its subject: `ingot-<version>.cdx.json` and `ingot-frontend-<version>.cdx.json`.

2. **CycloneDX 1.6 JSON, one format.** Both generators emit CycloneDX 1.6 JSON, which is the newest version both tools
   write without a flag that changes the meaning of the fields. SPDX would be the second format of the same data, and it
   is left out because the question of which format the operator's scanner reads is not decided here. The TR-03183-2
   mapping table below is the working assumption about the field list; its version must be checked against the Part 2
   PDF before the first commit of the implementation.

3. **Gradle plugin: `org.cyclonedx.bom`, pinned in the build script.** The plugin is applied to
   `:reposilite-backend` only, with `includeConfigs = listOf("runtimeClasspath")`, `projectType = "application"`,
   `schemaVersion = CycloneDxSchemaVersion.VERSION_1_6`, `outputFormat = "json"` and `outputName` set from the project
   version. The task `cyclonedxBom` runs in the `jar` job next to `shadowJar`. The version is the release current at
   apply time, recorded in the build script as an exact version. It is not taken from this document, because the version
   has to be read from the Gradle Plugin Portal when the commit is made.

4. **npm SBOM: `@cyclonedx/cyclonedx-npm`, pinned on the command line.** The command is
   `npx --yes @cyclonedx/cyclonedx-npm@<exact> --package-lock-only --omit dev --spec-version 1.6 --output-format JSON --output-file <path>`
   run in `reposilite-frontend`. `--package-lock-only` reads the lockfile and needs no `node_modules`, and `--omit dev`
   keeps build-time tools out of a runtime SBOM. The exact version is set in one place in the workflow, as an `env` value
   that a Renovate custom manager can update. The built-in `npm sbom` command of npm 11 (bundled with Node 24, as the `licenses` job
   uses) is the fallback if the pinned tool stops working; the command and its output are confirmed on npm 11 before it
   is used, and the choice is recorded here, not made silently.

5. **Attestation of the SBOMs, not a detached file.** `actions/attest-sbom`, pinned by commit SHA, creates an attestation
   whose subject is the jar and whose predicate is the SBOM, once per file. The attestation is stored in GitHub's
   attestation store, as the jar's provenance already is, so no key and no extra signature file is needed. The frontend
   SBOM is attested against the jar too, because the dashboard is inside the jar and that is the artifact an operator
   downloads. The verify command uses the CycloneDX predicate type `https://cyclonedx.org/bom`; the predicate type is
   confirmed against the first release before the documentation is merged.

6. **Order in the `jar` job.** The steps run in this order, and the policy test of the implementation asserts it:
   build the jar and both SBOMs; validate both SBOMs with `.github/scripts/verify_sbom.py`; attest the jar's provenance
   (existing step); attest both SBOMs; verify the three attestations with `gh attestation verify`; upload the jar and both
   SBOMs to the release. An SBOM that fails validation or attestation never reaches the release page.

7. **Validation in CI on every pull request.** The `sbom` job in `ci.yml` runs the same build and the same validation on
   pull requests into `main`, with `contents: read` only. A broken generator is then found in the pull request that
   broke it. The release job repeats the validation on the files it attests, because the release is the artifact that
   matters.

8. **Validation rules.** `.github/scripts/verify_sbom.py` checks, in this order:
   - the document validates against the vendored CycloneDX 1.6 JSON schema (`bom-1.6.schema.json` with its SPDX and JSF
     references, vendored under `.github/scripts/schemas/` so no run needs the network), using `jsonschema` pinned in
     the `sbom` job;
   - `bomFormat` is `CycloneDX` and `specVersion` is `1.6`;
   - `metadata.timestamp`, `metadata.tools`, `metadata.component` with `name` and `version`, and `metadata.authors` are
     present;
   - every component has `name`, `version` and a `purl`, and `bom-ref` values are unique;
   - every reference in `dependencies` names a component or the metadata component;
   - the share of components with a licence and the share with a hash are printed. The licence share must reach the
     threshold in the script (default 95 percent, a value the first measured run may move, recorded as an open question).

   The hash and supplier shares are reported, not enforced, because the Gradle plugin and the npm generator fill them
   from different sources.

9. **Nightlies and PRs.** The SBOM steps live in the `jar` job, which only runs when `release_created` is `true`, and in
   the `sbom` job, which only reads and never pushes. Nightly images, their BuildKit attestations and the `changes` deny
   list are not touched. A pull request that changes `build.gradle.kts` still rebuilds the nightly as it does today,
   because that is an image input.

## Permissions

| Job | Today | After |
|---|---|---|
| `jar` | `contents: write`, `id-token: write`, `attestations: write` | unchanged |
| `sbom` (new, pull requests) | none | `contents: read` |
| top level of `ci.yml` and `release-please.yml` | `contents: read` | unchanged |

## TR-03183-2 field mapping (working assumption)

The table lists the fields that TR-03183-2 Part 2 is expected to ask for, as an SBOM reader in the public sector sees
them. It has to be checked against the Part 2 PDF (version 2.1.0 at the time of research) before the implementation
commit, and the implementation commit updates this table where the PDF differs.

| Field | Filled by the generators | Source | Status |
|---|---|---|---|
| Component name and version | yes | Gradle module, npm package | filled |
| Unique identifier (purl) | yes | both generators | filled |
| Dependency relationships | yes | both generators | filled |
| Licence | where the POM or `package.json` declares one | build metadata | filled, measured by the script |
| Hash | npm: SHA-512 from the lockfile `integrity`; Gradle: to be confirmed on the first run | lockfile, artifact | partial |
| Supplier | where the POM or registry metadata names an organisation | build metadata | partial, reported |
| SBOM author and timestamp | yes | `metadata.authors`, `metadata.timestamp` | filled |
| Tool that created the SBOM | yes | `metadata.tools` | filled |
| Component scope | yes (runtime) | both generators | filled |
| Vulnerabilities | no | not in an SBOM | follow-up `add-vex-documents` |
| Signature of the SBOM | attestation, not a field in the file | `actions/attest-sbom` | provided outside the file |
| Pedigree, patches, external references | no | no source in the build | not filled |

## Files

- `reposilite-backend/build.gradle.kts`: plugin application and the `cyclonedxBom` configuration.
- `.github/workflows/release-please.yml`: the `jar` job gains the frontend SBOM step, the validation step, the two
  attestation steps, the verification, and the uploads. Every new step carries the `release_created` condition the
  neighbouring steps use.
- `.github/workflows/ci.yml`: the new `sbom` job and the `pip install` of the pinned `jsonschema` in the `images` job,
  which runs the discover command.
- `.github/scripts/verify_sbom.py`, `.github/scripts/schemas/`, `.github/scripts/tests/test_verify_sbom.py` and fixtures
  under `.github/scripts/tests/fixtures/sbom/`.
- `.github/RELEASE.md`: the list of release assets.
- `docs/gitbook/how-to/verify-release-sboms.md`, with its entry in `docs/gitbook/SUMMARY.md`: download the SBOMs next to
  the jar, verify the attestations with `gh attestation verify --predicate-type https://cyclonedx.org/bom`, read the
  component list, and feed the file to a scanner.

## Risks

- The Gradle plugin may include test-only artifacts if the configuration drifts. The `includeConfigs` setting is pinned,
  and the first release's SBOM is compared with `./gradlew :reposilite-backend:dependencies --configuration runtimeClasspath`
  as a manual check recorded in the pull request.
- A generator upgrade can change the document shape. The `sbom` job runs on every pull request, so a change in shape
  fails before a release.
- Predicate type and flag names of the three tools are taken from their documentation at apply time. Any difference is a
  change of this design, not a silent workaround in the workflow.
- The first release proves the attestation step. A failure there fails that release, and the fix is a `fix(release)`
  change; the already published jar and images are not changed by it.

## Open questions with defaults

- **Licence threshold.** Default: 95 percent of components. The first measured run may show that Maven components lack
  licence data in the POM; the maintainer decides whether the threshold or the data source changes.
- **Frontend SBOM tool.** Default: `@cyclonedx/cyclonedx-npm` pinned exactly. Fallback: `npm sbom`, if the pinned tool
  is abandoned or fails the validation.
- **Predicate type.** Default: `https://cyclonedx.org/bom`, confirmed on the first release. If the attest action uses a
  different predicate type, the verify commands in the documentation follow it.

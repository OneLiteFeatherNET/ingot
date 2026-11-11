# Verify the SBOMs of a release

Every release of Ingot carries two software bill of materials (SBOMs) next to the standalone jar. An SBOM is a machine-readable list of the components a file contains, with their versions, licences and package identifiers. This page explains how to download the two files, check that they belong to the jar, and read them.

## What a release attaches

Each release on the [releases page](https://github.com/OneLiteFeatherNET/ingot/releases) carries three files for version `<version>`:

| File | Describes |
| --- | --- |
| `ingot-<version>.jar` | The standalone server. |
| `ingot-<version>.cdx.json` | The Java and Kotlin libraries that the jar contains, the runtime classpath of the server. |
| `ingot-frontend-<version>.cdx.json` | The npm packages of the dashboard. The dashboard is compiled into the jar as JavaScript, so its packages are not visible in the first file. |

Both SBOMs use the [CycloneDX](https://cyclonedx.org/) format in version 1.6, as JSON. The full list of release assets is in [`.github/RELEASE.md`](../../../.github/RELEASE.md). The container images and the Helm chart are not described by these files. Their signatures are covered in the same document.

Nightly images do not come with SBOM files.

## Download and verify the attestations

The release workflow attests each SBOM to the jar. The attestation says that the SBOM was produced by the release workflow on the `main` branch, for that exact jar. Download the three files and run the checks from the directory that holds them:

```sh
gh release download v<version> --repo OneLiteFeatherNET/ingot \
  --pattern "ingot-<version>.jar" \
  --pattern "ingot-<version>.cdx.json" \
  --pattern "ingot-frontend-<version>.cdx.json"

gh attestation verify ingot-<version>.jar \
  --repo OneLiteFeatherNET/ingot \
  --signer-workflow OneLiteFeatherNET/ingot/.github/workflows/release-please.yml \
  --source-ref refs/heads/main \
  --predicate-type https://cyclonedx.org/bom
```

The command exits with an error when no SBOM attestation of the jar exists, or when the attestation was made by another workflow, branch or repository. The release workflow runs the same check before it publishes the files, so a release page that carries the SBOMs has passed it.

To check the build provenance of the jar itself, run the command from [Verify a release](../../../.github/RELEASE.md#verify-a-release) without the `--predicate-type` option.

## Read the components

Each SBOM lists one entry per component. The command below prints the group, name, version and licence of every component of the server jar:

```sh
jq -r '.components[] | [(.group // ""), .name, (.version // ""),
  ([.licenses[]? | (.license.id // .license.name // .expression)] | join(", "))] | @tsv' \
  ingot-<version>.cdx.json
```

A component without a licence shows an empty last column. The release check tolerates a few of them and fails only when fewer than 95 percent of the components declare a licence. Use the same command on `ingot-frontend-<version>.cdx.json` for the dashboard packages.

Each component has a package URL (`purl`), which identifies it in vulnerability databases and in most scanners. Most components carry hashes in the `hashes` field. The file does not record the supplier of the server libraries.

## Scan the SBOM

A scanner reads the file without the jar. Trivy, for example, reports the known vulnerabilities of every listed component:

```sh
trivy sbom ingot-<version>.cdx.json
trivy sbom ingot-frontend-<version>.cdx.json
```

A scanner report describes the components in the file, not whether Ingot is exploitable. Ingot does not yet publish statements about which vulnerabilities affect it, so a finding in a listed library may be one that the server never reaches.

## What the SBOMs do not contain

- **Vulnerability statements.** The files list components only. Statements about which findings affect a release (VEX) are a later addition.
- **The signature inside the file.** The signature is the attestation, which lives outside the file and is checked with `gh attestation verify`.
- **Pedigree, patches and external references** of each component. The build does not record them.

## Related

- [Verify a release](../../../.github/RELEASE.md#verify-a-release)
- [Upgrade Ingot](upgrade-ingot.md)

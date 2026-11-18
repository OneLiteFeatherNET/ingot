## Purpose

The CLI ships as its own container image and as a jar, built and published under the same supply-chain rules as the server image, without
touching the server image.

## ADDED Requirements

### Requirement: Separate image

The project SHALL publish `ghcr.io/onelitefeathernet/ingot-cli` for `linux/amd64` and `linux/arm64`, with the entrypoint `ingot`, running as a
non-root user. The server image `ghcr.io/onelitefeathernet/ingot` SHALL be unchanged by this capability.

#### Scenario: Interactive run

- **WHEN** `docker run -it -e INGOT_URL=... -e INGOT_TOKEN_FILE=/run/secrets/t ghcr.io/onelitefeathernet/ingot-cli` runs
- **THEN** the REPL opens and a command such as `status` returns the server status

#### Scenario: Server image untouched

- **WHEN** the Container images job runs
- **THEN** the unmodified upstream compose file still starts with only the image line swapped, `/app/data` is adopted, and `PUID`, `PGID` and
  `REPOSILITE_OPTS` still take effect

### Requirement: Build rules

The build stage SHALL be pinned to `--platform=$BUILDPLATFORM`. Base images SHALL be pinned by tag and digest. Releases SHALL carry an SBOM
and provenance (`sbom: true`, `provenance: mode=max`), nightlies SHALL carry neither. A nightly SHALL be pushed only when the push changed
something the image contains, and anything the filter does not know SHALL count as relevant.

#### Scenario: Docs-only push

- **WHEN** a push changes only files under `docs/`
- **THEN** no nightly CLI image is pushed

#### Scenario: CLI change

- **WHEN** a push changes a file of the CLI module
- **THEN** the nightly CLI image is built and pushed for both architectures

### Requirement: Jar on releases

Each release SHALL attach a runnable CLI jar, signed and with its SBOM like the other release artefacts.

#### Scenario: Release assets

- **WHEN** a release is published
- **THEN** it has `ingot-cli-<version>.jar`, its signature and its SBOM

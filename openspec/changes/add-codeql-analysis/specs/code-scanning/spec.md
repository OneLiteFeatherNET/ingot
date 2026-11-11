# Spec Delta

## Purpose

The backend and the dashboard are analysed by CodeQL on every pull request into main, on every push to main and on a weekly schedule. The analysis of the Kotlin backend comes from a real build, its findings reach the maintainers through code scanning, and it holds no more permission than it needs. Nightly and release images are not affected.

## ADDED Requirements

### Requirement: Pull requests, main and a schedule are analysed

The repository SHALL run CodeQL from `.github/workflows/codeql.yml` on pull requests into `main`, on pushes to `main` and on a weekly schedule, for the languages `java-kotlin` and `javascript-typescript`.

#### Scenario: Pull request into main

- **WHEN** a pull request into `main` is opened or updated
- **THEN** the CodeQL workflow runs an analysis for both languages

#### Scenario: Weekly schedule

- **WHEN** the weekly schedule fires
- **THEN** the workflow analyses the head of `main` for both languages

### Requirement: Kotlin is analysed from a build

The `java-kotlin` analysis SHALL use a manual build step that compiles the main source sets of the repository. The build mode `none` MUST NOT be used for `java-kotlin`.

#### Scenario: Kotlin analysis

- **WHEN** the `java-kotlin` job runs
- **THEN** a Gradle `classes` task compiles the backend and the bundled plugins before CodeQL analyses the sources

### Requirement: Findings do not fail the workflow

The workflow MUST NOT exit with a failure because CodeQL reports a finding. Findings SHALL be uploaded to code scanning. A gate on new high and critical alerts SHALL be configured as a repository ruleset, not inside the workflow, and SHALL NOT be a required status check until the baseline is clean.

#### Scenario: Finding on a pull request

- **WHEN** CodeQL reports a finding on a pull request
- **THEN** the workflow run completes successfully and the finding appears as a code scanning alert on the pull request

### Requirement: Least privilege

The workflow SHALL declare `contents: read` at the top level. Only the job that uploads results SHALL declare `security-events: write`, and no job SHALL declare `packages`, `id-token` or `attestations` write.

#### Scenario: Permissions of the analysis

- **WHEN** `.github/workflows/codeql.yml` is read
- **THEN** the top level permissions are `contents: read`, and the `analyze` job is the only job with `security-events: write`

### Requirement: Actions are pinned by commit SHA

Every `uses:` reference in `codeql.yml` SHALL name a full commit SHA with a `# vX.Y.Z` comment, as the other workflows do.

#### Scenario: Action reference

- **WHEN** `codeql.yml` is read
- **THEN** every `uses:` line names a 40-character commit SHA followed by a version comment

### Requirement: Nightly images are unaffected

The CodeQL workflow MUST NOT push an image, a package or an attestation, and a change that only touches `.github/workflows/codeql.yml` MUST NOT cause the nightly image to be built.

#### Scenario: Push that only changes the CodeQL workflow

- **WHEN** a push to main changes only `.github/workflows/codeql.yml` and `.github/codeql/codeql-config.yml`
- **THEN** the `changes` job of the release workflow reports no image change and no nightly image is pushed

# Spec Delta

## Purpose

The workflows that build, test and publish Ingot run the same code for the same tag on every run, declare what each job may write, verify the frontend packages they install, and are measured by OpenSSF Scorecard.

## ADDED Requirements

### Requirement: Actions are pinned by commit SHA

Every `uses:` reference in `.github/workflows/` SHALL name a full 40-character commit SHA, including the calls to reusable workflows. Each such line SHALL carry a comment with the release tag that the SHA stands for.

#### Scenario: Action reference

- **WHEN** `.github/workflows/ci.yml` is read
- **THEN** every `uses:` line names a commit SHA and a comment of the form `# vX.Y.Z`

#### Scenario: Reusable workflow call

- **WHEN** the `build` job of `.github/workflows/ci.yml` calls a workflow from `OneLiteFeatherNET/workflows`
- **THEN** the call names a commit SHA and not a tag

### Requirement: Renovate keeps action pins as digests

The Renovate configuration SHALL extend a preset that pins GitHub Actions by digest, so that a new action release reaches the repository as a pull request that changes the SHA and the version comment together.

#### Scenario: Newer action release

- **WHEN** Renovate finds a newer release of a pinned action
- **THEN** the pull request replaces the commit SHA and the `# vX.Y.Z` comment, and does not change the reference to a tag

### Requirement: Frontend packages have their signatures verified

The `licenses` job SHALL run `npm audit signatures` in `reposilite-frontend` after `npm ci`. The job SHALL fail when a registry signature or provenance attestation of an installed package does not verify.

#### Scenario: Lockfile matches the registry

- **WHEN** the `licenses` job runs on a pull request whose packages carry valid signatures
- **THEN** the step succeeds and the job continues to the license check

#### Scenario: Signature does not verify

- **WHEN** an installed package carries a signature or attestation that does not verify
- **THEN** the step fails and the job fails before the license check

### Requirement: Read-only token by default

Both workflow files SHALL set `permissions: contents: read` at the top level. Every job SHALL declare its own `permissions` block. A job SHALL hold a write scope only for an action it performs: release-please pull requests and releases, package publishing, and the Scorecard job.

#### Scenario: Job without write needs

- **WHEN** a job of `.github/workflows/ci.yml` only builds, tests or scans
- **THEN** its `permissions` block grants `contents: read` and no write scope

#### Scenario: Job that publishes

- **WHEN** a job of `.github/workflows/release-please.yml` publishes a container image
- **THEN** its `permissions` block grants `packages: write`, and every other job of the workflow does not

### Requirement: Scorecard measures the repository

The repository SHALL run OpenSSF Scorecard from `.github/workflows/scorecard.yml` on pushes to `main` and on a weekly schedule. The workflow SHALL upload its SARIF result to code scanning, publish its result to the OpenSSF API, and pin its action by commit SHA. The workflow SHALL NOT run on `pull_request` events.

#### Scenario: Push to main

- **WHEN** a commit lands on `main`
- **THEN** the Scorecard workflow runs and publishes a result

#### Scenario: Pull request from a fork

- **WHEN** a pull request is opened from a fork
- **THEN** the Scorecard workflow does not run and no token with write scope is offered to the fork

### Requirement: The change does not alter the runtime

The change SHALL NOT add, remove or rename any configuration key, environment variable, volume, container image, deployment manifest or plugin API. Client-visible behaviour of Ingot SHALL stay as it is.

#### Scenario: Operator upgrade

- **WHEN** an operator replaces the Reposilite image with Ingot after this change
- **THEN** no configuration, volume or manifest has to change, and the server answers the same requests with the same responses

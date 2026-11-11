# Spec Delta

## Purpose

The browser suite runs on every pull request and on every push to main, and its result is a check that can be required. The job
changes no image, jar, chart or existing job.

## ADDED Requirements

### Requirement: Job runs the suite on pull requests and on main

A job named `Dashboard end-to-end` SHALL run `npm run e2e` in `reposilite-frontend` on every pull request to main and on every push
to main, after building the shaded server jar in the same job.

#### Scenario: Pull request

- **WHEN** a pull request to main is opened or updated
- **THEN** the job runs and reports a check named `Dashboard end-to-end`

#### Scenario: Push to main

- **WHEN** a commit is pushed to main
- **THEN** the job runs on that commit

### Requirement: Job has a time limit and pinned actions

The job SHALL set `timeout-minutes: 15`. Every action it uses SHALL be pinned by commit SHA with a version comment, as the other jobs
of the workflow are.

#### Scenario: Hung server

- **WHEN** the server or a browser does not finish within the time limit
- **THEN** the job fails at the limit and does not hold the runner

#### Scenario: Unpinned action rejected

- **WHEN** an action of the job is referenced by a tag instead of a 40-character SHA
- **THEN** the workflow shape test fails and names the action

### Requirement: Report is uploaded only on failure

The job SHALL upload the Playwright report directory only when a step of the job failed. A green run SHALL upload no artifact.

#### Scenario: Green run

- **WHEN** every test passes
- **THEN** the job uploads no artifact

#### Scenario: Failed run

- **WHEN** a test fails
- **THEN** the job uploads the report directory with its traces and screenshots

### Requirement: Check becomes required after its first green run

The check `Dashboard end-to-end` SHALL be added to the required checks of the main branch by the maintainer after its first green
run on main. Until then it is not required.

#### Scenario: Required after first green run

- **WHEN** the job has been green on main once and the maintainer has added the name to branch protection
- **THEN** a pull request whose job fails cannot be merged

### Requirement: No breaking change

The job SHALL leave the image, the jar, the chart, the configuration and the existing jobs and their required checks unchanged.

#### Scenario: Existing jobs unchanged

- **WHEN** the workflow is compared with main
- **THEN** no existing job, step or required check is removed or renamed

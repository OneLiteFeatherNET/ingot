# Spec Delta

## Purpose

A pull request runs only the checks and tests its changed paths can reach, any doubt selects everything, and the light checks finish
inside a measurable time budget. This changes which existing jobs run on a pull request, not what they assert, and it changes no image,
jar, chart, configuration key or job name that the ruleset requires.

## ADDED Requirements

### Requirement: Jobs run only for the paths they depend on

On a pull request, every light job SHALL run only when the scope step reports that a path it depends on changed. A job that does not run
SHALL be skipped through a job-level condition and SHALL NOT be removed by a workflow-level `paths` or `branches` filter.

#### Scenario: Documentation only

- **WHEN** a pull request changes only files under `docs/`, `openspec/` or `*.md`
- **THEN** no backend test, no frontend test, no image job and no dependency scan runs, and every required check reports success or skipped

#### Scenario: Frontend only

- **WHEN** a pull request changes only files under `reposilite-frontend/`
- **THEN** the frontend jobs run, the backend runs only the tests that serve the dashboard, and no other backend test runs

#### Scenario: Backend source change

- **WHEN** a pull request changes Kotlin sources of one backend domain
- **THEN** the unit tests that the selection of #257 names for that domain run, and the integration suite does not run

### Requirement: Any doubt selects everything

The scope step SHALL select every light job and the full unit suite when a changed path is unknown, quoted or unmapped, when the diff range
is unusable, when the script fails, when the event is not a pull request, or when a changed path is a build file, `gradle/`, `buildSrc`,
a workflow file, a workflow script or a shared test fixture.

#### Scenario: Build logic changed

- **WHEN** a pull request changes a file under `buildSrc/` or a `*.gradle.kts` file
- **THEN** every light job runs and the full unit suite runs

#### Scenario: Scope step fails

- **WHEN** the scope step fails or times out
- **THEN** every light job runs, and the `Light checks` aggregate reports the failure

### Requirement: Light checks stay within five minutes

Every light job SHALL finish within its budget, and no light job budget SHALL exceed 300 seconds of wall clock, measured as the job's
completion time minus its start time, so runner queue time is excluded. The budget per job is the table in `design.md`.

#### Scenario: Within budget

- **WHEN** a pull request's light jobs all finish within their budgets
- **THEN** the `Light checks` summary shows each job's duration against its budget and no warning

#### Scenario: Over budget

- **WHEN** a light job runs longer than its budget
- **THEN** the `Light checks` summary lists the job with a warning, and the aggregate result is not changed by the warning alone

### Requirement: One required light aggregate

The workflow SHALL provide a job named `Light checks` that always runs, depends on every light job, and succeeds when each result is
`success` or `skipped`. It SHALL be listed in the required checks of the protected branch.

#### Scenario: A light job fails

- **WHEN** any light job ends with `failure` or `cancelled`
- **THEN** `Light checks` fails

#### Scenario: A light job is skipped by scope

- **WHEN** a light job is skipped because its paths did not change
- **THEN** `Light checks` still succeeds

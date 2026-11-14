# Spec Delta

## Purpose

Heavy tests (the integration suite, image builds and container tests, the HA smoke test, the dashboard end-to-end suite, the Helm chart
job, the search backend container jobs and CodeQL) are automated jobs that a maintainer starts with a label, and that run in full before
a release is cut. They do not run on ordinary pull requests, and they do not run nightly unless that is switched on.

## ADDED Requirements

### Requirement: Heavy jobs start from a label

A heavy job SHALL run on a pull request only when the pull request carries `ci:heavy` or the label of its area, or when it is a release
pull request. The workflow SHALL start on the `pull_request` types `opened`, `reopened`, `synchronize`, `labeled` and `unlabeled`, and the
light workflow SHALL NOT start on label events.

#### Scenario: No label

- **WHEN** an ordinary pull request carries no `ci:` label
- **THEN** no heavy job runs and `Heavy tests` succeeds with the message that heavy tests were not requested

#### Scenario: Area label

- **WHEN** a pull request carries `ci:e2e` only
- **THEN** the dashboard end-to-end job runs and no other heavy job runs

#### Scenario: All heavy jobs

- **WHEN** a pull request carries `ci:heavy`
- **THEN** every heavy job runs

#### Scenario: New push to a labelled same-repository pull request

- **WHEN** a commit is pushed to a pull request branch of this repository that still carries the label
- **THEN** the labelled heavy jobs run again on the new head commit

### Requirement: Fork pull requests cannot reach secrets

The heavy workflow SHALL use the `pull_request` event only, SHALL declare `permissions: contents: read`, and SHALL NOT use
`pull_request_target`, `secrets.*` or a `workflow_run` with write scope. Only users with triage or write access can add a label.

#### Scenario: Fork author opens a pull request

- **WHEN** a fork author opens a pull request
- **THEN** the author cannot add `ci:` labels, and no heavy job runs until a maintainer adds one

#### Scenario: Maintainer labels a fork pull request

- **WHEN** a maintainer adds `ci:heavy` to a fork pull request
- **THEN** the jobs run with no secrets and a read-only token

#### Scenario: Fork author pushes after the label

- **WHEN** a fork author pushes a commit to a labelled fork pull request
- **THEN** the heavy jobs do not rerun until the maintainer removes and re-adds the label

### Requirement: A label event cancels no unrelated run

The concurrency group of the heavy workflow SHALL be unique per event for a label that does not start with `ci:`, and SHALL be shared per
pull request for every other event. A label event SHALL NOT cancel a run of the light workflow.

#### Scenario: Unrelated label added during a heavy run

- **WHEN** the label `bug` is added while a heavy run is in progress
- **THEN** the heavy run continues

#### Scenario: Heavy label removed during a run

- **WHEN** `ci:heavy` is removed while a heavy run is in progress
- **THEN** that run is cancelled and the new run skips every heavy job

### Requirement: A release needs one green full heavy run

On a pull request whose head branch starts with `release-please--`, the aggregate `Heavy tests` SHALL succeed only when every heavy job
ran in full, including the complete integration suite, and passed on the head commit. `Heavy tests` SHALL be a required check of the
protected branch.

#### Scenario: Release pull request without a heavy run

- **WHEN** a release pull request carries no `ci:heavy` label
- **THEN** `Heavy tests` fails with the instruction to add the label

#### Scenario: Release pull request after a green run

- **WHEN** `ci:heavy` is on the release pull request and every heavy job passed on its head commit
- **THEN** `Heavy tests` succeeds

#### Scenario: Release pull request updated after the run

- **WHEN** release-please pushes a new head commit
- **THEN** the earlier result does not count and the heavy jobs run again on the new head

### Requirement: A failed scope step fails safe

A heavy job that is required by a path or a label SHALL run when the scope step failed, and `Heavy tests` SHALL fail when the scope step
did not succeed.

#### Scenario: Scope step failure

- **WHEN** the heavy scope step fails
- **THEN** the aggregate fails and no heavy job is reported as passed merely because it was skipped

### Requirement: On-demand run on main

The heavy workflow SHALL offer `workflow_dispatch` on the main branch that runs every heavy job in full. A nightly schedule SHALL NOT be
enabled unless the maintainer enables it, and if enabled, a failed run SHALL open or update a single issue labelled `ci:red-main`.

#### Scenario: Maintainer starts a baseline

- **WHEN** a maintainer dispatches the workflow on main
- **THEN** every heavy job runs in full

#### Scenario: No schedule by default

- **WHEN** the workflow file is read
- **THEN** it contains no `schedule` trigger

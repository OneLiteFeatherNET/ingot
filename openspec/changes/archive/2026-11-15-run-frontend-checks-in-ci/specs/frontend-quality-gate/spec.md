# Spec Delta

## Purpose

The frontend unit tests and the ESLint checks run on every change that could break them, and a broken test fails the
pull request.

## ADDED Requirements

### Requirement: Frontend tests gate the pipeline

The CI pipeline SHALL run the frontend unit tests (`npm test` in `reposilite-frontend`) on every pull request to `main` and
on every push to `main`. A failing test SHALL fail the check.

#### Scenario: Passing tests

- **WHEN** a pull request changes a component and all frontend unit tests pass
- **THEN** the `Frontend` check reports success

#### Scenario: Failing test

- **WHEN** a pull request contains a unit test whose assertion fails
- **THEN** the `Frontend` check reports failure and the failing test name appears in the job log

### Requirement: ESLint runs in warn phase

The CI pipeline SHALL run `npm run eslint` in `reposilite-frontend` on the same triggers. During the warn phase every rule
finding SHALL be reported as a warning, and warnings SHALL NOT fail the check. A parse error or an invalid configuration
SHALL fail the check.

#### Scenario: Lint warning

- **WHEN** a pull request adds a Vue file with an unused variable
- **THEN** the job log shows the warning with its file and line, and the `Frontend` check still reports success

#### Scenario: Invalid configuration

- **WHEN** a pull request leaves `reposilite-frontend/eslint.config.js` unparseable
- **THEN** the `Frontend` check reports failure

### Requirement: Frontend job is independent of the licence job

The frontend checks SHALL run in their own job, so a failing lint or test shows as its own check and does not hide the
licence or vulnerability results.

#### Scenario: Test failure with licence success

- **WHEN** a frontend test fails and the licence check passes
- **THEN** the `Licenses` check reports success and the `Frontend` check reports failure

### Requirement: No change to shipped behaviour

The change SHALL NOT alter any file under `reposilite-frontend/src/`, `reposilite-frontend/server/`, the built dashboard,
the backend or the container images. The rules of the ESLint configuration SHALL change severity only, not their set.

#### Scenario: Built dashboard unchanged

- **WHEN** the change is compared with `origin/main`
- **THEN** no file outside `.github/workflows/ci.yml` and `reposilite-frontend/eslint.config.js` differs

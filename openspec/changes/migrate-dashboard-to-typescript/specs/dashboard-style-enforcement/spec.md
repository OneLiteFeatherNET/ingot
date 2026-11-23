# Spec Delta

## Purpose

The dashboard lint gate keeps its rule that every finding fails the check. The suppressions file that records the findings of the migration period exists only while findings remain, and the dashboard deletes it once the last finding is fixed.

## MODIFIED Requirements

### Requirement: Lint findings fail the check

The `reposilite-frontend/eslint.config.js` configuration SHALL NOT map rule severities to `warn`. Every enabled rule SHALL report as an error. Findings that exist before the change MAY be recorded in `reposilite-frontend/eslint-suppressions.json`, and the file SHALL be pruned so that it lists no finding that no longer occurs. The file SHALL exist only while it lists at least one finding, and the change that fixes the last finding SHALL delete it. A new finding SHALL NOT be added to the file, and without the file every finding fails the check.

#### Scenario: New lint error

- **WHEN** a pull request adds a Vue file with an unused variable
- **THEN** `npm run eslint` exits with an error that names the file and the rule

#### Scenario: Suppressed finding

- **WHEN** an existing finding is listed in the suppressions file
- **THEN** `npm run eslint` does not report it

#### Scenario: Stale suppression

- **WHEN** a suppressed finding no longer occurs
- **THEN** `npx eslint --prune-suppressions` removes its entry, and the entry is removed in the same pull request

#### Scenario: Last finding fixed

- **WHEN** a pull request fixes the last finding that the suppressions file lists
- **THEN** the pull request deletes `reposilite-frontend/eslint-suppressions.json`, and `npm run eslint` exits without errors

#### Scenario: New finding is not suppressed

- **WHEN** a pull request introduces a finding and adds it to the suppressions file, or recreates the file to hold it
- **THEN** the reviewer rejects the pull request, because a finding is fixed and never suppressed

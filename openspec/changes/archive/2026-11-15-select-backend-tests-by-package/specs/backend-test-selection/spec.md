# Spec Delta

## Purpose

A pull request that changes one backend package runs the backend tests that package can reach, and the full backend suite runs whenever
a narrower run could hide a regression. The selection changes which existing tests run, not what they assert, and it changes no image,
jar, chart, configuration key or job name.

## ADDED Requirements

### Requirement: Pull request runs the packages a change can reach

For a pull request, the coverage job SHALL run only the backend test packages that the changed Kotlin files can reach. A changed file
SHALL select its own domain package and every domain whose main, test or integration sources import a class from it.

#### Scenario: Change confined to one leaf package

- **WHEN** a pull request changes only files in a package that no other domain imports
- **THEN** the job runs the tests of that package and of the packages that import it, and no other test package

#### Scenario: Change in a package with importers

- **WHEN** a pull request changes a file in a package that is imported by another domain
- **THEN** the job also runs the tests of the importing domain

#### Scenario: Several changed packages

- **WHEN** a pull request changes files in several domains
- **THEN** the selection is the union of the selections of those domains

### Requirement: Full suite on the triggers that a narrow run cannot cover

The coverage job SHALL run the complete backend suite instead of the selection whenever one of these holds: a changed path is outside the
backend Kotlin source roots; a changed Kotlin file lies in an infrastructure package; a changed main file maps to an empty selection; the
selection holds more than 60 percent of the test annotations; a changed file cannot be mapped; the selection script fails; or the event is a
push to main.

#### Scenario: Build logic changed

- **WHEN** a pull request changes a Gradle build file, a version catalog or a file under `buildSrc`
- **THEN** the job runs the complete backend suite

#### Scenario: Infrastructure package changed

- **WHEN** a pull request changes a file in the configuration, shared, web, plugin or storage package, or in the application bootstrap
- **THEN** the job runs the complete backend suite

#### Scenario: Selection above the threshold

- **WHEN** the selected packages hold more than 60 percent of the test annotations
- **THEN** the job runs the complete backend suite

#### Scenario: Script failure

- **WHEN** the selection script exits with an error or prints no decision
- **THEN** the job runs the complete backend suite and does not report the selection as a success

#### Scenario: Push to main

- **WHEN** a commit is pushed to main
- **THEN** the job runs the complete backend suite on that commit

### Requirement: Smoke set runs on every backend pull request

Every backend run on a pull request SHALL run the smoke set, whatever the selection: the dependency rules test, the characterisation test,
the OpenAPI tests, the security header and dashboard policy tests, and the startup tests. The smoke set SHALL be named in the selection
script, and a test class that is renamed or removed SHALL make the script's own test fail.

#### Scenario: Narrow selection

- **WHEN** the selection contains one leaf package
- **THEN** the smoke set still runs

#### Scenario: Renamed smoke test

- **WHEN** a class of the smoke set is renamed and the name is not updated in the script
- **THEN** the script's test fails and names the missing class

### Requirement: Partial run never uploads coverage

A run that does not cover the complete backend suite SHALL NOT upload a coverage report to Codecov. A partial run SHALL keep the job name
`Coverage`, so that the required check keeps its name, and SHALL report success when its selected tests pass.

#### Scenario: Partial run passes

- **WHEN** the selected tests of a pull request pass
- **THEN** the `Coverage` check reports success and no coverage report is uploaded

#### Scenario: Full run on main

- **WHEN** the complete backend suite runs on main
- **THEN** the coverage report is uploaded as before

### Requirement: No breaking change

The selection SHALL leave the container image, the server jar, the Helm chart, every configuration key and environment variable, and every
job name and required check unchanged.

#### Scenario: Existing checks unchanged

- **WHEN** the workflow is compared with main
- **THEN** no job, required check or step of another job is removed or renamed

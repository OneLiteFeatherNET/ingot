# Spec Delta

## Purpose

Every pull request shows whether the plugin API that Reposilite 3.5.28 plugins link against still exists in Ingot.

## ADDED Requirements

### Requirement: Binary comparison against Reposilite 3.5.28

The build SHALL provide a task that compares the jar of `com.reposilite:reposilite:3.5.28` with the jar of the current
`reposilite-backend` build at bytecode level, limited to the package list recorded in the build script, and writes an HTML
and a Markdown report. In this change the task SHALL NOT fail the build because of a difference.

#### Scenario: Unchanged surface

- **WHEN** the task runs on a build whose listed packages match 3.5.28
- **THEN** both reports state that no binary-incompatible difference was found and the task succeeds

#### Scenario: A removed method is reported

- **WHEN** a public method of `MavenFacade` that exists in 3.5.28 is missing from the current jar
- **THEN** the report lists the class and the method as binary incompatible and the task still succeeds

#### Scenario: Packages outside the list are ignored

- **WHEN** a class in `com.reposilite.journalist` or in an unlisted package differs
- **THEN** the report does not list it

### Requirement: Upstream plugin jars run on Ingot

An integration test SHALL load the jars published by upstream for `checksum-plugin` 3.5.28 and `migration-plugin` 3.5.28,
unmodified, into a fresh server with its own temporary working directory, and SHALL assert the behaviour of each plugin. The
jars SHALL be resolved by Gradle, not downloaded by the test.

#### Scenario: Checksum plugin generates a checksum

- **WHEN** the upstream `checksum-plugin` jar is in the plugins directory and a client requests the `.sha256` of a deployed
  artifact in a repository without mirrors
- **THEN** the server answers `200` with the SHA-256 of the artifact

#### Scenario: Migration plugin migrates legacy tokens

- **WHEN** the upstream `migration-plugin` jar is in the plugins directory and the working directory holds a legacy
  `tokens.dat`
- **THEN** the server starts and the token named in the fixture exists with its route

### Requirement: Bundled compile canaries are watched

The CI job SHALL compile and test the bundled `checksum-plugin` and `migration-plugin` against the current backend, and SHALL
add a note to its summary when the pull request changes a file under the `src/main` directory of either plugin.

#### Scenario: Plugin source edited in the same pull request

- **WHEN** a pull request changes `reposilite-plugins/checksum-plugin/src/main/kotlin/com/reposilite/plugin/checksum/ChecksumPlugin.kt`
- **THEN** the job summary names the file and asks the reviewer to read the binary report

### Requirement: Report-only CI job

A CI job named "Plugin API" SHALL run on every pull request to main, publish the Markdown report and the canary results in
its job summary, and upload the HTML report as an artifact. Until the gate is made blocking by a later change, a finding of
the report or a failing canary SHALL NOT fail the job.

#### Scenario: Drift found

- **WHEN** the report lists a binary-incompatible difference and a canary test fails
- **THEN** the job summary shows both, the HTML report is attached, and the job result is success

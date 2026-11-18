# Spec Delta

## ADDED Requirements

### Requirement: Ingot plugin API is compared with the last Ingot release

The build SHALL provide a task that compares the jar of the last released `net.onelitefeather.ingot:ingot` with the jar of the current
`reposilite-backend` build, at bytecode level, limited to the packages recorded in `ingotPluginApiPackages`, and SHALL write an HTML and a
Markdown report. The baseline release SHALL be the value of one property of the build script. The task SHALL NOT fail the build because of a
difference.

#### Scenario: A type that the last release does not have

- **WHEN** a public type of `ingotPluginApiPackages` exists in the current jar and not in the baseline
- **THEN** the report lists it as an addition, and the task succeeds

#### Scenario: A removed method of a documented Ingot type

- **WHEN** a public method of a type in `net.onelitefeather.ingot.search.backend` that the baseline has is missing from the current jar
- **THEN** the report lists it as binary incompatible in the group of its type, and the task succeeds

#### Scenario: Baseline not in the repository

- **WHEN** the baseline release cannot be resolved
- **THEN** the report says that no Ingot baseline was compared, and the task succeeds

### Requirement: Experimental findings are reported apart from promised ones

Each finding of the Ingot comparison SHALL belong to the group `experimental` when its type or member carries an annotation whose simple name
starts with `ExperimentalIngot`, or when its package is named in `ingotExperimentalPackages`, and to the group `promised` otherwise. The report
SHALL show the two groups in separate sections with their counts. A finding in the experimental group SHALL NOT be a candidate for blocking.

#### Scenario: A change in an experimental SPI

- **WHEN** a method of an interface annotated with `@ExperimentalIngotSearchApi` changes incompatibly
- **THEN** the report lists it under the experimental section, and under no other section

#### Scenario: A change in an unannotated documented type

- **WHEN** a method of `net.onelitefeather.ingot.repository.RepositoryRegistry` changes incompatibly
- **THEN** the report lists it under the promised section

### Requirement: Kotlin internal declarations are not reported

The comparison SHALL exclude the classes that Kotlin marks `internal`, and the members of public classes whose names carry the `$` mangling
that Kotlin gives an `internal` member. A class whose metadata cannot be read SHALL stay in the comparison, and the task SHALL log its name.

#### Scenario: Internal class in a listed package

- **WHEN** a class of `net.onelitefeather.ingot.search.application` is `internal` and changes its constructor
- **THEN** the report does not list it

#### Scenario: Unreadable metadata

- **WHEN** the metadata of a class cannot be read
- **THEN** the class is compared and its name is logged

### Requirement: Package lists match the documentation

A unit test SHALL fail when a package that the plugin API documentation names under `net.onelitefeather.ingot`, or `com.reposilite.frontend.api`,
is missing from `ingotPluginApiPackages`. The same test SHALL fail when `com.reposilite.journalist` is in either list. The test SHALL run
without Gradle and without network access.

#### Scenario: A documented package is missing

- **WHEN** `plugin-api.md` names `net.onelitefeather.ingot.storage.capability` and the list does not contain it
- **THEN** the test fails with a message that names the package

#### Scenario: The third-party library is never listed

- **WHEN** `com.reposilite.journalist` is added to a list
- **THEN** the test fails

### Requirement: The Ingot comparison is report-only in CI

The CI job "Plugin API" SHALL run the Ingot comparison and SHALL add a section "Ingot plugin API" to its job summary with the counts of the two
groups. Until the gate is made blocking by a later change, a finding of either group SHALL NOT fail the job.

#### Scenario: Promised finding in a pull request

- **WHEN** a pull request removes the method `registerPage` of `com.reposilite.frontend.api.PluginUiRegistrar`, which carries no experimental marker
- **THEN** the job summary shows the finding in the promised section, the HTML report is attached, and the job result is success

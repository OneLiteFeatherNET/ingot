## ADDED Requirements

### Requirement: Embedded copy loaded in its own class loader

The server jar SHALL embed the cleanup plugin jar as `embedded-plugins/cleanup-plugin.jar`, and the loader SHALL load it in its own class loader with the plugin id `cleanup`. No class of the package `com.reposilite.cleanup` SHALL be loadable from the server class loader. Cleanup SHALL be active without any switch, file or environment variable.

#### Scenario: Fresh start without extra files

- **WHEN** an instance starts from `ingot.jar` with an empty plugins directory and no bundled directory
- **THEN** the plugin `cleanup` is registered with origin embedded and effective tier Core, and the settings domain `cleanup` is listed

#### Scenario: Cleanup classes not on the server classpath

- **WHEN** the server class loader loads `com.reposilite.cleanup.CleanupService` with initialisation disabled
- **THEN** the load fails with a class-not-found error

#### Scenario: Image start

- **WHEN** the image starts with an unmodified upstream compose file
- **THEN** the plugin `cleanup` is registered, and no file is added to `/app/bundled-plugins`

### Requirement: Newer jar replaces the embedded copy

A jar in the plugins directory with the plugin id `cleanup` SHALL replace the embedded copy only when its manifest `Implementation-Version` is strictly higher than the embedded copy's. A jar with an equal or lower version, or without a version, SHALL be ignored. The start SHALL log one INFO line that names both versions and the copy in use, and SHALL NOT fail with an already-registered error.

#### Scenario: Higher version in the plugins directory

- **WHEN** the embedded copy has version `1.0.0` and a jar in the plugins directory has `1.1.0` with the id `cleanup`
- **THEN** the jar in the plugins directory is the copy in use, and the start log holds one INFO line that names `1.0.0`, `1.1.0` and the copy in use

#### Scenario: Equal or lower version

- **WHEN** a jar in the plugins directory has the id `cleanup` and version `1.0.0` or lower
- **THEN** the embedded copy stays in use, the jar is ignored, and the start log names both versions and the embedded copy

#### Scenario: Jar without a version

- **WHEN** a jar in the plugins directory has the id `cleanup` and no `Implementation-Version`
- **THEN** the embedded copy stays in use

### Requirement: Settings, routes and data unchanged

The plugin SHALL keep the settings key `cleanup`, the shape of `CleanupSettings`, the HTTP routes under `/api/cleanup`, the run table `cleanup_run`
and the OpenAPI document. An existing `configuration.shared.json` and an existing database SHALL load without a change.

#### Scenario: Existing shared configuration

- **WHEN** an instance with a `cleanup` domain in `configuration.shared.json` starts on the moved code
- **THEN** the policies load unchanged, and the stored file is not rewritten at start

#### Scenario: OpenAPI document unchanged

- **WHEN** the served OpenAPI document is compared with `docs/openapi/ingot-openapi.json`
- **THEN** the paths under `/api/cleanup` are the same as before the move

### Requirement: Audit and metrics unchanged

A cleanup run SHALL emit one `CLEANUP_RUN` audit event with the same detail keys as before the move. The last successful scheduled run SHALL be exported as the job `cleanup` in the existing metric, and the series SHALL be omitted when no value exists.

#### Scenario: Run audited

- **WHEN** a manual cleanup run finishes
- **THEN** one audit event with the action `CLEANUP_RUN` is written with the keys `runId`, `policy`, `scope`, `trigger`, `status`, `deletedCount` and `deletedBytes`

#### Scenario: Metric without a value

- **WHEN** no scheduled run has finished and the metrics are scraped
- **THEN** no series with the job label `cleanup` is exported, and the other job series are exported as before

### Requirement: Job status through a core interface

The core SHALL declare the interface `JobLastSuccessSource` in `net.onelitefeather.ingot.deploy`. The cleanup service SHALL implement it and register it as a facade. The prometheus plugin SHALL read the job status through that interface and SHALL NOT import any class of the package `com.reposilite.cleanup`.

#### Scenario: Interface registered

- **WHEN** the cleanup plugin is registered
- **THEN** the prometheus plugin finds the interface among the facades and exports the last success of the job `cleanup`

#### Scenario: Prometheus classpath

- **WHEN** the prometheus plugin is compiled
- **THEN** its classpath contains no class from the package `com.reposilite.cleanup`

### Requirement: Worker target and background jobs unchanged

The cleanup schedule SHALL run through the background job gate with the job id `cleanup-schedule`. It SHALL run on an instance that runs background jobs and SHALL NOT be submitted on an instance whose background jobs switch is off.

#### Scenario: Worker runs the schedule

- **WHEN** an instance runs as the worker target with background jobs enabled
- **THEN** the submitted singleton jobs include `CLEANUP_SCHEDULE`

#### Scenario: Server with the switch off

- **WHEN** an instance runs with `INGOT_BACKGROUND_JOBS` set to `false`
- **THEN** no cleanup schedule is submitted on that instance

### Requirement: Dashboard location unchanged

The Cleanup policies page SHALL stay at `/settings/cleanup`, listed under Artifacts in the administration navigation, and the Maintenance page SHALL keep its Cleanup policies panel. The dashboard SHALL NOT change for this move.

#### Scenario: Address unchanged

- **WHEN** a signed-in administrator opens `/settings/cleanup`
- **THEN** the Cleanup policies page is shown under Artifacts, at the same address as before the move

#### Scenario: Domain not listed

- **WHEN** the server does not list the `cleanup` domain
- **THEN** the Artifacts entry and the Maintenance shortcut are not drawn, and the Maintenance page still loads

### Requirement: Own version and release line

The cleanup plugin module SHALL have its own version, `1.0.0`, independent of the server version, and its jar SHALL declare it in the manifest `Implementation-Version`. Publishing that jar is not part of this change.

#### Scenario: Version in the jar

- **WHEN** the cleanup plugin jar is built locally
- **THEN** its manifest declares `Implementation-Version` `1.0.0`

### Requirement: Absent embedded copy leaves the rest working

When the embedded copy is absent, no cleanup run SHALL start, no cleanup route SHALL answer, and the stored `cleanup` domain in `configuration.shared.json` SHALL stay unchanged. Removing the copy SHALL NOT delete the policies or the run table. The server SHALL start in such a build only after the cluster plugin no longer names `cleanup` as a dependency (decision O3).

#### Scenario: Stored domain kept

- **WHEN** a server built without the embedded copy, with the cluster dependency removed, starts with a `cleanup` domain in `configuration.shared.json`
- **THEN** no cleanup run starts, and the file keeps the `cleanup` domain unchanged

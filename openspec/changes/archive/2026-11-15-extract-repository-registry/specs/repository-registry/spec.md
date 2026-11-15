# Spec Delta

## Purpose

Parts of the server that only need to know which repositories exist read them through a format-neutral registry instead of
the Maven facade, with no change in behaviour.

## ADDED Requirements

### Requirement: Read-only repository registry

The server SHALL register a `RepositoryRegistry` facade that returns, for a name, the live repository or null, and the list
of all repositories in configuration order. Each entry SHALL expose name, visibility, effective type, storage provider and
whether it stores proxied files. The registry SHALL return the same repositories, in the same order, as
`MavenFacade.getRepositories()` at every point in time.

#### Scenario: Same view as the Maven facade

- **WHEN** the default settings are loaded
- **THEN** the registry lists `releases`, `snapshots` and `private` in that order, with the visibility, type and storage
  provider instance that `MavenFacade.getRepository` returns for each

#### Scenario: Settings change is visible at once

- **WHEN** a repository `extra` is added to the `maven` settings domain through the settings API
- **THEN** the next registry lookup of `extra` returns it, without a restart

### Requirement: Consumers read through the registry without behaviour change

The identity model, the dashboard handler, search, cleanup and the redirect plugin SHALL obtain repository names,
visibility, existence and storage providers from the registry. Their observable behaviour SHALL stay as it is.

#### Scenario: Private repository hidden from search

- **WHEN** an anonymous client searches and an artifact exists only in the `private` repository
- **THEN** the result does not contain it, as before

#### Scenario: Shared dashboard path

- **WHEN** a repository named `settings` exists and a Maven client requests `GET /settings/com/example/a/1.0/a-1.0.pom`
- **THEN** the response is the artifact, as before

#### Scenario: Permission node for repository visibility

- **WHEN** a principal without repository nodes reads a `PUBLIC` repository and a `PRIVATE` repository
- **THEN** access is granted and refused exactly as before

### Requirement: Cluster cache calls go through a coordinator

`ClusterPlugin` and its cache hooks SHALL invalidate resolution entries, bind the resolution cache to the bus and apply
received storage usage through a `RepositoryCacheCoordinator` facade, not through `MavenFacade`.

#### Scenario: Remote invalidation

- **WHEN** a `resolution.invalidated` message for repository `releases` and a path arrives on the cluster bus
- **THEN** the resolution cache entry of that path in `releases` is invalidated, as before

### Requirement: Maven does not depend on search

No class under `com.reposilite.maven` SHALL depend on a class under `net.onelitefeather.ingot.search`. The artifact index
sink SHALL live in a neutral package, and writes SHALL reach the search index when search is enabled and do nothing when it
is disabled or absent.

#### Scenario: Deploy with search enabled

- **WHEN** search is enabled and a client deploys `a-1.0.jar`
- **THEN** the index records the change, as before

#### Scenario: Deploy with search disabled

- **WHEN** search is disabled and a client deploys `a-1.0.jar`
- **THEN** the deploy succeeds and no index work is queued

#### Scenario: Dependency rule

- **WHEN** the architecture test inspects the compiled backend classes
- **THEN** it finds no dependency from `com.reposilite.maven` to `net.onelitefeather.ingot.search`, and none from token,
  cluster, search or the dashboard handler to `MavenFacade`

### Requirement: Public plugin types unchanged

The change SHALL NOT remove or alter any existing public type, method or constructor in `com.reposilite.*`. It MAY add a
supertype and a property to `com.reposilite.maven.Repository`.

#### Scenario: Binary report

- **WHEN** the plugin API report of `gate-plugin-abi` runs on this change
- **THEN** it lists no difference that it does not also list for main

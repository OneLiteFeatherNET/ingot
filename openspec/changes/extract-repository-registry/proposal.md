# Proposal

## Why

Ingot is meant to serve more than Maven. Before a second format can exist, the parts of the server that only need to know
"which repositories are there, who may see them, where do they store their files" must stop asking the Maven plugin for it.
Today they all do:

- The identity model asks `MavenFacade` for repository names and visibility (`token/application/AccessTokenPlugin.kt:76-77`).
- The cluster bus reaches into `MavenFacade` to invalidate the resolution cache, bind it to the bus and apply received
  storage usage (`cluster/FacadeCacheHooks.kt:29,37`, `cluster/ClusterPlugin.kt:265`).
- The dashboard handler asks `MavenFacade` whether a path segment is a repository (`frontend/infrastructure/FrontendHandler.kt:117`).
- Search reads names, visibility and storage through `MavenFacade` (`search/api/SearchApiEndpoints.kt:323,362`,
  `search/application/SearchStorage.kt:43-91`), while the Maven package in turn imports the search sink
  (`maven/*.kt`, nine imports of `net.onelitefeather.ingot.search`). The two packages depend on each other.
- Cleanup and the redirect plugin enumerate repositories and their storage through `MavenFacade`
  (`cleanup/application/CleanupPlugin.kt:57`, `redirect/RedirectPlugin.kt:39`).

Each of these is a small read. Together they make the Maven plugin the owner of the repository list, so any later format
would have to be bolted onto `MavenFacade`. This change gives them one read-only registry to ask instead, and makes the
dependency between Maven and search point one way only.

## What Changes

- **Read-only registry.** New interfaces `RepositoryRegistry` (a `Facade`) and `RegisteredRepository` in
  `net.onelitefeather.ingot.repository`. The registry lists repositories in configuration order and looks one up by name.
  A registered repository exposes its name, visibility, effective type, storage provider and whether it stores proxied
  files. `com.reposilite.maven.Repository` implements `RegisteredRepository`; the Maven plugin registers a registry backed by
  its live `RepositoryProvider`, so a settings change is visible at once, exactly as through `MavenFacade` today.
- **Consumers switch to the registry.** `AccessTokenPlugin`, `FrontendHandler` (the repository check of shared paths), search
  (`SearchApiEndpoints`, `MavenIndexStorage`, `MavenRepositoryEntries`), cleanup (enumeration and storage) and the redirect
  plugin (storage lookup) read through the registry.
- **Cluster hooks behind a port.** The three cluster calls are write-side, so they do not belong to a read-only registry.
  A second small interface `RepositoryCacheCoordinator` (invalidate a resolution entry, bind to the bus, apply received
  storage usage) is implemented by the Maven plugin and used by `ClusterPlugin` and `FacadeCacheHooks`.
- **Search and Maven no longer form a cycle.** `ArtifactIndexSink` and `NoOpArtifactIndexSink` move to
  `net.onelitefeather.ingot.repository.index`, together with the lazy sink that finds the search side at call time
  (today `SearchFacadeArtifactIndexSink`). `SearchFacade` exposes its sink through a neutral interface. After this, nothing
  under `com.reposilite.maven` imports `net.onelitefeather.ingot.search`; search may still use Maven types (events, path
  helpers, `RepositoryVisibility`).
- **A guard.** An ArchUnit test fixes the new edges: Maven does not depend on search, and token, cluster, search and the
  dashboard handler do not depend on `MavenFacade`.
- **Documentation.** `docs/gitbook/explanation/backend-architecture.md` (registry, sink package, dependency direction) and the
  facade table of `docs/gitbook/reference/plugin-api.md` (the registry is listed as not yet stable for plugins).

### Out of scope

- Any behaviour change. Routes, responses, settings, plugin load order and the `@Plugin` dependency lists stay as they are.
- Removing or changing any existing public type or method. `MavenFacade.getRepository` and `getRepositories` stay and keep
  their signatures.
- Statistics. It imports only the public `maven.api.Identifier` and two length constants, never `MavenFacade`, so there is
  nothing for a registry to replace.
- The request dispatcher, the `format` attribute and the storage leaks: `add-repository-format-spi`.
- Javadocs, latest-version and badge endpoints, which are Maven features and keep using `MavenFacade`.
- Cleanup's version deletion and mirror-cache lookup, which stay Maven calls until cleanup asks the format what a version is.

## Capabilities

### New Capabilities

- `repository-registry`: the read-only registry, the cache coordinator port, the neutral index sink and the dependency rules.

## Impact

- **Type and scope.** The change ships under `refactor(maven)`, with `test(maven)` and `docs(maven)` commits. The pull request
  title is:

  `refactor(maven): read repositories through a read-only registry`

- **Breaking marker.** No breaking change. No configuration key, environment variable, volume, manifest or plugin changes.
  The only public additions are new interfaces in `net.onelitefeather.ingot.repository` and a new supertype of
  `Repository`, which is binary compatible. `ArtifactIndexSink` moves package; it lives in `net.onelitefeather.ingot.*`, is
  not part of the documented plugin API and has no user outside the backend. The title carries no `!`.
- **Order.** Depends on `gate-plugin-abi`, whose report must show no new difference for this pull request.
  `add-repository-format-spi` depends on this change.
- **Code.** `reposilite-backend` (maven, token, cluster, frontend, search, cleanup, redirect) and `docs/gitbook/`.

## Verification

- Targeted unit and integration tests through `heavy` with `--max-workers=2`; the existing Maven, token, search, cleanup,
  cluster and frontend tests pass with no edit other than the import of the moved sink.
- The "Plugin API" report of `gate-plugin-abi` shows no difference that main does not already show.
- `openspec validate extract-repository-registry --strict` passes and
  `grep -nP '[\x{2013}\x{2014}]' -r openspec/changes/extract-repository-registry` prints nothing.

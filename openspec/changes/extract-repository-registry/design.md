# Design

## Context

Verified against origin/main (c0f01846):

- `Extensions.facade(type)` returns the first registered facade for which `type.isInstance(it)` holds
  (`plugin/Extensions.kt`). `Extensions.registerFacade` is public, so a plugin can register more than the one facade its
  `initialize` returns.
- Plugin order: `maven` depends on `statistics`, `frontend`, `authentication`, `access-token` and others
  (`maven/application/MavenPlugin.kt:48`). `access-token`, `frontend` and `statistics` therefore initialize before `maven`,
  and look Maven up lazily: `AccessTokenPlugin` in lambdas (`:76-77`), `FrontendPlugin` inside its `RoutingSetupEvent`
  listener (`:102`). `search`, `cleanup`, `redirect`, `javadoc` and `cluster` list `maven` as a dependency.
- Reads per consumer: token needs names and visibility; the dashboard handler needs existence; search needs names,
  visibility and storage provider; cleanup needs name, storage provider, whether a mirror stores files, the mirror cache
  entries (`internal`) and version deletion; redirect needs the storage provider of one repository; cluster needs three
  `internal` write-side calls (`bindResolutionCacheToClusterBus`, `applyReceivedStorageUsage`, resolution cache
  invalidation through `Repository.resolutionCache`).
- `RepositoryProvider.apply` replaces repository instances when settings change and keeps unchanged ones live
  (`maven/RepositoryProvider.kt`). `MavenFacade.getRepository` and `getRepositories` read that live state.
- Maven imports `net.onelitefeather.ingot.search.ArtifactIndexSink` in eight files and `SearchFacadeArtifactIndexSink` in
  `MavenPlugin.kt:42`. `SearchFacadeArtifactIndexSink` finds the search side at call time through
  `extensions.getFacades().filterIsInstance<SearchFacade>()`, because search initializes after Maven.
- `statistics` imports `maven.api.Identifier`, `GAV_MAX_LENGTH` and `REPOSITORY_NAME_MAX_LENGTH` only. These are public
  plugin types and stay.
- There is no architecture test in the build today.

## Decisions

### D1: two interfaces, Repository implements the read side

```kotlin
package net.onelitefeather.ingot.repository

interface RepositoryRegistry : Facade {
    /** The repository named [name], or null. Reads the live state, so a settings change is visible at once. */
    fun repository(name: String): RegisteredRepository?
    /** All repositories in configuration order. */
    fun repositories(): List<RegisteredRepository>
}

interface RegisteredRepository {
    val name: String
    val visibility: RepositoryVisibility          // com.reposilite.maven, stays there for binary reasons
    val effectiveType: EffectiveRepositoryType    // com.reposilite.maven
    val storageProvider: StorageProvider
    val storesProxiedFiles: Boolean               // any proxied host with store = true
}
```

`com.reposilite.maven.Repository` implements `RegisteredRepository`. `name`, `visibility`, `effectiveType` and
`storageProvider` already exist with these types, and `storesProxiedFiles` is a new derived property. Adding a supertype
and a property to a public class is binary compatible, and it avoids an adapter object per repository, which would have to
be kept in step with `RepositoryProvider.apply`.

`MavenFacade` does **not** implement `RepositoryRegistry`. The registry is meant to become core-owned once more than one
format exists; removing a supertype from `MavenFacade` later would break plugins compiled against it. An internal class
`MavenRepositoryRegistry(repositoryProvider)` implements the interface and is registered by `MavenPlugin.initialize` through
`extensions().registerFacade(...)` before it returns `MavenFacade`.

Consumers that initialize before Maven (token, frontend) look the registry up lazily, as they look up `MavenFacade` today.
Lookup timing does not change, so plugin order does not change.

The types that appear in the interface (`RepositoryVisibility`, `EffectiveRepositoryType`) live in `com.reposilite.maven`
and cannot move (`RepositoryVisibility` is public plugin API and part of `RepositoryDirectoryInfo`). Neutral code imports
them; that is an import of a stable value type, not of Maven behaviour.

The interfaces are new public API of Ingot. They are documented as "not yet stable for plugins" until the format SPI ships,
because `add-repository-format-spi` adds `format` to `RegisteredRepository`, and an added interface member breaks external
implementors.

### D2: cluster gets a write-side port, not registry methods

The three cluster calls change state. Putting them on a read-only registry would make the name a lie. A second interface:

```kotlin
interface RepositoryCacheCoordinator : Facade {
    fun invalidateResolution(repository: String, path: String)
    fun bindToClusterBus()
    fun applyReceivedStorageUsage(event: StorageUsageRefreshedEvent): Boolean
}
```

The same internal Maven class implements it and delegates to today's `internal` functions. `ClusterPlugin` and
`FacadeCacheHooks` call it instead of `MavenFacade`. `ClusterPlugin` keeps `maven` in its dependency list: the list decides
load order, and this change must not move it. In `add-repository-format-spi` storage usage stays core and resolution
invalidation becomes a format hook; the port is where that split happens.

### D3: the index sink moves to a neutral package

`ArtifactIndexSink`, `NoOpArtifactIndexSink` and the lazy delegating sink move to
`net.onelitefeather.ingot.repository.index`. The lazy sink stops naming `SearchFacade` and looks for any facade implementing
a new interface `ArtifactIndexSinkProvider { val sink: ArtifactIndexSink }`, which `SearchFacade` implements. Behaviour is the
same: no search facade or a disabled search means a no-op, and the lookup happens per call.

Alternative considered: keep the sink in `search` and invert only the Maven plugin's import. That leaves eight Maven files
importing a search package, so the cycle stays at package level. Rejected.

After the move the direction is one way: search depends on Maven types (events, `indexChanged`, `provenancePath`,
`isChecksumSidecar`, `MirrorCacheLedger`, `RepositoryVisibility`); Maven depends on nothing in search.

### D4: an ArchUnit test keeps the edges cut

The rules are checked on compiled classes with ArchUnit (Apache-2.0, test scope only):

1. No class in `com.reposilite.maven..` depends on a class in `net.onelitefeather.ingot.search..`.
2. No class in `com.reposilite.token..`, `net.onelitefeather.ingot.cluster..` or `net.onelitefeather.ingot.search..`, and
   not `ResourcesFrontendHandler`, depends on `com.reposilite.maven.MavenFacade`.
3. No class in `net.onelitefeather.ingot.repository..` depends on `com.reposilite.maven..` other than `RepositoryVisibility`
   and `EffectiveRepositoryType`.

The import is limited to the backend's main classes, which keeps it at a few seconds. A source-text scan was rejected: it
reads the filesystem and misses fully qualified references.

Cleanup, redirect, javadoc and `FrontendPlugin` (which still builds `MavenEndpoints`) keep `MavenFacade` on purpose and
are not in rule 2. `add-repository-format-spi` removes the `FrontendPlugin` and redirect uses through the dispatcher and
tightens the rule.

## Risks

- A consumer that reads through the registry before Maven registered it gets "facade not found". Mitigation: every switched
  call site keeps today's lazy lookup point; a test starts the full plugin set and exercises each consumer once.
- Search code and its tests import the sink from the new package. That is a mechanical import change, reviewed in one
  commit of its own.

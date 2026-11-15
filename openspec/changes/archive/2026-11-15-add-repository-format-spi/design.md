# Design

## Context

Verified against origin/main (c0f01846), after `extract-repository-registry`:

- Repositories are declared only in `MavenSettings.repositories` (`maven/application/MavenSettings.kt:44-48`), as
  `RepositorySettings` (`:124`). The precedent for an optional key is `type: RepositoryType? = null` (`:138`), documented as
  "leave it empty to keep the behaviour of earlier versions".
- Settings are written with `DEFAULT_OBJECT_MAPPER`, which serializes with `Include.NON_NULL` and reads with
  `FAIL_ON_UNKNOWN_PROPERTIES = false` (`ReposiliteObjectMapper.kt:40-41`). Reposilite 3.5.28 reads with the same setting.
  A nullable key that is null is therefore never written, and an older server ignores an unknown key.
- `Repository` has an `internal` constructor and public properties (`maven/Repository.kt:31-44`). It is created only by
  `RepositoryFactory.createRepository` (`maven/RepositoryFactory.kt`).
- The Maven routes are `/{repository}/<gav>` for HEAD and GET (`maven/infrastructure/MavenEndpoints.kt:85`), POST and PUT
  (`:224`) and DELETE (`:255`), registered at default priority by `MavenPlugin` (`:96`). `MavenEndpoints.findFile(ctx,
  identifier, repository, gav)` is public and is also called by `ResourcesFrontendHandler.answerFromMaven`
  (`frontend/infrastructure/FrontendHandler.kt:144-153`) and by `RedirectPlugin` (`redirect/RedirectPlugin.kt:28-52`).
  `FrontendPlugin` registers the dashboard at `Priorities.HIGH` (`:90`).
- `PostAuthHandler` is a `Route.AFTER` filter on `/{repository}/<*>` that only adds `WWW-Authenticate` to a `401`
  (`auth/infrastructure/PostAuthHandler.kt:30`). It is not a second endpoint.
- The OpenAPI document is generated at compile time from the `@OpenApi` annotations on `MavenEndpoints`.
- Maven rules in shared code: `Repository.acceptsDeploymentOf` / `acceptsCachingOf` (`:70,73`, `maven-metadata.xml`),
  `RepositoryService.ignoredExtensions` (`:67-82`, download counting), `ResolutionProvider.isResolutionMetadata` (`:147-153`),
  `isMetadataOrChecksumFileName` (`MirrorCache.kt:170`), `artifactIndexPath` (`ArtifactIndexPath.kt`).
- Storage leaks: `storage/api/FileDetails.kt:18` imports `RepositoryVisibility` for `RepositoryDirectoryInfo` (`:68`);
  `storage/s3/CachableS3Client.kt:45-51` drops `<parent of parent>/maven-metadata.xml*` from its head cache on every put and
  delete; `storage/Comparators.kt:35` `VersionComparator` splits on `[-._]` and orders filesystem listings
  (`FileSystemStorageProvider.kt:269`). `CachableS3Client` is built inside `S3StorageProviderFactory` (`:103`), behind the
  public `StorageProviderFactory.create(...)` signature.
- Javalin is 7.2.2.

## Goals

- A repository says which format it is; every existing repository is `maven2` without a change on disk.
- Shared code asks the format instead of naming Maven files.
- The SPI surface is defined far enough that Raw, Helm classic, npm, PyPI and OCI can be designed against it.
- Nothing observable changes.

## Decisions

### D1: `format` is a nullable trailing key of `RepositorySettings`

```kotlin
@get:Doc(title = "Format", description = "The repository format. Leave it empty for Maven (maven2), which is the only format of this version.")
val format: String? = null,   // last parameter
```

- Null means `maven2`. The resolved id is computed when the repository is built, never written into the settings.
- Because the mapper writes `NON_NULL`, a repository without `format` is written back without it, whatever else is edited.
  A repository with `format: "maven2"` keeps the key; `normalized()` does not touch it.
- The parameter is appended last, and the previous primary constructor is kept as a secondary constructor with the exact
  previous parameter list, so code compiled against today's `RepositorySettings` keeps linking. `copy` cannot keep its old
  JVM signature in a data class. Settings classes are not in the documented plugin API (`plugin-api.md` lists facades,
  events and annotations), and Ingot 1.0 already changed this constructor relative to Reposilite 3.5.28, so this is not a
  plugin API break under `VERSIONING.md`. The difference is still shown by the gate and named in the pull request. Putting
  `format` in the class body instead was rejected: a body property is not part of `equals`, and `RepositoryProvider` decides
  whether to rebuild a repository by comparing settings.
- Validation in `MavenSettings.violations()`: an unknown id is a violation naming the repository and the known ids; a local
  proxied reference to a repository of another format is a violation (D5). The known ids come from the format registry
  (D2), which is immutable after start.
- At start, a stored repository with an unknown format is skipped with an error log that names it, and the others start.
  Serving it as Maven would apply the wrong rules to its files. This covers the rollback from a later Ingot that knew more
  formats. It uses the path that already exists: `RepositoryFactory` throws, and `RepositoryProvider.build` records the
  failure ("Cannot load <id> repository") and leaves the repository out.
- Alternative considered: a `formats` map in `MavenSettings` keyed by repository id. It keeps `RepositorySettings` untouched,
  but splits one repository's definition over two places and does not survive the settings split. Rejected.
- Alternative considered: the id `maven`. `maven2` names the layout (as Nexus does), leaves room for a legacy layout, and is
  what the roadmap uses. A value of `maven` is refused with a hint to use `maven2` or leave it empty.

### D2: the SPI is discovered like storage factories and starts experimental

`RepositoryFormats` loads `RepositoryFormat` implementations through `ServiceLoader`, keyed by `id`, once at start; tests
construct it from a list. Duplicate ids fail the start with both class names. `maven2` is the default format and must be
present.

Every SPI type carries `@ExperimentalIngotFormatApi` (a Kotlin `@RequiresOptIn`). Members will be added while Raw, Helm and
npm prove the surface; an opt-in marker makes that honest. The `gate-plugin-abi` follow-up adds an API dump for this package
once the marker is removed.

### D3: the SPI surface

Shipped in this change (each has a caller and a Maven implementation that reproduces today's behaviour):

```kotlin
package net.onelitefeather.ingot.format

interface RepositoryFormat {
    val id: String                            // "maven2"
    val displayName: String                   // "Maven 2"
    val mount: FormatMount                    // D7; Maven: FormatMount.RepositoryPath
    val pathRules: PathRules
    val groupPolicy: GroupPolicy              // D5
    val storageHints: StorageHints            // D6
    fun handler(context: FormatContext): RepositoryRequestHandler   // D4
}

interface PathRules {
    /** A file the server generates and owns, so a client may overwrite it and a proxy must not freeze it. */
    fun isGeneratedMetadata(path: Location): Boolean           // Maven: name contains maven-metadata.xml
    /** A checksum or signature sidecar, and the file it belongs to. */
    fun sidecarBase(path: Location): Location?                 // Maven: .md5 .sha1 .sha256 .sha512 -> base
    /** A file whose resolution decision may be cached (resolution cache). */
    fun isResolutionMetadata(path: Location): Boolean          // Maven: .pom .module maven-metadata.xml ivy*.xml
    /** Whether a successful read of this path counts as a download in statistics. */
    fun countsAsDownload(path: Location): Boolean              // Maven: not in today's ignoredExtensions
    /** The path the search index is told about for a write, or null. */
    fun indexPath(path: Location): String?                     // Maven: today's artifactIndexPath()
}
```

`Repository.acceptsDeploymentOf` and `acceptsCachingOf` keep their signatures and ask `pathRules.isGeneratedMetadata`.
`RepositoryService`, `ResolutionProvider`, `MirrorService`, `MirrorCache` and `ArtifactIndexPath` ask the repository's
format. A characterisation test per rule runs the old function and the Maven rule over the same table of paths (including
`maven-metadata.xml.sha1`, `-SNAPSHOT` paths, `ivy-1.0.xml`, a file without extension) before the old code is removed.

Defined here, added by the change that first needs them (no hook without a caller):

```kotlin
interface MetadataHooks {                       // Helm classic (index.yaml), npm (packument), Maven later
    fun afterWrite(repository: RegisteredRepository, path: Location)
    fun afterDelete(repository: RegisteredRepository, path: Location)
}
interface ProxyRules {                          // npm, PyPI, OCI; Maven keeps storagePolicy and metadataMaxAge
    fun cachePolicy(path: Location): CachePolicy                   // IMMUTABLE | MUTABLE(maxAge) | NEVER
    fun rewrite(path: Location, upstreamBody: InputStream, publicBase: URI): InputStream?  // npm dist.tarball, Helm urls
    fun isAllowed(path: Location, allowedNamespaces: List<String>, allowedExtensions: List<String>): Boolean
}
interface CleanupCriteria {                     // add-raw-format, then per format
    fun versions(entries: Sequence<StoredFile>): List<VersionGroup>    // Maven: directories holding a .pom
    fun isPrerelease(version: String): Boolean                         // Maven: -SNAPSHOT
    fun compareVersions(a: String, b: String): Int                     // Maven: VersionComparator
    val scopes: Set<String>                                            // Maven: RELEASES, SNAPSHOTS
}
interface IndexHooks {                          // search generalisation
    fun extract(path: Location): IndexRow?                             // Maven: ArtifactPathParser
    val facets: List<String>
}
interface PermissionScope {                     // identity, per format node vocabulary
    fun pathPrefix(namespace: String): String?                         // Maven: groupId dots to slashes
    val nodeKind: String?                                              // Maven: "artifact"
}
```

Permission evaluation itself stays core and path based (`/<repository>/<path>`, `PermissionEvaluator`); a format only maps
its coordinates to path prefixes. Version semantics leave the storage package for `CleanupCriteria.compareVersions`.

### D4: one dispatcher, Maven keeps its handler code

```kotlin
interface RepositoryRequestHandler {
    fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse>
}
data class RepositoryRequest(val ctx: Context, val identifier: AccessTokenIdentifier?, val repository: String, val path: String)
```

`RepositoryRouteDispatcher` (core) registers `/{repository}/<path>` for HEAD and GET, POST and PUT, and DELETE: the same
patterns, methods and default priority as the Maven routes it replaces, and it is registered by `MavenPlugin` at the same
point, so the Javalin match order is unchanged. Per request it looks the repository up in the registry, takes its format
(or the default format when the repository does not exist, so today's `404` page and body are produced by the same Maven
code), and calls that format's handler inside the same `accessed { }` wrapper the routes use today.

The Maven handler is today's route bodies, moved out of the route lambdas into `MavenEndpoints` functions without edits:
`findFile` (already public), `deployFile`, `deleteFile`. `MavenEndpoints` keeps its `@OpenApi` annotations, so the generated
document is identical; a test compares the repository paths of the document before and after.

`ResourcesFrontendHandler.answerFromMaven` becomes `answerFromRepository` and calls the dispatcher; `DashboardRouteDecision`
and its `MAVEN` target name stay. `RedirectPlugin` builds its routes on the dispatcher. `PostAuthHandler` is not touched. The
architecture test of `extract-repository-registry` gains: only `com.reposilite.maven..` and the Maven format may construct
`MavenEndpoints`.

### D5: groups stay inside one format

A typed group, and an untyped repository with loopback references, may reference local repositories of its own format only.
Members are resolved first hit in configuration order, as today. `GroupPolicy` is `FIRST_HIT` for Maven; a format that needs
merged answers (npm packuments, PyPI simple pages) declares `MERGE` and supplies the merge with its `MetadataHooks` change.

### D6: the storage boundary

`StorageProvider` stays the byte layer: paths in, bytes out, no format knowledge.

1. **S3 invalidation.** `CachableS3Client` gets a related-key rule `(key: String) -> Collection<String>` (prefixes to drop
   from the head cache after a put or delete). Its existing constructor keeps today's Maven rule as the default, so an S3
   provider created anywhere else behaves as before. `S3StorageProvider` implements a new optional interface
   `RelatedKeyAware`; `RepositoryFactory` passes `format.storageHints.relatedKeys` after creating the provider. Maven's hint
   is today's rule, moved, so nothing changes; a later Raw format supplies none. `StorageProviderFactory` is untouched.
2. **`RepositoryVisibility` in `storage.api`.** It cannot move: `FileDetails` is sealed, its subclasses must stay in
   `storage.api`, and `RepositoryVisibility` is plugin API. It is documented as a core concept with a historical package,
   and the architecture test allows exactly this one import from storage into `com.reposilite.maven`.
3. **`VersionComparator`.** Public API, used for listing order. It stays as a generic natural-order comparator (KDoc says
   so). Ordering of versions is the format's job (`CleanupCriteria.compareVersions`); Maven keeps using this class.

### D7: how Raw and OCI plug in later

- **Raw** (`add-raw-format`): `id = "raw"`, `mount = RepositoryPath`, path rules that treat every path as a plain file (no
  generated metadata, checksum sidecars optional, every read counts), `FIRST_HIT` groups, no index, path-only permissions,
  a handler on the shared `RepositoryService` for GET, HEAD, PUT and DELETE. A Raw repository lives at `/{repository}/**`
  like Maven; the dispatcher picks it by the repository's format, so no new top-level path appears.
- **OCI** needs `/v2/` at the host root, which a path under `/{repository}` cannot give. `FormatMount` is a sealed type:

  ```kotlin
  sealed interface FormatMount {
      object RepositoryPath : FormatMount                      // /{repository}/**, all formats so far
      data class Host(val hostSetting: String) : FormatMount   // OCI: INGOT_OCI_HOST, opt-in
  }
  ```

  A host mount is served by a second dispatcher, a `before` handler on the same port 8080 that runs only when the request's
  `Host` equals the configured name. It handles `/v2/**` for that host and stops the remaining handlers; every other host
  goes to the routes of today, so the Maven root and the dashboard are untouched. Inside `/v2/<repository>/<image>/...` the
  first name segment selects the repository through the registry, and that repository must be of format `oci`. The token
  realm is built from the configured host, never from the request. With no host configured, nothing is registered. The
  Javalin 7 mechanism for stopping the remaining handlers from a `before` handler is checked in the OCI spike before that
  change is proposed. `DashboardPaths` does not need `v2` as a reserved segment, because the host mount never shares a host
  with repository paths.

## Risks

- Moving route bodies could change a header or a status in a corner case. Mitigation: before the move, a characterisation
  integration test records status, headers and body for HEAD, GET, PUT, POST and DELETE on a hosted, a proxy and a group
  repository, an unknown repository, a directory, `/api/unknown`, a shared dashboard path and a redirected path; it must pass
  unedited afterwards.
- A misbehaving format from the class path could break start. Mitigation: duplicate ids fail fast with names; formats come
  only from the server class path in this change.
- `copy` of `RepositorySettings` changes its JVM signature (see D1). A plugin that calls it must be recompiled. The gate
  report lists it, the pull request names it, and `plugin-api.md` states that settings classes are not covered by the
  compatibility promise.

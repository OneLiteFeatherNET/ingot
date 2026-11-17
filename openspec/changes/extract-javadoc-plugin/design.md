# Design

This design depends on the prerequisite change `add-embedded-plugin-jars` (`feat(plugin)`), which must be merged before the code pull
requests start. It refers to `add-plugin-support-tiers` only for the tier name, which that change defines.

## Context

- The viewer is the `JavadocPlugin` class of `reposilite-backend/src/main/kotlin/com/reposilite/javadocs/application/`, registered by
  `reposilite-backend/src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin`. Its classes are on the server
  classpath.
- The plugin declares the `javadoc` settings domain (`JavadocSettings`), registers the placeholder `{{REPOSILITE.JAVADOC_SUFFIXES}}`
  (empty while javadoc is disabled), serves `/javadoc/{repository}/<gav>` and its `/raw/` route, listens to `DeployEvent`, and emits
  `JavadocInvalidatedEvent`, which the cluster plugin publishes as `javadoc.invalidated`.
- Loader facts, read from `reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt` and `Extensions.kt`:
  - The plugins directory is loaded first, in a `URLClassLoader` whose parent is the server class loader, and its `ServiceLoader`
    results go to `Extensions.registerPlugin`. That call uses `check(!plugins.containsKey(name))`, so a second plugin with the same id
    fails the start.
  - The parent loader is parent-first. A class that the server jar also contains is resolved from the server, not from a plugin jar.
    A newer copy of a class in a plugins-directory jar cannot replace a class on the server classpath.
  - The store loads after the plugins directory and skips an id that a plugins-directory jar holds. The bundled jars load last and skip
    an id that is already registered, with a debug log. Today the precedence is: plugins directory, then store, then bundled, and no
    jar can replace a built-in plugin.
  - No version is read from a plugin today. `@Plugin` has `name`, `dependencies`, `settings` and the tier (prerequisite of
    `add-plugin-support-tiers`). The version of a jar is its manifest.
- Plugin versions. Every subproject takes `version = projectVersion` from the root `build.gradle.kts`, which Release Please rewrites.
  The plugin release job publishes `opensearch-plugin` only, and its `check-bundled` step compares the published jar with the one in the
  image.
- The module directory existed before the code was merged into the core (commit `9b8f8c2a8`). The IDE run configuration
  `.run/Build javadoc-plugin to test workspace` names it, and builds a workspace jar that the new module does not produce.

## Decisions

### D1. The viewer is a module with its own version and jar, embedded in the server

`reposilite-plugins/javadoc-plugin` is a Kotlin JVM project with `version = "1.0.0"`, overriding the subproject default. The line
carries the marker `// x-release-please-version` so that the follow-up change can bump it. It compiles against `reposilite-backend`
with `compileOnly`, like the other plugin modules, and its tests use `testImplementation` on the backend and its test fixtures.

Its shadow jar `javadoc-plugin.jar` declares `Implementation-Version` in the manifest, which the loader of the prerequisite reads. Its
Maven publication `net.onelitefeather.ingot:javadoc-plugin` is configured as `reposilite-plugins/gcs-storage-plugin/build.gradle.kts`
configures its own. The publication is built locally in this change and uploaded only by the follow-up change (D11).

The backend `shadowJar` takes that jar through the hook of `add-embedded-plugin-jars`, which places it at `embedded-plugins/javadoc-plugin.jar`
inside `ingot.jar`. The classes of the viewer are not on the server classpath, so they are loaded only through the nested jar, in its
own class loader. The build order is `javadoc:shadowJar` before `backend:shadowJar`, and no task depends the other way, so Gradle sees no
cycle.

The integration tests run the server from classes, not from `ingot.jar`, so the integration task places the module jar into the test
workspace plugins directory, as the other plugin modules do with `reposilite-test/workspace/plugins`. Task 3.4 verifies the wiring.

### D2. Packages, plugin id and file names do not change

The package stays `com.reposilite.javadocs`, the plugin id stays `javadoc`, and the file names stay. `git mv` moves each file, so a
reviewer sees renames. Kotlin `internal` is per Gradle module. Classes that the core currently reaches with `internal` visibility are
listed by task 0.3, and each one is either made public in the move pull request or passed through the seam of D3. No class is made
public for another reason.

### D3. The core reaches the viewer through an interface it owns

`FacadeCacheHooks.javadocInvalidated` no longer looks up `JavadocFacade`. The cluster package declares a public interface
`JavadocCacheReceiver` with one method, `evictReceived(repository: String, path: String): Result<Unit, ErrorResponse>`. `JavadocFacade`
implements it. The hook finds the implementation among the facades with `filterIsInstance`, as it does today, and logs at debug level
when none is loaded.

The interface lives in `net.onelitefeather.ingot.cluster`. It is core API, not plugin API, so it is not in `pluginApiPackages` and the
plugin API dump does not change. An architecture test pins the boundary: no class outside `com.reposilite.javadocs` may depend on that
package. The test is written first and fails on `FacadeCacheHooks`.

### D4. The settings domain keeps its place

The plugin keeps `settings = JavadocSettings::class`, so it registers the `javadoc` domain as before. The Administration tree keeps
the domain under Artifacts through the static entry `{ domain: 'javadoc', ... }` of `reposilite-frontend/src/navigation/registry.ts`,
which carries the label, description and keywords. The dashboard does not change: same heading, label, description and link. No
plugin section is registered, because a section would only repeat the placement and cannot carry the description.

The domain stays keyed by `/settings/<domain>`, so `/settings/javadoc` keeps its address. For a build without the viewer the address
shows the state the dashboard shows for an unknown domain. Task 0.2 records that state. If it is an error, the page redirects to
`/settings`, and a test pins the redirect.

### D5. Without the viewer the Javadoc links are hidden, not broken

There are three cases, and each one hides the Javadoc action in the file browser:

1. **Disabled by the operator.** `javadoc.enabled=false`. The viewer stays loaded. Its placeholder is empty, so the action is hidden.
   The routes answer `404`, as today.
2. **Built without the viewer.** No placeholder is sent. Today the frontend falls back to `-javadoc.jar,-groovydoc.jar`, which would
   show links to routes that do not exist. The fallback for `REPOSILITE.JAVADOC_SUFFIXES` becomes empty.
3. **An older server.** It always registers the placeholder, so the empty fallback does not affect it.

The operator's switch is `javadoc.enabled`. No embedded copy is removed in this change (question 1, settled 2026-11-17). A stored `javadoc` row that no
loaded domain claims is kept by the shared configuration (task 0.1 verifies it) and is inert.

### D6. Routes, events and the cache directory do not change

- Routes: `/javadoc/{repository}/<gav>` and `/javadoc/{repository}/<gav>/raw/<resource>`, at the same paths.
- Events: `JavadocInvalidatedEvent` is emitted by the viewer and published by the cluster plugin. The wire type `JAVADOC_INVALIDATED`
  and the fields `repository` and `path` stay in `ClusterEventType`, because the wire format is a core contract.
- Cache directory: `<workingDirectory>/javadocs`. An existing installation adopts its cache unchanged.
- Settings: the key `javadoc`, the fields `enabled` and `suffixes`, their defaults, and the shared configuration file are unchanged.

### D7. Tests move with the code, and the cluster test stays

The unit tests `JavadocContainerServiceTest`, `JavadocEvictionTest`, `JavadocMaxEntryBytesTest`, `JavadocSnapshotResolutionTest` and
`specification/JavadocSpecification.kt` move into the module's test source set, in the same package, so they keep their access to
`internal` declarations. The integration test `JavadocsIntegrationTest` and its specification move into the module's integration
source set. Only build wiring changes, and no assertion does.

`ClusterJavadocEventsTest` stays in the core, because it tests the bus. It uses a fake of the seam of D3 for the received side and the
`JavadocInvalidatedEvent` of the plugin API for the publishing side. `FrontendFacadeTest` and `OpenApiCompletenessTest` keep their
generic javadoc references.

Each test follows F.I.R.S.T.: no sleeps, no system clock, no shared static state, and a `@TempDir` per test.

### D8. The tier is Core by origin

The viewer is built into the server, as an embedded jar. `add-plugin-support-tiers` reads the origin of such a jar as built into the
server, so it is Core without a declaration. The prerequisite maps its origin `EMBEDDED` to Core. This change adds no `tier` element.

### D9. The update rule

A jar in the plugins directory with the id `javadoc` replaces the embedded copy only when its manifest `Implementation-Version` is
strictly higher than the embedded copy's. The rule is implemented by the prerequisite, and this change relies on it, as follows:

- Equal or lower version, or no version: the embedded copy is used, and the start logs one INFO line that names both versions and the one
  in use.
- Strictly higher version: the plugins-directory jar is used, and the same INFO line names both versions.
- Two plugins with the id are never registered.

The rule replaces only the viewer, not the rule of the plugins directory for any other plugin id, which stays as it is today.

### D10. The image keeps its file list

The released jar is not copied into `/app/bundled-plugins`. The embedded copy already gives the image the viewer, and a second copy with
the same id would add a second precedence path (the bundled rule skips a registered id, and a bundled copy beside an embedded one would
depend on the order of loading). The file list of the image is therefore unchanged. Its `ingot.jar` changes content only.

The plugins directory of the image is the one of the working directory, as today. Task 0.4 records where that directory is, so the
update path in the operator guide names the right place.

### D11. Release is a follow-up

Publishing the jar to the official repository, the catalogue entry and the check against the image need three changes to the release
job: a release-please component for the module with its own tag, a generalised `publish_plugins.sh` that takes the plugin name, and a
`check-bundled` that compares the published jar with the embedded copy. They are the change `add-javadoc-plugin-release` (`ci(plugin)`).
This change ends with a locally built and locally published Maven artifact.

### D12. Rejected: the viewer's classes in the server jar, and a bundled jar in the image

Two alternatives were considered and rejected.

- Classes in the server jar, with no nested jar. A newer copy in the plugins directory could not replace them, because the parent loader
  is parent-first (see the context). The update path would not work.
- A separate jar in `/app/bundled-plugins`. Its switch would need a default of on, because a missing switch is off for every bundled jar.
  A server jar started outside the image would lose the viewer, because `/app/bundled-plugins` does not exist there.

## Risks

- **The prerequisite is a dependency.** If it slips, this change slips. Its code pull requests cannot start before it merges.
- **Internal visibility across modules.** The compiler finds each use (task 0.3). The fix stays small and is part of the move pull request.
- **Load order.** The dependencies of the plugin (`failure`, `shared-configuration`, `frontend`, `maven`) do not change, so the order of
  initialisation stays the same.
- **Dashboard placeholder.** The frontend and the server must agree. An older dashboard reads the placeholder the server sends, and the
  empty fallback matters only when the placeholder is absent.
- **Release drift.** Until `add-javadoc-plugin-release` merges, the viewer ships only inside the server, so its own version does not
  reach operators by the release job. The update rule of D9 works for a jar that an operator or the follow-up places.

## Migration plan

Each step is one pull request, buildable and passing alone:

0. The prerequisite `add-embedded-plugin-jars` is merged (outside this change).
1. The seam of D3 and the architecture test. The core stops importing the viewer, which is still in the core.
2. The module, the move of the sources and the tests, the build wiring of D1, the embedded jar and the removal of the run configuration.
   Renames dominate the diff, so the pull request stays near 400 changed lines.
3. The frontend fallback of D5 and its tests.
4. The docs of the module, the embedded copy and the update path.

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

Verified (task 0.4): the `internal` declarations of `reposilite-backend/src/main/kotlin/com/reposilite/javadocs` are
`JavadocEndpoints`, the constructor of `JavadocFacade`, `javadocEvictionRoot`, `JavadocPlugin`, `DEFAULT_JAVADOC_MAX_ENTRY_BYTES`,
`javadocMaxEntryBytes`, `JavadocContainer`, `JavadocContainerService` (with `createContainer` and `unpackJavadocJar`) and `JavadocView`.
No class outside the package reaches any of them. The only references from outside are the service entry that names `JavadocPlugin`
and the public `JavadocCacheReceiver` of the cluster seam, which `JavadocFacade` implements. The move therefore needs no visibility
change for the core, and every one of these stays `internal` in the module. This pins the list of the move pull request as empty.
The design text above cites task 0.3 for this check, while `tasks.md` numbers it 0.4.

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

Verified (task 0.3): `/settings/:domain?` (`reposilite-frontend/src/router/routes.ts`) checks no domain. `SettingsView.vue`
(`activeDomain`, lines 111-114) falls back to the first listed domain when the requested one is not listed, and its watcher (lines 234-235)
reports the change, so `IndexPage.vue` (`setDomain`, lines 341-342) replaces the address with `/settings/<first listed domain>`. The
result is not an error page and not the `/settings` hub, so the error branch of this decision does not apply and the redirect to
`/settings` is not required by it. With no domain listed at all, the page shows an empty view.

### D5. Without the viewer the Javadoc links are hidden, not broken

There are three cases, and each one hides the Javadoc action in the file browser:

1. **Disabled by the operator.** `javadoc.enabled=false`. The viewer stays loaded. Its placeholder is empty, so the action is hidden.
   The routes answer `404`, as today.
2. **Built without the viewer.** No placeholder is sent. Today the frontend falls back to `-javadoc.jar,-groovydoc.jar`, which would
   show links to routes that do not exist. The fallback for `REPOSILITE.JAVADOC_SUFFIXES` becomes empty.
3. **An older server.** It always registers the placeholder, so the empty fallback does not affect it.

The operator's switch is `javadoc.enabled`. No embedded copy is removed in this change (question 1, settled 2026-11-17). A stored `javadoc` row that no
loaded domain claims is kept by the shared configuration (task 0.1 verifies it) and is inert.

Verified (task 0.2): the load path keeps an unclaimed row. `SharedConfigurationFacade.loadSharedSettingsFromString`
(`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SharedConfigurationFacade.kt`, lines 190-197) applies only the
names of the registered domains, and the file is not rewritten on load. The write path does not keep it: `renderConfiguration` (lines
489-497) renders only the registered domains, and `storeRendered` (line 466) writes that rendering on every save of any domain
(`updateSharedSettings`, `updateDomain`), and `rewriteDocument` does the same for the secret key commands. So the stored `javadoc` row
survives until the next save of any other domain, and is then removed. The row is inert in either case, so the behaviour of D5 holds,
but the claim that the row is kept untouched holds only until that save. Question 3 of `proposal.md` is affected only if the row must
survive a save, which is a maintainer decision.

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

Verified (task 0.5): the image entrypoint (`entrypoint.sh`) prepends `--working-directory=/app/data` unless `INGOT_OPTS`
or `REPOSILITE_OPTS` already names `--working-directory` or `-wd`, and passes the options on both the root and the non-root branch. The
plugins directory defaults to `<working directory>/plugins` (`ReposiliteParameters.kt`, line 170), which is `/app/data/plugins` in the
image. The embedded copies of the prerequisite extract into `<working directory>/embedded-plugins`, which is `/app/data/embedded-plugins`
(its design D1). `/app/bundled-plugins` is a separate default constant of `PluginLoader.kt` and is not the plugins directory. The
loader that reads `embedded-plugins` is not on main yet (see the gate of task 0.1), so this path is taken from the prerequisite's design.

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

### D13. Embedded plugins carry their API document fragment and settings schema

Found on the draft cleanup move pull request #895, and true of this move as well: the server builds two things at compile time from its own
classes only, and neither sees a plugin that loads from its own jar.

- **The API document.** `kapt` runs `openapi-annotation-processor` on the backend's main source set only (`reposilite-backend/build.gradle.kts`,
  the exclusion of `kaptTest`, `kaptIntegration` and `kaptTestFixtures`). It writes `openapi-plugin/.index` and
  `openapi-plugin/openapi-default.json` into the server's resources. `JavalinConfiguration.configureOpenApi` registers Javalin's
  `OpenApiPlugin`, whose `OpenApiLoader` reads that index through one class loader (by default the server's) on the first request to
  `/openapi`. The same document feeds the Swagger UI and the committed snapshot `docs/openapi/ingot-openapi.json`, which
  `OpenApiSnapshotIntegrationTest` compares with the served one. The viewer declares no `@OpenApi` route today, so the document holds no
  javadoc entry and the move removes nothing from it. The rule still applies, so that a documented route added to the viewer later reaches
  the document.
- **The settings schema.** The same processor writes `json-schemes/index` and one file `json-schemes/<class name>` per `@JsonSchema` class,
  here `JavadocSettings`. `SharedConfigurationFacade` reads them with `JsonSchemaLoader`, which calls
  `JsonSchemaLoader::class.java.getResourceAsStream("/json-schemes/index")`, so only the server's index is seen. After the move the
  `javadoc` domain is not in it, and the server logs `Cannot find scheme for class ...JavadocSettings, scheme has to be generated at runtime`
  (`SharedConfigurationFacade.kt`, line 156) on every start and serves a schema from the runtime generator, which is not the one served today.

Both break the drop-in contract: an existing installation would see a new warning on every start and a different settings schema. The
decision, the same as `extract-cleanup-plugin` D13:

- **The module generates both at build time, as the server does.** The module applies `kapt` with `openapi-annotation-processor`. Its build
  renames the processor's `openapi-plugin/openapi-default.json` to `META-INF/ingot/openapi/javadoc.json` (the plugin id) and drops its
  `openapi-plugin/.index` and `json-schemes/index`, so the server's indexes stay the only ones on any classpath, including the flat
  integration classpath of task 3.2. The `json-schemes/<class name>` files keep their names, which are unique per class.
- **The server merges the fragments of loaded plugins into the served document.** The definition processor of `configureOpenApi` adds the
  `paths` and `components.schemas` of `META-INF/ingot/openapi/<id>.json` for every registered plugin id, read through the plugin's own class
  loader and, for the flat test classpath, through the server's. An entry whose key the server document already has is not replaced, and the
  start logs one WARN line that names the plugin and the key. The merge runs on the first request, after every plugin has loaded.
- **The server looks up a domain's schema in the plugin's own jar before the runtime generator.** For a domain type that the server's index
  does not list, and whose class loader is not the server's, `SharedConfigurationFacade` reads `json-schemes/<type name>` through
  `type.classLoader`, which is the plugin jar's own loader. Only when that also fails does it log the warning and generate the schema at
  runtime, as today. Classes on the server's own loader keep today's path, so the tests, whose empty `json-schemes/index` keeps them on the
  runtime generator, do not change behaviour.
- **The committed snapshot stays the full merged document.** `docs/openapi/ingot-openapi.json` is not refreshed by the move pull request,
  and `OpenApiSnapshotIntegrationTest` passing unchanged keeps guarding the drop-in contract.

The server part (the merge and the schema lookup, with their tests against a fixture plugin) is shared with `extract-cleanup-plugin` and is
generic to every plugin jar, so neither extraction owns it. It is a new small change `add-plugin-api-fragments` (`feat(plugin)`), a follow-up
of `add-embedded-plugin-jars`, proposed in its own proposal pull request. Its merge on main is a gate of the move pull request (task 2a.1).
This change owns the module side: the build step above and the guard tests of group 2a.

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
1a. The guard test of D13 (task group 2a), green on main. The move waits for `add-plugin-api-fragments` as well.
2. The module, the move of the sources and the tests, the build wiring of D1, the embedded jar and the removal of the run configuration.
   Renames dominate the diff, so the pull request stays near 400 changed lines.
3. The frontend fallback of D5 and its tests.
4. The docs of the module, the embedded copy and the update path.

## Open questions

- **Q112. The unit tests of the viewer cannot move as D7 says.** Raised by the maintainer as question 112, not decided yet.
  `specification/JavadocSpecification.kt` extends `MavenSpecification` (`reposilite-backend/src/test/kotlin/com/reposilite/maven/specification/`),
  which is `internal` and builds its fixture from backend `internal` types (`MavenComponents`, `InMemoryMirrorCacheLedger`,
  `RecordingArtifactIndexSink` and others). Kotlin `internal` is visible only inside the module that declares it, so the four unit tests of D7
  do not compile in the module's test source set. The options:
  1. **Keep the unit tests in the backend.** `JavadocSpecification` and the four tests stay in `reposilite-backend/src/test/`, and the
     backend's test source set depends on the module. The move pull request stays small, but the tests no longer move with the code, and the
     core gains a test dependency on a plugin module.
  2. **Move the shared fixture into the backend's test fixtures.** `MavenSpecification` and what it needs move into the existing
     `testFixtures` source set of `reposilite-backend`, and the `internal` types it touches become public there or are reached through a
     public fixture factory. The module's tests use `testImplementation(testFixtures(project(":reposilite-backend")))`. The tests move with the
     code, at the cost of a wider fixture surface that other plugin modules can also use.
  3. **Rewrite the viewer's tests against public API.** The module gets its own small specification built only from public types, or the
     cases become integration tests over the HTTP surface of the viewer. Nothing in the backend changes, but the tests are rewritten rather
     than moved, so the move pull request grows and the "no assertion changes" promise of D7 no longer holds.

  Until it is decided, task 3.3 cannot move the unit tests, and group 3 does not start.

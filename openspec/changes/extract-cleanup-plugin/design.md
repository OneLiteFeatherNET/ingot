# Design

This design depends on the prerequisite change `add-embedded-plugin-jars` (`feat(plugin)`), which must be merged before the code pull
requests start. It shares that prerequisite with `extract-javadoc-plugin`.

## Context

- The cleanup feature is `reposilite-backend/src/main/kotlin/com/reposilite/cleanup/`: planner, inventory, scheduler, service, endpoints,
  the run table `cleanup_run`, and the plugin `CleanupPlugin`, registered by
  `reposilite-backend/src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin`. Its classes are on the server classpath.
- Loader facts, read from `reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt` and `Extensions.kt`:
  - The plugins directory is loaded in a `URLClassLoader` whose parent is the server class loader, and parent-first. A class that the
    server jar contains is resolved from the server. A newer copy in a plugins-directory jar cannot replace it, so a replacement needs its
    own class loader, which is what `add-embedded-plugin-jars` provides.
  - `Extensions.registerPlugin` uses `check(!plugins.containsKey(name))`, so a second plugin with the same id fails the start today.
  - Plugin versions are not read today. `@Plugin` has `version` with a default of the server version, but the loader does not compare it.
    The prerequisite reads the manifest `Implementation-Version` of the jar instead, as the javadoc proposal does.
- Cleanup reaches the core by plugin id in one place, `ClusterPlugin`'s `@Plugin(dependencies)`, which names `"cleanup"`. A dependency on an
  id needs no class on the server classpath. It resolves when a plugin with that id is registered, and the embedded copy always is.
- `prometheus-plugin`'s `FacadeOperationalSource` imports `com.reposilite.cleanup.CleanupService` and calls `lastScheduledRunSucceededAt()`.
  This is a compile dependency of one plugin on another plugin's class, and it must go.

## Decisions

### D1. The cleanup plugin is a module with its own version and jar, embedded in the server

`reposilite-plugins/cleanup-plugin` is a Kotlin JVM project with `version = "1.0.0"`, overriding the subproject default. The line carries the
marker `// x-release-please-version` so that the follow-up change can bump it. It compiles against `reposilite-backend` with `compileOnly`,
like the other plugin modules, and its tests use `testImplementation` on the backend.

Its shadow jar `cleanup-plugin.jar` declares `Implementation-Version` in its manifest, which the prerequisite reads. Its Maven publication
`net.onelitefeather.ingot:cleanup-plugin` is built locally in this change and uploaded only by the follow-up change.

The backend `shadowJar` takes that jar through the hook of `add-embedded-plugin-jars`, which places it at `embedded-plugins/cleanup-plugin.jar`
inside `ingot.jar`. The cleanup classes are not on the server classpath, so they load only through the nested jar, in their own class loader.
The build order is `cleanup-plugin:shadowJar` before `backend:shadowJar`, and no task depends the other way, so Gradle sees no cycle.

### D2. Packages, plugin id and file names do not change, and the move is one pull request

The package stays `com.reposilite.cleanup`, the plugin id stays `cleanup`, and the file names stay. `git mv` moves each file, so a reviewer
sees renames.

The move cannot be split into partial pull requests. A class that stays on the server classpath while its neighbours move would be loaded
from the server, not from the nested jar, and the replacement rule would apply to half the feature. The sources, the unit tests, the
integration tests, the service entry and the build wiring therefore land in one pull request, whose renames count little in the line count.

Kotlin `internal` is per Gradle module. Every `internal` declaration that the backend or the prometheus plugin reaches is listed by task 0.4,
and each one is either made public in the move pull request or reached through the seam of D3. No class is made public for another reason.

Verified (task 0.4): the `internal` declarations of `reposilite-backend/src/main/kotlin/com/reposilite/cleanup` are
`InMemoryCleanupRunRepository`, `CleanupRunTable`, `SqlCleanupRunRepository`, `CleanupCatchUp`, `CleanupEndpoints`, `CleanupScheduler`,
`CleanupPlugin`, `CleanupTarget`, the constructor of `CleanupService`, `CleanupService.runScheduledTick` and `CleanupService.runScheduled`,
`CleanupInventory` and `CleanupPlanner`. The prometheus plugin reaches none of them: on main it names no class of the package, since its
`FacadeOperationalSource` reads `JobLastSuccessSource` (task 1.2 appears to be in place). The backend reaches the package in three places:
the public enum `CleanupRunStatus` in `net/onelitefeather/ingot/jobs/JobStateMapping.kt`, its test `JobStateMappingTest.kt`, and
`runScheduledTick` in `WorkerTwoInstanceCleanupIntegrationTest`, which moves with the module. The enum is public and needs no visibility
change, but the core `JobStateMapping.cleanup` function cannot stay in the server once the classes leave it. Group 2 does not list that
mapping, so the move pull request must either move the mapping with the module or give the enum a home in the core. This is a gap in
`tasks.md` that the move pull request has to close.

The other direction is not covered by that list: the cleanup code reaches two `internal` declarations of the core. `CleanupPlugin` calls
`MavenFacade.mirrorCacheEntries` for the mirror copies it may evict, and builds `SqlScheduledJobClaims` for the claims of its scheduled runs.
The draft move (pull request #895) made both public. They stay `internal`, and the cleanup plugin reaches them through the interface of D14.

### D3. The core seam for the job status lives in the core

`FacadeOperationalSource` must not name a cleanup class. The core declares a public interface in `net.onelitefeather.ingot.deploy`, next to
`BackgroundJobGate` and `SingletonJob`:

```kotlin
interface JobLastSuccessSource : Facade {
    val job: String           // "cleanup"
    fun lastSuccessAt(): Instant?
}
```

The cleanup service implements it and registers it as a facade. `FacadeOperationalSource.jobLastSuccess()` collects every facade of that type
and produces the map entry for each `job`. The audit purge keeps its entry from `AuditFacade`.

The interface is core API, not plugin API, so it is not in `pluginApiPackages` and the plugin API dump does not change. The metric name,
the label values and the rule that omits a series without a value stay the same. When the embedded copy is absent, no `cleanup` series is
exported, which is what happens today when the facade is missing. The prometheus plugin then compiles without any cleanup class.

This seam is the first pull request of the code, before the move, so each step builds on its own.

### D4. The settings domain keeps its place

The plugin keeps `settings = CleanupSettings::class`, so it registers the `cleanup` domain as before. The Administration tree keeps the
domain under Artifacts through the static entry `{ domain: 'cleanup', ... }` of `reposilite-frontend/src/navigation/registry.ts`. The
Maintenance page keeps its Cleanup policies panel. The dashboard does not change.

The domain stays keyed by `/settings/<domain>`, so `/settings/cleanup` keeps its address. Without the embedded copy, the server does not list
the domain, the Administration tree hides the entry (`showsDomains && domains.includes(...)`), and the address shows the dashboard's state
for an unknown domain. Task 0.3 records that state.

Verified (task 0.3): `/settings/:domain?` (`reposilite-frontend/src/router/routes.ts`) checks no domain. `SettingsView.vue`
(`activeDomain`, lines 111-114) falls back to the first listed domain when the requested one is not listed, and its watcher (lines 234-235)
reports the change, so `IndexPage.vue` (`setDomain`, lines 341-342) replaces the address with `/settings/<first listed domain>`. The
result is not an error page and not the `/settings` hub, so the `/settings` redirect of group 3 is not required by D4's error branch.
With no domain listed at all, the page shows an empty view.

### D5. Without the embedded copy, cleanup is absent and nothing else breaks

Cleanup is active in every build of the server jar. An operator cannot take it out with a file, and the only removal is a build without the
embedded jar (decision O3). In such a build:

- No cleanup run starts, no cleanup route answers, and the `cleanup` settings domain is not listed.
- The stored `cleanup` domain in `configuration.shared.json` is kept untouched, provided the shared configuration keeps domains it does not know.
  Task 0.2 verifies that.
- The `cleanup` dependency of the cluster plugin is not satisfied, so the loader stops with `Unknown dependency 'cleanup'`. Dropping that entry
  is a follow-up change, which this change does not make. This change does not touch the cluster plugin.

Verified (task 0.2): the load path keeps an unclaimed row. `SharedConfigurationFacade.loadSharedSettingsFromString`
(`reposilite-backend/src/main/kotlin/com/reposilite/configuration/shared/SharedConfigurationFacade.kt`, lines 190-197) applies only the
names of the registered domains, and the file is not rewritten on load. The write path does not keep it: `renderConfiguration` (lines
489-497) renders only the registered domains, and `storeRendered` (line 466) writes that rendering on every save of any domain. So a stored
`cleanup` row survives until the next save of any other domain, and is then removed. In a build without the embedded jar that is the
configured cleanup policy, so D5's claim that the row is kept untouched holds only until that save. Question 3 of `proposal.md` covers
this, and the choice is for the maintainer.

### D6. Routes, events, audit, metrics, worker target and data do not change

- **Routes.** `/api/cleanup/policies/{id}/dry-run`, `/api/cleanup/policies/{id}/run` and `/api/cleanup/runs` keep their paths and annotations,
  and the OpenAPI snapshot does not change.
- **Settings.** The key `cleanup`, the `CleanupSettings` and `CleanupPolicy` shapes, the validation and the schema stay the same. The
  `configuration.shared.json` content does not change.
- **Cluster events.** Cleanup publishes none. It takes leases in `job_leases` through `ScheduledJobClaims`, which is core.
- **Audit.** `AuditAction.CLEANUP_RUN` keeps its name and its detail keys (`runId`, `policy`, `scope`, `trigger`, `status`, `deletedCount`,
  `deletedBytes`). Per-deletion events stay suppressed through `cleanupRunId` on the core delete event.
- **Metrics.** `job="cleanup"` keeps its name (D3).
- **Worker target and background jobs.** The schedule still runs through `BackgroundJobGate` with `SingletonJob.CLEANUP_SCHEDULE`, which stays
  in the core. A server with `INGOT_BACKGROUND_JOBS` off records no submission, and a worker records it.
- **Run table and switches.** `cleanup_run` keeps its name and columns, created by the plugin with `SchemaUtils.create`. `INGOT_CLEANUP_CATCHUP`
  and `REPOSILITE_CLEANUP_CATCHUP` keep their names and precedence.

### D7. Tests move with the code, the seam test stays in the core

The unit tests `CleanupPlannerTest`, `CleanupInventoryTest`, `CleanupSettingsTest`, `CleanupCatchUpSettingTest`, `CleanupServiceTest` and the
other `Cleanup*Test` files in `reposilite-backend/src/test/kotlin/com/reposilite/cleanup/` move into the module's test source set, in the same
package, so they keep their access to `internal` declarations. The integration tests in `reposilite-backend/src/integration/kotlin/com/reposilite/cleanup/`
and `WorkerTwoInstanceCleanupIntegrationTest` move into the module's integration source set. The core tests that name the settings class,
`SettingsIntegrationTest` and the OpenAPI snapshot test, change only their imports. The seam test of group 2 stays in the core, because it tests
the core interface and the prometheus facade.

Each test follows F.I.R.S.T.: no sleeps, no system clock, no shared static state, and a `@TempDir` per test.

### D8. The tier is Core by origin

The embedded copy is built into the server, so `add-embedded-plugin-jars` reads its origin as `EMBEDDED`, which the tier model reads as Core.
This change adds no `tier` element. A replacing jar from the plugins directory keeps the tier its declaration gives it (see O1).

### D9. The update rule

A jar in the plugins directory with the id `cleanup` replaces the embedded copy only when its manifest `Implementation-Version` is strictly higher
than the embedded copy's. The rule is implemented by the prerequisite, and this change relies on it:

- Equal or lower version, or no version: the embedded copy is used, and the start logs one INFO line that names both versions and the one in use.
- Strictly higher version: the plugins-directory jar is used, and the same INFO line names both versions.
- Two plugins with the id are never registered.

The rule applies to the cleanup plugin only. Other plugin ids keep the rule of the plugins directory as it is today.

### D10. The image keeps its file list

The released jar is not copied into `/app/bundled-plugins`. The embedded copy gives the image cleanup, and a second copy with the same id would add
a second precedence path. `.dockerignore` allow-lists `!reposilite-plugins/cleanup-plugin`, because `ingot.jar` now depends on the module and the
build context would otherwise lack it. The `Dockerfile` does not change.

Verified (task 0.5): the image entrypoint (`entrypoint.sh`) prepends `--working-directory=/app/data` unless `INGOT_OPTS`
or `REPOSILITE_OPTS` already names `--working-directory` or `-wd`, and passes the options on both the root and the non-root branch. The
plugins directory defaults to `<working directory>/plugins` (`ReposiliteParameters.kt`, line 170), which is `/app/data/plugins` in the
image. The embedded copies extract into `<working directory>/embedded-plugins`, which is `/app/data/embedded-plugins`. The operator notes
of group 3 name that path, and name `/app/data/plugins` as the place of a newer jar. `/app/bundled-plugins` is a separate default constant
of `PluginLoader.kt`. The loader that reads `embedded-plugins` is not on main yet (see the gate of task 0.1), so the path is taken from the
prerequisite's design.

### D11. Release is a follow-up

Publishing the jar, its catalogue entry and a release-please component for the module with its own tag belong to `add-cleanup-plugin-release`
(`ci(plugin)`). This change ends with a locally built Maven artifact and a jar that the server embeds.

### D12. Rejected: the cleanup classes in the server jar, and a bundled jar in the image

- Classes in the server jar, with no nested jar. A newer copy in the plugins directory could not replace them, because the parent loader is
  parent-first. The update path would not work.
- A separate jar in `/app/bundled-plugins`. Its switch would need a default of on, because a missing switch is off for every bundled jar. A
  server jar started outside the image would lose cleanup, because `/app/bundled-plugins` does not exist there.

### D13. Embedded plugins carry their API document fragment and settings schema

Found on the draft move pull request #895: the server builds two things at compile time from its own classes only, and neither sees a
plugin that loads from its own jar.

- **The API document.** `kapt` runs `openapi-annotation-processor` on the backend's main source set only (`reposilite-backend/build.gradle.kts`,
  the exclusion of `kaptTest`, `kaptIntegration` and `kaptTestFixtures`). It writes `openapi-plugin/.index` and
  `openapi-plugin/openapi-default.json` into the server's resources. `JavalinConfiguration.configureOpenApi` registers Javalin's
  `OpenApiPlugin`, whose `OpenApiLoader` reads that index through one class loader (`resourceClassLoader`, by default the loader of
  `OpenApiLoader`, which is the server's) on the first request to `/openapi`. The same document feeds the Swagger UI and the committed
  snapshot `docs/openapi/ingot-openapi.json`, which `OpenApiSnapshotIntegrationTest` compares with the served one. With cleanup in its own
  jar, the `@OpenApi` routes of `CleanupEndpoints` (`/api/cleanup/runs`, `/api/cleanup/policies/{id}/run`, `/api/cleanup/policies/{id}/dry-run`)
  and their schemas leave the document, and the snapshot test fails.
- **The settings schema.** The same processor writes `json-schemes/index` and one file `json-schemes/<class name>` per `@JsonSchema` class,
  here `CleanupSettings`. `SharedConfigurationFacade` reads them with `JsonSchemaLoader`, which calls
  `JsonSchemaLoader::class.java.getResourceAsStream("/json-schemes/index")`, so only the server's index is seen. A domain that is not in it
  logs `Cannot find scheme for class ...CleanupSettings, scheme has to be generated at runtime` (`SharedConfigurationFacade.kt`, line 156)
  on every start and is served a schema from the runtime generator, which is not the one served today.

Both break the drop-in contract: an existing installation would see a different API document and a new warning on every start. The
decision:

- **The module generates both at build time, as the server does.** The module applies `kapt` with `openapi-annotation-processor`. Its build
  renames the processor's `openapi-plugin/openapi-default.json` to `META-INF/ingot/openapi/cleanup.json` (the plugin id) and drops its
  `openapi-plugin/.index`, so the server's index stays the only one on any classpath, including the flat integration classpath of task 2.2.
  The `json-schemes/<class name>` files keep their names, which are unique per class. The module's `json-schemes/index` is dropped for the
  same reason.
- **The server merges the fragments of loaded plugins into the served document.** The definition processor of `configureOpenApi` adds the
  `paths` and `components.schemas` of `META-INF/ingot/openapi/<id>.json` for every registered plugin id, read through the plugin's own class
  loader and, for the flat test classpath, through the server's. An entry whose key the server document already has is not replaced, and the
  start logs one WARN line that names the plugin and the key. The merge runs on the first request, after every plugin has loaded, as the
  document is built today.
- **The server looks up a domain's schema in the plugin's own jar before the runtime generator.** For a domain type that the server's index
  does not list, and whose class loader is not the server's, `SharedConfigurationFacade` reads `json-schemes/<type name>` through
  `type.classLoader`, which is the plugin jar's own loader. Only when that also fails does it log the warning and generate the schema at
  runtime, as today. A domain of an embedded or a plugins-directory jar that ships its schema is therefore served the compile-time schema
  with no warning. Classes on the server's own loader keep today's path, so the unit and integration tests, whose empty `json-schemes/index`
  keeps them on the runtime generator, do not change behaviour.
- **The committed snapshot stays the full merged document.** `docs/openapi/ingot-openapi.json` keeps the cleanup routes and schemas, and
  the move pull request does not refresh it. `OpenApiSnapshotIntegrationTest` passing unchanged is the proof that the move kept the API
  document, so the test keeps guarding the drop-in contract.

The server part (the merge and the schema lookup, with their tests against a fixture plugin) is shared with `extract-javadoc-plugin` and is
generic to every plugin jar, so neither extraction owns it. It is a new small change `add-plugin-api-fragments` (`feat(plugin)`), a follow-up
of `add-embedded-plugin-jars`, proposed in its own proposal pull request. Its merge on main is a gate of the move pull request (task 1a.1).
This change owns the module side: the build step above and the guard tests of group 1a.

### D14. The cleanup plugin reaches the core through one narrow interface

Decided by the maintainer (question 115). The move must not make `MavenFacade.mirrorCacheEntries` or `SqlScheduledJobClaims` public. Both
are core internals, and making them public would turn them into API by accident, since every public member of the server is something a
plugin can compile against. Read on main and in the draft move (pull request #895), the cleanup plugin needs exactly two things from them:

1. **The mirror copies of a repository for eviction.** `CleanupPlugin` builds each `CleanupTarget` with
   `cachedVersions = mavenFacade.mirrorCacheEntries(repository.name)`: the version directories that hold mirror copies, with their last
   access. Only these versions can be cleaned up as copies.
2. **The claims of scheduled jobs.** `CleanupPlugin` builds one `SqlScheduledJobClaims(reposilite().database, clock)` and hands it to the
   service and the scheduler, so the day lease and the run lease have one owner and a run is started by one instance of a cluster. The
   interface `com.reposilite.scheduling.ScheduledJobClaims` is already public. Only the database-backed implementation is internal.

The core declares one interface for these two needs, and nothing else:

```kotlin
package net.onelitefeather.ingot.cleanup

@ExperimentalIngotPluginApi
interface CleanupSupportFacade : Facade {
    /** The version directories that hold mirror copies of [repository], with their last access. */
    fun mirrorCacheEntries(repository: String): Map<String, Instant>

    /** A claim store shared by every instance through the server's database, which reads the time from [clock]. */
    fun scheduledJobClaims(clock: Clock): ScheduledJobClaims
}
```

- It is in `net.onelitefeather.ingot.*`, because it is new Ingot API, and it carries `@ExperimentalIngotPluginApi`, because it exists for
  one plugin and may change with it.
- It is listed in `reposilite-backend/plugin-api-classes.txt`, so the slim plugin API jar holds it and the closure check guards it. The
  types its signatures name are `com.reposilite.*` types the server provides (`Facade`, `ScheduledJobClaims`) and JDK types, so no further
  entry is needed.
- The core implements it with `MavenFacade.mirrorCacheEntries` and `SqlScheduledJobClaims`, which both stay `internal`, and the maven plugin
  registers it next to `ViewerAccessFacade`. The cleanup plugin already depends on `maven`, so the load order does not change.
- `CleanupPlugin` reads it with `facade<CleanupSupportFacade>()` and no longer names `MavenFacade.mirrorCacheEntries` or
  `SqlScheduledJobClaims`. Run behaviour, the claim keys and the tables do not change.

This interface lands in its own pull request before the move (task group 1b), with its tests first, while cleanup is still in the core, so
the move pull request changes no visibility of the core for cleanup's main code.

## Risks

- **The prerequisite is a dependency.** If it slips, this change slips. The move pull request cannot start before it merges.
- **Internal visibility across modules.** The compiler finds each use (task 0.4). The fix stays small and is part of the move pull request.
  The two core internals that cleanup needs are reached through D14, which lands before the move, so the move makes neither public.
- **Load order.** The dependencies of the plugin (`shared-configuration`, `maven`, `read-only`) do not change, so the initialisation order stays.
- **Release drift.** Until `add-cleanup-plugin-release` merges, the module's jar ships only inside the server. The update rule works for a jar
  that an operator or the follow-up places.

## Migration plan

Each step is one pull request, buildable and passing alone:

0. The prerequisite `add-embedded-plugin-jars` is merged (outside this change).
1. The seam of D3 and its test. The prometheus plugin stops importing the cleanup service, which is still in the core.
1a. The guard tests of D13, green on main and red on the draft move. The move waits for `add-plugin-api-fragments` as well.
1b. The interface of D14 and its tests. Cleanup, still in the core, stops naming `MavenFacade.mirrorCacheEntries` and `SqlScheduledJobClaims`.
2. The module, the move of the sources and the tests, the build wiring of D1 and the embedded jar. This pull request is the single move of D2.
3. The docs of the module, the embedded copy and the update path.
4. The replacement test of group 7, which is written after the prerequisite and passes against it.

## Settled questions

Settled on 2026-11-17: the maintainer accepted the default of each question below, and the tasks follow it.

- **O1. Tier of a replacing jar.** Until the release change declares a tier, a jar in the plugins directory without a tier element reads as
  Community, and the start log shows `cleanup (community)` after an update, with the server's embedded copy still Core. Settled 2026-11-17, default: the release
  change `add-cleanup-plugin-release` decides, and the module keeps no tier element until then.
- **O2. Received cache events and the cluster dependency.** The cluster plugin names the id `cleanup`. Settled 2026-11-17, default: the entry stays in this change, because the embedded copy is not removed.
- **O3. Removing the embedded copy.** An operator cannot take the embedded copy out with a file. Options: a switch such as
  `INGOT_CLEANUP_ENABLED=false` read by the loader, or a server build without the embedded jar. The second needs the cluster entry of O2
  removed first. Settled 2026-11-17, default: no switch in this change, and the embedded copy is not removed by this change.
- **O4. Development runs.** Runs from an IDE or `./gradlew run` use the server classes, which contain no cleanup classes, so cleanup needs the
  nested jar in `embedded-plugins/` or the module jar in the plugins directory of the workspace. Settled 2026-11-17, default: the integration task places the module
  jar into the workspace plugins directory, as the other plugin modules do, and the IDE setup is unchanged.

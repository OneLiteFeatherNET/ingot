# Proposal

## Premise check

The maintainer asked for cleanup policies to move out of the core into a plugin that can be fixed and released faster than the core
release cycle. Four facts from the code shape the answer:

- `storage-cache-plugin` is not in the image. `.dockerignore` names it only for the integration tests, so it is not a model for
  "bundled by default".
- The loader resolves plugins from a `URLClassLoader` whose parent is the server class loader, which is parent-first. A class that the
  server jar contains wins over the same class in a plugins-directory jar. Cleanup classes on the server classpath therefore cannot be
  replaced by a newer jar. The embedded copy must be a nested jar that the loader opens in its own class loader.
- `Extensions.registerPlugin` fails on a second plugin with the same id, and no plugin declares a version the loader reads today.
- Cleanup classes are reached by one other plugin, `prometheus-plugin`, which imports `CleanupService` at compile time.

## Decisions taken with the maintainer

1. **Settings placement.** Cleanup policies stay under Artifacts in the administration navigation, at `/settings/cleanup`. The address
   does not change, so no redirect is needed.
2. **Packaging.** The cleanup plugin is its own Gradle module with its own published jar and its own version (`1.0.0`, independent of the
   server version). The server jar embeds a copy of that jar as `embedded-plugins/cleanup-plugin.jar`. The embedded copy keeps cleanup
   active by default, also for a server jar started outside the image. A jar with the same plugin id in the plugins directory replaces
   it only when its version is strictly higher.
3. **Image.** The image does not copy the released jar into `/app/bundled-plugins`. The embedded copy already covers the image, and a
   second copy with the same id would add a second precedence path. The file list of the image is unchanged. Only the content of
   `ingot.jar` changes.
4. **Release is a follow-up.** Publishing the jar, its catalogue entry and the release-please component are the change
   `add-cleanup-plugin-release` (`ci(plugin)`), which is out of scope here, as for the javadoc viewer.

## Prerequisite change: `add-embedded-plugin-jars` (feat(plugin))

The loader cannot embed a nested jar or apply the version rule yet. That work is one shared `feat(plugin)` change, `add-embedded-plugin-jars`,
which the javadoc proposal `extract-javadoc-plugin` also depends on. It merges before any code pull request of this change starts:

- The loader reads the nested jars of the server jar (`embedded-plugins/*.jar`), extracts each one to `<workingDirectory>/embedded-plugins/`,
  and loads it in its own class loader, as the plugins directory is loaded. The origin of such a plugin is `EMBEDDED`, which the tier model
  reads as Core.
- A jar of the plugins directory with the same plugin id as an embedded jar wins only when its manifest `Implementation-Version` is strictly
  higher. Otherwise the embedded copy is used. One INFO line names both versions and the one in use. A jar without a version counts as
  lowest. Two plugins with one id are never registered.
- The Gradle build can place a named module jar into `embedded-plugins/` of the server jar. With no jar named, the change has no effect.

The prerequisite is not a group of this change, because a change has one type.

## Why

Cleanup policies have their own settings domain, routes, scheduler, run table and metric, and they are the largest feature still in the
core package `com.reposilite.cleanup`. Every fix to the retention rules or the run history waits for a server release. A module with its own
version and jar lets the feature be fixed on its own cycle. The embedded copy and the version rule keep it working for every existing
installation, so the update path is "drop a newer jar into the plugins directory", which is opt-in.

The move must not change what an operator sees. Ingot is a drop-in replacement for Reposilite and for earlier Ingot releases. The settings
key, the routes, the audit actions, the metric names, the worker behaviour and the dashboard stay the same.

## What Changes

- **A Gradle module `reposilite-plugins/cleanup-plugin`** with package `com.reposilite.cleanup` and plugin id `cleanup`, both unchanged.
  Its sources move with `git mv`. It has `version = "1.0.0"` with the marker `// x-release-please-version`, a shadow jar `cleanup-plugin.jar`
  with `Implementation-Version` in its manifest, and a Maven publication `net.onelitefeather.ingot:cleanup-plugin` that is built locally in
  this change. It compiles against `reposilite-backend` with `compileOnly`.
- **The server jar embeds the module's jar** as `embedded-plugins/cleanup-plugin.jar`, through the hook of the prerequisite. No cleanup class
  is on the server classpath, which the test of group 4 checks.
- **A core seam for prometheus.** The job interface `JobLastSuccessSource` lives in the core, in `net.onelitefeather.ingot.deploy` next to
  `BackgroundJobGate`. The cleanup service implements it, and `FacadeOperationalSource` reads it through the facades. `prometheus-plugin`
  no longer imports any cleanup class. The metric name and labels do not change.
- **Settings and placement.** The `cleanup` domain keeps its key, fields and defaults, and its place under Artifacts. The static dashboard
  entry is unchanged, and no dashboard code changes.
- **Docs.** A GitBook page describes the module, the embedded copy and the update path.

### Out of scope

- The prerequisite `add-embedded-plugin-jars`, which is its own `feat(plugin)` change.
- The release of the cleanup jar, its catalogue entry and its release-please component (`add-cleanup-plugin-release`, `ci(plugin)`).
- A new environment variable or switch for cleanup, and a build without the embedded copy (decision O3).
- Removing the `cleanup` entry from the dependencies of the cluster plugin (O3). The entry names the plugin id only, so it needs no cleanup class.
- A tier element on the module (the embedded copy is Core by origin, see O1).
- Changing routes, the JSON API, the settings schema, the run table `cleanup_run`, the audit action `CLEANUP_RUN`, the metric names, the
  singleton job id `cleanup-schedule` or the environment variables `INGOT_CLEANUP_CATCHUP` and `REPOSILITE_CLEANUP_CATCHUP`.
- Moving the dashboard panel. The Cleanup policies page and the panel on Maintenance stay in the core dashboard build.
- Renaming `com.reposilite.*` packages, the `reposilite-*` module names or `cleanupScheme` in the schema generator.
- Any change to `com.reposilite.journalist` or `org.panda_lang.*`, as set out in `CLAUDE.md`.

## Type and pull request

- Conventional Commits type and scope: `refactor(cleanup)` for the seam and the move, because they change structure and not behaviour.
  The commits inside one pull request have one type each: the failing test is `test(cleanup)`, the build file is `build(cleanup)`, the
  move is `refactor(cleanup)` and the docs are `docs(gitbook)`. Squash-merging makes the pull request title the single commit on main.
- The proposal pull request is `docs(openspec): propose extract-cleanup-plugin`.
- Code pull requests, in the order of `tasks.md`, each titled as its group names it.

## Operator impact

No operator must change anything. The environment variables, the configuration files, the volume, the manifest, the plugin list and the
image reference stay as they are. An unmodified upstream compose file still starts the server, and an existing `/app/data` is adopted. Cleanup
is active by default. A newer `cleanup-plugin.jar` in the plugins directory is the update path, and it is opt-in. No change is BREAKING.

## Capabilities

### New Capabilities

- `cleanup-plugin`: the module and its own version, the embedded copy and the version rule, the default, the settings placement, the
  prometheus seam, the behaviour without the embedded copy and the boundary to the core.

### Modified Capabilities

None. The behaviour of cleanup policies is specified by `cleanup-scheduling`, `repository-policy` and the existing settings specs, and none
of those requirements change.

## Impact

- **Modules.** A new Gradle project `:reposilite-plugins:cleanup-plugin` with its own version and publication. `:reposilite-backend` embeds its
  jar. `prometheus-plugin` loses its compile dependency on the cleanup classes.
- **Binary surface.** The plugin API is unchanged. The job interface is core API, not in `pluginApiPackages`, so the plugin API dump does not
  change.
- **Image.** `ingot.jar` changes its content, not its place. The file list of the image is unchanged.
- **Order.** The prerequisite merges first. Then the seam, the move, the docs, and the replacement test, each buildable and passing alone.

## Verification

- Unit tests run without network, database or system clock, with the injected `Clock` where time matters, as the cleanup tests do today.
- No class of `com.reposilite.cleanup` is loadable from the server class loader.
- The prometheus plugin's classpath contains no cleanup class.
- A jar of higher version in the plugins directory replaces the embedded copy, and a lower or equal one is ignored, with one INFO line each.
- An install that starts the new server with the default image and no plugins directory shows cleanup active.
- `npx -y @fission-ai/openspec@latest validate extract-cleanup-plugin --strict` passes, and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/extract-cleanup-plugin` prints nothing.

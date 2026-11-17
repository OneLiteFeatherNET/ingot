# Design

This change is the prerequisite of `extract-javadoc-plugin` and of the cleanup change that follows it. Both name this change's
mechanism, the list `embeddedPluginProjects`, the directory `embedded-plugins/` and the origin `EMBEDDED`, so their names are
fixed here.

## Context

The loader as it is on `origin/main`, with the facts the design relies on:

- `PluginLoader.loadPluginsByServiceFiles` (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt:137-167`)
  opens every valid jar of the plugins directory in one `URLClassLoader` (line 156). That loader has no explicit parent, so its
  parent is the system loader, which holds the server classpath. Delegation is parent-first.
- Its `ServiceLoader` results are registered with origin `PLUGINS_DIRECTORY` when the jar's own service file names the class,
  and `BUILT_IN` otherwise (lines 145-146, 159-162). A duplicate id reaches `Extensions.registerPlugin`, whose `check` at
  `Extensions.kt:61` stops the start.
- The store loads next (`loadStorePlugins`, line 175). A store plugin whose id a plugins-directory jar holds is `SHADOWED`
  (line 215). The bundled jars load last (`loadBundledPlugins`, line 286), and a bundled plugin whose id is registered is skipped
  with a debug line (lines 312-313). Today the precedence is: plugins directory, store, bundled, and no jar replaces a built-in plugin.
- The origin is the enum `PluginOrigin` in `reposilite-backend/src/main/kotlin/net/onelitefeather/ingot/plugin/tier/PluginTierResolver.kt:22-34`.
  The resolver maps `BUILT_IN` and `BUNDLED_IMAGE` to Core, unless the declared tier is Experimental (lines 43-50). `Extensions`
  keeps the origin of each plugin by id (`Extensions.kt:45`, `:63`).
- The `@Plugin` annotation has `name`, `version` (default: the server version), `dependencies`, `settings` and `tier`
  (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/api/Plugin.java:32-59`). Only the tier is read by the loader.
- The plugins directory is `<workingDirectory>/plugins` by default (`ReposiliteParameters.kt:170`) and is built in
  `ReposiliteFactory.kt:193`. The image sets the working directory to `/app/data` (`entrypoint.sh:12`), which is a volume
  (`Dockerfile:77`).
- The backend shadow jar is configured at `reposilite-backend/build.gradle.kts:244`. `minimize` excludes some dependencies, and
  `duplicatesStrategy` is `INCLUDE`. A plugin module already wires a named jar with a configuration of its own
  (`nexusImportPlugin`, lines 417-428), and that is the pattern for the hook.

Facts from the sibling notes that this design corrects or keeps:

- The sibling notes say that no version is read from a plugin today. `@Plugin.version()` exists and defaults to the server version.
  The rule here reads the manifest instead, so the sibling's decision D9 (manifest `Implementation-Version`) is kept.
- The sibling's D8 (the viewer is Core by origin) is kept. Its D1 names the hook of this change, and its D9 names the rule.

## Decisions

### D1. The hook copies named module jars into `embedded-plugins/`

In `reposilite-backend/build.gradle.kts`:

```kotlin
val embeddedPluginProjects: List<String> = emptyList()
val embeddedPlugin by configurations.creating { isCanBeConsumed = false; isCanBeResolved = true; isTransitive = false }
dependencies { embeddedPluginProjects.forEach { add("embeddedPlugin", project(path = it, configuration = "shadow")) } }
tasks.withType<ShadowJar> {
    from(configurations.named("embeddedPlugin")) { into("embedded-plugins") }
}
```

The configuration is not on any classpath, so the backend's compile, runtime and integration classpaths do not change.
A `from` copies the file of the module's shadow jar as it is. It is not merged into the server, so its classes stay in
their own jar. The module is not a dependency of the server's runtime classpath, so there is no cycle: the plugin module
compiles against the backend with `compileOnly`, and the backend's shadow jar depends on the plugin's shadow jar. Task 0.3
confirms that Gradle reports no cycle.

A task `verifyEmbeddedPlugins` opens the built server jar and checks three things: each listed module has exactly one
entry `embedded-plugins/<jar name>`, its manifest attribute `Implementation-Version` is present, and the server jar has no
other `embedded-plugins/` entry. The pure check is a function in `buildSrc/src/main/kotlin/EmbeddedPluginJars.kt`, so its tests
run in `buildSrc` with a temporary jar and need no build of the server. The task is wired into `check` of the backend.

Docker: the build stage copies the repository through the build context. A sibling that names a module also keeps that module's
directory out of `.dockerignore` (the `!reposilite-plugins/<name>` line). This change names no module and adds no line.

### D2. The version is the manifest attribute `Implementation-Version`

A jar's version is the value of `Implementation-Version` in the main manifest of the jar. The `@Plugin.version()` element is not
used for the rule, for two reasons:

- Its default is the server version. A plugin that does not set it would read as the server version, which is higher than any
  independently numbered plugin (for example `1.0.0`), so the embedded copy would never be replaced.
- Gradle writes the manifest from the module's `version`, so the value cannot drift from the jar's name.

The annotation keeps its meaning, and the support bundle still reads it (`SupportPlugin.kt:88`). An embedded plugin sets it to the
same value as the manifest, which is the author's duty and is documented in task 5.3. Open question 2 covers a check of that.

### D3. Version grammar and comparison

A version is `MAJOR[.MINOR[.PATCH]]`, each part a decimal number, followed by an optional `-` pre-release suffix of letters,
digits, dots and hyphens, and an optional `+` build suffix that is ignored. A string that does not match is invalid.

The comparison works in this order. The three numbers compare numerically, a missing part counts as `0`, so `1.2` equals
`1.2.0` and `1.2.10` is higher than `1.2.9`. When the numbers are equal, a release is higher than a pre-release. Two pre-releases
of the same numbers are equal, so neither is strictly higher. A `-SNAPSHOT` version is a pre-release.

A missing, blank or invalid version counts as lower than every valid version. An invalid version never fails the start. The
parser lives in the new package `net.onelitefeather.ingot.plugin.version` as a pure object, so it needs no class loader.

### D4. Extraction directory, rewrite at each start, and cleanup

The loader lists the nested jars of the running server. The list comes from the code source of `PluginLoader`: a jar file is
enumerated for entries under `embedded-plugins/` that end in `.jar`, and a classes directory is enumerated for the same
files. No index file is written, so the build has no second thing to keep in step.

Each start does the following, before any nested jar is loaded:

- The directory `<workingDirectory>/embedded-plugins/` is removed recursively when it exists, whether or not the server jar has
  embedded jars. A removed copy of a plugin that the build no longer embeds does not stay on disk.
- When the server has at least one nested jar, the directory is created and each jar is written to a `<name>.jar.partial` file
  and then moved to `<name>.jar` with `ATOMIC_MOVE`. A partial file that a crash leaves behind is removed by the next start.
- When the server has none, nothing is created. This keeps the drop-in guarantee: an installation without embedded jars sees
  no new directory.

The extraction directory is a sibling of the plugins directory. It holds only files the loader writes, and its size is the sum of
the embedded jars, a few megabytes for each.

### D5. Read-only working directory and an unwritable extraction directory

The plugins directory is unchanged: when it cannot be created, the start stops as it does today. The extraction directory has
its own rule, because the loader can still run when only that directory is read-only.

1. Extract into `<workingDirectory>/embedded-plugins/`.
2. When that fails with an `IOException`, extract into `<java.io.tmpdir>/ingot-embedded-plugins-<hash>`, where `<hash>` is the
   first twelve hexadecimal digits of the SHA-256 of the absolute working directory. The same directory is reused and rewritten
   at each start, so the fallback does not accumulate directories. One WARN line names both paths and the reason.
3. When the fallback fails too, the jar is not loaded. One ERROR line names the jar and both reasons, and the start goes on.
   The features of that plugin are then absent, and the dashboard shows what it shows for a missing plugin (for the viewer,
   the Javadoc links are hidden by the sibling's frontend rule).

The fallback root is a constructor parameter, so the tests pass a temporary directory and never touch the real temporary
directory. Stopping the start in step 3 was rejected: an upgrade would turn a working installation into a failed start, for an
embedded plugin the operator never configured.

### D6. Each embedded jar has its own class loader, and only its declared classes are instantiated

For each extracted jar, the loader opens `URLClassLoader(arrayOf(url), ReposilitePlugin::class.java.classLoader)`, the same parent
as the plugins directory. The plugin classes are read from the jar's own service file, as `declaredStorePlugins` already does
(`PluginLoader.kt:247-258`), so a lookup does not return the parent's built-in plugins. The shared helper that reads the names
is extracted in pull request 3 with no behaviour change, and the store path uses it too.

Each embedded loader is added to `pluginClassLoaders`, so the storage providers of the embedded jar are discovered with the
others (`loadPlugins`, line 121).

### D7. Precedence between an embedded copy and a plugins-directory jar

Embedded jars are read before the plugins directory is scanned, and their candidates are held without registration. The scan of
the plugins directory then decides each plugin whose id a candidate holds:

- A plugins-directory provider whose id is held by a candidate is compared with it by D3, using the manifest of the jar that
  declares the provider (the map of class name to jar that `declaredByJarsIn` builds, line 321).
- The provider's version is strictly higher: the provider is registered with origin `PLUGINS_DIRECTORY`, and the candidate is
  dropped.
- Otherwise: the candidate is registered with origin `EMBEDDED` at once, and the provider is not registered.
- In both cases exactly one INFO line is logged, in the form
  `Plugin <id>: embedded version <v>, plugins directory version <v>, the <plugins directory|embedded> copy is used`. A missing
  version shows `none`, and an invalid one shows the raw string in quotes.
- The decision removes the candidate from the held set, so a second plugins-directory provider with the same id meets an id that
  is registered and reaches the existing `check`. Two plugins-directory jars with one id therefore still stop the start with
  `Plugin <id> is already registered`, whatever the embedded copy's version.
- Candidates that no plugins-directory provider claims are registered after the scan, with origin `EMBEDDED`, before the store is loaded.

A provider that loses is still constructed by the `ServiceLoader`, because the lookup creates the instances. Its constructor runs
and its `initialize` and `load` do not. A plugin whose constructor has side effects is therefore a risk, and the documentation for
plugin authors says so (task 5.3).

The rule decides only for ids held by an embedded candidate. Every other id loads as today.

### D8. The origin `EMBEDDED` maps to Core

`PluginOrigin` gets the value `EMBEDDED`, documented as "a jar embedded in the server". `PluginTierResolver.resolve` maps it with
`BUILT_IN` and `BUNDLED_IMAGE`: Core, unless the declared tier is Experimental. A replacing jar of the plugins directory keeps
the origin `PLUGINS_DIRECTORY` and its declared tier, with the rule that a Core claim from outside reads as Community.

`PluginOrigin` is in `net.onelitefeather.ingot`, not in the plugin API, so the plugin API dump does not change.

### D9. Store and bundled rules are unchanged

- A store plugin whose id an embedded candidate holds is `SHADOWED`, as it is for a plugins-directory jar. The reason text
  "a jar in the plugins directory has the same plugin id" stays, which is slightly imprecise for an embedded copy. Changing the text
  is a small follow-up if a maintainer wants it.
- A bundled plugin whose id is registered is skipped, so an embedded plugin wins over a bundled copy of the same id.

### D10. Drop-in guarantee and plugin API

With no embedded jar the loader reads nothing new, creates no directory, logs no new line and registers the same plugins in the
same order. The existing tests (`BundledPluginLoadingTest`, `PluginIdCollisionTest`, `PluginTierLogTest` and the store tests) pass
unedited, which is a verification step of every code pull request.

`PluginTier`, the annotation `@Plugin`, `Extensions.registerPlugin` and the other public signatures stay as they are. The
plugin ABI gate of `plugin-abi-gate` runs unchanged.

## Risks

- **Every installation runs the new start path.** Mitigated by D10: the path with no embedded jar is the unchanged path, and a
  test pins the directory and the log as they are.
- **Read-only containers.** Mitigated by D5. An operator who wants no write at all to the container filesystem keeps the
  working directory on a volume, which is the image's default.
- **Two jars with the same file name.** The build fails when the list names two modules with the same jar name, because
  `verifyEmbeddedPlugins` requires one entry per name.
- **Losing constructors.** A plugin's constructor runs even when its copy loses (D7). Documented for authors.
- **Annotation and manifest versions differ.** The rule uses the manifest, and the support bundle shows the annotation (open
  question 2).
- **Tier check.** A module that declares Core would need a jar in the image (open question 5). The siblings declare no tier.
- **Slip of the sibling chain.** Both siblings wait for this change. Each pull request of this change stands alone, so the
  siblings can start on the merged parts.

## Migration plan

Each step is one pull request, buildable and passing alone, in this order:

1. `feat(plugin): add the embedded origin and the plugin version rule`. The origin, the version parser and the resolver rows.
   Nothing reads them yet, so the behaviour is unchanged.
2. `feat(plugin): embed a named plugin jar into the server jar`. The list, the configuration, the copy and `verifyEmbeddedPlugins`.
   With the list empty the server jar is unchanged, so the step can merge before the loader changes.
3. `feat(plugin): load plugin jars embedded in the server`. Extraction, cleanup, the fallback, the own loaders, the origin and the
   registration of candidates without a plugins-directory copy.
4. `feat(plugin): let a newer plugins directory jar replace the embedded copy`. The precedence, the INFO line and the operator page.

The sibling `extract-javadoc-plugin` starts its code pull requests only after pull request 4 has merged, and its group 0.1 checks that.

## Open questions

Each one is listed in `proposal.md` with the default that this design builds in. The maintainer decides before pull request 1.

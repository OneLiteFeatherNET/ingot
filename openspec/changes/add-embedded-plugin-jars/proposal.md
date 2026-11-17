# Proposal

## Why

Two sibling changes move a feature out of the core into a plugin: the Javadoc viewer (`extract-javadoc-plugin`) and the
cleanup work that follows it. A plugin that ships in the server jar can be updated only with a server release, which is the
cycle these changes are meant to get away from. A plugin that is only a jar in the plugins directory would break every
existing installation, because the default image would lose the feature until an operator copied the jar.

The loader has no way to ship a plugin inside the server and still let a newer jar replace it. The classes of an embedded
plugin would sit on the server classpath, where the parent-first class loader of the plugins directory always wins, so a
newer copy could never take over. The embedded plugin therefore has to be a nested jar that the loader opens in its own
class loader, and the loader needs a rule for when a jar of the plugins directory replaces it.

This change is the mechanism only. It embeds no plugin, so an installation sees no difference until a sibling change names
one. Ingot is a drop-in replacement for Reposilite: the existing plugins, the configuration, the volume and the image stay as
they are.

## Premise check

Three facts from the code shape the design:

- `@Plugin` already has an element `version()`, which defaults to the server version (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/api/Plugin.java:39-43`).
  The sibling notes say that no version is read from a plugin, and that is not accurate for the annotation. The annotation default
  would make an independently versioned plugin read as the server version, so the precedence rule reads the manifest instead
  (decision D2 of `design.md`).
- A service lookup on the plugins-directory loader also returns the built-in plugins of the parent loader
  (`reposilite-backend/src/main/kotlin/com/reposilite/plugin/PluginLoader.kt:145-146`). The loader already tells the two apart by
  reading the service files of the jars, and the embedded loader uses the same method.
- A second plugin with the same id in the plugins directory stops the start (`Extensions.kt:61`, test `PluginIdCollisionTest`).
  That stays true for two plugins-directory jars. The new rule only decides between an embedded copy and one plugins-directory
  copy.

## Type and scope

- Conventional Commits type and scope: `feat(plugin)`. The change adds a capability to the loader and changes nothing that an
  operator already uses, so it is a feature and not a `refactor`.
- The proposal pull request is `docs(openspec): propose add-embedded-plugin-jars`.
- The code ships as four pull requests, in this order. Each title starts with `feat(plugin):`, and each has about 400 changed
  lines. Inside a pull request the commits have one type each (a failing test is `test(plugin)`, the Gradle file is
  `build(plugin)`, the change is `feat(plugin)`, the operator page is `docs(plugin)`), and squash-merging makes the pull request title the one commit on main.
  1. `feat(plugin): add the embedded origin and the plugin version rule`
  2. `feat(plugin): embed a named plugin jar into the server jar`
  3. `feat(plugin): load plugin jars embedded in the server`
  4. `feat(plugin): let a newer plugins directory jar replace the embedded copy`

## What Changes

- **Build hook.** `reposilite-backend/build.gradle.kts` gets a list `embeddedPluginProjects`, empty in this change. Each listed
  module's shadow jar is copied into the server jar as `embedded-plugins/<jar name>`. A task `verifyEmbeddedPlugins` checks the
  result. With the list empty the server jar is unchanged. No plugin is embedded by this change, so it has no effect until a
  sibling change adds a path to the list.
- **Nested jars at start.** The loader reads the `embedded-plugins/*.jar` entries of the server jar, extracts each one to
  `<workingDirectory>/embedded-plugins/`, and loads it in its own class loader, with the same rules as a jar of the plugins
  directory. The extraction is rewritten at every start. A stale copy is removed.
- **Origin.** A plugin from an embedded jar has the origin `EMBEDDED`. The tier resolver maps it to Core, as it maps a built-in
  plugin. A declared Experimental stays Experimental.
- **Version rule.** The version of a jar is the manifest attribute `Implementation-Version`. A jar of the plugins directory with
  the id of an embedded plugin replaces the embedded copy only when its version is strictly higher. Otherwise the embedded copy
  is used. Exactly one INFO line names the id, both versions and the copy in use, whenever both copies exist.
- **Read-only working directory.** When the extraction directory cannot be written, the jars are extracted into a directory
  under the temporary directory of the JVM. When that fails too, the jar is skipped with one ERROR line and the start goes on.
- **Tests.** Unit tests for the version parser, the tier mapping, the extraction, the fallback, the loader and the precedence,
  each written first. No test reads the real temporary directory, the real network or the system clock.

### Out of scope

- Embedding any plugin. The Javadoc viewer and the cleanup work are separate changes that name their jar in the list.
- Releasing an embedded plugin on its own cycle, and the catalogue or CI check for it (`add-javadoc-plugin-release`).
- A new `@Plugin` element, a new environment variable or a configuration key. The rule reads only the jar manifest.
- Any change to the plugin API, its binary surface or the plugin API dump. `PluginTier` and `@Plugin` stay as they are.
- Changing how the plugins directory is created. A plugins directory that cannot be created stops the start as it does today.
- Checking that the annotation version of a plugin equals its manifest version (maintainer decision 2).
- Packages `com.reposilite.journalist` and `org.panda_lang.*`, as set out in `CLAUDE.md`.

## Operator impact

No BREAKING item. No environment variable, configuration key, volume, manifest, plugin list or image reference changes. An
unmodified upstream compose file still starts the server, and an existing `/app/data` is adopted. With no embedded jar the
loader creates no `embedded-plugins/` directory and writes no new line. An operator who runs the server with a read-only root
filesystem keeps working, because `/app/data` is a volume and the fallback uses the temporary directory. An operator who wants the
fallback to use a tmpfs mounts one at the temporary directory of the container. That is documented as an option and not set by
the image.

## Capabilities

### New Capabilities

- `embedded-plugin-jars`: the build hook, the nested jars of the server jar, extraction and its directory, cleanup, the
  read-only fallback, the own class loader, the origin `EMBEDDED` and the drop-in guarantee.
- `plugin-version-precedence`: how the version of a jar is read, how it is compared, the replacement rule, the one INFO line and
  the cases of a missing or invalid version.

### Modified Capabilities

None. The tier rules of `add-plugin-support-tiers` are extended by one origin, which this change states as an added requirement
rather than a modification of that change.

## Impact

- **Modules.** `reposilite-backend` only. `build.gradle.kts` of the backend, the loader package, the tier resolver and the
  factory that builds the loader. No plugin module changes.
- **Files on disk.** `<workingDirectory>/embedded-plugins/` exists only when the server jar carries embedded jars. Its content is
  rewritten at each start.
- **Image.** The image's file list is unchanged. `ingot.jar` changes its content only when a sibling embeds a jar.
- **Order.** This change is the prerequisite of the two siblings. Its four code pull requests merge in the order above, and each
  one is buildable and passing alone.

## Maintainer decisions

Settled on 2026-11-17: the maintainer accepted the recommended default of each question below. The default is the answer this proposal builds in. A later change of answer updates the matching requirement and task before the first code pull request is opened.

1. **Version source.** Settled 2026-11-17, default: the manifest attribute `Implementation-Version`. The alternative is the `version()` element of
   `@Plugin`, which needs the plugin author to set it and the build to keep it equal to the manifest. The annotation default is
   the server version, so an embedded plugin that did not set it would never be replaced.
2. **Annotation and manifest disagree.** Settled 2026-11-17, default: the loader does not compare them. The support bundle reads the annotation
   (`SupportPlugin.kt:88`), so an embedded plugin must set both. Checking it in `verifyEmbeddedPlugins` is not part of this change.
3. **Extraction fallback.** Settled 2026-11-17, default: the temporary directory of the JVM, keyed by the working directory, and then a skipped
   jar with one ERROR line. The alternative is to stop the start, which a read-only container would then hit after an upgrade.
4. **Where the list lives.** Settled 2026-11-17, default: a Kotlin list in `reposilite-backend/build.gradle.kts`. The alternative is a Gradle property,
   which a CI run could set without a commit.
5. **Tier check.** `add-plugin-support-tiers` says a module that declares Core needs a jar in the image. An embedded jar is not
   copied into `/app/bundled-plugins`. Settled 2026-11-17, default: the sibling changes declare no tier, so the check never sees the question. The
   tier check is extended in its own change if a maintainer wants embedded plugins to declare Core.
6. **Commit type of the build hook.** Settled 2026-11-17, default: the hook is a `build(plugin)` commit inside the `feat(plugin)` pull request, as
   the proposal says a change ships under one type. The alternative is a separate `build(plugin)` change.

## Verification

- Every unit test runs with `:reposilite-backend:test --tests '<pattern>'` through `heavy` and `--max-workers=2`, and no test
  needs the network, the real temporary directory or the system clock.
- `npx -y @fission-ai/openspec@latest validate add-embedded-plugin-jars --strict` passes.
- `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/add-embedded-plugin-jars` prints nothing.
- After pull request 2, `./gradlew :reposilite-backend:shadowJar` with the list empty produces a jar with no `embedded-plugins/`
  entry, and with a fixture module named produces exactly one.
- After pull request 3, a server started from a jar with an embedded fixture plugin shows the plugin with origin `EMBEDDED` and
  tier `core` on the start line.

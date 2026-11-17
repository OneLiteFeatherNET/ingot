# Proposal

## Premise check

The maintainer asked for the Javadoc viewer to move out of the core into a plugin that can be updated and fixed faster than the
core release cycle. Three facts from the code shape the answer:

- `storage-cache-plugin` is not in the image. Its own proposal says so, and `build.gradle.kts` only adds its compiled classes to the
  integration test classpath. It is not a model for "bundled by default".
- Every plugin module today takes its version from the root `projectVersion` (`build.gradle.kts`, `subprojects { version = projectVersion }`),
  and the plugin release job publishes that one version. No plugin can be released on its own cycle yet.
- The loader resolves plugins from a `URLClassLoader` whose parent is the server class loader, which is parent-first. A class of the
  server jar therefore wins over the same class in a jar of the plugins directory. A newer viewer jar could not replace classes that
  sit on the server classpath, whatever the registry does. The embedded copy must be a nested jar that the loader opens in its own
  class loader.

## Decisions taken with the maintainer

1. **Settings placement.** The Javadoc settings stay under Artifacts, where they are today.
2. **Packaging.** The viewer is its own Gradle module with its own published jar and its own version (`1.0.0`, independent of the
   server version). The server jar embeds a copy of that jar as a nested resource. The embedded copy keeps the viewer active by
   default, also for a server jar started outside the image. A jar with the same plugin id in the plugins directory wins when its
   version is higher (the precedence rule is part of the prerequisite, see below).
3. **Image.** The image does not copy the released jar into `/app/bundled-plugins`. The embedded copy already covers the image, and
   a second copy with the same id would only add a second precedence path. The image's file list is unchanged, and the viewer keeps
   loading as it does today. The only change inside the image is the content of `ingot.jar`, which now carries the nested jar instead
   of the classes.

## Prerequisite change: `add-embedded-plugin-jars` (feat(plugin))

The loader cannot do the embedded copy or the version rule yet. That work is a separate `feat(plugin)` change, proposed and merged
before any code pull request of this change:

- The loader reads the nested jars of the server jar (`embedded-plugins/*.jar`), extracts each one to a directory under the working
  directory, and loads it through its own class loader, as the plugins directory is loaded. The origin of such a plugin is
  `EMBEDDED`, which the tier model reads as Core (built into the server).
- A jar of the plugins directory with the same plugin id as an embedded jar wins only when its version, read from the jar manifest
  `Implementation-Version`, is strictly higher. Otherwise the embedded copy is used and one INFO line names both versions and the
  one in use. A jar without a version counts as lowest, so the embedded copy wins. Two plugins with one id are never registered.
- The Gradle build can place a named module jar into `embedded-plugins/` of the server jar. With no jar named, the change has no
  effect, so it can merge before this change.

The prerequisite is not a `refactor`, so it is not a group of this change (the rule "one type per change" applies). Its proposal is
written separately. Until it has merged, the code pull requests of this change do not start.

## Maintainer decisions

Settled on 2026-11-17: the maintainer accepted the recommended default of each question below. The default is the answer this proposal builds in, and the viewer stays embedded and active by default. A later change of answer updates the matching requirement and task before the first code pull request is opened.

1. **Disable and remove.** Settled 2026-11-17, default: an operator disables the viewer with the existing `javadoc.enabled` setting. There is no new
   environment variable, because a switch is a `feat` change. No embedded copy is removed in this change. Both cases hide the
   Javadoc links (design D5).
2. **Received cache events.** Settled 2026-11-17, default: the cluster package declares a public interface for "evict this received javadoc path", and
   `JavadocFacade` implements it. The core looks it up when an event arrives. No new plugin API is added. The alternative is a public
   receiver in `com.reposilite.plugin.api`, which is a `feat(plugin)` change of its own.
3. **Stored `javadoc` row without the viewer.** Settled 2026-11-17, default: a build without the viewer leaves the stored `javadoc` domain untouched, and
   nothing deletes it. Task 0.1 verifies that the shared configuration keeps domains it does not know.
4. **Settings deep link without the domain.** Settled 2026-11-17, default: `/settings/javadoc` on a server without the domain redirects to `/settings`.
   Task 0.2 records what the dashboard does today.
5. **Dashboard icon.** `reposilite-frontend/src/components/icons/JavaDocsIcon.vue` is not imported anywhere. Settled 2026-11-17, default: it stays.
6. **Dev run configuration.** `.run/Build javadoc-plugin to test workspace` builds a workspace jar that the new module no longer
   produces. Settled 2026-11-17, default: it is removed in the move pull request.
7. **Release of the viewer jar.** The plugin release job (`publish_plugins.sh`, the `plugins` job of `release-please.yml`) publishes one
   hard-coded plugin, `opensearch-plugin`, and its `check-bundled` step compares the published jar with the jar in the image. Neither
   fits the viewer yet. Settled 2026-11-17, default: this change ends with a published Maven artifact built locally, and the release wiring (a
   release-please component with its own tag, the catalogue entry, and a `check-bundled` that compares with the embedded copy) is the
   follow-up change `add-javadoc-plugin-release` (`ci(plugin)`).
8. **Extraction directory.** Settled 2026-11-17, default: the prerequisite extracts into `<workingDirectory>/embedded-plugins/`, and the directory is
   rewritten at every start. The prerequisite proposal confirms it.

## Why

The Javadoc viewer has its own settings domain, routes, event listener and cache directory, and it is the only viewer-like feature
still in the core package `com.reposilite.javadocs`. The module directory `reposilite-plugins/javadoc-plugin` existed before the code
was merged into the core (commit `9b8f8c2a8`, 2023). The core also names the viewer in three places that have no business knowing it:
the cluster cache hooks (`FacadeCacheHooks` imports `JavadocFacade`), the frontend fallback for the suffix placeholder, and the
dependency rules.

A module with its own version and jar lets the viewer be fixed and released on its own cycle. The embedded copy and the precedence
rule keep it working for every existing install, so the update path is "drop a newer jar into the plugins directory" and nothing is
forced on operators.

The move must not change what an operator sees. Ingot is a drop-in replacement for Reposilite: the settings key, the routes, the
cluster events, the cache path and the dashboard stay the same.

## What Changes

- **A Gradle module `reposilite-plugins/javadoc-plugin`**, package `com.reposilite.javadocs`, plugin id `javadoc`, unchanged. Its
  sources move with `git mv`. It has `version = "1.0.0"` (own version, `// x-release-please-version` marker for the follow-up), a
  shadow jar `javadoc-plugin.jar` with `Implementation-Version` in the manifest, and a Maven publication `net.onelitefeather.ingot:javadoc-plugin`
  of that jar, configured as `gcs-storage-plugin` configures its own. It compiles against `reposilite-backend` with `compileOnly`.
- **The server jar carries the viewer as a nested jar**, `embedded-plugins/javadoc-plugin.jar`. The backend `shadowJar` takes the
  module's jar through the hook of the prerequisite change. The classes of the viewer are no longer on the server classpath, so the
  nested copy cannot shadow or be shadowed by a plugins-directory jar at class level.
- **A core seam for received evictions.** `FacadeCacheHooks` no longer imports `JavadocFacade`. The cluster package declares a public
  interface `JavadocCacheReceiver`, and the viewer's facade implements it. An architecture test keeps the core free of the viewer.
- **Settings.** The `javadoc` domain keeps its key, fields, defaults and its place under Artifacts. The static dashboard entry keeps
  the label and the description. No manifest field is added.
- **Frontend.** The fallback for `{{REPOSILITE.JAVADOC_SUFFIXES}}` in `reposilite-frontend/src/store/placeholders.ts` becomes empty.
  The server sends the placeholder only while the viewer is loaded and javadoc is enabled, so a build without the viewer shows no
  Javadoc action.
- **Build.** `.dockerignore` gets `!reposilite-plugins/javadoc-plugin`, because the backend build script names the project. The
  Dockerfile does not change. The run configuration of question 6 is removed.
- **Docs.** `docs/gitbook/references/architecture/backend-architecture.md` describes the module and the embedded copy. The owner of
  the `javadoc` domain is named in `docs/gitbook/references/settings/settings-domains.md` and
  `docs/gitbook/references/http-api/dashboard-pages.md`. The configuration reference, the environment variables and the web routes
  page do not change.
- **Tests.** The unit and integration tests of `javadocs` move with the code and keep their assertions. `ClusterJavadocEventsTest`
  stays in the core, because it tests the bus.

### Out of scope

- The prerequisite `add-embedded-plugin-jars` (its own `feat(plugin)` change).
- A new environment variable or switch for the viewer (question 1).
- A release of the viewer jar and its catalogue entry (question 7, `add-javadoc-plugin-release`).
- Copying the released jar into `/app/bundled-plugins` (decision 3).
- Changing routes, the JSON API, the settings schema, the cache directory `javadocs/`, or the event names `javadoc.invalidated` and
  `JAVADOC_INVALIDATED`.
- A public receiver for cluster events in `com.reposilite.plugin.api` (question 2).
- The tier declaration. The tier model reads an embedded viewer as `core` without a declaration.
- The time bound and the poll for javadoc caches. `docs/gitbook/set-up/cluster/configure-cluster-bus.md` already says they do not exist.
- Renaming or removing `JavaDocsIcon.vue` (question 5).
- Any change to `com.reposilite.journalist` or `org.panda_lang.*`, as set out in `CLAUDE.md`.

## Type and pull request

- Conventional Commits type and scope: `refactor(javadoc)`. Every code pull request of this change has this type, because the change
  moves code and does not change its behaviour. The commits inside one pull request have one type each (a failing test is
  `test(javadoc)`, the build file is `build(javadoc)`, the move is `refactor(javadoc)`), and squash-merging makes the pull request title
  the single commit on main.
- The proposal pull request is `docs(openspec): propose extract-javadoc-plugin`.
- Code pull requests, in the order of `tasks.md`, each titled as its group names it. Each starts with `refactor(javadoc):`.

## Operator impact

No operator must change anything. The environment variables, the configuration files, the volume, the manifest, the plugin list and
the image reference stay as they are. An unmodified upstream compose file still starts the server, and an existing `/app/data` is
adopted. The viewer is active by default. A newer `javadoc-plugin.jar` in the plugins directory is the update path, and it is opt-in
by the operator. No change is BREAKING.

## Capabilities

### New Capabilities

- `javadoc-plugin`: the module and its own version, the embedded copy and the update rule, the default, the disabling by setting, the
  behaviour of the dashboard without the viewer, the place of the settings page, the cluster events and the boundary to the core.

### Modified Capabilities

- `javadoc-cache`: the received eviction is applied by the viewer, and a received event is ignored while the viewer is not loaded.

## Impact

- **Modules.** A new Gradle project `:reposilite-plugins:javadoc-plugin` with its own version and publication. `:reposilite-backend`
  embeds its jar. No other module changes.
- **Binary surface.** The public classes of `com.reposilite.javadocs` keep their names and signatures. The seam interface is public in
  `net.onelitefeather.ingot.cluster`, which is not in `pluginApiPackages`, so the plugin API dump does not change.
- **Image.** `ingot.jar` changes its content, not its place. The file list of the image is unchanged.
- **Order.** The prerequisite merges first. Then the seam, the move, the frontend and the docs, each buildable and passing alone.

## Verification

- Unit tests run without network, database or system clock, with the injected `Clock` where time matters, as the tests of
  `javadocs` do today.
- An architecture test fails when a class of the core package imports `com.reposilite.javadocs`.
- A test pins the placeholder of the suffixes to empty when the server does not send it, and the Javadoc action is absent in that case.
- A test pins `/settings/javadoc` and the redirect to `/settings` for a missing domain (question 4).
- The moved unit and integration tests pass in the module with only their build wiring changed.
- The container images job passes on the move pull request, including the upgrade rehearsal against the upstream image.
- An install that starts the new server with the default image and no plugins directory shows the viewer active, and a plugins-directory
  jar of a higher version is the one in use (test of the prerequisite, repeated here as an integration check).
- `npx -y @fission-ai/openspec@latest validate extract-javadoc-plugin --strict` passes, and
  `grep -rnP '[\x{2013}\x{2014}]' openspec/changes/extract-javadoc-plugin` prints nothing.

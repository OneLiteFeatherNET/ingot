# Design

## Context

Verified against origin/main (c0f01846):

- The build has no API dump, japicmp or binary-compatibility validator (no match for `apiDump`, `japicmp` or
  `binary-compatibility` in any Gradle file). Kotlin is 2.4.0 (`build.gradle.kts`).
- `checksum-plugin` and `migration-plugin` compile against `project(":reposilite-backend")` (`compileOnly`). Their `src/main`
  is identical to upstream tag `3.5.28` (`git diff 3.5.28 origin/main -- reposilite-plugins/{checksum,migration}-plugin/src/main`
  is empty). They use `MavenFacade`, `LookupRequest`, `PreResolveEvent` (destructured), `Repository.mirrorHosts`,
  `Repository.storageProvider`, `Location`, and the token types `AccessToken`, `AccessTokenPermission`, `Route`,
  `RoutePermission`, `ExportService`, `AccessTokenDetails`.
- Upstream publishes `com.reposilite:reposilite:3.5.28` as a thin jar with a POM (about 0.9 MB, 43 dependencies), and the
  plugins as `com.reposilite.plugin:checksum-plugin:3.5.28` and `com.reposilite.plugin:migration-plugin:3.5.28` (thin jars;
  the migration plugin declares `kaml-jvm`), all at `https://maven.reposilite.com/releases`. The root build already declares
  that repository, filtered to the group `com.reposilite`, releases only.
- `PluginLoader` loads jars from the plugins directory of the working directory.
- Known drift that the first report must show: `RepositorySettings` (3.5.28: `id, visibility, redeployment, ...`; now
  `id, visibility, type, description, tags, redeployment, ...`) and `MavenSettings` (3.5.28: `repositories`; now
  `repositories, resolutionCacheClusterMaxAgeSeconds`).

## Decisions

### D1: japicmp against the 3.5.28 jar, not an API dump

Two tools were considered.

- **API dump** (the Kotlin Gradle plugin's ABI validation, successor of kotlinx binary-compatibility-validator). It writes a
  dump of the current public API into the repository and fails when the build differs from it. It understands Kotlin
  visibility, so `internal` declarations stay out of the dump. Its baseline is whatever the first dump records, which is
  Ingot today. Every break since the fork, including the two above, would be written into that baseline and become
  invisible.
- **japicmp against `com.reposilite:reposilite:3.5.28`.** It compares two jars at bytecode level, which is what a plugin
  actually links against. Its baseline is the release whose plugins Ingot promises to run. It shows the drift that already
  happened, and it keeps showing anything new.

The promise is "a Reposilite 3.5.28 plugin runs on Ingot", so the baseline has to be 3.5.28. japicmp is the only one of the
two that can use it. Its weakness is that it reads bytecode without Kotlin metadata: a Kotlin `internal` class is public in
bytecode, and a data class exposes `copy$default` and `componentN`. D2 bounds the first; the second is real ABI and is
reported on purpose, because plugins do call `copy` and destructure events (`PreResolveEvent` in `checksum-plugin`).

An API dump for Ingot's own new plugin surface (`net.onelitefeather.ingot.format`) is still useful once that surface is
declared stable. It answers a different question (did Ingot break its own API) and is out of scope here.

### D2: the compared surface is a fixed package list

The report covers these packages, the surface documented in `plugin-api.md` and used by the bundled plugins:
`com.reposilite.plugin`, `com.reposilite.plugin.api`, `com.reposilite.maven`, `com.reposilite.maven.api`,
`com.reposilite.storage`, `com.reposilite.storage.api`, `com.reposilite.token`, `com.reposilite.token.api`,
`com.reposilite.statistics.api`, `com.reposilite.shared`, `com.reposilite.web.api`, `com.reposilite.configuration.shared.api`,
`com.reposilite.console.api`, `com.reposilite.status`, and `com.reposilite.maven.application`.

The last one is watched rather than promised: `plugin-api.md` lists facades, events and annotations, not settings classes,
but plugins read `MavenSettings` and `RepositorySettings` through `SharedConfigurationFacade.getDomainSettings`, and both
already drifted. Whether settings classes become part of the promise is decided when the first report is triaged.

Comparing all of `com.reposilite.*` would flood the first report with internal classes that no plugin can reach in Kotlin
(for example `MavenPlugin` is `internal`). The list lives in one place in the build script, with a comment pointing at the
docs section, so widening it is a reviewed one-line change. `com.reposilite.journalist` is a separate third-party artifact
and is never in the list.

The old classpath is the transitive dependency set of the 3.5.28 POM, resolved in a detached configuration, so japicmp can
follow supertypes. Resolution uses only Maven Central and the existing upstream repository.

### D3: binary canaries are the upstream jars, resolved by Gradle

The integration test needs the jars as files. A test that downloads them breaks Repeatable. Instead a test-scope
configuration `pluginCanary` declares `com.reposilite.plugin:checksum-plugin:3.5.28` and
`com.reposilite.plugin:migration-plugin:3.5.28` (with `kaml-jvm` transitively), Gradle resolves and caches them like any
dependency, and the `integration` task passes their paths as a system property. Each test copies the jars into the plugins
directory of a fresh `@TempDir` working directory and starts its own server.

The upstream repository filter gets one more group, `com.reposilite.plugin`, releases only. It stays a content filter, so
the host still cannot answer for anything else.

What each canary asserts:

- `checksum-plugin`: after deploying `a-1.0.jar` to a repository without mirrors, `GET .../a-1.0.jar.sha256` answers the
  SHA-256 of the jar. This exercises `PreResolveEvent` destructuring, `MavenFacade.findFile`, `LookupRequest` and
  `Repository.storageProvider`.
- `migration-plugin`: with a legacy `tokens.dat` fixture in the working directory, the server starts and the token from the
  fixture exists afterwards with its route. This exercises the token types and `ExportService`.

### D4: report-only, and what flips it

In this change nothing fails: the japicmp task sets `failOnModification = false`, and the CI job runs the canary step with
`continue-on-error: true`. The canary tests are ordinary assertions; only the job tolerates their failure for now, because
the first run is expected to find drift.

The follow-up that makes the gate blocking does three things: it adds `reposilite-backend/plugin-api/accepted.txt` listing
every difference of the first report that the maintainers accept (each with a reason), it sets the task to fail on any
binary-incompatible difference not in that file, and it removes `continue-on-error`. That split keeps this pull request
small and keeps the triage of existing drift a decision of its own.

### D5: compile canaries report edits

`./gradlew build` already compiles both bundled plugins. The gap is that a pull request can break the API and repair the
plugin in the same diff. The CI job therefore runs `:reposilite-plugins:checksum-plugin:test` and
`:reposilite-plugins:migration-plugin:test` as a named step and adds a line to the summary when the pull request touches
`reposilite-plugins/checksum-plugin/src/main` or `reposilite-plugins/migration-plugin/src/main`. A reviewer then knows to
read the japicmp report closely.

## Risks

- Upstream could delete 3.5.28 from its repository. Mitigation: Gradle caches the artifacts in CI, and the follow-up can
  vendor the two plugin jars and the server jar's API into a test fixture if the host proves unreliable.
- The first report will be long. That is the point; it is triaged once.

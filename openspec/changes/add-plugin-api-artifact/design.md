# Design

Paths are relative to `reposilite-backend/src/main/kotlin/` unless stated otherwise. Line numbers are from `origin/main` at
`3614657d0`.

## Context

- **One publication today.** `reposilite-backend/build.gradle.kts:291-296` publishes a single `bundle` with `artifactId = "ingot"`.
  There is no `plugin-api` or `ingot-plugin` module and no second publication.
- **Compile path of a plugin.** `reposilite-plugins/example-plugin/build.gradle.kts` uses `compileOnly(project(":reposilite-backend"))`.
  The docs point external plugins to `net.onelitefeather.ingot:ingot` as `compileOnly`, from a repository that requires credentials
  for reads (`docs/gitbook/references/plugins/plugin-api.md`, coordinate section).
- **The surface a plugin touches.** `@Plugin` is a Java annotation (`com/reposilite/plugin/api/Plugin.java:30-31`). The base class is
  `com/reposilite/plugin/api/ReposilitePlugin.java:29`. Kotlin helpers `event`, `facade`, `reposilite()` and `parameters()` are top
  level functions in `com/reposilite/plugin/ReposilitePluginExtensions.kt:26-35`. Facades are marker interfaces
  (`plugin/api/Facade.kt:19`, `plugin/api/PluginComponents.kt:20`). Events are declared in `plugin/api/Event.kt:19-34`, and the bus
  lives in `plugin/Extensions.kt:59-72`. Extensions injects annotated fields by reflection (`plugin/Extensions.kt:44-51`).
- **Packages are not a boundary.** The ABI report already lists package prefixes (`pluginApiPackages`, around
  `reposilite-backend/build.gradle.kts:500-520`), among them `com.reposilite.maven`, `com.reposilite.token`, `com.reposilite.shared` and
  `com.reposilite.storage`. Those packages also contain internal implementation classes. Kotlin `internal` classes compile to public
  classes in bytecode, so a package-based jar would carry them. Example: `MavenFacade` is a public class with a Kotlin
  `internal` constructor (`maven/MavenFacade.kt:59`), and `RepositorySecurityProvider` is an `internal class` in the same package
  (`maven/RepositorySecurityProvider.kt:34`). Visibility in Kotlin does not hide them from the artifact.
- **The gate does not fail.** The japicmp task `pluginApiReport` (around `build.gradle.kts:521-544`) compares against
  `com.reposilite:reposilite:3.5.28` and sets `failOnModification = false`, so it reports and never fails. CI runs it in the job
  `plugin-api` (`.github/workflows/ci.yml:563`). Turning the report into a gate is `extend-plugin-abi-gate`.
- **Release.** The bundle is part of the release; nightlies are built by `release-please.yml`. The exact publication steps are read
  in the first code pull request, not assumed here.

## Goals and non-goals

Goals: a published artifact that a third party can compile against with no server classes on the classpath other than the ones it
needs; a build that proves the artifact is closed under references; zero change for the bundle and for existing plugins.

Non-goals: a new API surface; a compatibility gate; a public repository; a new package name; changing how plugins are loaded.

## Decisions

### D1: the artifact is an explicit class allowlist

The artifact holds the classes named in `reposilite-backend/plugin-api-classes.txt`, one fully qualified name per line, with a
trailing `*` allowed for a nested set of one class (`Outer*` for `Outer$Inner`). Packages are not used, for the reason in Context.
The first version of the list is derived from the types a plugin can reach today: the annotation, the base classes, the helper
file, the facades listed in `plugin/api`, the events of `maven/api`, `storage/api`, `web/api` and `token`, and the value types those
signatures use. The list is reviewed by a maintainer in the first code pull request. Every later change that adds plugin-facing
types adds its classes to the list in its own pull request.

The jar is built by a task `pluginApiJar` from `sourceSets.main.output`, filtered by the list. No class is generated or copied from
another module.

### D2: the closure check fails the build

A class in the artifact may reference a class outside the artifact only if that class is on the allowlist, or is a JDK or Kotlin
standard library class. The check reads the constant pool and the signatures of the class files in the artifact and fails with the
referring class and the missing target, one line per violation. Two cases are handled explicitly:

- A type that appears only in a private member is not a violation, because it is not part of the signature a plugin compiles
  against. The check ignores private members.
- A type that appears in a public signature is a violation, unless the author adds it to the allowlist in the same pull request.

The check reads class files. The reader is ASM if it is already on the build classpath, otherwise `jdeps` run from the JDK, to be
confirmed in the first code pull request. The check is a pure function of the class files, so its unit tests can build class files
into a temporary directory and need no server.

The number of violations for the first list is not known until the list is built. If it is large, the allowlist grows in the
pull request that finds the violation, as D2 states (Q2). No class is moved or renamed by this change.

### D3: the consumer fixture is the proof

`reposilite-test/plugin-api-consumer` is a Gradle module with one sample plugin. It depends on the slim jar as a file, not as a
project, and on nothing else of the monorepo. Its test asserts that the plugin class carries `@Plugin` and that the
annotation is readable at runtime. The fixture is the executable form of "a plugin built only against the published API". In CI it
runs with the artifact built in the same job, so no network and no credentials are needed (F.I.R.S.T.: repeatable).

### D4: the marker annotation

`@ExperimentalIngotPluginApi` is a `@RequiresOptIn` annotation with level `WARNING`, in `net.onelitefeather.ingot.plugin`, mirroring
`@ExperimentalIngotSearchApi` (`search/backend/SearchBackendApi.kt:32`). It is added to the allowlist. No type uses it in this change.

### D5: the bundle does not change

`bundle` keeps its coordinates, its contents and its task graph. The only change in `build.gradle.kts` is a second publication and the
new tasks. The bundle task output is compared in the first code pull request (same jar hash before and after, apart from the
marker class, which is added to both).

### D6: publication follows the release

The slim artifact has the same version as `ingot` and is published in the release job next to the bundle. It is not published with
nightlies (Q3, settled). A consumer that wants a nightly uses the bundle as today.

## Consequences for the other prerequisites

- `add-artifact-enrichment-hook`, `add-search-document-attributes`, `add-search-attribute-facets` and `expose-plugin-access-checks`
  each add their classes to the allowlist. Their closure is checked by this change's build.
- The two portal plugins build against `net.onelitefeather.ingot:ingot-plugin-api` (`compileOnly`) and never against a project.
- `extend-plugin-abi-gate` may later compare the slim artifact against the previous release instead of the bundle.

## Alternatives considered

- **Package prefixes.** Rejected (D1): they carry internal classes that Kotlin visibility does not hide.
- **A separate Gradle module copying sources.** Rejected: two sources of truth.
- **Shading the whole bundle.** Rejected: the goal is the opposite, a smaller set.
- **Publishing only a Kotlin interface module.** Rejected: the plugin API is Java and Kotlin, and `@Plugin` is Java.

## Open questions, settled 2026-11-17

The maintainer settled every question with the default recommended here. The answers are binding for the code pull requests.

- **Q1. Publication target.** Settled 2026-11-17: the repository that already hosts `ingot`, so reads need credentials as they do
  today. A public repository or Maven Central is not part of this change.
- **Q2. Closure policy.** Settled 2026-11-17: the build fails on a public-signature reference outside the allowlist (D2). The
  allowlist grows in the pull request that finds the violation. The closure is not included automatically.
- **Q3. Nightly publication.** Settled 2026-11-17: release only.
- **Q4. Version.** Settled 2026-11-17: the same version as `ingot`, with no independent versioning.
- **Q5. Ownership of the allowlist.** Settled 2026-11-17: a maintainer reviews every change of `plugin-api-classes.txt`. The review
  is enforced by review, not by a code owners file.

## Risks

- **The first list may be too small or too large.** Mitigation: the closure check reports both sides, and the consumer fixture
  exercises the signatures a real plugin uses.
- **Hidden coupling to Kotlin metadata.** A Kotlin-only consumer may need `kotlin-stdlib` at the same version as the server.
  Mitigation: the fixture is written in Kotlin and pins the version the server uses.

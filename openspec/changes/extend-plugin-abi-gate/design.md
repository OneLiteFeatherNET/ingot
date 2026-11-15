# Design

Paths are relative to the repository root. The build script is `reposilite-backend/build.gradle.kts`.

## Context

Verified against `main` at `3614657d0`:

- The 3.5.28 comparison is `pluginApiReport` (about line 523 of the build script). It reads `pluginApiPackages` (about line 503), fifteen
  `com.reposilite.*` packages, and compares `com.reposilite:reposilite:3.5.28` with the `jar` task output, with
  `onlyBinaryIncompatibleModified = true`, `failOnModification = false` and `ignoreMissingClasses = true`.
- The Ingot bundle is `net.onelitefeather.ingot:ingot`, published by the `bundle` publication of `reposilite-backend/build.gradle.kts`.
  Releases are published to `repo.onelitefeather.dev/onelitefeather-releases` by the release workflow (`.github/workflows/release-please.yml`).
  The last tag is `v1.1.1`.
- `docs/gitbook/references/plugins/plugin-api.md` documents these Ingot packages to plugin authors: `net.onelitefeather.ingot.format`
  (experimental), `net.onelitefeather.ingot.repository` (`RepositoryRegistry`, "not yet stable"), `net.onelitefeather.ingot.search.backend`
  (experimental) and `net.onelitefeather.ingot.storage.capability` (experimental).
- Experimental annotations, all `@RequiresOptIn` markers: `ExperimentalIngotFormatApi` (`format/RepositoryFormat.kt`), `ExperimentalIngotSearchApi`
  (`search/backend/SearchBackendApi.kt`) and `ExperimentalIngotStorageApi` (`storage/IngotStorageProviderFactory.kt`). The storage capability
  interfaces in `storage/capability/StorageCapabilities.kt` carry the storage marker.
- `RepositoryRegistry` (`repository/RepositoryRegistry.kt`) has no marker.
- The CI job "Plugin API" (`.github/workflows/ci.yml`) runs `pluginApiReport`, the upstream canaries and the bundled plugin tests, and it
  writes the report into `$GITHUB_STEP_SUMMARY`.
- The Python tests of the CI scripts live in `.github/scripts/tests` and run with `unittest`, without Gradle.

## Decisions

### D1: the Ingot baseline is the last release, not an API dump

Two tools were considered, as in `gate-plugin-abi/design.md`, D1.

- **An API dump with a Kotlin binary-compatibility tool.** It records the public API in a file and fails on a difference. It understands
  Kotlin visibility. Its baseline is whatever the first dump records. For Ingot's packages that would be today's code, so every break
  since the last release would be written into the baseline and disappear. A dump also needs a committed file per package.
- **japicmp against the last Ingot release.** The baseline is the artifact that operators installed and plugins were compiled against. A
  break is a difference from that artifact, which is what a plugin feels. This is the same tool and the same report format as the 3.5.28
  comparison, so reviewers read one kind of report.

japicmp is chosen. Its weakness is Kotlin visibility, which D4 handles.

### D2: the baseline is a property, changed by the release

`ingotBaseline` is a property in `reposilite-backend/build.gradle.kts`, set to `1.1.1`. Each release changes it, in the release change
that the release process owns, so that the next release is compared with the one before it. Until a release exists in the repository,
the value is the last release that does (Q1).

The baseline is resolved in a detached configuration with `isTransitive = false`, as the 3.5.28 archive is, and its dependencies come
from a second detached configuration that serves as the classpath.

### D3: experimental and promised findings

Each finding gets a group.

- **Experimental** when the changed type or member carries an annotation whose simple name starts with `ExperimentalIngot`, or when its
  package is in `ingotExperimentalPackages`. The second rule is for a package that is documented as experimental without an annotation.
- **Promised** otherwise.

The report has two sections with counts. Only the promised section will be able to block later. The experimental section is reported
for information and never blocks, which is what an experimental marker promises to its users. Classification reads the annotations of the
new jar and of the baseline. A type that becomes experimental in the new jar is reported once, as experimental, with the note "marked
experimental in this build".

The annotation lookup uses the class-file reader that the build already has on its classpath. The implementation pins which one. Reading
annotations with a reader is needed because japicmp does not report annotations of the type it compares.

### D4: internal declarations

japicmp sees Kotlin `internal` classes as public, and Kotlin gives an `internal` member a mangled name with a `$` in it. Without a filter
the first report would list every internal type of `search.application` and `search.database`. The filter runs in two steps:

1. A task `ingotInternalClasses` reads the `@kotlin.Metadata` of every class of the Ingot packages in the new jar with
   `kotlinx-metadata-jvm` (a pinned dependency), and writes the internal class names to `build/plugin-api/ingot-internal.txt`. A class
   with the Kotlin visibility flag `internal` is recorded there.
2. The comparison excludes those classes through japicmp's class excludes, and it excludes members of public classes whose names
   contain `$`.

The filter errs on the side of keeping a finding. A class whose metadata cannot be read is not excluded, and the task logs its name.

### D5: the package list and its test

The Ingot list is a single line per package in `ingotPluginApiPackages`, next to the 3.5.28 list, with a comment that points at the
documentation section. Widening it is one reviewed line, as for the 3.5.28 list.

A Python unit test in `.github/scripts/tests` reads the build script and `plugin-api.md` as text. It fails when a package that the
documentation names under `net.onelitefeather.ingot` is missing from `ingotPluginApiPackages`, and when `com.reposilite.journalist` appears in
either list. Reading two repository files is allowed in this test, because it runs in CI from a checkout and needs no network. The test uses
no clock and no shared state.

### D6: the report, the job and the documentation

- `ingotPluginApiReport` writes `build/reports/plugin-api/ingot-report.html` and `ingot-report.md`. The existing `plugin-api-report` artifact
  uploads the directory, so both reports go up together.
- The job "Plugin API" gets one step, `./gradlew --build-cache :reposilite-backend:ingotPluginApiReport`, after the 3.5.28 report, and one
  section "Ingot plugin API" in the summary with the counts of each group. The step is report-only, like the others.
- `plugin-api.md`, "Compatibility checks": the baseline row gets the Ingot release, the packages row gets the Ingot packages with their
  experimental status, and the "Not compared" row is removed. The gate stays report-only.

### D7: the prerequisites join the list in their own pull requests

`add-plugin-api-artifact` (a slim `net.onelitefeather.ingot:ingot-plugin-api` artifact), `add-artifact-enrichment-hook`,
`add-search-document-attributes`, `add-search-attribute-facets` and `expose-plugin-access-checks` each add their package to
`ingotPluginApiPackages` in the same pull request that adds the package. The list test of D5 is then the check that a new package was
not forgotten. When the slim artifact exists in a release, the report compares it with its own previous release as well, as a second
baseline entry. That addition belongs to `add-plugin-api-artifact`, not to this change.

### D8: Ingot-introduced plugin packages in the `com.reposilite.*` namespace

Some plugin API that Ingot added keeps the `com.reposilite.*` package name, as CLAUDE.md requires. The dashboard facade is one of them:
`PluginUiFacade`, its registrar and the page model live in `com.reposilite.frontend.api`. Neither comparison sees that package today. The
3.5.28 list does not name it, because 3.5.28 had no such facade, and the Ingot list names only `net.onelitefeather.ingot.*`. A change to it
would pass both reports silently.

So `ingotPluginApiPackages` also lists `com.reposilite.frontend.api`. Its baseline is the last Ingot release, and a type that is not in that
release is an addition, which is reported as such and never as a break. The changes of `add-plugin-reader-pages` (a new `PluginUiReaderPage`
class and a `registerReaderPage` method) and of `add-plugin-ui-browse-blocks` (four subclasses of the sealed `PluginUiBlock`) then show as
additions. A break in them, for example a changed constructor of `PluginUiPage`, shows as a break. That is the case the reader design (its
D1) avoids on purpose.

The rule of D5 applies: a package that is documented as plugin API of Ingot is listed, and `com.reposilite.journalist` is never listed.

## Risks

- **Experimental findings are read as harmless.** Mitigation: the section is labelled "experimental, not promised", and the documentation says
  what the marker means for users.
- **The Ingot baseline is not in the repository.** If the release is not visible, the report is empty for Ingot and says so. The job does not
  fail.
- **The first report is long.** It is triaged once, in the same way as the 3.5.28 report, and each accepted finding is recorded before the
  gate becomes blocking.
- **Kotlin metadata changes with the compiler.** Mitigation: the metadata reader is pinned, and D4's unreadable-class rule keeps a finding
  rather than dropping it.

## Open questions (each has a default)

- **Q1. Is `net.onelitefeather.ingot:ingot:1.1.1` in the release repository?** Default: yes, to verify in the first pull request with
  `gradlew dependencies --configuration` on the detached configuration. If not, the first release that is there becomes the baseline.
- **Q2. When does this land?** Default: after the prerequisites it lists, as the Minecraft proposal orders it. Recommended: before them, so
  that each prerequisite is reported as it lands. The maintainers choose.
- **Q3. Mark `RepositoryRegistry` experimental.** Default: report it as promised, which is what the code says. The documentation says "not yet
  stable". Adding a marker is a code change in another pull request, and this change does not make it.
- **Q4. Where `com.reposilite.frontend.api` is compared.** Default: in the Ingot comparison (D8), because the facade is new in Ingot and
  is not part of the 3.5.28 promise. Adding it to the 3.5.28 list would only show it as an addition, so the maintainers may add it there for
  visibility, without any change of the promise.

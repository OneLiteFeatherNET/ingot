# Design

This design depends on the plugin annotation and loader of `reposilite-backend` (`com.reposilite.plugin.api.Plugin`,
`ReposilitePlugin`, `PluginLoader`), on the plugin store (`net.onelitefeather.ingot.plugin.store`) and on the release
generator `.github/scripts/generate_plugin_catalogue.py`. Names are those of those files.

## Context

- Nine plugin modules sit in `reposilite-plugins/` (checksum, elasticsearch, example, groovy, meilisearch, migration,
  nexus-import, opensearch, prometheus, swagger). Three of them are bundled in the image (`Dockerfile`, the lines that copy
  `prometheus-plugin.jar`, `nexus-import-plugin.jar` and `opensearch-plugin.jar` to `/app/bundled-plugins`). Their switches are in
  `PluginLoader.BUNDLED_SWITCHES`.
- About thirty plugins are built into `reposilite-backend` (`@Plugin` on classes under `com/reposilite/*` and
  `net/onelitefeather/ingot/*`). They are part of `ingot.jar`, so they are in the image.
- Experimental markers exist and are opted in to by the code: `ExperimentalIngotStorageApi`, `ExperimentalIngotFormatApi` and
  `ExperimentalIngotSearchApi`. The search plugins (`elasticsearch-plugin`, `opensearch-plugin`, `meilisearch-plugin`) already
  opt in to `ExperimentalIngotSearchApi` with file-level `@file:OptIn`.
- The system information page lists the plugins of the instance by name and version (`SystemInfoView.vue`, section "Plugins").
  The support bundle writes the same list to `plugins.json` (`SupportInformationCollector`, `SupportSources.PluginFact`).
  The dashboard therefore already has a plugin list, and no new page is needed.
- The startup log of `PluginLoader.initialize` prints the loaded plugins in chunks of five names.
- The catalogue is append-only and signed (`plugin-catalogue-publishing`). Its reader ignores unknown fields, so an additive
  field keeps schema 1.
- `SUPPORT.md` says the support is community support, that there is no paid tier and no SLA, and that no feature depends on a
  support arrangement. Those sentences stay unchanged. The tier section is added below them, and it defines Official as a plugin
  maintained and tested by the OneLiteFeather maintainers, fixed on the supported release lines, on a best effort community basis.

## Decisions

### D1: The tier is an annotation element with a default of Community

`com.reposilite.plugin.api.PluginTier` (`CORE`, `OFFICIAL`, `COMMUNITY`, `EXPERIMENTAL`) and `@Plugin tier() default
PluginTier.COMMUNITY`. The default is the value a plugin without the element gets, so a plugin compiled against Reposilite reads
as Community with no change. The JVM takes an annotation default from the annotation type it is compiled against, so the
default does not depend on the plugin's own compile.

Alternatives rejected: a manifest attribute in the jar (`Ingot-Plugin-Tier`) would be read from the jar file, which the loader
already opens for the service file, but it would not be checked by the compiler and would not be visible to the CI check that
reads annotations. A separate descriptor file would add a second file per plugin with no benefit.

### D2: The effective tier is computed from origin first, then from the declaration

| Origin of the plugin | Declared | Effective |
|---|---|---|
| Built into the backend, or a jar of `/app/bundled-plugins` | any | Core, except Experimental when declared Experimental |
| A jar of the plugins directory or of the plugin store | Core | Community, with a warning that names the plugin |
| A jar of the plugins directory or of the plugin store | Community, Official or Experimental | the declared tier |
| Any origin, no element | (default Community) | Community |

Origin wins because the image is what OneLiteFeather ships. A jar that claims Core from outside the image must not be shown
as Core. Experimental stays sticky inside the image, so `opensearch-plugin` shows as Experimental when its switch is on.

Warnings never change what loads. The loader logs them and goes on.

### D3: Experimental plugins warn once at start

When a plugin's effective tier is Experimental, `PluginLoader.initialize` logs one WARN line after the list of loaded plugins.
The line names the plugin and says that its API may change or be removed without a major release, and that the plugin has no
support beyond the public issues. It is logged once per start, not per request.

### D4: Catalogue entries carry the tier, and the tier of a version is fixed

The generator does not read jars. It reads `build/plugin-tiers.json`, which the tier check of D5 writes from the annotations, and
it writes each tier as `"tier": "<name>"` in lower case. A plugin without an entry in that file stops the release. The field is optional for readers: a missing field reads as `community`, and an unknown value
refuses that entry only. The store compares the entry tier with the effective tier of the loaded jar. A mismatch is a
warning, and the jar's own effective tier is what the dashboard shows.

Because the catalogue is append-only, the tier of a released version cannot change. The generator refuses to write a changed
tier for a listed version, as it refuses a changed digest. A promotion ships as a new version with the new tier. A yanked version
keeps its tier.

### D5: The tier check is a Gradle task with its own tests, run in a CI job

A Gradle task `checkPluginTiers` in `buildSrc` reads each module under `reposilite-plugins/` and computes its evidence:

- **Experimental opt-in**: the module's Kotlin or Java sources contain `OptIn(ExperimentalIngot...` or `@Experimental...` from
  the names above. A module with an opt-in must declare Experimental. A module that declares Experimental must have an opt-in.
- **Official evidence**: the module has a `contractTest` source set or task, and a workflow in `.github/workflows/` runs
  `:reposilite-plugins:<module>` by name. Both are needed.
- **Core evidence**: the module's jar is one of the jars the `Dockerfile` copies into `/app/bundled-plugins`. The set of Core
  declarations and the set of bundled jars must be equal.
- **Community**: no extra evidence, only the plugin ABI check that already runs.

The task reads the declaration from the compiled class (the annotation), so the check tests what the loader sees. It runs in
its own CI job, `Plugin tiers`, on every pull request, and the release job runs it before the generator. The job is blocking
from the first day, because a declaration without evidence is a false promise, not a metric.

### D6: The tier is shown in four places, and no new page is added

- The startup log keeps the existing line of `initialize` byte for byte. The tiers are one additional INFO line logged directly after
  it, in the same chunks of five names, in the form `checksum (official)`. No existing line changes text, and no line is removed.
- The system information page shows the tier as a label next to the version of each plugin. The label is read from the existing
  support information, which gets the field `tier` in `PluginInformation` and `PluginFact`.
- The support bundle `plugins.json` gets the same field.
- The GitBook shows the tier on each plugin page and defines the tiers on one page, `references/plugins/plugin-tiers.md`.

A dashboard plugin browser is not added. The store's selection list shows the tier when it exists, and that is its only place.

### D7: Support is fixed on the release lines listed in SUPPORT.md

Core and Official are fixed on the release lines that `SUPPORT.md` lists as supported, on a best effort basis. Official means a
plugin maintained and tested by the OneLiteFeather maintainers, and it does not mean paid support. The existing `SUPPORT.md` text
stays unchanged: community support only, no paid tier, no SLA, and no fix date is promised. The text never says "the latest
release" for a tier. `SUPPORT.md` is the one list, and the GitBook page and the `SUPPORT.md` section both link to it, so a change of
the list changes the fixed lines of both tiers at once.

The parallel change `add-release-branches-and-backports` adds release branches and backports, and LTS lines may follow it.
This change does not depend on the branching model, only on `SUPPORT.md` naming the lines. When that change merges, the list
it writes in `SUPPORT.md` is what Core and Official follow. Until then the list is the one `SUPPORT.md` has today.

Community plugins receive community support only, through the public discussions. Experimental plugins carry no promise about the API
at all, and they are fixed on the same best effort basis as any other plugin.

### D8: The table of existing and pending plugins

Status is the state on `origin/main` for the existing modules, and the state of the open pull requests or the open change for
the pending ones. Tiers are the values the declarations of task 5.2 will carry.

| Plugin | Where it lives | Status | Tier | Reason |
|---|---|---|---|---|
| Built-in plugins of the backend (about thirty `@Plugin` classes in `reposilite-backend`) | in `ingot.jar` | shipped | Core | Built into the server image, covered by the drop-in contract. |
| `prometheus-plugin` | bundled in the image, switch `INGOT_METRICS_ENABLED` | shipped | Core | Copied into `/app/bundled-plugins` by the `Dockerfile`, and it has its own switch. |
| `nexus-import-plugin` | bundled in the image, switch `INGOT_NEXUS_IMPORT_ENABLED` | shipped | Core | Copied into `/app/bundled-plugins` by the `Dockerfile`. Its dashboard page is part of the image. |
| `opensearch-plugin` | bundled in the image, switch `INGOT_OPENSEARCH_ENABLED`, off by default | shipped, switched off | Experimental | Opts in to `ExperimentalIngotSearchApi`. Sticky rule (decision 2). Only Experimental jar in the image. |
| `elasticsearch-plugin` | separate jar, not in the image | shipped as a jar | Experimental | Opts in to `ExperimentalIngotSearchApi`. CI runs its contract suites in `search-backends.yml`, but Official is not reachable while the API is experimental (decision 2). |
| `meilisearch-plugin` | separate jar, not in the image | shipped as a jar | Experimental | Same as Elasticsearch. Contract suite in `search-backends.yml`. |
| `checksum-plugin` | separate jar, not in the image | shipped as a jar | Official | CI job test in `ci.yml` and the bundled compile canary. The upstream contract test is specified by `plugin-abi-gate`. Confirm it runs before the declaration (task 5.2). |
| `migration-plugin` | separate jar, not in the image | shipped as a jar | Official | CI job test in `ci.yml`, the canary, and the upstream contract test specified by `plugin-abi-gate` (same confirmation). |
| `swagger-plugin` | separate jar, not in the image | shipped as a jar | Community | Maintained with core, but no contract test yet. Promote by a one-line change once one exists (decision 3). |
| `groovy-plugin` | separate jar, not in the image | shipped as a jar | Community | Maintained with core, no contract test. Scripting is not an experimental API. Decision 3. |
| `example-plugin` | template, not listed in the catalogue | not shipped | Community | A template for plugin authors. It is not installed by operators. |
| `azure-storage-plugin` (`add-azure-blob-storage-plugin`, PRs #403 to #424) | separate jar, not in the image | open, not built | Experimental | Opts in to `ExperimentalIngotStorageApi`, and its proposal says the surface stays experimental until it has a user (decision 4). |
| `gcs-storage-plugin` (`add-gcs-storage-plugin`, PRs #426 to #455) | separate jar, not in the image | open, not built | Experimental | Opts in to `ExperimentalIngotStorageApi` (decision 4). |
| `storage-cache-plugin` (`add-storage-cache-plugin`, PRs #406 to #448) | separate jar, not in the image | open, not built | Experimental | Opts in to `ExperimentalIngotStorageApi` as its proposal states (decision 4). |
| `lucene-plugin` (`add-lucene-search-plugin`, PRs #431 to #449) | separate jar, not in the image | open, not built | Experimental | Opts in to `ExperimentalIngotSearchApi` as its design states (decision 4). |
| Gradle plugin portal (`add-gradle-plugin-portal-plugin`) | own repository, built against the published API | change only, no pull request | Undecided: Official or Experimental | Official if it uses only stable API and ships with CI and contract tests in its own repository. Experimental if its design needs one of the new hooks, which its proposal says it does. Decide when it is built. |
| Minecraft plugin portal (`add-minecraft-plugin-portal-plugin`) | own repository, built against the published API | change only, no pull request | Undecided: Official or Experimental | Same rule as the Gradle portal, and the same open hook question. |

The Gradle and Minecraft portals have no open pull request. Their change is only a proposal, so the table shows their target tier
and not a declaration.

### D9: The tier check and the four changes in flight

The four plugins in flight (`azure-storage-plugin`, `gcs-storage-plugin`, `storage-cache-plugin`, `lucene-plugin`) do not
declare a tier yet. Once task 5.2 and the check are on `main`, each of them declares `EXPERIMENTAL` in the commit that adds its
registration, so the check passes on its pull request. That is a follow-up in those changes, not a task of this one.

## Risks

- **Log parsers.** None of the existing startup lines changes. The tier information is one additional line, so a parser of the
  existing line sees no difference. The upgrade notes name the additional line so that a parser which matches all lines sees it.
- **Official claims that CI does not back.** The check exists so that an unbacked claim fails. The check reads the workflow files
  by name, so a renamed job can make it fail. That is intended, and the failure names the module and the missing evidence.
- **Experimental pressure.** Marking three search plugins and four cloud plugins Experimental puts a warning in the start log
  of every installation that enables them. This is the point of the tier.
- **Spoofed tiers.** A plugin from outside the image cannot be Core (D2). A plugin from outside can still claim Official in its
  own jar. The dashboard shows the claim as the jar's declaration, and only the signed catalogue entry of OneLiteFeather is
  evidence.

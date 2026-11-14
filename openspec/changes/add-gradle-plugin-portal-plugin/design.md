# Design

Priority: nice to have, after the must-have changes.

## Context: what a Gradle plugin looks like in a Maven repository

- **Marker artifact.** A plugin published with `java-gradle-plugin` plus `maven-publish` produces, per declared plugin id, a marker
  with groupId `<id>`, artifactId `<id>.gradle.plugin`, same version, packaging `pom`, and a single dependency on the implementation
  artifact. Gradle resolves `plugins { id("x") version "1" }` by requesting `x/x.gradle.plugin/1/x.gradle.plugin-1.pom` from every
  repository of `pluginManagement`.
- **Descriptor in the jar.** The implementation jar holds `META-INF/gradle-plugins/<id>.properties` with `implementation-class=...`.
  The file name is the plugin id. This is how a jar is recognised when no marker was published (for example a plugin deployed by hand).
- **Description.** The marker POM normally carries `name` and `description` from the plugin declaration; the implementation POM
  carries the project description. Both may be missing.

## Findings in this repository

Paths are relative to `reposilite-backend/src/main/kotlin/` unless stated.

- **Events a plugin can hear.** `DeployEvent(repository, gav, by)` (`com/reposilite/maven/api/DeploymentApi.kt:35`) fires after a
  store. It carries no content, no size and no checksum. `ResolvedFileEvent` and `ResolvedFileDataEvent`
  (`com/reposilite/maven/api/LookupApi.kt:53,60`) fire on reads. Nothing fires when a proxied file is cached, and nothing fires
  during a reindex (`docs/gitbook/reference/plugin-api.md:56-80` is the whole event table).
- **Reading a file and rights.** `MavenFacade.findFile` and `findData` (`com/reposilite/maven/MavenFacade.kt:98,102`),
  `canAccessResource(accessToken, repository, gav)` (`:282`) and `findRepositories(accessToken)` (`:285`) answer per viewer. They are
  in `com.reposilite.maven`, which the ABI gate watches (`gate-plugin-abi/design.md`, D2).
- **Visibility.** `RepositoryVisibility { PUBLIC, HIDDEN, PRIVATE }` (`com/reposilite/maven/RepositoryVisibility.kt`).
  `RepositorySecurityProvider.kt:37-60` decides listing and reading per visibility; the implementation confirms the HIDDEN rule in
  its first red test.
- **The index.** One row per stored file (`net/onelitefeather/ingot/search/IndexModel.kt:33`: file record, provenance, size,
  modified, sha1, sha256, indexed at). POM content is not indexed (`extract-search-backend-spi/design.md`, "Indexed fields").
  `ArtifactSearchQuery` filters groupId and artifactId by **prefix**, extensions by exact value, and has free text `q`
  (`net/onelitefeather/ingot/search/ArtifactSearchEngine.kt`). There is no suffix match and no attribute filter, so "artifactId
  ends in `.gradle.plugin`" cannot be asked and "this jar contains a Gradle descriptor" cannot be stored.
- **Reaching the index from a plugin.** `SearchFacade.engine` is public, but `SearchFacade` has an internal constructor and lives in
  `net.onelitefeather.ingot.search.application`, outside the gated package list. `SearchAccessCompiler.compile(subject,
  repositories)` (`net/onelitefeather/ingot/search/access/SearchAccessCompiler.kt:60`) builds the access value from an
  `AccessSubject` and is not documented for plugins.
- **Search backends are pluggable but experimental.** `add-search-backend-registration` makes the backend a plugin point marked
  `@ExperimentalIngotSearchApi`. The indexed document is still the file row with fixed fields.
- **UI.** `add-plugin-ui-extension-point` offers the blocks `text`, `keyValue`, `table`, `form`, `action`, `status`, areas
  `settings|users|teams|tokens|maintenance|audit|console|dashboard|*`, groups Repository, Security, System and Support, and answers
  `401` to anonymous callers on `GET /api/frontend/plugin-ui` (`add-plugin-stylesheet-channel/design.md`, "Starting point"). `text`
  is plain text, so a snippet cannot be copied with one click and keeps its line breaks only by accident.
- **Distribution.** `docs/gitbook/reference/plugin-api.md:11` names the coordinate `net.onelitefeather.ingot:ingot`, the full server
  bundle, `compileOnly`, from a repository that "requires credentials, also for reads". A third party needs credentials to compile
  and sees every internal class.
- **Existing plugins.** `reposilite-plugins/example-plugin/build.gradle.kts` compiles against `project(":reposilite-backend")` and
  writes into `reposilite-test/workspace/plugins`. Nothing in the monorepo shows a plugin built from a published artifact, which is
  what this change is meant to prove.

## Goals and non-goals

Goals: a reader-facing Gradle plugin overview with correct snippets, rights-aware; an external plugin repository as a validation of
the plugin API; a visible list of core gaps.

Non-goals:

- **Not a replacement for publishing to the Gradle Plugin Portal.** `com.gradle.plugin-publish` talks to plugins.gradle.org's own API.
  Publishing to Ingot stays an ordinary Maven deploy (`maven-publish`).
- No reviews, ratings, download counters or comments.
- No change to repository behaviour, resolution or the Maven API.
- Not a mirror of plugins.gradle.org (see D6).

## Decisions

### D1: own repository, published API artifact only

The plugin is built with `compileOnly("net.onelitefeather.ingot:ingot-plugin-api:<v>")` from `add-plugin-api-artifact` (name is Q2).
The build file contains no `project(...)` dependency and no copy of server classes. A CI job builds the plugin from a clean checkout
with only published artifacts, loads the jar into the current Ingot image and runs a smoke test. That job is the validation.

### D2: detection happens at index time, not per request

Scanning every POM and jar on each page view is not acceptable. The plugin registers an enricher (`add-artifact-enrichment-hook`)
that is called when a file is indexed or reindexed, for `.pom` and `.jar` files only. It writes attributes
(`add-search-document-attributes`): `gradle.plugin.id`, `gradle.plugin.kind` (`marker` or `descriptor`), `gradle.plugin.name`,
`gradle.plugin.description`, `gradle.plugin.impl` (coordinates of the implementation, from the marker's dependency). The pages
read the index with an attribute filter.

Fallback without core changes: the plugin keeps its own cache fed by `DeployEvent` and a startup scan through `MavenFacade`. That
works for hosted repositories, misses proxied files and does not scale across replicas (each builds its own cache). It is recorded
as the fallback if the prerequisites are declined, not as the plan.

The marker needs only the path and the small POM. The descriptor case reads the jar's central directory and one small entry, with a
bounded stream (D7).

### D3: grouping

A plugin id groups its versions across repositories. A listing row is (id, latest version, description, repositories the viewer may
read). Versions sort with the existing ordering (`ArtifactVersions.kt`). Snapshots are listed and marked.

### D4: rights

The plugin never answers from data the viewer could not read:

1. Resolve the viewer from the request (`accessed { }`), including anonymous.
2. Obtain the access value for the viewer (`expose-plugin-access-checks`) and pass it with the query, so the backend filters rows.
3. For a detail page, repeat `canAccessResource` on the marker path first, so a guessed URL answers `404` and not `403` (no
   existence leak). A plugin that does not exist answers the same `404`.

HIDDEN repositories follow the core rule: a viewer who may read the file can open the detail page by link; the list shows only
what the core lists for that viewer.

### D5: snippets

Generated on the server from validated values (plugin id matched against `[A-Za-z0-9_.-]+`, version from the path), delivered as
plain data to a code block, never as markup.

```kotlin
// build.gradle.kts
plugins { id("com.example.demo") version "1.2.3" }
```
```groovy
plugins { id 'com.example.demo' version '1.2.3' }
```
```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://<host>/<repository>")
            // only for a PRIVATE repository:
            credentials {
                username = providers.gradleProperty("ingotUser").get()
                password = providers.gradleProperty("ingotToken").get()
            }
        }
        gradlePluginPortal()
    }
}
```

Credentials are property names, never values, and never the viewer's own token. The block appears only when the repository
visibility is PRIVATE; for HIDDEN none, because reading needs no sign-in. `gradlePluginPortal()` stays by default because a plugin's
own dependencies often resolve there (Q5). The host is the configured public base URL, else the request host (Q4).

### D6: portal mirror or proxy (open, default non-goal)

`https://plugins.gradle.org/m2` is a plain Maven layout, so an Ingot proxy repository can already front it and Gradle can use it as
a `pluginManagement` repository. That works today with no plugin and becomes a how-to in the plugin repository. This change does
not: list proxied plugins (default: hosted repositories only, expressible with the existing `OriginFilter.HOSTED`), reimplement the
portal's search or REST API, or accept the portal's publish API. Whether cached proxied plugins should be listed is Q3.

### D7: bounded reading

The enricher gets a bounded handle from the hook (maximum bytes, deadline). A POM over 1 MiB, a jar with a damaged central
directory or an entry over 64 KiB is skipped with a warning naming repository and path. A failed read never fails indexing or a
deploy (the hook isolates failures). XML is parsed with DTDs and external entities disabled.

### D8: UI

A reader page `gradle-plugins` (`add-plugin-reader-pages`) with a list block and filter, and a detail page with `keyValue`, a
`table` of versions and `code` blocks with a copy action (`add-plugin-ui-browse-blocks`). No plugin script runs in the browser,
consistent with `add-plugin-ui-extension-point`. The stylesheet channel (`add-plugin-stylesheet-channel`) is used only if a block
needs a small adjustment.

## Plugin API gaps found (each becomes a separate core change)

| # | Gap | Evidence | Proposed change |
| --- | --- | --- | --- |
| G1 | No slim, readable API artifact | `plugin-api.md:11`, `example-plugin/build.gradle.kts` | `add-plugin-api-artifact` |
| G2 | No hook on index, reindex or proxy cache, with content access | `DeploymentApi.kt:35`, `LookupApi.kt:53` | `add-artifact-enrichment-hook` |
| G3 | No attributes on index rows; no suffix or attribute filter | `IndexModel.kt:33`, `ArtifactSearchEngine.kt` | `add-search-document-attributes` |
| G4 | Plugin pages for administrators only; anonymous gets 401 | `add-plugin-ui-extension-point/design.md`, "The page model" | `add-plugin-reader-pages` |
| G5 | No code block with copy, no list or filter block | same, block list | `add-plugin-ui-browse-blocks` |
| G6 | Viewer access value built in an undocumented, ungated class | `SearchAccessCompiler.kt:60` | `expose-plugin-access-checks` |
| G7 | ABI gate ignores `net.onelitefeather.ingot.*` | `gate-plugin-abi/design.md`, D2 | `extend-plugin-abi-gate` |
| G8 | No stable plugin-owned persistence | search tables are core-owned | not proposed: the plugin stores nothing (D2) |

## Alternatives considered

- **Build inside the monorepo as a module of `reposilite-plugins/`.** Rejected: it would not validate the third party path.
- **A standalone service reading Maven over HTTP.** Rejected: it cannot use the viewer's session, so it would need its own login and
  rights model, and rights-aware listing is the point.
- **Scan on request.** Rejected: cost grows with repository size.
- **Read only `maven-metadata.xml` of `*.gradle.plugin` directories.** Cheap and works for markers, but misses descriptor-only jars
  and gives no description. Kept as part of the D2 fallback.

## Risks

- Many prerequisites. The first useful slice (marker detection through the D2 fallback, no UI) needs only G1; the rest raise quality,
  not feasibility.
- The experimental search API may change before this plugin ships. It is pinned through `apiVersion` and the catalogue
  `compatibility` block (`add-plugin-store/design.md`, D8).

## Open questions (each has a default)

- **Q1. Repository name.** Default `ingot-plugin-gradle-portal`.
- **Q2. API artifact coordinate.** Default `net.onelitefeather.ingot:ingot-plugin-api`.
- **Q3. List proxied plugins.** Default no, hosted only.
- **Q4. Base URL in snippets.** Default the configured public URL, else the request host.
- **Q5. Keep `gradlePluginPortal()` in the generated block.** Default yes.
- **Q6. Order of the prerequisites.** Default G1, G2, G3 (shared), then G6, G4, G5, G7.

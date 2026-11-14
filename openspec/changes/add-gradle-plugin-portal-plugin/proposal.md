# Proposal

Priority: nice to have, after the must-have changes. Explicitly backlog. Nothing here is implemented now; this proposal records the
shape and, above all, the gaps in the plugin API that the work would expose.

## Why

Teams that publish Gradle plugins to an Ingot repository have no place that answers "which Gradle plugins do we have, in which
versions, and how do I use them". Today a colleague has to know the plugin id, guess the version from the file browser and copy a
`pluginManagement` block from a wiki. The Gradle Plugin Portal solves exactly this for the public ecosystem, and an Ingot instance
that already holds the plugin marker artifacts holds all the information needed.

The second reason is the more important one for Ingot. This plugin is meant to live in its **own repository**, built **only against the
published plugin API artifact**, with the ABI gate watching it. That makes it a real third party consumer of the plugin API. Every
place where the author has to reach into server internals, copy code or ask for a new hook is a gap in the plugin API, and this
proposal makes those gaps visible as separate prerequisite changes in the core instead of letting them be solved by workarounds.

## What changes

In a new repository `OneLiteFeatherNET/ingot-plugin-gradle-portal` (name is open question Q1):

- **Detection.** The plugin recognises Gradle plugins in readable repositories: plugin marker artifacts
  (`<id>:<id>.gradle.plugin:<version>`, a POM) and jars that carry `META-INF/gradle-plugins/<id>.properties`.
- **Listing.** A dashboard page for readers lists the plugins with id, versions and description, with a search box.
- **Snippets.** A detail page shows the `plugins { id("...") version "..." }` snippet (Kotlin and Groovy DSL) and the
  `pluginManagement { repositories { ... } }` block for `settings.gradle(.kts)`.
- **Rights.** Only plugins from repositories the viewer can read are listed. The snippet contains credential placeholders only for
  a private repository, never a real secret.
- **Catalogue.** The plugin is published as a jar with a detached signature and listed in the plugin catalogue
  (`publish-plugin-catalogue`, `add-plugin-store`).

Prerequisite changes in the core (each its own proposal and pull request, additive, defaults unchanged; the design repeats the table
with the evidence):

| Change | Commit title | Needed because |
| --- | --- | --- |
| `add-plugin-api-artifact` | `feat(plugin): publish a slim plugin api artifact` | A third party compiles against the whole server bundle today. |
| `add-artifact-enrichment-hook` | `feat(search): let plugins enrich artifacts when they are indexed` | `DeployEvent` carries no content and nothing fires for proxied or reindexed files. |
| `add-search-document-attributes` | `feat(search): store plugin attributes with indexed artifacts` | The index cannot carry "this POM is a Gradle plugin marker". |
| `add-plugin-reader-pages` | `feat(frontend): let plugins contribute pages for readers` | Plugin pages exist only for administrators and answer 401 to anonymous callers. |
| `add-plugin-ui-browse-blocks` | `feat(frontend): add list, code and filter blocks to plugin pages` | The block set has no code block with a copy action and no list. |
| `expose-plugin-access-checks` | `feat(maven): expose viewer aware access checks to plugins` | Viewer rights live partly in an experimental, non-gated package. |
| `extend-plugin-abi-gate` | `ci(plugin): cover the ingot plugin api packages in the abi gate` | The gate watches `com.reposilite.*` only. |

The API artifact, the enrichment hook, the attributes and the gate change are shared with `add-minecraft-plugin-portal-plugin`;
whichever proposal is picked up first carries them.

## Conventional Commits type and scope

- Plugin repository: `feat(gradle-portal)` for the features, and `build`, `ci`, `docs` as usual in that repository.
- Core prerequisites: the commit titles in the table above, one type each.
- This proposal PR: `docs(openspec): propose add-gradle-plugin-portal-plugin and add-minecraft-plugin-portal-plugin`.

## Impact

- **Drop-in.** Nothing changes for an installation without the plugin. The core prerequisites are additive: new events, new optional
  attributes, a new optional page kind, a new artifact. No default changes, no configuration key is renamed, no existing plugin
  is affected. Packages stay `com.reposilite.*` where they exist today; new surface lives in `net.onelitefeather.ingot.*` and is
  marked experimental until it has a user, and this plugin is that user.
- **Capability added:** `gradle-plugin-portal`, owned by the plugin repository.
- **Not in the ingot monorepo.** No module is added to `reposilite-plugins/`.

## Open maintainer questions

See `design.md`, section "Open questions". Each carries a default; none blocks the proposal PR.

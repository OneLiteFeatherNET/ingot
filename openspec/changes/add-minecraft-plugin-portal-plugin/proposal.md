# Proposal

Priority: nice to have, after the must-have changes. Explicitly backlog. Nothing here is implemented now; this proposal records the
shape, the security approach for reading untrusted jars, and the gaps in the plugin API that the work would expose.

## Why

OneLiteFeather runs Minecraft servers and publishes plugins and mods as Maven artifacts. People who want one of them today browse a
file tree of group and artifact directories and read the POM to learn what it is. Platforms such as Modrinth answer the real
questions directly: what does this do, for which loader and game version, which version is current, what does it depend on, where is
the download. The jar already says all of that in its own descriptor file. A portal view over the Maven repository turns the
repository into a catalogue without a second system of record: the Maven repository stays the backend, the jar stays the truth.

The second reason is the one that matters for Ingot. The plugin lives in its **own repository**, is built **only against the
published plugin API artifact**, and runs behind the ABI gate. It is a demanding third party consumer: it needs a hook when files are
stored and reindexed, attributes in the search index, filterable facets and a card based reader page. Each missing piece is a gap
in the plugin API, recorded here as a separate prerequisite change in the core.

## What changes

In a new repository `OneLiteFeatherNET/ingot-plugin-minecraft-portal` (name is open question Q1):

- **Metadata extraction.** On deploy, and on reindex for existing artifacts, the plugin inspects a jar for these descriptors
  (names verified against public docs, see the design): `plugin.yml`, `paper-plugin.yml`, `bungee.yml`, `velocity-plugin.json`,
  `fabric.mod.json`, `META-INF/mods.toml`, `META-INF/neoforge.mods.toml`, `quilt.mod.json`, `META-INF/sponge_plugins.json`, and a
  Minestom extension descriptor if one exists. It extracts name, version, authors, description, supported API or game versions,
  dependencies and loader.
- **Index.** The extracted fields are stored in the search index as attributes through the search SPI.
- **Portal view.** A page for readers with cards, filters by loader and game version, free text, a detail page with version list and
  a download link to the Maven path.
- **Security.** Jars are untrusted input. Reading is bounded in bytes, entries, time and memory; YAML is parsed with a safe loader
  without arbitrary types; a parsing failure never blocks the deploy.
- **Rights.** Only artifacts the viewer may read are listed, counted or faceted.
- **Catalogue.** The plugin is published as a jar with a detached signature and listed in the plugin catalogue.

Prerequisite changes in the core (each its own proposal and pull request, additive, defaults unchanged; the design has the evidence):

| Change | Commit title | Needed because |
| --- | --- | --- |
| `add-plugin-api-artifact` | `feat(plugin): publish a slim plugin api artifact` | A third party compiles against the whole server bundle today. |
| `add-artifact-enrichment-hook` | `feat(search): let plugins enrich artifacts when they are indexed` | `DeployEvent` carries no content and nothing fires for proxied or reindexed files. |
| `add-search-document-attributes` | `feat(search): store plugin attributes with indexed artifacts` | The index has fixed fields and no per-project document. |
| `add-search-attribute-facets` | `feat(search): filter and facet on plugin attributes` | Loader and game version filters need multi-valued, countable attributes. |
| `add-plugin-reader-pages` | `feat(frontend): let plugins contribute pages for readers` | Plugin pages are for administrators only. |
| `add-plugin-ui-browse-blocks` | `feat(frontend): add list, code and filter blocks to plugin pages` | The block set has no card list or filter bar. |
| `expose-plugin-access-checks` | `feat(maven): expose viewer aware access checks to plugins` | Viewer rights live partly in an experimental, ungated package. |
| `extend-plugin-abi-gate` | `ci(plugin): cover the ingot plugin api packages in the abi gate` | The gate watches `com.reposilite.*` only. |
| `add-plugin-asset-routes` | `feat(plugin): serve static plugin assets to readers` | Icons from jars need a safe, cacheable route (optional, see D9). |

The API artifact, the enrichment hook, the attributes and the gate change are shared with `add-gradle-plugin-portal-plugin`.

## Conventional Commits type and scope

- Plugin repository: `feat(minecraft-portal)` for the features, and `build`, `ci`, `docs` as usual in that repository.
- Core prerequisites: the commit titles in the table, one type each.
- This proposal PR: `docs(openspec): propose add-gradle-plugin-portal-plugin and add-minecraft-plugin-portal-plugin`.

## Impact

- **Drop-in.** Nothing changes for an installation without the plugin. The core prerequisites are additive: new events, optional
  attributes, a new optional page kind, a new artifact. No default changes, no key renamed, no existing plugin affected. Packages
  stay `com.reposilite.*` where they exist; new surface lives in `net.onelitefeather.ingot.*`, marked experimental until it has a
  user, and this plugin is that user.
- **Capability added:** `minecraft-plugin-portal`, owned by the plugin repository.
- **Not in the ingot monorepo.**

## Open maintainer questions

See `design.md`, section "Open questions". Each carries a default; none blocks the proposal PR.
